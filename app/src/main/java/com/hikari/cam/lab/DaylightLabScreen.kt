package com.hikari.cam.lab

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Accent = Color(0xFFFFB86B)
private val Dim = Color(0xFF9A9A9A)

/** All Daylight Collection looks side by side on the same daylight test photo, scene by scene. */
@Composable
fun DaylightLabScreen(onBack: () -> Unit, vm: DaylightLabViewModel = viewModel()) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }
    var open by remember { mutableStateOf<String?>(null) }
    BackHandler { if (open != null) open = null else onBack() }
    val src = vm.sources[vm.scene]

    open?.let { id ->
        val spec = vm.looks.first { it.id == id }
        val eff = vm.results[id]
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹ 返回", color = Color.White, modifier = Modifier.clickable { open = null }.padding(4.dp))
                Text("${spec.name}  ${spec.zh}", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (src != null && eff != null) CompareView(src, eff)
            }
            Text(spec.note, color = Dim, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            Text("拖動畫面看原圖／效果分界，按住看原圖", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Color.White, fontSize = 24.sp, modifier = Modifier.clickable { onBack() }.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text("Daylight Filter Lab", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(vm.busy ?: (if (vm.renderMs > 0) "GPU 算繪 ${vm.looks.size} 款：%.0f ms".format(vm.renderMs)
                    else "同一張白天照片比較日光系列 ${vm.looks.size} 款"), color = Dim, fontSize = 11.sp)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in DaylightScene.entries) {
                val on = s == vm.scene
                Text((if (vm.imported[s] == true) "● " else "") + "${s.ordinal + 1}. ${s.zh}",
                    color = if (on) Color.Black else Color.White, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Accent else Color(0xFF222222))
                        .clickable { vm.select(s) }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(vm.scene.hint, color = Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("換成我的照片", color = Accent, fontSize = 13.sp,
                modifier = Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(6.dp))
            if (vm.imported[vm.scene] == true) Text("還原", color = Dim, fontSize = 13.sp,
                modifier = Modifier.clickable { vm.resetScene() }.padding(6.dp))
        }
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(span = { GridItemSpan(2) }) {
                Tile("原圖 Original", src, null) {}
            }
            items(vm.looks, key = { it.id }) { spec ->
                Tile(spec.name, vm.results[spec.id], spec.id) { open = spec.id }
            }
        }
    }
    vm.message?.let { Text(it, color = Color.White) }
}

@Composable
private fun Tile(label: String, bmp: android.graphics.Bitmap?, id: String?, onClick: () -> Unit) {
    val ratio = bmp?.let { it.width.toFloat() / it.height } ?: (4f / 3f)
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color(0xFF151515))
        .border(0.5.dp, Color(0xFF2A2A2A)).clickable(enabled = id != null, onClick = onClick)) {
        if (bmp != null) Image(bmp.asImageBitmap(), label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text(label, color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
