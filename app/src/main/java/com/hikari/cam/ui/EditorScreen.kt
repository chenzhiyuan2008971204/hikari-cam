package com.hikari.cam.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hikari.cam.lab.CompareView
import com.hikari.engine.Adjustments

@Composable
fun EditorScreen(app: AppViewModel, uri: Uri, lookId: String?, vm: EditorViewModel = viewModel()) {
    LaunchedEffect(uri) { vm.open(uri, lookId) }
    val favorites by app.favorites.collectAsState()
    val presets by app.presets.collectAsState()
    val settings by app.settings.collectAsState()
    var saveRecipe by remember { mutableStateOf(false) }   // "save as camera recipe" dialog
    var tab by remember { mutableStateOf(0) } // 0 looks, 1 effects, 2 timestamp, 3 frame, 4 smart
    // Back first leaves a tool (timestamp, frame, smart...) and shows the photo with its filter strip
    // again; back from there leaves the editor. The arrow in the top bar does the same.
    val goBack = { if (tab != 0) tab = 0 else { app.back(); Unit } }
    BackHandler { goBack() }
    var pick by remember { mutableStateOf(0) } // which composition suggestion is drawn on the photo
    var showStrength by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) app.back()
    }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        // top bar: back · undo redo · delete · save
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { goBack() }
            Text(if (vm.fullW > 0) "${vm.fullW}×${vm.fullH}" else "", color = HikariColors.Dim, fontSize = 11.sp,
                modifier = Modifier.weight(1f).padding(start = 6.dp))
            BarIcon(Icons.AutoMirrored.Outlined.Undo, "還原", vm.canUndo) { vm.undo() }
            BarIcon(Icons.AutoMirrored.Outlined.Redo, "重做", vm.canRedo) { vm.redo() }
            BarIcon(Icons.Outlined.BookmarkAdd, "存成相機配方") { saveRecipe = true }
            BarIcon(Icons.Outlined.DeleteOutline, "刪除") { confirmDelete = true }
            BarIcon(Icons.Outlined.FileDownload, "儲存", vm.busy == null && vm.preview != null) { vm.export(settings.jpegQuality) }
        }
        if (vm.busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = HikariColors.Accent)
            Text(vm.busy ?: "", color = HikariColors.Accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        // the photo is the main thing
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            val o = vm.original
            val p = vm.preview
            if (o != null && p != null) Box(Modifier.clip(RoundedCornerShape(14.dp))) {
                val fb = vm.framed
                when {
                    tab == 4 -> AnalysisView(p, vm.report?.suggestions?.getOrNull(pick))
                    // with a timestamp or a frame the preview is the composed result (what export writes)
                    fb != null -> FramedView(fb, vm, placing = tab == 2)
                    else -> CompareView(o, p)
                }
            }
            // look name over the photo, like a print label
            Text(vm.look.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                    .background(Color(0x55000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
            Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(34.dp).clip(CircleShape).background(Color(0x66000000))
                .clickable { app.toggleFavorite(vm.look.id) }, contentAlignment = Alignment.Center) {
                Text(if (vm.look.id in favorites) "♥" else "♡", color = if (vm.look.id in favorites) Color(0xFFFF6B7A) else Color.White, fontSize = 18.sp)
            }
        }

        if (tab == 0) {
            LookCategoryBar(app, vm.looks, short = true)
            val shown = looksInCategory(app, vm.looks)
            // filter tiles; the selected one shows its strength, tap it again for the ruler
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                items(shown.size, key = { shown[it].id }) { i ->
                    val spec = shown[i]
                    val sel = spec.id == vm.look.id
                    Column(Modifier.width(66.dp).clickable {
                        if (sel) showStrength = !showStrength else { vm.selectLook(spec); app.touch(spec.id); showStrength = false }
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(62.dp).clip(RoundedCornerShape(10.dp)).background(HikariColors.Panel)
                            .border(if (sel) 2.dp else 0.dp, if (sel) Color(0xFFE9E2D2) else Color.Transparent, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center) {
                            vm.thumbs[spec.id]?.let { b ->
                                val img = remember(b) { b.asImageBitmap() }
                                Image(img, spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } ?: FilmIcon(spec, 54.dp)
                            if (sel) Box(Modifier.fillMaxSize().background(Color(0x99252019)), contentAlignment = Alignment.Center) {
                                Text("${(vm.adjust.strength * 100).roundToInt()}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(spec.name, color = if (sel) Color.White else Color(0xFF9A9A9A), fontSize = 10.sp, maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            if (showStrength) RulerSlider(vm.adjust.strength, { vm.updateAdjust(vm.adjust.copy(strength = it)) }, { vm.commit() },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        } else if (tab == 2) {
            StampPanel(vm, app)
        } else if (tab == 3) {
            FramePanel(vm, app)
        } else if (tab == 4) {
            SmartPanel(vm, pick, { pick = it }, { spec -> vm.selectLook(spec); app.touch(spec.id) })
        } else {
            AdjustSliders(
                spec = vm.look, adjust = vm.adjust,
                onChange = { vm.updateAdjust(it) }, onCommit = { vm.commit() }, onReset = { vm.reset() },
                modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
            )
        }
        // tabs
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            for ((i, label) in listOf("濾鏡", "效果", "時間戳", "邊框", "智慧").withIndex()) {
                Column(Modifier.clickable { tab = i }.padding(horizontal = 9.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (tab == i) HikariColors.Accent else Color.Transparent))
                    Text(label, color = if (tab == i) Color.White else Color(0xFF8A8A8A), fontSize = 13.sp,
                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Text("預設", color = Color(0xFF8A8A8A), fontSize = 13.sp, modifier = Modifier.clickable { showPresets = true }.padding(horizontal = 9.dp))
        }
    }

    if (saveRecipe) RecipeDialog(app, vm.look, vm.adjust, vm.stamp.encode(), vm.frame.encode(), null) { saveRecipe = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這張照片？") },
            text = { Text("照片會從手機相簿中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val u = vm.uri ?: return@TextButton
                    val req = com.hikari.cam.data.Media.deleteRequest(ctx, listOf(u))
                    if (req != null) deleteLauncher.launch(IntentSenderRequest.Builder(req).build())
                    else scope.launch {
                        val need = com.hikari.cam.data.Media.deleteDirect(ctx, listOf(u))
                        if (need != null) deleteLauncher.launch(IntentSenderRequest.Builder(need).build()) else app.back()
                    }
                }) { Text("刪除", color = Color(0xFFFF7070)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("自訂參數") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (presets.isEmpty()) Text("還沒有儲存的參數。", color = HikariColors.Dim)
                    for (p in presets) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.apply(p.lookId, Adjustments.fromJson(p.adjustJson)); showPresets = false },
                                modifier = Modifier.weight(1f)) {
                                Text("${p.name}（${app.look(p.lookId).name}）", color = Color.White)
                            }
                            TextButton(onClick = { app.deletePreset(p.id) }) { Text("刪除", color = HikariColors.Dim) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresets = false; naming = true }) { Text("儲存目前參數") } },
            dismissButton = { TextButton(onClick = { showPresets = false }) { Text("關閉") } },
        )
    }
    if (naming) {
        var name by remember { mutableStateOf("${vm.look.name} 自訂") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("儲存參數") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { app.savePreset(name.ifBlank { vm.look.name }, vm.look.id, vm.adjust); naming = false }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("取消") } },
        )
    }
    Toast(vm.message) { vm.message = null }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (enabled) Color.White else Color(0xFF555555), modifier = Modifier.size(22.dp))
    }
}

/** Strength as a ruler: ticks every 5 %, long ticks every 25 %, drag or tap anywhere. */
@Composable
fun RulerSlider(value: Float, onChange: (Float) -> Unit, onCommit: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier.height(36.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onCommit) { ch, _ -> onChange((ch.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) { detectTapGestures { p -> onChange((p.x / size.width).coerceIn(0f, 1f)); onCommit() } }
    ) {
        val n = 20
        for (i in 0..n) {
            val x = size.width * i / n
            val long = i % 5 == 0
            drawLine(Color(0xFF8A8A8A), androidx.compose.ui.geometry.Offset(x, size.height * (if (long) 0.25f else 0.45f)),
                androidx.compose.ui.geometry.Offset(x, size.height * 0.75f), if (long) 2f else 1.2f)
        }
        val x = size.width * value
        drawLine(Color.White, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 3f)
        drawCircle(Color.White, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, size.height * 0.08f))
    }
}

@Composable
fun Toast(msg: String?, onDone: () -> Unit) {
    if (msg == null) return
    LaunchedEffect(msg) {
        kotlinx.coroutines.delay(3000)
        onDone()
    }
    Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
        Text(msg, color = Color.White, modifier = Modifier
            .background(Color(0xE0303030), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp))
    }
}


