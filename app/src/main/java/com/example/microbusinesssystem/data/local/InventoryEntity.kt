package com.example.microbusinesssystem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String,
    val price: Long, // Price in minor units (cents / KES * 100)
    val quantity: Int,
    val lowStockThreshold: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
    val ownerUid: String? = null
)
