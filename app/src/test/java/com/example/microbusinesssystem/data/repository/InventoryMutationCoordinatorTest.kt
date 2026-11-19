package com.example.microbusinesssystem.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryMutationCoordinatorTest {
    @Test
    fun remoteFailureIsReportedAfterLocalWriteHasCommitted() = runBlocking {
        var localItemPresent = false
        var remoteAttempted = false

        val error = assertThrows(InventorySyncException::class.java) {
            runBlocking {
                persistInventoryThenSync(
                    mutation = InventoryMutation.SAVE,
                    localWrite = {
                        localItemPresent = true
                        "item-1"
                    },
                    remoteWrite = {
                        remoteAttempted = true
                        throw IllegalStateException("remote rejected")
                    }
                )
            }
        }

        assertEquals(InventoryMutation.SAVE, error.mutation)
        assertTrue(localItemPresent)
        assertTrue(remoteAttempted)
        assertEquals("remote rejected", error.cause?.message)
    }

    @Test
    fun localFailureDoesNotAttemptRemoteWrite() = runBlocking {
        var remoteAttempted = false

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                persistInventoryThenSync(
                    mutation = InventoryMutation.DELETE,
                    localWrite = { throw IllegalArgumentException("local failure") },
                    remoteWrite = { remoteAttempted = true }
                )
            }
        }

        assertFalse(remoteAttempted)
    }

    @Test
    fun remoteDeleteFailureLeavesLocalItemUntouchedForRetry() = runBlocking {
        var localItemPresent = true
        var localDeleteAttempted = false

        val error = assertThrows(InventorySyncException::class.java) {
            runBlocking {
                deleteInventoryRemoteThenLocal(
                    remoteDelete = { throw IllegalStateException("remote unavailable") },
                    localDelete = {
                        localDeleteAttempted = true
                        localItemPresent = false
                    }
                )
            }
        }

        assertEquals(InventoryMutation.DELETE, error.mutation)
        assertFalse(error.localMutationCommitted)
        assertTrue(localItemPresent)
        assertFalse(localDeleteAttempted)
    }

    @Test
    fun localDeleteFailureAfterRemoteSuccessIsReportedSeparately() = runBlocking {
        var remoteDeleted = false

        assertThrows(InventoryLocalCleanupException::class.java) {
            runBlocking {
                deleteInventoryRemoteThenLocal(
                    remoteDelete = { remoteDeleted = true },
                    localDelete = { throw IllegalStateException("database unavailable") }
                )
            }
        }

        assertTrue(remoteDeleted)
    }
}
