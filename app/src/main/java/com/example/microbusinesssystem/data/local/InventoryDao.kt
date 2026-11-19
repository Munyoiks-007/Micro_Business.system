package com.example.microbusinesssystem.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory WHERE ownerUid = :ownerUid")
    fun getAllItems(ownerUid: String): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE ownerUid = :ownerUid AND quantity <= lowStockThreshold")
    fun getLowStockItems(ownerUid: String): Flow<List<InventoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: InventoryEntity)

    @Query("""
        UPDATE inventory SET
            name = :name,
            category = :category,
            price = :price,
            quantity = :quantity,
            lowStockThreshold = :lowStockThreshold,
            lastUpdated = :lastUpdated
        WHERE id = :id AND ownerUid = :ownerUid
    """)
    suspend fun updateItem(
        id: String,
        ownerUid: String,
        name: String,
        category: String,
        price: Long,
        quantity: Int,
        lowStockThreshold: Int,
        lastUpdated: Long
    ): Int

    @Query("SELECT * FROM inventory WHERE id = :id AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getItem(id: String, ownerUid: String): InventoryEntity?

    @Query("DELETE FROM inventory WHERE id = :id AND ownerUid = :ownerUid")
    suspend fun deleteItem(id: String, ownerUid: String): Int
}
