package com.example.smileproc.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.AppContainer
import com.example.smileproc.SmileProcApplication
import com.example.smileproc.StorageState
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.ManagedSealer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CheckInReceiverTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var container: AppContainer
    private lateinit var fakeSealer: FakeManagedSealer
    private lateinit var dbName: String

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        fakeSealer = FakeManagedSealer()
        dbName = "test-checkin-${System.nanoTime()}.db"
        container = AppContainer(
            context = context,
            sealer = fakeSealer,
            dbName = dbName,
            autoInitialize = false,
        )
        SmileProcApplication.setContainerForTest(container)
        container.initialize()
    }

    @After
    fun tearDown() = runTest {
        container.close()
        SmileProcApplication.setContainerForTest(null)
        context.deleteDatabase(dbName)
    }

    private class FakeManagedSealer : ManagedSealer {
        private var delegate: JcaSealer? = null
        override fun isKeyAvailable(): Boolean = delegate != null
        override fun ensureKeyExists() {
            if (delegate == null) delegate = JcaSealer.withNewKey()
        }
        override fun deleteKeyIfPresent() { delegate = null }
        override fun seal(rowId: String, plaintext: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).seal(rowId, plaintext)
        override fun open(rowId: String, payload: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).open(rowId, payload)
    }

    @Test
    fun tapSavesEntryAndTransitionsNotificationToSavedWithRestoreTimer() = runTest {
        val shadowNotif = shadowOf(notificationManager)
        val shadowAlarm = shadowOf(alarmManager)

        // 1. Initial resting state: standing is true -> Faces(silent)
        container.settingsRepository.setStanding(true)
        container.settingsRepository.setDismissed(false)
        CheckInNotifier.render(context, CheckInMachine.State.Faces(alerting = false), ongoing = true)
        assertEquals(1, shadowNotif.allNotifications.size)

        // 2. Tap Good action from notification
        val intent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_RATE
            putExtra(CheckInReceiver.EXTRA_MOOD, Mood.GOOD.value)
            putExtra(CheckInReceiver.EXTRA_TEST, false)
        }
        val receiver = CheckInReceiver()
        receiver.handleIntent(context, intent)

        // Check entry was sealed & persisted
        val readyState = container.awaitState() as StorageState.Ready
        val entries = readyState.storage.entryRepository.getAllOldestFirst()
        assertEquals(1, entries.size)
        assertEquals(Mood.GOOD, entries[0].mood)
        assertEquals(EntrySource.NOTIFICATION, entries[0].source)

        // Notification updated to "Saved ✓", still exactly 1 notification
        assertEquals(1, shadowNotif.allNotifications.size)
        val notif = shadowNotif.allNotifications[0]
        assertEquals("Saved ✓", notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertTrue("Saved state has no action buttons", notif.actions.isNullOrEmpty())

        // Restore alarm scheduled
        assertNotNull(shadowAlarm.nextScheduledAlarm)

        // 3. Fire restore timer -> returns to resting state (Faces silent)
        val restoreIntent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_RESTORE
        }
        receiver.handleIntent(context, restoreIntent)

        assertEquals(1, shadowNotif.allNotifications.size)
        val restoredNotif = shadowNotif.allNotifications[0]
        assertEquals("How are you right now?", restoredNotif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(3, restoredNotif.actions.size)
    }

    @Test
    fun testCheckInFlowDoesNotSaveEntryAndRecordsSuccess() = runTest {
        val shadowNotif = shadowOf(notificationManager)
        val receiver = CheckInReceiver()

        // 1. Request test check-in
        CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.TestRequested)

        // 2. 10s timer fires -> enters State.Test
        val timerIntent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_TEST_TIMER
        }
        receiver.handleIntent(context, timerIntent)

        assertEquals(1, shadowNotif.allNotifications.size)
        val testNotif = shadowNotif.allNotifications[0]
        assertEquals("How are you right now?", testNotif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(3, testNotif.actions.size)

        // 3. Tap on test notification
        val tapIntent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_RATE
            putExtra(CheckInReceiver.EXTRA_MOOD, Mood.OKAY.value)
            putExtra(CheckInReceiver.EXTRA_TEST, true)
        }
        receiver.handleIntent(context, tapIntent)

        // Assert NO entry saved
        val readyState = container.awaitState() as StorageState.Ready
        assertEquals(0, readyState.storage.entryRepository.count())

        // Assert notification shows "It works ✓"
        assertEquals(1, shadowNotif.allNotifications.size)
        val doneNotif = shadowNotif.allNotifications[0]
        assertEquals("It works ✓", doneNotif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        // Assert test success recorded
        val lastSuccess = container.settingsRepository.lastTestSuccess.first()
        assertNotNull(lastSuccess)
    }

    @Test
    fun swipeDismissAndReminderReAlertFlow() = runTest {
        val shadowNotif = shadowOf(notificationManager)
        val receiver = CheckInReceiver()

        // 1. Standing notification active
        container.settingsRepository.setStanding(true)
        container.settingsRepository.setDismissed(false)
        container.settingsRepository.setMachineState(CheckInMachine.State.Faces(alerting = false))
        CheckInNotifier.render(context, CheckInMachine.State.Faces(alerting = false), ongoing = true)
        assertEquals(1, shadowNotif.allNotifications.size)

        // 2. Swipe away -> ACTION_DISMISSED
        val dismissIntent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_DISMISSED
        }
        receiver.handleIntent(context, dismissIntent)

        // Notification cancelled, dismissed set to true
        assertEquals(0, shadowNotif.allNotifications.size)
        assertTrue(container.settingsRepository.dismissed.first())

        // 3. Reminder fires -> re-alerts and clears dismissed
        CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.ReminderFired)

        assertEquals(1, shadowNotif.allNotifications.size)
        assertFalse(container.settingsRepository.dismissed.first())
        val alertingNotif = shadowNotif.allNotifications[0]
        assertEquals(0, alertingNotif.flags and Notification.FLAG_ONLY_ALERT_ONCE)

        // 4. In-app rating -> restores to silent resting state
        CheckInReceiver.dispatchMachineEvent(context, CheckInMachine.Event.RatedInApp)

        assertEquals(1, shadowNotif.allNotifications.size)
        val restingNotif = shadowNotif.allNotifications[0]
        assertTrue((restingNotif.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
    }

    @Test
    fun tapSaveFailureTransitionsToFailedAndSchedulesRestore() = runTest {
        val shadowNotif = shadowOf(notificationManager)
        val shadowAlarm = shadowOf(alarmManager)

        container.settingsRepository.setStanding(true)
        container.settingsRepository.setDismissed(false)
        container.settingsRepository.setMachineState(CheckInMachine.State.Faces(alerting = true))
        CheckInNotifier.render(context, CheckInMachine.State.Faces(alerting = true), ongoing = true)
        assertEquals(1, shadowNotif.allNotifications.size)

        // Delete sealer key to cause storage save to fail
        fakeSealer.deleteKeyIfPresent()

        val intent = Intent(context, CheckInReceiver::class.java).apply {
            action = CheckInReceiver.ACTION_RATE
            putExtra(CheckInReceiver.EXTRA_MOOD, Mood.OKAY.value)
            putExtra(CheckInReceiver.EXTRA_TEST, false)
        }
        val receiver = CheckInReceiver()
        receiver.handleIntent(context, intent)

        // Notification updated to "Couldn't save. Open SmileProc.", no buttons
        assertEquals(1, shadowNotif.allNotifications.size)
        val notif = shadowNotif.allNotifications[0]
        assertEquals("Couldn't save. Open SmileProc.", notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertTrue("Failed state has no action buttons", notif.actions.isNullOrEmpty())

        // State is Failed
        val state = container.settingsRepository.machineState.first()
        assertEquals(CheckInMachine.State.Failed, state)

        // Restore alarm scheduled
        assertNotNull(shadowAlarm.nextScheduledAlarm)
    }
}
