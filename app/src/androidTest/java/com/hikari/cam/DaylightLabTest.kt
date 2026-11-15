package com.hikari.cam

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the Daylight Filter Lab and screenshots all eight daylight looks on each of the seven scenes. */
@RunWith(AndroidJUnit4::class)
class DaylightLabTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shot(name: String) {
        val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        Report.png(ctx, name, screen)
    }

    @Test
    fun daylightLab() {
        Thread.sleep(2000)
        rule.onNodeWithText("Settings").performClick()
        Thread.sleep(800)
        rule.onNodeWithText("Daylight Filter Lab").performScrollTo().performClick()
        val scenes = listOf("藍天白雲", "綠色樹木", "日本街道", "建築與水泥", "白色衣物", "人像", "逆光")
        for ((i, s) in scenes.withIndex()) {
            rule.onNodeWithText("${i + 1}. $s").performScrollTo().performClick()
            Thread.sleep(400)
            // all looks rendered when the timing line is back
            rule.waitUntil(120_000) { rule.onAllNodesWithText("GPU 算繪", substring = true).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(800)
            shot("daylight_${i + 1}.png")
        }
        rule.onNodeWithText("Muted Chrome · 霧鉻").performClick()
        Thread.sleep(1500)
        shot("daylight_compare.png")
        Report.line("daylight lab: 7 scenes x 8 looks rendered, compare view opened")
        Report.flush(ctx, "daylight_report.txt")
    }
}
