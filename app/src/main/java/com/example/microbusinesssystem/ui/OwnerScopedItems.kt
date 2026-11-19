package com.example.microbusinesssystem.ui

import com.example.microbusinesssystem.data.repository.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> ownerScopedItems(
    sessionState: Flow<SessionState>,
    source: Flow<List<T>>,
    ownerUid: (T) -> String?
): Flow<List<T>> = sessionState.flatMapLatest { state ->
    val uid = (state as? SessionState.Authenticated)?.uid
        ?: return@flatMapLatest flowOf(emptyList())

    flow {
        emit(emptyList())
        emitAll(source.map { rows -> rows.filter { ownerUid(it) == uid } })
    }
}
