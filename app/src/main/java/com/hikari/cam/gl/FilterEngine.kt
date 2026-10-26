package com.hikari.cam.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import com.hikari.cam.filter.CubeLut
import com.hikari.cam.filter.FilterParams
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/** Applies a filter to a full-resolution bitmap on the GPU, off screen, on its own thread. */
object FilterEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "FilterEngine") }
        .asCoroutineDispatcher()

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program: FilterProgram? = null
    private var maxTex = 4096

    suspend fun render(src: Bitmap, params: FilterParams, lut: CubeLut?): Bitmap = withContext(dispatcher) {
        ensureGl()
        val input = fitToGpu(src)
        val w = input.width
        val h = input.height

        val ids = IntArray(3)
        GLES30.glGenTextures(2, ids, 0)
        val srcTex = ids[0]
        val outTex = ids[1]
        GLES30.glGenFramebuffers(1, ids, 2)
        val fbo = ids[2]
        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
            setTexParams()
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, input, 0)

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, outTex)
            setTexParams()
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, outTex, 0)
            GLES30.glViewport(0, 0, w, h)

            // Framebuffer row 0 == bitmap row 0 == image top, so uv and tex coincide.
            val corners = floatArrayOf(
                0f, 0f, 0f, 0f,
                1f, 0f, 1f, 0f,
                0f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f,
            )
            program!!.draw(srcTex, FilterProgram.IDENTITY, corners, params, lut, w, h,
                w.toFloat() / h, (System.nanoTime() % 97).toFloat())

            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
            GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.copyPixelsFromBuffer(buf)
            out
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteTextures(2, intArrayOf(srcTex, outTex), 0)
            if (input !== src) input.recycle()
        }
    }

    private fun setTexParams() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun fitToGpu(src: Bitmap): Bitmap {
        var b = src
        if (b.config != Bitmap.Config.ARGB_8888) b = b.copy(Bitmap.Config.ARGB_8888, false)
        val longest = maxOf(b.width, b.height)
        if (longest > maxTex) {
            val s = maxTex.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
            if (b !== src) b.recycle()
            b = scaled
        }
        return b
    }

    private fun ensureGl() {
        if (context != EGL14.EGL_NO_CONTEXT) return
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(display, ver, 0, ver, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, context)
        val m = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, m, 0)
        maxTex = minOf(m[0], 8192)
        program = FilterProgram(oes = false)
    }
}
