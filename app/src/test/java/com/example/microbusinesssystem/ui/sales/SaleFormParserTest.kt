package com.example.microbusinesssystem.ui.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleFormParserTest {
    @Test
    fun calculatesSaleTotalWithExactMinorUnits() {
        val result = SaleFormParser.parse("Tea", "3", "12.50")

        assertTrue(result is SaleFormResult.Valid)
        val fields = (result as SaleFormResult.Valid).fields
        assertEquals("Tea", fields.itemName)
        assertEquals(3, fields.quantity)
        assertEquals(1_250L, fields.unitPriceMinor)
        assertEquals(3_750L, fields.totalMinor)
        assertEquals(3_750L, SaleFormParser.previewTotalMinor("3", "12.50"))
    }

    @Test
    fun rejectsInvalidQuantitiesAndPrices() {
        assertTrue(SaleFormParser.parse("Tea", "0", "12.50") is SaleFormResult.Invalid)
        assertTrue(SaleFormParser.parse("Tea", "two", "12.50") is SaleFormResult.Invalid)
        assertTrue(SaleFormParser.parse("Tea", "2", "0") is SaleFormResult.Invalid)
        assertTrue(SaleFormParser.parse("Tea", "2", "-3") is SaleFormResult.Invalid)
        assertTrue(SaleFormParser.parse("Tea", "2", "12.501") is SaleFormResult.Invalid)
    }
}
