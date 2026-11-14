package com.hikari.cam.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
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
    var panel by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { app.back() }) { Text("‹ 返回", color = Color.White) }
            Text(if (vm.fullW > 0) "${vm.fullW}×${vm.fullH}" else "", color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.undo() }, enabled = vm.canUndo) { Text("還原") }
            TextButton(onClick = { vm.redo() }, enabled = vm.canRedo) { Text("重做") }
            Button(onClick = { vm.export(settings.jpegQuality) }, enabled = vm.busy == null && vm.preview != null) { Text("匯出") }
        }
        if (vm.busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = HikariColors.Accent)
            Text(vm.busy ?: "", color = HikariColors.Accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val o = vm.original
            val p = vm.preview
            if (o != null && p != null) CompareView(o, p)
        }
        Text("左右拖曳比較 · 按住看原圖", color = HikariColors.Dim, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${vm.look.name}　${vm.look.zh}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { app.toggleFavorite(vm.look.id) }) {
                Text(if (vm.look.id in favorites) "★ 已收藏" else "☆ 收藏", color = HikariColors.Accent)
            }
            TextButton(onClick = { showPresets = true }) { Text("預設") }
            TextButton(onClick = { panel = !panel }) { Text(if (panel) "收起調整" else "調整") }
        }
        if (panel) {
            AdjustSliders(
                spec = vm.look, adjust = vm.adjust,
                onChange = { vm.setAdjust(it) }, onCommit = { vm.commit() }, onReset = { vm.reset() },
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).background(HikariColors.Panel),
            )
        } else {
            LookStrip(vm.looks, vm.thumbs, vm.look.id, favorites, onSelect = { vm.selectLook(it); app.touch(it.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), size = 80)
        }
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
