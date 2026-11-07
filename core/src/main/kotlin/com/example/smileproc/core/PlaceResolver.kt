package com.example.smileproc.core

import kotlinx.serialization.Serializable

/**
 * Identifier of a resolved place cluster: `"p:<labelID>"` for named places,
 * `"u:<earliestEntryID>"` for unnamed clusters (`docs/spec/logic.md` › Place clustering).
 */
@JvmInline
@Serializable
value class ClusterID(val value: String) : Comparable<ClusterID> {
    init {
        require(value.startsWith("p:") || value.startsWith("u:")) {
            "ClusterID must start with 'p:' or 'u:', got $value"
        }
        require(value.length > 2) { "ClusterID payload cannot be empty: $value" }
    }

    val isNamed: Boolean get() = value.startsWith("p:")
    val isUnnamed: Boolean get() = value.startsWith("u:")

    override fun compareTo(other: ClusterID): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        fun named(labelId: String): ClusterID = ClusterID("p:$labelId")
        fun unnamed(entryId: String): ClusterID = ClusterID("u:$entryId")
    }
}

/** Resolved place cluster with its centroid, ordered member entry IDs, and optional [PlaceLabel]. */
data class Cluster(
    val id: ClusterID,
    val centroid: LatLon,
    val memberIDs: List<String>,
    val label: PlaceLabel?,
)

/** Mapping of entries to resolved clusters (`docs/spec/logic.md` › Place clustering). */
data class PlaceAssignment(
    val entryToCluster: Map<String, ClusterID>,
    val clusters: Map<ClusterID, Cluster>,
) {
    fun clusterOf(entryId: String): ClusterID? = entryToCluster[entryId]

    fun clusterForEntry(entryId: String): Cluster? = entryToCluster[entryId]?.let { clusters[it] }
}

/** Structured summary for rendering `"Unnamed place · 8 check-ins · mostly weekday mornings"`. */
data class UnnamedPlaceSummary(
    val checkInCount: Int,
    val mostlyWeekend: Boolean,
    val dominantDaypart: Daypart,
)

/**
 * Computes place clusters across history (`docs/spec/logic.md` › Place clustering):
 * 1. Eligible entries (`horizontalAccuracy <= placeMaxAccuracyMeters`) sorted by `(createdAt, id)`.
 * 2. Named pass: nearest label anchor within `placeRadiusMeters` -> `"p:<labelID>"`.
 * 3. Unnamed pass: nearest running centroid within `placeRadiusMeters`, else new `"u:<firstEntryID>"`.
 * 4. Stabilise once: recompute centroids, reassign each unnamed entry to nearest centroid within
 *    `placeRadiusMeters` (or its own cluster), drop empty clusters, keep `"u:<earliest member id>"`.
 * 5. Ineligible / unlocated entries have no cluster.
 * Distance: haversine (`R = 6,371,000 m`). Ties: lower `ClusterID` string wins.
 */
object PlaceResolver {

    private val ENTRY_ORDER: Comparator<Entry> = compareBy<Entry> { it.createdAt }.thenBy { it.id }

