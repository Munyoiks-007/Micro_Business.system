package com.example.microbusinesssystem.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.microbusinesssystem.data.local.SaleEntity
import com.example.microbusinesssystem.data.repository.MpesaInitiationResult
import com.example.microbusinesssystem.data.repository.SalesRepository
import com.example.microbusinesssystem.data.repository.SandboxResetResult
import com.example.microbusinesssystem.data.repository.SessionRepository
import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SalesViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionRepository.sessionState
    private val filters = MutableStateFlow(SalesListFilters())
    val dateRange = filters.map { it.dateRange }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val salesState: StateFlow<UiState<List<SaleEntity>>> = combine(
        sessionState,
        filters
    ) { session, currentFilters -> session to currentFilters }
        .flatMapLatest { (session, currentFilters) ->
            when (session) {
                SessionState.Loading -> flowOf(UiState.Loading)
                SessionState.Unauthenticated -> flowOf(UiState.Idle)
                is SessionState.ProfileUnavailable -> flowOf(UiState.Error(session.message))
                is SessionState.Authenticated -> flow<UiState<List<SaleEntity>>> {
                    emit(UiState.Loading)
                    val bounds = currentFilters.dateRange?.epochBounds()
                    emitAll(
                        salesRepository.salesForOwner(session.uid, bounds?.first, bounds?.second).map { sales ->
                            UiState.Success<List<SaleEntity>>(
                                SalesListFilter.apply(
                                    sales,
                                    session.uid,
                                    currentFilters.copy(dateRange = null)
                                )
                            )
                        }
                    )
                }
            }
        }.catch {
        emit(UiState.Error("Sales could not be loaded."))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    fun setDateRange(range: SalesDateRange) {
        filters.value = filters.value.copy(dateRange = range)
    }

    fun clearDateRange() {
        filters.value = filters.value.copy(dateRange = null)
    }

    fun setSearchQuery(query: String) {
        filters.value = filters.value.copy(searchQuery = query)
    }

    fun setPaymentStatus(status: String?) {
        filters.value = filters.value.copy(paymentStatus = status)
    }

    suspend fun recordManualSale(itemName: String, quantity: Int, unitPriceMinor: Long): SaleEntity {
        return salesRepository.recordManualSale(itemName, quantity, unitPriceMinor)
    }

    suspend fun initiateMpesaPayment(
        itemName: String,
        quantity: Int,
        unitPriceMinor: Long,
        phone: String,
    ): MpesaInitiationResult {
        return try {
            salesRepository.initiateMpesaPayment(itemName, quantity, unitPriceMinor, phone)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            MpesaInitiationResult.Error(invalid.message ?: "Check the sale details and try again.")
        } catch (_: IllegalStateException) {
            MpesaInitiationResult.Error("Your session could not be verified. Sign in again and try once more.")
        } catch (_: Exception) {
            MpesaInitiationResult.Error("The M-Pesa request could not be started. Please try again.")
        }
    }

    suspend fun resetUnresolvedSandboxMpesaAttempts(): SandboxResetResult {
        return salesRepository.resetUnresolvedSandboxMpesaAttempts()
    }
}
