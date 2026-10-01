package ru.hopes.workouttimer.presentation.screen.workoutPreview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveWorkoutUseCase
import ru.hopes.workouttimer.presentation.session.AddResult
import ru.hopes.workouttimer.presentation.session.RunningWorkout
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
import javax.inject.Inject

private const val TAG = "WorkoutPreviewVM"

@HiltViewModel
class WorkoutPreviewViewModel @Inject constructor(
    private val observeWorkoutUseCase: ObserveWorkoutUseCase,
    private val catalogRepository: ExerciseCatalogRepository,
    private val getLastSessionDurationsUseCase: GetLastSessionDurationsUseCase,
    private val sessionManager: WorkoutSessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutPreviewState())
    val state = _state.asStateFlow()

    private var loadedId: Int? = null

    fun load(workoutId: Int) {
        // LaunchedEffect перезапускается при пересоздании активити, ViewModel — нет.
        if (loadedId == workoutId) return
        loadedId = workoutId
        // runningWorkout, а не session: тики отдыха пересобирали бы экран 5 раз в секунду.
        combine(
            observeWorkoutUseCase(workoutId),
            catalogRepository.observeLastSessionSets(),
            getLastSessionDurationsUseCase(),
            sessionManager.runningWorkout
        ) { workout, lastSets, durations, running ->
            WorkoutPreviewState(
                isLoaded = true,
                workout = workout,
                lastTime = lastTimeByCatalog(lastSets),
                lastDurationMillis = durations[workoutId],
                bottom = previewBottomOf(workoutId, workout, running),
                addedExerciseIds = running?.addedExerciseIds.orEmpty(),
                todayDone = running?.takeIf { !it.isLoading && it.workoutId == workoutId }?.doneSets
            )
        }
            .onEach { _state.value = it }
            .catch { e ->
                Log.e(TAG, "Просмотр тренировки не прочитан", e)
                _state.update { it.copy(isLoaded = true, loadFailed = true) }
            }
            .launchIn(viewModelScope)
    }

    /** «+ В сегодняшнюю»: признак «Добавлено» придёт сам через runningWorkout. */
    fun addToday(exercise: Exercise): AddResult = sessionManager.addExerciseToday(exercise)
}

data class WorkoutPreviewState(
    val isLoaded: Boolean = false,
    val loadFailed: Boolean = false,
    /** null после загрузки — тренировку удалили. */
    val workout: Workout? = null,
    /** Последняя сессия по записи справочника (из любой тренировки). */
    val lastTime: Map<Long, LastTime> = emptyMap(),
    val lastDurationMillis: Long? = null,
    val bottom: PreviewBottom = PreviewBottom.None,
    /** exercise.id строк этой тренировки, уже добавленных в идущую. */
    val addedExerciseIds: Set<Int> = emptySet(),
    /** Идёт эта тренировка: сделано подходов сегодня по exercise.id; иначе null. */
    val todayDone: Map<Int, Int>? = null
) {
    /** «+ В сегодняшнюю» — только когда идёт другая тренировка. */
    val canAddToday: Boolean get() = bottom is PreviewBottom.RunningOther
}

/** Нижняя зона экрана просмотра. */
sealed interface PreviewBottom {
    /** Сессия загружается (доли секунды), тренировки нет или она пустая. */
    data object None : PreviewBottom

    data object Start : PreviewBottom

    /** Идёт эта тренировка. */
    data object Return : PreviewBottom

    /** Идёт другая тренировка: подсказка вместо кнопки, у строк — «+ В сегодняшнюю». */
    data class RunningOther(val workoutName: String) : PreviewBottom
}

/** Последний раз, когда делали упражнение: время сессии и её подходы по порядку. */
data class LastTime(val finishedAt: Long, val sets: List<SessionSet>)

/**
 * Подходы последней сессии каждой записи справочника. Источник и так отдаёт одну сессию
 * на запись; если пришли подходы нескольких, берутся подходы самой поздней.
 */
fun lastTimeByCatalog(sets: List<LoggedSet>): Map<Long, LastTime> =
    sets.groupBy { it.set.catalogId }.mapValues { (_, logged) ->
        val latest = logged.maxBy { it.finishedAt }
        LastTime(
            finishedAt = latest.finishedAt,
            sets = logged.filter { it.set.sessionId == latest.set.sessionId }.map { it.set }
        )
    }

/**
 * Без сессии (None, Error, Finished — сводки нет) — «Начать», если есть что начинать;
 * своя — «Вернуться»; чужая — подсказка; Loading — ничего.
 */
fun previewBottomOf(workoutId: Int, workout: Workout?, running: RunningWorkout?): PreviewBottom = when {
    running == null ->
        if (workout != null && workout.exercises.isNotEmpty()) PreviewBottom.Start else PreviewBottom.None
    running.isLoading -> PreviewBottom.None
    running.workoutId == workoutId -> PreviewBottom.Return
    else -> PreviewBottom.RunningOther(running.workoutName)
}
