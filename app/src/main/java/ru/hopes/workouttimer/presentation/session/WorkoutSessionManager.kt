package ru.hopes.workouttimer.presentation.session

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.di.ApplicationScope
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.RecordedSet
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.domain.usecase.FinishWorkoutSessionUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.service.TimerNotificationService
import ru.hopes.workouttimer.presentation.service.WorkoutAlerts
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import ru.hopes.workouttimer.presentation.utils.VibrationManager
import ru.hopes.workouttimer.presentation.utils.WakeLockHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Идущая тренировка. Живёт весь процесс, а не экран выполнения: экран можно свернуть,
 * а таймер отдыха, уведомление и учёт простоя продолжают работать. Всё состояние меняется
 * на главном потоке (область — Main.immediate), поэтому поля без синхронизации.
 */
@Singleton
class WorkoutSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soundPlayer: SoundPlayer,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val vibrationManager: VibrationManager,
    private val wakeLockHelper: WakeLockHelper,
    private val workoutRepository: WorkoutRepository,
    private val finishWorkoutSessionUseCase: FinishWorkoutSessionUseCase,
    private val workoutAlerts: WorkoutAlerts,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _session = MutableStateFlow<WorkoutSession>(WorkoutSession.None)
    val session: StateFlow<WorkoutSession> = _session.asStateFlow()

    private val _finishError = MutableStateFlow(false)
    val finishError: StateFlow<Boolean> = _finishError.asStateFlow()

    private val _runningWorkout = MutableStateFlow<RunningWorkout?>(null)

    /** Сводка без тиков: MutableStateFlow не публикует равное значение. */
    val runningWorkout: StateFlow<RunningWorkout?> = _runningWorkout.asStateFlow()

    /** Сессия идёт: Loading, Rest или Active. Error, Finished и None — не идёт. */
    val isRunning: Boolean
        get() = present?.phase?.isRunning == true

    // Дочерняя область текущей сессии: загрузка, таймер, напоминание, записи заметки и веса.
    // Запись завершённой сессии идёт в scope — её не обрывает уход с экрана Finished.
    private var sessionScope: CoroutineScope = newSessionScope()
    private var lastSessionId = 0L

    private var sessionStartedAt = 0L

    internal var lastInteractionAt: Long = 0L
        private set
    internal var excludedIdleMillis: Long = 0L
        private set

    private val _recordedSets = mutableListOf<RecordedSet>()
    internal val recordedSets: List<RecordedSet> get() = _recordedSets

    // Последний подход уже в списке, но сессия не записалась: повторное нажатие
    // пробует записать снова, не добавляя подход второй раз.
    private var finishPending = false

    private var timerJob: Job? = null
    private var idleReminderJob: Job? = null

    internal val isIdleReminderJobActive: Boolean
        get() = idleReminderJob?.isActive == true

    internal val isRestTimerActive: Boolean
        get() = timerJob?.isActive == true

    private val present: WorkoutSession.Present?
        get() = _session.value as? WorkoutSession.Present

    // Текущий подход закрывает всю тренировку: экран спрашивает подтверждение перед
    // onExerciseFinished(), потому что дальше сессия уйдёт в БД. Считается по текущей
    // длине списка: добавленное упражнение отодвигает конец тренировки.
    val isLastSetOfWorkout: Boolean
        get() {
            val current = present ?: return false
            val phase = current.phase as? WorkoutExecutionState.Active ?: return false
            return phase.currentSet == phase.totalSets && current.exerciseIndex == current.exercises.lastIndex
        }

    fun dismissFinishError() {
        _finishError.value = false
    }

    /**
     * Начинает сессию и возвращает её sessionId. Идущую сессию (любой тренировки) не трогает
     * и возвращает её sessionId: экран выполнения зовёт start из LaunchedEffect, а тот
     * перезапускается при пересоздании активности (тема, шрифт, язык).
     */
    fun start(workoutId: Int): Long {
        present?.let { current -> if (current.phase.isRunning) return current.sessionId }
        resetSessionScope()
        _recordedSets.clear()
        finishPending = false
        _finishError.value = false
        val sessionId = ++lastSessionId
        setSession(
            WorkoutSession.Present(
                sessionId = sessionId,
                workoutId = workoutId,
                workoutName = "",
                exercises = emptyList(),
                exerciseIndex = 0,
                phase = WorkoutExecutionState.Loading
            )
        )
        sessionScope.launch {
            val workout = getWorkoutByIdUseCase(workoutId)
            if (workout != null && workout.exercises.isNotEmpty()) {
                val exercises = workout.exercises.sortedBy { it.order }.map { SessionExercise(it) }
                sessionStartedAt = System.currentTimeMillis()
                lastInteractionAt = sessionStartedAt
                excludedIdleMillis = 0L
                _recordedSets.clear()
                finishPending = false
                val first = exercises[0].exercise
                setSession(
                    WorkoutSession.Present(
                        sessionId = sessionId,
                        workoutId = workoutId,
                        workoutName = workout.name,
                        exercises = exercises,
                        exerciseIndex = 0,
                        phase = WorkoutExecutionState.Active(
                            exercise = first,
                            currentSet = 1,
                            totalSets = first.sets
                        )
                    )
                )
                scheduleIdleReminderIfActive()
            } else {
                updatePresent { it.copy(phase = WorkoutExecutionState.Error) }
            }
        }
        return sessionId
    }

    /** Выход без сохранения: всё, что раньше делал уход с экрана, плюс сброс состояния. */
    fun abandon() {
        endSession()
    }

    /**
     * Закрыть сессию [sessionId], если она не идёт: Loading, Error или Finished. Идущую
     * (свёрнутую) тренировку и чужую, более новую сессию не трогает.
     */
    fun close(sessionId: Long) {
        val current = present ?: return
        if (current.sessionId != sessionId || current.phase.isInProgress) return
        endSession()
    }

    // В БД ничего не пишется. Запись завершения идёт в scope и не отменяется: пользователь
    // уже подтвердил завершение; её корутина сверит sessionId и состояние не тронет.
    // «Отдых завершён» и «Вы всё ещё тренируетесь?» снимаются: тренировки, к которой они зовут, больше нет.
    private fun endSession() {
        resetSessionScope()
        wakeLockHelper.release()
        stopNotification()
        workoutAlerts.dismiss()
        soundPlayer.release()
        _recordedSets.clear()
        finishPending = false
        setSession(WorkoutSession.None)
        _finishError.value = false
    }

    /** Отменяет таймер, напоминание, загрузку и незаконченные записи заметки и веса. */
    private fun resetSessionScope() {
        sessionScope.cancel()
        sessionScope = newSessionScope()
    }

    fun skipRest() {
        registerInteraction()
        timerJob?.cancel()
        wakeLockHelper.release()
        stopNotification()
        updatePresent { current ->
            val phase = current.phase
            if (phase is WorkoutExecutionState.Rest) {
                current.copy(
                    phase = WorkoutExecutionState.Active(
                        exercise = phase.exercise,
                        currentSet = phase.currentSet,
                        totalSets = phase.totalSets,
                        weight = phase.exercise.weight,
                        reps = phase.exercise.reps
                    ),
                    restOver = false
                )
            } else {
                current
            }
        }
        scheduleIdleReminderIfActive()
    }

    private fun startRestTimer() {
        val rest = present?.phase as? WorkoutExecutionState.Rest ?: return
        timerJob?.cancel()

        wakeLockHelper.acquire(rest.restTimeMillis)

        val finishTime = System.currentTimeMillis() + rest.restTimeMillis

        startNotification(
            exerciseName = rest.exercise.name,
            currentSet = rest.currentSet,
            totalSets = rest.totalSets,
            timeLeftMillis = rest.restTimeMillis
        )

        timerJob = sessionScope.launch {
            var lastNotificationSecond = -1L

            countdownFlow(finishTime)
                .onCompletion { cause ->
                    if (cause == null) {
                        onRestFinished()
                    } else {
                        wakeLockHelper.release()
                    }
                }
                .collect { timeLeft ->
                    updatePresent { current ->
                        val phase = current.phase
                        if (phase is WorkoutExecutionState.Rest) {
                            current.copy(phase = phase.copy(restTimeMillis = timeLeft))
                        } else {
                            current
                        }
                    }

                    // Уведомление обновляется не чаще раза в секунду.
                    val currentSecond = timeLeft / 1000
                    if (currentSecond != lastNotificationSecond) {
                        lastNotificationSecond = currentSecond
                        updateNotification(
                            exerciseName = rest.exercise.name,
                            currentSet = rest.currentSet,
                            totalSets = rest.totalSets,
                            timeLeftMillis = timeLeft
                        )
                    }
                }
        }
    }

    private fun countdownFlow(finishTime: Long): Flow<Long> = flow {
        while (true) {
            val remaining = finishTime - System.currentTimeMillis()
            if (remaining <= 0) {
                emit(0L)
                break
            }
            emit(remaining)
            delay(200)
        }
    }

    internal fun onRestFinished() {
        val previous = present?.phase as? WorkoutExecutionState.Rest
        timerJob?.cancel()
        stopNotification()
        soundPlayer.playSound(R.raw.timer)
        vibrationManager.vibrate()
        wakeLockHelper.release()

        if (previous != null) {
            showRestFinishedNotification(
                exerciseName = previous.exercise.name,
                currentSet = previous.currentSet,
                totalSets = previous.totalSets
            )
        }

        updatePresent { current ->
            val phase = current.phase
            if (phase is WorkoutExecutionState.Rest) {
                current.copy(
                    phase = WorkoutExecutionState.Active(
                        exercise = phase.exercise,
                        currentSet = phase.currentSet,
                        totalSets = phase.totalSets
                    ),
                    restOver = true
                )
            } else {
                current
            }
        }
        scheduleIdleReminderIfActive()
    }

    fun onExerciseFinished() {
        if (present?.isFinishing == true) return
        registerInteraction()
        val current = present ?: return
        val phase = current.phase as? WorkoutExecutionState.Active ?: return
        // Подход снимается до перехода: на последнем подходе moveToNextExercise
        // сразу пишет сессию, и снимать будет уже поздно.
        val set = RecordedSet(
            catalogId = phase.exercise.catalogId,
            exerciseName = phase.exercise.name,
            weight = phase.weight,
            extraWeight = phase.extraWeight,
            reps = phase.reps,
            unit = phase.exercise.unit
        )
        // Повтор после сбоя записи: последний подход уже в списке — заменяем
        // его текущими значениями (плитку могли поправить), а не дублируем. Флаг снимается
        // сразу: если после сбоя добавили упражнение, его подходы должны дописываться.
        if (finishPending && _recordedSets.isNotEmpty()) {
            _recordedSets[_recordedSets.lastIndex] = set
            finishPending = false
        } else {
            _recordedSets += set
        }
        if (phase.currentSet < phase.totalSets) {
            setSession(
                current.copy(
                    phase = WorkoutExecutionState.Rest(
                        exercise = phase.exercise,
                        currentSet = phase.currentSet + 1,
                        totalSets = phase.totalSets,
                        restTimeMillis = phase.exercise.timeMillis,
                        totalRestTimeMillis = phase.exercise.timeMillis
                    ),
                    restOver = false
                )
            )
            startRestTimer()
            scheduleIdleReminderIfActive()
        } else {
            moveToNextExercise()
        }
    }

    private fun moveToNextExercise() {
        val current = present ?: return
        if (current.exerciseIndex < current.exercises.lastIndex) {
            val nextIndex = current.exerciseIndex + 1
            val next = current.exercises[nextIndex].exercise
            setSession(
                current.copy(
                    exerciseIndex = nextIndex,
                    phase = WorkoutExecutionState.Rest(
                        exercise = next,
                        currentSet = 1,
                        totalSets = next.sets,
                        restTimeMillis = next.timeMillis,
                        totalRestTimeMillis = next.timeMillis
                    ),
                    restOver = false
                )
            )
            startRestTimer()
            scheduleIdleReminderIfActive()
        } else {
            finishSession(current)
        }
    }

    private fun finishSession(current: WorkoutSession.Present) {
        val sessionId = current.sessionId
        val workoutId = current.workoutId
        // isFinishing поднимается синхронно, до записи: второй тап его уже видит.
        setSession(current.copy(isFinishing = true, restOver = false))
        finishPending = true
        val startedAt = sessionStartedAt
        val sets = _recordedSets.toList()
        scope.launch {
            val finishedAt = System.currentTimeMillis()
            val durationMillis = (finishedAt - startedAt - excludedIdleMillis).coerceAtLeast(0L)
            try {
                finishWorkoutSessionUseCase(
                    workoutId = workoutId,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    durationMillis = durationMillis,
                    sets = sets
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Подходы остаются в памяти, экран — на последнем подходе:
                // повторное «Закончить подход» попробует записать ещё раз.
                Log.e(TAG, "Не удалось записать завершённую тренировку", e)
                // Сессию за это время закрыли или начали новую — её состояние не трогаем.
                if (present?.sessionId != sessionId) return@launch
                updatePresent { it.copy(isFinishing = false) }
                _finishError.value = true
                // Экран остался Active — заново взводим напоминание о простое.
                scheduleIdleReminderIfActive()
                return@launch
            }
            if (present?.sessionId == sessionId) {
                finishPending = false
                // Finished — раньше updateLastUseAt(): тот дёргает перерисовку виджета (биндер,
                // DataStore, композиция Glance), и экран ждал бы её. Порядок «сессия раньше
                // updateLastUseAt()» сохраняется: виджет берёт длительность из последней сессии.
                updatePresent {
                    it.copy(phase = WorkoutExecutionState.Finished(durationMillis), isFinishing = false)
                }
                scheduleIdleReminderIfActive()
            }
            workoutRepository.updateLastUseAt(workoutId)
        }
    }

    /** Переход по шторке выбора упражнения: с подхода 1, без отдыха. */
    fun moveToExercise(index: Int) {
        val current = present ?: return
        val target = current.exercises.getOrNull(index) ?: return
        registerInteraction()
        // Ушли с последнего подхода — следующий закрытый подход записывается заново.
        finishPending = false
        timerJob?.cancel()
        wakeLockHelper.release()
        stopNotification()
        setSession(
            current.copy(
                exerciseIndex = index,
                phase = WorkoutExecutionState.Active(
                    exercise = target.exercise,
                    currentSet = 1,
                    totalSets = target.exercise.sets
                ),
                restOver = false
            )
        )
        scheduleIdleReminderIfActive()
    }

    /**
     * «+ в сегодняшнюю»: копия упражнения другой тренировки в конец сессии, только на сегодня.
     * Тренировка в БД не меняется. Одна копия на исходную строку (exercise.id сохраняется в
     * копии) — от двойного тапа. Текущие индекс и фаза не трогаются; это действие сессии.
     */
    fun addExerciseToday(exercise: Exercise): AddResult {
        val current = present ?: return AddResult.NO_SESSION
        if (current.isFinishing || !current.phase.isInProgress) return AddResult.NO_SESSION
        if (current.exercises.any { it.addedToday && it.exercise.id == exercise.id }) {
            return AddResult.ALREADY_ADDED
        }
        registerInteraction()
        setSession(current.copy(exercises = current.exercises + SessionExercise(exercise.copy(), addedToday = true)))
        return AddResult.ADDED
    }

    /**
     * Заметка: для строки шаблона — сначала БД, потом состояние, как было; у добавленного
     * упражнения exercise.id — строка чужой тренировки, его правка живёт только в сессии.
     */
    fun updateExerciseNote(index: Int, note: String) {
        registerInteraction()
        val row = present?.exercises?.getOrNull(index) ?: return
        if (row.addedToday) {
            applyNote(index, note)
            return
        }
        // Индекс, взятый до записи, остаётся верным: список только дописывается,
        // а смена сессии отменяет эту корутину вместе с sessionScope.
        sessionScope.launch {
            workoutRepository.updateExerciseNote(row.exercise.id, note)
            applyNote(index, note)
        }
    }

    private fun applyNote(index: Int, note: String) {
        updatePresent { current ->
            val row = current.exercises.getOrNull(index) ?: return@updatePresent current
            val updated = row.exercise.copy(note = note)
            val exercises = current.exercises.toMutableList().apply { set(index, row.copy(exercise = updated)) }
            val phase = current.phase
            val newPhase = when {
                index != current.exerciseIndex -> phase
                phase is WorkoutExecutionState.Active -> phase.copy(exercise = updated)
                phase is WorkoutExecutionState.Rest -> phase.copy(exercise = updated)
                else -> phase
            }
            current.copy(exercises = exercises, phase = newPhase)
        }
    }

    fun updateExerciseWeightAndReps(index: Int, weight: Double, extraWeight: Double, reps: Int) {
        registerInteraction()
        val current = present ?: return
        val row = current.exercises.getOrNull(index) ?: return
        val updated = row.exercise.copy(weight = weight, extraWeight = extraWeight, reps = reps)
        val exercises = current.exercises.toMutableList().apply { set(index, row.copy(exercise = updated)) }

        // Сначала состояние, потом база: «Закончить подход» сразу после правки
        // должен записать новые значения, а не ждать окончания записи.
        // Плитки в Active берут числа из weight/extraWeight/reps фазы, а не из
        // exercise, поэтому одного обновления упражнения им мало. Во время отдыха
        // текущее упражнение — упражнение следующего подхода.
        val phase = current.phase
        val newPhase = when {
            index != current.exerciseIndex -> phase
            phase is WorkoutExecutionState.Active ->
                phase.copy(exercise = updated, weight = weight, extraWeight = extraWeight, reps = reps)
            phase is WorkoutExecutionState.Rest -> phase.copy(exercise = updated)
            else -> phase
        }
        setSession(current.copy(exercises = exercises, phase = newPhase))
        // Правка добавленного упражнения не трогает шаблон чужой тренировки.
        if (!row.addedToday) {
            sessionScope.launch {
                workoutRepository.updateExerciseWeightAndReps(row.exercise.id, weight, extraWeight, reps)
            }
        }
    }

    /** Действие сессии для учёта простоя. Без аргумента — «сейчас»; [now] задают тесты. */
    fun registerInteraction(now: Long = System.currentTimeMillis()) {
        if (lastInteractionAt != 0L) {
            val gap = now - lastInteractionAt
            if (gap > IDLE_EXCLUSION_THRESHOLD_MILLIS) {
                excludedIdleMillis += gap - IDLE_EXCLUSION_THRESHOLD_MILLIS
            }
        }
        lastInteractionAt = now
        // Пользователь уже здесь — звать его к тренировке незачем.
        workoutAlerts.dismiss()
    }

    private fun scheduleIdleReminderIfActive() {
        idleReminderJob?.cancel()
        if (present?.phase is WorkoutExecutionState.Active) {
            idleReminderJob = sessionScope.launch {
                delay(IDLE_REMINDER_DELAY_MILLIS)
                showIdleReminderNotification()
            }
        }
    }

    private fun setSession(value: WorkoutSession) {
        _session.value = value
        _runningWorkout.value = runningWorkoutOf(value)
    }

    private inline fun updatePresent(transform: (WorkoutSession.Present) -> WorkoutSession.Present) {
        val current = present ?: return
        setSession(transform(current))
    }

    private fun newSessionScope(): CoroutineScope =
        CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job))

    private fun startNotification(exerciseName: String, currentSet: Int, totalSets: Int, timeLeftMillis: Long) {
        val intent = Intent(context, TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_START
            putExtra(TimerNotificationService.EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(TimerNotificationService.EXTRA_CURRENT_SET, currentSet)
            putExtra(TimerNotificationService.EXTRA_TOTAL_SETS, totalSets)
            putExtra(TimerNotificationService.EXTRA_TIME_LEFT, timeLeftMillis)
        }
        context.startService(intent)
    }

    private fun updateNotification(exerciseName: String, currentSet: Int, totalSets: Int, timeLeftMillis: Long) {
        val intent = Intent(context, TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_UPDATE
            putExtra(TimerNotificationService.EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(TimerNotificationService.EXTRA_CURRENT_SET, currentSet)
            putExtra(TimerNotificationService.EXTRA_TOTAL_SETS, totalSets)
            putExtra(TimerNotificationService.EXTRA_TIME_LEFT, timeLeftMillis)
        }
        context.startService(intent)
    }

    private fun stopNotification() {
        val intent = Intent(context, TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun showRestFinishedNotification(exerciseName: String, currentSet: Int, totalSets: Int) {
        val intent = Intent(context, TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_SHOW_FINISHED
            putExtra(TimerNotificationService.EXTRA_EXERCISE_NAME, exerciseName)
            putExtra(TimerNotificationService.EXTRA_CURRENT_SET, currentSet)
            putExtra(TimerNotificationService.EXTRA_TOTAL_SETS, totalSets)
        }
        context.startService(intent)
    }

    private fun showIdleReminderNotification() {
        val phase = present?.phase as? WorkoutExecutionState.Active ?: return
        val intent = Intent(context, TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_SHOW_IDLE_REMINDER
            putExtra(TimerNotificationService.EXTRA_EXERCISE_NAME, phase.exercise.name)
            putExtra(TimerNotificationService.EXTRA_CURRENT_SET, phase.currentSet)
            putExtra(TimerNotificationService.EXTRA_TOTAL_SETS, phase.totalSets)
        }
        try {
            context.startService(intent)
        } catch (e: IllegalStateException) {
            // Best-effort: приложение может быть целиком в фоне без сервиса переднего плана
            // (ровно тот случай, ради которого напоминание есть), и тогда Android (API 26+)
            // запрещает запуск сервиса и бросает IllegalStateException (на API 31+ — его
            // подкласс ForegroundServiceStartNotAllowedException). Проглатываем.
        }
    }

    companion object {
        private const val TAG = "WorkoutSessionManager"
        internal const val IDLE_EXCLUSION_THRESHOLD_MILLIS = 10 * 60 * 1000L
        internal const val IDLE_REMINDER_DELAY_MILLIS = 5 * 60 * 1000L
    }
}
