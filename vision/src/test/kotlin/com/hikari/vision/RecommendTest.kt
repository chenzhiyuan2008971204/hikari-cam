package com.hikari.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recommendations come from the measured scene and the measured traits of every shipped look. */
class RecommendTest {
    private val looks = Synth.looks()
    private fun top3(f: Frame, d: List<Detection> = emptyList()) = FilterRecommender.rank(SceneAnalyzer.analyze(f, d), looks).take(3)

    @Test
    fun everyShippedLookIsRankedWithTraits() {
        assertEquals(62, looks.size)
        for (l in looks) assertTrue(l.id, l.t.size >= 12)
        val r = FilterRecommender.rank(SceneAnalyzer.analyze(Synth.landscape(0.5f, 0f)), looks)
        assertEquals(62, r.size)
        assertTrue(r.all { it.score in 0..100 && it.reason.isNotBlank() })
    }

    @Test
    fun sameSceneAlwaysGivesTheSameOrder() {
        val a = FilterRecommender.rank(SceneAnalyzer.analyze(Synth.landscape(0.5f, 0f, seed = 1)), looks).map { it.look.id }
        val b = FilterRecommender.rank(SceneAnalyzer.analyze(Synth.landscape(0.5f, 0f, seed = 1)), looks).map { it.look.id }
        assertEquals(a, b)
    }

    @Test
    fun blueSkyPrefersLooksThatKeepTheSky() {
        val scene = SceneAnalyzer.analyze(Synth.landscape(0.6f, 0f))
        assertTrue("types ${scene.types}", scene.types[SceneType.BLUE_SKY]!! > 0.5f)
        val t = top3(Synth.landscape(0.6f, 0f))
        // none of the top three washes the sky out or turns it neon
        for (r in t) assertTrue("${r.look.id} skySat ${r.look["skySat"]}", r.look["mono"] > 0.5f || r.look["skySat"] in 0.85f..1.3f)
        Report.add("recommend_sky", "blue sky scene -> " + t.joinToString { "${it.look.name} ${it.score} (${it.reason})" } + "\n")
    }

    @Test
    fun portraitPrefersLooksThatAreKindToSkin() {
        val face = Detection(Detection.Kind.FACE, Box(0.35f, 0.2f, 0.65f, 0.6f))
        val t = top3(Synth.blob(0.5f, 0.4f, r = 0.2f), listOf(face))
        for (r in t) assertTrue("${r.look.id}", r.look["mono"] > 0.5f || (r.look["skinSat"] in 0.78f..1.06f && kotlin.math.abs(r.look["skinHue"]) < 3f))
        Report.add("recommend_portrait", "portrait scene -> " + t.joinToString { "${it.look.name} ${it.score} (${it.reason})" } + "\n")
    }

    @Test
    fun differentScenesGetDifferentAnswers() {
        val sky = top3(Synth.landscape(0.6f, 0f)).map { it.look.id }
        val face = Detection(Detection.Kind.FACE, Box(0.35f, 0.2f, 0.65f, 0.6f))
        val portrait = top3(Synth.blob(0.5f, 0.4f, r = 0.2f), listOf(face)).map { it.look.id }
        assertTrue("sky $sky portrait $portrait", sky != portrait)
    }

    @Test
    fun shownTopThreeDoesNotFlickerOnSmallChanges() {
        val stable = StableTop()
        val first = stable.update(FilterRecommender.rank(SceneAnalyzer.analyze(Synth.landscape(0.6f, 0f, seed = 1)), looks)).map { it.look.id }
        // the same scene with different noise, a slightly moved horizon, a slight tilt
        for ((i, f) in listOf(Synth.landscape(0.6f, 0f, seed = 2), Synth.landscape(0.58f, 0.5f, seed = 3), Synth.landscape(0.62f, -0.5f, seed = 4)).withIndex()) {
            val now = stable.update(FilterRecommender.rank(SceneAnalyzer.analyze(f), looks)).map { it.look.id }
            assertEquals("step $i", first, now)
        }
        // a really different scene does change it
        val face = Detection(Detection.Kind.FACE, Box(0.35f, 0.2f, 0.65f, 0.6f))
        val other = stable.update(FilterRecommender.rank(SceneAnalyzer.analyze(Synth.blob(0.5f, 0.4f, r = 0.2f), listOf(face)), looks)).map { it.look.id }
        assertTrue(other != first)
    }
}
