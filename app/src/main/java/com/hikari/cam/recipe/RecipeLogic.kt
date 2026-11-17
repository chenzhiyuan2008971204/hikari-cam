package com.hikari.cam.recipe

import com.hikari.data.FilmRecipe
import com.hikari.engine.Adjustments

/**
 * Camera recipes (C1..C7): turning the camera's current state into a recipe and a recipe back into what the
 * engine renders. Pure functions, unit tested.
 */
object RecipeLogic {
    const val SLOTS = 7

    fun label(slot: Int) = if (slot in 1..SLOTS) "C$slot" else "預設"

    /** "C2 街拍負片", or just "C2" for a recipe without a name */
    fun title(r: FilmRecipe) = if (r.name.isBlank()) label(r.slot) else "${label(r.slot)} ${r.name}"

    /** The adjustments a recipe renders with: its other adjustments plus the five fields it keeps in their own columns. */
    fun adjustments(r: FilmRecipe): Adjustments =
        Adjustments.fromJson(r.adjustJson).copy(
            strength = r.strength.coerceIn(0f, 1.5f), exposure = r.exposure.coerceIn(-2f, 2f),
            grain = r.grain, halation = r.halation, vignette = r.vignette,
        )

    /** The adjustments to store with the five dedicated fields back at their defaults. */
    fun otherAdjustments(a: Adjustments): Adjustments = a.copy(strength = 1f, exposure = 0f, grain = null, halation = null, vignette = null)

    /**
     * A recipe from the camera's (or the editor's) current state.
     * [stampSpec] / [frameOptions] are the encoded studio settings; [stampOn] / [frameOn] say whether the shutter applies them.
     */
    fun make(
        slot: Int, name: String, lookId: String, adjust: Adjustments,
        stampOn: Boolean, stampSpec: String, frameOn: Boolean, frameOptions: String,
        saveClean: Boolean, now: Long,
    ): FilmRecipe {
        require(slot in 1..SLOTS) { "slot $slot" }
        return FilmRecipe(
            slot = slot, name = name.trim().take(24), lookId = lookId,
            strength = adjust.strength.coerceIn(0f, 1.5f), exposure = adjust.exposure.coerceIn(-2f, 2f),
            grain = adjust.grain, halation = adjust.halation, vignette = adjust.vignette,
            adjustJson = otherAdjustments(adjust).toJson(),
            stampOn = stampOn, stampSpec = stampSpec, frameOn = frameOn, frameOptions = frameOptions,
            saveClean = saveClean, updatedAt = now,
        )
    }

    /** True when the camera's current look and adjustments no longer match the recipe that is selected (it was changed by hand). */
    fun modified(r: FilmRecipe, lookId: String, adjust: Adjustments): Boolean =
        r.lookId != lookId || adjustments(r).toJson() != adjust.toJson()

    /** What the shutter does for this recipe, in pipeline order (for the settings text and the tests). */
    fun pipeline(r: FilmRecipe): List<String> = buildList {
        add("原圖"); add("調整"); add("濾鏡")
        if (r.stampOn) add("時間戳")
        if (r.frameOn) add("邊框")
        add("輸出")
    }
}
