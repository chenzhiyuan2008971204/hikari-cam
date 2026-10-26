package com.hikari.cam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.hikari.cam.gl.CameraRenderer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the manual panel can control, read from the camera hardware. */
data class ManualCaps(
    val isoRange: Range<Int>,
    val exposureRangeNs: Range<Long>,
    val minFocusDiopters: Float,
    val evRange: Range<Int>,
    val evStep: Float,
    val minZoom: Float,
    val maxZoom: Float,
)

/** Manual settings. null = automatic. */
data class ManualSettings(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDiopters: Float? = null,
    val awbMode: Int = CameraMetadata.CONTROL_AWB_MODE_AUTO,
    val evIndex: Int = 0,
)

enum class Flash { OFF, AUTO, ON }

@OptIn(ExperimentalCamera2Interop::class)
class HikariCamera(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var renderer: CameraRenderer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var pendingRequest: SurfaceRequest? = null
    private val main = ContextCompat.getMainExecutor(context)

    var lensFacing = CameraSelector.LENS_FACING_BACK
        private set

    var caps: ManualCaps? = null
        private set

    suspend fun bind(owner: LifecycleOwner, renderer: CameraRenderer, facing: Int = lensFacing): ManualCaps? {
        this.renderer = renderer
        lensFacing = facing
        renderer.mirror = facing == CameraSelector.LENS_FACING_FRONT
        val p = provider ?: awaitProvider().also { provider = it }
        p.unbindAll()

        val aspect = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        val pv = Preview.Builder()
            .setResolutionSelector(
                aspect.setResolutionStrategy(
                    ResolutionStrategy(Size(1440, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                ).build()
            )
            .build()
        // ~12MP keeps full detail while staying fast to filter on the GPU.
        val ic = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(4096, 3072), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    ).build()
            )
            .setJpegQuality(100)
            .build()

        pv.setSurfaceProvider(main) { req -> onSurfaceRequest(req) }
        val selector = CameraSelector.Builder().requireLensFacing(facing).build()
        val cam = p.bindToLifecycle(owner, selector, pv, ic)
        camera = cam
        preview = pv
        imageCapture = ic
        caps = readCaps(cam)
        return caps
    }

    /** Called by the GL view whenever it (re)creates its texture. */
    fun onSurfaceTexture(st: SurfaceTexture) {
        surfaceTexture = st
        val req = pendingRequest
        if (req != null) {
            pendingRequest = null
            provide(req, st)
        } else {
            // Re-attach the camera to the new texture.
            preview?.setSurfaceProvider(main) { r -> onSurfaceRequest(r) }
        }
    }

    private fun onSurfaceRequest(req: SurfaceRequest) {
        val st = surfaceTexture
        if (st == null) {
            pendingRequest?.willNotProvideSurface()
            pendingRequest = req
        } else {
            provide(req, st)
        }
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

    private fun readCaps(cam: Camera): ManualCaps {
        val info = Camera2CameraInfo.from(cam.cameraInfo)
        val iso = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 3200)
        val exp = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            ?: Range(100_000L, 250_000_000L)
        val focus = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val ev = cam.cameraInfo.exposureState
        val zoom = cam.cameraInfo.zoomState.value
        return ManualCaps(
            isoRange = iso,
            // Cap at 1s so preview stays usable.
            exposureRangeNs = Range(exp.lower, minOf(exp.upper, 1_000_000_000L)),
            minFocusDiopters = focus,
            evRange = ev.exposureCompensationRange,
            evStep = ev.exposureCompensationStep.toFloat(),
            minZoom = zoom?.minZoomRatio ?: 1f,
            maxZoom = zoom?.maxZoomRatio ?: 1f,
        )
    }

    fun setZoom(ratio: Float) {
        val c = caps ?: return
        camera?.cameraControl?.setZoomRatio(ratio.coerceIn(c.minZoom, c.maxZoom))
    }

    fun setFlash(f: Flash) {
        imageCapture?.flashMode = when (f) {
            Flash.OFF -> ImageCapture.FLASH_MODE_OFF
            Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO
            Flash.ON -> ImageCapture.FLASH_MODE_ON
        }
    }

    fun applyManual(s: ManualSettings) {
        val cam = camera ?: return
        val b = CaptureRequestOptions.Builder()
        val manualExposure = s.iso != null || s.exposureNs != null
        if (manualExposure) {
            val c = caps
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, s.iso ?: c?.isoRange?.lower ?: 100)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, s.exposureNs ?: 10_000_000L)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, s.exposureNs ?: 33_333_333L)
        }
        if (s.focusDiopters != null) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, s.focusDiopters)
        }
        b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, s.awbMode)
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
        if (!manualExposure) cam.cameraControl.setExposureCompensationIndex(s.evIndex)
    }

    /** Tap to focus and meter. x, y in view pixels. */
    fun focusAt(x: Float, y: Float, viewW: Int, viewH: Int) {
        val cam = camera ?: return
        @Suppress("DEPRECATION")
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        val factory = DisplayOrientedMeteringPointFactory(display, cam.cameraInfo, viewW.toFloat(), viewH.toFloat())
        val action = FocusMeteringAction.Builder(factory.createPoint(x, y))
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /** Takes a photo and returns it upright (rotation and selfie mirroring applied). */
    suspend fun capture(): Bitmap {
        val ic = imageCapture ?: error("Camera not ready")
        val proxy = suspendCancellableCoroutine<ImageProxy> { cont ->
            ic.takePicture(main, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = cont.resume(image)
                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            })
        }
        return proxy.use { toUprightBitmap(it) }
    }

    private fun toUprightBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val rot = image.imageInfo.rotationDegrees
        val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
        if (rot == 0 && !mirror) return raw
        val m = Matrix()
        m.postRotate(rot.toFloat())
        if (mirror) m.postScale(-1f, 1f)
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out !== raw) raw.recycle()
        return out
    }

    fun unbind() {
        provider?.unbindAll()
        camera = null
    }

    @SuppressLint("RestrictedApi")
    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({
            try {
                cont.resume(f.get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, main)
    }
}
