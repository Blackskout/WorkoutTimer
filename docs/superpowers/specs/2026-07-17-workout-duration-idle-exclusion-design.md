# Исключение простоя из длительности тренировки — дизайн

## Проблема

`WorkoutExecutionViewModel` считает длительность сессии как простую разницу wall-clock `finishedAt - startedAt` ([`WorkoutExecutionViewModel.kt:296-301`](../../../app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt)). Приложение не завершается при сворачивании — `ViewModel` продолжает жить. Если пользователь физически закончил тренировку, но забыл нажать «Закончить упражнение» на последнем подходе, и ушёл из приложения, таймер продолжает молча идти. Когда пользователь возвращается — через минуты или часы — и всё-таки нажимает кнопку, весь этот простой попадает в сохранённую длительность, искажая статистику (обычно завышая её).

Единственное состояние, которое может «зависнуть» на неопределённое время — `Active`: оно ждёт ручного нажатия и не имеет собственного таймера. `Rest`, наоборот, самоограничено — досчитывает до нуля, воспроизводит звук/вибрацию и переходит в `Active` автоматически, независимо от присутствия пользователя.

## Цель

1. Длительность, которая пишется в `workout_sessions`, не должна включать длинные периоды простоя между двумя реальными действиями пользователя — независимо от того, в каком упражнении/подходе и в каком порядке эти действия происходили (в приложении можно переключаться между упражнениями через выпадающий список, минуя линейный порядок).
2. Дать пользователю мягкое напоминание вернуться, если он завис в `Active` дольше 5 минут — по его собственной изначальной идее.

## Вне рамок

- Восстановление сессии после уничтожения процесса Android (kill под memory pressure). Если это происходит — сессия, как и сейчас, просто не сохраняется. Пользователь подтвердил, что его реальная проблема — завышенная длительность у сохранённых сессий, а не их полное отсутствие.
- Диалог/экран для просмотра или ручной корректировки исключённого времени перед сохранением — исключение происходит молча.
- Любая логика, завязанная на порядок/идентичность упражнений (сегментирование по конкретному подходу или упражнению). Сознательно отвергнуто: в приложении есть свободный переход между упражнениями через `ExercisesDropdown` (`moveToSelectedExercise`), и любая механика, завязанная на этом порядке, рискует конфликтовать с ним. Решение ниже полностью не зависит от того, какое упражнение активно.
- Изменение формата уведомления таймера отдыха (`TimerNotificationService` для `Rest`) — не трогается.

## Архитектура

Всё изменение локализовано в `WorkoutExecutionViewModel`. Схема `WorkoutSessionEntity` не меняется — `durationMillis` уже хранится отдельным полем, независимым от `startedAt`/`finishedAt`, так что миграция БД не требуется.

### Часть A: Исключение простоя (основной фикс)

**Файл:** `presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`

Новые поля:

```kotlin
private var lastInteractionAt: Long = 0L
private var excludedIdleMillis: Long = 0L

companion object {
    private const val IDLE_EXCLUSION_THRESHOLD_MILLIS = 10 * 60 * 1000L // 10 минут
}
```

Новый приватный метод, вызываемый в начале каждого обработчика реального пользовательского действия:

```kotlin
private fun registerInteraction() {
    val now = System.currentTimeMillis()
    if (lastInteractionAt != 0L) {
        val gap = now - lastInteractionAt
        if (gap > IDLE_EXCLUSION_THRESHOLD_MILLIS) {
            excludedIdleMillis += gap - IDLE_EXCLUSION_THRESHOLD_MILLIS
        }
    }
    lastInteractionAt = now
}
```

Вызывается из:
- `onExerciseFinished()` — в начале
- `skipRest()` — в начале
- `moveToSelectedExercise()` — в начале, после проверки `index != -1`
- `updateExerciseNote()` — в начале

**Не** вызывается из `onRestFinished()` — это автоматическое срабатывание таймера, а не доказательство присутствия пользователя. Гэп, накопленный во время `Rest`, за которым следует необнаруженный простой в `Active`, попадёт в один общий интервал между двумя реальными нажатиями и будет обработан той же формулой (см. «Как это работает на практике» ниже).

`loadWorkout()`, ветка успешной загрузки — вместе с `sessionStartedAt = System.currentTimeMillis()` инициализируется и `lastInteractionAt = sessionStartedAt` (первая точка отсчёта, чтобы `registerInteraction()` при первом реальном действии не посчитал весь путь от старта загрузки гэпом).

`moveToNextExercise()`, ветка завершения тренировки — перед вызовом `addWorkoutSessionUseCase` уже отработал `registerInteraction()` (т.к. эта ветка вызывается из `onExerciseFinished()`). Вычисление меняется с

