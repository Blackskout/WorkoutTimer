package ru.hopes.workouttimer.presentation.screen.exercises

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.model.exerciseNameKey
import ru.hopes.workouttimer.domain.usecase.ChangeCatalogUnitUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveCatalogSummariesUseCase
import ru.hopes.workouttimer.domain.usecase.RenameCatalogExerciseUseCase
import javax.inject.Inject

private const val TAG = "ExerciseCatalogVM"

@HiltViewModel
class ExerciseCatalogViewModel @Inject constructor(
    observeCatalogSummariesUseCase: ObserveCatalogSummariesUseCase,
    private val renameCatalogExerciseUseCase: RenameCatalogExerciseUseCase,
    private val changeCatalogUnitUseCase: ChangeCatalogUnitUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(ExerciseCatalogState())
    val state = _state.asStateFlow()

    init {
        combine(observeCatalogSummariesUseCase(), query) { all, input ->
            _state.update {
                it.copy(
                    rows = filterCatalog(all, input),
                    isLoaded = true,
                    isCatalogEmpty = all.isEmpty()
                )
            }
        }
            .catch { e ->
                Log.e(TAG, "Справочник не прочитан", e)
                _state.update { it.copy(errorMessage = R.string.catalog_error_load) }
            }
            .launchIn(viewModelScope)
    }

    fun updateQuery(input: String) {
        // Сырой ввод, как в поиске тренировок: обрезка съела бы пробел между словами.
        _state.update { it.copy(query = input) }
        query.value = input
    }

    fun startRename(target: CatalogExercise) {
        _state.update { it.copy(renameTarget = target, renameTaken = false) }
    }

    fun cancelRename() {
        _state.update { it.copy(renameTarget = null, renameTaken = false) }
    }

    /** Пользователь правит название — прежняя ошибка «уже есть» больше не про него. */
    fun clearRenameTaken() {
        if (_state.value.renameTaken) _state.update { it.copy(renameTaken = false) }
    }

    fun confirmRename(newName: String) {
        val target = _state.value.renameTarget ?: return
        launchGuarded(R.string.catalog_error_rename) {
            when (renameCatalogExerciseUseCase(target.id, newName)) {
                RenameResult.RENAMED -> _state.update { it.copy(renameTarget = null, renameTaken = false) }
                RenameResult.NAME_TAKEN -> _state.update { it.copy(renameTaken = true) }
                // Кнопка «Сохранить» неактивна при пустом поле; диалог просто остаётся открытым.
                RenameResult.BLANK -> Unit
            }
        }
    }

    fun changeUnit(target: CatalogExercise, unit: ExerciseUnit) {
        // Та же единица — не изменение: convertLoadToUnit обнулил бы добавку к плите.
        if (target.unit == unit) return
        launchGuarded(R.string.catalog_error_unit) {
            changeCatalogUnitUseCase(target.id, unit)
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Сбой записи — снекбар, а не падение; транзакция DAO откатилась целиком. */
    private fun launchGuarded(@StringRes message: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Запись справочника не удалась", e)
                _state.update { it.copy(errorMessage = message, renameTarget = null, renameTaken = false) }
            }
        }
    }
}

/**
 * Поиск по справочнику — тем же ключом, что и подсказки редактора. Пустой запрос
 * проверяется до ключа: exerciseNameKey("") — это ключ «Без названия».
 */
internal fun filterCatalog(all: List<CatalogSummary>, query: String): List<CatalogSummary> {
    if (query.isBlank()) return all
    val key = exerciseNameKey(query)
    return all.filter { exerciseNameKey(it.exercise.name).contains(key) }
}

data class ExerciseCatalogState(
    val query: String = "",
    val rows: List<CatalogSummary> = emptyList(),
    val isLoaded: Boolean = false,
    val isCatalogEmpty: Boolean = false,
    val renameTarget: CatalogExercise? = null,
    val renameTaken: Boolean = false,
    @StringRes val errorMessage: Int? = null
)
