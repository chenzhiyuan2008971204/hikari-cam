package com.hikari.cam

import android.media.MediaMetadataRetriever
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Video recording end to end on the virtual camera: switch to video, record a few seconds through the film pipeline,
 * stop, and check that a real playable mp4 of the right length and size is in Movies/Hikari. The picture content is
 * not judged here (the CI emulator's software GL cannot be trusted for colours); the file must decode.
 */
@RunWith(AndroidJUnit4::class)
class VideoRecordTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun poll(ms: Long, f: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (runCatching(f).getOrDefault(false)) return true; Thread.sleep(300) }
        return false
    }

    private fun videos(): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        ctx.contentResolver.query(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.Video.Media._ID, android.provider.MediaStore.Video.Media.DISPLAY_NAME),
            "${android.provider.MediaStore.Video.Media.DISPLAY_NAME} LIKE 'HIKARI_%'", null, null)?.use { c ->
            while (c.moveToNext()) out.add(c.getLong(0) to c.getString(1))
        }
        return out
    }

    @Test
    fun recordsAPlayableVideo() {
        runBlocking { com.hikari.data.HikariDatabase.get(ctx).settings().put(com.hikari.data.Setting("virtualCamera", "true")) }
        Thread.sleep(6000)
        val before = videos().size
        rule.onNodeWithTag("modeVideo").performClick()
        Thread.sleep(500)
        rule.onNodeWithTag("shutter").performClick()
        val started = poll(15_000) { rule.onAllNodesWithTagCount("recTimer") > 0 }
        Report.line("video: recording started = $started")
        Thread.sleep(4500)
        rule.onNodeWithTag("shutter").performClick()
        val saved = poll(60_000) { videos().size > before }
        Report.line("video: saved = $saved")
        assertTrue("recording did not start", started)
        assertTrue("no video was saved", saved)
        val (id, name) = videos().maxBy { it.first }
        val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(ctx, uri)
        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        val hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        val frame = mmr.getFrameAtTime(1_000_000)
        Report.line("video: $name ${w}x$h, $dur ms, audio=$hasAudio, frame at 1 s = ${frame?.width}x${frame?.height}")
        Report.flush(ctx, "video_report.txt")
        mmr.release()
        assertTrue("duration $dur ms", dur in 2500..8000)
        assertTrue("size ${w}x$h", w % 2 == 0 && h % 2 == 0 && minOf(w, h) >= 720)
        assertTrue("a frame could not be decoded", frame != null)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
