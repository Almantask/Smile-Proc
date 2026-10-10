package com.example.smileproc.core

data class LibraryRow(
    val id: String,
    val match: Map<String, String>,
    val title: String,
    val common: String,
    val tip: String,
    val question: String,
)
