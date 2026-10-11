package com.example.smileproc.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

/** Paths to the shared test data, passed in by core/build.gradle.kts. */
object TestData {
    val fixtures: File = dir("smileproc.fixtures")
    val content: File = dir("smileproc.content")
    val strings: File = File(System.getProperty("smileproc.strings") ?: error("smileproc.strings is not set"))

    fun json(file: File): JsonElement = Json.parseToJsonElement(file.readText(Charsets.UTF_8))

    fun content(name: String): JsonElement = json(File(content, name))

    private fun dir(property: String): File {
        val path = System.getProperty(property) ?: error("$property is not set; run the tests through Gradle")
        return File(path).also { check(it.isDirectory) { "$property points to a missing directory: $it" } }
    }
}
