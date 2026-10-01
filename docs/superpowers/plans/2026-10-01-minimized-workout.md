# Свёрнутая тренировка (мини-плеер) — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Идущая тренировка переживает уход с экрана выполнения: «Назад» сворачивает её в плашку внизу всех экранов (отдых с отсчётом, подход, «Пора: подход N»), тап по плашке, виджет и уведомление возвращают в неё; тап по тренировке в списке открывает новый экран просмотра с «Начать» / «Вернуться к тренировке» / «+ В сегодняшнюю». Схема базы не меняется.

**Architecture:** Вся логика сессии переезжает из `WorkoutExecutionViewModel` в `@Singleton WorkoutSessionManager` (`presentation/session/`), корутины — в области процесса `@ApplicationScope` (`SupervisorJob + Main.immediate + CoroutineExceptionHandler`) и в дочерней области текущей сессии. Состояние — один снимок `StateFlow<WorkoutSession>`; сводка без тиков таймера — `StateFlow<RunningWorkout?>`. ViewModel экрана выполнения становится тонким адаптером; плашка (`MiniWorkoutBar` + `MiniWorkoutBarViewModel`) рисуется в корне навигации под `NavHost`. Правила, которые можно проверить без Android (`miniBarStateOf`, `ringFraction`, `resolveIntent`, `listMenuEntries`, `lastTimeByCatalog`, `previewBottomOf`, `runningWorkoutOf`, `formatPlannedSet`), — чистые функции с JVM-тестами.

**Tech Stack:** Kotlin 2.2.21, Jetpack Compose (BOM 2026.03.00, foundation-layout 1.10.x, Material3, тема «Dark Athletic»), Hilt 2.57.2 (+ hilt-navigation-compose 1.3.0, `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`), Navigation Compose 2.9.7, Coroutines 1.10.2, Room 2.8.4 (схема v8 без изменений), JUnit4 + MockK 1.13.13 + coroutines-test (JVM), Compose UI test (`androidTest`, эмулятор).

**Spec:** `docs/superpowers/specs/2026-10-01-minimized-workout-design.md` — целиком. Порядок задач повторяет «Порядок выпуска» спеки: **M1** (перенос в менеджер без изменения поведения) — Task 1–3, **M2** (сворачивание и плашка) — Task 4–7, **M3** (просмотр и «+ в сегодняшнюю») — Task 8–11, проверка на эмуляторе — Task 12. После Task 3 и после Task 7 приложение цельное: это границы будущих PR (их режет контроллер).

## Global Constraints

- Ветка `feature/minimized-workout` (спека — коммиты `1f50bbb`, `e46c635`). Push и PR в этом плане **не делаются** — их делает контроллер после финального ревью.
- Схема базы **не меняется**: версия 8, новых сущностей, миграций и `@Query` нет. После каждой задачи `git status --short app/schemas` — пусто; если нет — остановиться и разобраться, схему не коммитить. `fallbackToDestructiveMigrationFrom` в `AppModule.kt` не трогать.
- `app/build.gradle.kts` и `gradle/libs.versions.toml` не меняются: все нужные API уже есть (`WindowInsets.isImeVisible`, `Modifier.consumeWindowInsets`, `currentBackStackEntryAsState`, `NavController.currentBackStack`, `DropdownMenu`, `AnimatedVisibility`). `lifecycle-runtime-compose` не добавлять — состояние читается `collectAsState()`, как во всём проекте.
- Непечатные и типографские символы в исходниках Kotlin — только экранированными: `\u00A0` (неразрывный пробел), `\u2212` (минус), никогда не литералом, и в комментариях тоже. Перед коммитом: `grep -rnP '\x{00A0}|\x{2212}' app/src` — пусто.
- Регулярки — без флага `(?U)` (ICU на Android его не знает). В этом плане регулярок нет; если понадобятся — без `(?U)`.
- Все пользовательские тексты — в `app/src/main/res/values/strings.xml`, по-русски, в секции своего экрана, с префиксами из спеки: `minibar_` (плашка), `preview_` (просмотр), дополнения к `execution_` и `list_`. Тексты со счётом — `plurals`. Диалог выхода на плашке переиспользует `execution_exit_title` / `execution_exit_message`.
- Дизайн — только существующие токены и компоненты: `MaterialTheme.colorScheme/typography/shapes`, `ScreenPadding`/`CardSpacing`/`SectionSpacing`, `PrimaryButton`, `EmptyState`, `AppBottomSheet`, `ActionSheet`. Новых цветов, шрифтов и размеров текста нет; размеры геометрии плашки (64 dp, кольцо 28 dp в зоне 48 dp, обводка 3 dp) — константы в `MiniWorkoutBar.kt`.
- Чистая логика — в JVM-тестируемых функциях и классах; область корутин менеджера внедряется (`@ApplicationScope`), тесты подставляют `CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher)`. Часы в менеджер **не** внедряются (спека, «Время»): таймер и учёт простоя читают `System.currentTimeMillis()`, тесты вызывают `onRestFinished()` и `registerInteraction(now)` напрямую.
- Комментарии в коде — по-русски, коротко, объясняют «почему». Сообщения коммитов — по-русски с префиксом (`feat:`, `test:`, `refactor:`, `fix:`, `docs:`), последняя строка — `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Каждая задача заканчивается зелёным** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin` (lint — 0 ошибок; предупреждения `UnusedResources` между задачами допустимы, к концу Task 11 их быть не должно). CI инструментальные тесты не компилирует — поэтому `compileDebugAndroidTestKotlin` обязателен.
- Инструментальные тесты — **только** на `emulator-5554`: перед запуском `~/Library/Android/sdk/platform-tools/adb devices` — если в списке есть что-то кроме `emulator-5554`, **не запускать** (`connectedAndroidTest` сносит приложение с устройства). Запуск: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`. Эмулятор не запущен — `~/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro -no-snapshot-save -no-audio &` и дождаться `adb -s emulator-5554 shell getprop sys.boot_completed` = `1`. Имена методов в `androidTest` — через подчёркивания (`плашка_показывает_отдых`), без обратных кавычек.
- JVM-тесты по одному классу: `./gradlew :app:testDebugUnitTest --tests '<FQCN>'`. В JVM-тестах `isReturnDefaultValues = true`: `Intent`, `Uri`, `Log` — заглушки, поэтому всё, что разбирает интент, принимает строки.

## Review Focus

1. **Виджет или уведомление при свёрнутой тренировке, пользователь на другом экране, виджет показывает другую тренировку.** Ожидается: открывается выполнение **идущей** тренировки поверх текущего экрана, стек не перестраивается, вторая сессия не начинается; тап по устаревшему «Отдых завершён» после выхода из тренировки просто выводит приложение вперёд. Тесты — Task 6 (`resolveIntent`), ручная проверка — Task 12.
2. **Двойной тап по плашке / повторный возврат.** Ожидается: в стеке ровно одна запись выполнения, «Назад» после возврата ведёт на экран, с которого вернулись; если выполнение — единственная запись, сворачивание открывает список, а не пустой экран. Тест — Task 5 (`SessionNavigationTest`).
3. **Пересоздание активности посреди тренировки (тема, шрифт, язык).** Ожидается: сессия не сбрасывается (подходы, индекс, учёт простоя на месте), экран выполнения не мигает «Загрузкой». Тесты — Task 2 (`start` идемпотентен), Task 3 (`uiState` сразу равен фазе снимка).
4. **«+ в сегодняшнюю», когда пользователь стоит на последнем подходе, и после сбоя записи завершения.** Ожидается: «Закончить подход» не завершает тренировку, а уводит на отдых перед добавленным упражнением; после сбоя записи первый подход добавленного упражнения дописывается, а не затирает предыдущий. Тесты — Task 9.
5. **Правка или удаление идущей тренировки из списка.** Ожидается: пункты «Редактировать» и «Удалить» в меню её строки неактивны с подписью «Недоступно во время тренировки»; у других тренировок — активны. Тесты — Task 7 (`listMenuEntries`, `ActionSheetContentTest`).

## Решения по неоднозначностям спеки

- **Вопрос о клавиатуре закрыт контроллером:** плашка прячется при открытой клавиатуре только там, где это работает (API 35+, окно принудительно edge-to-edge); на Android 7–14 плашка при клавиатуре остаётся видимой. Решение записано в спеку тем же коммитом, что и этот план.
- **`runningWorkout` — синхронно обновляемый `MutableStateFlow`, а не `map + distinctUntilChanged + stateIn(scope)`.** Каждая запись снимка идёт через `setSession()`, которая тут же пересчитывает сводку; `MutableStateFlow` сам не публикует равное значение, так что тики отдыха сводку не меняют. Семантика спеки та же, но без лишней корутины и без задержки на один диспетчер: экран просмотра видит «Добавлено» в том же кадре, а тесты не зависят от порядка запуска коллекторов.
- **`registerInteraction` — одна публичная функция с `now: Long = System.currentTimeMillis()`** вместо пары «публичная без аргумента + internal с `now`».
- **Тонкая ViewModel не отдаёт экрану `None`.** `uiState` и шапка берут фазу только из `Present` (`mapNotNull`): после «Выйти без сохранения» уходящий экран на время анимации держит последний кадр, а не «Загрузку» (спека требует того же порядком «сначала `popBackStack()`, потом `abandon()`», но этого порядка мало — экран всё равно перерисовывается после `abandon()`).
- **Сворачивание проверяет `previousBackStackEntry`, а не результат `popBackStack()`.** `popBackStack()` единственной записи возвращает `true` и оставляет `NavHost` пустым, так что ветку «вернул `false`» спеки поймать нельзя; при отсутствии записи под выполнением открывается список с `popUpTo(Execution) { inclusive = true }`.
- **`start()` новой сессии сбрасывает `finishError`.** Ошибка — свойство сессии; новая сессия начинается без неё.
- **`updateLastUseAt` при `abandon()` во время записи завершения всё равно вызывается.** Сессия уже записана (пользователь подтвердил завершение) — очередь должна это учесть; состояние при этом остаётся `None` (сверка `sessionId`).
- **`isFinishing` в `Finished` опускается** (раньше оставался поднятым навсегда). В `Finished` `onExerciseFinished()` и так ничего не делает: фаза не `Active`.
- **`restOver` добавляется в Task 4 полем со значением по умолчанию `false`**, чтобы конструкторы `Present` из M1 не менялись.
- **Отдых в строке плана — `DateFormatter.formatDurationCompact`**, как велит спека: «отдых 02:00», а не «2:00» из примера (тот же формат, что у кольца отдыха).
- **TalkBack у строки упражнения на просмотре:** «%1$s, открыть прогресс» — `onClickLabel` строки, а не `contentDescription`: описание заменило бы для TalkBack план и «прошлый раз».
- **Сбой чтения на просмотре** — пустое состояние «Не удалось загрузить тренировку» (`preview_error_load`, в таблице строк спеки его нет).
- **«Прошлый раз» — последняя сессия по `finishedAt`.** Источник (`observeLastSessionSets`) и так отдаёт одну сессию на запись справочника; если для `catalogId` пришли подходы нескольких сессий, берутся подходы самой поздней.
- **Правила «НАЧАТЬ» / «ВЕРНУТЬСЯ», пункты меню и блокировки — в M2 (Task 7)**, вместе с временным правилом «тап по строке при идущей сессии возвращает в неё». В M3 (Task 11) меняется только тап по строке и по карточке «Следующая» — они ведут на просмотр.
- **Меню строки списка — чистая функция `listMenuEntries`**, чтобы правило блокировок проверялось JVM-тестом без композиции листа.
- **Событие «вернуться к тренировке»** из `MainActivity` — флаг `mutableStateOf(false)`, как уже устроен `newIntent`; `NavGraph` гасит его колбэком.
- **`ACTION_OPEN_ACTIVE_WORKOUT`** — константа в `companion object` `MainActivity` (как в спеке); `resolveIntent` и JVM-тест ссылаются на неё (`const` встраивается при компиляции, класс активности в тесте не грузится).

## Карта файлов

- `di/CoroutineScopeModule.kt` (новый) — квалификатор `@ApplicationScope` и область процесса.
- `presentation/session/` (новый пакет):
  - `WorkoutExecutionState.kt` — перенесён из `WorkoutExecutionViewModel.kt` без изменений формы;
  - `WorkoutSession.kt` — `SessionExercise`, `WorkoutSession`, `RunningWorkout`, `AddResult`, `runningWorkoutOf`, признаки фазы;
  - `WorkoutSessionManager.kt` — вся логика сессии;
  - `MiniBarState.kt` — `MiniBarState`, `MiniBarStatus`, `miniBarStateOf`, `ringFraction`;
  - `MiniWorkoutBarViewModel.kt`, `MiniWorkoutBar.kt` — плашка.
- `presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt` — тонкий адаптер + `ExecutionChrome`; `WorkoutExecutionScreen.kt` — индексы вместо `Exercise`, сворачивание, меню ⋮, метка «сегодня».
- `presentation/navigation/SessionNavigation.kt` (новый) — `returnToSession`, `minimizeExecution`; `NavGraph.kt` — колонка с плашкой, диалог ×, маршрут просмотра, новые колбэки списка.
- `presentation/IntentRoute.kt` (новый) — `IntentRoute`, `resolveIntent`; `presentation/MainActivity.kt` — менеджер вместо трекера, `onCreate`/`onNewIntent`.
- `presentation/service/TimerNotificationService.kt` — `action` у трёх `PendingIntent`; `presentation/widget/QuickStartWidget.kt` — комментарий.
- `presentation/utils/ActiveWorkoutTracker.kt` и `ActiveWorkoutTrackerTest.kt` — удаляются.
- `presentation/ui/components/Sheets.kt` — `ActionSheetItem.enabled`.
- `presentation/screen/workouts/` — `ListMenu.kt` (новый, `listMenuEntries`), `ListWorkoutViewModel.kt` (`runningWorkoutId`), `ListWorkoutScreen.kt` (колбэки `onOpen`/`onStart`/`onReturn`, «ВЕРНУТЬСЯ», блокировки, кликабельная карточка).
- `domain/usecase/ObserveWorkoutUseCase.kt` (новый); `presentation/utils/SetFormat.kt` — `formatPlannedSet`.
- `presentation/screen/workoutPreview/` (новый пакет) — `WorkoutPreviewViewModel.kt` (+ `lastTimeByCatalog`, `previewBottomOf`), `WorkoutPreviewScreen.kt` (+ `WorkoutPreviewContent`).
- `app/src/main/res/values/strings.xml` — секции `minibar_`, `preview_`, дополнения `execution_`, `list_`.
- Тесты JVM: `presentation/session/WorkoutSessionManagerTest.kt`, `MiniBarStateTest.kt`, `MiniWorkoutBarViewModelTest.kt`, `WorkoutSessionTest.kt`; `presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt` (переписан); `presentation/navigation/ScreenDeepLinkTest.kt`; `presentation/screen/workouts/ListMenuTest.kt`, `ListWorkoutViewModelTest.kt`; `domain/usecase/ObserveWorkoutUseCaseTest.kt`; `presentation/utils/SetFormatTest.kt`; `presentation/screen/workoutPreview/WorkoutPreviewViewModelTest.kt`.
- Тесты `androidTest`: `presentation/session/MiniWorkoutBarContentTest.kt`, `presentation/navigation/SessionNavigationTest.kt`, `presentation/ui/components/ActionSheetContentTest.kt`, `presentation/screen/workouts/QueueContentTest.kt`, `presentation/screen/workoutPreview/WorkoutPreviewContentTest.kt`; `ActiveContentTilesTest.kt` — импорт.

Пути ниже без префикса — от `app/src/main/java/ru/hopes/workouttimer/`; тесты — от `app/src/test/java/ru/hopes/workouttimer/` и `app/src/androidTest/java/ru/hopes/workouttimer/`.

---

### Task 1: Менеджер сессии с перенесёнными тестами (M1, поведение не меняется)

Перенос логики `WorkoutExecutionViewModel` в `WorkoutSessionManager` один к одному. Старая ViewModel пока остаётся и работает как раньше (её сменит Task 3); меняется только пакет `WorkoutExecutionState`. В этой задаче `start()` ещё ведёт себя как `loadWorkout()` — всегда начинает новую сессию; идемпотентность, `abandon`, `close`, `isRunning` и `runningWorkout` — в Task 2.

**Files:**
- Create: `di/CoroutineScopeModule.kt`
- Create: `presentation/session/WorkoutExecutionState.kt`
- Create: `presentation/session/WorkoutSession.kt`
- Create: `presentation/session/WorkoutSessionManager.kt`
- Modify: `presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt` (удалить `sealed class WorkoutExecutionState`, строки 530–553; добавить импорт)
- Modify: `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` (импорт)
- Modify (test): `presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt` (импорт)
- Modify (androidTest): `presentation/screen/workoutExecution/ActiveContentTilesTest.kt` (импорт)
- Test: `presentation/session/WorkoutSessionManagerTest.kt` (новый, 34 перенесённых теста)

**Interfaces:**
- Consumes: `GetWorkoutByIdUseCase.invoke(id: Int): Workout?`, `WorkoutRepository.updateExerciseNote(exerciseId: Int, note: String)`, `WorkoutRepository.updateExerciseWeightAndReps(exerciseId: Int, weight: Double, extraWeight: Double, reps: Int)`, `WorkoutRepository.updateLastUseAt(workoutId: Int)`, `FinishWorkoutSessionUseCase.invoke(workoutId, startedAt, finishedAt, durationMillis, sets: List<RecordedSet>)`, `TimerNotificationService.ACTION_*`/`EXTRA_*`, `SoundPlayer.playSound/release`, `VibrationManager.vibrate`, `WakeLockHelper.acquire/release`.
- Produces:
  - `@Qualifier annotation class ApplicationScope` (`ru.hopes.workouttimer.di`)
  - `sealed class WorkoutExecutionState` в пакете `ru.hopes.workouttimer.presentation.session` (форма прежняя: `Loading`, `Error`, `Rest(exercise, currentSet, totalSets, restTimeMillis, totalRestTimeMillis)`, `Active(exercise, currentSet, totalSets, weight, reps, extraWeight)`, `Finished(durationMillis)`)
  - `data class SessionExercise(val exercise: Exercise, val addedToday: Boolean = false)`
  - `sealed interface WorkoutSession { data object None; data class Present(sessionId: Long, workoutId: Int, workoutName: String, exercises: List<SessionExercise>, exerciseIndex: Int, phase: WorkoutExecutionState, isFinishing: Boolean = false) }`
  - `class WorkoutSessionManager`: `val session: StateFlow<WorkoutSession>`, `val finishError: StateFlow<Boolean>`, `val isLastSetOfWorkout: Boolean`, `fun start(workoutId: Int): Long`, `fun skipRest()`, `fun onExerciseFinished()`, `fun moveToExercise(index: Int)`, `fun updateExerciseNote(index: Int, note: String)`, `fun updateExerciseWeightAndReps(index: Int, weight: Double, extraWeight: Double, reps: Int)`, `fun registerInteraction(now: Long = System.currentTimeMillis())`, `fun dismissFinishError()`; `internal`: `recordedSets`, `lastInteractionAt`, `excludedIdleMillis`, `isIdleReminderJobActive`, `isRestTimerActive`, `onRestFinished()`; константы `IDLE_EXCLUSION_THRESHOLD_MILLIS`, `IDLE_REMINDER_DELAY_MILLIS`.

- [ ] **Step 1: Область процесса**

`di/CoroutineScopeModule.kt`:

```kotlin
package ru.hopes.workouttimer.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Область корутин на всё время жизни процесса. Сейчас в ней живёт идущая тренировка. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    private const val TAG = "ApplicationScope"

    /**
     * Main.immediate — состояние сессии меняется только на главном потоке, как раньше во
     * viewModelScope: поля менеджера без синхронизации рассчитаны на это. SupervisorJob —
     * сбой одной корутины (записи заметки) не отменяет таймер отдыха. Обработчик — без него
     * непойманное исключение дочерней корутины уронило бы процесс.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Сбой корутины в области процесса", e)
        }
    )
}
```

- [ ] **Step 2: `WorkoutExecutionState` в новый пакет**

`presentation/session/WorkoutExecutionState.kt` — класс переносится из `WorkoutExecutionViewModel.kt:530-553` без изменений:

```kotlin
package ru.hopes.workouttimer.presentation.session

import ru.hopes.workouttimer.domain.model.Exercise

sealed class WorkoutExecutionState {
    data object Loading : WorkoutExecutionState()

    data object Error : WorkoutExecutionState()

    data class Rest(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val restTimeMillis: Long = exercise.timeMillis,
        val totalRestTimeMillis: Long = exercise.timeMillis
    ) : WorkoutExecutionState()

    data class Active(
        val exercise: Exercise,
        val currentSet: Int,
        val totalSets: Int = exercise.sets,
        val weight: Double = exercise.weight,
        val reps: Int = exercise.reps,
        val extraWeight: Double = exercise.extraWeight
    ) : WorkoutExecutionState()

    data class Finished(val durationMillis: Long) : WorkoutExecutionState()
}
```

В `WorkoutExecutionViewModel.kt` удалить строки 530–553 (`sealed class WorkoutExecutionState { … }`) и добавить импорт `import ru.hopes.workouttimer.presentation.session.WorkoutExecutionState`. Тот же импорт добавить в `WorkoutExecutionScreen.kt`, в `app/src/test/.../workoutExecution/WorkoutExecutionViewModelTest.kt` и в `app/src/androidTest/.../workoutExecution/ActiveContentTilesTest.kt` (они в пакете `workoutExecution` и раньше видели класс без импорта). `MusicSheet.kt` упоминает `WorkoutExecutionState.Rest` только текстом KDoc — не трогать.

- [ ] **Step 3: Снимок состояния**

`presentation/session/WorkoutSession.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import ru.hopes.workouttimer.domain.model.Exercise

/** Упражнение сессии. Позиция в списке — ключ: список только дописывается в конец. */
data class SessionExercise(
    val exercise: Exercise,
    /** Копия из другой тренировки («+ в сегодняшнюю»): правки не пишутся в БД. */
    val addedToday: Boolean = false
)

/**
 * Один снимок на всю сессию: список, индекс и фаза меняются вместе, поэтому экран
 * не увидит «индекс уже новый, список ещё старый».
 */
sealed interface WorkoutSession {
    data object None : WorkoutSession

    data class Present(
        /** Растёт на каждом start() новой сессии; по нему ViewModel закрывает только свою. */
        val sessionId: Long,
        val workoutId: Int,
        val workoutName: String,
        val exercises: List<SessionExercise>,
        val exerciseIndex: Int,
        val phase: WorkoutExecutionState,
        /** Идёт запись завершённой сессии: второй тап по последнему подходу ничего не делает. */
        val isFinishing: Boolean = false
    ) : WorkoutSession
}
```

- [ ] **Step 4: Перенести тесты (падают — менеджера ещё нет)**

`app/src/test/java/ru/hopes/workouttimer/presentation/session/WorkoutSessionManagerTest.kt` — перенос 34 тестов `WorkoutExecutionViewModelTest` (четыре теста отметки «тренировка идёт» переедут в Task 2 на `isRunning`). Правила переноса: `buildViewModel` → `buildManager` (общая `scope` на `dispatcher`, отменяется в `tearDown`), `loadWorkout` → `start`, `uiState.value` → `phase` (расширение ниже), `exerciseId` → индекс (`id = 1` → `0`, `id = 2` → `1`), `moveToSelectedExercise(x)` → `moveToExercise(индекс x)`. Сценарии и ожидания не меняются. Файл целиком:

```kotlin
package ru.hopes.workouttimer.presentation.session

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.just
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.RecordedSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.domain.usecase.FinishWorkoutSessionUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import ru.hopes.workouttimer.presentation.utils.VibrationManager
import ru.hopes.workouttimer.presentation.utils.WakeLockHelper

