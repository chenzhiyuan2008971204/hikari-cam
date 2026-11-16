package com.hikari.cam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hikari.cam.camera.Subject
import kotlin.math.abs
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Composition guides drawn over the live preview: where to put the subject and how to hold the
 * phone. All coordinates are fractions of the preview, so a guide fits every aspect ratio.
 */
class CompositionGuide(val id: String, val name: String, val hint: String, val draw: DrawScope.(Float) -> Unit)

private fun DrawScope.pen(scale: Float) = Stroke(width = 2.2f * scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * scale, 9f * scale)))
private fun DrawScope.solid(scale: Float) = Stroke(width = 2f * scale)
private val ink = Color.White.copy(alpha = 0.92f)
private val soft = Color.White.copy(alpha = 0.55f)

private fun DrawScope.subject(l: Float, t: Float, r: Float, b: Float, s: Float) {
    drawRoundRect(ink, Offset(size.width * l, size.height * t), Size(size.width * (r - l), size.height * (b - t)),
        CornerRadius(size.width * 0.06f), style = solid(s))
}

private fun DrawScope.hline(y: Float, s: Float, dashed: Boolean = true, color: Color = ink) =
    drawLine(color, Offset(0f, size.height * y), Offset(size.width, size.height * y), strokeWidth = 2f * s,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f * s, 9f * s)) else null)

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, s: Float, color: Color = ink) =
    drawLine(color, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), strokeWidth = 2f * s,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * s, 9f * s)))

private fun DrawScope.dot(x: Float, y: Float, s: Float) =
    drawCircle(ink, radius = 5f * s, center = Offset(size.width * x, size.height * y))

