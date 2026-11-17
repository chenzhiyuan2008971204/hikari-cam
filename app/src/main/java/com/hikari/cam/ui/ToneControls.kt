package com.hikari.cam.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.camera2.CameraMetadata
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * White-balance presets: the camera's own AWB modes, and where each sits on the colour pad
 * (temperature axis, -1..1 = 3000K..8000K): daylight 5500K, cloudy 6500K, tungsten 3200K.
 */
enum class WbPreset(val label: String, val mode: Int, val base: Float) {
    AUTO("A", CameraMetadata.CONTROL_AWB_MODE_AUTO, 0f),
    DAYLIGHT("☀", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, 0f),
    CLOUDY("☁", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, 0.4f),
    TUNGSTEN("💡", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, -0.92f),
}

/** Temperature adjustment (-1..1, + = warmer picture) shown as Kelvin the way film apps label it. */
fun kelvinLabel(temp: Float) = "${(5500 + temp * 2500).roundToInt()}K"
fun tintLabel(tint: Float) = "${(tint * 50).roundToInt()}"

/**
 * Two-dimensional colour pad: left/right = colour temperature (cool..warm), up/down = tint
 * (magenta..green). The dot is where the picture's adjustment sits; drag or tap to move it.
 */
@Composable
fun WhiteBalancePad(temp: Float, tint: Float, onChange: (Float, Float) -> Unit, onCommit: () -> Unit, modifier: Modifier = Modifier,
                    /** double tap: back to the start position (the chosen preset; the centre for auto) */
                    onReset: () -> Unit = { onChange(0f, 0f) }) {
    fun update(p: Offset, w: Float, h: Float) {
        val t = ((p.x / w) * 2f - 1f).coerceIn(-1f, 1f)
        val g = (1f - (p.y / h) * 2f).coerceIn(-1f, 1f)
        onChange((t * 100).roundToInt() / 100f, (g * 100).roundToInt() / 100f)
    }
    Column(modifier) {
        Text("${kelvinLabel(temp)}  ${tintLabel(tint)}" + if (temp != 0f || tint != 0f) "　·　點兩下回中" else "", color = Darkroom.Text, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = onCommit) { change, _ ->
                        update(change.position, size.width.toFloat(), size.height.toFloat())
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // double tap: back to neutral (centre)
                        onDoubleTap = { onReset(); onCommit() },
                        onTap = { p -> update(p, size.width.toFloat(), size.height.toFloat()); onCommit() },
                    )
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF6E8BFF), Color(0xFFB9C8FF), Color(0xFFFFF3E4), Color(0xFFFFB98A), Color(0xFFFF7A5C))))
            drawRect(Brush.verticalGradient(listOf(Color(0x99E04BD8), Color.Transparent, Color(0x9954D98A))))
            val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
            drawLine(Color(0x99000000), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5f, pathEffect = dash)
            drawLine(Color(0x99000000), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f, pathEffect = dash)
            val c = Offset((temp + 1f) / 2f * size.width, (1f - tint) / 2f * size.height)
            drawCircle(Color.Black, radius = 11.dp.toPx(), center = c)
            drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        }
    }
}

/** 光影 panel: exposure, white balance pad and presets, date stamp. */
@Composable
fun TonePanel(
    evIndex: Int, evMin: Int, evMax: Int, evStep: Float, onEv: (Int) -> Unit,
    temp: Float, tint: Float, onTempTint: (Float, Float) -> Unit, onCommit: () -> Unit,
    preset: WbPreset, onPreset: (WbPreset) -> Unit,
    stamp: Boolean, note: String, onStamp: (Boolean) -> Unit, onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (evMax > evMin) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☀ ${if (evIndex > 0) "+" else ""}$evIndex", color = Darkroom.Text, fontSize = 12.sp,
                modifier = Modifier.background(Darkroom.CardHi, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
            ThinSlider(evIndex.toFloat(), { onEv(it.roundToInt()) }, evMin.toFloat()..evMax.toFloat(), steps = (evMax - evMin - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text("%+.1f EV".format(evIndex * evStep), color = Darkroom.Dim, fontSize = 11.sp, modifier = Modifier.width(52.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            // the dot shows the preset's own position plus the user's fine adjustment on top of it
            val b = preset.base
            WhiteBalancePad((temp + b).coerceIn(-1f, 1f), tint,
                { t, g -> onTempTint(((t - b) * 100).roundToInt() / 100f, g) }, onCommit, Modifier.weight(0.7f),
                onReset = { onTempTint(0f, 0f) })
            Row(Modifier.padding(start = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (p in WbPreset.entries) Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .border(1.dp, if (p == preset) Darkroom.Amber else Darkroom.Border, CircleShape)
                        .clickable { onPreset(p) },
                    contentAlignment = Alignment.Center,
                ) { Text(p.label, color = if (p == preset) Darkroom.Amber else Darkroom.Dim, fontSize = 11.sp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("日期戳記", color = Darkroom.Text, fontSize = 13.sp)
                Text(stampText(Date(), note), color = Darkroom.Dim, fontSize = 11.sp)
            }
            QuietSwitch(stamp, onStamp)
        }
        if (stamp) BasicTextField(
            note, { onNote(it.take(24)) }, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp), cursorBrush = SolidColor(Color(0xFFFFB86B)),
            modifier = Modifier.fillMaxWidth().background(Color(0xFF222222), RoundedCornerShape(8.dp)).padding(10.dp),
            decorationBox = { inner ->
                if (note.isEmpty()) Text("天氣／地點，例如：晴 / Hokkaido", color = Color(0xFF777777), fontSize = 13.sp)
                inner()
            },
        )
    }
}

/** "5 月 · 12:08pm · 晴 / Hokkaido" */
fun stampText(date: Date, note: String): String {
    val month = SimpleDateFormat("M", Locale.US).format(date)
    val time = SimpleDateFormat("h:mma", Locale.US).format(date).lowercase()
    return buildString {
        append("$month 月 · $time")
        if (note.isNotBlank()) append(" · ${note.trim()}")
    }
}

/**
 * Prints the stamp onto the finished photo: spaced serif type, white with a soft shadow, centred
 * at 82 % height, sized from the picture so it looks the same at any resolution.
 */
fun drawDateStamp(bmp: Bitmap, text: String) {
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        textSize = minOf(bmp.width, bmp.height) * 0.034f
        letterSpacing = 0.18f
        textAlign = Paint.Align.CENTER
        setShadowLayer(textSize * 0.25f, 0f, textSize * 0.05f, 0x66000000)
    }
    c.drawText(text, bmp.width / 2f, bmp.height * 0.82f, p)
}
