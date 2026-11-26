package com.yanki.player.data

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class MediaInfo(
    val key: String,          // "a123" (ses) veya "v123" (video)
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val artUri: Uri?,
    val durationMs: Long,
    val folderPath: String,
    val isVideo: Boolean
) {
    val folderName: String
        get() = folderPath.substringAfterLast('/').ifBlank { "Bilinmeyen klasör" }

    val thumb: Any?
        get() = if (isVideo) uri else artUri

    fun toMediaItem(): MediaItem {
        val extras = Bundle().apply { putBoolean("video", isVideo) }
        val md = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(key)
            .setUri(uri)
            .setMediaMetadata(md)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .build()
    }
}

fun uriForKey(key: String): Uri {
    val id = key.substring(1).toLong()
    return if (key[0] == 'v')
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    else
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}
