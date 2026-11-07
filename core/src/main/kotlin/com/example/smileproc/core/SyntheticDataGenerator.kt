package com.example.smileproc.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure deterministic synthetic entry and label generator driven by an injected [Random] and [ZoneId]
 * (`android-implementation-plan.md` › M2, `.agents/rules/core.md`).
 */
object SyntheticDataGenerator {

    private val DEFAULT_HOME = LatLon(45.4642, 9.1900)
    private val DEFAULT_WORK = LatLon(45.4850, 9.2050)

    data class GeneratedDataset(
        val entries: List<Entry>,
        val labels: List<PlaceLabel>,
        val now: Instant,
        val zone: ZoneId,
    )

    /**
     * Generates [weeks] weeks of uniform random check-ins ([entriesPerWeek] per week) ending just
     * before [cardTime] on [endSunday], matching the distribution of `noise-1..3` in `generate_fixtures.py`:
     * moods uniform in `{Low, Okay, Good}`, random day/hour, ~60% Home / ~30% Work / ~10% unlocated,
     * and 0–2 random tags from [emotionTags].
     */
    fun generateUniformNoise(
        random: Random,
        zone: ZoneId,
        emotionTags: EmotionTags,
        endSunday: LocalDate = LocalDate.of(2026, 10, 18),
        weeks: Int = 4,
        entriesPerWeek: Int = 25,
        cardTime: WeeklyCardTime = WeeklyCardTime.DEFAULT,
        includeLabels: Boolean = false,
        includePreWindowAnchor: Boolean = false,
    ): GeneratedDataset {
        require(weeks >= 1 && entriesPerWeek >= 1) { "weeks and entriesPerWeek must be >= 1" }
        // Start on the Monday (weeks * 7 - 6 days) before endSunday so each week spans Mon..Sun before cardTime.
        val firstMonday = endSunday.minusDays((weeks * 7L) - 1L)
        val anchorDate = endSunday.minusDays(45)
        val labelCreated = ZonedDateTime.of(anchorDate, cardTime.toLocalTime(), zone).toInstant()

        val labels = if (includeLabels) {
            listOf(
                PlaceLabel(
                    id = randomUuid(random),
                    name = "Home",
                    kind = PlaceKind.HOME,
                    anchorLatitude = DEFAULT_HOME.latitude,
                    anchorLongitude = DEFAULT_HOME.longitude,
                    createdAt = labelCreated,
                ),
                PlaceLabel(
                    id = randomUuid(random),
                    name = "Work",
                    kind = PlaceKind.WORK,
                    anchorLatitude = DEFAULT_WORK.latitude,
                    anchorLongitude = DEFAULT_WORK.longitude,
                    createdAt = labelCreated,
                ),
            )
        } else {
            emptyList()
        }

        val entries = mutableListOf<Entry>()
        if (includePreWindowAnchor) {
            entries.add(
                Entry(
                    id = randomUuid(random),
                    createdAt = labelCreated,
                    updatedAt = labelCreated,
                    timeZoneID = zone.id,
                    mood = Mood.OKAY,
                    tags = emptyList(),
                    comment = null,
                    location = null,
                    source = EntrySource.APP,
                ),
            )
        }
        val tagCounts = intArrayOf(0, 0, 1, 2)

        for (week in 0 until weeks) {
            repeat(entriesPerWeek) {
                val dayOffset = week * 7L + random.nextInt(7).toLong()
                val date = firstMonday.plusDays(dayOffset)
                val maxHourExclusive = if (date.dayOfWeek.value == cardTime.weekday) {
                    cardTime.hour.coerceAtLeast(8)
                } else {
                    23
                }
                val hour = random.nextInt(7, maxHourExclusive)
                val minute = random.nextInt(12) * 5
                val createdAt = ZonedDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, minute, 0, 0, zone).toInstant()

                val mood = Mood.fromValue(random.nextInt(1, 4))
                val source = if (random.nextBoolean()) EntrySource.NOTIFICATION else EntrySource.APP

                val placeRoll = random.nextDouble()
                val basePlace = when {
                    placeRoll < 0.6 -> DEFAULT_HOME
                    placeRoll < 0.9 -> DEFAULT_WORK
                    else -> null
                }
                val location = basePlace?.let { jitterLocation(random, it, createdAt, source) }

                val desiredTags = tagCounts[random.nextInt(tagCounts.size)]
                val chosenTags = mutableListOf<String>()
                repeat(desiredTags) {
                    val core = emotionTags.cores[random.nextInt(emotionTags.cores.size)]
                    val options = listOf(core.id) + core.fine
                    val picked = options[random.nextInt(options.size)]
                    if (picked !in chosenTags) {
                        chosenTags.add(picked)
                    }
                }

                val updatedAt = if (chosenTags.isNotEmpty()) createdAt.plusSeconds(70) else createdAt
                entries.add(
                    Entry(
                        id = randomUuid(random),
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        timeZoneID = zone.id,
                        mood = mood,
                        tags = chosenTags,
                        comment = null,
                        location = location,
                        source = source,
                    ),
                )
            }
        }

        val sortedEntries = entries.sortedWith(compareBy<Entry> { it.createdAt }.thenBy { it.id })
        val now = ZonedDateTime.of(endSunday, cardTime.toLocalTime(), zone).plusHours(1).toInstant()

        return GeneratedDataset(
            entries = sortedEntries,
            labels = labels,
            now = now,
            zone = zone,
        )
    }

    /** Generates a deterministic UUID v4 string from [random]. */
    fun randomUuid(random: Random): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        val hex = StringBuilder(36)
        for (i in bytes.indices) {
            if (i == 4 || i == 6 || i == 8 || i == 10) hex.append('-')
            val b = bytes[i].toInt() and 0xFF
            hex.append(HEX_DIGITS[b ushr 4])
            hex.append(HEX_DIGITS[b and 0x0F])
        }
        return hex.toString()
    }

    private fun jitterLocation(
        random: Random,
        centre: LatLon,
        createdAt: Instant,
        source: EntrySource,
        maxJitterMeters: Double = 15.0,
    ): LocationSample {
        val distance = maxJitterMeters * sqrt(random.nextDouble())
        val bearingRad = Math.toRadians(random.nextDouble() * 360.0)
        val dLat = Math.toDegrees(distance * cos(bearingRad) / Geo.EARTH_RADIUS_METERS)
        val dLon = Math.toDegrees(
            distance * sin(bearingRad) / (Geo.EARTH_RADIUS_METERS * cos(Math.toRadians(centre.latitude))),
        )
        val accuracy = random.nextInt(8, 26).toDouble()
        val (kind, capturedAt) = if (source == EntrySource.NOTIFICATION) {
            LocationKind.CACHED to createdAt.minusSeconds(240)
        } else {
            LocationKind.FRESH to createdAt.plusSeconds(3)
        }
        return LocationSample(
            latitude = round6(centre.latitude + dLat),
            longitude = round6(centre.longitude + dLon),
            horizontalAccuracy = accuracy,
            capturedAt = capturedAt,
            kind = kind,
        )
    }

    private fun round6(v: Double): Double = kotlin.math.round(v * 1_000_000.0) / 1_000_000.0

    private const val HEX_DIGITS = "0123456789abcdef"
}
