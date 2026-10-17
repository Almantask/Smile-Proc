package com.example.smileproc.spikes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The check-in notification as android.md specifies it: channel `checkin`, ID 1, face buttons
 * Low/Okay/Good. States: faces (alerting or silent), Saved ✓, Couldn't save.
 */
object SpikeNotifier {
    const val CHANNEL = "checkin"
    const val ID_CHECKIN = 1

    enum class State(val text: String) {
        FACES_ALERTING("How are you right now?"),
        FACES_SILENT("How are you right now?"),
        SAVED("Saved ✓"),
        FAILED("Couldn't save. Open SmileProc."),
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Check-ins", NotificationManager.IMPORTANCE_DEFAULT).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, state: State): Notification {
        val standing = SpikePrefs(context).standing
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_face)
            .setContentTitle("SmileProc")
            .setContentText(state.text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setAutoCancel(false)
            .setOngoing(standing)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, SpikeActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setDeleteIntent(FaceReceiver.pending(context, FaceReceiver.ACTION_DISMISSED, REQUEST_DISMISSED))
        if (state == State.FACES_ALERTING) {
            builder.setOnlyAlertOnce(false)
        } else {
            builder.setSilent(true).setOnlyAlertOnce(true)
        }
        if (state == State.FACES_ALERTING || state == State.FACES_SILENT) {
            FACES.forEachIndexed { i, label ->
                val mood = i + 1
                val intent = Intent(context, FaceReceiver::class.java)
                    .setAction(FaceReceiver.ACTION_RATE)
                    .putExtra(FaceReceiver.EXTRA_MOOD, mood)
                    .putExtra(FaceReceiver.EXTRA_TEST, false)
                val pending = PendingIntent.getBroadcast(
                    context, mood, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(
                    NotificationCompat.Action.Builder(0, label, pending)
                        .setAuthenticationRequired(false)
                        .setShowsUserInterface(false)
                        .build(),
                )
            }
        }
        return builder.build()
    }

    /** Posts or replaces the one notification. Returns false when posting isn't allowed. */
    fun render(context: Context, state: State): Boolean {
        ensureChannel(context)
        SpikePrefs(context).lastState = state.name
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.notify(ID_CHECKIN, build(context, state))
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** A reminder or an explicit post: clears `dismissed`, then shows the faces. */
    fun postFaces(context: Context, alerting: Boolean): Boolean {
        SpikePrefs(context).dismissed = false
        return render(context, if (alerting) State.FACES_ALERTING else State.FACES_SILENT)
    }

    /** logic.md's resting state: silent faces if standing and not dismissed, else nothing. */
    fun rest(context: Context): Boolean {
        val prefs = SpikePrefs(context)
        if (prefs.standing && !prefs.dismissed) return render(context, State.FACES_SILENT)
        cancel(context)
        return false
    }

    fun cancel(context: Context) {
        SpikePrefs(context).lastState = null
        NotificationManagerCompat.from(context).cancel(ID_CHECKIN)
    }

    /** The state last rendered, for the FGS to reuse ID 1 without changing what's shown. */
    fun lastState(context: Context): State =
        SpikePrefs(context).lastState?.let { name -> State.entries.firstOrNull { it.name == name } } ?: State.SAVED

    private val FACES = listOf("Low", "Okay", "Good")
    private const val REQUEST_DISMISSED = 20
}
