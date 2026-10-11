package com.example.smileproc

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigTest {
    @Test
    fun applicationIdIsThePlaceholder() {
        assertEquals("com.example.smileproc", BuildConfig.APPLICATION_ID)
    }
}
