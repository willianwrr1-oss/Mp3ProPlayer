package com.willian.mp3pro.lyrics

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * Envolve o reconhecedor de voz (Whisper, via sherpa-onnx). É o único arquivo do app
 * que conhece essa biblioteca.
 *
 * [language]: "" detecta sozinho; ou "pt", "en", "es"...
 */
class WhisperEngine(
    encoder: File,
    decoder: File,
    tokens: File,
    language: String
) : AutoCloseable {

    private val recognizer: OfflineRecognizer

    init {
        val whisper = OfflineWhisperModelConfig(
            encoder = encoder.absolutePath,
            decoder = decoder.absolutePath,
            language = language,
            task = "transcribe"
        )
        val model = OfflineModelConfig(
            whisper = whisper,
            tokens = tokens.absolutePath,
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        )
        recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = model))
    }

    /** Transcreve um trecho de até ~30 s (mono, 16 kHz). */
    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, AudioDecoder.TARGET_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }
}
