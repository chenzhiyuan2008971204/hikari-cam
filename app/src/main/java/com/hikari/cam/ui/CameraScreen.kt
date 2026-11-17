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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.hikari.engine.gl.FilmRecorder
import com.hikari.engine.gl.RecordableConfigChooser
import com.hikari.engine.gl.VideoSize
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
    fun shoot(look: FilterSpec, adjust: Adjustments, aspect: Float, s: UserSettings, recipe: com.hikari.data.FilmRecipe? = null) {
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
                var final = out
                var studio = false
                if (recipe != null) {
                    // the recipe's pipeline: original -> adjustments -> look (done above) -> timestamp -> frame -> output
                    val stamp = com.hikari.studio.StampSpec.decode(recipe.stampSpec).copy(enabled = recipe.stampOn)
                    val frame = if (recipe.frameOn) com.hikari.studio.FrameOptions.decode(recipe.frameOptions) else com.hikari.studio.FrameOptions()
                    if (stamp.enabled || frame.frame.isNotEmpty()) {
                        if (recipe.saveClean) Media.saveJpeg(ctx, out, s.jpegQuality, "_" + look.id + "_clean")
                        val now = java.time.LocalDateTime.now()
                        val info = com.hikari.cam.studio.PhotoInfo.read(ctx, android.net.Uri.fromFile(file)).let { com.hikari.cam.studio.PhotoInfo(it.exifTime ?: now, it.created ?: now, it.info, it.camera) }
                        val sc = withContext(Dispatchers.Default) {
                            com.hikari.cam.studio.StudioRenderer.scene(out.width, out.height, stamp, frame, info, com.hikari.cam.studio.StudioRenderer.palette(out))
                        }
                        if (sc != null) {
                            final = withContext(Dispatchers.Default) {
                                try { com.hikari.cam.studio.StudioRenderer.render(sc, out) }
                                catch (e: OutOfMemoryError) { System.gc(); com.hikari.cam.studio.StudioRenderer.render(sc, out, 0.7f, 1f) }
                            }
                            out.recycle()
                            studio = true
                        }
                    }
                } else if (s.dateStamp) {
                    // the capture moment is the true time of this photo
                    val spec = if (s.stampSpec.isNotEmpty()) com.hikari.studio.StampSpec.decode(s.stampSpec).copy(enabled = true) else null
                    val done = spec != null && runCatching {
                        val text = com.hikari.studio.Stamps.text(spec, java.time.LocalDateTime.now())
                        val prims = com.hikari.studio.Stamps.prims(spec, text, com.hikari.studio.R(0f, 0f, out.width.toFloat(), out.height.toFloat()))
                        com.hikari.cam.studio.StudioRenderer.drawOnto(out, prims)
                    }.isSuccess
                    if (!done) drawDateStamp(out, stampText(java.util.Date(), s.stampNote))
                }
                val uri = Media.saveJpeg(ctx, final, s.jpegQuality, "_" + look.id + (if (studio) "_studio" else ""))
                Media.copyExif(ctx, null, file, uri)
                lastThumb = withContext(Dispatchers.Default) { LabViewModel.fit(final, 160) }
                val savedW = final.width; val savedH = final.height
                final.recycle()
                lastUri = uri
                if (s.saveOriginal) {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                    Media.saveFileCopy(ctx, file, "image/jpeg", "HIKARI_${stamp}_original.jpg")
                }
                message = "已儲存 ${savedW}×${savedH}${if (shot.dng != null) " + DNG" else ""}"
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
    val recipes by app.recipes.collectAsState()
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
            setEGLConfigChooser(RecordableConfigChooser())   // a config the video encoder's surface can share
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
    // identifies this camera screen: while screens cross-fade the old one is disposed after the
    // new one started, and must not take the camera or the live guide away from it
    val screenToken = remember { Any() }
    var cameraError by remember { mutableStateOf<String?>(null) }
    // every lens this phone exposes by any route (listed, physical, hidden) for the lens picker
    // hidden-id probing is opt-in and remembered: the user asks for it once from the lens menu
    val prefs = remember { ctx.getSharedPreferences("lens", android.content.Context.MODE_PRIVATE) }
    var probeHidden by remember { mutableStateOf(prefs.getBoolean("probeHidden", false)) }
    // the lenses are only looked up when the lens menu is first opened: nothing extra touches the
    // camera service while the camera starts or shoots
    var probeWanted by remember { mutableStateOf(false) }
    val probe = remember(probeHidden, probeWanted) {
        if (!probeWanted) emptyList<com.hikari.cam.camera.LensOption>() to ""
        else runCatching { com.hikari.cam.camera.LensProbe.discover(ctx, probeHidden) }
            .getOrDefault(emptyList<com.hikari.cam.camera.LensOption>() to "")
    }
    LaunchedEffect(probe) {
        if (probe.second.isNotBlank()) app.cameraReport = listOf(vm.caps?.report.orEmpty(), probe.second)
            .filter { it.isNotBlank() }.joinToString("\n\n")
    }
    var lensOpt by remember { mutableStateOf<com.hikari.cam.camera.LensOption?>(null) }
    var lensMsg by remember { mutableStateOf<String?>(null) }
    var activePhysical by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val cb: (String?) -> Unit = { activePhysical = it }
        vm.controller.onActivePhysical = cb
        onDispose { if (vm.controller.onActivePhysical === cb) vm.controller.onActivePhysical = null }
    }
    // ---- optical telephoto: which lens serves the zoom -------------------------------------------------
    // The phone's own logical camera may switch to its telephoto by itself; if it does not, the zoom step
    // binds the physical lens (Camera2Interop). Either way the capture results are checked, and the app
    // falls back to digital zoom on the logical camera when the lens refuses or ignores the zoom.
    var autoTele by remember { mutableStateOf(prefs.getBoolean("teleAuto", true)) }
    var autoOpt by remember { mutableStateOf(false) }      // the current lens was chosen by the zoom, not by the user
    var pendingZoom by remember { mutableStateOf<Float?>(null) }
    val halResult = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val teleBlocked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val teles = remember(probe, vm.caps?.mainFocal35) {
        com.hikari.cam.camera.TelePlan.teles(probe.first, vm.caps?.mainFocal35 ?: probe.first.firstOrNull()?.focal35)
    }
    fun applyZoom(z: Float) {
        zoom = z
        val routed = autoTele && lens == CameraSelector.LENS_FACING_BACK && !settings.virtualCamera && (lensOpt == null || autoOpt)
        val pick = com.hikari.cam.camera.TelePlan.pick(teles, z)
        val plan = if (routed) com.hikari.cam.camera.TelePlan.step(teles, z, pick?.let { halResult[it.option.key] }, teleBlocked.toSet())
            else com.hikari.cam.camera.TelePlan.Step.Main
        when (plan) {
            is com.hikari.cam.camera.TelePlan.Step.Physical ->
                if (lensOpt?.key != plan.tele.option.key) { autoOpt = true; pendingZoom = z; lensOpt = plan.tele.option }
                else vm.controller.setZoom(com.hikari.cam.camera.TelePlan.relativeZoom(z, plan.tele))
            else ->
                if (autoOpt && lensOpt != null) { autoOpt = false; pendingZoom = z; lensOpt = null }   // back to the logical camera
                else vm.controller.setZoom(z)
        }
    }
    LaunchedEffect(Math.round(zoom * 5f), teles, lensOpt, autoTele) {
        val t = com.hikari.cam.camera.TelePlan.pick(teles, zoom) ?: return@LaunchedEffect
        if (!autoTele || (lensOpt != null && !autoOpt)) return@LaunchedEffect
        if (autoOpt && lensOpt?.key == t.option.key) {
            // we stream the lens ourselves: did the camera honour the zoom that is left over on top of it?
            delay(1000)
            val o = vm.controller.observed ?: return@LaunchedEffect
            val asked = com.hikari.cam.camera.TelePlan.relativeZoom(zoom, t)
            if (!com.hikari.cam.camera.TelePlan.honoured(asked, o)) {
                teleBlocked += t.option.key
                lensMsg = "這顆長焦鏡頭沒有照要求變焦（要 ${"%.1f".format(asked)}×，相機實際 ${"%.1f".format(o.zoomRatio ?: (o.cropFraction?.let { 1f / it } ?: 0f))}×）：改用數位變焦"
                autoOpt = false; pendingZoom = zoom; lensOpt = null
            }
        } else if (t.option.kind == com.hikari.cam.camera.LensOption.Kind.PHYSICAL && lensOpt == null && halResult[t.option.key] == null) {
            // does the phone move to the telephoto by itself at this zoom? the capture results say which lens streams
            delay(900)
            val o = vm.controller.observed ?: return@LaunchedEffect
            val id = o.activePhysical ?: return@LaunchedEffect   // the phone does not say which lens streams: cannot tell
            val ok = id == t.option.physicalId
            halResult[t.option.key] = ok
            if (ok) lensMsg = "這支手機會自己切到長焦（實體 #$id）" else applyZoom(zoom)
        }
    }
    LaunchedEffect(lensMsg) { if (lensMsg != null) { delay(3500); lensMsg = null } }
    LaunchedEffect(lens, settings.saveRaw, settings.naturalDetail, lensOpt, settings.virtualCamera) {
        val opt = lensOpt.takeIf { lens == CameraSelector.LENS_FACING_BACK }
        val c = try {
            vm.controller.bind(owner, r, lens, settings.saveRaw, settings.naturalDetail, opt,
                trackPhysical = false, owner2 = screenToken, virtual = settings.virtualCamera)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // the screen or the source changed while opening: not an error, the next bind is already on its way
        } catch (e: Throwable) {
            android.util.Log.w("HikariCamera", "bind failed", e)
            if (opt != null) {
                // the phone refused this lens: say why and go back to the default camera
                lensMsg = "這支手機不讓 App 使用這顆鏡頭：${e.message ?: e.javaClass.simpleName}"
                lensOpt = null
                return@LaunchedEffect
            }
            cameraError = if (settings.virtualCamera) "虛擬相機啟動失敗：${e.message ?: e.javaClass.simpleName}"
                else "無法開啟相機。\n這台裝置可能沒有可用的相機，或相機正被其他 App 使用。"
            return@LaunchedEffect
        }
        if (settings.virtualCamera) lensMsg = "虛擬相機：正在播放內建測試照片（設定可關閉）"
        if (opt != null) lensMsg = "已切換：${opt.label(probe.first.firstOrNull()?.focal35)}（${opt.source}）"
        cameraError = null
        vm.onCaps(c)
        app.rawSupported = c.raw
        app.naturalApplied = c.naturalApplied
        app.cameraReport = c.report
        val pz = pendingZoom
        pendingZoom = null
        if (pz != null) {
            // the zoom step rebound the camera (another lens): keep the zoom the user asked for
            zoom = pz.coerceIn(c.minZoom, c.maxZoom)
            val t = if (autoOpt) teles.firstOrNull { it.option.key == lensOpt?.key } else null
            vm.controller.setZoom(if (t != null) com.hikari.cam.camera.TelePlan.relativeZoom(zoom, t) else zoom)
        } else {
            zoom = c.minZoom.coerceAtLeast(1f).coerceAtMost(c.maxZoom)
            vm.controller.setZoom(zoom)
        }
        // a multi-lens phone: look its lenses up once, so 2x / 3x / 5x can reach a real telephoto
        if (!probeWanted && !settings.virtualCamera && (c.lensStops.size > 1 || c.maxZoom >= 4f)) probeWanted = true
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
            vm.controller.unbind(screenToken)
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
        vm.shoot(look, adjust, aspect, settings, recipes.firstOrNull { it.slot == settings.recipeSlot && settings.recipeSlot != 0 }); vm.newSeed(); r.seed = vm.seed
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
    // video: the same film pipeline, drawn a second time into the encoder
    var videoMode by rememberSaveable { mutableStateOf(false) }
    var active by remember { mutableStateOf<ActiveRec?>(null) }
    var recSecs by remember { mutableIntStateOf(0) }
    fun beginRecording(withAudio: Boolean) {
        if (active != null) return
        val nv = try { Media.createVideo(ctx) } catch (e: Exception) { vm.message = "無法建立影片檔"; return }
        var rec: FilmRecorder? = null
        var err: Throwable? = null
        // 1080 on the short side; a phone whose encoder refuses that gets 720
        for (short in listOf(1080, 720)) {
            val (w, h) = VideoSize.forAspect(r.cropAspect, short)
            try { rec = FilmRecorder(w, h, nv.pfd.fileDescriptor, withAudio).also { it.start() }; break } catch (e: Throwable) { err = e }
        }
        if (rec == null) {
            runCatching { nv.pfd.close() }; Media.discardVideo(ctx, nv.uri)
            vm.message = "這支手機無法錄影（${err?.javaClass?.simpleName}）"
            return
        }
        r.recorder = rec
        active = ActiveRec(rec, nv.uri, nv.pfd)
        glView.requestRender()
        if (!rec.hasAudio) vm.message = if (withAudio) "麥克風無法使用，這支影片沒有聲音" else "沒有麥克風權限，這支影片沒有聲音"
    }
    fun endRecording() {
        val a = active ?: return
        active = null
        r.stopRecording(a.rec) { ms, err ->
            scope.launch(Dispatchers.IO) {
                runCatching { a.pfd.close() }
                if (ms >= 300 && err == null) {
                    Media.publishVideo(ctx, a.uri)
                    withContext(Dispatchers.Main) { vm.message = "影片已儲存到 Movies/Hikari（${ms / 1000} 秒）" }
                } else {
                    Media.discardVideo(ctx, a.uri)
                    withContext(Dispatchers.Main) { vm.message = "錄影失敗${err?.let { "：" + it.javaClass.simpleName } ?: "（沒有錄到畫面）"}" }
                }
            }
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> beginRecording(granted) }
    fun toggleRecord() {
        if (active != null) endRecording()
        else if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording(true)
        else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    val endNow by rememberUpdatedState(::endRecording)
    DisposableEffect(Unit) { onDispose { endNow() } }   // leaving the screen ends and saves the recording
    LaunchedEffect(active) { recSecs = 0; while (active != null) { delay(1000); recSecs++ } }
    var panel by remember { mutableIntStateOf(-1) } // -1 closed, 0 looks, 2 tone, 3 adjust

    // what the camera sees -> the three looks that suit it (measured scene x measured look traits)
    var recs by remember { mutableStateOf<List<com.hikari.vision.Recommendation>>(emptyList()) }
    var sceneLabel by remember { mutableStateOf("") }
    val stableTop = remember { com.hikari.vision.StableTop() }
    val lookTraits = remember { com.hikari.cam.smart.Smart.traits(app.looks) }
    DisposableEffect(settings.recommend) {
        if (!settings.recommend) { vm.controller.onScene = null; recs = emptyList(); return@DisposableEffect onDispose {} }
        val cb: (com.hikari.vision.Frame) -> Unit = { fr ->
            scope.launch(Dispatchers.Default) {
                val scene = com.hikari.vision.SceneAnalyzer.analyze(fr)
                val top = stableTop.update(com.hikari.vision.FilterRecommender.rank(scene, lookTraits))
                withContext(Dispatchers.Main) { recs = top; sceneLabel = scene.top.take(2).joinToString("・") { it.zh } }
            }
        }
        vm.controller.onScene = cb
        onDispose { if (vm.controller.onScene === cb) vm.controller.onScene = null }
    }
    Column(Modifier.fillMaxSize().background(Darkroom.Ground)) {
        // top bar: icons only, only what this camera supports
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            if (caps?.hasFlash == true) TopIcon(
                when (flash) { Flash.OFF -> Icons.Outlined.FlashOff; Flash.AUTO -> Icons.Outlined.FlashAuto; Flash.ON -> Icons.Outlined.FlashOn },
                "閃光燈", flash != Flash.OFF,
            ) { flash = Flash.entries[(flash.ordinal + 1) % Flash.entries.size]; vm.controller.setFlash(flash) }
            TopIcon(when (timer) { 3 -> Icons.Outlined.Timer3; 10 -> Icons.Outlined.Timer10; else -> Icons.Outlined.TimerOff }, "計時器", timer != 0) {
                timer = listOf(0, 3, 10)[(listOf(0, 3, 10).indexOf(timer) + 1) % 3]
            }
            Box(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Darkroom.Border, RoundedCornerShape(6.dp))
                .clickable {
                    val i = UserSettings.ASPECTS.indexOf(settings.aspect)
                    app.updateSettings { it.copy(aspect = UserSettings.ASPECTS[(i + 1) % UserSettings.ASPECTS.size]) }
                }.padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(settings.aspect, color = Darkroom.Text, fontSize = 12.sp)
            }
            TopIcon(if (settings.grid) Icons.Outlined.GridOn else Icons.Outlined.GridOff, "格線", settings.grid) {
                app.updateSettings { it.copy(grid = !it.grid) }
            }
            TopIcon(Icons.Outlined.Exposure, "光影", ev != 0 || panel == 2) { panel = if (panel == 2) 0 else 2 }
        }

        // preview: as large as the space allows, rounded like a print
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.TopCenter) {
            val fitHeight = maxWidth / maxHeight > aspect
            Box(
                (if (fitHeight) Modifier.fillMaxHeight().aspectRatio(aspect) else Modifier.fillMaxWidth().aspectRatio(aspect))
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(caps, teles) {
                        detectTransformGestures { _, _, z, _ ->
                            val c = caps ?: return@detectTransformGestures
                            applyZoom((zoom * z).coerceIn(c.minZoom, c.maxZoom))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            // a tap on the preview first closes an open panel
                            if (panel >= 0) { panel = -1; return@detectTapGestures }
                            focusAt = p
                            vm.controller.focusAt(p.x / size.width, p.y / size.height)
                        }
                    }
            ) {
                AndroidView({ glView }, Modifier.fillMaxSize())
                cameraError?.let {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$it\n\n仍可在 Gallery 匯入照片套用濾鏡。", color = HikariColors.Dim, fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("改用虛擬相機（內建測試照片）", color = Color.Black, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 14.dp).clip(Darkroom.R8).background(HikariColors.Accent)
                                .clickable { app.updateSettings { it.copy(virtualCamera = true) } }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                if (settings.grid) Canvas(Modifier.fillMaxSize()) {
                    val c = Color(0x55FFFFFF)
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
                if (active != null) Text("● %02d:%02d".format(recSecs / 60, recSecs % 60), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).background(Color(0xCCE5484D), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp).testTag("recTimer"))
                if (countdown > 0) Text("$countdown", color = Color.White, fontSize = 72.sp, modifier = Modifier.align(Alignment.Center))
                if (settings.dateStamp) Text(stampText(java.util.Date(), settings.stampNote), color = Color.White, fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, letterSpacing = 2.sp,
                    modifier = Modifier.align(BiasAlignment(0f, 0.64f)))
                // look name, top-left like a film label
                Text(look.name, color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color(0x55000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (lens == CameraSelector.LENS_FACING_BACK) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    LensPicker(probe.first, lensOpt, activePhysical, probeHidden, onOpen = { probeWanted = true },
                        onSearchHidden = {
                            prefs.edit().putBoolean("probeHidden", true).apply(); probeHidden = true
                            lensMsg = "已搜尋系統未列出的鏡頭"
                        }, teleAuto = autoTele, onTeleAuto = { autoTele = it; prefs.edit().putBoolean("teleAuto", it).apply() }) { autoOpt = false; lensOpt = it }
                }
                lensMsg?.let {
                    Text(it, color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(BiasAlignment(0f, -0.7f)).padding(horizontal = 24.dp)
                            .background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                // capture flash
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = flashAlpha.value }.background(Color.White))
            }
            // zoom stops, and the open panel laid over the bottom of the preview so the preview keeps its size
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (caps != null && caps.maxZoom > caps.minZoom) ZoomStops(caps.minZoom, caps.maxZoom,
                    (caps.lensStops + teles.map { Math.round(it.ratio * 10f) / 10f }).distinct().sorted(), caps.mainFocal35, zoom) { z ->
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    applyZoom(z)
                }
                // the panel is see-through graphite (the picture shows faintly behind it) with a hairline top edge
                if (panel >= 0) Box(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xC71C1C1E), Color(0xD10D0D0D))))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(top = 4.dp)) {
            when (panel) {
                0 -> CameraLookPanel(app, look, favorites, recs, sceneLabel) { id -> app.updateSettings { it.copy(lastLook = id) }; app.touch(id) }
                2 -> TonePanel(
                    evIndex = ev, evMin = caps?.evRange?.lower ?: 0, evMax = caps?.evRange?.upper ?: 0, evStep = caps?.evStep ?: 0f,
                    onEv = { ev = it; vm.controller.setExposure(it) },
                    temp = adjust.temperature, tint = adjust.tint,
                    onTempTint = { t, g -> app.updateSettings { it.copy(cameraAdjustJson = adjust.copy(temperature = t, tint = g).toJson()) } },
                    onCommit = {},
                    // choosing a preset moves the dot to the preset and clears the fine adjustment
                    preset = wb, onPreset = { p ->
                        wb = p; vm.controller.setWhiteBalance(p.mode)
                        app.updateSettings { it.copy(cameraAdjustJson = adjust.copy(temperature = 0f, tint = 0f).toJson()) }
                    },
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
                }
            }
        }

        // bottom panel: 濾鏡 / 光影 / 調整
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            for ((i, label) in listOf(0 to "濾鏡", 2 to "光影", 3 to "調整")) {
                Column(Modifier.clickable { panel = if (panel == i) -1 else i }.padding(horizontal = 18.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (panel == i) Color.White else Color(0xFF8A8A8A), fontSize = 13.sp,
                        fontWeight = if (panel == i) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.padding(top = 3.dp).size(width = 14.dp, height = 2.dp)
                        .background(if (panel == i) HikariColors.Accent else Color.Transparent, RoundedCornerShape(1.dp)))
                }
            }
        }
        // photo / video
        if (active == null) Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
            for ((isVideo, label) in listOf(false to "拍照", true to "錄影")) {
                Text(label, color = if (videoMode == isVideo) (if (isVideo) Color(0xFFE5484D) else Darkroom.Amber) else Color(0xFF8A8A8A), fontSize = 12.sp,
                    fontWeight = if (videoMode == isVideo) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { videoMode = isVideo }.padding(horizontal = 16.dp, vertical = 3.dp).testTag(if (isVideo) "modeVideo" else "modePhoto"))
            }
        }
        // shutter row
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(HikariColors.Panel)
                .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(12.dp))
                .clickable { vm.lastUri?.let { app.go(Route.Editor(it)) } }) {
                vm.lastThumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                if (vm.processing > 0) Text("…", color = HikariColors.Accent, modifier = Modifier.align(Alignment.Center))
            }
            ShutterButton(enabled = videoMode || (vm.processing <= 2 && countdown == 0), busy = !videoMode && vm.processing > 0, video = videoMode, recording = active != null) {
                if (videoMode) toggleRecord() else shoot()
            }
            if (caps != null && caps.hasFront && caps.hasBack) Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Darkroom.CardHi).border(1.dp, Darkroom.Border, RoundedCornerShape(12.dp)).clickable(enabled = active == null) {
                    lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Cameraswitch, contentDescription = "翻轉", tint = Darkroom.Text, modifier = Modifier.size(22.dp)) }
            else Box(Modifier.size(48.dp))
        }
    }
    Toast(vm.message) { vm.message = null }
}

