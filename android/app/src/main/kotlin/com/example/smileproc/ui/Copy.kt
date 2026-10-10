package com.example.smileproc.ui

import android.content.Context
import androidx.annotation.StringRes
import com.example.smileproc.R
import com.example.smileproc.data.StoredCardItem
import java.util.Locale

fun text(context: Context, @StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

fun tagLabel(context: Context, id: String): String {
    val res = tagRes(id)
    return if (res == 0) id else context.getString(res)
}

fun weekdayLabel(context: Context, iso: Int): String = context.getString(weekdayRes(iso))

fun daypartLabel(context: Context, id: String): String = context.getString(daypartRes(id))

fun libraryText(template: String, item: StoredCardItem, place: String, context: Context): String {
    var text = template
    item.segment?.let { text = text.replace("{segment}", daypartLabel(context, it)) }
    item.weekday?.toIntOrNull()?.let { text = text.replace("{weekday}", weekdayLabel(context, it)) }
    item.tag?.let { text = text.replace("{tag}", tagLabel(context, it).lowercase(Locale.getDefault())) }
    return text.replace("{place}", place)
}

fun observation(context: Context, item: StoredCardItem, place: String): String {
    val segment = item.segment?.let { daypartLabel(context, it) }.orEmpty()
    val weekday = item.weekday?.toIntOrNull()?.let { weekdayLabel(context, it) }.orEmpty()
    val tag = item.tag?.let { tagLabel(context, it).lowercase(Locale.getDefault()) }.orEmpty()
    val count = item.nSegment
    return when (item.detector) {
        "timeOfDay" -> context.getString(
            if (item.direction == "lower") R.string.week_observation_timeOfDay_lower else R.string.week_observation_timeOfDay_higher,
            segment,
            count,
        )
        "weekday" -> context.getString(
            if (item.direction == "lower") R.string.week_observation_weekday_lower else R.string.week_observation_weekday_higher,
            weekday,
            count,
        )
        "weekdayDaypart" -> context.getString(
            if (item.direction == "lower") R.string.week_observation_weekdayDaypart_lower else R.string.week_observation_weekdayDaypart_higher,
            weekday,
            segment,
            count,
        )
        "place" -> context.getString(
            if (item.direction == "lower") R.string.week_observation_place_lower else R.string.week_observation_place_higher,
            place,
            count,
        )
        "awayFromHome" -> context.getString(
            if (item.direction == "lower") R.string.week_observation_awayFromHome_lower else R.string.week_observation_awayFromHome_higher,
            count,
        )
        "tagOnLow" -> context.getString(R.string.week_observation_tagOnLow, tag, count)
        "tagAtPlace" -> context.getString(R.string.week_observation_tagAtPlace, tag, place, count)
        else -> ""
    }
}

private fun weekdayRes(iso: Int): Int = when (iso) {
    1 -> R.string.weekday_1
    2 -> R.string.weekday_2
    3 -> R.string.weekday_3
    4 -> R.string.weekday_4
    5 -> R.string.weekday_5
    6 -> R.string.weekday_6
    else -> R.string.weekday_7
}

private fun daypartRes(id: String): Int = when (id) {
    "morning" -> R.string.daypart_morning
    "afternoon" -> R.string.daypart_afternoon
    "evening" -> R.string.daypart_evening
    else -> R.string.daypart_night
}

private fun tagRes(id: String): Int = when (id) {
    "happy" -> R.string.tag_happy
    "happy.joyful" -> R.string.tag_happy_joyful
    "happy.grateful" -> R.string.tag_happy_grateful
    "happy.proud" -> R.string.tag_happy_proud
    "happy.hopeful" -> R.string.tag_happy_hopeful
    "happy.excited" -> R.string.tag_happy_excited
    "calm" -> R.string.tag_calm
    "calm.relaxed" -> R.string.tag_calm_relaxed
    "calm.content" -> R.string.tag_calm_content
    "calm.peaceful" -> R.string.tag_calm_peaceful
    "calm.focused" -> R.string.tag_calm_focused
    "calm.safe" -> R.string.tag_calm_safe
    "sad" -> R.string.tag_sad
    "sad.down" -> R.string.tag_sad_down
    "sad.lonely" -> R.string.tag_sad_lonely
    "sad.disappointed" -> R.string.tag_sad_disappointed
    "sad.hurt" -> R.string.tag_sad_hurt
    "sad.nostalgic" -> R.string.tag_sad_nostalgic
    "anxious" -> R.string.tag_anxious
    "anxious.stressed" -> R.string.tag_anxious_stressed
    "anxious.worried" -> R.string.tag_anxious_worried
    "anxious.overwhelmed" -> R.string.tag_anxious_overwhelmed
    "anxious.nervous" -> R.string.tag_anxious_nervous
    "anxious.insecure" -> R.string.tag_anxious_insecure
    "angry" -> R.string.tag_angry
    "angry.frustrated" -> R.string.tag_angry_frustrated
    "angry.irritated" -> R.string.tag_angry_irritated
    "angry.resentful" -> R.string.tag_angry_resentful
    "angry.jealous" -> R.string.tag_angry_jealous
    "angry.impatient" -> R.string.tag_angry_impatient
    "tired" -> R.string.tag_tired
    "tired.drained" -> R.string.tag_tired_drained
    "tired.sleepy" -> R.string.tag_tired_sleepy
    "tired.bored" -> R.string.tag_tired_bored
    "tired.unmotivated" -> R.string.tag_tired_unmotivated
    "tired.numb" -> R.string.tag_tired_numb
    "surprised" -> R.string.tag_surprised
    "surprised.amazed" -> R.string.tag_surprised_amazed
    "surprised.confused" -> R.string.tag_surprised_confused
    "surprised.curious" -> R.string.tag_surprised_curious
    "surprised.startled" -> R.string.tag_surprised_startled
    "surprised.moved" -> R.string.tag_surprised_moved
    else -> 0
}
