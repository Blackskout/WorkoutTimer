package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import ru.hopes.workouttimer.presentation.session.SessionExercise
import ru.hopes.workouttimer.presentation.session.Superset
import ru.hopes.workouttimer.presentation.session.WorkoutExecutionState
import ru.hopes.workouttimer.presentation.session.WorkoutSession
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
import javax.inject.Inject

/**
 * Тонкий адаптер экрана выполнения: состояние и действия — у [WorkoutSessionManager].
 * Сам решает одно: что делать с сессией, когда запись экрана уходит из стека.
 */
@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    private val manager: WorkoutSessionManager
) : ViewModel() {

    // Сессия, которую этот экран начал или застал; onCleared трогает только её.
    private var ownedSessionId: Long? = null

    /**
     * Фаза текущей сессии. None экрану не отдаётся: после выхода уходящий экран держит
     * последний кадр, а не мигает «Загрузкой». Начальное значение — из текущего снимка,
     * иначе при возврате в идущую тренировку экран на кадр показал бы Loading.
     */
    val uiState: StateFlow<WorkoutExecutionState> = manager.session
        .mapNotNull { (it as? WorkoutSession.Present)?.phase }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (manager.session.value as? WorkoutSession.Present)?.phase ?: WorkoutExecutionState.Loading
        )

    /** Шапка и шторка выбора: название, список, позиция. Тики отдыха её не меняют. */
    val chrome: StateFlow<ExecutionChrome> = manager.session
        .mapNotNull { (it as? WorkoutSession.Present)?.toChrome() }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            (manager.session.value as? WorkoutSession.Present)?.toChrome() ?: ExecutionChrome()
        )

    val finishError: StateFlow<Boolean> = manager.finishError

    val isLastSetOfWorkout: Boolean
        get() = manager.isLastSetOfWorkout

    /**
     * Идемпотентен для своей сессии в любой фазе: пересоздание активности на «ГОТОВО» не должно
     * запускать ту же тренировку заново. Возврат из свёрнутой идущей тренировки идёт через
     * менеджер, который отдаёт её sessionId.
     */
    fun start(workoutId: Int) {
        val current = (manager.session.value as? WorkoutSession.Present)?.sessionId
        if (current != null && current == ownedSessionId) return
        ownedSessionId = manager.start(workoutId)
    }

    /** «Повторить» после ошибки: всегда новая загрузка, владение переходит к новой сессии. */
    fun retry(workoutId: Int) {
        ownedSessionId = manager.start(workoutId)
    }

    fun skipRest() = manager.skipRest()

    fun onExerciseFinished() = manager.onExerciseFinished()

    fun moveToExercise(index: Int) = manager.moveToExercise(index)

    fun pairWith(index: Int) = manager.pairWith(index)

    fun unpair() = manager.unpair()

    fun updateExerciseNote(index: Int, note: String) = manager.updateExerciseNote(index, note)

    fun updateExerciseWeightAndReps(index: Int, weight: Double, extraWeight: Double, reps: Int) =
        manager.updateExerciseWeightAndReps(index, weight, extraWeight, reps)

    fun dismissFinishError() = manager.dismissFinishError()

    /** «Выйти без сохранения» из меню ⋮: экран сначала уходит, потом сессия сбрасывается. */
    fun abandon() = manager.abandon()

    override fun onCleared() {
        super.onCleared()
        // close() сам ничего не делает, если тренировка идёт (экран свернули) или сессия
        // уже другая; закрывает только Loading, Error и Finished этой сессии.
        ownedSessionId?.let { manager.close(it) }
    }
}

/** Всё, что экрану выполнения нужно помимо фазы. */
data class ExecutionChrome(
    val workoutName: String = "",
    val exercises: List<SessionExercise> = emptyList(),
    val exerciseIndex: Int = 0,
    val isFinishing: Boolean = false,
    val supersets: List<Superset> = emptyList()
) {
    val currentExerciseNumber: Int get() = exerciseIndex + 1
    val totalExercises: Int get() = exercises.size

    fun partnerOf(index: Int): Int? = supersets.firstOrNull { it.contains(index) }
        ?.let { if (it.lead == index) it.second else it.lead }

    /** С чем текущее можно связать в суперсет: несделанное и ещё без пары. Пусто, если текущее занято. */
    val supersetCandidates: List<Int>
        get() {
            val current = exercises.getOrNull(exerciseIndex) ?: return emptyList()
            if (current.isDone || partnerOf(exerciseIndex) != null) return emptyList()
            return exercises.indices.filter { it != exerciseIndex && !exercises[it].isDone && partnerOf(it) == null }
        }
}

private fun WorkoutSession.Present.toChrome() = ExecutionChrome(
    workoutName = workoutName,
    exercises = exercises,
    exerciseIndex = exerciseIndex,
    isFinishing = isFinishing,
    supersets = supersets
)
