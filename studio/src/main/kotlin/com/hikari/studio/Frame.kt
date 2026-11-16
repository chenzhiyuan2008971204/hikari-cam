package com.hikari.studio

import java.time.LocalDateTime
import java.util.Random
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class FrameCat(val zh: String) {
    FILM("底片 Film"), MINIMAL("極簡 Minimal"), JAPANESE("日系 Japanese"), CINEMATIC("電影 Cinematic"),
    INSTANT("拍立得 Instant"), EDITORIAL("雜誌 Editorial"), CREATIVE("創意 Creative")
}
enum class FitMode(val zh: String) { FIT("完整顯示 Fit"), FILL("填滿裁切 Fill") }

/** What the user can change about a frame; stored with the edit. */
data class FrameOptions(
    val frame: String = "",
    /** scales the frame's margins, 0.5 .. 2 */
    val thickness: Float = 1f,
    /** 0 = the frame's own paper colour */
    val color: Int = 0,
    /** canvas width / height, 0 = whatever the frame gives for this photo */
    val ratio: Float = 0f,
    val mode: FitMode = FitMode.FIT,
    /** crop the photo to this width / height first, 0 = keep (or the frame's own crop) */
    val photoAspect: Float = 0f,
    val title: String = "", val caption: String = "",
    val showDate: Boolean = true, val showInfo: Boolean = true,
    /** where the framed photo sits when the canvas ratio leaves spare room, -1 .. 1 */
    val offsetX: Float = 0f, val offsetY: Float = 0f,
) {
    fun encode() = listOf(frame, thickness, color, ratio, mode.name, photoAspect, esc(title), esc(caption), showDate, showInfo, offsetX, offsetY).joinToString("|")
    companion object {
        private fun esc(s: String) = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", " ")
        private fun un(s: String) = s.replace("\\p", "|").replace("\\\\", "\\")
        fun decode(s: String?): FrameOptions = runCatching {
            val p = s!!.split("|")
            FrameOptions(p[0], p[1].toFloat(), p[2].toInt(), p[3].toFloat(), FitMode.valueOf(p[4]), p[5].toFloat(), un(p[6]), un(p[7]), p[8].toBoolean(), p[9].toBoolean(),
                p[10].toFloat(), p[11].toFloat())
        }.getOrDefault(FrameOptions())
    }
}

/** Facts about the photo that frames may print. Nothing here is invented: empty means "not known". */
class Meta(val date: LocalDateTime? = null, val info: String = "", val camera: String = "", val place: String = "",
           /** five colours taken from the photo itself */
           val palette: IntArray = intArrayOf(0xFF2B2B2B.toInt(), 0xFF6B6B6B.toInt(), 0xFF9A9A9A.toInt(), 0xFFC8C8C8.toInt(), 0xFFEDEDED.toInt()),
           val number: Int = 1)

class FrameDef(val id: String, val name: String, val zh: String, val cat: FrameCat, val demo: String, val keywords: String,
               val photoAspect: Float = 0f, val build: B.() -> Unit)

