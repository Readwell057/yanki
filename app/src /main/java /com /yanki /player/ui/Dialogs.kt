package com.yanki.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun SpeedDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Oynatma hızı") },
        text = {
            Column {
                speeds.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(s); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = abs(current - s) < 0.01f, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text("${s}x")
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Kapat") } }
    )
}

@Composable
fun SleepDialog(remainingMs: Long, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uyku zamanlayıcısı") },
        text = {
            Column {
                if (remainingMs > 0) {
                    Text("Kalan süre: ${formatTime(remainingMs)}", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                listOf(15, 30, 60).forEach { m ->
                    TextButton({ onSelect(m); onDismiss() }) { Text("$m dakika sonra durdur") }
                }
                if (remainingMs > 0) {
                    TextButton({ onSelect(0); onDismiss() }) { Text("Zamanlayıcıyı kapat") }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Kapat") } }
    )
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Ad") })
        },
        confirmButton = { TextButton({ onOk(text) }, enabled = text.isNotBlank()) { Text("Oluştur") } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
fun PlaylistDialogs(vm: MainViewModel) {
    val playlists by vm.playlists.collectAsState()
    val target = vm.playlistTarget
    if (vm.showNewPlaylist) {
        NameDialog(
            title = "Yeni çalma listesi",
            onDismiss = { vm.showNewPlaylist = false },
            onOk = { name ->
                vm.createPlaylist(name, target)
                vm.showNewPlaylist = false
                vm.playlistTarget = null
            }
        )
    } else if (target != null) {
        AlertDialog(
            onDismissRequest = { vm.playlistTarget = null },
            title = { Text("Çalma listesine ekle") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    playlists.forEach { p ->
                        TextButton({ vm.addToPlaylist(p.id, target); vm.playlistTarget = null }) { Text(p.name) }
                    }
                    TextButton({ vm.showNewPlaylist = true }) { Text("+ Yeni liste oluştur") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ vm.playlistTarget = null }) { Text("Kapat") } }
        )
    }
}
