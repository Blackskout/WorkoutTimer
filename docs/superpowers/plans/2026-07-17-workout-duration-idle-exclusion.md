# Исключение простоя из длительности тренировки — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Перестать считать долгий простой (забытую кнопку «Закончить упражнение») частью сохранённой длительности тренировки, и мягко напоминать пользователю, если он завис в активном подходе дольше 5 минут.

**Architecture:** Всё изменение живёт в `WorkoutExecutionViewModel` плюс сквозной проброс явного `durationMillis` через `AddWorkoutSessionUseCase`/`WorkoutRepository`/`WorkoutRepositoryImpl` (схема БД не меняется). Логика простоя — чистая арифметика по меткам времени (`registerInteraction`), не завязанная на порядок/идентичность упражнений. Уведомление-напоминание переиспользует существующий `TimerNotificationService` (новый `ACTION`), таймер напоминания — обычная `delay()`-корутина в `viewModelScope`, по образцу уже существующего таймера отдыха.

**Tech Stack:** Kotlin, Android ViewModel + Hilt, kotlinx.coroutines, Room, JUnit + MockK + kotlinx-coroutines-test (`UnconfinedTestDispatcher`).

## Global Constraints

- Порог исключения простоя между двумя реальными взаимодействиями: **10 минут** — сверх него время не засчитывается в `durationMillis` (спека, часть A).
- Порог напоминания о простое в состоянии `Active`: **5 минут** (спека, часть B).
- Исключение простоя — молчаливое, без диалога/UI для просмотра или редактирования (спека, «Вне рамок»).
- Никакой логики, завязанной на порядок или идентичность конкретных упражнений/подходов — только метки времени (спека, «Вне рамок»).
- Никаких изменений схемы `WorkoutSessionEntity`/миграций БД — `durationMillis` уже независимое поле.
- Никакого нового Clock/DI-абстрагирования времени — используем `System.currentTimeMillis()` напрямую, единственный тестовый шов — `internal fun registerInteraction(now: Long = System.currentTimeMillis())` с `internal` полями `lastInteractionAt`/`excludedIdleMillis` (`private set`), видимыми из `app/src/test` в том же Gradle-модуле.
- Восстановление сессии после полного уничтожения процесса Android — не в рамках.

---

## Task 1: Арифметика исключения простоя (`registerInteraction`)

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`

**Interfaces:**
- Produces: `internal var lastInteractionAt: Long` (readonly извне, `private set`), `internal var excludedIdleMillis: Long` (readonly извне, `private set`), `internal fun registerInteraction(now: Long = System.currentTimeMillis())`. Эти три имени используются в Task 2 и Task 3.

- [ ] **Step 1: Написать падающие тесты арифметики порога**

Добавить в конец класса `WorkoutExecutionViewModelTest` (перед закрывающей `}` файла):

```kotlin
    @Test
    fun `a gap under the threshold excludes nothing`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        viewModel.registerInteraction(now = 0L)
        viewModel.registerInteraction(now = 5 * 60 * 1000L) // 5 минут, меньше порога в 10

        assertEquals(0L, viewModel.excludedIdleMillis)
    }

    @Test
    fun `a gap over the threshold excludes only the excess`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        viewModel.registerInteraction(now = 0L)
        viewModel.registerInteraction(now = 45 * 60 * 1000L) // 45 минут простоя

        assertEquals(35 * 60 * 1000L, viewModel.excludedIdleMillis) // исключены только 45 - 10 = 35 минут
    }

    @Test
    fun `multiple gaps over the threshold accumulate`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        viewModel.registerInteraction(now = 0L)
        viewModel.registerInteraction(now = 20 * 60 * 1000L) // гэп 20 мин -> исключено 10 мин
        viewModel.registerInteraction(now = 20 * 60 * 1000L + 30 * 60 * 1000L) // ещё гэп 30 мин -> исключено ещё 20 мин

        assertEquals(30 * 60 * 1000L, viewModel.excludedIdleMillis) // 10 + 20 = 30 минут суммарно
    }

    @Test
    fun `loadWorkout resets excludedIdleMillis for a fresh session`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.registerInteraction(now = 0L)
        viewModel.registerInteraction(now = 45 * 60 * 1000L)
        assertEquals(35 * 60 * 1000L, viewModel.excludedIdleMillis)

        viewModel.loadWorkout(1) // повторная загрузка = новая сессия

        assertEquals(0L, viewModel.excludedIdleMillis)
    }
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: FAIL to compile — `Unresolved reference: registerInteraction`, `Unresolved reference: excludedIdleMillis` (эти члены ещё не существуют в `WorkoutExecutionViewModel`).

