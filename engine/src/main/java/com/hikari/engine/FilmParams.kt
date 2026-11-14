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
                "uBloom" to (a.bloom ?: spec.bloom.amount),
                "uHalation" to (a.halation ?: spec.halation.amount),
                "uSoftFocus" to spec.diffusion.softFocus, "uMist" to spec.diffusion.mist,
                "uVignette" to (a.vignette ?: spec.lens.vignette), "uVignetteSoft" to spec.lens.vignetteSoft,
                "uAberration" to spec.lens.aberration, "uEdgeSoft" to spec.lens.edgeSoft,
                "uGrain" to (a.grain ?: spec.grain.amount), "uGrainSize" to (a.grainSize ?: spec.grain.size),
                "uGrainChroma" to spec.grain.chroma, "uGrainRough" to spec.grain.rough,
                "uDust" to spec.defects.dust, "uScratches" to spec.defects.scratches,
                "uLeak" to spec.defects.leak, "uUneven" to spec.defects.uneven, "uDrift" to spec.defects.drift,
            )
            val v = linkedMapOf("uHalationTint" to spec.halation.tint, "uLeakColor" to spec.defects.leakColor)
            return FilmParams(
                spec.bloom.radius, spec.bloom.threshold, spec.halation.radius, spec.halation.threshold,
                spec.diffusion.radius, f, v,
            )
        }
    }
}
