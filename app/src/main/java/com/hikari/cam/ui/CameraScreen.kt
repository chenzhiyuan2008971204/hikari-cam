package com.hikari.cam.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.media.MediaActionSound
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Timer10
import androidx.compose.material.icons.outlined.Timer3
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.BiasAlignment
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
                if (s.dateStamp) drawDateStamp(out, stampText(java.util.Date(), s.stampNote))
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
    var wb by remember { mutableStateOf(WbPreset.AUTO) }
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
    LaunchedEffect(lens, settings.saveRaw, settings.naturalDetail) {
        val c = try {
            vm.controller.bind(owner, r, lens, settings.saveRaw, settings.naturalDetail)
        } catch (e: Throwable) {
            android.util.Log.w("HikariCamera", "bind failed", e)
            cameraError = "無法開啟相機。\n這台裝置可能沒有可用的相機，或相機正被其他 App 使用。"
            return@LaunchedEffect
        }
        cameraError = null
        vm.onCaps(c)
        app.rawSupported = c.raw
        app.naturalApplied = c.naturalApplied
        app.cameraReport = c.report
        zoom = c.minZoom.coerceAtLeast(1f).coerceAtMost(c.maxZoom)
        vm.controller.setZoom(zoom)
        vm.controller.setFlash(flash)
        vm.controller.setWhiteBalance(wb.mode)
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

    // shutter feedback: press animation, haptic tick, system shutter sound, a short white flash
    val view = LocalView.current
    val shutterSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    DisposableEffect(Unit) { onDispose { shutterSound.release() } }
    val flashAlpha = remember { Animatable(0f) }
    var showLooks by remember { mutableStateOf(true) }

    fun fire() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        // shutter sound only when the phone is not on silent or vibrate
        val audio = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        if (audio.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        scope.launch { flashAlpha.snapTo(0.75f); flashAlpha.animateTo(0f, tween(260)) }
        vm.shoot(look, adjust, aspect, settings); vm.newSeed(); r.seed = vm.seed
    }

    fun shoot() {
        if (timer == 0) { fire(); return }
        scope.launch {
            for (t in timer downTo 1) { countdown = t; delay(1000) }
            countdown = 0
            fire()
        }
    }

    val caps = vm.caps
    var panel by remember { mutableIntStateOf(0) } // 0 looks, 1 composition, 2 tone, 3 adjust
    val guide = composition(settings.composition)
    var showHint by remember { mutableStateOf(false) }

    // live composition: tracked subject (smoothed) and the phone's tilt, only while a guide is on
    var subject by remember { mutableStateOf<com.hikari.cam.camera.Subject?>(null) }
    var lastSeen by remember { mutableStateOf(0L) }
    var tilt by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(guide?.id) {
        if (guide == null) { vm.controller.onSubject = null; subject = null; tilt = null; return@DisposableEffect onDispose {} }
        vm.controller.onSubject = { n ->
            val now = System.currentTimeMillis()
            if (n != null) {
                val o = subject
                subject = if (o == null) n else com.hikari.cam.camera.Subject(
                    o.left + (n.left - o.left) * 0.5f, o.top + (n.top - o.top) * 0.5f,
                    o.right + (n.right - o.right) * 0.5f, o.bottom + (n.bottom - o.bottom) * 0.5f)
                lastSeen = now
            } else if (now - lastSeen > 700) subject = null
        }
        val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val roll = Math.toDegrees(kotlin.math.atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat()
                // relative to the nearest upright or landscape orientation
                tilt = roll - Math.round(roll / 90f) * 90f
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
        onDispose { vm.controller.onSubject = null; sm.unregisterListener(listener) }
    }
    val guidance = if (guide != null) subject?.let { guidance(guide.id, it) } else null
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(guidance?.aligned) {
        val a = guidance?.aligned == true
        if (a && !wasAligned) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        wasAligned = a
    }
    LaunchedEffect(settings.composition) {
        if (guide != null) { showHint = true; delay(4000); showHint = false }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // top bar: icons only, only what this camera supports
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            if (caps?.hasFlash == true) TopIcon(
                when (flash) { Flash.OFF -> Icons.Outlined.FlashOff; Flash.AUTO -> Icons.Outlined.FlashAuto; Flash.ON -> Icons.Outlined.FlashOn },
                "閃光燈", flash != Flash.OFF,
            ) { flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size]; vm.controller.setFlash(flash) }
            TopIcon(when (timer) { 3 -> Icons.Outlined.Timer3; 10 -> Icons.Outlined.Timer10; else -> Icons.Outlined.TimerOff }, "計時器", timer != 0) {
                timer = listOf(0, 3, 10)[(listOf(0, 3, 10).indexOf(timer) + 1) % 3]
            }
            Box(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Color(0xFF888888), RoundedCornerShape(6.dp))
                .clickable {
                    val i = UserSettings.ASPECTS.indexOf(settings.aspect)
                    app.updateSettings { it.copy(aspect = UserSettings.ASPECTS[(i + 1) % UserSettings.ASPECTS.size]) }
                }.padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(settings.aspect, color = Color.White, fontSize = 12.sp)
            }
            TopIcon(if (settings.grid) Icons.Outlined.GridOn else Icons.Outlined.GridOff, "格線", settings.grid) {
                app.updateSettings { it.copy(grid = !it.grid) }
            }
            TopIcon(Icons.Outlined.Exposure, "光影", ev != 0 || panel == 2) { panel = if (panel == 2) 0 else 2 }
        }

        // preview: as large as the space allows, rounded like a print
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp).clipToBounds(), contentAlignment = Alignment.Center) {
            val fitHeight = maxWidth / maxHeight > aspect
            Box(
                (if (fitHeight) Modifier.fillMaxHeight().aspectRatio(aspect) else Modifier.fillMaxWidth().aspectRatio(aspect))
                    .clip(RoundedCornerShape(14.dp))
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
                    val c = Color(0x55FFFFFF)
                    for (i in 1..2) {
                        drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1f)
                        drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1f)
                    }
                }
                guide?.let {
                    CompositionOverlay(it)
                    LiveGuideOverlay(subject, guidance, tilt)
                }
                focusAt?.let { f ->
                    LaunchedEffect(f) { delay(1200); focusAt = null }
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(HikariColors.Accent, radius = 36f, center = f, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                }
                // composition name, and its tip for a few seconds after choosing it
                guide?.let { g ->
                    Column(Modifier.align(Alignment.TopCenter).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(g.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
                        if (showHint) Text(g.hint, color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp).background(Color(0x66000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
                if (countdown > 0) Text("$countdown", color = Color.White, fontSize = 72.sp, modifier = Modifier.align(Alignment.Center))
                if (settings.dateStamp) Text(stampText(java.util.Date(), settings.stampNote), color = Color.White, fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, letterSpacing = 2.sp,
                    modifier = Modifier.align(BiasAlignment(0f, 0.64f)))
                // look name, top-left like a film label
                Text(look.name, color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color(0x55000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (caps != null && caps.maxZoom > caps.minZoom) Box(Modifier.align(Alignment.BottomCenter)) {
                    ZoomStops(caps.minZoom, caps.maxZoom, caps.lensStops, caps.mainFocal35, zoom) { z ->
                        zoom = z
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        vm.controller.setZoom(z)
                    }
                }
                // capture flash
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = flashAlpha.value }.background(Color.White))
            }
        }

        // bottom panel: 濾鏡 / 構圖 / 調整
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            for ((i, label) in listOf("濾鏡", "構圖", "光影", "調整").withIndex()) {
                Column(Modifier.clickable { panel = i }.padding(horizontal = 18.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (panel == i) Color.White else Color(0xFF8A8A8A), fontSize = 13.sp,
                        fontWeight = if (panel == i) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.padding(top = 3.dp).size(width = 14.dp, height = 2.dp)
                        .background(if (panel == i) HikariColors.Accent else Color.Transparent, RoundedCornerShape(1.dp)))
                }
            }
        }
        when (panel) {
            0 -> LookStrip(app.looks, emptyMap(), look.id, favorites,
                onSelect = { s -> app.updateSettings { it.copy(lastLook = s.id) }; app.touch(s.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), size = 60)
            1 -> CompositionStrip(guide?.id, { id -> app.updateSettings { it.copy(composition = id ?: "") } },
                Modifier.fillMaxWidth().padding(vertical = 6.dp))
            2 -> TonePanel(
                evIndex = ev, evMin = caps?.evRange?.lower ?: 0, evMax = caps?.evRange?.upper ?: 0, evStep = caps?.evStep ?: 0f,
                onEv = { ev = it; vm.controller.setExposure(it) },
                temp = adjust.temperature, tint = adjust.tint,
                onTempTint = { t, g -> app.updateSettings { it.copy(cameraAdjustJson = adjust.copy(temperature = t, tint = g).toJson()) } },
                onCommit = {},
                preset = wb, onPreset = { p -> wb = p; vm.controller.setWhiteBalance(p.mode) },
                stamp = settings.dateStamp, note = settings.stampNote,
                onStamp = { v -> app.updateSettings { it.copy(dateStamp = v) } },
                onNote = { v -> app.updateSettings { it.copy(stampNote = v) } },
                modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
            )
            else -> AdjustSliders(look, adjust,
                onChange = { a -> app.updateSettings { it.copy(cameraAdjustJson = a.toJson()) } },
                onReset = { app.updateSettings { it.copy(cameraAdjustJson = "") } },
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp))
        }

        // shutter row
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(HikariColors.Panel)
                .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(12.dp))
                .clickable { vm.lastUri?.let { app.go(Route.Editor(it)) } }) {
                vm.lastThumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                if (vm.processing > 0) Text("…", color = HikariColors.Accent, modifier = Modifier.align(Alignment.Center))
            }
            ShutterButton(enabled = vm.processing <= 2 && countdown == 0, busy = vm.processing > 0) { shoot() }
            if (caps != null && caps.hasFront && caps.hasBack) Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF222222)).clickable {
                    lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Cameraswitch, contentDescription = "翻轉", tint = Color.White) }
            else Box(Modifier.size(48.dp))
        }
    }
    Toast(vm.message) { vm.message = null }
}

