package com.yanki.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MediaRepository {
    private val ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

    /** Cihaz hafızası ve SD karttaki tüm ses dosyaları (MediaStore otomatik tarar). */
    fun loadAudio(ctx: Context): List<MediaInfo> {
        val out = ArrayList<MediaInfo>()
        val a = MediaStore.Audio.Media
        val proj = arrayOf(a._ID, a.TITLE, a.ARTIST, a.ALBUM, a.ALBUM_ID, a.DURATION, a.DATA)
        val sel = "${a.DURATION} >= 5000 AND ${a.IS_RINGTONE} = 0 AND ${a.IS_NOTIFICATION} = 0 AND ${a.IS_ALARM} = 0"
        runCatching {
            ctx.contentResolver.query(
                a.EXTERNAL_CONTENT_URI, proj, sel, null, "${a.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(a._ID)
                val iTitle = c.getColumnIndexOrThrow(a.TITLE)
                val iArtist = c.getColumnIndexOrThrow(a.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(a.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(a.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(a.DURATION)
                val iData = c.getColumnIndexOrThrow(a.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Bilinmeyen sanatçı"
                    val album = c.getString(iAlbum)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
                    val albumId = c.getLong(iAlbumId)
                    out.add(
                        MediaInfo(
                            key = "a$id",
                            id = id,
                            uri = ContentUris.withAppendedId(a.EXTERNAL_CONTENT_URI, id),
                            title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "Adsız",
                            artist = artist,
                            album = album,
                            artUri = if (albumId > 0) ContentUris.withAppendedId(ART_BASE, albumId) else null,
                            durationMs = c.getLong(iDur),
                            folderPath = c.getString(iData)?.substringBeforeLast('/', "") ?: "",
                            isVideo = false
                        )
                    )
                }
            }
        }
        return out
    }

    fun loadVideos(ctx: Context): List<MediaInfo> {
        val out = ArrayList<MediaInfo>()
        val v = MediaStore.Video.Media
        val proj = arrayOf(v._ID, v.DISPLAY_NAME, v.DURATION, v.DATA)
        runCatching {
            ctx.contentResolver.query(
                v.EXTERNAL_CONTENT_URI, proj, null, null, "${v.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(v._ID)
                val iName = c.getColumnIndexOrThrow(v.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(v.DURATION)
                val iData = c.getColumnIndexOrThrow(v.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val path = c.getString(iData)?.substringBeforeLast('/', "") ?: ""
                    val name = (c.getString(iName) ?: "Video").substringBeforeLast('.')
                    out.add(
                        MediaInfo(
                            key = "v$id",
                            id = id,
                            uri = ContentUris.withAppendedId(v.EXTERNAL_CONTENT_URI, id),
                            title = name,
                            artist = path.substringAfterLast('/').ifBlank { "Video" },
                            album = "",
                            artUri = null,
                            durationMs = c.getLong(iDur),
                            folderPath = path,
                            isVideo = true
                        )
                    )
                }
            }
        }
        return out
    }
}
