package com.hikari.cam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hikari.cam.data.Media
import com.hikari.cam.smart.Samples
import com.hikari.cam.studio.PhotoInfo
import com.hikari.cam.studio.StudioRenderer
import com.hikari.cam.ui.EditorViewModel
import com.hikari.studio.FrameEngine
import com.hikari.studio.FrameOptions
import com.hikari.studio.Frames
import com.hikari.studio.Meta
import com.hikari.studio.PhotoP
import com.hikari.studio.StampPos
import com.hikari.studio.StampSpec
import com.hikari.studio.StampStyles
import com.hikari.studio.Stamps
import com.hikari.studio.TimeSource
import com.hikari.studio.Times
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlin.math.abs

/** Timestamp Studio and Frame Studio on Android itself, with the app's real renderer, on the bundled fixed photos. */
@RunWith(AndroidJUnit4::class)
class StudioDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val machine = "${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT}, " + (if (android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) "EMULATOR" else "PHYSICAL DEVICE") + ")"
    private fun photo(id: String) = Samples.bitmap(ctx, Samples.list(ctx).first { it.id == id })
    private fun diff(a: Int, b: Int) = maxOf(abs((a shr 16 and 255) - (b shr 16 and 255)), abs((a shr 8 and 255) - (b shr 8 and 255)), abs((a and 255) - (b and 255)))
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun exifCaptureTimeIsReadAndItsAbsenceIsReported() {
        val f = File(ctx.cacheDir, "exif_test.jpg")
        f.outputStream().use { photo("sunny").compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // no EXIF yet: the capture time is unknown, and a stamp that wants it shows nothing
        val none = PhotoInfo.read(ctx, Uri.fromFile(f))
        assertNull(none.exifTime)
        val r = Times.resolve(TimeSource.EXIF, none.times(), null)
        assertNull(r.time); assertNotNull(r.needsChoice)
        assertNull(StudioRenderer.scene(768, 512, StampSpec(enabled = true), FrameOptions(), none, IntArray(5))!!.prims.firstOrNull { it !is PhotoP })
        ExifInterface(f).apply { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2019:07:04 08:09:10"); setAttribute(ExifInterface.TAG_F_NUMBER, "1.8")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "200"); setAttribute(ExifInterface.TAG_MODEL, "TestCam"); saveAttributes() }
        val info = PhotoInfo.read(ctx, Uri.fromFile(f))
        assertEquals(LocalDateTime.of(2019, 7, 4, 8, 9, 10), info.exifTime)
        assertTrue(info.info, info.info.contains("f/1.8") && info.info.contains("ISO 200"))
        assertTrue(info.camera.contains("TestCam"))
        // the stamp shows the EXIF time, not today's date
        val s = StampSpec(enabled = true, format = "ymd_dot")
        assertEquals("2019.07.04", Stamps.text(s, Times.resolve(s.source, info.times(), null).time))
        Report.line("studio: EXIF capture time read (2019-07-04 08:09:10), missing EXIF reported without substituting another time")
        Report.flush(ctx, "studio_exif_report.txt")
    }

    @Test
    fun everyFrameRendersAndThePhotoIsCopiedUnchanged() {
        Report.line("studio rendering measured on: $machine")
        val t = LocalDateTime.of(2026, 11, 16, 18, 5, 9)
        val stamp = StampStyles.get("digital_orange").apply(StampSpec(enabled = true)); val text = Stamps.text(stamp, t)
        val cache = HashMap<String, Bitmap>(); var ms = 0.0; var exact = 0
        val problems = ArrayList<String>()
        for (def in Frames.all) {
            val b = cache.getOrPut(def.demo) { photo(def.demo) }
            val meta = Meta(t, "f/1.8 · 1/120s · ISO 100", "Hikari", "", StudioRenderer.palette(b), 12)
            val sc = FrameEngine.scene(def, FrameOptions(def.id), b.width, b.height, meta, stamp, text)
            val t0 = System.nanoTime(); val out = StudioRenderer.render(sc, b); ms += (System.nanoTime() - t0) / 1e6
            if (out.width != sc.w || out.height != sc.h) problems += "${def.id}: output ${out.width}x${out.height}, scene ${sc.w}x${sc.h}"
            val ph = sc.prims.filterIsInstance<PhotoP>().last { it.blur == 0f }
            // overlays on the photo (viewfinder, cover type, lower third) are allowed to cover it; elsewhere the pixels are the photo's
            val covered = def.cat == com.hikari.studio.FrameCat.CINEMATIC || def.id in setOf("mag_cover", "inset_line")
            if (!covered) {
                for ((fx, fy) in listOf(0.3f to 0.3f, 0.5f to 0.5f, 0.2f to 0.6f)) {
                    val dx = (ph.w * fx).toInt(); val dy = (ph.h * fy).toInt()
                    val d = diff(out.getPixel(Math.round(ph.x) + dx, Math.round(ph.y) + dy), b.getPixel(Math.round(ph.sx) + dx, Math.round(ph.sy) + dy))
                    if (d > 2) problems += "${def.id}: photo pixel changed by $d at $fx,$fy"
                }
                exact++
            }
            if (def.id in setOf("film35", "instant_classic", "jp_magazine", "cine_slate", "mag_cover", "journal", "blur_bg", "postage")) Report.jpeg(ctx, "frame_${def.id}.jpg", out)
            out.recycle()
        }
        Report.line("studio: ${Frames.all.size} frames rendered with a stamp at full sample size, ${"%.0f".format(ms / Frames.all.size)} ms each on average; photo copied pixel-exact in $exact frames")
        // stamp position on the real renderer: orange in the chosen corner only
        val b = photo("beach")
        for (pos in listOf(StampPos.BR, StampPos.TL)) {
            val s = stamp.copy(pos = pos, size = 0.06f)
            val out = StudioRenderer.render(FrameEngine.scene(null, FrameOptions(), b.width, b.height, Meta(t), s, Stamps.text(s, t)), b)
            fun orange(x0: Int, y0: Int, x1: Int, y1: Int): Int { var n = 0; for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) { val c = out.getPixel(x, y)
                if ((c shr 16 and 255) > 230 && (c shr 8 and 255) in 110..165 && (c and 255) < 70) n++ }; return n }
            val br = orange(out.width / 2, out.height * 3 / 4, out.width, out.height); val tl = orange(0, 0, out.width / 2, out.height / 4)
            if (pos == StampPos.BR && !(br > 50 && tl == 0)) problems += "stamp BR: orange pixels bottom-right $br, top-left $tl"
            if (pos == StampPos.TL && !(tl > 50 && br == 0)) problems += "stamp TL: orange pixels top-left $tl, bottom-right $br"
            if (pos == StampPos.BR) Report.jpeg(ctx, "stamp_br.jpg", out)
            out.recycle()
        }
        // every ratio on a few frames
        for (id in listOf("film35", "instant_classic", "cine_bars")) for (ratio in listOf(1f, 0.8f, 1.5f, 16f / 9f, 9f / 16f)) for (mode in com.hikari.studio.FitMode.values()) {
            val sc = FrameEngine.scene(Frames.get(id), FrameOptions(id, ratio = ratio, mode = mode), b.width, b.height, Meta(t))
            val out = StudioRenderer.render(sc, b)
            if (abs(out.width.toFloat() / out.height - ratio) > ratio * 0.01f) problems += "$id ratio $ratio $mode: got ${out.width}x${out.height}"
            out.recycle()
        }
        // a large export: 24 MP photo in a frame
        val big = Bitmap.createScaledBitmap(b, 6000, 4000, true)
        val rt = Runtime.getRuntime(); val before = android.os.Debug.getNativeHeapAllocatedSize()
        val sc = FrameEngine.scene(Frames.get("instant_classic"), FrameOptions("instant_classic"), 6000, 4000, Meta(t), stamp, text)
        val t0 = System.nanoTime(); val out = StudioRenderer.render(sc, big); val bigMs = (System.nanoTime() - t0) / 1e6
        if (out.width != sc.w || out.height != sc.h || diff(out.getPixel(Math.round(sc.photo.x) + 3000, Math.round(sc.photo.y) + 2000), big.getPixel(3000, 2000)) > 2) problems += "24 MP export wrong"
        Report.line("studio: 24 MP photo -> ${sc.w}x${sc.h} (${"%.1f".format(sc.w * sc.h / 1e6)} MP) in ${"%.0f".format(bigMs)} ms; native heap +${(android.os.Debug.getNativeHeapAllocatedSize() - before) / 1024 / 1024} MB while both bitmaps live, java heap ${(rt.totalMemory() - rt.freeMemory()) / 1024 / 1024} MB")
        out.recycle(); big.recycle()
        for (p in problems) Report.line("PROBLEM $p")
        Report.flush(ctx, "studio_report.txt")
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    /** The editor: stamp + frame are stored with the edit, exported at full size, and the original file is not touched. */
    @Test
    fun editorExportsStampAndFrameAndLeavesTheOriginalAlone() {
        val sample = Samples.list(ctx).first { it.id == "seaside" }
        val uri = runBlocking { Media.saveJpeg(ctx, Samples.bitmap(ctx, sample), 95, "_studio_src") }
        fun bytes() = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val before = sha(bytes())
        fun poll(timeout: Long, what: () -> Boolean): Boolean { val end = System.currentTimeMillis() + timeout
            while (System.currentTimeMillis() < end) { if (what()) return true; Thread.sleep(300) }; return false }
        lateinit var vm: EditorViewModel
        inst.runOnMainSync { vm = EditorViewModel(ctx.applicationContext as android.app.Application) }
        inst.runOnMainSync { vm.open(uri, null) }
        assertTrue("preview not rendered", poll(120_000) { vm.preview != null && vm.info != null })
        // the saved sample has no EXIF capture time: a date stamp must wait for the user's choice
        inst.runOnMainSync { vm.updateStamp(StampStyles.get("digital_orange").apply(StampSpec(enabled = true))) }
        assertTrue("no warning about the missing capture time", poll(20_000) { vm.stampWarning != null })
        Report.line("studio editor: missing EXIF time -> warning shown: ${vm.stampWarning}")
        inst.runOnMainSync { vm.updateStamp(vm.stamp.copy(source = TimeSource.CUSTOM, custom = "2021-03-04T05:06:07")); vm.updateFrame(FrameOptions("instant_classic")) }
        // the composed preview follows each change; wait for the one that has the frame's taller card
        assertTrue("preview is not framed", poll(30_000) { val b = vm.framed; b != null && vm.stampWarning == null && b.width.toFloat() / b.height < vm.fullW.toFloat() / vm.fullH - 0.05f })
        val pv = vm.framed!!
        Report.jpeg(ctx, "editor_framed_preview.jpg", pv)
        val n0 = runBlocking { Media.list(ctx) }.size
        inst.runOnMainSync { vm.export(95) }
        assertTrue("export did not finish", poll(180_000) { vm.message != null && vm.busy == null })
        val list = runBlocking { Media.list(ctx) }
        val newest = list.first()
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(newest.uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        val want = FrameEngine.scene(Frames.get("instant_classic"), FrameOptions("instant_classic"), vm.fullW, vm.fullH, Meta())
        Report.line("studio editor: export -> ${vm.message}; file ${newest.name} ${o.outWidth}x${o.outHeight}, expected ${want.w}x${want.h}; files $n0 -> ${list.size}")
        assertEquals(want.w, o.outWidth); assertEquals(want.h, o.outHeight)
        assertTrue(newest.name.contains("_studio"))
        assertEquals("the original file changed", before, sha(bytes()))
        // reopening restores the stored stamp and frame (non-destructive, editable again), and both can be removed
        lateinit var vm2: EditorViewModel
        inst.runOnMainSync { vm2 = EditorViewModel(ctx.applicationContext as android.app.Application); vm2.open(uri, null) }
        assertTrue("stored edit not restored", poll(60_000) { vm2.frame.frame == "instant_classic" && vm2.stamp.enabled && vm2.stamp.custom == "2021-03-04T05:06:07" })
        inst.runOnMainSync { vm2.updateStamp(vm2.stamp.copy(enabled = false)); vm2.updateFrame(FrameOptions()) }
        assertTrue("stamp and frame could not be removed", poll(30_000) { vm2.framed == null })
        Report.line("studio editor: original untouched (SHA-256 equal), edit restored on reopen, stamp and frame removable")
        Report.flush(ctx, "studio_editor_report.txt")
    }
}
