package com.example.microbusinesssystem.ui.sales

import java.math.BigDecimal
import java.math.RoundingMode

internal data class SaleFormFields(
    val itemName: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val totalMinor: Long
)

internal sealed interface SaleFormResult {
    data class Valid(val fields: SaleFormFields) : SaleFormResult
    data class Invalid(val message: String) : SaleFormResult
}

internal object SaleFormParser {
    fun parse(itemName: String, quantity: String, unitPrice: String): SaleFormResult {
        val cleanName = itemName.trim()
        if (cleanName.isEmpty()) return SaleFormResult.Invalid("Enter an item or service name.")

        val count = quantity.trim().toIntOrNull()
            ?: return SaleFormResult.Invalid("Enter a valid whole-number quantity.")
        if (count <= 0) return SaleFormResult.Invalid("Quantity must be greater than zero.")

        val priceMinor = try {
            BigDecimal(unitPrice.trim())
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        } catch (_: Exception) {
            return SaleFormResult.Invalid("Enter a valid unit price with no more than two decimal places.")
        }
        if (priceMinor <= 0L) return SaleFormResult.Invalid("Unit price must be greater than zero.")

        val total = try {
            Math.multiplyExact(priceMinor, count.toLong())
        } catch (_: ArithmeticException) {
            return SaleFormResult.Invalid("The sale total is too large.")
        }

        return SaleFormResult.Valid(SaleFormFields(cleanName, count, priceMinor, total))
    }

    fun previewTotalMinor(quantity: String, unitPrice: String): Long? {
        val count = quantity.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
        val parsedPrice = try {
            BigDecimal(unitPrice.trim()).movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: Exception) {
            return null
        }
        val priceMinor = parsedPrice.takeIf { it > 0L } ?: return null
        return try {
            Math.multiplyExact(priceMinor, count.toLong())
        } catch (_: ArithmeticException) {
            null
        }
    }
}
