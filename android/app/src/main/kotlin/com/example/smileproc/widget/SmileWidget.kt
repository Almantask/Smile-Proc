package com.example.smileproc.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import com.example.smileproc.MainActivity
import com.example.smileproc.R
import com.example.smileproc.SmileProcApp
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import java.time.ZoneId
import kotlinx.coroutines.launch

private val SavedUntilKey = longPreferencesKey("savedUntil")
private val MoodKey = ActionParameters.Key<Int>("mood")

class SmileWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val until = prefs[SavedUntilKey] ?: 0L
                val saved = System.currentTimeMillis() < until
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (saved) {
                        Text(text = context.getString(R.string.widget_saved))
                    } else {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FaceButton(context, Mood.LOW, R.drawable.face_low, R.string.widget_a11y_low)
                            Spacer(modifier = GlanceModifier.size(8.dp))
                            FaceButton(context, Mood.OKAY, R.drawable.face_okay, R.string.widget_a11y_okay)
                            Spacer(modifier = GlanceModifier.size(8.dp))
                            FaceButton(context, Mood.GOOD, R.drawable.face_good, R.string.widget_a11y_good)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun FaceButton(context: Context, mood: Mood, drawable: Int, label: Int) {
    Image(
        provider = ImageProvider(drawable),
        contentDescription = context.getString(label),
        modifier = GlanceModifier
            .size(48.dp)
            .clickable(actionRunCallback<LogMoodCallback>(actionParametersOf(MoodKey to mood.value))),
        colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurface),
    )
}

class SmileWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmileWidget()
}

class LogMoodCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val moodValue = parameters[MoodKey] ?: return
        val mood = Mood.entries.firstOrNull { it.value == moodValue } ?: return
        val app = context.applicationContext as SmileProcApp
        val source = if (isKeyguardHost(context, glanceId)) {
            EntrySource.LOCK_SCREEN_WIDGET
        } else {
            EntrySource.HOME_SCREEN_WIDGET
        }
        val now = java.time.Instant.now()
        val entry = app.container.repository.quickLog(mood, source, ZoneId.systemDefault(), now)
        if (app.container.settings.snapshot().captureLocation) {
            val sample = app.container.location.lastKnown()
            if (sample != null) {
                app.container.repository.attachLocationIfMissing(entry.id, sample, java.time.Instant.now())
            }
        }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[SavedUntilKey] = System.currentTimeMillis() + 60_000
            }
        }
        SmileWidget().update(context, glanceId)
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, WidgetSavedClearReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            CLEAR_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 60_000, pending)
    }

    private suspend fun isKeyguardHost(context: Context, glanceId: GlanceId): Boolean {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val category = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
        return category == android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
    }

    companion object {
        private const val CLEAR_REQUEST = 6_001
    }
}

class WidgetSavedClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as SmileProcApp
        app.container.scope.launch {
            try {
                SmileWidget().updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
