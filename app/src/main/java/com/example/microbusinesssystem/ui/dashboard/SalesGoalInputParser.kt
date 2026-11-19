package com.example.microbusinesssystem.ui.dashboard

import java.math.BigDecimal
import java.math.RoundingMode

internal object SalesGoalInputParser {
    fun parseMinorUnits(input: String): Long? {
        val normalized = input.trim()
        if (normalized.isEmpty()) return null
        val amount = BigDecimal(normalized)
        require(amount.signum() >= 0) { "Target must be nonnegative." }
        return amount.multiply(BigDecimal.valueOf(100L))
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    }

    fun displayAmount(minorUnits: Long?): String = minorUnits?.let {
        BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString()
    }.orEmpty()
}