- [ ] **Step 3: Реализовать `registerInteraction` и связанные поля**

В `WorkoutExecutionViewModel.kt`, заменить (строки 42-45):

```kotlin
    private var workout: Workout? = null
    var exercises: List<Exercise> = emptyList()
    private var exerciseIndex = 0
    private var sessionStartedAt: Long = 0L
```

на:

```kotlin
    private var workout: Workout? = null
    var exercises: List<Exercise> = emptyList()
    private var exerciseIndex = 0
    private var sessionStartedAt: Long = 0L

    internal var lastInteractionAt: Long = 0L
        private set
    internal var excludedIdleMillis: Long = 0L
        private set
```

В `loadWorkout()`, заменить (строки 114-126):

```kotlin
            if (loadedWorkout != null && loadedWorkout.exercises.isNotEmpty()) {
                workout = loadedWorkout
                exercises = loadedWorkout.exercises.sortedBy { it.order }
                exerciseIndex = 0
                sessionStartedAt = System.currentTimeMillis()

                // Начинаем с первого упражнения в состоянии Rest
                val firstExercise = exercises[0]
                _uiState.value = WorkoutExecutionState.Active(
                    exercise = firstExercise,
                    currentSet = 1,
                    totalSets = firstExercise.sets,
                )
            } else {
```

на:

```kotlin
            if (loadedWorkout != null && loadedWorkout.exercises.isNotEmpty()) {
                workout = loadedWorkout
                exercises = loadedWorkout.exercises.sortedBy { it.order }
                exerciseIndex = 0
                sessionStartedAt = System.currentTimeMillis()
                lastInteractionAt = sessionStartedAt
                excludedIdleMillis = 0L

                // Начинаем с первого упражнения в состоянии Rest
                val firstExercise = exercises[0]
                _uiState.value = WorkoutExecutionState.Active(
                    exercise = firstExercise,
                    currentSet = 1,
                    totalSets = firstExercise.sets,
                )
            } else {
```

В конце класса `WorkoutExecutionViewModel`, заменить хвост (строки 356-362 — закрытие `updateExerciseNote()`, две пустые строки и закрывающая `}` класса):

```kotlin
            }
        }
    }



}
```

на:

```kotlin
            }
        }
    }

    internal fun registerInteraction(now: Long = System.currentTimeMillis()) {
        if (lastInteractionAt != 0L) {
            val gap = now - lastInteractionAt
            if (gap > IDLE_EXCLUSION_THRESHOLD_MILLIS) {
                excludedIdleMillis += gap - IDLE_EXCLUSION_THRESHOLD_MILLIS
            }
        }
        lastInteractionAt = now
    }

    companion object {
        internal const val IDLE_EXCLUSION_THRESHOLD_MILLIS = 10 * 60 * 1000L
        internal const val IDLE_REMINDER_DELAY_MILLIS = 5 * 60 * 1000L
    }
}
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: PASS (все тесты, включая уже существовавшие два)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt
git commit -m "feat: add idle-gap threshold arithmetic to WorkoutExecutionViewModel"
```

---

## Task 2: Подключить `registerInteraction` к реальным действиям пользователя

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`

**Interfaces:**
- Consumes: `internal fun registerInteraction(now: Long)`, `internal var lastInteractionAt: Long` из Task 1.
- Produces: `onExerciseFinished()`, `skipRest()`, `moveToSelectedExercise()`, `updateExerciseNote()` теперь вызывают `registerInteraction()`. `onRestFinished()` становится `internal` (было `private`), но **не** вызывает `registerInteraction()` — это используется в Task 5.

- [ ] **Step 1: Написать падающие тесты подключения**

Добавить в конец `WorkoutExecutionViewModelTest`:

```kotlin
    @Test
    fun `onExerciseFinished registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.onExerciseFinished()

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `skipRest registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.skipRest()

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `moveToSelectedExercise registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.moveToSelectedExercise(exercise)

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `updateExerciseNote registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        coEvery { workoutRepository.updateExerciseNote(1, "note") } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        viewModel.registerInteraction(now = farFuture)

        viewModel.updateExerciseNote(1, "note")

        assertTrue(viewModel.lastInteractionAt < farFuture)
    }

    @Test
    fun `automatic rest completion does not register an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest, регистрирует взаимодействие
        val afterRealInteraction = viewModel.lastInteractionAt

        viewModel.onRestFinished()

        assertEquals(afterRealInteraction, viewModel.lastInteractionAt)
    }
```

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: FAIL to compile — `Cannot access 'onRestFinished': it is private in 'WorkoutExecutionViewModel'`. После временного исправления видимости — падение остальных assert'ов (`lastInteractionAt` не сброшен, так как методы ещё не вызывают `registerInteraction()`).

