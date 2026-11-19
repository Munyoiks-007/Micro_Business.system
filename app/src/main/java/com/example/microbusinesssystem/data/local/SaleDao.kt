package com.example.microbusinesssystem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE ownerUid = :ownerUid ORDER BY timestamp DESC")
    fun getAllSales(ownerUid: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE ownerUid = :ownerUid AND timestamp >= :startTime ORDER BY timestamp DESC")
    fun getSalesSince(ownerUid: String, startTime: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE ownerUid = :ownerUid AND timestamp >= :startInclusive AND timestamp < :endExclusive ORDER BY timestamp DESC")
    fun getSalesBetween(ownerUid: String, startInclusive: Long, endExclusive: Long): Flow<List<SaleEntity>>

    @Query("""
        SELECT SUM(totalAmount) FROM sales
        WHERE ownerUid = :ownerUid
          AND timestamp >= :startTime
          AND timestamp < :endTime
          AND paymentStatus = 'COMPLETED'
    """)
    suspend fun getRevenueBetween(ownerUid: String, startTime: Long, endTime: Long): Long?

    @Query("""
        SELECT SUM(totalAmount) FROM sales
        WHERE ownerUid = :ownerUid
          AND timestamp >= :startTime
          AND timestamp < :endTime
          AND paymentStatus = 'COMPLETED'
    """)
    fun observeRevenueBetween(ownerUid: String, startTime: Long, endTime: Long): Flow<Long?>

    @Query("""
        SELECT SUM(totalAmount) FROM sales
        WHERE ownerUid = :ownerUid
          AND timestamp >= :startTime
          AND paymentStatus = 'COMPLETED'
    """)
    suspend fun getRevenueSince(ownerUid: String, startTime: Long): Long?

    @Query("""
        SELECT COUNT(*) FROM sales
        WHERE ownerUid = :ownerUid
          AND saleType = 'MPESA'
          AND paymentStatus IN ('INITIATED', 'PENDING', 'UNCERTAIN')
    """)
    suspend fun countUnresolvedMpesaAttempts(ownerUid: String): Int

    @Query("""
        SELECT * FROM sales
        WHERE ownerUid = :ownerUid
          AND saleType = 'MPESA'
          AND paymentStatus IN ('INITIATED', 'PENDING', 'UNCERTAIN')
    """)
    suspend fun getUnresolvedMpesaAttempts(
        ownerUid: String
    ): List<SaleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity)

    @Transaction
    suspend fun insertMpesaAttemptIfNone(
        ownerUid: String,
        sale: SaleEntity
    ): Boolean {
        if (sale.ownerUid != ownerUid ||
            countUnresolvedMpesaAttempts(ownerUid) > 0
        ) return false

        insertSale(sale)
        return true
    }

    @Query("""
        UPDATE sales SET
            paymentStatus = :paymentStatus,
            merchantRequestId = :merchantRequestId,
            checkoutRequestId = :checkoutRequestId,
            receiptNumber = :receiptNumber
        WHERE id = :id AND ownerUid = :ownerUid AND saleType = 'MPESA'
    """)
    suspend fun updateMpesaState(
        id: String,
        ownerUid: String,
        paymentStatus: String,
        merchantRequestId: String?,
        checkoutRequestId: String?,
        receiptNumber: String?
    ): Int

    @Query("""
        UPDATE sales
        SET paymentStatus = 'CANCELLED'
        WHERE ownerUid = :ownerUid
          AND saleType = 'MPESA'
          AND paymentStatus IN ('INITIATED', 'PENDING', 'UNCERTAIN')
    """)
    suspend fun resetUnresolvedMpesaAttempts(ownerUid: String): Int

    @Query("""
        UPDATE sales
        SET paymentStatus = 'CANCELLED'
        WHERE ownerUid = :ownerUid
          AND saleType = 'MPESA'
          AND paymentStatus IN ('INITIATED', 'PENDING', 'UNCERTAIN')
    """)
    suspend fun resolveUnresolvedSandboxAttempts(ownerUid: String): Int
}
