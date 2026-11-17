package com.hikari.cam

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hikari.data.FilmRecipe
import com.hikari.data.HikariDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The recipes table arrives by migration: a version-1 database keeps every row and the app can use recipes. */
@RunWith(AndroidJUnit4::class)
class DbMigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun recipe(slot: Int, grain: Float?) = FilmRecipe(
        slot, "街拍負片", "fj_classic_neg", 0.8f, 0.3f, grain, 0f, null, "{\"contrast\":0.2}",
        true, "stamp-code", false, "", true, 1234L)

    @Test fun upgradeFromVersionOneKeepsEveryRow() {
        val name = "migration_test.db"
        ctx.deleteDatabase(name)
        val path = ctx.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        // a version-1 database exactly as Room created it
        SQLiteDatabase.openOrCreateDatabase(path, null).apply {
            execSQL("CREATE TABLE IF NOT EXISTS `favorite` (`lookId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`lookId`))")
            execSQL("CREATE TABLE IF NOT EXISTS `recent` (`lookId` TEXT NOT NULL, `usedAt` INTEGER NOT NULL, PRIMARY KEY(`lookId`))")
            execSQL("CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `lookId` TEXT NOT NULL, `adjustJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            execSQL("CREATE TABLE IF NOT EXISTS `edit` (`sourceUri` TEXT NOT NULL, `lookId` TEXT NOT NULL, `adjustJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `exportUri` TEXT, PRIMARY KEY(`sourceUri`))")
            execSQL("CREATE TABLE IF NOT EXISTS `setting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'version1')")
            execSQL("INSERT INTO favorite VALUES('mellow400', 11), ('fj_velvia', 12)")
            execSQL("INSERT INTO recent VALUES('jp_blue', 21)")
            execSQL("INSERT INTO preset (name, lookId, adjustJson, createdAt) VALUES('我的 Cinema', 'cinema400', '{\"strength\":0.7}', 31)")
            execSQL("INSERT INTO edit VALUES('content://photo/1', 'ci_800t', '{}', 41, NULL)")
            execSQL("INSERT INTO setting VALUES('lastLook', 'kyoto_soft'), ('studio:content://photo/1', 'x')")
            version = 1
            close()
        }
        val db = HikariDatabase.open(ctx, name)
        try {
            runBlocking {
                assertEquals(setOf("mellow400", "fj_velvia"), db.looks().favorites().first().map { it.lookId }.toSet())
                assertEquals(listOf("jp_blue"), db.looks().recents().first().map { it.lookId })
                assertEquals("我的 Cinema", db.looks().presets().first().single().name)
                assertEquals("ci_800t", db.edits().get("content://photo/1")!!.lookId)
                assertEquals("kyoto_soft", db.settings().get("lastLook")!!.value)
                assertEquals("x", db.settings().get("studio:content://photo/1")!!.value)
                // and the new table works
                assertEquals(emptyList<FilmRecipe>(), db.recipes().all().first())
                db.recipes().put(recipe(2, 0.5f))
                assertEquals(recipe(2, 0.5f), db.recipes().get(2))
            }
        } finally {
            db.close(); ctx.deleteDatabase(name)
        }
    }

    @Test fun recipesKeepNullOverridesApartFromZero() {
        val name = "recipe_test.db"
        ctx.deleteDatabase(name)
        val db = HikariDatabase.open(ctx, name)
        try {
            runBlocking {
                db.recipes().put(recipe(1, null)); db.recipes().put(recipe(7, 0f)); db.recipes().put(recipe(3, 0.4f))
                assertEquals(listOf(1, 3, 7), db.recipes().all().first().map { it.slot })          // ordered by slot
                assertNull(db.recipes().get(1)!!.grain); assertEquals(0f, db.recipes().get(7)!!.grain!!, 0f)
                assertNull(db.recipes().get(1)!!.vignette)
                db.recipes().put(recipe(3, 0.9f).copy(name = "改過"))                               // replaces the slot
                assertEquals("改過", db.recipes().get(3)!!.name); assertEquals(3, db.recipes().all().first().size)
                db.recipes().delete(3)
                assertNull(db.recipes().get(3))
            }
        } finally {
            db.close(); ctx.deleteDatabase(name)
        }
    }
}
