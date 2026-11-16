package com.hikari.studio

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import javax.imageio.ImageIO

/** Draws a Scene with java.awt - the test-side twin of the app's android.graphics renderer. */
object Awt {
    val root: File by lazy { var d: File? = File(System.getProperty("hikari.root") ?: ".").absoluteFile
        while (d != null && !File(d, "engine/src/main/assets").exists()) d = d.parentFile; d ?: error("repository root not found") }
    fun sample(id: String): BufferedImage {
        val dir = File(root, "app/src/main/assets/samples"); val f = dir.listFiles()!!.first { it.name.substringAfter('_').substringBefore('.') == id }
        val src = ImageIO.read(f); val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB); out.createGraphics().apply { drawImage(src, 0, 0, null); dispose() }; return out
    }
    fun palette(img: BufferedImage): IntArray {
        // five colours from dark to light: mean colour of each fifth of the pixels ordered by luma
        val n = 48; val px = ArrayList<Int>()
        for (y in 0 until n) for (x in 0 until n) px += img.getRGB(x * img.width / n, y * img.height / n)
        px.sortBy { (it shr 16 and 255) * 3 + (it shr 8 and 255) * 6 + (it and 255) }
        return IntArray(5) { k -> val part = px.subList(k * px.size / 5, (k + 1) * px.size / 5)
            (0xFF shl 24) or (part.sumOf { it shr 16 and 255 } / part.size shl 16) or (part.sumOf { it shr 8 and 255 } / part.size shl 8) or (part.sumOf { it and 255 } / part.size) }
    }
    private fun col(c: Int) = Color(c, true)
    private fun font(p: TextP, s: Float): Font {
        val fam = when (p.font) { "serif" -> Font.SERIF; "mono" -> Font.MONOSPACED; "hand", "casual" -> Font.SANS_SERIF; else -> Font.SANS_SERIF }
        var f = Font(fam, (if (p.weight >= 600) Font.BOLD else Font.PLAIN) or (if (p.italic || p.font == "hand") Font.ITALIC else 0), 10).deriveFont(p.size * s)
        val at = HashMap<TextAttribute, Any>(); at[TextAttribute.TRACKING] = p.spacing + if (p.font == "condensed") -0.04f else 0f
        f = f.deriveFont(at); return f
    }
    fun render(sc: Scene, photo: BufferedImage, scale: Float = 1f): BufferedImage {
        val W = Math.round(sc.w * scale).coerceAtLeast(1); val H = Math.round(sc.h * scale).coerceAtLeast(1)
        val out = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.scale(scale.toDouble(), scale.toDouble())
        for (p in sc.prims) draw(g, p, photo, out, scale)
        g.dispose(); return out
    }
    private fun draw(g: Graphics2D, p: Prim, photo: BufferedImage, out: BufferedImage, scale: Float) {
        when (p) {
            is Fill -> { g.color = col(p.color); if (p.radius > 0f) g.fill(RoundRectangle2D.Float(p.x, p.y, p.w, p.h, p.radius * 2, p.radius * 2)) else g.fill(java.awt.geom.Rectangle2D.Float(p.x, p.y, p.w, p.h)) }
            is Stroke -> { g.color = col(p.color); g.stroke = BasicStroke(p.width)
                if (p.radius > 0f) g.draw(RoundRectangle2D.Float(p.x, p.y, p.w, p.h, p.radius * 2, p.radius * 2)) else g.draw(java.awt.geom.Rectangle2D.Float(p.x, p.y, p.w, p.h)) }
            is LineP -> { g.color = col(p.color); g.stroke = BasicStroke(p.width); g.draw(java.awt.geom.Line2D.Float(p.x0, p.y0, p.x1, p.y1)) }
            is Oval -> { g.color = col(p.color); val e = Ellipse2D.Float(p.x, p.y, p.w, p.h); if (p.stroke > 0f) { g.stroke = BasicStroke(p.stroke); g.draw(e) } else g.fill(e) }
            is Poly -> { g.color = col(p.color); val path = Path2D.Float(); path.moveTo(p.pts[0], p.pts[1]); var i = 2; while (i < p.pts.size) { path.lineTo(p.pts[i], p.pts[i + 1]); i += 2 }; path.closePath(); g.fill(path) }
            is Grad -> { g.paint = if (p.vertical) GradientPaint(p.x, p.y, col(p.c0), p.x, p.y + p.h, col(p.c1)) else GradientPaint(p.x, p.y, col(p.c0), p.x + p.w, p.y, col(p.c1))
                g.fill(java.awt.geom.Rectangle2D.Float(p.x, p.y, p.w, p.h)) }
            is Shadow -> { val steps = 8; for (k in steps downTo 1) { val grow = p.blur * k / steps; g.color = withAlpha(p.color, 1f / steps * 0.9f).let { col(it) }
                g.fill(RoundRectangle2D.Float(p.x - grow, p.y - grow, p.w + 2 * grow, p.h + 2 * grow, (p.radius + grow) * 2, (p.radius + grow) * 2)) } }
            is Noise -> { val r = java.util.Random(p.seed.toLong()); val x0 = (p.x * scale).toInt().coerceAtLeast(0); val y0 = (p.y * scale).toInt().coerceAtLeast(0)
                val x1 = ((p.x + p.w) * scale).toInt().coerceAtMost(out.width); val y1 = ((p.y + p.h) * scale).toInt().coerceAtMost(out.height)
                for (y in y0 until y1) for (x in x0 until x1) { val d = ((r.nextFloat() - 0.5f) * 2f * p.amount * 255f).toInt(); val c = out.getRGB(x, y)
                    out.setRGB(x, y, (0xFF shl 24) or (((c shr 16 and 255) + d).coerceIn(0, 255) shl 16) or (((c shr 8 and 255) + d).coerceIn(0, 255) shl 8) or ((c and 255) + d).coerceIn(0, 255)) } }
            is PhotoP -> {
                val sx = p.sx.toInt().coerceIn(0, photo.width - 1); val sy = p.sy.toInt().coerceIn(0, photo.height - 1)
                var src = photo.getSubimage(sx, sy, Math.round(p.sw).coerceIn(1, photo.width - sx), Math.round(p.sh).coerceIn(1, photo.height - sy))
                if (p.blur > 0f) { val small = BufferedImage(48, (48 * p.sh / p.sw).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
                    small.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(src, 0, 0, small.width, small.height, null); dispose() }
                    src = ConvolveOp(Kernel(3, 3, FloatArray(9) { 1f / 9 }), ConvolveOp.EDGE_NO_OP, null).filter(small, null) }
                val clip = g.clip; if (p.radius > 0f) g.clip(RoundRectangle2D.Float(p.x, p.y, p.w, p.h, p.radius * 2, p.radius * 2))
                val comp = g.composite; if (p.alpha < 1f) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha)
                g.drawImage(src, java.awt.geom.AffineTransform(p.w / src.width.toDouble(), 0.0, 0.0, p.h / src.height.toDouble(), p.x.toDouble(), p.y.toDouble()), null)
                g.composite = comp; g.clip = clip }
            is TextP -> { val f = font(p, 1f); g.font = f; val w = g.fontMetrics.stringWidth(p.text).toFloat()
                val x = when (p.align) { -1 -> 0f; 0 -> -w / 2; else -> -w }
                val t = g.transform; g.translate(p.x.toDouble(), p.y.toDouble()); if (p.rotation != 0f) g.rotate(Math.toRadians(p.rotation.toDouble()))
                if (p.bg != 0) { g.color = col(p.bg); g.fill(RoundRectangle2D.Float(x - p.pad, -p.size - p.pad * 0.6f, w + 2 * p.pad, p.size * 1.25f + p.pad * 1.2f, p.pad, p.pad)) }
                g.color = col(p.color); g.drawString(p.text, x, 0f); g.transform = t }
        }
    }
    fun save(img: BufferedImage, f: File) { f.parentFile.mkdirs(); ImageIO.write(img, if (f.name.endsWith(".png")) "png" else "jpg", f) }
}
