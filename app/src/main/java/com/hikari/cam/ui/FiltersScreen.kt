package com.hikari.cam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.engine.FilterSpec

/** The look catalogue: every look rendered on the test chart, with its recipe. */
@Composable
fun FiltersScreen(app: AppViewModel) {
    val favorites by app.favorites.collectAsState()
    val recents by app.recents.collectAsState()
    val presets by app.presets.collectAsState()
    val settings by app.settings.collectAsState()
    var detail by remember { mutableStateOf<FilterSpec?>(null) }
    var pendingLook by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) app.go(Route.Editor(uri, pendingLook))
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Filters", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp)) }
        // every thumbnail is the real filter on the same standard photo; pick which photo here
        item {
            Text("預覽照片（所有縮圖都是用正式濾鏡引擎在同一張照片上算出來的）", color = HikariColors.Dim, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (sm in app.samples) {
                    val on = sm.id == settings.thumbScene
                    Text(sm.zh, color = if (on) Color.Black else Color.White, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) HikariColors.Accent else Color(0xFF222222))
                            .clickable { app.updateSettings { it.copy(thumbScene = sm.id) } }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        if (recents.isNotEmpty()) {
            item { SectionTitle("最近使用") }
            item {
                LookStrip(recents.map { app.look(it) }, app.thumbs, null, favorites, { detail = it }, size = 72)
            }
        }
        if (favorites.isNotEmpty()) {
            item { SectionTitle("收藏") }
            item {
                LookStrip(app.looks.filter { it.id in favorites }, app.thumbs, null, favorites, { detail = it }, size = 72)
            }
        }
        if (presets.isNotEmpty()) {
            item { SectionTitle("自訂參數") }
            items(presets, key = { "preset${it.id}" }) { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.name}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(app.look(p.lookId).name, color = HikariColors.Dim, fontSize = 11.sp)
                    TextButton(onClick = { app.deletePreset(p.id) }) { Text("刪除", color = HikariColors.Dim) }
                }
            }
        }
        for ((series, title) in SERIES_ZH) {
            val group = app.looks.filter { it.series == series }
            if (group.isEmpty()) continue
            item(key = "title-$series") { SectionTitle(title) }
            items(group, key = { it.id }) { spec ->
                LookRow(spec, spec.id in favorites, app) { detail = spec }
            }
        }
        item { Box(Modifier.padding(32.dp)) }
    }

    detail?.let { spec ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${spec.name}　${spec.zh}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // the look on several standard scenes, rendered now by the engine
                    val previews by androidx.compose.runtime.produceState(emptyList<Pair<String, android.graphics.Bitmap>>(), spec.id) { value = app.scenePreviews(spec) }
                    if (previews.isEmpty()) Text("正在用濾鏡引擎算繪預覽…", color = HikariColors.Dim, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((name, bmp) in previews) Column {
                            Image(bmp.asImageBitmap(), name, Modifier.size(width = 200.dp, height = 140.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Text(name, color = HikariColors.Dim, fontSize = 10.sp)
                        }
                    }
                    Text(spec.note, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    if (spec.inspired.isNotEmpty()) Text("原創配方，色彩靈感來自 ${spec.inspired} 這一類底片模擬的風格（非官方演算法）", color = HikariColors.Dim, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 6.dp))
                    for (line in spec.recipe) Text("• $line", color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { app.toggleFavorite(spec.id) }) { Text(if (spec.id in favorites) "★" else "☆") }
                    TextButton(onClick = {
                        app.updateSettings { it.copy(lastLook = spec.id) }; app.touch(spec.id); detail = null; app.go(Route.Camera)
                    }) { Text("用來拍照") }
                    TextButton(onClick = {
                        pendingLook = spec.id; detail = null
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("編輯照片") }
                }
            },
        )
    }
}

@Composable
private fun LookRow(spec: FilterSpec, favorite: Boolean, app: AppViewModel, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(HikariColors.Panel), contentAlignment = Alignment.Center) {
            val b = app.thumbs[spec.id]
            if (b != null) Image(remember(b) { b.asImageBitmap() }, spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else FilmIcon(spec, 76.dp)
        }
        Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (favorite) "★ " else "") + spec.name, color = Color.White, fontSize = 17.sp)
            Text(spec.zh, color = HikariColors.Accent, fontSize = 13.sp)
        }
        Text(spec.note, color = HikariColors.Dim, fontSize = 12.sp, maxLines = 2)
        }
    }
}
