package com.yanki.player.ui

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.yanki.player.data.uriForKey
import com.yanki.player.service.PlayerConnection
import com.yanki.player.service.PlayerUi
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
class VideoPlayerActivity : ComponentActivity() {
    var audioOnly = false
    private val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerConnection.connect(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        hideSystemBars()
        setContent {
            YankiTheme { VideoScreen(this, inPip.value) }
        }
    }

    override fun onStart() {
        super.onStart()
        audioOnly = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || isFinishing) return
        val vs = PlayerConnection.controller.value?.videoSize
        val ratio = if (vs != null && vs.width > 0 && vs.height > 0)
            (vs.width.toFloat() / vs.height).coerceIn(0.5f, 2.3f) else 16f / 9f
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
                    .build()
            )
        } catch (_: Exception) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!audioOnly && PlayerConnection.state.value.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Normal çıkışta videoyu duraklat; "sadece ses" modunda çalmaya devam eder
        if (!audioOnly && !isChangingConfigurations) PlayerConnection.controller.value?.pause()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoScreen(act: VideoPlayerActivity, inPip: Boolean) {
    val controller by PlayerConnection.controller.collectAsState()
    val ui by PlayerConnection.state.collectAsState()
    val c = controller
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (c == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
        else VideoContent(act, c, ui, inPip)
    }
}

private fun currentBrightness(act: ComponentActivity): Float {
    val b = act.window.attributes.screenBrightness
    if (b >= 0) return b
    return try {
        Settings.System.getInt(act.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) { 0.5f }
}

private fun setBrightness(act: ComponentActivity, v: Float) {
    val lp = act.window.attributes
    lp.screenBrightness = v.coerceIn(0.01f, 1f)
    act.window.attributes = lp
}

private fun trackName(f: Format, i: Int): String {
    val lang = f.language?.takeIf { it != "und" }
        ?.let { Locale(it).displayLanguage.replaceFirstChar { ch -> ch.uppercase() } }
    val base = f.label ?: lang ?: "Parça ${i + 1}"
    return if (f.channelCount > 0) "$base (${f.channelCount} kanal)" else base
}

private fun applySubtitle(ctx: Context, c: MediaController, uri: Uri) {
    val item = c.currentMediaItem ?: return
    runCatching {
        ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val name = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: ""
    val mime = when (name.substringAfterLast('.', "").lowercase()) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
    val sub = MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mime)
        .setLanguage("tr")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
    val idx = c.currentMediaItemIndex
    val pos = c.currentPosition
    val newItem = item.buildUpon()
        .setUri(uriForKey(item.mediaId))
        .setSubtitleConfigurations(listOf(sub))
        .build()
    c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    c.replaceMediaItem(idx, newItem)
    c.seekTo(idx, pos)
}

@Composable
private fun TrackDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onDismiss) { Text("Kapat") } }
    )
}

