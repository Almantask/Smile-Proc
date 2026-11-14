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
        val middleRadius = radius * EmotionWheelMath.MIDDLE_RATIO

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
        assertTrue(EmotionWheelMath.isCenterHoleTap(cx, cy, cx, cy, radius))

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
        val midMiddleRadius = (innerRadius + middleRadius) / 2f
        val midOuterRadius = (middleRadius + radius) / 2f

        // Every core inner ray resolves to that core id, and every middle & outer ring ray resolves accurately
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

            val groupSweep = coreSweep / core.groups.size
            core.groups.forEachIndexed { g, group ->
                val groupAngleDeg = EmotionWheelMath.START_ANGLE_DEG + (i * coreSweep) + (g * groupSweep) + (groupSweep / 2f)
                val groupRad = Math.toRadians(groupAngleDeg.toDouble())
                val groupTapX = (cx + midMiddleRadius * cos(groupRad)).toFloat()
                val groupTapY = (cy + midMiddleRadius * sin(groupRad)).toFloat()

                val groupDetected = EmotionWheelMath.findTappedTag(
                    tapX = groupTapX,
                    tapY = groupTapY,
                    centerX = cx,
                    centerY = cy,
                    radius = radius,
                    catalog = catalog,
                )
                assertEquals("Middle tag [$i][$g] should resolve to ${group.id}", group.id, groupDetected)

                val outerSweep = groupSweep / group.outer.size
                group.outer.forEachIndexed { o, outerId ->
                    val outerAngleDeg = EmotionWheelMath.START_ANGLE_DEG + (i * coreSweep) + (g * groupSweep) + (o * outerSweep) + (outerSweep / 2f)
                    val outerRad = Math.toRadians(outerAngleDeg.toDouble())
                    val outerTapX = (cx + midOuterRadius * cos(outerRad)).toFloat()
                    val outerTapY = (cy + midOuterRadius * sin(outerRad)).toFloat()

                    val outerDetected = EmotionWheelMath.findTappedTag(
                        tapX = outerTapX,
                        tapY = outerTapY,
                        centerX = cx,
                        centerY = cy,
                        radius = radius,
                        catalog = catalog,
                    )
                    assertEquals("Outer tag [$i][$g][$o] should resolve to $outerId", outerId, outerDetected)
                }
            }

            // Also verify focusedCoreId mode expands this core to 360 degrees
            val focusedGroupSweep = 360f / core.groups.size
            core.groups.forEachIndexed { g, group ->
                val fgAngleDeg = EmotionWheelMath.START_ANGLE_DEG + (g * focusedGroupSweep) + (focusedGroupSweep / 2f)
                val fgRad = Math.toRadians(fgAngleDeg.toDouble())
                val fgTapX = (cx + midMiddleRadius * cos(fgRad)).toFloat()
                val fgTapY = (cy + midMiddleRadius * sin(fgRad)).toFloat()
                assertEquals(
                    group.id,
                    EmotionWheelMath.findTappedTag(fgTapX, fgTapY, cx, cy, radius, catalog, focusedCoreId = core.id),
                )
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
