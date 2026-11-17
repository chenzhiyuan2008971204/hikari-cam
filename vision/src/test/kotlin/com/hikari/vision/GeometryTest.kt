package com.hikari.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Measurements against pictures whose answers are known by construction. */
class GeometryTest {
    @Test
    fun horizonPositionAndTilt() {
        val report = StringBuilder("horizon (synthetic): y, tilt -> found y, tilt, confidence\n")
        var n = 0; var errY = 0f; var errT = 0f
        for (y in listOf(0.3f, 0.5f, 0.66f)) for (t in listOf(-6f, -3f, 0f, 2f, 5f)) {
            val h = CompositionAnalyzer.findings(Synth.landscape(y, t)).horizon
            assertNotNull("horizon y=$y tilt=$t", h); h!!
            report.append("  %.2f %+.1f -> %.3f %+.2f %.2f\n".format(y, t, h.y, h.tiltDeg, h.confidence))
            assertEquals("y at tilt $t", y, h.y, 0.03f); assertEquals("tilt at y $y", t, h.tiltDeg, 1.0f)
            errY += abs(h.y - y); errT += abs(h.tiltDeg - t); n++
        }
        report.append("  mean error: y %.4f, tilt %.2f deg over $n cases\n".format(errY / n, errT / n))
        Report.add("horizon", report.toString())
    }

    @Test
    fun verticalTilt() {
        val report = StringBuilder("verticals (synthetic facade): tilt -> found tilt, lines, confidence\n")
        var n = 0; var err = 0f
        for (t in listOf(-6f, -3f, 0f, 2f, 4f, 7f)) {
            val v = CompositionAnalyzer.findings(Synth.facade(t)).verticals
            assertNotNull("verticals tilt=$t", v); v!!
            report.append("  %+.1f -> %+.2f %d %.2f\n".format(t, v.tiltDeg, v.count, v.confidence))
            assertEquals("tilt", t, v.tiltDeg, 1.0f); err += abs(v.tiltDeg - t); n++
        }
        report.append("  mean error: %.2f deg over $n cases\n".format(err / n))
        Report.add("verticals", report.toString())
    }

    @Test
    fun subjectPositionFromSaliency() {
        val report = StringBuilder("subject (synthetic object, no detector): position -> found\n")
        var n = 0; var err = 0f
        for ((cx, cy) in listOf(0.5f to 0.5f, 0.3f to 0.4f, 0.7f to 0.6f, 0.2f to 0.7f, 0.8f to 0.3f)) {
            val s = CompositionAnalyzer.findings(Synth.blob(cx, cy)).subject
            assertNotNull("subject at $cx,$cy", s); s!!
            val e = kotlin.math.hypot(s.anchorX - cx, s.anchorY - cy)
            report.append("  (%.2f, %.2f) -> (%.3f, %.3f) error %.3f conf %.2f\n".format(cx, cy, s.anchorX, s.anchorY, e, s.confidence))
            assertTrue("error $e", e < 0.05f); err += e; n++
        }
        report.append("  mean error: %.4f of the frame over $n cases\n".format(err / n))
        Report.add("subject", report.toString())
    }

    @Test
    fun leadingLinesMeetAtTheVanishingPoint() {
        val report = StringBuilder("leading lines (synthetic road): vanishing point -> found\n")
        for ((vx, vy) in listOf(0.5f to 0.35f, 0.35f to 0.4f, 0.62f to 0.3f)) {
            val l = CompositionAnalyzer.findings(Synth.road(vx, vy)).leading
            assertNotNull("leading lines vp=$vx,$vy", l); l!!
            report.append("  (%.2f, %.2f) -> (%.3f, %.3f) lines %d conf %.2f\n".format(vx, vy, l.vx, l.vy, l.lines.size, l.confidence))
            assertEquals(vx, l.vx, 0.06f); assertEquals(vy, l.vy, 0.06f)
        }
        Report.add("leading", report.toString())
    }

    @Test
    fun nothingIsClaimedOnNoise() {
        for (seed in 1..5) {
            val r = CompositionAnalyzer.analyze(Synth.noise(seed))
            assertNull("horizon on noise", r.findings.horizon)
            assertNull("verticals on noise", r.findings.verticals)
            assertNull("leading on noise", r.findings.leading)
            assertTrue("suggestions on noise: ${r.suggestions.map { it.title }}", r.suggestions.isEmpty())
            assertTrue(r.note.isNotEmpty())
        }
        // a facade has no horizon; a landscape has no verticals
        assertNull(CompositionAnalyzer.findings(Synth.facade(0f)).horizon)
        assertNull(CompositionAnalyzer.findings(Synth.landscape(0.5f, 0f)).verticals)
        Report.add("negatives", "negative cases: noise x5, facade (no horizon), landscape (no verticals): nothing claimed\n")
    }

    @Test
    fun symmetryScore() {
        assertTrue(Geometry.symmetry(Synth.road(0.5f, 0.35f)) > 0.9f)
        assertTrue(Geometry.symmetry(Synth.blob(0.25f, 0.5f)) < 0.8f)
    }
}

/** Collects the measured numbers of a test run into vision/build/reports/vision_metrics.txt. */
object Report {
    private val parts = java.util.TreeMap<String, String>()
    @Synchronized fun add(key: String, text: String) {
        parts[key] = text
        val f = File(Synth.repo, "vision/build/reports/vision_metrics.txt"); f.parentFile.mkdirs()
        f.writeText(parts.values.joinToString("\n"))
    }
}
