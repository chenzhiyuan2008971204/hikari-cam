package com.hikari.cam.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.hikari.cam.filter.CubeLut
import com.hikari.cam.filter.FilterParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders the live camera feed through the filter shader.
 * The camera writes into [surfaceTexture]; each new frame triggers a redraw.
 */
class CameraRenderer(
    private val view: GLSurfaceView,
    private val onSurfaceTexture: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var params: FilterParams = FilterParams.NEUTRAL
    @Volatile var lut: CubeLut? = null
    @Volatile var rotationDegrees: Int = 90
    @Volatile var mirror: Boolean = false
    @Volatile var bufferWidth: Int = 1440
    @Volatile var bufferHeight: Int = 1080

    private var program: FilterProgram? = null
    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val texMatrix = FloatArray(16)
    private var viewW = 1
    private var viewH = 1
    private var frame = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = FilterProgram(oes = true)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        oesTex = ids[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        surfaceTexture?.release()
        val st = SurfaceTexture(oesTex)
        st.setOnFrameAvailableListener { view.requestRender() }
        surfaceTexture = st
        view.post { onSurfaceTexture(st) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        val prog = program ?: return
        try {
            st.updateTexImage()
        } catch (e: RuntimeException) {
            return
        }
        st.getTransformMatrix(texMatrix)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val rot = ((rotationDegrees % 360) + 360) % 360
        val bw = bufferWidth.toFloat()
        val bh = bufferHeight.toFloat()
        val imgAspect = if (rot % 180 == 0) bw / bh else bh / bw // upright width / height
        val viewAspect = viewW.toFloat() / viewH
        corners = previewCorners(rot, mirror, imgAspect, viewAspect)
        frame++
        prog.draw(oesTex, texMatrix, corners, params, lut, viewW, viewH, bw / bh, (frame % 97).toFloat())
    }

    private var corners = FloatArray(16)

    companion object {
        /**
         * Builds the quad for the preview: center-crops the upright image to the view and maps each
         * screen corner back into the sensor-oriented camera buffer.
         */
        fun previewCorners(rot: Int, mirror: Boolean, imgAspect: Float, viewAspect: Float): FloatArray {
            // Visible part of the upright image (u right, v up), center crop.
            var su = 1f
            var sv = 1f
            if (imgAspect > viewAspect) su = viewAspect / imgAspect else sv = imgAspect / viewAspect
            // Screen corners BL, BR, TL, TR in upright coords (y up).
            val screen = arrayOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)
            val out = FloatArray(16)
            for ((i, c) in screen.withIndex()) {
                var u = 0.5f + (c.first - 0.5f) * su
                val v = 0.5f + (c.second - 0.5f) * sv
                if (mirror) u = 1f - u
                val (bx, by) = when (rot) {
                    90 -> (1f - v) to u
                    180 -> (1f - u) to (1f - v)
                    270 -> v to (1f - u)
                    else -> u to v
                }
                out[i * 4] = c.first          // image uv x
                out[i * 4 + 1] = 1f - c.second // image uv y (down)
                out[i * 4 + 2] = bx
                out[i * 4 + 3] = by
            }
            return out
        }
    }
}
