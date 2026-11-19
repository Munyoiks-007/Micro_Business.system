package com.example.microbusinesssystem.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.microbusinesssystem.data.local.InventoryEntity
import com.example.microbusinesssystem.data.repository.InventoryRepository
import com.example.microbusinesssystem.data.repository.SessionRepository
import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.ui.UiState
import com.example.microbusinesssystem.ui.ownerScopedItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val ownerScopedItems = ownerScopedItems(
        sessionRepository.sessionState,
        inventoryRepository.allItems
    ) { it.ownerUid }

    val itemsState: StateFlow<UiState<List<InventoryEntity>>> = combine(
        sessionRepository.sessionState,
        ownerScopedItems
    ) { session, items ->
        when (session) {
            SessionState.Loading -> UiState.Loading
            SessionState.Unauthenticated -> UiState.Idle
            is SessionState.Authenticated -> UiState.Success(items)
            is SessionState.ProfileUnavailable -> UiState.Error(session.message)
        }
    }.catch {
        emit(UiState.Error("Inventory could not be loaded."))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    suspend fun saveItem(item: InventoryEntity) {
        inventoryRepository.saveItem(item)
    }

    suspend fun updateItem(item: InventoryEntity) {
        inventoryRepository.updateItem(item)
    }

    suspend fun deleteItem(id: String) {
        inventoryRepository.deleteItem(id)
    }
}
