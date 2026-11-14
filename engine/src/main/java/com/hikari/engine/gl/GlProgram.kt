package com.hikari.engine.gl

import android.content.res.AssetManager
import android.opengl.GLES30

/** A compiled pass. Shaders come from assets/shaders, assembled exactly like the test harness. */
class GlProgram(assets: AssetManager, fragment: String, oes: Boolean) {
    val id: Int
    private val locations = HashMap<String, Int>()

    init {
        val read = { name: String -> assets.open("shaders/$name").use { it.readBytes().decodeToString() } }
        val header = if (oes) {
            "#version 300 es\n#extension GL_OES_EGL_image_external_essl3 : require\n#define SOURCE_OES\n"
        } else {
            "#version 300 es\n"
        }
        val vs = compile(GLES30.GL_VERTEX_SHADER, "#version 300 es\n" + read("quad.vert"))
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, header + read("common.glsl") + "\n" + read(fragment))
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vs)
        GLES30.glAttachShader(id, fs)
        GLES30.glBindAttribLocation(id, 0, "aPos")
        GLES30.glLinkProgram(id)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw IllegalStateException("link $fragment: " + GLES30.glGetProgramInfoLog(id))
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
    }

    fun use() = GLES30.glUseProgram(id)
    fun loc(name: String) = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    fun f(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)
    fun i(name: String, v: Int) = GLES30.glUniform1i(loc(name), v)
    fun v2(name: String, x: Float, y: Float) = GLES30.glUniform2f(loc(name), x, y)
    fun v3(name: String, v: FloatArray) = GLES30.glUniform3f(loc(name), v[0], v[1], v[2])
    fun v4(name: String, a: Float, b: Float, c: Float, d: Float) = GLES30.glUniform4f(loc(name), a, b, c, d)

    fun release() = GLES30.glDeleteProgram(id)

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            GLES30.glDeleteShader(s)
            throw IllegalStateException("shader compile failed: $log")
        }
        return s
    }
}

/** An RGBA8 texture with a framebuffer. Created on texture unit 7 so no sampled unit is disturbed. */
class RenderTarget(val w: Int, val h: Int) {
    val tex: Int
    val fbo: Int

    init {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        tex = ids[0]
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        linearClamp(GLES30.GL_TEXTURE_2D)
        GLES30.glGenFramebuffers(1, ids, 0)
        fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "framebuffer incomplete: $status" }
    }

    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
    }

    companion object {
        fun linearClamp(target: Int) {
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
    }
}