val COMPOSITIONS: List<CompositionGuide> = listOf(
    CompositionGuide("smart", "智慧構圖", "自動找出主體，帶你把它放到最近的三分點") { s ->
        for (f in listOf(1f / 3, 2f / 3)) { line(f, 0f, f, 1f, s, soft.copy(alpha = 0.35f)); hline(f, s, color = soft.copy(alpha = 0.35f)) }
    },
    CompositionGuide("thirds", "三分法", "主體放在任一交叉點上，畫面最穩（會即時提示移動方向）") { s ->
        for (f in listOf(1f / 3, 2f / 3)) { line(f, 0f, f, 1f, s, soft); hline(f, s, color = soft) }
        for (x in listOf(1f / 3, 2f / 3)) for (y in listOf(1f / 3, 2f / 3)) dot(x, y, s)
    },
    CompositionGuide("portrait_full", "人物全身", "手機放低到腰部高度，人站在右側線上，腿會更長") { s ->
        line(2f / 3, 0f, 2f / 3, 1f, s, soft)
        subject(0.56f, 0.24f, 0.78f, 0.93f, s)
        drawCircle(ink, radius = size.width * 0.045f, center = Offset(size.width * 0.67f, size.height * 0.31f), style = solid(s))
    },
    CompositionGuide("portrait_half", "人物半身", "胸口高度拍，眼睛對齊上方三分線") { s ->
        hline(1f / 3, s, color = soft)
        subject(0.28f, 0.2f, 0.72f, 1.02f, s)
        dot(0.42f, 1f / 3, s); dot(0.58f, 1f / 3, s)
    },
    CompositionGuide("small_subject", "小人大景", "人物放在下方，留大片天空或風景") { s ->
        hline(0.74f, s, color = soft)
        subject(0.44f, 0.6f, 0.56f, 0.86f, s)
    },
    CompositionGuide("center", "中心構圖", "主體放在正中央，四周留白") { s ->
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(ink, radius = min(size.width, size.height) * 0.24f, center = c, style = pen(s))
        drawLine(ink, Offset(c.x - 18f * s, c.y), Offset(c.x + 18f * s, c.y), 2f * s)
        drawLine(ink, Offset(c.x, c.y - 18f * s), Offset(c.x, c.y + 18f * s), 2f * s)
    },
    CompositionGuide("symmetry", "對稱", "對準中線，左右或上下對稱") { s ->
        line(0.5f, 0f, 0.5f, 1f, s)
        for (y in listOf(0.3f, 0.7f)) { line(0.2f, y, 0.4f, y, s, soft); line(0.6f, y, 0.8f, y, s, soft) }
    },
    CompositionGuide("spiral", "黃金螺旋", "主體放在螺旋收尾處，視線會被帶過去") { s ->
        val p = Path()
        val cx = size.width * 0.62f
        val cy = size.height * 0.62f
        val a = min(size.width, size.height) * 0.02f
        var first = true
        var t = 0f
        while (t < 4.2f * Math.PI) {
            val r = a * Math.exp(0.306 * t).toFloat()
            val x = cx + r * cos(t + Math.PI).toFloat()
            val y = cy + r * sin(t + Math.PI).toFloat()
            if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            t += 0.05f
        }
        drawPath(p, ink, style = pen(s))
        dot(0.62f, 0.62f, s)
    },
    CompositionGuide("diagonal", "對角線", "讓道路、欄杆或人物沿對角線延伸") { s ->
        line(0f, 1f, 1f, 0f, s)
        line(0f, 0.6f, 0.6f, 0f, s, soft)
        line(0.4f, 1f, 1f, 0.4f, s, soft)
    },
    CompositionGuide("triangle", "三角形", "三個主體或山形構成穩定的三角") { s ->
        val p = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.22f)
            lineTo(size.width * 0.14f, size.height * 0.82f)
            lineTo(size.width * 0.86f, size.height * 0.82f)
            close()
        }
        drawPath(p, ink, style = pen(s))
    },
    CompositionGuide("one_point", "一點透視", "走廊、道路、橋：消失點放在中央") { s ->
        val vx = 0.5f
        val vy = 0.45f
        for ((x, y) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) line(x, y, vx, vy, s, soft)
        drawRect(ink, Offset(size.width * 0.38f, size.height * 0.36f), Size(size.width * 0.24f, size.height * 0.18f), style = pen(s))
        dot(vx, vy, s)
    },
    CompositionGuide("two_point", "兩點透視", "建築轉角放在中間，兩側線條往外延伸") { s ->
        line(0.5f, 0.18f, 0.5f, 0.88f, s)
        for (y in listOf(0.18f, 0.88f)) { line(0.5f, y, 0f, 0.5f, s, soft); line(0.5f, y, 1f, 0.5f, s, soft) }
    },
    CompositionGuide("frame", "框中框", "用門窗、樹枝、拱門當作畫框") { s ->
        drawRect(ink, Offset(size.width * 0.16f, size.height * 0.16f), Size(size.width * 0.68f, size.height * 0.68f), style = pen(s))
        subject(0.43f, 0.45f, 0.57f, 0.74f, s)
    },
    CompositionGuide("horizon_low", "低地平線", "地平線放在下方 1/3，強調天空與雲") { s ->
        hline(2f / 3, s, dashed = false)
    },
    CompositionGuide("horizon_high", "高地平線", "地平線放在上方 1/3，強調地面、水面倒影") { s ->
        hline(1f / 3, s, dashed = false)
    },
)

fun composition(id: String?): CompositionGuide? = COMPOSITIONS.firstOrNull { it.id == id }

/** The guide over the preview. */
@Composable
fun CompositionOverlay(guide: CompositionGuide, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { guide.draw(this, 1f) }
}

/** Selector: "none" plus each guide as a small line drawing with its name. */
@Composable
fun CompositionStrip(selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        item {
            Tile("無", selected == null, { onSelect(null) }) {
                Text("—", color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        items(COMPOSITIONS, key = { it.id }) { g ->
            Tile(g.name, selected == g.id, { onSelect(g.id) }) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) { g.draw(this, 0.45f) }
            }
        }
    }
}

