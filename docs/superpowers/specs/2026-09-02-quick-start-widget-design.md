# Виджет быстрого старта тренировки — дизайн

**Дата:** 2026-09-02
**Ветка:** `feat/quick-start-widget`

## Проблема

Чтобы начать тренировку, нужно открыть приложение, дождаться сплеша, найти нужную тренировку в списке и нажать на неё. В зале телефон в руках с перчатками или мокрыми руками, и каждый лишний экран стоит времени.

Приложение уже знает, какая тренировка была последней: `WorkoutEntity.lastUseAt` обновляется в `WorkoutRepositoryImpl.updateLastUseAt()` при завершении тренировки. Но в самом списке этот порядок не используется — `getAllWorkouts()` идёт без `ORDER BY`, тренировки показываются в порядке добавления.

## Цель

Виджет на домашнем экране со списком тренировок, отсортированных по времени последнего выполнения. Тап по строке открывает экран выполнения этой тренировки напрямую, минуя список.

## Вне рамок

Отдельные фичи, каждая со своей спекой:

- **Живой таймер в виджете** (текущее упражнение, отсчёт отдыха, кнопки управления). Требует вынести состояние сессии из `WorkoutExecutionViewModel` наружу — сейчас таймер умирает вместе с экраном. Этот виджет готовит для неё инфраструктуру: Glance, deep link, доступ к данным из виджета.
- **Виджет статистики** (тренировок за неделю, суммарное время, серия).
- **Экран настройки виджета** и режим «одна закреплённая тренировка».
- **Сортировка по `lastUseAt` в самом приложении.** Виджет сортирует у себя; менять порядок на главном экране — отдельное продуктовое решение.

## Архитектура

### Выбор технологии

**Glance** (`androidx.glance:glance-appwidget:1.2.0`), а не классические `RemoteViews`.

Причина: в проекте ноль XML-layout'ов, весь UI на Compose. Виджет на Glance пишется тем же кодом, `LazyColumn` и адаптивные размеры доступны из коробки, `GlanceTheme` подхватывает динамические цвета так же, как `WorkoutTimerTheme`. На `RemoteViews` тот же виджет — заметно больше кода и ручная работа с размерами.

Плата: одна новая зависимость и то, что отрисовка Glance не покрывается юнит-тестами. Тестами закрываем слой данных и форматирование, отрисовку проверяем руками.

`minSdk` проекта 24, Glance требует 23 — проходим.

### Источник данных

Виджет читает Room напрямую, без промежуточного кэша. Room остаётся единственным источником правды.

Рассмотренная альтернатива — писать снимок списка в DataStore и читать в виджете только его. Отвергнута: второй источник правды и риск рассинхрона ради скорости, незаметной на списке из нескольких тренировок.

`GlanceAppWidget` создаётся системой и не имеет собственного DI-скоупа, поэтому зависимости берутся через Hilt `@EntryPoint` из `SingletonComponent` — штатный способ для точек входа вне графа.

## Часть A: Слой данных

### Модель

```kotlin
// domain/model/WidgetWorkout.kt
data class WidgetWorkout(
    val id: Int,
    val name: String,
    val exerciseCount: Int,
    val lastDurationMillis: Long?
)
```

`lastDurationMillis == null` означает «сессий ещё не было».

### Use case

```kotlin
// domain/usecase/GetWidgetWorkoutsUseCase.kt
class GetWidgetWorkoutsUseCase @Inject constructor(
    private val repository: WorkoutRepository
) {
    operator fun invoke(): Flow<List<WidgetWorkout>> =
        combine(
            repository.getAllWorkoutsWithExercise(),
            repository.getLastSessionDurations()
        ) { workouts, durations ->
            workouts
                .sortedByDescending { it.workout.lastUseAt }
                .map { it.toWidgetWorkout(durations[it.workout.id]) }
        }
}
```

Оба Flow уже существуют и используются в `ListWorkoutViewModel`. Новых запросов к DAO не добавляем.

**Сортировка живёт в use case, а не в `ORDER BY`.** Тренировок единицы, выигрыш от сортировки в SQL нулевой, зато правило покрывается обычным unit-тестом без Room и без инструментальных тестов.

### Обновление виджета

```kotlin
// domain/repository/WidgetUpdater.kt
interface WidgetUpdater {
    fun requestUpdate()
}
```

