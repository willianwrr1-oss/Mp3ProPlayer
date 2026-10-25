package com.willian.mp3pro.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.willian.mp3pro.MainActivity
import com.willian.mp3pro.R
import com.willian.mp3pro.audio.AudioFxProcessor
import com.willian.mp3pro.audio.FxStore
import com.willian.mp3pro.data.Http
import com.willian.mp3pro.data.OneDriveClient
import java.io.IOException

/**
 * Serviço de reprodução em segundo plano: mantém a música tocando com a tela
 * apagada, mostra a notificação com controles e integra com Bluetooth/fones.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        FxStore.init(this)

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

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        val provider = DefaultMediaNotificationProvider.Builder(this).build()
        provider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(provider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
