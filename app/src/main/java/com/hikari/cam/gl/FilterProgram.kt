package com.hikari.cam.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import com.hikari.cam.filter.CubeLut
import com.hikari.cam.filter.FilterParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val TAG = "FilterProgram"

private const val VERTEX = """#version 300 es
in vec2 aPos;
in vec2 aUv;
in vec2 aTex;
uniform mat4 uTexMatrix;
out vec2 vUv;
out vec2 vTex;
void main() {
    vUv = aUv;
    vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

private const val FRAGMENT_BODY = """
precision highp float;
precision highp sampler3D;
uniform sampler3D uLut;
in vec2 vUv;   // image coordinates, (0,0) = top-left
in vec2 vTex;  // source sample coordinates
out vec4 fragColor;

uniform float uExposure, uContrast, uSaturation, uVibrance, uTemp, uTint;
uniform float uHighlights, uShadows, uFade, uMono, uGrain, uVignette;
uniform float uHalation, uBloom, uLeak, uLutAmount, uSeed, uGrainScale;
uniform vec3 uLift, uGamma, uGain, uShadowTint, uHighlightTint, uLeakColor;
uniform vec3 uLutScale, uLutOffset;
uniform vec2 uRes, uGlowR;

const vec3 W = vec3(0.2126, 0.7152, 0.0722);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i + uSeed);
    float b = hash12(i + vec2(1.0, 0.0) + uSeed);
    float c = hash12(i + vec2(0.0, 1.0) + uSeed);
    float d = hash12(i + vec2(1.0, 1.0) + uSeed);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec3 c = texture(uTex, vTex).rgb;

    // Glow from bright neighbours: red halation (CineStill) and soft white bloom.
    if (uHalation > 0.001 || uBloom > 0.001) {
        vec3 s = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398 + 0.39;
            vec2 d = vec2(cos(a), sin(a)) * uGlowR;
            s += smoothstep(vec3(0.6), vec3(1.0), texture(uTex, vTex + d).rgb);
            s += 0.6 * smoothstep(vec3(0.6), vec3(1.0), texture(uTex, vTex + d * 2.2).rgb);
        }
        s /= 12.8;
        c += uHalation * dot(s, W) * vec3(1.0, 0.28, 0.12);
        c += uBloom * s * 0.6;
    }

    c *= exp2(uExposure);
    c *= vec3(1.0 + uTemp * 0.12, 1.0 - uTint * 0.08, 1.0 - uTemp * 0.12);

    float L = dot(c, W);
    c += uShadows * 0.25 * (1.0 - smoothstep(0.0, 0.55, L));
    c += uHighlights * 0.25 * smoothstep(0.45, 1.0, L);

    c = (c - 0.5) * (1.0 + uContrast * 0.8) + 0.5;

    L = dot(c, W);
    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(L), c, max(0.0, 1.0 + uSaturation + uVibrance * (1.0 - clamp(sat, 0.0, 1.0))));

    c = clamp(c, 0.0, 1.0);
    c = uGain * (c + uLift * (1.0 - c));
    c = pow(max(c, 0.0), 1.0 / max(uGamma, vec3(0.05)));

    L = dot(c, W);
    c += uShadowTint * (1.0 - smoothstep(0.0, 0.6, L)) * 1.5;
    c += uHighlightTint * smoothstep(0.4, 1.0, L) * 1.5;

    c = uFade * 0.18 + c * (1.0 - uFade * 0.25);
    c = mix(c, vec3(dot(c, W)), clamp(uMono, 0.0, 1.0));

    if (uLutAmount > 0.001) {
        vec3 lc = texture(uLut, clamp(c, 0.0, 1.0) * uLutScale + uLutOffset).rgb;
        c = mix(c, lc, uLutAmount);
    }

    if (uLeak > 0.001) {
        vec2 p1 = (vUv - vec2(1.05, -0.05)) * vec2(1.0, 1.4);
        vec2 p2 = (vUv - vec2(-0.08, 0.75)) * vec2(1.6, 1.0);
        float m = exp(-dot(p1, p1) * 4.0) + 0.6 * exp(-dot(p2, p2) * 9.0);
        c = 1.0 - (1.0 - clamp(c, 0.0, 1.0)) * (1.0 - clamp(uLeak * m, 0.0, 1.0) * uLeakColor);
    }

    if (uVignette > 0.001) {
        float ar = uRes.y / uRes.x;
        float d = length((vUv - 0.5) * vec2(1.0, ar)) / length(vec2(0.5, 0.5 * ar));
        c *= 1.0 - uVignette * 0.85 * smoothstep(0.35, 1.0, d);
    }

    if (uGrain > 0.001) {
        vec2 gp = vUv * uRes / uGrainScale;
        float g = (vnoise(gp) + 0.5 * vnoise(gp * 2.13 + 17.0)) / 1.5 - 0.5;
        L = dot(c, W);
        c += g * uGrain * 0.25 * (1.0 - 0.6 * abs(L * 2.0 - 1.0));
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

private const val FRAGMENT_OES = "#version 300 es\n#extension GL_OES_EGL_image_external_essl3 : require\n" +
    "uniform samplerExternalOES uTex;\n" + FRAGMENT_BODY

private const val FRAGMENT_2D = "#version 300 es\nuniform sampler2D uTex;\n" + FRAGMENT_BODY

/**
 * Compiled filter shader. Must be created and used on a thread with a current GL ES 3 context.
 * [oes] selects the camera (external texture) or bitmap (2D texture) source.
 */
class FilterProgram(private val oes: Boolean) {
    private val program: Int = link(VERTEX, if (oes) FRAGMENT_OES else FRAGMENT_2D)
    private val uniforms = HashMap<String, Int>()
    private val aPos = GLES30.glGetAttribLocation(program, "aPos")
    private val aUv = GLES30.glGetAttribLocation(program, "aUv")
    private val aTex = GLES30.glGetAttribLocation(program, "aTex")
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private var lutTex = 0
    private var uploadedLut: CubeLut? = null

    private fun loc(name: String) = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }
    private fun u1(n: String, v: Float) = GLES30.glUniform1f(loc(n), v)
    private fun u2(n: String, a: Float, b: Float) = GLES30.glUniform2f(loc(n), a, b)
    private fun u3(n: String, a: Float, b: Float, c: Float) = GLES30.glUniform3f(loc(n), a, b, c)
    private fun u3(n: String, v: com.hikari.cam.filter.Rgb) = u3(n, v.r, v.g, v.b)

    /**
     * Draw a full-viewport quad.
     * [corners] holds, for the corners BL, BR, TL, TR, four floats each: uv.x, uv.y (image coords,
     * y down) and tex.x, tex.y (source coords before [texMatrix]).
     */
    fun draw(
        texId: Int,
        texMatrix: FloatArray,
        corners: FloatArray,
        params: FilterParams,
        lut: CubeLut?,
        outW: Int,
        outH: Int,
        srcAspect: Float,
        seed: Float,
    ) {
        GLES30.glUseProgram(program)

        quad.clear()
        val pos = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        for (i in 0 until 4) {
            quad.put(pos[i * 2]); quad.put(pos[i * 2 + 1])
            quad.put(corners[i * 4]); quad.put(corners[i * 4 + 1])
            quad.put(corners[i * 4 + 2]); quad.put(corners[i * 4 + 3])
        }
        val stride = 6 * 4
        quad.position(0)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, stride, quad)
        GLES30.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES30.glVertexAttribPointer(aUv, 2, GLES30.GL_FLOAT, false, stride, quad)
        GLES30.glEnableVertexAttribArray(aUv)
        quad.position(4)
        GLES30.glVertexAttribPointer(aTex, 2, GLES30.GL_FLOAT, false, stride, quad)
        GLES30.glEnableVertexAttribArray(aTex)

        GLES30.glUniformMatrix4fv(loc("uTexMatrix"), 1, false, texMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(if (oes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(loc("uTex"), 0)

        val p = params
        u1("uExposure", p.exposure); u1("uContrast", p.contrast)
        u1("uSaturation", p.saturation); u1("uVibrance", p.vibrance)
        u1("uTemp", p.temperature); u1("uTint", p.tint)
        u1("uHighlights", p.highlights); u1("uShadows", p.shadows)
        u1("uFade", p.fade); u1("uMono", p.mono); u1("uGrain", p.grain)
        u1("uVignette", p.vignette); u1("uHalation", p.halation); u1("uBloom", p.bloom)
        u1("uLeak", p.leak); u3("uLeakColor", p.leakColor)
        u3("uLift", p.lift); u3("uGamma", p.gamma); u3("uGain", p.gain)
        u3("uShadowTint", p.shadowTint); u3("uHighlightTint", p.highlightTint)
        u2("uRes", outW.toFloat(), outH.toFloat())
        u1("uGrainScale", maxOf(1f, maxOf(outW, outH) / 1600f))
        u1("uSeed", seed)
        u2("uGlowR", 0.012f, 0.012f * srcAspect)

        bindLut(if (p.lutAmount > 0.001f) lut else null)
        if (uploadedLut != null) {
            val l = uploadedLut!!
            val n = l.size.toFloat()
            val s = FloatArray(3) { (n - 1f) / n / (l.domainMax[it] - l.domainMin[it]) }
            val o = FloatArray(3) { 0.5f / n - l.domainMin[it] * s[it] }
            u3("uLutScale", s[0], s[1], s[2]); u3("uLutOffset", o[0], o[1], o[2])
            u1("uLutAmount", p.lutAmount)
        } else {
            u1("uLutAmount", 0f)
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        GLES30.glUniform1i(loc("uLut"), 1)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aUv)
        GLES30.glDisableVertexAttribArray(aTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun bindLut(lut: CubeLut?) {
        if (lutTex == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            lutTex = ids[0]
            // 2x2x2 identity so the sampler is always complete.
            upload(CubeLut(2, floatArrayOf(0f,0f,0f, 1f,0f,0f, 0f,1f,0f, 1f,1f,0f, 0f,0f,1f, 1f,0f,1f, 0f,1f,1f, 1f,1f,1f),
                floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f)))
            uploadedLut = null
        }
        if (lut === uploadedLut) return
        if (lut != null) upload(lut)
        uploadedLut = lut
    }

    private fun upload(l: CubeLut) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        val buf = ByteBuffer.allocateDirect(l.data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(l.data).position(0)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB16F, l.size, l.size, l.size, 0,
            GLES30.GL_RGB, GLES30.GL_FLOAT, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    companion object {
        private fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(s)
                Log.e(TAG, "shader compile failed: $log")
                throw RuntimeException("Shader compile failed: $log")
            }
            return s
        }

        private fun link(vs: String, fs: String): Int {
            val p = GLES30.glCreateProgram()
            GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
            GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
            GLES30.glLinkProgram(p)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw RuntimeException("Program link failed: " + GLES30.glGetProgramInfoLog(p))
            return p
        }

        val IDENTITY = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    }
}
