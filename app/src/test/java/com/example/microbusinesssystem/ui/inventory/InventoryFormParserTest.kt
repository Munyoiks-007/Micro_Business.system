package com.example.microbusinesssystem.ui.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryFormParserTest {
    @Test
    fun parsesValidInventoryFieldsToMinorCurrencyUnits() {
        val result = InventoryFormParser.parse(" Flour ", " Dry goods ", "12.50", "4")

        assertTrue(result is InventoryFormResult.Valid)
        val fields = (result as InventoryFormResult.Valid).fields
        assertEquals("Flour", fields.name)
        assertEquals("Dry goods", fields.category)
        assertEquals(1250L, fields.priceMinor)
        assertEquals(4, fields.quantity)
    }

    @Test
    fun rejectsInvalidQuantityAndPrice() {
        assertTrue(
            InventoryFormParser.parse("Flour", "Dry goods", "12.50", "four")
                is InventoryFormResult.Invalid
        )
        assertTrue(
            InventoryFormParser.parse("Flour", "Dry goods", "-1", "4")
                is InventoryFormResult.Invalid
        )
        assertTrue(
            InventoryFormParser.parse("Flour", "Dry goods", "12.501", "4")
                is InventoryFormResult.Invalid
        )
    }
}
