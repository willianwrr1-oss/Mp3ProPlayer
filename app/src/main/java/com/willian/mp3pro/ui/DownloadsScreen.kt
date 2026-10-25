package com.willian.mp3pro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.MainViewModel
import com.willian.mp3pro.data.DownloadedTrack
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.RemoteEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val downloaded by Offline.downloaded.collectAsStateWithLifecycle()
    val lifetime by Offline.lifetimeBytes.collectAsStateWithLifecycle()
    val playing by vm.player.ui.collectAsStateWithLifecycle()

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulk by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<DownloadedTrack?>(null) }

    val tracks = downloaded.values.sortedBy { it.name.lowercase() }
    val currentBytes = tracks.sumOf { it.size }
    // Fila de reprodução offline: usa os arquivos locais (sem URL de rede).
    val queue = tracks.map {
        RemoteEntry(name = it.name, path = it.path, isFolder = false, size = it.size, contentUrl = null)
    }

    // Tira da seleção o que já foi apagado e sai do modo de seleção se a lista esvaziou.
    LaunchedEffect(downloaded) {
        selected = selected.filter { it in downloaded }.toSet()
        if (downloaded.isEmpty()) selecting = false
    }

    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }

    BackHandler(enabled = selecting) { exitSelection() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when {
                        !selecting -> "Baixadas"
                        selected.isEmpty() -> "Selecione músicas"
                        else -> "${selected.size} selecionada(s)"
                    }
                )
            },
            navigationIcon = {
                if (selecting) {
                    IconButton(onClick = { exitSelection() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção")
                    }
                }
            },
            actions = {
                if (selecting) {
                    IconButton(onClick = {
                        selected = if (selected.size == tracks.size) emptySet()
                        else tracks.map { it.path }.toSet()
                    }) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "Selecionar todas")
                    }
                    IconButton(onClick = { confirmBulk = true }, enabled = selected.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir selecionadas")
                    }
                } else if (tracks.isNotEmpty()) {
                    IconButton(onClick = { selecting = true }) {
                        Icon(Icons.Filled.CheckBox, contentDescription = "Selecionar músicas")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // Os dois totais: o histórico nunca diminui; o atual acompanha o que está na pasta.
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatLine("Total baixado (histórico)", formatMb(lifetime))
                StatLine("Na pasta agora (${tracks.size})", formatMb(currentBytes))
                Text(
                    "O histórico soma tudo que já foi baixado, mesmo as músicas apagadas depois.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma música baixada ainda.\nNa biblioteca, toque no ícone de download.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks, key = { it.path }) { t ->
                    val current = playing.mediaId == t.path
                    val isSelected = t.path in selected
                    val accent = if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface

                    fun toggle() {
                        selected = if (isSelected) selected - t.path else selected + t.path
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selecting) toggle()
                                    else {
                                        val idx = queue.indexOfFirst { it.path == t.path }
                                        if (idx >= 0) vm.player.playQueue(queue, idx)
                                    }
                                },
                                onLongClick = {
                                    if (selecting) toggle()
                                    else {
                                        selecting = true
                                        selected = setOf(t.path)
                                    }
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selecting) {
                            Checkbox(checked = isSelected, onCheckedChange = null)
                        } else {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accent)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title, color = accent, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val sub = listOf(t.artist, formatSize(t.size))
                                .filter { it.isNotEmpty() }.joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(
                                    sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (!selecting) {
                            IconButton(onClick = { toDelete = t }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover download")
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    if (confirmBulk) {
        val n = selected.size
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = { Text(if (n == 1) "Remover 1 música?" else "Remover $n músicas?") },
            text = {
                Text(
                    "Elas serão apagadas do aparelho (${formatMb(
                        tracks.filter { it.path in selected }.sumOf { it.size }
                    )}). Continuam na pasta do OneDrive, e o histórico de downloads não muda."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeDownloads(selected)
                    confirmBulk = false
                    exitSelection()
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text("Cancelar") } }
        )
    }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remover download?") },
            text = { Text("\"${t.title}\" será apagada do aparelho. Ela continua na pasta do OneDrive.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeDownload(t.path)
                    toDelete = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
