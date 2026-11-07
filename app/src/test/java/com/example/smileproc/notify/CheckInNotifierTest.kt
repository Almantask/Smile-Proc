package com.example.smileproc.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.R
import com.example.smileproc.core.CheckInMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CheckInNotifierTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun channelCreatedWithCorrectProperties() {
        CheckInNotifier.ensureChannel(context)
        val channel = notificationManager.getNotificationChannel(CheckInNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("Check-ins", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
        assertFalse(channel.canShowBadge())
    }

    @Test
    fun buildsAllStatesWithCorrectTextsAndFlags() {
        val states = listOf(
            CheckInMachine.State.Faces(alerting = true) to context.getString(R.string.notification_text_ask),
            CheckInMachine.State.Faces(alerting = false) to context.getString(R.string.notification_text_ask),
            CheckInMachine.State.Saved to context.getString(R.string.notification_text_saved),
            CheckInMachine.State.Failed to context.getString(R.string.notification_text_failed),
            CheckInMachine.State.Test to context.getString(R.string.notification_text_ask),
            CheckInMachine.State.TestDone to context.getString(R.string.notification_text_test_done),
        )

        for ((state, expectedText) in states) {
            val notification = CheckInNotifier.build(context, state, ongoing = true)
            assertEquals("SmileProc", notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(expectedText, notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertEquals(CheckInNotifier.CHANNEL_ID, notification.channelId)
            assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
            assertEquals(Notification.CATEGORY_REMINDER, notification.category)
            assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)

            val isAlerting = (state is CheckInMachine.State.Faces && state.alerting) || state is CheckInMachine.State.Test
            if (isAlerting) {
                assertEquals(0, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
            } else {
                assertTrue((notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
            }

            val hasActions = state is CheckInMachine.State.Faces || state is CheckInMachine.State.Test
            if (hasActions) {
                assertNotNull(notification.actions)
                assertEquals(3, notification.actions.size)
                assertEquals("Low", notification.actions[0].title)
                assertEquals("Okay", notification.actions[1].title)
                assertEquals("Good", notification.actions[2].title)
                assertFalse(notification.actions[0].isAuthenticationRequired)
                assertFalse(notification.actions[1].isAuthenticationRequired)
                assertFalse(notification.actions[2].isAuthenticationRequired)
            } else {
                assertTrue(notification.actions.isNullOrEmpty())
            }
        }
    }

    @Test
    fun renderAndCancelMaintainSingleNotificationInvariant() {
        val shadow = shadowOf(notificationManager)

        // 1. Initial render -> 1 notification
        val rendered1 = CheckInNotifier.render(context, CheckInMachine.State.Faces(alerting = true), ongoing = true)
        assertTrue(rendered1)
        assertEquals(1, shadow.allNotifications.size)

        // 2. State change to Saved -> still 1 notification with ID_CHECKIN
        val rendered2 = CheckInNotifier.render(context, CheckInMachine.State.Saved, ongoing = true)
        assertTrue(rendered2)
        assertEquals(1, shadow.allNotifications.size)
        val currentNotif = shadow.allNotifications[0]
        assertEquals("Saved ✓", currentNotif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        // 3. State change to Hidden -> cancels notification, 0 notifications
        val rendered3 = CheckInNotifier.render(context, CheckInMachine.State.Hidden, ongoing = true)
        assertFalse(rendered3)
        assertEquals(0, shadow.allNotifications.size)
    }
}