/**
 * Перенос WorkoutExecutionViewModelTest на менеджер сессии: те же сценарии и
 * ожидания. Отличия механические: start вместо loadWorkout, индекс упражнения
 * вместо exercise.id, фаза — из снимка session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSessionManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    // Область менеджера на том же планировщике, что и runTest: виртуальное время общее.
    private lateinit var scope: CoroutineScope

    private val context = mockk<Context>(relaxed = true)
    private val soundPlayer = mockk<SoundPlayer>(relaxed = true)
    private val vibrationManager = mockk<VibrationManager>(relaxed = true)
    private val wakeLockHelper = mockk<WakeLockHelper>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @After
    fun tearDown() {
        // Без отмены напоминание о простое и таймер одного теста переживают его.
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun buildManager(
        getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
        workoutRepository: WorkoutRepository,
        finishWorkoutSessionUseCase: FinishWorkoutSessionUseCase
    ): WorkoutSessionManager = WorkoutSessionManager(
        context = context,
        soundPlayer = soundPlayer,
        getWorkoutByIdUseCase = getWorkoutByIdUseCase,
        vibrationManager = vibrationManager,
        wakeLockHelper = wakeLockHelper,
        workoutRepository = workoutRepository,
        finishWorkoutSessionUseCase = finishWorkoutSessionUseCase,
        scope = scope
    )

    /** Фаза текущей сессии; тест падает, если сессии нет. */
    private val WorkoutSessionManager.phase: WorkoutExecutionState
        get() = (session.value as WorkoutSession.Present).phase

    private fun singleSetWorkout(): Workout {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        return Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
    }

    @Test
    fun `finishing the only exercise saves a session and exposes its duration`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        val durationSlot = slot<Long>()
        coEvery {
            finishWorkoutSessionUseCase(
                workoutId = 1,
                startedAt = any(),
                finishedAt = any(),
                durationMillis = capture(durationSlot),
                sets = any()
            )
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished()

        val state = manager.phase
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(durationSlot.captured, (state as WorkoutExecutionState.Finished).durationMillis)
        coVerify(exactly = 1) {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        }
    }

    // f5956c4 поменял порядок вызовов местами: пуш обновления виджета подвешен на
    // updateLastUseAt(), поэтому он обязан срабатывать уже после записи сессии в БД, иначе
    // виджет может перерисоваться со старой длительностью или без неё. До этого теста порядок
    // не был закреплён ничем, кроме комментария в коде.
    @Test
    fun `finishing the workout records the session before pushing lastUseAt`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        coEvery {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished()

        coVerifyOrder {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
            workoutRepository.updateLastUseAt(1)
        }
    }

    @Test
    fun `excluded idle time is subtracted from the saved duration and clamped at zero`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        val durationSlot = slot<Long>()
        coEvery {
            finishWorkoutSessionUseCase(
                workoutId = 1,
                startedAt = any(),
                finishedAt = any(),
                durationMillis = capture(durationSlot),
                sets = any()
            )
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        // Bank a large excluded gap using timestamps far in the "future" relative to real
        // wall-clock: the jump from start's real lastInteractionAt to farFutureBase is
        // itself well over the threshold and banks its own excess, and the subsequent 45-minute
        // gap banks a further 35 minutes on top -- the exact total isn't asserted, only that it's
        // large enough to exceed the (near-zero, in a fast unit test) raw duration below. The
        // automatic registerInteraction() inside onExerciseFinished() (which uses the real
        // current time, far in the past relative to farFutureBase) then computes a negative gap
        // and adds no further exclusion.
        val farFutureBase = System.currentTimeMillis() + 10_000_000L
        manager.registerInteraction(now = farFutureBase)
        manager.registerInteraction(now = farFutureBase + 45 * 60 * 1000L) // additional 45 min gap

        manager.onExerciseFinished()

        // Raw elapsed (real start time -> real finishedAt time) is near-zero in a fast unit
        // test, while the banked exclusion is 35 minutes, so the coerceAtLeast(0L) clamp must apply.
        assertEquals(0L, durationSlot.captured)
        val state = manager.phase
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(0L, (state as WorkoutExecutionState.Finished).durationMillis)
    }

    @Test
    fun `loading a workout does not save a session by itself`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        coVerify(exactly = 0) {
            finishWorkoutSessionUseCase(workoutId = any(), startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        }
    }

    @Test
    fun `a gap under the threshold excludes nothing`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        manager.registerInteraction(now = 1L) // ненулевая база: 0L совпал бы с "ещё не было взаимодействий"
        manager.registerInteraction(now = 1L + 5 * 60 * 1000L) // 5 минут, меньше порога в 10

        assertEquals(0L, manager.excludedIdleMillis)
    }

    @Test
    fun `a gap over the threshold excludes only the excess`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L) // 45 минут простоя

        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis) // исключены только 45 - 10 = 35 минут
    }

    @Test
    fun `multiple gaps over the threshold accumulate`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 20 * 60 * 1000L) // гэп 20 мин -> исключено 10 мин
        manager.registerInteraction(now = 1L + 20 * 60 * 1000L + 30 * 60 * 1000L) // ещё гэп 30 мин -> исключено ещё 20 мин

        assertEquals(30 * 60 * 1000L, manager.excludedIdleMillis) // 10 + 20 = 30 минут суммарно
    }

    @Test
    fun `start resets excludedIdleMillis for a fresh session`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L)
        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis)

        manager.start(1) // повторный start = новая сессия

        assertEquals(0L, manager.excludedIdleMillis)
    }

    @Test
    fun `onExerciseFinished registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.onExerciseFinished()

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `skipRest registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.skipRest()

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `moveToExercise registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.moveToExercise(0)

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `updateExerciseNote registers an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        coEvery { workoutRepository.updateExerciseNote(1, "note") } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.updateExerciseNote(0, "note")

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `automatic rest completion does not register an interaction`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest, регистрирует взаимодействие
        val afterRealInteraction = manager.lastInteractionAt

        manager.onRestFinished()

        assertEquals(afterRealInteraction, manager.lastInteractionAt)
    }

    @Test
    fun `loading a workout schedules the idle reminder for the first exercise`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)

        assertTrue(manager.isIdleReminderJobActive)
    }

    @Test
    fun `moving to rest cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> переход в Rest

        assertFalse(manager.isIdleReminderJobActive)
    }

    @Test
    fun `skipping rest re-schedules the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // -> Rest
        manager.skipRest() // -> Active

        assertTrue(manager.isIdleReminderJobActive)
    }

    @Test
    fun `finishing the workout cancels the idle reminder`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val finishWorkoutSessionUseCase = mockk<FinishWorkoutSessionUseCase>()
        coEvery {
            finishWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any(), durationMillis = any(), sets = any())
        } just Runs

        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, finishWorkoutSessionUseCase)
        manager.start(1)
        manager.onExerciseFinished() // единственное упражнение, единственный подход -> Finished

        assertFalse(manager.isIdleReminderJobActive)
    }

    @Test
    fun `isLastSetOfWorkout is false on an intermediate set`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1) // подход 1 из 2

        assertFalse(manager.isLastSetOfWorkout)
    }

    @Test
    fun `isLastSetOfWorkout is false on the last set of a non-final exercise`() = runTest {
        val first = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val second = Exercise(id = 2, name = "Pull", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 2)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(first, second), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1) // единственный подход первого из двух упражнений

        assertFalse(manager.isLastSetOfWorkout)
    }

    @Test
    fun `isLastSetOfWorkout is true on the last set of the last exercise`() = runTest {
        val first = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val second = Exercise(id = 2, name = "Pull", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 2)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(first, second), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.moveToExercise(1) // единственный подход последнего упражнения

        assertTrue(manager.isLastSetOfWorkout)
    }

    @Test
    fun `isLastSetOfWorkout is false while resting`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> Rest

        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertFalse(manager.isLastSetOfWorkout)
    }

    @Test
    fun `updateExerciseWeightAndReps stores the new values`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val manager = buildManager(getWorkoutByIdUseCase, workoutRepository, mockk<FinishWorkoutSessionUseCase>())

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 8)

        coVerify(exactly = 1) {
            workoutRepository.updateExerciseWeightAndReps(exerciseId = 1, weight = 12.5, extraWeight = 0.0, reps = 8)
        }
    }

    // Плитки на экране выполнения берут числа из Active.weight/Active.reps, а не из
    // Active.exercise, поэтому правка обязана обновить и отдельные поля состояния тоже.
    @Test
    fun `updateExerciseWeightAndReps refreshes the tiles of the current exercise`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 8)

        val state = manager.phase as WorkoutExecutionState.Active
        assertEquals(12.5, state.weight, 0.0)
        assertEquals(8, state.reps)
        assertEquals(12.5, state.exercise.weight, 0.0)
        assertEquals(8, state.exercise.reps)
    }

    @Test
    fun `updateExerciseWeightAndReps refreshes the exercise shown while resting`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.onExerciseFinished() // sets=2, currentSet 1<2 -> Rest
        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 8)

        val state = manager.phase as WorkoutExecutionState.Rest
        assertEquals(12.5, state.exercise.weight, 0.0)
        assertEquals(8, state.exercise.reps)
    }

    @Test
    fun `updateExerciseWeightAndReps of another exercise reaches it only when its turn comes`() = runTest {
        val first = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val second = Exercise(id = 2, name = "Pull", weight = 20.0, sets = 1, reps = 6, timeMillis = 1_000, order = 2)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(first, second), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(
            getWorkoutByIdUseCase,
            mockk<WorkoutRepository>(relaxed = true),
            mockk<FinishWorkoutSessionUseCase>()
        )

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 1, weight = 25.0, extraWeight = 0.0, reps = 9)

        val stillFirst = manager.phase as WorkoutExecutionState.Active
        assertEquals(10.0, stillFirst.weight, 0.0)
        assertEquals(5, stillFirst.reps)

        manager.moveToExercise(1)

        val nowSecond = manager.phase as WorkoutExecutionState.Active
        assertEquals(25.0, nowSecond.weight, 0.0)
        assertEquals(9, nowSecond.reps)
    }

    private fun workoutOf(vararg exercises: Exercise) =
        Workout(id = 1, name = "Test", exercises = exercises.toList(), lastUseAt = 0L)

    private fun ex(id: Int, name: String, sets: Int, weight: Double = 50.0, reps: Int = 8) =
        Exercise(id = id, name = name, weight = weight, sets = sets, reps = reps,
            timeMillis = 1_000, order = id, catalogId = id.toLong() * 10)

    @Test
    fun `last set of the workout is recorded together with the session`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 2))
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, repo, finish)

        manager.start(1)
        manager.onExerciseFinished()   // подход 1 → отдых
        manager.skipRest()
        manager.onExerciseFinished()   // подход 2 — последний

        assertEquals(
            listOf(
                RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG),
                RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG)
            ),
            setsSlot.captured
        )
    }

    @Test
    fun `jumping to another exercise records only finished sets`() = runTest {
        val squat = ex(1, "Присед", sets = 3)
        val press = ex(2, "Жим", sets = 1)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(squat, press)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()            // присед, подход 1
        manager.moveToExercise(1)   // бросили присед
        manager.onExerciseFinished()            // жим — последнее упражнение, последний подход

        assertEquals(listOf("Присед", "Жим"), setsSlot.captured.map { it.exerciseName })
    }

    @Test
    fun `leaving without finishing writes no session`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 3))
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()

        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
        assertEquals(1, manager.recordedSets.size)
    }

    @Test
    fun `edited tiles are recorded right away even if the db write is slow`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.updateExerciseWeightAndReps(any(), any(), any(), any()) } coAnswers { gate.await() }
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, repo, finish)

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 62.5, extraWeight = 0.0, reps = 6) // запись в БД висит
        manager.onExerciseFinished()

        assertEquals(62.5, setsSlot.captured.single().weight, 0.0)
        assertEquals(6, setsSlot.captured.single().reps)
        gate.complete(Unit)
    }

    @Test
    fun `double tap on the last set gives one session and one set`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val gate = CompletableDeferred<Unit>()
        val finish = mockk<FinishWorkoutSessionUseCase>()
        coEvery { finish(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()
        manager.onExerciseFinished()   // второй тап, пока запись висит
        gate.complete(Unit)

        coVerify(exactly = 1) { finish(any(), any(), any(), any(), any()) }
        assertEquals(1, manager.recordedSets.size)
    }

    @Test
    fun `failed finish keeps sets, reports error and retry does not duplicate the last set`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()
        assertTrue(manager.finishError.value)
        assertTrue(manager.phase is WorkoutExecutionState.Active)

        manager.dismissFinishError()
        manager.onExerciseFinished()   // повтор

        assertEquals(2, attempts.size)
        assertEquals(1, attempts.last().size)
        assertTrue(manager.phase is WorkoutExecutionState.Finished)
    }

    @Test
    fun `failed finish then jumping back and finishing again keeps every tapped set`() = runTest {
        val a = ex(1, "Присед", sets = 1)
        val b = ex(2, "Жим", sets = 1)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(a, b)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()            // A → отдых перед B
        manager.skipRest()
        manager.onExerciseFinished()            // B, последний подход — запись падает
        assertTrue(manager.finishError.value)

        manager.dismissFinishError()
        manager.moveToExercise(0)       // ушли с последнего подхода
        manager.onExerciseFinished()            // A ещё раз
        manager.skipRest()
        manager.onExerciseFinished()            // B — запись проходит

        val setA = RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG)
        val setB = RecordedSet(20L, "Жим", 50.0, 0.0, 8, ExerciseUnit.KG)
        assertEquals(2, attempts.size)
        assertEquals(listOf(setA, setB), attempts.first())
        // Неудавшийся подход B остаётся в истории: он был сделан, а не потерян.
        assertEquals(listOf(setA, setB, setA, setB), attempts.last())
        assertTrue(manager.phase is WorkoutExecutionState.Finished)
    }

    @Test
    fun `failed finish then weight edit is recorded by the retry without growing the list`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.onExerciseFinished()
        manager.dismissFinishError()
        manager.updateExerciseWeightAndReps(index = 0, weight = 70.0, extraWeight = 0.0, reps = 5)
        manager.onExerciseFinished()   // повтор

        assertEquals(1, attempts.last().size)
        assertEquals(70.0, attempts.last().single().weight, 0.0)
        assertEquals(5, attempts.last().single().reps)
        assertEquals(1, manager.recordedSets.size)
    }

    @Test
    fun `plate extra weight edited on the tiles is recorded with the set`() = runTest {
        val plate = Exercise(
            id = 1, name = "Тяга блока", weight = 5.0, sets = 1, reps = 12, timeMillis = 1_000, order = 1,
            catalogId = 4L, unit = ExerciseUnit.PLATE
        )
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns Workout(id = 1, name = "Спина", exercises = listOf(plate), lastUseAt = 0L)
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)

        manager.start(1)
        manager.updateExerciseWeightAndReps(index = 0, weight = 6.0, extraWeight = 2.0, reps = 10)

        assertEquals(2.0, (manager.phase as WorkoutExecutionState.Active).extraWeight, 0.0)
        manager.onExerciseFinished()
        assertEquals(
            RecordedSet(4L, "Тяга блока", 6.0, 2.0, 10, ExerciseUnit.PLATE),
            setsSlot.captured.single()
        )
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionManagerTest'`
Expected: FAIL — компиляция: `Unresolved reference: WorkoutSessionManager`.

- [ ] **Step 5: Менеджер**

`presentation/session/WorkoutSessionManager.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import ru.hopes.workouttimer.domain.model.RecordedSet
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.domain.usecase.FinishWorkoutSessionUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.service.TimerNotificationService
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
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _session = MutableStateFlow<WorkoutSession>(WorkoutSession.None)
    val session: StateFlow<WorkoutSession> = _session.asStateFlow()

    private val _finishError = MutableStateFlow(false)
    val finishError: StateFlow<Boolean> = _finishError.asStateFlow()

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

    /** Начинает сессию тренировки и возвращает её sessionId. */
    fun start(workoutId: Int): Long {
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
                    )
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
                    )
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
        // его текущими значениями (плитку могли поправить), а не дублируем.
        if (finishPending && _recordedSets.isNotEmpty()) {
            _recordedSets[_recordedSets.lastIndex] = set
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
                    )
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
                    )
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
        setSession(current.copy(isFinishing = true))
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
                )
            )
        )
        scheduleIdleReminderIfActive()
    }

    /** Заметка: сначала БД, потом состояние — как было. */
    fun updateExerciseNote(index: Int, note: String) {
        registerInteraction()
        val row = present?.exercises?.getOrNull(index) ?: return
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
        sessionScope.launch {
            workoutRepository.updateExerciseWeightAndReps(row.exercise.id, weight, extraWeight, reps)
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
```

- [ ] **Step 6: Прогнать перенесённые тесты**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionManagerTest'`
Expected: PASS, 34 теста. Если какой-то тест падает — расхождение с поведением ViewModel: чинить менеджер, а не тест (тест — перенос один к одному).

- [ ] **Step 7: Полная проверка и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
git add app/src/main/java/ru/hopes/workouttimer/di/CoroutineScopeModule.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/session \
        app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution \
        app/src/test/java/ru/hopes/workouttimer/presentation/session \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/ActiveContentTilesTest.kt
git commit -m "refactor: логика тренировки переезжает в WorkoutSessionManager

Менеджер-синглтон в области процесса с теми же правилами, что у
WorkoutExecutionViewModel; тесты ViewModel перенесены на менеджер.
Экран пока работает через старую ViewModel.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Жизненный цикл сессии — идемпотентный `start`, `abandon`, `close`, `isRunning`, `runningWorkout`

**Files:**
- Modify: `presentation/session/WorkoutSession.kt` (признаки фазы, `RunningWorkout`, `runningWorkoutOf`)
- Modify: `presentation/session/WorkoutSessionManager.kt`
- Test: `presentation/session/WorkoutSessionTest.kt` (новый)
- Test: `presentation/session/WorkoutSessionManagerTest.kt` (новые тесты; тест `start resets excludedIdleMillis` меняется)

**Interfaces:**
- Consumes: всё из Task 1.
- Produces:
  - `internal val WorkoutExecutionState.isRunning: Boolean` — `Loading`, `Rest`, `Active`
  - `internal val WorkoutExecutionState.isInProgress: Boolean` — `Rest`, `Active`
  - `data class RunningWorkout(val workoutId: Int, val workoutName: String, val isLoading: Boolean, val addedExerciseIds: Set<Int>)`
  - `internal fun runningWorkoutOf(session: WorkoutSession): RunningWorkout?`
  - `WorkoutSessionManager`: `val runningWorkout: StateFlow<RunningWorkout?>`, `val isRunning: Boolean`, `fun abandon()`, `fun close(sessionId: Long)`; `start(workoutId)` идемпотентен — при идущей сессии (любой тренировки) ничего не делает и возвращает её `sessionId`.

- [ ] **Step 1: Падающие тесты сводки**

`app/src/test/java/ru/hopes/workouttimer/presentation/session/WorkoutSessionTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise

class WorkoutSessionTest {

    private val push = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, order = 1)

    private fun present(phase: WorkoutExecutionState, exercises: List<SessionExercise> = listOf(SessionExercise(push))) =
        WorkoutSession.Present(
            sessionId = 1L, workoutId = 7, workoutName = "Ноги",
            exercises = exercises, exerciseIndex = 0, phase = phase
        )

    @Test
    fun `тренировка идёт в Loading, Rest и Active`() {
        assertTrue(WorkoutExecutionState.Loading.isRunning)
        assertTrue(WorkoutExecutionState.Rest(push, 2).isRunning)
        assertTrue(WorkoutExecutionState.Active(push, 1).isRunning)
        assertFalse(WorkoutExecutionState.Error.isRunning)
        assertFalse(WorkoutExecutionState.Finished(1_000L).isRunning)
    }

    @Test
    fun `в процессе — только Rest и Active`() {
        assertFalse(WorkoutExecutionState.Loading.isInProgress)
        assertTrue(WorkoutExecutionState.Rest(push, 2).isInProgress)
        assertTrue(WorkoutExecutionState.Active(push, 1).isInProgress)
    }

    @Test
    fun `сводки нет без идущей сессии`() {
        assertNull(runningWorkoutOf(WorkoutSession.None))
        assertNull(runningWorkoutOf(present(WorkoutExecutionState.Error)))
        assertNull(runningWorkoutOf(present(WorkoutExecutionState.Finished(1_000L))))
    }

    @Test
    fun `сводка не зависит от остатка отдыха`() {
        val a = runningWorkoutOf(present(WorkoutExecutionState.Rest(push, 2, restTimeMillis = 50_000L)))
        val b = runningWorkoutOf(present(WorkoutExecutionState.Rest(push, 2, restTimeMillis = 49_800L)))
        assertEquals(RunningWorkout(7, "Ноги", isLoading = false, addedExerciseIds = emptySet()), a)
        assertEquals(a, b)
    }

    @Test
    fun `сводка загрузки помечена и перечисляет добавленные упражнения`() {
        assertTrue(runningWorkoutOf(present(WorkoutExecutionState.Loading))!!.isLoading)
        val added = SessionExercise(push.copy(id = 42), addedToday = true)
        val summary = runningWorkoutOf(present(WorkoutExecutionState.Active(push, 1), listOf(SessionExercise(push), added)))
        assertEquals(setOf(42), summary!!.addedExerciseIds)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionTest'`
Expected: FAIL — `Unresolved reference: isRunning` / `runningWorkoutOf`.

- [ ] **Step 2: Признаки фазы и сводка**

Дописать в конец `presentation/session/WorkoutSession.kt`:

```kotlin
/** Сессия идёт: Loading, Rest или Active. Error и Finished — уже нет. */
internal val WorkoutExecutionState.isRunning: Boolean
    get() = this is WorkoutExecutionState.Loading || isInProgress

/** Тренировка в процессе — её можно свернуть, и её нельзя закрыть мимоходом. */
internal val WorkoutExecutionState.isInProgress: Boolean
    get() = this is WorkoutExecutionState.Rest || this is WorkoutExecutionState.Active

/**
 * Сводка для списка, просмотра и плашки — без тиков таймера: меняется только при старте
 * и конце сессии и при добавлении упражнения. Подписчики, которым тики не нужны, читают её,
 * а не session (та во время отдыха меняется каждые 200 мс).
 */
data class RunningWorkout(
    val workoutId: Int,
    val workoutName: String,
    val isLoading: Boolean,
    /** exercise.id исходных строк, добавленных «+ в сегодняшнюю». */
    val addedExerciseIds: Set<Int>
)

internal fun runningWorkoutOf(session: WorkoutSession): RunningWorkout? {
    val present = session as? WorkoutSession.Present ?: return null
    if (!present.phase.isRunning) return null
    return RunningWorkout(
        workoutId = present.workoutId,
        workoutName = present.workoutName,
        isLoading = present.phase is WorkoutExecutionState.Loading,
        addedExerciseIds = present.exercises.filter { it.addedToday }.map { it.exercise.id }.toSet()
    )
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionTest'`
Expected: PASS, 5 тестов.

- [ ] **Step 3: Падающие тесты менеджера**

В `WorkoutSessionManagerTest.kt` добавить импорты:

```kotlin
import io.mockk.verify
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
```

Заменить тест `start resets excludedIdleMillis for a fresh session` (теперь повторный `start` идущей сессии ничего не сбрасывает — новая сессия начинается после выхода):

```kotlin
    @Test
    fun `start after abandon resets excludedIdleMillis for a fresh session`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val manager = buildManager(getWorkoutByIdUseCase, mockk(relaxed = true), mockk())
        manager.start(1)
        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L)
        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis)

        manager.abandon()
        manager.start(1) // новая сессия

        assertEquals(0L, manager.excludedIdleMillis)
    }
```

Дописать в конец класса (перед последней `}`):

