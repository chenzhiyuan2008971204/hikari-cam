package com.hikari.vision

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The bundled sample photos with hand-made annotations (read off a 10 % grid): where the horizon is
 * or that there is none, the subject's box, faces, and what kind of scene it is.
 */
class RealPhotoTest {
    class Truth(
        val file: String,
        /** horizon y (fraction) or null = the photo has no horizon; partial = it covers under 60 % of the width */
        val horizonY: Float? = null, val partialHorizon: Boolean = false,
        val subject: Box? = null,
        val faces: List<Box> = emptyList(),
        val scene: Set<SceneType> = emptySet(),
        val hasVerticals: Boolean = false,
    )

    private val truths = listOf(
        Truth("01_sunny.jpg", horizonY = 0.575f, partialHorizon = true, subject = Box(0.40f, 0.10f, 0.52f, 0.62f), scene = setOf(SceneType.BLUE_SKY)),
        Truth("02_overcast.jpg", scene = setOf(SceneType.OVERCAST, SceneType.ARCHITECTURE), hasVerticals = true),
        Truth("03_foliage.jpg", scene = setOf(SceneType.FOLIAGE)),
        Truth("04_city.jpg", scene = setOf(SceneType.ARCHITECTURE), hasVerticals = true),
        Truth("05_portrait.jpg", faces = listOf(Box(0.33f, 0.30f, 0.72f, 0.62f)), scene = setOf(SceneType.PORTRAIT)),
        Truth("06_backlight.jpg", subject = Box(0.05f, 0.45f, 0.52f, 0.78f), scene = setOf(SceneType.BACKLIGHT, SceneType.HIGH_CONTRAST)),
        Truth("07_contrast.jpg", subject = Box(0.08f, 0.12f, 0.95f, 0.8f)),
        Truth("08_white.jpg", subject = Box(0.48f, 0.12f, 0.75f, 0.62f), hasVerticals = true),
        Truth("09_seaside.jpg", horizonY = 0.50f, scene = setOf(SceneType.BLUE_SKY, SceneType.OVERCAST)),
        Truth("10_child.jpg", faces = listOf(Box(0.42f, 0.10f, 0.95f, 0.85f)), scene = setOf(SceneType.PORTRAIT)),
        Truth("11_concrete.jpg", scene = setOf(SceneType.OVERCAST, SceneType.ARCHITECTURE), hasVerticals = true),
        // the sea horizon on the left runs on into the shoreline; the photo itself is tilted about 2 degrees
        Truth("12_beach.jpg", horizonY = 0.226f, partialHorizon = true, subject = Box(0.25f, 0.05f, 0.53f, 0.78f)),
    )

