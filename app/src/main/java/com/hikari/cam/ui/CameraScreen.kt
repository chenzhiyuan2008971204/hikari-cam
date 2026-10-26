package com.hikari.cam.ui

import android.graphics.Bitmap
import android.hardware.camera2.CameraMetadata
import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.cam.camera.Flash
import com.hikari.cam.camera.HikariCamera
import com.hikari.cam.camera.ManualCaps
import com.hikari.cam.camera.ManualSettings
import com.hikari.cam.data.DateStamp
import com.hikari.cam.data.FilterStore
import com.hikari.cam.data.PhotoStore
import com.hikari.cam.filter.Adjustments
import com.hikari.cam.filter.CubeLut
import com.hikari.cam.filter.Filter
import com.hikari.cam.filter.Presets
import com.hikari.cam.filter.effectiveParams
import com.hikari.cam.gl.CameraRenderer
import com.hikari.cam.gl.FilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

@Composable
fun CameraScreen(store: FilterStore, onEdit: (android.net.Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val camera = remember { HikariCamera(context) }

    var filter by remember { mutableStateOf<Filter>(Presets.all[1]) }
    var strength by remember { mutableFloatStateOf(1f) }
    var adj by remember { mutableStateOf(Adjustments()) }
    var tab by remember { mutableIntStateOf(0) }
    var facing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var caps by remember { mutableStateOf<ManualCaps?>(null) }
    var manual by remember { mutableStateOf(ManualSettings()) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var flash by remember { mutableStateOf(Flash.OFF) }
    var dateStamp by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var lastShot by remember { mutableStateOf<ImageBitmap?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var viewSize by remember { mutableStateOf(IntSize(1, 1)) }
    var lut by remember { mutableStateOf<CubeLut?>(null) }
    var saveDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Filter?>(null) }
    var pendingLut by remember { mutableStateOf<android.net.Uri?>(null) }

    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
        }
    }
    val renderer = remember {
        CameraRenderer(glView) { st -> camera.onSurfaceTexture(st) }.also {
            glView.setRenderer(it)
            glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            camera.unbind()
        }
    }

    LaunchedEffect(facing) {
        caps = runCatching { camera.bind(lifecycleOwner, renderer, facing) }.getOrNull()
        zoom = 1f
        camera.applyManual(manual)
        camera.setFlash(flash)
    }
    LaunchedEffect(filter) {
        lut = filter.lutFile?.let { withContext(Dispatchers.IO) { CubeLut.load(it) } }
        renderer.lut = lut
    }
    // Push the current look to the live preview.
    renderer.params = effectiveParams(filter, strength, adj)
    glView.requestRender()

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) { delay(1200); focusPoint = null }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onEdit(uri)
    }
    val pickLut = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingLut = uri
    }

    fun shoot() {
        if (busy) return
        busy = true
        val params = effectiveParams(filter, strength, adj)
        val shotLut = lut
        val stamp = dateStamp
        scope.launch {
            try {
                val raw = camera.capture()
                val filtered = FilterEngine.render(raw, params, shotLut)
                raw.recycle()
                val final = if (stamp) DateStamp.draw(filtered) else filtered
                PhotoStore.save(context, final)
                val t = Bitmap.createScaledBitmap(final, 160, 160 * final.height / final.width, true)
                lastShot = t.asImageBitmap()
                final.recycle()
            } catch (e: Exception) {
                Toast.makeText(context, "拍照失敗：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        // ── Top bar ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Pill(
                when (flash) { Flash.OFF -> "閃光 關"; Flash.AUTO -> "閃光 自動"; Flash.ON -> "閃光 開" },
                flash != Flash.OFF,
            ) {
                flash = Flash.entries[(flash.ordinal + 1) % 3]
                camera.setFlash(flash)
            }
            Pill("日期", dateStamp) { dateStamp = !dateStamp }
            Pill("格線", grid) { grid = !grid }
            Pill(filter.name, true) { tab = 0 }
        }

        // ── Live preview (3:4) ──
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        focusPoint = p
                        camera.focusAt(p.x, p.y, size.width, size.height)
                    }
                },
        ) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
            if (grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color.White.copy(alpha = 0.25f)
                    for (i in 1..2) {
                        drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1f)
                        drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1f)
                    }
                }
            }
            focusPoint?.let { p ->
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFFFFD27A), radius = 36.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
                }
            }
            // Lens / zoom buttons
            caps?.let { c ->
                val stops = listOf(0.6f, 1f, 2f, 3.2f, 5f, 10f).filter { it >= c.minZoom - 0.05f && it <= c.maxZoom + 0.01f }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (s in stops) {
                        val sel = kotlin.math.abs(zoom - s) < 0.05f
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (sel) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { zoom = s; camera.setZoom(s) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val label = if (s < 1f) ".6" else if (s % 1f == 0f) "${s.toInt()}" else "$s"
                            Text(
                                if (sel) "${label}x" else label, fontSize = 12.sp,
                                color = if (sel) Color(0xFFFFD27A) else Color.White, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            if (manual.iso != null || manual.exposureNs != null || manual.focusDiopters != null) {
                Text(
                    "M", color = Color(0xFFFFD27A), fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )
            }
        }

        // ── Panel ──
        Tabs(listOf("濾鏡", "調整", "手動"), tab) { tab = it }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> FilterStrip(
                    filters = Presets.all + store.custom,
                    selectedId = filter.id,
                    onSelect = { filter = it; strength = 1f },
                    onLongPress = { if (it.category == com.hikari.cam.filter.Category.MINE) deleteTarget = it },
                    onImportLut = { pickLut.launch(arrayOf("*/*")) },
                )
                1 -> AdjustPanel(strength, { strength = it }, adj, { adj = it }, onSaveFilter = { saveDialog = true })
                2 -> ManualPanel(caps, manual) { manual = it; camera.applyManual(it) }
            }
        }

        // ── Bottom controls ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                val shot = lastShot
                if (shot != null) Image(shot, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("相簿", color = Color.White, fontSize = 12.sp)
            }
            // Shutter
            Box(
                Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp)
                    .clip(CircleShape).background(if (busy) Dim else Color.White)
                    .clickable { shoot() },
            )
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Panel).clickable {
                    facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                    else CameraSelector.LENS_FACING_BACK
                },
                contentAlignment = Alignment.Center,
            ) { Text("翻轉", color = Color.White, fontSize = 12.sp) }
        }
    }

    if (saveDialog) {
        NameDialog("存成我的濾鏡", filter.name + " 改", onDismiss = { saveDialog = false }) { name ->
            val p = effectiveParams(filter, strength, adj)
            filter = store.saveCustom(name, p, filter.lutFile)
            strength = 1f
            adj = Adjustments()
            saveDialog = false
            tab = 0
        }
    }
    pendingLut?.let { uri ->
        NameDialog("匯入 LUT 濾鏡", "LUT ${store.custom.size + 1}", onDismiss = { pendingLut = null }) { name ->
            pendingLut = null
            scope.launch {
                try {
                    filter = store.importLut(uri, name)
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
            deleteTarget = null
        }
    }
}

private val WB_MODES = listOf(
    "自動" to CameraMetadata.CONTROL_AWB_MODE_AUTO,
    "日光" to CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT,
    "陰天" to CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
    "鎢絲燈" to CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT,
    "日光燈" to CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT,
)

private fun shutterText(ns: Long): String {
    val s = ns / 1e9
    return if (s >= 0.3) String.format("%.1fs", s) else "1/${(1 / s).roundToInt()}"
}

@Composable
fun ManualPanel(caps: ManualCaps?, s: ManualSettings, onChange: (ManualSettings) -> Unit) {
    if (caps == null) {
        Text("相機尚未就緒", color = Dim, modifier = Modifier.padding(16.dp))
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        val autoBtn: @Composable (Boolean, () -> Unit) -> Unit = { isAuto, reset ->
            Pill("A", isAuto, reset)
        }
        // ISO
        val iso = s.iso ?: caps.isoRange.lower
        LabeledSlider(
            "ISO", iso.toFloat(), caps.isoRange.lower.toFloat()..caps.isoRange.upper.toFloat(),
            if (s.iso == null) "自動" else "$iso",
            { onChange(s.copy(iso = it.roundToInt(), exposureNs = s.exposureNs ?: 10_000_000L)) },
        ) { autoBtn(s.iso == null && s.exposureNs == null) { onChange(s.copy(iso = null, exposureNs = null)) } }

        // Shutter, log scale
        val lo = ln(caps.exposureRangeNs.lower.toDouble())
        val hi = ln(caps.exposureRangeNs.upper.toDouble())
        val ns = s.exposureNs ?: 10_000_000L
        val pos = ((ln(ns.toDouble()) - lo) / (hi - lo)).toFloat()
        LabeledSlider(
            "快門", pos, 0f..1f,
            if (s.exposureNs == null) "自動" else shutterText(ns),
            {
                val v = exp(lo + (hi - lo) * it).toLong()
                onChange(s.copy(exposureNs = v, iso = s.iso ?: 400))
            },
        ) { autoBtn(s.iso == null && s.exposureNs == null) { onChange(s.copy(iso = null, exposureNs = null)) } }

        // Exposure compensation (only meaningful in auto exposure)
        if (s.iso == null && s.exposureNs == null && caps.evRange.upper > caps.evRange.lower) {
            LabeledSlider(
                "曝光補償", s.evIndex.toFloat(), caps.evRange.lower.toFloat()..caps.evRange.upper.toFloat(),
                String.format("%+.1f", s.evIndex * caps.evStep),
                { onChange(s.copy(evIndex = it.roundToInt())) },
            )
        }

        // Focus: 0 diopters = infinity
        if (caps.minFocusDiopters > 0f) {
            val f = s.focusDiopters ?: 0f
            LabeledSlider(
                "對焦", f, 0f..caps.minFocusDiopters,
                if (s.focusDiopters == null) "自動" else if (f < 0.05f) "∞" else String.format("%.2fm", 1f / f),
                { onChange(s.copy(focusDiopters = it)) },
            ) { autoBtn(s.focusDiopters == null) { onChange(s.copy(focusDiopters = null)) } }
        }

        // White balance
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("白平衡", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
            for ((name, mode) in WB_MODES) Pill(name, s.awbMode == mode) { onChange(s.copy(awbMode = mode)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
