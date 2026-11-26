package com.yanki.player.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.yanki.player.R
import com.yanki.player.service.PlayerConnection
import kotlinx.coroutines.delay

/** Tam ekran oynatıcı (telefon) / sağ bölme (tablet). */
@Composable
fun PlayerPanel(vm: MainViewModel, modifier: Modifier, onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    val ui by PlayerConnection.state.collectAsState()
    val controller by PlayerConnection.controller.collectAsState()
    val sleepLeft by PlayerConnection.sleepRemaining.collectAsState()
    val favs by vm.favorites.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val c = controller
    val pos by produceState(0L, c) {
        while (true) {
            value = c?.currentPosition ?: 0L
            delay(400)
        }
    }
    val dur = (c?.duration ?: 0L).coerceAtLeast(0L)
    var drag by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onClose != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Kapat") }
                Text("Şimdi çalıyor", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (ui.key == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.yanki_logo), null, Modifier.size(120.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Bir şarkı veya video seç", color = scheme.onSurfaceVariant)
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Thumb(ui.thumb, ui.isVideo, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), 24.dp)
            }
            Text(ui.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(ui.artist, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))

            Slider(
                value = drag ?: (if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f),
                onValueChange = { drag = it },
                onValueChangeFinished = {
                    drag?.let { c?.seekTo((it * dur).toLong()) }
                    drag = null
                }
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(drag?.let { (it * dur).toLong() } ?: pos), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
                Text(formatTime(dur), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton({ PlayerConnection.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Karışık",
                        tint = if (ui.shuffle) scheme.primary else scheme.onSurfaceVariant)
                }
                IconButton({ c?.seekToPrevious() }, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Önceki", Modifier.size(36.dp))
                }
                FilledIconButton({ PlayerConnection.togglePlay() }, Modifier.size(72.dp)) {
                    Icon(if (ui.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Oynat/Duraklat",
                        Modifier.size(40.dp))
                }
                IconButton({ c?.seekToNext() }, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Sonraki", Modifier.size(36.dp))
                }
                IconButton({ PlayerConnection.cycleRepeat() }) {
                    Icon(
                        if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Döngü",
                        tint = if (ui.repeat != Player.REPEAT_MODE_OFF) scheme.primary else scheme.onSurfaceVariant
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton({ showSpeed = true }) {
                    Icon(Icons.Rounded.Speed, null)
                    Spacer(Modifier.width(6.dp))
                    Text("${ui.speed}x")
                }
                TextButton({ showSleep = true }) {
                    Icon(Icons.Rounded.Bedtime, null)
                    if (sleepLeft > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatTime(sleepLeft))
                    }
                }
                val fav = ui.key in favs
                IconButton({ ui.key?.let { vm.toggleFavorite(it) } }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori",
                        tint = if (fav) Color(0xFFFF6B8A) else scheme.onSurfaceVariant
                    )
                }
                IconButton({ vm.playlistTarget = ui.key }) { Icon(Icons.Rounded.PlaylistAdd, "Listeye ekle") }
            }
            if (ui.isVideo) {
                Spacer(Modifier.height(8.dp))
                Button({ ctx.startActivity(Intent(ctx, VideoPlayerActivity::class.java)) }) {
                    Icon(Icons.Rounded.Movie, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Videoyu aç")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSpeed) SpeedDialog(ui.speed, { PlayerConnection.setSpeed(it) }, { showSpeed = false })
    if (showSleep) SleepDialog(sleepLeft, {
        PlayerConnection.startSleep(it)
        if (it > 0) Toast.makeText(ctx, "$it dakika sonra durdurulacak", Toast.LENGTH_SHORT).show()
    }, { showSleep = false })
}
