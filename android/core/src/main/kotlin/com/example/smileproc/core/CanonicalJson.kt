package com.example.smileproc.core

import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object CanonicalJson {
    private val json = Json { ignoreUnknownKeys = false }

    fun entry(entry: Entry): String {
        val comment = entry.comment?.trim()?.ifBlank { null }
        return buildString {
            append("{\"id\":").append(string(entry.id.lowercase()))
            append(",\"createdAt\":").append(string(Instants.format(entry.createdAt)))
            append(",\"updatedAt\":").append(string(Instants.format(entry.updatedAt)))
            append(",\"timeZoneID\":").append(string(entry.timeZoneID))
            append(",\"mood\":").append(entry.mood.value)
            append(",\"tags\":").append(stringArray(entry.tags))
            append(",\"comment\":").append(comment?.let(::string) ?: "null")
            append(",\"location\":").append(entry.location?.let(::location) ?: "null")
            append(",\"source\":").append(string(entry.source.wire))
            append('}')
        }
    }

    fun place(place: PlaceLabel): String = buildString {
        append("{\"id\":").append(string(place.id.lowercase()))
        append(",\"name\":").append(string(place.name))
        append(",\"kind\":").append(string(place.kind.wire))
        append(",\"anchorLatitude\":").append(number(place.anchorLatitude))
        append(",\"anchorLongitude\":").append(number(place.anchorLongitude))
        append(",\"createdAt\":").append(string(Instants.format(place.createdAt)))
        append('}')
    }

    fun location(sample: LocationSample): String = buildString {
        append("{\"latitude\":").append(number(sample.latitude))
        append(",\"longitude\":").append(number(sample.longitude))
        append(",\"horizontalAccuracy\":").append(number(sample.horizontalAccuracy))
        append(",\"capturedAt\":").append(string(Instants.format(sample.capturedAt)))
        append(",\"kind\":").append(string(sample.kind.wire))
        append('}')
    }

    fun exportDocument(document: ExportDocument): String = buildString {
        append("{\"exportedAt\":").append(string(Instants.format(document.exportedAt)))
        append(",\"app\":{\"platform\":").append(string(document.app.platform))
        append(",\"version\":").append(string(document.app.version))
        append("},\"entries\":[")
        append(document.entries.joinToString(",") { entry(it) })
        append("],\"places\":[")
        append(document.places.joinToString(",") { place(it) })
        append("],\"settings\":").append(settings(document.settings))
        append('}')
    }

    fun settings(settings: ExportSettings): String = buildString {
        append("{\"reminders\":[")
        append(settings.reminders.joinToString(",") { reminder ->
            buildString {
                append("{\"id\":").append(string(reminder.id.lowercase()))
                append(",\"hour\":").append(reminder.hour)
                append(",\"minute\":").append(reminder.minute)
                append(",\"weekdays\":[")
                append(reminder.weekdays.joinToString(","))
                append("]}")
            }
        })
        append("],\"weeklyCard\":{\"weekday\":").append(settings.weeklyCard.weekday)
        append(",\"hour\":").append(settings.weeklyCard.hour)
        append(",\"minute\":").append(settings.weeklyCard.minute)
        append("},\"tagPickerMode\":").append(string(settings.tagPickerMode))
        append('}')
    }

    fun parseEntry(text: String): Entry = entryFrom(json.parseToJsonElement(text).jsonObject)

    fun parsePlace(text: String): PlaceLabel = placeFrom(json.parseToJsonElement(text).jsonObject)

    fun parseExport(text: String): ExportDocument {
        val obj = json.parseToJsonElement(text).jsonObject
        val app = obj.getValue("app").jsonObject
        val settings = obj.getValue("settings").jsonObject
        val weekly = settings.getValue("weeklyCard").jsonObject
        return ExportDocument(
            exportedAt = Instants.parse(obj.str("exportedAt")),
            app = ExportApp(app.str("platform"), app.str("version")),
            entries = obj.getValue("entries").jsonArray.map { entryFrom(it.jsonObject) },
            places = obj.getValue("places").jsonArray.map { placeFrom(it.jsonObject) },
            settings = ExportSettings(
                reminders = settings.getValue("reminders").jsonArray.map { item ->
                    val row = item.jsonObject
                    ReminderSpec(
                        id = row.str("id"),
                        hour = row.int("hour"),
                        minute = row.int("minute"),
                        weekdays = row.getValue("weekdays").jsonArray.map { it.jsonPrimitive.int },
                    )
                },
                weeklyCard = WeeklyCardTime(
                    weekday = weekly.int("weekday"),
                    hour = weekly.int("hour"),
                    minute = weekly.int("minute"),
                ),
                tagPickerMode = settings.str("tagPickerMode"),
            ),
        )
    }

    fun number(value: Double): String = pythonFloat(value)

    fun string(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch.code < 0x20) {
                    out.append("\\u").append("%04x".format(ch.code))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
        return out.toString()
    }

    private fun stringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]", separator = ",") { string(it) }

    private fun entryFrom(obj: JsonObject): Entry {
        val location = obj["location"]?.let { element ->
            if (element is kotlinx.serialization.json.JsonNull) null else locationFrom(element.jsonObject)
        }
        val comment = obj["comment"]?.let { element ->
            if (element is kotlinx.serialization.json.JsonNull) null else element.jsonPrimitive.content.ifBlank { null }
        }
        return Entry(
            id = obj.str("id"),
            createdAt = Instants.parse(obj.str("createdAt")),
            updatedAt = Instants.parse(obj.str("updatedAt")),
            timeZoneID = obj.str("timeZoneID"),
            mood = Mood.fromValue(obj.int("mood")),
            tags = obj["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            comment = comment,
            location = location,
            source = EntrySource.fromWire(obj.str("source")),
        )
    }

    private fun locationFrom(obj: JsonObject): LocationSample = LocationSample(
        latitude = obj.double("latitude"),
        longitude = obj.double("longitude"),
        horizontalAccuracy = obj.double("horizontalAccuracy"),
        capturedAt = Instants.parse(obj.str("capturedAt")),
        kind = LocationKind.fromWire(obj.str("kind")),
    )

    private fun placeFrom(obj: JsonObject): PlaceLabel = PlaceLabel(
        id = obj.str("id"),
        name = obj.str("name"),
        kind = PlaceKind.fromWire(obj.str("kind")),
        anchorLatitude = obj.double("anchorLatitude"),
        anchorLongitude = obj.double("anchorLongitude"),
        createdAt = Instants.parse(obj.str("createdAt")),
    )

    /**
     * Python json.dumps float formatting: shortest round-trip, scientific when
     * the exponent is <= -5 or >= 16, and a trailing .0 for whole numbers.
     */
    internal fun pythonFloat(value: Double): String {
        require(value.isFinite()) { "non-finite" }
        if (value == 0.0) {
            return if (1.0 / value < 0.0) "-0.0" else "0.0"
        }
        val negative = value < 0.0
        val abs = kotlin.math.abs(value)
        val decimal = BigDecimal.valueOf(abs).stripTrailingZeros()
        val digits = decimal.unscaledValue().abs().toString()
        val exp = digits.length - 1 - decimal.scale()
        val body = if (exp <= -5 || exp >= 16) {
            val mantissa = if (digits.length == 1) {
                digits
            } else {
                val fraction = digits.substring(1).trimEnd('0')
                if (fraction.isEmpty()) digits.substring(0, 1) else digits[0] + "." + fraction
            }
            val sign = if (exp >= 0) "+" else "-"
            val expAbs = kotlin.math.abs(exp)
            val expText = if (expAbs < 10) "0$expAbs" else expAbs.toString()
            mantissa + "e" + sign + expText
        } else {
            val plain = decimal.toPlainString()
            if ('.' in plain) plain else "$plain.0"
        }
        return if (negative) "-$body" else body
    }
}

private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double
