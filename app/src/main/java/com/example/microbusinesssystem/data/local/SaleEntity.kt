package com.example.microbusinesssystem.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey
    val id: String,
    val itemsSummary: String,
    val totalAmount: Long, // Total amount in minor units (cents / KES * 100)
    val paymentMethod: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ownerUid: String? = null,
    val itemName: String? = null,
    val quantity: Int? = null,
    val unitPrice: Long? = null,
    @ColumnInfo(defaultValue = "'MANUAL'")
    val saleType: String = SaleType.MANUAL.name,
    @ColumnInfo(defaultValue = "'COMPLETED'")
    val paymentStatus: String = PaymentStatus.COMPLETED.name,
    val merchantRequestId: String? = null,
    val checkoutRequestId: String? = null,
    val receiptNumber: String? = null
)
