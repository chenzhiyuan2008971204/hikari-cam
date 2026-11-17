package com.hikari.cam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
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
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(com.hikari.studio.LookSort.DEFAULT) }
    val shown = looksInCategory(app, app.looks, query, sort)
    var detail by remember { mutableStateOf<FilterSpec?>(null) }
    var pendingLook by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) app.go(Route.Editor(uri, pendingLook))
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
                Text("邊框圖庫 ${com.hikari.studio.Frames.all.size} 款 ›", color = HikariColors.Accent, fontSize = 13.sp,
                    modifier = Modifier.clickable { app.go(Route.Frames) }.padding(6.dp).testTag("openFrames"))
            }
        }
        // every thumbnail is the real filter on the same standard photo; pick which photo here
        item {
            Text("預覽照片（縮圖都由正式濾鏡引擎算出；「依濾鏡特色」會替每款濾鏡挑適合它的照片）", color = HikariColors.Dim, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("依濾鏡特色", settings.thumbScene == "auto", "thumb-auto") { app.updateSettings { it.copy(thumbScene = "auto") } }
                for (sm in app.samples) {
                    val on = sm.id == settings.thumbScene
                    Text(sm.zh, color = if (on) Color.Black else Color.White, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) HikariColors.Accent else Color(0xFF222222))
                            .clickable { app.updateSettings { it.copy(thumbScene = sm.id) } }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
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
        // one category at a time: horizontal category navigation, then only that category's filters
        item(key = "cats") {
            LookCategoryBar(app, app.looks)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val cats = com.hikari.studio.Browse.categories(SERIES_ZH.toList(), lookItems(app.looks), favorites)
                val cur = cats.first { it.id == com.hikari.studio.Browse.valid(settings.filterCategory, cats) }
                Text(cur.title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).testTag("catTitle"))
                Text("${shown.size} / ${cur.count} 款", color = HikariColors.Dim, fontSize = 12.sp, modifier = Modifier.testTag("catCount"))
            }
            Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                if (query.isEmpty()) Text("在這個分類中搜尋", color = Color(0xFF6A6A6A), fontSize = 13.sp)
                androidx.compose.foundation.text.BasicTextField(query, { query = it }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(HikariColors.Accent), modifier = Modifier.fillMaxWidth().testTag("lookSearch"))
            }
            ChipRow { for (so in com.hikari.studio.LookSort.values()) Chip(so.zh, sort == so, "sort-${so.name}") { sort = so }
                Chip("匯出這個分類的 XMP", false, "exportXmpAll") { app.exportXmp(shown) } }
        }
        if (shown.isEmpty()) item(key = "empty") { Text(if (settings.filterCategory == "fav" && query.isEmpty()) "還沒有收藏的濾鏡。在濾鏡詳情按「收藏」即可加入。" else "這個分類中沒有符合的濾鏡。",
            color = HikariColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
        items(shown, key = { it.id }) { spec ->
            LookRow(spec, spec.id in favorites, app) { detail = spec }
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
                    var saveRecipe by remember { mutableStateOf(false) }
                    TextButton(onClick = { saveRecipe = true }, modifier = Modifier.testTag("saveRecipe")) { Text("存成相機配方（C1–C7）…") }
                    if (saveRecipe) RecipeDialog(app, spec, com.hikari.engine.Adjustments.DEFAULT, "", "", null) { saveRecipe = false }
                }
            },
            confirmButton = {
                Row {
                    var exportMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { exportMenu = true }, modifier = Modifier.testTag("exportMenu")) { Text("匯出 ▾") }
                        androidx.compose.material3.DropdownMenu(exportMenu, { exportMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Column { Text("XMP 設定檔"); Text("Lightroom／Camera Raw 的「設定檔」", fontSize = 11.sp, color = Color.Gray) } },
                                onClick = { exportMenu = false; app.exportXmp(listOf(spec)) }, modifier = Modifier.testTag("exportXmp"))
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Column { Text("3D LUT（.cube，33×33×33）"); Text("DaVinci Resolve、Photoshop、Premiere 等", fontSize = 11.sp, color = Color.Gray) } },
                                onClick = { exportMenu = false; app.exportCube(listOf(spec)) }, modifier = Modifier.testTag("exportCube"))
                        }
                    }
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
