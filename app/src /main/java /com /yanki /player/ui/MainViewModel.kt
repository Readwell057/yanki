package com.yanki.player.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yanki.player.data.*
import com.yanki.player.service.PlayerConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class Dest { MUSIC, VIDEO, FAVORITES }

sealed class Detail {
    data class Folder(val path: String, val video: Boolean) : Detail()
    data class Artist(val name: String) : Detail()
    data class Playlist(val id: Long, val name: String) : Detail()
}

fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33)
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun hasMediaPermission(ctx: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33)
        ok(Manifest.permission.READ_MEDIA_AUDIO) || ok(Manifest.permission.READ_MEDIA_VIDEO)
    else ok(Manifest.permission.READ_EXTERNAL_STORAGE)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    val audio = MutableStateFlow<List<MediaInfo>>(emptyList())
    val videos = MutableStateFlow<List<MediaInfo>>(emptyList())

    var loading by mutableStateOf(false)
    var granted by mutableStateOf(false)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var dest by mutableStateOf(Dest.MUSIC)
    var musicTab by mutableIntStateOf(0)
    var videoTab by mutableIntStateOf(0)
    var detail by mutableStateOf<Detail?>(null)
    var showPlayer by mutableStateOf(false)
    var playlistTarget by mutableStateOf<String?>(null)
    var showNewPlaylist by mutableStateOf(false)

    val favorites = dao.favorites().map { l -> l.map { it.mediaKey }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistItems = dao.playlistItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress = dao.allProgress().map { l -> l.associateBy { it.mediaKey } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var obsJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            obsJob?.cancel()
            obsJob = viewModelScope.launch {
                delay(1500)
                if (granted) refresh()
            }
        }
    }

    init {
        val cr = app.contentResolver
        cr.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        cr.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    fun onPermissionChanged(ctx: Context) {
        val g = hasMediaPermission(ctx)
        if (g && !granted) {
            granted = true
            refresh()
        } else granted = g
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        loading = true
        viewModelScope.launch {
            val (a, v) = withContext(Dispatchers.IO) {
                MediaRepository.loadAudio(ctx) to MediaRepository.loadVideos(ctx)
            }
            audio.value = a
            videos.value = v
            loading = false
        }
    }

    fun selectDest(d: Dest) {
        dest = d
        detail = null
        query = ""
        searching = false
    }

    fun playList(ctx: Context, list: List<MediaInfo>, index: Int) {
        viewModelScope.launch {
            val start = PlayerConnection.play(ctx, list, index)
            if (start > 0) {
                Toast.makeText(ctx, "Kaldığın yerden devam ediliyor (${formatTime(start)})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleFavorite(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (key in favorites.value) dao.removeFavorite(key) else dao.addFavorite(FavoriteEntity(key))
        }
    }

    fun createPlaylist(name: String, thenAdd: String?) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertPlaylist(PlaylistEntity(name = n))
            if (thenAdd != null) dao.addToPlaylist(PlaylistItemEntity(id, thenAdd, System.currentTimeMillis()))
        }
    }

    fun addToPlaylist(pid: Long, key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.addToPlaylist(PlaylistItemEntity(pid, key, System.currentTimeMillis()))
        }
    }

    fun removeFromPlaylist(pid: Long, key: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.removeFromPlaylist(pid, key) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPlaylist(id)
            dao.deletePlaylist(id)
        }
    }
}
