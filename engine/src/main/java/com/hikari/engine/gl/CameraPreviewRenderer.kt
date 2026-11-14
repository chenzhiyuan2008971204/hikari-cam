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
    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var viewW = 1
    private var viewH = 1
    private var frame = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        pipeline = FilmPipeline(assets, oes = true)
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
        val pipe = pipeline ?: return
        try {
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
        val map = SourceMap.camera(rotationDegrees, mirror, bufferWidth, bufferHeight, cropAspect)
        val p = FilmParams.effective(l.spec, l.adjust)
        pipe.prepareGlow(oesTex, map, cropAspect, p)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
        frame = (frame + 1) % 997
        val fr = FilmPipeline.Frame(oesTex, map, cropAspect, viewW, viewH, seed, seed + frame, onScreen = true)
        pipe.composite(fr, p, l.lut, 0, 0, viewW, viewH)
    }
}
