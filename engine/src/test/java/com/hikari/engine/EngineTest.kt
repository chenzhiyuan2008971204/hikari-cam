package com.hikari.engine

import com.hikari.engine.gl.SourceMap
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

class EngineTest {
    private val assets = File("src/main/assets")
    private fun asset(p: String) = File(assets, p).readText()
    private val DAYLIGHT = setOf("muted_chrome", "street_negative", "portrait_soft", "vivid_landscape",
        "cine_daylight", "nostalgic_summer", "clear_daylight", "summer400")
    private val ids: List<String> = JSONArray(asset("filters/index.json")).let { a -> (0 until a.length()).map { a.getString(it) } }

    @Test
    fun shippedLooksParseAndTheirLutsLoad() {
        assertEquals(62, ids.size)
        assertEquals(5, ids.count { it in DAYLIGHT })                // three daylight looks retired as near-duplicates
        for (id in ids) {
            val spec = FilterSpec.parse(asset("filters/$id.json"))
            assertEquals(id, spec.id)
            assertEquals("luts/$id.lut", spec.lut)
            val lut = CubeLut.parseBinary(File(assets, spec.lut).readBytes())
            assertEquals("$id lut size", 33, lut.size)
            // the binary file and the standard .cube text it replaces carry the same floats, bit for bit
            val back = CubeLut.parse(lut.toCubeText(spec.name))
            assertEquals("$id text size", lut.size, back.size)
            assertTrue("$id binary and .cube text differ", lut.data.contentEquals(back.data))
            assertTrue("$id lut range", lut.data.all { it in 0f..1f })
            assertTrue("$id grain size", spec.grain.size in 0.0003f..0.003f)
            assertTrue("$id recipe", spec.recipe.size >= 5)
        }
    }

    @Test
    fun onlyBinaryLutsAreShipped() {
        val files = File(assets, "luts").listFiles()!!.map { it.name }
        assertEquals(ids.sorted().map { "$it.lut" }, files.sorted())          // no .cube text, no stray files
    }

    /** The exported .cube is standard text: header, 33^3 rows of three 4-decimal numbers in 0..1, red fastest. */
    @Test
    fun cubeExportIsStandardCubeText() {
        val lut = CubeLut.parseBinary(File(assets, "luts/${ids.first()}.lut").readBytes())
        val lines = lut.toCubeText("Hikari Test \"quoted\"").lines().filter { it.isNotEmpty() }
        assertEquals("TITLE \"Hikari Test 'quoted'\"", lines[0])
        assertEquals("LUT_3D_SIZE 33", lines[1])
        assertEquals(2 + 33 * 33 * 33, lines.size)
        val row = Regex("[01]\\.\\d{4} [01]\\.\\d{4} [01]\\.\\d{4}")
        assertTrue("every data row is three 4-decimal numbers", lines.drop(2).all { row.matches(it) })
        // red changes fastest: the second row differs from the first only in red for an ordinary look
        val r0 = lines[2].split(" ").map { it.toFloat() }; val r1 = lines[3].split(" ").map { it.toFloat() }
        assertTrue(r1[0] > r0[0])
    }

    @Test
    fun binaryLutRejectsWhatIsNotAValidLut() {
        val good = File(assets, "luts/${ids.first()}.lut").readBytes()
        assertEquals(16 + 33 * 33 * 33 * 6, good.size)
        fun bad(msg: String, b: ByteArray) = org.junit.Assert.assertThrows(msg, IllegalArgumentException::class.java) { CubeLut.parseBinary(b) }
        bad("magic", good.copyOf().also { it[0] = 'X'.code.toByte() })
        bad("version", good.copyOf().also { it[4] = 2 })
        bad("size", good.copyOf().also { it[6] = 1; it[7] = 0 })
        bad("size too large", good.copyOf().also { it[6] = 0; it[7] = 1 })
        bad("scale", good.copyOf().also { it[8] = 0x11 })        // 10001 instead of 10000
        bad("truncated", good.copyOf(good.size - 2))
        bad("too long", good.copyOf(good.size + 2))
        bad("empty", ByteArray(0))
    }

    /** Retired near-duplicate looks: not shipped, no files left, and each points at a shipped look. */
    @Test
    fun retiredLooksPointAtShippedOnes() {
        val o = org.json.JSONObject(asset("filters/retired.json"))
        assertEquals(17, o.length())
        for (k in o.keys()) {
            assertTrue("$k still shipped", k !in ids)
            assertTrue("$k json left", !File(assets, "filters/$k.json").exists())
            assertTrue("$k lut left", !File(assets, "luts/$k.lut").exists() && !File(assets, "luts/$k.cube").exists())
            assertTrue("$k -> ${o.getString(k)} not shipped", o.getString(k) in ids)
        }
    }

