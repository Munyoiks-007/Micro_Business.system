package com.example.microbusinesssystem.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesGoalDao {
    @Query("SELECT * FROM sales_goals WHERE ownerUid = :ownerUid")
    fun observeGoal(ownerUid: String): Flow<SalesGoalEntity?>

    @Upsert
    suspend fun saveGoal(goal: SalesGoalEntity)

    @Query("DELETE FROM sales_goals WHERE ownerUid = :ownerUid")
    suspend fun deleteGoal(ownerUid: String)

    @Query("SELECT * FROM sales_goals WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun getGoal(ownerUid: String): SalesGoalEntity?

    @Query("SELECT * FROM sales_goal_history WHERE ownerUid = :ownerUid ORDER BY effectiveAtMillis")
    fun observeHistory(ownerUid: String): Flow<List<SalesGoalHistoryEntity>>

    @Query("SELECT * FROM sales_goal_history WHERE ownerUid = :ownerUid ORDER BY effectiveAtMillis")
    suspend fun getHistory(ownerUid: String): List<SalesGoalHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(entry: SalesGoalHistoryEntity)

    /** Preserve a pre-history daily goal as a baseline before the first new edit. */
    @Transaction
    suspend fun ensureLegacyTargetSnapshot(ownerUid: String, effectiveAtMillis: Long, effectiveDate: String) {
        if (getHistory(ownerUid).isNotEmpty()) return
        val legacyDailyTarget = getGoal(ownerUid)?.dailyTargetMinor ?: return
        insertHistory(
            SalesGoalHistoryEntity(
                ownerUid = ownerUid,
                effectiveAtMillis = effectiveAtMillis,
                effectiveDate = effectiveDate,
                dailyTargetMinor = legacyDailyTarget
            )
        )
    }

    /** Append a target change and update the legacy current row without touching its old weekly/monthly values. */
    @Transaction
    suspend fun setDailyTarget(
        ownerUid: String,
        dailyTargetMinor: Long?,
        requestedAtMillis: Long,
        effectiveDate: String
    ) {
        val history = getHistory(ownerUid)
        val legacyGoal = getGoal(ownerUid)
        val effectiveAt = maxOf(requestedAtMillis, (history.lastOrNull()?.effectiveAtMillis ?: 0L) + 1L)
        if (history.isEmpty() && legacyGoal?.dailyTargetMinor != null) {
            insertHistory(
                SalesGoalHistoryEntity(
                    ownerUid = ownerUid,
                    effectiveAtMillis = effectiveAt - 1L,
                    effectiveDate = effectiveDate,
                    dailyTargetMinor = legacyGoal.dailyTargetMinor
                )
            )
        }
        insertHistory(
            SalesGoalHistoryEntity(
                ownerUid = ownerUid,
                effectiveAtMillis = effectiveAt,
                effectiveDate = effectiveDate,
                dailyTargetMinor = dailyTargetMinor
            )
        )
        saveGoal(
            legacyGoal?.copy(dailyTargetMinor = dailyTargetMinor)
                ?: SalesGoalEntity(ownerUid, dailyTargetMinor, null, null)
        )
    }
}
