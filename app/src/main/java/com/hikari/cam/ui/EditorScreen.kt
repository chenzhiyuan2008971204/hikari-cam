package com.hikari.cam.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.cam.data.DateStamp
import com.hikari.cam.data.FilterStore
import com.hikari.cam.data.PhotoStore
import com.hikari.cam.filter.Adjustments
import com.hikari.cam.filter.Category
import com.hikari.cam.filter.CubeLut
import com.hikari.cam.filter.Filter
import com.hikari.cam.filter.Presets
import com.hikari.cam.filter.effectiveParams
import com.hikari.cam.gl.FilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(uri: Uri, store: FilterStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    var full by remember { mutableStateOf<Bitmap?>(null) }
    var small by remember { mutableStateOf<Bitmap?>(null) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var original by remember { mutableStateOf<ImageBitmap?>(null) }
    var holdOriginal by remember { mutableStateOf(false) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }

    var filter by remember { mutableStateOf<Filter>(Presets.ORIGINAL) }
    var strength by remember { mutableFloatStateOf(1f) }
    var adj by remember { mutableStateOf(Adjustments()) }
    var lut by remember { mutableStateOf<CubeLut?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var dateStamp by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Filter?>(null) }
    var pendingLut by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(uri) {
        try {
            val f = PhotoStore.load(context, uri, 4096)
            val s = withContext(Dispatchers.Default) {
                val k = 1600f / maxOf(f.width, f.height)
                if (k < 1f) Bitmap.createScaledBitmap(f, (f.width * k).toInt(), (f.height * k).toInt(), true) else f
            }
            full = f
            small = s
            original = s.asImageBitmap()
        } catch (e: Exception) {
            Toast.makeText(context, "無法開啟照片：${e.message}", Toast.LENGTH_LONG).show()
            onClose()
        }
    }

    // Filter thumbnails from the photo itself.
    LaunchedEffect(small, store.custom.size) {
        val s = small ?: return@LaunchedEffect
        val k = 180f / maxOf(s.width, s.height)
        val t = Bitmap.createScaledBitmap(s, (s.width * k).toInt(), (s.height * k).toInt(), true)
        for (f in Presets.all + store.custom) {
            if (thumbs.containsKey(f.id)) continue
            val l = f.lutFile?.let { withContext(Dispatchers.IO) { CubeLut.load(it) } }
            thumbs[f.id] = FilterEngine.render(t, f.params, l).asImageBitmap()
        }
    }

    LaunchedEffect(filter) {
        lut = filter.lutFile?.let { withContext(Dispatchers.IO) { CubeLut.load(it) } }
    }

    // Re-render the preview when anything changes (restarts on each change, so it keeps up with sliders).
    LaunchedEffect(small, filter, strength, adj, lut, dateStamp) {
        val s = small ?: return@LaunchedEffect
        delay(30)
        var out = FilterEngine.render(s, effectiveParams(filter, strength, adj), lut)
        if (dateStamp) out = DateStamp.draw(out)
        shown = out.asImageBitmap()
    }

    val pickLut = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) pendingLut = u
    }

    fun save() {
        val src = full ?: return
        if (saving) return
        saving = true
        val params = effectiveParams(filter, strength, adj)
        val l = lut
        val stamp = dateStamp
        scope.launch {
            try {
                var out = FilterEngine.render(src, params, l)
                if (stamp) out = DateStamp.draw(out)
                PhotoStore.save(context, out)
                out.recycle()
                Toast.makeText(context, "已儲存到相簿 Pictures/Hikari", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "儲存失敗：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("‹ 返回", false, onClose)
            Pill("日期", dateStamp) { dateStamp = !dateStamp }
            Pill(if (saving) "儲存中…" else "儲存", true) { save() }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holdOriginal = true
                    tryAwaitRelease()
                    holdOriginal = false
                })
            },
            contentAlignment = Alignment.Center,
        ) {
            val img = if (holdOriginal) original else shown ?: original
            if (img != null) {
                Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator(color = Accent)
            }
            Text(
                if (holdOriginal) "原圖" else "按住看原圖", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(6.dp),
            )
        }

        Tabs(listOf("濾鏡", "調整"), tab) { tab = it }
        Box(Modifier.fillMaxWidth().height(if (tab == 0) 130.dp else 260.dp)) {
            when (tab) {
                0 -> FilterStrip(
                    filters = Presets.all + store.custom,
                    selectedId = filter.id,
                    thumbs = thumbs,
                    onSelect = { filter = it; strength = 1f },
                    onLongPress = { if (it.category == Category.MINE) deleteTarget = it },
                    onImportLut = { pickLut.launch(arrayOf("*/*")) },
                )
                else -> AdjustPanel(strength, { strength = it }, adj, { adj = it }, onSaveFilter = { saveDialog = true })
            }
        }
    }

    if (saveDialog) {
        NameDialog("存成我的濾鏡", filter.name + " 改", onDismiss = { saveDialog = false }) { name ->
            filter = store.saveCustom(name, effectiveParams(filter, strength, adj), filter.lutFile)
            strength = 1f
            adj = Adjustments()
            saveDialog = false
            tab = 0
        }
    }
    pendingLut?.let { u ->
        NameDialog("匯入 LUT 濾鏡", "LUT ${store.custom.size + 1}", onDismiss = { pendingLut = null }) { name ->
            pendingLut = null
            scope.launch {
                try {
                    filter = store.importLut(u, name)
                    strength = 1f
                } catch (e: Exception) {
                    Toast.makeText(context, "匯入失敗：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    deleteTarget?.let { f ->
        ConfirmDialog("刪除濾鏡", "要刪除「${f.name}」嗎？", "刪除", onDismiss = { deleteTarget = null }) {
            if (filter.id == f.id) filter = Presets.ORIGINAL
            store.delete(f)
            thumbs.remove(f.id)
            deleteTarget = null
        }
    }
}
