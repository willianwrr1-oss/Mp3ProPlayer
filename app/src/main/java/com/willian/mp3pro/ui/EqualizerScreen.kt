package com.willian.mp3pro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.audio.FxBands
import com.willian.mp3pro.audio.FxPresets
import com.willian.mp3pro.audio.FxSettings
import com.willian.mp3pro.audio.FxStore
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen() {
    val fx by FxStore.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Equalizador") },
            actions = {
                Switch(
                    checked = fx.enabled,
                    onCheckedChange = { on -> FxStore.update { it.copy(enabled = on) } },
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FxPresets.all, key = { it.name }) { preset ->
                    FilterChip(
                        selected = fx.presetName == preset.name,
                        onClick = { FxStore.applyPreset(preset) },
                        label = { Text(preset.name) }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0 until FxBands.COUNT) {
                        BandColumn(fx, i, enabled = fx.enabled)
                    }
                }
            }

            SectionTitle("Pré-amplificador")
            LabeledSlider(
                label = "Ganho geral",
                valueText = formatDb(fx.preamp),
                value = fx.preamp,
                range = -12f..6f,
                enabled = fx.enabled
            ) { v -> FxStore.update { it.copy(preamp = (v * 2).roundToInt() / 2f) } }

            SectionTitle("Efeitos")
            EffectSlider("Loudness", fx.loudness, fx.enabled) { v ->
                FxStore.update { it.copy(loudness = v, presetName = "Personalizado") }
            }
            EffectSlider("Graves profundos", fx.bass, fx.enabled) { v ->
                FxStore.update { it.copy(bass = v, presetName = "Personalizado") }
            }
            EffectSlider("Espacial (alargamento estéreo)", fx.surround, fx.enabled) { v ->
                FxStore.update { it.copy(surround = v, presetName = "Personalizado") }
            }
            EffectSlider("Clareza (agudos)", fx.clarity, fx.enabled) { v ->
                FxStore.update { it.copy(clarity = v, presetName = "Personalizado") }
            }

            OutlinedButton(
                onClick = { FxStore.applyPreset(FxPresets.all.first()); FxStore.update { it.copy(preamp = 0f) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restaurar tudo ao padrão") }

            Text(
                "Os efeitos são processados pelo próprio app, então soam igual em qualquer aparelho. " +
                    "O áudio é limitado suavemente para evitar distorção quando há muito ganho.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatDb(v: Float): String {
    val r = (v * 10).roundToInt() / 10f
    return (if (r > 0) "+" else "") + (if (r % 1f == 0f) r.toInt().toString() else r.toString()) + " dB"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BandColumn(fx: FxSettings, index: Int, enabled: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Text(
            formatDb(fx.bands[index]).removeSuffix(" dB"),
            fontSize = 12.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        BandSlider(
            value = fx.bands[index],
            enabled = enabled,
            onChange = { v -> FxStore.update { it.withBand(index, v) } },
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(44.dp)
                .height(210.dp)
        )
        Text(
            FxBands.labels[index], fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Slider vertical desenhado à mão: arraste ou toque para definir o ganho (-12 a +12 dB). */
@Composable
private fun BandSlider(
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.outline
    var heightPx by remember { mutableFloatStateOf(1f) }
    val currentOnChange by rememberUpdatedState(onChange)
    val isEnabled by rememberUpdatedState(enabled)

    fun fromY(y: Float): Float {
        val margin = 14f
        val usable = (heightPx - 2 * margin).coerceAtLeast(1f)
        val frac = 1f - ((y - margin) / usable).coerceIn(0f, 1f)
        val db = FxBands.MIN_DB + frac * (FxBands.MAX_DB - FxBands.MIN_DB)
        return (db * 2).roundToInt() / 2f
    }

    Canvas(
        modifier
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset -> if (isEnabled) currentOnChange(fromY(offset.y)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> if (isEnabled) currentOnChange(fromY(offset.y)) },
                    onVerticalDrag = { change, _ ->
                        if (isEnabled) currentOnChange(fromY(change.position.y))
                    }
                )
            }
    ) {
        val margin = 14f
        val usable = size.height - 2 * margin
        val cx = size.width / 2f
        val trackW = 6.dp.toPx()
        val frac = (value - FxBands.MIN_DB) / (FxBands.MAX_DB - FxBands.MIN_DB)
        val thumbY = margin + (1f - frac) * usable
        val midY = margin + usable / 2f

        drawRoundRect(
            color = track,
            topLeft = Offset(cx - trackW / 2, margin),
            size = Size(trackW, usable),
            cornerRadius = CornerRadius(trackW / 2)
        )
        // Preenchimento entre o zero e o valor atual
        val top = minOf(thumbY, midY)
        val bottom = maxOf(thumbY, midY)
        drawRoundRect(
            color = primary,
            topLeft = Offset(cx - trackW / 2, top),
            size = Size(trackW, (bottom - top).coerceAtLeast(1f)),
            cornerRadius = CornerRadius(trackW / 2)
        )
        // Marca do zero
        drawLine(
            color = tick,
            start = Offset(cx - 14.dp.toPx(), midY),
            end = Offset(cx + 14.dp.toPx(), midY),
            strokeWidth = 1.5.dp.toPx()
        )
        drawCircle(color = primary, radius = 11.dp.toPx(), center = Offset(cx, thumbY))
        drawCircle(color = Color(0xFF0E1620), radius = 4.dp.toPx(), center = Offset(cx, thumbY))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}

@Composable
private fun EffectSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    LabeledSlider(
        label = label,
        valueText = "${(value * 100).roundToInt()}%",
        value = value,
        range = 0f..1f,
        enabled = enabled,
        onChange = onChange
    )
}
