package com.hikari.cam

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.hikari.cam.data.Media
import com.hikari.cam.ui.AppViewModel
import com.hikari.cam.ui.EditorViewModel
import com.hikari.cam.ui.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where does the CI emulator die when the editor opens? The editor's start-up is run only up to step `stage`
 * (EditorViewModel.EditorDebug: 0 nothing but the screen, 1 decode, 2 first preview, 3 look thumbnails,
 * 4 frame thumbnails, 5 everything), then the screen is left on for a while. The CI job editor-ui runs one stage per
 * runner: the first stage whose emulator disappears is the step that does it. Only runs when `stage` is given.
 */
@RunWith(AndroidJUnit4::class)
class EditorBisectTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)
    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun editorStartsUpToStage() {
        val stage = InstrumentationRegistry.getArguments().getString("stage")?.toIntOrNull() ?: return
        EditorViewModel.EditorDebug.stopAfter = if (stage >= 5) Int.MAX_VALUE else stage
        Thread.sleep(3000)
        val sample = com.hikari.cam.smart.Samples.list(ctx).first { it.id == "seaside" }
        val uri = runBlocking { Media.saveJpeg(ctx, com.hikari.cam.smart.Samples.bitmap(ctx, sample), 95, "_sample") }
        rule.onNodeWithText("Gallery").performClick()
        Thread.sleep(4000)
        Report.line("bisect stage $stage: opening the editor")
        rule.runOnUiThread { ViewModelProvider(rule.activity)[AppViewModel::class.java].go(Route.Editor(uri)) }
        val end = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < end && rule.onAllNodesWithText("智慧", substring = true).fetchSemanticsNodes().isEmpty()) Thread.sleep(300)
        Report.line("bisect stage $stage: editor screen shown")
        // let the start-up run to its stage and the screen stay up
        Thread.sleep(25_000)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { Report.png(ctx, "bisect_stage_$stage.png", it) }
        Report.line("bisect stage $stage: SURVIVED")
        Report.flush(ctx, "bisect_stage_$stage.txt")
        EditorViewModel.EditorDebug.stopAfter = Int.MAX_VALUE
        assertTrue(true)
    }
}
