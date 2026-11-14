package com.hikari.cam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    var detail by remember { mutableStateOf<FilterSpec?>(null) }
    var pendingLook by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) app.go(Route.Editor(uri, pendingLook))
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Filters", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(16.dp)) }
        if (recents.isNotEmpty()) {
            item { SectionTitle("最近使用") }
            item {
                LookStrip(recents.map { app.look(it) }, app.chartThumbs, null, favorites, { detail = it }, size = 72)
            }
        }
        if (favorites.isNotEmpty()) {
            item { SectionTitle("收藏") }
            item {
                LookStrip(app.looks.filter { it.id in favorites }, app.chartThumbs, null, favorites, { detail = it }, size = 72)
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
                    app.chartThumbs[spec.id]?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(6.dp)))
                    }
                    Text(spec.note, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(120.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(6.dp)).background(HikariColors.Panel)) {
            app.chartThumbs[spec.id]?.let { Image(it.asImageBitmap(), spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f)) {
            Text((if (favorite) "★ " else "") + spec.name, color = Color.White, fontSize = 15.sp)
            Text(spec.zh, color = HikariColors.Accent, fontSize = 12.sp)
            Text(spec.note, color = HikariColors.Dim, fontSize = 11.sp, maxLines = 2)
        }
    }
}
