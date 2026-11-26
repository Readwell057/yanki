package com.yanki.player.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.yanki.player.data.AppDb
import com.yanki.player.data.MediaInfo
import com.yanki.player.data.uriForKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlayerUi(
    val key: String? = null,
    val title: String = "",
    val artist: String = "",
    val artUri: Uri? = null,
    val isVideo: Boolean = false,
    val isPlaying: Boolean = false,
    val showPause: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f
) {
    val thumb: Any?
        get() = if (key == null) null else if (isVideo) runCatching { uriForKey(key) }.getOrNull() else artUri
}

/** Uygulamanın her yerinden aynı oynatıcıya (servis) bağlanmayı sağlar. */
object PlayerConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state

    val sleepRemaining = MutableStateFlow(0L)
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)
    }

    fun connect(context: Context) {
        if (future != null) return
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            val c = try { f.get() } catch (e: Exception) { future = null; null }
            if (c != null) {
                c.addListener(listener)
                _controller.value = c
                refresh(c)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun refresh(p: Player) {
        val item = p.currentMediaItem
        val md = item?.mediaMetadata
        _state.value = PlayerUi(
            key = item?.mediaId?.takeIf { it.isNotEmpty() },
            title = md?.title?.toString() ?: "",
            artist = md?.artist?.toString() ?: "",
            artUri = md?.artworkUri,
            isVideo = md?.extras?.getBoolean("video") ?: false,
            isPlaying = p.isPlaying,
            showPause = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE,
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode,
            speed = p.playbackParameters.speed
        )
    }

    /** Listeyi çalar. Kaydedilmiş bir konum varsa oradan devam eder. Başlangıç konumunu (ms) döndürür. */
    suspend fun play(ctx: Context, items: List<MediaInfo>, index: Int): Long {
        if (items.isEmpty()) return 0L
        val c = controller.filterNotNull().first()
        val i = index.coerceIn(items.indices)

        var list = items
        var idx = i
        if (items.size > 400) {              // çok büyük listeleri parçalayarak gönder
            val to = (maxOf(0, i - 100) + 400).coerceAtMost(items.size)
            val from = (to - 400).coerceAtLeast(0)
            list = items.subList(from, to)
            idx = i - from
        }

        val saved = withContext(Dispatchers.IO) { AppDb.get(ctx).dao().progress(items[i].key) }
        val start = if (saved != null && saved.positionMs > 5000 && saved.positionMs < saved.durationMs - 5000)
            saved.positionMs else 0L

        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        c.setMediaItems(list.map { it.toMediaItem() }, idx, start)
        c.prepare()
        c.play()
        return start
    }

    fun togglePlay() {
        val c = _controller.value ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause()
        else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun toggleShuffle() {
        val c = _controller.value ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = _controller.value ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        _controller.value?.setPlaybackSpeed(speed)
    }

    /** minutes <= 0 ise zamanlayıcıyı iptal eder. */
    fun startSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { sleepRemaining.value = 0; return }
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = scope.launch {
            while (true) {
                val left = end - SystemClock.elapsedRealtime()
                if (left <= 0) break
                sleepRemaining.value = left
                delay(1000)
            }
            sleepRemaining.value = 0
            _controller.value?.pause()
        }
    }
}
