package com.iar.tardypass.ui.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.csv.RosterImportResult
import com.iar.tardypass.data.repo.StudentRepository
import com.iar.tardypass.domain.RosterDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data object Parsing : ImportUiState()
    data class Preview(
        val text: String,
        val result: RosterImportResult,
        val fullReplace: Boolean = false,
        /** Null while the diff is being computed — Import stays disabled until then. */
        val diff: RosterDiff? = null
    ) : ImportUiState()
    data class Done(
        val imported: Int,
        val errorCount: Int,
        val syncedToCloud: Boolean = false,
        val syncError: String? = null,
        val deactivated: Int = 0
    ) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

class ImportViewModel(
    private val students: StudentRepository,
    private val sharedCsv: MutableStateFlow<String?>
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    val rosterCount = students.activeCount

    init {
        // Picks up a CSV shared into the app; consuming it clears the holder so
        // rotating or revisiting the screen doesn't re-trigger the import.
        viewModelScope.launch {
            sharedCsv.collect { text ->
                if (text != null) {
                    sharedCsv.value = null
                    preview(text)
                }
            }
        }
    }

    /** Parse CSV text (from file picker or share intent) into a preview. */
    fun preview(text: String) {
        _state.value = ImportUiState.Parsing
        viewModelScope.launch {
            val result = students.preview(text)
            _state.value = if (result.students.isEmpty()) {
                ImportUiState.Error(
                    result.errors.firstOrNull()?.message ?: "No students found in this file"
                )
            } else {
                ImportUiState.Preview(text, result)
            }
        }
    }

    /**
     * Recomputes what a complete-roster import would deactivate. Called when the
     * checkbox is toggled; the Import button stays disabled until it lands, which
     * is what guarantees the count is shown before anyone can confirm.
     */
    fun setFullReplace(on: Boolean) {
        val p = _state.value as? ImportUiState.Preview ?: return
        _state.value = p.copy(fullReplace = on, diff = null)
        if (!on) return
        viewModelScope.launch {
            val diff = students.previewDiff(p.result.students.map { it.id }.toSet())
            (_state.value as? ImportUiState.Preview)
                ?.takeIf { it.fullReplace }
                ?.let { _state.value = it.copy(diff = diff) }
        }
    }

    fun confirm(fullReplace: Boolean) {
        val preview = _state.value as? ImportUiState.Preview ?: return
        _state.value = ImportUiState.Parsing
        viewModelScope.launch {
            val outcome = students.importCsv(preview.text, fullReplace)
            _state.value = ImportUiState.Done(
                imported = outcome.imported,
                errorCount = outcome.errors.size,
                syncedToCloud = outcome.syncedToCloud,
                syncError = outcome.syncError,
                deactivated = outcome.deactivatedInCloud ?: outcome.deactivatedLocally
            )
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }
}
