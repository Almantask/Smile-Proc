package com.example.smileproc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.crypto.JcaSealer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.data.ManagedSealer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Regression: tapping "Allow location" in onboarding crashed with
 * `IllegalArgumentException: Can only use lower 16 bits for requestCode`.
 *
 * androidx.biometric 1.1.0 pulled in androidx.fragment 1.2.5, whose FragmentActivity
 * rejects the >16-bit request codes androidx.activity 1.13 generates. Every
 * permission request and every SAF launcher (export, import, error logs) hit it,
 * because [MainActivity] is a FragmentActivity. The UI tests host screens in a plain
 * ComponentActivity, so these tests drive the real MainActivity instead.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityResultLaunchTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val dbName = "launch-test-${System.nanoTime()}.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(
            context = context,
            sealer = FakeManagedSealer(),
            dbName = dbName,
            autoInitialize = false,
        )
        runBlocking {
            container.initialize()
            container.settingsRepository.setOnboardingCompleted(false)
            container.settingsRepository.setAppLock(false)
        }
        SmileProcApplication.setContainerForTest(container)
    }

    @After
    fun tearDown() {
        SmileProcApplication.setContainerForTest(null)
        runBlocking { container.close() }
        context.deleteDatabase(dbName)
    }

    @Test
    fun everyContractTheAppUsesLaunchesFromMainActivity() {
        val contracts: List<Pair<ActivityResultContract<*, *>, Any>> = listOf(
            ActivityResultContracts.RequestPermission() to Manifest.permission.POST_NOTIFICATIONS,
            ActivityResultContracts.RequestMultiplePermissions() to arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            ActivityResultContracts.OpenDocument() to arrayOf("*/*"),
            ActivityResultContracts.CreateDocument("application/octet-stream") to "smileproc-test.smileproc",
            ActivityResultContracts.CreateDocument("application/json") to "smileproc-error-logs-test.json",
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                contracts.forEachIndexed { i, (contract, input) ->
                    @Suppress("UNCHECKED_CAST")
                    val launcher = activity.activityResultRegistry.register(
                        "test-$i",
                        contract as ActivityResultContract<Any, Any?>,
                    ) {}
                    launcher.launch(input) // threw IllegalArgumentException before the fix
                    launcher.unregister()
                }
                val shadow = shadowOf(activity)
                assertNotNull(shadow.lastRequestedPermission)
                val started: Intent? = shadow.nextStartedActivityForResult?.intent
                assertNotNull(started)
            }
        }
    }

    @Test
    fun onboardingAllowLocationRequestsPermissionAndAdvances() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Just for you").assertExists()
            composeRule.onNodeWithContentDescription("Next").performClick()
            composeRule.onNodeWithText("Notice places").assertExists()

            composeRule.onNodeWithContentDescription("Allow location").performScrollTo().performClick()
            composeRule.waitForIdle()

            scenario.onActivity { activity ->
                val request = shadowOf(activity).lastRequestedPermission
                assertNotNull("no permission request was made", request)
                assertArrayEquals(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    request.requestedPermissions,
                )
                activity.onRequestPermissionsResult(
                    request.requestCode,
                    request.requestedPermissions,
                    intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED),
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Gentle reminders").assertExists()
        }
    }

    @Test
    fun onboardingTurnOnRemindersRequestsNotificationPermissionAndAdvances() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithContentDescription("Next").performClick()
            composeRule.onNodeWithContentDescription("Skip").performClick()
            composeRule.onNodeWithText("Gentle reminders").assertExists()

            val allowLabel = context.getString(R.string.onboarding_reminders_action)
            composeRule.onNodeWithContentDescription(allowLabel).performScrollTo().performClick()
            composeRule.waitForIdle()

            scenario.onActivity { activity ->
                val request = shadowOf(activity).lastRequestedPermission
                assertNotNull("no permission request was made", request)
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, request.requestedPermissions.single())
                activity.onRequestPermissionsResult(
                    request.requestCode,
                    request.requestedPermissions,
                    intArrayOf(PackageManager.PERMISSION_DENIED),
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Skip").assertDoesNotExist()
        }
    }

    private class FakeManagedSealer : ManagedSealer {
        private var delegate: JcaSealer? = null
        override fun isKeyAvailable(): Boolean = delegate?.isKeyAvailable() == true
        override fun ensureKeyExists() {
            if (delegate == null) delegate = JcaSealer.withNewKey()
        }
        override fun deleteKeyIfPresent() { delegate = null }
        override fun seal(rowId: String, plaintext: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).seal(rowId, plaintext)
        override fun open(rowId: String, payload: ByteArray): ByteArray =
            (delegate ?: throw SealException(SealException.Reason.KEY_UNAVAILABLE)).open(rowId, payload)
    }
}
