package com.hikari.cam.ui

import com.hikari.cam.camera.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceTest {
    private fun at(cx: Float, cy: Float, h: Float = 0.3f) = Subject(cx - 0.05f, cy - h / 2, cx + 0.05f, cy + h / 2)

    @Test
    fun smartGuideTargetsNearestThird() {
        val g = guidance("smart", at(0.30f, 0.35f))
        assertEquals(1f / 3, g.target.x, 1e-4f)
        assertEquals(1f / 3, g.target.y, 1e-4f)
        assertTrue(g.aligned)
    }

    @Test
    fun subjectLeftOfTargetMeansPanLeft() {
        val g = guidance("center", at(0.30f, 0.50f))
        assertFalse(g.aligned)
        assertTrue(g.tip, g.tip.contains("鏡頭往左"))
    }

    @Test
    fun fullBodyAsksToComeCloserWhenSubjectIsSmall() {
        val g = guidance("portrait_full", at(0.67f, 0.58f, h = 0.3f))
        assertTrue(g.tip, g.tip.contains("靠近一點"))
    }

    @Test
    fun fullBodyAlignedAtTargetAndSize() {
        assertTrue(guidance("portrait_full", at(0.67f, 0.58f, h = 0.66f)).aligned)
    }
}