- [ ] **Step 3: Подключить `registerInteraction()` к точкам входа**

В `WorkoutExecutionViewModel.kt`:

Изменить `onExerciseFinished()` (строка 260):

```kotlin
    fun onExerciseFinished() {
        val currentState = _uiState.value
```

на:

```kotlin
    fun onExerciseFinished() {
        registerInteraction()
        val currentState = _uiState.value
```

Изменить `skipRest()` (строка 135):

```kotlin
    fun skipRest() {
        timerJob?.cancel()
```

на:

```kotlin
    fun skipRest() {
        registerInteraction()
        timerJob?.cancel()
```

Изменить `moveToSelectedExercise()` (строки 307-311):

```kotlin
    fun moveToSelectedExercise(exercise: Exercise) {
        val index = exercises.indexOfFirst { it.id == exercise.id }

        if (index != -1) {
            timerJob?.cancel()
```

на:

```kotlin
    fun moveToSelectedExercise(exercise: Exercise) {
        val index = exercises.indexOfFirst { it.id == exercise.id }

        if (index != -1) {
            registerInteraction()
            timerJob?.cancel()
```

Изменить `updateExerciseNote()` (строки 325-327):

```kotlin
    fun updateExerciseNote(exerciseId: Int, note: String) {
        viewModelScope.launch {
            workoutRepository.updateExerciseNote(exerciseId, note)
```

на:

```kotlin
    fun updateExerciseNote(exerciseId: Int, note: String) {
        registerInteraction()
        viewModelScope.launch {
            workoutRepository.updateExerciseNote(exerciseId, note)
```

Изменить видимость `onRestFinished()` (строка 222), не добавляя вызов `registerInteraction()`:

```kotlin
    private fun onRestFinished() {
```

на:

```kotlin
    internal fun onRestFinished() {
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: PASS (все тесты)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt
git commit -m "feat: register a user interaction on every real workout-execution action"
```

---

## Task 3: Применить исключённый простой к сохраняемой длительности

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/AddWorkoutSessionUseCase.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`

**Interfaces:**
- Consumes: `internal var excludedIdleMillis: Long` из Task 1.
- Produces: `AddWorkoutSessionUseCase.invoke(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long): Unit` и `WorkoutRepository.addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long): Unit` — сигнатуры меняются с `Long`-возврата на явный `durationMillis`-параметр и `Unit`-возврат. Использует их только `WorkoutExecutionViewModel.moveToNextExercise()`.

- [ ] **Step 1: Переписать тест репозитория под новую сигнатуру (падающий тест)**

В `WorkoutRepositoryImplTest.kt` заменить тест (строки 19-33):

```kotlin
    @Test
    fun `addWorkoutSession computes duration and inserts entity with converted workoutId`() = runTest {
        val dao = mockk<WorkoutDao>()
        val entitySlot = slot<WorkoutSessionEntity>()
        coEvery { dao.insertSession(capture(entitySlot)) } just io.mockk.Runs
        val repo = WorkoutRepositoryImpl(dao)

        val duration = repo.addWorkoutSession(workoutId = 7, startedAt = 1_000L, finishedAt = 6_500L)

        assertEquals(5_500L, duration)
        assertEquals(7L, entitySlot.captured.workoutId)
        assertEquals(1_000L, entitySlot.captured.startedAt)
        assertEquals(6_500L, entitySlot.captured.finishedAt)
        assertEquals(5_500L, entitySlot.captured.durationMillis)
    }
```

на:

```kotlin
    @Test
    fun `addWorkoutSession stores the provided duration and inserts entity with converted workoutId`() = runTest {
        val dao = mockk<WorkoutDao>()
        val entitySlot = slot<WorkoutSessionEntity>()
        coEvery { dao.insertSession(capture(entitySlot)) } just io.mockk.Runs
        val repo = WorkoutRepositoryImpl(dao)

        repo.addWorkoutSession(workoutId = 7, startedAt = 1_000L, finishedAt = 6_500L, durationMillis = 4_000L)

        assertEquals(7L, entitySlot.captured.workoutId)
        assertEquals(1_000L, entitySlot.captured.startedAt)
        assertEquals(6_500L, entitySlot.captured.finishedAt)
        assertEquals(4_000L, entitySlot.captured.durationMillis)
    }
