package com.hikari.cam.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Range
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.hikari.cam.data.Media
import com.hikari.engine.gl.CameraPreviewRenderer
import com.hikari.engine.gl.SourceMap
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What this camera can do, read from the hardware; the UI only shows what is here. */
data class CameraCaps(
    val hasFlash: Boolean,
    val evRange: Range<Int>,
    val evStep: Float,
    val minZoom: Float,
    val maxZoom: Float,
    val raw: Boolean,
    val hasFront: Boolean,
    val hasBack: Boolean,
    val focus: Boolean,
    /** zoom ratios of this phone's real lenses relative to the main one (e.g. 0.6, 1, 2.6), empty if unknown */
    val lensStops: List<Float> = emptyList(),
    /** 35 mm-equivalent focal length of 1x (the main lens), null if the camera does not say */
    val mainFocal35: Float? = null,
    /** what natural-detail mode could set on this camera, for the settings page */
    val naturalApplied: String = "",
    /** which cameras this phone shows to apps, for the settings page */
    val report: String = "",
)

enum class Flash { OFF, AUTO, ON }

/** One finished capture: the JPEG in the app cache, and the DNG uri when RAW was saved too. */
class Shot(val jpeg: File, val dng: android.net.Uri?)

/**
 * CameraX: live preview into the film renderer's SurfaceTexture, and full-resolution capture.
 * The filtered photo is rendered from the captured JPEG by the off-screen engine, not taken from
 * the preview.
 */
