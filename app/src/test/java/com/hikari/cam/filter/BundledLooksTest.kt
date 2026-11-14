package com.hikari.cam.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The photographer looks ship as .cube files in assets. These tests load every one of them with
 * the app's own parser, so a broken bake or a preset pointing at a missing file fails the build.
 */
class BundledLooksTest {

    private val assets = File("src/main/assets")

    private fun lutFor(f: Filter): CubeLut {
        val path = f.lutFile!!
        assertTrue("${f.id}: built-in look must use a bundled LUT", path.startsWith(CubeLut.ASSET))
        val file = File(assets, path.removePrefix(CubeLut.ASSET))
        assertTrue("${f.id}: missing ${file.path}", file.exists())
        return CubeLut.parse(file.readText())
    }

    @Test
    fun everyStyleLookLoadsAndIsComplete() {
        assertTrue(StylePresets.all.isNotEmpty())
        for (f in StylePresets.all) {
            val lut = lutFor(f)
            assertEquals("${f.id}: size", 33, lut.size)
            assertEquals("${f.id}: entries", 33 * 33 * 33 * 3, lut.data.size)
            for (v in lut.data) assertTrue("${f.id}: value $v out of range", v in 0f..1f)
            assertEquals("${f.id}: strength blends the LUT", 1f, f.params.lutAmount)
        }
    }

    @Test
    fun greyStaysOrderedThroughEveryLook() {
        // Along the neutral axis the output brightness must never go down (no tone reversals).
        for (f in StylePresets.all) {
            val lut = lutFor(f)
            val n = lut.size
            var prev = -1f
            for (i in 0 until n) {
                val idx = ((i * n + i) * n + i) * 3
                val y = 0.2126f * lut.data[idx] + 0.7152f * lut.data[idx + 1] + 0.0722f * lut.data[idx + 2]
                assertTrue("${f.id}: grey step $i goes darker ($prev -> $y)", y >= prev - 1e-3f)
                prev = y
            }
        }
    }

    @Test
    fun idsAreUniqueAcrossAllBuiltIns() {
        val ids = Presets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun parserHandlesHeaderDomainAndRows() {
        val text = """
            # comment
            TITLE "x y"
            LUT_3D_SIZE 2
            DOMAIN_MIN 0 0 0
            DOMAIN_MAX 1 1 1
            0 0 0
            1.0 0 0
            0 1 0
            1 1 0
            0 0 1
            1 0 1
            0 1 1
            1 1 1
        """.trimIndent()
        val lut = CubeLut.parse(text)
        assertEquals(2, lut.size)
        assertEquals(1f, lut.data[3])
        assertEquals(1f, lut.data[23])
    }
}
