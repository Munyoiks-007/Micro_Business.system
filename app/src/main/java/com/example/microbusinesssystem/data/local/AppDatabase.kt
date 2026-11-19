package com.example.microbusinesssystem.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, InventoryEntity::class, SaleEntity::class, SalesGoalEntity::class, SalesGoalHistoryEntity::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun saleDao(): SaleDao
    abstract fun salesGoalDao(): SalesGoalDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `inventory` ADD COLUMN `ownerUid` TEXT")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `ownerUid` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `itemName` TEXT")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `quantity` INTEGER")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `unitPrice` INTEGER")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `saleType` TEXT NOT NULL DEFAULT 'MANUAL'")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `paymentStatus` TEXT NOT NULL DEFAULT 'COMPLETED'")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `merchantRequestId` TEXT")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `checkoutRequestId` TEXT")
                database.execSQL("ALTER TABLE `sales` ADD COLUMN `receiptNumber` TEXT")
                database.execSQL(
                    "UPDATE `sales` SET `saleType` = 'MPESA', `paymentStatus` = 'UNCERTAIN' " +
                        "WHERE LOWER(`paymentMethod`) = 'm-pesa'"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sales_goals` (
                        `ownerUid` TEXT NOT NULL,
                        `dailyTargetMinor` INTEGER,
                        `weeklyTargetMinor` INTEGER,
                        `monthlyTargetMinor` INTEGER,
                        PRIMARY KEY(`ownerUid`)
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sales_goal_history` (
                        `ownerUid` TEXT NOT NULL,
                        `effectiveAtMillis` INTEGER NOT NULL,
                        `effectiveDate` TEXT NOT NULL,
                        `dailyTargetMinor` INTEGER,
                        PRIMARY KEY(`ownerUid`, `effectiveAtMillis`)
                    )""".trimIndent()
                )
            }
        }
    }
}
