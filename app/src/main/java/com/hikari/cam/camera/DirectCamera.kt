package com.hikari.cam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.hikari.engine.gl.CameraPreviewRenderer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Plain Camera2 for a camera id CameraX cannot bind (one the system does not list): preview into
 * the film renderer's texture, full-size JPEG capture. Experimental: only some phones let an app
 * open such ids at all.
 */
class DirectCamera(private val context: Context, private val id: String) {
    private val thread = HandlerThread("DirectCamera").also { it.start() }
    private val handler = Handler(thread.looper)
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var sensorOrientation = 90

    @SuppressLint("MissingPermission")
    suspend fun open(st: SurfaceTexture, r: CameraPreviewRenderer) {
        val m = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val c = m.getCameraCharacteristics(id)
        sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: error("此鏡頭沒有輸出設定")
        fun is43(s: Size) = kotlin.math.abs(s.width.toFloat() / s.height - 4f / 3f) < 0.02f
        val previews = map.getOutputSizes(SurfaceTexture::class.java).orEmpty()
        val pv = previews.filter { is43(it) && it.width <= 1920 }.maxByOrNull { it.width * it.height }
            ?: previews.minByOrNull { kotlin.math.abs(it.width - 1440) } ?: error("沒有預覽尺寸")
        val jpegs = map.getOutputSizes(ImageFormat.JPEG).orEmpty()
        val jp = jpegs.filter { is43(it) }.maxByOrNull { it.width * it.height } ?: jpegs.maxByOrNull { it.width * it.height } ?: error("沒有 JPEG 輸出")

        st.setDefaultBufferSize(pv.width, pv.height)
        r.bufferWidth = pv.width
        r.bufferHeight = pv.height
        r.rotationDegrees = sensorOrientation // portrait UI, back camera
        r.mirror = false
        val surface = Surface(st).also { previewSurface = it }
        val rd = ImageReader.newInstance(jp.width, jp.height, ImageFormat.JPEG, 2).also { reader = it }

        val dev = suspendCancellableCoroutine<CameraDevice> { cont ->
            m.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) { if (cont.isActive) cont.resume(d) }
                override fun onDisconnected(d: CameraDevice) { d.close(); if (cont.isActive) cont.resumeWithException(IllegalStateException("鏡頭被系統斷開")) }
                override fun onError(d: CameraDevice, error: Int) { d.close(); if (cont.isActive) cont.resumeWithException(IllegalStateException("系統拒絕開啟這顆鏡頭（錯誤 $error）")) }
            }, handler)
        }
        device = dev
        @Suppress("DEPRECATION")
        val ses = suspendCancellableCoroutine<CameraCaptureSession> { cont ->
            dev.createCaptureSession(listOf(surface, rd.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { if (cont.isActive) cont.resume(s) }
                override fun onConfigureFailed(s: CameraCaptureSession) { if (cont.isActive) cont.resumeWithException(IllegalStateException("這顆鏡頭無法同時預覽與拍照")) }
            }, handler)
        }
        session = ses
        val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }.build()
        ses.setRepeatingRequest(req, null, handler)
    }

    /** Full-size JPEG into [file]; the camera writes the orientation into the EXIF. */
    suspend fun takePicture(file: File) {
        val dev = device ?: error("鏡頭未開啟")
        val ses = session ?: error("鏡頭未開啟")
        val rd = reader ?: error("鏡頭未開啟")
        suspendCancellableCoroutine<Unit> { cont ->
            rd.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buf = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    file.writeBytes(bytes)
                    if (cont.isActive) cont.resume(Unit)
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWithException(e)
                } finally {
                    img.close()
                }
            }, handler)
            val req = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(rd.surface)
                previewSurface?.let { addTarget(it) }
                set(CaptureRequest.JPEG_QUALITY, 100.toByte())
                set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }.build()
            ses.capture(req, null, handler)
        }
    }

    fun close() {
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        session = null; device = null; reader = null
        thread.quitSafely()
    }
}
