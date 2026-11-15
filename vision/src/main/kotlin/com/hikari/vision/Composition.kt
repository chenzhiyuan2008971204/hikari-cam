package com.hikari.vision

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Something measured in the picture. null fields = not found; nothing is assumed. */
class Findings(
    val scene: SceneStats,
    /** the main subject: from the detector when it found one, else from saliency */
    val subject: Subject?,
    val horizon: Horizon?,
    val verticals: Verticals?,
    val leading: Leading?,
    val symmetry: Float,
    /** x of the visual weight, -0.5 (left) .. 0.5 (right) */
    val balanceX: Float,
    /** share of the frame that is calm, empty area */
    val emptyFrac: Float,
    /** sides (L, T, R, B) that are dark borders around a brighter middle */
    val frameSides: Int,
    val lines: List<Line>,
) {
    class Subject(val box: Box, val anchorX: Float, val anchorY: Float, val source: String, val confidence: Float, val yawDeg: Float = 0f, val isPerson: Boolean = false)
    class Horizon(val line: Line, val y: Float, val tiltDeg: Float, val confidence: Float)
    /** tiltDeg: + = tops lean right; converge: + = lines lean toward each other at the top (looking up) */
    class Verticals(val tiltDeg: Float, val converge: Float, val count: Int, val confidence: Float, val lines: List<Line>)
    class Leading(val vx: Float, val vy: Float, val lines: List<Line>, val confidence: Float)
}

/** One piece of advice, with what it is based on and what to draw. */
class Suggestion(
    val kind: Kind,
    val title: String,
    /** why this fits the picture (measured facts) */
    val evidence: String,
    /** where the subject / line is now */
    val current: String,
    /** what to do */
    val action: String,
    /** what gets better */
    val benefit: String,
    val confidence: Float,
    val overlays: List<Overlay> = emptyList(),
    val crop: Box? = null,
) {
    enum class Kind { LEVEL_HORIZON, HORIZON_PLACEMENT, STRAIGHTEN_VERTICALS, RULE_OF_THIRDS, CENTER, SYMMETRY, LEADING_LINES,
        NEGATIVE_SPACE, EDGE_ROOM, HEADROOM, LEAD_ROOM, BALANCE, FRAME_IN_FRAME, DIAGONAL }
}

sealed class Overlay {
    class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val role: String) : Overlay()
    class Rect(val box: Box, val role: String) : Overlay()
    class Point(val x: Float, val y: Float, val role: String) : Overlay()
}

class CompositionReport(val findings: Findings, val suggestions: List<Suggestion>, val note: String)

object CompositionAnalyzer {
    /** set in tests to see why candidates were accepted or dropped */
    internal var trace: StringBuilder? = null
    private fun pct(v: Float) = "${(v * 100).roundToInt()}%"
    private fun deg(v: Float) = "%.1f°".format(abs(v))

    fun describe(x: Float, y: Float): String {
        val hx = when { x < 0.2f -> "最左側"; x < 0.42f -> "偏左"; x <= 0.58f -> "中央"; x <= 0.8f -> "偏右"; else -> "最右側" }
        val vy = when { y < 0.2f -> "頂部"; y < 0.42f -> "偏上"; y <= 0.58f -> "中間"; y <= 0.8f -> "偏下"; else -> "底部" }
        return "水平$hx、垂直$vy（x ${pct(x)}，y ${pct(y)}）"
    }

    fun findings(f: Frame, detections: List<Detection> = emptyList()): Findings {
        val scene = SceneAnalyzer.analyze(f, detections)
        val lines = Geometry.lines(f)
        val sal = Geometry.saliency(f)
        return Findings(scene, subject(f, sal, detections), horizon(f, scene, lines), verticals(lines), leading(lines),
            Geometry.symmetry(f), balance(f, sal), empty(f), frameSides(f), lines)
    }

    // ------------------------------------------------------------------ measurements

