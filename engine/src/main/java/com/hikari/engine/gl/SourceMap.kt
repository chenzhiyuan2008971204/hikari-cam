package com.hikari.engine.gl

/**
 * Where image coordinates (0..1, origin top-left) land in the source texture: tex = o + u*x + v*y.
 * Covers rotation, mirroring and cropping in one affine map.
 */
data class SourceMap(val ox: Float, val oy: Float, val xx: Float, val xy: Float, val yx: Float, val yy: Float) {
    companion object {
        /** A 2D texture uploaded from a bitmap (row 0 = top = t 0), optionally cropped and mirrored. */
        fun bitmap(cropL: Float = 0f, cropT: Float = 0f, cropR: Float = 1f, cropB: Float = 1f, mirror: Boolean = false) =
            if (!mirror) SourceMap(cropL, cropT, cropR - cropL, 0f, 0f, cropB - cropT)
            else SourceMap(cropR, cropT, cropL - cropR, 0f, 0f, cropB - cropT)

        /**
         * The live camera buffer. [rot] is CameraX's rotationDegrees (clockwise rotation that makes
         * the sensor buffer upright), [mirror] flips the upright image (selfie preview), and the
         * upright image is centre-cropped to [cropAspect] (width / height). The SurfaceTexture
         * transform matrix is not used: when the camera writes the texture directly it already
         * contains the sensor rotation, and combining both rotated the image twice.
         */
        fun camera(rot: Int, mirror: Boolean, bufferW: Int, bufferH: Int, cropAspect: Float): SourceMap {
            val r = ((rot % 360) + 360) % 360
            val upright = if (r % 180 == 0) bufferW.toFloat() / bufferH else bufferH.toFloat() / bufferW
            var su = 1f
            var sv = 1f
            if (upright > cropAspect) su = cropAspect / upright else sv = upright / cropAspect
            // image (u right, v down) -> upright (x right, y up) -> raw buffer (y up) -> texture (t down)
            fun tex(u: Float, v: Float): Pair<Float, Float> {
                var x = 0.5f + (u - 0.5f) * su
                val y = 0.5f + (0.5f - v) * sv
                if (mirror) x = 1f - x
                val (bx, by) = when (r) {
                    90 -> (1f - y) to x
                    180 -> (1f - x) to (1f - y)
                    270 -> y to (1f - x)
                    else -> x to y
                }
                return bx to (1f - by)
            }
            val o = tex(0f, 0f)
            val ux = tex(1f, 0f)
            val vy = tex(0f, 1f)
            return SourceMap(o.first, o.second, ux.first - o.first, ux.second - o.second, vy.first - o.first, vy.second - o.second)
        }
    }

    fun apply(u: Float, v: Float) = Pair(ox + u * xx + v * yx, oy + u * xy + v * yy)
}
