# Статистика времени тренировки — дизайн

## Цель

Во время выполнения тренировки приложение должно фиксировать **фактическое время, потраченное на всю тренировку** (wall-clock разница между стартом и завершением, без учёта пауз/сворачивания приложения), сохранять его в истории сессий и показывать пользователю:

1. Один раз — на экране/диалоге "Тренировка завершена" сразу после окончания.
2. В списке тренировок — длительность последней завершённой сессии рядом с существующей датой последнего использования.
3. В отдельном экране истории — список всех завершённых сессий конкретной тренировки (дата + длительность), доступном через долгий тап по карточке тренировки → пункт меню "История".

## Вне рамок

- Фактический вес/повторения по подходам (в этой версии не фиксируются — только суммарное время).
- RPE / субъективная сложность.
- Учёт пауз/времени в фоне — считается простая разница `finishedAt - startedAt`.
- Экспорт/импорт истории сессий (текущий JSON-экспорт тренировок не меняется).
- Частично пройденные тренировки — сессия сохраняется **только** при полном завершении всех упражнений. Досрочный выход (кнопка "назад") не создаёт запись.

## Архитектура

Новая сущность `WorkoutSession` (Room-таблица `workout_sessions`), привязанная к `Workout` по `workoutId`. Записывается в момент полного завершения тренировки в `WorkoutExecutionViewModel`. Читается двумя способами: (а) агрегированно — "последняя сессия на тренировку" для карточек в списке, (б) полным списком — для экрана истории конкретной тренировки. Слой репозитория/use-case расширяется по существующему в проекте паттерну "один use case — одно действие".

## Часть A: Данные и слой доступа

**Файлы:**
- `data/entity/WorkoutSessionEntity.kt` (новый)
- `data/dao/WorkoutDao.kt` (методы для сессий)
- `data/dao/AppDatabase.kt` (версия 6→7, `MIGRATION_6_7`)
- `domain/model/WorkoutSession.kt` (новый)
- `domain/repository/WorkoutRepository.kt` + `data/WorkoutRepositoryImpl.kt` (новые методы)
- `domain/usecase/AddWorkoutSessionUseCase.kt`, `GetWorkoutSessionsUseCase.kt`, `GetLastSessionDurationsUseCase.kt` (новые)
- `di/AppModule.kt` (регистрация миграции)

**Схема:**

```kotlin
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutId: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMillis: Long
)
```

`MIGRATION_6_7` создаёт таблицу `workout_sessions` через `execSQL`, не трогая существующие таблицы (в проекте включён `fallbackToDestructiveMigration(dropAllTables = true)` — миграция обязана быть явной, иначе апгрейд сотрёт все тренировки пользователей).

**DAO:**
- `insertSession(session: WorkoutSessionEntity)`
- `getSessionsForWorkout(workoutId: Long): Flow<List<WorkoutSessionEntity>>` (сортировка по `finishedAt` убыв.)
- `getLastSessionDurations(): Flow<List<LastSessionDuration>>` — агрегирующий запрос: для каждого `workoutId` берёт запись с максимальным `finishedAt`, возвращает `workoutId` + `durationMillis`. Нужен как отдельный запрос (а не пер-элементный вызов из списка), чтобы Room-`Flow` инвалидировался при вставке новой сессии.

```kotlin
data class LastSessionDuration(
    val workoutId: Long,
    val durationMillis: Long
)
```

**Domain и repository:**

```kotlin
data class WorkoutSession(
    val id: Int = 0,
    val workoutId: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMillis: Long
)
```

`WorkoutRepository` получает:
- `suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long)`
- `fun getSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSession>>`
- `fun getLastSessionDurations(): Flow<Map<Int, Long>>` (workoutId → durationMillis)

## Часть B: Запись сессии и экран завершения

**Файлы:**
- `presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt`
- `presentation/utils/DateFormatter.kt` (новая функция форматирования длительности)

