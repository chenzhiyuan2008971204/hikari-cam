package com.hikari.cam.filter

import org.json.JSONArray
import org.json.JSONObject

/** RGB triple used for tints and per-channel curves. */
data class Rgb(val r: Float, val g: Float, val b: Float) {
    fun toArray() = floatArrayOf(r, g, b)

    fun lerp(o: Rgb, t: Float) = Rgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)

    fun toJson() = JSONArray().put(r.toDouble()).put(g.toDouble()).put(b.toDouble())

    companion object {
        val ZERO = Rgb(0f, 0f, 0f)
        val ONE = Rgb(1f, 1f, 1f)
        fun fromJson(a: JSONArray?, def: Rgb) =
            if (a == null || a.length() < 3) def
            else Rgb(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())
    }
}

/**
 * Every value the shader understands. Neutral values (the defaults) leave the image unchanged,
 * which lets filter strength be a simple blend from NEUTRAL to the preset.
 */
data class FilterParams(
    val exposure: Float = 0f,      // stops, -2..2
    val contrast: Float = 0f,      // -1..1
    val saturation: Float = 0f,    // -1..1 (-1 = grey)
    val vibrance: Float = 0f,      // -1..1
    val temperature: Float = 0f,   // -1 cool .. 1 warm
    val tint: Float = 0f,          // -1 green .. 1 magenta
    val highlights: Float = 0f,    // -1..1
    val shadows: Float = 0f,       // -1..1
    val fade: Float = 0f,          // 0..1 lifted blacks
    val lift: Rgb = Rgb.ZERO,      // per channel black offset
    val gamma: Rgb = Rgb.ONE,      // per channel gamma
    val gain: Rgb = Rgb.ONE,       // per channel gain
    val shadowTint: Rgb = Rgb.ZERO,    // added to shadows (signed)
    val highlightTint: Rgb = Rgb.ZERO, // added to highlights (signed)
    val mono: Float = 0f,          // 0..1
    val grain: Float = 0f,         // 0..1
    val vignette: Float = 0f,      // 0..1
    val halation: Float = 0f,      // 0..1 red glow around highlights
    val bloom: Float = 0f,         // 0..1 soft white glow
    val leak: Float = 0f,          // 0..1 light leak
    val leakColor: Rgb = Rgb(1f, 0.45f, 0.15f),
    val lutAmount: Float = 0f,     // 0..1, only used when a LUT is attached
) {
    /** Blend from this toward [o] by t (0..1). */
    fun lerp(o: FilterParams, t: Float): FilterParams {
        fun l(a: Float, b: Float) = a + (b - a) * t
        return FilterParams(
            exposure = l(exposure, o.exposure),
            contrast = l(contrast, o.contrast),
            saturation = l(saturation, o.saturation),
            vibrance = l(vibrance, o.vibrance),
            temperature = l(temperature, o.temperature),
            tint = l(tint, o.tint),
            highlights = l(highlights, o.highlights),
            shadows = l(shadows, o.shadows),
            fade = l(fade, o.fade),
            lift = lift.lerp(o.lift, t),
            gamma = gamma.lerp(o.gamma, t),
            gain = gain.lerp(o.gain, t),
            shadowTint = shadowTint.lerp(o.shadowTint, t),
            highlightTint = highlightTint.lerp(o.highlightTint, t),
            mono = l(mono, o.mono),
            grain = l(grain, o.grain),
            vignette = l(vignette, o.vignette),
            halation = l(halation, o.halation),
            bloom = l(bloom, o.bloom),
            leak = l(leak, o.leak),
            leakColor = o.leakColor,
            lutAmount = l(lutAmount, o.lutAmount),
        )
    }

    /** Apply the user's adjustment sliders on top of a look. */
    fun plus(a: Adjustments) = copy(
        exposure = exposure + a.exposure,
        contrast = contrast + a.contrast,
        saturation = saturation + a.saturation,
        temperature = temperature + a.temperature,
        tint = tint + a.tint,
        highlights = highlights + a.highlights,
        shadows = shadows + a.shadows,
        fade = (fade + a.fade).coerceIn(0f, 1f),
        grain = (grain + a.grain).coerceIn(0f, 1f),
        vignette = (vignette + a.vignette).coerceIn(0f, 1f),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("exposure", exposure.toDouble()); put("contrast", contrast.toDouble())
        put("saturation", saturation.toDouble()); put("vibrance", vibrance.toDouble())
        put("temperature", temperature.toDouble()); put("tint", tint.toDouble())
        put("highlights", highlights.toDouble()); put("shadows", shadows.toDouble())
        put("fade", fade.toDouble()); put("lift", lift.toJson()); put("gamma", gamma.toJson())
        put("gain", gain.toJson()); put("shadowTint", shadowTint.toJson())
        put("highlightTint", highlightTint.toJson()); put("mono", mono.toDouble())
        put("grain", grain.toDouble()); put("vignette", vignette.toDouble())
        put("halation", halation.toDouble()); put("bloom", bloom.toDouble())
        put("leak", leak.toDouble()); put("leakColor", leakColor.toJson())
        put("lutAmount", lutAmount.toDouble())
    }

    companion object {
        val NEUTRAL = FilterParams()

        fun fromJson(j: JSONObject): FilterParams {
            fun f(k: String) = j.optDouble(k, 0.0).toFloat()
            return FilterParams(
                exposure = f("exposure"), contrast = f("contrast"), saturation = f("saturation"),
                vibrance = f("vibrance"), temperature = f("temperature"), tint = f("tint"),
                highlights = f("highlights"), shadows = f("shadows"), fade = f("fade"),
                lift = Rgb.fromJson(j.optJSONArray("lift"), Rgb.ZERO),
                gamma = Rgb.fromJson(j.optJSONArray("gamma"), Rgb.ONE),
                gain = Rgb.fromJson(j.optJSONArray("gain"), Rgb.ONE),
                shadowTint = Rgb.fromJson(j.optJSONArray("shadowTint"), Rgb.ZERO),
                highlightTint = Rgb.fromJson(j.optJSONArray("highlightTint"), Rgb.ZERO),
                mono = f("mono"), grain = f("grain"), vignette = f("vignette"),
                halation = f("halation"), bloom = f("bloom"), leak = f("leak"),
                leakColor = Rgb.fromJson(j.optJSONArray("leakColor"), Rgb(1f, 0.45f, 0.15f)),
                lutAmount = f("lutAmount"),
            )
        }
    }
}

/** The sliders shown in the adjustment panel. All zero = no change. */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val fade: Float = 0f,
    val grain: Float = 0f,
    val vignette: Float = 0f,
)

enum class Category(val label: String) {
    AIR("空氣感"),
    FILM("底片"),
    CINEMA("電影・夜"),
    MINE("我的"),
}

/** A named look. [lutFile] points at a .cube file in app storage for imported filters. */
data class Filter(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: Category,
    val params: FilterParams,
    val lutFile: String? = null,
)

/** Final shader input: the look blended by strength, with adjustments on top. */
fun effectiveParams(filter: Filter, strength: Float, adj: Adjustments): FilterParams =
    FilterParams.NEUTRAL.lerp(filter.params, strength.coerceIn(0f, 1f)).plus(adj)
