package com.example.microbusinesssystem.util

object MetricsCalculator {
    fun calculateNetMargin(revenueCents: Long, expensesCents: Long = 0L): Double {
        if (revenueCents <= 0L) return 0.0
        return ((revenueCents - expensesCents).toDouble() / revenueCents.toDouble()) * 100.0
    }
}