/** Builder a frame design draws with. Coordinates are pixels on the frame's own card; [u] is 1 % of the photo's short side. */
class B(val pw: Float, val ph: Float, val o: FrameOptions, val m: Meta, private val ox: Float, private val oy: Float) {
    val u = min(pw, ph) / 100f
    var l = 0f; var t = 0f; var r = 0f; var b = 0f
    var bg = WHITE
    var radius = 0f
    var blurBg = false
    val under = ArrayList<Prim>(); val over = ArrayList<Prim>()
    private var target = over
    val rnd = Random(7)
    val cw get() = l + pw + r
    val ch get() = t + ph + b
    val px get() = l
    val py get() = t
    /** whole pixels, so the photo lands on the pixel grid and is copied, not resampled */
    fun margins(left: Float, top: Float, right: Float, bottom: Float) { val k = u * o.thickness; l = Math.round(left * k).toFloat(); t = Math.round(top * k).toFloat(); r = Math.round(right * k).toFloat(); b = Math.round(bottom * k).toFloat() }
    fun paper(c: Int) { bg = if (o.color != 0) o.color else c }
    /** readable ink on the current paper */
    val ink: Int get() { val c = bg; val y = (0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255)); return if (y > 140) 0xFF1A1A1A.toInt() else 0xFFF4F1EA.toInt() }
    val dim: Int get() = withAlpha(ink, 0.55f)
    fun date(fmt: String): String? = if (o.showDate) m.date?.let { DateFormats.format(fmt, it) } else null
    fun info(): String? = if (o.showInfo) m.info.ifEmpty { null } else null
    fun title(default: String) = o.title.ifEmpty { default }
    fun caption(default: String) = o.caption.ifEmpty { default }
    fun behind(f: () -> Unit) { target = under; f(); target = over }

    fun fill(x: Float, y: Float, w: Float, h: Float, c: Int, rad: Float = 0f) { target += Fill(ox + x, oy + y, w, h, c, rad) }
    fun stroke(x: Float, y: Float, w: Float, h: Float, c: Int, width: Float, rad: Float = 0f) { target += Stroke(ox + x, oy + y, w, h, c, width, rad) }
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, width: Float) { target += LineP(ox + x0, oy + y0, ox + x1, oy + y1, c, width) }
    fun oval(x: Float, y: Float, w: Float, h: Float, c: Int, strokeW: Float = 0f) { target += Oval(ox + x, oy + y, w, h, c, strokeW) }
    fun poly(c: Int, vararg p: Float) { target += Poly(FloatArray(p.size) { p[it] + if (it % 2 == 0) ox else oy }, c) }
    fun grad(x: Float, y: Float, w: Float, h: Float, c0: Int, c1: Int, vertical: Boolean) { target += Grad(ox + x, oy + y, w, h, c0, c1, vertical) }
    fun noise(amount: Float) { under += Noise(ox, oy, cw, ch, amount, 11) }
    fun shadow(x: Float, y: Float, w: Float, h: Float, blur: Float, c: Int, rad: Float = 0f) { under += Shadow(ox + x, oy + y, w, h, blur, c, rad) }
    fun text(s: String?, x: Float, y: Float, size: Float, font: String = "sans", weight: Int = 400, c: Int = ink, align: Int = -1, spacing: Float = 0f,
             rot: Float = 0f, italic: Boolean = false) { if (!s.isNullOrEmpty()) target += TextP(s, ox + x, oy + y, size, font, weight, c, align, spacing, rot, 0, 0f, italic) }
    fun led(s: String?, x: Float, y: Float, size: Float, c: Int, align: Int = -1) {
        if (s.isNullOrEmpty()) return
        if (!SevenSeg.supports(s)) { text(s, x, y, size, "mono", 700, c, align, 0.05f); return }
        val w = SevenSeg.width(s, size, 0.06f); val x0 = when (align) { -1 -> x; 0 -> x - w / 2; else -> x - w }
        target += SevenSeg.polys(s, ox + x0, oy + y, size, c, 0.06f)
    }
    /** a row of film perforations between x0 and x1 */
    fun sprockets(y: Float, h: Float, c: Int, x0: Float = 0f, x1: Float = cw, pitch: Float = 4.75f * u, w: Float = 2.6f * u) {
        var x = x0 + pitch * 0.4f; while (x + w < x1) { fill(x, y, w, h, c, h * 0.22f); x += pitch }
    }
    fun sprocketsV(x: Float, w: Float, c: Int, pitch: Float = 4.75f * u, h: Float = 2.6f * u) {
        var y = pitch * 0.4f; while (y + h < ch) { fill(x, y, w, h, c, w * 0.22f); y += pitch }
    }
    /** L-shaped marks at the four corners of a rectangle */
    fun corners(x: Float, y: Float, w: Float, h: Float, len: Float, c: Int, width: Float) {
        for ((cx, sx) in listOf(x to 1f, x + w to -1f)) for ((cy, sy) in listOf(y to 1f, y + h to -1f)) {
            line(cx, cy, cx + sx * len, cy, c, width); line(cx, cy, cx, cy + sy * len, c, width) }
    }
    /** torn paper: bites of the outside colour along every edge of the card */
    fun torn(outside: Int, depth: Float) {
        val step = 2.2f * u
        fun edge(horizontal: Boolean, far: Boolean) {
            val len = if (horizontal) cw else ch; val pts = ArrayList<Float>(); var p = 0f
            val base = if (far) (if (horizontal) ch else cw) else 0f
            fun add(a: Float, d: Float) { val v = if (far) base - d else d; if (horizontal) { pts += a; pts += v } else { pts += v; pts += a } }
            add(0f, 0f)
            while (p <= len) { add(p, depth * (0.25f + 0.75f * rnd.nextFloat())); p += step * (0.6f + rnd.nextFloat()) }
            add(len, depth * 0.5f); add(len, 0f)
            poly(outside, *pts.toFloatArray())
        }
        edge(true, false); edge(true, true); edge(false, false); edge(false, true)
    }
    fun tape(cx: Float, cy: Float, w: Float, h: Float, deg: Float, c: Int) {
        val a = Math.toRadians(deg.toDouble()); val co = cos(a).toFloat(); val si = sin(a).toFloat()
        fun p(dx: Float, dy: Float) = floatArrayOf(cx + dx * co - dy * si, cy + dx * si + dy * co)
        val q = listOf(p(-w / 2, -h / 2), p(w / 2, -h / 2), p(w / 2, h / 2), p(-w / 2, h / 2))
        poly(c, q[0][0], q[0][1], q[1][0], q[1][1], q[2][0], q[2][1], q[3][0], q[3][1])
    }
    /** colour chips taken from the photo */
    fun chips(x: Float, y: Float, s: Float, gap: Float, round: Boolean = false) {
        for (i in 0 until 5) if (round) oval(x + i * (s + gap), y, s, s, m.palette[i]) else fill(x + i * (s + gap), y, s, s, m.palette[i])
    }
    fun barcode(x: Float, y: Float, w: Float, h: Float, c: Int) {
        var p = x; val r2 = Random(3)
        while (p < x + w) { val bw = u * (0.25f + 0.5f * r2.nextInt(3)); if (r2.nextBoolean()) fill(p, y, min(bw, x + w - p), h, c); p += bw + u * 0.25f }
    }
}

