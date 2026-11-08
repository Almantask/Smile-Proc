package com.example.smileproc.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smileproc.SafeLog
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.StorageState
import com.example.smileproc.core.CardItem
import com.example.smileproc.core.CardPeriod
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.Confidence
import com.example.smileproc.core.DetectedPattern
import com.example.smileproc.core.Detector
import com.example.smileproc.core.Direction
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Family
import com.example.smileproc.core.LatLon
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PatternParams
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.Variant
import com.example.smileproc.core.WeeklyCard
import com.example.smileproc.core.WindowKind
import com.example.smileproc.notify.CheckInReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Debug-only broadcasts (`scripts/emu/fire.sh`), guarded by `android.permission.DUMP`.
 *
 * - `SEAL_TEST_ENTRY` (M3): seals and persists a synthetic check-in via [AppContainer] using
 *   `goAsync()`, proving `KeystoreSealer` + Room save while the device is locked.
 * - `FIRE_REMINDER`, `FIRE_RESTORE` (wired in M5) and `SEED_DEMO`, `CLEAR_DEMO` (wired in M8).
 */
class DebugEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SEAL_TEST_ENTRY -> handleSealTestEntry(context.applicationContext)
            ACTION_FIRE_REMINDER -> handleFireReminder(context.applicationContext)
            ACTION_FIRE_RESTORE -> handleFireRestore(context.applicationContext)
            ACTION_SEED_DEMO -> handleSeedDemo(context.applicationContext)
            ACTION_CLEAR_DEMO -> handleClearDemo(context.applicationContext)
            else -> SafeLog.event(SafeLog.Event.DEBUG_EVENT_NOT_WIRED)
        }
    }

    private fun handleFireReminder(appContext: Context) {
        val container = SmileProcApplication.container(appContext)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                CheckInReceiver.dispatchMachineEvent(appContext, CheckInMachine.Event.ReminderFired)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleFireRestore(appContext: Context) {
        val container = SmileProcApplication.container(appContext)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                CheckInReceiver.dispatchMachineEvent(appContext, CheckInMachine.Event.RestoreTimer)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleSealTestEntry(appContext: Context) {
        val container = SmileProcApplication.container(appContext)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (val state = container.awaitState()) {
                    is StorageState.Ready -> {
                        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                        val entry = Entry(
                            id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
                            createdAt = now,
                            updatedAt = now,
                            timeZoneID = ZoneId.systemDefault().id,
                            mood = Mood.OKAY,
                            tags = emptyList(),
                            comment = null,
                            location = null,
                            source = EntrySource.NOTIFICATION,
                        )
                        state.storage.entryRepository.save(entry)
                        SafeLog.event(SafeLog.Event.DEBUG_SEAL_SAVED)
                    }
                    else -> {
                        SafeLog.event(SafeLog.Event.DEBUG_SEAL_FAILED)
                    }
                }
            } catch (e: Exception) {
                SafeLog.failure(SafeLog.Event.DEBUG_SEAL_FAILED, e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleSeedDemo(appContext: Context) {
        val container = SmileProcApplication.container(appContext)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (val state = container.awaitState()) {
                    is StorageState.Ready -> {
                        val storage = state.storage
                        val zone = ZoneId.systemDefault()
                        val cardTime = container.settingsRepository.weeklyCardTime.first()
                        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                        val latestPeriod = CardPeriod.resolve(now, zone, cardTime)

                        // Clear any old demo data first
                        storage.weeklyCardRepository.deleteAll()
                        storage.entryRepository.deleteAll()
                        storage.placeRepository.deleteAll()

                        val seededEntries = mutableListOf<Entry>()
                        val homeLocation = LatLon(45.4642, 9.1900)
                        val workLocation = LatLon(45.4850, 9.2050)

                        // 1. Home label
                        val homeLabel = PlaceLabel(
                            id = UUID.randomUUID().toString().lowercase(Locale.ROOT),
                            name = "Home",
                            kind = PlaceKind.HOME,
                            anchorLatitude = homeLocation.latitude,
                            anchorLongitude = homeLocation.longitude,
                            createdAt = now.minus(30, ChronoUnit.DAYS),
                        )
                        storage.placeRepository.save(homeLabel)

                        fun makeEntry(
                            instant: Instant,
                            mood: Mood,
                            tags: List<String> = emptyList(),
                            location: LatLon? = null,
                        ): Entry {
                            val id = UUID.randomUUID().toString().lowercase(Locale.ROOT)
                            val loc = location?.let {
                                LocationSample(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    horizontalAccuracy = 12.0,
                                    capturedAt = instant,
                                    kind = LocationKind.FRESH,
                                )
                            }
                            return Entry(
                                id = id,
                                createdAt = instant,
                                updatedAt = instant,
                                timeZoneID = zone.id,
                                mood = mood,
                                tags = tags,
                                comment = null,
                                location = loc,
                                source = EntrySource.APP,
                            )
                        }

                        // Periods:
                        // Period 4 (Latest): Variant.NORMAL
                        // Period 3 (1 week ago): isLowWeek = true
                        // Period 2 (2 weeks ago): Variant.NOT_ENOUGH_DATA
                        // Period 1 (3 weeks ago): Variant.FIRST_WEEK

                        val p4End = latestPeriod.periodEnd
                        val p4Start = latestPeriod.periodStart
                        val p3End = p4Start
                        val p3Start = latestPeriod.startDaysBeforeEnd(14)
                        val p2End = p3Start
                        val p2Start = latestPeriod.startDaysBeforeEnd(21)
                        val p1End = p2Start
                        val p1Start = latestPeriod.startDaysBeforeEnd(28)

                        // Entries for Week 4 (Normal, 25 entries)
                        val p4FirstEntry = makeEntry(p4Start.plus(2, ChronoUnit.HOURS), Mood.OKAY, location = workLocation)
                        val p4Entries = mutableListOf(p4FirstEntry)
                        for (i in 1 until 25) {
                            val t = p4Start.plus(i * 6L, ChronoUnit.HOURS)
                            val mood = if (i % 3 == 0) Mood.LOW else if (i % 2 == 0) Mood.GOOD else Mood.OKAY
                            val loc = if (i % 2 == 0) workLocation else homeLocation
                            p4Entries.add(makeEntry(t, mood, location = loc))
                        }
                        seededEntries.addAll(p4Entries)

                        val unnamedClusterId = ClusterID("u:${p4FirstEntry.id}")

                        val card4Days = List(7) { listOf(1, 2, 1) }
                        val card4 = WeeklyCard(
                            periodStart = p4Start,
                            periodEnd = p4End,
                            windowKind = WindowKind.WEEK,
                            entryCount = 25,
                            moodCounts = listOf(6, 11, 8),
                            days = card4Days,
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
                                        nSegment = 8,
                                        nRest = 17,
                                        meanSegment = 1.375,
                                        meanRest = 2.294,
                                        diff = -0.919,
                                        z = -3.12,
                                        score = 0.919,
                                        confidence = Confidence.PATTERN,
                                        supportingEntryIDs = emptyList(),
                                    ),
                                ),
                                CardItem(
                                    patternID = "place.lower.${unnamedClusterId.value}",
                                    libraryID = "place-lower",
                                    pattern = DetectedPattern(
                                        id = "place.lower.${unnamedClusterId.value}",
                                        detector = Detector.PLACE,
                                        family = Family.PLACE,
                                        direction = Direction.LOWER,
                                        params = PatternParams(clusterID = unnamedClusterId),
                                        nSegment = 12,
                                        nRest = 13,
                                        meanSegment = 1.5,
                                        meanRest = 2.4,
                                        diff = -0.9,
                                        z = -2.8,
                                        score = 0.9,
                                        confidence = Confidence.PATTERN,
                                        supportingEntryIDs = emptyList(),
                                    ),
                                ),
                            ),
                        )
                        storage.weeklyCardRepository.save(card4)

                        // Entries for Week 3 (Low week: 12 entries, 9 Low)
                        for (i in 0 until 12) {
                            val t = p3Start.plus((i + 1) * 12L, ChronoUnit.HOURS)
                            val mood = if (i < 9) Mood.LOW else Mood.OKAY
                            val tags = if (i < 9) listOf("tired.drained") else emptyList()
                            seededEntries.add(makeEntry(t, mood, tags = tags, location = homeLocation))
                        }
                        val card3Days = List(7) { listOf(2, 0, 0) }
                        val card3 = WeeklyCard(
                            periodStart = p3Start,
                            periodEnd = p3End,
                            windowKind = WindowKind.WEEK,
                            entryCount = 12,
                            moodCounts = listOf(9, 3, 0),
                            days = card3Days,
                            isLowWeek = true,
                            variant = Variant.NORMAL,
                            items = listOf(
                                CardItem(
                                    patternID = "tagOnLow.tired",
                                    libraryID = "tired-on-low",
                                    pattern = DetectedPattern(
                                        id = "tagOnLow.tired",
                                        detector = Detector.TAG_ON_LOW,
                                        family = Family.TAG,
                                        direction = null,
                                        params = PatternParams(tag = "tired"),
                                        nSegment = 8,
                                        nRest = 4,
                                        meanSegment = null,
                                        meanRest = null,
                                        diff = 0.6,
                                        z = null,
                                        score = 0.6,
                                        confidence = Confidence.PATTERN,
                                        supportingEntryIDs = emptyList(),
                                    ),
                                ),
                            ),
                        )
                        storage.weeklyCardRepository.save(card3)

                        // Entries for Week 2 (Not enough data: 3 entries)
                        for (i in 0 until 3) {
                            val t = p2Start.plus((i + 1) * 40L, ChronoUnit.HOURS)
                            seededEntries.add(makeEntry(t, Mood.OKAY, location = homeLocation))
                        }
                        val card2 = WeeklyCard(
                            periodStart = p2Start,
                            periodEnd = p2End,
                            windowKind = WindowKind.TWO_WEEKS,
                            entryCount = 3,
                            moodCounts = listOf(0, 3, 0),
                            days = List(7) { listOf(0, if (it < 3) 1 else 0, 0) },
                            isLowWeek = false,
                            variant = Variant.NOT_ENOUGH_DATA,
                            items = listOf(
                                CardItem(
                                    patternID = "timeOfDay.higher.morning",
                                    libraryID = "time-higher",
                                    pattern = DetectedPattern(
                                        id = "timeOfDay.higher.morning",
                                        detector = Detector.TIME_OF_DAY,
                                        family = Family.TIME,
                                        direction = Direction.HIGHER,
                                        params = PatternParams(segment = "morning"),
                                        nSegment = 2,
                                        nRest = 1,
                                        meanSegment = 2.5,
                                        meanRest = 2.0,
                                        diff = 0.5,
                                        z = 1.6,
                                        score = 0.3,
                                        confidence = Confidence.EARLY_HINT,
                                        supportingEntryIDs = emptyList(),
                                    ),
                                ),
                            ),
                        )
                        storage.weeklyCardRepository.save(card2)

                        // Entries for Week 1 (First week: 7 entries)
                        for (i in 0 until 7) {
                            val t = p1Start.plus((i + 1) * 22L, ChronoUnit.HOURS)
                            seededEntries.add(makeEntry(t, Mood.GOOD, location = homeLocation))
                        }
                        val card1 = WeeklyCard(
                            periodStart = p1Start,
                            periodEnd = p1End,
                            windowKind = WindowKind.WEEK,
                            entryCount = 7,
                            moodCounts = listOf(0, 0, 7),
                            days = List(7) { listOf(0, 0, 1) },
                            isLowWeek = false,
                            variant = Variant.FIRST_WEEK,
                            items = listOf(
                                CardItem(
                                    patternID = "weekday.higher.5",
                                    libraryID = "weekday-higher",
                                    pattern = DetectedPattern(
                                        id = "weekday.higher.5",
                                        detector = Detector.WEEKDAY,
                                        family = Family.WEEKDAY,
                                        direction = Direction.HIGHER,
                                        params = PatternParams(weekday = "5"),
                                        nSegment = 4,
                                        nRest = 3,
                                        meanSegment = 2.75,
                                        meanRest = 2.15,
                                        diff = 0.6,
                                        z = 2.2,
                                        score = 0.4,
                                        confidence = Confidence.PATTERN,
                                        supportingEntryIDs = emptyList(),
                                    ),
                                ),
                            ),
                        )
                        storage.weeklyCardRepository.save(card1)

                        // Save all entries
                        storage.entryRepository.saveAll(seededEntries)

                        SafeLog.event(SafeLog.Event.DEBUG_DEMO_SEEDED)
                    }
                    else -> SafeLog.event(SafeLog.Event.DEBUG_SEAL_FAILED)
                }
            } catch (e: Exception) {
                SafeLog.failure(SafeLog.Event.DEBUG_SEAL_FAILED, e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleClearDemo(appContext: Context) {
        val container = SmileProcApplication.container(appContext)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (val state = container.awaitState()) {
                    is StorageState.Ready -> {
                        state.storage.weeklyCardRepository.deleteAll()
                        state.storage.entryRepository.deleteAll()
                        state.storage.placeRepository.deleteAll()
                        SafeLog.event(SafeLog.Event.DEBUG_DEMO_CLEARED)
                    }
                    else -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEAL_TEST_ENTRY = "com.example.smileproc.debug.SEAL_TEST_ENTRY"
        const val ACTION_FIRE_REMINDER = "com.example.smileproc.debug.FIRE_REMINDER"
        const val ACTION_FIRE_RESTORE = "com.example.smileproc.debug.FIRE_RESTORE"
        const val ACTION_SEED_DEMO = "com.example.smileproc.debug.SEED_DEMO"
        const val ACTION_CLEAR_DEMO = "com.example.smileproc.debug.CLEAR_DEMO"
    }
}
