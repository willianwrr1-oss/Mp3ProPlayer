package com.willian.mp3pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.data.AppPrefs
import com.willian.mp3pro.lyrics.LyricsJobs
import com.willian.mp3pro.lyrics.LyricsStage
import com.willian.mp3pro.lyrics.LyricsStore
import com.willian.mp3pro.lyrics.WhisperEngine
import com.willian.mp3pro.lyrics.WhisperModel
import com.willian.mp3pro.lyrics.WhisperSize
import com.willian.mp3pro.player.PlayerUi

/**
 * Painel de letra do player: mostra a letra sincronizada com a linha atual em destaque,
 * ou o andamento da geração, ou o botão para gerar a letra com IA.
 */
@Composable
fun LyricsPanel(ui: PlayerUi, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by LyricsStore.version.collectAsStateWithLifecycle()
    val job by LyricsJobs.state.collectAsStateWithLifecycle()
    val lines = remember(ui.mediaId, version) { LyricsStore.load(ui.mediaId) }
    var options by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(20.dp)
    var follow by remember { mutableStateOf(false) }
    Column(
        modifier
            .then(if (lines != null) Modifier.height(440.dp) else Modifier.aspectRatio(1f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val mine = job.mediaId == ui.mediaId
        when {
            lines != null -> {
                // A letra inteira fica na tela; a linha atual só ganha destaque.
                val plain = LyricsStore.isPlain(lines)
                val position = ui.positionMs + 250
                val current = if (plain) -1 else lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
                val listState = rememberLazyListState()
                LaunchedEffect(current, lines, follow) {
                    if (follow && current >= 0) listState.animateScrollToItem((current - 3).coerceAtLeast(0))
                }
                if (plain) {
                    Text(
                        "Letra sem tempos: a linha atual não é destacada.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(lines) { i, line ->
                        val on = i == current
                        Text(
                            line.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !plain) { onSeek(line.timeMs) },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!plain) {
                        TextButton(onClick = { follow = !follow }) {
                            Text(if (follow) "Seguindo a música" else "Seguir a música")
                        }
                    }
                    TextButton(onClick = { options = true }) { Text("Opções") }
                    TextButton(onClick = { LyricsStore.delete(ui.mediaId) }) { Text("Apagar") }
                }
            }

            mine && job.running -> {
                Spacer(Modifier.weight(1f))
                Text(job.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { job.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("${job.percent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mantenha o app aberto. A letra fica salva no celular para as próximas vezes.",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { LyricsJobs.cancel() }) { Text("Cancelar") }
                Spacer(Modifier.weight(1f))
            }

            else -> {
                Spacer(Modifier.weight(1f))
                if (mine && job.stage == LyricsStage.FAILED) {
                    Text(job.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                } else {
                    Text(
                        "Sem letra para esta música.",
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "O app busca a letra em serviços gratuitos (LRCLIB e NetEase, com tempos; lyrics.ovh, só texto) e guarda no " +
                            "celular. Depois disso ela abre mesmo sem internet. Funciona melhor quando o " +
                            "arquivo se chama “Título - Artista”.",
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Button(
                    enabled = ui.mediaId.isNotEmpty(),
                    onClick = {
                        val size = WhisperSize.fromId(AppPrefs.whisperSize(context))
                        if (!WhisperEngine.AVAILABLE || WhisperModel.isReady(context, size)) {
                            LyricsJobs.start(context, ui.mediaId, ui.title, ui.artist, ui.durationMs)
                        } else confirmDownload = true
                    }
                ) { Text(if (mine && job.stage == LyricsStage.FAILED) "Tentar de novo" else "Buscar letra") }
                TextButton(onClick = { options = true }) { Text("Opções") }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (confirmDownload) {
        val size = WhisperSize.fromId(AppPrefs.whisperSize(context))
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Baixar o modelo de IA?") },
            text = {
                Text(
                    "Na primeira vez é preciso baixar o modelo (${size.label}, cerca de " +
                        "${size.downloadMb} MB). Recomendo usar Wi-Fi. Depois disso a letra é " +
                        "gerada sem internet."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = false
                    LyricsJobs.start(context, ui.mediaId, ui.title, ui.artist, ui.durationMs)
                }) { Text("Baixar e gerar") }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Agora não") } }
        )
    }

    if (options) LyricsOptionsDialog(onDismiss = { options = false })
}

private val languages = listOf(
    "auto" to "Automático", "pt" to "Português", "en" to "Inglês", "es" to "Espanhol"
)

@Composable
private fun LyricsOptionsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var size by remember { mutableStateOf(WhisperSize.fromId(AppPrefs.whisperSize(context))) }
    var lang by remember { mutableStateOf(AppPrefs.lyricsLanguage(context)) }
    var auto by remember { mutableStateOf(AppPrefs.autoLyrics(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Letra por IA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (WhisperEngine.AVAILABLE) Text("Idioma da música", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.forEach { (code, label) ->
                        FilterChip(
                            selected = lang == code,
                            onClick = {
                                lang = code
                                AppPrefs.setLyricsLanguage(context, code)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Precisão do modelo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhisperSize.entries.forEach { option ->
                        FilterChip(
                            selected = size == option,
                            onClick = {
                                size = option
                                AppPrefs.setWhisperSize(context, option.id)
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
                Text(
                    "Equilibrado acerta mais; Leve é mais rápido. " +
                        if (WhisperModel.isReady(context, size)) "Este modelo já está baixado."
                        else "Este modelo ainda não foi baixado (${size.downloadMb} MB).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Buscar a letra automaticamente", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Busca no LRCLIB e salva no celular, sem você precisar tocar em nada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = auto, onCheckedChange = {
                        auto = it
                        AppPrefs.setAutoLyrics(context, it)
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
