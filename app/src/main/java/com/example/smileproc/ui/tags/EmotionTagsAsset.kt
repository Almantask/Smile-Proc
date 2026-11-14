package com.example.smileproc.ui.tags

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.smileproc.R
import com.example.smileproc.core.BuiltInContextTag
import com.example.smileproc.core.CustomTag
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

fun Context.resolveContextTagLabel(tagId: String): String = when (BuiltInContextTag.fromId(tagId)) {
    BuiltInContextTag.SLEEP -> getString(R.string.ctx_sleep)
    BuiltInContextTag.MOVEMENT -> getString(R.string.ctx_movement)
    BuiltInContextTag.OUTDOORS -> getString(R.string.ctx_outdoors)
    BuiltInContextTag.SOCIAL -> getString(R.string.ctx_social)
    BuiltInContextTag.WORK -> getString(R.string.ctx_work)
    BuiltInContextTag.SOLO -> getString(R.string.ctx_solo)
    BuiltInContextTag.REST -> getString(R.string.ctx_rest)
    BuiltInContextTag.FOOD -> getString(R.string.ctx_food)
    null -> tagId
}

fun Context.resolveAnyTagLabel(
    tagId: String,
    customTagsById: Map<String, CustomTag> = emptyMap(),
): String = when {
    BuiltInContextTag.isContextTag(tagId) -> resolveContextTagLabel(tagId)
    tagId in customTagsById -> customTagsById.getValue(tagId).label
    else -> resolveTagLabel(tagId)
}

fun parseCoreColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val rgb = clean.toLongOrNull(16) ?: return Color(0xFFF2C14E)
    return Color(0xFF000000 or rgb)
}

fun resolveTagCoreColor(catalog: EmotionTags, tagId: String): Color? {
    val coreId = tagId.substringBefore('.')
    val core = catalog.cores.firstOrNull { it.id == coreId } ?: return null
    return parseCoreColor(core.color)
}

@Immutable
data class CoreTagChipColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

fun coreTagChipColors(
    coreColor: Color,
    isDark: Boolean,
    emphasized: Boolean = false,
): CoreTagChipColors = if (isDark) {
    CoreTagChipColors(
        container = coreColor.copy(alpha = if (emphasized) 0.28f else 0.22f),
        content = lerp(coreColor, Color.White, if (emphasized) 0.62f else 0.55f),
        border = coreColor.copy(alpha = if (emphasized) 0.72f else 0.55f),
    )
} else {
    CoreTagChipColors(
        container = lerp(coreColor, Color.White, if (emphasized) 0.46f else 0.55f),
        content = Color(0xFF1F1C26),
        border = coreColor.copy(alpha = if (emphasized) 0.85f else 0.70f),
    )
}
