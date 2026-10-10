package com.example.smileproc.core

object LibraryMatcher {
    fun coreOf(tag: String): String = tag.substringBefore('.')

    fun match(pattern: DetectedPattern, library: List<LibraryRow>): String? =
        match(pattern.detector, pattern.direction, pattern.params, library)

    fun match(
        detector: String,
        direction: String?,
        params: Map<String, String>,
        library: List<LibraryRow>,
    ): String? {
        var best: String? = null
        var bestKeys = -1
        for (entry in library) {
            val spec = entry.match
            if (spec["detector"] != detector) continue
            var ok = true
            for ((key, value) in spec) {
                if (key == "detector") continue
                if (key == "tag") {
                    val tag = params["tag"]
                    if (tag == null || !(value == tag || value == coreOf(tag))) {
                        ok = false
                        break
                    }
                } else {
                    val actual = if (key == "direction") direction else params[key]
                    if (actual == null || actual != value) {
                        ok = false
                        break
                    }
                }
            }
            if (!ok) continue
            if (spec.size > bestKeys) {
                best = entry.id
                bestKeys = spec.size
            }
        }
        return best
    }
}
