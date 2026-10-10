package com.example.smileproc.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class EmotionCore(val id: String, val color: String, val fine: List<String>)

data class EmotionCatalog(val version: Int, val cores: List<EmotionCore>) {
    val ids: Set<String> = cores.flatMap { core -> listOf(core.id) + core.fine }.toSet()

    fun core(id: String): EmotionCore? = cores.firstOrNull { it.id == id || id.startsWith("${it.id}.") }
}

data class Helpline(
    val name: String,
    val phone: String?,
    val sms: String?,
    val verified: Boolean,
)

data class HelplineDirectoryLink(val name: String, val url: String, val verified: Boolean)

data class HelplineDirectory(
    val regions: Map<String, List<Helpline>>,
    val emergency: Map<String, String>,
    val directory: HelplineDirectoryLink,
)

object ContentLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun patternConfig(text: String): PatternConfig {
        val obj = json.parseToJsonElement(text).jsonObject
        val missing = PatternConfig.keys.filter { it !in obj }
        require(missing.isEmpty()) { "pattern-config missing $missing" }
        fun i(key: String) = obj.getValue(key).jsonPrimitive.int
        fun d(key: String) = obj.getValue(key).jsonPrimitive.content.toDouble()
        return PatternConfig(
            version = i("version"),
            minSegment = i("minSegment"),
            minRest = i("minRest"),
            patternMinSegment = i("patternMinSegment"),
            patternMinRest = i("patternMinRest"),
            minAbsDiff = d("minAbsDiff"),
            zHint = d("zHint"),
            zPattern = d("zPattern"),
            sdFloor = d("sdFloor"),
            weekdayMinSegment = i("weekdayMinSegment"),
            weekdayMinDistinctDates = i("weekdayMinDistinctDates"),
            weekdayMinAbsDiff = d("weekdayMinAbsDiff"),
            weekdayMinDataDays = d("weekdayMinDataDays"),
            tagMinLowTagged = i("tagMinLowTagged"),
            tagMinCount = i("tagMinCount"),
            tagMinShare = d("tagMinShare"),
            tagMinShareGap = d("tagMinShareGap"),
            tagPatternCount = i("tagPatternCount"),
            tagAtPlaceMinShare = d("tagAtPlaceMinShare"),
            fineTagDominance = d("fineTagDominance"),
            shortWeekThreshold = i("shortWeekThreshold"),
            maxItems = i("maxItems"),
            maxItemsLowWeek = i("maxItemsLowWeek"),
            notEnoughDataBelow = i("notEnoughDataBelow"),
            firstWeekDays = d("firstWeekDays"),
            firstCardMinEntries = i("firstCardMinEntries"),
            lowWeekMinEntries = i("lowWeekMinEntries"),
            lowWeekShare = d("lowWeekShare"),
            lowRunLength = i("lowRunLength"),
            lowRunMinDays = i("lowRunMinDays"),
            placeRadiusMeters = d("placeRadiusMeters"),
            placeMaxAccuracyMeters = d("placeMaxAccuracyMeters"),
        )
    }

    fun emotionTags(text: String): EmotionCatalog {
        val obj = json.parseToJsonElement(text).jsonObject
        val cores = obj.getValue("cores").jsonArray.map { element ->
            val core = element.jsonObject
            EmotionCore(
                id = core.getValue("id").jsonPrimitive.content,
                color = core.getValue("color").jsonPrimitive.content,
                fine = core.getValue("fine").jsonArray.map { it.jsonPrimitive.content },
            )
        }
        return EmotionCatalog(obj.getValue("version").jsonPrimitive.int, cores)
    }

    fun patternLibrary(text: String): List<LibraryRow> {
        return json.parseToJsonElement(text).jsonArray.map { element ->
            val row = element.jsonObject
            val match = row.getValue("match").jsonObject.mapValues { it.value.jsonPrimitive.content }
            LibraryRow(
                id = row.getValue("id").jsonPrimitive.content,
                match = match,
                title = row.getValue("title").jsonPrimitive.content,
                common = row.getValue("common").jsonPrimitive.content,
                tip = row.getValue("tip").jsonPrimitive.content,
                question = row.getValue("question").jsonPrimitive.content,
            )
        }
    }

    fun helplines(text: String): HelplineDirectory {
        val obj = json.parseToJsonElement(text).jsonObject
        val regions = obj.getValue("regions").jsonObject.mapValues { (_, value) ->
            value.jsonArray.map { element ->
                val row = element.jsonObject
                Helpline(
                    name = row.getValue("name").jsonPrimitive.content,
                    phone = row["phone"]?.jsonPrimitive?.contentOrNull,
                    sms = row["sms"]?.jsonPrimitive?.contentOrNull,
                    verified = row.getValue("verified").jsonPrimitive.boolean,
                )
            }
        }
        val emergency = obj.getValue("emergency").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val directory = obj.getValue("directory").jsonObject
        return HelplineDirectory(
            regions = regions,
            emergency = emergency,
            directory = HelplineDirectoryLink(
                name = directory.getValue("name").jsonPrimitive.content,
                url = directory.getValue("url").jsonPrimitive.content,
                verified = directory.getValue("verified").jsonPrimitive.boolean,
            ),
        )
    }
}
