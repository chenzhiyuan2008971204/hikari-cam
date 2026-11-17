package com.hikari.cam.lab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * A built-in test chart, drawn at any size, for checking a look without a photo:
 *  - hue sweep fading to white (gamut, highlight roll-off, banding)
 *  - grey ramp (tone curve, neutrality, grain by brightness)
 *  - 24 colour patches with common reference colours and skin tones
 *  - a dark band with small bright lights (halation and bloom)
 * Drawn from geometry in fractions of the size, so every size shows the same picture.
 */
object TestChart {
    const val FULL_W = 4000
    const val FULL_H = 3000

    private val PATCHES = intArrayOf(
        0x735244, 0xC29682, 0x627A9D, 0x576C43, 0x8580B1, 0x67BDAA,
        0xD67E2C, 0x505BA6, 0xC15A63, 0x5E3C6C, 0x9DBC40, 0xE0A32E,
        0x383D96, 0x469449, 0xAF363C, 0xE7C71F, 0xBB5695, 0x0885A1,
        0xF3F3F2, 0xC8C8C8, 0xA0A0A0, 0x7A7A79, 0x555555, 0x343434,
    )
    private val SKIN = intArrayOf(0xF1C7AE, 0xE0AC8A, 0xC68863, 0x8D5A3E, 0x5C3A2A, 0xFFDBC8)

    fun draw(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.rgb(18, 18, 18))

        // 1. hue sweep, top 30%: saturated hues across, fading to white towards the top
        val sweepH = h * 0.30f
        val hues = IntArray(13) { Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f)) }
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, hues, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), sweepH, p)
        p.shader = LinearGradient(0f, 0f, 0f, sweepH, intArrayOf(Color.WHITE, 0x00FFFFFF), floatArrayOf(0f, 0.6f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), sweepH, p)

        // 2. grey ramp
        val rampTop = sweepH
        val rampH = h * 0.08f
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP)
        c.drawRect(0f, rampTop, w.toFloat(), rampTop + rampH, p)
        p.shader = null

        // 3. colour patches 6 x 4 on the left, skin tones on the right
        val gridTop = rampTop + rampH + h * 0.02f
        val cell = h * 0.085f
        val gap = cell * 0.12f
        for (i in PATCHES.indices) {
            val x = w * 0.03f + (i % 6) * (cell + gap)
            val y = gridTop + (i / 6) * (cell + gap)
            p.color = 0xFF000000.toInt() or PATCHES[i]
            c.drawRect(x, y, x + cell, y + cell, p)
        }
        val skinLeft = w * 0.03f + 6 * (cell + gap) + cell * 0.6f
        for (i in SKIN.indices) {
            val x = skinLeft + (i % 3) * (cell * 1.3f + gap)
            val y = gridTop + (i / 3) * (cell * 1.3f + gap)
            p.color = 0xFF000000.toInt() or SKIN[i]
            c.drawOval(x, y, x + cell * 1.3f, y + cell * 1.3f, p)
        }

        // 4. night band: dark blue gradient with point lights
        val nightTop = gridTop + 4 * (cell + gap) + h * 0.02f
        p.shader = LinearGradient(0f, nightTop, 0f, h.toFloat(), Color.rgb(10, 14, 30), Color.rgb(2, 2, 6), Shader.TileMode.CLAMP)
        c.drawRect(0f, nightTop, w.toFloat(), h.toFloat(), p)
        val lights = intArrayOf(Color.WHITE, Color.rgb(255, 210, 140), Color.rgb(255, 40, 60), Color.rgb(40, 220, 255), Color.rgb(255, 80, 220), Color.WHITE)
        val nightH = h - nightTop
        for (i in lights.indices) {
            val cx = w * (0.08f + i * 0.17f)
            val cy = nightTop + nightH * (if (i % 2 == 0) 0.4f else 0.65f)
            val r = h * 0.012f
            p.shader = RadialGradient(cx, cy, r * 1.6f, intArrayOf(lights[i], lights[i], 0x00000000), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r * 1.6f, p)
        }
        // a thin bright neon line
        p.shader = null
        p.color = Color.rgb(255, 60, 160)
        c.drawRect(w * 0.1f, nightTop + nightH * 0.85f, w * 0.9f, nightTop + nightH * 0.85f + h * 0.006f, p)
        return bmp
    }
}
