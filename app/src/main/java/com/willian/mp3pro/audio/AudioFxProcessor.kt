package com.willian.mp3pro.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Processador de áudio próprio, aplicado ao PCM de 16 bits antes da saída.
 *
 * Cadeia: pré-amplificador -> 6 filtros peaking (equalizador) -> shelf de graves
 * -> shelf de agudos -> alargamento estéreo -> ganho de loudness -> limitador suave.
 *
 * Como os filtros são calculados aqui (e não pelo Equalizer nativo do Android),
 * o resultado é idêntico em qualquer aparelho.
 */
@UnstableApi
class AudioFxProcessor : BaseAudioProcessor() {

    private companion object {
        const val MAX_CH = 8
        const val N_EQ = FxBands.COUNT
        const val IDX_LOW = N_EQ        // shelf de graves
        const val IDX_HIGH = N_EQ + 1   // shelf de agudos
        const val N_BQ = N_EQ + 2
        const val Q_EQ = 1.0
        const val LOW_SHELF_HZ = 110.0
        const val HIGH_SHELF_HZ = 9000.0
        const val CLIP_THRESHOLD = 0.85
    }

    private var sampleRate = 44100
    private var channels = 2

    // coeficientes normalizados: b0, b1, b2, a1, a2
    private val coef = Array(N_BQ) { DoubleArray(5) }
    private val active = BooleanArray(N_BQ)
    private val z1 = Array(MAX_CH) { DoubleArray(N_BQ) }
    private val z2 = Array(MAX_CH) { DoubleArray(N_BQ) }

    private var applied: FxSettings? = null
    private var enabled = true
    private var preGain = 1.0
    private var loudGain = 1.0
    private var width = 1.0
    private var anyFilter = false

    private val frame = DoubleArray(MAX_CH)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Só processa PCM 16 bits; outros formatos passam direto (processador inativo).
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioFormat.NOT_SET
        if (inputAudioFormat.channelCount !in 1..MAX_CH) return AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        applied = null
        return inputAudioFormat
    }

    override fun onFlush() {
        for (c in 0 until MAX_CH) {
            z1[c].fill(0.0)
            z2[c].fill(0.0)
        }
        applied = null
    }

    override fun onReset() {
        applied = null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = 2 * channels
        val frames = inputBuffer.remaining() / bytesPerFrame
        if (frames == 0) return

        refreshSettings()

        val out = replaceOutputBuffer(frames * bytesPerFrame)
        val start = inputBuffer.position()
        inputBuffer.order(ByteOrder.nativeOrder())

        if (!enabled) {
            for (i in 0 until frames * channels) {
                out.putShort(inputBuffer.getShort(start + i * 2))
            }
        } else {
            for (f in 0 until frames) {
                val base = start + f * bytesPerFrame
                for (c in 0 until channels) {
                    frame[c] = inputBuffer.getShort(base + c * 2) / 32768.0 * preGain
                }
                if (anyFilter) {
                    for (c in 0 until channels) frame[c] = filterChannel(c, frame[c])
                }
                if (channels == 2 && width != 1.0) {
                    val mid = (frame[0] + frame[1]) * 0.5
                    val side = (frame[0] - frame[1]) * 0.5 * width
                    frame[0] = mid + side
                    frame[1] = mid - side
                }
                for (c in 0 until channels) {
                    val y = softClip(frame[c] * loudGain)
                    val s = (y * 32767.0).toInt().coerceIn(-32768, 32767)
                    out.putShort(s.toShort())
                }
            }
        }

        inputBuffer.position(start + frames * bytesPerFrame)
        out.flip()
    }

    private fun filterChannel(c: Int, input: Double): Double {
        var x = input
        val s1 = z1[c]
        val s2 = z2[c]
        for (i in 0 until N_BQ) {
            if (!active[i]) continue
            val k = coef[i]
            val y = k[0] * x + s1[i]
            s1[i] = k[1] * x - k[3] * y + s2[i]
            s2[i] = k[2] * x - k[4] * y
            x = y
        }
        return x
    }

    /** Compressão suave acima do limiar; abaixo dele o sinal passa intacto. */
    private fun softClip(x: Double): Double {
        val a = abs(x)
        if (a <= CLIP_THRESHOLD) return x
        val range = 1.0 - CLIP_THRESHOLD
        val y = CLIP_THRESHOLD + range * tanh((a - CLIP_THRESHOLD) / range)
        return if (x < 0) -y else y
    }

    private fun refreshSettings() {
        val s = FxStore.state.value
        if (s === applied) return
        applied = s
        enabled = s.enabled

        // Ganhos efetivos (dB) de cada filtro
        val gains = DoubleArray(N_BQ)
        for (i in 0 until N_EQ) gains[i] = s.bands.getOrElse(i) { 0f }.toDouble()
        gains[IDX_LOW] = s.bass * 10.0 + s.loudness * 4.0
        gains[IDX_HIGH] = s.clarity * 8.0 + s.loudness * 3.0

        val maxPositive = max(0.0, gains.max())
        val preDb = s.preamp - 0.6 * maxPositive          // compensação automática
        preGain = 10.0.pow(preDb / 20.0)
        loudGain = 10.0.pow(s.loudness * 9.0 / 20.0)      // até +9 dB
        width = 1.0 + s.surround * 1.2                    // até 2,2x

        val nyquistLimit = sampleRate * 0.45
        anyFilter = false
        for (i in 0 until N_BQ) {
            val g = gains[i]
            active[i] = abs(g) > 0.05
            if (!active[i]) continue
            anyFilter = true
            coef[i] = when (i) {
                IDX_LOW -> lowShelf(LOW_SHELF_HZ, g)
                IDX_HIGH -> highShelf(HIGH_SHELF_HZ.coerceAtMost(nyquistLimit), g)
                else -> peaking(FxBands.freqs[i].toDouble().coerceAtMost(nyquistLimit), Q_EQ, g)
            }
        }
    }

    private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    private fun peaking(f: Double, q: Double, gainDb: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        return norm(
            1.0 + alpha * a, -2.0 * cosW, 1.0 - alpha * a,
            1.0 + alpha / a, -2.0 * cosW, 1.0 - alpha / a
        )
    }

    private fun lowShelf(f: Double, gainDb: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val t = 2.0 * sqrt(a) * alpha
        return norm(
            a * ((a + 1) - (a - 1) * cosW + t),
            2.0 * a * ((a - 1) - (a + 1) * cosW),
            a * ((a + 1) - (a - 1) * cosW - t),
            (a + 1) + (a - 1) * cosW + t,
            -2.0 * ((a - 1) + (a + 1) * cosW),
            (a + 1) + (a - 1) * cosW - t
        )
    }

    private fun highShelf(f: Double, gainDb: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val t = 2.0 * sqrt(a) * alpha
        return norm(
            a * ((a + 1) + (a - 1) * cosW + t),
            -2.0 * a * ((a - 1) + (a + 1) * cosW),
            a * ((a + 1) + (a - 1) * cosW - t),
            (a + 1) - (a - 1) * cosW + t,
            2.0 * ((a - 1) - (a + 1) * cosW),
            (a + 1) - (a - 1) * cosW - t
        )
    }
}
