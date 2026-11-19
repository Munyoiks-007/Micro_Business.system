package com.example.microbusinesssystem.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SalesGoalInputParserTest {
    @Test
    fun blankTargetsRemainUnconfiguredAndMoneyConvertsToMinorUnitsExactly() {
        assertEquals(null, SalesGoalInputParser.parseMinorUnits("  "))
        assertEquals(1234L, SalesGoalInputParser.parseMinorUnits("12.34"))
        assertEquals("12.34", SalesGoalInputParser.displayAmount(1234L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTargetsAreRejected() {
        SalesGoalInputParser.parseMinorUnits("-1")
    }

    @Test(expected = ArithmeticException::class)
    fun fractionsSmallerThanOneMinorUnitAreRejected() {
        SalesGoalInputParser.parseMinorUnits("1.001")
    }
}
