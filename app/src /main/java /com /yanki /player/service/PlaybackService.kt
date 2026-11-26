package com.yanki.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yanki.player.data.AppDb
import com.yanki.player.data.ProgressEntity
import com.yanki.player.data.uriForKey
import com.yanki.player.ui.MainActivity
import kotlinx.coroutines.*

/**
 * Tüm ses ve video oynatma burada, tek bir ExoPlayer ile yapılır.
 * Bildirim, kilit ekranı kontrolleri ve arka plan oynatma MediaSession sayesinde otomatik gelir.
 * Ses odağı (arama gelince duraklat, bitince devam) ExoPlayer tarafından yönetilir.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastKey: String? = null

    override fun onCreate() {
        super.onCreate()
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    lastKey?.let { k -> clearProgress(k) }
                }
                lastKey = mediaItem?.mediaId?.takeIf { it.isNotEmpty() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { clearProgress(it) }
                }
            }
        })

        // Her 5 saniyede bir kaldığın yeri kaydet
        scope.launch {
            while (isActive) {
                delay(5000)
                if (player.isPlaying) saveProgress(player)
            }
        }
    }

    private fun clearProgress(key: String) {
        scope.launch(Dispatchers.IO) { AppDb.get(this@PlaybackService).dao().clearProgress(key) }
    }

    private fun saveProgress(p: Player) {
        val item = p.currentMediaItem ?: return
        val key = item.mediaId.takeIf { it.isNotEmpty() } ?: return
        val isVideo = item.mediaMetadata.extras?.getBoolean("video") ?: false
        val dur = p.duration
        val pos = p.currentPosition
        if (dur == C.TIME_UNSET || dur <= 0) return
        // Videolar her zaman, ses dosyaları sadece 10 dakikadan uzunsa kaydedilir
        if (!isVideo && dur < 10 * 60_000L) return
        scope.launch(Dispatchers.IO) {
            val dao = AppDb.get(this@PlaybackService).dao()
            if (pos >= dur - 5000) dao.clearProgress(key)
            else if (pos >= 3000) dao.saveProgress(ProgressEntity(key, pos, dur, System.currentTimeMillis()))
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) item
                else runCatching { item.buildUpon().setUri(uriForKey(item.mediaId)).build() }.getOrDefault(item)
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
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
}
