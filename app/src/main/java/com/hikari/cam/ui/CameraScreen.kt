package com.hikari.cam.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hikari.cam.HikariApp
import com.hikari.cam.camera.CameraCaps
import com.hikari.cam.camera.CameraController
import com.hikari.cam.camera.Flash
import com.hikari.cam.data.Media
import com.hikari.cam.lab.LabViewModel
import com.hikari.data.UserSettings
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import com.hikari.engine.gl.CameraPreviewRenderer
import com.hikari.engine.gl.OffscreenRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Captures and their processing; survives recomposition and keeps working while the user shoots on. */
class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val hikari = app as HikariApp
    val controller = CameraController(app)
    var caps by mutableStateOf<CameraCaps?>(null); private set
    var processing by mutableIntStateOf(0); private set
    var lastThumb by mutableStateOf<Bitmap?>(null); private set
    var lastUri by mutableStateOf<android.net.Uri?>(null); private set
    var message by mutableStateOf<String?>(null)
    var seed = 1f; private set

    fun onCaps(c: CameraCaps) { caps = c }

    fun newSeed() { seed = (System.nanoTime() % 997).toFloat() + 1f }

    /**
     * Full-resolution capture: CameraX JPEG -> decoded upright -> cropped to the chosen aspect ->
     * rendered by the film engine at full size with the same look, adjustments and seed as the
     * preview -> new JPEG with the original's EXIF. The unfiltered JPEG (and DNG) can be kept too.
     */
    fun shoot(look: FilterSpec, adjust: Adjustments, aspect: Float, s: UserSettings) {
        val ctx = getApplication<Application>()
        val shotSeed = seed
        processing++
        viewModelScope.launch {
            val file = File(ctx.cacheDir, "shot_${System.nanoTime()}.jpg")
            try {
                val shot = controller.takePicture(file)
                val d = Media.decode(ctx, Media.source(file), hikari.renderer.maxTexture(), copies = 1)
                val w = d.bitmap.width
                val h = d.bitmap.height
                // the preview aspect is portrait (w/h); a landscape-held shot uses its inverse
                val target = if (w > h) 1f / aspect else aspect
                val img = w.toFloat() / h
                val (cw, ch) = if (img > target) (target / img) to 1f else 1f to (img / target)
                val crop = floatArrayOf((1 - cw) / 2, (1 - ch) / 2, (1 + cw) / 2, (1 + ch) / 2)
                val outW = (w * cw).roundToInt()
                val outH = (h * ch).roundToInt()
                val req = withContext(Dispatchers.IO) {
                    OffscreenRenderer.Request(look, hikari.looks.lut(look), adjust, crop = crop, seed = shotSeed)
                }
                val out = hikari.renderer.render(d.bitmap, req, outW, outH, releaseSource = true)
                val uri = Media.saveJpeg(ctx, out, s.jpegQuality, "_" + look.id)
                Media.copyExif(ctx, null, file, uri)
                lastThumb = withContext(Dispatchers.Default) { LabViewModel.fit(out, 160) }
                out.recycle()
                lastUri = uri
                if (s.saveOriginal) {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                    Media.saveFileCopy(ctx, file, "image/jpeg", "HIKARI_${stamp}_original.jpg")
                }
                message = "已儲存 ${outW}×${outH}${if (shot.dng != null) " + DNG" else ""}"
            } catch (e: Throwable) {
                message = "拍攝失敗：${e.message}"
            } finally {
                file.delete()
                processing--
            }
        }
    }
}

@Composable
fun CameraScreen(app: AppViewModel, vm: CameraViewModel = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("需要相機權限才能拍照。", color = Color.White)
            Text("不給權限也可以在 Gallery 匯入照片套用濾鏡。", color = HikariColors.Dim, fontSize = 12.sp)
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp)) { Text("允許相機") }
        }
        return
    }
    CameraContent(app, vm)
}

