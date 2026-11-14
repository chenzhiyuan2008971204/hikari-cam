package com.hikari.cam.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hikari.cam.data.Downloads
import com.hikari.cam.filter.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Comparison image, verification note and XMP / .cube downloads for a photographer look. */
@Composable
fun StyleInfoDialog(filter: Filter, onDismiss: () -> Unit) {
    val key = filter.styleKey ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var compare by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) {
        compare = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("compare/$key.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() }
            }.getOrNull()
        }
    }

    fun download(asset: String, fileName: String) {
        scope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { Downloads.saveAsset(context, asset, fileName) }
                "已存到「下載/HikariCam/$fileName」"
            } catch (e: Exception) {
                "下載失敗：${e.message}"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(filter.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(filter.subtitle, color = Dim, fontSize = 12.sp)

            Box(
                Modifier.fillMaxWidth().aspectRatio(1080f / 718f).clip(RoundedCornerShape(8.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                compare?.let { Image(it, "對照圖", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
            }
            Text("上：原圖　下：套用「${filter.name}」", color = Dim, fontSize = 12.sp)
            filter.note?.let { Text(it, color = Color.White, fontSize = 13.sp) }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { download("xmp/$key.xmp", "Hikari-${filter.subtitle}.xmp") },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("下載 XMP（Lightroom／Camera Raw 描述檔）") }
            OutlinedButton(
                onClick = { download("looks/$key.cube", "Hikari-${filter.subtitle}.cube") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("下載 .cube（LUT）", color = Color.White) }
            Text(
                "XMP：Lightroom Classic 用「檔案 › 匯入設定檔與預設集」，或放進 Camera Raw 的 Settings 資料夾後重新啟動，" +
                    "在描述檔瀏覽器的「Hikari 光」群組裡。.cube 可用在 Photoshop、DaVinci Resolve、VSCO 等。",
                color = Dim, fontSize = 11.sp,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("關閉", color = Accent) }
        }
    }
}
