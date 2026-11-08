package com.example.smileproc.ui.places

import android.content.Context
import com.example.smileproc.R
import com.example.smileproc.core.Cluster
import com.example.smileproc.core.Daypart
import com.example.smileproc.core.Entry
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceResolver

object PlaceFormatters {

    /**
     * Formats an unnamed cluster's description:
     * `"Unnamed place · 8 check-ins · mostly weekday mornings"`.
     */
    fun formatUnnamedDescription(context: Context, memberEntries: List<Entry>): String {
        if (memberEntries.isEmpty()) {
            return context.getString(R.string.place_unnamed_title)
        }
        val summary = PlaceResolver.summarizeUnnamed(memberEntries)
        val countText = context.resources.getQuantityString(
            R.plurals.place_unnamed_count,
            summary.checkInCount,
            summary.checkInCount,
        )
        val daypartPluralRes = when (summary.dominantDaypart) {
            Daypart.MORNING -> R.string.daypart_morning_plural
            Daypart.AFTERNOON -> R.string.daypart_afternoon_plural
            Daypart.EVENING -> R.string.daypart_evening_plural
            Daypart.NIGHT -> R.string.daypart_night_plural
        }
        val daypartPlural = context.getString(daypartPluralRes)
        val timingText = if (summary.mostlyWeekend) {
            context.getString(R.string.place_mostly_weekend, daypartPlural)
        } else {
            context.getString(R.string.place_mostly_weekday, daypartPlural)
        }
        return context.getString(R.string.place_unnamed_description, countText, timingText)
    }

    /**
     * Returns the human-readable string for a [PlaceKind].
     */
    fun formatPlaceKind(context: Context, kind: PlaceKind): String {
        return when (kind) {
            PlaceKind.HOME -> context.getString(R.string.place_kind_home)
            PlaceKind.WORK -> context.getString(R.string.place_kind_work)
            PlaceKind.OTHER -> context.getString(R.string.place_kind_other)
        }
    }
}
