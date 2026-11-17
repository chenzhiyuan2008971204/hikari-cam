package com.hikari.studio

/**
 * A picture described as a list of simple drawing steps in output pixels. The layout code (stamps,
 * frames) only produces these; the app draws them with android.graphics, the tests with java.awt.
 * Colours are ARGB ints.
 */
sealed class Prim
class Fill(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int, val radius: Float = 0f) : Prim()
class Stroke(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int, val width: Float, val radius: Float = 0f) : Prim()
class LineP(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: Int, val width: Float) : Prim()
class Oval(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int, val stroke: Float = 0f) : Prim()
/** filled polygon, points x0,y0,x1,y1... */
class Poly(val pts: FloatArray, val color: Int) : Prim()
class Grad(val x: Float, val y: Float, val w: Float, val h: Float, val c0: Int, val c1: Int, val vertical: Boolean) : Prim()
/** paper grain: every pixel of the area gets a random lightness offset of up to +-amount (0..1) */
class Noise(val x: Float, val y: Float, val w: Float, val h: Float, val amount: Float, val seed: Int) : Prim()
class Shadow(val x: Float, val y: Float, val w: Float, val h: Float, val blur: Float, val color: Int, val radius: Float = 0f) : Prim()
/**
 * The photo: source rectangle (photo pixels) drawn into a destination rectangle of the same shape.
 * blur > 0 draws a blurred copy (backgrounds); alpha 0..1.
 */
class PhotoP(val x: Float, val y: Float, val w: Float, val h: Float, val sx: Float, val sy: Float, val sw: Float, val sh: Float,
             val radius: Float = 0f, val blur: Float = 0f, val alpha: Float = 1f) : Prim()
/**
 * Text on a baseline. align -1 left, 0 centre, 1 right of (x, y). font: sans, serif, mono, condensed,
 * hand, casual. spacing in em. bg != 0 draws a box behind the text (pad around it).
 */
class TextP(val text: String, val x: Float, val y: Float, val size: Float, val font: String, val weight: Int, val color: Int,
            val align: Int = -1, val spacing: Float = 0f, val rotation: Float = 0f, val bg: Int = 0, val pad: Float = 0f, val italic: Boolean = false) : Prim()

class R(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
}

/** Canvas size, what to draw in order, where the photo sits, and which part of the photo is used. */
class Scene(val w: Int, val h: Int, val prims: List<Prim>, val photo: R, val crop: R)

fun argb(a: Int, rgb: Int) = (a shl 24) or (rgb and 0xFFFFFF)
fun withAlpha(c: Int, alpha: Float) = argb(((c ushr 24) * alpha).toInt().coerceIn(0, 255), c)
const val WHITE = -1
const val BLACK = -0x1000000

/** Seven-segment digits as polygons, for LED date stamps that look the same on every device. */
object SevenSeg {
    private const val A = 1; private const val B = 2; private const val C = 4; private const val D = 8; private const val E = 16; private const val F = 32; private const val G = 64
    private val digits = intArrayOf(A or B or C or D or E or F, B or C, A or B or G or E or D, A or B or G or C or D, F or G or B or C,
        A or F or G or C or D, A or F or G or E or C or D, A or B or C, A or B or C or D or E or F or G, A or B or C or D or F or G)
    fun supports(s: String) = s.all { it.isDigit() || it in " :./-'" }
    /** advance width of [s] at height [size] */
    fun width(s: String, size: Float, spacing: Float = 0f): Float = s.sumOf { (adv(it, size) + spacing * size).toDouble() }.toFloat() - spacing * size
    private fun adv(c: Char, size: Float) = when (c) { ':', '.', '\'' -> 0.3f; ' ' -> 0.42f; '/', '-' -> 0.55f; else -> 0.62f } * size
    /** polygons for [s] with its left baseline at (x, y) */
    fun polys(s: String, x: Float, y: Float, size: Float, color: Int, spacing: Float = 0f): List<Prim> {
        val out = ArrayList<Prim>(); var cx = x
        val w = 0.5f * size; val t = 0.11f * size; val top = y - size; val mid = y - size / 2f
        fun h(x0: Float, yc: Float) { val a = x0 + t * 0.6f; val b = x0 + w - t * 0.6f
            out += Poly(floatArrayOf(a, yc, a + t / 2, yc - t / 2, b - t / 2, yc - t / 2, b, yc, b - t / 2, yc + t / 2, a + t / 2, yc + t / 2), color) }
        fun v(xc: Float, y0: Float, y1: Float) { val a = y0 + t * 0.6f; val b = y1 - t * 0.6f
            out += Poly(floatArrayOf(xc, a, xc + t / 2, a + t / 2, xc + t / 2, b - t / 2, xc, b, xc - t / 2, b - t / 2, xc - t / 2, a + t / 2), color) }
        for (c in s) {
            when {
                c.isDigit() -> { val m = digits[c - '0']
                    if (m and A != 0) h(cx, top + t / 2); if (m and G != 0) h(cx, mid); if (m and D != 0) h(cx, y - t / 2)
                    if (m and F != 0) v(cx + t / 2, top, mid); if (m and B != 0) v(cx + w - t / 2, top, mid)
                    if (m and E != 0) v(cx + t / 2, mid, y); if (m and C != 0) v(cx + w - t / 2, mid, y) }
                c == ':' -> { out += Fill(cx + 0.06f * size, mid - 0.2f * size, t, t, color); out += Fill(cx + 0.06f * size, mid + 0.2f * size - t, t, t, color) }
                c == '.' -> out += Fill(cx + 0.06f * size, y - t, t, t, color)
                c == '\'' -> out += Fill(cx + 0.06f * size, top, t, t * 1.8f, color)
                c == '-' -> out += Fill(cx + 0.04f * size, mid - t / 2, 0.36f * size, t, color)
                c == '/' -> out += Poly(floatArrayOf(cx + 0.02f * size, y, cx + 0.02f * size + t, y, cx + 0.42f * size, top, cx + 0.42f * size - t, top), color)
            }
            cx += adv(c, size) + spacing * size
        }
        return out
    }
}
