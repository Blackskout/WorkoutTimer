# Виджет быстрого старта тренировки — дизайн

**Дата:** 2026-09-02
**Ветка:** `feat/quick-start-widget`

## Проблема

Чтобы начать тренировку, нужно открыть приложение, дождаться сплеша, найти нужную тренировку в списке и нажать на неё. В зале телефон в руках с перчатками или мокрыми руками, и каждый лишний экран стоит времени.

Приложение уже знает, какая тренировка была последней: `WorkoutEntity.lastUseAt`. Но в списке этот порядок не используется — `getAllWorkouts()` идёт без `ORDER BY` (`WorkoutDao.kt:19`), тренировки показываются в порядке добавления.

## Цель

Виджет на домашнем экране со списком тренировок, отсортированных по времени последнего выполнения. Тап по строке открывает экран выполнения этой тренировки напрямую, минуя список.

## Вне рамок

Отдельные фичи, каждая со своей спекой:

- **Живой таймер в виджете** (текущее упражнение, отсчёт отдыха, кнопки управления). Требует вынести состояние сессии из `WorkoutExecutionViewModel` наружу — сейчас таймер умирает вместе с экраном. Этот виджет готовит для неё инфраструктуру: Glance, deep link, доступ к данным из виджета.
- **Виджет статистики** (тренировок за неделю, суммарное время, серия).
- **Экран настройки виджета** и режим «одна закреплённая тренировка».
- **Сортировка по `lastUseAt` в самом приложении.** Виджет сортирует у себя; менять порядок на главном экране — отдельное продуктовое решение.

## Часть 0: Починка `lastUseAt`

**Это нужно сделать первым, иначе виджет будет врать с первого же дня.**

Сейчас у `lastUseAt` два писателя, и второй ставит его не по делу:

- `CreateWorkoutViewModel.kt:113` подставляет `System.currentTimeMillis()` в модель `Workout`, а на `:116–119` эта модель уходит и в `addWorkoutUseCase`, и в `updateWorkoutUseCase`.
- `WorkoutRepositoryImpl.kt:42–46` передаёт значение в `WorkoutDao.updateWorkout()` (`WorkoutDao.kt:34–35`), который пишет его в БД.

То есть **сохранение правки тренировки засчитывается как её выполнение**. Баг видно уже сейчас, без всякого виджета: `ListWorkoutScreen.kt:291` показывает `DateFormatter.formatDateToString(workout.lastUseAt)`, поэтому после правки тренировка говорит «только что», хотя её не делали, а `:255` перестаёт подсвечивать её как заброшенную. Виджет просто вытащил бы это на домашний экран: отредактированная тренировка встала бы первой с подписью «ещё не делали».

**Решение — единственный писатель.** `lastUseAt` меняет только `updateLastUseAt()`, вызываемый при завершении тренировки:

1. `WorkoutDao.updateWorkout()` перестаёт трогать колонку: `UPDATE workouts SET name = :name WHERE id = :id`, параметр `lastUseAt` из сигнатуры убирается. Схема не меняется, миграция не нужна.
2. `WorkoutRepositoryImpl.updateWorkout()` перестаёт передавать значение в DAO.
3. `CreateWorkoutViewModel.loadWorkout()` запоминает исходный `lastUseAt` рядом с `editingWorkoutId` и подставляет его в модель на пути редактирования, чтобы модель не содержала заведомо неверное значение. На пути создания остаётся `System.currentTimeMillis()`.

Что сознательно **не** трогаем:

- **Создание тренировки** по-прежнему ставит `lastUseAt = now`. Новая тренировка оказывается наверху виджета — это удобно, а подпись честно скажет «ещё не делали».
- **Импорт** (`ExportImportRepositoryImpl.kt:116`) по-прежнему берёт `lastUseAt` из файла. Это оригинальное значение из экспорта, оно верное по смыслу.

## Архитектура

### Выбор технологии

**Glance** (`androidx.glance:glance-appwidget:1.2.0` — актуальный стабильный релиз), а не классические `RemoteViews`.

Причина: в проекте ноль XML-layout'ов, весь UI на Compose. Виджет на Glance пишется тем же кодом, `LazyColumn` и адаптивные размеры доступны из коробки, `GlanceTheme` подхватывает динамические цвета так же, как `WorkoutTimerTheme`. На `RemoteViews` тот же виджет — заметно больше кода и ручная работа с размерами.

`glance-material3` подключать не нужно: `GlanceTheme` лежит в core-артефакте `androidx.glance` и приезжает транзитивно. Отдельная настройка compose-компилятора или KSP не нужна — плагин `kotlin-compose` уже применён.

Плата:

