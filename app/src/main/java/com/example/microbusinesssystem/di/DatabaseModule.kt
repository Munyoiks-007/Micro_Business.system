package com.example.microbusinesssystem.di

import android.content.Context
import androidx.room.Room
import com.example.microbusinesssystem.data.local.AppDatabase
import com.example.microbusinesssystem.data.local.InventoryDao
import com.example.microbusinesssystem.data.local.SaleDao
import com.example.microbusinesssystem.data.local.SalesGoalDao
import com.example.microbusinesssystem.data.local.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "microbusiness_database"
        )
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides
    fun provideInventoryDao(database: AppDatabase): InventoryDao = database.inventoryDao()

    @Provides
    fun provideSaleDao(database: AppDatabase): SaleDao = database.saleDao()

    @Provides
    fun provideSalesGoalDao(database: AppDatabase): SalesGoalDao = database.salesGoalDao()

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()
}