private fun roleColor(role: String) = when (role) {
    "thirds" -> Color(0x66FFFFFF)
    "horizon" -> Color(0xFF5CC8FF)
    "vertical" -> Color(0xFFB48CFF)
    "leading" -> Color(0xFFFFB74D)
    "subject" -> Color(0xFFFFE066)
    "target" -> Color(0xFF6BE38A)
    else -> Color.White
}

/** The photo with the evidence of one suggestion drawn on it: the lines, boxes and points that were measured. */
@Composable
fun AnalysisView(photo: android.graphics.Bitmap, s: com.hikari.vision.Suggestion?) {
    val ratio = photo.width.toFloat() / photo.height
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitWide = maxWidth / maxHeight > ratio
        val m = if (fitWide) Modifier.fillMaxHeight().aspectRatio(ratio) else Modifier.fillMaxWidth().aspectRatio(ratio)
        Box(m) {
            Image(remember(photo) { photo.asImageBitmap() }, "照片", Modifier.fillMaxSize())
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().testTag("analysisOverlay")) {
                val w = size.width; val h = size.height
                fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * w, y * h)
                if (s == null) return@Canvas
                s.crop?.let { c ->
                    // outside the suggested crop is dimmed
                    val dim = Color(0x99000000)
                    drawRect(dim, o(0f, 0f), androidx.compose.ui.geometry.Size(w, c.top * h))
                    drawRect(dim, o(0f, c.bottom), androidx.compose.ui.geometry.Size(w, (1f - c.bottom) * h))
                    drawRect(dim, o(0f, c.top), androidx.compose.ui.geometry.Size(c.left * w, c.h * h))
                    drawRect(dim, o(c.right, c.top), androidx.compose.ui.geometry.Size((1f - c.right) * w, c.h * h))
                    drawRect(Color.White, o(c.left, c.top), androidx.compose.ui.geometry.Size(c.w * w, c.h * h),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                for (ov in s.overlays) when (ov) {
                    is com.hikari.vision.Overlay.Seg -> drawLine(roleColor(ov.role), o(ov.x0, ov.y0), o(ov.x1, ov.y1),
                        if (ov.role == "thirds") 1.dp.toPx() else 2.5.dp.toPx(),
                        pathEffect = if (ov.role == "target") androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null)
                    is com.hikari.vision.Overlay.Rect -> drawRect(roleColor(ov.role), o(ov.box.left, ov.box.top),
                        androidx.compose.ui.geometry.Size(ov.box.w * w, ov.box.h * h), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    is com.hikari.vision.Overlay.Point -> {
                        drawCircle(roleColor(ov.role), 7.dp.toPx(), o(ov.x, ov.y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                        if (ov.role != "target") drawCircle(roleColor(ov.role), 2.5.dp.toPx(), o(ov.x, ov.y))
                    }
                }
            }
        }
    }
}

/** Top-3 looks for this photo and up to three composition suggestions, each with its measured evidence. */
@Composable
private fun SmartPanel(vm: EditorViewModel, pick: Int, onPick: (Int) -> Unit, onLook: (com.hikari.engine.FilterSpec) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        if (vm.analysing) { Text("正在分析照片…", color = HikariColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(8.dp)); return@Column }
        Text("濾鏡推薦 · 場景：${vm.sceneLabel}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text("依照片量測到的光線、色彩與場景，對全部 ${vm.looks.size} 款濾鏡評分後取前三名", color = HikariColors.Dim, fontSize = 10.sp)
        for ((i, rc) in vm.recs.withIndex()) {
            val spec = vm.looks.firstOrNull { it.id == rc.look.id } ?: continue
            val sel = spec.id == vm.look.id
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) Color(0xFF2A2620) else HikariColors.Panel)
                .clickable { onLook(spec) }.padding(8.dp).testTag("recCard"), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                    vm.thumbs[spec.id]?.let { b -> Image(remember(b) { b.asImageBitmap() }, spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("${i + 1}. ${spec.name}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(rc.reason, color = Color(0xFFD8D2C4), fontSize = 11.sp)
                    Text("與原圖差異：${com.hikari.cam.smart.Smart.difference(rc.look)}", color = HikariColors.Dim, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${rc.score}", color = HikariColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("適合度", color = HikariColors.Dim, fontSize = 9.sp)
                }
            }
        }
        Text("構圖分析", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("${vm.detectorNote} · 分析 ${vm.analysisMs.roundToInt()} ms · 只在量到明確依據時才給建議", color = HikariColors.Dim, fontSize = 10.sp)
        val rep = vm.report
        if (rep == null || rep.suggestions.isEmpty()) {
            Text(rep?.note?.ifEmpty { null } ?: "沒有可用的分析結果。", color = Color(0xFFD8D2C4), fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp).testTag("compNote"))
        } else for ((i, sg) in rep.suggestions.withIndex()) {
            val sel = i == pick
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) Color(0xFF2A2620) else HikariColors.Panel)
                .clickable { onPick(i) }.padding(8.dp).testTag("compCard")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sg.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("信心 ${(sg.confidence * 100).roundToInt()}%", color = HikariColors.Accent, fontSize = 11.sp)
                }
                Text("依據：${sg.evidence}", color = Color(0xFFD8D2C4), fontSize = 11.sp)
                Text("目前：${sg.current}", color = Color(0xFFD8D2C4), fontSize = 11.sp)
                Text("建議：${sg.action}", color = Color.White, fontSize = 11.sp)
                Text("預期：${sg.benefit}", color = HikariColors.Dim, fontSize = 11.sp)
                if (sel && sg.crop != null) Text("照片上的白框是建議的裁切範圍", color = HikariColors.Dim, fontSize = 10.sp)
            }
        }
        Box(Modifier.height(8.dp))
    }
}
