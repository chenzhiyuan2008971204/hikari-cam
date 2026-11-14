package com.hikari.cam.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.cam.HikariApp
import com.hikari.cam.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Photos made with Hikari (Pictures/Hikari), importing any photo, and deleting.
 * Tap opens the editor; long-press starts selecting, then taps add/remove photos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(app: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Media.Photo>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(app.route, reload) { photos = Media.list(ctx) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) app.go(Route.Editor(uri))
    }

    fun forgetEdits(uris: Collection<Uri>) = scope.launch {
        val db = (ctx.applicationContext as HikariApp).db
        for (u in uris) db.edits().delete(u.toString())
    }

    var pending by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val systemConfirm = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                forgetEdits(pending)
                message = "已刪除 ${pending.size} 張"
                selected = emptySet()
            } else {
                // Android 10: permission granted for that file, try the rest again
                scope.launch {
                    val again = Media.deleteDirect(ctx, pending)
                    if (again == null) { forgetEdits(pending); message = "已刪除 ${pending.size} 張"; selected = emptySet() }
                }
            }
        }
        reload++
    }

    fun delete(uris: List<Uri>) {
        pending = uris
        val req = Media.deleteRequest(ctx, uris)
        if (req != null) {
            systemConfirm.launch(IntentSenderRequest.Builder(req).build())
        } else scope.launch {
            val need = Media.deleteDirect(ctx, uris)
            if (need != null) systemConfirm.launch(IntentSenderRequest.Builder(need).build())
            else { forgetEdits(uris); message = "已刪除 ${uris.size} 張"; selected = emptySet(); reload++ }
        }
    }

    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Text("已選 ${selected.size} 張", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = photos.orEmpty().map { it.uri }.toSet() }) { Text("全選") }
                TextButton(onClick = { selected = emptySet() }) { Text("取消", color = Color.White) }
                Button(onClick = { confirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD04848), contentColor = Color.White)) { Text("刪除") }
            } else {
                Text("Gallery", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
                Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("匯入照片編輯")
                }
            }
        }
        val list = photos
        when {
            list == null -> Unit
            list.isEmpty() -> Text("還沒有照片。用相機拍一張，或匯入相簿裡的照片。", color = HikariColors.Dim,
                modifier = Modifier.padding(24.dp))
            else -> {
                if (!selecting) Text("長按照片可選取、刪除", color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                LazyVerticalGrid(
                    GridCells.Fixed(3), contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(list, key = { it.uri.toString() }) { p ->
                        val thumb by produceState<Bitmap?>(null, p.uri) {
                            value = withContext(Dispatchers.IO) {
                                runCatching { ctx.contentResolver.loadThumbnail(p.uri, Size(360, 360), null) }.getOrNull()
                            }
                        }
                        val isSel = p.uri in selected
                        Box(
                            Modifier.aspectRatio(1f).background(HikariColors.Panel)
                                .border(if (isSel) 3.dp else 0.dp, if (isSel) HikariColors.Accent else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (selecting) selected = if (isSel) selected - p.uri else selected + p.uri
                                        else app.go(Route.Editor(p.uri))
                                    },
                                    onLongClick = { selected = selected + p.uri },
                                )
                        ) {
                            thumb?.let { Image(it.asImageBitmap(), p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            if (selecting) Text(if (isSel) "●" else "○", color = if (isSel) HikariColors.Accent else Color.White,
                                fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                            if (p.name.contains("original")) Text("原圖", color = Color.White, fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.BottomStart).background(Color(0x99000000)).padding(horizontal = 4.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("刪除 ${selected.size} 張照片？") },
            text = { Text("照片會從手機相簿中移除。") },
            confirmButton = { TextButton(onClick = { confirm = false; delete(selected.toList()) }) { Text("刪除", color = Color(0xFFFF7070)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
    Toast(message) { message = null }
}
