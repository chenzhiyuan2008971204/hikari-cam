package com.hikari.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {
    @Test
    fun roundTripsThroughRows() {
        val s = UserSettings(jpegQuality = 88, saveOriginal = false, saveRaw = true, grid = true, aspect = "1:1", lastLook = "night_bloom", cameraAdjustJson = "{}")
        assertEquals(s, UserSettings.from(s.toRows()))
    }

    @Test
    fun badValuesFallBackToDefaults() {
        val s = UserSettings.from(listOf(Setting("jpegQuality", "x"), Setting("aspect", "5:1"), Setting("grid", "maybe")))
        assertEquals(UserSettings(), s)
    }

    @Test
    fun aspectRatio() {
        assertEquals(0.75f, UserSettings.ratio("3:4"), 1e-6f)
        assertEquals(1f, UserSettings.ratio("1:1"), 1e-6f)
    }
}
