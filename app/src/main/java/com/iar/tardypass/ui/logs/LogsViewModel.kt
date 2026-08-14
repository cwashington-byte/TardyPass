package com.iar.tardypass.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.repo.PassRepository
import com.iar.tardypass.domain.Pass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModel(private val passes: PassRepository) : ViewModel() {

    val todayOnly = MutableStateFlow(true)

    val logs: StateFlow<List<Pass>> = todayOnly
        .flatMapLatest { today -> if (today) passes.today() else passes.latest(200) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> =
        passes.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun exportCsv(): String = passes.exportCsv()

    fun clearAll() {
        viewModelScope.launch { passes.clearAll() }
    }
}
