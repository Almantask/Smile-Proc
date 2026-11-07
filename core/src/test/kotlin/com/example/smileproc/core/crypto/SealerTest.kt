package com.example.smileproc.core.crypto

import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.CardItem
import com.example.smileproc.core.Confidence
import com.example.smileproc.core.DetectedPattern
import com.example.smileproc.core.Detector
import com.example.smileproc.core.Direction
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Family
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PatternParams
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Variant
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WindowKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.time.Instant

class SealerTest {

    @Test
    fun sealAndOpenRoundTripEntryPlaceAndWeeklyCardWithRandomizedIv() {
        val sealer = JcaSealer.withNewKey(SecureRandom())
        assertTrue(sealer.isKeyAvailable())

        val entry = Entry(
            id = "3f0c2b9e-7d1a-4a52-9a8e-0c6f1d2e4b10",
            createdAt = Instant.parse("2026-10-12T13:05:00.000Z"),
            updatedAt = Instant.parse("2026-10-12T13:06:10.000Z"),
            timeZoneID = "Europe/Rome",
            mood = Mood.OKAY,
            tags = listOf("anxious.stressed"),
            comment = "Quiet walk after lunch",
            location = LocationSample(
                latitude = 45.4642,
                longitude = 9.19,
                horizontalAccuracy = 12.0,
                capturedAt = Instant.parse("2026-10-12T13:05:03.000Z"),
                kind = LocationKind.FRESH,
            ),
            source = EntrySource.NOTIFICATION,
        )
        val entryJson = CanonicalJson.encodeEntry(entry)
        val entryBytes = entryJson.toByteArray(Charsets.UTF_8)

        val sealed1 = sealer.seal(entry.id, entryBytes)
        val sealed2 = sealer.seal(entry.id, entryBytes)
        assertEquals(Sealer.IV_BYTES + entryBytes.size + Sealer.TAG_BYTES, sealed1.size)
        assertFalse(
            "Repeated seal of identical plaintext must generate distinct random IVs",
            sealed1.copyOfRange(0, Sealer.IV_BYTES).contentEquals(sealed2.copyOfRange(0, Sealer.IV_BYTES)),
        )
        assertFalse(
            "Sealed payload must not contain plaintext comment substring",
            sealed1.toString(Charsets.ISO_8859_1).contains("Quiet walk"),
        )

        val openedEntry = CanonicalJson.decodeEntry(sealer.openUtf8(entry.id, sealed1))
        assertEquals(entry, openedEntry)
        assertArrayEquals(entryBytes, sealer.open(entry.id, sealed2))

        val place = PlaceLabel(
            id = "11111111-2222-4333-8444-555555555555",
            name = "Office",
            kind = PlaceKind.WORK,
            anchorLatitude = 45.48,
            anchorLongitude = 9.21,
            createdAt = Instant.parse("2026-10-01T09:00:00.000Z"),
        )
        val sealedPlace = sealer.sealUtf8(place.id, CanonicalJson.encodePlaceLabel(place))
        assertEquals(place, CanonicalJson.decodePlaceLabel(sealer.openUtf8(place.id, sealedPlace)))

        val card = WeeklyCard(
            periodStart = Instant.parse("2026-10-11T17:00:00.000Z"),
            periodEnd = Instant.parse("2026-10-18T17:00:00.000Z"),
            windowKind = WindowKind.WEEK,
            entryCount = 6,
            moodCounts = listOf(2, 2, 2),
            days = listOf(
                listOf(1, 0, 0),
                listOf(1, 0, 0),
                listOf(0, 1, 0),
                listOf(0, 1, 0),
                listOf(0, 0, 1),
                listOf(0, 0, 1),
                listOf(0, 0, 0),
            ),
            isLowWeek = false,
            variant = Variant.NORMAL,
            items = listOf(
                CardItem(
                    patternID = "timeOfDay.lower.afternoon",
                    libraryID = "afternoon-dip",
                    pattern = DetectedPattern(
                        id = "timeOfDay.lower.afternoon",
                        detector = Detector.TIME_OF_DAY,
                        family = Family.TIME,
                        direction = Direction.LOWER,
                        params = PatternParams(segment = "afternoon"),
                        nSegment = 3,
                        nRest = 3,
                        meanSegment = 1.0,
                        meanRest = 3.0,
                        diff = -2.0,
                        z = -4.0,
                        score = 0.8,
                        confidence = Confidence.PATTERN,
                        supportingEntryIDs = listOf(entry.id),
                    ),
                ),
            ),
        )
        val cardRowId = card.periodStart.toEpochMilli().toString()
        val sealedCard = sealer.sealUtf8(cardRowId, CanonicalJson.encodeWeeklyCard(card))
        assertEquals(card, CanonicalJson.decodeWeeklyCard(sealer.openUtf8(cardRowId, sealedCard)))
    }

    @Test
    fun tamperWrongRowIdShortPayloadAndMissingKeyAllFailCleanly() {
        val sealer = JcaSealer.withNewKey()
        val rowA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val rowB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        val secretText = "sensitive-private-note"
        val sealed = sealer.sealUtf8(rowA, secretText)

        // 1. Wrong rowId (AAD mismatch)
        expectSealReason(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED, secretText) {
            sealer.open(rowB, sealed)
        }

        // 2. Flip one bit in IV
        val badIv = sealed.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        expectSealReason(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED, secretText) {
            sealer.open(rowA, badIv)
        }

        // 3. Flip one bit in ciphertext
        val badCipher = sealed.copyOf().also { it[Sealer.IV_BYTES] = (it[Sealer.IV_BYTES].toInt() xor 0x01).toByte() }
        expectSealReason(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED, secretText) {
            sealer.open(rowA, badCipher)
        }

        // 4. Flip one bit in authentication tag
        val badTag = sealed.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte() }
        expectSealReason(SealException.Reason.AUTHENTICATION_OR_DECRYPT_FAILED, secretText) {
            sealer.open(rowA, badTag)
        }

        // 5. Truncated payload (< 28 bytes)
        expectSealReason(SealException.Reason.PAYLOAD_TOO_SHORT, secretText) {
            sealer.open(rowA, ByteArray(Sealer.MIN_PAYLOAD_BYTES - 1))
        }

        // 6. Blank rowId
        expectSealReason(SealException.Reason.INVALID_ROW_ID, secretText) {
            sealer.seal("   ", secretText.toByteArray(Charsets.UTF_8))
        }

        // 7. Missing key
        val missingKeySealer = JcaSealer(keyProvider = { null })
        assertFalse(missingKeySealer.isKeyAvailable())
        expectSealReason(SealException.Reason.KEY_UNAVAILABLE, secretText) {
            missingKeySealer.open(rowA, sealed)
        }
    }

    private fun expectSealReason(expected: SealException.Reason, forbiddenSubstring: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected SealException($expected), nothing thrown")
        } catch (e: SealException) {
            assertEquals(expected, e.reason)
            assertFalse("Exception message must never leak plaintext", e.message.orEmpty().contains(forbiddenSubstring))
        }
    }
}
