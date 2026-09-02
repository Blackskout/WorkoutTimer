# Виджет быстрого старта тренировки — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Виджет на домашнем экране со списком тренировок по времени последнего выполнения; тап открывает экран выполнения напрямую.

**Architecture:** Виджет на Jetpack Glance читает Room через существующие use case, получая их по Hilt `@EntryPoint`. Тап шлёт явный интент с deep-link URI в `MainActivity`, где его разбирает `NavController`. Репозитории после мутаций дёргают `WidgetUpdater`, чтобы виджет перерисовался, даже когда его Glance-сессия остановлена.

**Tech Stack:** Kotlin 2.2.21, Jetpack Compose (BOM 2026.03.00), Glance 1.2.0, Room, Hilt, Navigation Compose 2.9.7, JUnit4 + mockk + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-02-quick-start-widget-design.md`

## Global Constraints

- `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36`, JVM target 17.
- Зависимости объявляются только через version catalog `gradle/libs.versions.toml`, никаких строковых координат в `app/build.gradle.kts`.
- Тесты — JVM unit-тесты в `app/src/test`. Robolectric и инструментальные тесты не добавляем.
- `unitTests.isReturnDefaultValues = true` — методы `android.jar` в тестах возвращают дефолты, поэтому тестируемый код не должен требовать `Context`/`Resources`.
- Комментарии и пользовательские строки — на русском, как в остальном проекте.
- Собирать: `./gradlew :app:assembleDebug`. Тесты: `./gradlew :app:testDebugUnitTest`.
- `WidgetUpdater.requestUpdate()` — `suspend`.
- Deep-link схема: `workouttimer://execution/{workout_id}`.
- Интент из виджета несёт флаги `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`.

---

## Структура файлов

| Файл | Ответственность |
|---|---|
| `domain/model/WidgetWorkout.kt` | Модель строки виджета: id, имя, число упражнений, длительность прошлой сессии |
| `domain/usecase/GetWidgetWorkoutsUseCase.kt` | Собирает и сортирует строки виджета, гасит ошибки |
| `domain/repository/WidgetUpdater.kt` | Абстракция «перерисуй виджет» для data-слоя |
| `data/mapper/WorkoutMapper.kt` | (изменяется) + `toWidgetWorkout()` рядом с существующим `toDomain()` |
| `presentation/widget/QuickStartWidget.kt` | `GlanceAppWidget`: получение данных и вся отрисовка |
| `presentation/widget/QuickStartWidgetReceiver.kt` | Регистрация виджета в системе |
| `presentation/widget/WidgetEntryPoint.kt` | Мост из Hilt в `GlanceAppWidget` |
| `presentation/widget/GlanceWidgetUpdater.kt` | Реализация `WidgetUpdater` через `updateAll()` |
| `presentation/widget/WidgetSubtitleFormatter.kt` | Чистая функция подписи строки |
| `res/xml/quick_start_widget_info.xml` | Метаданные провайдера виджета |

Порядок задач: сначала чинится `lastUseAt` (без этого виджет врёт), затем ставится риск-гейт на совместимость Glance, дальше данные, навигация, отрисовка и в конце пуш обновлений.

---

## Task 1: Единственный писатель `lastUseAt`

Сейчас сохранение правки тренировки засчитывается как её выполнение: `CreateWorkoutViewModel` подставляет `System.currentTimeMillis()` в модель и на пути редактирования тоже, а DAO пишет это в колонку. После задачи `lastUseAt` меняет только `updateLastUseAt()`.

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt:34-35`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt:39-46`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt:27,108-120,132-138`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: `WorkoutDao.updateWorkout(id: Int, name: String)` — параметр `lastUseAt` убран.

- [ ] **Step 1: Написать падающий тест**

Дописать в `WorkoutRepositoryImplTest`:

```kotlin
@Test
fun `updateWorkout does not touch lastUseAt`() = runTest {
    val dao = mockk<WorkoutDao>(relaxed = true)
    val repo = WorkoutRepositoryImpl(dao)

    repo.updateWorkout(
        Workout(id = 3, name = "Ноги", exercises = emptyList(), lastUseAt = 999L)
    )

    coVerify { dao.updateWorkout(id = 3, name = "Ноги") }
}
```

Добавить импорты: `io.mockk.coVerify`, `ru.hopes.workouttimer.domain.model.Workout`.

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*WorkoutRepositoryImplTest*"`
Expected: FAIL — компиляция теста не проходит, у `dao.updateWorkout` есть третий параметр `lastUseAt`.

