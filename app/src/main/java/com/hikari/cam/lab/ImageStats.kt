package com.hikari.cam.lab

import android.graphics.Bitmap
import kotlin.math.abs

/** Pixel maths for the preview-vs-export check. Plain CPU code, run off the main thread. */
object ImageStats {

    /** Downscales by repeated halving (each halving is an exact 2x2 box filter), then to [w] x [h]. */
    fun downscale(src: Bitmap, w: Int, h: Int): Bitmap {
        var cur = src
        while (cur.width / 2 >= w && cur.height / 2 >= h) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        if (cur.width == w && cur.height == h) return if (cur === src) src.copy(Bitmap.Config.ARGB_8888, false) else cur
        val out = Bitmap.createScaledBitmap(cur, w, h, true)
        if (cur !== src) cur.recycle()
        return out
    }

    class Result(
        /** mean |difference| over all pixels and channels, in 8-bit levels, after a light blur */
        val meanAbs: Double,
        /** 99th percentile of the per-pixel max-channel difference */
        val p99: Int,
        val max: Int,
        /** signed mean difference per channel (export - preview): overall colour or brightness shift */
        val biasR: Double, val biasG: Double, val biasB: Double,
        /** difference x8 as a grey image, for looking at where it differs */
        val heatmap: Bitmap,
    ) {
        val pass get() = meanAbs < 1.5 && p99 < 8
    }

    /**
     * Compares two same-size images after a 5x5 box blur (film grain is randomness, not the look,
     * so it is averaged away; edges and colour are not).
     */
    fun compare(a: Bitmap, b: Bitmap): Result {
        require(a.width == b.width && a.height == b.height) { "size mismatch" }
        val w = a.width
        val h = a.height
        val pa = IntArray(w * h).also { a.getPixels(it, 0, w, 0, 0, w, h) }
        val pb = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        val ca = Array(3) { c -> blur(channel(pa, c), w, h) }
        val cb = Array(3) { c -> blur(channel(pb, c), w, h) }
        val hist = IntArray(256)
        var sumAbs = 0.0
        val bias = DoubleArray(3)
        var mx = 0
        val heat = IntArray(w * h)
        for (i in 0 until w * h) {
            var m = 0
            for (c in 0..2) {
                val d = cb[c][i] - ca[c][i]
                bias[c] += d
                val ad = abs(d)
                sumAbs += ad
                val q = ad.toInt().coerceAtMost(255)
                if (q > m) m = q
            }
            hist[m]++
            if (m > mx) mx = m
            val g = (m * 8).coerceAtMost(255)
            heat[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val n = (w * h).toDouble()
        var acc = 0
        var p99 = 255
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= 0.99 * n) { p99 = v; break }
        }
        val hm = Bitmap.createBitmap(heat, w, h, Bitmap.Config.ARGB_8888)
        return Result(sumAbs / (n * 3), p99, mx, bias[0] / n, bias[1] / n, bias[2] / n, hm)
    }

    private fun channel(p: IntArray, c: Int): FloatArray {
        val shift = 16 - 8 * c
        return FloatArray(p.size) { ((p[it] shr shift) and 0xFF).toFloat() }
    }

    /** separable 5x5 box blur, edges clamped */
    private fun blur(src: FloatArray, w: Int, h: Int, r: Int = 2): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val k = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var s = 0f
                for (d in -r..r) s += src[row + (x + d).coerceIn(0, w - 1)]
                tmp[row + x] = s * k
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f
                for (d in -r..r) s += tmp[(y + d).coerceIn(0, h - 1) * w + x]
                out[y * w + x] = s * k
            }
        }
        return out
    }
}