- Отрисовка Glance не покрывается юнит-тестами. Тестами закрываем слой данных и форматирование, отрисовку проверяем руками.
- Транзитивно приезжают `androidx.work:work-runtime-ktx`, `androidx.datastore`, `androidx.core:core-remoteviews`. Не блокер (WorkManager поднимается своим app-startup провайдером), но это не «одна библиотека».

`minSdk` проекта 24, `glance-appwidget` требует 23 — проходим.

**Проверить до написания плана:** собрать проект с добавленной зависимостью. `glance-appwidget 1.2.0` собран против Compose 1.7.x, а проект тянет BOM `2026.03.00`; фактическая совместимость рантаймов без сборки не проверяется.

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
    private val getAllWorkoutsWithExercise: GetAllWorkoutsWithExerciseUseCase,
    private val getLastSessionDurations: GetLastSessionDurationsUseCase
) {
    operator fun invoke(): Flow<List<WidgetWorkout>> =
        combine(
            getAllWorkoutsWithExercise(),
            getLastSessionDurations()
        ) { workouts, durations ->
            workouts
                .sortedByDescending { it.workout.lastUseAt }
                .map { it.toWidgetWorkout(durations[it.workout.id]) }
        }.catch { emit(emptyList()) }
}
```

Ходим через существующие use case, а не в репозиторий напрямую — в проекте на каждый метод репозитория заведён свой use case. Новых запросов к DAO не добавляем: `getLastSessionDurations()` уже отдаёт `Flow<Map<Int, Long>>` с ключом `Int` (`WorkoutRepositoryImpl.kt:120–124`).

Попутно: у `GetAllWorkoutsWithExerciseUseCase.invoke()` стоит бессмысленный `suspend` — он возвращает `Flow`, ничего не приостанавливая. У use case сейчас **ноль вызывающих**, так что модификатор снимается без риска; иначе `GetWidgetWorkoutsUseCase` пришлось бы делать `suspend` на ровном месте.

**Сортировка живёт в use case, а не в `ORDER BY`.** Тренировок единицы, выигрыш от сортировки в SQL нулевой, зато правило покрывается обычным unit-тестом без Room и без инструментальных тестов.

`.catch { emit(emptyList()) }` стоит на самом Flow, а не `runCatching` вокруг вызова: данные собираются внутри `provideContent`, поэтому исключение из Room прилетит из корутины сбора и в теле композабла его не поймать.

### Обновление виджета

```kotlin
// domain/repository/WidgetUpdater.kt
interface WidgetUpdater {
    suspend fun requestUpdate()
}
```

`suspend` — потому что `GlanceAppWidget.updateAll(context)` объявлена как suspend-функция. Все точки вызова в репозиториях и так `suspend`, поэтому собственный `CoroutineScope` внутри `GlanceWidgetUpdater` не нужен, гонок нет, а fake в тестах остаётся тривиальным.

Интерфейс в domain, реализация `GlanceWidgetUpdater` в `presentation/widget` — так data-слой не зависит от presentation.

Внедряется в `WorkoutRepositoryImpl` и `ExportImportRepositoryImpl`, вызывается после мутаций:

| Место | Что меняется |
|---|---|
| `WorkoutRepositoryImpl.updateLastUseAt()` | порядок списка (конец тренировки) |
| `WorkoutRepositoryImpl.addWorkout()` | состав списка |
| `WorkoutRepositoryImpl.updateWorkout()` | название, число упражнений |
| `WorkoutRepositoryImpl.deleteWorkout()` | состав списка |
| `ExportImportRepositoryImpl` (импорт) | состав списка |

`addWorkoutSession()` пуш не вызывает: она всегда идёт в паре с `updateLastUseAt()` из `WorkoutExecutionViewModel.moveToNextExercise()` (`:320,324`), другого пути сохранения сессии нет.

### Биндинг в Hilt

`AppModule` — это `object`, поэтому `@Binds` в него не положить. Добавляется `@Provides @Singleton fun provideWidgetUpdater(@ApplicationContext ctx: Context): WidgetUpdater`, а `provideWorkoutRepository` и `provideExportImportRepository` получают новый параметр.

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
- Список: `LazyColumn`, скроллится внутри виджета. Разметка от размера не зависит, поэтому `SizeMode.Single` (он же дефолт) достаточно, `SizeMode.Responsive` не нужен.
- Строка: название в одну строку с многоточием, под ним подпись.
- Размеры: минимум 3×2 ячейки (`minWidth` 180dp, `minHeight` 110dp), `resizeMode="horizontal|vertical"`.
- Тема: `GlanceTheme` — на Android 12+ динамические цвета, как и в `WorkoutTimerTheme`.
- Пустое состояние: текст «Нет тренировок», тап открывает приложение.

### Подпись строки

Чистая функция без `Context`, чтобы покрывалась обычным unit-тестом:

```kotlin
fun formatWidgetSubtitle(exerciseCount: Int, lastDurationMillis: Long?): String
```

- `7 упр. · 52 мин` — длительность форматирует существующий `DateFormatter.formatDurationToString()`
- `5 упр. · ещё не делали` — когда `lastDurationMillis == null`

Строки здесь захардкожены, как уже сделано в `DateFormatter.formatDurationToString()` (`DateFormatter.kt:60–69`). Тянуть их из `strings.xml` означало бы протащить в функцию `Resources`, а тест при `unitTests.isReturnDefaultValues = true` начал бы проверять строки, которые сам же и подставил через мок.

Статичные подписи виджета — название в шапке и текст пустого состояния — наоборот, живут в `strings.xml`: они читаются внутри Glance-композабла, где контекст доступен.

### Устойчивость к ошибкам

Ошибка чтения данных гасится через `.catch { emit(emptyList()) }` в use case — виджет покажет пустое состояние. Непойманное исключение внутри Glance приводит к системной «ошибке загрузки» на весь виджет, это заметно хуже пустого списка. Как второй эшелон можно переопределить `GlanceAppWidget.onCompositionError()`.

## Часть C: Deep link

URI: `workouttimer://execution/{workout_id}`

