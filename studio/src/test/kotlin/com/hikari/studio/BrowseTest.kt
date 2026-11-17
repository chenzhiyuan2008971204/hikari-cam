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
        File(Awt.root, "app/src/main/java/com/hikari/cam/ui/Components.kt").readText().substringAfter("val SERIES_ZH").substringBefore(")\n")).map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test fun existingFiltersIdsAndCategoriesAreUntouched() {
        assertEquals(79, items.size)
        assertEquals(ids, items.map { it.id })                       // file name = id, as before
        assertEquals(listOf("sim", "daylight", "film", "japanese", "ccd", "special", "fuji", "cinema", "pro", "vintage"), order.map { it.first })
        val expected = listOf("standard_chrome", "soft_chrome", "true_negative", "silver_bypass", "studio_hi", "studio_std", "mono_fine", "muted_chrome", "street_negative",
            "portrait_soft", "vivid_landscape", "cine_daylight", "nostalgic_summer", "clear_daylight", "summer400", "mellow400", "gold_memory", "cinema400", "vivid100", "tokyo_blue",
            "kyoto_soft", "summer_green", "ccd2000", "flash_diary", "compact_chrome", "night_bloom", "dream1998")
        assertEquals(expected, ids.take(27))                          // the 27 older looks: same ids, same order, still first
        // the older Japanese looks are still in their category, before the added ones
        assertEquals(listOf("tokyo_blue", "kyoto_soft", "summer_green"), items.filter { it.series == "japanese" }.take(3).map { it.id })
        assertTrue(items.all { it.series in order.map { o -> o.first } })
    }

    @Test fun oneCategoryAtATimeNeverMixed() {
        val cats = Browse.categories(order, items, setOf("mellow400"))
        assertEquals(listOf("all", "fav") + order.map { it.first }, cats.map { it.id })
        assertEquals(79, cats[0].count); assertEquals(1, cats[1].count)
        var sum = 0
        for (c in cats.drop(2)) { val l = Browse.list(items, c.id, emptySet())
            assertEquals(c.count, l.size); assertTrue(l.all { it.series == c.id }); assertTrue(l.isNotEmpty()); sum += l.size }
        assertEquals(79, sum)                                         // every filter is in exactly one category
        assertEquals(listOf("mellow400"), Browse.list(items, Browse.FAV, setOf("mellow400")).map { it.id })
        assertEquals("all", Browse.valid("gone", cats)); assertEquals("japanese", Browse.valid("japanese", cats))
    }

    @Test fun searchAndSortStayInsideTheCategory() {
        val jp = Browse.list(items, "japanese", emptySet())
        assertEquals(listOf("tokyo_blue", "jp_tokyo_night"), Browse.list(items, "japanese", emptySet(), "tokyo").map { it.id })
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

    @Test fun demoPhotosFitTheLookAndDoNotRepeat() {
        val photos = listOf(
            LookDemo.Photo("n1.jpg", setOf("night", "street")), LookDemo.Photo("n2.jpg", setOf("night")),
            LookDemo.Photo("s1.jpg", setOf("sakura")), LookDemo.Photo("g1.jpg", setOf("green", "garden")),
            LookDemo.Photo("t1.jpg", setOf("street")), LookDemo.Photo("t2.jpg", setOf("street", "rail")),
            LookDemo.Photo("e1.jpg", setOf("sea")), LookDemo.Photo("h1.jpg", setOf("shrine")),
        )
        val looks = listOf("ci_800t" to "CineStill 800T", "jp_tokyo_night" to "Tokyo Night", "jp_pastel" to "Soft Pastel",
            "fj_velvia" to "Velvia", "pr_street" to "Urban Street", "fj_acros" to "Acros", "pr_golden" to "Golden Hour")
        val a = LookDemo.assign(looks, photos)
        assertEquals(looks.size, a.size)
        assertEquals(looks.size, a.values.toSet().size)          // no photo twice while the pool lasts
        assertTrue(a["ci_800t"]!!.startsWith("n")); assertTrue(a["jp_tokyo_night"]!!.startsWith("n"))
        assertEquals("s1.jpg", a["jp_pastel"]); assertEquals("g1.jpg", a["fj_velvia"])
        assertEquals("h1.jpg", a["fj_acros"]); assertEquals("e1.jpg", a["pr_golden"])
        assertTrue(a["pr_street"]!!.startsWith("t"))
        assertEquals(a, LookDemo.assign(looks, photos))           // deterministic
        // more looks than photos: every photo is used before any is used twice
        val many = (0 until 20).map { "x$it" to "Look $it" }
        val counts = LookDemo.assign(many, photos).values.groupingBy { it }.eachCount().values
        assertTrue(counts.max() - counts.min() <= 1)
        assertTrue(LookDemo.assign(looks, emptyList()).isEmpty())
    }

    /** the app's real looks on the app's real Japanese photo pool (assets/japan/japan.json) */
    @Test fun everyLookGetsItsOwnFittingJapanesePhoto() {
        val json = File(Awt.root, "app/src/main/assets/japan/japan.json").readText()
        val photos = Regex("\\{[^{}]*\"file\":\\s*\"([^\"]+)\"[^{}]*\"tags\":\\s*\\[([^\\]]*)\\][^{}]*\\}").findAll(json).map { m ->
            LookDemo.Photo(m.groupValues[1], Regex("\"(\\w+)\"").findAll(m.groupValues[2]).map { it.groupValues[1] }.toSet())
        }.toList()
        assertTrue("pool covers the frame library without repeats", photos.size >= Frames.all.size)
        photos.forEach { assertTrue(it.file, File(Awt.root, "app/src/main/assets/japan/${it.file}").isFile) }
        val a = LookDemo.assign(items.map { it.id to it.name }, photos)
        assertEquals(items.size, a.size)
        assertEquals("no look shares its thumbnail photo", items.size, a.values.toSet().size)
        val byFile = photos.associateBy { it.file }
        val misses = items.filter { LookDemo.wants(it.id, it.name).none { w -> w in byFile[a[it.id]]!!.tags } }
        assertTrue("looks on a photo of the wrong kind: ${misses.map { it.id }}", misses.size <= items.size / 10)
        for (n in items.filter { "night" in it.id || "800t" in it.id }) assertTrue(n.id, "night" in byFile[a[n.id]]!!.tags)
        File(Awt.root, "studio/build/look_photos.txt").writeText(items.joinToString("\n") { "${it.id} ${a[it.id]} ${it.name}" })
    }
}