```

(`durationMillis = 4_000L` умышленно отличается от `finishedAt - startedAt = 5_500L` — тест доказывает, что репозиторий больше не пересчитывает длительность сам, а хранит переданное значение.)

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.WorkoutRepositoryImplTest"`
Expected: FAIL to compile — `No value passed for parameter 'durationMillis'` / `addWorkoutSession` пока возвращает `Long`, а не принимает четвёртый параметр.

- [ ] **Step 3: Изменить сигнатуры use case, интерфейса и реализации репозитория**

В `AddWorkoutSessionUseCase.kt` заменить:

```kotlin
class AddWorkoutSessionUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int, startedAt: Long, finishedAt: Long): Long {
        return repo.addWorkoutSession(workoutId, startedAt, finishedAt)
    }
}
```

на:

```kotlin
class AddWorkoutSessionUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long) {
        repo.addWorkoutSession(workoutId, startedAt, finishedAt, durationMillis)
    }
}
```

В `WorkoutRepository.kt` заменить строку 19:

```kotlin
        suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long): Long
```

на:

```kotlin
        suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long)
```

В `WorkoutRepositoryImpl.kt` заменить (строки 105-115):

```kotlin
    override suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long): Long {
        val durationMillis = finishedAt - startedAt
        dao.insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId.toLong(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMillis = durationMillis
            )
        )
        return durationMillis
    }
```

на:

```kotlin
    override suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long) {
        dao.insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId.toLong(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMillis = durationMillis
            )
        )
    }
```

- [ ] **Step 4: Обновить `moveToNextExercise()` — вычислять скорректированную длительность**

В `WorkoutExecutionViewModel.kt` заменить (строки 292-304):

```kotlin
        } else {
            val workoutId = workout?.id ?: return
            viewModelScope.launch {
                workoutRepository.updateLastUseAt(workoutId)
                val finishedAt = System.currentTimeMillis()
                val durationMillis = addWorkoutSessionUseCase(
                    workoutId = workoutId,
                    startedAt = sessionStartedAt,
                    finishedAt = finishedAt
                )
                _uiState.value = WorkoutExecutionState.Finished(durationMillis = durationMillis)
            }
        }
```

на:

```kotlin
        } else {
            val workoutId = workout?.id ?: return
            viewModelScope.launch {
                workoutRepository.updateLastUseAt(workoutId)
                val finishedAt = System.currentTimeMillis()
                val rawDurationMillis = finishedAt - sessionStartedAt
                val durationMillis = (rawDurationMillis - excludedIdleMillis).coerceAtLeast(0L)
                addWorkoutSessionUseCase(
                    workoutId = workoutId,
                    startedAt = sessionStartedAt,
                    finishedAt = finishedAt,
                    durationMillis = durationMillis
                )
                _uiState.value = WorkoutExecutionState.Finished(durationMillis = durationMillis)
            }
        }
```

- [ ] **Step 5: Обновить существующие тесты ViewModel под новую сигнатуру use case**

В `WorkoutExecutionViewModelTest.kt` добавить в импорты (после `import io.mockk.mockk`):

```kotlin
import io.mockk.Runs
import io.mockk.just
import io.mockk.slot
```

Заменить тест `finishing the only exercise saves a session and exposes its duration` (строки 58-81):

```kotlin
    @Test
    fun `finishing the only exercise saves a session and exposes its duration`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()
        coEvery {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any())
        } returns 1_234L

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished()

        val state = viewModel.uiState.value
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(1_234L, (state as WorkoutExecutionState.Finished).durationMillis)
        coVerify(exactly = 1) {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any())
        }
    }
```

на:

```kotlin
    @Test
    fun `finishing the only exercise saves a session and exposes its duration`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()
        val durationSlot = slot<Long>()
        coEvery {
            addWorkoutSessionUseCase(
                workoutId = 1,
                startedAt = any(),
                finishedAt = any(),
                durationMillis = capture(durationSlot)
            )
        } just Runs

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished()

        val state = viewModel.uiState.value
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(durationSlot.captured, (state as WorkoutExecutionState.Finished).durationMillis)
        coVerify(exactly = 1) {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any())
        }
    }
```

Заменить в тесте `loading a workout does not save a session by itself` (строки 95-97):