### Где живёт шаблон

`sealed class Screen` в `NavGraph.kt:130` объявлен `private`, а виджету нужно собрать ссылку. Класс становится `internal`, к `Screen.Execution` добавляется `createDeepLink(workoutId: Int): String` рядом с существующим `createRoute()`. Так шаблон URI остаётся в одном месте и не дублируется в пакете виджета.

`Screen.Execution` получает `navDeepLink` с этим шаблоном — маршрут по-прежнему описан целиком в `NavGraph`.

Рассмотренная альтернатива — передавать `workoutId` через `Intent` extra и делать `navigate` из `MainActivity`. Отвергнута: появляется второй, неявный путь навигации мимо маршрутов; deep link к тому же переиспользуется будущим виджетом активной тренировки.

### Интент из виджета

`actionStartActivity` с явным `Intent`: компонент `MainActivity`, `data` — URI ссылки, флаги `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`.

**Флаги обязательны, и вот почему.** `NavController.handleDeepLink()` ведёт себя по-разному в зависимости от них:

- Без `NEW_TASK` контроллер считает, что находится в чужой задаче, и навигирует на конечный пункт с `popUpTo(graph, inclusive = true)`. Стек становится `[execution]`, и «Назад» выходит из приложения.
- С `NEW_TASK`, но без `CLEAR_TASK` контроллер вообще не навигирует: он пересобирает задачу через `TaskStackBuilder` и вызывает `activity.finish()`. С `installSplashScreen()` (`MainActivity.kt:27`) это даёт два сплеша подряд на холодном старте.
- С `NEW_TASK or CLEAR_TASK` контроллер достраивает синтетический стек до стартового пункта. Это и нужно: «Назад» с экрана выполнения ведёт на список тренировок.

### Манифест

`MainActivity` получает `android:launchMode="singleTop"`.

`intent-filter` с `VIEW`/`BROWSABLE` на `MainActivity` **не добавляем.** Виджет шлёт явный интент с проставленным компонентом, а явные интенты через фильтры не проходят вовсе; Navigation матчит `navDeepLink` по `intent.data` независимо от манифеста. Фильтр понадобился бы только чтобы ссылку могли открывать браузер и сторонние приложения — это отдельное решение, и оно сделало бы `workouttimer://execution/{id}` публично вызываемым.

Регистрация самого виджета:

```xml
<receiver android:name=".presentation.widget.QuickStartWidgetReceiver"
          android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/quick_start_widget_info" />
</receiver>
```

`android:exported="true"` обязателен: у ресивера есть intent-filter, а `targetSdk = 36`.

В `quick_start_widget_info.xml`, помимо размеров и `resizeMode`:

- `android:initialLayout` — обязательный атрибут; Glance даёт готовый `@layout/glance_default_loading_layout`
- `android:targetCellWidth` / `targetCellHeight` — для Android 12+
- `android:description` и `android:previewImage` — иначе в системном списке виджетов не будет ни описания, ни превью

### Два сценария входа

Виджет шлёт интент с флагами `NEW_TASK|CLEAR_TASK`. `CLEAR_TASK` сносит задачу целиком, поэтому такой интент **всегда** поднимает новый экземпляр `MainActivity` — после очистки задачи `singleTop` попросту нечего переиспользовать. Из этого следует, что на практике оба сценария входа с виджета обслуживаются одним и тем же путём:

