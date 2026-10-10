package com.example.smileproc.data

import com.example.smileproc.core.CardItem
import com.example.smileproc.core.WeeklyCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredCardItem(
    val patternID: String,
    val libraryID: String,
    val detector: String,
    val direction: String? = null,
    val segment: String? = null,
    val weekday: String? = null,
    val clusterID: String? = null,
    val placeKind: String? = null,
    val tag: String? = null,
    val nSegment: Int,
    val confidence: String,
)

@Serializable
data class StoredWeeklyCard(
    val periodStart: String,
    val periodEnd: String,
    val windowKind: String,
    val entryCount: Int,
    val moodCounts: List<Int>,
    val days: List<List<Int>>,
    val isLowWeek: Boolean,
    val variant: String,
    val items: List<StoredCardItem>,
)

object StoredCards {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun from(card: WeeklyCard): StoredWeeklyCard = StoredWeeklyCard(
        periodStart = com.example.smileproc.core.Instants.format(card.periodStart),
        periodEnd = com.example.smileproc.core.Instants.format(card.periodEnd),
        windowKind = card.windowKind,
        entryCount = card.entryCount,
        moodCounts = card.moodCounts,
        days = card.days,
        isLowWeek = card.isLowWeek,
        variant = card.variant,
        items = card.items.map { it.toStored() },
    )

    fun encode(card: StoredWeeklyCard): String = json.encodeToString(card)

    fun decode(text: String): StoredWeeklyCard = json.decodeFromString(text)
}

private fun CardItem.toStored(): StoredCardItem = StoredCardItem(
    patternID = patternID,
    libraryID = libraryID,
    detector = pattern.detector,
    direction = pattern.direction,
    segment = pattern.params["segment"],
    weekday = pattern.params["weekday"],
    clusterID = pattern.params["clusterID"],
    placeKind = pattern.params["placeKind"],
    tag = pattern.params["tag"],
    nSegment = pattern.nSegment,
    confidence = pattern.confidence,
)
