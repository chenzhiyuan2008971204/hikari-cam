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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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
 * Where the subject's centre should go for a guide, given where it is now (fractions of the
 * preview), and the subject height that fits it (null = any size).
 */
fun guideTarget(id: String, cx: Float, cy: Float): Pair<Offset, Float?> {
    fun nearestThird(): Offset {
        val xs = listOf(1f / 3, 2f / 3)
        val ys = listOf(1f / 3, 2f / 3)
        return Offset(xs.minBy { kotlin.math.abs(it - cx) }, ys.minBy { kotlin.math.abs(it - cy) })
    }
    return when (id) {
        "portrait_full" -> Offset(0.67f, 0.58f) to 0.66f
        "portrait_half" -> Offset(0.5f, 0.62f) to 0.8f
        "small_subject" -> Offset(0.5f, 0.73f) to 0.24f
        "center", "triangle", "one_point" -> Offset(0.5f, 0.5f) to null
        "symmetry", "two_point" -> Offset(0.5f, cy.coerceIn(0.3f, 0.7f)) to null
        "spiral" -> Offset(0.62f, 0.62f) to null
        "frame" -> Offset(0.5f, 0.6f) to null
        "horizon_low" -> Offset(cx.coerceIn(0.25f, 0.75f), 0.55f) to null
        "horizon_high" -> Offset(cx.coerceIn(0.25f, 0.75f), 0.62f) to null
        else -> nearestThird() to null
    }
}

/** Live guidance state for the UI: what to tell the photographer, and whether it is lined up. */
data class Guidance(val target: Offset, val tip: String, val aligned: Boolean)

/**
 * Turns the tracked subject into an instruction. Directions are for the camera (the way people
 * think when framing): the subject left of its target means "pan left".
 */
fun guidance(guideId: String, s: com.hikari.cam.camera.Subject): Guidance {
    val (t, wantH) = guideTarget(guideId, s.cx, s.cy)
    val dx = s.cx - t.x
    val dy = s.cy - t.y
    val tips = mutableListOf<String>()
    if (kotlin.math.abs(dx) > 0.04f) tips += if (dx < 0) "鏡頭往左" else "鏡頭往右"
    if (kotlin.math.abs(dy) > 0.04f) tips += if (dy < 0) "鏡頭往上" else "鏡頭往下"
    if (wantH != null && s.h > 0.05f) {
        if (s.h < wantH * 0.8f) tips += "靠近一點" else if (s.h > wantH * 1.2f) tips += "退後一點"
    }
    val aligned = tips.isEmpty()
    return Guidance(t, if (aligned) "構圖完美，按下快門" else tips.joinToString(" · "), aligned)
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
                val level = kotlin.math.abs(deg) < 1f
                val c = Offset(w / 2, h / 2)
                val len = w * 0.16f
                val rad = Math.toRadians(deg.toDouble())
                val dxl = (cos(rad) * len).toFloat()
                val dyl = (sin(rad) * len).toFloat()
                drawLine(if (level) accent else Color.White.copy(alpha = 0.8f), Offset(c.x - dxl, c.y - dyl), Offset(c.x + dxl, c.y + dyl), 2.5f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x - len * 1.3f, c.y), Offset(c.x - len * 1.1f, c.y), 2f)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(c.x + len * 1.1f, c.y), Offset(c.x + len * 1.3f, c.y), 2f)
            }
            if (subject != null && guidance != null) {
                val col = if (guidance.aligned) accent else yellow
                val l = subject.left * w
                val t = subject.top * h
                val r = subject.right * w
                val b = subject.bottom * h
                val k = min(r - l, b - t) * 0.22f
                for ((x, y, sx, sy) in listOf(Quad(l, t, 1f, 1f), Quad(r, t, -1f, 1f), Quad(l, b, 1f, -1f), Quad(r, b, -1f, -1f))) {
                    drawLine(col, Offset(x, y), Offset(x + k * sx, y), 3f)
                    drawLine(col, Offset(x, y), Offset(x, y + k * sy), 3f)
                }
                val from = Offset(subject.cx * w, subject.cy * h)
                val to = Offset(guidance.target.x * w, guidance.target.y * h)
                drawCircle(col, radius = 14f, center = to, style = Stroke(2.5f))
                drawCircle(col, radius = 4f, center = to)
                if (!guidance.aligned) {
                    drawLine(col.copy(alpha = 0.9f), from, to, 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    // arrow head at the target
                    val ang = kotlin.math.atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
                    for (d in listOf(2.6, -2.6)) {
                        val a = ang + d
                        drawLine(col, to, Offset(to.x + (cos(a) * 18).toFloat(), to.y + (sin(a) * 18).toFloat()), 3f)
                    }
                    drawCircle(col, radius = 5f, center = from)
                }
            }
        }
        guidance?.let { g ->
            Text(g.tip, color = if (g.aligned) Color.Black else Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp)
                    .background(if (g.aligned) accent else Color(0x88000000), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

private data class Quad(val x: Float, val y: Float, val sx: Float, val sy: Float)
