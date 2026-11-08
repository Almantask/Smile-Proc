package com.example.smileproc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.EmotionTags
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.tags.EmotionListPicker
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.tags.EmotionWheelMath
import com.example.smileproc.ui.tags.TagPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class TagPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val catalog: EmotionTags by lazy {
        EmotionTagsCatalog.get(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun emotionWheelMath_resolvesAllCoresAndFineTagsAccurately() {
        val cx = 200f
        val cy = 200f
        val radius = 180f
        val holeRadius = radius * EmotionWheelMath.HOLE_RATIO
        val innerRadius = radius * EmotionWheelMath.INNER_RATIO

        // Center hole returns null
        assertNull(
            EmotionWheelMath.findTappedTag(
                tapX = cx,
                tapY = cy,
                centerX = cx,
                centerY = cy,
                radius = radius,
                catalog = catalog,
            )
        )

        // Outside wheel returns null
        assertNull(
            EmotionWheelMath.findTappedTag(
                tapX = cx + radius + 10f,
                tapY = cy,
                centerX = cx,
                centerY = cy,
                radius = radius,
                catalog = catalog,
            )
        )

        val coreSweep = 360f / catalog.cores.size
        val midInnerRadius = (holeRadius + innerRadius) / 2f
        val midOuterRadius = (innerRadius + radius) / 2f

        // Every core inner ray resolves to that core id
        catalog.cores.forEachIndexed { i, core ->
            val angleDeg = EmotionWheelMath.START_ANGLE_DEG + (i * coreSweep) + (coreSweep / 2f)
            val rad = Math.toRadians(angleDeg.toDouble())
            val tapX = (cx + midInnerRadius * cos(rad)).toFloat()
            val tapY = (cy + midInnerRadius * sin(rad)).toFloat()

            val detected = EmotionWheelMath.findTappedTag(
                tapX = tapX,
                tapY = tapY,
                centerX = cx,
                centerY = cy,
                radius = radius,
                catalog = catalog,
            )
            assertEquals("Core index $i should resolve to ${core.id}", core.id, detected)

            // Every fine tag outer ray resolves to that fine tag id
            val fineSweep = coreSweep / core.fine.size
            core.fine.forEachIndexed { j, fineId ->
                val fineAngleDeg = EmotionWheelMath.START_ANGLE_DEG + (i * coreSweep) + (j * fineSweep) + (fineSweep / 2f)
                val fineRad = Math.toRadians(fineAngleDeg.toDouble())
                val fineTapX = (cx + midOuterRadius * cos(fineRad)).toFloat()
                val fineTapY = (cy + midOuterRadius * sin(fineRad)).toFloat()

                val fineDetected = EmotionWheelMath.findTappedTag(
                    tapX = fineTapX,
                    tapY = fineTapY,
                    centerX = cx,
                    centerY = cy,
                    radius = radius,
                    catalog = catalog,
                )
                assertEquals("Fine tag [$i][$j] should resolve to $fineId", fineId, fineDetected)
            }
        }
    }

    @Test
    fun emotionListPicker_displaysCoresAndTogglesSelectionWithLimit() {
        var selected by mutableStateOf(listOf<String>())

        composeTestRule.setContent {
            EmotionListPicker(
                selectedTags = selected,
                onTagToggled = { tagId ->
                    selected = if (tagId in selected) selected - tagId else selected + tagId
                },
                catalog = catalog,
            )
        }

        // Happy core section and chips exist
        composeTestRule.onNodeWithText("Happy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Just Happy").performClick()
        assertEquals(listOf("happy"), selected)

        // Fine tag click adds fine tag
        composeTestRule.onNodeWithText("Joyful").performClick()
        assertEquals(listOf("happy", "happy.joyful"), selected)

        // Adding third tag
        composeTestRule.onNodeWithText("Grateful").performClick()
        assertEquals(listOf("happy", "happy.joyful", "happy.grateful"), selected)

        // Attempting to add 4th tag is ignored due to max 3 limit
        composeTestRule.onNodeWithText("Proud").performClick()
        assertEquals(3, selected.size)
        assertTrue("happy.proud" !in selected)

        // Tapping already selected tag deselects it
        composeTestRule.onNodeWithText("Joyful").performClick()
        assertEquals(listOf("happy", "happy.grateful"), selected)
    }

    @Test
    fun tagPicker_gradualMode_navigatesCoresAndFinerTags() {
        var selected by mutableStateOf(listOf<String>())

        composeTestRule.setContent {
            TagPicker(
                selectedTags = selected,
                onTagsChanged = { selected = it },
                mode = TagPickerMode.GRADUAL,
                onModeToggle = {},
                catalog = catalog,
            )
        }

        // Initial step shows core chips
        composeTestRule.onNodeWithText("What's closest?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calm").performClick()

        // Step 2 shows finer chips and "Just Calm"
        composeTestRule.onNodeWithText("Just Calm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Relaxed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Relaxed").performClick()

        assertEquals(listOf("calm.relaxed"), selected)

        // Selected tag appears as InputChip at the top with remove button
        composeTestRule.onNodeWithContentDescription("Remove Relaxed").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Remove Relaxed").performClick()

        assertEquals(emptyList<String>(), selected)
    }

    @Test
    fun tagPicker_detailedMode_switchesToEmotionListPickerAtLargeFontScale() {
        var selected by mutableStateOf(listOf<String>())

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 2.5f, fontScale = 2.0f)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TagPicker(
                        selectedTags = selected,
                        onTagsChanged = { selected = it },
                        mode = TagPickerMode.DETAILED,
                        onModeToggle = {},
                        catalog = catalog,
                    )
                }
            }
        }

        // At fontScale 2.0x, it renders the accessible list picker with core sections
        composeTestRule.onNodeWithText("Feelings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Happy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Just Happy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sad").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tagPicker_gradualMode_producesCanonicalTagId() {
        val gradualSelected = mutableListOf<String>()

        composeTestRule.setContent {
            TagPicker(
                selectedTags = gradualSelected,
                onTagsChanged = {
                    gradualSelected.clear()
                    gradualSelected.addAll(it)
                },
                mode = TagPickerMode.GRADUAL,
                onModeToggle = {},
                catalog = catalog,
            )
        }
        composeTestRule.onNodeWithText("Calm").performClick()
        composeTestRule.onNodeWithText("Peaceful").performClick()
        assertEquals(listOf("calm.peaceful"), gradualSelected)
    }

    @Test
    fun emotionListPicker_producesCanonicalTagId() {
        val listSelected = mutableListOf<String>()

        composeTestRule.setContent {
            EmotionListPicker(
                selectedTags = listSelected,
                onTagToggled = { listSelected.add(it) },
                catalog = catalog,
            )
        }
        composeTestRule.onNodeWithText("Peaceful").performClick()
        assertEquals(listOf("calm.peaceful"), listSelected)
    }

    @Test
    fun tagPicker_modeToggle_invokesCallback() {
        var toggleInvoked = false

        composeTestRule.setContent {
            TagPicker(
                selectedTags = emptyList(),
                onTagsChanged = {},
                mode = TagPickerMode.DETAILED,
                onModeToggle = { toggleInvoked = true },
                catalog = catalog,
            )
        }

        // In detailed mode, feelings wheel header is shown
        composeTestRule.onNodeWithText("Feelings wheel").assertIsDisplayed()

        // Clicking the mode switch button invokes onModeToggle
        composeTestRule.onNodeWithContentDescription("Switch between gradual and detailed feelings").performClick()
        assertTrue("Mode toggle callback should be invoked", toggleInvoked)
    }
}