@Composable
private fun TrackRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoContent(act: VideoPlayerActivity, c: MediaController, ui: PlayerUi, inPip: Boolean) {
    val ctx = LocalContext.current
    val audioManager = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var controlsVisible by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var showLockBtn by remember { mutableStateOf(false) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var showSpeed by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showSub by remember { mutableStateOf(false) }
    var tracks by remember(c) { mutableStateOf(c.currentTracks) }
    var dragSide by remember { mutableIntStateOf(0) }
    var startValue by remember { mutableFloatStateOf(0f) }
    var accum by remember { mutableFloatStateOf(0f) }
    var seekDrag by remember { mutableStateOf<Float?>(null) }

    fun show(msg: String) { hud = msg; hudTick++ }

    // Video görüntüsünü tekrar aç (önceki "sadece ses" modundan gelmiş olabilir)
    LaunchedEffect(c) {
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
    }
    DisposableEffect(c) {
        val l = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        c.addListener(l)
        onDispose { c.removeListener(l) }
    }
    LaunchedEffect(controlsVisible, touch, ui.isPlaying) {
        if (controlsVisible && ui.isPlaying) { delay(3500); controlsVisible = false }
    }
    LaunchedEffect(hudTick) { if (hud != null) { delay(900); hud = null } }
    LaunchedEffect(showLockBtn) { if (showLockBtn) { delay(2500); showLockBtn = false } }

    val pos by produceState(0L, c) {
        while (true) { value = c.currentPosition; delay(400) }
    }
    val dur = c.duration.coerceAtLeast(0L)

    val subLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { applySubtitle(ctx, c, uri); show("Altyazı yüklendi") }
    }

    BackHandler(enabled = locked) { showLockBtn = true }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = resize
                    player = c
                }
            },
            update = { v ->
                v.resizeMode = resize
                if (v.player !== c) v.player = c
            },
            onRelease = { it.player = null }
        )

        if (!inPip) {
            // Dokunmatik jestler
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = {
                                if (locked) showLockBtn = !showLockBtn
                                else { controlsVisible = !controlsVisible; touch++ }
                            },
                            onDoubleTap = { off ->
                                if (!locked) {
                                    val forward = off.x >= size.width / 2
                                    val d = c.duration
                                    val max = if (d > 0) d else Long.MAX_VALUE
                                    c.seekTo((c.currentPosition + if (forward) 10_000 else -10_000).coerceIn(0L, max))
                                    show(if (forward) "+10 sn" else "-10 sn")
                                }
                            }
                        )
                    }
                    .pointerInput(locked) {
                        if (!locked) {
                            detectVerticalDragGestures(
                                onDragStart = { off ->
                                    dragSide = if (off.x < size.width / 2) 1 else 2
                                    accum = 0f
                                    startValue = if (dragSide == 1) currentBrightness(act)
                                    else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    accum += -dy / size.height * 1.3f
                                    val v = (startValue + accum).coerceIn(0f, 1f)
                                    if (dragSide == 1) {
                                        setBrightness(act, v)
                                        show("Parlaklık %${(v * 100).roundToInt()}")
                                    } else {
                                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt(), 0)
                                        show("Ses %${(v * 100).roundToInt()}")
                                    }
                                }
                            )
                        }
                    }
            )

            hud?.let {
                Box(
                    Modifier.align(Alignment.Center)
                        .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text(it, color = Color.White) }
            }

            AnimatedVisibility(
                visible = controlsVisible && !locked,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Üst çubuk
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                            .displayCutoutPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton({ act.finish() }) { Icon(Icons.Rounded.ArrowBack, "Geri", tint = Color.White) }
                        Text(ui.title, color = Color.White, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        IconButton({
                            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                            act.audioOnly = true
                            act.finish()
                        }) { Icon(Icons.Rounded.Headphones, "Sadece ses", tint = Color.White) }
                        IconButton({ act.enterPip() }) {
                            Icon(Icons.Rounded.PictureInPictureAlt, "Resim içinde resim", tint = Color.White)
                        }
                        IconButton({ showSub = true }) { Icon(Icons.Rounded.Subtitles, "Altyazı", tint = Color.White) }
                        IconButton({ showAudio = true }) { Icon(Icons.Rounded.Audiotrack, "Ses dili", tint = Color.White) }
                        IconButton({
                            resize = when (resize) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            show(when (resize) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Sığdır"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Doldur"
                                else -> "Yakınlaştır"
                            })
                        }) { Icon(Icons.Rounded.AspectRatio, "En-boy oranı", tint = Color.White) }
                        IconButton({ showSpeed = true }) { Icon(Icons.Rounded.Speed, "Hız", tint = Color.White) }
                    }

                    // Orta kontroller
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton({ c.seekToPrevious(); touch++ }) {
                            Icon(Icons.Rounded.SkipPrevious, "Önceki", Modifier.size(36.dp), tint = Color.White)
                        }
                        IconButton({ c.seekTo((c.currentPosition - 10_000).coerceAtLeast(0)); touch++ }) {
                            Icon(Icons.Rounded.Replay10, "10 sn geri", Modifier.size(36.dp), tint = Color.White)
                        }
                        FilledIconButton({ PlayerConnection.togglePlay(); touch++ }, Modifier.size(68.dp)) {
                            Icon(if (ui.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Oynat/Duraklat",
                                Modifier.size(38.dp))
                        }
                        IconButton({ c.seekTo(c.currentPosition + 10_000); touch++ }) {
                            Icon(Icons.Rounded.Forward10, "10 sn ileri", Modifier.size(36.dp), tint = Color.White)
                        }
                        IconButton({ c.seekToNext(); touch++ }) {
                            Icon(Icons.Rounded.SkipNext, "Sonraki", Modifier.size(36.dp), tint = Color.White)
                        }
                    }

                    // Alt çubuk
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .displayCutoutPadding().padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(seekDrag?.let { (it * dur).toLong() } ?: pos), color = Color.White,
                                style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = seekDrag ?: (if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f),
                                onValueChange = { seekDrag = it; touch++ },
                                onValueChangeFinished = {
                                    seekDrag?.let { c.seekTo((it * dur).toLong()) }
                                    seekDrag = null
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(formatTime(dur), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ locked = true; showLockBtn = true }) {
                                Icon(Icons.Rounded.Lock, "Ekranı kilitle", tint = Color.White)
                            }
                            Text("Kilitle", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Text("${ui.speed}x", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Ekran kilidi açma düğmesi
            if (locked) {
                AnimatedVisibility(
                    visible = showLockBtn,
                    modifier = Modifier.align(Alignment.CenterStart),
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    IconButton(
                        onClick = { locked = false; showLockBtn = false; controlsVisible = true; touch++ },
                        modifier = Modifier.padding(24.dp).background(Color(0x99000000), CircleShape)
                    ) { Icon(Icons.Rounded.LockOpen, "Kilidi aç", tint = Color.White) }
                }
            }
        }
    }

    if (showSpeed) SpeedDialog(ui.speed, { PlayerConnection.setSpeed(it) }, { showSpeed = false })

    if (showAudio) {
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        TrackDialog("Ses dili / kanalı", { showAudio = false }) {
            if (groups.isEmpty()) Text("Ses parçası bulunamadı")
            groups.forEachIndexed { i, g ->
                TrackRow(trackName(g.getTrackFormat(0), i), g.isSelected) {
                    c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                        .build()
                    showAudio = false
                }
            }
        }
    }

    if (showSub) {
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val textOff = c.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        TrackDialog("Altyazı", { showSub = false }) {
            TrackRow("Kapalı", textOff) {
                c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                showSub = false
            }
            groups.forEachIndexed { i, g ->
                TrackRow(trackName(g.getTrackFormat(0), i), g.isSelected && !textOff) {
                    c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                        .build()
                    showSub = false
                }
            }
            TextButton({ showSub = false; subLauncher.launch(arrayOf("*/*")) }) {
                Text("Dosyadan seç (.srt, .vtt, .ass)")
            }
        }
    }
}
