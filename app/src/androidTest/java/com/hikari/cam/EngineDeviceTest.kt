package com.hikari.cam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hikari.cam.data.Media
import com.hikari.cam.lab.ImageStats
import com.hikari.cam.lab.LabViewModel
import com.hikari.cam.lab.TestChart
import com.hikari.engine.Adjustments
import com.hikari.engine.LookRepository
import com.hikari.engine.gl.OffscreenRenderer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

/**
 * The real engine on a real Android GL stack (emulator in CI): every look renders, preview and
 * full size agree, tiles have no seams, and the decoder's own downscaling is measured.
 */
@RunWith(AndroidJUnit4::class)
class EngineDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = LookRepository(ctx)
    private val renderer = OffscreenRenderer(ctx.assets)

    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1e6
    private fun f(x: Double) = "%.2f".format(x)

    /** a favourite, preset or edit saved under a retired near-duplicate look opens the look that stayed */
    @Test
    fun retiredLookIdsOpenTheirTwin() {
        org.junit.Assert.assertEquals(17, repo.retired.size)
        for ((old, twin) in repo.retired) {
            org.junit.Assert.assertTrue(old, repo.looks.none { it.id == old })
            org.junit.Assert.assertEquals(old, twin, repo.look(old).id)
            org.junit.Assert.assertTrue(old in repo.aliases(twin))
        }
        org.junit.Assert.assertEquals("studio_hi", repo.look("fj_provia").id)
        org.junit.Assert.assertEquals("mellow400", repo.look("mellow400").id)
    }

    @Test
    fun looksRenderAndPreviewMatchesExport() = runBlocking {
        renderer.maxTexture()
        Report.line("GPU ${renderer.gpuName}, max texture ${renderer.maxTextureSize}")
        val full = TestChart.draw(TestChart.FULL_W, TestChart.FULL_H)
        val prevSrc = ImageStats.downscale(full, 2048, 1536)
        val pw = 1600
        val ph = 1200
        var ok = true
        // On the CI emulator (software GPU, about 20 s per look at 12 MP) engineSample=true checks every
        // older look and every third of the newer ones; all looks are still rendered by the golden-image
        // job on the same shaders. Without the argument (a phone) every look is checked here.
        val sample = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("engineSample") == "true"
        val list = if (sample) repo.looks.filterIndexed { i, _ -> i < 27 || i % 3 == 0 } else repo.looks
        Report.line("looks checked: ${list.size} of ${repo.looks.size}" + if (sample) " (emulator sample)" else "")
        for (spec in list) {
            val lut = repo.lut(spec)
            // grain off: grain is randomness per pixel, everything else must match
            val req = OffscreenRenderer.Request(spec, lut, Adjustments(grain = 0f), seed = LabViewModel.SEED)
            var t0 = System.nanoTime()
            val small = renderer.render(prevSrc, req, pw, ph, cacheKey = "prev")
            val prevMs = ms(t0)
            t0 = System.nanoTime()
            val big = renderer.render(full, req, full.width, full.height)
            val fullMs = ms(t0)
            val down = ImageStats.downscale(big, pw, ph)
            val r = ImageStats.compare(small, down)
            Report.line("${spec.id}: preview ${pw}x$ph ${f(prevMs)} ms, full ${full.width}x${full.height} ${f(fullMs)} ms; " +
                "preview vs export mean ${f(r.meanAbs)} p99 ${r.p99} max ${r.max} bias ${f(r.biasR)}/${f(r.biasG)}/${f(r.biasB)} -> ${if (r.pass) "PASS" else "FAIL"}")
            val inLevel = ImageStats.meanLevel(prevSrc)
            val outLevel = ImageStats.meanLevel(small)
            val raw = renderer.render(prevSrc, OffscreenRenderer.Request(spec, lut, Adjustments(strength = 0f, grain = 0f), seed = 1f), pw, ph, cacheKey = "prev")
            val rawLevel = ImageStats.meanLevel(raw)
            raw.recycle()
            val sane = outLevel > inLevel * 0.5 && outLevel < inLevel * 1.6 + 20
            Report.line("${spec.id}: mean level input ${f(inLevel)}, strength 0 ${f(rawLevel)}, look ${f(outLevel)} -> ${if (sane) "OK" else "BROKEN OUTPUT"}")
            ok = ok && r.pass && sane
            // with grain, as the user sees it
            val withGrain = renderer.render(prevSrc, OffscreenRenderer.Request(spec, lut, Adjustments(), seed = LabViewModel.SEED),
                pw, ph, cacheKey = "prev")
            Report.jpeg(ctx, "look_${spec.id}.jpg", withGrain)
            // a 1:1 crop of the full-size render with grain, to judge grain and halation at real size
            val bigGrain = renderer.render(full, OffscreenRenderer.Request(spec, lut, Adjustments(), seed = LabViewModel.SEED),
                full.width, full.height)
            Report.png(ctx, "crop100_${spec.id}.png", Bitmap.createBitmap(bigGrain, 0, (full.height * 0.78f).toInt(), 1200, 600))
            bigGrain.recycle(); big.recycle(); down.recycle(); small.recycle(); withGrain.recycle()
        }
        Report.png(ctx, "chart_original.png", ImageStats.downscale(full, pw, ph))
        Report.flush(ctx, "engine_report.txt")
        assertTrue("preview vs export", ok)
    }

    @Test
    fun tiledExportHasNoSeams() = runBlocking {
        // the export reads back 1024 px tiles; a flat grey must come out flat across tile edges
        val w = 2600
        val h = 1100
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(118, 118, 118)) }
        val spec = repo.looks.first()
        val out = renderer.render(src, OffscreenRenderer.Request(spec, repo.lut(spec),
            Adjustments(grain = 0f, vignette = 0f, halation = 0f, bloom = 0f), seed = 1f), w, h)
        val row = IntArray(w).also { out.getPixels(it, 0, w, 0, h / 2, w, 1) }
        var worst = 0
        for (x in 1 until w) {
            val a = row[x - 1]
            val b = row[x]
            val d = maxOf(Math.abs(Color.red(a) - Color.red(b)), Math.abs(Color.green(a) - Color.green(b)), Math.abs(Color.blue(a) - Color.blue(b)))
            if (d > worst) worst = d
        }
        Report.line("tiles: largest step between neighbouring pixels on flat grey across tile edges: $worst levels")
        Report.flush(ctx, "tiles_report.txt")
        assertTrue("tile seam $worst", worst <= 3)
    }

    @Test
    fun decoderDownscaleVsFullDecode() = runBlocking {
        // a photo-like JPEG: smooth gradients plus fine texture, saved at high quality
        val w = 4000
        val h = 3000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(Color.rgb(40, 30, 25), Color.rgb(210, 180, 150), Color.rgb(90, 120, 160)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        val rnd = Random(3)
        for (i in px.indices) {
            val n = rnd.nextInt(41) - 20
            val v = px[i]
            px[i] = Color.rgb((Color.red(v) + n).coerceIn(0, 255), (Color.green(v) + n).coerceIn(0, 255), (Color.blue(v) + n).coerceIn(0, 255))
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        val file = File(ctx.cacheDir, "photo_like.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bmp.recycle()

        val scaled = Media.decode(ctx, ImageDecoder.createSource(file), LabViewModel.PREVIEW_SRC).bitmap
        val fullDec = Media.decode(ctx, ImageDecoder.createSource(file), 16384, copies = 1).bitmap
        val ours = ImageStats.downscale(fullDec, scaled.width, scaled.height)
        val r = ImageStats.compare(ours, scaled)
        Report.line("decoder: scaled decode ${scaled.width}x${scaled.height} vs full decode + box downscale: " +
            "mean ${f(r.meanAbs)} p99 ${r.p99} bias ${f(r.biasR)}/${f(r.biasG)}/${f(r.biasB)} " +
            "(the Lab now always uses full decode + box downscale)")
        Report.flush(ctx, "decoder_report.txt")
    }

    /** Which path goes black: cached vs fresh source texture, minified vs 1:1 output. */
    @Test
    fun diagnoseSourcePaths() = runBlocking {
        renderer.maxTexture()
        val full = TestChart.draw(TestChart.FULL_W, TestChart.FULL_H)
        val src = ImageStats.downscale(full, 2048, 1536)
        val spec = repo.looks.first()
        val req = OffscreenRenderer.Request(spec, repo.lut(spec), Adjustments(strength = 0f, grain = 0f, vignette = 0f), seed = 1f)
        Report.line("diag input level ${f(ImageStats.meanLevel(src))}")
        for ((name, cache, w, h) in listOf(
            Quad("fresh 1:1", null, 2048, 1536),
            Quad("fresh minified", null, 1600, 1200),
            Quad("cached 1:1", "d1", 2048, 1536),
            Quad("cached 1:1 again", "d1", 2048, 1536),
            Quad("cached minified", "d2", 1600, 1200),
            Quad("fresh small", null, 512, 384),
        )) {
            val out = renderer.render(src, req, w, h, cacheKey = cache)
            Report.line("diag $name ${w}x$h: level ${f(ImageStats.meanLevel(out))}, gl error after: 0x${Integer.toHexString(renderer.lastGlError)}")
            out.recycle()
        }
        Report.flush(ctx, "diag_report.txt")
    }

    private data class Quad(val a: String, val b: String?, val c: Int, val d: Int)
}