**Логика ViewModel:**
- Поле `private var sessionStartedAt: Long = 0L`.
- В `loadWorkout()`, в ветке успешной загрузки — `sessionStartedAt = System.currentTimeMillis()`.
- `WorkoutExecutionState.Finished` меняется с `data object` на `data class Finished(val durationMillis: Long) : WorkoutExecutionState()`.
- В `moveToNextExercise()`, ветка "упражнений больше нет" (после `updateLastUseAt`): вычислить `finishedAt = System.currentTimeMillis()`, вызвать `addWorkoutSessionUseCase(workoutId = id, startedAt = sessionStartedAt, finishedAt = finishedAt)`, затем `_uiState.value = WorkoutExecutionState.Finished(durationMillis = finishedAt - sessionStartedAt)`.
- Досрочный выход (пользователь нажимает "назад" до этой ветки) не пишет сессию — специальной обработки не требуется, `ViewModel` просто уничтожается.

**Логика экрана:**
- Текущий `LaunchedEffect(uiState) { if (uiState is Finished) onExerciseCompleted() }` заменяется диалогом: при `Finished` показывается `AlertDialog` с заголовком "Тренировка завершена" и текстом вида "Время: 42 мин", кнопка "ОК" вызывает `onExerciseCompleted()`. Без действия пользователя экран не закрывается.

**Форматирование:** новая функция `DateFormatter.formatDurationToString(millis: Long): String` — обычная (не `@Composable`) функция: если `millis >= 1 час` → `"Xч Yмин"`, иначе → `"Yмин"`.

## Часть C: Карточка в списке тренировок

**Файлы:**
- `presentation/screen/workouts/ListWorkoutViewModel.kt`
- `presentation/screen/workouts/ListWorkoutScreen.kt`

`ListWorkoutState` получает поле `lastSessionDurations: Map<Int, Long> = emptyMap()`. `ListWorkoutViewModel` комбинирует существующий `Flow` тренировок с новым `getLastSessionDurationsUseCase()` через `combine`, обновляя оба поля state вместе. Существующие use case'ы (`GetAllWorkoutsUseCase`, `SearchWorkoutsUseCase`, сигнатуры с `WorkoutEntity`) не меняются.

`WorkoutCard` получает новый параметр `lastSessionDurationMillis: Long?`. Если значение не `null`, под существующей строкой с датой добавляется текст с отформатированной длительностью (`DateFormatter.formatDurationToString`). Если тренировку ещё ни разу не завершали — ничего не выводится, только дата (как сейчас).

## Часть D: Экран истории сессий

**Файлы:**
- `presentation/screen/workoutHistory/WorkoutHistoryViewModel.kt` (новый)
- `presentation/screen/workoutHistory/WorkoutHistoryScreen.kt` (новый)
- `presentation/navigation/NavGraph.kt` (новый route)
- `presentation/screen/workouts/ListWorkoutScreen.kt` (пункт меню "История")

Новый route `history/{workout_id}`. Точка входа — существующий long-press `DropdownMenu` в `WorkoutCard`: рядом с "Редактировать"/"Удалить" добавляется пункт "История", проброшенный через новый параметр `onHistoryClick: (WorkoutEntity) -> Unit` (по аналогии с `onEditClick`).

`WorkoutHistoryViewModel`: получает `workoutId` из `SavedStateHandle`, отдаёт state с именем тренировки (через `GetWorkoutByIdUseCase`) и списком `WorkoutSession` (через `GetWorkoutSessionsUseCase`), отсортированным по `finishedAt` убыв.

`WorkoutHistoryScreen`: `TopAppBar` с именем тренировки и кнопкой "назад" (как на экране выполнения), `LazyColumn` со строками "дата, время — длительность" (`DateFormatter` для даты, `formatDurationToString` для длительности). Пустой список — текст "История пуста" по центру экрана.

## Тестирование

- Юнит-тесты на `formatDurationToString` (минуты, часы+минуты, 0 мс).
- Юнит-тесты на `WorkoutExecutionViewModel`: сессия сохраняется только при достижении `Finished` после последнего упражнения; `durationMillis` корректно равен `finishedAt - startedAt`; при отмене на середине сессия не пишется (use case не вызывается).
- Юнит-тест на DAO/агрегирующий запрос `getLastSessionDurations` (через Room in-memory DB): для тренировки с несколькими сессиями возвращается именно последняя по `finishedAt`.
- Ручная проверка миграции `MIGRATION_6_7`: апгрейд существующей БД версии 6 не теряет данные в `workouts`/`exercises`.
