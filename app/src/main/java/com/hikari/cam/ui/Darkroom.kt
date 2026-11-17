package com.hikari.cam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The darkroom look: charcoal ground, graphite cards, one film-amber accent kept for what matters (the
 * selected lens, the shutter ring, the composition guide, favourites, selected states), hard 6-12 dp corners
 * like the edges of a camera body. Everything else is grey so the colour of the picture is the only colour.
 */
object Darkroom {
    val Ground = Color(0xFF0D0D0D)
    val Card = Color(0xFF1C1C1E)
    val CardHi = Color(0xFF2C2C2E)
    val Border = Color(0xFF3A3A3C)
    val Text = Color(0xFFF2F2F7)
    val Dim = Color(0xFF8E8E93)
    val Amber = Color(0xFFF59E0B)
    val AmberDeep = Color(0xFFE68A00)

    val R6 = RoundedCornerShape(6.dp)
    val R8 = RoundedCornerShape(8.dp)
    val R12 = RoundedCornerShape(12.dp)
}

/** A graphite card: [title] in small grey capitals, content inside with 12 dp padding, 16 dp from the screen edge. */
@Composable
fun DarkCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(Darkroom.R12).background(Darkroom.Card).padding(12.dp)) {
        if (title != null) Text(title, color = Darkroom.Dim, fontSize = 12.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

/** A small, quiet switch: grey when off, light grey when on (amber is for the picture, not for toggles). */
@Composable
fun QuietSwitch(value: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        value, onChange, modifier = modifier.scale(0.78f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Darkroom.Text, checkedTrackColor = Color(0xFF636366), checkedBorderColor = Color(0xFF636366),
            uncheckedThumbColor = Darkroom.Dim, uncheckedTrackColor = Darkroom.CardHi, uncheckedBorderColor = Darkroom.Border,
        ),
    )
}

/** A thin slider with tick marks: a 2 dp rail, one tick per step, a small round handle. */
@Composable
fun ThinSlider(
    value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, steps: Int,
    modifier: Modifier = Modifier, onFinished: () -> Unit = {},
) {
    var width by remember { mutableFloatStateOf(1f) }
    fun at(x: Float): Float {
        val t = (x / width).coerceIn(0f, 1f)
        val raw = range.start + t * (range.endInclusive - range.start)
        return if (steps > 0) {
            val k = Math.round((raw - range.start) / (range.endInclusive - range.start) * (steps + 1))
            range.start + k.toFloat() / (steps + 1) * (range.endInclusive - range.start)
        } else raw
    }
    Canvas(
        modifier.height(28.dp)
            .pointerInput(range, steps) { detectTapGestures { p -> onChange(at(p.x)); onFinished() } }
            .pointerInput(range, steps) {
                detectDragGestures(onDragEnd = onFinished, onDragCancel = onFinished) { change, _ -> change.consume(); onChange(at(change.position.x)) }
            },
    ) {
        width = size.width.coerceAtLeast(1f)
        val cy = size.height / 2f
        val t = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        drawLine(Darkroom.Border, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Darkroom.Dim, Offset(0f, cy), Offset(size.width * t, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        for (i in 0..(steps + 1)) {
            val x = size.width * i / (steps + 1)
            val major = i % 5 == 0
            drawLine(if (x <= size.width * t) Darkroom.Dim else Darkroom.Border, Offset(x, cy - (if (major) 6 else 3).dp.toPx()), Offset(x, cy - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        drawCircle(Darkroom.Text, radius = 6.dp.toPx(), center = Offset(size.width * t, cy))
        drawCircle(Darkroom.Ground, radius = 3.dp.toPx(), center = Offset(size.width * t, cy))
    }
}

/** A segmented choice button: 6 dp corners, grey hairline when off, amber hairline when selected. */
@Composable
fun SegmentButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String = "") {
    Box(
        modifier.clip(Darkroom.R6).border(1.dp, if (selected) Darkroom.Amber else Darkroom.Border, Darkroom.R6)
            .background(if (selected) Color(0x1AF59E0B) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Darkroom.Amber else Darkroom.Text, fontSize = 13.sp) }
}

/** A row of text tabs with an underline under the selected one (no background, no pill). */
@Composable
fun TextTabs(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        for ((id, label) in items) {
            val on = id == selected
            Column(Modifier.clickable { onSelect(id) }.padding(vertical = 4.dp).then(if (id.isNotEmpty()) Modifier.testTag("tab-$id") else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (on) Darkroom.Text else Darkroom.Dim, fontSize = 13.sp, maxLines = 1)
                Box(Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth().background(if (on) Darkroom.Amber else Color.Transparent))
            }
        }
    }
}
