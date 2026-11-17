package com.hikari.cam

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hikari.cam.smart.Samples
import com.hikari.cam.smart.Smart
import com.hikari.vision.CompositionAnalyzer
import com.hikari.vision.Detection
import com.hikari.vision.FilterRecommender
import com.hikari.vision.SceneType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The smart features on Android itself, with the real detectors (ML Kit), on every standard photo:
 * what was detected, the scene, the three recommended looks and the composition advice, with timings
 * and memory. The numbers are labelled with the machine they were measured on.
 */
@RunWith(AndroidJUnit4::class)
class SmartDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = ctx.applicationContext as HikariApp
    private val machine = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT}, " +
        (if (android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) "EMULATOR" else "PHYSICAL DEVICE") + ")"

    @Test
    fun analysisOnEveryStandardPhoto() = runBlocking {
        Report.line("smart analysis measured on: $machine")
        val traits = Smart.traits(app.looks.looks)
        var detMs = 0.0; var anMs = 0.0; var n = 0
        val problems = ArrayList<String>()
        for (s in Samples.list(ctx)) {
            val b = Samples.bitmap(ctx, s)
            val t0 = System.nanoTime()
            val det = Smart.detect(b)
            val t1 = System.nanoTime()
            val rep = CompositionAnalyzer.analyze(Smart.frame(b), det)
            val recs = FilterRecommender.rank(rep.findings.scene, traits).take(3)
            val t2 = System.nanoTime()
            detMs += (t1 - t0) / 1e6; anMs += (t2 - t1) / 1e6; n++
            val faces = det.count { it.kind == Detection.Kind.FACE }
            Report.line("${s.id}: faces $faces, objects ${det.size - faces}, scene ${rep.findings.scene.top.joinToString { it.name }.ifEmpty { "-" }}; " +
                "detect ${"%.0f".format((t1 - t0) / 1e6)} ms, analyse+rank ${"%.0f".format((t2 - t1) / 1e6)} ms")
            Report.line("   looks: " + recs.joinToString(" | ") { "${it.look.name} ${it.score}" } + "  <- ${recs.first().reason}")
            if (rep.suggestions.isEmpty()) Report.line("   advice: (none) ${rep.note}")
            for (g in rep.suggestions) Report.line("   advice: ${g.title} [${"%.0f".format(g.confidence * 100)}%] ${g.evidence} -> ${g.action}")
            // rules that must hold whatever the detector finds
            if (rep.suggestions.size > 3) problems += "${s.id}: more than three suggestions"
            if (rep.suggestions.isEmpty() && rep.note.isEmpty()) problems += "${s.id}: neither advice nor an explanation"
            if (rep.suggestions.any { it.evidence.isBlank() || it.action.isBlank() }) problems += "${s.id}: a suggestion without evidence"
            if (recs.size != 3 || recs.map { it.look.id }.toSet().size != 3) problems += "${s.id}: top three not three different looks"
            if (s.id == "portrait" || s.id == "child") {
                if (faces == 0) problems += "${s.id}: the face detector found no face"
                else if (rep.findings.scene.top.firstOrNull() != SceneType.PORTRAIT) problems += "${s.id}: a face is there but the scene is not PORTRAIT"
            }
            val kinds = rep.suggestions.map { it.kind }
            // answers known from the annotated photos: the sea horizon is level, scenery has no "subject near the edge"
            if (s.id == "seaside" && com.hikari.vision.Suggestion.Kind.LEVEL_HORIZON in kinds) problems += "seaside: a level horizon reported as tilted"
            if (s.id in setOf("foliage", "sunny", "seaside", "concrete", "overcast") && com.hikari.vision.Suggestion.Kind.EDGE_ROOM in kinds) problems += "${s.id}: scenery treated as a subject at the edge"
            if (s.id == "nightstreet" && com.hikari.vision.Suggestion.Kind.HEADROOM in kinds) problems += "nightstreet: a distant face drove portrait advice"
            if (s.id == "beach" && com.hikari.vision.Suggestion.Kind.LEVEL_HORIZON !in kinds) problems += "beach: the 2 degree tilt was not reported"
            if (s.id in setOf("city", "foliage", "night") && faces > 0) problems += "${s.id}: a face was detected where there is none"
            b.recycle()
        }
        Report.line("average per photo: detectors ${"%.0f".format(detMs / n)} ms, analysis + ranking ${"%.0f".format(anMs / n)} ms")
        // same input, same answer: the ranking is not random
        val b = Samples.bitmap(ctx, Samples.list(ctx).first())
        val a1 = FilterRecommender.rank(CompositionAnalyzer.analyze(Smart.frame(b)).findings.scene, traits).map { it.look.id to it.score }
        val a2 = FilterRecommender.rank(CompositionAnalyzer.analyze(Smart.frame(b)).findings.scene, traits).map { it.look.id to it.score }
        if (a1 != a2) problems += "ranking differs between two runs on the same photo"
        // memory: repeated analysis must not grow
        System.gc(); Thread.sleep(300)
        val before = Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        repeat(30) { CompositionAnalyzer.analyze(Smart.frame(b)) }
        System.gc(); Thread.sleep(300)
        val after = Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        Report.line("memory: 30 repeated analyses changed the heap by ${(after - before) / 1024} KB (java+native in use ${after / 1024 / 1024} MB)")
        if (after - before > 24L * 1024 * 1024) problems += "memory grew by ${(after - before) / 1024 / 1024} MB over 30 analyses"
        for (p in problems) Report.line("PROBLEM $p")
        Report.line(if (problems.isEmpty()) "smart analysis: all rules held" else "smart analysis: ${problems.size} problem(s)")
        Report.flush(ctx, "smart_device_report.txt")
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }
}
