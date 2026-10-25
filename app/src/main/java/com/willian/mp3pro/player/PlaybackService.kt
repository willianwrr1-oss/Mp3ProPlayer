package com.willian.mp3pro.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.willian.mp3pro.MainActivity
import com.willian.mp3pro.R
import com.willian.mp3pro.audio.AudioFxProcessor
import com.willian.mp3pro.audio.FxStore
import com.willian.mp3pro.data.Http
import com.willian.mp3pro.data.Offline
import com.willian.mp3pro.data.OneDriveClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Serviço de reprodução em segundo plano: mantém a música tocando com a tela
 * apagada, mostra a notificação com controles e integra com Bluetooth/fones.
 *
 * Por ser um MediaLibraryService, o app também aparece no Android Auto, que navega
 * pelas músicas (Baixadas, OneDrive e Neste celular) e controla a reprodução.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var auto: AutoLibrary

    override fun onCreate() {
        super.onCreate()
        FxStore.init(this)
        Offline.init(this)
        auto = AutoLibrary(applicationContext)

        val renderers = FxRenderersFactory(this, AudioFxProcessor())
        val http = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(Http.client))
        // Troca o endereço interno "onedrive://" pelo link real (e novo) na hora de tocar.
        val dataSource = ResolvingDataSource.Factory(http) { spec ->
            if (spec.uri.scheme == OneDriveClient.SCHEME) {
                val caminho = spec.uri.getQueryParameter("p")
                    ?: throw IOException("caminho da música ausente")
                val real = OneDriveClient.get(applicationContext).downloadUrl(caminho)
                spec.withUri(Uri.parse(real))
            } else {
                spec
            }
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()

        val provider = DefaultMediaNotificationProvider.Builder(this).build()
        provider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(provider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Roda [block] fora da thread principal e entrega o resultado como um "future". */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(auto.rootItem(), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            future<LibraryResult<MediaItem>> {
                val item = auto.lookup(mediaId)
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            future<LibraryResult<ImmutableList<MediaItem>>> {
                val all = try {
                    auto.children(parentId)
                } catch (e: Exception) {
                    return@future LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
                }
                val from = (page.toLong() * pageSize).coerceIn(0L, all.size.toLong()).toInt()
                val to = (from.toLong() + pageSize).coerceIn(0L, all.size.toLong()).toInt()
                LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
            }

        /**
         * Quando o Android Auto manda tocar uma única música, monta a fila com as outras
         * da mesma pasta, para os botões de próxima e anterior funcionarem.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            future {
                val resolved = mediaItems.map { auto.resolve(it) }
                val single = resolved.singleOrNull()
                if (single != null && controller.packageName != packageName && auto.isTrack(single.mediaId)) {
                    val queue = auto.siblings(single.mediaId)
                    if (queue != null) {
                        return@future MediaSession.MediaItemsWithStartPosition(
                            queue.first, queue.second, startPositionMs
                        )
                    }
                }
                MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
            }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            future { mediaItems.map { auto.resolve(it) }.toMutableList() }
    }
}