    private fun subject(f: Frame, sal: FloatArray, det: List<Detection>): Findings.Subject? {
        val face = det.filter { it.kind == Detection.Kind.FACE }.maxByOrNull { it.box.area }
        if (face != null) {
            val body = det.filter { it.kind != Detection.Kind.FACE && face.box.cx in it.box.left..it.box.right && face.box.cy in it.box.top..it.box.bottom }
                .maxByOrNull { it.box.area }
            return Findings.Subject(body?.box ?: face.box, face.box.cx, face.box.top + face.box.h * 0.42f, "人臉偵測", 0.9f, face.yawDeg, true)
        }
        val obj = det.filter { it.kind != Detection.Kind.FACE }.maxByOrNull { it.box.area }
        if (obj != null && obj.box.area in 0.004f..0.85f)
            return Findings.Subject(obj.box, obj.box.cx, obj.box.top + obj.box.h * 0.45f, "物件偵測", 0.7f, 0f, obj.kind == Detection.Kind.PERSON)
        // saliency: the largest region that stands out from the border background
        val n = sal.size; var mean = 0.0; for (v in sal) mean += v; mean /= n
        var sd = 0.0; for (v in sal) sd += (v - mean) * (v - mean); sd = kotlin.math.sqrt(sd / n)
        val thr = (mean + 1.1 * sd).toFloat()
        val mask = BooleanArray(n) { sal[it] > thr }
        val (box, cnt) = Geometry.largestRegion(mask, f.w, f.h) ?: return null
        val areaFrac = cnt / n.toFloat()
        // saliency can only speak for a compact object that stands clear of the frame edge
        if (areaFrac < 0.004f || box.area > 0.35f || cnt / (box.area * n) < 0.3f) return null
        if (minOf(box.left, box.top, 1f - box.right, 1f - box.bottom) < 0.02f) return null
        var inside = 0f; var total = 0f; var cx = 0f; var cy = 0f
        for (y in 0 until f.h) for (x in 0 until f.w) {
            val i = y * f.w + x; if (!mask[i]) continue
            total += sal[i]
            val fx = x / f.w.toFloat(); val fy = y / f.h.toFloat()
            if (fx >= box.left && fx <= box.right && fy >= box.top && fy <= box.bottom) { inside += sal[i]; cx += fx * sal[i]; cy += fy * sal[i] }
        }
        val concentration = if (total > 0f) inside / total else 0f
        // confident only when one region clearly dominates
        val conf = ((concentration - 0.5f) / 0.4f).coerceIn(0f, 1f) * 0.65f
        if (conf < 0.25f) return null
        return Findings.Subject(box, cx / inside, cy / inside, "顯著區域分析", conf)
    }

    /**
     * The horizon: the strongest straight, level, edge-to-edge line with sky above it and none
     * below. Coastlines, rooflines and tree lines are ruled out by asking the left and the right
     * half of the line to agree on its slope. When in doubt, no horizon is reported.
     */
    private fun horizon(f: Frame, scene: SceneStats, lines: List<Line>): Findings.Horizon? {
        val (_, gy) = f.grad
        var best: Findings.Horizon? = null; var bestScore = 0f
        for (l in lines) {
            if (abs(l.angleDeg) > 7f || l.x1 - l.x0 < 0.9f || l.support < 0.8f) continue
            val yMid = l.yAt(0.5f)
            if (yMid < 0.06f || yMid > 0.94f) continue          // picture borders
            // sky in the area above, none in the area below
            var up = 0; var upN = 0; var down = 0; var downN = 0
            for (y in 0 until f.h step 2) for (x in 0 until f.w step 2) {
                val ly = l.yAt(x / (f.w - 1f)) * (f.h - 1)
                if (y < ly - 2) { upN++; if (scene.sky[y * f.w + x]) up++ } else if (y > ly + 2) { downN++; if (scene.sky[y * f.w + x]) down++ }
            }
            if (upN == 0 || downN == 0) continue
            val skyUp = up / upN.toFloat(); val skyDown = down / downN.toFloat()
            trace?.append("  cand y %.3f ang %+.1f sup %.2f skyUp %.2f skyDown %.3f".format(yMid, l.angleDeg, l.support, skyUp, skyDown))
            if (skyUp < 0.25f || skyDown > 0.03f) { trace?.append(" -> sky\n"); continue }
            // both halves of the line must sit on a real edge, and fit better than the same line turned a little:
            // a coastline or roofline only matches on one side
            fun fit(angle: Float, from: Float, to: Float): Float {
                val slope = kotlin.math.tan(Math.toRadians(angle.toDouble())).toFloat() * (f.w - 1) / (f.h - 1)
                var hit = 0; var cols = 0
                for (xi in (from * (f.w - 1)).toInt()..(to * (f.w - 1)).toInt()) {
                    cols++
                    val y0 = ((yMid + (xi / (f.w - 1f) - 0.5f) * slope) * (f.h - 1)).roundToInt()
                    for (y in maxOf(1, y0 - 1)..minOf(f.h - 2, y0 + 1)) if (abs(gy[y * f.w + xi]) > 0.03f) { hit++; break }
                }
                return hit / maxOf(1, cols).toFloat()
            }
            val mid = (l.x0 + l.x1) / 2f
            var ok = true
            for ((from, to) in listOf(l.x0 to mid, mid to l.x1)) {
                val s0 = fit(l.angleDeg, from, to)
                if (s0 < 0.6f || s0 < maxOf(fit(l.angleDeg + 1.5f, from, to), fit(l.angleDeg - 1.5f, from, to)) - 0.05f) ok = false
            }
            trace?.append(" halves ok=$ok\n")
            if (!ok) continue
            val score = l.strength * l.support
            if (score > bestScore) {
                bestScore = score
                val conf = (0.45f + 0.35f * (l.support - 0.8f) / 0.2f + 0.2f * minOf(1f, skyUp / 0.6f)).coerceIn(0f, 1f)
                best = Findings.Horizon(l, yMid, l.angleDeg, conf)
            }
        }
        return best
    }

