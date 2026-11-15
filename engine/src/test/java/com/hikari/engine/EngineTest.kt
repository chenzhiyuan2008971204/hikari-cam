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
        assertEquals(27, ids.size)
        assertEquals(8, ids.count { it in DAYLIGHT })
        for (id in ids) {
            val spec = FilterSpec.parse(asset("filters/$id.json"))
            assertEquals(id, spec.id)
            val lut = CubeLut.parse(asset(spec.lut))
            assertEquals("$id lut size", 33, lut.size)
            assertTrue("$id lut range", lut.data.all { it in 0f..1f })
            assertTrue("$id grain size", spec.grain.size in 0.0003f..0.003f)
            assertTrue("$id recipe", spec.recipe.size >= 5)
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