```kotlin
    // --- Жизненный цикл сессии (спека, «Тестирование» → новые тесты менеджера) ---

    private fun twoSetWorkout(id: Int = 1, name: String = "Test") = Workout(
        id = id, name = name, lastUseAt = 0L,
        exercises = listOf(Exercise(id = id * 10, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 60_000, order = 1))
    )

    private fun managerWith(vararg workouts: Workout, finish: FinishWorkoutSessionUseCase = mockk(relaxed = true)): WorkoutSessionManager {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(any()) } returns null
        workouts.forEach { w -> coEvery { getWorkout(w.id) } returns w }
        return buildManager(getWorkout, mockk(relaxed = true), finish)
    }

    @Test
    fun `a loaded workout is running`() = runTest {
        val manager = managerWith(singleSetWorkout())
        assertFalse(manager.isRunning)

        manager.start(1)

        assertTrue(manager.isRunning)
    }

    @Test
    fun `a workout that failed to load is not running`() = runTest {
        val manager = managerWith()

        manager.start(1)

        assertTrue(manager.phase is WorkoutExecutionState.Error)
        assertFalse(manager.isRunning)
    }

    @Test
    fun `finishing the workout stops it running`() = runTest {
        val manager = managerWith(singleSetWorkout())
        manager.start(1)

        manager.onExerciseFinished()

        assertTrue(manager.phase is WorkoutExecutionState.Finished)
        assertFalse(manager.isRunning)
    }

    @Test
    fun `resting and loading count as running`() = runTest {
        val gate = CompletableDeferred<Workout?>()
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, mockk(relaxed = true), mockk(relaxed = true))

        manager.start(1)
        assertTrue(manager.phase is WorkoutExecutionState.Loading)
        assertTrue(manager.isRunning)

        gate.complete(twoSetWorkout())
        manager.onExerciseFinished()
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertTrue(manager.isRunning)
    }

    @Test
    fun `start of the same workout while running resets nothing`() = runTest {
        val manager = managerWith(twoSetWorkout())
        val id = manager.start(1)
        manager.onExerciseFinished() // подход 1 → отдых
        manager.registerInteraction(now = 1L)
        manager.registerInteraction(now = 1L + 45 * 60 * 1000L)

        val again = manager.start(1) // пересоздание активности: LaunchedEffect снова зовёт start

        assertEquals(id, again)
        assertEquals(1, manager.recordedSets.size)
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertEquals(35 * 60 * 1000L, manager.excludedIdleMillis)
    }

    @Test
    fun `start of another workout while running is ignored`() = runTest {
        val manager = managerWith(twoSetWorkout(1, "Ноги"), twoSetWorkout(2, "Спина"))
        val id = manager.start(1)

        val other = manager.start(2)

        assertEquals(id, other)
        val session = manager.session.value as WorkoutSession.Present
        assertEquals(1, session.workoutId)
        assertEquals("Ноги", session.workoutName)
    }

    @Test
    fun `start from Finished or Error begins a new session`() = runTest {
        val manager = managerWith(singleSetWorkout())
        val first = manager.start(1)
        manager.onExerciseFinished() // Finished

        val second = manager.start(1)
        assertNotEquals(first, second)
        assertTrue(manager.phase is WorkoutExecutionState.Active)

        val failing = managerWith()
        val broken = failing.start(5) // Error
        assertNotEquals(broken, failing.start(5))
    }

    @Test
    fun `abandon releases everything, writes nothing and leaves no session`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = managerWith(twoSetWorkout(), finish = finish)
        manager.start(1)
        manager.onExerciseFinished() // отдых: таймер, wakelock, уведомление
        assertTrue(manager.isRestTimerActive)

        manager.abandon()

        assertEquals(WorkoutSession.None, manager.session.value)
        assertFalse(manager.isRestTimerActive)
        assertFalse(manager.isIdleReminderJobActive)
        assertTrue(manager.recordedSets.isEmpty())
        assertFalse(manager.finishError.value)
        verify { wakeLockHelper.release() }
        verify { soundPlayer.release() }
        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `close does nothing while the workout is in progress`() = runTest {
        val manager = managerWith(twoSetWorkout())
        val id = manager.start(1)

        manager.close(id) // Active
        assertTrue(manager.phase is WorkoutExecutionState.Active)

        manager.onExerciseFinished()
        manager.close(id) // Rest
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
    }

    @Test
    fun `close ends a finished or failed session`() = runTest {
        val manager = managerWith(singleSetWorkout())
        val finishedId = manager.start(1)
        manager.onExerciseFinished()
        manager.close(finishedId)
        assertEquals(WorkoutSession.None, manager.session.value)

        val failing = managerWith()
        val errorId = failing.start(5)
        failing.close(errorId)
        assertEquals(WorkoutSession.None, failing.session.value)
    }

    @Test
    fun `close during loading cancels the load`() = runTest {
        val gate = CompletableDeferred<Workout?>()
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, mockk(relaxed = true), mockk(relaxed = true))
        val id = manager.start(1)

        manager.close(id)
        gate.complete(singleSetWorkout())

        assertEquals(WorkoutSession.None, manager.session.value)
    }

    @Test
    fun `close with a stale sessionId leaves the new session alone`() = runTest {
        val manager = managerWith(singleSetWorkout())
        val old = manager.start(1)
        manager.onExerciseFinished() // Finished
        val fresh = manager.start(1)

        manager.close(old) // onCleared старой записи навигации приходит позже
        assertEquals(fresh, (manager.session.value as WorkoutSession.Present).sessionId)

        val failing = managerWith()
        val staleError = failing.start(5)
        val freshError = failing.start(5)
        failing.close(staleError)
        assertEquals(freshError, (failing.session.value as WorkoutSession.Present).sessionId)
    }

    @Test
    fun `abandon during a slow note write cancels the write`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns twoSetWorkout()
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.updateExerciseNote(any(), any()) } coAnswers { gate.await() }
        val manager = buildManager(getWorkout, repo, mockk(relaxed = true))
        manager.start(1)
        manager.updateExerciseNote(0, "колени наружу") // запись висит

        manager.abandon()
        manager.start(1)
        gate.complete(Unit)

        val session = manager.session.value as WorkoutSession.Present
        assertEquals("", session.exercises[0].exercise.note)
    }

    @Test
    fun `abandon while the finish is being written keeps the session closed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val finish = mockk<FinishWorkoutSessionUseCase>()
        coEvery { finish(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns singleSetWorkout()
        val manager = buildManager(getWorkout, repo, finish)
        manager.start(1)
        manager.onExerciseFinished() // запись висит, isFinishing

        manager.abandon()
        gate.complete(Unit)

        assertEquals(WorkoutSession.None, manager.session.value)
        assertFalse(manager.finishError.value)
        coVerify(exactly = 1) { finish(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { repo.updateLastUseAt(1) }
    }

    @Test
    fun `running summary follows start and end but not rest ticks`() = runTest {
        val manager = managerWith(twoSetWorkout(1, "Ноги"))
        assertNull(manager.runningWorkout.value)

        manager.start(1)
        assertEquals(RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = emptySet()), manager.runningWorkout.value)

        manager.onExerciseFinished() // отдых, таймер тикает каждые 200 мс
        val resting = manager.runningWorkout.value
        testScheduler.advanceTimeBy(1_000)
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertSame(resting, manager.runningWorkout.value)

        manager.abandon()
        assertNull(manager.runningWorkout.value)
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionManagerTest'`
Expected: FAIL — компиляция: `Unresolved reference: isRunning`, `abandon`, `close`, `runningWorkout`.

- [ ] **Step 4: Реализация в менеджере**

В `WorkoutSessionManager.kt`:

1. Импорты: добавить `import kotlinx.coroutines.cancel`.

2. После `val finishError …` добавить сводку и признак:

```kotlin
    private val _runningWorkout = MutableStateFlow<RunningWorkout?>(null)

    /** Сводка без тиков: MutableStateFlow не публикует равное значение. */
    val runningWorkout: StateFlow<RunningWorkout?> = _runningWorkout.asStateFlow()

    /** Сессия идёт: Loading, Rest или Active. Error, Finished и None — не идёт. */
    val isRunning: Boolean
        get() = present?.phase?.isRunning == true
```

3. Заменить начало `start()` — от `fun start(workoutId: Int): Long {` до строки `val sessionId = ++lastSessionId` включительно — на:

```kotlin
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
```

4. После `start()` добавить выход и закрытие:

```kotlin
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
    // Уже показанные «Отдых завершён» и «Вы всё ещё тренируетесь?» не снимаются — как раньше.
    private fun endSession() {
        resetSessionScope()
        wakeLockHelper.release()
        stopNotification()
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
```

5. `setSession` пересчитывает сводку:

```kotlin
    private fun setSession(value: WorkoutSession) {
        _session.value = value
        _runningWorkout.value = runningWorkoutOf(value)
    }
```

- [ ] **Step 5: Тесты зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.*'`
Expected: PASS — `WorkoutSessionManagerTest` 49 тестов, `WorkoutSessionTest` 5.

- [ ] **Step 6: Полная проверка и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
git add app/src/main/java/ru/hopes/workouttimer/presentation/session \
        app/src/test/java/ru/hopes/workouttimer/presentation/session
git commit -m "feat: жизненный цикл сессии — идемпотентный start, abandon, close

start() не сбрасывает идущую тренировку (пересоздание активности),
abandon() и close(sessionId) отменяют область сессии, isRunning и
сводка runningWorkout без тиков таймера.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Тонкая ViewModel экрана выполнения, `MainActivity` на менеджере, удаление трекера

Конец M1. Поведение для пользователя прежнее: «Назад» спрашивает подтверждение, уход с экрана выполнения завершает тренировку без сохранения (`onCleared` → `abandon()`). Видимые отличия из спеки: пересоздание активности больше не сбрасывает тренировку; сбой записи заметки или веса не роняет приложение.

**Files:**
- Modify (переписать целиком): `presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- Modify: `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` (функция `WorkoutExecutionScreen`, строки 70–354)
- Modify: `presentation/MainActivity.kt`
- Delete: `presentation/utils/ActiveWorkoutTracker.kt`, `app/src/test/.../presentation/utils/ActiveWorkoutTrackerTest.kt`
- Test (переписать целиком): `presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`

**Interfaces:**
- Consumes: `WorkoutSessionManager` (Task 1–2): `session`, `finishError`, `isLastSetOfWorkout`, `isRunning`, `start`, `skipRest`, `onExerciseFinished`, `moveToExercise`, `updateExerciseNote`, `updateExerciseWeightAndReps`, `dismissFinishError`, `abandon`, `close`.
- Produces:
  - `data class ExecutionChrome(val workoutName: String = "", val exercises: List<SessionExercise> = emptyList(), val exerciseIndex: Int = 0, val isFinishing: Boolean = false)` с `currentExerciseNumber`, `totalExercises`
  - `WorkoutExecutionViewModel(manager)`: `uiState: StateFlow<WorkoutExecutionState>`, `chrome: StateFlow<ExecutionChrome>`, `finishError`, `isLastSetOfWorkout`, `start(workoutId)`, `skipRest()`, `onExerciseFinished()`, `moveToExercise(index)`, `updateExerciseNote(index, note)`, `updateExerciseWeightAndReps(index, weight, extraWeight, reps)`, `dismissFinishError()`
  - `MainActivity.sessionManager: WorkoutSessionManager` (`@Inject lateinit var`)

- [ ] **Step 1: Падающие тесты тонкой ViewModel**

`app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt` — заменить содержимое целиком (старые тесты уже перенесены в `WorkoutSessionManagerTest`):

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.session.SessionExercise
import ru.hopes.workouttimer.presentation.session.WorkoutExecutionState
import ru.hopes.workouttimer.presentation.session.WorkoutSession
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutExecutionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val session = MutableStateFlow<WorkoutSession>(WorkoutSession.None)
    private val manager = mockk<WorkoutSessionManager>(relaxed = true)
    private val store = ViewModelStore()

    private val push = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, order = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { manager.session } returns session
        every { manager.finishError } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Через ViewModelStore: store.clear() вызывает настоящий onCleared().
    private fun viewModel(): WorkoutExecutionViewModel = ViewModelProvider.create(
        store,
        viewModelFactory { initializer { WorkoutExecutionViewModel(manager) } }
    )[WorkoutExecutionViewModel::class]

    private fun present(id: Long, phase: WorkoutExecutionState) = WorkoutSession.Present(
        sessionId = id, workoutId = 1, workoutName = "Ноги",
        exercises = listOf(SessionExercise(push)), exerciseIndex = 0, phase = phase
    )

    @Test
    fun `leaving before start closes nothing`() {
        viewModel()

        store.clear()

        verify(exactly = 0) { manager.close(any()) }
        verify(exactly = 0) { manager.abandon() }
    }

    @Test
    fun `leaving a finished session closes the session this screen started`() {
        every { manager.start(1) } returns 7L
        val vm = viewModel()
        vm.start(1)
        session.value = present(7L, WorkoutExecutionState.Finished(1_000L))

        store.clear()

        verify { manager.close(7L) }
        verify(exactly = 0) { manager.abandon() }
    }

    @Test
    fun `leaving a running session abandons it`() {
        every { manager.start(1) } returns 7L
        every { manager.isRunning } returns true
        val vm = viewModel()
        vm.start(1)
        session.value = present(7L, WorkoutExecutionState.Active(push, 1))

        store.clear()

        verify { manager.abandon() }
        verify(exactly = 0) { manager.close(any()) }
    }

    @Test
    fun `retry after an error owns the new session`() {
        every { manager.start(1) } returnsMany listOf(7L, 8L)
        val vm = viewModel()
        vm.start(1)
        vm.start(1) // «Повторить»
        session.value = present(8L, WorkoutExecutionState.Error)

        store.clear()

        verify { manager.close(8L) }
        verify(exactly = 0) { manager.close(7L) }
    }

    @Test
    fun `ui state starts from the current snapshot`() {
        session.value = present(3L, WorkoutExecutionState.Rest(push, 2))

        val vm = viewModel()

        assertTrue(vm.uiState.value is WorkoutExecutionState.Rest)
    }

    @Test
    fun `ui state follows the manager and keeps the last phase when the session ends`() {
        val vm = viewModel()
        assertEquals(WorkoutExecutionState.Loading, vm.uiState.value)

        session.value = present(3L, WorkoutExecutionState.Active(push, 1))
        assertTrue(vm.uiState.value is WorkoutExecutionState.Active)

        session.value = WorkoutSession.None
        assertTrue(vm.uiState.value is WorkoutExecutionState.Active)
    }

    @Test
    fun `chrome shows the session name and position`() {
        session.value = present(3L, WorkoutExecutionState.Active(push, 1))

        val chrome = viewModel().chrome.value

        assertEquals("Ноги", chrome.workoutName)
        assertEquals(1, chrome.currentExerciseNumber)
        assertEquals(1, chrome.totalExercises)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest'`
Expected: FAIL — компиляция: у `WorkoutExecutionViewModel` нет конструктора `(manager)`, нет `chrome`.

- [ ] **Step 2: Тонкая ViewModel**

`presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt` — заменить содержимое целиком:

```kotlin
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

    /** «Повторить» после ошибки тоже идёт сюда — владение переходит к новой сессии. */
    fun start(workoutId: Int) {
        ownedSessionId = manager.start(workoutId)
    }

    fun skipRest() = manager.skipRest()

    fun onExerciseFinished() = manager.onExerciseFinished()

    fun moveToExercise(index: Int) = manager.moveToExercise(index)

    fun updateExerciseNote(index: Int, note: String) = manager.updateExerciseNote(index, note)

    fun updateExerciseWeightAndReps(index: Int, weight: Double, extraWeight: Double, reps: Int) =
        manager.updateExerciseWeightAndReps(index, weight, extraWeight, reps)

    fun dismissFinishError() = manager.dismissFinishError()

    override fun onCleared() {
        super.onCleared()
        val id = ownedSessionId ?: return
        // Сворачивания ещё нет: уход с экрана идущей тренировки — это выход без сохранения.
        val current = manager.session.value as? WorkoutSession.Present
        if (current?.sessionId == id && manager.isRunning) {
            manager.abandon()
        } else {
            manager.close(id)
        }
    }
}

/** Всё, что экрану выполнения нужно помимо фазы. */
data class ExecutionChrome(
    val workoutName: String = "",
    val exercises: List<SessionExercise> = emptyList(),
    val exerciseIndex: Int = 0,
    val isFinishing: Boolean = false
) {
    val currentExerciseNumber: Int get() = exerciseIndex + 1
    val totalExercises: Int get() = exercises.size
}

private fun WorkoutSession.Present.toChrome() = ExecutionChrome(
    workoutName = workoutName,
    exercises = exercises,
    exerciseIndex = exerciseIndex,
    isFinishing = isFinishing
)
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest'`
Expected: компиляция `main` падает на `WorkoutExecutionScreen.kt` (`loadWorkout`, `exercises`, `workoutName`…) — это Step 3.

- [ ] **Step 3: Экран на индексах и снимке**

В `WorkoutExecutionScreen.kt` заменить функцию `WorkoutExecutionScreen` (от `@Composable\nfun WorkoutExecutionScreen(` до её закрывающей `}` перед `@Composable\nprivate fun ExerciseChip`) на:

```kotlin
@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onExerciseCompleted: () -> Unit,
    workoutId: Int
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome by viewModel.chrome.collectAsState()

    // Диалог заметки и шторка веса запоминают позицию упражнения в сессии, а не сам
    // Exercise: значения полей берутся из текущего снимка. Открываются они только для
    // текущего упражнения (в Rest — упражнения следующего подхода).
    var noteIndex by remember { mutableStateOf<Int?>(null) }
    var weightSheetIndex by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }

    // Уходить без подтверждения нечего терять только в Loading/Error/Finished:
    // в Finished сессия уже сохранена, в остальных двух её ещё нет.
    val hasUnsavedProgress =
        uiState is WorkoutExecutionState.Active || uiState is WorkoutExecutionState.Rest

    // start() идемпотентен: при пересоздании активности идущая сессия не сбрасывается.
    LaunchedEffect(workoutId) {
        viewModel.start(workoutId)
    }

    BackHandler(enabled = hasUnsavedProgress) { showExitDialog = true }

    val snackbarHostState = remember { SnackbarHostState() }
    val finishError by viewModel.finishError.collectAsState()
    val finishErrorText = stringResource(R.string.execution_finish_error)
    LaunchedEffect(finishError) {
        if (finishError) {
            snackbarHostState.showSnackbar(finishErrorText)
            viewModel.dismissFinishError()
        }
    }

    val finishedState = uiState as? WorkoutExecutionState.Finished
    if (finishedState != null) {
        FinishedContent(
            workoutName = chrome.workoutName,
            durationMillis = finishedState.durationMillis,
            onDone = onExerciseCompleted
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val currentState = uiState
            val currentExercise = when (currentState) {
                is WorkoutExecutionState.Active -> currentState.exercise
                is WorkoutExecutionState.Rest -> currentState.exercise
                else -> null
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (hasUnsavedProgress) showExitDialog = true else onExerciseCompleted()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (currentExercise != null) {
                        ExerciseChip(
                            name = currentExercise.name,
                            position = chrome.currentExerciseNumber,
                            total = chrome.totalExercises,
                            onClick = { showExercisePicker = true }
                        )
                    } else {
                        Text(
                            text = chrome.workoutName.ifEmpty { stringResource(R.string.execution_workout_fallback) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Box(modifier = Modifier.size(48.dp))
            }

            if (currentExercise != null && chrome.totalExercises > 0) {
                ProgressSegments(
                    total = chrome.totalExercises,
                    currentIndex = chrome.exerciseIndex,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentState) {
                    is WorkoutExecutionState.Loading -> LoadingContent()

                    is WorkoutExecutionState.Error -> EmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = stringResource(R.string.execution_load_error_title),
                        subtitle = stringResource(R.string.execution_load_error_subtitle),
                        actionText = stringResource(R.string.execution_retry),
                        onAction = { viewModel.start(workoutId) }
                    )

                    is WorkoutExecutionState.Active -> ActiveContent(
                        state = currentState,
                        onEditNote = { noteIndex = chrome.exerciseIndex },
                        onEditWeightAndReps = { weightSheetIndex = chrome.exerciseIndex }
                    )

                    is WorkoutExecutionState.Rest -> RestContent(
                        state = currentState,
                        onEditNote = { noteIndex = chrome.exerciseIndex },
                        onEditWeightAndReps = { weightSheetIndex = chrome.exerciseIndex }
                    )

                    is WorkoutExecutionState.Finished -> Unit
                }
            }

            if (currentState is WorkoutExecutionState.Active ||
                currentState is WorkoutExecutionState.Rest
            ) {
                MusicSection(
                    isResting = currentState is WorkoutExecutionState.Rest,
                    modifier = Modifier.padding(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        bottom = 10.dp
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ScreenPadding, end = ScreenPadding, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentState is WorkoutExecutionState.Active) {
                        PrimaryButton(
                            text = stringResource(R.string.execution_finish_set),
                            onClick = {
                                if (viewModel.isLastSetOfWorkout) {
                                    showFinishDialog = true
                                } else {
                                    viewModel.onExerciseFinished()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.execution_skip_rest),
                            onClick = { viewModel.skipRest() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showExercisePicker) {
        AppBottomSheet(onDismiss = { showExercisePicker = false }) {
            Text(
                text = stringResource(R.string.execution_exercises),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
            )
            chrome.exercises.forEachIndexed { index, row ->
                val isCurrent = index == chrome.exerciseIndex
                val isDone = index < chrome.exerciseIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.moveToExercise(index)
                            showExercisePicker = false
                        }
                        .padding(horizontal = ScreenPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = row.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    val weightSheetExercise = weightSheetIndex?.let { chrome.exercises.getOrNull(it)?.exercise }
    if (weightSheetIndex != null && weightSheetExercise != null) {
        val index = weightSheetIndex ?: 0
        AppBottomSheet(onDismiss = { weightSheetIndex = null }) {
            WeightRepsSheetContent(
                exerciseName = weightSheetExercise.name,
                unit = weightSheetExercise.unit,
                weight = weightSheetExercise.weight,
                extraWeight = weightSheetExercise.extraWeight,
                reps = weightSheetExercise.reps,
                onApply = { weight, extraWeight, reps ->
                    viewModel.updateExerciseWeightAndReps(index, weight, extraWeight, reps)
                    weightSheetIndex = null
                }
            )
        }
    }

    val noteExercise = noteIndex?.let { chrome.exercises.getOrNull(it)?.exercise }
    if (noteIndex != null && noteExercise != null) {
        val index = noteIndex ?: 0
        NoteEditDialog(
            exercise = noteExercise,
            onDismiss = { noteIndex = null },
            onSave = { note ->
                viewModel.updateExerciseNote(index, note)
                noteIndex = null
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.execution_exit_title)) },
            text = { Text(stringResource(R.string.execution_exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExerciseCompleted()
                }) { Text(stringResource(R.string.common_exit)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.execution_finish_title)) },
            text = { Text(stringResource(R.string.execution_finish_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    viewModel.onExerciseFinished()
                }) { Text(stringResource(R.string.execution_finish_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
```

Остальные функции файла (`ExerciseChip`, `ActiveContent`, `RestContent`, `NoteBlock`, `FinishedContent`, `LoadingContent`, `NoteEditDialog`, превью) не меняются; сигнатуры `onEditNote: (Exercise) -> Unit` у `ActiveContent`/`RestContent` остаются (их зовёт `ActiveContentTilesTest`), экран просто не использует аргумент.

- [ ] **Step 4: `MainActivity` на менеджере, трекер удалить**

В `presentation/MainActivity.kt`:
- импорт `ru.hopes.workouttimer.presentation.utils.ActiveWorkoutTracker` заменить на `ru.hopes.workouttimer.presentation.session.WorkoutSessionManager`;
- поле:

```kotlin
    @Inject
    lateinit var sessionManager: WorkoutSessionManager
```

  вместо `lateinit var activeWorkoutTracker: ActiveWorkoutTracker`;
- в `onNewIntent` строку `if (activeWorkoutTracker.isActive) return` заменить на `if (sessionManager.isRunning) return` (комментарий над ней не меняется).

```bash
git rm app/src/main/java/ru/hopes/workouttimer/presentation/utils/ActiveWorkoutTracker.kt \
       app/src/test/java/ru/hopes/workouttimer/presentation/utils/ActiveWorkoutTrackerTest.kt
grep -rn "ActiveWorkoutTracker\|activeWorkoutTracker" app/src   # пусто
```

Комментарий в `NavGraph.kt:32-37` («…только когда тренировка не идёт (иначе MainActivity его отбрасывает)…») остаётся верным; его перепишет Task 6.

- [ ] **Step 5: Тесты зелёные**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workoutExecution.*' --tests 'ru.hopes.workouttimer.presentation.session.*'`
Expected: PASS — `WorkoutExecutionViewModelTest` 7, `WorkoutSessionManagerTest` 49, `WorkoutSessionTest` 5.

- [ ] **Step 6: Полная проверка, инструментальный тест плиток и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.workoutExecution.ActiveContentTilesTest
git add -A app/src/main/java/ru/hopes/workouttimer/presentation \
           app/src/test/java/ru/hopes/workouttimer/presentation
git commit -m "refactor: экран выполнения — тонкий адаптер над менеджером сессии

WorkoutExecutionViewModel только пробрасывает действия и решает судьбу
сессии в onCleared; экран работает с индексами упражнений. MainActivity
смотрит на isRunning менеджера, ActiveWorkoutTracker удалён.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: «Отдых закончился» и плашка (`MiniWorkoutBar`, без подключения к навигации)

Начало M2. Плашка собирается и тестируется отдельно; в корень навигации её ставит Task 5.

**Files:**
- Modify: `presentation/session/WorkoutSession.kt` (`Present.restOver`)
- Modify: `presentation/session/WorkoutSessionManager.kt` (`restOver` поднимается и опускается)
- Create: `presentation/session/MiniBarState.kt`
- Create: `presentation/session/MiniWorkoutBarViewModel.kt`
- Create: `presentation/session/MiniWorkoutBar.kt`
- Modify: `app/src/main/res/values/strings.xml` (секция «Плашка свёрнутой тренировки»)
- Test: `presentation/session/WorkoutSessionManagerTest.kt` (тесты `restOver`)
- Test: `presentation/session/MiniBarStateTest.kt` (новый)
- Test: `presentation/session/MiniWorkoutBarViewModelTest.kt` (новый)
- Test (androidTest): `presentation/session/MiniWorkoutBarContentTest.kt` (новый)

**Interfaces:**
- Consumes: `WorkoutSession`, `WorkoutSessionManager.session/runningWorkout/registerInteraction/abandon` (Task 1–2), `DateFormatter.formatDurationCompact(millis: Long): String`.
- Produces:
  - `WorkoutSession.Present.restOver: Boolean = false` — `Active` наступил сам по истечении отдыха
  - `data class MiniBarState(val workoutName: String, val status: MiniBarStatus)`; `sealed interface MiniBarStatus { Resting(timeLeftMillis: Long, totalMillis: Long); Working(currentSet: Int, totalSets: Int, exerciseName: String); RestOver(nextSet: Int) }`
  - `fun miniBarStateOf(session: WorkoutSession): MiniBarState?`, `fun ringFraction(status: MiniBarStatus): Float`
  - `MiniWorkoutBarViewModel(manager)`: `barState: StateFlow<MiniBarState?>`, `visible: StateFlow<Boolean>`, `fun abandon()`, `fun prepareReturn(): Int?`
  - `@Composable fun MiniWorkoutBar(viewModel: MiniWorkoutBarViewModel, onOpen: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun MiniWorkoutBarContent(state: MiniBarState, onOpen: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier)`
  - строки `minibar_rest`, `minibar_set`, `minibar_rest_over`, `minibar_open`, `minibar_exit`

- [ ] **Step 1: Падающие тесты `restOver` в менеджере**

Дописать в конец `WorkoutSessionManagerTest`:

```kotlin
    // --- «Пора: подход N» на плашке ---

    private val WorkoutSessionManager.restOver: Boolean
        get() = (session.value as WorkoutSession.Present).restOver

    @Test
    fun `rest that runs out by itself raises restOver`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished() // отдых
        assertFalse(manager.restOver)

        manager.onRestFinished()

        assertTrue(manager.phase is WorkoutExecutionState.Active)
        assertTrue(manager.restOver)
    }

    @Test
    fun `skipping rest does not raise restOver`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()

        manager.skipRest()

        assertFalse(manager.restOver)
    }

    @Test
    fun `finishing a set or jumping to an exercise lowers restOver`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished()
        manager.onRestFinished()
        assertTrue(manager.restOver)

        manager.onExerciseFinished() // последний подход → Finished
        assertFalse(manager.restOver)

        val other = managerWith(twoSetWorkout())
        other.start(1)
        other.onExerciseFinished()
        other.onRestFinished()
        other.moveToExercise(0)
        assertFalse(other.restOver)
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionManagerTest'`
Expected: FAIL — `Unresolved reference: restOver`.

- [ ] **Step 2: `restOver` в снимке и менеджере**

В `WorkoutSession.kt` в `Present` после `isFinishing` добавить поле:

```kotlin
        val isFinishing: Boolean = false,
        /** Active наступил сам по истечении отдыха: плашка пишет «Пора: подход N». */
        val restOver: Boolean = false
```

(запятая после `isFinishing: Boolean = false` — новая).

В `WorkoutSessionManager.kt`:

1. `skipRest()` — в `current.copy(phase = WorkoutExecutionState.Active(…))` добавить `restOver = false` (после `phase = …`):

```kotlin
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
```

2. `onRestFinished()` — переход `Rest → Active` поднимает флаг:

```kotlin
                current.copy(
                    phase = WorkoutExecutionState.Active(
                        exercise = phase.exercise,
                        currentSet = phase.currentSet,
                        totalSets = phase.totalSets
                    ),
                    restOver = true
                )
```

3. `onExerciseFinished()` — в `setSession(current.copy(phase = WorkoutExecutionState.Rest(…)))` добавить `restOver = false` вторым аргументом `copy`.

4. `moveToNextExercise()` — в `current.copy(exerciseIndex = nextIndex, phase = …)` добавить `restOver = false`.

5. `finishSession()` — `setSession(current.copy(isFinishing = true, restOver = false))`.

6. `moveToExercise()` — в `current.copy(exerciseIndex = index, phase = …)` добавить `restOver = false`.

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionManagerTest'`
Expected: PASS, 52 теста.

- [ ] **Step 3: Падающие тесты состояния плашки**

`app/src/test/java/ru/hopes/workouttimer/presentation/session/MiniBarStateTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise

class MiniBarStateTest {

    private val press = Exercise(id = 1, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, timeMillis = 120_000L, order = 1)

    private fun present(phase: WorkoutExecutionState, restOver: Boolean = false) = WorkoutSession.Present(
        sessionId = 1L, workoutId = 3, workoutName = "Ноги",
        exercises = listOf(SessionExercise(press)), exerciseIndex = 0, phase = phase, restOver = restOver
    )

    @Test
    fun `плашки нет без сессии в отдыхе или подходе`() {
        assertNull(miniBarStateOf(WorkoutSession.None))
        assertNull(miniBarStateOf(present(WorkoutExecutionState.Loading)))
        assertNull(miniBarStateOf(present(WorkoutExecutionState.Error)))
        assertNull(miniBarStateOf(present(WorkoutExecutionState.Finished(1_000L))))
    }

    @Test
    fun `отдых — остаток и полное время`() {
        val state = miniBarStateOf(present(WorkoutExecutionState.Rest(press, 2, restTimeMillis = 78_000L)))
        assertEquals(MiniBarState("Ноги", MiniBarStatus.Resting(78_000L, 120_000L)), state)
    }

    @Test
    fun `подход — номер, число подходов и упражнение`() {
        val state = miniBarStateOf(present(WorkoutExecutionState.Active(press, 2)))
        assertEquals(MiniBarState("Ноги", MiniBarStatus.Working(2, 4, "Жим лёжа")), state)
    }

    @Test
    fun `отдых закончился сам — пора делать подход`() {
        val state = miniBarStateOf(present(WorkoutExecutionState.Active(press, 2), restOver = true))
        assertEquals(MiniBarState("Ноги", MiniBarStatus.RestOver(2)), state)
    }

    @Test
    fun `кольцо — доля оставшегося отдыха, пустое в подходе, полное после отдыха`() {
        assertEquals(0.5f, ringFraction(MiniBarStatus.Resting(60_000L, 120_000L)), 0.0001f)
        assertEquals(0f, ringFraction(MiniBarStatus.Resting(10_000L, 0L)), 0.0001f)
        assertEquals(1f, ringFraction(MiniBarStatus.Resting(130_000L, 120_000L)), 0.0001f)
        assertEquals(0f, ringFraction(MiniBarStatus.Working(1, 4, "Жим лёжа")), 0.0001f)
        assertEquals(1f, ringFraction(MiniBarStatus.RestOver(2)), 0.0001f)
    }
}
```

`app/src/test/java/ru/hopes/workouttimer/presentation/session/MiniWorkoutBarViewModelTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise

@OptIn(ExperimentalCoroutinesApi::class)
class MiniWorkoutBarViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val session = MutableStateFlow<WorkoutSession>(WorkoutSession.None)
    private val running = MutableStateFlow<RunningWorkout?>(null)
    private val manager = mockk<WorkoutSessionManager>(relaxed = true)
    private val press = Exercise(id = 1, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, timeMillis = 120_000L, order = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { manager.session } returns session
        every { manager.runningWorkout } returns running
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun resting(left: Long) = WorkoutSession.Present(
        sessionId = 1L, workoutId = 3, workoutName = "Ноги", exercises = listOf(SessionExercise(press)),
        exerciseIndex = 0, phase = WorkoutExecutionState.Rest(press, 2, restTimeMillis = left)
    )

    @Test
    fun `плашка повторяет сессию`() {
        val vm = MiniWorkoutBarViewModel(manager)
        assertNull(vm.barState.value)
        assertFalse(vm.visible.value)

        session.value = resting(78_000L)

        assertEquals(MiniBarStatus.Resting(78_000L, 120_000L), vm.barState.value?.status)
        assertTrue(vm.visible.value)
    }

    @Test
    fun `тики отдыха не меняют видимость`() = runTest {
        session.value = resting(78_000L)
        val vm = MiniWorkoutBarViewModel(manager)
        val seen = mutableListOf<Boolean>()
        val job = launch(dispatcher) { vm.visible.collect { seen += it } }

        session.value = resting(77_800L)
        session.value = resting(77_600L)

        assertEquals(listOf(true), seen)
        job.cancel()
    }

    @Test
    fun `возврат засчитывает действие и отдаёт идущую тренировку`() {
        running.value = RunningWorkout(3, "Ноги", isLoading = false, addedExerciseIds = emptySet())
        val vm = MiniWorkoutBarViewModel(manager)

        assertEquals(3, vm.prepareReturn())
        verify { manager.registerInteraction(any()) }
    }

    @Test
    fun `без сессии возвращаться некуда`() {
        val vm = MiniWorkoutBarViewModel(manager)

        assertNull(vm.prepareReturn())
        verify(exactly = 0) { manager.registerInteraction(any()) }
    }

    @Test
    fun `крестик выходит без сохранения`() {
        MiniWorkoutBarViewModel(manager).abandon()

        verify { manager.abandon() }
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.MiniBarStateTest' --tests 'ru.hopes.workouttimer.presentation.session.MiniWorkoutBarViewModelTest'`
Expected: FAIL — `Unresolved reference: miniBarStateOf`, `MiniWorkoutBarViewModel`.

- [ ] **Step 4: Состояние плашки и её ViewModel**

`presentation/session/MiniBarState.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

/** Что показывает плашка свёрнутой тренировки. */
data class MiniBarState(
    val workoutName: String,
    val status: MiniBarStatus
)

sealed interface MiniBarStatus {
    /** Идёт отдых: «Отдых 01:18», кольцо — доля оставшегося времени. */
    data class Resting(val timeLeftMillis: Long, val totalMillis: Long) : MiniBarStatus

    /** Идёт подход: «Подход 2 из 4 · Жим лёжа». */
    data class Working(val currentSet: Int, val totalSets: Int, val exerciseName: String) : MiniBarStatus

    /** Отдых закончился сам: «Пора: подход 2». */
    data class RestOver(val nextSet: Int) : MiniBarStatus
}

/** Плашка есть только в Rest и Active: в Loading, Error и Finished тренировка не идёт. */
fun miniBarStateOf(session: WorkoutSession): MiniBarState? {
    val present = session as? WorkoutSession.Present ?: return null
    val status = when (val phase = present.phase) {
        is WorkoutExecutionState.Rest ->
            MiniBarStatus.Resting(phase.restTimeMillis, phase.totalRestTimeMillis)
        is WorkoutExecutionState.Active ->
            if (present.restOver) {
                MiniBarStatus.RestOver(phase.currentSet)
            } else {
                MiniBarStatus.Working(phase.currentSet, phase.totalSets, phase.exercise.name)
            }
        else -> return null
    }
    return MiniBarState(present.workoutName, status)
}

/** Заполненная доля кольца: остаток отдыха, пусто в подходе, полное — «пора». */
fun ringFraction(status: MiniBarStatus): Float = when (status) {
    is MiniBarStatus.Resting ->
        if (status.totalMillis > 0L) {
            (status.timeLeftMillis.toFloat() / status.totalMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    is MiniBarStatus.Working -> 0f
    is MiniBarStatus.RestOver -> 1f
}
```

`presentation/session/MiniWorkoutBarViewModel.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Плашка и возврат в тренировку для корня навигации (область — активность). */
@HiltViewModel
class MiniWorkoutBarViewModel @Inject constructor(
    private val manager: WorkoutSessionManager
) : ViewModel() {

    /** Во время отдыха меняется 5 раз в секунду — читает только сама плашка. */
    val barState: StateFlow<MiniBarState?> = manager.session
        .map { miniBarStateOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, miniBarStateOf(manager.session.value))

    /** Корню навигации — только этот признак: тики его не меняют и NavHost не перерисовывают. */
    val visible: StateFlow<Boolean> = barState
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, barState.value != null)

    fun abandon() = manager.abandon()

    /**
     * Возврат в тренировку — тап по плашке, «Вернуться» на просмотре и в списке, виджет,
     * уведомление. Возврат считается действием для учёта простоя. null — сессии нет.
     */
    fun prepareReturn(): Int? {
        val running = manager.runningWorkout.value ?: return null
        manager.registerInteraction()
        return running.workoutId
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.*'`
Expected: PASS — `MiniBarStateTest` 5, `MiniWorkoutBarViewModelTest` 5, остальные классы пакета без изменений.

- [ ] **Step 5: Строки плашки**

В `strings.xml` после строки `<string name="execution_note_placeholder" tools:ignore="TypographyEllipsis">Введите заметку...</string>` вставить:

```xml

    <!-- Плашка свёрнутой тренировки -->
    <string name="minibar_rest">Отдых %1$s</string>
    <string name="minibar_set">Подход %1$d из %2$d · %3$s</string>
    <string name="minibar_rest_over">Пора: подход %1$d</string>
    <string name="minibar_open">Вернуться к тренировке</string>
    <string name="minibar_exit">Выйти из тренировки без сохранения</string>
```

- [ ] **Step 6: Падающий Compose-тест плашки**

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/session/MiniWorkoutBarContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class MiniWorkoutBarContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var opened = 0
    private var exited = 0

    private fun show(status: MiniBarStatus) = composeRule.setContent {
        WorkoutTimerTheme {
            MiniWorkoutBarContent(
                state = MiniBarState("Ноги", status),
                onOpen = { opened++ },
                onExit = { exited++ }
            )
        }
    }

    // В compose-ui-test есть только assertTouchHeightIsEqualTo; «не меньше» — по границам
    // зоны нажатия узла (они уже учитывают минимальный размер касания).
    private fun SemanticsNodeInteraction.assertTouchHeightAtLeast48() {
        val height = with(composeRule.density) { fetchSemanticsNode().touchBoundsInRoot.height.toDp() }
        assertTrue("высота зоны нажатия $height < 48.dp", height >= 48.dp)
    }

    @Test
    fun отдых_показывает_отсчёт() {
        show(MiniBarStatus.Resting(78_000L, 120_000L))

        composeRule.onNodeWithText("Отдых 01:18").assertIsDisplayed()
    }

    @Test
    fun подход_показывает_номер_и_упражнение() {
        show(MiniBarStatus.Working(2, 4, "Жим лёжа"))

        composeRule.onNodeWithText("Подход 2 из 4 · Жим лёжа").assertIsDisplayed()
    }

    @Test
    fun после_отдыха_пора_делать_подход() {
        show(MiniBarStatus.RestOver(2))

        composeRule.onNodeWithText("Пора: подход 2").assertIsDisplayed()
    }

    @Test
    fun тап_по_плашке_возвращает_а_крестик_выходит() {
        show(MiniBarStatus.Working(2, 4, "Жим лёжа"))

        composeRule.onNodeWithText("Ноги").performClick()
        composeRule.onNodeWithContentDescription("Выйти из тренировки без сохранения").performClick()

        assertEquals(1, opened)
        assertEquals(1, exited)
    }

    @Test
    fun зоны_нажатия_не_меньше_48_dp() {
        show(MiniBarStatus.Resting(78_000L, 120_000L))

        composeRule.onNodeWithText("Ноги").assertTouchHeightAtLeast48()
        composeRule.onNodeWithContentDescription("Выйти из тренировки без сохранения")
            .assertTouchHeightAtLeast48()
    }

    @Test
    fun плашка_читается_одной_фразой_с_меткой_нажатия() {
        show(MiniBarStatus.Resting(78_000L, 120_000L))

        composeRule.onNodeWithText("Ноги")
            .assertTextContains("Отдых 01:18")
            .assert(
                SemanticsMatcher("метка нажатия «Вернуться к тренировке»") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Вернуться к тренировке"
                }
            )
    }
}
```

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `Unresolved reference: MiniWorkoutBarContent`.

- [ ] **Step 7: Composable плашки**

`presentation/session/MiniWorkoutBar.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter

private val BarHeight = 64.dp
private val RingZone = 48.dp
private val RingSize = 28.dp
private val RingStroke = 3.dp
private val TopBorder = 1.dp

/** Плашка свёрнутой тренировки внизу всех экранов, кроме экрана выполнения. */
@Composable
fun MiniWorkoutBar(
    viewModel: MiniWorkoutBarViewModel,
    onOpen: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.barState.collectAsState()
    // На время анимации исчезновения сессия уже None — плашка дорисовывает последний
    // показанный снимок, иначе уезжала бы пустой.
    val lastShown = remember { LastShown<MiniBarState>() }
    state?.let { lastShown.value = it }
    val shown = lastShown.value ?: return
    MiniWorkoutBarContent(state = shown, onOpen = onOpen, onExit = onExit, modifier = modifier)
}

/** Держатель без снимкового состояния: перерисовку и так даёт смена barState. */
private class LastShown<T : Any> {
    var value: T? = null
}

/** Тело плашки без ViewModel — для тестов и превью. */
@Composable
fun MiniWorkoutBarContent(
    state: MiniBarState,
    onOpen: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outline
    val status = state.status
    val statusText = when (status) {
        is MiniBarStatus.Resting ->
            stringResource(R.string.minibar_rest, DateFormatter.formatDurationCompact(status.timeLeftMillis))
        is MiniBarStatus.Working ->
            stringResource(R.string.minibar_set, status.currentSet, status.totalSets, status.exerciseName)
        is MiniBarStatus.RestOver -> stringResource(R.string.minibar_rest_over, status.nextSet)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .drawBehind {
                // Верхняя граница цвета outline — как рамка карточек темы.
                drawLine(outline, Offset.Zero, Offset(size.width, 0f), strokeWidth = TopBorder.toPx())
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Вся плашка, кроме крестика, — одна зона нажатия. clickable сам объединяет тексты
        // в один узел: TalkBack читает «Ноги, Отдых 01:18». liveRegion не ставится —
        // отсчёт зачитывался бы вслух каждую секунду.
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    onClickLabel = stringResource(R.string.minibar_open),
                    role = Role.Button,
                    onClick = onOpen
                )
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(RingZone), contentAlignment = Alignment.Center) {
                MiniRing(status = status, modifier = Modifier.size(RingSize))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = state.workoutName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (status is MiniBarStatus.RestOver) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onExit) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.minibar_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Маленькое кольцо отдыха: дорожка outline, дуга primary. Не мигает и не крутится. */
@Composable
private fun MiniRing(status: MiniBarStatus, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val fraction = ringFraction(status)
    Canvas(modifier = modifier) {
        val width = RingStroke.toPx()
        val inset = width / 2
        val arcSize = Size(size.width - width, size.height - width)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width)
        )
        if (fraction > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
        }
    }
}