    private fun tiltOf(l: Line) = if (l.angleDeg > 0) l.angleDeg - 90f else l.angleDeg + 90f

    private fun verticals(lines: List<Line>): Findings.Verticals? {
        val cand = lines.filter { abs(tiltOf(it)) <= 24f && abs(it.y1 - it.y0) >= 0.25f && it.support >= 0.3f }
            .sortedByDescending { it.support * abs(it.y1 - it.y0) }
        // one edge often comes back as several lines half a degree apart: keep the best of each
        val v = ArrayList<Line>()
        for (l in cand) {
            val x = (l.x0 + l.x1) / 2f
            if (v.none { abs((it.x0 + it.x1) / 2f - x) < 0.04f && abs(tiltOf(it) - tiltOf(l)) < 4f }) v += l
        }
        if (v.size < 3) return null
        val w = v.map { it.support * abs(it.y1 - it.y0) }
        val xs = v.map { (it.x0 + it.x1) / 2f }; val ts = v.map { tiltOf(it) }
        // tilt = a + b * (x - 0.5). b = 0 is a level camera or a rolled one; b != 0 is looking up or down
        // (the verticals converge). Roof edges, gables and leaning poles are steep too but fit neither,
        // so the model is chosen by agreement (RANSAC), and the simpler one wins unless the other is clearly better.
        fun score(a: Float, b: Float): Float { var sc = 0f; for (k in v.indices) if (abs(ts[k] - a - b * (xs[k] - 0.5f)) <= 2.5f) sc += w[k]; return sc }
        var fa = 0f; var fs = -1f
        for (k in v.indices) { val sc = score(ts[k], 0f); if (sc > fs) { fs = sc; fa = ts[k] } }
        var sa = 0f; var sb = 0f; var ss = -1f
        for (p in v.indices) for (q in p + 1 until v.size) {
            if (abs(xs[p] - xs[q]) < 0.2f) continue
            val b = (ts[p] - ts[q]) / (xs[p] - xs[q]); if (abs(b) > 45f) continue
            val a = ts[p] - b * (xs[p] - 0.5f)
            val sc = score(a, b); if (sc > ss) { ss = sc; sa = a; sb = b }
        }
        val sloped = ss >= fs * 1.25f && abs(sb) >= 3f
        val a = if (sloped) sa else fa; val b = if (sloped) sb else 0f
        val inl = v.indices.filter { abs(ts[it] - a - b * (xs[it] - 0.5f)) <= 3f }
        if (inl.size < 3) return null
        // more than a few degrees off at the centre is not a tilted camera, it is something that is not a vertical
        if (abs(a) > 8f) return null
        // a claim about the whole frame needs lines spread over it
        val span = inl.maxOf { xs[it] } - inl.minOf { xs[it] }
        if (span < 0.25f) return null
        val wi = inl.sumOf { w[it].toDouble() }.toFloat()
        val spread = inl.sumOf { (abs(ts[it] - a - b * (xs[it] - 0.5f)) * w[it]).toDouble() }.toFloat() / wi
        val agree = wi / w.sum()
        val conf = ((inl.size - 2) / 4f).coerceIn(0f, 1f) * (1f - (spread / 6f).coerceIn(0f, 0.5f)) * (0.5f + 0.5f * agree)
        return Findings.Verticals(a, -b, inl.size, conf, inl.map { v[it] })
    }

