package com.hikari.vision

import kotlin.math.abs

/** Scene types the recommender and the composition advice reason about. */
enum class SceneType(val zh: String) {
    BLUE_SKY("藍天日景"), OVERCAST("陰天"), FOLIAGE("綠色植物"), PORTRAIT("人像"), ARCHITECTURE("建築／街景"),
    BACKLIGHT("逆光"), HIGH_CONTRAST("高反差"), SUNSET("日落暖光"), NIGHT("夜景／低光"), INDOOR("室內")
}

/**
 * Measured facts about a picture. Everything is a number taken from the pixels (and from the
 * detector's boxes when there are any); nothing is guessed from a description.
 */
class SceneStats(
    val meanL: Float, val p05: Float, val p50: Float, val p95: Float,
    /** share of pixels near clipping / near black */
    val highlightFrac: Float, val shadowFrac: Float,
    val meanChroma: Float,
    /** warm (+) / cool (-) balance of the near-neutral pixels (OKLab b) */
    val warmth: Float,
    val skyFrac: Float, val blueSkyFrac: Float, val greenFrac: Float, val skinFrac: Float, val neutralFrac: Float,
    /** share of straight structural edges (vertical + horizontal) among all edges */
    val structure: Float,
    val faces: Int, val faceArea: Float,
    /** fraction of pixels per hue family: red, orange/yellow, green, cyan/blue, purple/magenta */
    val hueShares: FloatArray,
    val types: Map<SceneType, Float>,
    /** sky mask (true = sky), for geometry */
    val sky: BooleanArray,
) {
    val contrast get() = p95 - p05
    /** scene types by score, strongest first, only those that clearly apply */
    val top: List<SceneType> get() = types.entries.filter { it.value >= 0.35f }.sortedByDescending { it.value }.map { it.key }
}

