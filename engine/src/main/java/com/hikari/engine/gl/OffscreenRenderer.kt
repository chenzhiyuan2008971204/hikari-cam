package com.hikari.engine.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import com.hikari.engine.Adjustments
import com.hikari.engine.CubeLut
import com.hikari.engine.FilmParams
import com.hikari.engine.FilterSpec
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Runs the film pipeline off screen on its own GL thread: editor previews, filter thumbnails and
 * full-resolution export all go through [render], so a preview is the export at a smaller size.
 *
 * Memory: the source is uploaded to the GPU and can be released before the output is allocated
 * (see [render]'s [releaseSource]), and the output is read back in tiles straight into the result
 * bitmap, so a large photo never needs more than about one full-size bitmap on the Java heap.
 */
class OffscreenRenderer(private val assets: AssetManager) {
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "FilmRenderer") }.asCoroutineDispatcher()
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pipeline: FilmPipeline? = null
    private var tile: RenderTarget? = null

    /** Largest texture side the GPU accepts (known after the first call). */
    var maxTextureSize = 4096
        private set

    /** GL_RENDERER / GL_VERSION of the context (known after the first call). */
    var gpuName = ""
        private set

    /** Where the time of the last [render] went, in milliseconds. */
    data class Timing(val uploadMs: Double, val glowMs: Double, val compositeMs: Double, val totalMs: Double)

    @Volatile var lastTiming = Timing(0.0, 0.0, 0.0, 0.0)
        private set

    /** Source textures kept on the GPU for interactive re-rendering, keyed by the caller. */
    private val cache = LinkedHashMap<Any, Int>()
    private val cacheSlots = 3

    class Request(
        val spec: FilterSpec,
        val lut: CubeLut,
        val adjust: Adjustments,
        /** crop in source fractions: left, top, right, bottom */
        val crop: FloatArray = floatArrayOf(0f, 0f, 1f, 1f),
        val mirror: Boolean = false,
        val seed: Float = 1f,
    )

    /**
     * Renders [source] through the look at [outW] x [outH] (the cropped area's aspect). With
     * [releaseSource] the source bitmap is recycled right after it is on the GPU.
     */
    suspend fun render(
        source: Bitmap, req: Request, outW: Int, outH: Int,
        releaseSource: Boolean = false,
        /** non-null: keep the uploaded source on the GPU under this key and reuse it next time */
        cacheKey: Any? = null,
    ): Bitmap =
        withContext(thread) {
            ensureGl()
            val pipe = pipeline!!
            val t0 = System.nanoTime()
            val cached = cacheKey?.let { cache[it] }
            val srcTex = cached ?: upload(source)
            if (cacheKey != null && cached == null) {
                cache[cacheKey] = srcTex
                while (cache.size > cacheSlots) {
                    val first = cache.keys.first()
                    GLES30.glDeleteTextures(1, intArrayOf(cache.remove(first)!!), 0)
                }
            }
            if (releaseSource) source.recycle()
            try {
                val p = FilmParams.effective(req.spec, req.adjust)
                val map = SourceMap.bitmap(req.crop[0], req.crop[1], req.crop[2], req.crop[3], req.mirror)
                val aspect = outW.toFloat() / outH
                GLES30.glFinish()
                val t1 = System.nanoTime()
                pipe.prepareGlow(srcTex, map, aspect, p)
                GLES30.glFinish()
                val t2 = System.nanoTime()
                val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val t = tile!!
                val pixels = IntArray(t.w * t.h)
                val buf = ByteBuffer.allocateDirect(t.w * t.h * 4).order(ByteOrder.LITTLE_ENDIAN)
                val frame = FilmPipeline.Frame(srcTex, map, aspect, outW, outH, req.seed, req.seed, onScreen = false)
                GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 4)
                var y0 = 0
                while (y0 < outH) {
                    val th = minOf(t.h, outH - y0)
                    var x0 = 0
                    while (x0 < outW) {
                        val tw = minOf(t.w, outW - x0)
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.fbo)
                        GLES30.glViewport(0, 0, tw, th)
                        pipe.composite(frame, p, req.lut, x0, y0, tw, th)
                        buf.clear()
                        GLES30.glReadPixels(0, 0, tw, th, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
                        buf.position(0)
                        buf.asIntBuffer().get(pixels, 0, tw * th)
                        out.setPixels(pixels, 0, tw, x0, y0, tw, th)
                        x0 += tw
                    }
                    y0 += th
                }
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                val t3 = System.nanoTime()
                lastTiming = Timing((t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t3 - t0) / 1e6)
                out
            } finally {
                if (cacheKey == null) GLES30.glDeleteTextures(1, intArrayOf(srcTex), 0)
            }
        }

    /** Drops a cached source texture (e.g. when a new photo is imported). */
    suspend fun forget(cacheKey: Any) = withContext(thread) {
        cache.remove(cacheKey)?.let { GLES30.glDeleteTextures(1, intArrayOf(it), 0) }
        Unit
    }

    /** The GPU's max texture side, creating the context if needed. */
    suspend fun maxTexture(): Int = withContext(thread) { ensureGl(); maxTextureSize }

    private fun upload(b: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, b, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun ensureGl() {
        if (context != EGL14.EGL_NO_CONTEXT) return
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        check(EGL14.eglInitialize(display, ver, 0, ver, 1)) { "EGL init failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) { "no GLES3 config" }
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
        val m = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, m, 0)
        maxTextureSize = m[0]
        gpuName = "${GLES30.glGetString(GLES30.GL_RENDERER)} · ${GLES30.glGetString(GLES30.GL_VERSION)}"
        pipeline = FilmPipeline(assets, oes = false)
        tile = RenderTarget(1024, 1024)
    }
}
