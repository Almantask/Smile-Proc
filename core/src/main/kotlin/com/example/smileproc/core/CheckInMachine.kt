package com.example.smileproc.core

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Pure reducer for SmileProc's single check-in notification
 * (`docs/spec/logic.md` › Check-in notification state machine).
 *
 * Invariant: every transition emits at most one notification post-or-cancel effect, always
 * targeting [ID_CHECKIN] (`1`), and notification text never contains mood data.
 */
object CheckInMachine {

    const val ID_CHECKIN: Int = 1
    const val RESTORE_DELAY_SECONDS: Long = 60L
    const val TEST_DELAY_SECONDS: Long = 10L

    enum class NotificationCopyKey(val resourceName: String) {
        ASK("notification_text_ask"),
        SAVED("notification_text_saved"),
        TEST_DONE("notification_text_test_done"),
        FAILED("notification_text_failed"),
    }

    @Serializable
    sealed interface State {
        @Serializable
        data object Hidden : State

        @Serializable
        data class Faces(val alerting: Boolean) : State

        @Serializable
        data object Saved : State

        @Serializable
        data object Failed : State

        @Serializable
        data object Test : State

        @Serializable
        data object TestDone : State
    }

    @Serializable
    data class Settings(
        val standing: Boolean = true,
        val dismissed: Boolean = false,
        val rememberedRestingState: State? = null,
    )

    sealed interface Event {
        data object ReminderFired : Event
        data class Tap(val mood: Mood, val saveSucceeded: Boolean = true) : Event
        data object SaveFailed : Event
        data object RestoreTimer : Event
        data object RatedInApp : Event
        data object Dismissed : Event
        data object StandingTurnedOn : Event
        data object StandingTurnedOff : Event
        data object TestRequested : Event
        data object TestTimerFired : Event
        data object Booted : Event
        data object AppUpdated : Event
    }

    sealed interface Effect {
        sealed interface NotificationAction : Effect {
            val notificationId: Int
        }

        data class PostNotification(
            override val notificationId: Int = ID_CHECKIN,
            val state: State,
            val ongoing: Boolean,
        ) : NotificationAction {
            init {
                require(notificationId == ID_CHECKIN) { "Only ID_CHECKIN ($ID_CHECKIN) may be posted" }
                require(state != State.Hidden) { "Hidden state must use CancelNotification" }
            }
        }

        data class CancelNotification(
            override val notificationId: Int = ID_CHECKIN,
        ) : NotificationAction {
            init {
                require(notificationId == ID_CHECKIN) { "Only ID_CHECKIN ($ID_CHECKIN) may be cancelled" }
            }
        }

        data class InsertEntry(
            val mood: Mood,
            val createdAt: Instant,
            val source: EntrySource = EntrySource.NOTIFICATION,
        ) : Effect

        data class RecordTestSuccess(val completedAt: Instant) : Effect
        data class ScheduleRestoreTimer(val delaySeconds: Long = RESTORE_DELAY_SECONDS) : Effect
        data object CancelRestoreTimer : Effect
        data class ScheduleTestTimer(val delaySeconds: Long = TEST_DELAY_SECONDS) : Effect
        data class RememberRestingState(val restingState: State) : Effect
        data object RescheduleAlarms : Effect
    }

    data class Transition(
        val state: State,
        val settings: Settings,
        val effects: List<Effect>,
    )

    /** Resting state = `Faces(silent)` if `standing && !dismissed`, else `Hidden`. */
    fun restingState(settings: Settings): State =
        if (settings.standing && !settings.dismissed) {
            State.Faces(alerting = false)
        } else {
            State.Hidden
        }

    /** Lock-screen-safe `strings.xml` resource key for [state], or `null` when [State.Hidden]. */
    fun notificationCopyKey(state: State): NotificationCopyKey? = when (state) {
        State.Hidden -> null
        is State.Faces, State.Test -> NotificationCopyKey.ASK
        State.Saved -> NotificationCopyKey.SAVED
        State.TestDone -> NotificationCopyKey.TEST_DONE
        State.Failed -> NotificationCopyKey.FAILED
    }

