package com.hikari.cam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.engine.FilterSpec

/** Look of one icon: a 35 mm canister, or a compact digital camera for the CCD series. */
private data class IconStyle(
    val body: Color,
    val label: Color,
    val accent: Color,
    val text: String,
    val sub: String,
    val camera: Boolean = false,
    val textColor: Color = Color.White,
)

private val STYLES = mapOf(
    "mellow400" to IconStyle(Color(0xFFEADCC8), Color(0xFFE29B74), Color(0xFFB8664A), "MELLOW", "400"),
    "gold_memory" to IconStyle(Color(0xFFF2C14E), Color(0xFFC8432B), Color(0xFF7A2418), "GOLD", "200"),
    "cinema400" to IconStyle(Color(0xFF2C2C30), Color(0xFFB8382F), Color(0xFFE8C25A), "CINEMA", "400T"),
    "vivid100" to IconStyle(Color(0xFFF4F2EC), Color(0xFFD7263D), Color(0xFF1B4F9C), "VIVID", "100"),
    "tokyo_blue" to IconStyle(Color(0xFFDDE8EE), Color(0xFF2F6F8F), Color(0xFF9FD3E0), "TOKYO", "BLUE"),
    "kyoto_soft" to IconStyle(Color(0xFFF1E8DC), Color(0xFFB9A089), Color(0xFF8C7462), "KYOTO", "SOFT"),
    "summer_green" to IconStyle(Color(0xFFE9F1DC), Color(0xFF5E8C3A), Color(0xFFF3D35B), "SUMMER", "GREEN"),
    "ccd2000" to IconStyle(Color(0xFFC9CCD2), Color(0xFF3A5BA0), Color(0xFF6F7682), "CCD", "2000", camera = true),
    "flash_diary" to IconStyle(Color(0xFF1F1F22), Color(0xFFF5D04C), Color(0xFF8A8A8A), "FLASH", "DIARY", camera = true),
    "compact_chrome" to IconStyle(Color(0xFFB9BEC6), Color(0xFF2A2D33), Color(0xFFE9ECEF), "CHROME", "C-1", camera = true),
    "night_bloom" to IconStyle(Color(0xFF1B2340), Color(0xFFE2445C), Color(0xFF7FC8F8), "NIGHT", "800"),
    "dream1998" to IconStyle(Color(0xFFE8D9F0), Color(0xFFC48DB8), Color(0xFF8E6A9E), "DREAM", "1998"),
)

private fun styleFor(spec: FilterSpec) = STYLES[spec.id]
    ?: IconStyle(Color(0xFF444444), Color(0xFFFFB86B), Color(0xFF222222), spec.name.take(6).uppercase(), "")

/** An original, hand-drawn icon for a look (no photos, no colour charts). */
@Composable
fun FilmIcon(spec: FilterSpec, size: Dp, modifier: Modifier = Modifier) {
    val st = styleFor(spec)
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(size)) {
        if (st.camera) drawCamera(st) else drawCanister(st)
        val main = measurer.measure(st.text, TextStyle(color = st.textColor, fontSize = (this.size.width / 9f).toSp(), fontWeight = FontWeight.Bold))
        val sub = measurer.measure(st.sub, TextStyle(color = st.textColor.copy(alpha = 0.85f), fontSize = (this.size.width / 12f).toSp(), fontWeight = FontWeight.Medium))
        val cx = if (st.camera) this.size.width * 0.5f else this.size.width * 0.40f
        val cy = if (st.camera) this.size.height * 0.80f else this.size.height * 0.50f
        drawText(main, topLeft = Offset(cx - main.size.width / 2f, cy - main.size.height * 0.9f))
        drawText(sub, topLeft = Offset(cx - sub.size.width / 2f, cy + main.size.height * 0.1f))
    }
}

