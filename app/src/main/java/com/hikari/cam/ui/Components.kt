package com.hikari.cam.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import kotlin.math.roundToInt

object HikariColors {
    val Accent = Color(0xFFFFB86B)
    val Panel = Color(0xFF141414)
    val Dim = Color(0xFF9A9A9A)
    val Line = Color(0xFF2A2A2A)
}

@Composable
fun HikariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HikariColors.Accent, onPrimary = Color.Black,
            surface = HikariColors.Panel, background = Color.Black,
            secondaryContainer = Color(0xFF3A2A18), onSecondaryContainer = HikariColors.Accent,
        ),
        content = content,
    )
}

val SERIES_ZH = mapOf("film" to "Film 底片", "japanese" to "Japanese 日系", "ccd" to "CCD 數位", "special" to "Special 特殊")

/** One adjustable parameter: [get] shows the look's own value when the user has not changed it. */
class Param(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val format: (Float) -> String,
    val get: (Adjustments, FilterSpec) -> Float,
    val set: (Adjustments, Float) -> Adjustments,
)

fun signed(v: Float) = (if (v >= 0) "+" else "") + "%.2f".format(v)

val ADJUST_PARAMS = listOf(
    Param("濾鏡強度", 0f..1f, { "${(it * 100).roundToInt()}%" }, { a, _ -> a.strength }, { a, v -> a.copy(strength = v) }),
    Param("顆粒", 0f..1f, { "%.2f".format(it) }, { a, s -> a.grain ?: s.grain.amount }, { a, v -> a.copy(grain = v) }),
    Param("顆粒大小", 0.0005f..0.003f, { "%.2f‰".format(it * 1000) }, { a, s -> a.grainSize ?: s.grain.size }, { a, v -> a.copy(grainSize = v) }),
    Param("Halation", 0f..1.5f, { "%.2f".format(it) }, { a, s -> a.halation ?: s.halation.amount }, { a, v -> a.copy(halation = v) }),
    Param("Bloom", 0f..1f, { "%.2f".format(it) }, { a, s -> a.bloom ?: s.bloom.amount }, { a, v -> a.copy(bloom = v) }),
    Param("暗角", 0f..1f, { "%.2f".format(it) }, { a, s -> a.vignette ?: s.lens.vignette }, { a, v -> a.copy(vignette = v) }),
    Param("褪色", 0f..1f, { "%.2f".format(it) }, { a, _ -> a.fade }, { a, v -> a.copy(fade = v) }),
    Param("曝光 EV", -2f..2f, ::signed, { a, _ -> a.exposure }, { a, v -> a.copy(exposure = v) }),
    Param("色溫", -1f..1f, ::signed, { a, _ -> a.temperature }, { a, v -> a.copy(temperature = v) }),
    Param("色調", -1f..1f, ::signed, { a, _ -> a.tint }, { a, v -> a.copy(tint = v) }),
    Param("對比", -1f..1f, ::signed, { a, _ -> a.contrast }, { a, v -> a.copy(contrast = v) }),
    Param("飽和", -1f..1f, ::signed, { a, _ -> a.saturation }, { a, v -> a.copy(saturation = v) }),
)

/** Lens character and film defects: each one can be switched off (0) or set; null = the look's own amount. */
val LENS_PARAMS = listOf(
    Param("色差", 0f..1f, { "%.2f".format(it) }, { a, s -> a.aberration ?: s.lens.aberration }, { a, v -> a.copy(aberration = v) }),
    Param("柔焦", 0f..1f, { "%.2f".format(it) }, { a, s -> a.softFocus ?: s.diffusion.softFocus }, { a, v -> a.copy(softFocus = v) }),
    Param("光線擴散", 0f..1f, { "%.2f".format(it) }, { a, s -> a.mist ?: s.diffusion.mist }, { a, v -> a.copy(mist = v) }),
    Param("邊緣柔化", 0f..1f, { "%.2f".format(it) }, { a, s -> a.edgeSoft ?: s.lens.edgeSoft }, { a, v -> a.copy(edgeSoft = v) }),
    Param("灰塵", 0f..1f, { "%.2f".format(it) }, { a, s -> a.dust ?: s.defects.dust }, { a, v -> a.copy(dust = v) }),
    Param("刮痕", 0f..1f, { "%.2f".format(it) }, { a, s -> a.scratches ?: s.defects.scratches }, { a, v -> a.copy(scratches = v) }),
    Param("漏光", 0f..1f, { "%.2f".format(it) }, { a, s -> a.leak ?: s.defects.leak }, { a, v -> a.copy(leak = v) }),
    Param("曝光不均", 0f..1f, { "%.2f".format(it) }, { a, s -> a.uneven ?: s.defects.uneven }, { a, v -> a.copy(uneven = v) }),
    Param("色彩漂移", 0f..1f, { "%.2f".format(it) }, { a, s -> a.drift ?: s.defects.drift }, { a, v -> a.copy(drift = v) }),
)

