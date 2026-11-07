package com.example.smileproc.ui.places

import android.content.Context
import com.example.smileproc.R
import com.example.smileproc.core.Entry
import com.example.smileproc.core.PatternConfig
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.PlaceResolver

object PatternConfigCatalog {
    private var cached: PatternConfig? = null

    fun get(context: Context): PatternConfig {
        cached?.let { return it }
        val json = context.assets.open("pattern-config.json").bufferedReader().use { it.readText() }
        val parsed = PatternConfig.fromJson(json)
        cached = parsed
        return parsed
    }
}

fun resolvePlaces(
    context: Context,
    entries: List<Entry>,
    labels: List<PlaceLabel>,
): PlaceAssignment {
    val config = PatternConfigCatalog.get(context)
    return PlaceResolver.resolve(entries, labels, config)
}

fun getPlaceDisplayName(
    context: Context,
    entryId: String,
    assignment: PlaceAssignment?,
): String? {
    val clusterId = assignment?.clusterOf(entryId) ?: return null
    val cluster = assignment.clusters[clusterId] ?: return null
    return cluster.label?.name ?: context.getString(R.string.place_unnamed_title)
}
