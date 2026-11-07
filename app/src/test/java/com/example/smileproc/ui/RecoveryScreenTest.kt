package com.example.smileproc.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.R
import com.example.smileproc.ui.theme.SmileProcTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecoveryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recoveryScreenRendersAt200PercentFontScaleAndConfirmsBeforeStartFresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.recovery_title)
        val body = context.getString(R.string.recovery_body)
        val importLabel = context.getString(R.string.recovery_import)
        val startFreshLabel = context.getString(R.string.recovery_start_fresh)
        val confirmBody = context.getString(R.string.recovery_start_fresh_confirm)
        val cancelLabel = context.getString(R.string.action_cancel)

        var importClicks = 0
        var startFreshConfirmed = 0

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f),
            ) {
                SmileProcTheme(darkTheme = true) {
                    RecoveryScreen(
                        onImportExport = { importClicks++ },
                        onStartFresh = { startFreshConfirmed++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(body).assertIsDisplayed()

        // Tapping Import an export invokes callback directly.
        composeRule
            .onNodeWithContentDescription(importLabel)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, importClicks)
        assertEquals(0, startFreshConfirmed)

        // Tapping Start fresh opens confirmation dialog; Cancel keeps data intact.
        composeRule
            .onNodeWithContentDescription(startFreshLabel)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(confirmBody).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(cancelLabel).performClick()
        assertEquals(0, startFreshConfirmed)

        // Confirming Start fresh invokes destructive callback.
        composeRule
            .onNodeWithContentDescription(startFreshLabel)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(confirmBody).assertIsDisplayed()
        // In dialog + background screen, pick the dialog confirm button (last clickable Start fresh node).
        composeRule
            .onAllNodes(hasContentDescription(startFreshLabel) and hasClickAction())[1]
            .performClick()
        assertEquals(1, startFreshConfirmed)
    }
}