object FrameEngine {
    private fun build(def: FrameDef, pw: Float, ph: Float, o: FrameOptions, m: Meta, ox: Float, oy: Float) = B(pw, ph, o, m, ox, oy).also { def.build(it) }

    /**
     * Lays out the photo, the stamp and the frame. The photo is never stretched: it is drawn 1:1
     * from a crop of the same shape (the whole photo unless a crop was asked for), and the canvas
     * grows around it.
     */
    fun scene(def: FrameDef?, o: FrameOptions, photoW: Int, photoH: Int, m: Meta, stamp: StampSpec? = null, stampText: String? = null): Scene {
        // 1. the part of the photo that is used
        var cw = photoW.toFloat(); var ch = photoH.toFloat()
        val a = if (o.photoAspect > 0f) o.photoAspect else def?.photoAspect ?: 0f
        if (a > 0f) { if (cw / ch > a) cw = Math.round(ch * a).toFloat() else ch = Math.round(cw / a).toFloat() }
        fun crop() = R(((photoW - cw) / 2f).toInt().toFloat(), ((photoH - ch) / 2f).toInt().toFloat(), cw, ch)
        if (def == null) {
            val W = ceil(cw).toInt(); val H = ceil(ch).toInt(); val pr = R(0f, 0f, cw, ch); val c = crop()
            return Scene(W, H, listOf<Prim>(PhotoP(0f, 0f, cw, ch, c.x, c.y, c.w, c.h)) + (stamp?.let { Stamps.prims(it, stampText, pr) } ?: emptyList()), pr, c)
        }
        var b = build(def, cw, ch, o, m, 0f, 0f)
        // 2. Fill: crop the photo so that photo + margins has the requested canvas ratio
        if (o.ratio > 0f && o.mode == FitMode.FILL) {
            val L = b.l + b.r; val T = b.t + b.b
            var ww = cw; var wh = (ww + L) / o.ratio - T
            if (wh <= 0f || wh > ch) { wh = ch; ww = o.ratio * (wh + T) - L }
            if (ww >= 1f && ww <= cw + 0.5f && wh >= 1f) { cw = Math.round(min(ww, cw)).toFloat(); ch = Math.round(wh).toFloat(); b = build(def, cw, ch, o, m, 0f, 0f) }
        }
        // 3. spare room when the card does not have the requested ratio
        var W = b.cw; var H = b.ch; var dx = 0f; var dy = 0f
        if (o.ratio > 0f) {
            if (W / H < o.ratio) { val ex = H * o.ratio - W; dx = Math.round(ex / 2f * (1f + o.offsetX.coerceIn(-1f, 1f))).toFloat(); W += ex }
            else { val ey = W / o.ratio - H; dy = Math.round(ey / 2f * (1f + o.offsetY.coerceIn(-1f, 1f))).toFloat(); H += ey }
        }
        if (dx != 0f || dy != 0f) b = build(def, cw, ch, o, m, dx, dy)
        val iw = ceil(W).toInt(); val ih = ceil(H).toInt(); val c = crop()
        val pr = R(dx + b.l, dy + b.t, cw, ch)
        val prims = ArrayList<Prim>()
        prims += Fill(0f, 0f, iw.toFloat(), ih.toFloat(), b.bg)
        if (b.blurBg) {  // the photo itself, enlarged to cover the canvas and blurred
            val s = maxOf(iw / c.w, ih / c.h); val bw = iw / s; val bh = ih / s
            prims += PhotoP(0f, 0f, iw.toFloat(), ih.toFloat(), c.x + (c.w - bw) / 2f, c.y + (c.h - bh) / 2f, bw, bh, 0f, b.u * 6f, 1f)
            prims += Fill(0f, 0f, iw.toFloat(), ih.toFloat(), 0x33000000)
        }
        prims += b.under
        prims += PhotoP(pr.x, pr.y, pr.w, pr.h, c.x, c.y, c.w, c.h, b.radius)
        if (stamp != null) prims += Stamps.prims(stamp, stampText, pr)
        prims += b.over
        return Scene(iw, ih, prims, pr, c)
    }
}