- [ ] **Step 3: Убрать колонку из DAO-запроса**

`WorkoutDao.kt`, заменить:

```kotlin
    @Query("UPDATE workouts SET name = :name, lastUseAt = :lastUseAt WHERE id = :id")
    suspend fun updateWorkout(id: Int, name: String, lastUseAt: Long)
```

на:

```kotlin
    // lastUseAt меняет только updateLastUseAt(): правка тренировки — не её выполнение
    @Query("UPDATE workouts SET name = :name WHERE id = :id")
    suspend fun updateWorkout(id: Int, name: String)
```

Схема таблицы не меняется, миграция не нужна.

- [ ] **Step 4: Убрать передачу значения из репозитория**

`WorkoutRepositoryImpl.updateWorkout()`, заменить:

```kotlin
            dao.updateWorkout(
                id = workout.id,
                name = workout.name,
                lastUseAt = workout.lastUseAt
            )
```

на:

```kotlin
            dao.updateWorkout(
                id = workout.id,
                name = workout.name
            )
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*WorkoutRepositoryImplTest*"`
Expected: PASS, все тесты класса.

- [ ] **Step 6: Убрать неверное значение из ViewModel**

Чтобы модель не несла заведомо ложный `lastUseAt` на пути редактирования, `CreateWorkoutViewModel` запоминает исходное значение.

Рядом с полем `editingWorkoutId` (строка 27) добавить:

```kotlin
    private var editingLastUseAt: Long? = null
```

В `loadWorkout()` рядом с `editingWorkoutId = w.id` добавить:

```kotlin
                editingLastUseAt = w.lastUseAt
```

В блоке сохранения заменить строку `lastUseAt = System.currentTimeMillis()` на:

```kotlin
                            lastUseAt = editingLastUseAt ?: System.currentTimeMillis()
```

Создание тренировки по-прежнему получает текущее время; редактирование — исходное.

- [ ] **Step 7: Собрать проект**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt \
        app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt \
        app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt
git commit -m "fix: не считать редактирование тренировки её выполнением"
```

---

## Task 2: Glance на экране — риск-гейт

Виджет-заглушка со статичным текстом. Задача проверяет то, что без сборки не проверяется: совместимость `glance-appwidget 1.2.0` с Compose BOM 2026.03.00 и правильность регистрации в манифесте. Если совместимости нет — дальше идти незачем, и это выяснится здесь, а не на седьмой задаче.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/QuickStartWidget.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/QuickStartWidgetReceiver.kt`
- Create: `app/src/main/res/xml/quick_start_widget_info.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: ничего.
- Produces: `class QuickStartWidget : GlanceAppWidget()` и `class QuickStartWidgetReceiver : GlanceAppWidgetReceiver()` в пакете `ru.hopes.workouttimer.presentation.widget`.

- [ ] **Step 1: Добавить зависимость в каталог**

`gradle/libs.versions.toml`, в `[versions]`:

```toml
glance = "1.2.0"
```

в `[libraries]`:

```toml
androidx-glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
```

- [ ] **Step 2: Подключить зависимость к модулю**

`app/build.gradle.kts`, в блок `dependencies`:

```kotlin
    implementation(libs.androidx.glance.appwidget)
```

- [ ] **Step 3: Собрать и убедиться, что зависимость встала**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Если сборка падает на несовместимости версий Compose — остановиться и доложить: план дальше не выполняется, нужен пересмотр версии Glance.

- [ ] **Step 4: Добавить строки**

`app/src/main/res/values/strings.xml`, внутрь `<resources>`:

```xml
    <string name="widget_empty">Нет тренировок</string>
    <string name="widget_description">Быстрый старт тренировки</string>
```

- [ ] **Step 5: Написать виджет-заглушку**

`presentation/widget/QuickStartWidget.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R

class QuickStartWidget : GlanceAppWidget() {

