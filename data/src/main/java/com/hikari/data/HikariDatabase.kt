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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/**
 * A camera recipe (C1..C7, like a Fujifilm custom setting): a whole style that the camera switches to in
 * one tap: look, filter strength, exposure fine-tune, grain / halation / vignette overrides, the other
 * adjustments, the timestamp style and the frame style (each with its own on/off), and whether the
 * shutter also keeps a clean film-only copy. [slot] is 1..7.
 */
@Entity(tableName = "recipe")
data class FilmRecipe(
    @PrimaryKey val slot: Int,
    val name: String,
    val lookId: String,
    /** filter strength, 0..1.5 (1 = the look as designed) */
    val strength: Float,
    /** exposure fine-tune in stops */
    val exposure: Float,
    /** null = the look's own amount, 0 = off */
    val grain: Float?,
    val halation: Float?,
    val vignette: Float?,
    /** every other adjustment (engine Adjustments JSON with the five fields above left at their defaults) */
    val adjustJson: String,
    val stampOn: Boolean,
    /** encoded StampSpec (studio module) */
    val stampSpec: String,
    val frameOn: Boolean,
    /** encoded FrameOptions (studio module) */
    val frameOptions: String,
    /** also save the film-rendered photo without timestamp and frame */
    val saveClean: Boolean,
    val updatedAt: Long,
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
interface RecipeDao {
    @Query("SELECT * FROM recipe ORDER BY slot")
    fun all(): Flow<List<FilmRecipe>>

    @Query("SELECT * FROM recipe WHERE slot = :slot")
    suspend fun get(slot: Int): FilmRecipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(r: FilmRecipe)

    @Query("DELETE FROM recipe WHERE slot = :slot")
    suspend fun delete(slot: Int)
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

    /** one value by key (per-photo records such as "studio:<uri>" live here too) */
    @Query("SELECT * FROM setting WHERE `key` = :key")
    suspend fun get(key: String): Setting?
}

@Database(entities = [Favorite::class, Recent::class, Preset::class, Edit::class, Setting::class, FilmRecipe::class], version = 2, exportSchema = false)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun looks(): LookDao
    abstract fun edits(): EditDao
    abstract fun settings(): SettingDao
    abstract fun recipes(): RecipeDao

    companion object {
        /** 1 -> 2: the recipe table. Nothing else changes; every existing row stays. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipe` (`slot` INTEGER NOT NULL, `name` TEXT NOT NULL, `lookId` TEXT NOT NULL, " +
                        "`strength` REAL NOT NULL, `exposure` REAL NOT NULL, `grain` REAL, `halation` REAL, `vignette` REAL, " +
                        "`adjustJson` TEXT NOT NULL, `stampOn` INTEGER NOT NULL, `stampSpec` TEXT NOT NULL, `frameOn` INTEGER NOT NULL, " +
                        "`frameOptions` TEXT NOT NULL, `saveClean` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`slot`))")
            }
        }

        @Volatile private var instance: HikariDatabase? = null

        fun get(context: Context): HikariDatabase = instance ?: synchronized(this) {
            instance ?: open(context, "hikari.db").also { instance = it }
        }

        /** A database file by name (the app uses "hikari.db"; tests open their own copy). Upgrades keep the user's data. */
        fun open(context: Context, name: String): HikariDatabase =
            Room.databaseBuilder(context.applicationContext, HikariDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
