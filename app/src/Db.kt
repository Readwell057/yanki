package com.yanki.player.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val mediaKey: String)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "mediaKey"])
data class PlaylistItemEntity(
    val playlistId: Long,
    val mediaKey: String,
    val addedAt: Long
)

/** Kaldığın yerden devam et. */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

@Dao
interface YankiDao {
    @Query("SELECT * FROM favorites")
    fun favorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaKey = :key")
    suspend fun removeFavorite(key: String)

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Query("SELECT * FROM playlist_items ORDER BY addedAt")
    fun playlistItems(): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(i: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :pid AND mediaKey = :key")
    suspend fun removeFromPlaylist(pid: Long, key: String)

    @Query("SELECT * FROM progress WHERE mediaKey = :key")
    suspend fun progress(key: String): ProgressEntity?

    @Query("SELECT * FROM progress")
    fun allProgress(): Flow<List<ProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(p: ProgressEntity)

    @Query("DELETE FROM progress WHERE mediaKey = :key")
    suspend fun clearProgress(key: String)
}

@Database(
    entities = [FavoriteEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, ProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): YankiDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "yanki.db")
                .build().also { inst = it }
        }
    }
}
