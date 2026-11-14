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

    suspend fun bind(owner: LifecycleOwner, r: CameraPreviewRenderer, lens: Int, wantRaw: Boolean): CameraCaps {
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
            .setJpegQuality(97)
        if (rawMode) icb.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
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
        ).also { caps = it }
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
