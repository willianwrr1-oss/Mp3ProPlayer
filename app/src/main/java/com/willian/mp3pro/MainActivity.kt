package com.willian.mp3pro

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.willian.mp3pro.data.AppPrefs
import com.willian.mp3pro.lyrics.LyricsJobs
import com.willian.mp3pro.lyrics.LyricsStore
import com.willian.mp3pro.lyrics.WhisperEngine
import com.willian.mp3pro.lyrics.WhisperModel
import com.willian.mp3pro.lyrics.WhisperSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.ui.AboutDialog
import com.willian.mp3pro.ui.DownloadsScreen
import com.willian.mp3pro.ui.EqualizerScreen
import com.willian.mp3pro.ui.LibraryScreen
import com.willian.mp3pro.ui.LocalScreen
import com.willian.mp3pro.ui.MiniPlayer
import com.willian.mp3pro.ui.Mp3ProTheme
import com.willian.mp3pro.ui.PlayerScreen
import com.willian.mp3pro.ui.SettingsDialog

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Mp3ProTheme {
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val shareUrl by vm.shareUrl.collectAsStateWithLifecycle()

    // Android 13+: pede permissão para mostrar a notificação de reprodução.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Letra automática: na primeira vez que uma música toca, se o usuário ligou a opção
    // e o modelo já está no celular.
    val context = LocalContext.current
    val playing by vm.player.ui.collectAsStateWithLifecycle()
    LaunchedEffect(playing.mediaId) {
        val id = playing.mediaId
        if (id.isNotEmpty() && AppPrefs.autoLyrics(context) && !LyricsStore.has(id) &&
            (!WhisperEngine.AVAILABLE ||
                WhisperModel.isReady(context, WhisperSize.fromId(AppPrefs.whisperSize(context))))
        ) {
            LyricsJobs.start(context, id, playing.title, playing.artist, playing.durationMs)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(vm.player, onOpen = { showPlayer = true })
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                            label = { Text("OneDrive") }
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                            label = { Text("Baixadas") }
                        )
                        NavigationBarItem(
                            selected = tab == 2, onClick = { tab = 2 },
                            icon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                            label = { Text("Celular") }
                        )
                        NavigationBarItem(
                            selected = tab == 3, onClick = { tab = 3 },
                            icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                            label = { Text("Equalizador") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> LibraryScreen(
                        vm = vm,
                        onOpenSettings = { showSettings = true },
                        onOpenAbout = { showAbout = true },
                        onShowDownloads = { tab = 1 }
                    )
                    1 -> DownloadsScreen(vm)
                    2 -> LocalScreen(vm)
                    else -> EqualizerScreen()
                }
            }
        }

        if (showPlayer) {
            PlayerScreen(controller = vm.player, onClose = { showPlayer = false })
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (showSettings) {
        SettingsDialog(
            currentUrl = shareUrl,
            onSave = {
                vm.saveShareUrl(it)
                showSettings = false
                tab = 0
            },
            onDismiss = { showSettings = false }
        )
    }
}