    // Разметка от размера не зависит — список скроллится сам
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

@Composable
private fun WidgetContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .padding(12.dp)
    ) {
        Text(text = context.getString(R.string.app_name))
    }
}
```

- [ ] **Step 6: Написать ресивер**

`presentation/widget/QuickStartWidgetReceiver.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class QuickStartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickStartWidget()
}
```

- [ ] **Step 7: Описать метаданные провайдера**

`app/src/main/res/xml/quick_start_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:previewImage="@mipmap/ic_launcher"
    android:resizeMode="horizontal|vertical"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen" />
```

`initialLayout` обязателен; `glance_default_loading_layout` приезжает вместе с библиотекой. `updatePeriodMillis="0"` — фонового обновления по таймеру нет, обновления приходят пушем. Превью пока берётся с иконки приложения; отдельный скриншот виджета — необязательное улучшение и в этот план не входит.

- [ ] **Step 8: Зарегистрировать ресивер в манифесте**

`AndroidManifest.xml`, внутрь `<application>` рядом с `<service>`:

```xml
        <receiver
            android:name=".presentation.widget.QuickStartWidgetReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/quick_start_widget_info" />
        </receiver>
```

`android:exported="true"` обязателен при `targetSdk = 36`, потому что у ресивера есть intent-filter.

- [ ] **Step 9: Собрать и проверить на устройстве**

Run: `./gradlew :app:installDebug`
Проверить вручную:
- в системном списке виджетов есть «Workout Timer» с описанием «Быстрый старт тренировки»
- виджет ставится на домашний экран и показывает название приложения
- виджет тянется по горизонтали и вертикали

- [ ] **Step 10: Коммит**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/xml/quick_start_widget_info.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/java/ru/hopes/workouttimer/presentation/widget/
git commit -m "feat: заготовка виджета на Glance"
```

---

## Task 3: Данные для виджета

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/WidgetWorkout.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCase.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/mapper/WorkoutMapper.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetAllWorkoutsWithExerciseUseCase.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt`

**Interfaces:**
- Consumes: `GetAllWorkoutsWithExerciseUseCase` (после правки — не `suspend`, возвращает `Flow<List<WorkoutWithExercises>>`), `GetLastSessionDurationsUseCase` → `Flow<Map<Int, Long>>`.
- Produces:
  - `data class WidgetWorkout(val id: Int, val name: String, val exerciseCount: Int, val lastDurationMillis: Long?)`
  - `class GetWidgetWorkoutsUseCase` с `operator fun invoke(): Flow<List<WidgetWorkout>>`
  - `fun WorkoutWithExercises.toWidgetWorkout(lastDurationMillis: Long?): WidgetWorkout`

- [ ] **Step 1: Убрать бессмысленный `suspend`**

`GetAllWorkoutsWithExerciseUseCase.kt` — функция возвращает `Flow` и ничего не приостанавливает, а вызывающих у неё сейчас ноль. Заменить:

```kotlin
    suspend operator fun invoke(): Flow<List<WorkoutWithExercises>> {
```

на:

```kotlin
    operator fun invoke(): Flow<List<WorkoutWithExercises>> {
```

- [ ] **Step 2: Написать падающий тест**

`app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity

class GetWidgetWorkoutsUseCaseTest {

    private fun workoutWith(
        id: Int,
        name: String,
        lastUseAt: Long,
        exerciseCount: Int = 1
    ) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
        exercises = (1..exerciseCount).map { index ->
            ExerciseEntity(
                id = index,
                workoutId = id.toLong(),
                name = "Упражнение $index",
                weight = 10.0,
                sets = 3,
                reps = 12,
                restTimeMillis = 120_000L,
                orderInWorkout = index,
                note = ""
            )
        }
    )

    private fun useCase(
        workouts: List<WorkoutWithExercises> = emptyList(),
        durations: Map<Int, Long> = emptyMap()
    ): GetWidgetWorkoutsUseCase {
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getAll() } returns flowOf(workouts)
        every { getDurations() } returns flowOf(durations)
        return GetWidgetWorkoutsUseCase(getAll, getDurations)
    }

    @Test
    fun `sorts workouts by lastUseAt descending`() = runTest {
        val result = useCase(
            workouts = listOf(
                workoutWith(id = 1, name = "Старая", lastUseAt = 100L),
                workoutWith(id = 2, name = "Свежая", lastUseAt = 300L),
                workoutWith(id = 3, name = "Средняя", lastUseAt = 200L)
            )
        )().first()

        assertEquals(listOf("Свежая", "Средняя", "Старая"), result.map { it.name })
    }

    @Test
    fun `puts the last session duration on the matching workout`() = runTest {
        val result = useCase(
            workouts = listOf(
                workoutWith(id = 7, name = "Ноги", lastUseAt = 100L),
                workoutWith(id = 9, name = "Спина", lastUseAt = 50L)
            ),
            durations = mapOf(7 to 3_120_000L)
        )().first()

        assertEquals(3_120_000L, result[0].lastDurationMillis)
        assertNull(result[1].lastDurationMillis)
    }

    @Test
    fun `counts exercises of each workout`() = runTest {
        val result = useCase(
            workouts = listOf(workoutWith(id = 1, name = "Ноги", lastUseAt = 1L, exerciseCount = 7))
        )().first()

        assertEquals(7, result[0].exerciseCount)
    }

    @Test
    fun `returns an empty list when there are no workouts`() = runTest {
        assertEquals(emptyList<Any>(), useCase()().first())
    }

    @Test
    fun `returns an empty list instead of failing when the source flow throws`() = runTest {
        val getAll = mockk<GetAllWorkoutsWithExerciseUseCase>()
        val getDurations = mockk<GetLastSessionDurationsUseCase>()
        every { getAll() } returns flow { throw IllegalStateException("db is gone") }
        every { getDurations() } returns flowOf(emptyMap())

        val result = GetWidgetWorkoutsUseCase(getAll, getDurations)().first()

        assertEquals(emptyList<Any>(), result)
    }
}
```

- [ ] **Step 3: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*GetWidgetWorkoutsUseCaseTest*"`
Expected: FAIL — не компилируется, `GetWidgetWorkoutsUseCase` и `WidgetWorkout` не существуют.

