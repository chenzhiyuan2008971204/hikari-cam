package com.hikari.engine.gl

import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.hikari.engine.Adjustments
import com.hikari.engine.CubeLut
import com.hikari.engine.FilmParams
import com.hikari.engine.FilterSpec
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Live camera preview through the film pipeline (same shaders as export, external-texture source).
 * Grain moves from frame to frame like real film; dust, scratches and leaks use [seed], which the
 * capture reuses so the saved photo matches what was on screen.
 */
class CameraPreviewRenderer(
    private val assets: AssetManager,
    private val view: GLSurfaceView,
    private val onSurfaceTexture: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer {

    class Look(val spec: FilterSpec, val lut: CubeLut, val adjust: Adjustments)

    @Volatile var look: Look? = null
    @Volatile var rotationDegrees = 90
    @Volatile var mirror = false
    @Volatile var bufferWidth = 1440
    @Volatile var bufferHeight = 1080
    /** width / height of the frame being composed (the chosen aspect ratio) */
    @Volatile var cropAspect = 3f / 4f
    @Volatile var seed = 1f

    private var pipeline: FilmPipeline? = null

    // Still-frame source (the virtual camera): upright bitmaps uploaded as an ordinary 2D texture and
    // drawn through the same film pipeline (its 2D-source variant, the one export uses).
    private val pendingStill = java.util.concurrent.atomic.AtomicReference<android.graphics.Bitmap?>(null)
    @Volatile private var stillOn = false
    private var pipeline2d: FilmPipeline? = null
    private var stillTex = 0
    private var stillW = 0
    private var stillH = 0

    /** Shows [frame] (upright) instead of the camera texture; the renderer recycles it. */
    fun showStill(frame: android.graphics.Bitmap) {
        stillOn = true
        pendingStill.getAndSet(frame)?.recycle()
        view.requestRender()
    }

    /** Back to the camera texture. */
    fun clearStill() {
        stillOn = false
        pendingStill.getAndSet(null)?.recycle()
        view.requestRender()
    }

    private fun uploadStill(b: android.graphics.Bitmap) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        if (stillTex == 0 || b.width != stillW || b.height != stillH) {
            if (stillTex != 0) GLES30.glDeleteTextures(1, intArrayOf(stillTex), 0)
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            stillTex = ids[0]; stillW = b.width; stillH = b.height
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stillTex)
            val levels = 1 + kotlin.math.floor(kotlin.math.log2(maxOf(b.width, b.height).toDouble())).toInt()
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, levels, GLES30.GL_RGBA8, b.width, b.height)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stillTex)
        android.opengl.GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, b, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }
    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var viewW = 1
    private var viewH = 1
    private var frame = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        pipeline = FilmPipeline(assets, oes = true)
        pipeline2d = null; stillTex = 0   // the old context's objects are gone
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        oesTex = ids[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        RenderTarget.linearClamp(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        surfaceTexture?.release()
        val st = SurfaceTexture(oesTex)
        st.setOnFrameAvailableListener { view.requestRender() }
        surfaceTexture = st
        view.post { onSurfaceTexture(st) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        var pipe = pipeline ?: return
        val still = stillOn
        if (still) {
            pendingStill.getAndSet(null)?.let { b -> if (!b.isRecycled) { uploadStill(b); b.recycle() } }
            pipe = pipeline2d ?: FilmPipeline(assets, oes = false).also { pipeline2d = it }
        } else try {
            st.updateTexImage()
        } catch (e: RuntimeException) {
            return
        }
        val l = look
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (l == null) return
        if (still && stillTex == 0) return
        val src = if (still) stillTex else oesTex
        val map = if (still) {
            // centre crop of the upright picture to the chosen aspect
            val a = stillW.toFloat() / stillH
            if (a > cropAspect) { val w = cropAspect / a; SourceMap.bitmap((1f - w) / 2f, 0f, (1f + w) / 2f, 1f) }
            else { val h = a / cropAspect; SourceMap.bitmap(0f, (1f - h) / 2f, 1f, (1f + h) / 2f) }
        } else SourceMap.camera(rotationDegrees, mirror, bufferWidth, bufferHeight, cropAspect)
        val p = FilmParams.effective(l.spec, l.adjust)
        pipe.prepareGlow(src, map, cropAspect, p)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
        frame = (frame + 1) % 997
        val fr = FilmPipeline.Frame(src, map, cropAspect, viewW, viewH, seed, seed + frame, onScreen = true)
        pipe.composite(fr, p, l.lut, 0, 0, viewW, viewH)
        if (still && frame % 40 == 1) {
            // test source only: one pixel read back, so a run can tell a drawn preview from a black one
            val px = java.nio.ByteBuffer.allocateDirect(4)
            GLES30.glReadPixels(viewW / 2, viewH / 2, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px)
            android.util.Log.i("HikariCamera", "still preview centre rgb ${px.get(0).toInt() and 255},${px.get(1).toInt() and 255},${px.get(2).toInt() and 255} view ${viewW}x$viewH tex ${stillW}x$stillH gl 0x${Integer.toHexString(GLES30.glGetError())}")
        }
    }
}
