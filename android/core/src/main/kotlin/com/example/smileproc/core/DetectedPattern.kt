package com.example.smileproc.core

data class DetectedPattern(
    val id: String,
    val detector: String,
    val family: String,
    val direction: String?,
    val params: Map<String, String>,
    val nSegment: Int,
    val nRest: Int,
    val meanSegment: Double?,
    val meanRest: Double?,
    val diff: Double?,
    val z: Double?,
    val score: Double,
    val confidence: String,
    val supportingEntryIDs: List<String>,
)

data class PublicCandidate(
    val id: String,
    val confidence: String,
    val nSegment: Int,
    val nRest: Int,
    val meanSegment: Double?,
    val meanRest: Double?,
    val diff: Double?,
    val z: Double?,
    val score: Double,
)

fun DetectedPattern.toPublic(): PublicCandidate = PublicCandidate(
    id = id,
    confidence = confidence,
    nSegment = nSegment,
    nRest = nRest,
    meanSegment = meanSegment?.let(Rounding::r6),
    meanRest = meanRest?.let(Rounding::r6),
    diff = diff?.let(Rounding::r6),
    z = z?.let(Rounding::r6),
    score = Rounding.r6(score),
)
