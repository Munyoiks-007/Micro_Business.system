package com.example.microbusinesssystem.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.microbusinesssystem.data.local.AppDatabase
import com.example.microbusinesssystem.data.local.InventoryEntity
import com.example.microbusinesssystem.data.remote.DarajaApiClient
import com.example.microbusinesssystem.data.remote.DarajaConfiguration
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryAuthenticationGuardTest {

    @Test
    fun unauthenticatedInventoryAndSalesOperationsFailSafely() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = requireNotNull(FirebaseOptions.fromResource(context))
        val firebaseApp = FirebaseApp.initializeApp(
            context,
            options,
            "repository-auth-test-${System.currentTimeMillis()}"
        )
        val auth = FirebaseAuth.getInstance(firebaseApp)
        val firestore = FirebaseFirestore.getInstance(firebaseApp)
        auth.signOut()
        assertNull(auth.currentUser)

        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val inventoryRepository = InventoryRepository(database.inventoryDao(), firestore, auth)
            val salesRepository = SalesRepository(
                database.saleDao(),
                DarajaApiClient.apiService,
                DarajaConfiguration("", "", "", "", ""),
                firestore,
                auth
            )

            assertTrue(inventoryRepository.allItems.first().isEmpty())
            assertTrue(inventoryRepository.lowStockItems.first().isEmpty())
            assertTrue(salesRepository.allSales.first().isEmpty())

            expectAuthenticationFailure {
                inventoryRepository.saveItem(
                    InventoryEntity("unauth-item", "Item", "Test", 100L, 1, 1, ownerUid = "forged")
                )
            }
            expectAuthenticationFailure { inventoryRepository.deleteItem("unauth-item") }
            expectAuthenticationFailure {
                salesRepository.recordManualSale("Sale", quantity = 1, unitPriceMinor = 100L)
            }
            expectAuthenticationFailure { salesRepository.getTodayRevenue() }
            expectAuthenticationFailure {
                salesRepository.initiateMpesaPayment(
                    itemName = "Item",
                    quantity = 1,
                    unitPriceMinor = 100L,
                    phone = "254700000000"
                )
            }

            assertEquals(0, database.inventoryDao().getAllItems("forged").first().size)
            assertEquals(0, database.saleDao().getAllSales("forged").first().size)
        } finally {
            database.close()
            firebaseApp.delete()
        }
    }

    private suspend fun expectAuthenticationFailure(operation: suspend () -> Unit) {
        try {
            operation()
            fail("The operation should require an authenticated user.")
        } catch (_: IllegalStateException) {
        }
    }
}
