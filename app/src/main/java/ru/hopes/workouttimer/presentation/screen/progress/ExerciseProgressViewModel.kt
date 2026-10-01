package ru.hopes.workouttimer.presentation.screen.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseHistory
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.ProgressSummary
import ru.hopes.workouttimer.domain.model.summarizeProgress
import ru.hopes.workouttimer.domain.usecase.ObserveExerciseHistoryUseCase
import java.util.TimeZone
import javax.inject.Inject

private const val TAG = "ExerciseProgressVM"

@HiltViewModel
class ExerciseProgressViewModel @Inject constructor(
    private val observeExerciseHistoryUseCase: ObserveExerciseHistoryUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseProgressState())
    val state = _state.asStateFlow()

    private var loadedId: Long? = null

    fun load(catalogId: Long) {
        // LaunchedEffect перезапускается при пересоздании активити, ViewModel — нет:
        // второй подписчик на ту же запись только удвоил бы пересчёты.
        if (loadedId == catalogId) return
        loadedId = catalogId
        observeExerciseHistoryUseCase(catalogId)
            .onEach { history -> _state.update { it.withHistory(history) } }
            .catch { e ->
                Log.e(TAG, "Прогресс не прочитан", e)
                _state.update { it.copy(isLoaded = true, loadFailed = true) }
            }
            .launchIn(viewModelScope)
    }

    fun selectPeriod(period: ProgressPeriod) {
        _state.update { it.copy(period = period).recomputed() }
    }

    /** Тап по уже выбранной точке и тап мимо точек (null) снимают выбор. */
    fun selectPoint(sessionId: Long?) {
        _state.update { it.copy(selectedSessionId = if (sessionId == it.selectedSessionId) null else sessionId) }
    }
}

data class ExerciseProgressState(
    val isLoaded: Boolean = false,
    val loadFailed: Boolean = false,
    val exercise: CatalogExercise? = null,
    val sessions: List<ProgressSession> = emptyList(),
    val period: ProgressPeriod = ProgressPeriod.QUARTER,
    val summary: ProgressSummary? = null,
    val selectedSessionId: Long? = null
)

private fun ExerciseProgressState.withHistory(history: ExerciseHistory?): ExerciseProgressState =
    copy(isLoaded = true, exercise = history?.exercise, sessions = history?.sessions.orEmpty()).recomputed()

/**
 * Сводка пересчитывается при новых данных и смене периода. «Сейчас» берётся в
 * момент пересчёта: «1 мес» отсчитывается от текущей даты. Выбор точки, которой
 * в новом периоде нет, снимается — иначе подпись висела бы от невидимой точки.
 */
private fun ExerciseProgressState.recomputed(): ExerciseProgressState {
    val unit = exercise?.unit ?: return copy(summary = null, selectedSessionId = null)
    val summary = summarizeProgress(sessions, unit, period, System.currentTimeMillis(), TimeZone.getDefault())
    val selected = selectedSessionId?.takeIf { id -> summary.points.any { it.sessionId == id } }
    return copy(summary = summary, selectedSessionId = selected)
}
