package com.example.smileproc

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.smileproc.core.CardItem
import com.example.smileproc.core.CheckInMachine
import com.example.smileproc.core.Confidence
import com.example.smileproc.core.DetectedPattern
import com.example.smileproc.core.Detector
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntrySource
import com.example.smileproc.core.Family
import com.example.smileproc.core.LocationKind
import com.example.smileproc.core.LocationSample
import com.example.smileproc.core.Mood
import com.example.smileproc.core.PatternParams
import com.example.smileproc.notify.CheckInNotifier
import com.example.smileproc.ui.places.PlaceFormatters
import com.example.smileproc.ui.week.PatternLibraryCatalog
import com.example.smileproc.ui.week.WeeklyCardRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class LocalizationTest {

    @Test
    @Config(qualifiers = "en")
    fun englishLocaleLoadsEnglishPatternLibrary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = PatternLibraryCatalog.get(context)
        assertEquals("Afternoons run lower", library.getValue("afternoon-dip").title)
    }

    @Test
    @Config(qualifiers = "lt")
    fun lithuanianLocaleLoadsLithuanianPatternLibraryAndRendersCard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val library = PatternLibraryCatalog.get(context)
        assertEquals("Popietės būna sunkesnės", library.getValue("afternoon-dip").title)

        val item = CardItem(
            patternID = "tagOnLow:anxious.stressed",
            libraryID = "tag-on-low",
            pattern = DetectedPattern(
                id = "tagOnLow:anxious.stressed",
                detector = Detector.TAG_ON_LOW,
                family = Family.TAG,
                direction = null,
                params = PatternParams(tag = "anxious.stressed"),
                nSegment = 2,
                nRest = 3,
                meanSegment = null,
                meanRest = null,
                diff = null,
                z = null,
                score = 0.67,
                confidence = Confidence.PATTERN,
                supportingEntryIDs = listOf("e1", "e2"),
            ),
        )

        val rendered = WeeklyCardRenderer.renderItem(
            context = context,
            item = item,
            library = library,
            labels = emptyList(),
            places = null,
        )
        assertTrue(rendered.title.contains("įsitempęs"))
        assertEquals(
            "Jausmą „įsitempęs“ pasirinkai 2 prastuose įrašuose.",
            rendered.observation,
        )
    }

    @Test
    @Config(qualifiers = "lt")
    fun lithuanianPluralsFollowCldrOneFewAndOtherForms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val res = context.resources

        assertEquals("1 sekta diena", res.getQuantityString(R.plurals.week_days_tracked, 1, 1))
        assertEquals("2 sektos dienos", res.getQuantityString(R.plurals.week_days_tracked, 2, 2))
        assertEquals("11 sektų dienų", res.getQuantityString(R.plurals.week_days_tracked, 11, 11))
        assertEquals("21 sekta diena", res.getQuantityString(R.plurals.week_days_tracked, 21, 21))
    }

    @Test
    @Config(qualifiers = "lt")
    fun lithuanianCheckInNotificationsUseTranslatedSafeText() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val ask = CheckInNotifier.build(context, CheckInMachine.State.Faces(alerting = false), ongoing = true)
        assertEquals("SmileProc", ask.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Kaip jautiesi dabar?", ask.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals("Prastai", ask.actions[0].title)
        assertEquals("Normaliai", ask.actions[1].title)
        assertEquals("Gerai", ask.actions[2].title)

        val saved = CheckInNotifier.build(context, CheckInMachine.State.Saved, ongoing = true)
        assertEquals("Išsaugota ✓", saved.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        val testDone = CheckInNotifier.build(context, CheckInMachine.State.TestDone, ongoing = false)
        assertEquals("Veikia ✓", testDone.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        val failed = CheckInNotifier.build(context, CheckInMachine.State.Failed, ongoing = false)
        assertEquals(
            "Nepavyko išsaugoti. Atidaryk „SmileProc“.",
            failed.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
    }

    @Test
    @Config(qualifiers = "lt")
    fun lithuanianPlaceFormatterUsesTranslatedLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = Entry(
            id = "00000000-0000-4000-8000-000000000001",
            createdAt = Instant.parse("2026-06-08T09:00:00Z"),
            updatedAt = Instant.parse("2026-06-08T09:00:00Z"),
            timeZoneID = "UTC",
            mood = Mood.OKAY,
            tags = emptyList(),
            comment = null,
            location = LocationSample(
                latitude = 54.6872,
                longitude = 25.2797,
                horizontalAccuracy = 15.0,
                capturedAt = Instant.parse("2026-06-08T09:00:00Z"),
                kind = LocationKind.CACHED,
            ),
            source = EntrySource.APP,
        )
        val desc = PlaceFormatters.formatUnnamedDescription(context, listOf(entry))
        assertEquals("Neįvardyta vieta · 1 įrašas · dažniausiai darbo dienomis rytais", desc)
    }

    @Test
    @Config(sdk = [30], qualifiers = "en")
    fun appLocalePersistsAndResetsOnPre33Devices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppLocale.reset(context)
        assertEquals(AppLanguage.SYSTEM, AppLocale.currentLanguage(context))

        AppLocale.setLanguage(context, AppLanguage.LT)
        assertEquals(AppLanguage.LT, AppLocale.currentLanguage(context))
        val wrapped = AppLocale.wrapContext(context)
        assertEquals("lt", AppLocale.effectiveLanguageCode(wrapped))
        assertEquals("Šiandien", wrapped.getString(R.string.tab_today))

        AppLocale.reset(context)
        assertEquals(AppLanguage.SYSTEM, AppLocale.currentLanguage(context))
    }
}
