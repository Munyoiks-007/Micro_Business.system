package com.example.microbusinesssystem.data.repository

import com.example.microbusinesssystem.data.local.InventoryDao
import com.example.microbusinesssystem.data.local.InventoryEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val authenticatedUidChanges: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    val allItems: Flow<List<InventoryEntity>> = authenticatedUidChanges.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else inventoryDao.getAllItems(uid)
    }

    val lowStockItems: Flow<List<InventoryEntity>> = authenticatedUidChanges.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else inventoryDao.getLowStockItems(uid)
    }

    suspend fun saveItem(item: InventoryEntity) {
        val uid = requireAuthenticatedUid()
        val ownedItem = item.copy(ownerUid = uid)
        withContext(Dispatchers.IO) {
            persistInventoryThenSync(
                mutation = InventoryMutation.SAVE,
                localWrite = { inventoryDao.insertItem(ownedItem) },
                remoteWrite = {
                    firestore.collection("users").document(uid)
                        .collection("inventory").document(ownedItem.id)
                        .set(ownedItem).await()
                }
            )
        }
    }

    suspend fun updateItem(item: InventoryEntity) {
        val uid = requireAuthenticatedUid()
        val ownedItem = item.copy(ownerUid = uid, lastUpdated = System.currentTimeMillis())
        withContext(Dispatchers.IO) {
            persistInventoryThenSync(
                mutation = InventoryMutation.SAVE,
                localWrite = {
                    val updated = inventoryDao.updateItem(
                        id = ownedItem.id,
                        ownerUid = uid,
                        name = ownedItem.name,
                        category = ownedItem.category,
                        price = ownedItem.price,
                        quantity = ownedItem.quantity,
                        lowStockThreshold = ownedItem.lowStockThreshold,
                        lastUpdated = ownedItem.lastUpdated
                    )
                    if (updated != 1) {
                        throw IllegalStateException("The inventory item does not belong to the authenticated user.")
                    }
                },
                remoteWrite = {
                    firestore.collection("users").document(uid)
                        .collection("inventory").document(ownedItem.id)
                        .set(ownedItem).await()
                }
            )
        }
    }

    suspend fun deleteItem(id: String) {
        val uid = requireAuthenticatedUid()
        withContext(Dispatchers.IO) {
            val ownedItem = inventoryDao.getItem(id, uid)
                ?: throw IllegalStateException("The inventory item does not belong to the authenticated user.")
            deleteInventoryRemoteThenLocal(
                remoteDelete = {
                    firestore.collection("users").document(uid)
                        .collection("inventory").document(ownedItem.id)
                        .delete().await()
                },
                localDelete = {
                    val deleted = inventoryDao.deleteItem(id, uid)
                    if (deleted != 1) {
                        throw IllegalStateException("The inventory item does not belong to the authenticated user.")
                    }
                    deleted
                }
            )
        }
    }

    private fun requireAuthenticatedUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("An authenticated user is required.")
}
