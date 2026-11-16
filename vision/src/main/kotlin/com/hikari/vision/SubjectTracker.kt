package com.hikari.vision

import kotlin.math.hypot

/**
 * Keeps the live composition guide on ONE subject. A detector returns a fresh, unordered set of
 * boxes every frame; taking "the largest" each time makes the guide jump from thing to thing.
 * The tracker locks onto a subject and follows that same one from frame to frame:
 *  - a new subject must be seen in [confirm] consecutive frames before it is taken
 *  - the locked subject is followed by overlap / nearness, not by size
 *  - when it is not detected for a moment the lock is held ([hold] frames) instead of jumping
 *  - a face takes over from an object (people come first) after [confirm] frames
 *  - another face only takes over when it is clearly bigger for [switch] frames in a row
 */
class SubjectTracker(private val confirm: Int = 3, private val hold: Int = 10, private val switch: Int = 8) {
    enum class Kind { FACE, OBJECT }
    /** [index] into the list of that kind given to [update]; -1 = not detected in this frame, lock held */
    class Pick(val kind: Kind, val index: Int, val box: Box)

    private var kind: Kind? = null
    private var box: Box? = null
    private var missing = 0
    private var pendingKind: Kind? = null
    private var pendingBox: Box? = null
    private var pendingCount = 0
    private var rivalBox: Box? = null
    private var rivalCount = 0

    fun reset() { kind = null; box = null; missing = 0; pendingBox = null; pendingCount = 0; rivalBox = null; rivalCount = 0 }

    private fun iou(a: Box, b: Box): Float {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left); val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val i = w * h
        return i / (a.area + b.area - i)
    }
    private fun same(a: Box, b: Box): Boolean {
        val d = hypot(a.cx - b.cx, a.cy - b.cy)
        val size = maxOf(a.area, b.area) / minOf(a.area, b.area).coerceAtLeast(1e-6f)
        return (iou(a, b) >= 0.3f || d <= 0.5f * minOf(hypot(a.w, a.h), hypot(b.w, b.h)).coerceAtLeast(0.06f)) && size <= 3f
    }
    private fun match(list: List<Box>, to: Box): Int {
        var best = -1; var bestD = Float.MAX_VALUE
        for ((i, b) in list.withIndex()) if (same(b, to)) { val d = hypot(b.cx - to.cx, b.cy - to.cy) - iou(b, to); if (d < bestD) { bestD = d; best = i } }
        return best
    }
    /** objects worth following: not a speck, not the whole frame (that is scenery) */
    private fun usable(b: Box) = b.area in 0.004f..0.7f
    /** which new subject would be taken: the largest face, else the object that is large and near the centre */
    private fun candidate(faces: List<Box>, objects: List<Box>): Pair<Kind, Int>? {
        if (faces.isNotEmpty()) return Kind.FACE to faces.indices.maxBy { faces[it].area }
        val ok = objects.indices.filter { usable(objects[it]) }
        if (ok.isEmpty()) return null
        return Kind.OBJECT to ok.maxBy { objects[it].area * (1f - hypot(objects[it].cx - 0.5f, objects[it].cy - 0.5f).coerceAtMost(0.7f)) }
    }
    private fun lock(k: Kind, b: Box) { kind = k; box = b; missing = 0; pendingBox = null; pendingCount = 0; rivalBox = null; rivalCount = 0 }

    fun update(faces: List<Box>, objects: List<Box>): Pick? {
        val k = kind; val cur = box
        if (k != null && cur != null) {
            val list = if (k == Kind.FACE) faces else objects
            val m = match(list, cur)
            if (m >= 0) { box = list[m]; missing = 0 } else missing++
            // who could take over
            val rival: Box? = when {
                k == Kind.OBJECT && faces.isNotEmpty() -> faces.maxBy { it.area }
                k == Kind.FACE -> faces.filterIndexed { i, _ -> i != m }.filter { it.area > 1.6f * (box?.area ?: 0f) }.maxByOrNull { it.area }
                else -> null
            }
            if (rival != null) {
                rivalCount = if (rivalBox != null && same(rival, rivalBox!!)) rivalCount + 1 else 1
                rivalBox = rival
                val need = if (k == Kind.OBJECT) confirm else switch
                if (rivalCount >= need) { lock(Kind.FACE, rival); return Pick(Kind.FACE, faces.indexOf(rival), rival) }
            } else { rivalBox = null; rivalCount = 0 }
            if (missing <= hold) return Pick(k, m, box!!)
            reset()
        }
        // nothing locked: a candidate has to stay for a few frames
        val c = candidate(faces, objects) ?: run { pendingBox = null; pendingCount = 0; return null }
        val cb = if (c.first == Kind.FACE) faces[c.second] else objects[c.second]
        pendingCount = if (pendingKind == c.first && pendingBox != null && same(cb, pendingBox!!)) pendingCount + 1 else 1
        pendingKind = c.first; pendingBox = cb
        if (pendingCount >= confirm) { lock(c.first, cb); return Pick(c.first, c.second, cb) }
        return null
    }
}
