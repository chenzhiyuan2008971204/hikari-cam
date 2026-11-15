package com.hikari.vision

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * What a look does to a picture, as measured numbers (tools/looks/traits.py writes them into each
 * filter json from the look's real output):
 *  contrast   change of mid-tone slope (+ = more contrast)
 *  sat        change of overall colourfulness (ratio - 1)
 *  warmth     shift of neutral grey toward warm (+) / cool (-), OKLab b x100
 *  skySat     ratio of sky-blue chroma after / before;  skyL  change of sky lightness (- = deeper)
 *  greenSat   ratio for foliage greens;  greenShift  hue shift of foliage, degrees
 *  skinSat    ratio for skin;  skinHue  hue shift of skin, degrees
 *  shadowLift output level of a deep shadow minus input (+ = opened shadows)
 *  hlSoft     0..1 how much the highlights are rolled off
 *  fade       black level;  mono 0/1;  glow  halation + bloom;  grain
 */
class LookTraits(val id: String, val name: String, val series: String, val t: Map<String, Float>) {
    operator fun get(k: String) = t[k] ?: 0f
}

class Recommendation(val look: LookTraits, val score: Int, val reason: String, val scene: SceneType?)

object FilterRecommender {
    private fun near(v: Float, target: Float, tol: Float) = exp(-((v - target) / tol) * ((v - target) / tol))
    private fun ramp(v: Float, lo: Float, hi: Float) = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)

    /** How well a look suits one scene type, 0..1, with the reason for its strongest point. */
    fun suit(type: SceneType, k: LookTraits): Pair<Float, String> {
        val mono = k["mono"] > 0.5f
        fun best(vararg parts: Triple<Float, Float, String>): Pair<Float, String> {
            val score = parts.sumOf { (it.first * it.second).toDouble() }.toFloat()
            return score to parts.maxBy { it.first * it.second }.third
        }
        return when (type) {
            SceneType.BLUE_SKY -> if (mono) 0.45f to "黑白會把藍天壓深，雲的層次更明顯" else best(
                Triple(0.35f, near(k["skySat"], 1.05f, 0.2f), "保留藍天的飽和與層次，不會變螢光"),
                Triple(0.25f, ramp(-k["skyL"], -0.004f, 0.012f), "把天空壓深一些，雲朵更立體"),
                Triple(0.2f, near(k["contrast"], 0.12f, 0.2f), "適中的反差讓晴天更通透"),
                Triple(0.2f, near(k["warmth"], 0f, 0.35f), "白色保持乾淨不偏色"))
            SceneType.FOLIAGE -> if (mono) 0.3f to "黑白下綠色會變成中灰，層次靠明暗表現" else best(
                Triple(0.5f, near(k["greenSat"], 1.0f, 0.2f), "綠色保持自然的飽和度"),
                Triple(0.3f, near(abs(k["greenShift"]), 0f, 10f), "綠色的色相不偏黃也不偏青"),
                Triple(0.2f, near(k["contrast"], 0.05f, 0.25f), "保留葉片明暗層次"))
            SceneType.PORTRAIT -> if (mono) 0.55f to "黑白人像專注在光影與表情" else best(
                Triple(0.4f, near(k["skinSat"], 0.92f, 0.12f), "膚色飽和度自然，不會過橘"),
                Triple(0.25f, near(k["skinHue"], 0f, 5f), "膚色色相準確"),
                Triple(0.2f, near(k["contrast"], -0.12f, 0.2f), "柔和的反差讓皮膚更細緻"),
                Triple(0.15f, ramp(k["shadowLift"], 0f, 0.05f), "暗部柔和提亮，臉上的陰影不會太硬"))
            SceneType.ARCHITECTURE -> if (mono) 0.7f to "黑白強調建築的線條與結構" else best(
                Triple(0.45f, near(k["contrast"], 0.18f, 0.12f), "俐落的反差突顯建築線條"),
                Triple(0.25f, near(k["sat"], -0.1f, 0.2f), "收斂的色彩讓材質更有質感"),
                Triple(0.3f, near(k["warmth"], 0f, 0.3f), "水泥與白牆維持中性不偏色"))
            SceneType.OVERCAST -> if (mono) 0.5f to "陰天顏色平淡，黑白反而更有氣氛" else best(
                Triple(0.45f, near(k["contrast"], 0.18f, 0.1f), "補上陰天平光缺少的反差"),
                Triple(0.25f, near(k["warmth"], 0.1f, 0.3f), "白平衡中性略暖，不灰冷也不發黃"),
                Triple(0.3f, near(k["sat"], -0.1f, 0.2f), "收斂的色彩在平光下更有質感"))
            SceneType.HIGH_CONTRAST, SceneType.BACKLIGHT -> best(
                Triple(0.45f, ramp(-k["contrast"], 0f, 0.15f), "較低的反差保住亮暗兩端的細節"),
                Triple(0.3f, ramp(k["shadowLift"], 0f, 0.05f), "把暗部提亮，陰影裡的細節看得見"),
                Triple(0.25f, ramp(k["hlSoft"], 0f, 0.2f), "高光柔和收尾，不會一片死白"))
            SceneType.SUNSET -> if (mono) 0.2f to "黑白會失去夕陽的顏色" else best(
                Triple(0.4f, ramp(k["warmth"], -0.2f, 0.6f), "保留並強化暖色光線"),
                Triple(0.3f, near(k["sat"], 0.05f, 0.15f), "天空的顏色飽滿但不過頭"),
                Triple(0.3f, near(k["contrast"], 0.05f, 0.2f), "保留暮色的層次"))
            SceneType.NIGHT -> best(
                Triple(0.4f, ramp(k["glow"], 0.1f, 0.8f), "燈光帶光暈，夜景更有氣氛"),
                Triple(0.3f, ramp(k["shadowLift"], -0.01f, 0.04f), "暗部不會一片死黑"),
                Triple(0.3f, near(k["contrast"], 0.1f, 0.2f), "保留燈光與黑夜的對比"))
            SceneType.INDOOR -> if (mono) 0.45f to "黑白可以避開室內混合光的偏色" else best(
                Triple(0.4f, near(k["warmth"], -0.15f, 0.35f), "不再加暖，抵消室內燈光的黃"),
                Triple(0.3f, near(k["sat"], -0.08f, 0.15f), "降低一點飽和，混合光偏色不明顯"),
                Triple(0.3f, near(k["contrast"], 0f, 0.15f), "柔和反差適合室內光線"))
        }
    }

    /** Special-effect looks (heavy glow, strong fade) only fit the scenes they were made for. */
    private fun character(scene: SceneStats, k: LookTraits): Float {
        val night = scene.types[SceneType.NIGHT] ?: 0f
        return 1f - 0.3f * ramp(k["glow"], 0.3f, 1.0f) * (1f - night) - 0.15f * ramp(k["fade"], 0.05f, 0.075f)
    }

    /** All looks scored for this scene (0..100), best first. Deterministic: ties break on id. */
    fun rank(scene: SceneStats, looks: List<LookTraits>): List<Recommendation> {
        val active = scene.types.filter { it.value >= 0.2f }
        return looks.map { k ->
            if (active.isEmpty()) {
                // nothing distinctive: prefer faithful all-round looks
                val s = (0.3f * near(k["contrast"], 0.05f, 0.2f) + 0.25f * near(k["sat"], 0f, 0.15f) + 0.25f * near(k["warmth"], 0f, 0.35f) +
                    0.2f * near(abs(k["greenShift"]), 0f, 8f)) * character(scene, k)
                Recommendation(k, (s * 100).roundToInt(), "場景沒有特別明顯的特徵，這款色彩忠實、適用範圍廣", null)
            } else {
                // a person in the picture comes first: skin decides more than anything else
                val weights = active.mapValues { (t, v) -> if (t == SceneType.PORTRAIT && scene.faces > 0) v * 2.5f else v }
                var sum = 0f; var w = 0f
                for ((t, v) in weights) { sum += v * suit(t, k).first; w += v }
                var score = sum / w * character(scene, k)
                val portrait = weights[SceneType.PORTRAIT] ?: 0f
                if (portrait >= 0.5f && k["mono"] < 0.5f) {
                    val skinOk = near(k["skinSat"], 0.92f, 0.14f) * near(k["skinHue"], 0f, 4f)
                    score *= 0.55f + 0.45f * skinOk
                }
                // the reason speaks about the scene that weighs most
                val top = weights.maxBy { it.value }.key
                Recommendation(k, (score * 100).roundToInt(), "${top.zh}：${suit(top, k).second}", top)
            }
        }.sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.look.id })
    }
}

/**
 * Keeps the shown top three steady while the picture changes a little (live preview, small edits):
 * an outsider only takes a place when it beats the weakest shown look by [margin] points.
 */
class StableTop(private val margin: Int = 5, private val size: Int = 3) {
    private var shown: List<String> = emptyList()

    fun update(ranked: List<Recommendation>): List<Recommendation> {
        val byId = ranked.associateBy { it.look.id }
        val keep = shown.mapNotNull { byId[it] }.toMutableList()
        for (r in ranked) {
            if (keep.any { it.look.id == r.look.id }) continue
            if (keep.size < size) { keep += r; continue }
            val weakest = keep.minBy { it.score }
            if (r.score >= weakest.score + margin) { keep.remove(weakest); keep += r } else break
        }
        // order only changes when the difference is clear
        val prev = shown
        val sorted = keep.sortedWith { a, b ->
            val d = b.score - a.score
            if (abs(d) >= 3) d else (prev.indexOf(a.look.id).let { if (it < 0) 99 else it }) - (prev.indexOf(b.look.id).let { if (it < 0) 99 else it })
        }
        shown = sorted.map { it.look.id }
        return sorted
    }
}
