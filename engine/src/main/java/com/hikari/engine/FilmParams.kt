package com.hikari.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Filter spec + adjustments -> shader uniforms and glow-chain sizes. Mirrors tools/engine/params.js
 * (used by the WebGL test harness); FilmParamsTest checks both give the same numbers.
 */
class FilmParams private constructor(
    val bloomRadius: Float, val bloomThreshold: Float,
    val halationRadius: Float, val halationThreshold: Float,
    val diffusionRadius: Float,
    val floats: Map<String, Float>,
    val vec3s: Map<String, FloatArray>,
) {
    /** Glow chain textures are sized from their blur radius, so they are the same at any output size. */
    data class ChainSize(val w: Int, val h: Int, val sigma: Float)

    val needsBloom get() = (floats["uBloom"] ?: 0f) > 0f
    val needsHalation get() = (floats["uHalation"] ?: 0f) > 0f
    val needsDiffusion get() = (floats["uSoftFocus"] ?: 0f) > 0f || (floats["uMist"] ?: 0f) > 0f ||
        (floats["uEdgeSoft"] ?: 0f) > 0f

    companion object {
        /**
         * Above a quarter strength the red glow also starts from less bright light (the threshold drops
         * by up to 0.3), so turning halation up spreads it to lamps and bright edges instead of only
         * making the few clipped pixels redder. Up to 0.25 the look's own threshold is used unchanged.
         */
        fun halationThreshold(threshold: Float, amount: Float): Float = threshold - 0.3f * ((amount - 0.25f) / 0.75f).coerceIn(0f, 1f)

        fun chainSize(radius: Float, aspect: Float): ChainSize {
            val r = max(radius, 1e-4f)
            val short = min(512, max(48, (4f / r).roundToInt()))
            val w = if (aspect >= 1f) (short * aspect).roundToInt() else short
            val h = if (aspect >= 1f) short else (short / aspect).roundToInt()
            return ChainSize(w, h, min(6f, max(0.5f, r * short)))
        }

        fun effective(spec: FilterSpec, a: Adjustments): FilmParams {
            val f = linkedMapOf(
                "uStrength" to a.strength,
                "uLutAmount" to spec.lutAmount,
                "uExposure" to a.exposure + spec.exposure,
                "uTemp" to a.temperature, "uTint" to a.tint,
                "uContrast" to a.contrast, "uSaturation" to a.saturation, "uFade" to a.fade,
                "uHighlights" to a.highlights, "uShadows" to a.shadows, "uBlacks" to a.blacks, "uWhites" to a.whites,
                "uSharpness" to a.sharpness,
                "uBloom" to (a.bloom ?: spec.bloom.amount),
                "uHalation" to (a.halation ?: spec.halation.amount),
                "uSoftFocus" to (a.softFocus ?: spec.diffusion.softFocus), "uMist" to (a.mist ?: spec.diffusion.mist),
                "uVignette" to (a.vignette ?: spec.lens.vignette), "uVignetteSoft" to spec.lens.vignetteSoft,
                "uAberration" to (a.aberration ?: spec.lens.aberration), "uEdgeSoft" to (a.edgeSoft ?: spec.lens.edgeSoft),
                "uGrain" to (a.grain ?: spec.grain.amount), "uGrainSize" to (a.grainSize ?: spec.grain.size),
                "uGrainChroma" to spec.grain.chroma, "uGrainRough" to spec.grain.rough,
                "uDust" to (a.dust ?: spec.defects.dust), "uScratches" to (a.scratches ?: spec.defects.scratches),
                "uLeak" to (a.leak ?: spec.defects.leak), "uUneven" to (a.uneven ?: spec.defects.uneven), "uDrift" to (a.drift ?: spec.defects.drift),
            )
            val v = linkedMapOf("uHalationTint" to spec.halation.tint, "uLeakColor" to spec.defects.leakColor)
            return FilmParams(
                spec.bloom.radius, spec.bloom.threshold, spec.halation.radius, halationThreshold(spec.halation.threshold, a.halation ?: spec.halation.amount),
                spec.diffusion.radius, f, v,
            )
        }
    }
}
