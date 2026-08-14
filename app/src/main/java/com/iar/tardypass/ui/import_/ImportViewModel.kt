package com.iar.tardypass.ui.import_

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.csv.RosterImportResult
import com.iar.tardypass.data.repo.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data object Parsing : ImportUiState()
    data class Preview(val text: String, val result: RosterImportResult) : ImportUiState()
    data class Done(val imported: Int, val errorCount: Int) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

class ImportViewModel(private val students: StudentRepository) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    val rosterCount = students.activeCount

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

    fun confirm(fullReplace: Boolean) {
        val preview = _state.value as? ImportUiState.Preview ?: return
        _state.value = ImportUiState.Parsing
        viewModelScope.launch {
            val outcome = students.importCsv(preview.text, fullReplace)
            _state.value = ImportUiState.Done(outcome.imported, outcome.errors.size)
        }
    }

    fun reset() {
        _state.value = ImportUiState.Idle
    }
}
