package com.hikari.cam.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.cam.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos made with Hikari (Pictures/Hikari) plus importing any photo from the device. */
@Composable
fun GalleryScreen(app: AppViewModel) {
    val ctx = LocalContext.current
    var photos by remember { mutableStateOf<List<Media.Photo>?>(null) }
    LaunchedEffect(app.route) { photos = Media.list(ctx) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) app.go(Route.Editor(uri))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Gallery", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("匯入照片編輯")
            }
        }
        val list = photos
        when {
            list == null -> Unit
            list.isEmpty() -> Text("還沒有照片。用相機拍一張，或匯入相簿裡的照片。", color = HikariColors.Dim,
                modifier = Modifier.padding(24.dp))
            else -> LazyVerticalGrid(
                GridCells.Fixed(3), contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(list, key = { it.uri.toString() }) { p ->
                    val thumb by produceState<Bitmap?>(null, p.uri) {
                        value = withContext(Dispatchers.IO) {
                            runCatching { ctx.contentResolver.loadThumbnail(p.uri, Size(360, 360), null) }.getOrNull()
                        }
                    }
                    Box(Modifier.aspectRatio(1f).background(HikariColors.Panel).clickable { app.go(Route.Editor(p.uri)) }) {
                        thumb?.let { Image(it.asImageBitmap(), p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }
            }
        }
    }
}
