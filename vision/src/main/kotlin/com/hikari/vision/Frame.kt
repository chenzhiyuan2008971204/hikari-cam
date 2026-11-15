package com.hikari.vision

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/**
 * A small analysis image: sRGB 0..1, row-major, 3 floats per pixel. The app scales the photo or
 * the camera frame to about 256 px on the long side; all analysis is done at that size.
 */
class Frame(val w: Int, val h: Int, val rgb: FloatArray) {
    init { require(rgb.size == w * h * 3) { "rgb size" } }

    /** display luma per pixel */
    val luma: FloatArray by lazy { FloatArray(w * h) { i -> 0.2126f * rgb[i * 3] + 0.7152f * rgb[i * 3 + 1] + 0.0722f * rgb[i * 3 + 2] } }

    /** OKLab L, a, b per pixel */
    val lab: FloatArray by lazy {
        val out = FloatArray(w * h * 3)
        for (i in 0 until w * h) {
            val r = lin(rgb[i * 3]); val g = lin(rgb[i * 3 + 1]); val b = lin(rgb[i * 3 + 2])
            val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            out[i * 3] = (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).toFloat()
            out[i * 3 + 1] = (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s).toFloat()
            out[i * 3 + 2] = (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s).toFloat()
        }
        out
    }

    fun L(i: Int) = lab[i * 3]
    fun chroma(i: Int) = hypot(lab[i * 3 + 1], lab[i * 3 + 2])
    /** OKLab hue in degrees 0..360 */
    fun hue(i: Int): Float { val d = Math.toDegrees(atan2(lab[i * 3 + 2].toDouble(), lab[i * 3 + 1].toDouble())).toFloat(); return if (d < 0) d + 360f else d }

    /** Sobel gradients of luma: gx, gy (interior pixels; border = 0) */
    val grad: Pair<FloatArray, FloatArray> by lazy {
        val gx = FloatArray(w * h); val gy = FloatArray(w * h); val y = luma
        for (r in 1 until h - 1) for (c in 1 until w - 1) {
            val i = r * w + c
            gx[i] = (y[i - w + 1] + 2 * y[i + 1] + y[i + w + 1] - y[i - w - 1] - 2 * y[i - 1] - y[i + w - 1]) / 4f
            gy[i] = (y[i + w - 1] + 2 * y[i + w] + y[i + w + 1] - y[i - w - 1] - 2 * y[i - w] - y[i - w + 1]) / 4f
        }
        gx to gy
    }

    val gradMag: FloatArray by lazy { val (gx, gy) = grad; FloatArray(w * h) { hypot(gx[it], gy[it]) } }

    companion object {
        private fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

        /** Box-downscale packed ARGB/RGB ints (as from a Bitmap or BufferedImage) to at most [maxSide]. */
        fun fromArgb(pixels: IntArray, w: Int, h: Int, maxSide: Int = 256): Frame {
            val s = maxOf(1f, maxOf(w, h) / maxSide.toFloat())
            val ow = maxOf(1, (w / s).toInt()); val oh = maxOf(1, (h / s).toInt())
            val out = FloatArray(ow * oh * 3)
            for (oy in 0 until oh) {
                val y0 = (oy * h) / oh; val y1 = maxOf(y0 + 1, ((oy + 1) * h) / oh)
                for (ox in 0 until ow) {
                    val x0 = (ox * w) / ow; val x1 = maxOf(x0 + 1, ((ox + 1) * w) / ow)
                    var r = 0L; var g = 0L; var b = 0L
                    for (y in y0 until y1) for (x in x0 until x1) { val p = pixels[y * w + x]; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255 }
                    val n = ((y1 - y0) * (x1 - x0)).toFloat() * 255f
                    val o = (oy * ow + ox) * 3
                    out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n
                }
            }
            return Frame(ow, oh, out)
        }
    }
}

/** A box in fractions of the frame (0..1), y down. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val w get() = right - left
    val h get() = bottom - top
    val area get() = w * h
}

/** Something a detector found (ML Kit on the phone, annotations in tests). */
data class Detection(val kind: Kind, val box: Box, val yawDeg: Float = 0f) {
    enum class Kind { FACE, PERSON, OBJECT }
}