    @Test
    fun measurementsAgreeWithTheAnnotations() {
        val looks = Synth.looks()
        val sb = StringBuilder("real photos (12 bundled samples, hand annotated)\n")
        var horizonOk = 0; var horizonCases = 0; var falseHorizon = 0; var noHorizonCases = 0
        var subjHit = 0; var subjFound = 0; var subjCases = 0; var sceneOk = 0; var sceneCases = 0; var vertOk = 0; var vertCases = 0
        val problems = ArrayList<String>()
        for (t in truths) {
            val f = Synth.photo(t.file)
            val det = t.faces.map { Detection(Detection.Kind.FACE, it) }
            val r = CompositionAnalyzer.analyze(f, det)
            val fd = r.findings
            sb.append("\n${t.file}\n  scene: " + fd.scene.types.filter { it.value >= 0.2f }.entries.sortedByDescending { it.value }.joinToString { "${it.key} %.2f".format(it.value) } + "\n")
            sb.append("  sky %.2f blueSky %.2f green %.2f skin %.2f contrast %.2f warmth %+.3f structure %.2f\n".format(fd.scene.skyFrac, fd.scene.blueSkyFrac, fd.scene.greenFrac, fd.scene.skinFrac, fd.scene.contrast, fd.scene.warmth, fd.scene.structure))
            // horizon
            val h = fd.horizon
            sb.append("  horizon: " + (h?.let { "y %.3f tilt %+.1f conf %.2f".format(it.y, it.tiltDeg, it.confidence) } ?: "none") + "  (truth: ${t.horizonY ?: "none"}${if (t.partialHorizon) ", partial" else ""})\n")
            if (t.horizonY == null) { noHorizonCases++; if (h != null) { falseHorizon++; problems += "${t.file}: horizon claimed at ${h.y} but there is none" } }
            else if (!t.partialHorizon) { horizonCases++; if (h != null && abs(h.y - t.horizonY) < 0.04f && abs(h.tiltDeg) < 1.5f) horizonOk++ else problems += "${t.file}: horizon ${h?.y} vs ${t.horizonY}" }
            else if (h != null && abs(h.y - t.horizonY) > 0.05f) problems += "${t.file}: partial horizon found at the wrong place ${h.y} vs ${t.horizonY}"
            // subject from saliency (faces are passed to the analyzer as the detector would)
            if (t.subject != null) {
                subjCases++
                val s = fd.subject
                sb.append("  subject: " + (s?.let { "(%.2f, %.2f) %s conf %.2f".format(it.anchorX, it.anchorY, it.source, it.confidence) } ?: "none") + "\n")
                if (s != null) { subjFound++
                    val b = t.subject
                    if (s.anchorX in b.left..b.right && s.anchorY in b.top..b.bottom) subjHit++ else problems += "${t.file}: subject claimed at (${s.anchorX}, ${s.anchorY}) outside the annotated box" }
            }
            if (t.hasVerticals) { vertCases++; val v = fd.verticals
                sb.append("  verticals: " + (v?.let { "tilt %+.1f converge %+.1f n %d conf %.2f".format(it.tiltDeg, it.converge, it.count, it.confidence) } ?: "none") + "\n")
                if (v != null) vertOk++ }
            if (t.scene.isNotEmpty()) { sceneCases++
                val top = fd.scene.types.entries.sortedByDescending { it.value }.take(2).filter { it.value >= 0.3f }.map { it.key }
                if (top.any { it in t.scene }) sceneOk++ else problems += "${t.file}: scene $top, expected one of ${t.scene}" }
            val kinds = r.suggestions.map { it.kind }
            when (t.file) {
                "09_seaside.jpg" -> if (Suggestion.Kind.HORIZON_PLACEMENT !in kinds) problems += "${t.file}: horizon in the middle not mentioned"
                "12_beach.jpg" -> if (Suggestion.Kind.LEVEL_HORIZON !in kinds) problems += "${t.file}: 2 degree tilt not mentioned"
                "05_portrait.jpg", "10_child.jpg" -> if (kinds.any { it == Suggestion.Kind.LEADING_LINES || it == Suggestion.Kind.RULE_OF_THIRDS || it == Suggestion.Kind.LEVEL_HORIZON })
                    problems += "${t.file}: advice that does not fit a close portrait: $kinds"
                "03_foliage.jpg", "07_contrast.jpg" -> if (kinds.any { it == Suggestion.Kind.LEVEL_HORIZON || it == Suggestion.Kind.STRAIGHTEN_VERTICALS }) problems += "${t.file}: geometry advice without geometry: $kinds"
            }
            sb.append("  advice: " + (if (r.suggestions.isEmpty()) r.note else r.suggestions.joinToString(" | ") { "${it.title} (%.2f)".format(it.confidence) }) + "\n")
            val rec = FilterRecommender.rank(fd.scene, looks).take(3)
            sb.append("  filters: " + rec.joinToString(" | ") { "${it.look.name} ${it.score}" } + "  <- ${rec.first().reason}\n")
        }
        sb.append("\nsummary\n  full horizons found within 4 %% and 1.5 deg: $horizonOk / $horizonCases\n".format())
        sb.append("  false horizons on photos without one: $falseHorizon / $noHorizonCases\n")
        sb.append("  subject (saliency only): found $subjFound / $subjCases, inside the annotated box $subjHit / $subjFound\n")
        sb.append("  verticals found on architecture photos: $vertOk / $vertCases\n")
        sb.append("  scene type in the top two: $sceneOk / $sceneCases\n")
        if (problems.isNotEmpty()) sb.append("  problems:\n" + problems.joinToString("\n") { "    $it" } + "\n")
        Report.add("real", sb.toString())
        assertTrue("problems: $problems", problems.isEmpty())
        assertTrue("horizons: $problems", horizonOk == horizonCases)
        assertTrue("false horizons: $problems", falseHorizon == 0)
        assertTrue("wrong subject claims: $problems", subjHit == subjFound)
        assertTrue("scene types: $problems", sceneOk >= sceneCases - 1)
        assertTrue("verticals: $vertOk / $vertCases", vertOk >= vertCases - 1)
    }
}
