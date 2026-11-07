package com.example.smileproc.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Serializes [Instant] in SmileProc's canonical UTC ISO-8601 format with millisecond precision
 * and trailing `Z` (e.g. `2026-10-12T13:05:00.000Z`).
 */
object IsoInstantSerializer : KSerializer<Instant> {
    private val canonicalPattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""")

    private val formatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss.")
        .appendValue(ChronoField.MILLI_OF_SECOND, 3)
        .appendLiteral('Z')
        .toFormatter(Locale.ROOT)
        .withZone(ZoneOffset.UTC)

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.example.smileproc.core.IsoInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(format(value))
    }

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return parseOrThrow(raw)
    }

    fun format(instant: Instant): String = formatter.format(instant)

    fun parseOrThrow(raw: String): Instant {
        if (!canonicalPattern.matches(raw)) {
            throw SerializationException("Timestamp is not canonical millisecond UTC ISO-8601: $raw")
        }
        return try {
            Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            throw SerializationException("Invalid ISO-8601 timestamp: $raw", e)
        }
    }
}

/**
 * Canonical JSON codec shared by sealed database payloads, fixtures, and exports
 * (`docs/spec/data.md` › Canonical data format).
 */
object CanonicalJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        allowSpecialFloatingPointValues = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    val prettyJson: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        allowSpecialFloatingPointValues = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Strict JSON codec for runtime content files that omit optional nullable fields. */
    @OptIn(ExperimentalSerializationApi::class)
    val contentJson: Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        allowSpecialFloatingPointValues = false
    }

    fun encodeEntry(entry: Entry): String = json.encodeToString(Entry.serializer(), entry)

    fun decodeEntry(raw: String): Entry = json.decodeFromString(Entry.serializer(), raw)

    fun encodePlaceLabel(label: PlaceLabel): String = json.encodeToString(PlaceLabel.serializer(), label)

    fun decodePlaceLabel(raw: String): PlaceLabel = json.decodeFromString(PlaceLabel.serializer(), raw)
}