Интерфейс в domain, реализация `GlanceWidgetUpdater` в `presentation/widget` вызывает `QuickStartWidget().updateAll(context)`. Так data-слой не зависит от presentation, а в тестах подставляется fake.

Внедряется в `WorkoutRepositoryImpl` и `ExportImportRepositoryImpl`, вызывается после мутаций:

| Место | Что меняется |
|---|---|
| `WorkoutRepositoryImpl.updateLastUseAt()` | порядок списка (конец тренировки) |
| `WorkoutRepositoryImpl.addWorkout()` | состав списка |
| `WorkoutRepositoryImpl.updateWorkout()` | название, число упражнений |
| `WorkoutRepositoryImpl.deleteWorkout()` | состав списка |
| `ExportImportRepositoryImpl` (импорт) | состав списка |

`addWorkoutSession()` пуш не вызывает: она всегда идёт в паре с `updateLastUseAt()` из `WorkoutExecutionViewModel.moveToNextExercise()`, второй вызов был бы лишним.

### Зачем пуш, если есть Flow

Внутри `provideContent` Flow из use case собирается в state, поэтому пока Glance-сессия жива, изменения в Room доезжают до виджета сами. Когда система просит перерисовать виджет заново, `provideGlance` вызывается заново и читает свежие данные. Устаревших данных на перерисовке не бывает по построению.

Пуш закрывает другой случай: тренировка завершилась, пока пользователь был в приложении, а виджет на домашнем экране не виден — Glance-сессия в этот момент остановлена. Пуш гарантирует, что к возвращению на домашний экран порядок уже верный.

Возможно, Glance перезапускает сессию сам при возврате на лаунчер — это не проверено на устройстве. `GlanceWidgetUpdater` стоит десяток строк и снимает вопрос, поэтому оставляем его, а не полагаемся на догадку.

## Часть B: Виджет

### Файлы

```
presentation/widget/
  QuickStartWidget.kt          — GlanceAppWidget, отрисовка
  QuickStartWidgetReceiver.kt  — GlanceAppWidgetReceiver
  WidgetEntryPoint.kt          — Hilt @EntryPoint
  GlanceWidgetUpdater.kt       — реализация WidgetUpdater
  WidgetSubtitleFormatter.kt   — формирование подписи строки
res/xml/quick_start_widget_info.xml — метаданные провайдера
```

### Вёрстка

```
┌────────────────────────────┐
│ 🏋 Workout Timer           │  ← тап: открыть список
├────────────────────────────┤
│ Понедельник                │
│ 7 упр. · 52 мин            │  ← тап: сразу в тренировку
├────────────────────────────┤
│ Среда                      │
│ 6 упр. · 48 мин            │
├────────────────────────────┤
│ Пятница                    │
│ 5 упр. · ещё не делали     │
└────────────────────────────┘
```

- Шапка: иконка и название приложения, тап открывает `MainActivity` обычным образом (список тренировок).
- Список: `LazyColumn`, скроллится внутри виджета. Поэтому отдельные layout'ы под разные размеры не нужны — `SizeMode.Single`.
- Строка: название в одну строку с многоточием, под ним подпись.
- Размеры: минимум 3×2 ячейки (`minWidth` 180dp, `minHeight` 110dp), `resizeMode="horizontal|vertical"`.
- Тема: `GlanceTheme` — на Android 12+ динамические цвета, как и в `WorkoutTimerTheme`.
- Пустое состояние: текст «Нет тренировок», тап открывает приложение.

### Подпись строки

Чистая функция, чтобы покрывалась тестом:

```kotlin
fun formatWidgetSubtitle(exerciseCount: Int, lastDurationMillis: Long?): String
```

- `7 упр. · 52 мин` — длительность форматирует существующий `DateFormatter.formatDurationToString()`
- `5 упр. · ещё не делали` — когда `lastDurationMillis == null`

Строки — в `strings.xml`, как в остальном приложении.

### Устойчивость к ошибкам

Чтение данных в `provideGlance` оборачивается в `runCatching`; при сбое рисуется пустое состояние. Непойманное исключение внутри Glance приводит к системной «ошибке загрузки» на весь виджет — это заметно хуже пустого списка.

## Часть C: Deep link