    /** Kotlin FilmParams must give exactly what tools/engine/params.js (the test harness) gives. */
    @Test
    fun paramsMatchTheTestedHarness() {
        val fixtures = JSONArray(File("src/test/resources/params_fixtures.json").readText())
        for (i in 0 until fixtures.length()) {
            val fx = fixtures.getJSONObject(i)
            val spec = FilterSpec.parse(asset("filters/${fx.getString("id")}.json"))
            val adj = Adjustments.fromJson(fx.getJSONObject("adjust").toString())
            val p = FilmParams.effective(spec, adj)
            val e = fx.getJSONObject("effective")
            val u = e.getJSONObject("uniforms")
            for (k in u.keys()) {
                val v = u.get(k)
                if (v is JSONArray) {
                    val got = p.vec3s[k] ?: error("missing $k")
                    for (c in 0..2) assertEquals("$k[$c]", v.getDouble(c).toFloat(), got[c], 1e-6f)
                } else {
                    assertEquals("${spec.id} $k", (v as Number).toFloat(), p.floats[k] ?: error("missing $k"), 1e-6f)
                }
            }
            assertEquals(u.length(), p.floats.size + p.vec3s.size)
            val chains = fx.getJSONArray("chains")
            for (c in 0 until chains.length()) {
                val ch = chains.getJSONObject(c)
                val aspect = ch.getDouble("aspect").toFloat()
                fun check(name: String, radius: Float) {
                    val want = ch.getJSONObject(name)
                    val got = FilmParams.chainSize(radius, aspect)
                    assertEquals("$name w", want.getInt("w"), got.w)
                    assertEquals("$name h", want.getInt("h"), got.h)
                    assertEquals("$name sigma", want.getDouble("sigma").toFloat(), got.sigma, 1e-5f)
                }
                check("bloom", p.bloomRadius); check("halation", p.halationRadius); check("diffusion", p.diffusionRadius)
            }
        }
    }

    @Test
    fun adjustmentsRoundTrip() {
        val a = Adjustments(strength = 0.6f, grain = 0.2f, halation = null, fade = 0.1f, temperature = -0.3f)
        assertEquals(a, Adjustments.fromJson(a.toJson()))
        assertEquals(Adjustments.DEFAULT, Adjustments.fromJson(null))
    }

    // ── camera orientation: the preview must show the scene upright for every sensor rotation ──

    private val w = 3
    private val h = 4
    private val upright = Array(h) { y -> IntArray(w) { x -> y * 10 + x } }
    private fun rotCW(a: Array<IntArray>) = Array(a[0].size) { r -> IntArray(a.size) { c -> a[a.size - 1 - c][r] } }
    private fun sensor(rot: Int): Array<IntArray> {
        var b = upright
        repeat(((360 - rot) % 360) / 90) { b = rotCW(b) }
        return b
    }

    private fun checkCamera(rot: Int, mirror: Boolean) {
        val buf = sensor(rot)
        val m = SourceMap.camera(rot, mirror, buf[0].size, buf.size, w.toFloat() / h)
        fun at(u: Float, v: Float): Int {
            val (s, t) = m.apply(u, v) // t = 0 is the buffer's first row
            return buf[(t * (buf.size - 1)).roundToInt()][(s * (buf[0].size - 1)).roundToInt()]
        }
        val tl = if (mirror) upright[0][w - 1] else upright[0][0]
        val br = if (mirror) upright[h - 1][0] else upright[h - 1][w - 1]
        assertEquals("rot=$rot mirror=$mirror top-left", tl, at(0f, 0f))
        assertEquals("rot=$rot mirror=$mirror bottom-right", br, at(1f, 1f))
    }

    @Test
    fun cameraPreviewIsUprightForEverySensorRotation() {
        for (rot in listOf(0, 90, 180, 270)) { checkCamera(rot, false); checkCamera(rot, true) }
    }

    @Test
    fun cameraCropKeepsTheCentre() {
        // a 4:3 landscape buffer, upright portrait 3:4, cropped to 1:1
        val m = SourceMap.camera(90, false, 4, 3, 1f)
        val (s0, t0) = m.apply(0.5f, 0.5f)
        assertEquals(0.5f, s0, 1e-5f)
        assertEquals(0.5f, t0, 1e-5f)
    }

    @Test
    fun bitmapCropAndMirror() {
        val m = SourceMap.bitmap(0.1f, 0.2f, 0.9f, 0.8f, mirror = true)
        val (s, t) = m.apply(0f, 0f)
        assertEquals(0.9f, s, 1e-6f); assertEquals(0.2f, t, 1e-6f)
        val (s2, t2) = m.apply(1f, 1f)
        assertEquals(0.1f, s2, 1e-6f); assertEquals(0.8f, t2, 1e-6f)
    }

    @Test
    fun fastDecimalParserMatchesToFloat() {
        for (x in listOf("0", "1", "0.5", "-0.25", "0.0123", "1.0000", "+2.5", "1e-3", "-4.5E2", "0.99999994")) {
            assertEquals(x, x.toFloat(), CubeLut.parseDecimal(x, 0, x.length), 1e-7f)
        }
        for (bad in listOf("", "-", ".", "1e", "abc", "1.2.3", "1x")) {
            assertTrue(bad, CubeLut.parseDecimal(bad, 0, bad.length).isNaN())
        }
    }
}
