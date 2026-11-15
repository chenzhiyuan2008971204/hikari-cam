package com.hikari.cam.lab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/**
 * The seven daylight test scenes of the Daylight Filter Lab. Each starts as a drawn stand-in with
 * the colours that matter for that scene (measured daylight sky, foliage, skin and white values);
 * the user can replace any of them with a real photo.
 */
enum class DaylightScene(val zh: String, val hint: String) {
    SKY("藍天白雲", "天空層次不螢光，雲朵細節"),
    TREES("綠色樹木", "黃綠、草綠、深綠分開，不髒不螢光"),
    STREET("日本街道", "招牌、柏油、白牆、天空"),
    CONCRETE("建築與水泥", "中性灰不偏色，陰影乾淨"),
    WHITE("白色衣物", "日光與陰影下的白，乾淨不黃不灰"),
    PORTRAIT("人像", "不同光線的膚色，自然不橘紅"),
    BACKLIGHT("逆光", "太陽周圍的高光過渡、剪影細節"),
}

object DaylightScenes {
    private fun c(r: Int, g: Int, b: Int) = android.graphics.Color.rgb(r, g, b)

    fun draw(scene: DaylightScene, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = Random(scene.ordinal * 31 + 7)
        fun sky(top: Float, bottom: Float) {
            p.shader = LinearGradient(0f, top, 0f, bottom,
                intArrayOf(c(44, 86, 168), c(72, 122, 196), c(110, 160, 215), c(150, 190, 228), c(196, 220, 238)),
                null, Shader.TileMode.CLAMP)
            cv.drawRect(0f, top, w.toFloat(), bottom, p); p.shader = null
        }
        fun cloud(cx: Float, cy: Float, r: Float) {
            // lit top, grey-blue shaded underside
            for (i in 0 until 9) {
                val x = cx + (rnd.nextFloat() - 0.5f) * r * 2.2f
                val y = cy + (rnd.nextFloat() - 0.3f) * r * 0.8f
                val rr = r * (0.45f + rnd.nextFloat() * 0.5f)
                p.shader = RadialGradient(x, y - rr * 0.3f, rr, intArrayOf(c(252, 252, 253), c(236, 238, 242), c(196, 202, 214), 0x00C4CAD6),
                    floatArrayOf(0f, 0.45f, 0.85f, 1f), Shader.TileMode.CLAMP)
                cv.drawCircle(x, y, rr, p)
            }
            p.shader = null
        }
        fun blob(cx: Float, cy: Float, r: Float, light: Int, dark: Int) {
            p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.2f, light, dark, Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, r, p); p.shader = null
        }
        val W = w.toFloat(); val H = h.toFloat()
        when (scene) {
            DaylightScene.SKY -> {
                sky(0f, H)
                cloud(W * 0.25f, H * 0.62f, H * 0.16f); cloud(W * 0.7f, H * 0.5f, H * 0.2f); cloud(W * 0.5f, H * 0.82f, H * 0.14f)
                cloud(W * 0.15f, H * 0.25f, H * 0.07f)
            }
            DaylightScene.TREES -> {
                sky(0f, H * 0.5f)
                p.color = c(96, 148, 52); cv.drawRect(0f, H * 0.62f, W, H, p)
                p.shader = LinearGradient(0f, H * 0.62f, 0f, H, c(150, 178, 70), c(70, 112, 40), Shader.TileMode.CLAMP)
                cv.drawRect(0f, H * 0.62f, W, H, p); p.shader = null
                val greens = listOf(c(165, 180, 60) to c(110, 130, 40), c(96, 148, 52) to c(50, 92, 32),
                    c(62, 112, 48) to c(28, 62, 30), c(40, 92, 72) to c(18, 50, 40))
                for (i in 0 until 26) {
                    val (l, d) = greens[i % greens.size]
                    blob(W * rnd.nextFloat(), H * (0.3f + rnd.nextFloat() * 0.32f), H * (0.07f + rnd.nextFloat() * 0.08f), l, d)
                }
            }
            DaylightScene.STREET -> {
                sky(0f, H * 0.35f)
                p.color = c(92, 92, 96); cv.drawRect(0f, H * 0.72f, W, H, p)
                val walls = listOf(c(236, 232, 222), c(200, 196, 190), c(178, 168, 150), c(244, 244, 242), c(150, 146, 140))
                var x = 0f
                while (x < W) {
                    val bw = W * (0.12f + rnd.nextFloat() * 0.12f)
                    val top = H * (0.12f + rnd.nextFloat() * 0.25f)
                    p.color = walls[rnd.nextInt(walls.size)]; cv.drawRect(x, top, x + bw, H * 0.72f, p)
                    p.color = c(60, 66, 74)
                    var y = top + H * 0.05f
                    while (y < H * 0.62f) { cv.drawRect(x + bw * 0.15f, y, x + bw * 0.4f, y + H * 0.05f, p); cv.drawRect(x + bw * 0.6f, y, x + bw * 0.85f, y + H * 0.05f, p); y += H * 0.1f }
                    val signs = listOf(c(200, 40, 40), c(30, 70, 160), c(240, 200, 40), c(30, 140, 90))
                    p.color = signs[rnd.nextInt(signs.size)]; cv.drawRect(x + bw * 0.2f, top + H * 0.02f, x + bw * 0.8f, top + H * 0.045f, p)
                    x += bw
                }
                p.color = c(240, 240, 236)
                for (i in 0 until 8) cv.drawRect(W * (i / 8f), H * 0.85f, W * (i / 8f) + W * 0.06f, H * 0.87f, p)
            }
            DaylightScene.CONCRETE -> {
                sky(0f, H * 0.3f)
                p.shader = LinearGradient(0f, 0f, W, 0f, c(196, 194, 190), c(150, 149, 146), Shader.TileMode.CLAMP)
                cv.drawRect(0f, H * 0.3f, W * 0.62f, H, p)
                p.shader = LinearGradient(W * 0.62f, 0f, W, 0f, c(120, 122, 126), c(96, 98, 104), Shader.TileMode.CLAMP)
                cv.drawRect(W * 0.62f, H * 0.3f, W, H, p); p.shader = null
                p.color = c(70, 72, 76)
                for (i in 1 until 5) cv.drawRect(0f, H * (0.3f + i * 0.14f), W, H * (0.3f + i * 0.14f) + 2f, p)
                for (i in 0 until 4000) { p.color = android.graphics.Color.argb(40, rnd.nextInt(60, 220), rnd.nextInt(60, 220), rnd.nextInt(60, 220)); cv.drawPoint(W * rnd.nextFloat(), H * (0.3f + 0.7f * rnd.nextFloat()), p) }
            }
            DaylightScene.WHITE -> {
                p.color = c(150, 160, 150); cv.drawRect(0f, 0f, W, H, p)
                fun cloth(r: RectF, lit: Int, mid: Int, shade: Int) {
                    p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(lit, mid, lit, shade, mid), null, Shader.TileMode.CLAMP)
                    cv.drawRoundRect(r, 24f, 24f, p); p.shader = null
                }
                cloth(RectF(W * 0.04f, H * 0.08f, W * 0.34f, H * 0.92f), c(252, 251, 248), c(236, 234, 228), c(205, 204, 200))
                cloth(RectF(W * 0.36f, H * 0.08f, W * 0.66f, H * 0.92f), c(232, 236, 244), c(214, 220, 232), c(186, 194, 210))
                cloth(RectF(W * 0.68f, H * 0.08f, W * 0.96f, H * 0.92f), c(246, 240, 228), c(232, 224, 210), c(200, 192, 180))
            }
            DaylightScene.PORTRAIT -> {
                p.shader = LinearGradient(0f, 0f, 0f, H, c(120, 150, 90), c(60, 90, 50), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W, H, p); p.shader = null
                val skins = listOf(c(236, 196, 170), c(205, 170, 156), c(242, 182, 134), c(226, 186, 158), c(140, 96, 72), c(214, 182, 168))
                for ((i, s) in skins.withIndex()) {
                    val cx = W * (0.17f + (i % 3) * 0.33f); val cy = H * (0.28f + (i / 3) * 0.46f)
                    p.color = c(40, 30, 26); cv.drawOval(RectF(cx - H * 0.15f, cy - H * 0.2f, cx + H * 0.15f, cy + H * 0.12f), p)
                    p.shader = RadialGradient(cx - H * 0.04f, cy - H * 0.04f, H * 0.18f, s, darker(s), Shader.TileMode.CLAMP)
                    cv.drawOval(RectF(cx - H * 0.11f, cy - H * 0.15f, cx + H * 0.11f, cy + H * 0.15f), p); p.shader = null
                }
            }
            DaylightScene.BACKLIGHT -> {
                p.shader = LinearGradient(0f, 0f, 0f, H, c(150, 175, 205), c(236, 222, 196), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W, H, p)
                p.shader = RadialGradient(W * 0.55f, H * 0.35f, H * 0.6f, intArrayOf(c(255, 255, 250), c(255, 240, 200), 0x00FFE0B0),
                    floatArrayOf(0f, 0.12f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W, H, p)
                p.shader = LinearGradient(0f, H * 0.7f, 0f, H, c(196, 170, 70), c(70, 70, 30), Shader.TileMode.CLAMP)
                cv.drawRect(0f, H * 0.7f, W, H, p); p.shader = null
                p.color = c(34, 28, 22)
                cv.drawRect(W * 0.5f, H * 0.2f, W * 0.56f, H * 0.82f, p)
                for (i in 0 until 7) {
                    p.strokeWidth = H * 0.012f
                    cv.drawLine(W * 0.53f, H * (0.3f + i * 0.06f), W * (0.2f + rnd.nextFloat() * 0.7f), H * (0.05f + rnd.nextFloat() * 0.35f), p)
                }
            }
        }
        return bmp
    }

    private fun darker(c: Int): Int = android.graphics.Color.rgb(
        (android.graphics.Color.red(c) * 0.72f).toInt(), (android.graphics.Color.green(c) * 0.7f).toInt(),
        (android.graphics.Color.blue(c) * 0.7f).toInt())
}