URI: `workouttimer://execution/{workout_id}`

### Навигация

`Screen.Execution` получает `navDeepLink` с этим шаблоном, так что маршрут по-прежнему описан в одном месте — в `NavGraph`.

Рассмотренная альтернатива — передавать `workoutId` через `Intent` extra и делать `navigate` из `MainActivity`. Отвергнута: появляется второй, неявный путь навигации мимо маршрутов; deep link к тому же переиспользуется будущим виджетом активной тренировки.

### Манифест

- `MainActivity`: `android:launchMode="singleTop"`
- `intent-filter` с `VIEW` / `DEFAULT` / `BROWSABLE` и `<data android:scheme="workouttimer" android:host="execution" />`

### Два сценария входа

- **Приложение закрыто.** `NavHost` разбирает интент сам при создании.
- **Приложение уже открыто.** `MainActivity` хранит текущий интент в `mutableStateOf`, обновляет его в `onNewIntent()` и передаёт в `NavGraph`; тот реагирует через `navController.handleDeepLink(intent)`.

### Back stack

Navigation достраивает синтетический стек до стартового пункта, поэтому «Назад» с экрана выполнения ведёт на список тренировок, а не закрывает приложение.

### Интент из виджета

`actionStartActivity` с явным `Intent`, у которого проставлены компонент `MainActivity` и `data` URI — чтобы система не показывала диалог выбора приложения.

## Краевые случаи

| Ситуация | Поведение |
|---|---|
| Тренировку удалили, виджет ещё не обновился | Тап ведёт на `execution/{id}` несуществующей тренировки. `loadWorkout()` уже отдаёт `WorkoutExecutionState.Error`, экран показывает `ErrorState` с кнопкой. Отдельная обработка не нужна, проверяем руками. |
| У тренировки нет упражнений | Тот же путь через `Error`. |
| Сессий по тренировке ещё не было | Подпись «ещё не делали» вместо времени. |
| Тренировок нет вовсе | Пустое состояние виджета. |
| Ошибка чтения БД | `runCatching` → пустое состояние. |

## Тестирование

Разработка по TDD, как в остальном проекте.

**Новые unit-тесты:**

- `GetWidgetWorkoutsUseCaseTest`
  - сортировка по `lastUseAt` по убыванию
  - длительность подставляется по `workoutId`
  - `lastDurationMillis == null`, когда сессий не было
  - `exerciseCount` считается по списку упражнений
  - пустой список тренировок → пустой результат
- `WidgetSubtitleFormatterTest`
  - подпись с длительностью
  - подпись без сессий
  - счётчик упражнений

**Изменённые тесты:**

- `WorkoutRepositoryImplTest` — конструктор `WorkoutRepositoryImpl` получает `WidgetUpdater`, существующие тесты обновляются под fake. Добавляется проверка, что мутации дёргают `requestUpdate()`.

**Ручная проверка на устройстве:**

- добавление виджета на домашний экран
- тап при закрытом приложении и при уже открытом
- «Назад» с экрана выполнения ведёт на список
- ресайз виджета
- тёмная тема и динамические цвета
- порядок обновляется после завершения тренировки

## Затронутые файлы

**Новые:**

```
app/src/main/java/ru/hopes/workouttimer/domain/model/WidgetWorkout.kt
app/src/main/java/ru/hopes/workouttimer/domain/repository/WidgetUpdater.kt
app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCase.kt
app/src/main/java/ru/hopes/workouttimer/presentation/widget/QuickStartWidget.kt
app/src/main/java/ru/hopes/workouttimer/presentation/widget/QuickStartWidgetReceiver.kt
app/src/main/java/ru/hopes/workouttimer/presentation/widget/WidgetEntryPoint.kt
app/src/main/java/ru/hopes/workouttimer/presentation/widget/GlanceWidgetUpdater.kt
app/src/main/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatter.kt
app/src/main/res/xml/quick_start_widget_info.xml
app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt
app/src/test/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatterTest.kt
```

**Изменяемые:**

```
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt
app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt
app/src/main/java/ru/hopes/workouttimer/data/ExportImportRepositoryImpl.kt
app/src/main/java/ru/hopes/workouttimer/presentation/MainActivity.kt
app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt
app/src/main/res/values/strings.xml
app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt
```
