package com.hikari.cam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import com.hikari.cam.smart.Samples
import com.hikari.engine.gl.CameraPreviewRenderer
import java.io.File
import kotlin.math.sin

/**
 * Test camera source: plays the bundled standard photos into the same preview texture the real
 * camera writes to, so the live film preview, subject tracking, composition guidance, filter
 * recommendation and capture all run through their normal code without a camera. The picture
 * drifts slowly, like a hand-held phone, so tracking really has something to follow.
 */
class VirtualCamera(private val context: Context) {
    private val thread = HandlerThread("VirtualCamera").also { it.start() }
    private val handler = Handler(thread.looper)
    private val samples = Samples.list(context)
    private var renderer: CameraPreviewRenderer? = null
    private var source: Bitmap? = null
    private var crop = Rect()
    private var frameNo = 0
    @Volatile private var running = false
    @Volatile var index = 0
        private set

    /** every few frames: the frame as the analysers should see it (upright, preview framing) */
    @Volatile var onFrame: ((Bitmap) -> Unit)? = null

    val current get() = samples[index % samples.size]

    fun start(st: SurfaceTexture, r: CameraPreviewRenderer, sampleIndex: Int) {
        index = ((sampleIndex % samples.size) + samples.size) % samples.size
        // frames go to the renderer as ordinary textures (no camera buffer queue is involved)
        r.mirror = false
        renderer = r
        running = true
        handler.post {
            val b = Samples.bitmap(context, current)
            source = b
            // centre crop to the buffer's 3:4 portrait shape, a little inside so the drift never leaves the photo
            val ar = W.toFloat() / H
            var cw = b.width * 0.94f; var ch = cw / ar
            if (ch > b.height * 0.94f) { ch = b.height * 0.94f; cw = ch * ar }
            crop = Rect(((b.width - cw) / 2).toInt(), ((b.height - ch) / 2).toInt(), ((b.width + cw) / 2).toInt(), ((b.height + ch) / 2).toInt())
            tick()
        }
    }

    private fun framing(): Rect {
        val b = source ?: return crop
        val dx = (sin(frameNo / 23.0) * (b.width - crop.width()) / 2 * 0.8).toInt()
        val dy = (sin(frameNo / 31.0) * (b.height - crop.height()) / 2 * 0.8).toInt()
        return Rect(crop.left + dx, crop.top + dy, crop.right + dx, crop.bottom + dy)
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun tick() {
        if (!running) return
        val r = renderer; val b = source
        if (r != null && b != null) {
            val src = framing()
            val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).drawBitmap(b, src, Rect(0, 0, W, H), paint)
            if (running) r.showStill(out) else out.recycle()
            if (frameNo % 3 == 0) onFrame?.let { cb ->
                val f = Bitmap.createBitmap(W / 2, H / 2, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(f).drawBitmap(b, src, Rect(0, 0, f.width, f.height), paint)
                cb(f)
            }
            frameNo++
        }
        handler.postDelayed({ tick() }, 100)
    }

    /** The current framing as a JPEG at the photo's own resolution, like a camera's still capture. */
    fun takePicture(file: File) {
        val b = source ?: error("虛擬相機尚未就緒")
        val src = framing()
        val shot = Bitmap.createBitmap(b, src.left.coerceAtLeast(0), src.top.coerceAtLeast(0),
            src.width().coerceAtMost(b.width - src.left.coerceAtLeast(0)), src.height().coerceAtMost(b.height - src.top.coerceAtLeast(0)))
        file.outputStream().use { shot.compress(Bitmap.CompressFormat.JPEG, 97, it) }
        shot.recycle()
    }

    fun close() {
        running = false
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        runCatching { thread.join(500) }   // a frame in flight finishes before the renderer goes back to the camera
        renderer?.clearStill()
        renderer = null
    }

    companion object {
        const val W = 768
        const val H = 1024
    }
}
