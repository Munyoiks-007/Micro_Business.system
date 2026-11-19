package com.example.microbusinesssystem.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricsCalculatorTest {

    @Test
    fun testCalculateNetMargin() {
        val margin = MetricsCalculator.calculateNetMargin(1000000L, 400000L)
        assertEquals(60.0, margin, 0.001)

        val zeroMargin = MetricsCalculator.calculateNetMargin(0L, 0L)
        assertEquals(0.0, zeroMargin, 0.001)
    }

}
