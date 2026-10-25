package com.willian.mp3pro

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willian.mp3pro.data.AppPrefs
import com.willian.mp3pro.data.DownloadWorker
import com.willian.mp3pro.data.LocalLibrary
import com.willian.mp3pro.data.LocalTrack
import com.willian.mp3pro.data.OneDriveClient
import com.willian.mp3pro.data.OneDriveException
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.RemoteEntry
import com.willian.mp3pro.data.SafCrumb
import com.willian.mp3pro.data.SafLibrary
import com.willian.mp3pro.data.SafRoot
import com.willian.mp3pro.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUi(
    val path: String = "",
    val entries: List<RemoteEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val details: List<String> = emptyList()
)

data class LocalFolder(val name: String, val dir: String, val count: Int)

data class LocalUi(
    val dir: String = "",
    val folders: List<LocalFolder> = emptyList(),
    val tracks: List<RemoteEntry> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val total: Int = 0,
    val error: String? = null
)

data class SafUi(
    /** Pastas que o usuário já escolheu. */
    val roots: List<SafRoot> = emptyList(),
    /** Pasta escolhida que está aberta no momento (nula = nenhuma). */
    val root: SafRoot? = null,
    /** Caminho dentro da pasta aberta; o último item é a pasta atual. */
    val crumbs: List<SafCrumb> = emptyList(),
    val folders: List<SafCrumb> = emptyList(),
    val tracks: List<RemoteEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val player = PlayerController(app)

    private val _shareUrl = MutableStateFlow(AppPrefs.shareUrl(app))
    val shareUrl: StateFlow<String> = _shareUrl.asStateFlow()

    private val _library = MutableStateFlow(LibraryUi(loading = true))
    val library: StateFlow<LibraryUi> = _library.asStateFlow()

    private var loadJob: Job? = null

    private val _local = MutableStateFlow(LocalUi())
    val local: StateFlow<LocalUi> = _local.asStateFlow()
    private var allLocal: List<LocalTrack> = emptyList()

    private val _saf = MutableStateFlow(SafUi(roots = SafLibrary.load(app)))
    val saf: StateFlow<SafUi> = _saf.asStateFlow()

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
                val list = OneDriveClient.get(getApplication()).list(path)
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

    fun removeDownloads(paths: Collection<String>) = Offline.removeAll(paths)

    // ------------------------------------------------------------------ músicas do celular

    /** Procura as músicas do aparelho (precisa da permissão de áudio já concedida). */
    fun scanLocal() {
        if (_local.value.loading) return
        _local.value = _local.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                allLocal = LocalLibrary.scan(getApplication())
                showLocal(_local.value.dir, total = allLocal.size)
            } catch (e: Exception) {
                _local.value = _local.value.copy(
                    loading = false,
                    error = "Não foi possível ler as músicas do celular."
                )
            }
        }
    }

    fun openLocalFolder(folder: LocalFolder) = showLocal(folder.dir)

    fun localUp() {
        val atual = _local.value.dir
        if (atual.isEmpty()) return
        val pai = atual.trimEnd('/').substringBeforeLast('/', "")
        showLocal(if (pai.isEmpty()) "" else "$pai/")
    }

    private fun showLocal(dir: String, total: Int = allLocal.size) {
        val aqui = allLocal.filter { it.dir == dir }
        val subpastas = allLocal
            .filter { it.dir != dir && it.dir.startsWith(dir) }
            .groupBy { it.dir.removePrefix(dir).substringBefore('/') }
            .map { (nome, lista) -> LocalFolder(nome, "$dir$nome/", lista.size) }
            .sortedBy { it.name.lowercase() }
        _local.value = LocalUi(
            dir = dir,
            folders = subpastas,
            tracks = aqui.map { it.toEntry() },
            loading = false,
            loaded = true,
            total = total
        )
    }

    // ------------------------------------------------------------------ pastas escolhidas pelo usuário

    /** Guarda a pasta escolhida no seletor do Android e já a abre. */
    fun addSafFolder(uri: Uri) {
        viewModelScope.launch {
            val tree = uri.toString()
            val nome = withContext(Dispatchers.IO) { SafLibrary.displayName(getApplication(), tree) }
            val root = SafRoot(tree, nome)
            val roots = _saf.value.roots.filter { it.treeUri != tree } + root
            SafLibrary.save(getApplication(), roots)
            _saf.value = _saf.value.copy(roots = roots)
            openSafRoot(root)
        }
    }

    fun removeSafFolder(root: SafRoot) {
        val roots = _saf.value.roots.filter { it.treeUri != root.treeUri }
        SafLibrary.save(getApplication(), roots)
        try {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(root.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        _saf.value = _saf.value.copy(roots = roots)
        closeSaf()
    }

    fun openSafRoot(root: SafRoot) =
        loadSaf(root, listOf(SafCrumb(SafLibrary.rootDocId(root.treeUri), root.name)))

    fun openSafFolder(folder: SafCrumb) {
        val s = _saf.value
        val root = s.root ?: return
        loadSaf(root, s.crumbs + folder)
    }

    fun safUp() {
        val s = _saf.value
        val root = s.root ?: return
        if (s.crumbs.size > 1) loadSaf(root, s.crumbs.dropLast(1)) else closeSaf()
    }

    fun closeSaf() {
        _saf.value = _saf.value.copy(
            root = null, crumbs = emptyList(), folders = emptyList(),
            tracks = emptyList(), loading = false, error = null
        )
    }

    private fun loadSaf(root: SafRoot, crumbs: List<SafCrumb>) {
        _saf.value = _saf.value.copy(root = root, crumbs = crumbs, loading = true, error = null)
        viewModelScope.launch {
            try {
                val (pastas, musicas) = SafLibrary.list(getApplication(), root, crumbs.last().docId)
                _saf.value = _saf.value.copy(folders = pastas, tracks = musicas, loading = false)
            } catch (e: Exception) {
                _saf.value = _saf.value.copy(
                    loading = false,
                    error = "Não foi possível abrir esta pasta. Tente escolhê-la de novo."
                )
            }
        }
    }

    fun playSafTracks() {
        val musicas = _saf.value.tracks
        if (musicas.isNotEmpty()) player.playQueue(musicas, 0)
    }

    /** Toca todas as músicas da pasta [dir], incluindo as subpastas. */
    fun playLocalFolder(dir: String) {
        val fila = allLocal
            .filter { it.dir.startsWith(dir) }
            .sortedWith(compareBy({ it.dir.lowercase() }, { it.name.lowercase() }))
            .map { it.toEntry() }
        if (fila.isNotEmpty()) player.playQueue(fila, 0)
    }

    fun saveShareUrl(url: String) {
        val limpo = url.trim()
        if (limpo.isEmpty()) return
        AppPrefs.setShareUrl(getApplication(), limpo)
        _shareUrl.value = limpo
        load("")
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
