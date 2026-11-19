package com.example.microbusinesssystem.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migration3To5PreservesRowsAndClassifiesLegacyMpesaAsUncertain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-v3-${System.currentTimeMillis()}.db"
        createVersion3Database(context, databaseName)

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.readableDatabase.query(
                "SELECT id, name, category, price, quantity, lowStockThreshold, lastUpdated, ownerUid " +
                    "FROM inventory WHERE id = 'legacy-item'"
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("legacy-item", cursor.getString(0))
                assertEquals("Tea", cursor.getString(1))
                assertEquals("Grocery", cursor.getString(2))
                assertEquals(1250L, cursor.getLong(3))
                assertEquals(7, cursor.getInt(4))
                assertEquals(3, cursor.getInt(5))
                assertEquals(1_700_000_000_000L, cursor.getLong(6))
                assertTrue(cursor.isNull(7))
            }
            database.openHelper.readableDatabase.query(
                "SELECT id, itemsSummary, totalAmount, paymentMethod, timestamp, ownerUid, " +
                    "saleType, paymentStatus, itemName, quantity, unitPrice " +
                    "FROM sales WHERE id = 'legacy-sale'"
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("legacy-sale", cursor.getString(0))
                assertEquals("Tea x2", cursor.getString(1))
                assertEquals(2500L, cursor.getLong(2))
                assertEquals("Cash", cursor.getString(3))
                assertEquals(1_700_000_100_000L, cursor.getLong(4))
                assertTrue(cursor.isNull(5))
                assertEquals("MANUAL", cursor.getString(6))
                assertEquals("COMPLETED", cursor.getString(7))
                assertTrue(cursor.isNull(8))
                assertTrue(cursor.isNull(9))
                assertTrue(cursor.isNull(10))
            }
            database.openHelper.readableDatabase.query(
                "SELECT saleType, paymentStatus FROM sales WHERE id = 'legacy-mpesa'"
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("MPESA", cursor.getString(0))
                assertEquals("UNCERTAIN", cursor.getString(1))
            }

            assertTrue(database.inventoryDao().getAllItems("test-owner").first().isEmpty())
            assertTrue(database.saleDao().getAllSales("test-owner").first().isEmpty())

            database.inventoryDao().insertItem(
                InventoryEntity(
                    id = "owned-item",
                    name = "Coffee",
                    category = "Grocery",
                    price = 900L,
                    quantity = 4,
                    lowStockThreshold = 2,
                    lastUpdated = 1_700_000_200_000L,
                    ownerUid = "test-owner"
                )
            )
            database.saleDao().insertSale(
                SaleEntity(
                    id = "owned-sale",
                    itemsSummary = "Coffee x1",
                    totalAmount = 900L,
                    paymentMethod = "Cash",
                    timestamp = 1_700_000_300_000L,
                    ownerUid = "test-owner"
                )
            )

            val ownedInventory = database.inventoryDao().getAllItems("test-owner").first()
                .single { it.id == "owned-item" }
            val ownedSale = database.saleDao().getAllSales("test-owner").first()
                .single { it.id == "owned-sale" }
            assertEquals("test-owner", ownedInventory.ownerUid)
            assertEquals("test-owner", ownedSale.ownerUid)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion3Database(context: android.content.Context, databaseName: String) {
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                """CREATE TABLE `users` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `email` TEXT NOT NULL,
                    `passwordHash` TEXT NOT NULL,
                    `ownerName` TEXT NOT NULL,
                    `businessName` TEXT NOT NULL,
                    `phone` TEXT NOT NULL
                )""".trimIndent()
            )
            database.execSQL(
                "CREATE UNIQUE INDEX `index_users_email` ON `users` (`email`)"
            )
            database.execSQL(
                """CREATE TABLE `inventory` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `price` INTEGER NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `lowStockThreshold` INTEGER NOT NULL,
                    `lastUpdated` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent()
            )
            database.execSQL(
                """CREATE TABLE `sales` (
                    `id` TEXT NOT NULL,
                    `itemsSummary` TEXT NOT NULL,
                    `totalAmount` INTEGER NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent()
            )
            database.execSQL(
                """INSERT INTO `inventory`
                    (`id`, `name`, `category`, `price`, `quantity`, `lowStockThreshold`, `lastUpdated`)
                    VALUES ('legacy-item', 'Tea', 'Grocery', 1250, 7, 3, 1700000000000)""".trimIndent()
            )
            database.execSQL(
                """INSERT INTO `sales`
                    (`id`, `itemsSummary`, `totalAmount`, `paymentMethod`, `timestamp`)
                    VALUES ('legacy-sale', 'Tea x2', 2500, 'Cash', 1700000100000)""".trimIndent()
            )
            database.execSQL(
                """INSERT INTO `sales`
                    (`id`, `itemsSummary`, `totalAmount`, `paymentMethod`, `timestamp`)
                    VALUES ('legacy-mpesa', 'Coffee', 300, 'M-Pesa', 1700000200000)""".trimIndent()
            )
            database.version = 3
        }
    }
}