    private fun leading(lines: List<Line>): Findings.Leading? {
        // long, well supported obliques, rising and falling ones that meet inside the frame
        trace?.let { t -> for (l in lines.filter { abs(it.angleDeg) in 14f..64f }) t.append("  oblique ang %+.1f len %.2f sup %.2f\n".format(l.angleDeg, l.length, l.support)) }
        val ob = lines.filter { abs(it.angleDeg) in 20f..64f && it.length >= 0.35f && it.support >= 0.6f }
        if (ob.none { it.angleDeg > 0 } || ob.none { it.angleDeg < 0 }) return null
        val pts = ArrayList<FloatArray>(); val used = LinkedHashSet<Line>()
        for (i in ob.indices) for (j in i + 1 until ob.size) {
            val a = ob[i]; val b = ob[j]
            if (a.angleDeg * b.angleDeg > 0f) continue
            val d = (a.x0 - a.x1) * (b.y0 - b.y1) - (a.y0 - a.y1) * (b.x0 - b.x1)
            if (abs(d) < 1e-6f) continue
            val px = ((a.x0 * a.y1 - a.y0 * a.x1) * (b.x0 - b.x1) - (a.x0 - a.x1) * (b.x0 * b.y1 - b.y0 * b.x1)) / d
            val py = ((a.x0 * a.y1 - a.y0 * a.x1) * (b.y0 - b.y1) - (a.y0 - a.y1) * (b.x0 * b.y1 - b.y0 * b.x1)) / d
            if (px in 0f..1f && py in 0f..1f) { pts += floatArrayOf(px, py); used += a; used += b }
        }
        if (pts.isEmpty()) return null
        val mx = pts.map { it[0] }.sorted()[pts.size / 2]; val my = pts.map { it[1] }.sorted()[pts.size / 2]
        val near = pts.count { hypot(it[0] - mx, it[1] - my) < 0.12f }
        val agree = near / pts.size.toFloat()
        val conf = (0.3f + 0.15f * minOf(used.size, 4)) * agree * used.map { it.support }.average().toFloat().coerceIn(0.5f, 1f) / 0.75f
        if (conf < 0.4f) return null
        return Findings.Leading(mx, my, used.toList(), conf.coerceAtMost(0.9f))
    }

    private fun balance(f: Frame, sal: FloatArray): Float {
        var sx = 0f; var s = 0f
        for (y in 0 until f.h) for (x in 0 until f.w) { val v = sal[y * f.w + x]; sx += v * (x / (f.w - 1f) - 0.5f); s += v }
        return if (s > 0f) sx / s else 0f
    }

    private fun empty(f: Frame): Float {
        val gw = 16; val gh = 12; var calm = 0
        for (gy in 0 until gh) for (gx in 0 until gw) {
            var e = 0f; var n = 0
            for (y in gy * f.h / gh until (gy + 1) * f.h / gh) for (x in gx * f.w / gw until (gx + 1) * f.w / gw) { e += f.gradMag[y * f.w + x]; n++ }
            if (n > 0 && e / n < 0.012f) calm++
        }
        return calm / (gw * gh).toFloat()
    }

