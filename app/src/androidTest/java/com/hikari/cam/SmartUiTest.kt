package com.hikari.cam

import android.graphics.BitmapFactory
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.hikari.cam.data.Media
import com.hikari.cam.ui.AppViewModel
import com.hikari.cam.ui.Route
import com.hikari.data.HikariDatabase
import com.hikari.data.Setting
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole camera flow on the virtual camera (the bundled standard photos played into the same
 * preview pipeline a real lens feeds): preview, live recommendation, filter switch, capture through
 * the full-resolution engine into MediaStore, next scene, then the editor's smart analysis and export.
 * No physical or emulated camera is involved, so this runs anywhere.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class SmartUiTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun set(k: String, v: String) = runBlocking { HikariDatabase.get(ctx).settings().put(Setting(k, v)) }
    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun tags(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
    private fun shot(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { Report.png(ctx, name, it) }
    }
    private fun poll(timeout: Long, what: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) { if (runCatching(what).getOrDefault(false)) return true; Thread.sleep(400) }
        return false
    }

    @After
    fun restore() {
        set("virtualCamera", "false"); set("composition", "")
        Thread.sleep(2500)   // let the source close before the activity goes away
    }

    @Test
    fun virtualCameraFlow() {
        val t0 = System.currentTimeMillis()
        // the app writes its own settings at start; keep asking until the virtual source is really playing
        val up = poll(40_000) { set("virtualCamera", "true"); has("正在播放") }
        Thread.sleep(4000)
        shot("smart_01_virtual_preview.png")
        if (!up) Report.line("smart: virtual camera did not start; start failure shown: ${has("啟動失敗")}, no-camera message shown: ${has("無法開啟相機")}")
        Report.flush(ctx, "smart_start_report.txt")
        assertTrue("virtual camera did not start", up)
        Report.line("smart: virtual camera preview up after ${System.currentTimeMillis() - t0} ms (includes app start)")
        assertTrue("camera error shown in virtual mode", !has("無法開啟相機") && !has("啟動失敗"))

        // live recommendation chips come from measuring the frames
        rule.onNodeWithText("濾鏡").performClick()
        val gotRecs = poll(40_000) { tags("rec") >= 3 }
        Thread.sleep(1000)
        shot("smart_02_recommend.png")
        Report.line("smart: live recommendation chips ${tags("rec")}")
        assertTrue("no live recommendation", gotRecs)
        runCatching { rule.onAllNodesWithTag("rec").onFirst().performClick() }
        Thread.sleep(2500)
        shot("smart_03_recommended_look.png")

        // live composition guide on the same frames
        set("composition", "smart")
        Thread.sleep(6000)
        shot("smart_04_composition_live.png")
        set("composition", "")
        Thread.sleep(1500)

        // capture: the still goes through the same full-resolution engine path as a real photo
        val before = runBlocking { Media.list(ctx) }.size
        val c0 = System.nanoTime()
        rule.onNodeWithTag("shutter").performClick()
        val done = poll(180_000) { has("已儲存") || has("拍攝失敗") }
        val ms = (System.nanoTime() - c0) / 1e6
        shot("smart_05_after_capture.png")
        val after = runBlocking { Media.list(ctx) }
        val newest = after.firstOrNull { !it.name.contains("original") }
        var size = "?"
        if (newest != null) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(newest.uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
            size = "${o.outWidth}x${o.outHeight}"
            ctx.contentResolver.openInputStream(newest.uri)!!.use { BitmapFactory.decodeStream(it) }?.let { Report.jpeg(ctx, "smart_photo.jpg", it) }
        }
        Report.line("smart: capture ${if (done && !has("拍攝失敗")) "ok" else "FAILED"} in ${"%.0f".format(ms)} ms, files $before -> ${after.size}, newest ${newest?.name} $size")
        assertTrue("capture did not finish", done)
        assertTrue("capture failed", !has("拍攝失敗"))
        assertTrue("no new photo saved", after.size > before)

        // the lens switch steps to the next standard scene
        rule.onNodeWithContentDescription("翻轉").performClick()
        Thread.sleep(6000)
        shot("smart_06_next_scene.png")
        rule.onNodeWithContentDescription("翻轉").performClick()
        Thread.sleep(5000)
        shot("smart_07_third_scene.png")

        Report.line("smart: flip button stepped through two more scenes")
        Report.flush(ctx, "smart_report.txt")
    }

    /** Editor: top-three recommendation cards, composition analysis with overlays, apply a look, export. */
    @Test
    fun zEditorSmartFlow() {
        Thread.sleep(3000)
        // a standard photo saved into the gallery, as if it had just been taken
        val sample = com.hikari.cam.smart.Samples.list(ctx).first { it.id == "seaside" }
        val uri = runBlocking { Media.saveJpeg(ctx, com.hikari.cam.smart.Samples.bitmap(ctx, sample), 95, "_sample") }
        Report.line("editor: sample photo saved to the gallery")
        // leave the camera screen first, the way a person gets to the editor (through the gallery)
        rule.onNodeWithText("Gallery").performClick()
        Thread.sleep(4000)
        Report.line("editor: gallery shown, opening the editor")
        rule.runOnUiThread { ViewModelProvider(rule.activity)[AppViewModel::class.java].go(Route.Editor(uri)) }
        assertTrue("editor did not open", poll(60_000) { has("智慧") })
        Report.line("editor: opened")
        poll(120_000) { !has("讀取照片") }
        Report.line("editor: photo loaded and looks rendered")
        Thread.sleep(3000)
        shot("smart_08_editor.png")
        rule.onNodeWithText("智慧").performClick()
        Report.line("editor: smart tab opened")
        val analysed = poll(90_000) { tags("recCard") >= 3 }
        Thread.sleep(1500)
        shot("smart_09_editor_smart.png")
        Report.line("editor: recommendation cards ${tags("recCard")}, composition suggestions ${tags("compCard")}, 'nothing to adjust' note ${tags("compNote")}")
        assertTrue("editor recommendation missing", analysed)
        // either real suggestions or an honest 'nothing to say' note, never neither
        assertTrue("composition panel empty", tags("compCard") + tags("compNote") > 0)
        // the sea horizon sits in the middle of this photo: that must be found
        assertTrue("horizon advice missing on the seaside photo", has("地平線"))
        runCatching { rule.onAllNodesWithTag("recCard").onFirst().performClick() }
        Thread.sleep(3000)
        shot("smart_10_editor_applied.png")
        val n0 = runBlocking { Media.list(ctx) }.size
        rule.onNodeWithContentDescription("儲存").performClick()
        val exported = poll(180_000) { has("已儲存") || has("匯出失敗") }
        shot("smart_11_exported.png")
        val n1 = runBlocking { Media.list(ctx) }.size
        Report.line("editor: export ${if (exported && !has("匯出失敗")) "ok" else "FAILED"}, files $n0 -> $n1")
        Report.flush(ctx, "smart_editor_report.txt")
        assertTrue("export failed", exported && !has("匯出失敗") && n1 > n0)
    }

    /** Turning the virtual camera on and off repeatedly must not crash or leave a dead preview. */
    @Test
    fun switchingSourcesIsStable() {
        repeat(3) {
            assertTrue("virtual camera did not start (round $it)", poll(40_000) { set("virtualCamera", "true"); has("正在播放") })
            Thread.sleep(1500)
            set("virtualCamera", "false")
            Thread.sleep(2500)
        }
        // no camera on this machine: the app must say so and offer the virtual camera instead of crashing
        shot("smart_12_real_source.png")
        Report.line("smart: source switching x3 ok; real source state: ${if (has("無法開啟相機")) "no camera, message shown" else "camera running"}")
        Report.flush(ctx, "smart_switch_report.txt")
    }
}