    fun reduce(
        state: State,
        settings: Settings,
        event: Event,
        now: Instant,
    ): Transition = when (event) {
        Event.ReminderFired -> {
            val nextSettings = settings.copy(dismissed = false, rememberedRestingState = null)
            val nextState = State.Faces(alerting = true)
            Transition(
                state = nextState,
                settings = nextSettings,
                effects = listOf(
                    Effect.CancelRestoreTimer,
                    renderAction(nextState, nextSettings),
                ),
            )
        }

        is Event.Tap -> when (state) {
            is State.Faces -> {
                if (event.saveSucceeded) {
                    val nextState = State.Saved
                    Transition(
                        state = nextState,
                        settings = settings,
                        effects = listOf(
                            Effect.InsertEntry(mood = event.mood, createdAt = now, source = EntrySource.NOTIFICATION),
                            renderAction(nextState, settings),
                            Effect.ScheduleRestoreTimer(RESTORE_DELAY_SECONDS),
                        ),
                    )
                } else {
                    val nextState = State.Failed
                    Transition(
                        state = nextState,
                        settings = settings,
                        effects = listOf(
                            renderAction(nextState, settings),
                            Effect.ScheduleRestoreTimer(RESTORE_DELAY_SECONDS),
                        ),
                    )
                }
            }

            State.Test -> {
                val nextState = State.TestDone
                Transition(
                    state = nextState,
                    settings = settings,
                    effects = listOf(
                        Effect.RecordTestSuccess(completedAt = now),
                        renderAction(nextState, settings),
                        Effect.ScheduleRestoreTimer(RESTORE_DELAY_SECONDS),
                    ),
                )
            }

            State.Hidden, State.Saved, State.Failed, State.TestDone -> Transition(
                state = state,
                settings = settings,
                effects = emptyList(),
            )
        }

        Event.SaveFailed -> {
            val nextState = State.Failed
            Transition(
                state = nextState,
                settings = settings,
                effects = listOf(
                    renderAction(nextState, settings),
                    Effect.ScheduleRestoreTimer(RESTORE_DELAY_SECONDS),
                ),
            )
        }

        Event.RestoreTimer -> {
            val target = settings.rememberedRestingState ?: restingState(settings)
            val nextSettings = settings.copy(rememberedRestingState = null)
            Transition(
                state = target,
                settings = nextSettings,
                effects = listOf(renderAction(target, nextSettings)),
            )
        }

        Event.RatedInApp -> {
            if (state == State.Faces(alerting = true)) {
                val target = restingState(settings)
                Transition(
                    state = target,
                    settings = settings,
                    effects = listOf(renderAction(target, settings)),
                )
            } else {
                Transition(state = state, settings = settings, effects = emptyList())
            }
        }

        Event.Dismissed -> {
            val nextSettings = settings.copy(
                dismissed = if (settings.standing) true else settings.dismissed,
                rememberedRestingState = null,
            )
            val nextState = State.Hidden
            Transition(
                state = nextState,
                settings = nextSettings,
                effects = listOf(
                    Effect.CancelRestoreTimer,
                    renderAction(nextState, nextSettings),
                ),
            )
        }

        Event.StandingTurnedOn -> {
            val nextSettings = settings.copy(
                standing = true,
                dismissed = false,
                rememberedRestingState = null,
            )
            val nextState = if (state == State.Hidden) restingState(nextSettings) else state
            val effects = if (nextState != state || state != State.Hidden) {
                listOf(renderAction(nextState, nextSettings))
            } else {
                emptyList()
            }
            Transition(
                state = nextState,
                settings = nextSettings,
                effects = effects,
            )
        }

        Event.StandingTurnedOff -> {
            val nextSettings = settings.copy(
                standing = false,
                rememberedRestingState = null,
            )
            val nextState = if (state == State.Faces(alerting = false)) State.Hidden else state
            val effects = if (nextState != state || state != State.Hidden) {
                listOf(renderAction(nextState, nextSettings))
            } else {
                emptyList()
            }
            Transition(
                state = nextState,
                settings = nextSettings,
                effects = effects,
            )
        }

        Event.TestRequested -> {
            val remembered = restingState(settings)
            val nextSettings = settings.copy(rememberedRestingState = remembered)
            Transition(
                state = state,
                settings = nextSettings,
                effects = listOf(
                    Effect.RememberRestingState(remembered),
                    Effect.ScheduleTestTimer(TEST_DELAY_SECONDS),
                ),
            )
        }

        Event.TestTimerFired -> {
            val nextState = State.Test
            Transition(
                state = nextState,
                settings = settings,
                effects = listOf(
                    Effect.CancelRestoreTimer,
                    renderAction(nextState, settings),
                ),
            )
        }

        Event.Booted, Event.AppUpdated -> {
            val nextSettings = settings.copy(rememberedRestingState = null)
            val nextState = restingState(nextSettings)
            Transition(
                state = nextState,
                settings = nextSettings,
                effects = listOf(
                    Effect.RescheduleAlarms,
                    renderAction(nextState, nextSettings),
                ),
            )
        }
    }

    private fun renderAction(state: State, settings: Settings): Effect.NotificationAction =
        if (state == State.Hidden) {
            Effect.CancelNotification(ID_CHECKIN)
        } else {
            Effect.PostNotification(
                notificationId = ID_CHECKIN,
                state = state,
                ongoing = settings.standing,
            )
        }
}
