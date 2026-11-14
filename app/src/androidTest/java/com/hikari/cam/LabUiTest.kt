package com.hikari.cam

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the Filter Lab like a person: switch looks, run the check, export, open every tab. */
@RunWith(AndroidJUnit4::class)
class LabUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun waitText(text: String, timeout: Long = 120_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitIdleLab(timeout: Long = 180_000) {
        // the progress text disappears when a job ends
        rule.waitUntil(timeout) {
            rule.onAllNodesWithText("…", substring = true).fetchSemanticsNodes().isEmpty()
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Report.png(ctx, name, rule.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun filterLabFlow() {
        waitText("內建測試圖卡 ·")
        waitIdleLab()
        Thread.sleep(1500)
        shot("ui_01_start.png")

        for ((i, name) in listOf("Mellow 400", "Cinema 400", "Tokyo Blue", "Night Bloom").withIndex()) {
            rule.onNodeWithText(name).performClick()
            Thread.sleep(2500)
            shot("ui_02_look_${i}.png")
        }

        rule.onNodeWithText("配方").performClick()
        shot("ui_03_recipe.png")

        rule.onNodeWithText("一致性").performClick()
        rule.onNodeWithText("檢查預覽＝匯出").performClick()
        Thread.sleep(500)
        waitIdleLab()
        shot("ui_04_check.png")
        waitText("通過", 30_000)

        rule.onNodeWithText("完整解析度匯出").performClick()
        Thread.sleep(500)
        waitIdleLab()
        rule.onNodeWithText("效能").performClick()
        waitText("上次完整解析度匯出", 10_000)
        shot("ui_05_perf.png")

        rule.onNodeWithText("參數").performClick()
        shot("ui_06_params.png")
        Report.line("ui: start, 4 looks, recipe, check passed, full-size export, perf, params - all reached")
        Report.flush(ctx, "ui_report.txt")
    }
}