```kotlin
        coVerify(exactly = 0) {
            addWorkoutSessionUseCase(workoutId = any(), startedAt = any(), finishedAt = any())
        }
```

на:

```kotlin
        coVerify(exactly = 0) {
            addWorkoutSessionUseCase(workoutId = any(), startedAt = any(), finishedAt = any(), durationMillis = any())
        }
```

- [ ] **Step 6: Запустить все тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.WorkoutRepositoryImplTest" --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: PASS (все тесты в обоих классах)

- [ ] **Step 7: Проверить, что весь модуль компилируется (другие вызывающие стороны use case отсутствуют)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (единственная вызывающая сторона `AddWorkoutSessionUseCase` — `WorkoutExecutionViewModel`, уже обновлена в Step 4)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/usecase/AddWorkoutSessionUseCase.kt app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt
git commit -m "feat: subtract excluded idle time from saved session duration"
```

---

## Task 4: Уведомление-напоминание в `TimerNotificationService`

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/service/TimerNotificationService.kt`

**Interfaces:**
- Produces: `TimerNotificationService.ACTION_SHOW_IDLE_REMINDER` (константа) — новый `Intent.action`, обрабатываемый в `onStartCommand`, принимает те же extras, что и `ACTION_SHOW_FINISHED` (`EXTRA_EXERCISE_NAME`, `EXTRA_CURRENT_SET`, `EXTRA_TOTAL_SETS`). Используется в Task 5.

Этот класс — `android.app.Service`, в проекте нет Robolectric/инструментальных тестов для сервисов (только `androidTestImplementation` для Compose UI-тестов), а `TimerNotificationService` не покрыт тестами и сейчас. Проверка — вручную (см. Task 6).

- [ ] **Step 1: Добавить константу действия**

В `TimerNotificationService.kt`, в `companion object` (строки 176-186), заменить:

```kotlin
    companion object {
        const val ACTION_START = "START"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_STOP = "STOP"
        const val ACTION_SHOW_FINISHED = "SHOW_FINISHED"

        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_CURRENT_SET = "current_set"
        const val EXTRA_TOTAL_SETS = "total_sets"
        const val EXTRA_TIME_LEFT = "time_left"
    }
```

на:

```kotlin
    companion object {
        const val ACTION_START = "START"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_STOP = "STOP"
        const val ACTION_SHOW_FINISHED = "SHOW_FINISHED"
        const val ACTION_SHOW_IDLE_REMINDER = "SHOW_IDLE_REMINDER"

        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_CURRENT_SET = "current_set"
        const val EXTRA_TOTAL_SETS = "total_sets"
        const val EXTRA_TIME_LEFT = "time_left"
    }
```

- [ ] **Step 2: Добавить канал и уведомление для напоминания**

В `TimerNotificationService.kt`, добавить новые поля рядом с существующими (строки 17-21):

```kotlin
    private val channelId = "timer_notification_channel"
    private val notificationId = 1

    private val finishedChannelId = "rest_finished_notification_channel"
    private val finishedNotificationId = 2
```

заменить на:

```kotlin
    private val channelId = "timer_notification_channel"
    private val notificationId = 1

    private val finishedChannelId = "rest_finished_notification_channel"
    private val finishedNotificationId = 2

    private val idleReminderChannelId = "idle_reminder_notification_channel"
    private val idleReminderNotificationId = 3
```

В `onCreate()` (строки 23-27), заменить:

```kotlin
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createFinishedNotificationChannel()
    }
```

на:

```kotlin
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createFinishedNotificationChannel()
        createIdleReminderNotificationChannel()
    }
```

В `onStartCommand()` (строки 29-57), добавить новую ветку `when` перед закрывающей `}`:

```kotlin
            ACTION_SHOW_FINISHED -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Упражнение"
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                notificationManager.notify(finishedNotificationId, createFinishedNotification(exerciseName, currentSet, totalSets))
            }
        }
        return START_NOT_STICKY
```

на:

```kotlin
            ACTION_SHOW_FINISHED -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Упражнение"
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                notificationManager.notify(finishedNotificationId, createFinishedNotification(exerciseName, currentSet, totalSets))
            }
            ACTION_SHOW_IDLE_REMINDER -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Упражнение"
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                notificationManager.notify(idleReminderNotificationId, createIdleReminderNotification(exerciseName, currentSet, totalSets))
            }
        }
        return START_NOT_STICKY
```

