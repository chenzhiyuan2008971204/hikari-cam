package com.hikari.cam.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.cam.filter.Adjustments
import com.hikari.cam.filter.Category
import com.hikari.cam.filter.Filter
import kotlin.math.roundToInt

val Accent = Color(0xFFE8553F)
val Panel = Color(0xFF141414)
val Dim = Color(0xFF9A9A9A)

@Composable
fun HikariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Color(0xFFBFD8E6),
            background = Color.Black,
            surface = Panel,
        ),
        content = content,
    )
}

/** Category colour used for the swatch on filter chips. */
private fun swatch(f: Filter): Brush = when {
    f.id == "original" -> Brush.linearGradient(listOf(Color(0xFF666666), Color(0xFFBBBBBB)))
    f.params.mono > 0.5f -> Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFFDDDDDD)))
    f.category == Category.AIR -> Brush.linearGradient(listOf(Color(0xFFDDEEF2), Color(0xFFF6E2E4)))
    f.category == Category.FILM -> Brush.linearGradient(listOf(Color(0xFFE9B872), Color(0xFF8E6B4E)))
    f.category == Category.CINEMA -> Brush.linearGradient(listOf(Color(0xFF16404F), Color(0xFFE0874B)))
    else -> Brush.linearGradient(listOf(Color(0xFF7A5CC0), Color(0xFFE8553F)))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterStrip(
    filters: List<Filter>,
    selectedId: String,
    thumbs: Map<String, ImageBitmap> = emptyMap(),
    onSelect: (Filter) -> Unit,
    onLongPress: (Filter) -> Unit,
    onImportLut: () -> Unit,
) {
    var category by remember { mutableStateOf<Category?>(null) }
    Column {
        // Category tabs
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val tabs = listOf<Category?>(null) + Category.entries
            for (c in tabs) {
                val sel = c == category
                Text(
                    c?.label ?: "全部",
                    color = if (sel) Color.White else Dim,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    modifier = Modifier.combinedClickable(onClick = { category = c }),
                )
            }
        }
        val shown = filters.filter { category == null || it.category == category || it.id == "original" }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it.id }) { f ->
                val sel = f.id == selectedId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .combinedClickable(onClick = { onSelect(f) }, onLongClick = { onLongPress(f) }),
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(swatch(f))
                            .border(2.dp, if (sel) Accent else Color.Transparent, RoundedCornerShape(10.dp)),
                    ) {
                        thumbs[f.id]?.let {
                            Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp))
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        f.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (sel) Accent else Color.White, textAlign = TextAlign.Center,
                    )
                    Text(f.subtitle, fontSize = 9.sp, maxLines = 1, color = Dim)
                }
            }
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).combinedClickable(onClick = onImportLut),
                ) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Dim, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+", fontSize = 26.sp, color = Dim) }
                    Spacer(Modifier.height(3.dp))
                    Text("匯入 LUT", fontSize = 12.sp, color = Dim)
                }
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(36.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent),
        )
        Text(valueText, color = Dim, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
        trailing?.invoke()
    }
}

private fun signed(v: Float) = (v * 100).roundToInt().let { if (it > 0) "+$it" else "$it" }

@Composable
fun AdjustPanel(
    strength: Float,
    onStrength: (Float) -> Unit,
    adj: Adjustments,
    onAdj: (Adjustments) -> Unit,
    onSaveFilter: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LabeledSlider("濾鏡強度", strength, 0f..1f, "${(strength * 100).roundToInt()}%", onStrength)
        LabeledSlider("曝光", adj.exposure, -1.5f..1.5f, signed(adj.exposure), { onAdj(adj.copy(exposure = it)) })
        LabeledSlider("對比", adj.contrast, -1f..1f, signed(adj.contrast), { onAdj(adj.copy(contrast = it)) })
        LabeledSlider("飽和", adj.saturation, -1f..1f, signed(adj.saturation), { onAdj(adj.copy(saturation = it)) })
        LabeledSlider("色溫", adj.temperature, -1f..1f, signed(adj.temperature), { onAdj(adj.copy(temperature = it)) })
        LabeledSlider("色調", adj.tint, -1f..1f, signed(adj.tint), { onAdj(adj.copy(tint = it)) })
        LabeledSlider("亮部", adj.highlights, -1f..1f, signed(adj.highlights), { onAdj(adj.copy(highlights = it)) })
        LabeledSlider("暗部", adj.shadows, -1f..1f, signed(adj.shadows), { onAdj(adj.copy(shadows = it)) })
        LabeledSlider("褪色", adj.fade, 0f..1f, signed(adj.fade), { onAdj(adj.copy(fade = it)) })
        LabeledSlider("顆粒", adj.grain, 0f..1f, signed(adj.grain), { onAdj(adj.copy(grain = it)) })
        LabeledSlider("暗角", adj.vignette, 0f..1f, signed(adj.vignette), { onAdj(adj.copy(vignette = it)) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onAdj(Adjustments()); onStrength(1f) }) { Text("重設", color = Dim) }
            TextButton(onClick = onSaveFilter) { Text("存成我的濾鏡", color = Accent) }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("名稱") }) },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** Small pill toggle used in top bars and mode switches. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (active) Color.White else Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Tab row: 濾鏡 / 調整 / 手動 */
@Composable
fun Tabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        tabs.forEachIndexed { i, t -> Pill(t, i == selected) { onSelect(i) } }
    }
}