- [ ] **Step 4: Написать модель**

`domain/model/WidgetWorkout.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

/**
 * Строка виджета быстрого старта.
 * lastDurationMillis == null означает, что тренировку ещё ни разу не делали.
 */
data class WidgetWorkout(
    val id: Int,
    val name: String,
    val exerciseCount: Int,
    val lastDurationMillis: Long?
)
```

- [ ] **Step 5: Добавить маппер**

В конец `data/mapper/WorkoutMapper.kt`:

```kotlin
fun WorkoutWithExercises.toWidgetWorkout(lastDurationMillis: Long?): WidgetWorkout {
    return WidgetWorkout(
        id = workout.id,
        name = workout.name,
        exerciseCount = exercises.size,
        lastDurationMillis = lastDurationMillis
    )
}
```

Добавить импорт `ru.hopes.workouttimer.domain.model.WidgetWorkout`.

- [ ] **Step 6: Написать use case**

`domain/usecase/GetWidgetWorkoutsUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import ru.hopes.workouttimer.data.mapper.toWidgetWorkout
import ru.hopes.workouttimer.domain.model.WidgetWorkout
import javax.inject.Inject

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
        }
            // Виджет с пустым списком лучше системной «ошибки загрузки» на всю плитку
            .catch { emit(emptyList()) }
}
```

- [ ] **Step 7: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*GetWidgetWorkoutsUseCaseTest*"`
Expected: PASS, 5 тестов.

- [ ] **Step 8: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/model/WidgetWorkout.kt \
        app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCase.kt \
        app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetAllWorkoutsWithExerciseUseCase.kt \
        app/src/main/java/ru/hopes/workouttimer/data/mapper/WorkoutMapper.kt \
        app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt
git commit -m "feat: источник данных для виджета быстрого старта"
```

---

## Task 4: Подпись строки виджета

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatter.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatterTest.kt`

**Interfaces:**
- Consumes: `DateFormatter.formatDurationToString(millis: Long): String`.
- Produces: `fun formatWidgetSubtitle(exerciseCount: Int, lastDurationMillis: Long?): String`.

Функция намеренно не принимает `Context`: при `unitTests.isReturnDefaultValues = true` тест на строках из ресурсов проверял бы значения, которые сам же и подставил через мок. Строки захардкожены так же, как в `DateFormatter.formatDurationToString()`.

- [ ] **Step 1: Написать падающий тест**

