package com.example.microbusinesssystem.data.sync

import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.example.microbusinesssystem.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "microbusiness_database"
            ).build()
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()
            val uid = auth.currentUser?.uid ?: return Result.success()

            // Perform bidirectional sync between Room and Firestore
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object SyncScheduler {
    fun scheduleSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "MicroBusinessSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
