package com.example.smileproc.core

object PlaceResolver {
    private fun nearer(dist: Double, clusterId: String, bestDist: Double?, bestId: String?): Boolean {
        if (bestDist == null || bestId == null) return true
        return dist < bestDist || (dist == bestDist && clusterId < bestId)
    }

    fun resolve(entries: List<Entry>, labels: List<PlaceLabel>, config: PatternConfig): PlaceAssignment {
        val radius = config.placeRadiusMeters
        val maxAcc = config.placeMaxAccuracyMeters
        val eligible = entries
            .filter { entry ->
                val loc = entry.location
                loc != null && loc.horizontalAccuracy <= maxAcc
            }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))

        val assignment = linkedMapOf<String, String>()
        val labelsById = labels.associateBy { it.id }

        for (entry in eligible) {
            val loc = entry.location ?: continue
            var bestId: String? = null
            var bestDist: Double? = null
            for (label in labels) {
                val dist = Geo.haversineM(
                    loc.latitude,
                    loc.longitude,
                    label.anchorLatitude,
                    label.anchorLongitude,
                )
                val clusterId = "p:${label.id}"
                if (dist <= radius && nearer(dist, clusterId, bestDist, bestId)) {
                    bestDist = dist
                    bestId = clusterId
                }
            }
            if (bestId != null) {
                assignment[entry.id] = bestId
            }
        }

        val unnamed = linkedMapOf<String, MutableList<Entry>>()
        for (entry in eligible) {
            if (entry.id in assignment) continue
            val loc = entry.location ?: continue
            var bestId: String? = null
            var bestDist: Double? = null
            for (clusterId in unnamed.keys.toList()) {
                val members = unnamed.getValue(clusterId)
                val lat = members.mapNotNull { it.location?.latitude }.average()
                val lon = members.mapNotNull { it.location?.longitude }.average()
                val dist = Geo.haversineM(loc.latitude, loc.longitude, lat, lon)
                if (dist <= radius && nearer(dist, clusterId, bestDist, bestId)) {
                    bestDist = dist
                    bestId = clusterId
                }
            }
            if (bestId == null) {
                bestId = "u:${entry.id}"
                unnamed[bestId] = mutableListOf(entry)
            } else {
                unnamed.getValue(bestId).add(entry)
            }
            assignment[entry.id] = bestId
        }

        if (unnamed.isNotEmpty()) {
            val centroids = unnamed.mapValues { (_, members) ->
                members.mapNotNull { it.location?.latitude }.average() to
                    members.mapNotNull { it.location?.longitude }.average()
            }
            val rebuilt = linkedMapOf<String, MutableList<Entry>>()
            for (clusterId in unnamed.keys) {
                rebuilt[clusterId] = mutableListOf()
            }
            for (entry in eligible) {
                val current = assignment[entry.id] ?: continue
                if (!current.startsWith("u:")) continue
                val loc = entry.location ?: continue
                var bestId: String? = null
                var bestDist: Double? = null
                for ((clusterId, centroid) in centroids) {
                    val dist = Geo.haversineM(loc.latitude, loc.longitude, centroid.first, centroid.second)
                    if (dist <= radius && nearer(dist, clusterId, bestDist, bestId)) {
                        bestDist = dist
                        bestId = clusterId
                    }
                }
                if (bestId == null) bestId = current
                rebuilt.getOrPut(bestId) { mutableListOf() }.add(entry)
            }
            val finalUnnamed = linkedMapOf<String, MutableList<Entry>>()
            for (members in rebuilt.values) {
                if (members.isEmpty()) continue
                val earliest = members.minWith(compareBy({ it.createdAt }, { it.id }))
                val finalId = "u:${earliest.id}"
                finalUnnamed.getOrPut(finalId) { mutableListOf() }.addAll(members)
            }
            for ((clusterId, members) in finalUnnamed) {
                for (member in members) {
                    assignment[member.id] = clusterId
                }
            }
        }

        val clusters = assignment.values.toSortedSet().mapNotNull { cid ->
            val members = eligible.filter { assignment[it.id] == cid }
            if (members.isEmpty()) return@mapNotNull null
            val lat = members.mapNotNull { it.location?.latitude }.average()
            val lon = members.mapNotNull { it.location?.longitude }.average()
            val label = if (cid.startsWith("p:")) labelsById[cid.removePrefix("p:")] else null
            Cluster(
                id = cid,
                centroidLatitude = lat,
                centroidLongitude = lon,
                memberIDs = members.sortedWith(compareBy({ it.createdAt }, { it.id })).map { it.id },
                labelID = label?.id,
            )
        }
        val entryClusters = assignment.keys.sorted().associateWith { assignment.getValue(it) }
        return PlaceAssignment(entryClusters, clusters)
    }

    fun labelWithinRadius(
        latitude: Double,
        longitude: Double,
        labels: List<PlaceLabel>,
        radiusMeters: Double,
    ): PlaceLabel? {
        return labels
            .map { label ->
                label to Geo.haversineM(latitude, longitude, label.anchorLatitude, label.anchorLongitude)
            }
            .filter { it.second <= radiusMeters }
            .minWithOrNull(compareBy<Pair<PlaceLabel, Double>> { it.second }.thenBy { it.first.id })
            ?.first
    }

    fun combinedAnchor(
        memberLatitudes: List<Double>,
        memberLongitudes: List<Double>,
        label: PlaceLabel,
    ): Pair<Double, Double> {
        val lat = (memberLatitudes + label.anchorLatitude).average()
        val lon = (memberLongitudes + label.anchorLongitude).average()
        return lat to lon
    }
}
