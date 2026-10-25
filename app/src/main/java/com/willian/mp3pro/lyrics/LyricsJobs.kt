package com.willian.mp3pro.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.provider.MediaStore
import com.willian.mp3pro.data.AppPrefs
import com.willian.mp3pro.data.Http
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.OneDriveClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.sqrt

enum class LyricsStage { IDLE, MODEL, FETCH, DECODE, TRANSCRIBE, FAILED }

data class LyricsJobState(
    val mediaId: String = "",
    val stage: LyricsStage = LyricsStage.IDLE,
    val percent: Int = 0,
    val message: String = ""
) {
    val running: Boolean
        get() = stage != LyricsStage.IDLE && stage != LyricsStage.FAILED
}

/**
 * Gera a letra de uma música: baixa o modelo (uma vez), obtém o áudio, decodifica,
 * transcreve em trechos e salva o resultado no celular. Um trabalho por vez.
 */
object LyricsJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(LyricsJobState())
    val state: StateFlow<LyricsJobState> = _state.asStateFlow()

    private const val CHUNK_SECONDS = 12
    private const val MIN_TAIL_SECONDS = 3
    private const val SILENCE_RMS = 0.004f

    fun cancel() {
        job?.cancel()
    }

    fun start(context: Context, mediaId: String, title: String = "", artist: String = "", durationMs: Long = 0L) {
        if (mediaId.isEmpty() || _state.value.running) return
        val app = context.applicationContext
        _state.value = LyricsJobState(mediaId, LyricsStage.FETCH, 0, "Preparando…")
        job = scope.launch {
            val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mp3pro:lyrics")
            lock.acquire(40 * 60 * 1000L)
            var temp: File? = null
            try {
                generate(app, mediaId, title, artist, durationMs, { ensureActive() }) { temp = it }
                _state.value = LyricsJobState()
            } catch (e: CancellationException) {
                _state.value = LyricsJobState()
            } catch (e: Throwable) {
                _state.value = LyricsJobState(
                    mediaId, LyricsStage.FAILED, 0,
                    e.message?.takeIf { it.isNotBlank() } ?: "Não foi possível gerar a letra."
                )
            } finally {
                temp?.delete()
                if (lock.isHeld) lock.release()
            }
        }
    }

    private fun generate(
        context: Context, mediaId: String, title: String, artist: String, durationMs: Long,
        active: () -> Unit, onTemp: (File) -> Unit
    ) {
        // 0) letra pronta no LRCLIB: rápido e quase sempre mais correta
        _state.value = LyricsJobState(mediaId, LyricsStage.FETCH, 0, "Buscando a letra no LRCLIB…")
        val ready = LrcLib.fetch(title, artist, durationMs)
        active()
        if (ready != null) {
            LyricsStore.save(mediaId, ready)
            return
        }
        if (!WhisperEngine.AVAILABLE) {
            throw IOException(
                "Não achei a letra desta música no LRCLIB. Confira se o nome do arquivo " +
                    "está como “Título - Artista”."
            )
        }

        val size = WhisperSize.fromId(AppPrefs.whisperSize(context))
        val lang = AppPrefs.lyricsLanguage(context).let { if (it == "auto") "" else it }

        // 1) modelo
        if (!WhisperModel.isReady(context, size)) {
            _state.value = LyricsJobState(mediaId, LyricsStage.MODEL, 0, "Baixando o modelo de IA (${size.downloadMb} MB, só desta vez)…")
            WhisperModel.download(
                context, size,
                onProgress = { _state.value = LyricsJobState(mediaId, LyricsStage.MODEL, it, "Baixando o modelo de IA (${size.downloadMb} MB, só desta vez)…") },
                checkCancelled = active
            )
        }

        // 2) áudio
        val source = resolveSource(context, mediaId, onTemp) { pct ->
            _state.value = LyricsJobState(mediaId, LyricsStage.FETCH, pct, "Baixando a música para analisar…")
        }
        active()
        _state.value = LyricsJobState(mediaId, LyricsStage.DECODE, 0, "Lendo o áudio…")
        val samples = AudioDecoder.decode(
            context, source,
            onProgress = { _state.value = LyricsJobState(mediaId, LyricsStage.DECODE, it, "Lendo o áudio…") },
            checkCancelled = active
        )
        if (samples.size < AudioDecoder.TARGET_RATE) throw IOException("O áudio é curto demais.")

        // 3) transcrição em trechos
        val lines = ArrayList<LyricLine>()
        WhisperEngine(
            WhisperModel.encoder(context, size), WhisperModel.decoder(context, size),
            WhisperModel.tokens(context, size), lang
        ).use { engine ->
            val rate = AudioDecoder.TARGET_RATE
            var pos = 0
            var lastText = ""
            while (pos < samples.size) {
                active()
                val end = chunkEnd(samples, pos, rate)
                val chunk = samples.copyOfRange(pos, end)
                val rms = rms(chunk, 0, chunk.size)
                if (rms >= SILENCE_RMS) {
                    val text = engine.transcribe(chunk)
                    if (text.isNotBlank() && !text.equals(lastText, ignoreCase = true)) {
                        lastText = text
                        val startMs = (pos + voiceStart(chunk, rms)) * 1000L / rate
                        val spanMs = (end - pos) * 1000L / rate
                        lines += splitLines(text, startMs, spanMs)
                    }
                }
                pos = end
                val pct = (pos * 100L / samples.size).toInt()
                _state.value = LyricsJobState(
                    mediaId, LyricsStage.TRANSCRIBE, pct,
                    "Transcrevendo a letra… (${lines.size} linhas)"
                )
            }
        }

        if (lines.isEmpty()) throw IOException("Não foi encontrada voz nesta música (pode ser instrumental).")
        LyricsStore.save(mediaId, lines)
    }

    // ---- origem do áudio --------------------------------------------------------------

    private fun resolveSource(
        context: Context,
        mediaId: String,
        onTemp: (File) -> Unit,
        onProgress: (Int) -> Unit
    ): AudioSource {
        val id = LyricsStore.normalize(mediaId)
        if (id.startsWith("content://")) return AudioSource(uri = Uri.parse(id))
        if (id.startsWith("local:")) {
            val num = id.removePrefix("local:").toLongOrNull() ?: throw IOException("música local inválida")
            return AudioSource(
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, num)
            )
        }
        Offline.fileOf(id)?.let { return AudioSource(file = it) }

        // OneDrive em streaming: baixa para a pasta temporária, só para analisar.
        val temp = File(context.cacheDir, "lyrics_tmp.audio")
        onTemp(temp)
        val url = OneDriveClient.get(context).downloadUrl(id)
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Falha ao baixar a música (HTTP ${resp.code}).")
            val body = resp.body ?: throw IOException("Resposta vazia.")
            val total = body.contentLength()
            var done = 0L
            var lastPct = -1
            temp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        }
        return AudioSource(file = temp)
    }

    // ---- recorte em trechos --------------------------------------------------------------

    /** Fim do trecho que começa em [pos]: perto de 12 s, no ponto mais silencioso da vizinhança. */
    private fun chunkEnd(samples: FloatArray, pos: Int, rate: Int): Int {
        val target = pos + CHUNK_SECONDS * rate
        // Sobra pouco? O último trecho leva tudo (o Whisper aceita até 30 s).
        if (samples.size - pos <= (CHUNK_SECONDS + MIN_TAIL_SECONDS) * rate) return samples.size
        val frame = rate / 20 // 50 ms
        val from = (target - rate * 3 / 2).coerceAtLeast(pos + frame)
        val to = (target + rate * 3 / 2).coerceAtMost(samples.size - frame)
        var best = target
        var bestEnergy = Float.MAX_VALUE
        var i = from
        while (i <= to) {
            val e = rms(samples, i, i + frame)
            if (e < bestEnergy) {
                bestEnergy = e
                best = i + frame / 2
            }
            i += frame
        }
        return best.coerceIn(pos + rate, samples.size)
    }

    private fun rms(a: FloatArray, from: Int, to: Int): Float {
        val end = to.coerceAtMost(a.size)
        if (end <= from) return 0f
        var sum = 0.0
        for (i in from until end) sum += a[i] * a[i]
        return sqrt(sum / (end - from)).toFloat()
    }

    /** Onde a voz realmente começa dentro do trecho (pula o silêncio inicial), em amostras. */
    private fun voiceStart(chunk: FloatArray, chunkRms: Float): Int {
        val frame = AudioDecoder.TARGET_RATE / 20
        val limit = maxOf(0.01f, chunkRms * 0.25f)
        var i = 0
        while (i + frame <= chunk.size) {
            if (rms(chunk, i, i + frame) >= limit) {
                return (i - AudioDecoder.TARGET_RATE / 5).coerceAtLeast(0)
            }
            i += frame
        }
        return 0
    }

    /** Divide frases longas em duas linhas, repartindo o tempo pelo tamanho do texto. */
    private fun splitLines(text: String, startMs: Long, spanMs: Long): List<LyricLine> {
        if (text.length <= 64) return listOf(LyricLine(startMs, text))
        val mid = text.length / 2
        var cut = -1
        var best = Int.MAX_VALUE
        for (i in text.indices) {
            if (text[i] == ' ' || text[i] == ',') {
                val d = abs(i - mid)
                if (d < best) {
                    best = d
                    cut = i
                }
            }
        }
        if (cut <= 0 || cut >= text.length - 1) return listOf(LyricLine(startMs, text))
        val first = text.substring(0, cut + 1).trim()
        val second = text.substring(cut + 1).trim()
        val offset = (spanMs * 0.9 * cut / text.length).toLong()
        return listOf(LyricLine(startMs, first), LyricLine(startMs + offset, second))
    }
}