- **Приложение закрыто.** `NavHost` разбирает стартовый интент сам при создании графа (`NavController.setGraph()`).
- **Приложение уже открыто.** Благодаря `CLEAR_TASK` это неотличимо от предыдущего случая: старый экземпляр активити уничтожается, поднимается новый, и стартовый интент снова разбирает `setGraph()`.

Путь через `onNewIntent()` — `MainActivity` вызывает `setIntent(intent)`, кладёт интент в `mutableStateOf`, а `NavGraph` реагирует через `navController.handleDeepLink(intent)` и после обработки гасит значение (`null`) — остаётся в коде как защита на случай, если флаги когда-нибудь изменят и активити начнёт переиспользоваться. Сейчас он тапом по виджету не достигается.

Через state идут **только** интенты из `onNewIntent()`. Стартовый интент туда не попадает: `NavController.setGraph()` уже обработал его сам, а публичный `handleDeepLink()` флагом `deepLinkHandled` не защищён и отработал бы второй раз.

## Краевые случаи

| Ситуация | Поведение |
|---|---|
| Тренировку удалили, виджет ещё не обновился | Тап ведёт на `execution/{id}` несуществующей тренировки. `loadWorkout()` уже отдаёт `WorkoutExecutionState.Error` (`WorkoutExecutionViewModel.kt:146`), экран показывает `ErrorState` с кнопкой. Отдельная обработка не нужна, проверяем руками. |
| У тренировки нет упражнений | Тот же путь через `Error`. |
| Сессий по тренировке ещё не было | Подпись «ещё не делали» вместо времени. |
| Тренировок нет вовсе | Пустое состояние виджета. |
| Ошибка чтения БД | `.catch` в use case → пустое состояние. |
| Тап по виджету во время незавершённой тренировки | `FLAG_ACTIVITY_CLEAR_TASK` сносит задачу целиком, `BackHandler` не срабатывает, диалог подтверждения выхода (`feat/workout-exit-confirmation`) не показывается — тренировка молча теряется, сессия в БД не пишется (она пишется только при завершении). **Известное и принятое ограничение**: без `CLEAR_TASK` ломается разбор deep link (см. «Флаги обязательны» выше), а показать диалог из уничтожаемой активити невозможно. Снимается только вынесением состояния сессии наружу, переживающим пересоздание активити — это прямо отнесено к «вне рамок» (живой таймер в виджете). |

## Тестирование

Разработка по TDD, как в остальном проекте.

**Новые unit-тесты:**

- `GetWidgetWorkoutsUseCaseTest`
  - сортировка по `lastUseAt` по убыванию
  - длительность подставляется по `workoutId`
  - `lastDurationMillis == null`, когда сессий не было
  - `exerciseCount` считается по списку упражнений
  - пустой список тренировок → пустой результат
  - ошибка в исходном Flow → пустой список, без падения
- `WidgetSubtitleFormatterTest`
  - подпись с длительностью
  - подпись без сессий
  - счётчик упражнений

**Изменённые тесты:**

- `WorkoutRepositoryImplTest` — конструктор `WorkoutRepositoryImpl` получает `WidgetUpdater`, существующие тесты обновляются под fake. Добавляются проверки, что мутации дёргают `requestUpdate()` и что `updateWorkout()` больше не передаёт `lastUseAt` в DAO.

Инфраструктуры хватает: JUnit4 + mockk + kotlinx-coroutines-test, как в существующем `WorkoutRepositoryImplTest`. Robolectric и инструментальные тесты не нужны.

**Ручная проверка на устройстве:**

- добавление виджета на домашний экран, наличие превью и описания в системном списке
- тап при закрытом приложении и при уже открытом
- «Назад» с экрана выполнения ведёт на список, сплеш не показывается дважды
- ресайз виджета
- тёмная тема и динамические цвета
- порядок обновляется после завершения тренировки
- редактирование тренировки **не** поднимает её наверх и не сбрасывает «дней назад» на главном экране

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
app/src/main/res/drawable/ (иконка шапки и превью виджета)
app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt
app/src/test/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatterTest.kt
```

**Изменяемые:**

```
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt
app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt                        (часть 0)
app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt                 (часть 0 + пуш)
app/src/main/java/ru/hopes/workouttimer/data/ExportImportRepositoryImpl.kt
app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetAllWorkoutsWithExerciseUseCase.kt
app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt  (часть 0)
app/src/main/java/ru/hopes/workouttimer/presentation/MainActivity.kt
app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt
app/src/main/res/values/strings.xml
app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt
```
