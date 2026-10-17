package com.example.smileproc.spikes

import android.content.Context
import androidx.core.content.edit

/** Spike settings: standing on/off, A2 mode, last rendered state, the pending reminder. */
class SpikePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("spikes", Context.MODE_PRIVATE)

    var standing: Boolean
        get() = prefs.getBoolean("standing", true)
        set(value) = prefs.edit { putBoolean("standing", value) }

    /** The person swiped the standing notification away; cleared by the next reminder or post (logic.md). */
    var dismissed: Boolean
        get() = prefs.getBoolean("dismissed", false)
        set(value) = prefs.edit { putBoolean("dismissed", value) }

    /** A2: "lastKnown" (the spec's default) or "fgs" (last known, then a short location FGS). */
    var mode: String
        get() = prefs.getString("mode", MODE_LAST_KNOWN) ?: MODE_LAST_KNOWN
        set(value) = prefs.edit { putString("mode", value) }

    var lastState: String?
        get() = prefs.getString("lastState", null)
        set(value) = prefs.edit { putString("lastState", value) }

    /** Wall-clock time the pending reminder was set for, 0 when none. */
    var reminderAt: Long
        get() = prefs.getLong("reminderAt", 0)
        set(value) = prefs.edit { putLong("reminderAt", value) }

    companion object {
        const val MODE_LAST_KNOWN = "lastKnown"
        const val MODE_FGS = "fgs"
    }
}
