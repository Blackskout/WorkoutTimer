package ru.hopes.workouttimer.presentation.screen.exportImport

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.repository.ExportImportRepository
import ru.hopes.workouttimer.domain.repository.ImportResult
import ru.hopes.workouttimer.domain.usecase.ExportAllWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.ImportWorkoutsUseCase
import javax.inject.Inject

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportUseCase: ExportAllWorkoutsUseCase,
    private val importUseCase: ImportWorkoutsUseCase,
    private val exportImportRepo: ExportImportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExportImportUiState>(ExportImportUiState.Idle)
    val uiState: StateFlow<ExportImportUiState> = _uiState.asStateFlow()

    var showExportDialog by mutableStateOf(false)
        private set

    var exportedUri by mutableStateOf<Uri?>(null)
        private set

    fun exportWorkouts() {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading

            try {
                val json = exportUseCase()
                
                // Сохраняем в кэш для sharing
                exportImportRepo.shareJson(json)
                
                _uiState.value = ExportImportUiState.ExportSuccess
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error(e.message, R.string.export_error_export)
            }
        }
    }

    fun importWorkouts(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ExportImportUiState.Loading

            try {
                val result = importUseCase(uri)
                _uiState.value = ExportImportUiState.ImportSuccess(result)
            } catch (e: Exception) {
                _uiState.value = ExportImportUiState.Error(e.message, R.string.export_error_import)
            }
        }
    }

    fun resetState() {
        _uiState.value = ExportImportUiState.Idle
    }

    fun dismissDialog() {
        showExportDialog = false
    }
}

sealed class ExportImportUiState {
    object Idle : ExportImportUiState()
    object Loading : ExportImportUiState()
    object ExportSuccess : ExportImportUiState()
    data class ImportSuccess(val result: ImportResult) : ExportImportUiState()
    /** [detail] — текст исключения, если он есть; иначе экран показывает [fallback]. */
    data class Error(val detail: String?, @StringRes val fallback: Int) : ExportImportUiState()
}
