package com.example.smileproc.ui.tags

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.smileproc.core.EmotionTags

object EmotionTagsCatalog {
    private var cached: EmotionTags? = null

    fun get(context: Context): EmotionTags {
        cached?.let { return it }
        val json = context.assets.open("emotion-tags.json").bufferedReader().use { it.readText() }
        val parsed = EmotionTags.fromJson(json)
        cached = parsed
        return parsed
    }
}

fun Context.resolveTagLabel(tagId: String): String {
    val resName = "tag_" + tagId.replace('.', '_')
    val resId = resources.getIdentifier(resName, "string", packageName)
    return if (resId != 0) getString(resId) else tagId
}

fun parseCoreColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val rgb = clean.toLongOrNull(16) ?: return Color(0xFFF2C14E)
    return Color(0xFF000000 or rgb)
}
