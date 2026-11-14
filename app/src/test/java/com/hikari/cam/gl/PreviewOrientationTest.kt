package com.hikari.cam.gl

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The live preview samples the raw sensor buffer through [CameraRenderer.previewCorners]. These
 * tests build an upright test image, turn it into what the sensor would deliver for each CameraX
 * rotationDegrees, sample it back through the corners and check that the screen shows it upright
 * (and mirrored for the selfie camera).
 */
class PreviewOrientationTest {

    // Upright 3 x 4 (portrait) test image with a distinct value per pixel.
    private val w = 3
    private val h = 4
    private val upright = Array(h) { y -> IntArray(w) { x -> y * 10 + x } }

    /** Rotates an image 90 degrees clockwise. */
    private fun rotCW(a: Array<IntArray>): Array<IntArray> {
        val ah = a.size
        val aw = a[0].size
        return Array(aw) { r -> IntArray(ah) { c -> a[ah - 1 - c][r] } }
    }

    /** What the sensor delivers: rotating it clockwise by [rot] gives the upright image. */
    private fun sensorBuffer(rot: Int): Array<IntArray> {
        var b = upright
        repeat(((360 - rot) % 360) / 90) { b = rotCW(b) }
        return b
    }

    private fun check(rot: Int, mirror: Boolean) {
        val buf = sensorBuffer(rot)
        val bh = buf.size
        val bw = buf[0].size
        val corners = CameraRenderer.previewCorners(rot, mirror, w.toFloat() / h, w.toFloat() / h)
        // Screen corners BL, BR, TL, TR -> pixel the viewer must see there.
        val expected = listOf(
            upright[h - 1][0], upright[h - 1][w - 1], upright[0][0], upright[0][w - 1],
        ).let { if (mirror) listOf(it[1], it[0], it[3], it[2]) else it }
        for (i in 0 until 4) {
            val s = corners[i * 4 + 2]
            val t = corners[i * 4 + 3] // t = 0 is the buffer's first (top) row
            val got = buf[(t * (bh - 1)).roundToInt()][(s * (bw - 1)).roundToInt()]
            assertEquals("rot=$rot mirror=$mirror corner=$i", expected[i], got)
        }
        // Image coordinates used by vignette / leaks: (0,0) is the top-left of the screen.
        assertEquals(0f, corners[2 * 4]); assertEquals(0f, corners[2 * 4 + 1])
        assertEquals(1f, corners[1 * 4]); assertEquals(1f, corners[1 * 4 + 1])
    }

    @Test fun backCamera() = listOf(0, 90, 180, 270).forEach { check(it, false) }

    @Test fun selfieCamera() = listOf(0, 90, 180, 270).forEach { check(it, true) }

    @Test
    fun cropsToTheViewWithoutStretching() {
        // A 4:3-tall image in a square view keeps its centre: visible height = width.
        val c = CameraRenderer.previewCorners(0, false, 3f / 4f, 1f)
        val tTop = c[2 * 4 + 3]
        val tBottom = c[0 * 4 + 3]
        assertEquals(0.75f, tBottom - tTop, 1e-5f)
        assertEquals(0.5f, (tTop + tBottom) / 2, 1e-5f)
    }
}