object SceneAnalyzer {
    private fun ramp(v: Float, lo: Float, hi: Float) = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)

    fun analyze(f: Frame, detections: List<Detection> = emptyList()): SceneStats {
        val n = f.w * f.h
        val Ls = FloatArray(n) { f.L(it) }
        val sorted = Ls.copyOf().also { it.sort() }
        fun q(p: Float) = sorted[((n - 1) * p).toInt()]
        var hl = 0; var sh = 0; var cSum = 0f; var warm = 0.0; var nNeutral = 0
        var green = 0; var skin = 0; val hues = FloatArray(5); var coloured = 0
        val skyLike = BooleanArray(n); val blue = BooleanArray(n)
        val mag = f.gradMag
        for (i in 0 until n) {
            val L = Ls[i]; val c = f.chroma(i); val h = f.hue(i)
            if (f.luma[i] > 0.94f) hl++
            if (f.luma[i] < 0.06f) sh++
            cSum += c
            if (c < 0.045f && L > 0.25f && L < 0.92f) { warm += f.lab[i * 3 + 2]; nNeutral++ }
            if (c > 0.035f) {
                coloured++
                when {
                    h < 40f || h >= 345f -> hues[0]++
                    h < 105f -> hues[1]++
                    h < 170f -> hues[2]++
                    h < 280f -> hues[3]++
                    else -> hues[4]++
                }
            }
            // foliage: green hue with real colour; pale sand and haze (bright, weak colour) do not count
            if (c > 0.04f && h in 110f..162f && L > 0.2f && !(L > 0.72f && c < 0.09f)) green++
            if (c in 0.03f..0.15f && h in 35f..78f && L in 0.4f..0.92f) skin++
            val isBlue = c > 0.02f && h in 195f..282f && L > 0.36f
            blue[i] = isBlue
            // sky candidates: blue, or bright and smooth (cloud, overcast, haze, sunset glow)
            val smooth = mag[i] < 0.03f
            val glow = c > 0.03f && (h < 95f || h > 300f) && L > 0.62f && smooth
            // clouds: grey to white with soft texture
            skyLike[i] = (isBlue && mag[i] < 0.09f) || (L > 0.72f && c < 0.05f && smooth) || (L > 0.5f && c < 0.035f && mag[i] < 0.06f) || glow
        }
        // sky = sky-like pixels connected to the top edge
        val sky = BooleanArray(n); val stack = IntArray(n); var sp = 0
        // seeds: the top 4 % of rows (scans and prints often have a thin border on the very first row)
        for (y in 0..(f.h / 25)) for (x in 0 until f.w) { val i = y * f.w + x; if (skyLike[i] && !sky[i]) { sky[i] = true; stack[sp++] = i } }
        while (sp > 0) {
            val i = stack[--sp]; val x = i % f.w; val y = i / f.w
            if (x > 0 && skyLike[i - 1] && !sky[i - 1]) { sky[i - 1] = true; stack[sp++] = i - 1 }
            if (x < f.w - 1 && skyLike[i + 1] && !sky[i + 1]) { sky[i + 1] = true; stack[sp++] = i + 1 }
            if (y < f.h - 1 && skyLike[i + f.w] && !sky[i + f.w]) { sky[i + f.w] = true; stack[sp++] = i + f.w }
            if (y > 0 && skyLike[i - f.w] && !sky[i - f.w]) { sky[i - f.w] = true; stack[sp++] = i - f.w }
        }
        var skyN = 0; var blueSky = 0
        for (i in 0 until n) if (sky[i]) { skyN++; if (blue[i]) blueSky++ }
        // structure: how much of the edge energy is horizontal or vertical (buildings, streets)
        val (gx, gy) = f.grad; var straight = 0f; var all = 0f
        for (i in 0 until n) { val m = mag[i]; if (m < 0.05f) continue
            all += m
            val ax = abs(gx[i]); val ay = abs(gy[i])
            if (ax > 4f * ay || ay > 4f * ax) straight += m }
        val faces = detections.filter { it.kind == Detection.Kind.FACE }
        val faceArea = faces.maxOfOrNull { it.box.area } ?: 0f

        val meanL = Ls.average().toFloat()
        val p05 = q(0.05f); val p50 = q(0.5f); val p95 = q(0.95f)
        val highlightFrac = hl / n.toFloat(); val shadowFrac = sh / n.toFloat()
        val meanChroma = cSum / n
        val warmth = if (nNeutral > n / 50) (warm / nNeutral).toFloat() else 0f
        val skyFrac = skyN / n.toFloat(); val blueSkyFrac = blueSky / n.toFloat()
        val greenFrac = green / n.toFloat(); val skinFrac = skin / n.toFloat()
        val structure = if (all > 0f) straight / all else 0f
        val neutralFrac = nNeutral / n.toFloat()
        if (coloured > 0) for (k in hues.indices) hues[k] /= n.toFloat()
        val warmShare = hues[0] + hues[1]

        val dark = ramp(0.42f - p50, 0f, 0.2f)
        val types = LinkedHashMap<SceneType, Float>()
        types[SceneType.BLUE_SKY] = ramp(blueSkyFrac, 0.04f, 0.22f) * (1f - dark)
        types[SceneType.OVERCAST] = ramp(skyFrac - blueSkyFrac, 0.06f, 0.25f) * ramp(0.1f - meanChroma, 0f, 0.06f) * (1f - dark) *
            (1f - ramp(warmShare, 0.25f, 0.5f))
        types[SceneType.FOLIAGE] = ramp(greenFrac, 0.1f, 0.35f)
        types[SceneType.PORTRAIT] = maxOf(ramp(faceArea, 0.008f, 0.05f), if (faces.isEmpty()) ramp(skinFrac, 0.22f, 0.45f) * 0.5f else 0f)
        types[SceneType.ARCHITECTURE] = ramp(structure, 0.5f, 0.75f) * ramp(neutralFrac, 0.2f, 0.5f) * (1f - ramp(greenFrac, 0.2f, 0.4f))
        // a bright light source in a mostly dark frame
        types[SceneType.BACKLIGHT] = ramp(highlightFrac, 0.003f, 0.03f) * maxOf(ramp(shadowFrac, 0.05f, 0.2f), ramp(0.45f - p50, 0f, 0.15f))
        types[SceneType.HIGH_CONTRAST] = ramp(p95 - p05, 0.64f, 0.82f)
        types[SceneType.SUNSET] = ramp(warmShare, 0.3f, 0.6f) * ramp(warmth, 0.01f, 0.04f) * ramp(0.75f - p50, 0f, 0.2f) * (1f - ramp(greenFrac, 0.15f, 0.3f)) *
            ramp(skyFrac, 0.03f, 0.12f)
        types[SceneType.NIGHT] = dark * ramp(0.3f - meanL, 0f, 0.12f) * (1f - ramp(highlightFrac, 0.003f, 0.03f) * 0.6f)
        types[SceneType.INDOOR] = (1f - ramp(skyFrac, 0.02f, 0.08f)) * ramp(warmth, 0.008f, 0.03f) * (1f - ramp(greenFrac, 0.08f, 0.2f)) *
            (1f - dark) * 0.8f
        return SceneStats(meanL, p05, p50, p95, highlightFrac, shadowFrac, meanChroma, warmth, skyFrac, blueSkyFrac, greenFrac,
            skinFrac, neutralFrac, structure, faces.size, faceArea, hues, types, sky)
    }
}