Добавить новые методы `createIdleReminderNotificationChannel()` и `createIdleReminderNotification()` сразу после `createFinishedNotification()` (после строки 162, перед `private fun formatTime`):

```kotlin
    private fun createIdleReminderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                idleReminderChannelId,
                "Напоминание о тренировке",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Напоминание вернуться в приложение, если подход давно не завершён"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createIdleReminderNotification(
        exerciseName: String,
        currentSet: Int,
        totalSets: Int
    ): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Вы всё ещё тренируетесь?"
        val content = "$exerciseName — Подход $currentSet из $totalSets. Не забудьте закончить подход."

        return NotificationCompat.Builder(this, idleReminderChannelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setShowWhen(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
```

- [ ] **Step 3: Проверить компиляцию**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/service/TimerNotificationService.kt
git commit -m "feat: add idle-reminder notification action to TimerNotificationService"
```

---

## Task 5: Планирование напоминания при входе в `Active`

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerNotificationService.ACTION_SHOW_IDLE_REMINDER` из Task 4; `IDLE_REMINDER_DELAY_MILLIS` из Task 1.
- Produces: `internal val isIdleReminderJobActive: Boolean` — для проверки в тестах, что напоминание запущено/отменено в нужный момент.

- [ ] **Step 1: Написать падающие тесты планирования**

Добавить в конец `WorkoutExecutionViewModelTest`:

```kotlin
    @Test
    fun `loading a workout schedules the idle reminder for the first exercise`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        assertTrue(viewModel.isIdleReminderJobActive)
    }

    @Test
    fun `moving to rest cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest

        assertFalse(viewModel.isIdleReminderJobActive)
    }

    @Test
    fun `skipping rest re-schedules the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // -> Rest
        viewModel.skipRest() // -> Active

        assertTrue(viewModel.isIdleReminderJobActive)
    }

    @Test
    fun `finishing the workout cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()
        coEvery {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any())
        } just Runs

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished() // единственное упражнение, единственный подход -> Finished

        assertFalse(viewModel.isIdleReminderJobActive)
    }
```

Добавить импорт `org.junit.Assert.assertFalse` в `WorkoutExecutionViewModelTest.kt` (рядом с существующим `import org.junit.Assert.assertTrue`).

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: FAIL to compile — `Unresolved reference: isIdleReminderJobActive`.

- [ ] **Step 3: Реализовать планирование напоминания**

В `WorkoutExecutionViewModel.kt`, добавить новое поле рядом с `timerJob` (строка 61):

```kotlin
    private var timerJob: Job? = null
```

заменить на:

```kotlin
    private var timerJob: Job? = null
    private var idleReminderJob: Job? = null

    internal val isIdleReminderJobActive: Boolean
        get() = idleReminderJob?.isActive == true
```

Добавить методы планирования и показа напоминания между `registerInteraction()` и `companion object` (оба добавлены в Task 1) — заменить:

```kotlin
        lastInteractionAt = now
    }

    companion object {
```

на:

```kotlin
        lastInteractionAt = now
    }

    private fun scheduleIdleReminderIfActive() {
        idleReminderJob?.cancel()
        if (_uiState.value is WorkoutExecutionState.Active) {
            idleReminderJob = viewModelScope.launch {
                delay(IDLE_REMINDER_DELAY_MILLIS)
                showIdleReminderNotification()
            }
        }
    }

    private fun showIdleReminderNotification() {
        val state = _uiState.value as? WorkoutExecutionState.Active ?: return
        val intent = Intent(context, TimerNotificationService::class.java).apply {
            action = TimerNotificationService.ACTION_SHOW_IDLE_REMINDER
            putExtra(TimerNotificationService.EXTRA_EXERCISE_NAME, state.exercise.name)
            putExtra(TimerNotificationService.EXTRA_CURRENT_SET, state.currentSet)
            putExtra(TimerNotificationService.EXTRA_TOTAL_SETS, state.totalSets)
        }
        context.startService(intent)
    }

    companion object {
```

В `loadWorkout()`, вызвать планирование сразу после установки первого `Active`-состояния — заменить (в редакции после Task 1):

```kotlin
                // Начинаем с первого упражнения в состоянии Rest
                val firstExercise = exercises[0]
                _uiState.value = WorkoutExecutionState.Active(
                    exercise = firstExercise,
                    currentSet = 1,
                    totalSets = firstExercise.sets,
                )
            } else {
```

на:

```kotlin
                // Начинаем с первого упражнения в состоянии Rest
                val firstExercise = exercises[0]
                _uiState.value = WorkoutExecutionState.Active(
                    exercise = firstExercise,
                    currentSet = 1,
                    totalSets = firstExercise.sets,
                )
                scheduleIdleReminderIfActive()
            } else {
```

В `skipRest()`, заменить (в редакции после Task 2):

```kotlin
    fun skipRest() {
        registerInteraction()
        timerJob?.cancel()
        wakeLockHelper.release()
        stopNotification()
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Rest -> {
                    WorkoutExecutionState.Active(
                        exercise = state.exercise,
                        currentSet = state.currentSet,
                        totalSets = state.totalSets,
                        weight = state.exercise.weight,
                        reps = state.exercise.reps
                    )
                }

                else -> state
            }
        }
    }
