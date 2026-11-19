package com.example.microbusinesssystem.ui.sales

import com.example.microbusinesssystem.data.local.SaleEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SalesDateRange(val startDate: LocalDate, val endDate: LocalDate) {
    init {
        require(!endDate.isBefore(startDate)) { "End date must not be before start date." }
    }

    fun epochBounds(zone: ZoneId = BUSINESS_ZONE): Pair<Long, Long> =
        startDate.atStartOfDay(zone).toInstant().toEpochMilli() to
            endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
    }
}

internal data class SalesListFilters(
    val dateRange: SalesDateRange? = null,
    val searchQuery: String = "",
    val paymentStatus: String? = null
)

internal object SalesListFilter {
    fun apply(
        sales: List<SaleEntity>,
        ownerUid: String,
        filters: SalesListFilters,
        zone: ZoneId = SalesDateRange.BUSINESS_ZONE
    ): List<SaleEntity> {
        val (start, end) = filters.dateRange?.epochBounds(zone) ?: (Long.MIN_VALUE to Long.MAX_VALUE)
        val query = filters.searchQuery.trim().lowercase()
        return sales.filter { sale ->
            sale.ownerUid == ownerUid &&
                sale.timestamp >= start && sale.timestamp < end &&
                (query.isEmpty() || sale.itemsSummary.lowercase().contains(query) ||
                    sale.itemName.orEmpty().lowercase().contains(query)) &&
                (filters.paymentStatus == null || sale.paymentStatus == filters.paymentStatus)
        }
    }
}
