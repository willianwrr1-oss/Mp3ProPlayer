package com.willian.mp3pro.audio

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Medidor do áudio que está passando pelo player: nível dos canais esquerdo e direito e
 * 8 faixas de frequência. Quem escreve é o processador de áudio; quem lê são os
 * mostradores do visual "Gravador de rolo". Valores de 0 a 1.
 */
object AudioMeter {
    const val BANDS = 8

    @Volatile var left = 0f
    @Volatile var right = 0f
    val bands = FloatArray(BANDS)

    private val freqs = floatArrayOf(60f, 150f, 400f, 1000f, 2400f, 5000f, 9000f, 14000f)
    private const val WINDOW = 1024
    private val mono = FloatArray(WINDOW)

    /** Analisa [frames] quadros PCM de 16 bits a partir de [start] (leitura absoluta, sem mover o buffer). */
    fun analyze(buf: ByteBuffer, start: Int, frames: Int, channels: Int, sampleRate: Int) {
        if (frames <= 0 || channels <= 0) return
        val bytesPerFrame = 2 * channels

        var pl = 0
        var pr = 0
        for (f in 0 until frames) {
            val base = start + f * bytesPerFrame
            val l = abs(buf.getShort(base).toInt())
            val r = if (channels > 1) abs(buf.getShort(base + 2).toInt()) else l
            if (l > pl) pl = l
            if (r > pr) pr = r
        }
        left = pl / 32768f
        right = pr / 32768f

        // Espectro: algoritmo de Goertzel sobre os últimos quadros (janela de Hann).
        val n = minOf(frames, WINDOW)
        if (n < 64) return
        val first = frames - n
        for (i in 0 until n) {
            val base = start + (first + i) * bytesPerFrame
            var sum = 0
            for (c in 0 until channels) sum += buf.getShort(base + c * 2).toInt()
            val w = 0.5f - 0.5f * cos(2.0 * PI * i / (n - 1)).toFloat()
            mono[i] = sum / channels / 32768f * w
        }
        for (b in 0 until BANDS) {
            val f = freqs[b]
            if (f >= sampleRate * 0.45f) {
                bands[b] = 0f
                continue
            }
            val k = 2.0 * cos(2.0 * PI * f / sampleRate)
            var s1 = 0.0
            var s2 = 0.0
            for (i in 0 until n) {
                val s0 = mono[i] + k * s1 - s2
                s2 = s1
                s1 = s0
            }
            val power = max(0.0, s1 * s1 + s2 * s2 - k * s1 * s2)
            val amp = 4.0 * sqrt(power) / n
            // Faixa de -62 a -12 dB; o "tilt" compensa a queda natural dos agudos na música.
            val db = 20.0 * log10(amp + 1e-9) + 2.5 * b
            bands[b] = ((db + 62.0) / 50.0).toFloat().coerceIn(0f, 1f)
        }
    }
}
