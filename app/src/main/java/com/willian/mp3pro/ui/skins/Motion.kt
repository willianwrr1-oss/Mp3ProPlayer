package com.willian.mp3pro.ui.skins

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.willian.mp3pro.player.PlayerUi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ângulo (em graus) de algo que gira enquanto a música toca. Acelera suavemente ao
 * dar play e desacelera aos poucos ao pausar, como um motor de verdade. O valor muda
 * a cada quadro, então deve ser lido só dentro do desenho (Canvas), sem recompor a tela.
 */
@Composable
fun rememberSpin(playing: Boolean, degreesPerSecond: Float): FloatState {
    val angle = remember { mutableFloatStateOf(0f) }
    val speed = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        launch {
            speed.animateTo(
                if (playing) 1f else 0f,
                tween(if (playing) 900 else 1800, easing = LinearOutSlowInEasing)
            )
        }
        var last = 0L
        while (isActive && (playing || speed.value > 0.003f)) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    angle.floatValue = (angle.floatValue + dt * degreesPerSecond * speed.value) % 360f
                }
                last = now
            }
        }
    }
    return angle
}

internal fun degToRad(deg: Float): Float = deg * (PI.toFloat() / 180f)

internal fun polar(c: Offset, r: Float, radians: Float): Offset =
    Offset(c.x + r * cos(radians), c.y + r * sin(radians))

/** Quanto da música já tocou, de 0 a 1. */
internal fun progressOf(ui: PlayerUi): Float =
    if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f

/** Cor da etiqueta do disco: muda a cada música, de forma estável. */
internal fun labelColor(seed: String): Color {
    val hue = (seed.hashCode().toLong() and 0x7fffffffL) % 360L
    return Color.hsv(hue.toFloat(), 0.55f, 0.85f)
}
