package com.iar.tardypass.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.remote.FirestoreSync
import com.iar.tardypass.data.repo.PassRepository
import com.iar.tardypass.domain.Pass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Whose passes to show: just this printer, or every device in the school. */
enum class LogScope { DEVICE, SCHOOL }

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModel(
    private val passes: PassRepository,
    private val sync: FirestoreSync? = null
) : ViewModel() {

    val todayOnly = MutableStateFlow(true)
    val scope = MutableStateFlow(LogScope.DEVICE)

    val syncAvailable: Boolean get() = sync?.isAvailable == true

    val logs: StateFlow<List<Pass>> = combine(todayOnly, scope) { today, s -> today to s }
        .flatMapLatest { (today, s) ->
            when (s) {
                LogScope.DEVICE -> if (today) passes.today() else passes.latest(200)
                LogScope.SCHOOL -> {
                    val remote = sync?.observeAllPasses() ?: flowOf(emptyList())
                    if (today) {
                        val start = startOfToday()
                        remote.map { list -> list.filter { it.timestampMillis >= start } }
                    } else remote
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> =
        passes.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun exportCsv(): String = passes.exportCsv()

    fun clearAll() {
        // Only ever clears this device's copy; cloud records are the audit trail
        // and are deletable by admins in the console, not from a hallway device.
        viewModelScope.launch { passes.clearAll() }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