@Preview(backgroundColor = 0xFF16161C, showBackground = true)
@Composable
private fun MiniWorkoutBarPreview() {
    WorkoutTimerTheme {
        Column {
            MiniWorkoutBarContent(MiniBarState("Ноги", MiniBarStatus.Resting(78_000L, 120_000L)), {}, {})
            MiniWorkoutBarContent(MiniBarState("Ноги", MiniBarStatus.Working(2, 4, "Жим лёжа")), {}, {})
            MiniWorkoutBarContent(MiniBarState("Ноги", MiniBarStatus.RestOver(2)), {}, {})
        }
    }
}
```

- [ ] **Step 8: Полная проверка, Compose-тест на эмуляторе и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.session.MiniWorkoutBarContentTest
```

Expected: 6 тестов зелёные.

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/session \
        app/src/main/res/values/strings.xml \
        app/src/test/java/ru/hopes/workouttimer/presentation/session \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/session
git commit -m "feat: плашка свёрнутой тренировки и признак «пора: подход»

restOver поднимается, когда отдых закончился сам. Плашка: название,
статус (отдых с отсчётом, подход, «Пора: подход N»), малое кольцо и
крестик выхода; одна зона нажатия с меткой «Вернуться к тренировке».

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Сворачивание — «Назад», меню ⋮, плашка в корне навигации, возврат

**Files:**
- Create: `presentation/navigation/SessionNavigation.kt`
- Modify (переписать целиком): `presentation/navigation/NavGraph.kt`
- Modify: `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` (функция `WorkoutExecutionScreen` + импорты)
- Modify: `presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt` (`onCleared`, `abandon`)
- Modify: `app/src/main/res/values/strings.xml` (`execution_minimize`, `execution_more`, `execution_exit_without_saving`)
- Test: `presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`
- Test (androidTest): `presentation/navigation/SessionNavigationTest.kt` (новый)

**Interfaces:**
- Consumes: `MiniWorkoutBar`, `MiniWorkoutBarViewModel.visible/abandon/prepareReturn` (Task 4); `WorkoutExecutionViewModel` (Task 3).
- Produces:
  - `internal fun NavController.returnToSession(workoutId: Int)` — `navigate(Execution) { launchSingleTop = true }`
  - `internal fun NavController.minimizeExecution()` — снять выполнение; под ним пусто — список
  - `WorkoutExecutionScreen(workoutId: Int, onMinimize: () -> Unit, onLeave: () -> Unit, viewModel = hiltViewModel())`
  - `WorkoutExecutionViewModel.abandon()`; `onCleared()` — только `close(ownedSessionId)`
  - в `NavGraph` — локальная `val returnToSession: () -> Unit` (её используют Task 6, 7, 11)

- [ ] **Step 1: Падающие тесты ViewModel — свёрнутая тренировка не закрывается**

В `WorkoutExecutionViewModelTest.kt` заменить тест `leaving a running session abandons it` на:

```kotlin
    @Test
    fun `leaving a running session only asks to close it, which keeps it running`() {
        every { manager.start(1) } returns 7L
        every { manager.isRunning } returns true
        val vm = viewModel()
        vm.start(1)
        session.value = present(7L, WorkoutExecutionState.Active(push, 1))

        store.clear() // экран свернули

        verify { manager.close(7L) } // менеджер сам ничего не сделает: тренировка в процессе
        verify(exactly = 0) { manager.abandon() }
    }

    @Test
    fun `exit without saving abandons the session`() {
        viewModel().abandon()

        verify { manager.abandon() }
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest'`
Expected: FAIL — `Unresolved reference: abandon`; после его появления первый тест падает на `verify(exactly = 0) { manager.abandon() }`.

- [ ] **Step 2: ViewModel — свернуть значит оставить**

В `WorkoutExecutionViewModel.kt` заменить `onCleared()` и добавить `abandon()`:

```kotlin
    /** «Выйти без сохранения» из меню ⋮: экран сначала уходит, потом сессия сбрасывается. */
    fun abandon() = manager.abandon()

    override fun onCleared() {
        super.onCleared()
        // close() сам ничего не делает, если тренировка идёт (экран свернули) или сессия
        // уже другая; закрывает только Loading, Error и Finished этой сессии.
        ownedSessionId?.let { manager.close(it) }
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest'`
Expected: PASS, 8 тестов.

- [ ] **Step 3: Падающий навигационный тест**

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/navigation/SessionNavigationTest.kt` — настоящие маршруты `Screen.*`, заглушки-`Text` вместо экранов (настоящим нужен Hilt, тестовой инфраструктуры Hilt в проекте нет); переходы — через те же функции, что зовёт `NavGraph`:

```kotlin
package ru.hopes.workouttimer.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    private fun showGraph(start: String = Screen.Workouts.route) {
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = start) {
                composable(Screen.Workouts.route) { Text("Список") }
                composable(
                    route = Screen.History.route,
                    arguments = listOf(navArgument("workout_id") { type = NavType.IntType })
                ) { Text("Другой экран") }
                composable(
                    route = Screen.Execution.route,
                    arguments = listOf(navArgument("workout_id") { type = NavType.IntType; defaultValue = 1 })
                ) { entry -> Text("Выполнение ${Screen.Execution.getWorkoutId(entry.arguments)}") }
            }
        }
    }

    /** Маршруты стека снизу вверх, без корневого графа. */
    private fun stack(): List<String> = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun возврат_через_плашку_кладёт_одну_запись_выполнения() {
        showGraph()
        composeRule.runOnIdle {
            nav.navigate(Screen.Execution.createRoute(1))
            nav.minimizeExecution()
            nav.navigate(Screen.History.createRoute(2))
            nav.returnToSession(1)
            nav.returnToSession(1) // двойной тап
        }

        composeRule.onNodeWithText("Выполнение 1").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                listOf(Screen.Workouts.route, Screen.History.route, Screen.Execution.route),
                stack()
            )
        }
    }

    @Test
    fun назад_после_возврата_ведёт_на_экран_под_выполнением() {
        showGraph()
        composeRule.runOnIdle {
            nav.navigate(Screen.Execution.createRoute(1))
            nav.minimizeExecution()
            nav.navigate(Screen.History.createRoute(2))
            nav.returnToSession(1)
            nav.minimizeExecution()
        }

        composeRule.onNodeWithText("Другой экран").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(Screen.Workouts.route, Screen.History.route), stack())
        }
    }

    @Test
    fun сворачивание_единственного_выполнения_открывает_список() {
        showGraph(start = Screen.Execution.route)
        composeRule.runOnIdle { nav.minimizeExecution() }

        composeRule.onNodeWithText("Список").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf(Screen.Workouts.route), stack()) }
    }
}
```

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `Unresolved reference: minimizeExecution`, `returnToSession`.

- [ ] **Step 4: Навигационные функции сессии**

`presentation/navigation/SessionNavigation.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.navigation

import androidx.navigation.NavController

/**
 * Возврат в идущую тренировку. Запись выполнения в стеке либо на вершине, либо её нет:
 * с экрана выполнения никуда не переходят, а сворачивание её снимает. launchSingleTop
 * страхует от двойного тапа — второй записи не будет. Стек — прежний плюс выполнение
 * сверху, и «Назад» снова сворачивает на экран, с которого вернулись.
 */
internal fun NavController.returnToSession(workoutId: Int) {
    navigate(Screen.Execution.createRoute(workoutId)) { launchSingleTop = true }
}

/**
 * Свернуть экран выполнения. Под ним штатно всегда что-то есть (диплинк строит стек
 * «список → выполнение»); если нет — открывается список: popBackStack() единственной
 * записи оставил бы NavHost пустым.
 */
internal fun NavController.minimizeExecution() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Screen.Workouts.route) {
            popUpTo(Screen.Execution.route) { inclusive = true }
        }
    }
}
```

- [ ] **Step 5: Строки экрана выполнения**

В `strings.xml` после `<string name="execution_note_placeholder" …>Введите заметку...</string>` (перед секцией плашки) вставить:

```xml
    <string name="execution_minimize">Свернуть тренировку</string>
    <string name="execution_more">Ещё</string>
    <string name="execution_exit_without_saving">Выйти без сохранения</string>
```

- [ ] **Step 6: Экран выполнения сворачивается**

В `WorkoutExecutionScreen.kt`:

1. Импорты добавить:

```kotlin
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
```

2. Заменить начало функции — от `@Composable\nfun WorkoutExecutionScreen(` до строки `BackHandler(enabled = hasUnsavedProgress) { showExitDialog = true }` включительно — на:

```kotlin
@Composable
fun WorkoutExecutionScreen(
    workoutId: Int,
    onMinimize: () -> Unit,
    onLeave: () -> Unit,
    viewModel: WorkoutExecutionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome by viewModel.chrome.collectAsState()

    // Диалог заметки и шторка веса запоминают позицию упражнения в сессии, а не сам
    // Exercise: значения полей берутся из текущего снимка. Открываются они только для
    // текущего упражнения (в Rest — упражнения следующего подхода).
    var noteIndex by remember { mutableStateOf<Int?>(null) }
    var weightSheetIndex by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Идёт тренировка — «Назад» и стрелка сворачивают её без диалога. В Loading, Error и
    // Finished сворачивать нечего: уход закрывает сессию (onCleared → close).
    val isLive = uiState is WorkoutExecutionState.Active || uiState is WorkoutExecutionState.Rest

    // start() идемпотентен: при пересоздании активности и при возврате в свёрнутую
    // тренировку сессия не сбрасывается.
    LaunchedEffect(workoutId) {
        viewModel.start(workoutId)
    }

    // Пока пишется завершение, «Назад» поглощается и ничего не делает: свернуть посреди
    // записи значило бы получить Finished, которого никто не увидит.
    BackHandler(enabled = isLive) {
        if (!chrome.isFinishing) onMinimize()
    }
```

3. В блоке `if (finishedState != null)` — `onDone = onExerciseCompleted` заменить на `onDone = onLeave`.

4. Шапку — `Row(…) { IconButton(…) {…}; Box(weight) {…}; Box(Modifier.size(48.dp)) }` — заменить на:

```kotlin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    when {
                        !isLive -> onLeave()
                        !chrome.isFinishing -> onMinimize()
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(
                            if (isLive) R.string.execution_minimize else R.string.common_back
                        ),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (currentExercise != null) {
                        ExerciseChip(
                            name = currentExercise.name,
                            position = chrome.currentExerciseNumber,
                            total = chrome.totalExercises,
                            onClick = { showExercisePicker = true }
                        )
                    } else {
                        Text(
                            text = chrome.workoutName.ifEmpty { stringResource(R.string.execution_workout_fallback) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // «Назад» больше не выходит — выход без сохранения переехал в меню.
                if (isLive && !chrome.isFinishing) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.execution_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.execution_exit_without_saving)) },
                                onClick = {
                                    menuExpanded = false
                                    showExitDialog = true
                                }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp))
                }
            }