    private fun frameSides(f: Frame): Int {
        fun mean(x0: Float, y0: Float, x1: Float, y1: Float): Float {
            var s = 0f; var n = 0
            for (y in (y0 * f.h).toInt() until (y1 * f.h).toInt()) for (x in (x0 * f.w).toInt() until (x1 * f.w).toInt()) { s += f.luma[y * f.w + x]; n++ }
            return if (n > 0) s / n else 0f
        }
        val mid = mean(0.3f, 0.3f, 0.7f, 0.7f)
        var sides = 0
        if (mid - mean(0f, 0.1f, 0.1f, 0.9f) > 0.3f) sides++
        if (mid - mean(0.1f, 0f, 0.9f, 0.1f) > 0.3f) sides++
        if (mid - mean(0.9f, 0.1f, 1f, 0.9f) > 0.3f) sides++
        if (mid - mean(0.1f, 0.9f, 0.9f, 1f) > 0.3f) sides++
        return sides
    }

    // ------------------------------------------------------------------ advice

    /** Largest same-aspect crop that puts the anchor (ax, ay) at (tx, ty); null when it would cut too much. */
    fun cropFor(ax: Float, ay: Float, tx: Float, ty: Float, keep: Box?): Box? {
        var s = 1f
        if (tx > 0f) s = minOf(s, ax / tx); if (tx < 1f) s = minOf(s, (1f - ax) / (1f - tx))
        if (ty > 0f) s = minOf(s, ay / ty); if (ty < 1f) s = minOf(s, (1f - ay) / (1f - ty))
        if (s < 0.55f) return null
        val x0 = (ax - tx * s).coerceIn(0f, 1f - s); val y0 = (ay - ty * s).coerceIn(0f, 1f - s)
        val b = Box(x0, y0, x0 + s, y0 + s)
        if (keep != null && (keep.left < b.left - 0.01f || keep.right > b.right + 0.01f || keep.top < b.top - 0.01f || keep.bottom > b.bottom + 0.01f)) return null
        return if (s > 0.985f) null else b
    }

    private fun thirds() = listOf(Overlay.Seg(1f / 3, 0f, 1f / 3, 1f, "thirds"), Overlay.Seg(2f / 3, 0f, 2f / 3, 1f, "thirds"),
        Overlay.Seg(0f, 1f / 3, 1f, 1f / 3, "thirds"), Overlay.Seg(0f, 2f / 3, 1f, 2f / 3, "thirds"))

