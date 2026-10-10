package com.example.smileproc.core

data class Cluster(
    val id: String,
    val centroidLatitude: Double,
    val centroidLongitude: Double,
    val memberIDs: List<String>,
    val labelID: String?,
)

data class PlaceAssignment(
    val entryClusters: Map<String, String>,
    val clusters: List<Cluster>,
)
