package com.willian.mp3pro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.MainViewModel
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.RemoteEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    onShowDownloads: () -> Unit
) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val downloaded by Offline.downloaded.collectAsStateWithLifecycle()
    val progress by Offline.progress.collectAsStateWithLifecycle()
    val playing by vm.player.ui.collectAsStateWithLifecycle()

    val inSubfolder = lib.path.isNotEmpty()
    BackHandler(enabled = inSubfolder) { vm.goUp() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (inSubfolder) lib.path.substringAfterLast('/') else "Minha música",
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (inSubfolder) {
                    IconButton(onClick = { vm.goUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            },
            actions = {
                IconButton(onClick = { vm.downloadAll(lib.entries) }) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = "Baixar todas desta pasta")
                }
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Link da pasta")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        when {
            lib.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            lib.error != null -> ErrorBox(
                message = lib.error.orEmpty(),
                details = lib.details,
                onRetry = { vm.refresh() },
                onShowDownloads = onShowDownloads
            )

            lib.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma música nesta pasta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(lib.entries, key = { it.path }) { entry ->
                    EntryRow(
                        entry = entry,
                        isCurrent = playing.mediaId == entry.path,
                        isDownloaded = entry.path in downloaded,
                        progress = progress[entry.path],
                        onClick = {
                            if (entry.isFolder) vm.openFolder(entry) else vm.play(lib.entries, entry)
                        },
                        onDownload = { vm.download(entry) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: RemoteEntry,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    progress: Int?,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val accent = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.MusicNote,
            contentDescription = null,
            tint = if (entry.isFolder) MaterialTheme.colorScheme.secondary else accent
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (entry.isFolder) entry.name else entry.title,
                color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            if (!entry.isFolder) {
                val sub = listOf(entry.artist, formatSize(entry.size)).filter { it.isNotEmpty() }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        when {
            entry.isFolder -> Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            progress != null -> CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp
            )

            isDownloaded -> Icon(
                Icons.Filled.DownloadDone, contentDescription = "Disponível offline",
                tint = MaterialTheme.colorScheme.primary
            )

            else -> IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "Baixar para ouvir offline")
            }
        }
    }
}

@Composable
private fun ErrorBox(
    message: String,
    details: List<String>,
    onRetry: () -> Unit,
    onShowDownloads: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.size(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("Tentar de novo") }
            OutlinedButton(onClick = onShowDownloads) { Text("Ver baixadas") }
        }
        if (details.isNotEmpty()) {
            Spacer(Modifier.size(20.dp))
            Text(
                "Detalhes técnicos (útil para diagnóstico):",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Column {
                    details.forEach {
                        Text(
                            "• $it", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
