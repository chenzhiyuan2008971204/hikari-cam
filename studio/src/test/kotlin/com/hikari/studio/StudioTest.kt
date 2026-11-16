package com.hikari.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.math.abs

class StudioTest {
    private val t = LocalDateTime.of(2026, 11, 16, 18, 5, 9)
    private val meta = Meta(t, "f/1.8 · 1/120s · ISO 100 · 23mm", "Hikari", "Taipei", number = 12)
    private val sizes = listOf(3000 to 2000, 4000 to 3000, 2000 to 2000, 2400 to 3000, 2160 to 3840, 3840 to 2160)   // 3:2 4:3 1:1 4:5 9:16 16:9

    // ---------------------------------------------------------------- timestamp
    @Test fun everyDateFormatWritesTheRightDate() {
        val want = mapOf("ymd_dot" to "2026.11.16", "ymd_slash" to "2026/11/16", "yymd_dot" to "26.11.16", "mdy_slash" to "11/16/2026", "dmy_dot" to "16.11.2026",
            "ymd_dash" to "2026-11-16", "hm" to "18:05", "hms" to "18:05:09", "ymd_dot_hm" to "2026.11.16 18:05", "ymd_slash_hms" to "2026/11/16 18:05:09",
            "retro_yy_m_d" to "'26 11 16", "retro_d_m_yy" to "16 11 '26", "mon_d_y" to "NOV 16 2026", "d_mon_y" to "16 NOV 2026", "y_m_d_space" to "2026 11 16")
        assertEquals(want.keys, DateFormats.all.map { it.id }.toSet())
        for ((id, s) in want) assertEquals(id, s, DateFormats.format(id, t))
        assertEquals("2026.11.16 18:05:09", DateFormats.format("ymd_dot_hm", t, seconds = true))
        // single-digit months and days are padded
        assertEquals("2027.01.05", DateFormats.format("ymd_dot", LocalDateTime.of(2027, 1, 5, 3, 4, 5)))
    }

    @Test fun exifTimeIsParsedAndBadValuesAreRejected() {
        assertEquals(t, Times.parseExif("2026:11:16 18:05:09"))
        assertEquals(t, Times.parseExif("2026-11-16T18:05:09+08:00"))
        for (bad in listOf(null, "", "0000:00:00 00:00:00", "2026:13:40 99:99:99", "not a date")) assertNull("$bad", Times.parseExif(bad))
    }

    @Test fun theChosenTimeSourceIsUsedAndNeverSilentlyReplaced() {
        val now = LocalDateTime.of(2030, 1, 1, 0, 0); val created = LocalDateTime.of(2026, 11, 20, 9, 0)
        val full = PhotoTimes(t, created, now); val noExif = PhotoTimes(null, created, now)
        assertEquals(t, Times.resolve(TimeSource.EXIF, full, null).time)
        assertEquals(created, Times.resolve(TimeSource.CREATED, full, null).time)
        assertEquals(now, Times.resolve(TimeSource.NOW, full, null).time)
        val custom = LocalDateTime.of(1999, 12, 31, 23, 59)
        assertEquals(custom, Times.resolve(TimeSource.CUSTOM, full, custom).time)
        // no EXIF: nothing is shown and the user is told - the current time must not be used instead
        val r = Times.resolve(TimeSource.EXIF, noExif, null)
        assertNull(r.time); assertNotNull(r.needsChoice)
        assertNull(Stamps.text(StampSpec(enabled = true), r.time))
        assertNull(Times.resolve(TimeSource.CUSTOM, full, null).time)
    }

    @Test fun stampContentVariants() {
        val s = StampSpec(enabled = true, format = "ymd_dot")
        assertEquals("2026.11.16", Stamps.text(s, t))
        assertEquals("hello", Stamps.text(s.copy(content = StampContent.TEXT, text = " hello "), null))
        assertEquals("2026.11.16  Kyoto trip", Stamps.text(s.copy(content = StampContent.DATE_TEXT, text = "Kyoto trip"), t))
        assertEquals("Taipei  2026.11.16", Stamps.text(s.copy(content = StampContent.PLACE_DATE, place = "Taipei"), t))
        assertNull(Stamps.text(s.copy(content = StampContent.TEXT, text = "  "), t))
        assertEquals(s.copy(text = "a|b\\c", place = "台北"), StampSpec.decode(s.copy(text = "a|b\\c", place = "台北").encode()))
        assertEquals(10, StampStyles.all.size)
    }

