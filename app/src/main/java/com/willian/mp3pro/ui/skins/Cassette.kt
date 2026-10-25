package com.willian.mp3pro.ui.skins

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.mp3pro.player.PlayerUi
import com.willian.mp3pro.ui.formatTime

/**
 * Toca-fitas: os dois carretéis giram e a fita passa de um lado para o outro conforme a
 * música avança (o carretel da esquerda esvazia, o da direita enche). As teclas funcionam
 * e a tecla de play fica afundada enquanto toca.
 */
@Composable
fun CassetteSkin(
    ui: PlayerUi,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spin = rememberSpin(ui.isPlaying, 140f)
    val progress = animateFloatAsState(
        progressOf(ui), tween(500, easing = LinearEasing), label = "tape"
    )

    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val w = maxWidth

        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val cy = s * 0.45f
            val p = progress.value
            val rot = -spin.floatValue

            // painel do aparelho
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF3B3E45), Color(0xFF1F2126))),
                cornerRadius = CornerRadius(s * 0.05f)
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.07f),
                topLeft = Offset(s * 0.012f, s * 0.012f),
                size = Size(s * 0.976f, s * 0.976f),
                cornerRadius = CornerRadius(s * 0.045f),
                style = Stroke(s * 0.004f)
            )

            // corpo da fita
            val bodyTL = Offset(s * 0.07f, s * 0.08f)
            val bodySize = Size(s * 0.86f, s * 0.55f)
            drawRoundRect(
                Color.Black.copy(alpha = 0.4f),
                topLeft = bodyTL + Offset(s * 0.006f, s * 0.012f),
                size = bodySize, cornerRadius = CornerRadius(s * 0.03f)
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF30333A), Color(0xFF17191D)),
                    startY = bodyTL.y, endY = bodyTL.y + bodySize.height
                ),
                topLeft = bodyTL, size = bodySize, cornerRadius = CornerRadius(s * 0.03f)
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.10f),
                topLeft = bodyTL, size = bodySize,
                cornerRadius = CornerRadius(s * 0.03f), style = Stroke(s * 0.003f)
            )

            // parafusos
            for (sx in listOf(0.10f, 0.90f)) {
                for (sy in listOf(0.11f, 0.60f)) {
                    drawCircle(Color(0xFFA9AEB6), s * 0.012f, Offset(s * sx, s * sy))
                    drawLine(
                        Color(0xFF2A2C30), Offset(s * sx - s * 0.008f, s * sy),
                        Offset(s * sx + s * 0.008f, s * sy), s * 0.003f
                    )
                }
            }

            // etiqueta
            drawRoundRect(
                Color(0xFFEFE8D8), topLeft = Offset(s * 0.13f, s * 0.115f),
                size = Size(s * 0.74f, s * 0.23f), cornerRadius = CornerRadius(s * 0.012f)
            )
            drawRect(
                Color(0xFFD9542B), topLeft = Offset(s * 0.13f, s * 0.115f),
                size = Size(s * 0.74f, s * 0.032f)
            )

            // janela da fita
            drawRoundRect(
                Color(0xFF0E1013), topLeft = Offset(s * 0.22f, s * 0.37f),
                size = Size(s * 0.56f, s * 0.16f), cornerRadius = CornerRadius(s * 0.02f)
            )

            // carretéis: o tamanho da fita enrolada acompanha a música
            val xl = s * 0.34f
            val xr = s * 0.66f
            val packL = s * (0.040f + 0.036f * (1f - p))
            val packR = s * (0.040f + 0.036f * p)

            fun reel(cx: Float, pack: Float) {
                val c = Offset(cx, cy)
                drawCircle(Color(0xFF4B3426), pack, c)
                drawCircle(Color(0xFF6A4A37), pack, c, style = Stroke(s * 0.003f))
                drawCircle(Color(0xFFDDE0E4), s * 0.034f, c)
                drawCircle(Color(0xFF14161A), s * 0.022f, c)
                rotate(rot, c) {
                    for (i in 0 until 6) {
                        val a = degToRad(i * 60f)
                        drawLine(
                            Color(0xFFDDE0E4), polar(c, s * 0.012f, a), polar(c, s * 0.023f, a),
                            s * 0.007f
                        )
                    }
                }
            }
            reel(xl, packL)
            reel(xr, packR)

            // fita esticada entre os carretéis
            drawLine(
                Color(0xFF3A281D), Offset(xl, cy + packL), Offset(xr, cy + packR),
                s * 0.005f, StrokeCap.Round
            )
            // brilho do plástico da janela
            drawRoundRect(
                Color.White.copy(alpha = 0.07f), topLeft = Offset(s * 0.22f, s * 0.37f),
                size = Size(s * 0.56f, s * 0.07f), cornerRadius = CornerRadius(s * 0.02f)
            )
            drawRoundRect(
                Color(0xFF50545B), topLeft = Offset(s * 0.22f, s * 0.37f),
                size = Size(s * 0.56f, s * 0.16f), cornerRadius = CornerRadius(s * 0.02f),
                style = Stroke(s * 0.004f)
            )

            // base trapezoidal com os furos do cabeçote
            val trap = Path().apply {
                moveTo(s * 0.24f, s * 0.63f)
                lineTo(s * 0.76f, s * 0.63f)
                lineTo(s * 0.70f, s * 0.555f)
                lineTo(s * 0.30f, s * 0.555f)
                close()
            }
            drawPath(trap, Color(0xFF3C4047))
            drawRoundRect(
                Color(0xFF14161A), topLeft = Offset(s * 0.34f, s * 0.585f),
                size = Size(s * 0.06f, s * 0.03f), cornerRadius = CornerRadius(s * 0.006f)
            )
            drawRoundRect(
                Color(0xFF14161A), topLeft = Offset(s * 0.60f, s * 0.585f),
                size = Size(s * 0.06f, s * 0.03f), cornerRadius = CornerRadius(s * 0.006f)
            )
            drawRoundRect(
                Color(0xFF14161A), topLeft = Offset(s * 0.46f, s * 0.575f),
                size = Size(s * 0.08f, s * 0.045f), cornerRadius = CornerRadius(s * 0.006f)
            )
        }

        // texto escrito à mão na etiqueta
        Box(Modifier.offset(x = w * 0.15f, y = w * 0.16f).width(w * 0.70f)) {
            Text(
                ui.title.ifEmpty { "Sem fita" },
                color = Color(0xFF2B2F45), fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold, fontSize = (w.value * 0.062f).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (ui.artist.isNotEmpty()) {
                Text(
                    ui.artist,
                    color = Color(0xFF5A5F6B), fontFamily = FontFamily.Cursive,
                    fontSize = (w.value * 0.046f).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(y = w * 0.075f)
                )
            }
        }

        // contador digital
        Box(
            Modifier
                .offset(x = w * 0.63f, y = w * 0.775f)
                .size(w * 0.30f, w * 0.14f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0B0D0F))
                .border(1.dp, Color(0xFF50545B), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                formatTime(ui.positionMs),
                color = Color(0xFFFFA03A), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = (w.value * 0.075f).sp
            )
        }

        // teclas
        val keyW = w * 0.17f
        val keyH = w * 0.14f
        RetroKey(
            Icons.Filled.SkipPrevious, "Anterior", false, onPrevious,
            Modifier.offset(x = w * 0.07f, y = w * 0.775f).size(keyW, keyH)
        )
        RetroKey(
            if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (ui.isPlaying) "Pausar" else "Tocar", ui.isPlaying, onToggle,
            Modifier.offset(x = w * 0.26f, y = w * 0.775f).size(keyW, keyH)
        )
        RetroKey(
            Icons.Filled.SkipNext, "Próxima", false, onNext,
            Modifier.offset(x = w * 0.45f, y = w * 0.775f).size(keyW, keyH)
        )
    }
}