    fun resolve(
        entries: List<Entry>,
        labels: List<PlaceLabel>,
        config: PatternConfig,
    ): PlaceAssignment {
        val eligible = entries
            .filter { entry ->
                val loc = entry.location
                loc != null && loc.horizontalAccuracy <= config.placeMaxAccuracyMeters
            }
            .sortedWith(ENTRY_ORDER)

        if (eligible.isEmpty()) {
            return PlaceAssignment(emptyMap(), emptyMap())
        }

        val labelById = labels.associateBy { it.id }
        val namedBuckets = linkedMapOf<String, MutableList<Entry>>()
        val unnamedEntries = mutableListOf<Entry>()

        // 2. Named pass
        for (entry in eligible) {
            val point = entry.locationPoint()
            var bestLabelId: String? = null
            var bestClusterId: ClusterID? = null
            var bestDist = Double.POSITIVE_INFINITY

            for (label in labels) {
                val dist = Geo.haversineMeters(
                    point.latitude,
                    point.longitude,
                    label.anchorLatitude,
                    label.anchorLongitude,
                )
                if (dist <= config.placeRadiusMeters) {
                    val candidateId = ClusterID.named(label.id)
                    if (isBetterMatch(dist, candidateId, bestDist, bestClusterId)) {
                        bestDist = dist
                        bestClusterId = candidateId
                        bestLabelId = label.id
                    }
                }
            }

            if (bestLabelId != null) {
                namedBuckets.getOrPut(bestLabelId) { mutableListOf() }.add(entry)
            } else {
                unnamedEntries.add(entry)
            }
        }

        // 3. Unnamed pass (sequential running centroids)
        class RunningCluster(
            val initialId: ClusterID,
            val members: MutableList<Entry>,
        ) {
            fun centroid(): LatLon = Geo.centroid(members.map { it.locationPoint() })
        }

        val pass1Unnamed = mutableListOf<RunningCluster>()
        for (entry in unnamedEntries) {
            val point = entry.locationPoint()
            var bestIndex = -1
            var bestId: ClusterID? = null
            var bestDist = Double.POSITIVE_INFINITY

            for ((idx, cluster) in pass1Unnamed.withIndex()) {
                val c = cluster.centroid()
                val dist = Geo.haversineMeters(point, c)
                if (dist <= config.placeRadiusMeters) {
                    if (isBetterMatch(dist, cluster.initialId, bestDist, bestId)) {
                        bestDist = dist
                        bestId = cluster.initialId
                        bestIndex = idx
                    }
                }
            }

            if (bestIndex >= 0) {
                pass1Unnamed[bestIndex].members.add(entry)
            } else {
                pass1Unnamed.add(RunningCluster(ClusterID.unnamed(entry.id), mutableListOf(entry)))
            }
        }

        // 4. Stabilise once against frozen centroids from Step 3
        val frozenCentroids: List<Pair<ClusterID, LatLon>> =
            pass1Unnamed.map { it.initialId to it.centroid() }
        val reassignedBuckets = Array(frozenCentroids.size) { mutableListOf<Entry>() }
        val detachedSingletons = mutableListOf<Entry>()

        for (entry in unnamedEntries) {
            val point = entry.locationPoint()
            var bestIndex = -1
            var bestId: ClusterID? = null
            var bestDist = Double.POSITIVE_INFINITY

            for ((idx, pair) in frozenCentroids.withIndex()) {
                val (cid, centroid) = pair
                val dist = Geo.haversineMeters(point, centroid)
                if (dist <= config.placeRadiusMeters) {
                    if (isBetterMatch(dist, cid, bestDist, bestId)) {
                        bestDist = dist
                        bestId = cid
                        bestIndex = idx
                    }
                }
            }

            if (bestIndex >= 0) {
                reassignedBuckets[bestIndex].add(entry)
            } else {
                detachedSingletons.add(entry)
            }
        }

        val entryToCluster = linkedMapOf<String, ClusterID>()
        val clusters = sortedMapOf<ClusterID, Cluster>()

        for ((labelId, members) in namedBuckets) {
            val clusterId = ClusterID.named(labelId)
            val label = labelById.getValue(labelId)
            val centroid = Geo.centroid(members.map { it.locationPoint() })
            val memberIds = members.map { it.id }
            clusters[clusterId] = Cluster(
                id = clusterId,
                centroid = centroid,
                memberIDs = memberIds,
                label = label,
            )
            for (id in memberIds) {
                entryToCluster[id] = clusterId
            }
        }

        val finalUnnamedGroups: List<List<Entry>> = buildList {
            for (bucket in reassignedBuckets) {
                if (bucket.isNotEmpty()) add(bucket)
            }
            for (singleton in detachedSingletons) {
                add(listOf(singleton))
            }
        }

        for (members in finalUnnamedGroups) {
            val earliestId = members.first().id
            val clusterId = ClusterID.unnamed(earliestId)
            val centroid = Geo.centroid(members.map { it.locationPoint() })
            val memberIds = members.map { it.id }
            clusters[clusterId] = Cluster(
                id = clusterId,
                centroid = centroid,
                memberIDs = memberIds,
                label = null,
            )
            for (id in memberIds) {
                entryToCluster[id] = clusterId
            }
        }

        return PlaceAssignment(
            entryToCluster = entryToCluster,
            clusters = clusters,
        )
    }

    /**
     * Checks whether naming a cluster with [clusterCentroid] is within `placeRadiusMeters`
     * of an existing [PlaceLabel] anchor (`"Is this {name}?"` prompt in `docs/spec/logic.md`).
     */
    fun findMergeCandidate(
        clusterCentroid: LatLon,
        existingLabels: List<PlaceLabel>,
        config: PatternConfig,
    ): PlaceLabel? {
        var best: PlaceLabel? = null
        var bestId: ClusterID? = null
        var bestDist = Double.POSITIVE_INFINITY
        for (label in existingLabels) {
            val dist = Geo.haversineMeters(
                clusterCentroid.latitude,
                clusterCentroid.longitude,
                label.anchorLatitude,
                label.anchorLongitude,
            )
            if (dist <= config.placeRadiusMeters) {
                val cid = ClusterID.named(label.id)
                if (isBetterMatch(dist, cid, bestDist, bestId)) {
                    bestDist = dist
                    bestId = cid
                    best = label
                }
            }
        }
        return best
    }

    /** Computes the combined centroid when merging an unnamed cluster into [existingLabel]. */
    fun mergedAnchor(
        existingLabel: PlaceLabel,
        existingMemberPoints: List<LatLon>,
        newMemberPoints: List<LatLon>,
    ): LatLon {
        val combined = if (existingMemberPoints.isNotEmpty()) {
            existingMemberPoints + newMemberPoints
        } else {
            listOf(LatLon(existingLabel.anchorLatitude, existingLabel.anchorLongitude)) + newMemberPoints
        }
        return Geo.centroid(combined)
    }

    /** Summarises an unnamed cluster's entries for `"Unnamed place · 8 check-ins · mostly weekday mornings"`. */
    fun summarizeUnnamed(memberEntries: List<Entry>): UnnamedPlaceSummary {
        require(memberEntries.isNotEmpty()) { "Cannot summarise an empty cluster" }
        val (weekend, weekday) = memberEntries.partition { it.isoWeekday() >= 6 }
        val mostlyWeekend = weekend.size > weekday.size
        val pool = if (mostlyWeekend) weekend else weekday
        val countsByDaypart = pool.groupingBy { it.daypart() }.eachCount()
        val dominant = Daypart.entries.maxBy { countsByDaypart[it] ?: 0 }
        return UnnamedPlaceSummary(
            checkInCount = memberEntries.size,
            mostlyWeekend = mostlyWeekend,
            dominantDaypart = dominant,
        )
    }

    private fun isBetterMatch(
        candidateDist: Double,
        candidateId: ClusterID,
        bestDist: Double,
        bestId: ClusterID?,
    ): Boolean {
        if (bestId == null) return true
        if (candidateDist < bestDist) return true
        if (candidateDist > bestDist) return false
        return candidateId < bestId
    }

    private fun Entry.locationPoint(): LatLon {
        val loc = requireNotNull(location) { "Entry $id has no location" }
        return LatLon(loc.latitude, loc.longitude)
    }
}