    @Test fun stampLandsWhereItWasPlaced() {
        val photo = R(100f, 50f, 3000f, 2000f); val m = 0.045f * 2000f
        for (pos in StampPos.values()) for (style in StampStyles.all) {
            val s = style.apply(StampSpec(enabled = true)).copy(pos = pos, x = 0.25f, y = 0.4f)
            val (x, y, align) = Stamps.anchor(s, photo)
            when (pos) {
                StampPos.BL -> { assertEquals(100f + m, x, 0.5f); assertEquals(2050f - m, y, 0.5f); assertEquals(-1, align) }
                StampPos.BR -> { assertEquals(3100f - m, x, 0.5f); assertEquals(2050f - m, y, 0.5f); assertEquals(1, align) }
                StampPos.BC -> { assertEquals(1600f, x, 0.5f); assertEquals(0, align) }
                StampPos.TL -> { assertEquals(100f + m, x, 0.5f); assertTrue(y < 50f + 2000f * 0.2f) }
                StampPos.TR -> { assertEquals(3100f - m, x, 0.5f); assertTrue(y < 50f + 2000f * 0.2f) }
                StampPos.CUSTOM -> { assertEquals(100f + 750f, x, 0.5f); assertEquals(50f + 800f, y, 0.5f) }
            }
            // everything drawn stays inside the photo
            for (p in Stamps.prims(s, Stamps.text(s, t, meta.info, 12), photo)) when (p) {
                is Poly -> for (i in p.pts.indices) { val v = p.pts[i]; if (i % 2 == 0) assertTrue("$pos ${style.id} x $v", v in 100f..3100f) else assertTrue("$pos ${style.id} y $v", v in 50f..2050f) }
                is Fill -> assertTrue(p.x >= 100f && p.x + p.w <= 3100f && p.y >= 50f && p.y + p.h <= 2050f)
                is TextP -> assertTrue(p.x in 100f..3100f && p.y in 50f..2050f)
                else -> {}
            }
        }
        assertTrue(Stamps.prims(StampSpec(enabled = false), "x", photo).isEmpty())
    }

    @Test fun sevenSegmentWidthMatchesItsPolygons() {
        val s = "'26 11 16"; val polys = SevenSeg.polys(s, 0f, 100f, 100f, WHITE, 0.06f)
        val maxX = polys.maxOf { when (it) { is Poly -> it.pts.filterIndexed { i, _ -> i % 2 == 0 }.maxOrNull() ?: 0f; is Fill -> it.x + it.w; else -> 0f } }
        assertTrue(maxX <= SevenSeg.width(s, 100f, 0.06f) + 1f)
        assertTrue(SevenSeg.supports("2026/11/16 18:05")); assertTrue(!SevenSeg.supports("NOV 16"))
    }

    // ---------------------------------------------------------------- frames
    @Test fun libraryIsLargeUniqueAndCoversEveryCategory() {
        assertTrue("only ${Frames.all.size} frames", Frames.all.size >= 80)
        assertEquals(Frames.all.size, Frames.all.map { it.id }.toSet().size)
        assertEquals(Frames.all.size, Frames.all.map { it.name }.toSet().size)
        for (c in FrameCat.values()) assertTrue("$c has ${Frames.inCategory(c).size}", Frames.inCategory(c).size >= 10)
        // structure, not colour: no two frames have the same margins + the same kinds and counts of elements
        fun sig(f: FrameDef): String { val sc = FrameEngine.scene(f, FrameOptions(f.id), 3000, 2000, meta)
            val kinds = sc.prims.groupingBy { it::class.simpleName ?: "" }.eachCount().toSortedMap()
            return "%.3f %.3f %.3f %.3f %.3f %s".format(sc.photo.x / sc.w, sc.photo.y / sc.h, sc.photo.w / sc.w, sc.photo.h / sc.h, f.photoAspect, kinds) }
        val groups = Frames.all.groupBy { sig(it) }.filter { it.value.size > 1 }
        assertTrue("frames that differ only in colour: " + groups.values.map { g -> g.map { it.id } }, groups.isEmpty())
    }

