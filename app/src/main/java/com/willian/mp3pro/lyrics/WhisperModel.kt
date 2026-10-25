package com.willian.mp3pro.lyrics

import android.content.Context
import com.willian.mp3pro.data.Http
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Tamanhos do modelo Whisper. Maior = mais preciso, mas mais lento e mais pesado. */
enum class WhisperSize(val id: String, val label: String, val downloadMb: Int) {
    TINY("tiny", "Leve (tiny)", 115),
    BASE("base", "Equilibrado (base)", 200);

    companion object {
        fun fromId(id: String): WhisperSize = entries.firstOrNull { it.id == id } ?: BASE
    }
}

/**
 * Modelo de reconhecimento de voz (Whisper) guardado no celular. É baixado uma única
 * vez, do repositório oficial do sherpa-onnx no GitHub, e depois funciona sem internet.
 */
object WhisperModel {
    private const val BASE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-"

    fun dir(context: Context, size: WhisperSize): File =
        File(context.applicationContext.filesDir, "whisper/${size.id}")

    fun encoder(context: Context, size: WhisperSize) = File(dir(context, size), "encoder.onnx")
    fun decoder(context: Context, size: WhisperSize) = File(dir(context, size), "decoder.onnx")
    fun tokens(context: Context, size: WhisperSize) = File(dir(context, size), "tokens.txt")

    fun isReady(context: Context, size: WhisperSize): Boolean =
        encoder(context, size).length() > 0 && decoder(context, size).length() > 0 &&
            tokens(context, size).length() > 0

    fun delete(context: Context, size: WhisperSize) {
        dir(context, size).deleteRecursively()
    }

    /** Conta quantos bytes já passaram, para mostrar o progresso do download. */
    private class Counting(input: InputStream, val onBytes: (Long) -> Unit) : FilterInputStream(input) {
        private var total = 0L
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) onBytes(++total)
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                total += n
                onBytes(total)
            }
            return n
        }
    }

    /**
     * Baixa o pacote (.tar.bz2) e extrai só o que o app usa (versão int8, menor e mais rápida).
     * [onProgress] recebe de 0 a 100.
     */
    @Suppress("DEPRECATION")
    fun download(
        context: Context,
        size: WhisperSize,
        onProgress: (Int) -> Unit,
        checkCancelled: () -> Unit
    ) {
        val target = dir(context, size)
        val tmp = File(target.parentFile, size.id + ".tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()

        val url = "$BASE_URL${size.id}.tar.bz2"
        val req = Request.Builder().url(url).build()
        try {
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Falha ao baixar o modelo (HTTP ${resp.code}).")
                val body = resp.body ?: throw IOException("Resposta vazia ao baixar o modelo.")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: (size.downloadMb * 1024L * 1024L)
                var lastPct = -1
                val counted = Counting(body.byteStream()) { read ->
                    val pct = (read * 100 / totalBytes).toInt().coerceIn(0, 99)
                    if (pct != lastPct) {
                        lastPct = pct
                        onProgress(pct)
                    }
                }
                val tar = TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counted, 1 shl 16)))
                var got = 0
                val buf = ByteArray(1 shl 16)
                while (got < 3) {
                    checkCancelled()
                    val entry = tar.nextTarEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    val dest = when {
                        name.endsWith("encoder.int8.onnx") -> "encoder.onnx"
                        name.endsWith("decoder.int8.onnx") -> "decoder.onnx"
                        name.endsWith("tokens.txt") -> "tokens.txt"
                        else -> null
                    } ?: continue
                    File(tmp, dest).outputStream().use { o ->
                        while (true) {
                            checkCancelled()
                            val n = tar.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                        }
                    }
                    got++
                }
                if (got < 3) throw IOException("O pacote do modelo veio incompleto.")
            }
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar o modelo.")
            onProgress(100)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
