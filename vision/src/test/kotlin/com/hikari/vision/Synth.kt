package com.hikari.vision

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.tan
import kotlin.random.Random

/** Test pictures with known answers, and loaders for the real sample photos. */
object Synth {
    const val W = 256; const val H = 192

    fun frame(paint: (x: Float, y: Float, rnd: Random) -> FloatArray, seed: Int = 1): Frame {
        val rnd = Random(seed); val out = FloatArray(W * H * 3)
        // 3x3 supersampling: edges are anti-aliased like a real photo, not pixel staircases
        for (y in 0 until H) for (x in 0 until W) {
            val i = (y * W + x) * 3
            for (sy in 0..2) for (sx in 0..2) {
                val c = paint((x + (sx - 1) / 3f) / (W - 1f), (y + (sy - 1) / 3f) / (H - 1f), rnd)
                out[i] += c[0].coerceIn(0f, 1f) / 9f; out[i + 1] += c[1].coerceIn(0f, 1f) / 9f; out[i + 2] += c[2].coerceIn(0f, 1f) / 9f
            }
        }
        return Frame(W, H, out)
    }

    private fun n(rnd: Random, a: Float) = (rnd.nextFloat() - 0.5f) * a

    /** Blue sky over textured ground; the horizon passes (0.5, y) with the given tilt (+ = right side lower). */
    fun landscape(y: Float, tiltDeg: Float, seed: Int = 1) = frame({ fx, fy, rnd ->
        val hy = y + (fx - 0.5f) * tan(Math.toRadians(tiltDeg.toDouble())).toFloat() * W / H
        if (fy < hy) { val t = fy / hy.coerceAtLeast(0.01f); floatArrayOf(0.25f + 0.35f * t, 0.45f + 0.3f * t, 0.8f + 0.1f * t) }
        else floatArrayOf(0.30f + n(rnd, 0.12f), 0.42f + n(rnd, 0.12f), 0.20f + n(rnd, 0.08f))
    }, seed)

    /** A facade of windows; all verticals lean by tiltDeg (+ = tops lean right). */
    fun facade(tiltDeg: Float, seed: Int = 1) = frame({ fx, fy, rnd ->
        val shift = (0.5f - fy) * tan(Math.toRadians(tiltDeg.toDouble())).toFloat() * H / W
        val u = fx - shift
        val col = ((u * 8f) % 1f + 1f) % 1f; val row = (fy * 6f) % 1f
        val window = col in 0.25f..0.75f && row in 0.2f..0.8f
        val v = if (window) 0.18f else 0.72f
        floatArrayOf(v + n(rnd, 0.03f), v + n(rnd, 0.03f), v + 0.02f + n(rnd, 0.03f))
    }, seed)

    /** One coloured object on a plain background, centred at (cx, cy). */
    fun blob(cx: Float, cy: Float, r: Float = 0.1f, seed: Int = 1) = frame({ fx, fy, rnd ->
        val d = kotlin.math.hypot((fx - cx) * W / H, fy - cy)
        if (d < r) floatArrayOf(0.85f, 0.25f, 0.2f) else floatArrayOf(0.62f + n(rnd, 0.02f), 0.66f + n(rnd, 0.02f), 0.7f + n(rnd, 0.02f))
    }, seed)

    /** A road narrowing to a vanishing point at (vx, vy), under a plain sky. */
    fun road(vx: Float, vy: Float, seed: Int = 1) = frame({ fx, fy, rnd ->
        if (fy < vy) floatArrayOf(0.7f, 0.78f, 0.9f) else {
            val half = (fy - vy) / (1f - vy) * 0.45f
            if (abs(fx - vx) < half) floatArrayOf(0.25f + n(rnd, 0.02f), 0.25f + n(rnd, 0.02f), 0.27f + n(rnd, 0.02f))
            else floatArrayOf(0.45f + n(rnd, 0.03f), 0.6f + n(rnd, 0.03f), 0.3f + n(rnd, 0.03f))
        }
    }, seed)

    fun noise(seed: Int = 1) = frame({ _, _, rnd -> floatArrayOf(0.5f + n(rnd, 0.3f), 0.5f + n(rnd, 0.3f), 0.5f + n(rnd, 0.3f)) }, seed)

    val repo: File by lazy {
        var d: File? = File(System.getProperty("hikari.root") ?: ".").absoluteFile
        while (d != null && !File(d, "engine/src/main/assets").exists()) d = d.parentFile
        d ?: error("repository root not found")
    }

    fun photo(name: String): Frame {
        val img = ImageIO.read(File(repo, "app/src/main/assets/samples/$name"))
        val px = IntArray(img.width * img.height); img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return Frame.fromArgb(px, img.width, img.height, 256)
    }

    /** Traits of every shipped look, read from the filter json files (flat numbers). */
    fun looks(): List<LookTraits> {
        val dir = File(repo, "engine/src/main/assets/filters")
        val ids = Regex("\"([a-z0-9_]+)\"").findAll(File(dir, "index.json").readText()).map { it.groupValues[1] }.toList()
        return ids.map { id ->
            val text = File(dir, "$id.json").readText()
            val block = text.substringAfter("\"traits\": {").substringBefore("}")
            val t = Regex("\"(\\w+)\":\\s*(-?[0-9.eE+-]+)").findAll(block).associate { it.groupValues[1] to it.groupValues[2].toFloat() }
            val name = Regex("\"name\":\\s*\"([^\"]+)\"").find(text)!!.groupValues[1]
            val series = Regex("\"series\":\\s*\"([^\"]+)\"").find(text)!!.groupValues[1]
            LookTraits(id, name, series, t)
        }
    }
}
