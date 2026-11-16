package com.hikari.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Category browsing on the app's real filter files and its real category order. */
class BrowseTest {
    private val dir = File(Awt.root, "engine/src/main/assets/filters")
    private val ids = Regex("\"([a-z0-9_]+)\"").findAll(File(dir, "index.json").readText()).map { it.groupValues[1] }.toList()
    private val items = ids.map { id -> val t = File(dir, "$id.json").readText()
        fun f(k: String) = Regex("\"$k\":\\s*\"([^\"]*)\"").find(t)?.groupValues?.get(1) ?: ""
        LookItem(f("id"), f("name"), f("zh"), f("series")) }
    // the order and names the app has always used (Components.kt SERIES_ZH)
    private val order = Regex("\"(\\w+)\" to \"([^\"]+)\"").findAll(
        File(Awt.root, "app/src/main/java/com/hikari/cam/ui/Components.kt").readLines().first { it.startsWith("val SERIES_ZH") }).map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test fun existingFiltersIdsAndCategoriesAreUntouched() {
        assertEquals(27, items.size)
        assertEquals(ids, items.map { it.id })                       // file name = id, as before
        assertEquals(listOf("sim", "daylight", "film", "japanese", "ccd", "special"), order.map { it.first })
        val expected = listOf("standard_chrome", "soft_chrome", "true_negative", "silver_bypass", "studio_hi", "studio_std", "mono_fine", "muted_chrome", "street_negative",
            "portrait_soft", "vivid_landscape", "cine_daylight", "nostalgic_summer", "clear_daylight", "summer400", "mellow400", "gold_memory", "cinema400", "vivid100", "tokyo_blue",
            "kyoto_soft", "summer_green", "ccd2000", "flash_diary", "compact_chrome", "night_bloom", "dream1998")
        assertEquals(expected, ids)
        assertTrue(items.all { it.series in order.map { o -> o.first } })
    }

    @Test fun oneCategoryAtATimeNeverMixed() {
        val cats = Browse.categories(order, items, setOf("mellow400"))
        assertEquals(listOf("all", "fav") + order.map { it.first }, cats.map { it.id })
        assertEquals(27, cats[0].count); assertEquals(1, cats[1].count)
        var sum = 0
        for (c in cats.drop(2)) { val l = Browse.list(items, c.id, emptySet())
            assertEquals(c.count, l.size); assertTrue(l.all { it.series == c.id }); assertTrue(l.isNotEmpty()); sum += l.size }
        assertEquals(27, sum)                                         // every filter is in exactly one category
        assertEquals(listOf("mellow400"), Browse.list(items, Browse.FAV, setOf("mellow400")).map { it.id })
        assertEquals("all", Browse.valid("gone", cats)); assertEquals("japanese", Browse.valid("japanese", cats))
    }

    @Test fun searchAndSortStayInsideTheCategory() {
        val jp = Browse.list(items, "japanese", emptySet())
        assertEquals(listOf("tokyo_blue"), Browse.list(items, "japanese", emptySet(), "tokyo").map { it.id })
        assertTrue(Browse.list(items, "japanese", emptySet(), "chrome").isEmpty())      // a chrome look of another category does not leak in
        assertTrue(Browse.list(items, Browse.ALL, emptySet(), "chrome").size >= 3)
        assertEquals(jp.map { it.name }.sortedBy { it.lowercase() }, Browse.list(items, "japanese", emptySet(), sort = LookSort.NAME).map { it.name })
        val recent = Browse.list(items, "japanese", emptySet(), sort = LookSort.RECENT, recents = listOf("summer_green", "mellow400", "kyoto_soft"))
        assertEquals(listOf("summer_green", "kyoto_soft"), recent.take(2).map { it.id })
        assertEquals(jp.map { it.id }.toSet(), recent.map { it.id }.toSet())
    }

    @Test fun favouriteAndRecentLists() {
        assertEquals("b,a", Browse.csvAdd("a", "b")); assertEquals("a,b", Browse.csvAdd("b,a", "a"))
        assertEquals("x", Browse.csvToggle("", "x")); assertEquals("", Browse.csvToggle("x", "x")); assertEquals("y,x", Browse.csvToggle("x", "y"))
        assertEquals(listOf("a", "b"), Browse.csvList("a,b,"))
    }
}
