package com.example.microbusinesssystem.data.repository

import kotlinx.coroutines.CancellationException

internal enum class InventoryMutation {
    SAVE,
    DELETE
}

internal class InventorySyncException(
    val mutation: InventoryMutation,
    cause: Exception,
    val localMutationCommitted: Boolean = true
) : Exception("Inventory was changed locally but could not be synchronized.", cause)

internal class InventoryLocalCleanupException(cause: Exception) :
    Exception("The cloud delete completed but the local inventory copy could not be removed.", cause)

/** Executes the existing local-first policy and makes a remote failure explicit. */
internal suspend fun <T> persistInventoryThenSync(
    mutation: InventoryMutation,
    localWrite: suspend () -> T,
    remoteWrite: suspend () -> Unit
): T {
    val result = localWrite()
    try {
        remoteWrite()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw InventorySyncException(mutation, error)
    }
    return result
}

internal suspend fun <T> deleteInventoryRemoteThenLocal(
    remoteDelete: suspend () -> Unit,
    localDelete: suspend () -> T
): T {
    try {
        remoteDelete()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw InventorySyncException(InventoryMutation.DELETE, error, localMutationCommitted = false)
    }

    try {
        return localDelete()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw InventoryLocalCleanupException(error)
    }
}
