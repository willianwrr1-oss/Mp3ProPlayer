package com.willian.mp3pro.lyrics

import java.io.File
import java.io.IOException

/**
 * Ponto de ligação com o reconhecedor de voz (Whisper). A biblioteca nativa
 * (sherpa-onnx) não está no Maven Central, então por enquanto este motor fica
 * desligado ([AVAILABLE] = false) e a letra vem do LRCLIB. Quando a biblioteca
 * for incluída ao projeto, só este arquivo precisa mudar.
 */
class WhisperEngine(
    @Suppress("UNUSED_PARAMETER") encoder: File,
    @Suppress("UNUSED_PARAMETER") decoder: File,
    @Suppress("UNUSED_PARAMETER") tokens: File,
    @Suppress("UNUSED_PARAMETER") language: String
) : AutoCloseable {

    companion object {
        const val AVAILABLE = false
    }

    init {
        if (!AVAILABLE) throw IOException("A transcrição por IA ainda não está disponível nesta versão.")
    }

    fun transcribe(@Suppress("UNUSED_PARAMETER") samples: FloatArray): String =
        throw IOException("A transcrição por IA ainda não está disponível nesta versão.")

    override fun close() {}
}
