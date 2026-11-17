package com.hikari.cam.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lens routing and the camera diagnosis text, on a phone described the way a Xiaomi multi-lens camera is. */
class TeleTest {
    private fun lens(id: String, parent: String?, f35: Float, listed: Boolean = true, facing: String = "後", logical: Boolean = false, phys: List<String> = emptyList()) =
        LensDetail(id, parent, listed, facing, logical, phys, listOf(f35 / 4f), f35, listOf(1.7f), 9.8f to 7.3f, 8000 to 6000, 0.6f, 10f, 8f, true, "FULL")

    private val main = LensOption("0", null, LensOption.Kind.LISTED, 23f, true)
    private val uw = LensOption("0", "2", LensOption.Kind.PHYSICAL, 14f, false)
    private val wide = LensOption("0", "3", LensOption.Kind.PHYSICAL, 23f, false)
    private val tele3 = LensOption("0", "4", LensOption.Kind.PHYSICAL, 70f, false)
    private val tele5 = LensOption("0", "5", LensOption.Kind.PHYSICAL, 120f, false)
    private val hidden = LensOption("50", null, LensOption.Kind.HIDDEN, 100f, false)

    @Test fun telephotosAreLensesAtLeastOneAndAHalfTimesTheMain() {
        val t = TelePlan.teles(listOf(main, uw, wide, tele3, tele5, hidden), 23f)
        assertEquals(listOf("4", "5"), t.map { it.option.physicalId })        // ultra-wide, main and the hidden lens are not chosen
        assertEquals(3.04f, t[0].ratio, 0.05f); assertEquals(5.2f, t[1].ratio, 0.05f)
        assertTrue(TelePlan.teles(listOf(main, uw), 23f).isEmpty())
        assertTrue(TelePlan.teles(listOf(tele3), null).isEmpty())
    }

    @Test fun zoomPicksTheLongestLensThatHasBeenReached() {
        val t = TelePlan.teles(listOf(tele3, tele5), 23f)
        assertNull(TelePlan.pick(t, 1f)); assertNull(TelePlan.pick(t, 2f)); assertNull(TelePlan.pick(t, 2.8f))
        assertEquals("4", TelePlan.pick(t, 3.0f)!!.option.physicalId)
        assertEquals("4", TelePlan.pick(t, 4.9f)!!.option.physicalId)
        assertEquals("5", TelePlan.pick(t, 5.2f)!!.option.physicalId)
        assertEquals("5", TelePlan.pick(t, 10f)!!.option.physicalId)
        assertEquals(1f, TelePlan.relativeZoom(3.0f, t[0]), 0.05f)            // the lens's own magnification is not applied twice
        assertEquals(1.92f, TelePlan.relativeZoom(10f, t[1]), 0.05f)
        assertEquals(1f, TelePlan.relativeZoom(2f, t[1]), 0f)                 // never below 1x of the lens
    }

    @Test fun phoneThatSwitchesByItselfIsLeftAlone_otherwiseWeBindTheLens() {
        val t = TelePlan.teles(listOf(tele3), 23f)
        assertTrue(TelePlan.step(t, 1.5f, null) is TelePlan.Step.Main)
        assertTrue(TelePlan.step(t, 3f, null) is TelePlan.Step.Auto)            // optimistic until the capture results say otherwise
        assertTrue(TelePlan.step(t, 3f, true) is TelePlan.Step.Auto)
        assertTrue(TelePlan.step(t, 3f, false) is TelePlan.Step.Physical)
        // a lens that was tried and refused is not tried again (unless the phone switches to it itself)
        assertTrue(TelePlan.step(t, 3f, false, setOf(tele3.key)) is TelePlan.Step.Main)
        assertTrue(TelePlan.step(t, 3f, true, setOf(tele3.key)) is TelePlan.Step.Auto)
        // a separately listed camera is never switched to by the phone: always bound by us
        val own = TelePlan.teles(listOf(LensOption("3", null, LensOption.Kind.LISTED, 70f, false)), 23f)
        assertTrue(TelePlan.step(own, 4f, null) is TelePlan.Step.Physical)
    }

    @Test fun zoomThatTheCameraIgnoredIsDetected() {
        assertTrue(TelePlan.honoured(2f, TelePlan.Observed("4", 2.0f, null)))
        assertTrue(TelePlan.honoured(2f, TelePlan.Observed("4", 2.2f, null)))   // within 15 %
        assertFalse(TelePlan.honoured(2f, TelePlan.Observed("4", 1.0f, null)))  // locked at 1x
        assertTrue(TelePlan.honoured(2f, TelePlan.Observed(null, null, 0.5f)))  // crop region half the array = 2x
        assertFalse(TelePlan.honoured(2f, TelePlan.Observed(null, null, 1.0f)))
        assertTrue(TelePlan.honoured(2f, TelePlan.Observed(null, null, null)))  // nothing reported: cannot say, do not fail the lens
    }

    @Test fun diagnosisListsEveryLensAndSaysWhetherATelephotoWasFound() {
        val back = listOf(
            lens("0", null, 23f, logical = true, phys = listOf("2", "3")),
            lens("2", "0", 14f), lens("3", "0", 23f),
            lens("1", null, 23f, facing = "前"),
        )
        val text = CameraDiagnostics.format(back)
        assertTrue(text, "#0 後 多鏡頭(#2、#3)" in text)
        assertTrue(text, "└ #2 後 14mm" in text && "└ #3 後 23mm" in text)
        assertTrue(text, "光圈 f/1.7" in text && "感光元件 9.80×7.30mm" in text && "變焦 0.6–10.0×" in text && "RAW 支援" in text)
        assertTrue(text, "沒有找到比主鏡長" in text && "數位裁切" in text)
        assertTrue(CameraDiagnostics.telephotos(back).isEmpty())

        val withTele = back + lens("4", "0", 70f) + lens("50", null, 120f, listed = false)
        val t = CameraDiagnostics.telephotos(withTele)
        assertEquals(listOf("4", "50"), t.map { it.lens.id })
        assertTrue(t[0].route.contains("多鏡頭 #0") && t[1].route.contains("系統未列出"))
        val text2 = CameraDiagnostics.format(withTele)
        assertTrue(text2, "【長焦判讀】" in text2 && "#4 約 3.0×" in text2 && "#50 約 5.2×" in text2 && "隱藏 ID 1 顆" in text2)
    }

    @Test fun unreadableLensIsReportedNotHidden() {
        val bad = LensDetail("7", "0", false, "?", false, emptyList(), emptyList(), null, emptyList(), null, null, null, null, null, false, "?", error = "SecurityException: no")
        val text = CameraDiagnostics.format(listOf(lens("0", null, 23f), bad))
        assertTrue(text, "讀取失敗：SecurityException" in text)
    }
}
