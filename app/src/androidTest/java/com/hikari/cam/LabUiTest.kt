package com.hikari.cam

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the Filter Lab like a person: switch looks, run the check, export, open every tab. */
@RunWith(AndroidJUnit4::class)
class LabUiTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    @get:Rule(order = 1)
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
        // system screenshot: Compose capture (PixelCopy) stalls while the GL camera preview is on screen
        val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        Report.png(ctx, name, screen ?: rule.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun filterLabFlow() {
        // the app opens on the camera tab; walk the four tabs, then open Filter Lab from Settings
        Thread.sleep(3000)
        shot("app_01_camera.png")
        rule.onNodeWithText("Gallery").performClick()
        Thread.sleep(1000)
        shot("app_02_gallery.png")
        rule.onNodeWithText("Filters").performClick()
        Thread.sleep(8000)
        shot("app_03_filters.png")
        // one category at a time: switch, count, search inside the category, sort, favourites, back to all
        fun has(t: String) = rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
        fun count() = rule.onNodeWithTag("catCount").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        rule.onNodeWithTag("cat-all").performClick(); Thread.sleep(800)
        org.junit.Assert.assertEquals("62 / 62 款", count())
        rule.onNodeWithTag("cat-japanese").performScrollTo().performClick(); Thread.sleep(1200)
        shot("app_03b_category_japanese.png")
        org.junit.Assert.assertEquals("12 / 12 款", count())
        org.junit.Assert.assertTrue("Japanese category does not show its looks", has("Tokyo Blue") && has("Kyoto Soft"))
        org.junit.Assert.assertTrue("a look of another category is shown in Japanese", !has("Standard Chrome") && !has("Mellow 400"))
        rule.onNodeWithTag("lookSearch").performTextInput("tokyo"); Thread.sleep(800)
        org.junit.Assert.assertEquals("2 / 12 款", count())
        rule.onNodeWithTag("lookSearch").performTextClearance(); Thread.sleep(500)
        rule.onNodeWithTag("sort-NAME").performClick(); Thread.sleep(500)
        org.junit.Assert.assertEquals("12 / 12 款", count())
        rule.onNodeWithTag("sort-DEFAULT").performClick(); Thread.sleep(500)   // back to the library order (the list shows only what fits on screen)
        // an added category: only its own looks, none of the older ones
        rule.onNodeWithTag("cat-cinema").performScrollTo().performClick(); Thread.sleep(1200)
        shot("app_03b2_category_cinema.png")
        org.junit.Assert.assertEquals("11 / 11 款", count())
        org.junit.Assert.assertTrue(has("Kodak 2383") && !has("Tokyo Blue") && !has("Standard Chrome"))
        rule.onNodeWithTag("cat-sim").performScrollTo().performClick(); Thread.sleep(1000)
        org.junit.Assert.assertEquals("3 / 3 款", count())
        org.junit.Assert.assertTrue(has("Standard Chrome") && !has("Tokyo Blue"))
        // the category is remembered: leave the tab and come back
        rule.onNodeWithText("Gallery").performClick(); Thread.sleep(600)
        rule.onNodeWithText("Filters").performClick(); Thread.sleep(1500)
        org.junit.Assert.assertEquals("3 / 3 款", count())
        // frame library: every frame on a demo photo, by category, with search
        rule.onNodeWithTag("openFrames").performClick(); Thread.sleep(6000)
        shot("app_03c_frame_library.png")
        fun frames() = rule.onNodeWithTag("frameCount").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        org.junit.Assert.assertEquals("${com.hikari.studio.Frames.all.size} 款", frames())
        rule.onNodeWithTag("frameCat-INSTANT").performScrollTo().performClick(); Thread.sleep(1500)
        shot("app_03d_frames_instant.png")
        org.junit.Assert.assertEquals("${com.hikari.studio.Frames.inCategory(com.hikari.studio.FrameCat.INSTANT).size} 款", frames())
        rule.onNodeWithTag("frameCat-all").performScrollTo().performClick()
        rule.onNodeWithTag("frameLibSearch").performTextInput("日系"); Thread.sleep(1000)
        org.junit.Assert.assertEquals("${com.hikari.studio.Frames.search("日系").size} 款", frames())
        Report.line("ui: filter categories (switch, count, search, sort, remembered) and frame library (${com.hikari.studio.Frames.all.size} frames, category, search) checked")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; Thread.sleep(800)
        rule.onNodeWithTag("cat-all").performScrollTo().performClick(); Thread.sleep(500)
        rule.onNodeWithText("Settings").performClick()
        Thread.sleep(800)
        shot("app_04_settings.png")
        rule.onNodeWithText("Filter Lab").performScrollTo().performClick()
        waitText("內建測試圖卡 ·")
        waitIdleLab()
        Thread.sleep(1500)
        shot("ui_01_start.png")

        for ((i, name) in listOf("Soft Chrome", "True Negative", "Silver Bypass", "Standard Chrome").withIndex()) {
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
