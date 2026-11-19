package com.example.microbusinesssystem.ui.inventory

import java.math.BigDecimal
import java.math.RoundingMode

internal data class InventoryFields(
    val name: String,
    val category: String,
    val priceMinor: Long,
    val quantity: Int
)

internal sealed interface InventoryFormResult {
    data class Valid(val fields: InventoryFields) : InventoryFormResult
    data class Invalid(val message: String) : InventoryFormResult
}

internal object InventoryFormParser {
    fun parse(
        name: String,
        category: String,
        price: String,
        quantity: String
    ): InventoryFormResult {
        val cleanName = name.trim()
        val cleanCategory = category.trim()
        if (cleanName.isEmpty() || cleanCategory.isEmpty()) {
            return InventoryFormResult.Invalid("Enter an item name and category.")
        }

        val priceMinor = try {
            BigDecimal(price.trim())
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        } catch (_: Exception) {
            return InventoryFormResult.Invalid("Enter a valid price with no more than two decimal places.")
        }
        if (priceMinor <= 0L) return InventoryFormResult.Invalid("Price must be greater than zero.")

        val parsedQuantity = quantity.trim().toIntOrNull()
            ?: return InventoryFormResult.Invalid("Enter a valid whole-number quantity.")
        if (parsedQuantity < 0) return InventoryFormResult.Invalid("Quantity cannot be negative.")

        return InventoryFormResult.Valid(
            InventoryFields(cleanName, cleanCategory, priceMinor, parsedQuantity)
        )
    }
}
