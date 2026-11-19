package com.example.microbusinesssystem.ui

import com.example.microbusinesssystem.data.repository.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerScopedItemsTest {

    @Test
    fun accountSwitchLogoutAndStaleRowsNeverExposeAnotherOwnersData() = runBlocking {
        val accountA = SessionState.Authenticated("account-a", "a@example.test", "Owner A", "A", "")
        val accountB = SessionState.Authenticated("account-b", "b@example.test", "Owner B", "B", "")
        val sessions = MutableStateFlow<SessionState>(accountA)
        val rows = MutableStateFlow(
            listOf(
                OwnedRow("a-item", "account-a"),
                OwnedRow("b-item", "account-b"),
                OwnedRow("legacy-item", null)
            )
        )
        val visibleRows = ownerScopedItems(sessions, rows) { it.ownerUid }

        assertEquals(listOf("a-item"), visibleRows.first { it.isNotEmpty() }.map { it.id })

        sessions.value = SessionState.Unauthenticated
        assertEquals(emptyList<OwnedRow>(), visibleRows.first())
        rows.value = listOf(OwnedRow("late-a-item", "account-a"))
        assertEquals(emptyList<OwnedRow>(), visibleRows.first())

        sessions.value = accountB
        assertEquals(emptyList<OwnedRow>(), visibleRows.first())
        rows.value = listOf(OwnedRow("stale-a-item", "account-a"))
        assertEquals(emptyList<OwnedRow>(), visibleRows.first())
        rows.value = listOf(OwnedRow("b-item", "account-b"))
        assertEquals(listOf("b-item"), visibleRows.first { it.isNotEmpty() }.map { it.id })

        sessions.value = accountA
        rows.value = listOf(OwnedRow("a-item", "account-a"))
        assertEquals(listOf("a-item"), visibleRows.first { it.isNotEmpty() }.map { it.id })
    }

    private data class OwnedRow(val id: String, val ownerUid: String?)
}
