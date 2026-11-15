package com.hikari.cam

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hikari.cam.data.Media
import com.hikari.cam.smart.Samples
import com.hikari.cam.ui.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editor's logic without its screen: open a photo from the gallery, render the preview and all
 * look thumbnails, analyse it (scene, top-three looks, composition), apply the first recommendation
 * and export at full size. Reports every step to logcat.
 */
@RunWith(AndroidJUnit4::class)
class EditorModelTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private fun poll(timeout: Long, what: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) { if (what()) return true; Thread.sleep(300) }
        return false
    }

    @Test
    fun openAnalyseApplyExport() {
        val sample = Samples.list(ctx).first { it.id == "seaside" }
        val uri = runBlocking { Media.saveJpeg(ctx, Samples.bitmap(ctx, sample), 95, "_sample") }
        Report.line("editor-model: photo saved")
        lateinit var vm: EditorViewModel
        inst.runOnMainSync { vm = EditorViewModel(ctx.applicationContext as android.app.Application) }
        val t0 = System.nanoTime()
        inst.runOnMainSync { vm.open(uri, null) }
        assertTrue("preview not rendered: ${vm.message}", poll(120_000) { vm.preview != null })
        Report.line("editor-model: preview rendered in ${(System.nanoTime() - t0) / 1_000_000} ms (${vm.fullW}x${vm.fullH})")
        assertTrue("thumbnails missing: ${vm.thumbs.size}", poll(240_000) { vm.thumbs.size == vm.looks.size })
        Report.line("editor-model: ${vm.thumbs.size} look thumbnails rendered, ${(System.nanoTime() - t0) / 1_000_000} ms after open")
        assertTrue("analysis did not finish", poll(120_000) { !vm.analysing && vm.busy == null })
        val rep = vm.report
        Report.line("editor-model: scene ${vm.sceneLabel}; looks ${vm.recs.joinToString(" | ") { "${it.look.name} ${it.score}" }}; " +
            "advice ${rep?.suggestions?.joinToString(" | ") { it.title } ?: "-"}; ${vm.detectorNote}; analysis ${vm.analysisMs.toInt()} ms")
        assertTrue("no top three", vm.recs.size == 3 && vm.recs.map { it.look.id }.toSet().size == 3)
        assertTrue("no composition report", rep != null && (rep.suggestions.isNotEmpty() || rep.note.isNotEmpty()))
        // the sea horizon cuts this photo in half: that must be found
        assertTrue("horizon advice missing", rep!!.suggestions.any { it.kind == com.hikari.vision.Suggestion.Kind.HORIZON_PLACEMENT })
        val first = vm.looks.first { it.id == vm.recs.first().look.id }
        val before = vm.preview
        inst.runOnMainSync { vm.selectLook(first) }
        assertTrue("look not applied", poll(60_000) { vm.look.id == first.id && vm.preview !== before })
        Report.line("editor-model: applied ${first.name}, preview re-rendered in ${vm.lastRenderMs.toInt()} ms")
        val n0 = runBlocking { Media.list(ctx) }.size
        inst.runOnMainSync { vm.export(95) }
        assertTrue("export did not finish", poll(180_000) { vm.message != null && vm.busy == null })
        val n1 = runBlocking { Media.list(ctx) }.size
        Report.line("editor-model: export -> ${vm.message}; files $n0 -> $n1")
        Report.flush(ctx, "editor_model_report.txt")
        assertTrue("export failed: ${vm.message}", vm.message!!.contains("已儲存") && n1 > n0)
    }
}
