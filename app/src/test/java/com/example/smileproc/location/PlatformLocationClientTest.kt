package com.example.smileproc.location

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.Reminder
import com.example.smileproc.data.SettingsRepository
import com.example.smileproc.data.TagPickerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLocationManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class PlatformLocationClientTest {

    private lateinit var context: Context
    private lateinit var shadowLm: ShadowLocationManager
    private lateinit var fakeSettings: FakeSettingsRepository
    private val fixedNow = Instant.parse("2026-10-12T12:00:00.000Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowLm = shadowOf(lm)
        shadowLm.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLm.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        fakeSettings = FakeSettingsRepository()
    }

    @Test
    fun returnsNullWhenCaptureLocationIsDisabled() = runTest {
        fakeSettings.setCaptureLocation(false)
        val client = PlatformLocationClient(context, fakeSettings, clock)

        val fresh = client.captureInAppFix()
        assertNull(fresh)

        val cached = client.getLastKnownFix()
        assertNull(cached)
    }

    @Test
    fun resolvesLastKnownLocationWhenWithinTenMinutesAndUnderTwoHundredMeters() = runTest {
        fakeSettings.setCaptureLocation(true)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 45.4642
            longitude = 9.1900
            accuracy = 65.0f
            time = fixedNow.minusSeconds(120).toEpochMilli() // 2 minutes ago
        }
        shadowLm.setLastKnownLocation(LocationManager.GPS_PROVIDER, loc)

        val client = PlatformLocationClient(context, fakeSettings, clock)
        val cached = client.getLastKnownFix()

        assertEquals(45.4642, cached!!.latitude, 0.0001)
        assertEquals(9.1900, cached.longitude, 0.0001)
        assertEquals(65.0, cached.horizontalAccuracy, 0.01)
        assertEquals(LocationKind.CACHED, cached.kind)
    }

    @Test
    fun rejectsLastKnownLocationWhenOlderThanTenMinutes() = runTest {
        fakeSettings.setCaptureLocation(true)
        val loc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 45.4642
            longitude = 9.1900
            accuracy = 45.0f
            time = fixedNow.minusSeconds(650).toEpochMilli() // > 10 min ago
        }
        shadowLm.setLastKnownLocation(LocationManager.GPS_PROVIDER, loc)

        val client = PlatformLocationClient(context, fakeSettings, clock)
        assertNull(client.getLastKnownFix())
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val _capture = MutableStateFlow(true)
        private val _onboarding = MutableStateFlow(false)
        private val _tagPicker = MutableStateFlow(TagPickerMode.GRADUAL)
        private val _lowRun = MutableStateFlow(false)
        private val _lowRunDismissedEntryId = MutableStateFlow<String?>(null)

        override val captureLocation: Flow<Boolean> = _capture
        override val onboardingCompleted: Flow<Boolean> = _onboarding
        override val tagPickerMode: Flow<TagPickerMode> = _tagPicker
        override val lowRunDismissed: Flow<Boolean> = _lowRun
        override val lowRunDismissedEntryId: Flow<String?> = _lowRunDismissedEntryId
        override val standing: Flow<Boolean> = MutableStateFlow(true)
        override val dismissed: Flow<Boolean> = MutableStateFlow(false)
        override val machineState: Flow<CheckInMachine.State> = MutableStateFlow(CheckInMachine.State.Hidden)
        override val reminders: Flow<List<Reminder>> = MutableStateFlow(Reminder.DEFAULT_REMINDERS)
        override val lastTestSuccess: Flow<Instant?> = MutableStateFlow(null)
        override val weeklyCardTime: Flow<com.example.smileproc.core.WeeklyCardTime> = MutableStateFlow(com.example.smileproc.core.WeeklyCardTime.DEFAULT)
        override val lastSeenWeeklyCardPeriodStart: Flow<Instant?> = MutableStateFlow(null)

        override suspend fun setCaptureLocation(enabled: Boolean) { _capture.value = enabled }
        override suspend fun setOnboardingCompleted(completed: Boolean) { _onboarding.value = completed }
        override suspend fun setTagPickerMode(mode: TagPickerMode) { _tagPicker.value = mode }
        override suspend fun setLowRunDismissed(dismissed: Boolean) { _lowRun.value = dismissed }
        override suspend fun setLowRunDismissedEntryId(entryId: String?) { _lowRunDismissedEntryId.value = entryId }
        override suspend fun setStanding(enabled: Boolean) {}
        override suspend fun setDismissed(dismissed: Boolean) {}
        override suspend fun setMachineState(state: CheckInMachine.State) {}
        override suspend fun setReminders(reminders: List<Reminder>) {}
        override suspend fun recordTestSuccess(completedAt: Instant) {}
        override suspend fun clearLastTestSuccess() {}
        override suspend fun setWeeklyCardTime(time: com.example.smileproc.core.WeeklyCardTime) {}
        override suspend fun setLastSeenWeeklyCardPeriodStart(periodStart: Instant?) {}
        override suspend fun clearAll() {}
    }
}
