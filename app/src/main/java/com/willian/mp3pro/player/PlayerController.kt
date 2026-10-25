package com.willian.mp3pro.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.RemoteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUi(
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val mediaId: String = "",
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null
)

/** Ponte entre a interface e o serviço de reprodução (via MediaController). */
class PlayerController(private val context: Context) {

    private var controller: MediaController? = null
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()

        override fun onPlayerError(error: PlaybackException) {
            _ui.value = _ui.value.copy(
                error = "Não foi possível tocar esta música. Verifique a conexão ou tente outra."
            )
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                sync()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "Falha ao conectar ao player.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun sync() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _ui.value = _ui.value.copy(
            hasMedia = item != null,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            mediaId = item?.mediaId.orEmpty(),
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            error = if (c.playbackState == Player.STATE_READY) null else _ui.value.error
        )
    }

    /** Atualiza só a posição (chamado a cada meio segundo enquanto toca). */
    fun refreshPosition() {
        val c = controller ?: return
        _ui.value = _ui.value.copy(
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: _ui.value.durationMs
        )
    }

    fun playQueue(entries: List<RemoteEntry>, startIndex: Int) {
        val c = controller ?: return
        val items = entries.mapNotNull { toMediaItem(it) }
        if (items.isEmpty()) return
        // Mantém o índice correto caso algum item tenha sido descartado.
        val startId = entries.getOrNull(startIndex)?.path
        val idx = items.indexOfFirst { it.mediaId == startId }.coerceAtLeast(0)
        _ui.value = _ui.value.copy(error = null)
        c.setMediaItems(items, idx, 0L)
        c.prepare()
        c.play()
    }

    private fun toMediaItem(e: RemoteEntry): MediaItem? {
        val local = Offline.fileOf(e.path)
        val uri: Uri = when {
            local != null -> Uri.fromFile(local)
            e.contentUrl != null -> Uri.parse(e.contentUrl)
            else -> return null
        }
        return MediaItem.Builder()
            .setMediaId(e.path)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(e.title)
                    .setArtist(e.artist.ifEmpty { null })
                    .build()
            )
            .build()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