private fun DrawScope.drawCanister(st: IconStyle) {
    val w = size.width
    val h = size.height
    // film tongue with perforations, coming out of the right side
    val tongueTop = h * 0.30f
    val tongueH = h * 0.40f
    drawRoundRect(Color(0xFF3B2A1E), Offset(w * 0.60f, tongueTop), Size(w * 0.36f, tongueH), CornerRadius(w * 0.03f))
    val hole = w * 0.035f
    var x = w * 0.66f
    while (x < w * 0.93f) {
        drawRect(Color(0xFF15100C), Offset(x, tongueTop + h * 0.03f), Size(hole, hole * 1.3f))
        drawRect(Color(0xFF15100C), Offset(x, tongueTop + tongueH - h * 0.03f - hole * 1.3f), Size(hole, hole * 1.3f))
        x += w * 0.07f
    }
    // body (cylinder) with shading
    val left = w * 0.10f
    val bodyW = w * 0.60f
    val top = h * 0.14f
    val bodyH = h * 0.72f
    drawRoundRect(
        Brush.horizontalGradient(listOf(st.body.darker(0.18f), st.body, st.body.darker(0.06f), st.body.darker(0.25f)), left, left + bodyW),
        Offset(left, top), Size(bodyW, bodyH), CornerRadius(w * 0.05f),
    )
    // label band
    drawRect(st.label, Offset(left, top + bodyH * 0.22f), Size(bodyW, bodyH * 0.56f))
    drawRect(st.accent, Offset(left, top + bodyH * 0.22f), Size(bodyW, bodyH * 0.05f))
    drawRect(st.accent, Offset(left, top + bodyH * 0.73f), Size(bodyW, bodyH * 0.05f))
    // caps and spool
    val cap = Color(0xFF2A2A2A)
    drawRoundRect(cap, Offset(left - w * 0.02f, top - h * 0.05f), Size(bodyW + w * 0.04f, h * 0.08f), CornerRadius(w * 0.02f))
    drawRoundRect(cap, Offset(left - w * 0.02f, top + bodyH - h * 0.03f), Size(bodyW + w * 0.04f, h * 0.08f), CornerRadius(w * 0.02f))
    drawRoundRect(Color(0xFF555555), Offset(left + bodyW * 0.38f, top - h * 0.11f), Size(bodyW * 0.24f, h * 0.07f), CornerRadius(w * 0.015f))
    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(left + bodyW * 0.12f, top), Size(bodyW * 0.08f, bodyH))
}

private fun DrawScope.drawCamera(st: IconStyle) {
    val w = size.width
    val h = size.height
    val top = h * 0.18f
    val bodyH = h * 0.50f
    drawRoundRect(
        Brush.verticalGradient(listOf(st.body, st.body.darker(0.2f)), top, top + bodyH),
        Offset(w * 0.06f, top), Size(w * 0.88f, bodyH), CornerRadius(w * 0.08f),
    )
    // shutter button, flash window, viewfinder
    drawRoundRect(st.accent, Offset(w * 0.68f, top - h * 0.05f), Size(w * 0.16f, h * 0.06f), CornerRadius(w * 0.02f))
    drawRoundRect(Color(0xFFFFF6D8), Offset(w * 0.14f, top + h * 0.06f), Size(w * 0.16f, h * 0.09f), CornerRadius(w * 0.015f))
    drawRoundRect(Color(0xFF20242A), Offset(w * 0.70f, top + h * 0.06f), Size(w * 0.14f, h * 0.08f), CornerRadius(w * 0.015f))
    // lens
    val c = Offset(w * 0.50f, top + bodyH * 0.55f)
    drawCircle(st.label, radius = w * 0.20f, center = c)
    drawCircle(Color(0xFF111418), radius = w * 0.15f, center = c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF4A6E9A), Color(0xFF0B0E12)), c, w * 0.11f), radius = w * 0.11f, center = c)
    drawCircle(Color.White.copy(alpha = 0.55f), radius = w * 0.025f, center = Offset(c.x - w * 0.04f, c.y - w * 0.04f))
    drawCircle(st.accent, radius = w * 0.20f, center = c, style = Stroke(w * 0.015f))
    // name plate under the camera
    drawRoundRect(st.label, Offset(w * 0.10f, h * 0.72f), Size(w * 0.80f, h * 0.24f), CornerRadius(w * 0.04f))
}

private fun Color.darker(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
