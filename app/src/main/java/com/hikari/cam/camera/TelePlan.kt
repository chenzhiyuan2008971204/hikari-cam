package com.hikari.cam.camera

import kotlin.math.abs

/**
 * How a zoom ratio reaches a real telephoto lens, and how the app knows the phone honoured it.
 * Pure logic (no Android classes) so it is unit tested; CameraController and the camera screen feed it
 * what the camera reports.
 *
 * Two ways to get an optical telephoto:
 *  - the phone's logical multi-camera switches to it by itself when the zoom ratio gets there:
 *    nothing to do, the capture results say so (LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
 *  - it does not: the app binds the physical lens itself (Camera2Interop physical camera id) and asks
 *    for the zoom that is left over on top of that lens.
 * If neither works (the lens ignores zoom, or the phone refuses the lens) the app falls back to the
 * logical camera's digital zoom, and says so.
 */
object TelePlan {
    /** A telephoto the app could stream: [ratio] = its 35 mm focal length over the main lens's */
    data class Tele(val option: LensOption, val ratio: Float)

    /** Physical or separately listed lenses at least 1.5x the main lens, shortest first. Hidden lenses are never chosen automatically. */
    fun teles(options: List<LensOption>, mainFocal35: Float?): List<Tele> {
        val main = mainFocal35 ?: return emptyList()
        return options.filter { it.kind != LensOption.Kind.HIDDEN && it.focal35 != null && it.focal35 / main >= 1.5f }
            .map { Tele(it, it.focal35!! / main) }
            .distinctBy { Math.round(it.ratio * 10f) }
            .sortedBy { it.ratio }
    }

    /** The telephoto that serves zoom [z] (the longest one not longer than z, with a little slack), or null: stay on the main camera. */
    fun pick(teles: List<Tele>, z: Float): Tele? = teles.lastOrNull { z >= it.ratio * 0.97f }

    /** Zoom to ask of the camera when it streams [tele] itself: what is left over after the lens's own magnification. */
    fun relativeZoom(z: Float, tele: Tele): Float = (z / tele.ratio).coerceAtLeast(1f)

    /** What the camera said it did with the last frame. */
    data class Observed(
        /** physical lens streaming behind a logical camera (null when the phone does not say) */
        val activePhysical: String?,
        val zoomRatio: Float?,
        /** width of the crop region over the width of the active array (1 = no crop) */
        val cropFraction: Float?,
    )

    /** True when the zoom the camera applied matches the one asked for (within 15 %). Unknown counts as honoured. */
    fun honoured(requested: Float, o: Observed): Boolean {
        val applied = o.zoomRatio ?: o.cropFraction?.takeIf { it > 0f }?.let { 1f / it } ?: return true
        return abs(applied - requested) <= 0.15f * requested
    }

    /** What to do at zoom [z]. */
    sealed class Step {
        /** the main camera is right (stay or go back to it) */
        object Main : Step()
        /** the phone's own logical camera already switches to this lens: keep it, only zoom */
        data class Auto(val tele: Tele) : Step()
        /** bind this lens ourselves */
        data class Physical(val tele: Tele) : Step()
    }

    /**
     * @param halSwitched what the capture results showed after we zoomed on the logical camera: true if the
     *   phone moved to [pick]'s physical lens by itself, false if it did not, null if not known yet
     * @param physicalBlocked the lens was tried and refused or ignored zoom: do not try it again
     */
    fun step(teles: List<Tele>, z: Float, halSwitched: Boolean?, physicalBlocked: Set<String> = emptySet()): Step {
        val t = pick(teles.filter { it.option.key !in physicalBlocked || halSwitched == true }, z) ?: return Step.Main
        // only a physical lens behind the logical camera can be switched to by the phone itself; a separately
        // listed camera is a different camera id and is always bound by us
        if (t.option.kind != LensOption.Kind.PHYSICAL) return Step.Physical(t)
        return when (halSwitched) {
            true -> Step.Auto(t)
            false -> Step.Physical(t)
            null -> Step.Auto(t)   // optimistic until the capture results come in
        }
    }
}
