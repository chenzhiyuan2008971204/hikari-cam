package com.hikari.engine.gl

import android.content.res.AssetManager
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Half
import com.hikari.engine.CubeLut
import com.hikari.engine.FilmParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The film pipeline on the GPU, pass for pass the same as tools/engine/pipeline.js (the WebGL
 * harness the filters are tested with):
 *   3 glow chains (prefilter -> blur H -> blur V), sized from their radius -> composite.
 * Must be created and used on one thread with a current GLES 3.0 context.
 *
 * Texture units: 0 source, 1 bloom, 2 halation, 3 diffusion, 4 LUT, 7 scratch (creation only).
 */
class FilmPipeline(assets: AssetManager, private val oes: Boolean) {
    private val pre = GlProgram(assets, "prefilter.frag", oes)
    private val blur = GlProgram(assets, "blur.frag", false)
    private val comp = GlProgram(assets, "composite.frag", oes)
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)); position(0) }
    private val chains = arrayOfNulls<Pair<RenderTarget, RenderTarget>>(3)
    private val black: RenderTarget = RenderTarget(1, 1).also {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, it.fbo)
        GLES30.glViewport(0, 0, 1, 1)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }
    private var lutTex = 0
    private var uploadedLut: CubeLut? = null

    private val srcTarget get() = if (oes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D

    /**
     * Builds the three glow textures for the current source. Call once per frame / image before
     * one or more [composite] calls. [aspect] = width / height of the (cropped) image.
     */
    fun prepareGlow(src: Int, map: SourceMap, aspect: Float, p: FilmParams) {
        val passes = listOf(
            Triple(0, p.bloomRadius, p.bloomThreshold) to p.needsBloom,
            Triple(1, p.halationRadius, p.halationThreshold) to p.needsHalation,
            Triple(2, p.diffusionRadius, 0f) to p.needsDiffusion,
        )
        for ((idx, entry) in passes.withIndex()) {
            val (spec, needed) = entry
            val (mode, radius, threshold) = spec
            if (!needed) continue
            val size = FilmParams.chainSize(radius, aspect)
            val cur = chains[idx]
            val (a, b) = if (cur != null && cur.first.w == size.w && cur.first.h == size.h) cur else {
                cur?.first?.release(); cur?.second?.release()
                (RenderTarget(size.w, size.h) to RenderTarget(size.w, size.h)).also { chains[idx] = it }
            }
            pre.use()
            bindSource(pre, src, map)
            pre.i("uMode", mode)
            pre.f("uThreshold", threshold)
            pre.v2("uTexel", 1f / size.w, 1f / size.h)
            drawInto(pre, a, 0f, 0f, 1f, 1f)
            blur.use()
            blur.i("uGlow", 1)
            blur.f("uSigma", size.sigma)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, a.tex)
            blur.v2("uStep", 1f / size.w, 0f)
            drawInto(blur, b, 0f, 0f, 1f, 1f)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, b.tex)
            blur.v2("uStep", 0f, 1f / size.h)
            drawInto(blur, a, 0f, 0f, 1f, 1f)
        }
        // bind results (or black) to units 1..3
        for (idx in 0..2) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1 + idx)
            val needed = passes[idx].second
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (needed) chains[idx]!!.first.tex else black.tex)
        }
    }

    /** Settings for one [composite] call. */
    class Frame(
        val src: Int,
        val map: SourceMap,
        val aspect: Float,
        val outW: Int,
        val outH: Int,
        val seed: Float,
        val grainSeed: Float,
        val onScreen: Boolean,
    )

    /**
     * Draws the tile [tx, ty, tw, th] (in output pixels) of the finished image into the bound
     * framebuffer, filling the current viewport. On screen the image is upright; off screen
     * (onScreen = false) row 0 of the framebuffer is the tile's top row and pixels are BGRA, so a
     * little-endian int read gives Android ARGB.
     */
    fun composite(fr: Frame, p: FilmParams, lut: CubeLut, tx: Int, ty: Int, tw: Int, th: Int) {
        comp.use()
        bindSource(comp, fr.src, fr.map)
        comp.i("uBloomTex", 1); comp.i("uHaloTex", 2); comp.i("uDiffTex", 3); comp.i("uLut", 4)
        bindLut(lut)
        val n = lut.size.toFloat()
        val sc = FloatArray(3) { (n - 1f) / n / (lut.domainMax[it] - lut.domainMin[it]) }
        val of = FloatArray(3) { 0.5f / n - lut.domainMin[it] * sc[it] }
        comp.v3("uLutScale", sc)
        comp.v3("uLutOffset", of)
        comp.f("uAspect", fr.aspect)
        comp.f("uOutShortPx", minOf(fr.outW, fr.outH).toFloat())
        comp.v2("uOutSizePx", fr.outW.toFloat(), fr.outH.toFloat())
        comp.f("uSeed", fr.seed)
        comp.f("uGrainSeed", fr.grainSeed)
        comp.f("uOutputBGRA", if (fr.onScreen) 0f else 1f)
        for ((k, v) in p.floats) comp.f(k, v)
        for ((k, v) in p.vec3s) comp.v3(k, v)
        comp.v4("uTile", tx.toFloat() / fr.outW, ty.toFloat() / fr.outH, tw.toFloat() / fr.outW, th.toFloat() / fr.outH)
        comp.f("uFlipY", if (fr.onScreen) 1f else 0f)
        drawQuad()
    }

    private fun bindSource(prog: GlProgram, src: Int, map: SourceMap) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(srcTarget, src)
        prog.i("uSrc", 0)
        prog.v2("uSrcO", map.ox, map.oy)
        prog.v2("uSrcX", map.xx, map.xy)
        prog.v2("uSrcY", map.yx, map.yy)
    }

    private fun drawInto(prog: GlProgram, t: RenderTarget, x: Float, y: Float, w: Float, h: Float) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.fbo)
        GLES30.glViewport(0, 0, t.w, t.h)
        prog.v4("uTile", x, y, w, h)
        prog.f("uFlipY", 0f)
        drawQuad()
    }

    private fun drawQuad() {
        quad.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun bindLut(lut: CubeLut) {
        if (lutTex == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            lutTex = ids[0]
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        if (lut !== uploadedLut) {
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            while (GLES30.glGetError() != GLES30.GL_NO_ERROR) { /* clear */ }
            // RGBA16F + HALF_FLOAT is the combination every GLES 3.0 driver must accept (RGB16F with
            // FLOAT data is legal but the Android emulator's GL translator drops it: the LUT became
            // an incomplete texture and every look rendered black).
            val n = lut.size * lut.size * lut.size
            val half = ByteBuffer.allocateDirect(n * 4 * 2).order(ByteOrder.nativeOrder())
            val hs = half.asShortBuffer()
            for (i in 0 until n) {
                hs.put(Half.toHalf(lut.data[i * 3]))
                hs.put(Half.toHalf(lut.data[i * 3 + 1]))
                hs.put(Half.toHalf(lut.data[i * 3 + 2]))
                hs.put(Half.toHalf(1f))
            }
            half.position(0)
            GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA16F, lut.size, lut.size, lut.size, 0,
                GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, half)
            if (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
                // last resort: 8-bit LUT (slightly coarser, but never black)
                val b = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
                for (i in 0 until n) {
                    for (c in 0..2) b.put((lut.data[i * 3 + c].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
                    b.put(255.toByte())
                }
                b.position(0)
                GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA8, lut.size, lut.size, lut.size, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, b)
                val err = GLES30.glGetError()
                check(err == GLES30.GL_NO_ERROR) { "3D LUT upload failed (GL error 0x${Integer.toHexString(err)})" }
            }
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
            uploadedLut = lut
        }
    }

    fun release() {
        pre.release(); blur.release(); comp.release()
        chains.forEach { it?.first?.release(); it?.second?.release() }
        black.release()
        if (lutTex != 0) GLES30.glDeleteTextures(1, intArrayOf(lutTex), 0)
    }
}
