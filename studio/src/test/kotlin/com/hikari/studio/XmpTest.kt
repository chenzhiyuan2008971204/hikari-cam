package com.hikari.studio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.Inflater
import kotlin.math.abs

class XmpTest {
    private fun cube(id: String): Pair<FloatArray, Int> {
        var n = 0; val v = ArrayList<Float>()
        for (l in File(Awt.root, "engine/src/main/assets/luts/$id.cube").readLines()) { val t = l.trim()
            if (t.startsWith("LUT_3D_SIZE")) n = t.split(Regex("\\s+"))[1].toInt()
            else if (t.isNotEmpty() && (t[0].isDigit() || t[0] == '-' || t[0] == '.')) t.split(Regex("\\s+")).forEach { v += it.toFloat() } }
        return v.toFloatArray() to n
    }

    @Test fun base85RoundTrip() {
        for (len in listOf(0, 1, 2, 3, 4, 5, 7, 8, 1001)) { val d = ByteArray(len) { (it * 37 + 11).toByte() }; assertArrayEquals(d, XmpProfile.b85Decode(XmpProfile.b85(d))) }
    }

    @Test fun identityLutGivesAnEmptyTable() {
        val n = 17; val id = FloatArray(n * n * n * 3)
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) { val i = ((b * n + g) * n + r) * 3; id[i] = r / (n - 1f); id[i + 1] = g / (n - 1f); id[i + 2] = b / (n - 1f) }
        val block = XmpProfile.block(id, n)
        // every delta is 0 or +-1 (rounding of the identity ramp)
        val t = XmpProfile.decode(block); var k = 0
        for (r in 0 until 32) for (g in 0 until 32) for (b in 0 until 32) { assertEquals(r / 31f, t[k], 2e-5f); assertEquals(g / 31f, t[k + 1], 2e-5f); assertEquals(b / 31f, t[k + 2], 2e-5f); k += 3 }
    }

    /** The table inside the exported file is the look's own LUT: decode the finished .xmp text and compare. */
    @Test fun exportedProfileCarriesTheLooksLut() {
        val out = File("build/reports/xmp").apply { mkdirs() }
        for (id in listOf("mellow400", "ci_portra400", "fj_acros", "jp_blue")) {
            val (lut, n) = cube(id)
            val text = XmpProfile.xmp("Hikari $id", "Hikari", "test", lut, n)
            File(out, "$id.xmp").writeText(text)
            val md5 = Regex("crs:RGBTable=\"([0-9A-F]{32})\"").find(text)!!.groupValues[1]
            val enc = Regex("crs:Table_$md5=\"([^\"]+)\"").find(text)!!.groupValues[1]
            val payload = XmpProfile.b85Decode(enc)
            val len = java.nio.ByteBuffer.wrap(payload).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val block = ByteArray(len); Inflater().apply { setInput(payload, 4, payload.size - 4); assertEquals(len, inflate(block)) }
            assertEquals(md5, java.security.MessageDigest.getInstance("MD5").digest(block).joinToString("") { "%02X".format(it) })
            val t = XmpProfile.decode(block); val px = FloatArray(3); var worst = 0f; var k = 0
            for (r in 0 until 32) for (g in 0 until 32) for (b in 0 until 32) { XmpProfile.sample(lut, n, r / 31f, g / 31f, b / 31f, px)
                for (c in 0..2) worst = maxOf(worst, abs(t[k + c] - px[c].coerceIn(0f, 1f))); k += 3 }
            assertTrue("$id: table differs from the LUT by $worst", worst < 1e-4f)
            assertTrue(text.contains("<rdf:li xml:lang=\"x-default\">Hikari $id</rdf:li>"))
        }
    }
}
