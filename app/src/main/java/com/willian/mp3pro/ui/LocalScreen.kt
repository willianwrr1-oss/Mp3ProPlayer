package com.willian.mp3pro.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val ui by vm.local.collectAsStateWithLifecycle()
    val playing by vm.player.ui.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok }

    LaunchedEffect(granted) {
        if (granted && !ui.loaded && !ui.loading) vm.scanLocal()
    }

    val inSubfolder = ui.dir.isNotEmpty()
    BackHandler(enabled = inSubfolder) { vm.localUp() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (inSubfolder) ui.dir.trimEnd('/').substringAfterLast('/') else "No celular",
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (inSubfolder) {
                    IconButton(onClick = { vm.localUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            },
            actions = {
                if (granted && ui.loaded) {
                    IconButton(onClick = { vm.playLocalFolder(ui.dir) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Tocar esta pasta (com subpastas)")
                    }
                    IconButton(onClick = { vm.scanLocal() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Procurar de novo")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        when {
            !granted -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Para tocar as músicas do celular, o app precisa de acesso a \"Música e áudio\".",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(16.dp))
                Button(onClick = { askPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) }) {
                    Text("Permitir acesso")
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    "Se o Android não perguntar, ative em Configurações > Apps > MP3 Pro Player > Permissões.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            ui.loading && !ui.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            ui.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            ui.folders.isEmpty() && ui.tracks.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (ui.total == 0) "Nenhuma música encontrada no celular." else "Pasta vazia.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(ui.folders, key = { "d:" + it.dir }) { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openLocalFolder(folder) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (folder.count == 1) "1 música" else "${folder.count} músicas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
                items(ui.tracks, key = { it.path }) { entry ->
                    val current = playing.mediaId == entry.path
                    val accent = if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.play(ui.tracks, entry) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val sub = listOf(entry.artist, formatSize(entry.size))
                                .filter { it.isNotEmpty() }.joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(
                                    sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
