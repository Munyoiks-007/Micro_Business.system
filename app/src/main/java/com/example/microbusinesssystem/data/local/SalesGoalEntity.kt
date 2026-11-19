package com.example.microbusinesssystem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-defined sales targets, stored in minor currency units and keyed to one Firebase account. */
@Entity(tableName = "sales_goals")
data class SalesGoalEntity(
    @PrimaryKey val ownerUid: String,
    val dailyTargetMinor: Long?,
    val weeklyTargetMinor: Long?,
    val monthlyTargetMinor: Long?
)
