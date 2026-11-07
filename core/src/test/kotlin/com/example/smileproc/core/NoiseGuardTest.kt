package com.example.smileproc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import kotlin.random.Random

class NoiseGuardTest {

    private val config: PatternConfig =
        PatternConfig.fromJson(File(TestData.content, "pattern-config.json").readText(Charsets.UTF_8))
    private val emotionTags: EmotionTags =
        EmotionTags.fromJson(File(TestData.content, "emotion-tags.json").readText(Charsets.UTF_8))
    private val library: PatternLibrary =
        PatternLibrary.fromJson(File(TestData.content, "pattern-library.en.json").readText(Charsets.UTF_8))

    /**
     * `docs/spec/testing.md` › Automated tests:
     * 100 seeded random weeks of 25 entries -> average `pattern` items per card <= 0.3.
     * Includes a pre-window anchor entry 45 days earlier so every week runs under `Variant.NORMAL`
     * (`maxItems = 3` rather than `firstWeek`'s cap of 1) with Home/Work labels enabled.
     */
    @Test
    fun hundredSeededRandomWeeksAverageAtMostZeroPointThreePatternItemsPerCard() {
        val rome = ZoneId.of("Europe/Rome")
        val masterRng = Random(42L)
        var totalPatternItems = 0

        repeat(100) {
            val weekRng = Random(masterRng.nextLong())
            val dataset = SyntheticDataGenerator.generateUniformNoise(
                random = weekRng,
                zone = rome,
                emotionTags = emotionTags,
                weeks = 1,
                entriesPerWeek = 25,
                includeLabels = true,
                includePreWindowAnchor = true,
            )
            val result = WeeklyCardBuilder.build(
                entries = dataset.entries,
                labels = dataset.labels,
                now = dataset.now,
                zone = dataset.zone,
                config = config,
                emotionTags = emotionTags,
                library = library,
            )
            assertEquals(Variant.NORMAL, result.card.variant)
            assertEquals(25, result.card.entryCount)
            totalPatternItems += result.card.items.count { it.pattern.confidence == Confidence.PATTERN }
        }

        val averagePatternItems = totalPatternItems.toDouble() / 100.0
        assertTrue(
            "Noise guard exceeded 0.3 average pattern items per card: got $averagePatternItems ($totalPatternItems / 100)",
            averagePatternItems <= 0.3,
        )
    }
}
