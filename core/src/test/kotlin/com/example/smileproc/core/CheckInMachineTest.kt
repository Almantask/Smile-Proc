package com.example.smileproc.core

import com.example.smileproc.core.CheckInMachine.Effect
import com.example.smileproc.core.CheckInMachine.Event
import com.example.smileproc.core.CheckInMachine.ID_CHECKIN
import com.example.smileproc.core.CheckInMachine.NotificationCopyKey
import com.example.smileproc.core.CheckInMachine.Settings
import com.example.smileproc.core.CheckInMachine.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

class CheckInMachineTest {

    private val now: Instant = Instant.parse("2026-10-12T13:05:00.000Z")
    private val stringsByName: Map<String, String> = run {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(TestData.strings)
        val out = mutableMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.tagName == "string") {
                out[node.getAttribute("name")] = node.textContent
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
            }
        }
        out
    }
    private val allowedTexts = setOf(
        "How are you right now?",
        "Saved ✓",
        "It works ✓",
        "Couldn't save. Open SmileProc.",
    )

    @Test
    fun everySpecTransitionRowBehavesAsSpecified() {
        // 1. ReminderFired -> Faces(alerting), dismissed = false, cancel restore timer
        val tReminder = CheckInMachine.reduce(
            state = State.Hidden,
            settings = Settings(standing = true, dismissed = true),
            event = Event.ReminderFired,
            now = now,
        )
        assertEquals(State.Faces(alerting = true), tReminder.state)
        assertFalse(tReminder.settings.dismissed)
        assertTrue(tReminder.effects.contains(Effect.CancelRestoreTimer))
        assertTrue(tReminder.effects.contains(Effect.PostNotification(ID_CHECKIN, State.Faces(true), ongoing = true)))

        // 2. Tap(mood) on Faces -> Saved, insert entry (source = notification), restore timer 60s
        val tTapSaved = CheckInMachine.reduce(
            state = State.Faces(alerting = false),
            settings = Settings(standing = true),
            event = Event.Tap(Mood.GOOD),
            now = now,
        )
        assertEquals(State.Saved, tTapSaved.state)
        assertTrue(tTapSaved.effects.contains(Effect.InsertEntry(Mood.GOOD, now, EntrySource.NOTIFICATION)))
        assertTrue(tTapSaved.effects.contains(Effect.ScheduleRestoreTimer(60L)))

        // 3. save throws -> Failed, restore timer 60s
        val tSaveFailed = CheckInMachine.reduce(
            state = State.Faces(alerting = false),
            settings = Settings(standing = true),
            event = Event.Tap(Mood.LOW, saveSucceeded = false),
            now = now,
        )
        assertEquals(State.Failed, tSaveFailed.state)
        assertFalse(tSaveFailed.effects.any { it is Effect.InsertEntry })
        assertTrue(tSaveFailed.effects.contains(Effect.ScheduleRestoreTimer(60L)))

        // Also via Event.SaveFailed
        val tExplicitFail = CheckInMachine.reduce(State.Saved, Settings(standing = true), Event.SaveFailed, now)
        assertEquals(State.Failed, tExplicitFail.state)
        assertTrue(tExplicitFail.effects.contains(Effect.ScheduleRestoreTimer(60L)))

        // 4. Tap(mood) on Test -> TestDone, record test success, no entry, restore timer 60s
        val tTapTest = CheckInMachine.reduce(
            state = State.Test,
            settings = Settings(standing = true, rememberedRestingState = State.Faces(false)),
            event = Event.Tap(Mood.OKAY),
            now = now,
        )
        assertEquals(State.TestDone, tTapTest.state)
        assertTrue(tTapTest.effects.contains(Effect.RecordTestSuccess(now)))
        assertFalse(tTapTest.effects.any { it is Effect.InsertEntry })
        assertTrue(tTapTest.effects.contains(Effect.ScheduleRestoreTimer(60L)))

        // 5. RestoreTimer -> resting state
        val tRestoreStanding = CheckInMachine.reduce(State.Saved, Settings(standing = true, dismissed = false), Event.RestoreTimer, now)
        assertEquals(State.Faces(alerting = false), tRestoreStanding.state)
        val tRestoreDismissed = CheckInMachine.reduce(State.Saved, Settings(standing = true, dismissed = true), Event.RestoreTimer, now)
        assertEquals(State.Hidden, tRestoreDismissed.state)

        // 6. RatedInApp while Faces(alerting) -> resting state; while Faces(silent) -> unchanged
        val tRatedAlerting = CheckInMachine.reduce(State.Faces(alerting = true), Settings(standing = false), Event.RatedInApp, now)
        assertEquals(State.Hidden, tRatedAlerting.state)
        val tRatedSilent = CheckInMachine.reduce(State.Faces(alerting = false), Settings(standing = true), Event.RatedInApp, now)
        assertEquals(State.Faces(alerting = false), tRatedSilent.state)
        assertTrue(tRatedSilent.effects.isEmpty())

        // 7. Dismissed -> Hidden; dismissed = true if standing
        val tDismissStanding = CheckInMachine.reduce(State.Faces(false), Settings(standing = true, dismissed = false), Event.Dismissed, now)
        assertEquals(State.Hidden, tDismissStanding.state)
        assertTrue(tDismissStanding.settings.dismissed)
        val tDismissNonStanding = CheckInMachine.reduce(State.Faces(true), Settings(standing = false, dismissed = false), Event.Dismissed, now)
        assertEquals(State.Hidden, tDismissNonStanding.state)
        assertFalse(tDismissNonStanding.settings.dismissed)

        // 8. StandingTurnedOn -> resting state if Hidden; dismissed = false
        val tStandingOnFromHidden = CheckInMachine.reduce(State.Hidden, Settings(standing = false, dismissed = true), Event.StandingTurnedOn, now)
        assertEquals(State.Faces(alerting = false), tStandingOnFromHidden.state)
        assertTrue(tStandingOnFromHidden.settings.standing)
        assertFalse(tStandingOnFromHidden.settings.dismissed)

        val tStandingOnFromSaved = CheckInMachine.reduce(State.Saved, Settings(standing = false, dismissed = true), Event.StandingTurnedOn, now)
        assertEquals(State.Saved, tStandingOnFromSaved.state)
        assertFalse(tStandingOnFromSaved.settings.dismissed)

        // 9. StandingTurnedOff -> Hidden if Faces(silent); else unchanged
        val tStandingOffSilent = CheckInMachine.reduce(State.Faces(false), Settings(standing = true), Event.StandingTurnedOff, now)
        assertEquals(State.Hidden, tStandingOffSilent.state)
        val tStandingOffAlerting = CheckInMachine.reduce(State.Faces(true), Settings(standing = true), Event.StandingTurnedOff, now)
        assertEquals(State.Faces(true), tStandingOffAlerting.state)

        // 10. TestRequested -> unchanged, remember resting state, 10s timer -> TestTimerFired -> Test -> Tap -> TestDone -> RestoreTimer -> remembered resting state
        val tTestReq = CheckInMachine.reduce(State.Hidden, Settings(standing = true, dismissed = true), Event.TestRequested, now)
        assertEquals(State.Hidden, tTestReq.state)
        assertEquals(State.Hidden, tTestReq.settings.rememberedRestingState)
        assertTrue(tTestReq.effects.contains(Effect.ScheduleTestTimer(10L)))

        val tTestFire = CheckInMachine.reduce(tTestReq.state, tTestReq.settings, Event.TestTimerFired, now.plusSeconds(10))
        assertEquals(State.Test, tTestFire.state)
        val tTestTap = CheckInMachine.reduce(tTestFire.state, tTestFire.settings, Event.Tap(Mood.GOOD), now.plusSeconds(12))
        assertEquals(State.TestDone, tTestTap.state)
        val tTestRestore = CheckInMachine.reduce(tTestTap.state, tTestTap.settings, Event.RestoreTimer, now.plusSeconds(72))
        assertEquals(State.Hidden, tTestRestore.state)

        // 11. Booted / AppUpdated -> resting state + reschedule alarms
        for (ev in listOf(Event.Booted, Event.AppUpdated)) {
            val tBoot = CheckInMachine.reduce(State.Saved, Settings(standing = true, dismissed = false), ev, now)
            assertEquals(State.Faces(alerting = false), tBoot.state)
            assertTrue(tBoot.effects.contains(Effect.RescheduleAlarms))
        }
    }

    @Test
    fun propertyRandomEventSequencesEmitAtMostOneNotificationActionOnIdCheckinAndSafeCopy() {
        val rng = Random(20261012L)
        val eventPool = listOf(
            Event.ReminderFired,
            Event.Tap(Mood.LOW, saveSucceeded = true),
            Event.Tap(Mood.OKAY, saveSucceeded = true),
            Event.Tap(Mood.GOOD, saveSucceeded = true),
            Event.Tap(Mood.LOW, saveSucceeded = false),
            Event.SaveFailed,
            Event.RestoreTimer,
            Event.RatedInApp,
            Event.Dismissed,
            Event.StandingTurnedOn,
            Event.StandingTurnedOff,
            Event.TestRequested,
            Event.TestTimerFired,
            Event.Booted,
            Event.AppUpdated,
        )

        assertNull(CheckInMachine.notificationCopyKey(State.Hidden))
        assertEquals(NotificationCopyKey.ASK, CheckInMachine.notificationCopyKey(State.Faces(false)))
        assertEquals(NotificationCopyKey.ASK, CheckInMachine.notificationCopyKey(State.Faces(true)))
        assertEquals(NotificationCopyKey.ASK, CheckInMachine.notificationCopyKey(State.Test))
        assertEquals(NotificationCopyKey.SAVED, CheckInMachine.notificationCopyKey(State.Saved))
        assertEquals(NotificationCopyKey.TEST_DONE, CheckInMachine.notificationCopyKey(State.TestDone))
        assertEquals(NotificationCopyKey.FAILED, CheckInMachine.notificationCopyKey(State.Failed))

        repeat(1_000) {
            var state: State = if (rng.nextBoolean()) State.Hidden else State.Faces(alerting = false)
            var settings = Settings(standing = rng.nextBoolean(), dismissed = rng.nextBoolean())
            var clock = now

            repeat(50) {
                val event = eventPool[rng.nextInt(eventPool.size)]
                clock = clock.plusSeconds(rng.nextLong(1, 120))
                val transition = CheckInMachine.reduce(state, settings, event, clock)

                val notifEffects = transition.effects.filterIsInstance<Effect.NotificationAction>()
                assertTrue(
                    "Transition on $event from $state emitted ${notifEffects.size} notification actions",
                    notifEffects.size <= 1,
                )
                for (ne in notifEffects) {
                    assertEquals(ID_CHECKIN, ne.notificationId)
                    if (ne is Effect.PostNotification) {
                        val copyKey = CheckInMachine.notificationCopyKey(ne.state)
                        assertNotNull("Expected non-null copy key for ${ne.state}", copyKey)
                        val resolvedText = stringsByName[copyKey!!.resourceName]
                        assertTrue("Unexpected notification text: $resolvedText", resolvedText in allowedTexts)
                    }
                }

                state = transition.state
                settings = transition.settings
            }
        }
    }
}
