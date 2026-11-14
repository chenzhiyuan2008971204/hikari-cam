package com.hikari.cam.lab

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import kotlin.math.roundToInt

private val Accent = Color(0xFFFFB86B)
private val Panel = Color(0xFF161616)
private val Dim = Color(0xFF9A9A9A)

@Composable
fun FilterLabScreen(vm: LabViewModel = viewModel()) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }
    var tab by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Filter Lab", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.loadChart() }) { Text("測試圖卡", color = Accent) }
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("匯入照片")
            }
        }
        Text(vm.source?.let { "${it.label} · 原始 ${it.fullW}×${it.fullH} · 預覽 ${it.outW}×${it.outH}" } ?: "",
            color = Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        if (vm.busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            Text(vm.busy ?: "", color = Accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }

        // image + compare
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val s = vm.source
            val p = vm.preview
            if (s != null && p != null) CompareView(s.original, p)
        }
        Text("在圖上左右拖曳＝分割比較（拖回最左結束）　按住＝看原圖", color = Dim, fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Center)

        // filter strip with real thumbnails
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(vm.looks) { i, spec ->
                LookChip(spec, vm.thumbs[spec.id], selected = i == vm.lookIndex) { vm.selectLook(i) }
            }
        }

        TabRow(selectedTabIndex = tab, containerColor = Panel, contentColor = Accent) {
            listOf("參數", "配方", "效能", "一致性").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        Box(Modifier.fillMaxWidth().height(250.dp).background(Panel)) {
            when (tab) {
                0 -> ParamsPanel(vm)
                1 -> RecipePanel(vm.look)
                2 -> PerfPanel(vm)
                else -> CheckPanel(vm)
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.runCheck() }, enabled = vm.busy == null, modifier = Modifier.weight(1f)) {
                Text("檢查預覽＝匯出")
            }
            Button(onClick = { vm.exportFull() }, enabled = vm.busy == null && vm.source != null, modifier = Modifier.weight(1f)) {
                Text("完整解析度匯出")
            }
        }
    }

    val msg = vm.message
    if (msg != null) {
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3500)
            vm.message = null
        }
        Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
            Text(msg, color = Color.White, modifier = Modifier
                .background(Color(0xE0303030), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/**
 * Effect with a draggable split line: original on the left of the line, effect on the right.
 * Holding a finger still on the picture shows the whole original.
 */
@Composable
private fun CompareView(original: Bitmap, effect: Bitmap) {
    // divider position as a fraction of the width: original left of it, effect right of it
    var divider by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    val ratio = effect.width.toFloat() / effect.height
    val label = Modifier.padding(6.dp).background(Color(0x80000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitWide = maxWidth / maxHeight > ratio
        val boxMod = if (fitWide) Modifier.fillMaxHeight().aspectRatio(ratio) else Modifier.fillMaxWidth().aspectRatio(ratio)
        val origImg = remember(original) { original.asImageBitmap() }
        val effImg = remember(effect) { effect.asImageBitmap() }
        Box(
            boxMod
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { holding = false },
                        onDrag = { change, _ ->
                            holding = false
                            divider = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
        ) {
            Image(origImg, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            if (!holding) {
                Image(
                    effImg, null,
                    Modifier.fillMaxSize().drawWithContent {
                        clipRect(left = size.width * divider) { this@drawWithContent.drawContent() }
                    },
                    contentScale = ContentScale.FillBounds,
                )
                if (divider > 0f) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        Box(Modifier.offset(x = maxWidth * divider - 1.dp).width(2.dp).fillMaxHeight().background(Color.White))
                        Text("原圖", color = Color.White, fontSize = 11.sp, modifier = label)
                        Text("效果", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).then(label))
                    }
                }
            } else {
                Text("原圖", color = Color.White, fontSize = 12.sp, modifier = label)
            }
        }
    }
}

@Composable
private fun LookChip(spec: FilterSpec, thumb: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(84.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier.size(76.dp).clip(shape).background(Panel)
                .border(if (selected) 2.dp else 0.dp, if (selected) Accent else Color.Transparent, shape)
        ) {
            if (thumb != null) {
                val img = remember(thumb) { thumb.asImageBitmap() }
                Image(img, spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Text(spec.name, color = if (selected) Accent else Color.White, fontSize = 11.sp, maxLines = 1)
        Text(spec.zh, color = Dim, fontSize = 10.sp, maxLines = 1)
    }
}

/** One slider: [get] reads the current value (filter default when not overridden), [set] writes it. */
private class Param(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val format: (Float) -> String,
    val get: (Adjustments, FilterSpec) -> Float,
    val set: (Adjustments, Float) -> Adjustments,
)

private fun signed(v: Float) = (if (v >= 0) "+" else "") + "%.2f".format(v)

private val PARAMS = listOf(
    Param("濾鏡強度", 0f..1f, { "${(it * 100).roundToInt()}%" }, { a, _ -> a.strength }, { a, v -> a.copy(strength = v) }),
    Param("曝光 EV", -2f..2f, ::signed, { a, _ -> a.exposure }, { a, v -> a.copy(exposure = v) }),
    Param("對比", -1f..1f, ::signed, { a, _ -> a.contrast }, { a, v -> a.copy(contrast = v) }),
    Param("飽和", -1f..1f, ::signed, { a, _ -> a.saturation }, { a, v -> a.copy(saturation = v) }),
    Param("色溫", -1f..1f, ::signed, { a, _ -> a.temperature }, { a, v -> a.copy(temperature = v) }),
    Param("色調", -1f..1f, ::signed, { a, _ -> a.tint }, { a, v -> a.copy(tint = v) }),
    Param("褪色", 0f..1f, { "%.2f".format(it) }, { a, _ -> a.fade }, { a, v -> a.copy(fade = v) }),
    Param("顆粒", 0f..1f, { "%.2f".format(it) }, { a, s -> a.grain ?: s.grain.amount }, { a, v -> a.copy(grain = v) }),
    Param("顆粒大小", 0.0005f..0.003f, { "%.2f‰".format(it * 1000) }, { a, s -> a.grainSize ?: s.grain.size }, { a, v -> a.copy(grainSize = v) }),
    Param("Halation", 0f..1.5f, { "%.2f".format(it) }, { a, s -> a.halation ?: s.halation.amount }, { a, v -> a.copy(halation = v) }),
    Param("Bloom", 0f..1f, { "%.2f".format(it) }, { a, s -> a.bloom ?: s.bloom.amount }, { a, v -> a.copy(bloom = v) }),
    Param("暗角", 0f..1f, { "%.2f".format(it) }, { a, s -> a.vignette ?: s.lens.vignette }, { a, v -> a.copy(vignette = v) }),
)

@Composable
private fun ParamsPanel(vm: LabViewModel) {
    val spec = vm.look
    val a = vm.adjust
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${spec.name} 參數（每款濾鏡各自保存）", color = Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.resetAdjust() }) { Text("重設", color = Accent) }
        }
        for (p in PARAMS) {
            val v = p.get(a, spec)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(72.dp))
                Slider(
                    value = v.coerceIn(p.range.start, p.range.endInclusive),
                    onValueChange = { vm.setAdjust(p.set(vm.adjust, it)) },
                    valueRange = p.range,
                    modifier = Modifier.weight(1f).height(34.dp),
                )
                Text(p.format(v), color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(58.dp).padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun RecipePanel(spec: FilterSpec) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("${spec.name}　${spec.zh}", color = Accent, fontWeight = FontWeight.SemiBold)
        Text(spec.note, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        Text("色彩處理流程：線性光白平衡／曝光 → 光暈 → 暗角 → 3D LUT（OKLab 調色烘焙）→ 使用者調整 → 顆粒 → 抖動",
            color = Dim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        for (line in spec.recipe) Text("• $line", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun PerfPanel(vm: LabViewModel) {
    val mono = Modifier.padding(vertical = 1.dp)
    @Composable fun line(t: String) = Text(t, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = mono)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        line("GPU      ${vm.gpu}")
        val s = vm.source
        if (s != null) line("預覽     ${s.outW}×${s.outH}（來源 ${s.previewSrc.width}×${s.previewSrc.height}）")
        vm.lastPreview?.let { t ->
            line("預覽渲染 上次 ${"%.1f".format(vm.lastPreviewWallMs)} ms，近 20 次平均 ${"%.1f".format(vm.previewAvgMs)} ms（共 ${vm.previewCount} 次）")
            line("  上傳 ${"%.1f".format(t.uploadMs)}  光暈 ${"%.1f".format(t.glowMs)}  合成+讀回 ${"%.1f".format(t.compositeMs)} ms")
        }
        line("LUT 載入 ${vm.looks.size} 個共 ${"%.0f".format(vm.lutLoadMs)} ms（啟動時一次）")
        line("縮圖     ${vm.looks.size} 款共 ${"%.0f".format(vm.thumbsMs)} ms")
        if (vm.decodeMs > 0) line("預覽解碼 ${"%.0f".format(vm.decodeMs)} ms")
        vm.export?.let { e ->
            line("── 上次完整解析度匯出（${e.look}）")
            line("尺寸     ${e.w}×${e.h}（${"%.1f".format(e.w * e.h / 1e6)} MP）${if (e.downscaled) "  ⚠ 記憶體不足已縮小" else ""}")
            line("解碼     ${"%.0f".format(e.decodeMs)} ms")
            line("GPU      ${"%.0f".format(e.render.totalMs)} ms（上傳 ${"%.0f".format(e.render.uploadMs)}／光暈 ${"%.0f".format(e.render.glowMs)}／合成+讀回 ${"%.0f".format(e.render.compositeMs)}）")
            line("JPEG     ${"%.0f".format(e.encodeMs)} ms（品質 95）")
            line("總計     ${"%.0f".format(e.totalMs)} ms")
            line("Heap     峰值 ${e.heapPeakMb} MB／上限 ${e.heapMaxMb} MB")
            e.vsPreview?.let { r ->
                line("匯出縮小 vs 預覽（含顆粒）：平均 ${"%.2f".format(r.meanAbs)} 級，色偏 R${signed(r.biasR.toFloat())} G${signed(r.biasG.toFloat())} B${signed(r.biasB.toFloat())}")
            }
        }
        Spacer(Modifier.height(8.dp))
        for (l in vm.log.take(12)) Text(l, color = Dim, fontSize = 10.sp)
    }
}

@Composable
private fun CheckPanel(vm: LabViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("把同一張照片用匯出路徑以完整解析度渲染，縮小到預覽尺寸，和預覽渲染逐像素比較（顆粒關閉，其餘全部效果開啟）。標準：平均差 < 1.5 級、99% 像素 < 8 級。",
            color = Dim, fontSize = 11.sp)
        val c = vm.check
        if (c == null) {
            Text("尚未執行，按下方「檢查預覽＝匯出」。", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            val r = c.result
            Text(if (r.pass) "通過 ✓" else "未通過 ✗", color = if (r.pass) Color(0xFF7CD992) else Color(0xFFFF7070),
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("${c.look}：完整 ${c.fullW}×${c.fullH} → 縮小到 ${c.prevW}×${c.prevH}", color = Color.White, fontSize = 12.sp)
            Text("平均差 ${"%.2f".format(r.meanAbs)} 級　99% ${r.p99} 級　最大 ${r.max} 級", color = Color.White, fontSize = 12.sp)
            Text("整體色偏 R${signed(r.biasR.toFloat())} G${signed(r.biasG.toFloat())} B${signed(r.biasB.toFloat())}　耗時 ${"%.0f".format(c.ms)} ms",
                color = Color.White, fontSize = 12.sp)
            Text("差異圖（×8，越亮差越多）：", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            val img = remember(r) { r.heatmap.asImageBitmap() }
            Image(img, null, Modifier.fillMaxWidth().aspectRatio(r.heatmap.width.toFloat() / r.heatmap.height))
        }
    }
}
