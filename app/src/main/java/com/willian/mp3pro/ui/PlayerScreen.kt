package com.willian.mp3pro.ui

import androidx.activity.compose.BackHandler
import android.content.Context
import android.media.AudioManager
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import com.willian.mp3pro.data.AppPrefs
import com.willian.mp3pro.ui.skins.PlayerSkin
import com.willian.mp3pro.ui.skins.SkinStage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.player.PlayerController

@Composable
fun PlayerScreen(controller: PlayerController, onClose: () -> Unit) {
    val ui by controller.ui.collectAsStateWithLifecycle()
    BackHandler { onClose() }

    val context = LocalContext.current
    var skin by remember { mutableStateOf(PlayerSkin.fromId(AppPrefs.skin(context))) }
    var skinMenu by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = ui.durationMs.coerceAtLeast(1L).toFloat()
    val position = if (dragging) dragValue else ui.positionMs.toFloat().coerceIn(0f, duration)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Fechar", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { themeMenu = true }) {
                    Icon(
                        Icons.Filled.Brightness6, contentDescription = "Trocar entre visual escuro e claro",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Visual escuro (noturno)") },
                        leadingIcon = {
                            if (ThemeState.dark) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            ThemeState.set(context, true)
                            themeMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Visual claro") },
                        leadingIcon = {
                            if (!ThemeState.dark) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            ThemeState.set(context, false)
                            themeMenu = false
                        }
                    )
                }
            }
            Box {
                IconButton(onClick = { skinMenu = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = "Trocar o visual do player", tint = MaterialTheme.colorScheme.onBackground)
                }
                DropdownMenu(expanded = skinMenu, onDismissRequest = { skinMenu = false }) {
                    PlayerSkin.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (option == skin) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                skin = option
                                AppPrefs.setSkin(context, option.id)
                                skinMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SkinStage(
            skin = skin,
            ui = ui,
            onPrevious = { controller.previous() },
            onToggle = { controller.togglePlay() },
            onNext = { controller.next() },
            modifier = Modifier.fillMaxWidth(0.92f)
        )

        Spacer(Modifier.height(28.dp))
        Text(
            ui.title.ifEmpty { "Nada tocando" },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        if (ui.artist.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                ui.artist, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(20.dp))
        Slider(
            value = position,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                controller.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..duration,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(ui.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle, contentDescription = "Aleatório",
                    tint = if (ui.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { controller.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { controller.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (ui.isPlaying) "Pausar" else "Tocar",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { controller.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Próxima", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = { controller.cycleRepeat() }) {
                Icon(
                    if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repetir",
                    tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        VolumeBar()
        Spacer(Modifier.height(24.dp))
    }
}

/** Barra de volume: controla o volume de mídia do aparelho, o mesmo dos botões físicos. */
@Composable
private fun VolumeBar() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var touching by remember { mutableStateOf(false) }

    // Acompanha mudanças feitas pelos botões físicos enquanto a tela está aberta.
    LaunchedEffect(Unit) {
        while (true) {
            if (!touching) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            delay(400)
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (volume <= 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = "Volume",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = volume,
            onValueChange = {
                touching = true
                volume = it
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
            },
            onValueChangeFinished = { touching = false },
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Volume máximo",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MiniPlayer(controller: PlayerController, onOpen: () -> Unit) {
    val ui by controller.ui.collectAsStateWithLifecycle()
    if (!ui.hasMedia) return

    val progress = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onOpen)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                if (ui.artist.isNotEmpty()) {
                    Text(
                        ui.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { controller.togglePlay() }) {
                Icon(
                    if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (ui.isPlaying) "Pausar" else "Tocar",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { controller.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Próxima", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
