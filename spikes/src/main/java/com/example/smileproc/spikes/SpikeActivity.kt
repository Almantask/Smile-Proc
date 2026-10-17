package com.example.smileproc.spikes

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spike readout and controls. adb can drive the parts that need the app in the foreground:
 *   am start -n com.example.smileproc.spikes/.SpikeActivity --es cmd locateNow|startFgs
 */
class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SpikeNotifier.ensureChannel(this)
        runCommand(intent)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SpikeScreen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runCommand(intent)
    }

    private fun runCommand(intent: Intent?) {
        when (intent?.getStringExtra("cmd")) {
            "locateNow" -> locateNow()
            "startFgs" -> startFgs()
        }
        intent?.removeExtra("cmd")
    }

    /** The in-app path (android.md › Location): a current fix, 10 s limit. Also warms the cache for A2a. */
    fun locateNow() {
        lifecycleScope.launch(Dispatchers.IO) {
            Records.add(this@SpikeActivity, "locate", JSONObject().put("location", Locator.current(this@SpikeActivity, 10_000)))
        }
    }

    /** A2b control: the same service, started while the app is in the foreground. */
    fun startFgs() {
        try {
            ContextCompat.startForegroundService(this, LocationFgs.intent(this, null, "app"))
        } catch (e: Exception) {
            lifecycleScope.launch(Dispatchers.IO) {
                Records.add(
                    this@SpikeActivity, "fgs",
                    JSONObject().put("origin", "app").put("started", false).put("exception", e.javaClass.simpleName),
                )
            }
        }
    }

    @Composable
    private fun SpikeScreen() {
        val scope = rememberCoroutineScope()
        val prefs = remember { SpikePrefs(this) }
        var probe by remember { mutableStateOf<JSONObject?>(null) }
        var records by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
        var standing by remember { mutableStateOf(prefs.standing) }
        var fgsMode by remember { mutableStateOf(prefs.mode == SpikePrefs.MODE_FGS) }
        var at by remember { mutableStateOf("07:30") }
        var tick by remember { mutableIntStateOf(0) }
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

        LaunchedEffect(tick) {
            while (true) {
                withContext(Dispatchers.IO) {
                    probe = Records.probe(this@SpikeActivity)
                    records = Records.all(this@SpikeActivity)
                }
                delay(2_000)
            }
        }

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("SmileProc spikes (M1)", style = MaterialTheme.typography.titleLarge)
            probe?.let { p ->
                Text(
                    "API ${p.optInt("api")} · notifications ${onOff(p.optBoolean("notificationsEnabled"))} · " +
                        "fine ${onOff(p.optBoolean("fine"))} · coarse ${onOff(p.optBoolean("coarse"))} · " +
                        "location ${onOff(p.optBoolean("locationEnabled"))}",
                )
                val reminderAt = p.optLong("reminderAt")
                Text(if (reminderAt > 0) "Reminder set for ${time(reminderAt)}" else "No reminder set")
            }
            Button(onClick = {
                val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
                permissions.launch(wanted.toTypedArray())
            }) { Text("Ask for notification and location permissions") }

            HorizontalDivider()
            Text("A1 / A3: the check-in notification", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = standing, onCheckedChange = { standing = it; prefs.standing = it })
                Text("  Keep it on the lock screen (ongoing)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { SpikeNotifier.render(this@SpikeActivity, SpikeNotifier.State.FACES_SILENT) }) { Text("Post silent") }
                Button(onClick = { SpikeNotifier.render(this@SpikeActivity, SpikeNotifier.State.FACES_ALERTING) }) { Text("Post alerting") }
            }
            OutlinedButton(onClick = { SpikeNotifier.cancel(this@SpikeActivity) }) { Text("Remove it") }

            HorizontalDivider()
            Text("A2: location", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = fgsMode, onCheckedChange = {
                    fgsMode = it
                    prefs.mode = if (it) SpikePrefs.MODE_FGS else SpikePrefs.MODE_LAST_KNOWN
                })
                Text("  After a face tap, also run the location service")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { locateNow() }) { Text("Get location now") }
                Button(onClick = { startFgs() }) { Text("Start location service") }
            }

            HorizontalDivider()
            Text("A4: reminder", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { ReminderReceiver.scheduleIn(this@SpikeActivity, 180); tick++ }) { Text("Reminder in 3 minutes") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = at, onValueChange = { at = it }, label = { Text("HH:MM") }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    runCatching { ReminderReceiver.scheduleAt(this@SpikeActivity, at) }
                    tick++
                }) { Text("Set") }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Results (newest first)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { Records.clear(this@SpikeActivity) }; tick++ }) { Text("Clear") }
            }
            records.forEach { Text(describe(it), style = MaterialTheme.typography.bodySmall) }
        }
    }

    private fun onOff(value: Boolean) = if (value) "on" else "off"

    private fun time(millis: Long) = SimpleDateFormat("EEE HH:mm:ss", Locale.getDefault()).format(Date(millis))

    private fun describe(r: JSONObject): String {
        val head = "${time(r.optLong("at"))} · ${r.optString("kind")}"
        val lockedText = if (r.has("locked")) (if (r.optBoolean("locked")) " · locked" else " · unlocked") else ""
        return when (r.optString("kind")) {
            "tap" -> "$head · ${FACES.getOrElse(r.optInt("mood") - 1) { "?" }}$lockedText · ${location(r.optJSONObject("location"))}"
            "fgs" -> "$head (${r.optString("origin")})$lockedText · " +
                (if (r.optBoolean("started")) "${location(r.optJSONObject("location"))} · ${r.optLong("totalMs")} ms"
                else "not started: ${r.optString("exception")}")
            "locate" -> "$head · ${location(r.optJSONObject("location"))}"
            "reminder" -> "$head · ${r.opt("delayS")} s late · set for ${time(r.optLong("scheduledAt"))}" +
                (if (r.optBoolean("idle")) " · idle" else "")
            "boot" -> "$head$lockedText · faces ${if (r.optBoolean("posted")) "posted" else "not posted"}"
            else -> "$head$lockedText ${r.optString("exception")}${r.optString("openFailed")}"
        }
    }

    private fun location(l: JSONObject?): String {
        if (l == null) return "no location data"
        val outcome = l.optString("outcome")
        if (outcome != "fresh" && outcome != "cached") {
            return "${l.optString("source")}: $outcome" + (if (l.has("exception")) " (${l.optString("exception")})" else "")
        }
        return "${l.optString("source")}: $outcome ${l.optString("provider")} ±${l.opt("accuracyM")} m, ${l.opt("ageS")} s old"
    }

    private companion object {
        val FACES = listOf("Low", "Okay", "Good")
    }
}