```

на:

```kotlin
    fun skipRest() {
        registerInteraction()
        timerJob?.cancel()
        wakeLockHelper.release()
        stopNotification()
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Rest -> {
                    WorkoutExecutionState.Active(
                        exercise = state.exercise,
                        currentSet = state.currentSet,
                        totalSets = state.totalSets,
                        weight = state.exercise.weight,
                        reps = state.exercise.reps
                    )
                }

                else -> state
            }
        }
        scheduleIdleReminderIfActive()
    }
```

В `onRestFinished()`, заменить (строки 239-251, видимость уже `internal` после Task 2):

```kotlin
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Rest -> {
                    WorkoutExecutionState.Active(
                        exercise = state.exercise,
                        currentSet = state.currentSet,
                        totalSets = state.totalSets
                    )
                }
                else -> state
            }
        }
    }
```

на:

```kotlin
        _uiState.update { state ->
            when (state) {
                is WorkoutExecutionState.Rest -> {
                    WorkoutExecutionState.Active(
                        exercise = state.exercise,
                        currentSet = state.currentSet,
                        totalSets = state.totalSets
                    )
                }
                else -> state
            }
        }
        scheduleIdleReminderIfActive()
    }
```

В `onExerciseFinished()`, заменить (в редакции после Task 2, строки внутри веток if/else):

```kotlin
    fun onExerciseFinished() {
        registerInteraction()
        val currentState = _uiState.value
        if (currentState is WorkoutExecutionState.Active) {
            if (currentState.currentSet < currentState.totalSets) {
                // Переход к следующему подходу того же упражнения
                _uiState.value = WorkoutExecutionState.Rest(
                    exercise = currentState.exercise,
                    currentSet = currentState.currentSet + 1,
                    totalSets = currentState.totalSets,
                    restTimeMillis = currentState.exercise.timeMillis,
                    totalRestTimeMillis = currentState.exercise.timeMillis
                )
                startRestTimer()
            } else {
                // Упражнение завершено, переходим к следующему
                moveToNextExercise()
            }
        }
    }
```

на:

```kotlin
    fun onExerciseFinished() {
        registerInteraction()
        val currentState = _uiState.value
        if (currentState is WorkoutExecutionState.Active) {
            if (currentState.currentSet < currentState.totalSets) {
                // Переход к следующему подходу того же упражнения
                _uiState.value = WorkoutExecutionState.Rest(
                    exercise = currentState.exercise,
                    currentSet = currentState.currentSet + 1,
                    totalSets = currentState.totalSets,
                    restTimeMillis = currentState.exercise.timeMillis,
                    totalRestTimeMillis = currentState.exercise.timeMillis
                )
                startRestTimer()
                scheduleIdleReminderIfActive()
            } else {
                // Упражнение завершено, переходим к следующему
                moveToNextExercise()
            }
        }
    }
```

В `moveToNextExercise()`, заменить (в редакции после Task 3):

```kotlin
    private fun moveToNextExercise() {
        if (exerciseIndex < exercises.size - 1) {
            exerciseIndex++
            val nextExercise = exercises[exerciseIndex]
            _uiState.value = WorkoutExecutionState.Rest(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets,
                restTimeMillis = nextExercise.timeMillis,
                totalRestTimeMillis = nextExercise.timeMillis
            )
            startRestTimer()
        } else {
            val workoutId = workout?.id ?: return
            viewModelScope.launch {
                workoutRepository.updateLastUseAt(workoutId)
                val finishedAt = System.currentTimeMillis()
                val rawDurationMillis = finishedAt - sessionStartedAt
                val durationMillis = (rawDurationMillis - excludedIdleMillis).coerceAtLeast(0L)
                addWorkoutSessionUseCase(
                    workoutId = workoutId,
                    startedAt = sessionStartedAt,
                    finishedAt = finishedAt,
                    durationMillis = durationMillis
                )
                _uiState.value = WorkoutExecutionState.Finished(durationMillis = durationMillis)
            }
        }
    }
