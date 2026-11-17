package com.hikari.cam

import com.hikari.cam.recipe.RecipeLogic
import com.hikari.engine.Adjustments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeLogicTest {
    private val adjust = Adjustments(strength = 0.8f, exposure = 0.3f, grain = 0.5f, halation = 0f, vignette = null, contrast = 0.2f, temperature = -0.1f, shadows = 0.15f)

    private fun make(a: Adjustments = adjust, stamp: Boolean = true, frame: Boolean = false) =
        RecipeLogic.make(2, "  街拍負片  ", "fj_classic_neg", a, stamp, "stamp-code", frame, "frame-code", true, 1234L)

    @Test fun recipeKeepsTheNamedFieldsInTheirOwnColumnsAndTheRestInJson() {
        val r = make()
        assertEquals("街拍負片", r.name)                           // trimmed
        assertEquals(0.8f, r.strength, 0f); assertEquals(0.3f, r.exposure, 0f)
        assertEquals(0.5f, r.grain!!, 0f); assertEquals(0f, r.halation!!, 0f); assertNull(r.vignette)   // 0 = off is kept apart from null = the look's own
        val rest = Adjustments.fromJson(r.adjustJson)
        assertEquals(1f, rest.strength, 0f); assertEquals(0f, rest.exposure, 0f); assertNull(rest.grain)
        assertEquals(0.2f, rest.contrast, 0f); assertEquals(-0.1f, rest.temperature, 0f); assertEquals(0.15f, rest.shadows, 0f)
    }

    @Test fun roundTripGivesBackTheSameAdjustments() {
        val r = make()
        assertEquals(adjust.toJson(), RecipeLogic.adjustments(r).toJson())
        for (a in listOf(Adjustments(), adjust.copy(vignette = 0.4f, bloom = 0f), adjust.copy(strength = 1.5f, exposure = -2f))) {
            assertEquals(a.toJson(), RecipeLogic.adjustments(make(a)).toJson())
        }
    }

    @Test fun valuesAreClampedIntoTheirRanges() {
        val r = make(adjust.copy(strength = 9f, exposure = -9f))
        assertEquals(1.5f, r.strength, 0f); assertEquals(-2f, r.exposure, 0f)
        assertEquals("x".repeat(24), RecipeLogic.make(1, "x".repeat(60), "a", adjust, false, "", false, "", false, 0).name)
    }

    @Test fun slotsAreOneToSeven() {
        for (bad in listOf(0, 8, -1)) assertTrue(runCatching { RecipeLogic.make(bad, "n", "a", adjust, false, "", false, "", false, 0) }.isFailure)
        assertEquals("C1", RecipeLogic.label(1)); assertEquals("C7", RecipeLogic.label(7)); assertEquals("預設", RecipeLogic.label(0))
        assertEquals("C2 街拍負片", RecipeLogic.title(make())); assertEquals("C2", RecipeLogic.title(make().copy(name = "")))
    }

    @Test fun changingTheLookOrAnAdjustmentByHandMarksTheRecipeModified() {
        val r = make()
        assertFalse(RecipeLogic.modified(r, "fj_classic_neg", adjust))
        assertTrue(RecipeLogic.modified(r, "mellow400", adjust))
        assertTrue(RecipeLogic.modified(r, "fj_classic_neg", adjust.copy(contrast = 0.3f)))
        assertTrue(RecipeLogic.modified(r, "fj_classic_neg", adjust.copy(grain = null)))
    }

    @Test fun shutterPipelineFollowsTheRecipeSwitches() {
        assertEquals(listOf("原圖", "調整", "濾鏡", "輸出"), RecipeLogic.pipeline(make(stamp = false, frame = false)))
        assertEquals(listOf("原圖", "調整", "濾鏡", "時間戳", "輸出"), RecipeLogic.pipeline(make(stamp = true, frame = false)))
        assertEquals(listOf("原圖", "調整", "濾鏡", "邊框", "輸出"), RecipeLogic.pipeline(make(stamp = false, frame = true)))
        assertEquals(listOf("原圖", "調整", "濾鏡", "時間戳", "邊框", "輸出"), RecipeLogic.pipeline(make(stamp = true, frame = true)))
    }
}
