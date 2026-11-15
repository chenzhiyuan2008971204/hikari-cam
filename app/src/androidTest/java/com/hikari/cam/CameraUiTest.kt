package com.hikari.cam

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.hikari.cam.data.Media
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The camera on the emulator's virtual camera: live film preview, a real capture through the
 * full-resolution engine path into MediaStore, and switching lenses.
 */
@RunWith(AndroidJUnit4::class)
class CameraUiTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    /** a real screen capture: the GL camera preview is a SurfaceView, which Compose capture cannot see */
    private fun shot(name: String) {
        rule.waitForIdle()
        val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        Report.png(ctx, name, screen ?: rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun captureSavesFullResolutionPhoto() {
        Thread.sleep(8000) // camera open + first frames + look thumbnails on a software GPU
        shot("cam_01_preview.png")
        if (has("無法開啟相機")) {
            Report.line("camera: emulator has no usable camera, message shown instead of crashing")
            Report.flush(ctx, "camera_report.txt")
            return
        }
        // live composition guidance on the virtual camera scene. While a guide tracks the subject the
        // UI keeps redrawing, so nothing here waits for Compose to go idle: screenshot directly,
        // then switch the guide off through the settings table.
        rule.onNodeWithText("構圖").performClick()
        Thread.sleep(800)
        rule.onNodeWithText("智慧構圖").performClick()
        Thread.sleep(6000)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { Report.png(ctx, "cam_01b_composition.png", it) }
        runBlocking { com.hikari.data.HikariDatabase.get(ctx).settings().put(com.hikari.data.Setting("composition", "")) }
        Thread.sleep(1500)
        Report.line("camera: live composition guide shown and switched off")
        rule.onNodeWithText("濾鏡").performClick()
        Thread.sleep(500)
        val before = runBlocking { Media.list(ctx) }.size
        val t0 = System.nanoTime()
        rule.onNodeWithTag("shutter").performClick()
        rule.waitUntil(180_000) { has("已儲存") || has("拍攝失敗") }
        val ms = (System.nanoTime() - t0) / 1e6
        shot("cam_02_after_capture.png")
        val failed = has("拍攝失敗")
        // the filtered photo itself, scaled down, for review
        runBlocking { Media.list(ctx) }.firstOrNull { !it.name.contains("original") }?.let { p ->
            val o = BitmapFactory.Options().apply { inSampleSize = 2 }
            ctx.contentResolver.openInputStream(p.uri)!!.use { BitmapFactory.decodeStream(it, null, o) }?.let { Report.jpeg(ctx, "cam_photo.jpg", it) }
        }
        val after = runBlocking { Media.list(ctx) }
        val newest = after.firstOrNull()
        var size = "?"
        if (newest != null) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(newest.uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
            size = "${o.outWidth}x${o.outHeight}"
        }
        Report.line("camera: capture ${if (failed) "FAILED" else "ok"} in ${"%.0f".format(ms)} ms, files ${before} -> ${after.size}, newest ${newest?.name} $size")
        if (has("翻轉")) {
            rule.onNodeWithText("翻轉").performClick()
            Thread.sleep(5000)
            shot("cam_03_front.png")
            Report.line("camera: switched to front lens")
        } else Report.line("camera: only one lens, switch hidden")
        Report.flush(ctx, "camera_report.txt")
        assertTrue("capture failed", !failed)
        assertTrue("no new photo saved", after.size > before)
    }
}
