package com.willian.mp3pro.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.willian.mp3pro.data.DownloadedTrack
import com.willian.mp3pro.data.LocalLibrary
import com.willian.mp3pro.data.LocalTrack
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.OneDriveClient
import com.willian.mp3pro.data.RemoteEntry

/**
 * Árvore de músicas que o Android Auto (e outros controles externos) navega:
 *
 *   Raiz
 *   ├── Baixadas        ("dl")            músicas salvas no aparelho
 *   ├── OneDrive        ("odf:<pasta>")   a pasta compartilhada, com subpastas
 *   └── Neste celular   ("lf:<pasta>/")   músicas que o Android já indexou
 *
 * As músicas têm identificadores com prefixo: "dl:", "od:" e "lt:".
 */
class AutoLibrary(private val context: Context) {

    companion object {
        const val ROOT = "root"
        const val DOWNLOADS = "dl"
        const val ONEDRIVE_PREFIX = "odf:"
        const val LOCAL_PREFIX = "lf:"
        private const val TRACK_DL = "dl:"
        private const val TRACK_OD = "od:"
        private const val TRACK_LOCAL = "lt:"
        private const val LOCAL_CACHE_MS = 60_000L
    }

    private var localCache: List<LocalTrack> = emptyList()
    private var localAt = 0L

    fun isTrack(id: String): Boolean =
        id.startsWith(TRACK_DL) || id.startsWith(TRACK_OD) || id.startsWith(TRACK_LOCAL)

    fun rootItem(): MediaItem = folder(ROOT, "MP3 Pro Player")

    // ------------------------------------------------------------------ navegação

    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(DOWNLOADS, "Baixadas"),
            folder(ONEDRIVE_PREFIX, "OneDrive"),
            folder(LOCAL_PREFIX, "Neste celular")
        )

        parentId == DOWNLOADS -> downloads()
        parentId.startsWith(ONEDRIVE_PREFIX) -> oneDrive(parentId.removePrefix(ONEDRIVE_PREFIX))
        parentId.startsWith(LOCAL_PREFIX) -> local(parentId.removePrefix(LOCAL_PREFIX))
        else -> emptyList()
    }

    /** Um item pelo identificador (pasta ou música). */
    suspend fun lookup(mediaId: String): MediaItem? = when {
        mediaId == ROOT -> rootItem()
        mediaId == DOWNLOADS -> folder(DOWNLOADS, "Baixadas")
        mediaId.startsWith(ONEDRIVE_PREFIX) -> folder(
            mediaId,
            mediaId.removePrefix(ONEDRIVE_PREFIX).trimEnd('/').substringAfterLast('/')
                .ifEmpty { "OneDrive" }
        )

        mediaId.startsWith(LOCAL_PREFIX) -> folder(
            mediaId,
            mediaId.removePrefix(LOCAL_PREFIX).trimEnd('/').substringAfterLast('/')
                .ifEmpty { "Neste celular" }
        )

        mediaId.startsWith(TRACK_DL) ->
            Offline.downloaded.value[mediaId.removePrefix(TRACK_DL)]?.let { dlTrack(it) }

        mediaId.startsWith(TRACK_OD) -> odTrack(mediaId.removePrefix(TRACK_OD))
        mediaId.startsWith(TRACK_LOCAL) -> {
            val id = mediaId.removePrefix(TRACK_LOCAL).toLongOrNull()
            localTracks().firstOrNull { it.id == id }?.let { localTrack(it) }
        }

        else -> null
    }

    /** Dá a uma música sem endereço (vinda do Android Auto) o endereço de reprodução. */
    suspend fun resolve(item: MediaItem): MediaItem =
        if (item.localConfiguration != null) item else (lookup(item.mediaId) ?: item)

    /** A fila de uma música: as outras músicas da mesma pasta e a posição dela. */
    suspend fun siblings(mediaId: String): Pair<List<MediaItem>, Int>? {
        val list: List<MediaItem> = when {
            mediaId.startsWith(TRACK_DL) -> downloads()
            mediaId.startsWith(TRACK_OD) -> {
                val parent = mediaId.removePrefix(TRACK_OD).substringBeforeLast('/', "")
                oneDrive(parent).filter { it.mediaMetadata.isPlayable == true }
            }

            mediaId.startsWith(TRACK_LOCAL) -> {
                val me = localTracks().firstOrNull { TRACK_LOCAL + it.id == mediaId } ?: return null
                localTracks().filter { it.dir == me.dir }.map { localTrack(it) }
            }

            else -> return null
        }
        val index = list.indexOfFirst { it.mediaId == mediaId }
        return if (index >= 0) list to index else null
    }

    // ------------------------------------------------------------------ listas

    private fun downloads(): List<MediaItem> =
        Offline.downloaded.value.values
            .sortedBy { it.name.lowercase() }
            .filter { Offline.fileOf(it.path) != null }
            .map { dlTrack(it) }

    private suspend fun oneDrive(path: String): List<MediaItem> =
        OneDriveClient.get(context).list(path).map {
            if (it.isFolder) folder(ONEDRIVE_PREFIX + it.path, it.name) else odTrack(it.path)
        }

    private suspend fun local(dir: String): List<MediaItem> {
        val all = localTracks()
        val here = all.filter { it.dir == dir }
        val subfolders = all
            .filter { it.dir != dir && it.dir.startsWith(dir) }
            .map { it.dir.removePrefix(dir).substringBefore('/') }
            .distinct()
            .sortedBy { it.lowercase() }
        return subfolders.map { folder(LOCAL_PREFIX + dir + it + "/", it) } +
            here.map { localTrack(it) }
    }

    private suspend fun localTracks(): List<LocalTrack> {
        val agora = System.currentTimeMillis()
        if (agora - localAt > LOCAL_CACHE_MS) {
            localCache = try {
                LocalLibrary.scan(context)
            } catch (e: Exception) {
                emptyList()
            }
            localAt = agora
        }
        return localCache
    }

    // ------------------------------------------------------------------ itens

    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun track(id: String, title: String, artist: String, uri: Uri): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist.ifEmpty { null })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    private fun dlTrack(t: DownloadedTrack): MediaItem {
        val file = Offline.fileOf(t.path)
        val uri = if (file != null) Uri.fromFile(file) else Uri.parse(OneDriveClient.pseudoUrl(t.path))
        return track(TRACK_DL + t.path, t.title, t.artist, uri)
    }

    private fun odTrack(path: String): MediaItem {
        val nome = RemoteEntry(
            name = path.substringAfterLast('/'), path = path,
            isFolder = false, size = 0L, contentUrl = null
        )
        // Se a música já foi baixada, toca o arquivo do aparelho.
        val file = Offline.fileOf(path)
        val uri = if (file != null) Uri.fromFile(file) else Uri.parse(OneDriveClient.pseudoUrl(path))
        return track(TRACK_OD + path, nome.title, nome.artist, uri)
    }

    private fun localTrack(t: LocalTrack): MediaItem {
        val e = t.toEntry()
        return track(TRACK_LOCAL + t.id, e.title, e.artist, Uri.parse(t.uri))
    }
}