```

5. В диалоге выхода кнопку «Выйти» заменить на:

```kotlin
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    // Сначала уходим, потом сбрасываем: уходящий экран держит последний кадр
                    // (ViewModel не отдаёт None), и onCleared застанет уже None — close() пустой.
                    onMinimize()
                    viewModel.abandon()
                }) { Text(stringResource(R.string.common_exit)) }
            },
```

Остальное тело функции — без изменений.

- [ ] **Step 7: Корень навигации с плашкой**

`presentation/navigation/NavGraph.kt` — заменить содержимое целиком (маршруты и `Screen` прежние, меняются колонка с плашкой, диалог ×, вызов экрана выполнения):

```kotlin
package ru.hopes.workouttimer.presentation.navigation

import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.screen.creation.CreateWorkoutScreen
import ru.hopes.workouttimer.presentation.screen.exercises.ExerciseCatalogScreen
import ru.hopes.workouttimer.presentation.screen.exportImport.ExportImportScreen
import ru.hopes.workouttimer.presentation.screen.progress.ExerciseProgressScreen
import ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionScreen
import ru.hopes.workouttimer.presentation.screen.workoutHistory.WorkoutHistoryScreen
import ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutScreen
import ru.hopes.workouttimer.presentation.session.MiniWorkoutBar
import ru.hopes.workouttimer.presentation.session.MiniWorkoutBarViewModel

