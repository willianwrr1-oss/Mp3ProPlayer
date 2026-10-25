package com.willian.mp3pro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willian.mp3pro.data.AppPrefs
import com.willian.mp3pro.data.DownloadWorker
import com.willian.mp3pro.data.OneDriveClient
import com.willian.mp3pro.data.OneDriveException
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.RemoteEntry
import com.willian.mp3pro.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUi(
    val path: String = "",
    val entries: List<RemoteEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val details: List<String> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val player = PlayerController(app)

    private var client = OneDriveClient(AppPrefs.shareUrl(app))

    private val _shareUrl = MutableStateFlow(AppPrefs.shareUrl(app))
    val shareUrl: StateFlow<String> = _shareUrl.asStateFlow()

    private val _library = MutableStateFlow(LibraryUi(loading = true))
    val library: StateFlow<LibraryUi> = _library.asStateFlow()

    private var loadJob: Job? = null

    init {
        player.connect()
        load("")
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (player.ui.value.isPlaying) player.refreshPosition()
            }
        }
    }

    fun load(path: String) {
        loadJob?.cancel()
        _library.value = _library.value.copy(path = path, loading = true, error = null, details = emptyList())
        loadJob = viewModelScope.launch {
            try {
                val list = client.list(path)
                _library.value = LibraryUi(path = path, entries = list)
            } catch (e: OneDriveException) {
                _library.value = LibraryUi(
                    path = path,
                    error = "Não foi possível ler a pasta do OneDrive.",
                    details = e.details
                )
            } catch (e: Exception) {
                _library.value = LibraryUi(
                    path = path,
                    error = "Sem conexão com a internet?",
                    details = listOf(e.message ?: e.javaClass.simpleName)
                )
            }
        }
    }

    fun refresh() = load(_library.value.path)

    fun openFolder(entry: RemoteEntry) = load(entry.path)

    fun goUp() = load(_library.value.path.substringBeforeLast('/', ""))

    /** Toca [entries] (apenas músicas) começando por [entry]. */
    fun play(entries: List<RemoteEntry>, entry: RemoteEntry) {
        val songs = entries.filter { !it.isFolder }
        val index = songs.indexOfFirst { it.path == entry.path }
        if (index >= 0) player.playQueue(songs, index)
    }

    fun download(entry: RemoteEntry) {
        if (entry.isFolder) return
        DownloadWorker.enqueue(getApplication(), entry)
    }

    fun downloadAll(entries: List<RemoteEntry>) {
        val downloaded = Offline.downloaded.value
        entries.filter { !it.isFolder && it.path !in downloaded }.forEach { download(it) }
    }

    fun removeDownload(path: String) = Offline.remove(path)

    fun saveShareUrl(url: String) {
        val limpo = url.trim()
        if (limpo.isEmpty()) return
        AppPrefs.setShareUrl(getApplication(), limpo)
        _shareUrl.value = limpo
        client = OneDriveClient(limpo)
        load("")
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
