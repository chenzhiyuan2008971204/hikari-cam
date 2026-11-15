package com.hikari.vision

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A straight line found in the frame.
 * @param angleDeg direction of the line, -90..90 (0 = horizontal, + = falls to the right, y down)
 * @param x0,y0,x1,y1 the stretch of the line that is really there (fractions of the frame)
 * @param support share of that stretch that lies on real edges (0..1)
 */
data class Line(val angleDeg: Float, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val support: Float, val strength: Float) {
    val length get() = hypot(x1 - x0, y1 - y0)
    fun yAt(x: Float) = if (abs(x1 - x0) < 1e-6f) y0 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    fun xAt(y: Float) = if (abs(y1 - y0) < 1e-6f) x0 else x0 + (x1 - x0) * (y - y0) / (y1 - y0)
}

object Geometry {
    private const val TSTEP = 0.5f
    private const val NT = 360

    /**
     * Hough transform on strong edges. Each edge votes only near its own gradient direction, which
     * keeps textures (leaves, water) from inventing lines.
     */
    fun lines(f: Frame, perClass: Int = 10): List<Line> {
        val max = perClass * 3
        val quota = IntArray(3)   // horizontal-ish, vertical-ish, oblique: one class cannot crowd out the others
        val w = f.w; val h = f.h
        val (gx, gy) = f.grad; val mag = f.gradMag
        val sorted = mag.copyOf().also { it.sort() }
        val thr = maxOf(0.035f, sorted[(sorted.size * 0.86f).toInt()])
        val diag = hypot(w.toFloat(), h.toFloat())
        val nr = (2 * diag).toInt() + 2
        val acc = FloatArray(NT * nr)
        val cosT = FloatArray(NT) { cos(Math.toRadians((it * TSTEP).toDouble())).toFloat() }
        val sinT = FloatArray(NT) { sin(Math.toRadians((it * TSTEP).toDouble())).toFloat() }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val m = mag[i]; if (m < thr) continue
            var tn = Math.toDegrees(kotlin.math.atan2(gy[i].toDouble(), gx[i].toDouble())).toFloat()
            if (tn < 0) tn += 180f; if (tn >= 180f) tn -= 180f
            val c = (tn / TSTEP).roundToInt()
            for (d in -5..5) {
                val t = ((c + d) % NT + NT) % NT
                val rho = x * cosT[t] + y * sinT[t]
                acc[t * nr + (rho + diag).roundToInt()] += minOf(m, 0.3f)
            }
        }
        // peaks with non-maximum suppression
        data class Peak(val t: Int, val r: Int, val v: Float)
        val peaks = ArrayList<Peak>()
        for (t in 0 until NT) for (r in 2 until nr - 2) {
            val v = acc[t * nr + r]; if (v < 1.2f) continue
            var isMax = true
            loop@ for (dt in -4..4) for (dr in -3..3) {
                if (dt == 0 && dr == 0) continue
                val tt = ((t + dt) % NT + NT) % NT
                // crossing 0/180 flips the sign of rho
                val rr = if (t + dt < 0 || t + dt >= NT) (2 * diag).roundToInt() - (r + dr) else r + dr
                if (rr < 0 || rr >= nr) continue
                if (acc[tt * nr + rr] > v) { isMax = false; break@loop }
            }
            if (isMax) peaks += Peak(t, r, v)
        }
        peaks.sortByDescending { it.v }
        val out = ArrayList<Line>()
        for (p in peaks.take(max * 6)) {
            val la = abs(p.t * TSTEP - 90f)               // 0 = horizontal line, 90 = vertical line
            val cls = if (la <= 13f) 0 else if (la >= 66f) 1 else 2
            if (quota[cls] >= perClass) continue
            val ct = cosT[p.t]; val st = sinT[p.t]; val rho = p.r - diag
            // clip the line x*ct + y*st = rho to the frame
            val pts = ArrayList<FloatArray>()
            if (abs(st) > 1e-4f) { for (x in listOf(0f, (w - 1).toFloat())) { val y = (rho - x * ct) / st; if (y >= 0 && y <= h - 1) pts += floatArrayOf(x, y) } }
            if (abs(ct) > 1e-4f) { for (y in listOf(0f, (h - 1).toFloat())) { val x = (rho - y * st) / ct; if (x >= 0 && x <= w - 1) pts += floatArrayOf(x, y) } }
            if (pts.size < 2) continue
            val a = pts[0]; val b = pts.maxBy { hypot(it[0] - a[0], it[1] - a[1]) }
            val len = hypot(b[0] - a[0], b[1] - a[1]); if (len < 8f) continue
            // which samples along the line sit on an edge of the right orientation
            val n = len.toInt(); val on = BooleanArray(n + 1)
            for (k in 0..n) {
                val x = a[0] + (b[0] - a[0]) * k / n; val y = a[1] + (b[1] - a[1]) * k / n
                for (o in -1..1) {
                    val xi = (x + o * ct).roundToInt().coerceIn(0, w - 1); val yi = (y + o * st).roundToInt().coerceIn(0, h - 1)
                    val i = yi * w + xi
                    if (mag[i] >= thr * 0.7f && abs(gx[i] * ct + gy[i] * st) > 0.8f * mag[i]) { on[k] = true; break }
                }
            }
            // the line is the stretch that is really there: the run with the most edge samples,
            // bridging gaps of up to 10 % of the frame crossing (windows, posts, passers-by)
            val maxGap = n / 10 + 2
            var bestS = -1; var bestE = -1; var bestHits = 0
            var k = 0
            while (k <= n) {
                if (!on[k]) { k++; continue }
                var e = k; var hits = 1; var j = k + 1; var gap = 0
                while (j <= n && gap <= maxGap) { if (on[j]) { e = j; hits++; gap = 0 } else gap++; j++ }
                if (hits > bestHits) { bestHits = hits; bestS = k; bestE = e }
                k = e + 1
            }
            if (bestS < 0 || bestE - bestS < 6) continue
            val support = bestHits / (bestE - bestS + 1f)
            val sa = floatArrayOf(a[0] + (b[0] - a[0]) * bestS / n, a[1] + (b[1] - a[1]) * bestS / n)
            val sb = floatArrayOf(a[0] + (b[0] - a[0]) * bestE / n, a[1] + (b[1] - a[1]) * bestE / n)
            var ang = p.t * TSTEP - 90f            // line direction from its normal
            if (ang > 90f) ang -= 180f; if (ang <= -90f) ang += 180f
            val (p0, p1) = if (sa[0] < sb[0] || (sa[0] == sb[0] && sa[1] < sb[1])) sa to sb else sb to sa
            out += Line(ang, p0[0] / (w - 1), p0[1] / (h - 1), p1[0] / (w - 1), p1[1] / (h - 1), support, p.v)
            quota[cls]++
            if (out.size >= max) break
        }
        return out
    }

    /** Left-right mirror similarity of the picture, 0..1 (1 = perfectly symmetric). */
    fun symmetry(f: Frame): Float {
        val n = 48; val m = (n * f.h / f.w).coerceAtLeast(8)
        val g = FloatArray(n * m)
        for (y in 0 until m) for (x in 0 until n) g[y * n + x] = f.luma[(y * f.h / m) * f.w + (x * f.w / n)]
        var sa = 0.0; var sb = 0.0; var sab = 0.0; var saa = 0.0; var sbb = 0.0; var c = 0
        for (y in 0 until m) for (x in 0 until n / 2) {
            val a = g[y * n + x].toDouble(); val b = g[y * n + (n - 1 - x)].toDouble()
            sa += a; sb += b; sab += a * b; saa += a * a; sbb += b * b; c++
        }
        val cov = sab / c - sa / c * sb / c
        val va = saa / c - (sa / c) * (sa / c); val vb = sbb / c - (sb / c) * (sb / c)
        if (va < 1e-5 || vb < 1e-5) return 0f
        return (cov / sqrt(va * vb)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Saliency: how different each pixel is from the frame's border (the background prior), plus
     * local contrast. Returned blurred, 0..1.
     */
    fun saliency(f: Frame): FloatArray {
        val w = f.w; val h = f.h; val lab = f.lab
        val bw = maxOf(2, w / 10); val bh = maxOf(2, h / 10)
        var l = 0.0; var a = 0.0; var b = 0.0; var n = 0
        for (y in 0 until h) for (x in 0 until w) if (x < bw || x >= w - bw || y < bh || y >= h - bh) {
            val i = (y * w + x) * 3; l += lab[i]; a += lab[i + 1]; b += lab[i + 2]; n++
        }
        l /= n; a /= n; b /= n
        val s = FloatArray(w * h)
        for (i in 0 until w * h) {
            val dl = lab[i * 3] - l; val da = lab[i * 3 + 1] - a; val db = lab[i * 3 + 2] - b
            s[i] = sqrt(dl * dl * 0.6 + (da * da + db * db) * 2.2).toFloat() + f.gradMag[i] * 0.6f
        }
        val out = boxBlur(boxBlur(s, w, h, maxOf(2, w / 40)), w, h, maxOf(2, w / 40))
        val mx = out.max().coerceAtLeast(1e-6f)
        for (i in out.indices) out[i] /= mx
        return out
    }

    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        for (y in 0 until h) { var acc = 0f
            for (x in -r..r) acc += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) { tmp[y * w + x] = acc / (2 * r + 1); acc += src[y * w + (x + r + 1).coerceIn(0, w - 1)] - src[y * w + (x - r).coerceIn(0, w - 1)] } }
        for (x in 0 until w) { var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) { out[y * w + x] = acc / (2 * r + 1); acc += tmp[(y + r + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - r).coerceIn(0, h - 1) * w + x] } }
        return out
    }

    /** Largest connected region where [mask] is true: its box (fractions), pixel count. */
    fun largestRegion(mask: BooleanArray, w: Int, h: Int): Pair<Box, Int>? {
        val seen = BooleanArray(w * h); var best: Pair<Box, Int>? = null
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!mask[start] || seen[start]) continue
            var sp = 0; stack[sp++] = start; seen[start] = true
            var x0 = w; var x1 = 0; var y0 = h; var y1 = 0; var cnt = 0
            while (sp > 0) {
                val i = stack[--sp]; val x = i % w; val y = i / w; cnt++
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                if (x > 0 && mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && mask[i - w] && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && mask[i + w] && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            if (best == null || cnt > best.second) best = Box(x0 / w.toFloat(), y0 / h.toFloat(), (x1 + 1) / w.toFloat(), (y1 + 1) / h.toFloat()) to cnt
        }
        return best
    }
}