class CameraController(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var renderer: CameraPreviewRenderer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var pending: SurfaceRequest? = null
    private val main = ContextCompat.getMainExecutor(context)
    private var rawMode = false

    var facing = CameraSelector.LENS_FACING_BACK
        private set
    var caps: CameraCaps? = null
        private set

    // the UI is portrait-locked; photos follow how the phone is held
    private val orientation = object : OrientationEventListener(context) {
        override fun onOrientationChanged(deg: Int) {
            if (deg == ORIENTATION_UNKNOWN) return
            capture?.targetRotation = when (deg) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
        }
    }

    suspend fun bind(owner: LifecycleOwner, r: CameraPreviewRenderer, lens: Int, wantRaw: Boolean, natural: Boolean = true): CameraCaps {
        renderer = r
        val p = provider ?: suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val f = ProcessCameraProvider.getInstance(context)
            f.addListener({ runCatching { f.get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) } }, main)
        }.also { provider = it }
        val hasBack = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        val hasFront = p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        facing = when {
            lens == CameraSelector.LENS_FACING_FRONT && hasFront -> lens
            hasBack -> CameraSelector.LENS_FACING_BACK
            else -> CameraSelector.LENS_FACING_FRONT
        }
        r.mirror = facing == CameraSelector.LENS_FACING_FRONT
        p.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(facing).build()

        val pv = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(1440, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build())
            .build()
        pv.setSurfaceProvider(main) { req -> onSurfaceRequest(req) }

        // RAW only when this camera really offers RAW + JPEG together
        val info = p.getCameraInfo(selector)
        val rawOk = ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
        rawMode = wantRaw && rawOk
        val icb = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build())
            // the camera JPEG is only an intermediate (the engine renders the final file from it): keep it at 100
            .setJpegQuality(100)
        if (rawMode) icb.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
        val naturalApplied = if (natural) applyNaturalDetail(icb, info) else ""
        val ic = icb.build()

        val cam = p.bindToLifecycle(owner, selector, pv, ic)
        camera = cam
        preview = pv
        capture = ic
        if (orientation.canDetectOrientation()) orientation.enable()
        val ev = cam.cameraInfo.exposureState
        val zoom = cam.cameraInfo.zoomState.value
        return CameraCaps(
            hasFlash = cam.cameraInfo.hasFlashUnit(),
            evRange = if (ev.isExposureCompensationSupported) ev.exposureCompensationRange else Range(0, 0),
            evStep = ev.exposureCompensationStep.toFloat(),
            minZoom = zoom?.minZoomRatio ?: 1f,
            maxZoom = zoom?.maxZoomRatio ?: 1f,
            raw = rawOk,
            hasFront = hasFront,
            hasBack = hasBack,
            focus = true,
            lensStops = lensStops(cam, zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f),
            mainFocal35 = runCatching { focal35(cam.cameraInfo) }.getOrNull(),
            naturalApplied = naturalApplied,
            report = runCatching { report(p, cam, zoom?.minZoomRatio ?: 1f, zoom?.maxZoomRatio ?: 1f) }.getOrDefault(""),
        ).also { caps = it }
    }

    /**
     * The phone's own lenses as zoom stops: 35 mm-equivalent focal length of every physical camera
     * behind this logical camera, divided by the main camera's. Phones that do not expose their
     * physical cameras give just 1x (plus the ultra-wide when the zoom range goes below 1x).
     */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun lensStops(cam: Camera, minZoom: Float, maxZoom: Float): List<Float> = runCatching {
        fun eq(info: androidx.camera.core.CameraInfo): Float? = focal35(info)
        val main = eq(cam.cameraInfo) ?: return@runCatching listOf(1f)
        val stops = sortedSetOf(1f)
        for (p in cam.cameraInfo.physicalCameraInfos) {
            val e = eq(p) ?: continue
            val r = Math.round(e / main * 10f) / 10f
            if (r >= minZoom * 0.95f && r <= maxZoom && kotlin.math.abs(r - 1f) > 0.15f) stops.add(r.coerceAtLeast(minZoom))
        }
        if (minZoom < 0.95f && stops.none { it < 0.95f }) stops.add(Math.round(minZoom * 10f) / 10f)
        stops.toList()
    }.getOrDefault(listOf(1f))

    /**
     * What the phone exposes to third-party apps: every back camera it lists (with its 35 mm
     * focal length), the physical lenses behind the camera in use, and the zoom range.
     */
    private fun report(p: ProcessCameraProvider, cam: Camera, minZoom: Float, maxZoom: Float): String {
        val backs = p.availableCameraInfos.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        val backFocals = backs.map { i -> focal35(i)?.let { "${Math.round(it)}mm" } ?: "?" }
        val phys = cam.cameraInfo.physicalCameraInfos.map { i -> focal35(i)?.let { "${Math.round(it)}mm" } ?: "?" }
        return buildString {
            append("App 可用的後鏡頭 ${backs.size} 顆：${backFocals.joinToString("、")}")
            append("\n目前相機內含實體鏡頭 ${phys.size} 顆")
            if (phys.isNotEmpty()) append("：${phys.joinToString("、")}")
            append("\n變焦範圍 ${"%.1f".format(minZoom)}× – ${"%.1f".format(maxZoom)}×")
        }
    }

    /** 35 mm-equivalent focal length of a camera: focal length x 43.27 mm / sensor diagonal */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun focal35(info: androidx.camera.core.CameraInfo): Float? {
        val c2 = androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
        val f = c2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
        val s = c2.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        return f * 43.27f / kotlin.math.sqrt(s.width * s.width + s.height * s.height)
    }

    /**
     * Natural detail for the still capture: edge enhancement (sharpening) off, the best noise
     * reduction, hot-pixel and aberration correction - each only when this camera lists the mode.
     * Returns a short description of what was set.
     */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun applyNaturalDetail(b: ImageCapture.Builder, info: androidx.camera.core.CameraInfo): String {
        val c2 = androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
        val ext = androidx.camera.camera2.interop.Camera2Interop.Extender(b)
        val done = mutableListOf<String>()
        fun modes(key: android.hardware.camera2.CameraCharacteristics.Key<IntArray>) = c2.getCameraCharacteristic(key)?.toList().orEmpty()
        if (android.hardware.camera2.CameraMetadata.EDGE_MODE_OFF in modes(android.hardware.camera2.CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)) {
            ext.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.EDGE_MODE, android.hardware.camera2.CameraMetadata.EDGE_MODE_OFF)
            done += "銳化關閉"
        }
        if (android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY in modes(android.hardware.camera2.CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)) {
            ext.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE, android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            done += "高品質降噪"
        }
        if (android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY in modes(android.hardware.camera2.CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)) {
            ext.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.HOT_PIXEL_MODE, android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
        }
        if (android.hardware.camera2.CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY in modes(android.hardware.camera2.CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)) {
            ext.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, android.hardware.camera2.CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
            done += "色差校正"
        }
        return done.joinToString("、")
    }

    fun unbind() {
        orientation.disable()
        provider?.unbindAll()
        camera = null
    }

    /** The GL view (re)created its texture. */
    fun onSurfaceTexture(st: SurfaceTexture) {
        surfaceTexture = st
        val req = pending
        if (req != null) {
            pending = null
            provide(req, st)
        } else {
            preview?.setSurfaceProvider(main) { r -> onSurfaceRequest(r) }
        }
    }

    private fun onSurfaceRequest(req: SurfaceRequest) {
        val st = surfaceTexture
        if (st == null) {
            pending?.willNotProvideSurface()
            pending = req
        } else provide(req, st)
    }

    private fun provide(req: SurfaceRequest, st: SurfaceTexture) {
        val r = renderer ?: return
        st.setDefaultBufferSize(req.resolution.width, req.resolution.height)
        r.bufferWidth = req.resolution.width
        r.bufferHeight = req.resolution.height
        req.setTransformationInfoListener(main) { info -> r.rotationDegrees = info.rotationDegrees }
        val surface = Surface(st)
        req.provideSurface(surface, main) { surface.release() }
    }

    fun setZoom(ratio: Float) {
        val c = caps ?: return
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(c.minZoom, c.maxZoom))
    }

    fun setExposure(index: Int) {
        val c = caps ?: return
        camera?.cameraControl?.setExposureCompensationIndex(index.coerceIn(c.evRange.lower, c.evRange.upper))
    }

    /** Camera white balance: auto, daylight, cloudy or tungsten (android.hardware.camera2 CONTROL_AWB_MODE_*). */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun setWhiteBalance(mode: Int) {
        val cam = camera ?: return
        runCatching {
            androidx.camera.camera2.interop.Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(
                androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE, mode).build())
        }
    }

    fun setFlash(f: Flash) {
        capture?.flashMode = when (f) {
            Flash.OFF -> ImageCapture.FLASH_MODE_OFF
            Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO
            Flash.ON -> ImageCapture.FLASH_MODE_ON
        }
    }

    /**
     * Tap to focus and meter at (u, v) on the shown (cropped, upright) preview, 0..1. The same map
     * the preview renderer uses takes it to sensor buffer coordinates.
     */
    fun focusAt(u: Float, v: Float) {
        val cam = camera ?: return
        val r = renderer ?: return
        val map = SourceMap.camera(r.rotationDegrees, r.mirror, r.bufferWidth, r.bufferHeight, r.cropAspect)
        val (bx, by) = map.apply(u, v)
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(bx.coerceIn(0f, 1f), by.coerceIn(0f, 1f))
        cam.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build())
    }

    /** Takes the full-resolution photo into [jpeg]; with RAW enabled also writes a DNG to the gallery. */
    suspend fun takePicture(jpeg: File): Shot {
        val ic = capture ?: error("相機尚未就緒")
        val jpegOpts = ImageCapture.OutputFileOptions.Builder(jpeg)
            .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = facing == CameraSelector.LENS_FACING_FRONT })
            .build()
        if (!rawMode) {
            suspendCancellableCoroutine<Unit> { cont ->
                ic.takePicture(jpegOpts, main, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) { if (cont.isActive) cont.resume(Unit) }
                    override fun onError(exception: ImageCaptureException) { if (cont.isActive) cont.resumeWithException(exception) }
                })
            }
            return Shot(jpeg, null)
        }
        val rawOpts = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Media.dngValues()).build()
        var dng: android.net.Uri? = null
        var jpegDone = false
        var rawDone = false
        suspendCancellableCoroutine<Unit> { cont ->
            ic.takePicture(rawOpts, jpegOpts, main, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (output.imageFormat == android.graphics.ImageFormat.RAW_SENSOR) {
                        dng = output.savedUri; rawDone = true
                    } else jpegDone = true
                    if (jpegDone && rawDone && cont.isActive) cont.resume(Unit)
                }
                override fun onError(exception: ImageCaptureException) { if (cont.isActive) cont.resumeWithException(exception) }
            })
        }
        return Shot(jpeg, dng)
    }
}