/**
 * Zoom stops as a ruler along the bottom of the viewfinder: the phone's own lenses (e.g. 0.6 / 1 / 2.6, read from the
 * hardware) and 2x / 5x / 10x digital stops, each with a short scale under it. The stop in use is amber, larger and shows
 * the exact zoom; the others are small and grey. Moving to another stop (a tap, or a pinch crossing it) ticks the phone.
 */
@Composable
private fun ZoomStops(min: Float, max: Float, lenses: List<Float>, focal35: Float?, zoom: Float, onZoom: (Float) -> Unit) {
    val native = lenses.ifEmpty { listOf(1f) }
    val stops = (native + listOf(2f, 5f, 10f).filter { d -> d in min..max && native.none { kotlin.math.abs(kotlin.math.ln(it / d)) < 0.2f } })
        .distinct().sorted()
    if (stops.size < 2) return
    val active = stops.minBy { kotlin.math.abs(kotlin.math.ln(it / zoom)) }
    val view = LocalView.current
    var shown by remember { mutableStateOf(active) }
    LaunchedEffect(active) { if (active != shown) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); shown = active } }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        for (z in stops) {
            val on = z == active
            val lens = z in native
            fun fmt(v: Float) = if (v == Math.round(v).toFloat() && v >= 1f) "%.0f".format(v) else "%.1f".format(v)
            Column(Modifier.width(58.dp).clickable { onZoom(z) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text((if (on) fmt(zoom) else fmt(z)) + "×",
                    color = if (on) Darkroom.Amber else Darkroom.Dim, fontSize = if (on) 16.sp else 11.sp,
                    fontWeight = if (on) FontWeight.Normal else FontWeight.Light, maxLines = 1, softWrap = false)
                // native lenses: their 35 mm-equivalent focal length; digital stops: "數位"
                Text(if (lens) (focal35?.let { "${Math.round(it * z)}mm" } ?: "鏡頭") else "數位",
                    color = if (on) Color(0xFFE5C07B) else Color(0xFF636366), fontSize = 9.sp, maxLines = 1, softWrap = false)
                androidx.compose.foundation.Canvas(Modifier.padding(top = 3.dp).fillMaxWidth().height(9.dp)) {
                    // five ticks per stop, the middle one long; the stop in use gets an amber long tick
                    for (i in 0..4) {
                        val x = size.width * (0.1f + 0.2f * i)
                        val long = i == 2
                        drawLine(if (long && on) Darkroom.Amber else if (long) Color(0xFF8E8E93) else Color(0xFF48484A),
                            androidx.compose.ui.geometry.Offset(x, size.height), androidx.compose.ui.geometry.Offset(x, size.height - (if (long) 9 else 4).dp.toPx()),
                            strokeWidth = (if (long && on) 1.6f else 1f).dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
private fun TopIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = if (on) Darkroom.Amber else Color(0xFFD1D1D6), modifier = Modifier.size(20.dp))
    }
}

/** A two-ring mechanical shutter: thin amber outer ring, a gap, a solid disc; the whole button presses in on touch. */
@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, video: Boolean = false, recording: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    LaunchedEffect(pressed) { if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "shutter")
    val disc by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.42f, stiffness = 700f), label = "shutterDisc")
    Box(
        Modifier.size(72.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .border(1.5.dp, if (enabled) Darkroom.Amber else Darkroom.Border, CircleShape)
            .testTag("shutter").semantics { contentDescription = "快門" }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val red = Color(0xFFE5484D)
        if (recording) Box(Modifier.size(30.dp).graphicsLayer { scaleX = disc; scaleY = disc }.clip(RoundedCornerShape(6.dp)).background(red))
        else Box(Modifier.size(58.dp).graphicsLayer { scaleX = disc; scaleY = disc }.clip(CircleShape)
            .background(if (!enabled) Color(0xFF636366) else if (video) (if (pressed) Color(0xFFB23A3E) else red) else if (pressed) Color(0xFFD9D3C7) else Color(0xFFF4EFE6)))
        if (busy) Box(Modifier.size(14.dp).clip(CircleShape).background(Darkroom.Amber))
    }
}

/** One recording in progress: the encoder, the file it writes, and the descriptor to close afterwards. */
private class ActiveRec(val rec: FilmRecorder, val uri: android.net.Uri, val pfd: android.os.ParcelFileDescriptor)

/** Holds the GL renderer created inside the view factory. */
private class CameraPreviewRendererHolder { var r: CameraPreviewRenderer? = null }


/**
 * Lens picker: every back lens the phone exposes by any route. The main lens's focal length is
 * the first listed camera's; hidden lenses are marked experimental. Shows the physical lens that
 * is actually streaming when the phone reports it.
 */
@Composable
private fun LensPicker(
    options: List<com.hikari.cam.camera.LensOption>,
    selected: com.hikari.cam.camera.LensOption?,
    active: String?,
    searched: Boolean,
    onOpen: () -> Unit,
    onSearchHidden: () -> Unit,
    teleAuto: Boolean,
    onTeleAuto: (Boolean) -> Unit,
    onSelect: (com.hikari.cam.camera.LensOption?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val mainFocal = options.firstOrNull()?.focal35
    val title = selected?.label(mainFocal) ?: "自動"
    Column(horizontalAlignment = Alignment.End) {
        Text("◎ $title", color = Color.White, fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x77000000))
                .clickable { onOpen(); open = true }.padding(horizontal = 8.dp, vertical = 4.dp))
        if (active != null) Text("實體 #$active", color = Color(0xFFFFB86B), fontSize = 9.sp,
            modifier = Modifier.padding(top = 2.dp, end = 4.dp))
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            // say plainly whether a telephoto was found, and by which routes
            val tele = options.any { o -> o.focal35 != null && mainFocal != null && o.focal35 > mainFocal * 1.6f }
            Text(
                when {
                    options.isEmpty() -> "正在讀取鏡頭…"
                    tele -> "找到長焦鏡頭，選它就能直接用"
                    searched -> "已試過：系統開放的鏡頭、多鏡頭裡的實體鏡頭、系統未列出的鏡頭。這支手機沒有把長焦開放給第三方 App。"
                    else -> "系統只開放了這些鏡頭。按最下面的「搜尋隱藏鏡頭」再試一次。"
                },
                fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 280.dp))
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (teleAuto) "長焦自動切換：開 ✓" else "長焦自動切換：關") },
                onClick = { onTeleAuto(!teleAuto) })
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("自動（系統預設＋變焦切換）") },
                onClick = { open = false; onSelect(null) })
            for (o in options) androidx.compose.material3.DropdownMenuItem(
                text = {
                    Column {
                        Text(o.label(mainFocal) + if (o.key == selected?.key) "  ✓" else "")
                        Text("${o.source} · #${o.cameraId}${o.physicalId?.let { " / $it" } ?: ""}", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                onClick = { open = false; onSelect(o) })
            if (!searched) androidx.compose.material3.DropdownMenuItem(
                text = { Text("搜尋隱藏鏡頭（實驗）", color = Color(0xFFFFB86B)) },
                onClick = { open = false; onSearchHidden() })
        }
    }
}
