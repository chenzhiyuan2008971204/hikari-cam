package com.hikari.cam.ui

import com.hikari.cam.camera.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Composition rules of the live guide, on typical framings (preview fractions, portrait 3:4). */
class GuidanceTest {
    private fun thing(cx: Float, cy: Float, w: Float = 0.1f, h: Float = 0.3f) = Subject(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

    /** a person whose face (height fh) is at fx/fy (face centre), body below it */
    private fun person(fx: Float, fy: Float, fh: Float, yaw: Float = 0f, bodyBottom: Float = 1f): Subject {
        val face = Subject.Face(fx - fh * 0.4f, fy - fh / 2, fx + fh * 0.4f, fy + fh / 2, yaw)
        return Subject(fx - fh * 1.3f, face.top - fh * 0.2f, fx + fh * 1.3f, bodyBottom, face)
    }

    @Test
    fun personEyesGoOnTheUpperThird() {
        val g = guidance("smart", person(0.5f, 0.6f, 0.14f))
        assertEquals(1f / 3, g.target.y, 0.02f)
        assertEquals(g.anchor.y, 0.6f - 0.14f / 2 + 0.14f * 0.42f, 1e-4f) // anchor is the eye line
        assertTrue(g.tip, g.tip.contains("鏡頭往下") || g.tip.contains("鏡頭往上"))
    }

    @Test
    fun personLookingRightGetsSpaceOnTheRight() {
        val t = smartTarget(person(0.5f, 0.38f, 0.14f, yaw = 30f))
        assertTrue("target $t", t.x < 0.45f)
        val u = smartTarget(person(0.5f, 0.38f, 0.14f, yaw = -30f))
        assertTrue("target $u", u.x > 0.55f)
    }

    @Test
    fun closeUpFaceIsCentredWithHeadroom() {
        val s = person(0.52f, 0.45f, 0.32f)
        val t = smartTarget(s)
        assertEquals(0.5f, t.x, 0.02f)
        // the top of the head stays inside the frame after moving
        val dy = t.y - anchorOf(s).y
        assertTrue("head top ${s.face!!.top - s.face!!.h * 0.25f + dy}", s.face!!.top - s.face!!.h * 0.25f + dy > 0f)
    }

    @Test
    fun smallObjectGoesOnAStrongPointNotTheCentre() {
        val t = smartTarget(thing(0.47f, 0.5f, w = 0.12f, h = 0.12f))
        assertFalse("target $t", kotlin.math.abs(t.x - 0.5f) < 0.01f && kotlin.math.abs(t.y - 0.5f) < 0.01f)
    }

    @Test
    fun largeObjectIsCentred() {
        val t = smartTarget(thing(0.45f, 0.5f, w = 0.7f, h = 0.75f))
        assertEquals(0.5f, t.x, 0.02f)
    }

    @Test
    fun targetDoesNotHopBetweenEqualPoints() {
        val a = smartTarget(thing(0.5f, 0.42f, w = 0.12f, h = 0.12f))
        val b = smartTarget(thing(0.505f, 0.42f, w = 0.12f, h = 0.12f), previous = a)
        assertEquals(a, b)
    }

    @Test
    fun subjectLeftOfTargetMeansPanLeft() {
        val g = guidance("center", thing(0.30f, 0.50f))
        assertFalse(g.aligned)
        assertTrue(g.tip, g.tip.contains("鏡頭往左"))
    }

    @Test
    fun fullBodyAsksToComeCloserWhenSubjectIsSmall() {
        val g = guidance("portrait_full", thing(0.67f, 0.58f, h = 0.3f))
        assertTrue(g.tip, g.tip.contains("靠近一點"))
    }

    @Test
    fun alignedAtTarget() {
        val s = person(0.5f, 1f / 3 - 0.14f * 0.42f + 0.14f / 2, 0.14f, bodyBottom = 0.95f)
        val g = guidance("smart", s, previous = androidx.compose.ui.geometry.Offset(0.5f, 1f / 3))
        assertTrue(g.tip, g.aligned)
    }
}
