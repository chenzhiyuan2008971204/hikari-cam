package com.hikari.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A look the user marked as favourite. */
@Entity(tableName = "favorite")
data class Favorite(@PrimaryKey val lookId: String, val addedAt: Long)

/** Last time a look was used (camera or editor); the newest few are "recent". */
@Entity(tableName = "recent")
data class Recent(@PrimaryKey val lookId: String, val usedAt: Long)

/** A saved set of adjustments on top of a look ("my Cinema 400"). [adjustJson] is engine Adjustments JSON. */
@Entity(tableName = "preset")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lookId: String,
    val adjustJson: String,
    val createdAt: Long,
)

/**
 * Non-destructive edit of one photo: the original at [sourceUri] is never written; this row holds
 * the recipe, and exports are new files.
 */
@Entity(tableName = "edit")
data class Edit(
    @PrimaryKey val sourceUri: String,
    val lookId: String,
    val adjustJson: String,
    val updatedAt: Long,
    /** last exported file, if any */
    val exportUri: String? = null,
)

/** Simple key/value user settings. */
@Entity(tableName = "setting")
data class Setting(@PrimaryKey val key: String, val value: String)

@Dao
interface LookDao {
    @Query("SELECT * FROM favorite ORDER BY addedAt DESC")
    fun favorites(): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: Favorite)

    @Query("DELETE FROM favorite WHERE lookId = :lookId")
    suspend fun removeFavorite(lookId: String)

    @Query("SELECT * FROM recent ORDER BY usedAt DESC LIMIT :limit")
    fun recents(limit: Int = 6): Flow<List<Recent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun touch(r: Recent)

    @Query("SELECT * FROM preset ORDER BY createdAt DESC")
    fun presets(): Flow<List<Preset>>

    @Insert
    suspend fun addPreset(p: Preset): Long

    @Query("DELETE FROM preset WHERE id = :id")
    suspend fun deletePreset(id: Long)
}

@Dao
interface EditDao {
    @Query("SELECT * FROM edit WHERE sourceUri = :uri")
    suspend fun get(uri: String): Edit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: Edit)

    @Query("DELETE FROM edit WHERE sourceUri = :uri")
    suspend fun delete(uri: String)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun all(): Flow<List<Setting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: Setting)
}

@Database(entities = [Favorite::class, Recent::class, Preset::class, Edit::class, Setting::class], version = 1, exportSchema = false)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun looks(): LookDao
    abstract fun edits(): EditDao
    abstract fun settings(): SettingDao

    companion object {
        @Volatile private var instance: HikariDatabase? = null

        fun get(context: Context): HikariDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HikariDatabase::class.java, "hikari.db")
                .build().also { instance = it }
        }
    }
}