```

на:

```kotlin
    private fun moveToNextExercise() {
        if (exerciseIndex < exercises.size - 1) {
            exerciseIndex++
            val nextExercise = exercises[exerciseIndex]
            _uiState.value = WorkoutExecutionState.Rest(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets,
                restTimeMillis = nextExercise.timeMillis,
                totalRestTimeMillis = nextExercise.timeMillis
            )
            startRestTimer()
            scheduleIdleReminderIfActive()
        } else {
            val workoutId = workout?.id ?: return
            viewModelScope.launch {
                workoutRepository.updateLastUseAt(workoutId)
                val finishedAt = System.currentTimeMillis()
                val rawDurationMillis = finishedAt - sessionStartedAt
                val durationMillis = (rawDurationMillis - excludedIdleMillis).coerceAtLeast(0L)
                addWorkoutSessionUseCase(
                    workoutId = workoutId,
                    startedAt = sessionStartedAt,
                    finishedAt = finishedAt,
                    durationMillis = durationMillis
                )
                _uiState.value = WorkoutExecutionState.Finished(durationMillis = durationMillis)
                scheduleIdleReminderIfActive()
            }
        }
    }
```

В `moveToSelectedExercise()`, заменить (в редакции после Task 2):

```kotlin
    fun moveToSelectedExercise(exercise: Exercise) {
        val index = exercises.indexOfFirst { it.id == exercise.id }

        if (index != -1) {
            registerInteraction()
            timerJob?.cancel()
            wakeLockHelper.release()
            stopNotification()
            exerciseIndex = index
            val nextExercise = exercises[exerciseIndex]

            _uiState.value = WorkoutExecutionState.Active(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets
            )
        }
    }
```

на:

```kotlin
    fun moveToSelectedExercise(exercise: Exercise) {
        val index = exercises.indexOfFirst { it.id == exercise.id }

        if (index != -1) {
            registerInteraction()
            timerJob?.cancel()
            wakeLockHelper.release()
            stopNotification()
            exerciseIndex = index
            val nextExercise = exercises[exerciseIndex]

            _uiState.value = WorkoutExecutionState.Active(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets
            )
            scheduleIdleReminderIfActive()
        }
    }
```

В `onCleared()`, заменить:

```kotlin
    override fun onCleared() {
        super.onCleared()
        soundPlayer.release()
        wakeLockHelper.release()
        stopNotification()
    }
```

на:

```kotlin
    override fun onCleared() {
        super.onCleared()
        soundPlayer.release()
        wakeLockHelper.release()
        stopNotification()
        idleReminderJob?.cancel()
    }
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: PASS (все тесты)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt
git commit -m "feat: schedule idle-reminder notification while waiting in Active state"
```

---

## Task 6: Полный прогон тестов и ручная проверка

**Files:** нет изменений production-кода — только проверка.

- [ ] **Step 1: Полный прогон юнит-тестов модуля**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, все тесты (включая не связанные с этой задачей) проходят.

- [ ] **Step 2: Сборка debug-APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Ручная проверка на устройстве/эмуляторе**

1. Установить debug-сборку, начать тренировку с несколькими упражнениями.
2. Дойти до состояния "Active" (ожидание нажатия «Закончить упражнение»), свернуть приложение (Home) и не возвращаться 5+ минут.
3. Убедиться, что пришло уведомление «Вы всё ещё тренируетесь?» с именем текущего упражнения и номером подхода.
4. Развернуть приложение, сразу нажать «Закончить упражнение» до истечения 10 минут простоя — убедиться, что в диалоге «Тренировка завершена» длительность не содержит аномального скачка (совпадает с ожидаемым временем без учёта краткого визита в фон).
5. Повторить сценарий, но не возвращаться в приложение дольше 10 минут — убедиться, что после финального нажатия сохранённая длительность (можно посмотреть в экране «История» тренировки) не включает простой сверх 10-минутного порога.
6. Убедиться, что обычный отдых между подходами (`Rest`, десятки секунд — пара минут) не вызывает никакого уведомления и не подрезается в итоговой длительности.

- [ ] **Step 4: Commit (если в ходе ручной проверки потребовались правки)**

Коммитить только если Step 3 выявил проблему и потребовал точечного фикса; в противном случае Task 6 не создаёт коммита.
