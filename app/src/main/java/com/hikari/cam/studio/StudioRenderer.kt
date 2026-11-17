package com.hikari.cam.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.hikari.studio.Fill
import com.hikari.studio.FrameEngine
import com.hikari.studio.FrameOptions
import com.hikari.studio.Frames
import com.hikari.studio.Grad
import com.hikari.studio.LineP
import com.hikari.studio.Meta
import com.hikari.studio.Noise
import com.hikari.studio.Oval
import com.hikari.studio.PhotoP
import com.hikari.studio.PhotoTimes
import com.hikari.studio.Poly
import com.hikari.studio.Prim
import com.hikari.studio.Scene
import com.hikari.studio.Shadow
import com.hikari.studio.StampSpec
import com.hikari.studio.Stamps
import com.hikari.studio.Stroke
import com.hikari.studio.TextP
import com.hikari.studio.Times
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What the photo file itself says about when and how it was taken. Empty fields mean "not recorded". */
class PhotoInfo(val exifTime: LocalDateTime?, val created: LocalDateTime?, val info: String, val camera: String) {
    fun times() = PhotoTimes(exifTime, created, LocalDateTime.now())

    companion object {
        fun read(context: Context, uri: Uri): PhotoInfo {
            var time: LocalDateTime? = null; var info = ""; var camera = ""
            runCatching {
                context.contentResolver.openInputStream(uri)!!.use { ins ->
                    val e = ExifInterface(ins)
                    time = Times.parseExif(e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) ?: Times.parseExif(e.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
                    val parts = ArrayList<String>()
                    e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { parts += "f/" + (if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)) }
                    e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { parts += if (it >= 1) "%.1fs".format(it) else "1/${Math.round(1 / it)}s" }
                    (e.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)).takeIf { it > 0 }?.let { parts += "ISO $it" }
                    (e.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)).takeIf { it > 0 }?.let { parts += "${it}mm" }
                    info = parts.joinToString(" · ")
                    camera = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ").trim()
                }
            }
            var created: LocalDateTime? = null
            runCatching {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { c ->
                    if (c.moveToFirst()) { val s = c.getLong(0).takeIf { it > 0 } ?: c.getLong(1)
                        if (s > 0) created = LocalDateTime.ofInstant(Instant.ofEpochSecond(s), ZoneId.systemDefault()) }
                }
            }
            return PhotoInfo(time, created, info, camera)
        }
    }
}

/** Draws a studio Scene (photo + timestamp + frame) with android.graphics. Same draw list as the tested java.awt renderer. */
object StudioRenderer {
    /** five colours from dark to light, taken from the photo */
    fun palette(b: Bitmap): IntArray {
        val n = 48; val small = Bitmap.createScaledBitmap(b, n, n, true); val px = IntArray(n * n); small.getPixels(px, 0, n, 0, 0, n, n)
        if (small !== b) small.recycle()
        val sorted = px.sortedBy { (it shr 16 and 255) * 3 + (it shr 8 and 255) * 6 + (it and 255) }
        return IntArray(5) { k -> val part = sorted.subList(k * sorted.size / 5, (k + 1) * sorted.size / 5)
            (0xFF shl 24) or (part.sumOf { it shr 16 and 255 } / part.size shl 16) or (part.sumOf { it shr 8 and 255 } / part.size shl 8) or (part.sumOf { it and 255 } / part.size) }
    }

    /** The scene for a photo of [w] x [h] pixels with the user's stamp and frame; null when neither is on. */
    fun scene(w: Int, h: Int, stamp: StampSpec, frame: FrameOptions, info: PhotoInfo, palette: IntArray, number: Int = 1): Scene? {
        val def = Frames.get(frame.frame)
        if (!stamp.enabled && def == null && frame.photoAspect == 0f) return null
        val time = Times.resolve(stamp.source, info.times(), stamp.customTime()).time
        // frames print the same moment the stamp shows; with the stamp off they use the photo's own capture time only
        val frameTime = if (stamp.enabled) time else info.exifTime
        val meta = Meta(frameTime, info.info, info.camera, stamp.place, palette, number)
        return FrameEngine.scene(def, frame, w, h, meta, stamp, Stamps.text(stamp, time, info.info, number))
    }

