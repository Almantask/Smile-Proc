package com.example.smileproc.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smileproc.AppContainer
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.LowMoodMonitor
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.PlaceResolver
import com.example.smileproc.core.ReminderSpec
import com.example.smileproc.core.WeeklyCardBuilder
import com.example.smileproc.core.toPublic
import com.example.smileproc.core.WeeklyCardTime
import com.example.smileproc.data.StoredWeeklyCard
import com.example.smileproc.location.LocationCapture
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SmileFactory<T : ViewModel>(
    private val create: () -> T,
) : ViewModelProvider.Factory {
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        val model = create()
        if (modelClass.isInstance(model)) {
            return modelClass.cast(model) ?: throw IllegalStateException("viewmodel")
        }
        throw IllegalArgumentException(modelClass.name)
    }
}

class TodayViewModel(private val app: AppContainer) : ViewModel() {
    data class State(
        val today: List<Entry> = emptyList(),
        val lowRun: Boolean = false,
        val followUpId: String? = null,
        val tagMode: String = "gradual",
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val settings = app.settings.snapshot()
            val entries = app.repository.entries()
            _state.update {
                it.copy(
                    today = entries
                        .filter { entry -> entry.createdAt.atZone(zone).toLocalDate() == today }
                        .sortedByDescending { entry -> entry.createdAt },
                    lowRun = LowMoodMonitor.armed(entries, app.config, settings.dismissedLowRunId),
                    tagMode = settings.tagPickerMode,
                )
            }
        }
    }

    fun log(mood: Mood) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val entry = app.repository.quickLog(mood, EntrySource.APP, zone, Instant.now())
            _state.update { it.copy(followUpId = entry.id) }
            refresh()
            val settings = app.settings.snapshot()
            if (settings.captureLocation) {
                val sample = app.location.captureFresh(LocationCapture.IN_APP_TIMEOUT_MS)
                if (sample != null) {
                    app.repository.attachLocationIfMissing(entry.id, sample, Instant.now())
                    refresh()
                }
            }
        }
    }

    fun dismissFollowUp() {
        _state.update { it.copy(followUpId = null) }
    }

    fun saveFollowUp(id: String, tags: List<String>, comment: String) {
        viewModelScope.launch {
            val current = app.repository.entry(id) ?: return@launch
            app.repository.upsert(
                current.copy(
                    tags = tags,
                    comment = comment,
                    updatedAt = Instant.now(),
                ),
            )
            app.settings.update { it.copy(tagPickerMode = _state.value.tagMode) }
            _state.update { it.copy(followUpId = null) }
            refresh()
        }
    }

    fun setMode(mode: String) {
        _state.update { it.copy(tagMode = mode) }
        viewModelScope.launch { app.settings.update { it.copy(tagPickerMode = mode) } }
    }

    fun dismissLowRun() {
        viewModelScope.launch {
            val entries = app.repository.entries().sortedWith(compareBy({ it.createdAt }, { it.id }))
            val latest = entries.lastOrNull()?.id
            app.settings.update { it.copy(dismissedLowRunId = latest) }
            refresh()
        }
    }
}

class HistoryViewModel(private val app: AppContainer) : ViewModel() {
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _entries.value = app.repository.entries().sortedByDescending { it.createdAt } }
    }

    fun save(entry: Entry) {
        viewModelScope.launch {
            app.repository.upsert(entry.copy(updatedAt = Instant.now()))
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            app.repository.deleteEntry(id)
            refresh()
        }
    }
}

class WeekViewModel(private val app: AppContainer) : ViewModel() {
    data class State(
        val cards: List<StoredWeeklyCard> = emptyList(),
        val weekly: WeeklyCardTime = WeeklyCardTime(7, 19, 0),
        val places: List<PlaceLabel> = emptyList(),
        val entries: List<Entry> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            app.refreshCard()
            _state.value = State(
                cards = app.repository.cards(),
                weekly = app.settings.snapshot().weeklyCard,
                places = app.repository.places(),
                entries = app.repository.entries(),
            )
        }
    }
}

class PlacesViewModel(private val app: AppContainer) : ViewModel() {
    val config: com.example.smileproc.core.PatternConfig
        get() = app.config
    data class State(
        val entries: List<Entry> = emptyList(),
        val labels: List<PlaceLabel> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = State(app.repository.entries(), app.repository.places())
        }
    }

    fun assignment() = PlaceResolver.resolve(_state.value.entries, _state.value.labels, app.config)

    fun saveLabel(label: PlaceLabel) {
        viewModelScope.launch {
            app.repository.upsertPlace(label)
            refresh()
        }
    }

    fun deleteLabel(id: String) {
        viewModelScope.launch {
            app.repository.deletePlace(id)
            refresh()
        }
    }

    fun removeLocations() {
        viewModelScope.launch {
            app.repository.removeAllLocations(Instant.now())
            refresh()
        }
    }

    fun createOrMerge(
        clusterId: String,
        name: String,
        kind: PlaceKind,
        mergeInto: PlaceLabel?,
    ) {
        viewModelScope.launch {
            val entries = app.repository.entries()
            val labels = app.repository.places()
            val resolved = PlaceResolver.resolve(entries, labels, app.config)
            val cluster = resolved.clusters.firstOrNull { it.id == clusterId } ?: return@launch
            if (mergeInto != null) {
                val members = entries.filter { it.id in cluster.memberIDs }
                val combined = PlaceResolver.combinedAnchor(
                    members.mapNotNull { it.location?.latitude },
                    members.mapNotNull { it.location?.longitude },
                    mergeInto,
                )
                app.repository.upsertPlace(
                    mergeInto.copy(anchorLatitude = combined.first, anchorLongitude = combined.second),
                )
            } else {
                app.repository.upsertPlace(
                    PlaceLabel(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        kind = kind,
                        anchorLatitude = cluster.centroidLatitude,
                        anchorLongitude = cluster.centroidLongitude,
                        createdAt = Instant.now(),
                    ),
                )
            }
            refresh()
        }
    }
}

