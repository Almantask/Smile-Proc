package com.example.smileproc.core

/**
 * Matches a [DetectedPattern] to the most specific [LibraryEntry] in [PatternLibrary]
 * (`docs/spec/logic.md` › Library matching and card building).
 *
 * A library entry matches when its `detector` equals the pattern's and every other key it
 * specifies (`direction`, `segment`, `weekday`, `placeKind`, `tag`) equals the pattern's param;
 * `tag` also matches the pattern tag's core. Most matched keys wins; ties go to file order.
 */
object LibraryMatcher {

    fun match(pattern: DetectedPattern, library: PatternLibrary): LibraryEntry? {
        var best: LibraryEntry? = null
        var bestKeys = -1
        for (entry in library.entries) {
            if (matches(entry.match, pattern)) {
                val keys = entry.match.keyCount
                if (keys > bestKeys) {
                    bestKeys = keys
                    best = entry
                }
            }
        }
        return best
    }

    fun matchOrThrow(pattern: DetectedPattern, library: PatternLibrary): LibraryEntry =
        match(pattern, library)
            ?: throw NoSuchElementException("No pattern library entry matches pattern ${pattern.id}")

    fun matches(criteria: LibraryMatchCriteria, pattern: DetectedPattern): Boolean {
        if (criteria.detector != pattern.detector.wireName) return false
        if (criteria.direction != null && criteria.direction != pattern.direction?.wireName) return false
        if (criteria.segment != null && criteria.segment != pattern.params.segment) return false
        if (criteria.weekday != null && criteria.weekday != pattern.params.weekday) return false
        if (criteria.placeKind != null && criteria.placeKind != pattern.params.placeKind?.wireName) return false
        if (criteria.tag != null) {
            val patternTag = pattern.params.tag ?: return false
            val patternCore = patternTag.substringBefore('.')
            if (criteria.tag != patternTag && criteria.tag != patternCore) return false
        }
        return true
    }
}
