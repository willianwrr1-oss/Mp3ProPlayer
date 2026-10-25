package com.willian.mp3pro.ui.skins

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.mp3pro.player.PlayerUi
import com.willian.mp3pro.ui.formatTime

/**
 * Gravador de rolo: dois carretéis grandes giram em sentido anti-horário e a fita passa de
 * um para o outro. Os dois mostradores analógicos são funcionais: o da esquerda marca o
 * tempo decorrido e o da direita o que falta. As teclas funcionam como as do cassete.
 */
@Composable
fun ReelDeckSkin(
    ui: PlayerUi,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spin = rememberSpin(ui.isPlaying, 160f)
    val progress = animateFloatAsState(
        progressOf(ui), tween(500, easing = LinearEasing), label = "reel"
    )

    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val w = maxWidth

        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val p = progress.value
            val rot = -spin.floatValue

            // painel metálico escovado
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF5A5D63), Color(0xFF2E3034))),
                cornerRadius = CornerRadius(s * 0.05f)
            )
            for (k in 0 until 60) {
                val y = s * k / 60f
                drawLine(Color.White.copy(alpha = 0.018f), Offset(0f, y), Offset(s, y), 1f)
            }
            drawRoundRect(
                Color.White.copy(alpha = 0.08f),
                topLeft = Offset(s * 0.012f, s * 0.012f),
                size = Size(s * 0.976f, s * 0.976f),
                cornerRadius = CornerRadius(s * 0.045f),
                style = Stroke(s * 0.004f)
            )

            // carretéis
            val cy = s * 0.265f
            val rFlange = s * 0.20f
            val xl = s * 0.29f
            val xr = s * 0.71f
            val packL = s * (0.09f + 0.095f * (1f - p))
            val packR = s * (0.09f + 0.095f * p)

            fun reel(cx: Float, pack: Float) {
                val c = Offset(cx, cy)
                drawCircle(Color(0xFF111316), rFlange, c)
                drawCircle(Color(0xFF5A3C2B), pack, c)
                drawCircle(Color(0xFF3B271C), pack, c, style = Stroke(s * 0.004f))
                rotate(rot, c) {
                    for (i in 0 until 3) {
                        drawArc(
                            Color(0xFFC9CDD3), i * 120f - 20f, 40f, true,
                            Offset(c.x - rFlange * 0.96f, c.y - rFlange * 0.96f),
                            Size(rFlange * 1.92f, rFlange * 1.92f)
                        )
                    }
                    drawCircle(Color(0xFFD4D8DD), rFlange, c, style = Stroke(rFlange * 0.07f))
                    drawCircle(Color(0xFFB8BDC4), s * 0.07f, c)
                    for (i in 0 until 3) {
                        drawCircle(
                            Color(0xFF2A2C30), s * 0.012f,
                            polar(c, s * 0.043f, degToRad(i * 120f + 60f))
                        )
                    }
                }
                drawCircle(Color(0xFF2A2C30), s * 0.012f, c)
            }
            reel(xl, packL)
            reel(xr, packR)

            // fita até o cabeçote
            drawLine(
                Color(0xFF5A3C2B), Offset(xl, cy + packL), Offset(s * 0.44f, s * 0.51f),
                s * 0.006f, StrokeCap.Round
            )
            drawLine(
                Color(0xFF5A3C2B), Offset(xr, cy + packR), Offset(s * 0.56f, s * 0.51f),
                s * 0.006f, StrokeCap.Round
            )
            drawCircle(Color(0xFFB8BDC4), s * 0.011f, Offset(s * 0.41f, s * 0.47f))
            drawCircle(Color(0xFFB8BDC4), s * 0.011f, Offset(s * 0.59f, s * 0.47f))
            drawRoundRect(
                Color(0xFF1B1D20), topLeft = Offset(s * 0.44f, s * 0.495f),
                size = Size(s * 0.12f, s * 0.04f), cornerRadius = CornerRadius(s * 0.008f)
            )

            // mostradores analógicos
            fun gauge(c: Offset, fraction: Float) {
                val r = s * 0.105f
                drawCircle(Color.Black.copy(alpha = 0.45f), r + s * 0.012f, c + Offset(s * 0.004f, s * 0.008f))
                drawCircle(Color(0xFF1A1B1E), r + s * 0.012f, c)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFF3ECD8), Color(0xFFD6CDB2)), center = c, radius = r
                    ),
                    r, c
                )
                drawArc(
                    Color(0xFF2B2B2B), -130f, 80f, false,
                    Offset(c.x - r * 0.86f, c.y - r * 0.86f), Size(r * 1.72f, r * 1.72f),
                    style = Stroke(s * 0.003f)
                )
                for (i in 0..10) {
                    val a = degToRad(-130f + i * 8f)
                    val outer = if (i % 5 == 0) 0.98f else 0.92f
                    drawLine(
                        if (i >= 8) Color(0xFFC0392B) else Color(0xFF2B2B2B),
                        polar(c, r * 0.74f, a), polar(c, r * outer, a), s * 0.003f
                    )
                }
                val na = degToRad(-130f + 80f * fraction.coerceIn(0f, 1f))
                val tipN = polar(c, r * 0.92f, na)
                drawLine(
                    Color.Black.copy(alpha = 0.25f), c + Offset(s * 0.003f, s * 0.004f),
                    tipN + Offset(s * 0.003f, s * 0.004f), s * 0.005f, StrokeCap.Round
                )
                drawLine(Color(0xFFB3261E), c, tipN, s * 0.005f, StrokeCap.Round)
                drawCircle(Color(0xFF2B2B2B), s * 0.012f, c)
            }
            gauge(Offset(s * 0.27f, s * 0.665f), p)
            gauge(Offset(s * 0.73f, s * 0.665f), 1f - p)
        }

        // tempos dentro dos mostradores
        val faceW = w * 0.18f
        Text(
            formatTime(ui.positionMs),
            color = Color(0xFF2B2B2B), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = (w.value * 0.042f).sp,
            textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.offset(x = w * 0.27f - faceW / 2, y = w * 0.70f).width(faceW)
        )
        Text(
            "-" + formatTime((ui.durationMs - ui.positionMs).coerceAtLeast(0)),
            color = Color(0xFF2B2B2B), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = (w.value * 0.042f).sp,
            textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.offset(x = w * 0.73f - faceW / 2, y = w * 0.70f).width(faceW)
        )
        Text(
            "DECORRIDO",
            color = Color(0xFFD3D6DB), fontSize = (w.value * 0.03f).sp,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip,
            modifier = Modifier.offset(x = w * 0.27f - w * 0.1f, y = w * 0.785f).width(w * 0.2f)
        )
        Text(
            "RESTANTE",
            color = Color(0xFFD3D6DB), fontSize = (w.value * 0.03f).sp,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip,
            modifier = Modifier.offset(x = w * 0.73f - w * 0.1f, y = w * 0.785f).width(w * 0.2f)
        )

        // luz de estado, no meio dos dois mostradores
        Box(
            Modifier
                .offset(x = w * 0.405f, y = w * 0.625f)
                .size(w * 0.19f, w * 0.08f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0B0D0F))
                .border(1.dp, Color(0xFF50545B), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (ui.isPlaying) "PLAY" else if (ui.hasMedia) "PAUSA" else "STOP",
                color = if (ui.isPlaying) Color(0xFFFFA03A) else Color(0xFF6B4A24),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = (w.value * 0.04f).sp, maxLines = 1
            )
        }

        // teclas
        val keyW = w * 0.17f
        val keyH = w * 0.115f
        RetroKey(
            Icons.Filled.SkipPrevious, "Anterior", false, onPrevious,
            Modifier.offset(x = w * 0.225f, y = w * 0.835f).size(keyW, keyH)
        )
        RetroKey(
            if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (ui.isPlaying) "Pausar" else "Tocar", ui.isPlaying, onToggle,
            Modifier.offset(x = w * 0.415f, y = w * 0.835f).size(keyW, keyH)
        )
        RetroKey(
            Icons.Filled.SkipNext, "Próxima", false, onNext,
            Modifier.offset(x = w * 0.605f, y = w * 0.835f).size(keyW, keyH)
        )
    }
}
