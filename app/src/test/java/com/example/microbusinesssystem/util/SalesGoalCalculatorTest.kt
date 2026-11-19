package com.example.microbusinesssystem.util

import com.example.microbusinesssystem.data.local.PaymentStatus
import com.example.microbusinesssystem.data.local.SaleEntity
import com.example.microbusinesssystem.data.local.SalesGoalHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SalesGoalCalculatorTest {
    private val zone = ZoneId.of("Africa/Nairobi")

    @Test
    fun dailyWeeklyAndMonthlyRevenueUseLocalCalendarBoundaries() {
        val now = LocalDate.of(2024, 1, 14).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val daily = SalesGoalCalculator.bounds(SalesGoalPeriod.DAILY, now, zone)
        val weekly = SalesGoalCalculator.bounds(SalesGoalPeriod.WEEKLY, now, zone)
        val monthly = SalesGoalCalculator.bounds(SalesGoalPeriod.MONTHLY, now, zone)
        assertEquals(LocalDate.of(2024, 1, 14).atStartOfDay(zone).toInstant().toEpochMilli(), daily.startInclusive)
        assertEquals(LocalDate.of(2024, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli(), daily.endExclusive)
        assertEquals(LocalDate.of(2024, 1, 8).atStartOfDay(zone).toInstant().toEpochMilli(), weekly.startInclusive)
        assertEquals(LocalDate.of(2024, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli(), weekly.endExclusive)
        assertEquals(LocalDate.of(2024, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(), monthly.startInclusive)
        assertEquals(LocalDate.of(2024, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli(), monthly.endExclusive)
    }

    @Test
    fun progressUsesOnlyCompletedSalesForTheRequestedOwnerAndClampsRemainingAtZero() {
        val now = LocalDate.of(2024, 1, 14).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val inPeriod = LocalDate.of(2024, 1, 14).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val sales = listOf(
            sale("manual", 400L, inPeriod, "owner-a", PaymentStatus.COMPLETED),
            sale("pending", 900L, inPeriod, "owner-a", PaymentStatus.PENDING),
            sale("failed", 700L, inPeriod, "owner-a", PaymentStatus.FAILED),
            sale("other-owner", 500L, inPeriod, "owner-b", PaymentStatus.COMPLETED),
            sale("previous-day", 800L, inPeriod - 86_400_000L, "owner-a", PaymentStatus.COMPLETED)
        )
        val result = SalesGoalCalculator.progress(sales, "owner-a", 1_000L, SalesGoalPeriod.DAILY, now, zone)
        assertEquals(400L, result.actualMinor)
        assertEquals(40, result.percent)
        assertEquals(600L, result.remainingMinor)

        val exceeded = SalesGoalCalculator.progress(sales, "owner-a", 300L, SalesGoalPeriod.DAILY, now, zone)
        assertEquals(133, exceeded.percent)
        assertEquals(0L, exceeded.remainingMinor)
    }

    @Test
    fun monthlyAndWeeklyTotalsDoNotCountPendingOrFailedTransactions() {
        val now = LocalDate.of(2024, 2, 12).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val ts = LocalDate.of(2024, 2, 12).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val sales = listOf(
            sale("completed", 1_250L, ts, "owner-a", PaymentStatus.COMPLETED),
            sale("pending", 9_000L, ts, "owner-a", PaymentStatus.PENDING),
            sale("failed", 8_000L, ts, "owner-a", PaymentStatus.FAILED),
            sale("cancelled", 7_000L, ts, "owner-a", PaymentStatus.CANCELLED),
            sale("uncertain", 6_000L, ts, "owner-a", PaymentStatus.UNCERTAIN)
        )
        assertEquals(1_250L, SalesGoalCalculator.progress(sales, "owner-a", null, SalesGoalPeriod.WEEKLY, now, zone).actualMinor)
        assertEquals(1_250L, SalesGoalCalculator.progress(sales, "owner-a", null, SalesGoalPeriod.MONTHLY, now, zone).actualMinor)
    }

    @Test
    fun emptyAndZeroGoalsHaveExplicitProgressState() {
        val now = LocalDate.of(2024, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val none = SalesGoalCalculator.progress(emptyList(), "owner-a", null, SalesGoalPeriod.DAILY, now, zone)
        val zero = SalesGoalCalculator.progress(emptyList(), "owner-a", 0L, SalesGoalPeriod.DAILY, now, zone)
        assertEquals(null, none.percent)
        assertEquals(null, none.remainingMinor)
        assertEquals(100, zero.percent)
        assertEquals(0L, zero.remainingMinor)
    }

    @Test
    fun historyShowsOnlyExistingCompletedPriorPeriods() {
        val now = LocalDate.of(2024, 3, 3).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val prior = LocalDate.of(2024, 3, 2).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val current = LocalDate.of(2024, 3, 3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val history = SalesGoalCalculator.historicalRevenue(
            listOf(
                sale("prior", 250L, prior, "owner-a", PaymentStatus.COMPLETED),
                sale("pending", 900L, prior, "owner-a", PaymentStatus.PENDING),
                sale("current", 500L, current, "owner-a", PaymentStatus.COMPLETED)
            ), "owner-a", SalesGoalPeriod.DAILY, now, zone
        )
        assertEquals(1, history.size)
        assertEquals(250L, history.single().amountMinor)
    }

    @Test
    fun monthlyTargetUsesActual28To31CalendarDays() {
        val rate = listOf(goalRate("2023-01-01", 2_000L), goalRate("2024-01-01", 2_000L))
        fun targetAt(date: LocalDate): Long? = SalesGoalCalculator.periodTarget(
            rate,
            SalesGoalPeriod.MONTHLY,
            date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            zone
        )
        assertEquals(62_000L, targetAt(LocalDate.of(2024, 1, 15)))
        assertEquals(58_000L, targetAt(LocalDate.of(2024, 2, 15)))
        assertEquals(62_000L, targetAt(LocalDate.of(2024, 3, 15)))
        assertEquals(56_000L, targetAt(LocalDate.of(2023, 2, 15)))
    }

    @Test
    fun mondayWeekCrossingMonthUsesTheRateApplicableOnEachDate() {
        val history = listOf(
            goalRate("2024-02-26", 2_000L),
            goalRate("2024-03-01", 3_000L)
        )
        val now = LocalDate.of(2024, 3, 2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(17_000L, SalesGoalCalculator.periodTarget(history, SalesGoalPeriod.WEEKLY, now, zone))
        assertEquals(2_000L, SalesGoalCalculator.targetForDate(history, LocalDate.of(2024, 2, 29), now, zone))
        assertEquals(3_000L, SalesGoalCalculator.targetForDate(history, LocalDate.of(2024, 3, 1), now, zone))
    }

    @Test
    fun newDailyTargetChangesOnlyDatesOnOrAfterItsEffectiveDate() {
        val history = listOf(
            goalRate("2024-06-01", 1_500L),
            goalRate("2024-06-15", 2_500L)
        )
        val now = LocalDate.of(2024, 6, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(1_500L, SalesGoalCalculator.targetForDate(history, LocalDate.of(2024, 6, 14), now, zone))
        assertEquals(2_500L, SalesGoalCalculator.targetForDate(history, LocalDate.of(2024, 6, 15), now, zone))
        assertEquals(2_500L, SalesGoalCalculator.targetForDate(history, LocalDate.of(2024, 6, 21), now, zone))
        assertEquals(61_000L, SalesGoalCalculator.periodTarget(history, SalesGoalPeriod.MONTHLY, now, zone))
    }

    private fun sale(id: String, amount: Long, timestamp: Long, uid: String, status: PaymentStatus) =
        SaleEntity(id, id, amount, "M-Pesa", timestamp, ownerUid = uid, paymentStatus = status.name)

    private fun goalRate(date: String, target: Long) = SalesGoalHistoryEntity(
        ownerUid = "owner-a",
        effectiveAtMillis = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(),
        effectiveDate = date,
        dailyTargetMinor = target
    )
}
