package com.example.microbusinesssystem.util

import com.example.microbusinesssystem.data.local.PaymentStatus
import com.example.microbusinesssystem.data.local.SaleEntity
import com.example.microbusinesssystem.data.local.SalesGoalHistoryEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale

enum class SalesGoalPeriod { DAILY, WEEKLY, MONTHLY }

data class PeriodBounds(val startInclusive: Long, val endExclusive: Long)

data class GoalProgress(
    val targetMinor: Long?,
    val actualMinor: Long,
    val percent: Int?,
    val remainingMinor: Long?
)

data class HistoricalRevenue(val period: SalesGoalPeriod, val label: String, val amountMinor: Long)

object SalesGoalCalculator {
    val BUSINESS_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")

    fun bounds(period: SalesGoalPeriod, nowMillis: Long, zone: ZoneId = BUSINESS_ZONE): PeriodBounds {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val startDate = when (period) {
            SalesGoalPeriod.DAILY -> date
            SalesGoalPeriod.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            SalesGoalPeriod.MONTHLY -> date.withDayOfMonth(1)
        }
        val endDate = when (period) {
            SalesGoalPeriod.DAILY -> startDate.plusDays(1)
            SalesGoalPeriod.WEEKLY -> startDate.plusWeeks(1)
            SalesGoalPeriod.MONTHLY -> startDate.plusMonths(1)
        }
        return PeriodBounds(
            startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    fun targetForDate(
        history: List<SalesGoalHistoryEntity>,
        date: LocalDate,
        nowMillis: Long,
        zone: ZoneId = BUSINESS_ZONE
    ): Long? {
        return history.asSequence()
            .filter { it.effectiveAtMillis <= nowMillis }
            .filter { LocalDate.parse(it.effectiveDate) <= date }
            .maxWithOrNull(compareBy<SalesGoalHistoryEntity> { it.effectiveDate }.thenBy { it.effectiveAtMillis })
            ?.dailyTargetMinor
    }

    /** A period target is the sum of the daily target effective on each local calendar date. */
    fun periodTarget(
        history: List<SalesGoalHistoryEntity>,
        period: SalesGoalPeriod,
        nowMillis: Long,
        zone: ZoneId = BUSINESS_ZONE
    ): Long? {
        val periodBounds = bounds(period, nowMillis, zone)
        var day = Instant.ofEpochMilli(periodBounds.startInclusive).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(periodBounds.endExclusive).atZone(zone).toLocalDate()
        var total = 0L
        var hasTarget = false
        while (day.isBefore(endDate)) {
            val daily = targetForDate(history, day, nowMillis, zone)
            if (daily != null) {
                total = Math.addExact(total, daily)
                hasTarget = true
            }
            day = day.plusDays(1)
        }
        return total.takeIf { hasTarget }
    }

    fun progress(
        sales: List<SaleEntity>,
        ownerUid: String,
        targetMinor: Long?,
        period: SalesGoalPeriod,
        nowMillis: Long,
        zone: ZoneId = BUSINESS_ZONE
    ): GoalProgress {
        require(targetMinor == null || targetMinor >= 0L) { "Sales targets cannot be negative." }
        val bounds = bounds(period, nowMillis, zone)
        val actual = completedSales(sales, ownerUid)
            .asSequence()
            .filter { it.timestamp >= bounds.startInclusive && it.timestamp < bounds.endExclusive }
            .fold(0L) { total, sale -> Math.addExact(total, sale.totalAmount) }
        if (targetMinor == null) return GoalProgress(null, actual, null, null)

        val percent = if (targetMinor == 0L) 100 else {
            BigDecimal.valueOf(actual).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(targetMinor), 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(Int.MAX_VALUE.toLong())).toInt()
        }
        return GoalProgress(targetMinor, actual, percent, (targetMinor - actual).coerceAtLeast(0L))
    }

    /** Returns only historical periods with at least one completed sale; no synthetic history is added. */
    fun historicalRevenue(
        sales: List<SaleEntity>,
        ownerUid: String,
        period: SalesGoalPeriod,
        nowMillis: Long,
        zone: ZoneId = BUSINESS_ZONE
    ): List<HistoricalRevenue> {
        val currentStart = Instant.ofEpochMilli(bounds(period, nowMillis, zone).startInclusive)
            .atZone(zone).toLocalDate()
        val perKey = linkedMapOf<Any, Long>()
        completedSales(sales, ownerUid).forEach { sale ->
            val date = Instant.ofEpochMilli(sale.timestamp).atZone(zone).toLocalDate()
            val key: Any = when (period) {
                SalesGoalPeriod.DAILY -> date
                SalesGoalPeriod.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                SalesGoalPeriod.MONTHLY -> YearMonth.from(date)
            }
            val periodStart = when (key) {
                is LocalDate -> key
                is YearMonth -> key.atDay(1)
                else -> error("Unexpected period key")
            }
            if (!periodStart.isBefore(currentStart)) return@forEach
            perKey[key] = Math.addExact(perKey[key] ?: 0L, sale.totalAmount)
        }
        val limit = when (period) {
            SalesGoalPeriod.DAILY -> 7
            SalesGoalPeriod.WEEKLY -> 4
            SalesGoalPeriod.MONTHLY -> 6
        }
        val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        return perKey.entries.sortedBy { entry ->
            when (val key = entry.key) {
                is LocalDate -> key
                is YearMonth -> key.atDay(1)
                else -> LocalDate.MIN
            }
        }.takeLast(limit).map { (key, amount) ->
            val label = when (key) {
                is LocalDate -> if (period == SalesGoalPeriod.WEEKLY) "Week of ${key.format(formatter)}" else key.format(formatter)
                is YearMonth -> key.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
                else -> ""
            }
            HistoricalRevenue(period, label, amount)
        }
    }

    private fun completedSales(sales: List<SaleEntity>, ownerUid: String): List<SaleEntity> =
        sales.filter { it.ownerUid == ownerUid && it.paymentStatus == PaymentStatus.COMPLETED.name }
}
