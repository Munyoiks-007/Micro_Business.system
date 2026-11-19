package com.example.microbusinesssystem.ui.sales

import com.example.microbusinesssystem.data.local.SaleEntity
import com.example.microbusinesssystem.data.local.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SalesDateRangeTest {
    private val zone = ZoneId.of("Africa/Nairobi")

    @Test
    fun emptySelectedPeriodProducesEmptyList() {
        val sales = listOf(sale("nov16", LocalDate.of(2024, 11, 16), 12, 0))
        val filtered = SalesListFilter.apply(
            sales, "owner-a", SalesListFilters(SalesDateRange(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 11, 10)))
        )
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun startDateAndEntireEndDateAreInclusiveButAdjacentDatesAreExcluded() {
        val sales = listOf(
            sale("before", LocalDate.of(2024, 11, 30), 23, 59, 59, 999),
            sale("start", LocalDate.of(2024, 12, 1), 0, 0),
            sale("end", LocalDate.of(2024, 12, 10), 23, 59, 59, 999),
            sale("after", LocalDate.of(2024, 12, 11), 0, 0)
        )
        val filtered = SalesListFilter.apply(
            sales,
            "owner-a",
            SalesListFilters(SalesDateRange(LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 10)))
        )
        assertEquals(listOf("start", "end"), filtered.map { it.id })
    }

    @Test
    fun monthBoundariesUseNairobiCalendarDates() {
        val range = SalesDateRange(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 1))
        val (start, end) = range.epochBounds(zone)
        val jan31AtMidnight = LocalDate.of(2024, 1, 31).atStartOfDay(zone).toInstant().toEpochMilli()
        val feb2AtMidnight = LocalDate.of(2024, 2, 2).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(jan31AtMidnight, start)
        assertEquals(feb2AtMidnight, end)
    }

    @Test
    fun timestampsAtNairobiMidnightRespectTheLocalBoundary() {
        val midnight = LocalDate.of(2024, 5, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sales = listOf(
            SaleEntity("before", "before", 100, "Cash", midnight - 1, ownerUid = "owner-a"),
            SaleEntity("at", "at", 100, "Cash", midnight, ownerUid = "owner-a")
        )
        val selected = SalesListFilter.apply(
            sales, "owner-a", SalesListFilters(SalesDateRange(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 1))), zone
        )
        assertEquals(listOf("at"), selected.map { it.id })
    }

    @Test
    fun changingFiltersAgainReplacesResultsIncludingWithEmptyResults() {
        val sales = listOf(
            sale("nov16", LocalDate.of(2024, 11, 16), 12, 0),
            sale("nov05", LocalDate.of(2024, 11, 5), 12, 0)
        )
        val first = SalesListFilter.apply(
            sales, "owner-a", SalesListFilters(SalesDateRange(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 11, 20)))
        )
        val next = SalesListFilter.apply(
            sales, "owner-a", SalesListFilters(SalesDateRange(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 11, 10)))
        )
        val cleared = SalesListFilter.apply(sales, "owner-a", SalesListFilters())
        assertEquals(listOf("nov16", "nov05"), first.map { it.id })
        assertEquals(listOf("nov05"), next.map { it.id })
        assertEquals(listOf("nov16", "nov05"), cleared.map { it.id })

        val noMatch = SalesListFilter.apply(
            sales, "owner-a", SalesListFilters(SalesDateRange(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 11, 3)))
        )
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun existingSearchAndStatusFiltersComposeWithDateAndUidScope() {
        val sales = listOf(
            sale("manual", LocalDate.of(2024, 11, 5), 12, 0, status = PaymentStatus.COMPLETED.name),
            sale("pending", LocalDate.of(2024, 11, 5), 12, 0, status = PaymentStatus.PENDING.name),
            sale("other-owner", LocalDate.of(2024, 11, 5), 12, 0, uid = "owner-b")
        )
        val result = SalesListFilter.apply(
            sales,
            "owner-a",
            SalesListFilters(
                dateRange = SalesDateRange(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 11, 10)),
                searchQuery = "tea",
                paymentStatus = PaymentStatus.COMPLETED.name
            )
        )
        assertEquals(listOf("manual"), result.map { it.id })
    }

    private fun sale(
        id: String,
        date: LocalDate,
        hour: Int,
        minute: Int,
        second: Int = 0,
        millis: Int = 0,
        uid: String = "owner-a",
        status: String = PaymentStatus.COMPLETED.name
    ): SaleEntity {
        val instant = date.atTime(hour, minute, second, millis * 1_000_000).atZone(zone).toInstant().toEpochMilli()
        return SaleEntity(id, "Tea sale", 100, "Cash", instant, ownerUid = uid, itemName = "Tea", paymentStatus = status)
    }
}