    private val noiseTile: Bitmap by lazy {
        val n = 256; val r = java.util.Random(11); val px = IntArray(n * n) { val v = r.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }
    private fun face(p: TextP): Typeface {
        val fam = when (p.font) { "serif" -> "serif"; "mono" -> "monospace"; "condensed" -> "sans-serif-condensed"; "hand" -> "cursive"; "casual" -> "casual"; else -> "sans-serif" }
        val style = (if (p.weight >= 600) Typeface.BOLD else 0) or (if (p.italic) Typeface.ITALIC else 0)
        return Typeface.create(fam, style)
    }

    /** Renders [sc] at [scale] (1 = full size). [photo] may be any size: its pixels are mapped from the scene's photo coordinates. */
    fun render(sc: Scene, photo: Bitmap, scale: Float = 1f, photoScale: Float = 1f): Bitmap {
        val out = Bitmap.createBitmap(Math.round(sc.w * scale).coerceAtLeast(1), Math.round(sc.h * scale).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (prim in sc.prims) draw(c, p, prim, photo, photoScale, scale == 1f && photoScale == 1f)
        return out
    }

    /** Draws stamp steps straight onto a mutable bitmap (the camera's just-rendered photo). */
    fun drawOnto(target: Bitmap, prims: List<Prim>) {
        val c = Canvas(target); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (prim in prims) draw(c, p, prim, target, 1f, true)
    }

    private fun draw(c: Canvas, p: Paint, prim: Prim, photo: Bitmap, ps: Float, exact: Boolean) {
        p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true
        when (prim) {
            is Fill -> { p.color = prim.color; if (prim.radius > 0f) c.drawRoundRect(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, prim.radius, prim.radius, p) else c.drawRect(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, p) }
            is Stroke -> { p.color = prim.color; p.style = Paint.Style.STROKE; p.strokeWidth = prim.width
                if (prim.radius > 0f) c.drawRoundRect(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, prim.radius, prim.radius, p) else c.drawRect(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, p) }
            is LineP -> { p.color = prim.color; p.strokeWidth = prim.width; c.drawLine(prim.x0, prim.y0, prim.x1, prim.y1, p) }
            is Oval -> { p.color = prim.color; if (prim.stroke > 0f) { p.style = Paint.Style.STROKE; p.strokeWidth = prim.stroke }; c.drawOval(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, p) }
            is Poly -> { p.color = prim.color; val path = Path(); path.moveTo(prim.pts[0], prim.pts[1]); var i = 2
                while (i < prim.pts.size) { path.lineTo(prim.pts[i], prim.pts[i + 1]); i += 2 }; path.close(); c.drawPath(path, p) }
            is Grad -> { p.shader = if (prim.vertical) LinearGradient(prim.x, prim.y, prim.x, prim.y + prim.h, prim.c0, prim.c1, Shader.TileMode.CLAMP)
                else LinearGradient(prim.x, prim.y, prim.x + prim.w, prim.y, prim.c0, prim.c1, Shader.TileMode.CLAMP)
                c.drawRect(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, p) }
            is Shadow -> { p.color = prim.color; p.maskFilter = BlurMaskFilter(prim.blur.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                c.drawRoundRect(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, prim.radius, prim.radius, p) }
            is Noise -> {
                // paper grain: a tiled grey texture; half of it lightens, half darkens
                p.shader = BitmapShader(noiseTile, Shader.TileMode.REPEAT, Shader.TileMode.MIRROR)
                p.alpha = (prim.amount * 255f * 1.6f).toInt().coerceIn(0, 255)
                p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.OVERLAY)
                c.drawRect(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h, p) }
            is PhotoP -> {
                val src = Rect(Math.round(prim.sx * ps), Math.round(prim.sy * ps), Math.round((prim.sx + prim.sw) * ps).coerceAtMost(photo.width), Math.round((prim.sy + prim.sh) * ps).coerceAtMost(photo.height))
                val dst = RectF(prim.x, prim.y, prim.x + prim.w, prim.y + prim.h)
                p.alpha = (prim.alpha * 255).toInt()
                if (prim.blur > 0f) {
                    val tiny = Bitmap.createBitmap(photo, src.left, src.top, src.width().coerceAtLeast(1), src.height().coerceAtLeast(1))
                    val small = Bitmap.createScaledBitmap(tiny, 40, (40f * src.height() / src.width().coerceAtLeast(1)).toInt().coerceAtLeast(1), true)
                    c.drawBitmap(small, null, dst, p); if (tiny !== photo) tiny.recycle(); small.recycle()
                } else {
                    val save = c.save()
                    if (prim.radius > 0f) c.clipPath(Path().apply { addRoundRect(dst, prim.radius, prim.radius, Path.Direction.CW) })
                    // same size in and out: a copy, no resampling
                    p.isFilterBitmap = !(exact && src.width() == Math.round(prim.w) && src.height() == Math.round(prim.h))
                    c.drawBitmap(photo, src, dst, p); c.restoreToCount(save)
                } }
            is TextP -> { p.typeface = face(prim); p.textSize = prim.size; p.letterSpacing = prim.spacing; p.color = prim.color
                p.textAlign = when (prim.align) { -1 -> Paint.Align.LEFT; 0 -> Paint.Align.CENTER; else -> Paint.Align.RIGHT }
                val save = c.save(); c.translate(prim.x, prim.y); if (prim.rotation != 0f) c.rotate(prim.rotation)
                if (prim.bg != 0) { val w = p.measureText(prim.text); val x = when (prim.align) { -1 -> 0f; 0 -> -w / 2; else -> -w }
                    val bgp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = prim.bg }
                    c.drawRoundRect(x - prim.pad, -prim.size - prim.pad * 0.6f, x + w + prim.pad, prim.size * 0.25f + prim.pad * 0.6f, prim.pad, prim.pad, bgp) }
                c.drawText(prim.text, 0f, 0f, p); c.restoreToCount(save) }
        }
    }
}
