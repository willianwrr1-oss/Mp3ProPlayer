package com.willian.mp3pro.ui.skins

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.willian.mp3pro.player.PlayerUi
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Toca-discos: o disco gira a 33⅓ rpm (200 graus por segundo) e o braço desce sobre
 * os sulcos e percorre o disco da borda até o centro conforme a música avança.
 * Ao pausar, o disco desacelera e o braço volta ao descanso. Tocar no disco dá play/pausa.
 */
@Composable
fun TurntableSkin(ui: PlayerUi, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val active = ui.hasMedia && (ui.isPlaying || ui.buffering)
    val spin = rememberSpin(ui.isPlaying, 200f)
    val onRecord = animateFloatAsState(
        if (active) 1f else 0f, tween(1000, easing = FastOutSlowInEasing), label = "arm"
    )
    val progress = animateFloatAsState(
        progressOf(ui), tween(500, easing = LinearEasing), label = "progress"
    )
    val labelCol = remember(ui.mediaId, ui.title) { labelColor(ui.mediaId + ui.title) }

    Canvas(
        modifier
            .aspectRatio(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
    ) {
        val s = size.minDimension
        val center = Offset(s * 0.43f, s * 0.54f)
        val rPlatter = s * 0.41f
        val rRecord = s * 0.375f

        // base de madeira
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color(0xFF4A3426), Color(0xFF2B1D14)),
                start = Offset.Zero, end = Offset(s, s)
            ),
            cornerRadius = CornerRadius(s * 0.05f)
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.08f),
            topLeft = Offset(s * 0.012f, s * 0.012f),
            size = Size(s * 0.976f, s * 0.976f),
            cornerRadius = CornerRadius(s * 0.045f),
            style = Stroke(s * 0.004f)
        )

        // prato metálico
        drawCircle(Color.Black.copy(alpha = 0.45f), rPlatter, center + Offset(s * 0.008f, s * 0.014f))
        drawCircle(
            Brush.sweepGradient(
                listOf(
                    Color(0xFFB9BEC6), Color(0xFF6E737B), Color(0xFFD7DBE1),
                    Color(0xFF6E737B), Color(0xFFB9BEC6)
                ),
                center
            ),
            rPlatter, center
        )

        // disco de vinil e sulcos (simétricos, não precisam girar)
        drawCircle(Color(0xFF0B0B0D), rRecord, center)
        var gr = rRecord * 0.42f
        var gi = 0
        while (gr < rRecord * 0.975f) {
            drawCircle(
                Color.White.copy(alpha = if (gi % 6 == 0) 0.10f else 0.035f),
                gr, center, style = Stroke(1f)
            )
            gr += rRecord * 0.012f
            gi++
        }
        for (band in listOf(0.55f, 0.70f, 0.85f)) {
            drawCircle(Color.Black.copy(alpha = 0.55f), rRecord * band, center, style = Stroke(s * 0.004f))
        }

        // partes que mostram o giro: pontos do estroboscópio e a etiqueta
        rotate(spin.floatValue, center) {
            val rs = (rRecord + rPlatter) / 2f
            for (i in 0 until 60) {
                drawCircle(
                    Color(0xFFE4E7EB).copy(alpha = if (i % 5 == 0) 0.95f else 0.55f),
                    s * 0.003f, polar(center, rs, degToRad(i * 6f))
                )
            }
            val rl = rRecord * 0.36f
            drawCircle(labelCol, rl, center)
            drawArc(
                Color.Black.copy(alpha = 0.20f), 0f, 180f, true,
                Offset(center.x - rl, center.y - rl), Size(rl * 2f, rl * 2f)
            )
            drawCircle(Color.White.copy(alpha = 0.55f), rl * 0.92f, center, style = Stroke(s * 0.003f))
            for (k in 0..2) {
                val y = center.y - rl * (0.30f + 0.19f * k)
                val half = rl * (0.55f - 0.12f * k)
                drawLine(
                    Color.Black.copy(alpha = 0.5f),
                    Offset(center.x - half, y), Offset(center.x + half, y),
                    s * 0.004f, StrokeCap.Round
                )
            }
            drawCircle(Color.White.copy(alpha = 0.85f), s * 0.006f, polar(center, rl * 0.7f, degToRad(120f)))
        }

        // reflexo da luz (fixo: o disco gira por baixo dele)
        drawCircle(
            Brush.sweepGradient(
                0f to Color.Transparent,
                0.07f to Color.White.copy(alpha = 0.13f),
                0.14f to Color.Transparent,
                0.5f to Color.Transparent,
                0.57f to Color.White.copy(alpha = 0.13f),
                0.64f to Color.Transparent,
                1f to Color.Transparent,
                center = center
            ),
            rRecord, center
        )
        drawCircle(Color(0xFFE5E7EB), s * 0.014f, center)
        drawCircle(Color(0xFF2A2C30), s * 0.006f, center)

        // ---- braço ----
        val pivot = Offset(s * 0.87f, s * 0.15f)
        val armLen = s * 0.60f
        val dx = center.x - pivot.x
        val dy = center.y - pivot.y
        val d = hypot(dx, dy)
        val base = atan2(dy, dx)

        // ângulo entre a linha pivô-centro e o braço para a agulha ficar a r do centro
        fun alphaFor(r: Float): Float {
            val c = ((armLen * armLen + d * d - r * r) / (2f * armLen * d)).coerceIn(-1f, 1f)
            return acos(c)
        }

        val aOuter = alphaFor(rRecord * 0.93f)
        val aInner = alphaFor(rRecord * 0.42f)
        val aRest = aOuter + 0.40f
        val aPlay = aOuter + (aInner - aOuter) * progress.value
        val alpha = aRest + (aPlay - aRest) * onRecord.value
        val theta = base - alpha

        val u = Offset(cos(theta), sin(theta))
        val perp = Offset(-u.y, u.x)
        val lift = 1f - onRecord.value
        val shadow = Offset(s * (0.006f + 0.012f * lift), s * (0.010f + 0.016f * lift))

        // apoio do braço
        val restTip = pivot + Offset(cos(base - aRest), sin(base - aRest)) * armLen
        drawCircle(Color(0xFF1B1D21), s * 0.034f, restTip)
        drawCircle(Color(0xFF9AA0A9), s * 0.022f, restTip)

        val tube = pivot + u * (armLen - s * 0.075f)
        val tip = pivot + u * armLen

        drawLine(Color.Black.copy(alpha = 0.30f), pivot + shadow, tip + shadow, s * 0.016f, StrokeCap.Round)

        // contrapeso
        drawLine(
            Color(0xFF1E2024), pivot - u * (s * 0.05f), pivot - u * (s * 0.17f),
            s * 0.062f, StrokeCap.Round
        )
        drawLine(
            Color(0xFF9AA0A9), pivot - u * (s * 0.06f) + perp * (s * 0.012f),
            pivot - u * (s * 0.16f) + perp * (s * 0.012f), s * 0.014f, StrokeCap.Round
        )

        // tubo, cabeça e agulha
        drawLine(Color(0xFFC8CDD4), pivot, tube, s * 0.014f, StrokeCap.Round)
        drawLine(
            Color.White.copy(alpha = 0.7f), pivot + perp * (s * 0.003f),
            tube + perp * (s * 0.003f), s * 0.004f, StrokeCap.Round
        )
        drawLine(Color(0xFF15171A), tube, tip, s * 0.032f, StrokeCap.Round)
        drawLine(
            Color(0xFFC8CDD4), tube + u * (s * 0.012f),
            tube + u * (s * 0.012f) + perp * (s * 0.045f), s * 0.008f, StrokeCap.Round
        )
        drawCircle(Color(0xFFE6E8EC), s * 0.006f, tip)

        // base do braço
        drawCircle(Color.Black.copy(alpha = 0.4f), s * 0.075f, pivot + shadow)
        drawCircle(Color(0xFF1B1D21), s * 0.075f, pivot)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFF0F2F5), Color(0xFF8E949D)),
                center = pivot, radius = s * 0.052f
            ),
            s * 0.052f, pivot
        )
        drawCircle(Color(0xFF2A2D32), s * 0.022f, pivot)

        // luz piloto
        val led = Offset(s * 0.06f, s * 0.93f)
        drawCircle(
            Color(0xFFFF4B3E).copy(alpha = 0.12f + 0.2f * onRecord.value), s * 0.026f, led
        )
        drawCircle(
            Color(0xFFFF4B3E).copy(alpha = 0.25f + 0.75f * onRecord.value), s * 0.011f, led
        )
    }
}