    @Test fun everyFrameFitsEveryAspectRatioWithoutStretching() {
        var n = 0
        for (f in Frames.all) for ((w, h) in sizes) for (ratio in listOf(0f, 1f, 0.8f, 1.5f, 16f / 9f, 9f / 16f)) for (mode in FitMode.values()) for (thick in listOf(0.5f, 1f, 2f)) {
            val o = FrameOptions(f.id, thickness = thick, ratio = ratio, mode = mode)
            val sc = FrameEngine.scene(f, o, w, h, meta, StampStyles.get("digital_orange").apply(StampSpec(enabled = true)), "'26 11 16")
            val tag = "${f.id} ${w}x$h ratio $ratio $mode x$thick"
            val ph = sc.prims.filterIsInstance<PhotoP>().last { it.blur == 0f }
            // 1:1 pixels: the photo is drawn at its own size, from a source rectangle of the same shape
            assertEquals(tag, ph.sw, ph.w, 0.51f); assertEquals(tag, ph.sh, ph.h, 0.51f)
            assertTrue(tag, ph.sx >= -0.5f && ph.sy >= -0.5f && ph.sx + ph.sw <= w + 0.5f && ph.sy + ph.sh <= h + 0.5f)
            assertTrue("$tag photo outside canvas", ph.x >= -0.5f && ph.y >= -0.5f && ph.x + ph.w <= sc.w + 1f && ph.y + ph.h <= sc.h + 1f)
            if (ratio > 0f) assertEquals("$tag canvas ratio", ratio, sc.w.toFloat() / sc.h, ratio * 0.01f)
            // Fit keeps the whole photo (or exactly the frame's own crop); only Fill may cut more
            if (mode == FitMode.FIT) { if (f.photoAspect == 0f) { assertEquals(tag, w.toFloat(), ph.sw, 0.51f); assertEquals(tag, h.toFloat(), ph.sh, 0.51f) }
                else assertEquals(tag, f.photoAspect, ph.sw / ph.sh, f.photoAspect * 0.01f) }
            assertTrue("$tag canvas ${sc.w}x${sc.h}", sc.w in 1..40000 && sc.h in 1..40000)
            // the stamp stays on the photo
            for (p in sc.prims.filterIsInstance<Poly>().filter { it.color == withAlpha(0xFFFF8A1E.toInt(), 0.95f) })
                for (i in p.pts.indices) if (i % 2 == 0) assertTrue(tag, p.pts[i] in ph.x - 1..ph.x + ph.w + 1) else assertTrue(tag, p.pts[i] in ph.y - 1..ph.y + ph.h + 1)
            n++
        }
        assertTrue(n > 15000)
    }

    @Test fun optionsChangeTheFrameAndCanTurnItsTextOff() {
        val f = Frames.get("film35")!!
        val a = FrameEngine.scene(f, FrameOptions(f.id), 3000, 2000, meta); val b = FrameEngine.scene(f, FrameOptions(f.id, thickness = 2f), 3000, 2000, meta)
        assertTrue(b.w > a.w && b.h > a.h)
        fun texts(sc: Scene) = sc.prims.filterIsInstance<TextP>().map { it.text }
        assertTrue(texts(a).any { "2026" in it })
        assertTrue(texts(FrameEngine.scene(f, FrameOptions(f.id, showDate = false), 3000, 2000, meta)).none { "2026" in it })
        // no date known: nothing is invented
        assertTrue(texts(FrameEngine.scene(f, FrameOptions(f.id), 3000, 2000, Meta())).none { Regex("20\\d\\d").containsMatchIn(it) })
        val cap = FrameEngine.scene(Frames.get("instant_caption"), FrameOptions("instant_caption", caption = "我的字"), 3000, 2000, meta)
        assertTrue(texts(cap).contains("我的字"))
        val col = FrameEngine.scene(Frames.get("min_white"), FrameOptions("min_white", color = 0xFF112233.toInt()), 3000, 2000, meta)
        assertEquals(0xFF112233.toInt(), (col.prims.first() as Fill).color)
        val o = FrameOptions("jp_note", 1.3f, 5, 1.5f, FitMode.FILL, 0.8f, "t|x", "c\\y", false, true, 0.3f, -0.2f)
        assertEquals(o, FrameOptions.decode(o.encode()))
        // user crop of the photo
        val sq = FrameEngine.scene(null, FrameOptions(photoAspect = 1f), 3000, 2000, meta)
        assertEquals(2000, sq.w); assertEquals(2000, sq.h); assertEquals(500f, sq.crop.x, 0.5f)
    }

