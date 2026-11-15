package com.hikari.cam.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
    BackHandler { app.back() }
    val favorites by app.favorites.collectAsState()
    val presets by app.presets.collectAsState()
    val settings by app.settings.collectAsState()
    var tab by remember { mutableStateOf(0) } // 0 looks, 1 effects
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
            BarIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { app.back() }
            Text(if (vm.fullW > 0) "${vm.fullW}×${vm.fullH}" else "", color = HikariColors.Dim, fontSize = 11.sp,
                modifier = Modifier.weight(1f).padding(start = 6.dp))
            BarIcon(Icons.AutoMirrored.Outlined.Undo, "還原", vm.canUndo) { vm.undo() }
            BarIcon(Icons.AutoMirrored.Outlined.Redo, "重做", vm.canRedo) { vm.redo() }
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
            if (o != null && p != null) Box(Modifier.clip(RoundedCornerShape(14.dp))) { CompareView(o, p) }
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
            // filter tiles; the selected one shows its strength, tap it again for the ruler
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                items(vm.looks.size) { i ->
                    val spec = vm.looks[i]
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
        } else {
            AdjustSliders(
                spec = vm.look, adjust = vm.adjust,
                onChange = { vm.updateAdjust(it) }, onCommit = { vm.commit() }, onReset = { vm.reset() },
                modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
            )
        }
        // tabs
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            for ((i, label) in listOf("濾鏡", "效果").withIndex()) {
                Column(Modifier.clickable { tab = i }.padding(horizontal = 22.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (tab == i) HikariColors.Accent else Color.Transparent))
                    Text(label, color = if (tab == i) Color.White else Color(0xFF8A8A8A), fontSize = 13.sp,
                        fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Text("預設", color = Color(0xFF8A8A8A), fontSize = 13.sp, modifier = Modifier.clickable { showPresets = true }.padding(horizontal = 22.dp))
        }
    }

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