`app/src/test/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatterTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSubtitleFormatterTest {

    @Test
    fun `shows exercise count and the last session duration`() {
        assertEquals("7 упр. · 52 мин", formatWidgetSubtitle(exerciseCount = 7, lastDurationMillis = 3_120_000L))
    }

    @Test
    fun `shows hours for long sessions`() {
        assertEquals("5 упр. · 1 ч 5 мин", formatWidgetSubtitle(exerciseCount = 5, lastDurationMillis = 3_900_000L))
    }

    @Test
    fun `says the workout was never done when there is no session`() {
        assertEquals("5 упр. · ещё не делали", formatWidgetSubtitle(exerciseCount = 5, lastDurationMillis = null))
    }

    @Test
    fun `handles a workout without exercises`() {
        assertEquals("0 упр. · ещё не делали", formatWidgetSubtitle(exerciseCount = 0, lastDurationMillis = null))
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "*WidgetSubtitleFormatterTest*"`
Expected: FAIL — не компилируется, `formatWidgetSubtitle` не существует.

- [ ] **Step 3: Написать функцию**

`presentation/widget/WidgetSubtitleFormatter.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.widget

import ru.hopes.workouttimer.presentation.utils.DateFormatter

/**
 * Подпись строки виджета: «7 упр. · 52 мин».
 * Без Context — иначе юнит-тест проверял бы строки, подставленные моком ресурсов.
 */
fun formatWidgetSubtitle(exerciseCount: Int, lastDurationMillis: Long?): String {
    val duration = lastDurationMillis
        ?.let { DateFormatter.formatDurationToString(it) }
        ?: "ещё не делали"
    return "$exerciseCount упр. · $duration"
}
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest --tests "*WidgetSubtitleFormatterTest*"`
Expected: PASS, 4 теста.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatter.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/widget/WidgetSubtitleFormatterTest.kt
git commit -m "feat: подпись строки виджета"
```

---

## Task 5: Deep link в экран выполнения

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt:20-24,88-104,130,143-155`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/MainActivity.kt:19-38`

**Interfaces:**
- Consumes: ничего.
- Produces:
  - `internal sealed class Screen` (была `private`)
  - `Screen.Execution.DEEP_LINK_PATTERN: String` = `"workouttimer://execution/{workout_id}"`
  - `Screen.Execution.createDeepLink(workoutId: Int): String`
  - `NavGraph(newIntent: Intent? = null, onIntentHandled: () -> Unit = {})`

- [ ] **Step 1: Открыть `Screen` и добавить сборку ссылки**

`NavGraph.kt`, строка 130: заменить `private sealed class Screen(val route: String) {` на:

```kotlin
internal sealed class Screen(val route: String) {
```

В `data object Execution` добавить константу и функцию рядом с существующей `createRoute()`:

```kotlin
    data object Execution : Screen("execution/{workout_id}") {

        const val DEEP_LINK_PATTERN = "workouttimer://execution/{workout_id}"

        fun createRoute(workoutId: Int): String {
            return "execution/$workoutId"
        }

        // Ссылка для виджета: шаблон URI живёт здесь же, рядом с маршрутом
        fun createDeepLink(workoutId: Int): String {
            return "workouttimer://execution/$workoutId"
        }

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }
```

- [ ] **Step 2: Объявить deep link у маршрута**

В `NavGraph.kt` у `composable(route = Screen.Execution.route, ...)` добавить параметр `deepLinks`:

```kotlin
        composable(
            route = Screen.Execution.route,
            arguments = listOf(
                navArgument("workout_id") { type = NavType.IntType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = Screen.Execution.DEEP_LINK_PATTERN }
            )
        ) { entry ->
```

Добавить импорт `androidx.navigation.navDeepLink`.

- [ ] **Step 3: Принять новый интент в `NavGraph`**

Заменить сигнатуру и начало `NavGraph`:

```kotlin
@Composable
fun NavGraph(
    newIntent: Intent? = null,
    onIntentHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    // Стартовый интент разбирает сам NavController при создании графа,
    // сюда приходят только интенты из onNewIntent — иначе он обработался бы дважды
    LaunchedEffect(newIntent) {
        if (newIntent != null) {
            navController.handleDeepLink(newIntent)
            onIntentHandled()
        }
    }
```

Добавить импорты: `android.content.Intent`, `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 4: Пробросить интент из `MainActivity`**

`MainActivity.kt`: добавить поле и переопределить `onNewIntent`, а `setContent` передаёт значение в `NavGraph`:

```kotlin
    private val newIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Запрашиваем разрешение на уведомления (Android 13+)
        requestNotificationPermission()

        setContent {
            WorkoutTimerTheme {
                NavGraph(
                    newIntent = newIntent.value,
                    onIntentHandled = { newIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newIntent.value = intent
    }
```

Добавить импорты: `android.content.Intent`, `androidx.compose.runtime.mutableStateOf`.

- [ ] **Step 5: Разрешить повторное использование экземпляра активити**

`AndroidManifest.xml`, к `<activity android:name=".presentation.MainActivity" ...>` добавить:

```xml
            android:launchMode="singleTop"
```

`intent-filter` с `VIEW`/`BROWSABLE` **не добавляем**: виджет шлёт явный интент с проставленным компонентом, а явные интенты через фильтры не проходят вовсе. Фильтр только открыл бы схему `workouttimer://` для любого стороннего приложения.

- [ ] **Step 6: Собрать**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Проверить ссылку вручную**

Установить: `./gradlew :app:installDebug`

Взять существующий id тренировки (или создать тренировку в приложении) и выполнить при **закрытом** приложении:

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "workouttimer://execution/1" \
  -n ru.hopes.workouttimer/.presentation.MainActivity \
  -f 0x10008000
```

`0x10008000` = `FLAG_ACTIVITY_NEW_TASK` (0x10000000) `or FLAG_ACTIVITY_CLEAR_TASK` (0x8000) — те же флаги, что будет ставить виджет.

Ожидается: открывается экран выполнения нужной тренировки; «Назад» ведёт на список тренировок, а не закрывает приложение; сплеш показывается один раз.

Повторить ту же команду при **открытом** приложении — переход должен произойти без перезапуска активити.

- [ ] **Step 8: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/MainActivity.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: deep link в экран выполнения тренировки"
```

---

## Task 6: Содержимое виджета

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/QuickStartWidget.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/WidgetEntryPoint.kt`

**Interfaces:**
- Consumes: `GetWidgetWorkoutsUseCase` (Task 3), `formatWidgetSubtitle` (Task 4), `Screen.Execution.createDeepLink` (Task 5), `WidgetWorkout`.
- Produces: `interface WidgetEntryPoint` с методом `getWidgetWorkoutsUseCase(): GetWidgetWorkoutsUseCase`.

Юнит-тестов здесь нет: отрисовка Glance ими не покрывается. Проверка — ручная, шаг 4.

- [ ] **Step 1: Написать мост из Hilt**

`presentation/widget/WidgetEntryPoint.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.hopes.workouttimer.domain.usecase.GetWidgetWorkoutsUseCase

// GlanceAppWidget создаётся системой и не имеет своего DI-скоупа,
// поэтому зависимости достаём из SingletonComponent вручную
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getWidgetWorkoutsUseCase(): GetWidgetWorkoutsUseCase
}
```

- [ ] **Step 2: Заменить заглушку на реальное содержимое**

`presentation/widget/QuickStartWidget.kt` целиком:

```kotlin
package ru.hopes.workouttimer.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.WidgetWorkout
import ru.hopes.workouttimer.presentation.MainActivity
import ru.hopes.workouttimer.presentation.navigation.Screen

class QuickStartWidget : GlanceAppWidget() {

    // Разметка от размера не зависит — список скроллится сам
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val getWidgetWorkouts = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .getWidgetWorkoutsUseCase()

        // Flow создаётся один раз, а не на каждую рекомпозицию
        val workoutsFlow = getWidgetWorkouts()

        provideContent {
            val workouts by workoutsFlow.collectAsState(initial = emptyList())
            GlanceTheme {
                WidgetContent(workouts)
            }
        }
    }
}

@Composable
private fun WidgetContent(workouts: List<WidgetWorkout>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(actionStartActivity<MainActivity>())
        )

        if (workouts.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp
                ),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>())
            )
        } else {
            LazyColumn {
                items(workouts, itemId = { it.id.toLong() }) { workout ->
                    WorkoutRow(workout)
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(workout: WidgetWorkout) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(actionStartActivity(startWorkoutIntent(context, workout.id)))
    ) {
        Text(
            text = workout.name,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            text = formatWidgetSubtitle(workout.exerciseCount, workout.lastDurationMillis),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Флаги обязательны: без NEW_TASK|CLEAR_TASK NavController обрезает стек до
 * одного экрана выполнения, и «Назад» закрывает приложение вместо возврата к списку.
 */
private fun startWorkoutIntent(context: Context, workoutId: Int): Intent =
    Intent(
        Intent.ACTION_VIEW,
        Screen.Execution.createDeepLink(workoutId).toUri(),
        context,
        MainActivity::class.java
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
```

- [ ] **Step 3: Собрать**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Проверить на устройстве**

Run: `./gradlew :app:installDebug`

Проверить вручную:
- виджет показывает тренировки, последняя выполненная — сверху
- у тренировки с историей видна длительность прошлой сессии, у остальных — «ещё не делали»
- тап по строке открывает экран выполнения именно этой тренировки
- «Назад» с экрана выполнения ведёт на список тренировок
- тап по шапке открывает список тренировок
- при удалении всех тренировок виджет показывает «Нет тренировок»
- список скроллится, если тренировок больше, чем влезает
- виджет читаем в светлой и тёмной теме

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/widget/
git commit -m "feat: список тренировок и переходы в виджете"
```

---

## Task 7: Пуш обновлений виджета

Закрывает случай, когда тренировка завершилась, пока пользователь был в приложении, а Glance-сессия остановлена, потому что домашний экран не виден.

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/repository/WidgetUpdater.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/widget/GlanceWidgetUpdater.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/ExportImportRepositoryImpl.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `QuickStartWidget` (Task 2).
- Produces: `interface WidgetUpdater { suspend fun requestUpdate() }`; конструктор `WorkoutRepositoryImpl(dao: WorkoutDao, widgetUpdater: WidgetUpdater)`; конструктор `ExportImportRepositoryImpl(context: Context, dao: WorkoutDao, widgetUpdater: WidgetUpdater)`.

- [ ] **Step 1: Написать падающие тесты**

В `WorkoutRepositoryImplTest` добавить fake и тесты:

```kotlin
    private class RecordingWidgetUpdater : WidgetUpdater {
        var updateCount = 0
            private set

        override suspend fun requestUpdate() {
            updateCount++
        }
    }

    @Test
    fun `updateLastUseAt requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.updateLastUseAt(7)

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `addWorkout requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.addWorkout(Workout(id = 0, name = "Ноги", exercises = emptyList(), lastUseAt = 1L))

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `deleteWorkout requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.deleteWorkout(WorkoutEntity(id = 3, name = "Ноги", lastUseAt = 1L))

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `updateWorkout requests a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.updateWorkout(Workout(id = 3, name = "Ноги", exercises = emptyList(), lastUseAt = 1L))

        assertEquals(1, updater.updateCount)
    }

    @Test
    fun `addWorkoutSession does not request a widget update`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val updater = RecordingWidgetUpdater()
        val repo = WorkoutRepositoryImpl(dao, updater)

        repo.addWorkoutSession(workoutId = 7, startedAt = 1L, finishedAt = 2L, durationMillis = 1L)

        // Сессия всегда пишется в паре с updateLastUseAt(), второй пуш был бы лишним
        assertEquals(0, updater.updateCount)
    }
```

Добавить импорты: `ru.hopes.workouttimer.domain.repository.WidgetUpdater`, `ru.hopes.workouttimer.data.entity.WorkoutEntity`.

Во **всех** существующих тестах класса заменить `WorkoutRepositoryImpl(dao)` на `WorkoutRepositoryImpl(dao, RecordingWidgetUpdater())` — это строки с `val repo = WorkoutRepositoryImpl(dao)` в четырёх тестах (три исходных плюс добавленный в Task 1).

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `./gradlew :app:testDebugUnitTest --tests "*WorkoutRepositoryImplTest*"`
Expected: FAIL — не компилируется, `WidgetUpdater` не существует, у конструктора один параметр.

- [ ] **Step 3: Объявить интерфейс**

`domain/repository/WidgetUpdater.kt`:

```kotlin
package ru.hopes.workouttimer.domain.repository

/**
 * Просит виджет перерисоваться. suspend — потому что GlanceAppWidget.updateAll()
 * приостанавливающая; все вызывающие в репозиториях и так suspend.
 */
interface WidgetUpdater {
    suspend fun requestUpdate()
}
```

- [ ] **Step 4: Дёргать обновление из `WorkoutRepositoryImpl`**

Заменить конструктор:

```kotlin
class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
    private val widgetUpdater: WidgetUpdater
) : WorkoutRepository {
```

Добавить импорт `ru.hopes.workouttimer.domain.repository.WidgetUpdater`.

Дописать `widgetUpdater.requestUpdate()` последней строкой в теле четырёх методов:

- `updateWorkout()` — после `dao.insertExercises(exerciseEntities)`, **вне** блока `withContext`
- `deleteWorkout()` — после блока `withContext`
- `addWorkout()` — после `dao.insertExercises(exerciseEntities)`
- `updateLastUseAt()` — после `dao.updateLastUseAt(...)`

В `addWorkoutSession()` вызов **не** добавляется.

- [ ] **Step 5: Дёргать обновление после импорта**

`ExportImportRepositoryImpl`: добавить в конструктор третий параметр

```kotlin
class ExportImportRepositoryImpl @Inject constructor(
    private val context: Context,
    private val dao: WorkoutDao,
    private val widgetUpdater: WidgetUpdater
) : ExportImportRepository {
```

В `importFromJson()`, в ветке успеха — перед созданием `ImportResult(success = true, ...)` — добавить:

```kotlin
                if (importedCount > 0) {
                    widgetUpdater.requestUpdate()
                }
```

Добавить импорт `ru.hopes.workouttimer.domain.repository.WidgetUpdater`.

- [ ] **Step 6: Написать реализацию**

`presentation/widget/GlanceWidgetUpdater.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.hopes.workouttimer.domain.repository.WidgetUpdater
import javax.inject.Inject

class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetUpdater {

    override suspend fun requestUpdate() {
        QuickStartWidget().updateAll(context)
    }
}
```

- [ ] **Step 7: Связать в Hilt**

`AppModule` — это `object`, поэтому `@Binds` использовать нельзя. Добавить провайдер и передать новую зависимость в оба репозитория:

```kotlin
    @Provides
    @Singleton
    fun provideWidgetUpdater(@ApplicationContext ctx: Context): WidgetUpdater {
        return GlanceWidgetUpdater(ctx)
    }

    @Provides
    @Singleton
    fun provideWorkoutRepository(
        dao: WorkoutDao,
        widgetUpdater: WidgetUpdater
    ): WorkoutRepository {
        return WorkoutRepositoryImpl(dao, widgetUpdater)
    }

    @Provides
    @Singleton
    fun provideExportImportRepository(
        @ApplicationContext ctx: Context,
        dao: WorkoutDao,
        widgetUpdater: WidgetUpdater
    ): ExportImportRepository {
        return ExportImportRepositoryImpl(ctx, dao, widgetUpdater)
    }
```

Добавить импорты: `ru.hopes.workouttimer.domain.repository.WidgetUpdater`, `ru.hopes.workouttimer.presentation.widget.GlanceWidgetUpdater`.

- [ ] **Step 8: Запустить тесты и убедиться, что они проходят**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, все тесты модуля, включая существовавшие раньше.

- [ ] **Step 9: Проверить на устройстве**

Run: `./gradlew :app:installDebug`

Проверить вручную:
- завершить тренировку, которая была не первой в виджете, находясь в приложении; выйти на домашний экран — тренировка поднялась наверх, у неё появилась длительность
- создать тренировку — она появилась в виджете
- удалить тренировку — она пропала из виджета
- отредактировать тренировку — название обновилось, но наверх она **не** поднялась и «дней назад» на главном экране не сбросилось

- [ ] **Step 10: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/repository/WidgetUpdater.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/widget/GlanceWidgetUpdater.kt \
        app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt \
        app/src/main/java/ru/hopes/workouttimer/data/ExportImportRepositoryImpl.kt \
        app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt \
        app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt
git commit -m "feat: обновление виджета после изменений тренировок"
```

---

## Финальная проверка

- [ ] `./gradlew :app:testDebugUnitTest` — все тесты проходят
- [ ] `./gradlew :app:assembleDebug` — сборка проходит
- [ ] Пройден полный сценарий: поставить виджет, начать по нему тренировку, довести до конца, вернуться на домашний экран и увидеть обновлённый порядок
