package com.example.microbusinesssystem.data.local

import androidx.room.Entity

/** Append-only target changes; each value is effective from its Nairobi calendar date. */
@Entity(
    tableName = "sales_goal_history",
    primaryKeys = ["ownerUid", "effectiveAtMillis"]
)
data class SalesGoalHistoryEntity(
    val ownerUid: String,
    val effectiveAtMillis: Long,
    val effectiveDate: String,
    val dailyTargetMinor: Long?
)