    @Test fun searchAndCategories() {
        assertTrue(Frames.search("拍立得").size >= 5)
        assertTrue(Frames.search("35mm 齒孔").all { it.cat == FrameCat.FILM })
        assertEquals(Frames.all.size, Frames.search("  ").size)
        assertTrue(Frames.search("zzzz").isEmpty())
        assertEquals(Frames.all.size, FrameCat.values().sumOf { Frames.inCategory(it).size })
    }

    // ---------------------------------------------------------------- real output on fixed photos
    @Test fun renderedOutputKeepsThePhotoPixelForPixelAndMatchesTheRecord() {
        val photo = Awt.sample("seaside"); val pal = Awt.palette(photo); val m = Meta(t, meta.info, "Hikari", "Taipei", pal, 12)
        val stamp = StampStyles.get("digital_orange").apply(StampSpec(enabled = true)); val st = Stamps.text(stamp, t)
        val record = File(Awt.root, "studio/src/test/resources/layout_golden.txt"); val sb = StringBuilder()
        var checked = 0
        for (f in Frames.all) {
            val sc = FrameEngine.scene(f, FrameOptions(f.id), photo.width, photo.height, m, stamp, st)
            val out = Awt.render(sc, photo)
            assertEquals(sc.w, out.width); assertEquals(sc.h, out.height)
            // photo pixels inside the window are the source pixels, unscaled and unshifted (away from stamp and overlays)
            val ph = sc.prims.filterIsInstance<PhotoP>().last { it.blur == 0f }
            val overlays = sc.prims.dropWhile { it !== ph }.drop(1)
            if (overlays.all { it is Poly || it is Fill && it.h < photo.height / 4 } || f.cat != FrameCat.CINEMATIC && f.id != "mag_cover" && f.id != "inset_line") {
                for ((fx, fy) in listOf(0.3f to 0.3f, 0.5f to 0.5f, 0.2f to 0.6f)) {
                    val dx = (ph.w * fx).toInt(); val dy = (ph.h * fy).toInt()
                    val a = out.getRGB(Math.round(ph.x) + dx, Math.round(ph.y) + dy); val b = photo.getRGB(Math.round(ph.sx) + dx, Math.round(ph.sy) + dy)
                    val d = maxOf(abs((a shr 16 and 255) - (b shr 16 and 255)), abs((a shr 8 and 255) - (b shr 8 and 255)), abs((a and 255) - (b and 255)))
                    assertTrue("${f.id}: photo pixel changed by $d at $fx,$fy", d <= 2)
                }
                checked++
            }
            sb.append(f.id).append(' ').append(sc.w).append('x').append(sc.h).append(" photo ").append("%.1f,%.1f,%.1f,%.1f".format(sc.photo.x, sc.photo.y, sc.photo.w, sc.photo.h))
                .append(" prims ").append(sc.prims.size).append(" texts ").append(sc.prims.filterIsInstance<TextP>().joinToString("|") { it.text }).append('\n')
        }
        assertTrue(checked >= 70)
        // layout record: any unintended change to a frame shows up as a diff here
        if (System.getProperty("studio.update") == "true" || !record.exists()) { record.parentFile.mkdirs(); record.writeText(sb.toString()) }
        assertEquals("frame layouts changed; if intended, re-record with -Dstudio.update=true", record.readText(), sb.toString())
    }