@Composable
private fun Tile(label: String, on: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Column(Modifier.width(62.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(10.dp)
        Box(Modifier.size(width = 54.dp, height = 70.dp).clip(shape).background(Color(0xFF1C1C1E))
            .border(if (on) 2.dp else 1.dp, if (on) HikariColors.Accent else Color(0xFF333333), shape), content = content)
        Text(label, color = if (on) HikariColors.Accent else Color(0xFFCCCCCC), fontSize = 10.sp, maxLines = 1,
            modifier = Modifier.padding(top = 3.dp))
    }
}


/**
 * The point of a subject that composition places: a person's eye line (where the viewer looks
 * first), otherwise the subject's visual centre, slightly above its geometric centre.
 */
fun anchorOf(s: Subject): Offset = s.face?.let { Offset(it.cx, it.eyeY) } ?: Offset(s.cx, s.top + s.h * 0.45f)

/**
 * Where the subject's anchor should go for a fixed guide (fractions of the preview), and the
 * subject height that fits it (null = any size). The smart guide is [smartTarget].
 */
fun guideTarget(id: String, a: Offset): Pair<Offset, Float?> {
    fun nearestThird() = Offset(listOf(1f / 3, 2f / 3).minBy { abs(it - a.x) }, listOf(1f / 3, 2f / 3).minBy { abs(it - a.y) })
    return when (id) {
        "portrait_full" -> Offset(0.67f, 0.58f) to 0.66f
        "portrait_half" -> Offset(0.5f, 0.62f) to 0.8f
        "small_subject" -> Offset(0.5f, 0.73f) to 0.24f
        "center", "triangle", "one_point" -> Offset(0.5f, 0.5f) to null
        "symmetry", "two_point" -> Offset(0.5f, a.y.coerceIn(0.3f, 0.7f)) to null
        "spiral" -> Offset(0.62f, 0.62f) to null
        "frame" -> Offset(0.5f, 0.6f) to null
        "horizon_low" -> Offset(a.x.coerceIn(0.25f, 0.75f), 0.55f) to null
        "horizon_high" -> Offset(a.x.coerceIn(0.25f, 0.75f), 0.62f) to null
        else -> nearestThird() to null
    }
}

/** Candidate positions for the smart guide: thirds, centre lines and golden-ratio points. */
private val SMART_POINTS: List<Offset> = buildList {
    for (x in listOf(1f / 3, 0.5f, 2f / 3)) for (y in listOf(1f / 3, 0.5f, 2f / 3)) add(Offset(x, y))
    for (x in listOf(0.382f, 0.618f)) for (y in listOf(0.382f, 0.618f)) add(Offset(x, y))
}

/**
 * How good it would look to move the frame so the subject's anchor lands on [c] (lower is better).
 * The rules photographers use, each as a cost:
 *  - move as little as possible (the shot you are already taking matters)
 *  - never cut more of the subject than is already cut; keep breathing room from the edges
 *  - people: eyes on the upper third, air above the head, space in the direction they look;
 *    a large face sits well centred
 *  - things: off-centre on a strong point, unless they fill the frame (then centred and balanced)
 */
internal fun smartCost(s: Subject, c: Offset): Float {
    val a = anchorOf(s)
    val dx = c.x - a.x
    val dy = c.y - a.y
    var cost = kotlin.math.hypot(dx, dy) * 0.6f
    fun over(v: Float) = kotlin.math.max(0f, v)
    // newly cut parts of the subject (what is already outside the frame does not count again)
    val cut = over(-(s.left + dx)) - over(-s.left) + over(s.right + dx - 1) - over(s.right - 1) +
        over(-(s.top + dy)) - over(-s.top) + over(s.bottom + dy - 1) - over(s.bottom - 1)
    cost += over(cut) * 3f
    val margin = minOf(s.left + dx, 1 - s.right - dx, s.top + dy, 1 - s.bottom - dy)
    if (margin in 0f..0.04f) cost += (0.04f - margin) * 2f
    val f = s.face
    if (f != null) {
        cost += when {
            c.y < 0.3f -> 0.15f
            c.y < 0.36f -> 0f
            c.y < 0.42f -> 0.08f
            c.y < 0.55f -> 0.14f
            else -> 0.4f
        }
        val headTop = f.top - f.h * 0.25f + dy
        if (headTop < 0.03f) cost += (0.03f - headTop) * 4f
        when {
            // looking toward the right edge: space on the right, so the person sits left
            abs(f.yaw) > 12f -> cost += abs(c.x - (if (f.yaw > 0) 1f / 3 else 2f / 3)) * 0.6f
            f.h > 0.25f -> cost += abs(c.x - 0.5f) * 0.35f
            else -> cost += if (abs(c.x - 0.5f) < 0.01f) 0.04f else 0f
        }
    } else {
        val big = s.w > 0.55f || s.h > 0.65f
        cost += if (big) kotlin.math.hypot(c.x - 0.5f, c.y - 0.5f) * 0.5f
        else if (abs(c.x - 0.5f) < 0.01f && abs(c.y - 0.5f) < 0.01f) 0.15f else 0f
    }
    return cost
}

/**
 * The smart guide's target for the anchor. [previous] (the last target shown) is kept unless
 * another point is clearly better, so the ring does not hop between points while framing.
 */
fun smartTarget(s: Subject, previous: Offset? = null): Offset {
    val best = SMART_POINTS.minBy { smartCost(s, it) }
    if (previous != null) {
        val keep = SMART_POINTS.minBy { (it - previous).getDistance() }
        if (smartCost(s, keep) <= smartCost(s, best) + 0.15f) return keep   // only a clearly better point replaces the one shown
    }
    return best
}

/** Live guidance state for the UI: what to tell the photographer, and whether it is lined up. */
data class Guidance(val anchor: Offset, val target: Offset, val tip: String, val aligned: Boolean)

/**
 * Turns the tracked subject into an instruction. Directions are for the camera (the way people
 * think when framing): the subject left of its target means "pan left".
 */
fun guidance(guideId: String, s: Subject, previous: Offset? = null): Guidance {
    val smart = guideId == "smart"
    val anchor = if (smart || guideId == "thirds" || guideId == "spiral") anchorOf(s) else Offset(s.cx, s.cy)
    val (t, wantH) = if (smart) smartTarget(s, previous) to null else guideTarget(guideId, anchor)
    val dx = anchor.x - t.x
    val dy = anchor.y - t.y
    val tips = mutableListOf<String>()
    val near = kotlin.math.hypot(dx, dy) < 0.045f
    if (!near && abs(dx) > 0.03f) tips += if (dx < 0) "鏡頭往左" else "鏡頭往右"
    if (!near && abs(dy) > 0.03f) tips += if (dy < 0) "鏡頭往上" else "鏡頭往下"
    if (wantH != null && s.h > 0.05f) {
        if (s.h < wantH * 0.8f) tips += "靠近一點" else if (s.h > wantH * 1.2f) tips += "退後一點"
    }
    val f = s.face
    if (smart && f != null) {
        if (f.h > 0.5f) tips += "退後一點"
        else if (f.h < 0.12f && s.top < 0.08f && s.bottom > 0.97f) tips += "退後一點，讓腳完整入鏡"
    } else if (smart && s.w > 0.95f && s.h > 0.9f) tips += "退後一點"
    val aligned = tips.isEmpty()
    return Guidance(anchor, t, if (aligned) "構圖完美，按下快門" else tips.joinToString(" · "), aligned)
}

/**
 * Drawn on top of the preview every frame: the tracked subject (corner brackets), its target, an
 * arrow between them, and a level line from the phone's tilt.
 */
@Composable
fun LiveGuideOverlay(subject: com.hikari.cam.camera.Subject?, guidance: Guidance?, tiltDeg: Float?, modifier: Modifier = Modifier) {
    val accent = HikariColors.Accent
    val yellow = Color(0xFFF5D04C)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // level
            tiltDeg?.let { deg ->
                val level = kotlin.math.abs(deg) < 1.5f
                val c = Offset(w / 2, h / 2)
                val len = w * 0.22f
                val rad = Math.toRadians(deg.toDouble())
                val dxl = (cos(rad) * len).toFloat()
                val dyl = (sin(rad) * len).toFloat()
                drawLine(if (level) yellow else Color.White.copy(alpha = 0.8f), Offset(c.x - dxl, c.y - dyl), Offset(c.x + dxl, c.y + dyl), 2.5f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x - len * 1.3f, c.y), Offset(c.x - len * 1.1f, c.y), 2f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x + len * 1.1f, c.y), Offset(c.x + len * 1.3f, c.y), 2f)
            }
            if (subject != null && guidance != null) {
                // Shot-suggestion style: a fixed ring in the middle of the frame is where the camera
                // points; the dot is the best shot. Turn the camera until the ring sits on the dot.
                val c = Offset(w / 2, h / 2)
                val off = Offset((guidance.anchor.x - guidance.target.x) * w, (guidance.anchor.y - guidance.target.y) * h)
                val best = Offset((c.x + off.x).coerceIn(w * 0.06f, w * 0.94f), (c.y + off.y).coerceIn(h * 0.06f, h * 0.94f))
                val ring = 30.dp.toPx(); val dot = 9.dp.toPx()
                if (guidance.aligned) {
                    drawCircle(yellow.copy(alpha = 0.25f), radius = ring, center = c)
                    drawCircle(yellow, radius = ring, center = c, style = Stroke(3.5.dp.toPx()))
                    drawCircle(yellow, radius = dot, center = c)
                } else {
                    drawCircle(Color.Black.copy(alpha = 0.15f), radius = ring, center = c)
                    drawCircle(Color.White, radius = ring, center = c, style = Stroke(2.5.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.25f), radius = dot + 2.dp.toPx(), center = best)
                    drawCircle(Color.White, radius = dot, center = best)
                    drawCircle(yellow, radius = dot * 0.55f, center = best)
                }
            }
        }
        if (subject != null && guidance != null && !guidance.aligned) {
            // the label rides with the dot, like "Best shot"
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val bx = (0.5f + guidance.anchor.x - guidance.target.x).coerceIn(0.06f, 0.94f)
                val by = (0.5f + guidance.anchor.y - guidance.target.y).coerceIn(0.06f, 0.94f)
                Text("最佳構圖", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.offset(x = maxWidth * bx - 26.dp, y = maxHeight * by + 14.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        guidance?.let { g ->
            // one short line, only while something has to change
            if (!g.aligned) Text(g.tip, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 92.dp)
                    .background(Color(0x88000000), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

private data class Quad(val x: Float, val y: Float, val sx: Float, val sy: Float)

/**
 * Calms the live guide down: the offset between subject and target is low-pass filtered, and
 * "aligned" has two thresholds (it turns on inside the inner radius and only turns off outside the
 * outer one), so the ring does not flicker when the hand trembles near the mark.
 */
class GuideStabilizer(private val alpha: Float = 0.18f, private val enter: Float = 0.05f, private val exit: Float = 0.10f) {
    private var dx = 0f; private var dy = 0f; private var has = false; private var aligned = false
    private var target: Offset? = null
    fun reset() { has = false; aligned = false; target = null }
    fun update(g: Guidance?): Guidance? {
        if (g == null) { reset(); return null }
        val ox = g.anchor.x - g.target.x; val oy = g.anchor.y - g.target.y
        // a new target point: start from the measured offset instead of sliding across the frame
        if (!has || target != g.target) { dx = ox; dy = oy; has = true; target = g.target } else { dx += (ox - dx) * alpha; dy += (oy - dy) * alpha }
        val d = kotlin.math.hypot(dx, dy)
        aligned = if (aligned) d < exit else d < enter
        // "step back" style hints still count: aligned only when the position is right and nothing else is asked
        val ok = aligned && (g.aligned || !g.tip.contains("退後"))
        return Guidance(Offset(g.target.x + dx, g.target.y + dy), g.target, if (ok) "" else g.tip.ifEmpty { "移動鏡頭，讓圓圈對準最佳構圖點" }, ok)
    }
}
