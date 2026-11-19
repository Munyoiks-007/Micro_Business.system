package com.example.microbusinesssystem.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.microbusinesssystem.data.local.InventoryEntity
import com.example.microbusinesssystem.data.local.SaleEntity
import com.example.microbusinesssystem.data.local.PaymentStatus
import com.example.microbusinesssystem.data.repository.InventoryRepository
import com.example.microbusinesssystem.data.repository.SalesRepository
import com.example.microbusinesssystem.data.repository.SalesGoalRepository
import com.example.microbusinesssystem.data.repository.SessionRepository
import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.ui.UiState
import com.example.microbusinesssystem.ui.ownerScopedItems
import com.example.microbusinesssystem.util.GoalProgress
import com.example.microbusinesssystem.util.HistoricalRevenue
import com.example.microbusinesssystem.util.SalesGoalCalculator
import com.example.microbusinesssystem.util.SalesGoalPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class DashboardGoalsState(
    val daily: GoalProgress,
    val weekly: GoalProgress,
    val monthly: GoalProgress,
    val dailyHistory: List<HistoricalRevenue>,
    val weeklyHistory: List<HistoricalRevenue>,
    val monthlyHistory: List<HistoricalRevenue>,
    val hasConfiguredTargets: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val salesGoalRepository: SalesGoalRepository,
    inventoryRepository: InventoryRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionRepository.sessionState
    private val periodRefresh = MutableStateFlow(System.currentTimeMillis())

    init {
        // Refresh date-based totals and goals at the next Nairobi midnight, even if the
        // dashboard stays in the foreground across the day boundary.
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val nextMidnight = Instant.ofEpochMilli(now)
                    .atZone(SalesGoalCalculator.BUSINESS_ZONE)
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(SalesGoalCalculator.BUSINESS_ZONE)
                    .toInstant()
                    .toEpochMilli()
                delay((nextMidnight - now).coerceAtLeast(1L))
                periodRefresh.value = System.currentTimeMillis()
            }
        }
    }

    val allSales: StateFlow<List<SaleEntity>> = ownerScopedItems(
        sessionState,
        salesRepository.allSales
    ) { it.ownerUid }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lowStockItems: StateFlow<List<InventoryEntity>> = ownerScopedItems(
        sessionState,
        inventoryRepository.lowStockItems
    ) { it.ownerUid }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val businessName: StateFlow<String> = sessionState
        .map { state -> (state as? SessionState.Authenticated)?.businessName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val ownerName: StateFlow<String> = sessionState
        .map { state -> (state as? SessionState.Authenticated)?.ownerName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val completedSalesCount: StateFlow<Int> = allSales
        .map { sales -> sales.count { it.paymentStatus == PaymentStatus.COMPLETED.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val goals: StateFlow<UiState<DashboardGoalsState>> = combine(
        sessionState,
        allSales,
        salesGoalRepository.currentHistory,
        periodRefresh
    ) { session, sales, goal, now ->
        val uid = (session as? SessionState.Authenticated)?.uid ?: return@combine UiState.Idle
        val dailyTarget = SalesGoalCalculator.periodTarget(goal, SalesGoalPeriod.DAILY, now)
        val weeklyTarget = SalesGoalCalculator.periodTarget(goal, SalesGoalPeriod.WEEKLY, now)
        val monthlyTarget = SalesGoalCalculator.periodTarget(goal, SalesGoalPeriod.MONTHLY, now)
        UiState.Success(DashboardGoalsState(
            daily = SalesGoalCalculator.progress(sales, uid, dailyTarget, SalesGoalPeriod.DAILY, now),
            weekly = SalesGoalCalculator.progress(sales, uid, weeklyTarget, SalesGoalPeriod.WEEKLY, now),
            monthly = SalesGoalCalculator.progress(sales, uid, monthlyTarget, SalesGoalPeriod.MONTHLY, now),
            dailyHistory = SalesGoalCalculator.historicalRevenue(sales, uid, SalesGoalPeriod.DAILY, now),
            weeklyHistory = SalesGoalCalculator.historicalRevenue(sales, uid, SalesGoalPeriod.WEEKLY, now),
            monthlyHistory = SalesGoalCalculator.historicalRevenue(sales, uid, SalesGoalPeriod.MONTHLY, now),
            hasConfiguredTargets = listOf(dailyTarget, weeklyTarget, monthlyTarget).any { it != null }
        ))
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(UiState.Error("Sales goals and history could not be loaded."))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    fun refreshPeriodBoundaries() {
        periodRefresh.value = System.currentTimeMillis()
    }

    suspend fun saveGoals(daily: Long?) {
        salesGoalRepository.saveDailyTarget(daily)
    }

    val todayRevenue: StateFlow<UiState<Long>> = combine(sessionState, periodRefresh) { session, now ->
        session to now
    }.flatMapLatest { (session, now) ->
        when (session) {
            SessionState.Loading -> flowOf(UiState.Loading)
            SessionState.Unauthenticated -> flowOf(UiState.Idle)
            is SessionState.ProfileUnavailable -> flowOf(UiState.Error(session.message))
            is SessionState.Authenticated -> {
                val bounds = SalesGoalCalculator.bounds(SalesGoalPeriod.DAILY, now)
                flow<UiState<Long>> {
                    emit(UiState.Loading)
                    emitAll(
                        salesRepository.observeRevenueBetween(
                            session.uid,
                            bounds.startInclusive,
                            bounds.endExclusive
                        ).map { revenue ->
                            if (sessionRepository.isAuthenticatedAs(session.uid)) {
                                UiState.Success<Long>(revenue ?: 0L)
                            } else {
                                UiState.Idle
                            }
                        }
                    )
                }
            }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(UiState.Error("Today's revenue could not be loaded."))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)
}