/** The adjustment sliders for one look. [onChange] fires while dragging; [onCommit] when a drag ends. */
@Composable
fun AdjustSliders(
    spec: FilterSpec,
    adjust: Adjustments,
    onChange: (Adjustments) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    onCommit: () -> Unit = {},
    params: List<Param> = ADJUST_PARAMS,
    lens: List<Param> = LENS_PARAMS,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${spec.name} · ${spec.zh}", color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("重設", color = HikariColors.Accent) }
        }
        for (p in params) ParamRow(p, spec, adjust, onChange, onCommit)
        if (lens.isNotEmpty()) {
            Text("鏡頭與底片瑕疵（每項可單獨開關）", color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            for (p in lens) {
                val v = p.get(adjust, spec)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = v > 0f,
                        onCheckedChange = { on ->
                            val own = p.get(Adjustments.DEFAULT, spec)
                            onChange(p.set(adjust, if (on) (if (own > 0f) own else 0.3f) else 0f)); onCommit()
                        },
                        modifier = Modifier.padding(end = 4.dp).height(28.dp),
                    )
                    Box(Modifier.weight(1f)) { ParamRow(p, spec, adjust, onChange, onCommit) }
                }
            }
        }
    }
}

@Composable
private fun ParamRow(p: Param, spec: FilterSpec, adjust: Adjustments, onChange: (Adjustments) -> Unit, onCommit: () -> Unit) {
    val v = p.get(adjust, spec)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(p.label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(70.dp))
        Slider(
            value = v.coerceIn(p.range.start, p.range.endInclusive),
            onValueChange = { onChange(p.set(adjust, it)) },
            onValueChangeFinished = onCommit,
            valueRange = p.range,
            colors = SliderDefaults.colors(thumbColor = HikariColors.Accent, activeTrackColor = HikariColors.Accent),
            modifier = Modifier.weight(1f).height(32.dp),
        )
        Text(p.format(v), color = HikariColors.Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(56.dp).padding(start = 6.dp))
    }
}

/** Horizontal strip of looks with real rendered thumbnails. */
@Composable
fun LookStrip(
    looks: List<FilterSpec>,
    thumbs: Map<String, Bitmap>,
    selectedId: String?,
    favorites: Set<String>,
    onSelect: (FilterSpec) -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 64,
) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(looks, key = { it.id }) { spec ->
            val selected = spec.id == selectedId
            Column(Modifier.width((size + 8).dp).clickable { onSelect(spec) }, horizontalAlignment = Alignment.CenterHorizontally) {
                val shape = RoundedCornerShape(6.dp)
                Box(Modifier.size(size.dp).clip(shape).background(HikariColors.Panel)
                    .border(if (selected) 2.dp else 0.dp, if (selected) HikariColors.Accent else Color.Transparent, shape)) {
                    thumbs[spec.id]?.let { b ->
                        val img = remember(b) { b.asImageBitmap() }
                        Image(img, spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    if (spec.id in favorites) Text("★", color = HikariColors.Accent, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(3.dp))
                }
                Text(spec.name, color = if (selected) HikariColors.Accent else Color.White, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = HikariColors.Dim, fontSize = 12.sp, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}