class SettingsViewModel(private val app: AppContainer) : ViewModel() {
    private val _settings = MutableStateFlow(com.example.smileproc.data.UserSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch { app.settings.settings.collect { _settings.value = it } }
    }

    fun setCapture(enabled: Boolean) {
        viewModelScope.launch {
            app.settings.update { it.copy(captureLocation = enabled) }
        }
    }

    fun setLock(enabled: Boolean) {
        viewModelScope.launch { app.settings.update { it.copy(appLockEnabled = enabled) } }
    }

    fun setReminders(reminders: List<ReminderSpec>) {
        viewModelScope.launch {
            app.settings.update { it.copy(reminders = reminders) }
            app.reminders.rescheduleAll()
        }
    }

    fun setWeekly(time: WeeklyCardTime) {
        viewModelScope.launch {
            app.settings.update { it.copy(weeklyCard = time) }
            app.reminders.rescheduleAll()
        }
    }

    fun finishOnboarding(lock: Boolean, capture: Boolean, reminders: List<ReminderSpec>) {
        viewModelScope.launch {
            app.settings.update {
                it.copy(
                    onboardingComplete = true,
                    appLockEnabled = lock && com.example.smileproc.Biometrics.available(app.context),
                    captureLocation = capture,
                    reminders = reminders,
                )
            }
            app.reminders.rescheduleAll()
        }
    }
}

class DebugViewModel(private val app: AppContainer) : ViewModel() {
    data class Row(
        val id: String,
        val nSegment: Int,
        val nRest: Int,
        val meanSegment: String,
        val meanRest: String,
        val diff: String,
        val z: String,
        val score: String,
        val confidence: String,
    )

    private val _rows = MutableStateFlow<List<Row>>(emptyList())
    val rows = _rows.asStateFlow()
    private val _seeded = MutableStateFlow(false)
    val seeded = _seeded.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val settings = app.settings.snapshot()
            val entries = app.repository.entries()
            val card = WeeklyCardBuilder.build(
                entries,
                app.repository.places(),
                app.config,
                app.library,
                Instant.now(),
                ZoneId.systemDefault().id,
                settings.weeklyCard.weekday,
                settings.weeklyCard.hour,
                settings.weeklyCard.minute,
            )
            _rows.value = card.candidates.map { candidate ->
                val pub = candidate.toPublic()
                Row(
                    id = pub.id,
                    nSegment = pub.nSegment,
                    nRest = pub.nRest,
                    meanSegment = pub.meanSegment?.toString() ?: "",
                    meanRest = pub.meanRest?.toString() ?: "",
                    diff = pub.diff?.toString() ?: "",
                    z = pub.z?.toString() ?: "",
                    score = pub.score.toString(),
                    confidence = pub.confidence,
                )
            }
        }
    }

    fun seed() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val start = now.atZone(zone).toLocalDate().minusDays(28)
            for (day in 0 until 28) {
                val date = start.plusDays(day.toLong())
                listOf(9 to false, 15 to true).forEach { (hour, afternoon) ->
                    val stamp = java.time.ZonedDateTime.of(date, java.time.LocalTime.of(hour, 5), zone).toInstant()
                    if (!stamp.isBefore(now)) return@forEach
                    val id = "demo-%02d-%02d".format(day, hour)
                    if (app.repository.entry(id) != null) return@forEach
                    val low = afternoon && day % 2 == 0
                    val mood = if (afternoon) if (low) Mood.LOW else Mood.OKAY else Mood.GOOD
                    val home = day % 2 == 0
                    app.repository.upsert(
                        Entry(
                            id = id,
                            createdAt = stamp,
                            updatedAt = stamp,
                            timeZoneID = zone.id,
                            mood = mood,
                            tags = if (low) listOf("tired") else emptyList(),
                            comment = null,
                            location = com.example.smileproc.core.LocationSample(
                                latitude = if (home) 45.4642 else 45.48,
                                longitude = if (home) 9.19 else 9.21,
                                horizontalAccuracy = 12.0,
                                capturedAt = stamp,
                                kind = com.example.smileproc.core.LocationKind.FRESH,
                            ),
                            source = EntrySource.APP,
                        ),
                    )
                }
            }
            app.refreshCard()
            _seeded.value = true
            load()
        }
    }
}
