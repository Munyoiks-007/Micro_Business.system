package com.example.microbusinesssystem.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerScopedDaoTest {

    @Test
    fun inventoryQueriesDeletesAndInsertsAreOwnerScoped() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.inventoryDao()
            dao.insertItem(inventory("shared-item", "Account A item", "account-a", quantity = 1))
            dao.insertItem(inventory("account-b-item", "Account B item", "account-b", quantity = 1))
            dao.insertItem(inventory("legacy-item", "Unowned legacy item", null, quantity = 1))

            assertEquals(listOf("shared-item"), dao.getAllItems("account-a").first().map { it.id })
            assertEquals(listOf("account-b-item"), dao.getAllItems("account-b").first().map { it.id })
            assertTrue(dao.getAllItems("unknown-account").first().isEmpty())
            assertEquals("shared-item", dao.getItem("shared-item", "account-a")?.id)
            assertEquals(null, dao.getItem("shared-item", "account-b"))
            assertEquals(listOf("shared-item"), dao.getLowStockItems("account-a").first().map { it.id })
            assertEquals(listOf("account-b-item"), dao.getLowStockItems("account-b").first().map { it.id })

            assertEquals(
                1,
                dao.updateItem(
                    id = "shared-item",
                    ownerUid = "account-a",
                    name = "Updated A item",
                    category = "Updated category",
                    price = 250L,
                    quantity = 8,
                    lowStockThreshold = 4,
                    lastUpdated = 100L
                )
            )
            assertEquals(
                0,
                dao.updateItem(
                    id = "shared-item",
                    ownerUid = "account-b",
                    name = "Unauthorized change",
                    category = "Test",
                    price = 999L,
                    quantity = 0,
                    lowStockThreshold = 10,
                    lastUpdated = 200L
                )
            )
            val updatedA = dao.getAllItems("account-a").first().single { it.id == "shared-item" }
            assertEquals("Updated A item", updatedA.name)
            assertEquals(250L, updatedA.price)
            assertEquals(8, updatedA.quantity)
            assertEquals(4, updatedA.lowStockThreshold)
            assertEquals("account-a", updatedA.ownerUid)

            assertEquals(0, dao.deleteItem("account-b-item", "account-a"))
            assertEquals(1, dao.getAllItems("account-b").first().size)
            assertEquals(1, dao.deleteItem("account-b-item", "account-b"))

            try {
                dao.insertItem(inventory("shared-item", "Account B replacement", "account-b"))
                fail("A duplicate ID must not replace another owner's row.")
            } catch (_: SQLiteConstraintException) {
            }
            val unchanged = dao.getAllItems("account-a").first().single()
            assertEquals("Updated A item", unchanged.name)
            assertEquals("account-a", unchanged.ownerUid)
            assertTrue(dao.getAllItems("account-b").first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun salesListsDateQueriesAndTotalsAreOwnerScoped() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.saleDao()
            dao.insertSale(sale("a-old", 100L, 50L, "account-a"))
            dao.insertSale(sale("a-new", 200L, 150L, "account-a"))
            dao.insertSale(sale("b-sale", 900L, 150L, "account-b"))
            dao.insertSale(sale("legacy-sale", 700L, 150L, null))

            assertEquals(
                listOf("a-new", "a-old"),
                dao.getAllSales("account-a").first().map { it.id }
            )
            assertEquals(listOf("b-sale"), dao.getAllSales("account-b").first().map { it.id })
            assertTrue(dao.getAllSales("unknown-account").first().isEmpty())
            assertEquals(listOf("a-new"), dao.getSalesSince("account-a", 100L).first().map { it.id })
            assertEquals(listOf("a-new"), dao.getSalesBetween("account-a", 100L, 151L).first().map { it.id })
            assertEquals(listOf("a-old"), dao.getSalesBetween("account-a", 50L, 100L).first().map { it.id })
            assertTrue(dao.getSalesBetween("account-a", 151L, 200L).first().isEmpty())
            assertEquals(300L, dao.getRevenueSince("account-a", 0L))
            assertEquals(900L, dao.getRevenueSince("account-b", 0L))
            assertEquals(null, dao.getRevenueSince("unknown-account", 0L))

            val initialRevenue = CompletableDeferred<Long?>()
            val revenueUpdates = async(start = CoroutineStart.UNDISPATCHED) {
                dao.observeRevenueBetween("account-a", 100L, 200L)
                    .onEach { if (!initialRevenue.isCompleted) initialRevenue.complete(it) }
                    .take(2)
                    .toList()
            }
            assertEquals(200L, initialRevenue.await())
            dao.insertSale(sale("a-today-extra", 50L, 175L, "account-a"))
            assertEquals(listOf(200L, 250L), revenueUpdates.await())
            assertEquals(900L, dao.observeRevenueBetween("account-b", 100L, 200L).first())
        } finally {
            database.close()
        }
    }

    @Test
    fun onlyCompletedSalesCountAsRevenueAndUnresolvedMpesaBlocksDuplicateAttempts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.saleDao()
            dao.insertSale(
                sale("manual", 1_000L, 100L, "account-a").copy(
                    saleType = SaleType.MANUAL.name,
                    paymentStatus = PaymentStatus.COMPLETED.name
                )
            )
            val pending = sale("pending", 2_000L, 200L, "account-a").copy(
                paymentMethod = "M-Pesa",
                saleType = SaleType.MPESA.name,
                paymentStatus = PaymentStatus.PENDING.name
            )
            assertTrue(dao.insertMpesaAttemptIfNone("account-a", pending))
            assertTrue(
                !dao.insertMpesaAttemptIfNone(
                    "account-a",
                    sale("duplicate", 2_000L, 201L, "account-a").copy(
                        paymentMethod = "M-Pesa",
                        saleType = SaleType.MPESA.name,
                        paymentStatus = PaymentStatus.INITIATED.name
                    )
                )
            )
            assertEquals(
                1,
                dao.updateMpesaState(
                    id = "pending",
                    ownerUid = "account-a",
                    paymentStatus = PaymentStatus.UNCERTAIN.name,
                    merchantRequestId = null,
                    checkoutRequestId = null,
                    receiptNumber = null
                )
            )
            assertTrue(
                !dao.insertMpesaAttemptIfNone(
                    "account-a",
                    sale("uncertain-duplicate", 2_000L, 202L, "account-a").copy(
                        paymentMethod = "M-Pesa",
                        saleType = SaleType.MPESA.name,
                        paymentStatus = PaymentStatus.INITIATED.name
                    )
                )
            )
            dao.insertSale(
                sale("failed", 3_000L, 300L, "account-a").copy(
                    paymentMethod = "M-Pesa",
                    saleType = SaleType.MPESA.name,
                    paymentStatus = PaymentStatus.FAILED.name
                )
            )
            dao.insertSale(
                sale("cancelled", 4_000L, 400L, "account-a").copy(
                    paymentMethod = "M-Pesa",
                    saleType = SaleType.MPESA.name,
                    paymentStatus = PaymentStatus.CANCELLED.name
                )
            )
            dao.insertSale(
                sale("uncertain", 5_000L, 500L, "account-a").copy(
                    paymentMethod = "M-Pesa",
                    saleType = SaleType.MPESA.name,
                    paymentStatus = PaymentStatus.UNCERTAIN.name
                )
            )
            dao.insertSale(
                sale("confirmed-mpesa", 600L, 600L, "account-a").copy(
                    paymentMethod = "M-Pesa",
                    saleType = SaleType.MPESA.name,
                    paymentStatus = PaymentStatus.COMPLETED.name
                )
            )

            assertEquals(1_600L, dao.getRevenueSince("account-a", 0L))
            assertEquals(0L, dao.getRevenueSince("account-b", 0L) ?: 0L)

            assertEquals(
                1,
                dao.updateMpesaState(
                    id = "pending",
                    ownerUid = "account-a",
                    paymentStatus = PaymentStatus.COMPLETED.name,
                    merchantRequestId = "merchant-1",
                    checkoutRequestId = "checkout-1",
                    receiptNumber = "receipt-1"
                )
            )
            assertEquals(3_600L, dao.getRevenueSince("account-a", 0L))
        } finally {
            database.close()
        }
    }

    private fun inventory(
        id: String,
        name: String,
        ownerUid: String?,
        quantity: Int = 5
    ) = InventoryEntity(
        id = id,
        name = name,
        category = "Test",
        price = 100L,
        quantity = quantity,
        lowStockThreshold = 2,
        ownerUid = ownerUid
    )

    private fun sale(id: String, amount: Long, timestamp: Long, ownerUid: String?) = SaleEntity(
        id = id,
        itemsSummary = "Test sale",
        totalAmount = amount,
        paymentMethod = "Cash",
        timestamp = timestamp,
        ownerUid = ownerUid
    )
}
