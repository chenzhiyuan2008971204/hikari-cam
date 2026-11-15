package com.hikari.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The advice must follow from what was measured, carry its evidence, and stay within three items. */
class AdviceTest {
    private fun kinds(r: CompositionReport) = r.suggestions.map { it.kind }

    @Test
    fun tiltedHorizonIsTheFirstAdvice() {
        val r = CompositionAnalyzer.analyze(Synth.landscape(0.36f, 4f))
        assertEquals(Suggestion.Kind.LEVEL_HORIZON, r.suggestions.first().kind)
        val s = r.suggestions.first()
        assertTrue(s.evidence, s.evidence.contains("4.0°") && s.action.contains("逆時針"))
        assertTrue(s.overlays.isNotEmpty() && s.confidence in 0.5f..1f)
    }

    @Test
    fun levelHorizonOnAThirdNeedsNoAdvice() {
        val r = CompositionAnalyzer.analyze(Synth.landscape(0.34f, 0f))
        assertTrue("got ${r.suggestions.map { it.title }}", kinds(r).none { it == Suggestion.Kind.LEVEL_HORIZON || it == Suggestion.Kind.HORIZON_PLACEMENT })
    }

    @Test
    fun horizonInTheMiddleIsMovedToAThird() {
        val r = CompositionAnalyzer.analyze(Synth.landscape(0.5f, 0f))
        val s = r.suggestions.firstOrNull { it.kind == Suggestion.Kind.HORIZON_PLACEMENT }
        assertNotNull("got ${r.suggestions.map { it.title }}", s)
        assertTrue(s!!.current, s.current.contains("50%"))
    }

    @Test
    fun leaningBuildingIsStraightened() {
        val r = CompositionAnalyzer.analyze(Synth.facade(4f))
        val s = r.suggestions.firstOrNull { it.kind == Suggestion.Kind.STRAIGHTEN_VERTICALS }
        assertNotNull("got ${r.suggestions.map { it.title }}", s)
        assertTrue(s!!.evidence, s.evidence.contains("右") && s.evidence.contains("4.0°"))
        assertTrue(kinds(CompositionAnalyzer.analyze(Synth.facade(0f))).none { it == Suggestion.Kind.STRAIGHTEN_VERTICALS })
    }

    @Test
    fun smallCentredObjectGetsRuleOfThirdsWithACrop() {
        val r = CompositionAnalyzer.analyze(Synth.blob(0.5f, 0.5f, r = 0.08f))
        val s = r.suggestions.firstOrNull { it.kind == Suggestion.Kind.RULE_OF_THIRDS || it.kind == Suggestion.Kind.NEGATIVE_SPACE }
        assertNotNull("got ${r.suggestions.map { it.title }}", s)
        assertTrue(s!!.current, s.current.contains("中央"))
        // an object already on a third is left alone
        val ok = CompositionAnalyzer.analyze(Synth.blob(0.34f, 0.36f, r = 0.08f))
        assertTrue("got ${ok.suggestions.map { it.title }}", kinds(ok).none { it == Suggestion.Kind.RULE_OF_THIRDS })
    }

    @Test
    fun faceLowInTheFrameGetsHeadroomAdvice() {
        val face = Detection(Detection.Kind.FACE, Box(0.42f, 0.55f, 0.58f, 0.75f))
        val r = CompositionAnalyzer.analyze(Synth.blob(0.5f, 0.65f), listOf(face))
        assertTrue("got ${r.suggestions.map { it.title }}", Suggestion.Kind.HEADROOM in kinds(r))
        val good = Detection(Detection.Kind.FACE, Box(0.42f, 0.24f, 0.58f, 0.44f))
        assertTrue(Suggestion.Kind.HEADROOM !in kinds(CompositionAnalyzer.analyze(Synth.blob(0.5f, 0.34f), listOf(good))))
    }

    @Test
    fun faceLookingAtTheNearEdgeGetsLeadRoom() {
        val face = Detection(Detection.Kind.FACE, Box(0.66f, 0.25f, 0.8f, 0.43f), yawDeg = 30f)
        val r = CompositionAnalyzer.analyze(Synth.blob(0.73f, 0.34f), listOf(face))
        val s = r.suggestions.firstOrNull { it.kind == Suggestion.Kind.LEAD_ROOM }
        assertNotNull("got ${r.suggestions.map { it.title }}", s)
        assertTrue(s!!.action, s.action.contains("左三分"))
    }

    @Test
    fun roadGivesLeadingLinesAndSymmetryNotThirds() {
        val r = CompositionAnalyzer.analyze(Synth.road(0.5f, 0.35f))
        assertTrue("got ${r.suggestions.map { it.title }}", Suggestion.Kind.LEADING_LINES in kinds(r))
        assertTrue(Suggestion.Kind.RULE_OF_THIRDS !in kinds(r))
    }

    @Test
    fun neverMoreThanThreeAndAlwaysWithEvidence() {
        val frames = listOf(Synth.landscape(0.5f, 5f), Synth.facade(6f), Synth.blob(0.5f, 0.5f), Synth.road(0.4f, 0.4f), Synth.noise())
        for (f in frames) {
            val r = CompositionAnalyzer.analyze(f)
            assertTrue(r.suggestions.size <= 3)
            for (s in r.suggestions) {
                assertTrue(s.title, s.evidence.any { it.isDigit() })   // every reason quotes a measurement
                assertTrue(s.current.isNotBlank() && s.action.isNotBlank() && s.benefit.isNotBlank())
                assertTrue(s.confidence in 0.3f..1f)
            }
        }
    }

    @Test
    fun cropPutsTheAnchorOnTheTarget() {
        val c = CompositionAnalyzer.cropFor(0.5f, 0.5f, 2f / 3, 1f / 3, null)!!
        assertEquals(2f / 3, (0.5f - c.left) / c.w, 0.01f)
        assertEquals(1f / 3, (0.5f - c.top) / c.h, 0.01f)
        assertTrue(c.left >= 0f && c.right <= 1.001f && c.top >= 0f && c.bottom <= 1.001f)
    }

    /** What a phone's object detector really returns on scenery: boxes that fill the frame or run off its edge. */
    @Test
    fun sceneryBoxesAndDistantFacesAreNotSubjects() {
        val f = Synth.photo("03_foliage.jpg")
        val scenery = listOf(Detection(Detection.Kind.OBJECT, Box(0.1f, 0f, 0.9f, 0.6f)), Detection(Detection.Kind.OBJECT, Box(0f, 0.02f, 0.5f, 0.98f)), Detection(Detection.Kind.OBJECT, Box(0.3f, 0.02f, 0.6f, 0.4f)))
        val r = CompositionAnalyzer.analyze(f, scenery)
        assertTrue("scenery box became a subject: ${kinds(r)}", r.findings.subject == null && kinds(r).none { it == Suggestion.Kind.EDGE_ROOM })
        val street = Synth.photo("14_nightstreet.jpg")
        val far = listOf(Detection(Detection.Kind.FACE, Box(0.30f, 0.60f, 0.33f, 0.64f)))
        val r2 = CompositionAnalyzer.analyze(street, far)
        assertTrue("a distant face drove portrait advice: ${kinds(r2)}", kinds(r2).none { it == Suggestion.Kind.HEADROOM || it == Suggestion.Kind.LEAD_ROOM })
        // a compact object clear of the edges is still a subject
        val r3 = CompositionAnalyzer.analyze(f, listOf(Detection(Detection.Kind.OBJECT, Box(0.4f, 0.4f, 0.6f, 0.6f))))
        assertTrue(r3.findings.subject != null)
    }
}
