package com.willian.mp3pro.lyrics

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Onde está o arquivo de áudio a ser lido: um arquivo do app ou um endereço do Android. */
class AudioSource(val file: File? = null, val uri: Uri? = null)

/**
 * Lê qualquer áudio que o Android decodifica (mp3, m4a, flac, ogg...) e entrega o
 * som em mono, 16 kHz, números de -1 a 1: o formato que o Whisper espera.
 */
object AudioDecoder {
    const val TARGET_RATE = 16000

    private class FloatBuf {
        var data = FloatArray(1 shl 20)
        var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }

    /**
     * [onProgress] recebe de 0 a 100. [checkCancelled] deve lançar uma exceção de
     * cancelamento se o trabalho foi interrompido.
     */
    fun decode(
        context: Context,
        source: AudioSource,
        onProgress: (Int) -> Unit,
        checkCancelled: () -> Unit
    ): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (source.file != null) extractor.setDataSource(source.file.absolutePath)
            else extractor.setDataSource(context, source.uri ?: throw IOException("sem origem"), null)

            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) throw IOException("O arquivo não tem faixa de áudio.")
            extractor.selectTrack(track)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("formato desconhecido")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            var inRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            val out = FloatBuf()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            // Reamostragem linear em fluxo: srcPos é a posição (em amostras de entrada)
            // da próxima amostra de saída.
            var ratio = inRate.toDouble() / TARGET_RATE
            var srcPos = 0.0
            var n = 0L
            var last = 0f
            var lastPct = -1

            while (!outputDone) {
                checkCancelled()
                if (!inputDone) {
                    val ib = dec.dequeueInputBuffer(10_000)
                    if (ib >= 0) {
                        val buf = dec.getInputBuffer(ib) ?: throw IOException("buffer indisponível")
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(ib, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val ob = dec.dequeueOutputBuffer(info, 10_000)
                if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat
                    inRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    ratio = inRate.toDouble() / TARGET_RATE
                } else if (ob >= 0) {
                    val buf = dec.getOutputBuffer(ob)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = shorts.remaining() / channels
                        var idx = 0
                        for (fr in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts.get(idx++).toInt()
                            val x = sum / channels / 32768f
                            while (srcPos <= n) {
                                val frac = (srcPos - (n - 1)).toFloat()
                                out.add(last + (x - last) * frac)
                                srcPos += ratio
                            }
                            last = x
                            n++
                        }
                    }
                    dec.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (durationUs > 0) {
                        val pct = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            return out.toArray()
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            extractor.release()
        }
    }
}