    fun analyze(f: Frame, detections: List<Detection> = emptyList()): CompositionReport {
        val fd = findings(f, detections)
        val out = ArrayList<Suggestion>()
        val s = fd.subject; val hz = fd.horizon; val vt = fd.verticals; val ld = fd.leading
        val symmetric = fd.symmetry > 0.82f

        // 1. tilted horizon
        if (hz != null && hz.confidence >= 0.45f && abs(hz.tiltDeg) >= 1.2f) {
            val dir = if (hz.tiltDeg > 0) "右邊偏低" else "左邊偏低"
            out += Suggestion(Suggestion.Kind.LEVEL_HORIZON, "校正水平線",
                "偵測到一條橫跨畫面 ${pct(hz.line.x1 - hz.line.x0)} 的地平線（上方是天空），傾斜 ${deg(hz.tiltDeg)}，$dir。",
                "地平線在畫面高度 ${pct(hz.y)} 處，傾斜 ${deg(hz.tiltDeg)}。",
                "把畫面${if (hz.tiltDeg > 0) "逆時針" else "順時針"}旋轉 ${deg(hz.tiltDeg)}（拍攝時把手機${if (hz.tiltDeg > 0) "右側抬高" else "左側抬高"}）。",
                "水平線放平後畫面更穩定，不會有傾倒的感覺。",
                (hz.confidence * (0.6f + minOf(abs(hz.tiltDeg), 6f) / 15f)).coerceAtMost(0.95f),
                listOf(Overlay.Seg(hz.line.x0, hz.line.y0, hz.line.x1, hz.line.y1, "horizon"), Overlay.Seg(0f, hz.y, 1f, hz.y, "target")))
        }
        // 2. horizon cutting the frame in half
        if (hz != null && hz.confidence >= 0.5f && abs(hz.y - 0.5f) < 0.07f) {
            val skyInteresting = fd.scene.blueSkyFrac > 0.12f || fd.scene.types[SceneType.SUNSET]!! > 0.4f
            val ty = if (skyInteresting) 2f / 3 else 1f / 3
            out += Suggestion(Suggestion.Kind.HORIZON_PLACEMENT, if (skyInteresting) "低地平線（多留天空）" else "高地平線（多留地面）",
                "地平線位於畫面高度 ${pct(hz.y)}，幾乎把畫面切成上下兩半；" + (if (skyInteresting) "天空佔 ${pct(fd.scene.skyFrac)} 且有顏色層次，值得多留。" else "天空較單調，地面的內容比較多。"),
                "地平線在 ${pct(hz.y)}（正中間 ±7% 內）。",
                if (skyInteresting) "鏡頭往上抬，讓地平線落在下三分之一線（約 67%）。" else "鏡頭往下壓，讓地平線落在上三分之一線（約 33%）。",
                "避免畫面被對半切開，主次更分明。", hz.confidence * 0.75f,
                listOf(Overlay.Seg(0f, hz.y, 1f, hz.y, "horizon"), Overlay.Seg(0f, ty, 1f, ty, "target")))
        }
        // 3. leaning verticals
        if (vt != null && vt.confidence >= 0.4f && abs(vt.tiltDeg) >= 2f && abs(vt.converge) < 6f) {
            out += Suggestion(Suggestion.Kind.STRAIGHTEN_VERTICALS, "校正建築垂直線",
                "找到 ${vt.count} 條接近垂直的長直線，整體向${if (vt.tiltDeg > 0) "右" else "左"}傾斜 ${deg(vt.tiltDeg)}。",
                "垂直線平均傾斜 ${deg(vt.tiltDeg)}。",
                "把畫面${if (vt.tiltDeg > 0) "逆時針" else "順時針"}旋轉 ${deg(vt.tiltDeg)}，讓建築的邊線與畫面邊緣平行。",
                "建築不再歪斜，畫面更端正。", (vt.confidence * (0.55f + minOf(abs(vt.tiltDeg), 6f) / 14f)).coerceAtMost(0.9f),
                vt.lines.take(6).map { Overlay.Seg(it.x0, it.y0, it.x1, it.y1, "vertical") })
        } else if (vt != null && vt.confidence >= 0.5f && abs(vt.converge) >= 6f) {
            out += Suggestion(Suggestion.Kind.STRAIGHTEN_VERTICALS, "減少垂直線匯聚",
                "找到 ${vt.count} 條垂直線，左右兩側往${if (vt.converge > 0) "上" else "下"}方匯聚（兩側相差約 ${deg(vt.converge)}），是${if (vt.converge > 0) "仰拍" else "俯拍"}造成的透視。",
                "垂直線左右不平行，差 ${deg(vt.converge)}。",
                if (vt.converge > 0) "退後一點並把手機立直（不要往上仰），或之後用透視校正。" else "把手機立直（不要往下俯）。",
                "建築看起來不會向後倒。", vt.confidence * 0.7f, vt.lines.take(6).map { Overlay.Seg(it.x0, it.y0, it.x1, it.y1, "vertical") })
        }
        // 4. subject placement
        if (s != null) {
            val box = s.box
            val margin = minOf(box.left, 1f - box.right, box.top, 1f - box.bottom)
            val small = box.area < 0.3f
            val centredX = abs(s.anchorX - 0.5f) < 0.07f
            val where = describe(s.anchorX, s.anchorY)
            val subjOverlay = listOf(Overlay.Rect(box, "subject"), Overlay.Point(s.anchorX, s.anchorY, "anchor"))
            if (margin < 0.025f && small && box.area > 0.01f) {
                val side = when (margin) { box.left -> "左"; 1f - box.right -> "右"; box.top -> "上"; else -> "下" }
                out += Suggestion(Suggestion.Kind.EDGE_ROOM, "主體離邊緣太近",
                    "主體（${s.source}）的${side}緣距離畫面邊緣只有 ${pct(margin)}。", "主體在$where，${side}側留白不足。",
                    "鏡頭往${side}移，或退後一點，讓主體四周至少留 5% 的空間。", "主體不會被邊緣切到或擠壓。", s.confidence * 0.8f, subjOverlay)
            }
            if (s.isPerson && s.source == "人臉偵測") {
                val headTop = box.top
                if (s.anchorY > 0.5f && box.area < 0.5f) {
                    val crop = cropFor(s.anchorX, s.anchorY, s.anchorX.coerceIn(0.3f, 0.7f), 1f / 3, null)
                    out += Suggestion(Suggestion.Kind.HEADROOM, "眼睛放在上三分線",
                        "偵測到人臉，眼睛位於畫面高度 ${pct(s.anchorY)}，頭頂上方空了 ${pct(headTop)}。", "人物在$where。",
                        "鏡頭往下壓，讓眼睛落在上三分之一線（約 33%）。", "減少頭頂多餘的空白，視線集中在臉上。", s.confidence * 0.85f,
                        subjOverlay + Overlay.Seg(0f, 1f / 3, 1f, 1f / 3, "target"), crop)
                }
                if (abs(s.yawDeg) > 15f) {
                    val lookRight = s.yawDeg > 0
                    val room = if (lookRight) 1f - s.anchorX else s.anchorX
                    if (room < 0.5f) {
                        val tx = if (lookRight) 1f / 3 else 2f / 3
                        out += Suggestion(Suggestion.Kind.LEAD_ROOM, "視線方向留白",
                            "人物的臉朝${if (lookRight) "右" else "左"}（約 ${deg(s.yawDeg)}），但視線前方只有 ${pct(room)} 的空間。", "人物在$where。",
                            "把人物放到${if (lookRight) "左" else "右"}三分之一線，讓視線前方留出空間。", "畫面有延伸感，不會覺得人物撞到邊框。", s.confidence * 0.8f,
                            subjOverlay + thirds(), cropFor(s.anchorX, s.anchorY, tx, s.anchorY.coerceIn(0.3f, 0.6f), box))
                    }
                }
            }
            if (symmetric && abs(s.anchorX - 0.5f) < 0.12f) {
                out += Suggestion(Suggestion.Kind.SYMMETRY, "對稱構圖",
                    "畫面左右相似度 ${pct(fd.symmetry)}，主體在水平 ${pct(s.anchorX)}，接近中軸。", "主體在$where。",
                    if (abs(s.anchorX - 0.5f) > 0.02f) "鏡頭往${if (s.anchorX > 0.5f) "右" else "左"}微調 ${pct(abs(s.anchorX - 0.5f))}，讓主體對準正中軸。" else "保持現在的位置，確認左右邊緣對稱。",
                    "強化對稱帶來的穩定與莊重感。", (0.5f + 0.4f * (fd.symmetry - 0.82f) / 0.18f) * s.confidence.coerceAtLeast(0.6f),
                    subjOverlay + Overlay.Seg(0.5f, 0f, 0.5f, 1f, "target"), cropFor(s.anchorX, s.anchorY, 0.5f, s.anchorY, box))
            } else if (small && centredX && !symmetric && !(s.isPerson && box.area > 0.06f) && margin >= 0.025f) {
                // room decides the side: put the subject on the third away from the emptier / lighter side
                val side = if (fd.balanceX > 0.03f) 1 else if (fd.balanceX < -0.03f) -1 else if (s.anchorX >= 0.5f) 1 else -1
                val tx = if (side > 0) 2f / 3 else 1f / 3
                val ty = listOf(1f / 3, 2f / 3).minBy { abs(it - s.anchorY) }
                out += Suggestion(Suggestion.Kind.RULE_OF_THIRDS, "三分法",
                    "主體只佔畫面 ${pct(box.area)}，卻放在正中央（水平 ${pct(s.anchorX)}），兩側的空間沒有被利用；畫面左右並不對稱（相似度 ${pct(fd.symmetry)}）。",
                    "主體在$where。", "把主體移到${if (side > 0) "右" else "左"}三分之一線附近（水平 ${pct(tx)}）。",
                    "畫面更有動感，並留出呼吸空間。", s.confidence * 0.7f,
                    subjOverlay + thirds() + Overlay.Point(tx, ty, "target"), cropFor(s.anchorX, s.anchorY, tx, ty, box))
            }
            if (box.area < 0.06f && fd.emptyFrac > 0.5f && out.none { it.kind == Suggestion.Kind.RULE_OF_THIRDS }) {
                val tx = listOf(1f / 3, 2f / 3).minBy { abs(it - s.anchorX) }; val ty = listOf(1f / 3, 2f / 3).minBy { abs(it - s.anchorY) }
                out += Suggestion(Suggestion.Kind.NEGATIVE_SPACE, "負空間（留白）",
                    "畫面有 ${pct(fd.emptyFrac)} 是平靜、沒有細節的區域，主體只佔 ${pct(box.area)}。", "主體在$where。",
                    if (hypot(s.anchorX - tx, s.anchorY - ty) > 0.08f) "把主體放到最近的三分點（${pct(tx)}, ${pct(ty)}），讓留白集中在一側。" else "保持主體在三分點上，留白已經集中在一側。",
                    "大面積留白讓小主體更突出，畫面更簡潔。", s.confidence * 0.65f, subjOverlay + Overlay.Point(tx, ty, "target"),
                    cropFor(s.anchorX, s.anchorY, tx, ty, box))
            }
        }
        // 5. leading lines
        // (not for close portraits: hat brims and collars are not leading lines)
        val closePortrait = s != null && s.isPerson && s.box.area > 0.06f
        if (ld != null && !closePortrait && ld.confidence >= 0.45f && ld.vx in 0f..1f && ld.vy in 0f..1f) {
            val near = s != null && hypot(s.anchorX - ld.vx, s.anchorY - ld.vy) < 0.15f
            out += Suggestion(Suggestion.Kind.LEADING_LINES, "引導線",
                "找到 ${ld.lines.size} 條斜向長直線，往畫面內 ${describe(ld.vx, ld.vy)} 匯聚。",
                if (s != null) "主體在${describe(s.anchorX, s.anchorY)}，" + (if (near) "正好在匯聚點附近。" else "離匯聚點 ${pct(hypot(s.anchorX - ld.vx, s.anchorY - ld.vy))}。") else "匯聚點附近沒有明確主體。",
                if (near) "保持主體在線條的匯聚點上。" else "移動位置或等待主體走到線條匯聚的地方，讓線條指向主體。",
                "線條把視線帶向主體，增加景深與方向感。", ld.confidence * (if (near) 0.9f else 0.75f),
                ld.lines.take(5).map { Overlay.Seg(it.x0, it.y0, it.x1, it.y1, "leading") } + Overlay.Point(ld.vx, ld.vy, "target"))
        }
        // 6. frame within a frame
        if (fd.frameSides >= 3 && s != null) {
            out += Suggestion(Suggestion.Kind.FRAME_IN_FRAME, "框中框",
                "畫面有 ${fd.frameSides} 個邊是明顯較暗的前景，包圍著較亮的中間區域。", "主體在${describe(s.anchorX, s.anchorY)}。",
                "讓主體完整落在亮的框內，暗框保持在四周。", "前景的框把視線集中到主體，並增加層次。", 0.55f, listOf(Overlay.Rect(s.box, "subject")))
        }
        // 7. lopsided weight without a clear subject
        if (s == null && abs(fd.balanceX) > 0.16f && !symmetric) {
            out += Suggestion(Suggestion.Kind.BALANCE, "視覺平衡",
                "畫面的視覺重量集中在${if (fd.balanceX > 0) "右" else "左"}側（重心偏離中心 ${pct(abs(fd.balanceX))}），找不到單一明確主體。",
                "重心在水平 ${pct(0.5f + fd.balanceX)}。", "鏡頭往${if (fd.balanceX > 0) "右" else "左"}轉一些，或在另一側加入前景元素來平衡。",
                "畫面不會往一邊傾斜。", 0.4f)
        }
        val ranked = out.sortedByDescending { it.confidence }.filter { it.confidence >= 0.3f }.take(3)
        val note = when {
            ranked.isNotEmpty() -> ""
            s == null && hz == null && vt == null && ld == null -> "畫面中找不到足夠明確的主體或線條，無法給出有根據的構圖建議。"
            else -> "目前的構圖沒有偵測到明顯需要調整的地方。"
        }
        return CompositionReport(fd, ranked, note)
    }
}
