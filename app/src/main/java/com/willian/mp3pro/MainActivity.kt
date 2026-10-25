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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.willian.mp3pro.ui.DownloadsScreen
import com.willian.mp3pro.ui.EqualizerScreen
import com.willian.mp3pro.ui.LibraryScreen
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
    val shareUrl by vm.shareUrl.collectAsStateWithLifecycle()

    // Android 13+: pede permissão para mostrar a notificação de reprodução.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                            label = { Text("Biblioteca") }
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                            label = { Text("Baixadas") }
                        )
                        NavigationBarItem(
                            selected = tab == 2, onClick = { tab = 2 },
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
                        onShowDownloads = { tab = 1 }
                    )
                    1 -> DownloadsScreen(vm)
                    else -> EqualizerScreen()
                }
            }
        }

        if (showPlayer) {
            PlayerScreen(controller = vm.player, onClose = { showPlayer = false })
        }
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
