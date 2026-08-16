# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Проект

Android-приложение «Workout Timer»: пользователь создаёт тренировки со списком упражнений (подходы, повторы, вес, время отдыха) и выполняет их с таймером отдыха, уведомлениями и историей сессий. Один Gradle-модуль `:app`, пакет `ru.hopes.workouttimer`.

**Язык:** комментарии в коде, UI-строки и документы в `docs/` — на русском. Продолжай в том же стиле.

## Команды

```bash
./gradlew assembleDebug                 # сборка
./gradlew installDebug                  # сборка + установка на подключённое устройство
./gradlew :app:testDebugUnitTest        # все unit-тесты (JVM)
./gradlew :app:connectedDebugAndroidTest # инструментальные тесты (нужно устройство)
./gradlew lint                          # Android Lint

# один класс / один метод
./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"
./gradlew :app:testDebugUnitTest --tests "*WorkoutExecutionViewModelTest.registerInteraction*"
```

Форматтеров/статических анализаторов (ktlint, detekt) в проекте нет.

## Архитектура

Clean-архитектура в трёх слоях внутри одного модуля:

- `domain/` — модели (`Workout`, `Exercise`, `WorkoutSession`), интерфейсы репозиториев, use-case'ы (один класс = одна операция, `operator fun invoke`).
- `data/` — Room (`dao/AppDatabase.kt`, `WorkoutDao`), `entity/`, реализации репозиториев, `mapper/` с extension-функциями `toDomain()` / `toEntity()` / `toExport()`.
- `presentation/` — Compose + ViewModel'и по одной на экран, навигация, сервис уведомлений, утилиты (звук, вибрация, wake lock).

DI — Hilt, всё собрано в единственном модуле `di/AppModule.kt` (`SingletonComponent`). ViewModel'и — `@HiltViewModel`, во Compose берутся через `hiltViewModel()`.

### Экран выполнения тренировки — ядро приложения

`presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt` — самый сложный файл, машина состояний `WorkoutExecutionState`:

`Loading` → `Active` (пользователь делает подход) ⇄ `Rest` (тикает таймер отдыха) → … → `Finished`

- `Active → Rest`: пользователь жмёт «закончил подход» (`onExerciseFinished`), таймер стартует автоматически.
- `Rest → Active`: таймер досчитал (`onRestFinished`) или пользователь пропустил отдых (`skipRest`).
- Таймер — корутина в `viewModelScope` с `countdownFlow`, тикающая раз в 200 мс от абсолютного `finishTime`; уведомление обновляется не чаще раза в секунду.
- **Учёт простоя:** каждое реальное действие пользователя вызывает `registerInteraction()`. Промежуток сверх `IDLE_EXCLUSION_THRESHOLD_MILLIS` (10 мин) копится в `excludedIdleMillis` и вычитается из сохраняемой `durationMillis`. Отдельно, при простое в `Active` дольше `IDLE_REMINDER_DELAY_MILLIS` (5 мин) шлётся уведомление-напоминание.
- Тестовый шов для времени: `internal fun registerInteraction(now: Long = System.currentTimeMillis())` + `internal` поля с `private set`. Абстракции `Clock` в DI нет и заводить её не надо.

### Уведомления

`presentation/service/TimerNotificationService` — обычный `Service`, управляется исключительно fire-and-forget интентами из ViewModel (`ACTION_START` / `ACTION_UPDATE` / `ACTION_STOP` / `ACTION_SHOW_FINISHED` / `ACTION_SHOW_IDLE_REMINDER`), три отдельных канала уведомлений. Никакого биндинга — обратной связи от сервиса нет.

**Готча:** `context.startService()` из фона может кинуть `IllegalStateException` (API 26+) / `ForegroundServiceStartNotAllowedException` (API 31+). Для некритичных уведомлений (напоминание о простое) это глотается в `try/catch` — см. `showIdleReminderNotification`. В манифесте у сервиса `foregroundServiceType="mediaPlayback"`.

### База данных

Room, `version = 7`, `exportSchema = false`. Миграции объявляются top-level `val`-ами в `data/dao/AppDatabase.kt` и **обязательно** регистрируются в `AppModule.provideDatabase`.

**Готча:** там же включён `fallbackToDestructiveMigration(dropAllTables = true)` — забытая миграция не упадёт с ошибкой, а молча снесёт данные пользователя. При изменении схемы всегда пиши миграцию.

**Готча:** имена полей entity и domain не совпадают — `ExerciseEntity.restTimeMillis` ↔ `Exercise.timeMillis`, `ExerciseEntity.orderInWorkout` ↔ `Exercise.order`. Маппинг только через `data/mapper/`.

### Навигация

Все маршруты — в `presentation/navigation/NavGraph.kt`, в приватном `sealed class Screen` внизу файла (`createRoute(id)` / `getWorkoutId(bundle)`). Новый экран = правка только этого файла.

## Тесты

JUnit 4 + MockK + `kotlinx-coroutines-test`. Robolectric нет: вместо него в `app/build.gradle.kts` включён `testOptions.unitTests.isReturnDefaultValues = true`, поэтому вызовы android-фреймворка в unit-тестах возвращают дефолты, а не бросают. Android-зависимости ViewModel'ей (`Context`, `SoundPlayer`, `VibrationManager`, `WakeLockHelper`) мокаются как `mockk(relaxed = true)`.

Стандартный setup в тестах ViewModel'ей — `Dispatchers.setMain(UnconfinedTestDispatcher())` в `@Before` и `resetMain()` в `@After`.

## Прочее

- `docs/superpowers/specs/` и `docs/superpowers/plans/` — спеки и планы реализации фич (формат `YYYY-MM-DD-название.md`), пишутся под воркфлоу плагина superpowers.
- `note/noteTODO.txt` — неформальный роадмап автора.
- Экспорт/импорт тренировок — JSON через `kotlinx.serialization` + `FileProvider` (authority `${applicationId}.fileprovider`, пути в `res/xml/file_paths.xml`).
- В `SystemMediaController` захардкожен запуск Яндекс.Музыки (`ru.yandex.music`, `<queries>` в манифесте) — экспериментальная фича, а не общий медиа-контроллер.
- minSdk 24, compileSdk/targetSdk 36, JVM target 17, версии зависимостей — только через `gradle/libs.versions.toml`.
