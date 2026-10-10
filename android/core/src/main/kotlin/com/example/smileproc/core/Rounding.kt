package com.example.smileproc.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object Rounding {
    /**
     * Format the IEEE-754 double with 12 digits after the decimal, then round
     * half away from zero. Matches spec/engine-rules.md.
     */
    fun roundHalfAway(value: Double, places: Int): Double {
        val formatted = String.format(Locale.US, "%.12f", value)
        return BigDecimal(formatted).setScale(places, RoundingMode.HALF_UP).toDouble()
    }

    fun r6(value: Double): Double = roundHalfAway(value, 6)

    fun r9(value: Double): BigDecimal {
        val formatted = String.format(Locale.US, "%.12f", value)
        return BigDecimal(formatted).setScale(9, RoundingMode.HALF_UP)
    }
}