```kotlin
val durationMillis = addWorkoutSessionUseCase(workoutId, sessionStartedAt, finishedAt)
```

(где `AddWorkoutSessionUseCase`/`WorkoutRepositoryImpl` сейчас сами считают `durationMillis = finishedAt - startedAt`) на явную передачу скорректированной длительности:

```kotlin
val rawDurationMillis = finishedAt - sessionStartedAt
val durationMillis = (rawDurationMillis - excludedIdleMillis).coerceAtLeast(0L)
addWorkoutSessionUseCase(workoutId, sessionStartedAt, finishedAt, durationMillis)
_uiState.value = WorkoutExecutionState.Finished(durationMillis = durationMillis)
```

`AddWorkoutSessionUseCase.invoke` и `WorkoutRepository.addWorkoutSession` получают новый параметр `durationMillis: Long` вместо вычисления его внутри, и возвращают `Unit` вместо `Long` — вызывающая сторона уже знает итоговую длительность и сама передаёт её в `WorkoutExecutionState.Finished`, повторное вычисление/возврат из репозитория не нужно. `WorkoutRepositoryImpl.addWorkoutSession` пишет переданное значение напрямую в `WorkoutSessionEntity.durationMillis`, не пересчитывая его из `startedAt`/`finishedAt`.

**Как это работает на практике:** гэп между любыми двумя реальными нажатиями (будь то соседние подходы одного упражнения, переход между упражнениями по порядку или прыжок через дропдаун) свыше 10 минут считается простоем, и всё, что превышает эти 10 минут, вычитается из итоговой длительности. Обычный `Rest` (десятки секунд — пара минут) никогда не приближается к порогу, так что легитимный отдых не подрезается.

### Часть B: Напоминание о простое

**Файлы:**
- `presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- `presentation/service/TimerNotificationService.kt`

Новый `Job` в `WorkoutExecutionViewModel`, аналогично `timerJob`:

```kotlin
private var idleReminderJob: Job? = null

companion object {
    private const val IDLE_REMINDER_DELAY_MILLIS = 5 * 60 * 1000L // 5 минут
}
```

- Запускается (перезапускается) при входе в `Active`-состояние (в конце `onExerciseFinished()`/`moveToNextExercise()`/`moveToSelectedExercise()`/`onRestFinished()`, во всех местах, где `_uiState.value` становится `WorkoutExecutionState.Active`) и при каждом вызове `registerInteraction()`.
- Отменяется при выходе из `Active` (переход в `Rest`, `Finished`, либо `moveToSelectedExercise` на новое упражнение) и в `onCleared()`.
- Реализация — простой `viewModelScope.launch { delay(IDLE_REMINDER_DELAY_MILLIS); showIdleReminderNotification() }`, без WorkManager/AlarmManager: это ненавязчивое, best-effort напоминание (по аналогии с уже существующим `countdownFlow` для отдыха), а не источник корректности данных — за корректность отвечает Часть A независимо от того, показалось ли уведомление.

`TimerNotificationService` получает новый `ACTION_SHOW_IDLE_REMINDER` (по образцу существующего `ACTION_SHOW_FINISHED`) и новый notification channel `idle_reminder_notification_channel` (`IMPORTANCE_DEFAULT`, с текстом вида «Вы всё ещё тренируетесь? Не забудьте закончить подход» и именем текущего упражнения). Уведомление не `ongoing`, автоматически убирается по тапу (`setAutoCancel(true)`), открывает `MainActivity` аналогично существующим.

## Тестирование

- Юнит-тесты на `WorkoutExecutionViewModel` (расширяют существующий `WorkoutExecutionViewModelTest`):
  - Гэп между действиями меньше порога (10 мин) — `durationMillis` совпадает с `finishedAt - startedAt` без вычетов.
  - Гэп больше порога — вычитается только превышение над порогом, не весь гэп.
  - Несколько гэпов за одну тренировку — исключения суммируются.
  - Прыжок через `moveToSelectedExercise` корректно сбрасывает `lastInteractionAt` (не создаёт ложный гэп и не «прячет» реальный простой, случившийся до прыжка).
  - `registerInteraction()` не вызывается из `onRestFinished()` — автоматическое завершение отдыха само по себе не сбрасывает счётчик простоя.
- Ручная проверка: свернуть приложение в состоянии `Active` дольше 5 минут → приходит напоминание; развернуть, нажать «Закончить упражнение» сразу — длительность не пострадала (гэп меньше 10-минутного порога, только напоминание успело сработать).
