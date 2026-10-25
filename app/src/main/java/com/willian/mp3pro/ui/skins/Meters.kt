package com.willian.mp3pro.ui.skins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.willian.mp3pro.audio.AudioMeter
import kotlinx.coroutines.isActive

/**
 * Valores suavizados dos medidores de áudio. [levels] guarda L, R e as 8 faixas de
 * frequência (nessa ordem); [peaks] guarda a marca de pico de L e R. [tick] muda a cada
 * quadro: quem desenha lê o [tick] para se redesenhar sem recompor a tela.
 */
class MeterState {
    val tick: MutableLongState = mutableLongStateOf(0L)
    val levels = FloatArray(2 + AudioMeter.BANDS)
    val peaks = FloatArray(2)
}

/** Atualiza os medidores a cada quadro enquanto toca, com subida rápida e descida lenta. */
@Composable
fun rememberMeters(playing: Boolean): MeterState {
    val state = remember { MeterState() }
    LaunchedEffect(playing) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                val lv = state.levels
                for (i in lv.indices) {
                    val target = if (!playing) 0f else when (i) {
                        0 -> AudioMeter.left
                        1 -> AudioMeter.right
                        else -> AudioMeter.bands[i - 2]
                    }
                    val cur = lv[i]
                    lv[i] = if (target > cur) cur + (target - cur) * 0.65f
                    else maxOf(target, cur - dt * 1.4f)
                }
                for (c in 0..1) {
                    state.peaks[c] = maxOf(lv[c], state.peaks[c] - dt * 0.45f)
                }
                state.tick.longValue = state.tick.longValue + 1
            }
            if (!playing && state.levels.all { it < 0.004f } && state.peaks.all { it < 0.004f }) break
        }
    }
    return state
}
