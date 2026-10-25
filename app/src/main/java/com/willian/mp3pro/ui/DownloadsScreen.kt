package com.willian.mp3pro.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.MainViewModel
import com.willian.mp3pro.data.DownloadedTrack
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.RemoteEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val downloaded by Offline.downloaded.collectAsStateWithLifecycle()
    val playing by vm.player.ui.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<DownloadedTrack?>(null) }

    val tracks = downloaded.values.sortedBy { it.name.lowercase() }
    // Fila de reprodução offline: usa os arquivos locais (sem URL de rede).
    val queue = tracks.map {
        RemoteEntry(name = it.name, path = it.path, isFolder = false, size = it.size, contentUrl = null)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                val total = formatSize(Offline.totalBytes())
                Text(if (total.isEmpty()) "Baixadas" else "Baixadas · $total")
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

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
                    val accent = if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val idx = queue.indexOfFirst { it.path == t.path }
                                if (idx >= 0) vm.player.playQueue(queue, idx)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accent)
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
                        IconButton(onClick = { toDelete = t }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remover download")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
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
