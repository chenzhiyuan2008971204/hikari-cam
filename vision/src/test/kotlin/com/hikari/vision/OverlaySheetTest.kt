package com.hikari.vision

import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws what the analyser measured on every standard photo (the same overlays the app shows) and
 * writes the pictures to build/reports/overlays, so a person can check the evidence by eye.
 */
class OverlaySheetTest {
    private fun colour(role: String) = when (role) {
        "thirds" -> Color(255, 255, 255, 90); "horizon" -> Color(0x5C, 0xC8, 0xFF); "vertical" -> Color(0xB4, 0x8C, 0xFF)
        "leading" -> Color(0xFF, 0xB7, 0x4D); "subject" -> Color(0xFF, 0xE0, 0x66); "target" -> Color(0x6B, 0xE3, 0x8A); else -> Color.WHITE
    }

    @Test
    fun writeOverlayPictures() {
        val dir = File("build/reports/overlays").apply { mkdirs() }
        val faces = mapOf("05_portrait.jpg" to Box(0.33f, 0.30f, 0.72f, 0.62f), "10_child.jpg" to Box(0.42f, 0.10f, 0.95f, 0.85f))
        for (file in File(Synth.repo, "app/src/main/assets/samples").listFiles { f -> f.name.endsWith(".jpg") }!!.sortedBy { it.name }) {
            val src = ImageIO.read(file)
            val det = faces[file.name]?.let { listOf(Detection(Detection.Kind.FACE, it)) } ?: emptyList()
            val rep = CompositionAnalyzer.analyze(Synth.photo(file.name), det)
            val scale = 1000.0 / src.width
            val w = 1000
            val h = (src.height * scale).toInt()
            val textH = 30 + 74 * maxOf(1, rep.suggestions.size)
            val out = BufferedImage(w, h + textH, BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(src, 0, 0, w, h, null)
            fun x(v: Float) = (v * w).toInt()
            fun y(v: Float) = (v * h).toInt()
            for (sg in rep.suggestions) {
                sg.crop?.let { c -> g.color = Color.WHITE; g.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 8f), 0f)
                    g.drawRect(x(c.left), y(c.top), x(c.w), y(c.h)) }
                for (o in sg.overlays) when (o) {
                    is Overlay.Seg -> { g.color = colour(o.role)
                        g.stroke = if (o.role == "target") BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(14f, 10f), 0f) else BasicStroke(if (o.role == "thirds") 1.2f else 3.5f)
                        g.drawLine(x(o.x0), y(o.y0), x(o.x1), y(o.y1)) }
                    is Overlay.Rect -> { g.color = colour(o.role); g.stroke = BasicStroke(3f); g.drawRect(x(o.box.left), y(o.box.top), x(o.box.w), y(o.box.h)) }
                    is Overlay.Point -> { g.color = colour(o.role); g.stroke = BasicStroke(3.5f); g.drawOval(x(o.x) - 11, y(o.y) - 11, 22, 22) }
                }
            }
            g.color = Color(18, 18, 18); g.fillRect(0, h, w, textH)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 15); g.color = Color.WHITE
            val sc = rep.findings.scene
            g.drawString("${file.name}   scene: ${sc.top.joinToString { it.name }.ifEmpty { "-" }}   sky ${"%.0f".format(sc.skyFrac * 100)}%  green ${"%.0f".format(sc.greenFrac * 100)}%  faces ${sc.faces}", 10, h + 20)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            var ty = h + 44
            if (rep.suggestions.isEmpty()) { g.color = Color(200, 200, 200); g.drawString(rep.note, 10, ty) }
            for ((i, sg) in rep.suggestions.withIndex()) {
                g.color = Color(0xFF, 0xD5, 0x4F); g.drawString("${i + 1}. ${sg.title}   [${"%.0f".format(sg.confidence * 100)}%]", 10, ty)
                g.color = Color(215, 215, 215); g.drawString("依據：${sg.evidence}", 24, ty + 19); g.drawString("目前：${sg.current}   建議：${sg.action}", 24, ty + 38)
                g.color = Color(160, 160, 160); g.drawString("預期：${sg.benefit}", 24, ty + 57)
                ty += 74
            }
            g.dispose()
            ImageIO.write(out, "jpg", File(dir, file.name))
        }
    }
}
