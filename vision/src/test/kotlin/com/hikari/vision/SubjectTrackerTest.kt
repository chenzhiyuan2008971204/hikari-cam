package com.hikari.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SubjectTrackerTest {
    private fun box(cx: Float, cy: Float, s: Float) = Box(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
    private val cup = box(0.35f, 0.55f, 0.2f)
    private val vase = box(0.7f, 0.5f, 0.22f)

    /** Two objects whose detected sizes wobble, so "the largest" changes from frame to frame. */
    @Test
    fun staysOnOneObjectWhileSizesWobble() {
        val t = SubjectTracker(); val rnd = Random(1); var switches = 0; var last: Float? = null; var seen = 0
        repeat(200) {
            val a = box(cup.cx + rnd.nextFloat() * 0.02f, cup.cy, 0.2f + rnd.nextFloat() * 0.08f)
            val b = box(vase.cx + rnd.nextFloat() * 0.02f, vase.cy, 0.2f + rnd.nextFloat() * 0.08f)
            val p = t.update(emptyList(), if (rnd.nextBoolean()) listOf(a, b) else listOf(b, a)) ?: return@repeat
            seen++
            if (last != null && kotlin.math.abs(p.box.cx - last!!) > 0.2f) switches++
            last = p.box.cx
        }
        assertTrue("subject was only shown in $seen of 200 frames", seen > 190)
        assertEquals("the guide jumped between the two objects", 0, switches)
    }

    @Test
    fun aSingleFrameDetectionIsNotTaken() {
        val t = SubjectTracker()
        assertNull(t.update(emptyList(), listOf(cup)))
        assertNull(t.update(emptyList(), emptyList()))
        assertNull(t.update(emptyList(), listOf(vase)))
    }

    @Test
    fun holdsThroughShortDropoutsAndReleasesAfterALongOne() {
        val t = SubjectTracker()
        repeat(4) { t.update(emptyList(), listOf(cup, vase)) }
        val locked = t.update(emptyList(), listOf(cup, vase))!!.box.cx
        // the locked object is missed for a few frames while the other one is still detected
        val other = if (locked < 0.5f) vase else cup
        repeat(6) { val p = t.update(emptyList(), listOf(other)); assertNotNull(p); assertEquals(locked, p!!.box.cx, 0.01f); assertEquals(-1, p.index) }
        // gone for good: the other object is taken, after being confirmed
        var p: SubjectTracker.Pick? = null
        repeat(20) { p = t.update(emptyList(), listOf(other)) }
        assertEquals(other.cx, p!!.box.cx, 0.01f)
    }

    @Test
    fun followsTheSubjectWhenItMoves() {
        val t = SubjectTracker(); var p: SubjectTracker.Pick? = null
        for (i in 0 until 60) { val x = 0.2f + i * 0.01f; p = t.update(emptyList(), listOf(box(x, 0.5f, 0.2f), vase.copy(left = 0.85f, right = 0.99f))) }
        assertEquals(0.79f, p!!.box.cx, 0.02f)
    }

    @Test
    fun aPersonTakesOverFromAnObjectButNotOnAOneFrameFalseFace() {
        val t = SubjectTracker(); val face = box(0.5f, 0.3f, 0.12f)
        repeat(5) { t.update(emptyList(), listOf(cup)) }
        assertEquals(SubjectTracker.Kind.OBJECT, t.update(listOf(face), listOf(cup))!!.kind)   // one frame: no
        assertEquals(SubjectTracker.Kind.OBJECT, t.update(emptyList(), listOf(cup))!!.kind)
        var p: SubjectTracker.Pick? = null
        repeat(4) { p = t.update(listOf(face), listOf(cup)) }
        assertEquals(SubjectTracker.Kind.FACE, p!!.kind)
    }

    @Test
    fun twoFacesOfSimilarSizeDoNotSwap() {
        val t = SubjectTracker(); val rnd = Random(3); var last: Float? = null; var switches = 0
        repeat(150) {
            val a = box(0.3f, 0.35f, 0.14f + rnd.nextFloat() * 0.04f); val b = box(0.68f, 0.38f, 0.14f + rnd.nextFloat() * 0.04f)
            val p = t.update(if (rnd.nextBoolean()) listOf(a, b) else listOf(b, a), emptyList()) ?: return@repeat
            if (last != null && kotlin.math.abs(p.box.cx - last!!) > 0.2f) switches++
            last = p.box.cx
        }
        assertEquals(0, switches)
    }

    @Test
    fun sceneryThatFillsTheFrameIsNotASubject() {
        val t = SubjectTracker(); var p: SubjectTracker.Pick? = null
        repeat(10) { p = t.update(emptyList(), listOf(Box(0f, 0f, 1f, 0.9f))) }
        assertNull(p)
    }
}
