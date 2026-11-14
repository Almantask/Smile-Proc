package com.example.smileproc.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.R
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Mood
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.components.GlassBottomNavBar
import com.example.smileproc.ui.components.SmileProcBackground
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.SmileProcTheme
import com.example.smileproc.ui.today.TodayScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ModernDesignSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smileProcTheme_providesDarkAndLightPalettes() {
        var darkFlag: Boolean? = null
        var lightFlag: Boolean? = null

        composeRule.setContent {
            SmileProcTheme(darkTheme = true) {
                darkFlag = LocalSmileProcColors.current.isDark
                SmileProcTheme(darkTheme = false) {
                    lightFlag = LocalSmileProcColors.current.isDark
                    Text("PaletteReady")
                }
            }
        }

        composeRule.onNodeWithText("PaletteReady").assertIsDisplayed()
        assertTrue(darkFlag == true)
        assertFalse(lightFlag ?: true)
    }

    @Test
    fun todayScreen_insideDarkThemeAndBackground_rendersBrandEntriesAndPillButton() {
        val now = Instant.now()
        val entry = Entry(
            id = "00000000-0000-4000-8000-000000000001",
            createdAt = now,
            updatedAt = now,
            timeZoneID = ZoneId.systemDefault().id,
            mood = Mood.GOOD,
            tags = listOf("excited"),
            comment = "Ready for today",
            location = null,
            source = EntrySource.APP,
        )

        composeRule.setContent {
            SmileProcTheme(darkTheme = true) {
                SmileProcBackground {
                    TodayScreen(
                        entries = listOf(entry),
                        places = null,
                        lowRunDismissedEntryId = null,
                        tagPickerMode = TagPickerMode.DETAILED,
                        onTagPickerModeToggle = {},
                        onSaveEntry = {},
                        onUpdateEntry = {},
                        onDeleteEntry = {},
                        onSavePlaceLabel = {},
                        onDismissLowRun = {},
                        onResetLowRunDismissed = {},
                        onCaptureLocationForEntry = {},
                        onNavigateToHistory = {},
                    )
                }
            }
        }

        val appName = ApplicationProvider.getApplicationContext<Context>().getString(R.string.app_name)
        composeRule.onNodeWithText(appName).assertIsDisplayed()
        composeRule.onNodeWithText("How are you right now?").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Low").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Okay").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Good").assertIsDisplayed()
        composeRule.onNodeWithText("Ready for today").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("All entries").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun glassBottomNavBar_rendersTabsSelectionAndClicks_evenAt200PercentFontScale() {
        var selectedTab: AppTab? = null

        composeRule.setContent {
            SmileProcTheme(darkTheme = false) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f),
                ) {
                    Box {
                        GlassBottomNavBar(
                            currentTab = AppTab.WEEK,
                            hasUnseenCard = true,
                            onSelectTab = { selectedTab = it },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Week").assertIsSelected()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertEquals(AppTab.SETTINGS, selectedTab)
    }
}
