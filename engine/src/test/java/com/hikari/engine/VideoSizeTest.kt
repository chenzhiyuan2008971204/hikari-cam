package com.hikari.engine

import com.hikari.engine.gl.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSizeTest {
    @Test fun portraitThreeFour() = assertEquals(1080 to 1440, VideoSize.forAspect(3f / 4f))
    @Test fun portraitNineSixteen() = assertEquals(1080 to 1920, VideoSize.forAspect(9f / 16f))
    @Test fun square() = assertEquals(1080 to 1080, VideoSize.forAspect(1f))
    @Test fun landscape() = assertEquals(1440 to 1080, VideoSize.forAspect(4f / 3f))
    @Test fun smallerShortSide() = assertEquals(720 to 960, VideoSize.forAspect(3f / 4f, 720))

    @Test fun alwaysEvenAndInRatio() {
        for (a in listOf(0.5f, 0.56f, 0.6667f, 0.75f, 0.8f, 1f, 1.25f, 1.3333f, 1.5f, 1.7778f, 2f)) {
            val (w, h) = VideoSize.forAspect(a)
            assertTrue("$a -> $w x $h", w % 2 == 0 && h % 2 == 0)
            assertEquals("$a ratio", a.toDouble(), w.toDouble() / h, 0.01)
        }
    }

    @Test fun absurdAspectIsClamped() {
        val (w, h) = VideoSize.forAspect(10f)
        assertTrue(w <= 2700 && h == 1080)
    }
}