private const val BAR_ANIMATION_MILLIS = 180

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavGraph(
    newIntent: Intent? = null,
    onIntentHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Область — активность: плашка одна на все экраны.
    val miniBar: MiniWorkoutBarViewModel = hiltViewModel()
    // Корень читает только видимость: тики отдыха его и NavHost не перерисовывают.
    val sessionShown by miniBar.visible.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onExecution = backStackEntry?.destination?.route == Screen.Execution.route
    // isImeVisible работает только в edge-to-edge окне (API 35+). На Android 7–14 Compose
    // клавиатуру не видит, и плашка остаётся внизу окна, как FAB списка, — решение спеки.
    val barVisible = sessionShown && !onExecution && !WindowInsets.isImeVisible
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    // Возврат в тренировку — один путь для плашки, просмотра, списка, виджета и уведомления.
    val returnToSession: () -> Unit = {
        miniBar.prepareReturn()?.let { navController.returnToSession(it) }
    }

    // Виджет шлёт интент только с FLAG_ACTIVITY_NEW_TASK. Холодный старт разбирает стартовый
    // интент в setGraph() (см. NavHost ниже). Если задача жива, интент приходит сюда через
    // MainActivity.onNewIntent — только когда тренировка не идёт (иначе MainActivity его
    // отбрасывает). handleDeepLink() с NEW_TASK без CLEAR_TASK сам перезапускает задачу со
    // стеком «список → выполнение». Стартовый интент сюда не попадает — NavController уже
    // обработал его сам, а публичный handleDeepLink() флагом deepLinkHandled не защищён.
    LaunchedEffect(newIntent) {
        if (newIntent != null) {
            navController.handleDeepLink(newIntent)
            onIntentHandled()
        }
    }

    // Плашка — под NavHost по раскладке: она не перекрывает экраны, FAB и снекбары
    // оказываются выше неё сами. Отступ системной навигации берёт на себя плашка, а экраны
    // его не добавляют повторно (их Scaffold вычитает поглощённые отступы).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Workouts.route,
            modifier = Modifier
                .weight(1f)
                .then(if (barVisible) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
        ) {
            // Экран списка тренировок
            composable(Screen.Workouts.route) {
                ListWorkoutScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = hiltViewModel(),
                    onAddWorkoutClick = {
                        navController.navigate(Screen.CreateWorkout.route)
                    },
                    onWorkoutClick = { workout ->
                        navController.navigate(Screen.Execution.createRoute(workout.id))
                    },
                    onEditClick = { workout ->
                        navController.navigate(Screen.EditWorkout.createRoute(workout.id))
                    },
                    onExportImportClick = {
                        navController.navigate(Screen.ExportImport.route)
                    },
                    onExercisesClick = { navController.navigate(Screen.Exercises.route) },
                    onHistoryClick = { workout ->
                        navController.navigate(Screen.History.createRoute(workout.id))
                    }
                )
            }

            // Экран создания тренировки
            composable(Screen.CreateWorkout.route) {
                CreateWorkoutScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = hiltViewModel(),
                    onFinished = {
                        navController.popBackStack()
                    }
                )
            }

            // Экран редактирования тренировки
            composable(
                route = Screen.EditWorkout.route,
                arguments = listOf(
                    navArgument("workout_id") { type = NavType.IntType }
                )
            ) { entry ->
                val workoutId = Screen.EditWorkout.getWorkoutId(entry.arguments)
                CreateWorkoutScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = hiltViewModel(),
                    onFinished = {
                        navController.popBackStack()
                    },
                    workoutId = workoutId
                )
            }

            // Экран выполнения тренировки
            composable(
                route = Screen.Execution.route,
                arguments = listOf(
                    navArgument("workout_id") { type = NavType.IntType }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = Screen.Execution.DEEP_LINK_PATTERN }
                )
            ) { entry ->
                WorkoutExecutionScreen(
                    workoutId = Screen.Execution.getWorkoutId(entry.arguments),
                    onMinimize = { navController.minimizeExecution() },
                    onLeave = { navController.popBackStack() },
                    viewModel = hiltViewModel()
                )
            }

            // Экран экспорта/импорта
            composable(Screen.ExportImport.route) {
                ExportImportScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Экран истории сессий тренировки
            composable(
                route = Screen.History.route,
                arguments = listOf(
                    navArgument("workout_id") { type = NavType.IntType }
                )
            ) { entry ->
                val workoutId = Screen.History.getWorkoutId(entry.arguments)
                WorkoutHistoryScreen(
                    viewModel = hiltViewModel(),
                    workoutId = workoutId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onExerciseClick = { catalogId ->
                        navController.navigate(Screen.ExerciseProgress.createRoute(catalogId))
                    }
                )
            }

            // Экран «Упражнения» — справочник
            composable(Screen.Exercises.route) {
                ExerciseCatalogScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenProgress = { exercise ->
                        navController.navigate(Screen.ExerciseProgress.createRoute(exercise.id))
                    }
                )
            }

            // Экран прогресса упражнения — из «Упражнений» и из развёрнутой сессии истории
            composable(
                route = Screen.ExerciseProgress.route,
                arguments = Screen.ExerciseProgress.arguments
            ) { entry ->
                ExerciseProgressScreen(
                    catalogId = Screen.ExerciseProgress.getCatalogId(entry.arguments),
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = barVisible,
            enter = slideInVertically(tween(BAR_ANIMATION_MILLIS)) { it } +
                expandVertically(tween(BAR_ANIMATION_MILLIS)),
            exit = slideOutVertically(tween(BAR_ANIMATION_MILLIS)) { it } +
                shrinkVertically(tween(BAR_ANIMATION_MILLIS))
        ) {
            MiniWorkoutBar(
                viewModel = miniBar,
                onOpen = returnToSession,
                onExit = { showExitDialog = true },
                // Фон до отступа: плашка заливает и полосу под системной навигацией.
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            )
        }
    }

    // Тот же диалог, что в меню ⋮ экрана выполнения. «Выйти» — сброс без записи; текущий
    // экран остаётся, плашка уезжает.
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.execution_exit_title)) },
            text = { Text(stringResource(R.string.execution_exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    miniBar.abandon()
                }) { Text(stringResource(R.string.common_exit)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

internal sealed class Screen(val route: String) {
    data object Workouts : Screen("workouts")
    data object CreateWorkout : Screen("create_workout")
    data object ExportImport : Screen("export_import")
    data object Exercises : Screen("exercises")
    data object EditWorkout : Screen("edit_workout/{workout_id}") {
        fun createRoute(workoutId: Int): String {
            return "edit_workout/$workoutId"
        }

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }

    // ВАЖНО: Маршрут должен содержать placeholder {workout_id}
    data object Execution : Screen("execution/{workout_id}") {

        const val DEEP_LINK_PATTERN = "workouttimer://execution/{workout_id}"

        fun createRoute(workoutId: Int): String {
            return "execution/$workoutId"
        }

        // Ссылка для виджета: строится из DEEP_LINK_PATTERN, а не дублирует его строкой —
        // иначе схема окажется захардкожена в двух местах, и расхождение не поймает ни
        // компилятор, ни тест, только тап по виджету на устройстве.
        fun createDeepLink(workoutId: Int): String =
            DEEP_LINK_PATTERN.replace("{workout_id}", workoutId.toString())

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }

    data object History : Screen("history/{workout_id}") {
        fun createRoute(workoutId: Int): String {
            return "history/$workoutId"
        }

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }

    data object ExerciseProgress : Screen("exercise_progress/{catalogId}") {
        private const val CATALOG_ID = "catalogId"

        // Long, а не Int: id справочника — Long, и getLong по аргументу IntType вернул бы 0.
        // Геттер, а не поле: JVM-тест маршрута не должен собирать аргументы навигации.
        val arguments: List<NamedNavArgument>
            get() = listOf(navArgument(CATALOG_ID) { type = NavType.LongType })

        fun createRoute(catalogId: Long): String = "exercise_progress/$catalogId"

        fun getCatalogId(arguments: Bundle?): Long = arguments?.getLong(CATALOG_ID) ?: 0L
    }
}
```

`returnToSession` в этой задаче использует только плашка; Task 6, 7 и 11 подключат к нему виджет, список и просмотр.

- [ ] **Step 8: Полная проверка, инструментальные тесты и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.navigation.SessionNavigationTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.navigation.ExerciseProgressRouteTest
```

Expected: `SessionNavigationTest` 3 зелёных, `ExerciseProgressRouteTest` 1 зелёный.

Быстрая ручная проверка на `emulator-5554` (сборка `./gradlew :app:installDebug` с `ANDROID_SERIAL=emulator-5554`): начать тренировку, «Назад» в отдыхе — список, внизу плашка «Отдых 0x:xx», отсчёт идёт; тап по плашке — экран выполнения; ⋮ → «Выйти без сохранения» → «Выйти» — список без плашки. Подробная проверка — Task 12.

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/navigation \
        app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution \
        app/src/main/res/values/strings.xml \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/navigation/SessionNavigationTest.kt
git commit -m "feat: «Назад» сворачивает тренировку в плашку

Экран выполнения в Rest/Active сворачивается без диалога, выход без
сохранения — в меню ⋮. Плашка под NavHost на всех экранах, кроме
выполнения и открытой клавиатуры (API 35+); тап возвращает, крестик —
тот же диалог выхода.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Виджет и уведомления возвращают в идущую тренировку

**Files:**
- Create: `presentation/IntentRoute.kt`
- Modify (переписать целиком): `presentation/MainActivity.kt`
- Modify: `presentation/navigation/NavGraph.kt` (параметры `returnToSessionRequested`/`onReturnHandled`, комментарий про интенты)
- Modify: `presentation/service/TimerNotificationService.kt:113-115`, `:147-149`, `:195-197`
- Modify: `presentation/widget/QuickStartWidget.kt:146-152` (комментарий)
- Test: `presentation/navigation/ScreenDeepLinkTest.kt`

**Interfaces:**
- Consumes: `WorkoutSessionManager.isRunning` (Task 2), `returnToSession` в `NavGraph` (Task 5), `Screen.Execution.DEEP_LINK_PATTERN`.
- Produces:
  - `sealed interface IntentRoute { data object OpenDeepLink; data object ReturnToSession; data object Ignore }`
  - `fun resolveIntent(action: String?, dataString: String?, isRunning: Boolean): IntentRoute`
  - `MainActivity.ACTION_OPEN_ACTIVE_WORKOUT = "ru.hopes.workouttimer.action.OPEN_ACTIVE_WORKOUT"`
  - `NavGraph(newIntent, onIntentHandled, returnToSessionRequested: Boolean = false, onReturnHandled: () -> Unit = {})`

- [ ] **Step 1: Падающие тесты разбора интента**

В `ScreenDeepLinkTest.kt` добавить импорты и тесты:

```kotlin
import ru.hopes.workouttimer.presentation.IntentRoute
import ru.hopes.workouttimer.presentation.MainActivity
import ru.hopes.workouttimer.presentation.resolveIntent
```

```kotlin
    // Виджет шлёт ACTION_VIEW с диплинком выполнения, уведомление — свой action без данных.
    private val widgetLink = "workouttimer://execution/7"
    private val viewAction = "android.intent.action.VIEW"

    @Test
    fun `виджет без сессии запускает тренировку диплинком`() {
        assertEquals(IntentRoute.OpenDeepLink, resolveIntent(viewAction, widgetLink, isRunning = false))
    }

    @Test
    fun `виджет при идущей сессии другой тренировки возвращает в неё`() {
        assertEquals(IntentRoute.ReturnToSession, resolveIntent(viewAction, widgetLink, isRunning = true))
    }

    @Test
    fun `уведомление при идущей сессии возвращает в неё, без сессии — ничего`() {
        assertEquals(
            IntentRoute.ReturnToSession,
            resolveIntent(MainActivity.ACTION_OPEN_ACTIVE_WORKOUT, null, isRunning = true)
        )
        assertEquals(
            IntentRoute.Ignore,
            resolveIntent(MainActivity.ACTION_OPEN_ACTIVE_WORKOUT, null, isRunning = false)
        )
    }

    @Test
    fun `запуск с иконки и чужие ссылки не трогают навигацию`() {
        assertEquals(IntentRoute.Ignore, resolveIntent("android.intent.action.MAIN", null, isRunning = true))
        assertEquals(IntentRoute.Ignore, resolveIntent(viewAction, "https://example.com/execution/7", isRunning = false))
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.navigation.ScreenDeepLinkTest'`
Expected: FAIL — `Unresolved reference: IntentRoute`, `ACTION_OPEN_ACTIVE_WORKOUT`.

- [ ] **Step 2: Разбор интента**

`presentation/IntentRoute.kt`:

```kotlin
package ru.hopes.workouttimer.presentation

import ru.hopes.workouttimer.presentation.navigation.Screen

/** Что сделать с интентом виджета или уведомления. */
sealed interface IntentRoute {
    /** Виджет без идущей сессии: разобрать диплинк и начать тренировку, как раньше. */
    data object OpenDeepLink : IntentRoute

    /** Сессия идёт: открыть её экран выполнения, диплинк не разбирать. */
    data object ReturnToSession : IntentRoute

    /** Уведомление без сессии, запуск с иконки, чужие данные — навигацию не трогать. */
    data object Ignore : IntentRoute
}

private val EXECUTION_DEEP_LINK_PREFIX = Screen.Execution.DEEP_LINK_PATTERN.substringBefore("{")

/**
 * На вход — строки, а не Intent: в JVM-тестах Intent и Uri — заглушки, возвращающие null.
 * При идущей сессии виджет любой тренировки возвращает в идущую (спека, решение 6).
 */
fun resolveIntent(action: String?, dataString: String?, isRunning: Boolean): IntentRoute {
    val fromNotification = action == MainActivity.ACTION_OPEN_ACTIVE_WORKOUT
    val fromWidget = dataString?.startsWith(EXECUTION_DEEP_LINK_PREFIX) == true
    return when {
        !fromNotification && !fromWidget -> IntentRoute.Ignore
        isRunning -> IntentRoute.ReturnToSession
        fromWidget -> IntentRoute.OpenDeepLink
        else -> IntentRoute.Ignore
    }
}
```

- [ ] **Step 3: `MainActivity`**

`presentation/MainActivity.kt` — заменить содержимое целиком:

```kotlin
package ru.hopes.workouttimer.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.hopes.workouttimer.presentation.navigation.NavGraph
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: WorkoutSessionManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Пользователь принял или отклонил разрешение на уведомления
    }

    private val newIntent = mutableStateOf<Intent?>(null)

    // Просьба открыть идущую тренировку; NavGraph гасит её после перехода.
    private val returnToSession = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Процесс жив, сессия идёт, а активность создана заново: тренировку свернули и
        // закрыли приложение «Назад» со списка, потом тапнули виджет или уведомление.
        // NavController сам разобрал бы диплинк стартового интента и открыл выполнение
        // другой тренировки — поэтому данные снимаются, а вместо них — возврат в идущую.
        // При восстановлении (savedInstanceState != null) стек восстанавливается сам,
        // диплинк повторно не разбирается, а возврат выдернул бы пользователя с его экрана.
        if (savedInstanceState == null &&
            resolveIntent(intent.action, intent.dataString, sessionManager.isRunning) == IntentRoute.ReturnToSession
        ) {
            intent = Intent(intent).setData(null)
            returnToSession.value = true
        }

        // Запрашиваем разрешение на уведомления (Android 13+)
        requestNotificationPermission()

        setContent {
            WorkoutTimerTheme {
                NavGraph(
                    newIntent = newIntent.value,
                    onIntentHandled = { newIntent.value = null },
                    returnToSessionRequested = returnToSession.value,
                    onReturnHandled = { returnToSession.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (resolveIntent(intent.action, intent.dataString, sessionManager.isRunning)) {
            IntentRoute.OpenDeepLink -> {
                setIntent(intent)
                newIntent.value = intent
            }
            // Диплинк не разбирается: handleDeepLink перестроил бы стек. Пользователь может
            // стоять на любом экране — выполнение откроется поверх него.
            IntentRoute.ReturnToSession -> returnToSession.value = true
            IntentRoute.Ignore -> Unit
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        /** Тап по уведомлению таймера: вернуть в идущую тренировку, если она есть. */
        const val ACTION_OPEN_ACTIVE_WORKOUT = "ru.hopes.workouttimer.action.OPEN_ACTIVE_WORKOUT"
    }
}
```

- [ ] **Step 4: Событие возврата в `NavGraph`**

В `NavGraph.kt`:

1. Сигнатура:

```kotlin
fun NavGraph(
    newIntent: Intent? = null,
    onIntentHandled: () -> Unit = {},
    returnToSessionRequested: Boolean = false,
    onReturnHandled: () -> Unit = {}
) {
```

2. Комментарий над `LaunchedEffect(newIntent)` заменить и добавить обработку возврата сразу после этого `LaunchedEffect`:

```kotlin
    // Виджет шлёт интент только с FLAG_ACTIVITY_NEW_TASK. Холодный старт без идущей сессии
    // разбирает стартовый интент в setGraph() (см. NavHost ниже). Если задача жива и сессии
    // нет, интент приходит сюда через MainActivity.onNewIntent: handleDeepLink() с NEW_TASK
    // без CLEAR_TASK сам перезапускает задачу со стеком «список → выполнение». При идущей
    // сессии MainActivity диплинк не отдаёт, а просит вернуться в тренировку (ниже).
    // Стартовый интент сюда не попадает — NavController уже обработал его сам, а публичный
    // handleDeepLink() флагом deepLinkHandled не защищён.
    LaunchedEffect(newIntent) {
        if (newIntent != null) {
            navController.handleDeepLink(newIntent)
            onIntentHandled()
        }
    }

    // Виджет или уведомление при идущей сессии — тот же возврат, что тап по плашке.
    LaunchedEffect(returnToSessionRequested) {
        if (returnToSessionRequested) {
            returnToSession()
            onReturnHandled()
        }
    }
```

- [ ] **Step 5: Уведомления помечают свой интент**

В `TimerNotificationService.kt` в трёх местах (`createNotification`, `createFinishedNotification`, `createIdleReminderNotification`) блок

```kotlin
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
```

заменить на

```kotlin
        val intent = Intent(this, MainActivity::class.java).apply {
            // Свой action: при идущей сессии тап возвращает в неё с любого экрана.
            action = MainActivity.ACTION_OPEN_ACTIVE_WORKOUT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
```

Флаги и `PendingIntent` прежние.

- [ ] **Step 6: Комментарий виджета**

В `QuickStartWidget.kt` KDoc над `startWorkoutIntent` заменить на:

```kotlin
/**
 * Только NEW_TASK, без CLEAR_TASK: CLEAR_TASK сносил задачу до того, как приложение успевало
 * что-то решить, и идущая тренировка терялась. Теперь живая задача просто выходит вперёд и
 * получает интент в MainActivity.onNewIntent (singleTop): если тренировка идёт (в том числе
 * свёрнутая), MainActivity возвращает в неё, какую бы тренировку ни показывал виджет. На холодном
 * старте без сессии NavController сам видит NEW_TASK без CLEAR_TASK, перезапускает задачу с
 * синтетическим стеком «список → выполнение», и «Назад» ведёт к списку, как раньше.
 */
```

- [ ] **Step 7: Тесты, полная проверка и коммит**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.navigation.ScreenDeepLinkTest'`
Expected: PASS, 7 тестов.

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
git add app/src/main/java/ru/hopes/workouttimer/presentation/IntentRoute.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/MainActivity.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/service/TimerNotificationService.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/widget/QuickStartWidget.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/navigation/ScreenDeepLinkTest.kt
git commit -m "feat: виджет и уведомление возвращают в идущую тренировку

resolveIntent решает по action и диплинку: при идущей сессии — возврат
поверх текущего экрана, без сессии виджет запускает тренировку, как
раньше. Пересоздание активности при живой сессии не разбирает диплинк.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Список при идущей сессии — «ВЕРНУТЬСЯ», блокировки правки и удаления

Конец M2. До экрана просмотра (M3) тап по строке без сессии по-прежнему запускает тренировку, а при идущей сессии — возвращает в неё (временное правило спеки, M2.5).

**Files:**
- Create: `presentation/screen/workouts/ListMenu.kt`
- Modify: `presentation/ui/components/Sheets.kt` (`ActionSheetItem.enabled`, `ActionSheetContent` → `internal`)
- Modify: `presentation/screen/workouts/ListWorkoutViewModel.kt` (`runningWorkoutId`)
- Modify: `presentation/screen/workouts/ListWorkoutScreen.kt` (колбэки, меню, карточка «Следующая», `QueueContent` → `internal`)
- Modify: `presentation/navigation/NavGraph.kt` (вызов `ListWorkoutScreen`)
- Modify: `app/src/main/res/values/strings.xml` (`list_return_button`, `list_action_return`, `list_action_locked`)
- Test: `presentation/screen/workouts/ListMenuTest.kt` (новый)
- Test: `presentation/screen/workouts/ListWorkoutViewModelTest.kt`
- Test (androidTest): `presentation/ui/components/ActionSheetContentTest.kt` (новый), `presentation/screen/workouts/QueueContentTest.kt` (новый)

**Interfaces:**
- Consumes: `WorkoutSessionManager.runningWorkout`, `RunningWorkout` (Task 2); `returnToSession`, `miniBar.prepareReturn()` в `NavGraph` (Task 5).
- Produces:
  - `enum class ListMenuAction { START, RETURN, SKIP, EDIT, HISTORY, DELETE }`, `data class ListMenuEntry(val action: ListMenuAction, val enabled: Boolean = true)`, `fun listMenuEntries(targetId: Int, runningWorkoutId: Int?, isSearching: Boolean): List<ListMenuEntry>`
  - `enum class HeroAction { START, RETURN }`, `fun heroActionOf(workoutId: Int, runningWorkoutId: Int?): HeroAction?`
  - `ActionSheetItem(text, icon, onClick, subtitle = null, destructive = false, enabled = true)`; `internal fun ColumnScope.ActionSheetContent(title, subtitle, items)`
  - `ListWorkoutState.runningWorkoutId: Int? = null`; `ListWorkoutViewModel(…, sessionManager: WorkoutSessionManager)` — новый последний параметр
  - `ListWorkoutScreen(modifier, viewModel, onAddWorkoutClick, onOpen: (WorkoutEntity) -> Unit, onStart: (WorkoutEntity) -> Unit, onReturn: () -> Unit, onEditClick, onExportImportClick, onHistoryClick, onExercisesClick)`
  - `internal fun QueueContent(state, onOpen, onStart, onReturn, onMenuClick, onAddWorkoutClick)`

- [ ] **Step 1: Падающие тесты правил меню**

`app/src/test/java/ru/hopes/workouttimer/presentation/screen/workouts/ListMenuTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.DELETE
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.EDIT
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.HISTORY
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.RETURN
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.SKIP
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.START

class ListMenuTest {

    private fun List<ListMenuEntry>.actions() = map { it.action }
    private fun List<ListMenuEntry>.enabled(action: ListMenuAction) = single { it.action == action }.enabled

    @Test
    fun `без сессии всё доступно`() {
        val entries = listMenuEntries(targetId = 1, runningWorkoutId = null, isSearching = false)

        assertEquals(listOf(START, SKIP, EDIT, HISTORY, DELETE), entries.actions())
        assertTrue(entries.all { it.enabled })
    }

    @Test
    fun `у идущей тренировки — вернуться, правка и удаление заблокированы`() {
        val entries = listMenuEntries(targetId = 1, runningWorkoutId = 1, isSearching = false)

        assertEquals(listOf(RETURN, SKIP, EDIT, HISTORY, DELETE), entries.actions())
        assertFalse(entries.enabled(EDIT))
        assertFalse(entries.enabled(DELETE))
        assertTrue(entries.enabled(HISTORY))
    }

    @Test
    fun `у другой тренировки при идущей сессии начать скрыт, правка доступна`() {
        val entries = listMenuEntries(targetId = 2, runningWorkoutId = 1, isSearching = false)

        assertEquals(listOf(SKIP, EDIT, HISTORY, DELETE), entries.actions())
        assertTrue(entries.all { it.enabled })
    }

    @Test
    fun `в поиске пропуска нет`() {
        assertEquals(
            listOf(START, EDIT, HISTORY, DELETE),
            listMenuEntries(targetId = 1, runningWorkoutId = null, isSearching = true).actions()
        )
    }

    @Test
    fun `кнопка карточки следующей`() {
        assertEquals(HeroAction.START, heroActionOf(workoutId = 1, runningWorkoutId = null))
        assertEquals(HeroAction.RETURN, heroActionOf(workoutId = 1, runningWorkoutId = 1))
        assertNull(heroActionOf(workoutId = 1, runningWorkoutId = 2))
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workouts.ListMenuTest'`
Expected: FAIL — `Unresolved reference: listMenuEntries`.

- [ ] **Step 2: Правила меню**

`presentation/screen/workouts/ListMenu.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workouts

enum class ListMenuAction { START, RETURN, SKIP, EDIT, HISTORY, DELETE }

data class ListMenuEntry(val action: ListMenuAction, val enabled: Boolean = true)

/**
 * Меню строки списка. Пока сессия идёт: у идущей тренировки «Начать» становится
 * «Вернуться», а «Редактировать» и «Удалить» неактивны — редактор пересоздаёт строки
 * упражнений, и правки сессии ушли бы в никуда, а удаление оставило бы сессию без
 * тренировки. У остальных тренировок «Начать» скрыт: вторую сессию не начать.
 */
fun listMenuEntries(targetId: Int, runningWorkoutId: Int?, isSearching: Boolean): List<ListMenuEntry> {
    val isRunningTarget = runningWorkoutId == targetId
    return buildList {
        when {
            runningWorkoutId == null -> add(ListMenuEntry(ListMenuAction.START))
            isRunningTarget -> add(ListMenuEntry(ListMenuAction.RETURN))
        }
        // В режиме поиска очередь не видна целиком, поэтому пропуск скрыт:
        // он переставил бы порядок, которого пользователь сейчас не наблюдает.
        if (!isSearching) add(ListMenuEntry(ListMenuAction.SKIP))
        add(ListMenuEntry(ListMenuAction.EDIT, enabled = !isRunningTarget))
        add(ListMenuEntry(ListMenuAction.HISTORY))
        add(ListMenuEntry(ListMenuAction.DELETE, enabled = !isRunningTarget))
    }
}

enum class HeroAction { START, RETURN }

/** Кнопка карточки «Следующая»: без сессии — «НАЧАТЬ», у идущей — «ВЕРНУТЬСЯ», у другой — нет. */
fun heroActionOf(workoutId: Int, runningWorkoutId: Int?): HeroAction? = when (runningWorkoutId) {
    null -> HeroAction.START
    workoutId -> HeroAction.RETURN
    else -> null
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workouts.ListMenuTest'`
Expected: PASS, 5 тестов.

- [ ] **Step 3: Падающий тест ViewModel списка**

В `ListWorkoutViewModelTest.kt`:

1. Импорты:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import ru.hopes.workouttimer.presentation.session.RunningWorkout
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
```

2. После `workoutWith(…)` добавить помощник:

```kotlin
    private fun sessionWith(running: MutableStateFlow<RunningWorkout?> = MutableStateFlow(null)): WorkoutSessionManager =
        mockk<WorkoutSessionManager>().also { every { it.runningWorkout } returns running }
```

3. В помощник `viewModel(…)` добавить последний параметр `running: MutableStateFlow<RunningWorkout?> = MutableStateFlow(null)` и передать в конструктор последним аргументом `sessionWith(running)`:

```kotlin
        return ListWorkoutViewModel(
            getAll,
            search,
            delete,
            getDurations,
            skip,
            undoSkip,
            sessionWith(running)
        )
```

4. В трёх тестах с прямым вызовом конструктора (`поиск переключает состояние…`, `многословный запрос…`, `очистка поискового запроса…`) после последнего аргумента `mockk(relaxed = true)` добавить `, sessionWith()`.

5. Новый тест в конец класса:

```kotlin
    @Test
    fun `идущая тренировка отмечается в состоянии списка`() = runTest(dispatcher) {
        val running = MutableStateFlow<RunningWorkout?>(null)
        val vm = viewModel(workouts = listOf(workoutWith(3, "Ноги", 0L)), running = running)
        testScheduler.advanceUntilIdle()
        assertNull(vm.state.value.runningWorkoutId)

        running.value = RunningWorkout(3, "Ноги", isLoading = false, addedExerciseIds = emptySet())
        testScheduler.advanceUntilIdle()
        assertEquals(3, vm.state.value.runningWorkoutId)

        running.value = null
        testScheduler.advanceUntilIdle()
        assertNull(vm.state.value.runningWorkoutId)
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutViewModelTest'`
Expected: FAIL — компиляция: лишний аргумент конструктора, нет `runningWorkoutId`.

- [ ] **Step 4: ViewModel списка знает идущую тренировку**

В `ListWorkoutViewModel.kt`:

1. Импорт `import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager`.
2. Конструктор — последний параметр:

```kotlin
    private val undoSkipWorkoutUseCase: UndoSkipWorkoutUseCase,
    private val sessionManager: WorkoutSessionManager
) : ViewModel() {
```

3. В конец блока `init` (после `.launchIn(viewModelScope)`) добавить:

```kotlin

        // Идущая тренировка: у неё «ВЕРНУТЬСЯ» вместо «НАЧАТЬ», правка и удаление закрыты.
        // runningWorkout, а не session: тики отдыха список не перерисовывают.
        sessionManager.runningWorkout
            .onEach { running -> _state.update { it.copy(runningWorkoutId = running?.workoutId) } }
            .launchIn(viewModelScope)
```

4. В `ListWorkoutState` после `errorMessage` добавить поле:

```kotlin
    @StringRes val errorMessage: Int? = null,
    /** Тренировка идущей сессии (в том числе свёрнутой); null — сессии нет. */
    val runningWorkoutId: Int? = null
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workouts.*'`
Expected: PASS — `ListWorkoutViewModelTest` (прежние + 1), `ListMenuTest` 5.

- [ ] **Step 5: Неактивный пункт листа действий**

В `Sheets.kt`:

1. `ActionSheetItem` — новое поле:

```kotlin
data class ActionSheetItem(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val subtitle: String? = null,
    val destructive: Boolean = false,
    /** Неактивный пункт приглушён и не нажимается; причину пишет subtitle. */
    val enabled: Boolean = true
)

// Стандартная прозрачность неактивного элемента Material.
private const val DisabledAlpha = 0.38f
```

2. `ActionSheetContent` — `private` → `internal` и тело цикла `items.forEach { item -> … }`:

```kotlin
        items.forEach { item ->
            val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
            val tint = when {
                !item.enabled -> disabledColor
                item.destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = item.enabled) { item.onClick() }
                    .padding(horizontal = ScreenPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        !item.enabled -> disabledColor
                        item.destructive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
```

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/ui/components/ActionSheetContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ActionSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var edits = 0
    private var history = 0

    private fun show() = composeRule.setContent {
        WorkoutTimerTheme {
            Column {
                ActionSheetContent(
                    title = "Ноги",
                    subtitle = null,
                    items = listOf(
                        ActionSheetItem(
                            text = "Редактировать",
                            icon = Icons.Default.Edit,
                            onClick = { edits++ },
                            subtitle = "Недоступно во время тренировки",
                            enabled = false
                        ),
                        ActionSheetItem(text = "История", icon = Icons.Default.History, onClick = { history++ })
                    )
                )
            }
        }
    }

    @Test
    fun неактивный_пункт_подписан_и_не_нажимается() {
        show()

        composeRule.onNodeWithText("Редактировать").assertIsNotEnabled()
        composeRule.onNodeWithText("Недоступно во время тренировки", substring = false, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Редактировать").performClick()

        composeRule.runOnIdle { assertEquals(0, edits) }
    }

    @Test
    fun активный_пункт_нажимается() {
        show()

        composeRule.onNodeWithText("История").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(1, history) }
    }
}
```

- [ ] **Step 6: Строки списка**

В `strings.xml` после `<string name="list_error_undo_skip">Не удалось отменить пропуск</string>` вставить:

```xml
    <string name="list_return_button">ВЕРНУТЬСЯ</string>
    <string name="list_action_return">Вернуться к тренировке</string>
    <string name="list_action_locked">Недоступно во время тренировки</string>
```

- [ ] **Step 7: Экран списка**

В `ListWorkoutScreen.kt`:

1. Сигнатура `ListWorkoutScreen` — `onWorkoutClick` заменить тремя колбэками:

```kotlin
fun ListWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: ListWorkoutViewModel = hiltViewModel(),
    onAddWorkoutClick: () -> Unit,
    onOpen: (WorkoutEntity) -> Unit,
    onStart: (WorkoutEntity) -> Unit,
    onReturn: () -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onExportImportClick: () -> Unit = {},
    onHistoryClick: (WorkoutEntity) -> Unit = {},
    onExercisesClick: () -> Unit = {}
) {
```

2. Блок `menuFor?.let { target -> val items = buildList { … } … }` — заменить построение `items`:

```kotlin
    menuFor?.let { target ->
        val locked = stringResource(R.string.list_action_locked)
        val items = listMenuEntries(target.id, state.runningWorkoutId, state.isSearching).map { entry ->
            when (entry.action) {
                ListMenuAction.START ->
                    ActionSheetItem(stringResource(R.string.list_action_start), Icons.Default.PlayArrow, {
                        menuFor = null
                        onStart(target)
                    })
                ListMenuAction.RETURN ->
                    ActionSheetItem(stringResource(R.string.list_action_return), Icons.Default.PlayArrow, {
                        menuFor = null
                        onReturn()
                    })
                ListMenuAction.SKIP ->
                    ActionSheetItem(stringResource(R.string.list_action_skip), Icons.Default.SkipNext, {
                        menuFor = null
                        viewModel.skipWorkout(target)
                    }, subtitle = stringResource(R.string.list_action_skip_subtitle))
                ListMenuAction.EDIT ->
                    ActionSheetItem(
                        stringResource(R.string.list_action_edit), Icons.Default.Edit, {
                            menuFor = null
                            onEditClick(target)
                        },
                        subtitle = if (entry.enabled) null else locked,
                        enabled = entry.enabled
                    )
                ListMenuAction.HISTORY ->
                    ActionSheetItem(stringResource(R.string.list_action_history), Icons.Default.History, {
                        menuFor = null
                        onHistoryClick(target)
                    })
                ListMenuAction.DELETE ->
                    ActionSheetItem(
                        stringResource(R.string.common_delete), Icons.Default.Delete, {
                            menuFor = null
                            workoutToDelete = target
                        },
                        subtitle = if (entry.enabled) null else locked,
                        destructive = true,
                        enabled = entry.enabled
                    )
            }
        }
        ActionSheet(
            title = target.name,
            subtitle = daysAgoText(target.lastUseAt),
            items = items,
            onDismiss = { menuFor = null }
        )
    }
```

3. Вызов `QueueContent(…)` в `Scaffold`:

```kotlin
            QueueContent(
                state = state,
                onOpen = onOpen,
                onStart = onStart,
                onReturn = onReturn,
                onMenuClick = { menuFor = it },
                onAddWorkoutClick = onAddWorkoutClick
            )
```

4. `QueueContent` — `private` → `internal`, сигнатура и места вызова карточек:

```kotlin
@Composable
internal fun QueueContent(
    state: ListWorkoutState,
    onOpen: (WorkoutEntity) -> Unit,
    onStart: (WorkoutEntity) -> Unit,
    onReturn: () -> Unit,
    onMenuClick: (WorkoutEntity) -> Unit,
    onAddWorkoutClick: () -> Unit
) {
```

  внутри — карточка «Следующая»:

```kotlin
                item(key = "hero-${next.workout.id}") {
                    NextWorkoutCard(
                        item = next,
                        durationMillis = state.lastSessionDurations[next.workout.id],
                        action = heroActionOf(next.workout.id, state.runningWorkoutId),
                        onAction = {
                            if (state.runningWorkoutId == null) onStart(next.workout) else onReturn()
                        },
                        onMenu = { onMenuClick(next.workout) }
                    )
                }
```

  и строка: `onClick = { onWorkoutClick(item.workout) }` → `onClick = { onOpen(item.workout) }`.

5. `NextWorkoutCard` — параметры `onStart: () -> Unit` заменить на `action: HeroAction?, onAction: () -> Unit`, а кнопку (`Box(…combinedClickable(onClick = onStart)…) { Text(list_start_button) }`) — на:

```kotlin
            // У другой тренировки при идущей сессии кнопки нет: вторую сессию не начать.
            if (action != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .combinedClickable(onClick = onAction),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            when (action) {
                                HeroAction.START -> R.string.list_start_button
                                HeroAction.RETURN -> R.string.list_return_button
                            }
                        ),
                        color = Accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
```

6. Три превью `QueueContent…Preview` — `onWorkoutClick = {},` заменить на `onOpen = {}, onStart = {}, onReturn = {},`.

- [ ] **Step 8: Корень навигации — колбэки списка**

В `NavGraph.kt` в `composable(Screen.Workouts.route)` строки

```kotlin
                    onWorkoutClick = { workout ->
                        navController.navigate(Screen.Execution.createRoute(workout.id))
                    },
```

заменить на:

```kotlin
                    // До экрана просмотра: без сессии тап по строке запускает тренировку, как
                    // раньше; при идущей — возвращает в неё, а не начинает вторую.
                    onOpen = { workout ->
                        val runningId = miniBar.prepareReturn()
                        if (runningId != null) {
                            navController.returnToSession(runningId)
                        } else {
                            navController.navigate(Screen.Execution.createRoute(workout.id))
                        }
                    },
                    onStart = { workout ->
                        navController.navigate(Screen.Execution.createRoute(workout.id))
                    },
                    onReturn = returnToSession,
```

- [ ] **Step 9: Compose-тест очереди**

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workouts/QueueContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class QueueContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Int>()
    private val started = mutableListOf<Int>()
    private var returned = 0

    private fun workout(id: Int, name: String) =
        WorkoutWithExercises(workout = WorkoutEntity(id = id, name = name, lastUseAt = 0L), exercises = emptyList())

    private fun show(runningWorkoutId: Int?) = composeRule.setContent {
        WorkoutTimerTheme {
            QueueContent(
                state = ListWorkoutState(
                    workouts = listOf(workout(1, "Ноги"), workout(2, "Спина")),
                    runningWorkoutId = runningWorkoutId
                ),
                onOpen = { opened += it.id },
                onStart = { started += it.id },
                onReturn = { returned++ },
                onMenuClick = {},
                onAddWorkoutClick = {}
            )
        }
    }

    @Test
    fun без_сессии_карточка_следующей_начинает_тренировку() {
        show(runningWorkoutId = null)

        composeRule.onNodeWithText("НАЧАТЬ").performClick()

        composeRule.runOnIdle { assertEquals(listOf(1), started) }
    }

    @Test
    fun идущая_тренировка_на_карточке_предлагает_вернуться() {
        show(runningWorkoutId = 1)

        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithText("ВЕРНУТЬСЯ").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, returned) }
    }

    @Test
    fun при_сессии_другой_тренировки_кнопки_на_карточке_нет() {
        show(runningWorkoutId = 2)

        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithText("ВЕРНУТЬСЯ").assertDoesNotExist()
    }

    @Test
    fun тап_по_строке_очереди_открывает_тренировку() {
        show(runningWorkoutId = null)

        // Клик по самому тексту: под ним — строка, а не кнопка меню.
        composeRule.onNodeWithText("Спина", useUnmergedTree = true).performClick()

        composeRule.runOnIdle { assertEquals(listOf(2), opened) }
    }
}
```

- [ ] **Step 10: Полная проверка, инструментальные тесты и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.ui.components.ActionSheetContentTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.workouts.QueueContentTest
```

Expected: `ActionSheetContentTest` 2, `QueueContentTest` 4 — зелёные.

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts \
        app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/Sheets.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/workouts \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/ui/components/ActionSheetContentTest.kt \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workouts/QueueContentTest.kt
git commit -m "feat: список при идущей тренировке — «ВЕРНУТЬСЯ» и блокировки

У идущей тренировки кнопка карточки и пункт меню возвращают в неё, а
«Редактировать» и «Удалить» неактивны с пояснением; у других «Начать»
скрыт. Тап по строке при идущей сессии возвращает в неё.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Опоры просмотра — план подхода, поток тренировки, маршрут `workout/{workout_id}`

Начало M3.

**Files:**
- Modify: `presentation/utils/SetFormat.kt` (`formatPlannedSet`, `formatSet` через неё)
- Create: `domain/usecase/ObserveWorkoutUseCase.kt`
- Modify: `presentation/navigation/NavGraph.kt` (`Screen.WorkoutPreview`)
- Test: `presentation/utils/SetFormatTest.kt`
- Test: `domain/usecase/ObserveWorkoutUseCaseTest.kt` (новый)
- Test: `presentation/navigation/ScreenDeepLinkTest.kt`

**Interfaces:**
- Consumes: `formatLoad(unit, weight, extraWeight, format): String?`, `SetFormat`, `WorkoutRepository.getAllWorkoutsWithExercise(): Flow<List<WorkoutWithExercises>>`, `WorkoutWithExercises.toDomain(): Workout` (`data/mapper/WorkoutMapper.kt`).
- Produces:
  - `fun formatPlannedSet(unit: ExerciseUnit, weight: Double, extraWeight: Double, reps: Int, format: SetFormat): String` — «60 кг × 8», «плита 5 +2 кг × 12», «12»
  - `class ObserveWorkoutUseCase { operator fun invoke(id: Int): Flow<Workout?> }`
  - `Screen.WorkoutPreview` — `route = "workout/{workout_id}"`, `createRoute(workoutId: Int): String`, `getWorkoutId(arguments: Bundle?): Int`

- [ ] **Step 1: Падающие тесты**

В `SetFormatTest.kt` добавить:

```kotlin
    @Test
    fun `план подхода из шаблона — та же подпись, что у сделанного`() {
        assertEquals("60 кг × 8", formatPlannedSet(KG, 60.0, 0.0, 8, format))
        assertEquals("плита 5 +2 кг × 12", formatPlannedSet(PLATE, 5.0, 2.0, 12, format))
        assertEquals("плита 5 × 12", formatPlannedSet(PLATE, 5.0, 0.0, 12, format))
        assertEquals("12", formatPlannedSet(BODYWEIGHT, 0.0, 0.0, 12, format))
    }
```

`app/src/test/java/ru/hopes/workouttimer/domain/usecase/ObserveWorkoutUseCaseTest.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.data.dao.ExerciseWithCatalog
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.repository.WorkoutRepository

class ObserveWorkoutUseCaseTest {

    private fun workout(id: Int, name: String, vararg exercises: Pair<String, Int>) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = 0L),
        exercises = exercises.mapIndexed { index, (exerciseName, order) ->
            ExerciseWithCatalog(
                exercise = ExerciseEntity(
                    id = id * 10 + index, workoutId = id.toLong(), name = exerciseName, weight = 60.0,
                    sets = 4, reps = 8, restTimeMillis = 120_000L, orderInWorkout = order, catalogId = 1L
                ),
                catalog = null
            )
        }
    )

    @Test
    fun `тренировка по id, упражнения по порядку, и новый состав после правки`() = runTest {
        val all = MutableStateFlow(listOf(workout(1, "Ноги", "Присед" to 2, "Выпады" to 1), workout(2, "Спина")))
        val repo = mockk<WorkoutRepository>()
        every { repo.getAllWorkoutsWithExercise() } returns all
        val observe = ObserveWorkoutUseCase(repo)

        val first = observe(1).first()
        assertEquals("Ноги", first?.name)
        assertEquals(listOf("Выпады", "Присед"), first?.exercises?.map { it.name })

        all.value = listOf(workout(1, "Ноги", "Жим ногами" to 1))
        assertEquals(listOf("Жим ногами"), observe(1).first()?.exercises?.map { it.name })
    }

    @Test
    fun `удалённой тренировки нет`() = runTest {
        val repo = mockk<WorkoutRepository>()
        every { repo.getAllWorkoutsWithExercise() } returns MutableStateFlow(listOf(workout(2, "Спина")))

        assertNull(ObserveWorkoutUseCase(repo)(1).first())
    }
}
```

В `ScreenDeepLinkTest.kt` добавить:

```kotlin
    @Test
    fun `маршрут просмотра тренировки`() {
        assertEquals("workout/{workout_id}", Screen.WorkoutPreview.route)
        assertEquals("workout/7", Screen.WorkoutPreview.createRoute(7))
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.utils.SetFormatTest' --tests 'ru.hopes.workouttimer.domain.usecase.ObserveWorkoutUseCaseTest' --tests 'ru.hopes.workouttimer.presentation.navigation.ScreenDeepLinkTest'`
Expected: FAIL — `Unresolved reference: formatPlannedSet`, `ObserveWorkoutUseCase`, `WorkoutPreview`.

- [ ] **Step 2: План подхода — одно правило с подписью сделанного**

В `SetFormat.kt` заменить `formatSet` и добавить `formatPlannedSet` перед ней:

```kotlin
/**
 * Подпись подхода по значениям, без SessionSet: план из шаблона тренировки на экране
 * просмотра — «60 кг × 8», «плита 5 +2 кг × 12», «12». То же правило, что у сделанного подхода.
 */
fun formatPlannedSet(unit: ExerciseUnit, weight: Double, extraWeight: Double, reps: Int, format: SetFormat): String {
    val load = formatLoad(unit, weight, extraWeight, format) ?: return reps.toString()
    return format.set.fill(load, reps)
}

/**
 * Подпись подхода — одна на всё приложение: «60 кг × 8», «плита 5 +2 кг × 12», «12».
 * [bareKg] — для перечисления: следующие подходы в кг пишутся без единицы, «60 × 8».
 */
fun formatSet(set: SessionSet, format: SetFormat, bareKg: Boolean = false): String {
    if (bareKg && set.unit == ExerciseUnit.KG) return format.set.fill(set.weight.toCorrectNum(), set.reps)
    return formatPlannedSet(set.unit, set.weight, set.extraWeight, set.reps, format)
}
```

- [ ] **Step 3: Поток тренировки**

`domain/usecase/ObserveWorkoutUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

/**
 * Тренировка по id как поток: после правки в редакторе и возврата просмотр показывает
 * новый состав. Нового SQL нет — так же устроен getWorkoutById. null — тренировку удалили.
 */
class ObserveWorkoutUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(id: Int): Flow<Workout?> =
        repo.getAllWorkoutsWithExercise()
            .map { list -> list.find { it.workout.id == id }?.toDomain() }
            .distinctUntilChanged()
}
```

- [ ] **Step 4: Маршрут просмотра**

В `NavGraph.kt` в `sealed class Screen` после `History` добавить:

```kotlin
    // Просмотр тренировки: тап по строке списка. Диплинка нет.
    data object WorkoutPreview : Screen("workout/{workout_id}") {
        fun createRoute(workoutId: Int): String = "workout/$workoutId"

        fun getWorkoutId(arguments: Bundle?): Int = arguments?.getInt("workout_id") ?: 0
    }
```

- [ ] **Step 5: Тесты зелёные, полная проверка, коммит**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.utils.SetFormatTest' --tests 'ru.hopes.workouttimer.domain.usecase.ObserveWorkoutUseCaseTest' --tests 'ru.hopes.workouttimer.presentation.navigation.ScreenDeepLinkTest'`
Expected: PASS (все прежние `SetFormatTest` тоже — `formatSet` переписан через `formatPlannedSet` без изменения вывода).

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
git add app/src/main/java/ru/hopes/workouttimer/presentation/utils/SetFormat.kt \
        app/src/main/java/ru/hopes/workouttimer/domain/usecase/ObserveWorkoutUseCase.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/utils/SetFormatTest.kt \
        app/src/test/java/ru/hopes/workouttimer/domain/usecase/ObserveWorkoutUseCaseTest.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/navigation/ScreenDeepLinkTest.kt
git commit -m "feat: опоры экрана просмотра — план подхода, поток тренировки, маршрут

formatPlannedSet подписывает подход шаблона тем же правилом, что и
сделанный; ObserveWorkoutUseCase отдаёт тренировку потоком; маршрут
workout/{workout_id}.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: «+ в сегодняшнюю» в менеджере и метка «сегодня» в шторке

**Files:**
- Modify: `presentation/session/WorkoutSession.kt` (`AddResult`)
- Modify: `presentation/session/WorkoutSessionManager.kt` (`addExerciseToday`, правки добавленного — только в сессии, сброс `finishPending`)
- Modify: `presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` (строка шторки выбора)
- Modify: `app/src/main/res/values/strings.xml` (`execution_added_today`)
- Test: `presentation/session/WorkoutSessionManagerTest.kt`

**Interfaces:**
- Consumes: `WorkoutSessionManager` (Task 1–4), `SessionExercise.addedToday`, `runningWorkoutOf` (Task 2).
- Produces:
  - `enum class AddResult { ADDED, ALREADY_ADDED, NO_SESSION }`
  - `fun WorkoutSessionManager.addExerciseToday(exercise: Exercise): AddResult` — копия в конец списка, только в `Rest`/`Active` и не во время записи завершения; одна копия на исходную строку (`exercise.id`)
  - правка веса и заметки добавленного упражнения не вызывает `workoutRepository`
  - `RunningWorkout.addedExerciseIds` содержит `exercise.id` добавленных строк (уже считается `runningWorkoutOf`)

- [ ] **Step 1: Падающие тесты**

В `WorkoutSessionManagerTest.kt` добавить импорт `import org.junit.Assert.assertNotNull` и в конец класса:

```kotlin
    // --- «+ в сегодняшнюю» ---

    // Упражнение другой тренировки (workoutId = 2), строка шаблона id = 21.
    private val fromBack = Exercise(
        id = 21, name = "Тяга блока", weight = 5.0, sets = 2, reps = 12, timeMillis = 60_000,
        order = 1, catalogId = 210L, unit = ExerciseUnit.PLATE, extraWeight = 2.0, note = "локти вниз"
    )

    private val WorkoutSessionManager.snapshot: WorkoutSession.Present
        get() = session.value as WorkoutSession.Present

    @Test
    fun `added exercise goes to the end without moving the current position`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.onExerciseFinished() // отдых перед подходом 2

        val result = manager.addExerciseToday(fromBack)

        assertEquals(AddResult.ADDED, result)
        assertEquals(listOf(false, true), manager.snapshot.exercises.map { it.addedToday })
        assertEquals(fromBack, manager.snapshot.exercises.last().exercise)
        assertEquals(0, manager.snapshot.exerciseIndex)
        assertTrue(manager.phase is WorkoutExecutionState.Rest)
        assertEquals(setOf(21), manager.runningWorkout.value?.addedExerciseIds)
    }

    @Test
    fun `the same source row is added only once`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        manager.addExerciseToday(fromBack)

        val again = manager.addExerciseToday(fromBack)

        assertEquals(AddResult.ALREADY_ADDED, again)
        assertEquals(2, manager.snapshot.exercises.size)
    }

    @Test
    fun `nothing is added without a workout in progress`() = runTest {
        val idle = managerWith(singleSetWorkout())
        assertEquals(AddResult.NO_SESSION, idle.addExerciseToday(fromBack))

        val finished = managerWith(singleSetWorkout())
        finished.start(1)
        finished.onExerciseFinished() // Finished
        assertEquals(AddResult.NO_SESSION, finished.addExerciseToday(fromBack))

        val gate = CompletableDeferred<Unit>()
        val finish = mockk<FinishWorkoutSessionUseCase>()
        coEvery { finish(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val finishing = managerWith(singleSetWorkout(), finish = finish)
        finishing.start(1)
        finishing.onExerciseFinished() // запись висит
        assertEquals(AddResult.NO_SESSION, finishing.addExerciseToday(fromBack))
        assertEquals(1, finishing.snapshot.exercises.size)
        gate.complete(Unit)
    }

    @Test
    fun `adding an exercise registers an interaction`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        val farFuture = System.currentTimeMillis() + 1_000_000L
        manager.registerInteraction(now = farFuture)

        manager.addExerciseToday(fromBack)

        assertTrue(manager.lastInteractionAt < farFuture)
    }

    @Test
    fun `adding on the last set moves the end of the workout`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val manager = managerWith(singleSetWorkout(), finish = finish)
        manager.start(1)
        assertTrue(manager.isLastSetOfWorkout)

        manager.addExerciseToday(fromBack)
        assertFalse(manager.isLastSetOfWorkout)

        manager.onExerciseFinished()

        val rest = manager.phase as WorkoutExecutionState.Rest
        assertEquals("Тяга блока", rest.exercise.name)
        assertEquals(1, manager.snapshot.exerciseIndex)
        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sets of the added exercise are saved with the running workout`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(1, any(), any(), any(), capture(setsSlot)) } returns Unit
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val manager = buildManager(getWorkout, repo, finish)
        manager.start(1)
        manager.addExerciseToday(fromBack.copy(sets = 1))

        manager.onExerciseFinished() // присед → отдых перед тягой
        manager.skipRest()
        manager.onExerciseFinished() // тяга — последний подход

        assertEquals(
            listOf(
                RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG),
                RecordedSet(210L, "Тяга блока", 5.0, 2.0, 12, ExerciseUnit.PLATE)
            ),
            setsSlot.captured
        )
        coVerify(exactly = 1) { repo.updateLastUseAt(1) }
        coVerify(exactly = 0) { repo.updateLastUseAt(2) }
    }

    @Test
    fun `edits of an added exercise stay in the session`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns twoSetWorkout()
        val manager = buildManager(getWorkout, repo, mockk(relaxed = true))
        manager.start(1)
        manager.addExerciseToday(fromBack)

        manager.updateExerciseWeightAndReps(index = 1, weight = 6.0, extraWeight = 0.0, reps = 10)
        manager.updateExerciseNote(1, "без рывков")

        val added = manager.snapshot.exercises[1].exercise
        assertEquals(6.0, added.weight, 0.0)
        assertEquals("без рывков", added.note)
        coVerify(exactly = 0) { repo.updateExerciseWeightAndReps(any(), any(), any(), any()) }
        coVerify(exactly = 0) { repo.updateExerciseNote(any(), any()) }
    }

    @Test
    fun `edits of a template row still go to the template`() = runTest {
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns twoSetWorkout()
        val manager = buildManager(getWorkout, repo, mockk(relaxed = true))
        manager.start(1)
        manager.addExerciseToday(fromBack)

        manager.updateExerciseWeightAndReps(index = 0, weight = 12.5, extraWeight = 0.0, reps = 6)
        manager.updateExerciseNote(0, "медленно")

        coVerify(exactly = 1) { repo.updateExerciseWeightAndReps(10, 12.5, 0.0, 6) }
        coVerify(exactly = 1) { repo.updateExerciseNote(10, "медленно") }
    }

    @Test
    fun `index addressing keeps an added copy apart from a template row of the same catalog entry`() = runTest {
        val manager = managerWith(twoSetWorkout())
        manager.start(1)
        // Та же запись справочника, что у строки шаблона, но другая исходная строка.
        val sameCatalog = fromBack.copy(id = 99, name = "Push", catalogId = 0L, unit = ExerciseUnit.KG, extraWeight = 0.0)
        manager.addExerciseToday(sameCatalog)

        manager.updateExerciseWeightAndReps(index = 1, weight = 40.0, extraWeight = 0.0, reps = 3)
        assertEquals(10.0, manager.snapshot.exercises[0].exercise.weight, 0.0)

        manager.moveToExercise(1)
        val active = manager.phase as WorkoutExecutionState.Active
        assertEquals(99, active.exercise.id)
        assertEquals(40.0, active.weight, 0.0)
    }

    @Test
    fun `after a failed finish the first set of an added exercise is appended`() = runTest {
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val manager = buildManager(getWorkout, mockk(relaxed = true), finish)
        manager.start(1)
        manager.onExerciseFinished() // последний подход — запись падает
        assertTrue(manager.finishError.value)
        manager.dismissFinishError()

        manager.addExerciseToday(fromBack) // два подхода тяги
        manager.onExerciseFinished() // присед ещё раз — заменяет неудачный, ведёт на отдых
        manager.skipRest()
        manager.onExerciseFinished() // тяга, подход 1 — дописывается
        manager.skipRest()
        manager.onExerciseFinished() // тяга, подход 2 — последний, запись проходит

        val squat = RecordedSet(10L, "Присед", 50.0, 0.0, 8, ExerciseUnit.KG)
        val row = RecordedSet(210L, "Тяга блока", 5.0, 2.0, 12, ExerciseUnit.PLATE)
        assertEquals(2, attempts.size)
        assertEquals(listOf(squat, row, row), attempts.last())
        assertNotNull(manager.phase as? WorkoutExecutionState.Finished)
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.WorkoutSessionManagerTest'`
Expected: FAIL — `Unresolved reference: addExerciseToday`, `AddResult`.

- [ ] **Step 2: Результат добавления**

Дописать в `WorkoutSession.kt`:

```kotlin
/** Итог «+ в сегодняшнюю». */
enum class AddResult { ADDED, ALREADY_ADDED, NO_SESSION }
```

- [ ] **Step 3: Добавление и правки только в сессии**

В `WorkoutSessionManager.kt`:

1. Импорт `import ru.hopes.workouttimer.domain.model.Exercise`.

2. После `moveToExercise()` добавить:

```kotlin
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
```

3. `updateExerciseNote()` — добавленное упражнение меняется сразу, без БД:

```kotlin
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
```

4. `updateExerciseWeightAndReps()` — запись в БД только для строки шаблона; последние строки функции:

```kotlin
        setSession(current.copy(exercises = exercises, phase = newPhase))
        // Правка добавленного упражнения не трогает шаблон чужой тренировки.
        if (!row.addedToday) {
            sessionScope.launch {
                workoutRepository.updateExerciseWeightAndReps(row.exercise.id, weight, extraWeight, reps)
            }
        }
    }
```

5. `onExerciseFinished()` — флаг замены снимается сразу после использования (ветка завершения поднимет его снова):

```kotlin
        // Повтор после сбоя записи: последний подход уже в списке — заменяем
        // его текущими значениями (плитку могли поправить), а не дублируем. Флаг снимается
        // сразу: если после сбоя добавили упражнение, его подходы должны дописываться.
        if (finishPending && _recordedSets.isNotEmpty()) {
            _recordedSets[_recordedSets.lastIndex] = set
            finishPending = false
        } else {
            _recordedSets += set
        }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.session.*'`
Expected: PASS — `WorkoutSessionManagerTest` 62 теста (все прежние сценарии сбоя записи тоже зелёные: повтор после сбоя снова идёт в ветку завершения и поднимает флаг).

- [ ] **Step 4: Метка «сегодня» в шторке выбора**

`strings.xml` — после `<string name="execution_exit_without_saving">Выйти без сохранения</string>`:

```xml
    <string name="execution_added_today">сегодня</string>
```

В `WorkoutExecutionScreen.kt` в шторке выбора (`chrome.exercises.forEachIndexed { index, row -> … }`) второй `Text` строки (название) и метку заменить на:

```kotlin
                    Text(
                        text = row.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Добавленные «+ в сегодняшнюю» стоят в конце списка с пометкой.
                    if (row.addedToday) {
                        Text(
                            text = stringResource(R.string.execution_added_today),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
```

Сегменты прогресса и «n/m» в шапке уже считают все строки списка (`chrome.totalExercises`).

- [ ] **Step 5: Полная проверка и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
git add app/src/main/java/ru/hopes/workouttimer/presentation/session \
        app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/ru/hopes/workouttimer/presentation/session/WorkoutSessionManagerTest.kt
git commit -m "feat: «+ в сегодняшнюю» — упражнение другой тренировки в конец сессии

Копия дописывается один раз на исходную строку, только в Rest/Active.
Правки веса и заметки копии остаются в сессии, подходы пишутся в
историю идущей тренировки. После сбоя записи следующий подход
добавленного упражнения дописывается, а не заменяет предыдущий.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: ViewModel экрана просмотра

**Files:**
- Create: `presentation/screen/workoutPreview/WorkoutPreviewViewModel.kt`
- Test: `presentation/screen/workoutPreview/WorkoutPreviewViewModelTest.kt` (новый)

**Interfaces:**
- Consumes: `ObserveWorkoutUseCase(id): Flow<Workout?>` (Task 8), `ExerciseCatalogRepository.observeLastSessionSets(): Flow<List<LoggedSet>>`, `GetLastSessionDurationsUseCase(): Flow<Map<Int, Long>>`, `WorkoutSessionManager.runningWorkout`, `addExerciseToday(exercise): AddResult` (Task 2, 9).
- Produces:
  - `data class LastTime(val finishedAt: Long, val sets: List<SessionSet>)`; `fun lastTimeByCatalog(sets: List<LoggedSet>): Map<Long, LastTime>`
  - `sealed interface PreviewBottom { None; Start; Return; RunningOther(workoutName: String) }`; `fun previewBottomOf(workoutId: Int, workout: Workout?, running: RunningWorkout?): PreviewBottom`
  - `data class WorkoutPreviewState(isLoaded, loadFailed, workout: Workout?, lastTime: Map<Long, LastTime>, lastDurationMillis: Long?, bottom: PreviewBottom, addedExerciseIds: Set<Int>)` с `canAddToday: Boolean`
  - `WorkoutPreviewViewModel`: `state: StateFlow<WorkoutPreviewState>`, `fun load(workoutId: Int)`, `fun addToday(exercise: Exercise): AddResult`

- [ ] **Step 1: Падающие тесты**

`app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutPreview/WorkoutPreviewViewModelTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutPreview

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveWorkoutUseCase
import ru.hopes.workouttimer.presentation.session.AddResult
import ru.hopes.workouttimer.presentation.session.RunningWorkout
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPreviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val press = Exercise(id = 5, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, order = 1, catalogId = 50L)
    private val back = Workout(id = 2, name = "Спина", exercises = listOf(press), lastUseAt = 0L)

    private val workout = MutableStateFlow<Workout?>(back)
    private val lastSets = MutableStateFlow<List<LoggedSet>>(emptyList())
    private val durations = MutableStateFlow<Map<Int, Long>>(mapOf(2 to 3_120_000L))
    private val running = MutableStateFlow<RunningWorkout?>(null)
    private val manager = mockk<WorkoutSessionManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { manager.runningWorkout } returns running
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(observe: ObserveWorkoutUseCase = mockk { every { this@mockk(2) } returns workout }): WorkoutPreviewViewModel {
        val catalog = mockk<ExerciseCatalogRepository> { every { observeLastSessionSets() } returns lastSets }
        val getDurations = mockk<GetLastSessionDurationsUseCase> { every { this@mockk() } returns durations }
        return WorkoutPreviewViewModel(observe, catalog, getDurations, manager).also { it.load(2) }
    }

    private fun kg(sessionId: Long, catalogId: Long, weight: Double, reps: Int) =
        SessionSet(id = 0, sessionId = sessionId, catalogId = catalogId, weight = weight, extraWeight = 0.0, reps = reps, unit = ExerciseUnit.KG)

    @Test
    fun `без сессии — начать`() {
        val state = viewModel().state.value

        assertTrue(state.isLoaded)
        assertEquals(PreviewBottom.Start, state.bottom)
        assertFalse(state.canAddToday)
        assertEquals(3_120_000L, state.lastDurationMillis)
    }

    @Test
    fun `идёт эта тренировка — вернуться`() {
        running.value = RunningWorkout(2, "Спина", isLoading = false, addedExerciseIds = emptySet())

        assertEquals(PreviewBottom.Return, viewModel().state.value.bottom)
    }

    @Test
    fun `идёт другая тренировка — подсказка и добавление`() {
        running.value = RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = emptySet())

        val state = viewModel().state.value

        assertEquals(PreviewBottom.RunningOther("Ноги"), state.bottom)
        assertTrue(state.canAddToday)
    }

    @Test
    fun `пока сессия загружается — ничего`() {
        running.value = RunningWorkout(1, "", isLoading = true, addedExerciseIds = emptySet())

        assertEquals(PreviewBottom.None, viewModel().state.value.bottom)
    }

    @Test
    fun `сессия закончилась — снова начать`() {
        running.value = RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = emptySet())
        val vm = viewModel()

        running.value = null // Finished, Error и выход дают null

        assertEquals(PreviewBottom.Start, vm.state.value.bottom)
    }

    @Test
    fun `пустую тренировку не начать`() {
        workout.value = back.copy(exercises = emptyList())

        assertEquals(PreviewBottom.None, viewModel().state.value.bottom)
    }

    @Test
    fun `удалённая тренировка — не найдена`() {
        workout.value = null

        val state = viewModel().state.value

        assertTrue(state.isLoaded)
        assertNull(state.workout)
        assertEquals(PreviewBottom.None, state.bottom)
    }

    @Test
    fun `новый состав после правки виден сразу`() {
        val vm = viewModel()

        workout.value = back.copy(exercises = listOf(press, press.copy(id = 6, name = "Разводка")))

        assertEquals(listOf("Жим лёжа", "Разводка"), vm.state.value.workout?.exercises?.map { it.name })
    }

    @Test
    fun `прошлый раз — по записи справочника`() {
        lastSets.value = listOf(
            LoggedSet(kg(sessionId = 7, catalogId = 50, weight = 60.0, reps = 8), "Жим лёжа", finishedAt = 1_000L),
            LoggedSet(kg(sessionId = 7, catalogId = 50, weight = 62.5, reps = 6), "Жим лёжа", finishedAt = 1_000L)
        )

        val lastTime = viewModel().state.value.lastTime[50L]

        assertEquals(1_000L, lastTime?.finishedAt)
        assertEquals(listOf(60.0, 62.5), lastTime?.sets?.map { it.weight })
    }

    @Test
    fun `прошлый раз — подходы самой поздней сессии`() {
        val grouped = lastTimeByCatalog(
            listOf(
                LoggedSet(kg(sessionId = 3, catalogId = 50, weight = 55.0, reps = 8), "Жим лёжа", finishedAt = 500L),
                LoggedSet(kg(sessionId = 7, catalogId = 50, weight = 60.0, reps = 8), "Жим лёжа", finishedAt = 1_000L),
                LoggedSet(kg(sessionId = 7, catalogId = 51, weight = 20.0, reps = 12), "Разводка", finishedAt = 1_000L)
            )
        )

        assertEquals(LastTime(1_000L, listOf(kg(7, 50, 60.0, 8))), grouped[50L])
        assertEquals(listOf(20.0), grouped[51L]?.sets?.map { it.weight })
    }

    @Test
    fun `добавленное упражнение отмечено`() {
        running.value = RunningWorkout(1, "Ноги", isLoading = false, addedExerciseIds = setOf(5))

        assertEquals(setOf(5), viewModel().state.value.addedExerciseIds)
    }

    @Test
    fun `добавление идёт в менеджер`() {
        every { manager.addExerciseToday(press) } returns AddResult.ADDED

        assertEquals(AddResult.ADDED, viewModel().addToday(press))
        verify { manager.addExerciseToday(press) }
    }

    @Test
    fun `сбой чтения — сообщение, а не падение`() {
        val failing = mockk<ObserveWorkoutUseCase> {
            every { this@mockk(2) } returns flow { throw IllegalStateException("disk I/O error") }
        }

        val state = viewModel(observe = failing).state.value

        assertTrue(state.isLoaded)
        assertTrue(state.loadFailed)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workoutPreview.WorkoutPreviewViewModelTest'`
Expected: FAIL — `Unresolved reference: WorkoutPreviewViewModel`.

- [ ] **Step 2: ViewModel и чистые правила просмотра**

`presentation/screen/workoutPreview/WorkoutPreviewViewModel.kt`:

```kotlin
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
                addedExerciseIds = running?.addedExerciseIds.orEmpty()
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
    val addedExerciseIds: Set<Int> = emptySet()
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
```

- [ ] **Step 3: Тесты зелёные, полная проверка, коммит**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.hopes.workouttimer.presentation.screen.workoutPreview.WorkoutPreviewViewModelTest'`
Expected: PASS, 13 тестов.

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutPreview \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutPreview
git commit -m "feat: ViewModel экрана просмотра тренировки

Тренировка, «прошлый раз» по записи справочника, длительность прошлой
сессии и сводка идущей сессии в одном состоянии; нижняя зона —
«Начать», «Вернуться» или подсказка с добавлением в сегодняшнюю.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Экран просмотра, маршрут и тап по тренировке в списке

Конец M3: тап по строке списка и по карточке «Следующая» открывает просмотр; «НАЧАТЬ», «ВЕРНУТЬСЯ» и меню работают по правилам Task 7.

**Files:**
- Create: `presentation/screen/workoutPreview/WorkoutPreviewScreen.kt`
- Modify: `presentation/navigation/NavGraph.kt` (маршрут просмотра, `onOpen` списка)
- Modify: `presentation/screen/workouts/ListWorkoutScreen.kt` (`NextWorkoutCard` кликабельна)
- Modify: `app/src/main/res/values/strings.xml` (секция «Просмотр тренировки»)
- Test (androidTest): `presentation/screen/workoutPreview/WorkoutPreviewContentTest.kt` (новый), `presentation/screen/workouts/QueueContentTest.kt`

**Interfaces:**
- Consumes: `WorkoutPreviewViewModel`, `WorkoutPreviewState`, `PreviewBottom`, `LastTime` (Task 10); `AddResult` (Task 9); `formatPlannedSet`, `Screen.WorkoutPreview` (Task 8); `formatSetList`, `setFormat()`, `daysAgoText`, `DateFormatter.formatDurationCompact`; `returnToSession` в `NavGraph` (Task 5).
- Produces:
  - `@Composable fun WorkoutPreviewScreen(workoutId: Int, onNavigateBack: () -> Unit, onStart: () -> Unit, onReturn: () -> Unit, onOpenProgress: (Long) -> Unit, viewModel: WorkoutPreviewViewModel = hiltViewModel())`
  - `@Composable internal fun WorkoutPreviewContent(state: WorkoutPreviewState, onStart: () -> Unit, onReturn: () -> Unit, onOpenProgress: (Long) -> Unit, onAddToday: (Exercise) -> Unit)`
  - строки `preview_*`

- [ ] **Step 1: Строки просмотра**

В `strings.xml` после секции плашки (после `<string name="minibar_exit">…</string>`) вставить:

```xml

    <!-- Просмотр тренировки -->
    <string name="preview_start">Начать</string>
    <string name="preview_return">Вернуться к тренировке</string>
    <string name="preview_running_other">Идёт «%1$s» — упражнение можно добавить в неё</string>
    <string name="preview_add_today">В сегодняшнюю</string>
    <string name="preview_add_today_description">Добавить «%1$s» в сегодняшнюю тренировку</string>
    <string name="preview_added">Добавлено</string>
    <string name="preview_added_snackbar">%1$s — в сегодняшней тренировке</string>
    <plurals name="preview_sets">
        <item quantity="one">%1$d подход</item>
        <item quantity="few">%1$d подхода</item>
        <item quantity="many">%1$d подходов</item>
        <item quantity="other">%1$d подхода</item>
    </plurals>
    <string name="preview_rest">отдых %1$s</string>
    <string name="preview_last_time">Прошлый раз · %1$s: %2$s</string>
    <string name="preview_never">Ещё не делали</string>
    <string name="preview_open_progress">%1$s, открыть прогресс</string>
    <string name="preview_not_found">Тренировка не найдена</string>
    <string name="preview_empty">В тренировке нет упражнений</string>
    <string name="preview_error_load">Не удалось загрузить тренировку</string>
```

- [ ] **Step 2: Падающий Compose-тест просмотра**

`app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workoutPreview/WorkoutPreviewContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutPreview

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class WorkoutPreviewContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val press = Exercise(
        id = 5, name = "Жим лёжа", weight = 60.0, sets = 4, reps = 8, timeMillis = 120_000L,
        order = 1, catalogId = 50L
    )
    private val flyes = Exercise(
        id = 6, name = "Разводка", weight = 0.0, sets = 3, reps = 12, timeMillis = 60_000L,
        order = 2, catalogId = 51L, unit = ExerciseUnit.BODYWEIGHT
    )
    private val back = Workout(id = 2, name = "Спина", exercises = listOf(press, flyes), lastUseAt = 0L)

    private var started = 0
    private var returned = 0
    private val progress = mutableListOf<Long>()
    private val added = mutableListOf<Exercise>()

    private fun kg(weight: Double, reps: Int) =
        SessionSet(id = 0, sessionId = 7, catalogId = 50, weight = weight, extraWeight = 0.0, reps = reps, unit = ExerciseUnit.KG)

    private fun show(state: WorkoutPreviewState) = composeRule.setContent {
        WorkoutTimerTheme {
            WorkoutPreviewContent(
                state = state,
                onStart = { started++ },
                onReturn = { returned++ },
                onOpenProgress = { progress += it },
                onAddToday = { added += it }
            )
        }
    }

    private fun loaded(bottom: PreviewBottom, addedIds: Set<Int> = emptySet()) = WorkoutPreviewState(
        isLoaded = true,
        workout = back,
        lastTime = mapOf(
            50L to LastTime(
                finishedAt = System.currentTimeMillis() - 3 * 86_400_000L - 3_600_000L,
                sets = listOf(kg(60.0, 8), kg(60.0, 8), kg(62.5, 6))
            )
        ),
        lastDurationMillis = 3_120_000L,
        bottom = bottom,
        addedExerciseIds = addedIds
    )

    @Test
    fun строка_показывает_план_и_прошлый_раз() {
        show(loaded(PreviewBottom.Start))

        composeRule.onNodeWithText("4 подхода · 60 кг × 8 · отдых 02:00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Прошлый раз · 3 дн. назад: 60 кг × 8 · 60 × 8 · 62.5 × 6", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("3 подхода · 12 · отдых 01:00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ещё не делали", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 упр · 52:00", useUnmergedTree = true).assertIsDisplayed()
    }

    // PrimaryButton пишет текст прописными: «Начать» на экране — «НАЧАТЬ».
    @Test
    fun без_сессии_кнопка_начать() {
        show(loaded(PreviewBottom.Start))

        composeRule.onNodeWithText("НАЧАТЬ").performClick()
        composeRule.onNodeWithText("В сегодняшнюю").assertDoesNotExist()

        composeRule.runOnIdle { assertEquals(1, started) }
    }

    @Test
    fun идёт_эта_тренировка_вернуться() {
        show(loaded(PreviewBottom.Return))

        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithText("ВЕРНУТЬСЯ К ТРЕНИРОВКЕ").performClick()

        composeRule.runOnIdle { assertEquals(1, returned) }
    }

    @Test
    fun идёт_другая_подсказка_и_добавление_в_сегодняшнюю() {
        show(loaded(PreviewBottom.RunningOther("Ноги")))

        composeRule.onNodeWithText("Идёт «Ноги» — упражнение можно добавить в неё").assertIsDisplayed()
        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Добавить «Жим лёжа» в сегодняшнюю тренировку").performClick()

        composeRule.runOnIdle { assertEquals(listOf(press), added) }
    }

    @Test
    fun добавленное_упражнение_неактивно() {
        show(loaded(PreviewBottom.RunningOther("Ноги"), addedIds = setOf(5)))

        composeRule.onNodeWithText("Добавлено").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Добавить «Разводка» в сегодняшнюю тренировку").assertIsDisplayed()
    }

    @Test
    fun тап_по_строке_открывает_прогресс_с_меткой() {
        show(loaded(PreviewBottom.Start))

        composeRule.onNodeWithText("Жим лёжа")
            .assert(
                SemanticsMatcher("метка «Жим лёжа, открыть прогресс»") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Жим лёжа, открыть прогресс"
                }
            )
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(50L), progress) }
    }

    @Test
    fun пустая_тренировка_без_кнопки_начать() {
        show(WorkoutPreviewState(isLoaded = true, workout = back.copy(exercises = emptyList()), bottom = PreviewBottom.None))

        composeRule.onNodeWithText("В тренировке нет упражнений").assertIsDisplayed()
        composeRule.onNodeWithText("НАЧАТЬ").assertDoesNotExist()
    }

    @Test
    fun удалённая_тренировка_не_найдена() {
        show(WorkoutPreviewState(isLoaded = true, workout = null))

        composeRule.onNodeWithText("Тренировка не найдена").assertIsDisplayed()
    }
}
```

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `Unresolved reference: WorkoutPreviewContent`.

- [ ] **Step 3: Экран просмотра**

`presentation/screen/workoutPreview/WorkoutPreviewScreen.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutPreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.session.AddResult
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.daysAgoText
import ru.hopes.workouttimer.presentation.utils.formatPlannedSet
import ru.hopes.workouttimer.presentation.utils.formatSetList
import ru.hopes.workouttimer.presentation.utils.setFormat

// Разделитель частей плана — знак препинания, а не текст: в ресурсы не выносится (как в SetFormat).
private const val PLAN_SEPARATOR = " · "

@Composable
fun WorkoutPreviewScreen(
    workoutId: Int,
    onNavigateBack: () -> Unit,
    onStart: () -> Unit,
    onReturn: () -> Unit,
    onOpenProgress: (Long) -> Unit,
    viewModel: WorkoutPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    LaunchedEffect(workoutId) {
        viewModel.load(workoutId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            PreviewHeader(name = state.workout?.name.orEmpty(), onNavigateBack = onNavigateBack)
            WorkoutPreviewContent(
                state = state,
                onStart = onStart,
                onReturn = onReturn,
                onOpenProgress = onOpenProgress,
                onAddToday = { exercise ->
                    if (viewModel.addToday(exercise) == AddResult.ADDED) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.preview_added_snackbar, exercise.name)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PreviewHeader(name: String, onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Тело экрана под шапкой: сводка, упражнения, нижняя зона. Вынесено из
 * [WorkoutPreviewScreen], чтобы тестировать и превьюшить без hiltViewModel().
 */
@Composable
internal fun WorkoutPreviewContent(
    state: WorkoutPreviewState,
    onStart: () -> Unit,
    onReturn: () -> Unit,
    onOpenProgress: (Long) -> Unit,
    onAddToday: (Exercise) -> Unit
) {
    // До первого ответа базы пустое состояние не показываем — оно мигнуло бы.
    if (!state.isLoaded) return
    if (state.loadFailed) {
        EmptyState(icon = Icons.Default.ErrorOutline, title = stringResource(R.string.preview_error_load))
        return
    }
    val workout = state.workout
    if (workout == null) {
        EmptyState(icon = Icons.Default.FitnessCenter, title = stringResource(R.string.preview_not_found))
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (workout.exercises.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(icon = Icons.Default.FitnessCenter, title = stringResource(R.string.preview_empty))
            }
        } else {
            val format = setFormat()
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(CardSpacing)
            ) {
                item(key = "summary") {
                    Text(
                        text = summaryLine(workout.exercises.size, state.lastDurationMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(workout.exercises, key = { it.id }) { exercise ->
                    PreviewExerciseRow(
                        exercise = exercise,
                        lastTime = state.lastTime[exercise.catalogId],
                        format = format,
                        canAdd = state.canAddToday,
                        added = exercise.id in state.addedExerciseIds,
                        onOpen = { onOpenProgress(exercise.catalogId) },
                        onAdd = { onAddToday(exercise) }
                    )
                }
            }
        }
        PreviewBottomZone(bottom = state.bottom, onStart = onStart, onReturn = onReturn)
    }
}

/** «6 упр · 52:10» — те же строки, что метаданные списка тренировок. */
@Composable
private fun summaryLine(count: Int, durationMillis: Long?): String =
    if (durationMillis != null) {
        stringResource(R.string.list_meta_with_duration, count, DateFormatter.formatDurationCompact(durationMillis))
    } else {
        stringResource(R.string.list_meta, count)
    }

@Composable
private fun PreviewExerciseRow(
    exercise: Exercise,
    lastTime: LastTime?,
    format: SetFormat,
    canAdd: Boolean,
    added: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Вся строка, кроме кнопки добавления, — тап на прогресс упражнения. Метка нажатия,
        // а не описание: описание заменило бы для TalkBack план и «прошлый раз».
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    onClickLabel = stringResource(R.string.preview_open_progress, exercise.name),
                    role = Role.Button,
                    onClick = onOpen
                )
                .padding(12.dp)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = planLine(exercise, format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = lastTimeLine(lastTime, format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (exercise.note.isNotBlank()) {
                Text(
                    text = exercise.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (canAdd) {
            AddTodayButton(name = exercise.name, added = added, onAdd = onAdd)
        }
    }
}

/** «4 подхода · 60 кг × 8 · отдых 02:00»; без веса — «3 подхода · 12 · отдых 01:00». */
@Composable
private fun planLine(exercise: Exercise, format: SetFormat): String = listOf(
    pluralStringResource(R.plurals.preview_sets, exercise.sets, exercise.sets),
    formatPlannedSet(exercise.unit, exercise.weight, exercise.extraWeight, exercise.reps, format),
    stringResource(R.string.preview_rest, DateFormatter.formatDurationCompact(exercise.timeMillis))
).joinToString(PLAN_SEPARATOR)

/** Последняя сессия упражнения из любой тренировки — тот же источник, что у «Упражнений». */
@Composable
private fun lastTimeLine(lastTime: LastTime?, format: SetFormat): String =
    if (lastTime == null || lastTime.sets.isEmpty()) {
        stringResource(R.string.preview_never)
    } else {
        stringResource(
            R.string.preview_last_time,
            daysAgoText(lastTime.finishedAt),
            formatSetList(lastTime.sets, format)
        )
    }

@Composable
private fun AddTodayButton(name: String, added: Boolean, onAdd: () -> Unit) {
    val description = stringResource(R.string.preview_add_today_description, name)
    TextButton(
        onClick = onAdd,
        enabled = !added,
        modifier = Modifier
            .padding(end = 4.dp)
            .heightIn(min = 48.dp)
            .then(if (added) Modifier else Modifier.semantics { contentDescription = description })
    ) {
        if (!added) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = stringResource(if (added) R.string.preview_added else R.string.preview_add_today),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Нижняя зона над плашкой: «Начать», «Вернуться к тренировке» или подсказка. */
@Composable
private fun PreviewBottomZone(bottom: PreviewBottom, onStart: () -> Unit, onReturn: () -> Unit) {
    when (bottom) {
        PreviewBottom.None -> Unit
        PreviewBottom.Start -> PrimaryButton(
            text = stringResource(R.string.preview_start),
            onClick = onStart,
            modifier = Modifier.padding(ScreenPadding)
        )
        PreviewBottom.Return -> PrimaryButton(
            text = stringResource(R.string.preview_return),
            onClick = onReturn,
            modifier = Modifier.padding(ScreenPadding)
        )
        is PreviewBottom.RunningOther -> Text(
            text = stringResource(R.string.preview_running_other, bottom.workoutName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 16.dp)
        )
    }
}
```

- [ ] **Step 4: Маршрут просмотра и тап по тренировке в списке**

В `NavGraph.kt`:

1. Импорт `import ru.hopes.workouttimer.presentation.screen.workoutPreview.WorkoutPreviewScreen`.

2. В `composable(Screen.Workouts.route)` заменить временный `onOpen` из Task 7:

```kotlin
                    // Тап по тренировке открывает просмотр: старт — явной кнопкой.
                    onOpen = { workout ->
                        navController.navigate(Screen.WorkoutPreview.createRoute(workout.id))
                    },
```

3. После `composable(Screen.History.route …) { … }` добавить:

```kotlin
            // Просмотр тренировки: состав, «прошлый раз», «Начать» / «Вернуться» / «+ в сегодняшнюю»
            composable(
                route = Screen.WorkoutPreview.route,
                arguments = listOf(
                    navArgument("workout_id") { type = NavType.IntType }
                )
            ) { entry ->
                val workoutId = Screen.WorkoutPreview.getWorkoutId(entry.arguments)
                WorkoutPreviewScreen(
                    workoutId = workoutId,
                    onNavigateBack = { navController.popBackStack() },
                    // Просмотр заменяется выполнением: «Назад» (сворачивание) ведёт на список,
                    // как после старта из списка.
                    onStart = {
                        navController.navigate(Screen.Execution.createRoute(workoutId)) {
                            popUpTo(Screen.WorkoutPreview.route) { inclusive = true }
                        }
                    },
                    // Просмотр остаётся под выполнением: «Назад» вернёт сюда.
                    onReturn = returnToSession,
                    onOpenProgress = { catalogId ->
                        navController.navigate(Screen.ExerciseProgress.createRoute(catalogId))
                    }
                )
            }
```

- [ ] **Step 5: Карточка «Следующая» открывает просмотр**

В `ListWorkoutScreen.kt`:

1. Импорт `import androidx.compose.foundation.clickable`.
2. `NextWorkoutCard` — новый параметр `onOpen: () -> Unit` (после `item`), внешний `Box`:

```kotlin
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Accent, AccentDark)))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
```

3. В `QueueContent` вызов карточки получает `onOpen = { onOpen(next.workout) },`.

В `QueueContentTest.kt` добавить тест:

```kotlin
    @Test
    fun тап_по_карточке_следующей_открывает_просмотр() {
        show(runningWorkoutId = null)

        // Клик по названию, а не по центру карточки: центр близко к кнопке «НАЧАТЬ».
        composeRule.onNodeWithText("Ноги", useUnmergedTree = true).performClick()

        composeRule.runOnIdle { assertEquals(listOf(1), opened) }
    }
```

- [ ] **Step 6: Полная проверка, инструментальные тесты и коммит**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
./gradlew :app:lintDebug && grep -c 'id="UnusedResources"' app/build/reports/lint-results-debug.xml   # 0 — все новые строки используются
git status --short app/schemas    # пусто
grep -rnP '\x{00A0}|\x{2212}' app/src   # пусто
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.workoutPreview.WorkoutPreviewContentTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.workouts.QueueContentTest
```

Expected: `WorkoutPreviewContentTest` 8, `QueueContentTest` 5 — зелёные. Если `UnusedResources` не 0 — найти строку в отчёте и убрать или задействовать (по спеке лишних строк нет).

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutPreview \
        app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt \
        app/src/main/res/values/strings.xml \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workoutPreview \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workouts/QueueContentTest.kt
git commit -m "feat: экран просмотра тренировки и «+ в сегодняшнюю»

Тап по тренировке в списке открывает просмотр: план и «прошлый раз»
по каждому упражнению, тап — прогресс. Внизу «Начать», «Вернуться к
тренировке» или, пока идёт другая, подсказка и кнопки «+ В сегодняшнюю».

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Проверка на эмуляторе

**Files:** нет изменений кода (кроме исправлений, если проверка что-то найдёт: каждое исправление — отдельный коммит `fix:` с тестом, который ловит найденное, и повтор `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin`).

Всё — только на `emulator-5554`. Скриншоты — в scratchpad текущей сессии; каждый просмотреть (Read) и сверить с ожиданием.

- [ ] **Step 1: Полный прогон**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug :app:compileDebugAndroidTestKotlin
git status --short app/schemas                                                # пусто: схема v8 не менялась
git diff master --stat -- app/build.gradle.kts gradle/libs.versions.toml      # пусто: зависимостей не добавлено
grep -rnP '\x{00A0}|\x{2212}' app/src                                         # пусто
grep -rn "ActiveWorkoutTracker" app/src                                       # пусто
~/Library/Android/sdk/platform-tools/adb devices                              # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Expected: всё зелёное. Новые инструментальные классы: `MiniWorkoutBarContentTest` (6), `SessionNavigationTest` (3), `ActionSheetContentTest` (2), `QueueContentTest` (5), `WorkoutPreviewContentTest` (8); прежние классы (`ActiveContentTilesTest`, `ExerciseProgressRouteTest` и др.) — без падений.

- [ ] **Step 2: Подготовка**

```bash
SCRATCH=<scratchpad текущей сессии>/minimized-check
mkdir -p "$SCRATCH"
ADB=~/Library/Android/sdk/platform-tools/adb
SQLITE=~/Library/Android/sdk/platform-tools/sqlite3
PKG=ru.hopes.workouttimer
$ADB -s emulator-5554 shell getprop ro.build.version.sdk     # записать: от него зависит пункт про клавиатуру
$ADB -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s emulator-5554 shell pm clear $PKG
$ADB -s emulator-5554 shell pm grant $PKG android.permission.POST_NOTIFICATIONS
```

Помощники (`$SCRATCH/tap.py` — тап по видимому тексту или описанию через дамп `uiautomator`):

```python
import re
import subprocess
import sys

adb, needle = sys.argv[1], sys.argv[2]
subprocess.run([adb, "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
xml = subprocess.run([adb, "-s", "emulator-5554", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
for match in re.finditer(r"<node [^>]*>", xml):
    node = match.group(0)
    text = re.search(r' text="([^"]*)"', node).group(1)
    desc = re.search(r' content-desc="([^"]*)"', node).group(1)
    if needle in (text, desc):
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    sys.exit("не найдено: " + needle)
```

```bash
tap()  { $ADB -s emulator-5554 shell input tap $(python3 "$SCRATCH/tap.py" "$ADB" "$1"); sleep 1; }
shot() { $ADB -s emulator-5554 exec-out screencap -p > "$SCRATCH/$1.png"; }
back() { $ADB -s emulator-5554 shell input keyevent KEYCODE_BACK; sleep 1; }
```

Данные — импортом (как в проверке E2/E3). `$SCRATCH/mw-import.json` (отдых 20 с — отсчёт видно на плашке и ждать недолго):

```json
{"version":1,"exportDate":"2026-10-01","appVersion":"1.0","workouts":[
 {"name":"Ноги","lastUseAt":1,"exercises":[
   {"name":"Присед","weight":60.0,"sets":2,"reps":8,"restTimeMillis":20000,"order":1,"note":"","unit":"KG","extraWeight":0.0},
   {"name":"Жим ногами","weight":120.0,"sets":1,"reps":10,"restTimeMillis":20000,"order":2,"note":"","unit":"KG","extraWeight":0.0}]},
 {"name":"Спина","lastUseAt":2,"exercises":[
   {"name":"Тяга блока","weight":5.0,"sets":2,"reps":12,"restTimeMillis":20000,"order":1,"note":"локти вниз","unit":"PLATE","extraWeight":2.0},
   {"name":"Подтягивания","weight":0.0,"sets":1,"reps":8,"restTimeMillis":20000,"order":2,"note":"","unit":"BODYWEIGHT","extraWeight":0.0}]}
]}
```

`$ADB -s emulator-5554 push "$SCRATCH/mw-import.json" /sdcard/Download/`, в приложении: список → «Экспорт и импорт» → «Импортировать тренировки» → выбрать файл (по пикеру — `tap` по видимым подписям). Id тренировок — из копии базы:

```bash
$ADB -s emulator-5554 shell am force-stop $PKG
for f in workout_db workout_db-wal workout_db-shm; do
  $ADB -s emulator-5554 exec-out run-as $PKG cat databases/$f > "$SCRATCH/$f"
done
$SQLITE "$SCRATCH/workout_db" "SELECT id, name FROM workouts;"   # записать id «Ноги» и «Спина»
$SQLITE "$SCRATCH/workout_db" "SELECT COUNT(*) FROM workout_sessions;"   # 0
```

- [ ] **Step 3: Сворачивание, плашка, «пора» (ручные пункты спеки 1, 2)**

Запустить приложение, «Ноги» — карточка «Следующая» → «НАЧАТЬ» → «Закончить подход» (отдых 20 с) → `back`:

- `mw-01-bar-rest.png` — список, внизу плашка: «Ноги» серым, «Отдых 00:1x», слева дуга `primary` на дорожке; FAB «Новая» выше плашки, плашка не перекрывает контент. Через 2 с ещё снимок — отсчёт уменьшился.
- `$ADB -s emulator-5554 shell cmd statusbar expand-notifications`, `mw-02-notification.png` — уведомление отдыха обновляется; `cmd statusbar collapse`.
- `$ADB -s emulator-5554 shell input keyevent KEYCODE_SLEEP`, подождать 25 с, `KEYCODE_WAKEUP`, `$ADB -s emulator-5554 shell wm dismiss-keyguard`: `mw-03-rest-over.png` — плашка «Пора: подход 2» лаймом, кольцо сплошное; в шторке уведомлений «Отдых завершён».

- [ ] **Step 4: Просмотр другой тренировки и «+ в сегодняшнюю» (пункт 3)**

`tap "Спина"` (строка очереди):

- `mw-04-preview-other.png` — шапка «Спина», «2 упр»; «Тяга блока», «2 подхода · плита 5 +2 кг × 12 · отдых 00:20», «Ещё не делали», «локти вниз»; «Подтягивания», «1 подход · 8 · отдых 00:20»; у строк «+ В сегодняшнюю»; внизу «Идёт «Ноги» — упражнение можно добавить в неё», кнопки «Начать» нет; ниже — плашка.
- `tap "Добавить «Тяга блока» в сегодняшнюю тренировку"`: `mw-05-added.png` — снекбар «Тяга блока — в сегодняшней тренировке» над плашкой, у строки неактивное «Добавлено». `back` и снова `tap "Спина"` — «Добавлено» на месте.
- `tap "Подтягивания"` — экран прогресса упражнения (пустое состояние E3), `back`.

- [ ] **Step 5: Возврат, добавленное упражнение, последний подход (пункты 4, 5)**

`tap "Ноги"` на плашке (тап по названию — вся плашка одна зона):

- открылось выполнение «Ноги»; тап по чипу упражнения — `mw-06-picker.png`: три строки, «Тяга блока» последней с меткой «сегодня»; сегменты прогресса — 3. Закрыть шторку.
- «Закончить подход» (присед 2/2) → отдых → «Пропустить отдых» → «Жим ногами» → «Закончить подход» **без** диалога завершения (последним стал «Тяга блока») → отдых → «Пропустить отдых» → плитка плиты («5», «ПЛИТА +2 КГ») → в шторке барабан плиты на 6 → «Готово» → «Закончить подход» → «Пропустить отдых» → «Закончить подход» — диалог «Завершить тренировку?» → «Завершить»: `mw-07-finished.png` — экран итогов «Ноги», плашки нет.
- «На главную» → плашки нет. `tap "Спина"` → `mw-08-preview-last-time.png`: план «плита 5 +2 кг × 12» (шаблон не изменился), «Прошлый раз · сегодня: плита 6 +2 кг × 12 · плита 6 +2 кг × 12»; внизу «Начать».
- Меню «Ноги» → «История» → верхняя сессия развёрнута: `mw-09-history.png` — есть строки «Присед», «Жим ногами», «Тяга блока — плита 6 +2 кг × 12 · …».

```bash
$ADB -s emulator-5554 shell am force-stop $PKG
for f in workout_db workout_db-wal workout_db-shm; do
  $ADB -s emulator-5554 exec-out run-as $PKG cat databases/$f > "$SCRATCH/$f"
done
$SQLITE "$SCRATCH/workout_db" "SELECT COUNT(*) FROM workout_sessions;"          # 1
$SQLITE "$SCRATCH/workout_db" "SELECT weight, extraWeight FROM exercises WHERE name = 'Тяга блока';"   # 5.0|2.0 — шаблон «Спины» не тронут
```

- [ ] **Step 6: Выход без сохранения — крестиком и из меню (пункт 6)**

- «НАЧАТЬ» у «Ноги» → «Закончить подход» → `back` → на плашке `tap "Выйти из тренировки без сохранения"` → диалог «Выйти из тренировки? Прогресс не будет сохранён.» → `tap "Выйти"`: `mw-10-abandon.png` — список без плашки; `$ADB -s emulator-5554 shell dumpsys notification --noredact | grep -c "$PKG"` — уведомления отдыха нет.
- «НАЧАТЬ» → ⋮ (`tap "Ещё"`) → «Выйти без сохранения» → «Выйти»: `mw-11-menu-exit.png` — список без плашки.
- Число сессий в базе — по-прежнему 1 (запрос из Step 5).

- [ ] **Step 7: Виджет и уведомление (пункты 7, 8)**

Виджет шлёт `ACTION_VIEW` с `workouttimer://execution/{id}` и `FLAG_ACTIVITY_NEW_TASK`; на эмуляторе тот же интент — через `am start`:

```bash
widget() { $ADB -s emulator-5554 shell am start -n $PKG/.presentation.MainActivity \
  -a android.intent.action.VIEW -d "workouttimer://execution/$1" -f 0x10000000; sleep 2; }
```

- Начать «Ноги», «Закончить подход», `back`, `tap "Упражнения"` (иконка гантели). `widget <id Спины>`: `mw-12-widget-return.png` — открылось выполнение **«Ноги»** (отдых идёт), не «Спина». `back` — снова экран «Упражнения».
- `back` до списка, ещё `back` (закрыть приложение со списка), затем `widget <id Спины>`: `mw-13-widget-after-close.png` — снова выполнение «Ноги».
- Свернуть, `tap "Упражнения"`, `cmd statusbar expand-notifications`, `tap` по уведомлению отдыха (текст заголовка «Отдых …»): `mw-14-notification-return.png` — выполнение «Ноги».
- Выйти без сохранения (⋮). `widget <id Ноги>` без сессии — выполнение «Ноги» начинается, как раньше; выйти без сохранения.
- Тап по устаревшему уведомлению «Отдых завершён», если осталось, — приложение просто выходит вперёд, тренировка не начинается.

- [ ] **Step 8: Пересоздание активности (пункт 9)**

Начать «Ноги», «Закончить подход» (идёт отдых):

```bash
$ADB -s emulator-5554 shell cmd uimode night yes; sleep 2
$ADB -s emulator-5554 shell cmd uimode night no; sleep 2
$ADB -s emulator-5554 shell settings put system font_scale 1.15; sleep 2
$ADB -s emulator-5554 shell settings put system font_scale 1.0; sleep 2
```

`mw-15-recreate.png` — экран выполнения: тот же подход «Отдых · далее подход 2», отсчёт продолжается (не сначала), «Загрузка тренировки» не мелькала. То же при свёрнутой тренировке на списке — плашка на месте. Выйти без сохранения.

- [ ] **Step 9: Раскладка, клавиатура, навигация (пункт 10)**

Начать «Ноги», свернуть:

- `mw-16-list-bar.png` — FAB над плашкой; прокрутить список вниз — последняя строка видна целиком над плашкой.
- Меню «Спина» → «Пропустить»: `mw-17-skip-snackbar.png` — снекбар «Спина пропущена» над плашкой.
- `$ADB -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.threebutton`, `mw-18-nav-buttons.png`; `… enable com.android.internal.systemui.navbar.gestural`, `mw-19-nav-gesture.png` — в обоих: отступ навигации один (нет двойной полосы), фон плашки заливает полосу под системной навигацией.
- `tap "Поиск"`, тап в поле: `mw-20-ime.png` — при API ≥ 35 (Step 2) плашки нет, пока открыта клавиатура, и она возвращается после `back`; при API ≤ 34 плашка остаётся внизу — ожидаемо по решению.

- [ ] **Step 10: Доступность и блокировки (пункты 11, 12)**

- `$ADB -s emulator-5554 shell uiautomator dump` при плашке на списке: у плашки один кликабельный узел с текстами «Ноги» и «Отдых …», крестик — отдельный узел с описанием «Выйти из тренировки без сохранения». Если на образе есть TalkBack (`pm list packages | grep -i talkback`) — включить, послушать плашку (одна фраза, «двойное нажатие — вернуться к тренировке»), выключить; если нет — довериться `MiniWorkoutBarContentTest`.
- ⋮ у карточки «Ноги» (идущая): `mw-21-locked-menu.png` — «Вернуться к тренировке» вместо «Начать», «Редактировать» и «Удалить» приглушены с подписью «Недоступно во время тренировки», тап по ним ничего не делает. ⋮ у «Спины»: `mw-22-other-menu.png` — «Начать» нет, «Редактировать» и «Удалить» активны. Карточка «Ноги» — «ВЕРНУТЬСЯ».
- Выйти без сохранения.

- [ ] **Step 11: Итог**

Push и PR **не делать** — это делает контроллер после финального ревью. В отчёт: результаты Step 1 (числа тестов), уровень API эмулятора, перечень скриншотов с путями и что на каждом совпало или не совпало с ожиданием, найденные и исправленные проблемы (SHA коммитов `fix:`), пункты, которые не удалось проверить (например, TalkBack без пакета на образе).
