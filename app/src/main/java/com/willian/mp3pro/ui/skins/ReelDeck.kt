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
import kotlin.math.sqrt
import com.willian.mp3pro.audio.AudioMeter
import com.willian.mp3pro.player.PlayerUi
import com.willian.mp3pro.ui.formatTime

/**
 * Gravador de rolo: dois carretéis grandes giram em sentido anti-horário e a fita passa de
 * um para o outro. À esquerda fica o medidor de nível (VU de LEDs, canais L e R, com marca
 * de pico) e à direita o analisador de espectro de 8 faixas; os dois reagem ao áudio que
 * está tocando. Abaixo de cada um, o tempo decorrido e o restante. Teclas como as do cassete.
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

    val meters = rememberMeters(ui.isPlaying)

    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val w = maxWidth

        Canvas(Modifier.fillMaxSize()) {
            meters.tick.longValue // lido aqui para redesenhar a cada quadro
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

            // A fita é desenhada ANTES dos carretéis: ela sai de baixo do disco do carretel
            // (sem atravessar a frente dele), passa pelo pino-guia e chega ao cabeçote.
            val tape = Color(0xFF6B4A36)
            val postL = Offset(s * 0.41f, s * 0.47f)
            val postR = Offset(s * 0.59f, s * 0.47f)
            val headL = Offset(s * 0.455f, s * 0.505f)
            val headR = Offset(s * 0.545f, s * 0.505f)
            fun tapeRun(c: Offset, pack: Float, post: Offset, head: Offset) {
                val dx = post.x - c.x
                val dy = post.y - c.y
                val d = sqrt(dx * dx + dy * dy)
                val from = Offset(c.x + dx / d * pack, c.y + dy / d * pack)
                drawLine(tape, from, post, s * 0.008f, StrokeCap.Round)
                drawLine(tape, post, head, s * 0.008f, StrokeCap.Round)
            }
            tapeRun(Offset(xl, cy), packL, postL, headL)
            tapeRun(Offset(xr, cy), packR, postR, headR)

            fun reel(cx: Float, pack: Float) {
                val c = Offset(cx, cy)
                drawCircle(Color.Black.copy(alpha = 0.35f), rFlange * 1.03f, c + Offset(s * 0.004f, s * 0.008f))
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

            // pinos-guia e cabeçote
            drawCircle(Color(0xFFB8BDC4), s * 0.012f, postL)
            drawCircle(Color(0xFFB8BDC4), s * 0.012f, postR)
            drawRoundRect(
                Color(0xFF1B1D20), topLeft = Offset(s * 0.44f, s * 0.495f),
                size = Size(s * 0.12f, s * 0.04f), cornerRadius = CornerRadius(s * 0.008f)
            )

            // ---- medidores de áudio ------------------------------------------------
            val lv = meters.levels

            fun segColor(frac: Float, lit: Boolean): Color {
                val base = when {
                    frac < 0.62f -> Color(0xFF3DDC84)
                    frac < 0.85f -> Color(0xFFFFC53D)
                    else -> Color(0xFFFF4D3D)
                }
                return if (lit) base else base.copy(alpha = 0.14f)
            }

            fun panel(x0: Float) {
                val y0 = s * 0.575f
                val pw = s * 0.31f
                val ph = s * 0.18f
                val r = CornerRadius(s * 0.018f)
                drawRoundRect(
                    Color.Black.copy(alpha = 0.4f), Offset(x0 + s * 0.004f, y0 + s * 0.008f),
                    Size(pw, ph), r
                )
                drawRoundRect(Color(0xFF0B0C0E), Offset(x0, y0), Size(pw, ph), r)
                drawRoundRect(
                    Color(0xFF4A4D53), Offset(x0, y0), Size(pw, ph), r, style = Stroke(s * 0.004f)
                )
            }

            // esquerda: VU de LEDs (L em cima, R embaixo), com marca de pico
            val xPanelL = s * 0.085f
            panel(xPanelL)
            run {
                val segs = 16
                val x0 = xPanelL + s * 0.05f
                val width = s * 0.31f - s * 0.07f
                val pitch = width / segs
                val rowH = s * 0.045f
                for (row in 0..1) {
                    val y = s * 0.575f + s * 0.0325f + row * s * 0.07f
                    val level = lv[row]
                    val peakSeg = (meters.peaks[row] * segs).toInt().coerceIn(0, segs - 1)
                    for (i in 0 until segs) {
                        val frac = (i + 1f) / segs
                        val lit = level >= frac - 0.5f / segs || (i == peakSeg && meters.peaks[row] > 0.03f)
                        drawRect(
                            segColor(frac, lit),
                            Offset(x0 + i * pitch, y),
                            Size(pitch * 0.78f, rowH)
                        )
                    }
                }
            }

            // direita: analisador de espectro, 8 faixas x 8 LEDs
            val xPanelR = s * 0.605f
            panel(xPanelR)
            run {
                val rows = 8
                val x0 = xPanelR + s * 0.02f
                val colPitch = (s * 0.31f - s * 0.04f) / AudioMeter.BANDS
                val top = s * 0.575f + s * 0.025f
                val rowPitch = s * 0.13f / rows
                for (b in 0 until AudioMeter.BANDS) {
                    val level = lv[2 + b]
                    for (i in 0 until rows) {
                        val frac = (i + 1f) / rows
                        val lit = level >= frac - 0.5f / rows
                        drawRect(
                            segColor(frac, lit),
                            Offset(x0 + b * colPitch, top + (rows - 1 - i) * rowPitch),
                            Size(colPitch * 0.78f, rowPitch * 0.78f)
                        )
                    }
                }
            }
        }

        // letras dos canais do VU
        val letterSize = (w.value * 0.032f).sp
        Text(
            "L", color = Color(0xFFD3D6DB), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = letterSize,
            modifier = Modifier.offset(x = w * 0.1f, y = w * 0.61f)
        )
        Text(
            "R", color = Color(0xFFD3D6DB), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = letterSize,
            modifier = Modifier.offset(x = w * 0.1f, y = w * 0.68f)
        )

        // tempos, em estilo de visor de LED, abaixo de cada medidor
        val timeSize = (w.value * 0.034f).sp
        val timeW = w * 0.34f
        Text(
            "DECORRIDO " + formatTime(ui.positionMs),
            color = Color(0xFFFFA03A), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = timeSize,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip,
            modifier = Modifier.offset(x = w * 0.24f - timeW / 2, y = w * 0.77f).width(timeW)
        )
        Text(
            "RESTANTE -" + formatTime((ui.durationMs - ui.positionMs).coerceAtLeast(0)),
            color = Color(0xFFFFA03A), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = timeSize,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip,
            modifier = Modifier.offset(x = w * 0.76f - timeW / 2, y = w * 0.77f).width(timeW)
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