    @Test fun highResolutionExportAndTiming() {
        // a 24 MP picture through a frame + stamp at full size
        val small = Awt.sample("sunny"); val big = java.awt.image.BufferedImage(6000, 4000, java.awt.image.BufferedImage.TYPE_INT_RGB)
        big.createGraphics().apply { drawImage(small, 0, 0, 6000, 4000, null); dispose() }
        val stamp = StampStyles.get("led_red").apply(StampSpec(enabled = true))
        val rep = StringBuilder("studio performance (JVM, java.awt backend - not a phone)\n")
        for (id in listOf("film35", "instant_classic", "blur_bg", "paper_texture")) {
            val t0 = System.nanoTime()
            val sc = FrameEngine.scene(Frames.get(id), FrameOptions(id), 6000, 4000, meta, stamp, Stamps.text(stamp, t))
            val layoutMs = (System.nanoTime() - t0) / 1e6
            val out = Awt.render(sc, big); val ms = (System.nanoTime() - t0) / 1e6
            assertTrue(out.width >= 6000 && out.height >= 4000)
            val ph = sc.photo
            assertEquals(big.getRGB(3000, 2000) and 0xFFFFFF, out.getRGB(Math.round(ph.x) + 3000, Math.round(ph.y) + 2000) and 0xFFFFFF)
            rep.append("  $id: canvas ${sc.w}x${sc.h} (${"%.1f".format(sc.w * sc.h / 1e6)} MP), layout ${"%.2f".format(layoutMs)} ms, layout + render ${"%.0f".format(ms)} ms\n")
        }
        var t0 = System.nanoTime(); var n = 0
        repeat(20) { for (f in Frames.all) { FrameEngine.scene(f, FrameOptions(f.id), 4000, 3000, meta); n++ } }
        rep.append("  layout only: ${"%.3f".format((System.nanoTime() - t0) / 1e6 / n)} ms per frame over $n layouts\n")
        File("build/reports").mkdirs(); File("build/reports/studio_perf.txt").writeText(rep.toString())
    }

    /** Preview sheets for people: every frame on the photo that suits it, and every stamp style. */
    @Test fun writePreviewSheets() {
        val dir = File("build/reports/frames").apply { mkdirs() }
        val stampOn = setOf("film35", "retro_print", "instant_classic", "jp_print", "cine_bars", "min_white")
        for (cat in FrameCat.values()) {
            val tiles = Frames.inCategory(cat).map { f ->
                val photo = Awt.sample(f.demo); val m = Meta(t, meta.info, "Hikari", "Taipei", Awt.palette(photo), 12)
                val stamp = if (f.id in stampOn) StampStyles.get(if (f.cat == FrameCat.CINEMATIC) "cinema" else "digital_orange").apply(StampSpec(enabled = true)) else null
                val sc = FrameEngine.scene(f, FrameOptions(f.id), photo.width, photo.height, m, stamp, stamp?.let { Stamps.text(it, t) })
                "${f.zh}  ${f.name}" to Awt.render(sc, photo, 520f / maxOf(sc.w, sc.h))
            }
            Awt.save(sheet("${cat.zh}  ·  ${tiles.size} 款", tiles), File(dir, "frames_${cat.name.lowercase()}.jpg"))
        }
        val photo = Awt.sample("beach")
        val tiles = StampStyles.all.map { st -> val s = st.apply(StampSpec(enabled = true)).copy(size = 0.05f)
            val sc = FrameEngine.scene(null, FrameOptions(), photo.width, photo.height, meta, s, Stamps.text(s, t, meta.info, 12)); st.zh to Awt.render(sc, photo, 520f / photo.width) }
        Awt.save(sheet("時間戳樣式  ·  ${tiles.size} 款", tiles), File(dir, "stamp_styles.jpg"))
    }

    private fun sheet(title: String, tiles: List<Pair<String, java.awt.image.BufferedImage>>, cols: Int = 4): java.awt.image.BufferedImage {
        val cell = 540; val rows = (tiles.size + cols - 1) / cols
        val out = java.awt.image.BufferedImage(cols * cell + 20, rows * (cell + 34) + 60, java.awt.image.BufferedImage.TYPE_INT_RGB); val g = out.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = java.awt.Color(0x5A, 0x5A, 0x5E); g.fillRect(0, 0, out.width, out.height)
        g.color = java.awt.Color.WHITE; g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 24); g.drawString(title, 14, 36)
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 16)
        for ((i, tl) in tiles.withIndex()) { val x = 10 + (i % cols) * cell; val y = 54 + (i / cols) * (cell + 34)
            g.drawImage(tl.second, x + (cell - tl.second.width) / 2, y + (cell - tl.second.height) / 2, null); g.drawString(tl.first, x + 6, y + cell + 22) }
        g.dispose(); return out
    }
}