@Composable
private fun CameraContent(app: AppViewModel, vm: CameraViewModel) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val settings by app.settings.collectAsState()
    val favorites by app.favorites.collectAsState()
    val look = app.look(settings.lastLook)
    val adjust = remember(settings.cameraAdjustJson) { Adjustments.fromJson(settings.cameraAdjustJson) }
    val aspect = UserSettings.ratio(settings.aspect)
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flash by remember { mutableStateOf(Flash.OFF) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var ev by remember { mutableIntStateOf(0) }
    var showEv by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()

    val renderer = remember {
        CameraPreviewRendererHolder()
    }
    val glView = remember {
        GLSurfaceView(ctx).apply {
            setEGLContextClientVersion(3)
            val r = CameraPreviewRenderer(ctx.assets, this) { st -> vm.controller.onSurfaceTexture(st) }
            renderer.r = r
            setRenderer(r)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    val r = renderer.r!!

    // what the preview shows = what the capture renders
    LaunchedEffect(look.id, adjust, aspect) {
        val lut = withContext(Dispatchers.IO) { (ctx.applicationContext as HikariApp).looks.lut(look) }
        r.look = CameraPreviewRenderer.Look(look, lut, adjust)
        r.cropAspect = aspect
        r.seed = vm.seed
        glView.requestRender()
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lens, settings.saveRaw) {
        val c = try {
            vm.controller.bind(owner, r, lens, settings.saveRaw)
        } catch (e: Throwable) {
            android.util.Log.w("HikariCamera", "bind failed", e)
            cameraError = "無法開啟相機。\n這台裝置可能沒有可用的相機，或相機正被其他 App 使用。"
            return@LaunchedEffect
        }
        cameraError = null
        vm.onCaps(c)
        app.rawSupported = c.raw
        zoom = c.minZoom.coerceAtLeast(1f).coerceAtMost(c.maxZoom)
        vm.controller.setZoom(zoom)
        vm.controller.setFlash(flash)
        ev = 0
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        glView.onResume()
        onDispose {
            owner.lifecycle.removeObserver(obs)
            glView.onPause()
            vm.controller.unbind()
        }
    }

    fun shoot() {
        if (timer == 0) { vm.shoot(look, adjust, aspect, settings); vm.newSeed(); r.seed = vm.seed; return }
        scope.launch {
            for (t in timer downTo 1) { countdown = t; delay(1000) }
            countdown = 0
            vm.shoot(look, adjust, aspect, settings); vm.newSeed(); r.seed = vm.seed
        }
    }

    val caps = vm.caps
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // top controls: only what this camera supports
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            if (caps?.hasFlash == true) TextButton(onClick = {
                flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size]; vm.controller.setFlash(flash)
            }) { Text("閃光 " + mapOf(Flash.OFF to "關", Flash.AUTO to "自動", Flash.ON to "開")[flash], color = Color.White, fontSize = 12.sp) }
            else Text("無閃光燈", color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
            TextButton(onClick = { timer = listOf(0, 3, 10)[(listOf(0, 3, 10).indexOf(timer) + 1) % 3] }) {
                Text(if (timer == 0) "計時 關" else "計時 ${timer}s", color = Color.White, fontSize = 12.sp)
            }
            TextButton(onClick = {
                val i = UserSettings.ASPECTS.indexOf(settings.aspect)
                app.updateSettings { it.copy(aspect = UserSettings.ASPECTS[(i + 1) % UserSettings.ASPECTS.size]) }
            }) { Text(settings.aspect, color = Color.White, fontSize = 12.sp) }
            TextButton(onClick = { app.updateSettings { it.copy(grid = !it.grid) } }) {
                Text("格線", color = if (settings.grid) HikariColors.Accent else Color.White, fontSize = 12.sp)
            }
            if (caps != null && caps.evRange.upper > caps.evRange.lower) TextButton(onClick = { showEv = !showEv }) {
                Text("EV ${"%+.1f".format(ev * caps.evStep)}", color = if (ev != 0) HikariColors.Accent else Color.White, fontSize = 12.sp)
            }
        }
        if (showEv && caps != null) {
            Slider(ev.toFloat(), { ev = it.roundToInt(); vm.controller.setExposure(ev) },
                valueRange = caps.evRange.lower.toFloat()..caps.evRange.upper.toFloat(),
                modifier = Modifier.padding(horizontal = 24.dp))
        }

        // preview, sized to the chosen aspect
        // the preview keeps the chosen aspect and fits inside the free space (width or height first),
        // so it never overflows over the controls above it
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
            val fitHeight = maxWidth / maxHeight > aspect
            Box(
                (if (fitHeight) Modifier.fillMaxHeight().aspectRatio(aspect) else Modifier.fillMaxWidth().aspectRatio(aspect))
                    .pointerInput(caps) {
                        detectTransformGestures { _, _, z, _ ->
                            val c = caps ?: return@detectTransformGestures
                            zoom = (zoom * z).coerceIn(c.minZoom, c.maxZoom)
                            vm.controller.setZoom(zoom)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            focusAt = p
                            vm.controller.focusAt(p.x / size.width, p.y / size.height)
                        }
                    }
            ) {
                AndroidView({ glView }, Modifier.fillMaxSize())
                cameraError?.let {
                    Text("$it\n\n仍可在 Gallery 匯入照片套用濾鏡。", color = HikariColors.Dim, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
                if (settings.grid) Canvas(Modifier.fillMaxSize()) {
                    val c = Color(0x66FFFFFF)
                    for (i in 1..2) {
                        drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1f)
                        drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1f)
                    }
                }
                focusAt?.let { f ->
                    LaunchedEffect(f) { delay(1200); focusAt = null }
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(HikariColors.Accent, radius = 36f, center = f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                }
                if (countdown > 0) Text("$countdown", color = Color.White, fontSize = 72.sp, modifier = Modifier.align(Alignment.Center))
                if (caps != null && caps.maxZoom > caps.minZoom) Text("${"%.1f".format(zoom)}×", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        .background(Color(0x66000000), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${look.name}　${look.zh}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAdjust = !showAdjust }) { Text(if (showAdjust) "濾鏡" else "調整") }
        }
        if (showAdjust) {
            AdjustSliders(look, adjust,
                onChange = { a -> app.updateSettings { it.copy(cameraAdjustJson = a.toJson()) } },
                onReset = { app.updateSettings { it.copy(cameraAdjustJson = "") } },
                modifier = Modifier.fillMaxWidth().weight(0.7f).background(HikariColors.Panel))
        } else {
            LookStrip(app.ordered(), app.chartThumbs, look.id, favorites,
                onSelect = { s -> app.updateSettings { it.copy(lastLook = s.id) }; app.touch(s.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), size = 72)
        }

        // shutter row
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(HikariColors.Panel)
                .clickable { vm.lastUri?.let { app.go(Route.Editor(it)) } }) {
                vm.lastThumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                if (vm.processing > 0) Text("…", color = HikariColors.Accent, modifier = Modifier.align(Alignment.Center))
            }
            Box(Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                .background(if (vm.processing > 2) Color.Gray else Color.White)
                .testTag("shutter").semantics { contentDescription = "快門" }
                .clickable(enabled = vm.processing <= 2 && countdown == 0) { shoot() })
            if (caps != null && caps.hasFront && caps.hasBack) TextButton(onClick = {
                lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            }) { Text("翻轉", color = Color.White) } else Box(Modifier.size(52.dp))
        }
    }
    Toast(vm.message) { vm.message = null }
}

/** Holds the GL renderer created inside the view factory. */
private class CameraPreviewRendererHolder { var r: CameraPreviewRenderer? = null }