/**
 * Zoom stops like the system camera: the phone's own lenses (e.g. 0.6 / 1 / 2.6, read from the
 * hardware), plus 2x / 5x / 10x digital stops inside the zoom range that are not next to a lens.
 * The active stop shows the exact current zoom; pinch still zooms freely.
 */
@Composable
private fun ZoomStops(min: Float, max: Float, lenses: List<Float>, focal35: Float?, zoom: Float, onZoom: (Float) -> Unit) {
    val native = lenses.ifEmpty { listOf(1f) }
    val stops = (native + listOf(2f, 5f, 10f).filter { d -> d in min..max && native.none { kotlin.math.abs(kotlin.math.ln(it / d)) < 0.2f } })
        .distinct().sorted()
    if (stops.size < 2) return
    val active = stops.minBy { kotlin.math.abs(kotlin.math.ln(it / zoom)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.background(Color(0x77000000), RoundedCornerShape(30.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (z in stops) {
                val on = z == active
                val lens = z in native
                fun fmt(v: Float) = if (v == Math.round(v).toFloat() && v >= 1f) "%.0f".format(v) else "%.1f".format(v)
                Column(
                    Modifier.clip(RoundedCornerShape(22.dp))
                        .background(if (on) Color(0xCC2A2A2A) else Color.Transparent)
                        .clickable { onZoom(z) }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text((if (on) fmt(zoom) else fmt(z)) + "×",
                        color = if (on) HikariColors.Accent else Color.White,
                        fontSize = 13.sp, fontWeight = if (lens) FontWeight.Bold else FontWeight.Normal)
                    // native lenses: their 35 mm-equivalent focal length and a dot; digital stops: "數位"
                    Text(
                        if (lens) (focal35?.let { "${Math.round(it * z)}mm" } ?: "鏡頭") + " •"
                        else "數位",
                        color = if (lens) Color(0xFFDDDDDD) else Color(0xFF999999), fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = if (on) HikariColors.Accent else Color.White, modifier = Modifier.size(22.dp))
    }
}

/** A shutter that feels pressed: the inner disc sinks on touch and springs back. */
@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) { if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.42f, stiffness = 700f), label = "shutter")
    Box(
        Modifier.size(78.dp).border(2.dp, Color(0xFFE8E2D6), CircleShape).padding(5.dp)
            .border(1.dp, HikariColors.Accent.copy(alpha = 0.6f), CircleShape)
            .testTag("shutter").semantics { contentDescription = "快門" }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
            .background(if (!enabled) Color.Gray else if (pressed) Color(0xFFD9D3C7) else Color(0xFFF4EFE6)))
        if (busy) Box(Modifier.size(18.dp).clip(CircleShape).background(HikariColors.Accent))
    }
}

/** Holds the GL renderer created inside the view factory. */
private class CameraPreviewRendererHolder { var r: CameraPreviewRenderer? = null }
