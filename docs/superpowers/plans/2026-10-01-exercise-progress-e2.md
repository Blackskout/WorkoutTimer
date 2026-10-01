# Прогресс в упражнении, E2 — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Подсказки из справочника в редакторе тренировки, разворот сессии в истории с фактическими подходами, экран «Упражнения» с переименованием и сменой единицы, честные единицы нагрузки (кг / плита с добавкой / без веса) в редакторе, на экране подхода и в файле экспорта. Схема базы не меняется.

**Architecture:** Единица живёт только в `exercise_catalog.unit`: `WorkoutWithExercises` переходит на вложенную связь `ExerciseWithCatalog`, маппер берёт единицу оттуда (нет записи — `KG`). Любое сохранение шаблона (редактор, импорт, смена единицы) приводит нагрузку к единице записи чистыми функциями `fitLoadToUnit`/`convertLoadToUnit`. Справочник читают новый `ExerciseCatalogRepository` и тонкие use case'ы; многошаговые записи (переименование, смена единицы) — `@Transaction`-методы по умолчанию в `WorkoutDao`, как в E1. Подпись подхода — одна функция `formatSet` с шаблонами из `strings.xml`, лучший подход — одна функция `bestSet`.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (Material3, тема «Dark Athletic»), Hilt, Room 2.8.4 (KSP, схема v8 без изменений), Coroutines/Flow, JUnit4 + MockK + coroutines-test (JVM), Compose UI test + in-memory Room (`androidTest`, эмулятор).

**Spec:** `docs/superpowers/specs/2026-10-01-exercise-progress-design.md` — раздел «E2 — подсказки, история с подходами, экран «Упражнения», единицы»; из «Модели данных» — «Как читается единица (с E2)» и доменные модели `CatalogExercise`, `SessionSet`; E2-части разделов «Ошибки и краевые случаи», «Тестирование», «Маршруты и строки». E3 (экран прогресса) вне рамок: строки, ведущие на прогресс, в E2 есть, но не нажимаются.

## Global Constraints

- Ветка `feature/exercise-progress-e2` поверх `feature/exercise-progress` (E1, PR #21). Push и PR в этом плане не делаются — их делает контроллер после финального ревью.
- Схема базы **не меняется**: версия 8, `app/schemas/ru.hopes.workouttimer.data.dao.AppDatabase/8.json` не трогается, новых миграций нет. Если после правок `git status` показывает изменённый `8.json` — в сущностях ошибка: остановиться и разобраться, а не коммитить схему.
- `fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 4)` в `AppModule.kt` **не трогать**.
- Кириллицу в SQL не сравнивать (`LOWER()` в SQLite — только ASCII). Ключ названия — только `exerciseNameKey()` в Kotlin. Пустой ввод проверяется `isBlank()` **до** `exerciseNameKey()`: от пустой строки он возвращает ключ «без названия», а не пустую строку.
- Регулярки — без флага `(?U)` (движок ICU на Android его не знает). Непечатные символы в исходниках — только экранированными: `\u00A0`, никогда не литералом.
- Единица упражнения хранится только в `exercise_catalog.unit`; шаблон читает её через `ExerciseWithCatalog`, при отсутствии записи — `KG`. `session_sets.unit` (единица на момент подхода) не переписывается никогда.
- Одна подпись подхода на всё приложение: `formatLoad` / `formatSet` / `formatSetList` в `presentation/utils/SetFormat.kt`. Один лучший подход: `bestSet()` в `domain/model/SessionSets.kt`. Своих форматтеров веса на экранах не заводить.
- Все пользовательские тексты — в `app/src/main/res/values/strings.xml`, в секции своего экрана, с префиксами `unit_`, `catalog_`, `history_`, `create_`, `execution_`.
- Дизайн — только существующие компоненты и токены: `EmptyState`, `AppBottomSheet`/`ActionSheet`, `AlertDialog` (как `NoteEditDialog`), `WheelPicker`/`WheelRow`, `StatTile`, `SectionHeader`, `ScreenPadding`/`CardSpacing`/`SectionSpacing`, `MaterialTheme.colorScheme/shapes/typography`. Новых цветов, шрифтов, форм и размеров текста нет.
- Строки, которые с E3 поведут на прогресс (упражнение в развёрнутой сессии истории, строка экрана «Упражнения»), в E2 не кликабельны.
- Комментарии в коде — по-русски, коротко, объясняют «почему». Сообщения коммитов — по-русски с префиксом (`feat:`, `test:`, `refactor:`), последняя строка `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Инструментальные тесты — **только** на `emulator-5554`: перед запуском `~/Library/Android/sdk/platform-tools/adb devices` — если подключено что-то кроме `emulator-5554`, не запускать (`connectedAndroidTest` сносит приложение). Запуск: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`. Эмулятор не запущен — `~/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro -no-snapshot-save -no-audio &` и дождаться `adb shell getprop sys.boot_completed` = 1. Имена тестовых методов в `androidTest` — через подчёркивания.
- JVM-тесты: `./gradlew :app:testDebugUnitTest --tests '<pattern>'`. Room in-memory в JVM-тестах **не собирается** (см. спеку, «Тестирование»), тесты DAO — только `androidTest`.
- В конце каждой задачи: `./gradlew :app:testDebugUnitTest :app:lintDebug` зелёные, lint — 0 ошибок; задачи, трогающие `androidTest`, дополнительно гоняют свои инструментальные классы на эмуляторе.

## Review Focus

1. Пустое поле — и в редакторе, и в поиске экрана «Упражнения» (в том числе из одних пробелов или `\u00A0`): `exerciseNameKey("")` равен ключу «Без названия», поэтому без ранней проверки редактор подсказал бы «Без названия», а поиск отфильтровал бы весь справочник до одной записи. Ожидается: подсказок нет, поиск показывает всё. Тесты — Task 6 и Task 9.
2. Повторный выбор той же единицы в меню упражнения: плита 5 + 2.5 кг не должна превратиться в «плиту 5» (`convertLoadToUnit` обнуляет добавку). Ожидается: ничего не меняется. Тесты — Task 4 (DAO) и Task 9 (ViewModel не пишет вовсе).
3. Переименование, меняющее только регистр или пробелы («присед» → «Присед»): новый ключ занят **этой же** записью. Ожидается: переименование проходит, а не «Такое упражнение уже есть». Тест — Task 4.
4. Добавка к плите на пути редактор → `ExerciseItem` → `Exercise` → `ExerciseEntity`: сейчас её нет ни в `ExerciseItem`, ни в `WorkoutRepositoryImpl`, и любое сохранение в редакторе обнулило бы «+2 кг». Ожидается: добавка переживает открытие и сохранение тренировки. Тесты — Task 3 (репозиторий, DAO) и Task 6 (ViewModel).
5. Редактирование существующей тренировки: `loadWorkout` сейчас заменяет состояние целиком (`CreateWorkoutState(...)`), что стёрло бы уже пришедший справочник — подсказки и единицы пропали бы именно в режиме правки. Ожидается: справочник сохраняется. Тест — Task 6.

## Карта файлов

- `domain/model/` — `ExerciseUnit.kt` (+ `exerciseUnitOf`), новые `ExerciseLoad.kt` (сетка нагрузки), `ExerciseCatalog.kt` (типы справочника и подходов), `SessionSets.kt` (`bestSet`, группировка, сводка справочника).
- `presentation/utils/SetFormat.kt` — подпись нагрузки и подхода; `presentation/utils/DaysAgo.kt` — давность, вынесенная из списка тренировок.
- `presentation/ui/components/WheelPicker.kt` — значения барабанов плиты и добавки.
- `data/dao/` — `WorkoutWithExercises.kt` (+ `ExerciseWithCatalog`), новые `ExerciseDraft.kt`, `LoggedSetRow.kt`; `WorkoutDao.kt` — нагрузка при сохранении, переименование, смена единицы, запросы подходов.
- `data/mapper/` — `WorkoutMapper.kt` (единица из связи), новый `CatalogMapper.kt`, `ExportMapper.kt`.
- `domain/repository/ExerciseCatalogRepository.kt` + `data/ExerciseCatalogRepositoryImpl.kt` + DI; use case'ы `ObserveCatalogUseCase`, `ObserveCatalogSummariesUseCase`, `RenameCatalogExerciseUseCase`, `ChangeCatalogUnitUseCase`, `GetWorkoutSessionSetsUseCase`.
- Экраны: `creation/` (подсказки, барабаны по единице, `ExerciseSuggestions.kt`), `workoutExecution/` (плитки, строка отдыха, лист веса), `workoutHistory/` (разворот сессии), новый `exercises/` (экран «Упражнения»), `workouts/ListWorkoutScreen.kt` (иконка в шапке), `navigation/NavGraph.kt` (маршрут `exercises`).

---

### Task 1: Доменные правила — единица, нагрузка, лучший подход, сводка справочника

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/model/ExerciseUnit.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/ExerciseLoad.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/ExerciseCatalog.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/SessionSets.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/model/ExerciseLoadTest.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/model/SessionSetsTest.kt`

**Interfaces:**
- Consumes: `exerciseNameKey(raw: String): String`, `enum class ExerciseUnit { KG, PLATE, BODYWEIGHT }` (E1).
- Produces:
  - `fun exerciseUnitOf(name: String?): ExerciseUnit` — неизвестное/`null` → `KG`
  - `const val PLATE_MIN = 1`, `const val PLATE_MAX = 30`, `const val PLATE_EXTRA_MAX = 10.0`, `const val PLATE_EXTRA_STEP = 0.5`
  - `data class Load(val weight: Double, val extraWeight: Double)`
  - `fun fitLoadToUnit(weight: Double, extraWeight: Double, unit: ExerciseUnit): Load` — сохранение шаблона
  - `fun convertLoadToUnit(weight: Double, extraWeight: Double, unit: ExerciseUnit): Load` — смена единицы
  - `data class CatalogExercise(val id: Long, val name: String, val unit: ExerciseUnit)`
  - `data class SessionSet(val id: Long, val sessionId: Long, val catalogId: Long, val weight: Double, val extraWeight: Double, val reps: Int, val unit: ExerciseUnit)`
  - `data class LoggedSet(val set: SessionSet, val exerciseName: String, val finishedAt: Long)`
  - `data class SessionExerciseSets(val catalogId: Long, val exerciseName: String, val sets: List<SessionSet>)`
  - `data class CatalogSummary(val exercise: CatalogExercise, val lastBest: SessionSet?, val lastDoneAt: Long?)`
  - `enum class RenameResult { RENAMED, NAME_TAKEN, BLANK }`
  - `fun bestSet(sets: List<SessionSet>, unit: ExerciseUnit): SessionSet?`
  - `fun groupSetsByExercise(sets: List<LoggedSet>): List<SessionExerciseSets>`
  - `fun summarizeCatalog(catalog: List<CatalogExercise>, lastSessionSets: List<LoggedSet>): List<CatalogSummary>`

- [ ] **Step 1: Написать падающие тесты**

`ExerciseLoadTest.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLoadTest {

    @Test
    fun `плита округляется до ближайшего номера и зажимается в 1-30`() {
        assertEquals(5.0, fitLoadToUnit(4.6, 0.0, ExerciseUnit.PLATE).weight, 0.0)
        assertEquals(3.0, fitLoadToUnit(2.5, 0.0, ExerciseUnit.PLATE).weight, 0.0)
        assertEquals(1.0, fitLoadToUnit(0.0, 0.0, ExerciseUnit.PLATE).weight, 0.0)
        assertEquals(30.0, fitLoadToUnit(60.0, 0.0, ExerciseUnit.PLATE).weight, 0.0)
    }

    @Test
    fun `добавка к плите ложится на шаг 0_5 и не выходит из 0-10`() {
        assertEquals(2.5, fitLoadToUnit(5.0, 2.3, ExerciseUnit.PLATE).extraWeight, 0.0)
        assertEquals(10.0, fitLoadToUnit(5.0, 12.0, ExerciseUnit.PLATE).extraWeight, 0.0)
        assertEquals(0.0, fitLoadToUnit(5.0, -1.0, ExerciseUnit.PLATE).extraWeight, 0.0)
    }

    @Test
    fun `у кг добавки нет, вес сохраняется`() {
        assertEquals(Load(62.5, 0.0), fitLoadToUnit(62.5, 2.5, ExerciseUnit.KG))
    }

    @Test
    fun `без веса нагрузка всегда нулевая`() {
        assertEquals(Load(0.0, 0.0), fitLoadToUnit(40.0, 2.0, ExerciseUnit.BODYWEIGHT))
    }

    @Test
    fun `смена на плиту округляет вес и обнуляет добавку`() {
        assertEquals(Load(30.0, 0.0), convertLoadToUnit(60.0, 0.0, ExerciseUnit.PLATE))
        assertEquals(Load(5.0, 0.0), convertLoadToUnit(5.0, 2.5, ExerciseUnit.PLATE))
    }

    @Test
    fun `смена на кг сохраняет число веса`() {
        assertEquals(Load(5.0, 0.0), convertLoadToUnit(5.0, 2.5, ExerciseUnit.KG))
    }

    @Test
    fun `смена на без веса обнуляет всё`() {
        assertEquals(Load(0.0, 0.0), convertLoadToUnit(5.0, 2.5, ExerciseUnit.BODYWEIGHT))
    }

    @Test
    fun `единица читается по имени, неизвестная — как кг`() {
        assertEquals(ExerciseUnit.PLATE, exerciseUnitOf("PLATE"))
        assertEquals(ExerciseUnit.BODYWEIGHT, exerciseUnitOf("BODYWEIGHT"))
        assertEquals(ExerciseUnit.KG, exerciseUnitOf(null))
        assertEquals(ExerciseUnit.KG, exerciseUnitOf("LBS"))
        assertEquals(ExerciseUnit.KG, exerciseUnitOf("plate"))
    }
}
```

`SessionSetsTest.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.model.ExerciseUnit.BODYWEIGHT
import ru.hopes.workouttimer.domain.model.ExerciseUnit.KG
import ru.hopes.workouttimer.domain.model.ExerciseUnit.PLATE

class SessionSetsTest {

    private fun set(
        unit: ExerciseUnit,
        weight: Double,
        reps: Int,
        extra: Double = 0.0,
        id: Long = 0L,
        sessionId: Long = 1L,
        catalogId: Long = 1L
    ) = SessionSet(
        id = id, sessionId = sessionId, catalogId = catalogId,
        weight = weight, extraWeight = extra, reps = reps, unit = unit
    )

    private fun logged(set: SessionSet, name: String, finishedAt: Long = 1_000L) =
        LoggedSet(set = set, exerciseName = name, finishedAt = finishedAt)

    @Test
    fun `в кг больший вес важнее повторов`() {
        val heavy = set(KG, 62.5, 6)
        assertEquals(heavy, bestSet(listOf(set(KG, 60.0, 10), heavy), KG))
    }

    @Test
    fun `в кг при равном весе побеждают повторы`() {
        val more = set(KG, 60.0, 9)
        assertEquals(more, bestSet(listOf(set(KG, 60.0, 8), more), KG))
    }

    @Test
    fun `у плиты номер важнее добавки, добавка важнее повторов`() {
        val best = set(PLATE, 6.0, 6, extra = 2.0)
        val sets = listOf(
            set(PLATE, 5.0, 15, extra = 2.0),
            set(PLATE, 6.0, 12),
            best,
            set(PLATE, 6.0, 12, extra = 1.0)
        )
        assertEquals(best, bestSet(sets, PLATE))
    }

    @Test
    fun `у плиты при той же нагрузке побеждают повторы`() {
        val more = set(PLATE, 6.0, 12, extra = 2.0)
        assertEquals(more, bestSet(listOf(set(PLATE, 6.0, 10, extra = 2.0), more), PLATE))
    }

    @Test
    fun `без веса решают повторы`() {
        val more = set(BODYWEIGHT, 0.0, 15)
        assertEquals(more, bestSet(listOf(set(BODYWEIGHT, 0.0, 12), more), BODYWEIGHT))
    }

    @Test
    fun `подходы в другой единице не сравниваются`() {
        val plate = set(PLATE, 3.0, 10)
        assertEquals(plate, bestSet(listOf(set(KG, 100.0, 5), plate), PLATE))
        assertNull(bestSet(listOf(set(KG, 100.0, 5)), BODYWEIGHT))
    }

    @Test
    fun `при полном равенстве лучший — первый записанный`() {
        assertEquals(1L, bestSet(listOf(set(KG, 60.0, 8, id = 1L), set(KG, 60.0, 8, id = 2L)), KG)?.id)
    }

    @Test
    fun `группировка сохраняет порядок первого появления упражнения`() {
        val groups = groupSetsByExercise(
            listOf(
                logged(set(KG, 60.0, 8, id = 1, catalogId = 7), "Присед"),
                logged(set(KG, 20.0, 10, id = 2, catalogId = 9), "Выпады"),
                logged(set(KG, 62.5, 6, id = 3, catalogId = 7), "Присед")
            )
        )
        assertEquals(listOf("Присед", "Выпады"), groups.map { it.exerciseName })
        assertEquals(listOf(7L, 9L), groups.map { it.catalogId })
        assertEquals(listOf(1L, 3L), groups[0].sets.map { it.id })
    }

    @Test
    fun `справочник идёт по алфавиту без учёта регистра и ё`() {
        val catalog = listOf("Тяга", "жим лёжа", "Ёлочка", "Армейский жим")
            .mapIndexed { index, name -> CatalogExercise(index.toLong() + 1, name, KG) }
        assertEquals(
            listOf("Армейский жим", "Ёлочка", "жим лёжа", "Тяга"),
            summarizeCatalog(catalog, emptyList()).map { it.exercise.name }
        )
    }

    @Test
    fun `сводка берёт лучший подход последней сессии и её время`() {
        val squat = CatalogExercise(7L, "Присед", KG)
        val summary = summarizeCatalog(
            listOf(squat),
            listOf(
                logged(set(KG, 60.0, 8, id = 1, catalogId = 7), "Присед", finishedAt = 9_000L),
                logged(set(KG, 62.5, 6, id = 2, catalogId = 7), "Присед", finishedAt = 9_000L)
            )
        ).single()
        assertEquals(62.5, summary.lastBest!!.weight, 0.0)
        assertEquals(9_000L, summary.lastDoneAt)
    }

    @Test
    fun `запись без подходов — без лучшего подхода и давности`() {
        val summary = summarizeCatalog(listOf(CatalogExercise(1L, "Присед", KG)), emptyList()).single()
        assertNull(summary.lastBest)
        assertNull(summary.lastDoneAt)
    }

    @Test
    fun `лучший подход последней сессии считается в её единице, а не в текущей`() {
        // Единицу сменили на плиту, а последняя сессия была ещё в кг: строка
        // справочника честно показывает то, что было сделано, — «60 кг × 8».
        val summary = summarizeCatalog(
            listOf(CatalogExercise(7L, "Тяга", PLATE)),
            listOf(logged(set(KG, 60.0, 8, id = 1, catalogId = 7), "Тяга"))
        ).single()
        assertEquals(KG, summary.lastBest!!.unit)
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseLoadTest*' --tests '*SessionSetsTest*'`
Expected: FAIL — `Unresolved reference 'fitLoadToUnit'`, `Unresolved reference 'SessionSet'`.

- [ ] **Step 3: Реализация**

`ExerciseUnit.kt` — целиком:

```kotlin
package ru.hopes.workouttimer.domain.model

/** Единица нагрузки упражнения. Имя константы хранится в базе и в файле экспорта как TEXT. */
enum class ExerciseUnit { KG, PLATE, BODYWEIGHT }

/**
 * Единица из базы или файла. Неизвестное или отсутствующее значение читается
 * как кг: так жили все данные до E2, и файл из будущей версии не должен ронять импорт.
 */
fun exerciseUnitOf(name: String?): ExerciseUnit =
    ExerciseUnit.entries.firstOrNull { it.name == name } ?: ExerciseUnit.KG
```

`ExerciseLoad.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

import kotlin.math.roundToInt

/** Номера плит в стопке тренажёра. */
const val PLATE_MIN = 1
const val PLATE_MAX = 30

/** Добавка к плите — гантель поверх стопки. */
const val PLATE_EXTRA_MAX = 10.0
const val PLATE_EXTRA_STEP = 0.5

/** Нагрузка шаблона: вес в кг или номер плиты и добавка к плите. */
data class Load(val weight: Double, val extraWeight: Double)

/**
 * Нагрузка, приведённая к сетке единицы, без лишних потерь. Так сохраняется шаблон
 * (редактор, импорт): добавка к плите остаётся, у кг и без веса её не бывает.
 */
fun fitLoadToUnit(weight: Double, extraWeight: Double, unit: ExerciseUnit): Load = when (unit) {
    ExerciseUnit.KG -> Load(weight, 0.0)
    ExerciseUnit.PLATE -> Load(
        weight = weight.roundToInt().coerceIn(PLATE_MIN, PLATE_MAX).toDouble(),
        extraWeight = ((extraWeight / PLATE_EXTRA_STEP).roundToInt() * PLATE_EXTRA_STEP)
            .coerceIn(0.0, PLATE_EXTRA_MAX)
    )
    ExerciseUnit.BODYWEIGHT -> Load(0.0, 0.0)
}

/**
 * Смена единицы на экране «Упражнения». Прежняя добавка к новой единице не
 * относится: → плита — номер в 1..30 и добавка 0; → кг — число веса
 * сохраняется; → без веса — всё 0.
 */
fun convertLoadToUnit(weight: Double, extraWeight: Double, unit: ExerciseUnit): Load =
    fitLoadToUnit(weight, if (unit == ExerciseUnit.PLATE) 0.0 else extraWeight, unit)
```

`ExerciseCatalog.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

/** Запись справочника упражнений. */
data class CatalogExercise(
    val id: Long,
    val name: String,
    val unit: ExerciseUnit
)

/** Фактический подход; unit — единица на момент подхода, после смены единицы не меняется. */
data class SessionSet(
    val id: Long,
    val sessionId: Long,
    val catalogId: Long,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: ExerciseUnit
)

/** Подход с тем, что нужно для показа: текущее название записи справочника и время сессии. */
data class LoggedSet(
    val set: SessionSet,
    val exerciseName: String,
    val finishedAt: Long
)

/** Подходы одного упражнения внутри сессии, в порядке записи. */
data class SessionExerciseSets(
    val catalogId: Long,
    val exerciseName: String,
    val sets: List<SessionSet>
)

/** Строка экрана «Упражнения»: запись и лучший подход её последней сессии. */
data class CatalogSummary(
    val exercise: CatalogExercise,
    val lastBest: SessionSet?,
    val lastDoneAt: Long?
)

/** Итог переименования записи справочника. */
enum class RenameResult { RENAMED, NAME_TAKEN, BLANK }
```

`SessionSets.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

/**
 * Лучший подход — одна функция на всё приложение. Сравниваются только подходы
 * в единице [unit]: «60 кг» и «плита 5» несравнимы. При полном равенстве
 * побеждает первый записанный (maxWithOrNull оставляет первый максимум).
 */
fun bestSet(sets: List<SessionSet>, unit: ExerciseUnit): SessionSet? {
    val comparator: Comparator<SessionSet> = when (unit) {
        ExerciseUnit.KG -> compareBy<SessionSet>({ it.weight }, { it.reps })
        ExerciseUnit.PLATE -> compareBy<SessionSet>({ it.weight }, { it.extraWeight }, { it.reps })
        ExerciseUnit.BODYWEIGHT -> compareBy<SessionSet> { it.reps }
    }
    return sets.filter { it.unit == unit }.maxWithOrNull(comparator)
}

/**
 * Подходы сессии по упражнениям в порядке первого появления. Вход — в порядке
 * записи (id): groupBy сохраняет порядок ключей и элементов.
 */
fun groupSetsByExercise(sets: List<LoggedSet>): List<SessionExerciseSets> =
    sets.groupBy { it.set.catalogId }.map { (catalogId, group) ->
        SessionExerciseSets(
            catalogId = catalogId,
            exerciseName = group.first().exerciseName,
            sets = group.map { it.set }
        )
    }

/**
 * Строки экрана «Упражнения». Алфавит — по ключу названия: он без регистра и с
 * «ё» как «е», а сырая строка поставила бы «Ёлочку» перед «А». Лучший подход —
 * в единице последнего подхода сессии: после смены единицы строка показывает
 * то, что реально было сделано, пока не появится новая сессия.
 */
fun summarizeCatalog(
    catalog: List<CatalogExercise>,
    lastSessionSets: List<LoggedSet>
): List<CatalogSummary> {
    val setsByCatalog = lastSessionSets.groupBy { it.set.catalogId }
    return catalog
        .sortedWith(compareBy<CatalogExercise>({ exerciseNameKey(it.name) }, { it.id }))
        .map { entry ->
            val sets = setsByCatalog[entry.id].orEmpty()
            val last = sets.lastOrNull()
            CatalogSummary(
                exercise = entry,
                lastBest = last?.let { bestSet(sets.map { logged -> logged.set }, it.set.unit) },
                lastDoneAt = last?.finishedAt
            )
        }
}
```

- [ ] **Step 4: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseLoadTest*' --tests '*SessionSetsTest*'`
Expected: PASS, 8 + 12 тестов.

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/model app/src/test/java/ru/hopes/workouttimer/domain/model
git commit -m "$(cat <<'EOF'
feat: доменные правила единиц, нагрузки и лучшего подхода

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Подпись подхода и барабаны плиты

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/utils/SetFormat.kt`
- Modify: `app/src/main/res/values/strings.xml` (новая секция «Единицы»)
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/ui/components/WheelPicker.kt:51-52`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/utils/SetFormatTest.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/ui/components/WheelPickerTest.kt`

**Interfaces:**
- Consumes: `ExerciseUnit`, `SessionSet`, `PLATE_MIN`, `PLATE_MAX`, `PLATE_EXTRA_MAX`, `PLATE_EXTRA_STEP` (Task 1); `Double.toCorrectNum(): String` (существует, `presentation/utils/FormaterNumber.kt`).
- Produces:
  - `data class SetFormat(val loadKg: String, val loadPlate: String, val loadPlateExtra: String, val set: String)`
  - `@Composable fun setFormat(): SetFormat` — шаблоны из ресурсов
  - `@Composable fun unitName(unit: ExerciseUnit): String` — «кг» / «плита» / «без веса»
  - `fun formatLoad(unit: ExerciseUnit, weight: Double, extraWeight: Double, format: SetFormat): String?` — `null` для `BODYWEIGHT`
  - `fun formatSet(set: SessionSet, format: SetFormat, bareKg: Boolean = false): String`
  - `fun formatSetList(sets: List<SessionSet>, format: SetFormat): String`
  - `val PlateValues: List<Double>` (1.0..30.0), `val PlateExtraValues: List<Double>` (0.0..10.0 шаг 0.5)
  - Строки: `unit_name_kg`, `unit_name_plate`, `unit_name_bodyweight`, `unit_plate_extra`, `unit_plate_with_extra`, `unit_load_kg`, `unit_load_plate`, `unit_load_plate_extra`, `unit_set`

- [ ] **Step 1: Строки единиц**

`strings.xml` — новая секция сразу после «Общие» (после `d_ago`):

```xml

    <!-- Единицы нагрузки. Шаблоны unit_load_* и unit_set собирает SetFormat. -->
    <string name="unit_name_kg">кг</string>
    <string name="unit_name_plate">плита</string>
    <string name="unit_name_bodyweight">без веса</string>
    <string name="unit_plate_extra">+кг</string>
    <string name="unit_plate_with_extra">плита +%1$s кг</string>
    <string name="unit_load_kg">%1$s кг</string>
    <string name="unit_load_plate">плита %1$s</string>
    <string name="unit_load_plate_extra">плита %1$s +%2$s кг</string>
    <string name="unit_set">%1$s × %2$d</string>
```

- [ ] **Step 2: Написать падающие тесты**

`SetFormatTest.kt` — шаблоны читаются из настоящего `strings.xml`, чтобы тест не разошёлся с ресурсами:

```kotlin
package ru.hopes.workouttimer.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ExerciseUnit.BODYWEIGHT
import ru.hopes.workouttimer.domain.model.ExerciseUnit.KG
import ru.hopes.workouttimer.domain.model.ExerciseUnit.PLATE
import ru.hopes.workouttimer.domain.model.SessionSet
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SetFormatTest {

    // Рабочий каталог JVM-тестов — модуль app/, поэтому путь относительный.
    private val strings: Map<String, String> by lazy {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private val format by lazy {
        SetFormat(
            loadKg = strings.getValue("unit_load_kg"),
            loadPlate = strings.getValue("unit_load_plate"),
            loadPlateExtra = strings.getValue("unit_load_plate_extra"),
            set = strings.getValue("unit_set")
        )
    }

    private fun set(unit: ExerciseUnit, weight: Double, reps: Int, extra: Double = 0.0) =
        SessionSet(id = 0, sessionId = 1, catalogId = 1, weight = weight, extraWeight = extra, reps = reps, unit = unit)

    @Test
    fun `кг — вес с единицей и повторы`() {
        assertEquals("60 кг × 8", formatSet(set(KG, 60.0, 8), format))
        assertEquals("62.5 кг × 6", formatSet(set(KG, 62.5, 6), format))
    }

    @Test
    fun `кг в перечислении — без единицы`() {
        assertEquals("60 × 8", formatSet(set(KG, 60.0, 8), format, bareKg = true))
    }

    @Test
    fun `плита — номер, добавка только если есть`() {
        assertEquals("плита 5 × 12", formatSet(set(PLATE, 5.0, 12), format))
        assertEquals("плита 5 +2 кг × 12", formatSet(set(PLATE, 5.0, 12, extra = 2.0), format))
        assertEquals("плита 5 +2.5 кг × 12", formatSet(set(PLATE, 5.0, 12, extra = 2.5), format))
    }

    @Test
    fun `плита пишется полностью и в перечислении`() {
        assertEquals("плита 5 × 12", formatSet(set(PLATE, 5.0, 12), format, bareKg = true))
    }

    @Test
    fun `без веса — только повторы`() {
        assertEquals("12", formatSet(set(BODYWEIGHT, 0.0, 12), format))
    }

    @Test
    fun `перечисление подходов — первый полностью, следующие кг без единицы`() {
        assertEquals(
            "60 кг × 8 · 60 × 8 · 62.5 × 6",
            formatSetList(listOf(set(KG, 60.0, 8), set(KG, 60.0, 8), set(KG, 62.5, 6)), format)
        )
    }

    @Test
    fun `перечисление плиты и без веса`() {
        assertEquals(
            "плита 5 × 12 · плита 5 +2 кг × 10",
            formatSetList(listOf(set(PLATE, 5.0, 12), set(PLATE, 5.0, 10, extra = 2.0)), format)
        )
        assertEquals("12 · 10", formatSetList(listOf(set(BODYWEIGHT, 0.0, 12), set(BODYWEIGHT, 0.0, 10)), format))
    }

    @Test
    fun `нагрузка без повторов`() {
        assertEquals("60 кг", formatLoad(KG, 60.0, 0.0, format))
        assertEquals("плита 5 +2 кг", formatLoad(PLATE, 5.0, 2.0, format))
        assertNull(formatLoad(BODYWEIGHT, 0.0, 0.0, format))
    }
}
```

В конец `WheelPickerTest.kt` (перед закрывающей скобкой класса):

```kotlin

    @Test
    fun `барабан плиты — номера с 1 по 30`() {
        assertEquals(30, PlateValues.size)
        assertEquals(1.0, PlateValues.first(), 0.0)
        assertEquals(30.0, PlateValues.last(), 0.0)
    }

    @Test
    fun `барабан добавки — от 0 до 10 с шагом 0_5`() {
        assertEquals(21, PlateExtraValues.size)
        assertEquals(0.0, PlateExtraValues.first(), 0.0)
        assertEquals(10.0, PlateExtraValues.last(), 0.0)
        assertEquals(2.5, PlateExtraValues[wheelIndexOfNearest(PlateExtraValues, 2.3)], 0.0)
    }
```

- [ ] **Step 3: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*SetFormatTest*' --tests '*WheelPickerTest*'`
Expected: FAIL — `Unresolved reference 'SetFormat'`, `Unresolved reference 'PlateValues'`.

- [ ] **Step 4: Реализация**

`SetFormat.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import java.util.Locale

/**
 * Шаблоны подписи подхода из strings.xml. Сами функции форматирования чистые
 * и получают шаблоны параметром — так их проверяет JVM-тест без ресурсов Android.
 */
data class SetFormat(
    val loadKg: String,
    val loadPlate: String,
    val loadPlateExtra: String,
    val set: String
)

/** Шаблоны без подстановки: getString без аргументов возвращает текст с %1$s как есть. */
@Composable
fun setFormat(): SetFormat = SetFormat(
    loadKg = stringResource(R.string.unit_load_kg),
    loadPlate = stringResource(R.string.unit_load_plate),
    loadPlateExtra = stringResource(R.string.unit_load_plate_extra),
    set = stringResource(R.string.unit_set)
)

/** Название единицы для меню и подсказок: «кг», «плита», «без веса». */
@Composable
fun unitName(unit: ExerciseUnit): String = stringResource(
    when (unit) {
        ExerciseUnit.KG -> R.string.unit_name_kg
        ExerciseUnit.PLATE -> R.string.unit_name_plate
        ExerciseUnit.BODYWEIGHT -> R.string.unit_name_bodyweight
    }
)

// Разделитель подходов — знак препинания, а не текст: в ресурсы не выносится.
private const val SET_SEPARATOR = " · "

private fun String.fill(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

/** Нагрузка без повторов: «60 кг», «плита 5», «плита 5 +2 кг». У упражнения без веса её нет. */
fun formatLoad(unit: ExerciseUnit, weight: Double, extraWeight: Double, format: SetFormat): String? =
    when (unit) {
        ExerciseUnit.KG -> format.loadKg.fill(weight.toCorrectNum())
        ExerciseUnit.PLATE ->
            if (extraWeight > 0.0) {
                format.loadPlateExtra.fill(weight.toCorrectNum(), extraWeight.toCorrectNum())
            } else {
                format.loadPlate.fill(weight.toCorrectNum())
            }
        ExerciseUnit.BODYWEIGHT -> null
    }

/**
 * Подпись подхода — одна на всё приложение: «60 кг × 8», «плита 5 +2 кг × 12», «12».
 * [bareKg] — для перечисления: следующие подходы в кг пишутся без единицы, «60 × 8».
 */
fun formatSet(set: SessionSet, format: SetFormat, bareKg: Boolean = false): String {
    val load = if (bareKg && set.unit == ExerciseUnit.KG) {
        set.weight.toCorrectNum()
    } else {
        formatLoad(set.unit, set.weight, set.extraWeight, format)
    } ?: return set.reps.toString()
    return format.set.fill(load, set.reps)
}

/** Подходы одного упражнения: «60 кг × 8 · 60 × 8 · 62.5 × 6». */
fun formatSetList(sets: List<SessionSet>, format: SetFormat): String =
    sets.mapIndexed { index, set ->
        formatSet(set, format, bareKg = index > 0 && sets[index - 1].unit == set.unit)
    }.joinToString(SET_SEPARATOR)
```

`WheelPicker.kt` — после `RepsValues` (строка 52) добавить и импортировать константы из `domain.model`:

```kotlin

/** Барабан плиты: номер в стопке тренажёра. */
val PlateValues: List<Double> = (PLATE_MIN..PLATE_MAX).map { it.toDouble() }

/** Барабан добавки к плите: гантель поверх стопки, шаг 0.5 кг. */
val PlateExtraValues: List<Double> =
    generateSequence(0.0) { it + PLATE_EXTRA_STEP }.takeWhile { it <= PLATE_EXTRA_MAX }.toList()
```

Импорты: `ru.hopes.workouttimer.domain.model.PLATE_EXTRA_MAX`, `PLATE_EXTRA_STEP`, `PLATE_MAX`, `PLATE_MIN`.

- [ ] **Step 5: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*SetFormatTest*' --tests '*WheelPickerTest*'`
Expected: PASS (8 новых в `SetFormatTest`, 7 в `WheelPickerTest`).

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок (предупреждения `UnusedResources` про новые `unit_*` уйдут к Task 6–9).

- [ ] **Step 6: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation app/src/main/res/values/strings.xml app/src/test/java/ru/hopes/workouttimer/presentation
git commit -m "$(cat <<'EOF'
feat: единая подпись подхода и барабаны плиты

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---
### Task 3: Единица из справочника при чтении, нагрузка и добавка при сохранении

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutWithExercises.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/dao/ExerciseDraft.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt` (`findOrCreateCatalog`, `resolveCatalog`, `insertWorkoutResolvingCatalog`, `importWorkouts`, `updateWorkoutResolvingCatalog`, `finishSession`)
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/mapper/WorkoutMapper.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt:44-56`, `:76-88`
- Modify (фикстуры под новую связь): `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt` (`previewWorkout`), `app/src/test/java/ru/hopes/workouttimer/domain/usecase/GetWidgetWorkoutsUseCaseTest.kt` (`workoutWith`), `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModelTest.kt` (`workoutWith`)
- Test: `app/src/test/java/ru/hopes/workouttimer/data/mapper/WorkoutMapperTest.kt` (новый)
- Test: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/data/dao/WorkoutDaoTest.kt`

**Interfaces:**
- Consumes: `exerciseUnitOf`, `fitLoadToUnit` (Task 1).
- Produces:
  - `data class ExerciseWithCatalog(@Embedded val exercise: ExerciseEntity, @Relation(parentColumn = "catalogId", entityColumn = "id") val catalog: ExerciseCatalogEntity?)`
  - `WorkoutWithExercises.exercises: List<ExerciseWithCatalog>` (было `List<ExerciseEntity>`)
  - `data class ExerciseDraft(val exercise: ExerciseEntity, val unitIfNew: ExerciseUnit)`
  - `suspend fun WorkoutDao.findOrCreateCatalog(rawName: String, unitIfNew: ExerciseUnit): ExerciseCatalogEntity` (второй параметр новый, без значения по умолчанию)
  - Сохранение шаблона (`insertWorkoutResolvingCatalog`, `updateWorkoutResolvingCatalog`, `importWorkouts`) приводит `weight`/`extraWeight` к единице найденной записи через `fitLoadToUnit`.
  - `Workout.toDomain()` заполняет `Exercise.unit` из записи справочника (нет записи — `KG`).
  - `WorkoutRepositoryImpl.addWorkout/updateWorkout` передают `Exercise.extraWeight` в `ExerciseEntity`.

- [ ] **Step 1: Падающие JVM-тесты**

`WorkoutMapperTest.kt`:

```kotlin
package ru.hopes.workouttimer.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.data.dao.ExerciseWithCatalog
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.ExerciseUnit

class WorkoutMapperTest {

    private fun entity(order: Int, extra: Double = 0.0) = ExerciseEntity(
        id = order, workoutId = 1L, name = "Тяга блока", weight = 5.0, sets = 3, reps = 12,
        restTimeMillis = 60_000L, orderInWorkout = order, catalogId = 4L, extraWeight = extra
    )

    private fun catalog(unit: String) =
        ExerciseCatalogEntity(id = 4L, name = "Тяга блока", nameKey = "тяга блока", unit = unit)

    private fun workout(vararg exercises: ExerciseWithCatalog) = WorkoutWithExercises(
        workout = WorkoutEntity(id = 1, name = "Спина", lastUseAt = 0L),
        exercises = exercises.toList()
    )

    @Test
    fun `единица и добавка берутся из справочника и шаблона`() {
        val exercise = workout(ExerciseWithCatalog(entity(1, extra = 2.5), catalog("PLATE")))
            .toDomain().exercises.single()
        assertEquals(ExerciseUnit.PLATE, exercise.unit)
        assertEquals(2.5, exercise.extraWeight, 0.0)
        assertEquals(4L, exercise.catalogId)
    }

    @Test
    fun `без записи справочника упражнение читается в кг`() {
        val exercise = workout(ExerciseWithCatalog(entity(1), catalog = null)).toDomain().exercises.single()
        assertEquals(ExerciseUnit.KG, exercise.unit)
    }

    @Test
    fun `упражнения идут в порядке orderInWorkout`() {
        val exercises = workout(
            ExerciseWithCatalog(entity(2), catalog("KG")),
            ExerciseWithCatalog(entity(1), catalog("KG"))
        ).toDomain().exercises
        assertEquals(listOf(1, 2), exercises.map { it.order })
    }
}
```

В `WorkoutRepositoryImplTest.kt` (импорты `io.mockk.slot` уже есть; добавить `ru.hopes.workouttimer.data.entity.ExerciseEntity` и `ru.hopes.workouttimer.domain.model.Exercise`):

```kotlin

    private val plateExercise = Exercise(
        name = "Тяга блока", weight = 5.0, sets = 3, reps = 12, order = 1, extraWeight = 2.5
    )

    @Test
    fun `addWorkout передаёт в DAO добавку к плите`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val saved = slot<List<ExerciseEntity>>()
        coEvery { dao.insertWorkoutResolvingCatalog(any(), capture(saved)) } returns 1L
        val repo = WorkoutRepositoryImpl(dao, RecordingWidgetUpdater())

        repo.addWorkout(Workout(name = "Спина", exercises = listOf(plateExercise), lastUseAt = 0L))

        assertEquals(2.5, saved.captured.single().extraWeight, 0.0)
    }

    @Test
    fun `updateWorkout передаёт в DAO добавку к плите`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val saved = slot<List<ExerciseEntity>>()
        coEvery { dao.updateWorkoutResolvingCatalog(any(), any(), capture(saved)) } returns Unit
        val repo = WorkoutRepositoryImpl(dao, RecordingWidgetUpdater())

        repo.updateWorkout(Workout(id = 3, name = "Спина", exercises = listOf(plateExercise), lastUseAt = 0L))

        assertEquals(2.5, saved.captured.single().extraWeight, 0.0)
    }
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutMapperTest*' --tests '*WorkoutRepositoryImplTest*'`
Expected: FAIL — компиляция: `Unresolved reference 'ExerciseWithCatalog'`. Тесты репозитория без Step 5 упали бы на `expected:<2.5> but was:<0.0>`.

- [ ] **Step 3: Вложенная связь и маппер**

`WorkoutWithExercises.kt` — целиком:

```kotlin
package ru.hopes.workouttimer.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId",
        entity = ExerciseEntity::class
    )
    val exercises: List<ExerciseWithCatalog>
)

/**
 * Упражнение вместе с записью справочника: единица живёт только там. catalog —
 * null, если инвариант catalogId > 0 когда-нибудь нарушится; тогда упражнение
 * читается в кг, а не роняет экран.
 */
data class ExerciseWithCatalog(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "catalogId", entityColumn = "id")
    val catalog: ExerciseCatalogEntity?
)
```

`WorkoutMapper.kt` — `toDomain()` целиком (импорт `ru.hopes.workouttimer.domain.model.exerciseUnitOf`; `toWidgetWorkout` не меняется — он читает только `exercises.size`):

```kotlin
fun WorkoutWithExercises.toDomain(): Workout {
    val exercisesDomain = exercises
        .sortedBy { it.exercise.orderInWorkout }
        .map { (e, catalog) ->
            Exercise(
                id = e.id,
                name = e.name,
                sets = e.sets,
                reps = e.reps,
                timeMillis = e.restTimeMillis,
                order = e.orderInWorkout,
                weight = e.weight,
                note = e.note,
                catalogId = e.catalogId,
                unit = exerciseUnitOf(catalog?.unit),
                extraWeight = e.extraWeight
            )
        }
    return Workout(
        id = workout.id,
        name = workout.name,
        lastUseAt = workout.lastUseAt,
        exercises = exercisesDomain
    )
}
```

- [ ] **Step 4: Нагрузка при сохранении в DAO**

`ExerciseDraft.kt`:

```kotlin
package ru.hopes.workouttimer.data.dao

import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.domain.model.ExerciseUnit

/**
 * Упражнение до привязки к справочнику. unitIfNew — единица новой записи, если
 * записи с таким ключом ещё нет: редактор создаёт кг, импорт — единицу из файла.
 * У существующей записи своя единица главнее.
 */
data class ExerciseDraft(
    val exercise: ExerciseEntity,
    val unitIfNew: ExerciseUnit
)
```

`WorkoutDao.kt` — заменить `findOrCreateCatalog`, `resolveCatalog` и места их вызова (импорты `ru.hopes.workouttimer.domain.model.exerciseUnitOf`, `ru.hopes.workouttimer.domain.model.fitLoadToUnit`). Значения по умолчанию у параметров методов DAO не используются: Room генерирует реализацию интерфейса, и синтетические `$default`-перегрузки ему ни к чему.

```kotlin
    /** Ищет запись по ключу названия; если её нет — создаёт с единицей [unitIfNew]. */
    suspend fun findOrCreateCatalog(rawName: String, unitIfNew: ExerciseUnit): ExerciseCatalogEntity {
        val name = normalizedExerciseName(rawName)
        val key = exerciseNameKey(name)
        findCatalogByKey(key)?.let { return it }
        val entry = ExerciseCatalogEntity(name = name, nameKey = key, unit = unitIfNew.name)
        return entry.copy(id = insertCatalog(entry))
    }

    // В упражнение пишется название из справочника: «присед » сохраняется как «Присед».
    // Нагрузка приводится к единице найденной записи — она главнее того, что пришло
    // из редактора или файла: у плиты целый номер 1..30, у кг и без веса нет добавки.
    private suspend fun resolveCatalog(workoutId: Long, drafts: List<ExerciseDraft>): List<ExerciseEntity> =
        drafts.map { draft ->
            val entry = findOrCreateCatalog(draft.exercise.name, draft.unitIfNew)
            val load = fitLoadToUnit(draft.exercise.weight, draft.exercise.extraWeight, exerciseUnitOf(entry.unit))
            draft.exercise.copy(
                workoutId = workoutId,
                catalogId = entry.id,
                name = entry.name,
                weight = load.weight,
                extraWeight = load.extraWeight
            )
        }

    // Редактор создаёт новые записи в кг: единицу меняют только на экране «Упражнения».
    private fun editorDrafts(exercises: List<ExerciseEntity>): List<ExerciseDraft> =
        exercises.map { ExerciseDraft(it, ExerciseUnit.KG) }
```

В `insertWorkoutResolvingCatalog` и `updateWorkoutResolvingCatalog`: `resolveCatalog(workoutId, exercises)` → `resolveCatalog(workoutId, editorDrafts(exercises))` (в `update` — `resolveCatalog(workoutId.toLong(), editorDrafts(exercises))`). В `importWorkouts` пока так же: `insertExercises(resolveCatalog(workoutId, editorDrafts(exercises)))` — единицу из файла подключает Task 5.

В `finishSession` — подход без записи создаёт её в своей единице:

```kotlin
            val catalogId = draft.catalogId.takeIf { it > 0 && findCatalogById(it) != null }
                ?: findOrCreateCatalog(draft.exerciseName, exerciseUnitOf(draft.unit)).id
```

- [ ] **Step 5: Добавка в репозитории**

`WorkoutRepositoryImpl.kt` — в обоих `ExerciseEntity(` (в `updateWorkout` и `addWorkout`) после `catalogId = 0L // проставит транзакция DAO` добавить строку:

```kotlin
                extraWeight = ex.extraWeight
```

(предыдущую строку дополнить запятой: `catalogId = 0L, // проставит транзакция DAO`).

- [ ] **Step 6: Фикстуры под новую связь**

`GetWidgetWorkoutsUseCaseTest.kt` — `workoutWith` целиком (импорт `ru.hopes.workouttimer.data.dao.ExerciseWithCatalog`):

```kotlin
    private fun workoutWith(
        id: Int,
        name: String,
        lastUseAt: Long,
        exerciseCount: Int = 1
    ) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
        exercises = (1..exerciseCount).map { index ->
            ExerciseWithCatalog(
                exercise = ExerciseEntity(
                    id = index,
                    workoutId = id.toLong(),
                    name = "Упражнение $index",
                    weight = 10.0,
                    sets = 3,
                    reps = 12,
                    restTimeMillis = 120_000L,
                    orderInWorkout = index,
                    note = "",
                    catalogId = index.toLong() + 1
                ),
                catalog = null
            )
        }
    )
```

`ListWorkoutViewModelTest.kt` — `workoutWith` целиком (тот же импорт):

```kotlin
    private fun workoutWith(id: Int, name: String, lastUseAt: Long) = WorkoutWithExercises(
        workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
        exercises = listOf(
            ExerciseWithCatalog(
                exercise = ExerciseEntity(
                    id = id,
                    workoutId = id.toLong(),
                    name = "Упражнение",
                    weight = 10.0,
                    sets = 3,
                    reps = 12,
                    restTimeMillis = 120_000L,
                    orderInWorkout = 1,
                    note = "",
                    catalogId = 1L
                ),
                catalog = null
            )
        )
    )
```

`ListWorkoutScreen.kt` — `previewWorkout` целиком (импорт `ru.hopes.workouttimer.data.dao.ExerciseWithCatalog`):

```kotlin
private fun previewWorkout(
    id: Int,
    name: String,
    lastUseAt: Long,
    exerciseCount: Int
): WorkoutWithExercises = WorkoutWithExercises(
    workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
    exercises = List(exerciseCount) { index ->
        ExerciseWithCatalog(
            exercise = ExerciseEntity(
                id = index,
                workoutId = id.toLong(),
                name = "Упражнение ${index + 1}",
                weight = 20.0,
                sets = 3,
                reps = 10,
                restTimeMillis = 60_000,
                orderInWorkout = index,
                catalogId = index.toLong() + 1
            ),
            catalog = null
        )
    }
)
```

`WorkoutDaoTest.kt` — в первом тесте `сохранение_находит_одну_запись_для_одинаковых_ключей` строку получения упражнений заменить на:

```kotlin
        val exercises = dao.getAllWorkoutsWithExercises().first()
            .single { it.workout.id.toLong() == id }.exercises.map { it.exercise }
```

- [ ] **Step 7: Инструментальные тесты сохранения**

В `WorkoutDaoTest.kt` добавить импорты `ru.hopes.workouttimer.data.mapper.toDomain`, `ru.hopes.workouttimer.domain.model.ExerciseUnit` и тесты:

```kotlin

    private suspend fun savedTemplate(): ExerciseEntity =
        dao.getAllWorkoutsWithExercises().first().single().exercises.single().exercise

    @Test
    fun единица_упражнения_читается_из_справочника() = runBlocking {
        dao.findOrCreateCatalog("Тяга блока", ExerciseUnit.PLATE)
        dao.insertWorkoutResolvingCatalog(WorkoutEntity(name = "Спина", lastUseAt = 0), listOf(exercise("тяга блока")))

        val row = dao.getAllWorkoutsWithExercises().first().single()
        assertEquals("PLATE", row.exercises.single().catalog?.unit)
        assertEquals(ExerciseUnit.PLATE, row.toDomain().exercises.single().unit)
    }

    @Test
    fun сохранение_в_запись_плиты_приводит_шаблон_к_сетке_плиты() = runBlocking {
        dao.findOrCreateCatalog("Тяга блока", ExerciseUnit.PLATE)
        dao.insertWorkoutResolvingCatalog(
            WorkoutEntity(name = "Спина", lastUseAt = 0),
            listOf(exercise("Тяга блока").copy(weight = 60.0, extraWeight = 2.3))
        )

        val saved = savedTemplate()
        assertEquals(30.0, saved.weight, 0.0)
        assertEquals(2.5, saved.extraWeight, 0.0)
    }

    @Test
    fun сохранение_в_запись_кг_обнуляет_добавку() = runBlocking {
        dao.insertWorkoutResolvingCatalog(
            WorkoutEntity(name = "Ноги", lastUseAt = 0),
            listOf(exercise("Присед").copy(extraWeight = 2.5))
        )

        val saved = savedTemplate()
        assertEquals(50.0, saved.weight, 0.0)
        assertEquals(0.0, saved.extraWeight, 0.0)
    }

    @Test
    fun добавка_к_плите_переживает_пересохранение_тренировки() = runBlocking {
        dao.findOrCreateCatalog("Тяга блока", ExerciseUnit.PLATE)
        val id = dao.insertWorkoutResolvingCatalog(
            WorkoutEntity(name = "Спина", lastUseAt = 0),
            listOf(exercise("Тяга блока").copy(weight = 5.0, extraWeight = 2.0))
        )
        dao.updateWorkoutResolvingCatalog(
            id.toInt(), "Спина",
            listOf(exercise("Тяга блока").copy(weight = 6.0, extraWeight = 2.0))
        )

        val saved = savedTemplate()
        assertEquals(6.0, saved.weight, 0.0)
        assertEquals(2.0, saved.extraWeight, 0.0)
    }
```

- [ ] **Step 8: Запустить**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutMapperTest*' --tests '*WorkoutRepositoryImplTest*' --tests '*GetWidgetWorkoutsUseCaseTest*' --tests '*ListWorkoutViewModelTest*'`
Expected: PASS.

Run: `git status --short app/schemas`
Expected: пусто — схема v8 не изменилась (`@Relation` схему не трогает).

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.data.dao.WorkoutDaoTest`
Expected: PASS, 12 тестов (8 из E1 + 4 новых).

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

- [ ] **Step 9: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: единица упражнения из справочника, нагрузка и добавка при сохранении

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Справочник в DAO — переименование, смена единицы, подходы для показа

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/data/dao/LoggedSetRow.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/mapper/CatalogMapper.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/repository/ExerciseCatalogRepository.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/ExerciseCatalogRepositoryImpl.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/ObserveCatalogUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/ObserveCatalogSummariesUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/RenameCatalogExerciseUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/ChangeCatalogUnitUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWorkoutSessionSetsUseCase.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/data/dao/CatalogDaoTest.kt` (новый)
- Test: `app/src/test/java/ru/hopes/workouttimer/data/ExerciseCatalogRepositoryImplTest.kt` (новый)
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/usecase/CatalogUseCasesTest.kt` (новый)

**Interfaces:**
- Consumes: `CatalogExercise`, `SessionSet`, `LoggedSet`, `SessionExerciseSets`, `CatalogSummary`, `RenameResult`, `convertLoadToUnit`, `exerciseUnitOf`, `groupSetsByExercise`, `summarizeCatalog` (Task 1); `findOrCreateCatalog(rawName, unitIfNew)`, `ExerciseWithCatalog` (Task 3).
- Produces:
  - DAO: `fun observeCatalog(): Flow<List<ExerciseCatalogEntity>>`, `fun observeSetsForWorkout(workoutId: Long): Flow<List<LoggedSetRow>>`, `fun observeLastSessionSets(): Flow<List<LoggedSetRow>>`, `suspend fun renameCatalog(id: Long, rawName: String): RenameResult`, `suspend fun changeCatalogUnit(id: Long, unit: ExerciseUnit)`
  - `data class LoggedSetRow(val id: Long, val sessionId: Long, val catalogId: Long, val exerciseName: String, val weight: Double, val extraWeight: Double, val reps: Int, val unit: String, val finishedAt: Long)`
  - `interface ExerciseCatalogRepository { fun observeCatalog(): Flow<List<CatalogExercise>>; fun observeLastSessionSets(): Flow<List<LoggedSet>>; fun observeSetsForWorkout(workoutId: Int): Flow<List<LoggedSet>>; suspend fun rename(id: Long, name: String): RenameResult; suspend fun changeUnit(id: Long, unit: ExerciseUnit) }`
  - `class ObserveCatalogUseCase { operator fun invoke(): Flow<List<CatalogExercise>> }`
  - `class ObserveCatalogSummariesUseCase { operator fun invoke(): Flow<List<CatalogSummary>> }`
  - `class RenameCatalogExerciseUseCase { suspend operator fun invoke(id: Long, name: String): RenameResult }`
  - `class ChangeCatalogUnitUseCase { suspend operator fun invoke(id: Long, unit: ExerciseUnit) }`
  - `class GetWorkoutSessionSetsUseCase { operator fun invoke(workoutId: Int): Flow<Map<Long, List<SessionExerciseSets>>> }` — ключ — `sessionId`

- [ ] **Step 1: Падающие инструментальные тесты DAO**

`CatalogDaoTest.kt`:

```kotlin
package ru.hopes.workouttimer.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.model.exerciseNameKey

@RunWith(AndroidJUnit4::class)
class CatalogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.workoutDao()
    }

    @After
    fun tearDown() = db.close()

    private fun exercise(name: String, weight: Double = 50.0, extraWeight: Double = 0.0, order: Int = 0) =
        ExerciseEntity(
            workoutId = 0, name = name, weight = weight, sets = 3, reps = 8,
            restTimeMillis = 60_000, orderInWorkout = order, catalogId = 0L, extraWeight = extraWeight
        )

    private suspend fun saveWorkout(name: String, vararg exercises: ExerciseEntity): Long =
        dao.insertWorkoutResolvingCatalog(WorkoutEntity(name = name, lastUseAt = 0), exercises.toList())

    private suspend fun catalogId(name: String): Long = dao.findCatalogByKey(exerciseNameKey(name))!!.id

    private suspend fun templates(): List<ExerciseEntity> =
        dao.getAllWorkoutsWithExercises().first().flatMap { w -> w.exercises.map { it.exercise } }

    private suspend fun load(): Pair<Double, Double> = templates().single().let { it.weight to it.extraWeight }

    private suspend fun finish(workoutId: Long, finishedAt: Long, vararg sets: SessionSetDraft): Long =
        dao.finishSession(
            WorkoutSessionEntity(workoutId = workoutId, startedAt = finishedAt - 1, finishedAt = finishedAt, durationMillis = 1),
            sets.toList()
        )

    private fun kg(catalogId: Long, name: String, weight: Double, reps: Int) =
        SessionSetDraft(catalogId, name, weight, 0.0, reps, "KG")

    @Test
    fun переименование_меняет_запись_и_названия_упражнений_во_всех_тренировках() = runBlocking {
        saveWorkout("Ноги А", exercise("Присед"))
        saveWorkout("Ноги Б", exercise("присед"))
        val id = catalogId("Присед")

        assertEquals(RenameResult.RENAMED, dao.renameCatalog(id, "  Присед  со штангой "))

        val entry = dao.findCatalogById(id)!!
        assertEquals("Присед со штангой", entry.name)
        assertEquals("присед со штангой", entry.nameKey)
        assertEquals(listOf("Присед со штангой", "Присед со штангой"), templates().map { it.name })
        // Поиск тренировок читает exercises.name — новое название находится.
        assertEquals(2, dao.searchWorkouts("штангой").first().size)
    }

    @Test
    fun переименование_в_занятое_название_отклоняется_и_ничего_не_меняет() = runBlocking {
        saveWorkout("Ноги", exercise("Присед"), exercise("Жим ногами", order = 1))
        val pressId = catalogId("Жим ногами")

        assertEquals(RenameResult.NAME_TAKEN, dao.renameCatalog(pressId, "ПРИСЕД "))

        assertEquals("Жим ногами", dao.findCatalogById(pressId)!!.name)
        assertEquals(listOf("Присед", "Жим ногами"), templates().sortedBy { it.orderInWorkout }.map { it.name })
    }

    @Test
    fun смена_только_регистра_не_считается_занятым_названием() = runBlocking {
        saveWorkout("Ноги", exercise("присед"))
        val id = catalogId("присед")

        assertEquals(RenameResult.RENAMED, dao.renameCatalog(id, "Присед"))

        assertEquals("Присед", dao.findCatalogById(id)!!.name)
        assertEquals(listOf("Присед"), templates().map { it.name })
    }

    @Test
    fun пустое_название_отклоняется() = runBlocking {
        saveWorkout("Ноги", exercise("Присед"))
        val id = catalogId("Присед")

        assertEquals(RenameResult.BLANK, dao.renameCatalog(id, " \u00A0 "))

        assertEquals("Присед", dao.findCatalogById(id)!!.name)
    }

    @Test
    fun смена_единицы_приводит_шаблоны_и_не_трогает_прошлые_подходы() = runBlocking {
        val workoutId = saveWorkout("Спина", exercise("Тяга блока", weight = 45.0))
        val id = catalogId("Тяга блока")
        val sessionId = finish(workoutId, 100, kg(id, "Тяга блока", 45.0, 8))

        dao.changeCatalogUnit(id, ExerciseUnit.PLATE)
        assertEquals("PLATE", dao.findCatalogById(id)!!.unit)
        assertEquals(30.0 to 0.0, load())

        dao.changeCatalogUnit(id, ExerciseUnit.KG)
        assertEquals(30.0 to 0.0, load())

        dao.changeCatalogUnit(id, ExerciseUnit.BODYWEIGHT)
        assertEquals(0.0 to 0.0, load())

        val set = dao.getSessionSets(sessionId).single()
        assertEquals("KG", set.unit)
        assertEquals(45.0, set.weight, 0.0)
    }

    @Test
    fun повторный_выбор_той_же_единицы_не_обнуляет_добавку() = runBlocking {
        dao.findOrCreateCatalog("Тяга блока", ExerciseUnit.PLATE)
        saveWorkout("Спина", exercise("Тяга блока", weight = 5.0, extraWeight = 2.5))
        val id = catalogId("Тяга блока")

        dao.changeCatalogUnit(id, ExerciseUnit.PLATE)

        assertEquals(5.0 to 2.5, load())
    }

    @Test
    fun подходы_тренировки_идут_в_порядке_записи_с_текущим_названием() = runBlocking {
        val legs = saveWorkout("Ноги", exercise("Присед"), exercise("Выпады", order = 1))
        val back = saveWorkout("Спина", exercise("Тяга"))
        val squat = catalogId("Присед")
        val lunge = catalogId("Выпады")
        val session = finish(
            legs, 100,
            kg(squat, "Присед", 60.0, 8), kg(lunge, "Выпады", 20.0, 10), kg(squat, "Присед", 62.5, 6)
        )
        finish(back, 150, kg(catalogId("Тяга"), "Тяга", 50.0, 10))
        dao.renameCatalog(squat, "Присед со штангой")

        val rows = dao.observeSetsForWorkout(legs).first()

        assertEquals(listOf("Присед со штангой", "Выпады", "Присед со штангой"), rows.map { it.exerciseName })
        assertEquals(listOf(8, 10, 6), rows.map { it.reps })
        assertEquals(setOf(session), rows.map { it.sessionId }.toSet())
        assertEquals(100L, rows.first().finishedAt)
    }

    @Test
    fun последняя_сессия_упражнения_выбирается_по_времени_завершения() = runBlocking {
        val legs = saveWorkout("Ноги", exercise("Присед"), exercise("Выпады", order = 1))
        val squat = catalogId("Присед")
        val lunge = catalogId("Выпады")
        val late = finish(legs, 200, kg(squat, "Присед", 60.0, 8), kg(squat, "Присед", 65.0, 5))
        // Записана позже, но закончилась раньше — для «последнего подхода» не годится.
        finish(legs, 100, kg(squat, "Присед", 80.0, 3), kg(lunge, "Выпады", 20.0, 10))

        val rows = dao.observeLastSessionSets().first()

        assertEquals(listOf(60.0, 65.0), rows.filter { it.catalogId == squat }.map { it.weight })
        assertEquals(setOf(late), rows.filter { it.catalogId == squat }.map { it.sessionId }.toSet())
        assertEquals(listOf(20.0), rows.filter { it.catalogId == lunge }.map { it.weight })
    }
}
```

- [ ] **Step 2: Падающие JVM-тесты репозитория и use case'ов**

`ExerciseCatalogRepositoryImplTest.kt`:

```kotlin
package ru.hopes.workouttimer.data

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.data.dao.LoggedSetRow
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet

class ExerciseCatalogRepositoryImplTest {

    @Test
    fun `справочник отдаётся доменными записями, неизвестная единица — кг`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeCatalog() } returns flowOf(
            listOf(
                ExerciseCatalogEntity(id = 1, name = "Тяга блока", nameKey = "тяга блока", unit = "PLATE"),
                ExerciseCatalogEntity(id = 2, name = "Присед", nameKey = "присед", unit = "???")
            )
        )

        val catalog = ExerciseCatalogRepositoryImpl(dao).observeCatalog().first()

        assertEquals(
            listOf(CatalogExercise(1, "Тяга блока", ExerciseUnit.PLATE), CatalogExercise(2, "Присед", ExerciseUnit.KG)),
            catalog
        )
    }

    @Test
    fun `подходы тренировки отдаются с названием и временем сессии`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeSetsForWorkout(3L) } returns flowOf(
            listOf(LoggedSetRow(5, 9, 1, "Тяга блока", 5.0, 2.0, 12, "PLATE", 7_000L))
        )

        val sets = ExerciseCatalogRepositoryImpl(dao).observeSetsForWorkout(3).first()

        assertEquals(
            listOf(LoggedSet(SessionSet(5, 9, 1, 5.0, 2.0, 12, ExerciseUnit.PLATE), "Тяга блока", 7_000L)),
            sets
        )
    }
}
```

`CatalogUseCasesTest.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository

class CatalogUseCasesTest {

    private fun logged(id: Long, sessionId: Long, catalogId: Long, name: String, weight: Double, reps: Int, finishedAt: Long = 1_000L) =
        LoggedSet(SessionSet(id, sessionId, catalogId, weight, 0.0, reps, ExerciseUnit.KG), name, finishedAt)

    @Test
    fun `сводка соединяет справочник с лучшим подходом последней сессии`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeCatalog() } returns flowOf(
            listOf(CatalogExercise(1L, "Присед", ExerciseUnit.KG), CatalogExercise(2L, "Жим", ExerciseUnit.KG))
        )
        every { repo.observeLastSessionSets() } returns flowOf(
            listOf(logged(1, 5, 1, "Присед", 60.0, 8, 9_000L), logged(2, 5, 1, "Присед", 62.5, 6, 9_000L))
        )

        val result = ObserveCatalogSummariesUseCase(repo)().first()

        assertEquals(listOf("Жим", "Присед"), result.map { it.exercise.name })
        assertNull(result[0].lastBest)
        assertEquals(62.5, result[1].lastBest!!.weight, 0.0)
        assertEquals(9_000L, result[1].lastDoneAt)
    }

    @Test
    fun `подходы тренировки группируются по сессиям, внутри — по упражнениям`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeSetsForWorkout(3) } returns flowOf(
            listOf(
                logged(1, 10, 1, "Присед", 60.0, 8),
                logged(2, 10, 2, "Выпады", 20.0, 10),
                logged(3, 10, 1, "Присед", 62.5, 6),
                logged(4, 11, 1, "Присед", 65.0, 5)
            )
        )

        val bySession = GetWorkoutSessionSetsUseCase(repo)(3).first()

        assertEquals(setOf(10L, 11L), bySession.keys)
        assertEquals(listOf("Присед", "Выпады"), bySession.getValue(10L).map { it.exerciseName })
        assertEquals(listOf(8, 6), bySession.getValue(10L)[0].sets.map { it.reps })
        assertEquals(listOf(5), bySession.getValue(11L).single().sets.map { it.reps })
    }
}
```

- [ ] **Step 3: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseCatalogRepositoryImplTest*' --tests '*CatalogUseCasesTest*'`
Expected: FAIL — `Unresolved reference 'LoggedSetRow'`, `'ExerciseCatalogRepository'`.

- [ ] **Step 4: DAO**

`LoggedSetRow.kt`:

```kotlin
package ru.hopes.workouttimer.data.dao

/** Подход с текущим названием записи справочника и временем завершения сессии. */
data class LoggedSetRow(
    val id: Long,
    val sessionId: Long,
    val catalogId: Long,
    val exerciseName: String,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: String,
    val finishedAt: Long
)
```

`WorkoutDao.kt` — добавить (импорты `ru.hopes.workouttimer.domain.model.RenameResult`, `ru.hopes.workouttimer.domain.model.convertLoadToUnit`):

```kotlin
    // Порядок задаёт Kotlin (по ключу названия): ORDER BY name в SQLite сравнивает байты
    // и поставил бы «Ёлочку» перед «А», а заглавные — перед строчными.
    @Query("SELECT * FROM exercise_catalog")
    fun observeCatalog(): Flow<List<ExerciseCatalogEntity>>

    @Query("UPDATE exercise_catalog SET name = :name, nameKey = :nameKey WHERE id = :id")
    suspend fun updateCatalogName(id: Long, name: String, nameKey: String)

    @Query("UPDATE exercises SET name = :name WHERE catalogId = :catalogId")
    suspend fun updateExerciseNamesOfCatalog(catalogId: Long, name: String)

    @Query("UPDATE exercise_catalog SET unit = :unit WHERE id = :id")
    suspend fun updateCatalogUnit(id: Long, unit: String)

    @Query("SELECT * FROM exercises WHERE catalogId = :catalogId")
    suspend fun getExercisesOfCatalog(catalogId: Long): List<ExerciseEntity>

    @Query("UPDATE exercises SET weight = :weight, extraWeight = :extraWeight WHERE id = :id")
    suspend fun updateExerciseLoad(id: Int, weight: Double, extraWeight: Double)

    // Название — текущее из справочника: после переименования история показывает новое.
    @Query(
        """
        SELECT s.id, s.sessionId, s.catalogId, c.name AS exerciseName, s.weight, s.extraWeight,
               s.reps, s.unit, ws.finishedAt
        FROM session_sets s
        JOIN workout_sessions ws ON ws.id = s.sessionId
        JOIN exercise_catalog c ON c.id = s.catalogId
        WHERE ws.workoutId = :workoutId
        ORDER BY s.id
        """
    )
    fun observeSetsForWorkout(workoutId: Long): Flow<List<LoggedSetRow>>

    // Для каждой записи справочника — её подходы из последней по времени завершения
    // сессии (id — тай-брейк при одинаковом finishedAt).
    @Query(
        """
        SELECT s.id, s.sessionId, s.catalogId, c.name AS exerciseName, s.weight, s.extraWeight,
               s.reps, s.unit, ws.finishedAt
        FROM session_sets s
        JOIN workout_sessions ws ON ws.id = s.sessionId
        JOIN exercise_catalog c ON c.id = s.catalogId
        WHERE s.sessionId = (
            SELECT s2.sessionId FROM session_sets s2
            JOIN workout_sessions w2 ON w2.id = s2.sessionId
            WHERE s2.catalogId = s.catalogId
            ORDER BY w2.finishedAt DESC, w2.id DESC
            LIMIT 1
        )
        ORDER BY s.id
        """
    )
    fun observeLastSessionSets(): Flow<List<LoggedSetRow>>

    /**
     * Переименование записи и всех её упражнений одной транзакцией: поиск тренировок
     * читает exercises.name. Ключ, занятый этой же записью («присед» → «Присед»),
     * занятым не считается.
     */
    @Transaction
    suspend fun renameCatalog(id: Long, rawName: String): RenameResult {
        if (rawName.isBlank()) return RenameResult.BLANK
        val name = normalizedExerciseName(rawName)
        val key = exerciseNameKey(name)
        val holder = findCatalogByKey(key)
        if (holder != null && holder.id != id) return RenameResult.NAME_TAKEN
        updateCatalogName(id, name, key)
        updateExerciseNamesOfCatalog(id, name)
        return RenameResult.RENAMED
    }

    /**
     * Смена единицы: шаблоны приводятся к новой, прошлые подходы сохраняют свою.
     * Повторный выбор той же единицы ничего не делает — иначе convertLoadToUnit
     * обнулил бы добавку к плите.
     */
    @Transaction
    suspend fun changeCatalogUnit(id: Long, unit: ExerciseUnit) {
        val entry = findCatalogById(id) ?: return
        if (entry.unit == unit.name) return
        updateCatalogUnit(id, unit.name)
        for (exercise in getExercisesOfCatalog(id)) {
            val load = convertLoadToUnit(exercise.weight, exercise.extraWeight, unit)
            updateExerciseLoad(exercise.id, load.weight, load.extraWeight)
        }
    }
```

- [ ] **Step 5: Маппер, репозиторий, DI, use case'ы**

`CatalogMapper.kt`:

```kotlin
package ru.hopes.workouttimer.data.mapper

import ru.hopes.workouttimer.data.dao.LoggedSetRow
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.exerciseUnitOf

fun ExerciseCatalogEntity.toDomain(): CatalogExercise =
    CatalogExercise(id = id, name = name, unit = exerciseUnitOf(unit))

fun LoggedSetRow.toDomain(): LoggedSet = LoggedSet(
    set = SessionSet(
        id = id,
        sessionId = sessionId,
        catalogId = catalogId,
        weight = weight,
        extraWeight = extraWeight,
        reps = reps,
        unit = exerciseUnitOf(unit)
    ),
    exerciseName = exerciseName,
    finishedAt = finishedAt
)
```

`ExerciseCatalogRepository.kt`:

```kotlin
package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.RenameResult

/** Справочник упражнений и подходы для показа. Пишет только переименование и единицу. */
interface ExerciseCatalogRepository {
    fun observeCatalog(): Flow<List<CatalogExercise>>

    /** Подходы последней сессии каждой записи справочника, в порядке записи. */
    fun observeLastSessionSets(): Flow<List<LoggedSet>>

    /** Все подходы всех сессий тренировки, в порядке записи. */
    fun observeSetsForWorkout(workoutId: Int): Flow<List<LoggedSet>>

    suspend fun rename(id: Long, name: String): RenameResult

    suspend fun changeUnit(id: Long, unit: ExerciseUnit)
}
```

`ExerciseCatalogRepositoryImpl.kt`:

```kotlin
package ru.hopes.workouttimer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

// Виджет не обновляется: он показывает только число упражнений и длительность.
class ExerciseCatalogRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao
) : ExerciseCatalogRepository {

    override fun observeCatalog(): Flow<List<CatalogExercise>> =
        dao.observeCatalog().map { list -> list.map { it.toDomain() } }

    override fun observeLastSessionSets(): Flow<List<LoggedSet>> =
        dao.observeLastSessionSets().map { rows -> rows.map { it.toDomain() } }

    override fun observeSetsForWorkout(workoutId: Int): Flow<List<LoggedSet>> =
        dao.observeSetsForWorkout(workoutId.toLong()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun rename(id: Long, name: String): RenameResult = dao.renameCatalog(id, name)

    override suspend fun changeUnit(id: Long, unit: ExerciseUnit) = dao.changeCatalogUnit(id, unit)
}
```

`AppModule.kt` — после `provideWorkoutRepository` (импорты `ExerciseCatalogRepositoryImpl`, `ExerciseCatalogRepository`):

```kotlin
    @Provides
    @Singleton
    fun provideExerciseCatalogRepository(dao: WorkoutDao): ExerciseCatalogRepository {
        return ExerciseCatalogRepositoryImpl(dao)
    }
```

`ObserveCatalogUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

class ObserveCatalogUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(): Flow<List<CatalogExercise>> = repo.observeCatalog()
}
```

`ObserveCatalogSummariesUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.summarizeCatalog
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

/** Строки экрана «Упражнения»: справочник по алфавиту с последним лучшим подходом. */
class ObserveCatalogSummariesUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(): Flow<List<CatalogSummary>> =
        combine(repo.observeCatalog(), repo.observeLastSessionSets()) { catalog, sets ->
            summarizeCatalog(catalog, sets)
        }
}
```

`RenameCatalogExerciseUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

class RenameCatalogExerciseUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    suspend operator fun invoke(id: Long, name: String): RenameResult = repo.rename(id, name)
}
```

`ChangeCatalogUnitUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

class ChangeCatalogUnitUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    suspend operator fun invoke(id: Long, unit: ExerciseUnit) = repo.changeUnit(id, unit)
}
```

`GetWorkoutSessionSetsUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.hopes.workouttimer.domain.model.SessionExerciseSets
import ru.hopes.workouttimer.domain.model.groupSetsByExercise
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

/** Подходы сессий тренировки: ключ — id сессии, внутри — упражнения в порядке первого появления. */
class GetWorkoutSessionSetsUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(workoutId: Int): Flow<Map<Long, List<SessionExerciseSets>>> =
        repo.observeSetsForWorkout(workoutId).map { sets ->
            sets.groupBy { it.set.sessionId }.mapValues { (_, inSession) -> groupSetsByExercise(inSession) }
        }
}
```

- [ ] **Step 6: Запустить**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseCatalogRepositoryImplTest*' --tests '*CatalogUseCasesTest*'`
Expected: PASS, 4 теста.

Run: `git status --short app/schemas`
Expected: пусто.

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.data.dao.CatalogDaoTest`
Expected: PASS, 8 тестов.

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

- [ ] **Step 7: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: переименование, смена единицы и подходы справочника в DAO

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Единица и добавка в экспорте и импорте

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/model/export/ExportExercise.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/mapper/ExportMapper.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt` (`importWorkouts`)
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/ExportImportRepositoryImpl.kt:102-116`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/mapper/ExportMapperTest.kt` (новый)
- Test: `app/src/test/java/ru/hopes/workouttimer/data/ExportImportRepositoryImplTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/data/dao/WorkoutDaoTest.kt`

**Interfaces:**
- Consumes: `ExerciseDraft`, `resolveCatalog` с `fitLoadToUnit` (Task 3); `exerciseUnitOf` (Task 1).
- Produces:
  - `ExportExercise(…, val unit: String = "KG", val extraWeight: Double = 0.0)`
  - `suspend fun WorkoutDao.importWorkouts(workouts: List<Pair<WorkoutEntity, List<ExerciseDraft>>>): Int` (было `List<ExerciseEntity>`)
  - Импорт: новая запись справочника получает единицу из файла; у существующей единица своя, шаблон приводится к ней.

- [ ] **Step 1: Падающие JVM-тесты**

`ExportMapperTest.kt`:

```kotlin
package ru.hopes.workouttimer.data.mapper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.export.ExportExercise

class ExportMapperTest {

    @Test
    fun `экспорт пишет единицу и добавку`() {
        val export = Exercise(
            name = "Тяга блока", weight = 5.0, sets = 3, reps = 12, order = 1,
            unit = ExerciseUnit.PLATE, extraWeight = 2.5
        ).toExport()
        assertEquals("PLATE", export.unit)
        assertEquals(2.5, export.extraWeight, 0.0)
    }

    @Test
    fun `старый файл без единицы читается как кг без добавки`() {
        val old = Json { ignoreUnknownKeys = true }.decodeFromString<ExportExercise>(
            """{"name":"Присед","weight":60.0,"sets":3,"reps":8,"restTimeMillis":60000,"order":1,"note":""}"""
        )
        assertEquals("KG", old.unit)
        assertEquals(0.0, old.extraWeight, 0.0)
        assertEquals(ExerciseUnit.KG, old.toDomain().unit)
    }
}
```

`ExportImportRepositoryImplTest.kt` — импорт `ru.hopes.workouttimer.data.dao.ExerciseDraft`, `ru.hopes.workouttimer.domain.model.ExerciseUnit`; в тесте `dropped workout and blank exercises of kept one are both counted as skipped` тип слота и последняя проверка:

```kotlin
        val saved = slot<List<Pair<WorkoutEntity, List<ExerciseDraft>>>>()
```

```kotlin
        assertEquals(listOf("Присед"), saved.captured.single().second.map { it.exercise.name })
```

(импорт `ExerciseEntity` из этого файла станет лишним — удалить). Новые тесты в конец класса:

```kotlin

    private fun exerciseWith(name: String, fields: String) =
        """{"name":"$name","weight":5.0,"sets":3,"reps":12,"restTimeMillis":60000,"order":1,"note":""$fields}"""

    private suspend fun importedDraft(json: String): ExerciseDraft {
        val saved = slot<List<Pair<WorkoutEntity, List<ExerciseDraft>>>>()
        val repo = repository(json)
        coEvery { dao.importWorkouts(capture(saved)) } answers { saved.captured.size }
        assertTrue(repo.importFromJson(uri).success)
        return saved.captured.single().second.single()
    }

    @Test
    fun `старый файл без единицы импортируется в кг`() = runTest {
        val draft = importedDraft(file(workout("Ноги", exercise("Присед", 0))))
        assertEquals(ExerciseUnit.KG, draft.unitIfNew)
        assertEquals(0.0, draft.exercise.extraWeight, 0.0)
    }

    @Test
    fun `единица и добавка из файла доходят до DAO`() = runTest {
        val draft = importedDraft(
            file(workout("Спина", exerciseWith("Тяга блока", ""","unit":"PLATE","extraWeight":2.5""")))
        )
        assertEquals(ExerciseUnit.PLATE, draft.unitIfNew)
        assertEquals(2.5, draft.exercise.extraWeight, 0.0)
    }

    @Test
    fun `неизвестная единица в файле читается как кг`() = runTest {
        val draft = importedDraft(file(workout("Спина", exerciseWith("Тяга", ""","unit":"LBS""""))))
        assertEquals(ExerciseUnit.KG, draft.unitIfNew)
    }
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExportMapperTest*' --tests '*ExportImportRepositoryImplTest*'`
Expected: FAIL — `Unresolved reference 'unit'` у `ExportExercise`.

- [ ] **Step 3: Реализация**

`ExportExercise.kt` — целиком:

```kotlin
package ru.hopes.workouttimer.domain.model.export

import kotlinx.serialization.Serializable

/**
 * Упражнение в файле экспорта. unit и extraWeight появились в E2; у старых
 * файлов их нет, и значения по умолчанию читают такие файлы как кг.
 */
@Serializable
data class ExportExercise(
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeMillis: Long,
    val order: Int,
    val note: String = "",
    val unit: String = "KG",
    val extraWeight: Double = 0.0
)
```

`ExportMapper.kt` — `Exercise.toExport()` и `ExportExercise.toDomain()` целиком (импорт `ru.hopes.workouttimer.domain.model.exerciseUnitOf`):

```kotlin
fun Exercise.toExport(): ExportExercise {
    return ExportExercise(
        name = name,
        weight = weight,
        sets = sets,
        reps = reps,
        restTimeMillis = timeMillis,
        order = order,
        note = note,
        unit = unit.name,
        extraWeight = extraWeight
    )
}
```

```kotlin
fun ExportExercise.toDomain(): Exercise {
    return Exercise(
        id = 0, // ID генерируется при вставке
        name = name,
        weight = weight,
        sets = sets,
        reps = reps,
        timeMillis = restTimeMillis,
        order = order,
        note = note,
        unit = exerciseUnitOf(unit),
        extraWeight = extraWeight
    )
}
```

`WorkoutDao.kt` — `importWorkouts` целиком:

```kotlin
    // Весь файл — одна транзакция: сбой посреди файла не оставляет половину тренировок.
    @Transaction
    suspend fun importWorkouts(workouts: List<Pair<WorkoutEntity, List<ExerciseDraft>>>): Int {
        for ((workout, drafts) in workouts) {
            val workoutId = insertWorkout(workout)
            insertExercises(resolveCatalog(workoutId, drafts))
        }
        cleanupCatalog()
        return workouts.size
    }
```

`ExportImportRepositoryImpl.kt` — построение упражнений в `prepared` (импорты `ru.hopes.workouttimer.data.dao.ExerciseDraft`, `ru.hopes.workouttimer.domain.model.exerciseUnitOf`):

```kotlin
                        exportWorkout.exercises.filter { it.name.isNotBlank() }.map { ex ->
                            ExerciseDraft(
                                exercise = ExerciseEntity(
                                    workoutId = 0,
                                    name = ex.name,
                                    weight = ex.weight,
                                    sets = ex.sets,
                                    reps = ex.reps,
                                    restTimeMillis = ex.restTimeMillis,
                                    orderInWorkout = ex.order,
                                    note = ex.note,
                                    catalogId = 0L, // проставит транзакция DAO
                                    extraWeight = ex.extraWeight
                                ),
                                // Единица из файла нужна только новой записи справочника:
                                // у существующей своя главнее, DAO приведёт шаблон к ней.
                                unitIfNew = exerciseUnitOf(ex.unit)
                            )
                        }
```

`editorDrafts` в `WorkoutDao` остаётся — им пользуются оба пути сохранения редактора.

- [ ] **Step 4: Инструментальные тесты импорта**

`WorkoutDaoTest.kt` — добавить хелпер и поправить два импортных теста из E1:

```kotlin
    private fun draft(name: String, unit: ExerciseUnit = ExerciseUnit.KG, weight: Double = 50.0, extra: Double = 0.0) =
        ExerciseDraft(exercise(name).copy(weight = weight, extraWeight = extra), unit)
```

В `импорт_сводит_одинаковые_ключи_из_одного_файла_в_одну_запись`: `listOf(exercise("Присед"))` → `listOf(draft("Присед"))`, `listOf(exercise("ПРИСЕД "))` → `listOf(draft("ПРИСЕД "))`. В `сбой_посреди_импорта_откатывает_весь_файл`: оба `listOf(broken)` → `listOf(ExerciseDraft(broken, ExerciseUnit.KG))`.

Новые тесты:

```kotlin

    @Test
    fun импорт_создаёт_запись_с_единицей_из_файла() = runBlocking {
        dao.importWorkouts(
            listOf(WorkoutEntity(name = "Спина", lastUseAt = 0) to listOf(draft("Тяга блока", ExerciseUnit.PLATE, weight = 5.0, extra = 2.5)))
        )

        assertEquals(listOf("PLATE"), dao.getCatalog().map { it.unit })
        val saved = savedTemplate()
        assertEquals(5.0, saved.weight, 0.0)
        assertEquals(2.5, saved.extraWeight, 0.0)
    }

    @Test
    fun импорт_не_меняет_единицу_существующей_записи_и_приводит_шаблон_к_ней() = runBlocking {
        dao.findOrCreateCatalog("Присед", ExerciseUnit.KG)

        dao.importWorkouts(
            listOf(WorkoutEntity(name = "Ноги", lastUseAt = 0) to listOf(draft("присед", ExerciseUnit.PLATE, weight = 5.0, extra = 2.5)))
        )

        assertEquals(listOf("KG"), dao.getCatalog().map { it.unit })
        val saved = savedTemplate()
        assertEquals(5.0, saved.weight, 0.0)
        assertEquals(0.0, saved.extraWeight, 0.0)
    }

    @Test
    fun одинаковые_ключи_с_разными_единицами_в_одном_файле_дают_одну_запись() = runBlocking {
        dao.importWorkouts(
            listOf(
                WorkoutEntity(name = "А", lastUseAt = 0) to listOf(draft("Тяга блока", ExerciseUnit.PLATE, weight = 5.0)),
                WorkoutEntity(name = "Б", lastUseAt = 0) to listOf(draft("ТЯГА БЛОКА", ExerciseUnit.KG, weight = 60.0))
            )
        )

        assertEquals(listOf("PLATE"), dao.getCatalog().map { it.unit })
        val weights = dao.getAllWorkoutsWithExercises().first().map { it.exercises.single().exercise.weight }
        assertEquals(listOf(5.0, 30.0), weights.sorted())
    }
```

- [ ] **Step 5: Запустить**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExportMapperTest*' --tests '*ExportImportRepositoryImplTest*'`
Expected: PASS (2 + 5 тестов).

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.data.dao.WorkoutDaoTest`
Expected: PASS, 15 тестов.

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

- [ ] **Step 6: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: единица и добавка к плите в файле экспорта и при импорте

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---
### Task 6: Подсказки и единицы в редакторе тренировки

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/ExerciseSuggestions.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt` (целиком)
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutScreen.kt` (`ExerciseRow`, `ExerciseEditSheet`, `ExerciseEditSheetContent`, превью, места вызова)
- Modify: `app/src/main/res/values/strings.xml` (секция «Создание и редактирование тренировки»)
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModelTest.kt`
- Create: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/creation/FakeExerciseCatalogRepository.kt`
- Modify: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutScreenNavigationTest.kt:29-33`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/creation/ExerciseEditSheetContentTest.kt` (новый)

**Interfaces:**
- Consumes: `ObserveCatalogUseCase`, `ExerciseCatalogRepository` (Task 4); `CatalogExercise`, `ExerciseUnit`, `fitLoadToUnit`, `exerciseNameKey` (Task 1); `setFormat()`, `formatLoad`, `unitName`, `PlateValues`, `PlateExtraValues` (Task 2).
- Produces:
  - `fun exerciseSuggestions(query: String, catalog: List<CatalogExercise>): List<CatalogExercise>` — не больше 5
  - `fun unitForName(name: String, catalog: List<CatalogExercise>): ExerciseUnit`
  - `CreateWorkoutViewModel(addWorkoutUseCase, getWorkoutByIdUseCase, updateWorkoutUseCase, observeCatalogUseCase: ObserveCatalogUseCase)`
  - `ExerciseItem.extraWeight: Double = 0.0`
  - `CreateWorkoutState.catalog: List<CatalogExercise>`, `fun CreateWorkoutState.suggestionsFor(item: ExerciseItem): List<CatalogExercise>`, `fun CreateWorkoutState.unitOf(item: ExerciseItem): ExerciseUnit`
  - `@Composable internal fun ExerciseEditSheetContent(item: ExerciseItem, unit: ExerciseUnit, suggestions: List<CatalogExercise>, onChange: (ExerciseItem) -> Unit, onDelete: () -> Unit, onDone: () -> Unit)`
  - Строки: `create_exercise_summary` (изменена), `create_exercise_summary_no_weight`

- [ ] **Step 1: Падающие тесты ViewModel**

`CreateWorkoutViewModelTest.kt` — импорты `io.mockk.every`, `kotlinx.coroutines.flow.flowOf`, `ru.hopes.workouttimer.domain.model.CatalogExercise`, `ru.hopes.workouttimer.domain.model.Exercise`, `ru.hopes.workouttimer.domain.model.ExerciseUnit`, `ru.hopes.workouttimer.domain.usecase.ObserveCatalogUseCase`. Хелпер `viewModel` заменить:

```kotlin
    private fun catalogOf(entries: List<CatalogExercise>) =
        mockk<ObserveCatalogUseCase>().also { every { it() } returns flowOf(entries) }

    private fun viewModel(
        add: AddWorkoutUseCase = mockk(relaxed = true),
        get: GetWorkoutByIdUseCase = mockk(relaxed = true),
        update: UpdateWorkoutUseCase = mockk(relaxed = true),
        catalog: List<CatalogExercise> = emptyList()
    ) = CreateWorkoutViewModel(add, get, update, catalogOf(catalog))

    private val catalog = listOf(
        CatalogExercise(1, "Присед", ExerciseUnit.KG),
        CatalogExercise(2, "Фронтальный присед", ExerciseUnit.KG),
        CatalogExercise(3, "Присед сумо", ExerciseUnit.PLATE),
        CatalogExercise(4, "Жим лёжа", ExerciseUnit.KG),
        CatalogExercise(5, "Без названия", ExerciseUnit.KG)
    )

    /** Добавляет упражнение с названием [name] и возвращает его из состояния. */
    private fun CreateWorkoutViewModel.exerciseNamed(name: String): ExerciseItem {
        processCommand(CreateWorkoutCommand.AddExercise())
        val item = state.value.exercises.last().copy(name = name)
        processCommand(CreateWorkoutCommand.UpdateExercise(item.id, item))
        return item
    }
```

Новые тесты в конец класса:

```kotlin

    @Test
    fun `подсказки не зависят от регистра и ищут по вхождению, начало — первым`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed("ПРИС")

        assertEquals(
            listOf("Присед", "Присед сумо", "Фронтальный присед"),
            vm.state.value.suggestionsFor(item).map { it.name }
        )
    }

    @Test
    fun `ё и е в запросе не различаются`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed("жим леж")

        assertEquals(listOf("Жим лёжа"), vm.state.value.suggestionsFor(item).map { it.name })
    }

    @Test
    fun `подсказок не больше пяти`() = runTest(dispatcher) {
        val many = (1..7).map { CatalogExercise(it.toLong(), "Тяга $it", ExerciseUnit.KG) }
        val vm = viewModel(catalog = many)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed("тяга")

        assertEquals(listOf("Тяга 1", "Тяга 2", "Тяга 3", "Тяга 4", "Тяга 5"), vm.state.value.suggestionsFor(item).map { it.name })
    }

    @Test
    fun `точное совпадение с записью скрывает подсказки`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        val item = vm.exerciseNamed(" присед ")

        assertEquals(emptyList<CatalogExercise>(), vm.state.value.suggestionsFor(item))
    }

    @Test
    fun `пустое поле не подсказывает даже при записи Без названия`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<CatalogExercise>(), vm.state.value.suggestionsFor(vm.exerciseNamed("")))
        assertEquals(emptyList<CatalogExercise>(), vm.state.value.suggestionsFor(vm.exerciseNamed(" \u00A0 ")))
    }

    @Test
    fun `единица упражнения берётся из справочника по названию`() = runTest(dispatcher) {
        val vm = viewModel(catalog = catalog)
        testScheduler.advanceUntilIdle()

        assertEquals(ExerciseUnit.PLATE, vm.state.value.unitOf(vm.exerciseNamed("присед  СУМО")))
        assertEquals(ExerciseUnit.KG, vm.state.value.unitOf(vm.exerciseNamed("Новое упражнение")))
    }

    @Test
    fun `загрузка тренировки не теряет справочник`() = runTest(dispatcher) {
        val get = mockk<GetWorkoutByIdUseCase>()
        coEvery { get(7) } returns Workout(
            id = 7, name = "Ноги", lastUseAt = 1L,
            exercises = listOf(Exercise(id = 1, name = "Присед сумо", weight = 5.0, sets = 3, reps = 10, order = 1))
        )
        val vm = viewModel(get = get, catalog = catalog)
        testScheduler.advanceUntilIdle()

        vm.loadWorkout(7)
        testScheduler.advanceUntilIdle()

        assertEquals(catalog, vm.state.value.catalog)
        assertEquals(ExerciseUnit.PLATE, vm.state.value.unitOf(vm.state.value.exercises.single()))
        assertFalse(vm.state.value.hasUnsavedChanges)
    }

    @Test
    fun `добавка к плите переживает открытие и сохранение тренировки`() = runTest(dispatcher) {
        val get = mockk<GetWorkoutByIdUseCase>()
        coEvery { get(7) } returns Workout(
            id = 7, name = "Спина", lastUseAt = 1L,
            exercises = listOf(
                Exercise(id = 1, name = "Присед сумо", weight = 5.0, sets = 3, reps = 10, order = 1, extraWeight = 2.5)
            )
        )
        val update = mockk<UpdateWorkoutUseCase>(relaxed = true)
        val vm = viewModel(get = get, update = update, catalog = catalog)
        vm.loadWorkout(7)
        testScheduler.advanceUntilIdle()

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Спина Б"))
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        val saved = slot<Workout>()
        coVerify { update(capture(saved)) }
        assertEquals(2.5, saved.captured.exercises.single().extraWeight, 0.0)
    }
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*CreateWorkoutViewModelTest*'`
Expected: FAIL — компиляция: у `CreateWorkoutViewModel` нет четвёртого параметра, `Unresolved reference 'suggestionsFor'`.

- [ ] **Step 3: Подсказки и ViewModel**

`ExerciseSuggestions.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.creation

import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.exerciseNameKey

private const val MAX_SUGGESTIONS = 5

/**
 * Подсказки под полем названия: до пяти записей, чей ключ содержит ключ ввода;
 * сначала начинающиеся с него, потом по алфавиту. Пустое поле проверяется до
 * ключа: exerciseNameKey("") — это ключ «Без названия», а не пустая строка.
 * Точное совпадение с записью гасит подсказки — выбирать уже нечего.
 */
fun exerciseSuggestions(query: String, catalog: List<CatalogExercise>): List<CatalogExercise> {
    if (query.isBlank()) return emptyList()
    val key = exerciseNameKey(query)
    val keyed = catalog.map { it to exerciseNameKey(it.name) }
    if (keyed.any { it.second == key }) return emptyList()
    return keyed
        .filter { it.second.contains(key) }
        .sortedWith(compareBy<Pair<CatalogExercise, String>>({ !it.second.startsWith(key) }, { it.second }))
        .take(MAX_SUGGESTIONS)
        .map { it.first }
}

/**
 * Единица упражнения в редакторе — единица записи с тем же ключом. Новое название
 * станет записью в кг (так создаёт её сохранение), поэтому и показывается кг.
 */
fun unitForName(name: String, catalog: List<CatalogExercise>): ExerciseUnit {
    if (name.isBlank()) return ExerciseUnit.KG
    val key = exerciseNameKey(name)
    return catalog.firstOrNull { exerciseNameKey(it.name) == key }?.unit ?: ExerciseUnit.KG
}
```

`CreateWorkoutViewModel.kt` — целиком:

```kotlin
package ru.hopes.workouttimer.presentation.screen.creation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveCatalogUseCase
import ru.hopes.workouttimer.domain.usecase.UpdateWorkoutUseCase
import javax.inject.Inject

@HiltViewModel
class CreateWorkoutViewModel @Inject constructor(
    private val addWorkoutUseCase: AddWorkoutUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val updateWorkoutUseCase: UpdateWorkoutUseCase,
    observeCatalogUseCase: ObserveCatalogUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CreateWorkoutState())
    val state = _state.asStateFlow()

    private var editingWorkoutId: Int? = null
    private var editingLastUseAt: Long? = null

    private companion object {
        const val TAG = "CreateWorkoutVM"
    }

    /** Слепок состояния после загрузки — с ним сравнивается текущее при выходе. */
    private var savedSnapshot: CreateWorkoutState = CreateWorkoutState()

    init {
        // Справочник — не правка: hasUnsavedChanges сравнивает только название и упражнения.
        observeCatalogUseCase()
            .onEach { catalog -> _state.update { it.copy(catalog = catalog) } }
            // Без подсказок редактор работает; падать из-за них нельзя.
            .catch { e -> Log.e(TAG, "Справочник упражнений не прочитан", e) }
            .launchIn(viewModelScope)
    }

    fun processCommand(command: CreateWorkoutCommand) {
        when (command) {
            is CreateWorkoutCommand.ChangeWorkoutName -> {
                _state.update { it.copy(workoutName = command.name) }
            }

            is CreateWorkoutCommand.AddExercise -> {
                _state.update { state ->
                    val exercises = state.exercises
                    state.copy(
                        exercises = exercises + ExerciseItem(
                            id = (exercises.maxOfOrNull { it.id } ?: 0) + 1,
                            name = "",
                            weight = 0.0,
                            sets = 4,
                            reps = 12,
                            restTimeSeconds = 120,
                            note = ""
                        )
                    )
                }
            }

            is CreateWorkoutCommand.RemoveExercise -> {
                _state.update {
                    it.copy(exercises = it.exercises.filter { ex -> ex.id != command.id })
                }
            }

            is CreateWorkoutCommand.UpdateExercise -> {
                _state.update {
                    it.copy(
                        exercises = it.exercises.map { ex ->
                            if (ex.id == command.id) command.exercise else ex
                        }
                    )
                }
            }

            is CreateWorkoutCommand.MoveExercise -> {
                _state.update { state ->
                    val items = state.exercises
                    if (command.from !in items.indices || command.to !in items.indices) {
                        return@update state
                    }
                    val reordered = items.toMutableList().apply {
                        add(command.to, removeAt(command.from))
                    }
                    // order здесь не трогаем: он присваивается при сохранении
                    // через mapIndexed, поэтому порядок списка и есть порядок упражнений.
                    state.copy(exercises = reordered)
                }
            }

            is CreateWorkoutCommand.UpdateExerciseNote -> {
                _state.update {
                    it.copy(
                        exercises = it.exercises.map { ex ->
                            if (ex.id == command.id) ex.copy(note = command.note) else ex
                        }
                    )
                }
            }

            CreateWorkoutCommand.Save -> {
                viewModelScope.launch {
                    // Нагрузку к единице записи справочника приводит DAO при сохранении:
                    // здесь единицы нет, связь находится по ключу названия.
                    val validExercises = _state.value.exercises
                        .filter { it.name.isNotBlank() }
                        .mapIndexed { index, ex ->
                            Exercise(
                                id = 0,
                                name = ex.name,
                                weight = ex.weight,
                                sets = ex.sets,
                                reps = ex.reps,
                                timeMillis = ex.restTimeSeconds * 1000L,
                                order = index + 1,
                                note = ex.note,
                                extraWeight = ex.extraWeight
                            )
                        }

                    if (_state.value.workoutName.isNotBlank() && validExercises.isNotEmpty()) {
                        val workout = Workout(
                            id = editingWorkoutId ?: 0,
                            name = _state.value.workoutName,
                            exercises = validExercises,
                            // 0 означает «ещё не делали»: при сортировке очереди по
                            // возрастанию новая тренировка встаёт первой, а не последней.
                            lastUseAt = editingLastUseAt ?: 0L
                        )

                        // Транзакция может упасть (например, гонка за UNIQUE справочника):
                        // редактор остаётся открытым, пользователь видит снекбар.
                        try {
                            if (editingWorkoutId != null) updateWorkoutUseCase(workout) else addWorkoutUseCase(workout)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Не удалось сохранить тренировку", e)
                            _state.update { it.copy(saveFailed = true) }
                            return@launch
                        }
                        _state.update { it.copy(isFinished = true) }
                    }
                }
            }

            CreateWorkoutCommand.DismissSaveError -> {
                _state.update { it.copy(saveFailed = false) }
            }

            CreateWorkoutCommand.Back -> {
                _state.update { it.copy(isFinished = true) }
            }
        }

        if (command !is CreateWorkoutCommand.Save && command !is CreateWorkoutCommand.Back) {
            _state.update { current ->
                val changed = current.workoutName != savedSnapshot.workoutName ||
                        current.exercises != savedSnapshot.exercises
                if (current.hasUnsavedChanges == changed) current else current.copy(hasUnsavedChanges = changed)
            }
        }
    }

    fun loadWorkout(workoutId: Int) {
        viewModelScope.launch {
            val workout = getWorkoutByIdUseCase(workoutId)
            workout?.let { w ->
                editingWorkoutId = w.id
                editingLastUseAt = w.lastUseAt
                // copy, а не новое состояние: справочник мог прийти раньше тренировки,
                // и без него в режиме правки пропали бы подсказки и единицы.
                _state.update {
                    it.copy(
                        workoutName = w.name,
                        exercises = w.exercises.map { ex ->
                            ExerciseItem(
                                id = ex.id,
                                name = ex.name,
                                weight = ex.weight,
                                sets = ex.sets,
                                reps = ex.reps,
                                restTimeSeconds = (ex.timeMillis / 1000).toInt(),
                                note = ex.note,
                                extraWeight = ex.extraWeight
                            )
                        },
                        isFinished = false,
                        hasUnsavedChanges = false,
                        saveFailed = false
                    )
                }
                savedSnapshot = _state.value
            }
        }
    }
}

sealed interface CreateWorkoutCommand {
    data class ChangeWorkoutName(val name: String) : CreateWorkoutCommand
    data class UpdateExercise(val id: Int, val exercise: ExerciseItem) : CreateWorkoutCommand
    data class AddExercise(val dummy: Unit = Unit) : CreateWorkoutCommand
    data class RemoveExercise(val id: Int) : CreateWorkoutCommand
    data class MoveExercise(val from: Int, val to: Int) : CreateWorkoutCommand
    data class UpdateExerciseNote(val id: Int, val note: String) : CreateWorkoutCommand
    data object Save : CreateWorkoutCommand
    data object DismissSaveError : CreateWorkoutCommand
    data object Back : CreateWorkoutCommand
}

/**
 * Упражнение в редакторе. catalogId сюда не протаскивается — связь находится при
 * сохранении по ключу названия. extraWeight протаскивается: без него сохранение
 * обнуляло бы добавку к плите.
 */
data class ExerciseItem(
    val id: Int,
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeSeconds: Int,
    val note: String = "",
    val extraWeight: Double = 0.0
)

data class CreateWorkoutState(
    val workoutName: String = "",
    val exercises: List<ExerciseItem> = emptyList(),
    val isFinished: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val saveFailed: Boolean = false,
    val catalog: List<CatalogExercise> = emptyList()
) {
    val isSaveEnabled: Boolean
        get() = workoutName.isNotBlank() && exercises.any { it.name.isNotBlank() }

    fun suggestionsFor(item: ExerciseItem): List<CatalogExercise> = exerciseSuggestions(item.name, catalog)

    fun unitOf(item: ExerciseItem): ExerciseUnit = unitForName(item.name, catalog)
}
```

- [ ] **Step 4: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*CreateWorkoutViewModelTest*'`
Expected: PASS — 9 прежних и 8 новых.

- [ ] **Step 5: Строки строки упражнения**

`strings.xml`, секция «Создание и редактирование тренировки» — заменить `create_exercise_summary` и добавить вариант без веса:

```xml
    <string name="create_exercise_summary">%1$s · %2$d×%3$d · отдых %4$s</string>
    <string name="create_exercise_summary_no_weight">%1$d×%2$d · отдых %3$s</string>
```

- [ ] **Step 6: Экран редактора**

`CreateWorkoutScreen.kt`. Импорты добавить: `androidx.compose.runtime.key`, `androidx.compose.ui.text.TextRange`, `androidx.compose.ui.text.input.TextFieldValue`, `ru.hopes.workouttimer.domain.model.CatalogExercise`, `ru.hopes.workouttimer.domain.model.ExerciseUnit`, `ru.hopes.workouttimer.domain.model.fitLoadToUnit`, `ru.hopes.workouttimer.presentation.ui.components.PlateExtraValues`, `ru.hopes.workouttimer.presentation.ui.components.PlateValues`, `ru.hopes.workouttimer.presentation.utils.formatLoad`, `ru.hopes.workouttimer.presentation.utils.setFormat`, `ru.hopes.workouttimer.presentation.utils.unitName`.

Место вызова строки (в `itemsIndexed`):

```kotlin
                        ExerciseRow(
                            item = item,
                            unit = state.unitOf(item),
                            onClick = { editingId = item.id },
```

Место вызова листа (в `editingId?.let`):

```kotlin
            ExerciseEditSheet(
                item = item,
                unit = state.unitOf(item),
                suggestions = state.suggestionsFor(item),
                onDismiss = { editingId = null },
```

`ExerciseRow` — параметр `unit: ExerciseUnit` после `item`, второй `Text` целиком:

```kotlin
            // Нагрузка показывается приведённой к единице записи — так её и сохранит DAO.
            val load = fitLoadToUnit(item.weight, item.extraWeight, unit)
            val loadText = formatLoad(unit, load.weight, load.extraWeight, setFormat())
            val rest = formatRest(item.restTimeSeconds)
            Text(
                text = if (loadText != null) {
                    stringResource(R.string.create_exercise_summary, loadText, item.sets, item.reps, rest)
                } else {
                    stringResource(R.string.create_exercise_summary_no_weight, item.sets, item.reps, rest)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
```

`ExerciseEditSheet` целиком:

```kotlin
@Composable
private fun ExerciseEditSheet(
    item: ExerciseItem,
    unit: ExerciseUnit,
    suggestions: List<CatalogExercise>,
    onDismiss: () -> Unit,
    onChange: (ExerciseItem) -> Unit,
    onDelete: () -> Unit
) {
    AppBottomSheet(onDismiss = onDismiss) {
        ExerciseEditSheetContent(
            item = item,
            unit = unit,
            suggestions = suggestions,
            onChange = onChange,
            onDelete = onDelete,
            onDone = onDismiss
        )
    }
}
```

`ExerciseEditSheetContent` целиком (было `private`, стало `internal` — для теста композиции):

```kotlin
// Извлечено из ExerciseEditSheet, чтобы тело листа можно было превьюшить и тестировать
// без ModalBottomSheet — он требует Window.
@Composable
internal fun ExerciseEditSheetContent(
    item: ExerciseItem,
    unit: ExerciseUnit,
    suggestions: List<CatalogExercise>,
    onChange: (ExerciseItem) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    // TextFieldValue, а не String: после выбора подсказки курсор встаёт в конец
    // названия, а не остаётся там, где кончался набранный кусок.
    var nameField by remember(item.id) {
        mutableStateOf(TextFieldValue(item.name, TextRange(item.name.length)))
    }
    // Ключ включает единицу: подсказка с другой единицей меняет набор барабанов,
    // и индексы прежнего набора к новому не относятся.
    val load = fitLoadToUnit(item.weight, item.extraWeight, unit)
    var weightIndex by remember(item.id, unit) {
        mutableIntStateOf(wheelIndexOfNearest(WeightValues, load.weight))
    }
    var plateIndex by remember(item.id, unit) {
        mutableIntStateOf(wheelIndexOfNearest(PlateValues, load.weight))
    }
    var extraIndex by remember(item.id, unit) {
        mutableIntStateOf(wheelIndexOfNearest(PlateExtraValues, load.extraWeight))
    }
    var setsIndex by remember(item.id) {
        mutableIntStateOf(wheelIndexOfNearest(SetsValues.map { it.toDouble() }, item.sets.toDouble()))
    }
    var repsIndex by remember(item.id) {
        mutableIntStateOf(wheelIndexOfNearest(RepsValues.map { it.toDouble() }, item.reps.toDouble()))
    }
    var restIndex by remember(item.id) {
        mutableIntStateOf(
            wheelIndexOfNearest(RestValues.map { it.toDouble() }, item.restTimeSeconds.toDouble())
        )
    }

    Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
        OutlinedTextField(
            value = nameField,
            onValueChange = {
                nameField = it
                if (it.text != item.name) onChange(item.copy(name = it.text))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.create_exercise_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )

        if (suggestions.isNotEmpty()) {
            SuggestionList(
                suggestions = suggestions,
                onPick = { entry ->
                    nameField = TextFieldValue(entry.name, TextRange(entry.name.length))
                    onChange(item.copy(name = entry.name))
                }
            )
        }

        // WheelPicker запоминает позицию при первом показе, поэтому смена единицы
        // пересоздаёт ряд барабанов целиком. Единицу редактор показывает, но не меняет.
        key(unit) {
            WheelRow(modifier = Modifier.padding(vertical = 12.dp)) {
                when (unit) {
                    ExerciseUnit.KG -> WheelPicker(
                        items = WeightValues,
                        selectedIndex = weightIndex,
                        onSelected = {
                            weightIndex = it
                            onChange(item.copy(weight = WeightValues[it]))
                        },
                        label = stringResource(R.string.common_unit_kg),
                        format = { it.toCorrectNum() }
                    )

                    ExerciseUnit.PLATE -> {
                        WheelPicker(
                            items = PlateValues,
                            selectedIndex = plateIndex,
                            onSelected = {
                                plateIndex = it
                                onChange(item.copy(weight = PlateValues[it], extraWeight = PlateExtraValues[extraIndex]))
                            },
                            label = stringResource(R.string.unit_name_plate),
                            format = { it.toCorrectNum() }
                        )
                        WheelPicker(
                            items = PlateExtraValues,
                            selectedIndex = extraIndex,
                            onSelected = {
                                extraIndex = it
                                onChange(item.copy(weight = PlateValues[plateIndex], extraWeight = PlateExtraValues[it]))
                            },
                            label = stringResource(R.string.unit_plate_extra),
                            format = { it.toCorrectNum() }
                        )
                    }

                    // Без веса барабана нагрузки нет: только подходы, повторы, отдых.
                    ExerciseUnit.BODYWEIGHT -> Unit
                }
                WheelPicker(
                    items = SetsValues,
                    selectedIndex = setsIndex,
                    onSelected = {
                        setsIndex = it
                        onChange(item.copy(sets = SetsValues[it]))
                    },
                    label = stringResource(R.string.create_unit_sets),
                    format = { it.toString() }
                )
                WheelPicker(
                    items = RepsValues,
                    selectedIndex = repsIndex,
                    onSelected = {
                        repsIndex = it
                        onChange(item.copy(reps = RepsValues[it]))
                    },
                    label = stringResource(R.string.common_unit_reps),
                    format = { it.toString() }
                )
                WheelPicker(
                    items = RestValues,
                    selectedIndex = restIndex,
                    onSelected = {
                        restIndex = it
                        onChange(item.copy(restTimeSeconds = RestValues[it]))
                    },
                    label = stringResource(R.string.create_unit_rest),
                    format = { formatRest(it) }
                )
            }
        }

        OutlinedTextField(
            value = item.note,
            onValueChange = { onChange(item.copy(note = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.common_note)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small
        )

        PrimaryButton(
            text = stringResource(R.string.common_done),
            onClick = onDone,
            modifier = Modifier.padding(top = 14.dp)
        )
        TextButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(stringResource(R.string.create_delete_exercise), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Подсказки из справочника под полем названия; справа — единица записи. */
@Composable
private fun SuggestionList(
    suggestions: List<CatalogExercise>,
    onPick: (CatalogExercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        suggestions.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = unitName(entry.unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

Превью: в `ExerciseRowPreview` добавить `unit = ExerciseUnit.KG`; в `ExerciseEditSheetContentPreview` — `unit = ExerciseUnit.KG, suggestions = emptyList()`. Добавить превью листа плиты с подсказками:

```kotlin
@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ExerciseEditSheetContentPlatePreview() {
    WorkoutTimerTheme {
        Column(modifier = Modifier.padding(vertical = ScreenPadding)) {
            ExerciseEditSheetContent(
                item = ExerciseItem(
                    id = 2,
                    name = "Тяга",
                    weight = 5.0,
                    sets = 3,
                    reps = 12,
                    restTimeSeconds = 90,
                    extraWeight = 2.0
                ),
                unit = ExerciseUnit.PLATE,
                suggestions = listOf(
                    CatalogExercise(1, "Тяга блока", ExerciseUnit.PLATE),
                    CatalogExercise(2, "Тяга штанги в наклоне", ExerciseUnit.KG)
                ),
                onChange = {},
                onDelete = {},
                onDone = {}
            )
        }
    }
}
```

- [ ] **Step 7: Тесты композиции и навигации**

`FakeExerciseCatalogRepository.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.creation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository

/** Справочник в памяти: тестам редактора нужен только поток записей. */
class FakeExerciseCatalogRepository(
    private val catalog: List<CatalogExercise> = emptyList()
) : ExerciseCatalogRepository {

    override fun observeCatalog(): Flow<List<CatalogExercise>> = flowOf(catalog)

    override fun observeLastSessionSets(): Flow<List<LoggedSet>> = flowOf(emptyList())

    override fun observeSetsForWorkout(workoutId: Int): Flow<List<LoggedSet>> = flowOf(emptyList())

    override suspend fun rename(id: Long, name: String): RenameResult = RenameResult.RENAMED

    override suspend fun changeUnit(id: Long, unit: ExerciseUnit) = Unit
}
```

`CreateWorkoutScreenNavigationTest.kt` — хелпер `viewModel` (импорт `ru.hopes.workouttimer.domain.usecase.ObserveCatalogUseCase`):

```kotlin
    private fun viewModel(repo: FakeWorkoutRepository) = CreateWorkoutViewModel(
        AddWorkoutUseCase(repo),
        GetWorkoutByIdUseCase(repo),
        UpdateWorkoutUseCase(repo),
        ObserveCatalogUseCase(FakeExerciseCatalogRepository())
    )
```

`ExerciseEditSheetContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.creation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ExerciseEditSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val item = ExerciseItem(id = 1, name = "при", weight = 60.0, sets = 4, reps = 8, restTimeSeconds = 120)

    private fun show(
        unit: ExerciseUnit,
        suggestions: List<CatalogExercise> = emptyList(),
        onChange: (ExerciseItem) -> Unit = {}
    ) = composeRule.setContent {
        WorkoutTimerTheme {
            ExerciseEditSheetContent(
                item = item,
                unit = unit,
                suggestions = suggestions,
                onChange = onChange,
                onDelete = {},
                onDone = {}
            )
        }
    }

    @Test
    fun подсказка_подставляет_название_записи() {
        var changed: ExerciseItem? = null
        show(
            unit = ExerciseUnit.KG,
            suggestions = listOf(
                CatalogExercise(1, "Присед", ExerciseUnit.KG),
                CatalogExercise(2, "Присед сумо", ExerciseUnit.PLATE)
            ),
            onChange = { changed = it }
        )

        composeRule.onNodeWithText("Присед сумо").assertIsDisplayed()
        composeRule.onNodeWithText("Присед").performClick()

        assertEquals("Присед", changed?.name)
    }

    @Test
    fun у_плиты_два_барабана_нагрузки() {
        show(unit = ExerciseUnit.PLATE)

        composeRule.onNodeWithText("ПЛИТА").assertIsDisplayed()
        composeRule.onNodeWithText("+КГ").assertIsDisplayed()
        composeRule.onNodeWithText("КГ").assertDoesNotExist()
    }

    @Test
    fun без_веса_нет_барабана_нагрузки() {
        show(unit = ExerciseUnit.BODYWEIGHT)

        composeRule.onNodeWithText("КГ").assertDoesNotExist()
        composeRule.onNodeWithText("ПЛИТА").assertDoesNotExist()
        composeRule.onNodeWithText("ПОВТ").assertIsDisplayed()
    }
}
```

- [ ] **Step 8: Запустить**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=ru.hopes.workouttimer.presentation.screen.creation`
Expected: PASS — `CreateWorkoutScreenNavigationTest` и 3 теста `ExerciseEditSheetContentTest`.

- [ ] **Step 9: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: подсказки из справочника и барабаны по единице в редакторе

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Единицы на экране выполнения

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt:71-72` (`updateExerciseWeightAndReps`)
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt:22`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt:115-119`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt:451-476`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WeightRepsSheet.kt` (целиком)
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt` (лист веса, `ActiveContent`, `RestContent`)
- Modify: `app/src/main/res/values/strings.xml` (секция «Выполнение тренировки»)
- Modify: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/creation/FakeWorkoutRepository.kt:49`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt:195-203`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WeightRepsSheetContentTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/ActiveContentTilesTest.kt` (новый)

**Interfaces:**
- Consumes: `Exercise.unit` из справочника (Task 3); `formatLoad`, `setFormat()`, `PlateValues`, `PlateExtraValues`, строки `unit_name_plate`, `unit_plate_extra`, `unit_plate_with_extra` (Task 2).
- Produces:
  - `suspend fun WorkoutDao.updateExerciseWeightAndReps(id: Int, weight: Double, extraWeight: Double, reps: Int)`
  - `suspend fun WorkoutRepository.updateExerciseWeightAndReps(exerciseId: Int, weight: Double, extraWeight: Double, reps: Int)`
  - `fun WorkoutExecutionViewModel.updateExerciseWeightAndReps(exerciseId: Int, weight: Double, extraWeight: Double, reps: Int)` — обновляет и `Active.extraWeight`
  - `@Composable internal fun WeightRepsSheetContent(exerciseName: String, unit: ExerciseUnit, weight: Double, extraWeight: Double, reps: Int, onApply: (weight: Double, extraWeight: Double, reps: Int) -> Unit)`
  - `@Composable internal fun ActiveContent(...)` (было `private`)
  - Строки: `execution_rest_weight_reps` (изменена), `execution_rest_reps`

- [ ] **Step 1: Падающие тесты ViewModel и репозитория**

В `WorkoutExecutionViewModelTest.kt` новый тест в конец класса (`ExerciseUnit`, `RecordedSet`, `slot` уже импортированы):

```kotlin

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
        val vm = buildViewModel(getWorkout, mockk(relaxed = true), finish)

        vm.loadWorkout(1)
        vm.updateExerciseWeightAndReps(exerciseId = 1, weight = 6.0, extraWeight = 2.0, reps = 10)

        assertEquals(2.0, (vm.uiState.value as WorkoutExecutionState.Active).extraWeight, 0.0)
        vm.onExerciseFinished()
        assertEquals(
            RecordedSet(4L, "Тяга блока", 6.0, 2.0, 10, ExerciseUnit.PLATE),
            setsSlot.captured.single()
        )
    }
```

Прежние вызовы в этом файле перевести на новую сигнатуру:

```bash
F=app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt
sed -i '' -E 's/updateExerciseWeightAndReps\(exerciseId = ([0-9]+), weight = ([0-9.]+), reps = ([0-9]+)\)/updateExerciseWeightAndReps(exerciseId = \1, weight = \2, extraWeight = 0.0, reps = \3)/' "$F"
sed -i '' 's/updateExerciseWeightAndReps(any(), any(), any())/updateExerciseWeightAndReps(any(), any(), any(), any())/' "$F"
grep -n "updateExerciseWeightAndReps" "$F"
```

Expected: все вызовы — с `extraWeight = 0.0` (включая `coVerify` в `updateExerciseWeightAndReps stores the new values`) или с четырьмя `any()`.

`WorkoutRepositoryImplTest.kt` — тест `updateExerciseWeightAndReps доходит до DAO целиком` целиком:

```kotlin
    @Test
    fun `updateExerciseWeightAndReps доходит до DAO целиком`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val repo = WorkoutRepositoryImpl(dao, mockk(relaxed = true))

        repo.updateExerciseWeightAndReps(exerciseId = 3, weight = 5.0, extraWeight = 2.5, reps = 6)

        coVerify(exactly = 1) { dao.updateExerciseWeightAndReps(id = 3, weight = 5.0, extraWeight = 2.5, reps = 6) }
    }
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutExecutionViewModelTest*' --tests '*WorkoutRepositoryImplTest*'`
Expected: FAIL — компиляция: `No parameter with name 'extraWeight' found`.

- [ ] **Step 3: Слой данных и ViewModel**

`WorkoutDao.kt`:

```kotlin
    @Query("UPDATE exercises SET weight = :weight, extraWeight = :extraWeight, reps = :reps WHERE id = :id")
    suspend fun updateExerciseWeightAndReps(id: Int, weight: Double, extraWeight: Double, reps: Int)
```

`WorkoutRepository.kt`:

```kotlin
        suspend fun updateExerciseWeightAndReps(exerciseId: Int, weight: Double, extraWeight: Double, reps: Int)
```

`WorkoutRepositoryImpl.kt`:

```kotlin
    override suspend fun updateExerciseWeightAndReps(exerciseId: Int, weight: Double, extraWeight: Double, reps: Int) {
        withContext(Dispatchers.IO) {
            dao.updateExerciseWeightAndReps(exerciseId, weight, extraWeight, reps)
        }
    }
```

`FakeWorkoutRepository.kt` (androidTest):

```kotlin
    override suspend fun updateExerciseWeightAndReps(exerciseId: Int, weight: Double, extraWeight: Double, reps: Int) = Unit
```

`WorkoutExecutionViewModel.kt` — `updateExerciseWeightAndReps` целиком:

```kotlin
    fun updateExerciseWeightAndReps(exerciseId: Int, weight: Double, extraWeight: Double, reps: Int) {
        registerInteraction()
        val index = exercises.indexOfFirst { it.id == exerciseId }
        if (index == -1) return
        val updatedExercise = exercises[index].copy(weight = weight, extraWeight = extraWeight, reps = reps)
        exercises = exercises.toMutableList().apply { set(index, updatedExercise) }

        // Сначала экран, потом база: «Закончить подход» сразу после правки
        // должен записать новые значения, а не ждать окончания записи.
        // Плитки в Active берут числа из state.weight/extraWeight/reps, а не из
        // state.exercise, поэтому одного обновления упражнения им мало.
        _uiState.update { state ->
            when {
                state is WorkoutExecutionState.Active && state.exercise.id == exerciseId ->
                    state.copy(exercise = updatedExercise, weight = weight, extraWeight = extraWeight, reps = reps)

                state is WorkoutExecutionState.Rest && state.exercise.id == exerciseId ->
                    state.copy(exercise = updatedExercise)

                else -> state
            }
        }
        viewModelScope.launch {
            workoutRepository.updateExerciseWeightAndReps(exerciseId, weight, extraWeight, reps)
        }
    }
```

- [ ] **Step 4: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutExecutionViewModelTest*' --tests '*WorkoutRepositoryImplTest*'`
Expected: PASS.

- [ ] **Step 5: Строки отдыха**

`strings.xml`, секция «Выполнение тренировки» — заменить `execution_rest_weight_reps` и добавить вариант без веса:

```xml
    <string name="execution_rest_weight_reps">%1$s · %2$d повторений</string>
    <string name="execution_rest_reps">%1$d повторений</string>
```

- [ ] **Step 6: Лист веса по единице**

`WeightRepsSheet.kt` — целиком:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.presentation.ui.components.PlateExtraValues
import ru.hopes.workouttimer.presentation.ui.components.PlateValues
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.components.RepsValues
import ru.hopes.workouttimer.presentation.ui.components.WeightValues
import ru.hopes.workouttimer.presentation.ui.components.WheelPicker
import ru.hopes.workouttimer.presentation.ui.components.WheelRow
import ru.hopes.workouttimer.presentation.ui.components.wheelIndexOfNearest
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.toCorrectNum

/**
 * Тело листа правки нагрузки и повторов на ходу. Вынесено из [AppBottomSheet] отдельной
 * функцией по той же причине, что и содержимое других листов: ModalBottomSheet
 * требует Window и не рендерится ни в @Preview, ни в тесте композиции.
 *
 * Барабаны накапливают выбор локально, наружу он уходит только по «Готово» —
 * закрытие листа свайпом должно оставлять нагрузку прежней. Набор барабанов —
 * по единице: кг; плита и добавка; без веса — только повторы.
 */
@Composable
internal fun WeightRepsSheetContent(
    exerciseName: String,
    unit: ExerciseUnit,
    weight: Double,
    extraWeight: Double,
    reps: Int,
    onApply: (weight: Double, extraWeight: Double, reps: Int) -> Unit
) {
    var weightIndex by remember(weight) {
        mutableIntStateOf(wheelIndexOfNearest(WeightValues, weight))
    }
    var plateIndex by remember(weight) {
        mutableIntStateOf(wheelIndexOfNearest(PlateValues, weight))
    }
    var extraIndex by remember(extraWeight) {
        mutableIntStateOf(wheelIndexOfNearest(PlateExtraValues, extraWeight))
    }
    var repsIndex by remember(reps) {
        mutableIntStateOf(wheelIndexOfNearest(RepsValues.map { it.toDouble() }, reps.toDouble()))
    }

    Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        WheelRow(modifier = Modifier.padding(vertical = 12.dp)) {
            when (unit) {
                ExerciseUnit.KG -> WheelPicker(
                    items = WeightValues,
                    selectedIndex = weightIndex,
                    onSelected = { weightIndex = it },
                    label = stringResource(R.string.common_unit_kg),
                    format = { it.toCorrectNum() }
                )

                ExerciseUnit.PLATE -> {
                    WheelPicker(
                        items = PlateValues,
                        selectedIndex = plateIndex,
                        onSelected = { plateIndex = it },
                        label = stringResource(R.string.unit_name_plate),
                        format = { it.toCorrectNum() }
                    )
                    WheelPicker(
                        items = PlateExtraValues,
                        selectedIndex = extraIndex,
                        onSelected = { extraIndex = it },
                        label = stringResource(R.string.unit_plate_extra),
                        format = { it.toCorrectNum() }
                    )
                }

                ExerciseUnit.BODYWEIGHT -> Unit
            }
            WheelPicker(
                items = RepsValues,
                selectedIndex = repsIndex,
                onSelected = { repsIndex = it },
                label = stringResource(R.string.common_unit_reps),
                format = { it.toString() }
            )
        }
        PrimaryButton(
            text = stringResource(R.string.common_done),
            onClick = {
                val newReps = RepsValues[repsIndex]
                when (unit) {
                    ExerciseUnit.KG -> onApply(WeightValues[weightIndex], 0.0, newReps)
                    ExerciseUnit.PLATE -> onApply(PlateValues[plateIndex], PlateExtraValues[extraIndex], newReps)
                    ExerciseUnit.BODYWEIGHT -> onApply(0.0, 0.0, newReps)
                }
            }
        )
    }
}

@Preview(backgroundColor = 0xFF14141A, showBackground = true)
@Composable
private fun WeightRepsSheetContentPreview() {
    WorkoutTimerTheme {
        WeightRepsSheetContent(
            exerciseName = "Жим лёжа",
            unit = ExerciseUnit.KG,
            weight = 80.0,
            extraWeight = 0.0,
            reps = 8,
            onApply = { _, _, _ -> }
        )
    }
}

@Preview(backgroundColor = 0xFF14141A, showBackground = true)
@Composable
private fun WeightRepsSheetContentPlatePreview() {
    WorkoutTimerTheme {
        WeightRepsSheetContent(
            exerciseName = "Тяга блока",
            unit = ExerciseUnit.PLATE,
            weight = 5.0,
            extraWeight = 2.0,
            reps = 12,
            onApply = { _, _, _ -> }
        )
    }
}
```

- [ ] **Step 7: Плитки и строка отдыха**

`WorkoutExecutionScreen.kt` — импорты `ru.hopes.workouttimer.domain.model.ExerciseUnit`, `ru.hopes.workouttimer.presentation.utils.formatLoad`, `ru.hopes.workouttimer.presentation.utils.setFormat`. Лист веса:

```kotlin
    weightSheetExercise?.let { exercise ->
        AppBottomSheet(onDismiss = { weightSheetExercise = null }) {
            WeightRepsSheetContent(
                exerciseName = exercise.name,
                unit = exercise.unit,
                weight = exercise.weight,
                extraWeight = exercise.extraWeight,
                reps = exercise.reps,
                onApply = { weight, extraWeight, reps ->
                    viewModel.updateExerciseWeightAndReps(exercise.id, weight, extraWeight, reps)
                    weightSheetExercise = null
                }
            )
        }
    }
```

`ActiveContent` — `private` → `internal`; ряд плиток целиком:

```kotlin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state.exercise.unit) {
                ExerciseUnit.KG -> StatTile(
                    value = state.weight.toCorrectNum(),
                    unit = stringResource(R.string.common_unit_kg),
                    modifier = Modifier.weight(1f),
                    onClick = { onEditWeightAndReps(state.exercise) }
                )

                ExerciseUnit.PLATE -> StatTile(
                    value = state.weight.toCorrectNum(),
                    unit = if (state.extraWeight > 0.0) {
                        stringResource(R.string.unit_plate_with_extra, state.extraWeight.toCorrectNum())
                    } else {
                        stringResource(R.string.unit_name_plate)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onEditWeightAndReps(state.exercise) }
                )

                // Без веса плитки нагрузки нет: повторы занимают всю ширину.
                ExerciseUnit.BODYWEIGHT -> Unit
            }
            StatTile(
                value = state.reps.toString(),
                unit = stringResource(R.string.common_unit_reps),
                modifier = Modifier.weight(1f),
                onClick = { onEditWeightAndReps(state.exercise) }
            )
        }
```

`RestContent` — строка нагрузки целиком:

```kotlin
        // Отдых — самый удобный момент, чтобы поправить вес на следующий подход,
        // поэтому строка ведёт в тот же лист, что и плитки в Active.
        val load = formatLoad(state.exercise.unit, state.exercise.weight, state.exercise.extraWeight, setFormat())
        Text(
            text = if (load != null) {
                stringResource(R.string.execution_rest_weight_reps, load, state.exercise.reps)
            } else {
                stringResource(R.string.execution_rest_reps, state.exercise.reps)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onEditWeightAndReps(state.exercise) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
```

- [ ] **Step 8: Тесты композиции**

`WeightRepsSheetContentTest.kt` — в двух прежних тестах вызов:

```kotlin
                WeightRepsSheetContent(
                    exerciseName = "Жим лёжа",
                    unit = ExerciseUnit.KG,
                    weight = 80.0,
                    extraWeight = 0.0,
                    reps = 8,
                    onApply = { newWeight, _, newReps -> applied = newWeight to newReps }
                )
```

(во втором — `weight = 78.7`; импорт `ru.hopes.workouttimer.domain.model.ExerciseUnit`). Новые тесты:

```kotlin

    @Test
    fun плита_с_добавкой_без_прокрутки_возвращает_текущие_значения() {
        var applied: Triple<Double, Double, Int>? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Тяга блока",
                    unit = ExerciseUnit.PLATE,
                    weight = 5.0,
                    extraWeight = 2.0,
                    reps = 12,
                    onApply = { w, e, r -> applied = Triple(w, e, r) }
                )
            }
        }

        composeRule.onNodeWithText("ПЛИТА").assertExists()
        composeRule.onNodeWithText("+КГ").assertExists()
        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(Triple(5.0, 2.0, 12), applied)
    }

    @Test
    fun плита_вне_сетки_прилипает_к_ближайшим_значениям() {
        var applied: Triple<Double, Double, Int>? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Тяга блока",
                    unit = ExerciseUnit.PLATE,
                    weight = 4.6,
                    extraWeight = 2.3,
                    reps = 12,
                    onApply = { w, e, r -> applied = Triple(w, e, r) }
                )
            }
        }

        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(Triple(5.0, 2.5, 12), applied)
    }

    @Test
    fun без_веса_только_повторы_и_нулевая_нагрузка() {
        var applied: Triple<Double, Double, Int>? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                WeightRepsSheetContent(
                    exerciseName = "Подтягивания",
                    unit = ExerciseUnit.BODYWEIGHT,
                    weight = 7.0,
                    extraWeight = 0.0,
                    reps = 8,
                    onApply = { w, e, r -> applied = Triple(w, e, r) }
                )
            }
        }

        composeRule.onNodeWithText("КГ").assertDoesNotExist()
        composeRule.onNodeWithText("Готово", ignoreCase = true).performClick()

        assertEquals(Triple(0.0, 0.0, 8), applied)
    }
```

`ActiveContentTilesTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

/** Плитка нагрузки подписана единицей упражнения, а у упражнения без веса её нет. */
@RunWith(AndroidJUnit4::class)
class ActiveContentTilesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(unit: ExerciseUnit, weight: Double, extra: Double = 0.0) {
        val exercise = Exercise(
            id = 1, name = "Тяга блока", weight = weight, sets = 3, reps = 12, order = 1,
            unit = unit, extraWeight = extra
        )
        composeRule.setContent {
            WorkoutTimerTheme {
                ActiveContent(
                    state = WorkoutExecutionState.Active(exercise = exercise, currentSet = 1),
                    onEditNote = {},
                    onEditWeightAndReps = {}
                )
            }
        }
    }

    @Test
    fun плитка_плиты_показывает_номер_и_добавку() {
        show(ExerciseUnit.PLATE, weight = 5.0, extra = 2.0)

        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("ПЛИТА +2 КГ").assertIsDisplayed()
    }

    @Test
    fun плита_без_добавки_подписана_просто_плитой() {
        show(ExerciseUnit.PLATE, weight = 5.0)

        composeRule.onNodeWithText("ПЛИТА").assertIsDisplayed()
    }

    @Test
    fun без_веса_остаётся_только_плитка_повторов() {
        show(ExerciseUnit.BODYWEIGHT, weight = 0.0)

        composeRule.onNodeWithText("КГ").assertDoesNotExist()
        composeRule.onNodeWithText("ПЛИТА").assertDoesNotExist()
        composeRule.onNodeWithText("ПОВТ").assertIsDisplayed()
    }
}
```

- [ ] **Step 9: Запустить**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=ru.hopes.workouttimer.presentation.screen.workoutExecution`
Expected: PASS — 5 тестов `WeightRepsSheetContentTest`, 3 теста `ActiveContentTilesTest`.

- [ ] **Step 10: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: плита с добавкой и упражнения без веса на экране подхода

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---
### Task 8: История — разворот сессии с подходами

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryViewModel.kt` (целиком)
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryScreen.kt` (`HistoryContent`, `SessionCard`, превью)
- Modify: `app/src/main/res/values/strings.xml` (секция «История»)
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryViewModelTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/HistoryContentTest.kt` (новый)

**Interfaces:**
- Consumes: `GetWorkoutSessionSetsUseCase` (Task 4); `SessionExerciseSets`, `SessionSet` (Task 1); `setFormat()`, `formatSetList` (Task 2).
- Produces:
  - `WorkoutHistoryViewModel(getWorkoutSessionsUseCase, getWorkoutByIdUseCase, getWorkoutSessionSetsUseCase: GetWorkoutSessionSetsUseCase)`
  - `WorkoutHistoryState.setsBySession: Map<Long, List<SessionExerciseSets>>` — ключ `WorkoutSession.id.toLong()`
  - `@Composable internal fun HistoryContent(sessions: List<WorkoutSession>, setsBySession: Map<Long, List<SessionExerciseSets>>)`
  - Строки: `history_no_sets`, `history_exercise_sets`, `history_expand`, `history_collapse`

- [ ] **Step 1: Падающий тест ViewModel**

`WorkoutHistoryViewModelTest.kt` — импорты `ru.hopes.workouttimer.domain.model.ExerciseUnit`, `ru.hopes.workouttimer.domain.model.SessionExerciseSets`, `ru.hopes.workouttimer.domain.model.SessionSet`, `ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionSetsUseCase`. В прежнем тесте конструктор:

```kotlin
        val getSets = mockk<GetWorkoutSessionSetsUseCase>()
        every { getSets(3) } returns flowOf(emptyMap())

        val viewModel = WorkoutHistoryViewModel(getWorkoutSessionsUseCase, getWorkoutByIdUseCase, getSets)
```

Новый тест:

```kotlin

    @Test
    fun `loadHistory exposes sets grouped by session`() = runTest {
        val sets = mapOf(
            1L to listOf(
                SessionExerciseSets(
                    catalogId = 7L,
                    exerciseName = "Присед",
                    sets = listOf(SessionSet(1L, 1L, 7L, 60.0, 0.0, 8, ExerciseUnit.KG))
                )
            )
        )
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(3) } returns null
        val getWorkoutSessionsUseCase = mockk<GetWorkoutSessionsUseCase>()
        every { getWorkoutSessionsUseCase(3) } returns flowOf(emptyList())
        val getSets = mockk<GetWorkoutSessionSetsUseCase>()
        every { getSets(3) } returns flowOf(sets)

        val viewModel = WorkoutHistoryViewModel(getWorkoutSessionsUseCase, getWorkoutByIdUseCase, getSets)
        viewModel.loadHistory(3)

        assertEquals(sets, viewModel.state.value.setsBySession)
    }
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutHistoryViewModelTest*'`
Expected: FAIL — компиляция: лишний аргумент конструктора, `Unresolved reference 'setsBySession'`.

- [ ] **Step 3: ViewModel**

`WorkoutHistoryViewModel.kt` — целиком:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutHistory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.domain.model.SessionExerciseSets
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionSetsUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionsUseCase
import javax.inject.Inject

private const val TAG = "WorkoutHistoryVM"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val getWorkoutSessionsUseCase: GetWorkoutSessionsUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val getWorkoutSessionSetsUseCase: GetWorkoutSessionSetsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutHistoryState())
    val state = _state.asStateFlow()

    fun loadHistory(workoutId: Int) {
        viewModelScope.launch {
            val workout = getWorkoutByIdUseCase(workoutId)
            _state.update { it.copy(workoutName = workout?.name ?: "") }
        }

        getWorkoutSessionsUseCase(workoutId)
            .onEach { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
            .launchIn(viewModelScope)

        getWorkoutSessionSetsUseCase(workoutId)
            .onEach { sets -> _state.update { it.copy(setsBySession = sets) } }
            // Без подходов история всё равно показывает сессии — падать из-за них нельзя.
            .catch { e -> Log.e(TAG, "Подходы истории не прочитаны", e) }
            .launchIn(viewModelScope)
    }
}

data class WorkoutHistoryState(
    val workoutName: String = "",
    val sessions: List<WorkoutSession> = emptyList(),
    val setsBySession: Map<Long, List<SessionExerciseSets>> = emptyMap()
)
```

- [ ] **Step 4: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutHistoryViewModelTest*'`
Expected: PASS, 2 теста.

- [ ] **Step 5: Строки и экран**

`strings.xml`, секция «История» — после `history_empty_subtitle`:

```xml
    <string name="history_no_sets">подходы не записывались</string>
    <string name="history_exercise_sets">%1$s — %2$s</string>
    <string name="history_expand">Показать подходы</string>
    <string name="history_collapse">Скрыть подходы</string>
```

`WorkoutHistoryScreen.kt`. Импорты добавить: `androidx.compose.foundation.clickable`, `androidx.compose.material.icons.filled.ExpandLess`, `androidx.compose.material.icons.filled.ExpandMore`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `ru.hopes.workouttimer.domain.model.ExerciseUnit`, `ru.hopes.workouttimer.domain.model.SessionExerciseSets`, `ru.hopes.workouttimer.domain.model.SessionSet`, `ru.hopes.workouttimer.presentation.utils.formatSetList`, `ru.hopes.workouttimer.presentation.utils.setFormat`.

В `WorkoutHistoryScreen` вызов: `HistoryContent(sessions = state.sessions, setsBySession = state.setsBySession)`.

`HistoryContent` и `SessionCard` целиком:

```kotlin
/**
 * Тело экрана под шапкой: пустое состояние либо список сессий карточками.
 * Вынесено из [WorkoutHistoryScreen], чтобы превьюшить и тестировать без `hiltViewModel()`.
 */
@Composable
internal fun HistoryContent(
    sessions: List<WorkoutSession>,
    setsBySession: Map<Long, List<SessionExerciseSets>>
) {
    if (sessions.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = stringResource(R.string.history_empty_title),
            subtitle = stringResource(R.string.history_empty_subtitle)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(CardSpacing)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(session, setsBySession[session.id.toLong()].orEmpty())
            }
        }
    }
}

/**
 * Карточка сессии. С подходами — разворачивается по тапу; сессия до обновления
 * подходов не имеет, не разворачивается и честно это подписывает.
 */
@Composable
private fun SessionCard(session: WorkoutSession, exercises: List<SessionExerciseSets>) {
    val hasSets = exercises.isNotEmpty()
    // rememberSaveable: развёрнутая карточка переживает прокрутку и поворот экрана.
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val format = setFormat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                enabled = hasSets,
                onClickLabel = stringResource(if (expanded) R.string.history_collapse else R.string.history_expand)
            ) { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DateFormatter.formatSessionDateTime(session.finishedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = DateFormatter.formatDurationCompact(session.durationMillis),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (hasSets) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        if (!hasSets) {
            Text(
                text = stringResource(R.string.history_no_sets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Строка упражнения поведёт на его прогресс с E3; пока не нажимается.
                exercises.forEach { group ->
                    Text(
                        text = stringResource(
                            R.string.history_exercise_sets,
                            group.exerciseName,
                            formatSetList(group.sets, format)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
```

Превью: `HistoryContentPopulatedPreview` — вызов с подходами у первой сессии:

```kotlin
        HistoryContent(
            sessions = listOf(
                previewSession(1, now, 2_730_000L),
                previewSession(2, now - dayMillis, 3_125_000L),
                previewSession(3, now - 3 * dayMillis, 4_010_000L)
            ),
            setsBySession = mapOf(
                1L to listOf(
                    SessionExerciseSets(
                        catalogId = 1L,
                        exerciseName = "Присед",
                        sets = listOf(
                            SessionSet(1L, 1L, 1L, 60.0, 0.0, 8, ExerciseUnit.KG),
                            SessionSet(2L, 1L, 1L, 62.5, 0.0, 6, ExerciseUnit.KG)
                        )
                    )
                )
            )
        )
```

`HistoryContentEmptyPreview`: `HistoryContent(sessions = emptyList(), setsBySession = emptyMap())`.

- [ ] **Step 6: Тест композиции**

`HistoryContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutHistory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionExerciseSets
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter

@RunWith(AndroidJUnit4::class)
class HistoryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val session = WorkoutSession(
        id = 1, workoutId = 1, startedAt = 1_759_300_000_000L - 3_000_000L,
        finishedAt = 1_759_300_000_000L, durationMillis = 3_000_000L
    )

    private fun set(id: Long, weight: Double, reps: Int) =
        SessionSet(id, 1L, 7L, weight, 0.0, reps, ExerciseUnit.KG)

    private fun show(setsBySession: Map<Long, List<SessionExerciseSets>>) = composeRule.setContent {
        WorkoutTimerTheme { HistoryContent(sessions = listOf(session), setsBySession = setsBySession) }
    }

    @Test
    fun тап_по_сессии_показывает_подходы_по_упражнениям() {
        show(
            mapOf(
                1L to listOf(
                    SessionExerciseSets(7L, "Присед", listOf(set(1, 60.0, 8), set(2, 60.0, 8), set(3, 62.5, 6))),
                    SessionExerciseSets(9L, "Выпады", listOf(set(4, 20.0, 10)))
                )
            )
        )
        val squat = "Присед — 60 кг × 8 · 60 × 8 · 62.5 × 6"
        composeRule.onNodeWithText(squat).assertDoesNotExist()

        composeRule.onNodeWithText(DateFormatter.formatSessionDateTime(session.finishedAt)).performClick()

        composeRule.onNodeWithText(squat).assertIsDisplayed()
        composeRule.onNodeWithText("Выпады — 20 кг × 10").assertIsDisplayed()
    }

    @Test
    fun сессия_без_подходов_подписана_и_не_разворачивается() {
        show(emptyMap())

        composeRule.onNodeWithText("подходы не записывались").assertIsDisplayed()
        // clickable(enabled = false) оставляет узел неактивным: тап ничего не раскроет.
        composeRule.onNodeWithText("подходы не записывались").assertIsNotEnabled()
    }
}
```

- [ ] **Step 7: Запустить**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.workoutHistory.HistoryContentTest`
Expected: PASS, 2 теста.

- [ ] **Step 8: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: подходы сессии в истории тренировки

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Экран «Упражнения»

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/utils/DaysAgo.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/exercises/ExerciseCatalogViewModel.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/exercises/ExerciseCatalogScreen.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt` (параметр `onExercisesClick`, иконка в шапке, `subtitleFor` → `daysAgoText`)
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml` (новая секция «Справочник упражнений»)
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/exercises/ExerciseCatalogViewModelTest.kt` (новый)
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/navigation/ScreenDeepLinkTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/exercises/ExerciseCatalogContentTest.kt` (новый)

**Interfaces:**
- Consumes: `ObserveCatalogSummariesUseCase`, `RenameCatalogExerciseUseCase`, `ChangeCatalogUnitUseCase` (Task 4); `CatalogSummary`, `CatalogExercise`, `RenameResult`, `ExerciseUnit`, `exerciseNameKey` (Task 1); `setFormat()`, `formatSet`, `unitName` (Task 2).
- Produces:
  - `@Composable fun daysAgoText(timestamp: Long): String` (бывший приватный `subtitleFor` из `ListWorkoutScreen.kt`)
  - `ExerciseCatalogViewModel`: `state: StateFlow<ExerciseCatalogState>`, `updateQuery(String)`, `startRename(CatalogExercise)`, `cancelRename()`, `clearRenameTaken()`, `confirmRename(String)`, `changeUnit(CatalogExercise, ExerciseUnit)`, `dismissError()`
  - `data class ExerciseCatalogState(query, rows: List<CatalogSummary>, isLoaded, isCatalogEmpty, renameTarget: CatalogExercise?, renameTaken, @StringRes errorMessage: Int?)`
  - `internal fun filterCatalog(all: List<CatalogSummary>, query: String): List<CatalogSummary>`
  - `@Composable fun ExerciseCatalogScreen(viewModel: ExerciseCatalogViewModel = hiltViewModel(), onNavigateBack: () -> Unit)`
  - `@Composable internal fun CatalogContent(state: ExerciseCatalogState, onMenu: (CatalogExercise) -> Unit)`, `internal fun RenameDialog(...)`, `internal fun UnitDialog(...)`
  - `Screen.Exercises` с маршрутом `"exercises"`; `ListWorkoutScreen(…, onExercisesClick: () -> Unit = {})`
  - Строки `catalog_*`

- [ ] **Step 1: Строки**

`strings.xml` — новая секция после «История»:

```xml

    <!-- Справочник упражнений -->
    <string name="catalog_title">Упражнения</string>
    <string name="catalog_search_placeholder">Название упражнения</string>
    <string name="catalog_actions">Действия с упражнением</string>
    <string name="catalog_last_set">%1$s · %2$s</string>
    <string name="catalog_empty_title">Упражнений пока нет</string>
    <string name="catalog_empty_subtitle">Они появятся, когда вы сохраните тренировку</string>
    <string name="catalog_search_empty_title">Ничего не найдено</string>
    <string name="catalog_search_empty_subtitle">Попробуйте другой запрос</string>
    <string name="catalog_action_rename">Переименовать</string>
    <string name="catalog_action_unit">Единица</string>
    <string name="catalog_rename_title">Переименовать упражнение</string>
    <string name="catalog_rename_taken">Такое упражнение уже есть</string>
    <string name="catalog_unit_title">Единица нагрузки</string>
    <string name="catalog_unit_hint">Вес в тренировках приведётся к новой единице. Прошлые подходы не изменятся.</string>
    <string name="catalog_error_load">Не удалось загрузить упражнения</string>
    <string name="catalog_error_rename">Не удалось переименовать упражнение</string>
    <string name="catalog_error_unit">Не удалось сменить единицу</string>
```

- [ ] **Step 2: Падающие JVM-тесты**

`ExerciseCatalogViewModelTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.exercises

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.usecase.ChangeCatalogUnitUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveCatalogSummariesUseCase
import ru.hopes.workouttimer.domain.usecase.RenameCatalogExerciseUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseCatalogViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val squat = CatalogExercise(1L, "Присед", ExerciseUnit.KG)
    private val bench = CatalogExercise(2L, "Жим лёжа", ExerciseUnit.KG)
    private val untitled = CatalogExercise(3L, "Без названия", ExerciseUnit.KG)

    private fun viewModel(
        entries: List<CatalogExercise> = listOf(untitled, bench, squat),
        rename: RenameCatalogExerciseUseCase = mockk(relaxed = true),
        changeUnit: ChangeCatalogUnitUseCase = mockk(relaxed = true)
    ): ExerciseCatalogViewModel {
        val summaries = mockk<ObserveCatalogSummariesUseCase>()
        every { summaries() } returns flowOf(entries.map { CatalogSummary(it, lastBest = null, lastDoneAt = null) })
        return ExerciseCatalogViewModel(summaries, rename, changeUnit)
    }

    @Test
    fun `пустой запрос показывает весь справочник, а не одну запись Без названия`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.updateQuery(" \u00A0 ")
        testScheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.rows.size)
        assertEquals(" \u00A0 ", vm.state.value.query)
    }

    @Test
    fun `поиск не зависит от регистра и ё`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.updateQuery("ЛЕЖА")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(bench), vm.state.value.rows.map { it.exercise })
    }

    @Test
    fun `пустой справочник отличается от пустого результата поиска`() = runTest(dispatcher) {
        val empty = viewModel(entries = emptyList())
        testScheduler.advanceUntilIdle()
        assertTrue(empty.state.value.isLoaded)
        assertTrue(empty.state.value.isCatalogEmpty)

        val searched = viewModel()
        searched.updateQuery("становая")
        testScheduler.advanceUntilIdle()
        assertFalse(searched.state.value.isCatalogEmpty)
        assertEquals(emptyList<CatalogSummary>(), searched.state.value.rows)
    }

    @Test
    fun `занятое название оставляет диалог открытым с ошибкой`() = runTest(dispatcher) {
        val rename = mockk<RenameCatalogExerciseUseCase>()
        coEvery { rename(1L, "жим лёжа") } returns RenameResult.NAME_TAKEN
        val vm = viewModel(rename = rename)

        vm.startRename(squat)
        vm.confirmRename("жим лёжа")
        testScheduler.advanceUntilIdle()

        assertEquals(squat, vm.state.value.renameTarget)
        assertTrue(vm.state.value.renameTaken)

        vm.clearRenameTaken()
        assertFalse(vm.state.value.renameTaken)
    }

    @Test
    fun `успешное переименование закрывает диалог`() = runTest(dispatcher) {
        val rename = mockk<RenameCatalogExerciseUseCase>()
        coEvery { rename(1L, "Присед со штангой") } returns RenameResult.RENAMED
        val vm = viewModel(rename = rename)

        vm.startRename(squat)
        vm.confirmRename("Присед со штангой")
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.renameTarget)
        coVerify(exactly = 1) { rename(1L, "Присед со штангой") }
    }

    @Test
    fun `сбой переименования — снекбар и закрытый диалог`() = runTest(dispatcher) {
        val rename = mockk<RenameCatalogExerciseUseCase>()
        coEvery { rename(any(), any()) } throws IllegalStateException("db")
        val vm = viewModel(rename = rename)

        vm.startRename(squat)
        vm.confirmRename("Присед со штангой")
        testScheduler.advanceUntilIdle()

        assertEquals(R.string.catalog_error_rename, vm.state.value.errorMessage)
        assertNull(vm.state.value.renameTarget)

        vm.dismissError()
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `сбой смены единицы — снекбар`() = runTest(dispatcher) {
        val changeUnit = mockk<ChangeCatalogUnitUseCase>()
        coEvery { changeUnit(any(), any()) } throws IllegalStateException("db")
        val vm = viewModel(changeUnit = changeUnit)

        vm.changeUnit(squat, ExerciseUnit.PLATE)
        testScheduler.advanceUntilIdle()

        assertEquals(R.string.catalog_error_unit, vm.state.value.errorMessage)
    }

    @Test
    fun `выбор текущей единицы ничего не пишет`() = runTest(dispatcher) {
        val changeUnit = mockk<ChangeCatalogUnitUseCase>(relaxed = true)
        val vm = viewModel(changeUnit = changeUnit)

        vm.changeUnit(squat, ExerciseUnit.KG)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { changeUnit(any(), any()) }
    }
}
```

`ScreenDeepLinkTest.kt` — тест в конец класса:

```kotlin

    // Маршрут из спеки; E3 добавит рядом exercise_progress/{catalogId}.
    @Test
    fun `exercises screen has its own route`() {
        assertEquals("exercises", Screen.Exercises.route)
    }
```

- [ ] **Step 3: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseCatalogViewModelTest*' --tests '*ScreenDeepLinkTest*'`
Expected: FAIL — `Unresolved reference 'ExerciseCatalogViewModel'`, `'Exercises'`.

- [ ] **Step 4: ViewModel**

`ExerciseCatalogViewModel.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.exercises

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.model.exerciseNameKey
import ru.hopes.workouttimer.domain.usecase.ChangeCatalogUnitUseCase
import ru.hopes.workouttimer.domain.usecase.ObserveCatalogSummariesUseCase
import ru.hopes.workouttimer.domain.usecase.RenameCatalogExerciseUseCase
import javax.inject.Inject

private const val TAG = "ExerciseCatalogVM"

@HiltViewModel
class ExerciseCatalogViewModel @Inject constructor(
    observeCatalogSummariesUseCase: ObserveCatalogSummariesUseCase,
    private val renameCatalogExerciseUseCase: RenameCatalogExerciseUseCase,
    private val changeCatalogUnitUseCase: ChangeCatalogUnitUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(ExerciseCatalogState())
    val state = _state.asStateFlow()

    init {
        combine(observeCatalogSummariesUseCase(), query) { all, input ->
            _state.update {
                it.copy(
                    rows = filterCatalog(all, input),
                    isLoaded = true,
                    isCatalogEmpty = all.isEmpty()
                )
            }
        }
            .catch { e ->
                Log.e(TAG, "Справочник не прочитан", e)
                _state.update { it.copy(errorMessage = R.string.catalog_error_load) }
            }
            .launchIn(viewModelScope)
    }

    fun updateQuery(input: String) {
        // Сырой ввод, как в поиске тренировок: обрезка съела бы пробел между словами.
        _state.update { it.copy(query = input) }
        query.value = input
    }

    fun startRename(target: CatalogExercise) {
        _state.update { it.copy(renameTarget = target, renameTaken = false) }
    }

    fun cancelRename() {
        _state.update { it.copy(renameTarget = null, renameTaken = false) }
    }

    /** Пользователь правит название — прежняя ошибка «уже есть» больше не про него. */
    fun clearRenameTaken() {
        if (_state.value.renameTaken) _state.update { it.copy(renameTaken = false) }
    }

    fun confirmRename(newName: String) {
        val target = _state.value.renameTarget ?: return
        launchGuarded(R.string.catalog_error_rename) {
            when (renameCatalogExerciseUseCase(target.id, newName)) {
                RenameResult.RENAMED -> _state.update { it.copy(renameTarget = null, renameTaken = false) }
                RenameResult.NAME_TAKEN -> _state.update { it.copy(renameTaken = true) }
                // Кнопка «Сохранить» неактивна при пустом поле; диалог просто остаётся открытым.
                RenameResult.BLANK -> Unit
            }
        }
    }

    fun changeUnit(target: CatalogExercise, unit: ExerciseUnit) {
        // Та же единица — не изменение: convertLoadToUnit обнулил бы добавку к плите.
        if (target.unit == unit) return
        launchGuarded(R.string.catalog_error_unit) {
            changeCatalogUnitUseCase(target.id, unit)
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Сбой записи — снекбар, а не падение; транзакция DAO откатилась целиком. */
    private fun launchGuarded(@StringRes message: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Запись справочника не удалась", e)
                _state.update { it.copy(errorMessage = message, renameTarget = null, renameTaken = false) }
            }
        }
    }
}

/**
 * Поиск по справочнику — тем же ключом, что и подсказки редактора. Пустой запрос
 * проверяется до ключа: exerciseNameKey("") — это ключ «Без названия».
 */
internal fun filterCatalog(all: List<CatalogSummary>, query: String): List<CatalogSummary> {
    if (query.isBlank()) return all
    val key = exerciseNameKey(query)
    return all.filter { exerciseNameKey(it.exercise.name).contains(key) }
}

data class ExerciseCatalogState(
    val query: String = "",
    val rows: List<CatalogSummary> = emptyList(),
    val isLoaded: Boolean = false,
    val isCatalogEmpty: Boolean = false,
    val renameTarget: CatalogExercise? = null,
    val renameTaken: Boolean = false,
    @StringRes val errorMessage: Int? = null
)
```

`NavGraph.kt` — в `Screen`:

```kotlin
    data object Exercises : Screen("exercises")
```

- [ ] **Step 5: Запустить JVM-тесты — проходят**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseCatalogViewModelTest*' --tests '*ScreenDeepLinkTest*'`
Expected: PASS — 8 + 2 теста.

- [ ] **Step 6: Давность — общий помощник**

`DaysAgo.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R

/**
 * Давность по полным суткам от «сейчас»: «сегодня», «вчера», «N дн. назад».
 * 0 — «ещё не делали»: так lastUseAt помечает новую тренировку.
 * Общая для списка тренировок и экрана «Упражнения».
 */
@Composable
fun daysAgoText(timestamp: Long): String {
    if (timestamp == 0L) return stringResource(R.string.common_never_done)
    val days = ((System.currentTimeMillis() - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> stringResource(R.string.list_today)
        days == 1 -> stringResource(R.string.list_yesterday)
        else -> stringResource(R.string.d_ago, days)
    }
}
```

`ListWorkoutScreen.kt`: удалить приватную функцию `subtitleFor` вместе с её KDoc и заменить три вызова `subtitleFor(` на `daysAgoText(` (импорт `ru.hopes.workouttimer.presentation.utils.daysAgoText`). Проверка: `grep -n "subtitleFor" app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt` — пусто.

Там же — параметр после `onHistoryClick`:

```kotlin
    onHistoryClick: (WorkoutEntity) -> Unit = {},
    onExercisesClick: () -> Unit = {}
```

и иконка в шапке между поиском и экспортом:

```kotlin
                IconButton(onClick = onExercisesClick) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = stringResource(R.string.catalog_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
```

- [ ] **Step 7: Экран**

`ExerciseCatalogScreen.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.components.ActionSheet
import ru.hopes.workouttimer.presentation.ui.components.ActionSheetItem
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.SectionSpacing
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.daysAgoText
import ru.hopes.workouttimer.presentation.utils.formatSet
import ru.hopes.workouttimer.presentation.utils.setFormat
import ru.hopes.workouttimer.presentation.utils.unitName

@Composable
fun ExerciseCatalogScreen(
    viewModel: ExerciseCatalogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var menuFor by remember { mutableStateOf<CatalogExercise?>(null) }
    var unitFor by remember { mutableStateOf<CatalogExercise?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // Сбой записи или чтения — снекбар, как в списке тренировок.
    LaunchedEffect(state.errorMessage) {
        val messageRes = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(messageRes))
        viewModel.dismissError()
    }

    menuFor?.let { target ->
        ActionSheet(
            title = target.name,
            subtitle = null,
            items = listOf(
                ActionSheetItem(stringResource(R.string.catalog_action_rename), Icons.Default.Edit, {
                    menuFor = null
                    viewModel.startRename(target)
                }),
                ActionSheetItem(stringResource(R.string.catalog_action_unit), Icons.Default.Straighten, {
                    menuFor = null
                    unitFor = target
                }, subtitle = unitName(target.unit))
            ),
            onDismiss = { menuFor = null }
        )
    }

    unitFor?.let { target ->
        UnitDialog(
            current = target.unit,
            onSelect = { unit ->
                unitFor = null
                viewModel.changeUnit(target, unit)
            },
            onDismiss = { unitFor = null }
        )
    }

    state.renameTarget?.let { target ->
        RenameDialog(
            initialName = target.name,
            nameTaken = state.renameTaken,
            onNameEdited = viewModel::clearRenameTaken,
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::cancelRename
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
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
                    text = stringResource(R.string.catalog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                placeholder = { Text(stringResource(R.string.catalog_search_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            CatalogContent(state = state, onMenu = { menuFor = it })
        }
    }
}

/**
 * Список справочника под поиском. Вынесен из [ExerciseCatalogScreen], чтобы
 * превьюшить и тестировать без `hiltViewModel()`.
 */
@Composable
internal fun CatalogContent(
    state: ExerciseCatalogState,
    onMenu: (CatalogExercise) -> Unit
) {
    // До первого ответа базы пустое состояние не показываем — оно мигнуло бы.
    if (!state.isLoaded) return
    if (state.rows.isEmpty()) {
        EmptyState(
            icon = Icons.Default.FitnessCenter,
            title = stringResource(
                if (state.isCatalogEmpty) R.string.catalog_empty_title else R.string.catalog_search_empty_title
            ),
            subtitle = stringResource(
                if (state.isCatalogEmpty) R.string.catalog_empty_subtitle else R.string.catalog_search_empty_subtitle
            )
        )
        return
    }
    val format = setFormat()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenPadding,
            end = ScreenPadding,
            top = SectionSpacing,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(CardSpacing)
    ) {
        items(state.rows, key = { it.exercise.id }) { summary ->
            CatalogRow(summary = summary, format = format, onMenu = { onMenu(summary.exercise) })
        }
    }
}

/** Строка справочника. Тап по ней откроет прогресс с E3; пока действия — только в меню. */
@Composable
private fun CatalogRow(
    summary: CatalogSummary,
    format: SetFormat,
    onMenu: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = summary.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = lastSetLine(summary, format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.catalog_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** «60 кг × 8 · 3 дн. назад» либо «ещё не делали». */
@Composable
private fun lastSetLine(summary: CatalogSummary, format: SetFormat): String {
    val best = summary.lastBest
    val doneAt = summary.lastDoneAt
    if (best == null || doneAt == null) return stringResource(R.string.common_never_done)
    return stringResource(R.string.catalog_last_set, formatSet(best, format), daysAgoText(doneAt))
}

/**
 * Переименование. Ошибка «уже есть» живёт в поле, а не в снекбаре: диалог
 * остаётся открытым, чтобы название можно было сразу поправить.
 */
@Composable
internal fun RenameDialog(
    initialName: String,
    nameTaken: Boolean,
    onNameEdited: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onNameEdited()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = nameTaken,
                supportingText = if (nameTaken) {
                    { Text(stringResource(R.string.catalog_rename_taken)) }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.small
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Выбор единицы: применяется сразу по тапу, подсказка объясняет, что станет с весом. */
@Composable
internal fun UnitDialog(
    current: ExerciseUnit,
    onSelect: (ExerciseUnit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_unit_title)) },
        text = {
            Column {
                ExerciseUnit.entries.forEach { unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(
                                selected = unit == current,
                                onClick = { onSelect(unit) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = unit == current, onClick = null)
                        Text(
                            text = unitName(unit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.catalog_unit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun CatalogContentPreview() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    WorkoutTimerTheme {
        CatalogContent(
            state = ExerciseCatalogState(
                isLoaded = true,
                rows = listOf(
                    CatalogSummary(
                        CatalogExercise(1, "Подтягивания", ExerciseUnit.BODYWEIGHT),
                        SessionSet(1, 1, 1, 0.0, 0.0, 12, ExerciseUnit.BODYWEIGHT),
                        now - day
                    ),
                    CatalogSummary(
                        CatalogExercise(2, "Присед", ExerciseUnit.KG),
                        SessionSet(2, 1, 2, 60.0, 0.0, 8, ExerciseUnit.KG),
                        now - 3 * day
                    ),
                    CatalogSummary(
                        CatalogExercise(3, "Тяга блока", ExerciseUnit.PLATE),
                        SessionSet(3, 1, 3, 5.0, 2.0, 12, ExerciseUnit.PLATE),
                        now
                    ),
                    CatalogSummary(CatalogExercise(4, "Фронтальный присед", ExerciseUnit.KG), null, null)
                )
            ),
            onMenu = {}
        )
    }
}
```

`NavGraph.kt` — в `composable(Screen.Workouts.route)` передать `onExercisesClick = { navController.navigate(Screen.Exercises.route) }`, после экрана истории добавить (импорт `ru.hopes.workouttimer.presentation.screen.exercises.ExerciseCatalogScreen`):

```kotlin

        // Экран «Упражнения» — справочник
        composable(Screen.Exercises.route) {
            ExerciseCatalogScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
```

- [ ] **Step 8: Тест композиции**

`ExerciseCatalogContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.exercises

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
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ExerciseCatalogContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val squat = CatalogExercise(1L, "Присед", ExerciseUnit.KG)

    private fun showRows(vararg rows: CatalogSummary, onMenu: (CatalogExercise) -> Unit = {}) =
        composeRule.setContent {
            WorkoutTimerTheme {
                CatalogContent(state = ExerciseCatalogState(rows = rows.toList(), isLoaded = true), onMenu = onMenu)
            }
        }

    @Test
    fun строка_показывает_последний_лучший_подход_с_давностью() {
        val doneAt = System.currentTimeMillis() - 3 * 86_400_000L - 3_600_000L
        showRows(CatalogSummary(squat, SessionSet(1L, 1L, 1L, 60.0, 0.0, 8, ExerciseUnit.KG), doneAt))

        composeRule.onNodeWithText("60 кг × 8 · 3 дн. назад").assertIsDisplayed()
    }

    @Test
    fun упражнение_без_подходов_подписано_ещё_не_делали() {
        showRows(CatalogSummary(squat, null, null))

        composeRule.onNodeWithText("ещё не делали").assertIsDisplayed()
    }

    @Test
    fun меню_строки_открывается_кнопкой() {
        var menuFor: CatalogExercise? = null
        showRows(CatalogSummary(squat, null, null), onMenu = { menuFor = it })

        composeRule.onNodeWithContentDescription("Действия с упражнением").performClick()

        assertEquals(squat, menuFor)
    }

    @Test
    fun занятое_название_показывает_ошибку_в_диалоге() {
        composeRule.setContent {
            WorkoutTimerTheme {
                RenameDialog(initialName = "Присед", nameTaken = true, onNameEdited = {}, onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Такое упражнение уже есть").assertIsDisplayed()
    }

    @Test
    fun пустое_название_не_сохраняется() {
        composeRule.setContent {
            WorkoutTimerTheme {
                RenameDialog(initialName = "", nameTaken = false, onNameEdited = {}, onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Сохранить").assertIsNotEnabled()
    }

    @Test
    fun выбор_единицы_передаёт_её_наружу() {
        var picked: ExerciseUnit? = null
        composeRule.setContent {
            WorkoutTimerTheme {
                UnitDialog(current = ExerciseUnit.KG, onSelect = { picked = it }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("плита").performClick()

        assertEquals(ExerciseUnit.PLATE, picked)
    }
}
```

- [ ] **Step 9: Запустить**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок. Все новые строки `unit_*`, `catalog_*`, `history_*` к этому моменту где-то используются — новых `UnusedResources` быть не должно; если есть, найти строку без вызова.

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.exercises.ExerciseCatalogContentTest`
Expected: PASS, 6 тестов.

- [ ] **Step 10: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: экран «Упражнения» — справочник с переименованием и сменой единицы

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---
### Task 10: Проверка на эмуляторе

**Files:** нет изменений кода (кроме исправлений, если проверка что-то найдёт; каждое исправление — отдельный коммит `fix:` с тестом, который ловит найденное).

- [ ] **Step 1: Полный прогон**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
git status --short app/schemas          # пусто: схема v8 не менялась
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Expected: всё зелёное. Новые инструментальные классы E2: `CatalogDaoTest` (8), `ExerciseEditSheetContentTest` (3), `ActiveContentTilesTest` (3), `HistoryContentTest` (2), `ExerciseCatalogContentTest` (6); `WorkoutDaoTest` — 15, `WeightRepsSheetContentTest` — 5.

- [ ] **Step 2: Данные с единицами**

Каталог для скриншотов — scratchpad своей сессии:

```bash
SCRATCH=<scratchpad текущей сессии>/e2-check
mkdir -p "$SCRATCH"
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Файл `$SCRATCH/e2-import.json` (латинские «Squat» нужны, чтобы набрать запрос через `adb shell input text` — кириллицу он не вводит; «Жим ногами» — строка старого формата без `unit`):

```json
{"version":1,"exportDate":"2026-10-01","appVersion":"1.0","workouts":[
 {"name":"Спина","lastUseAt":1,"exercises":[
   {"name":"Тяга блока","weight":5.0,"sets":2,"reps":12,"restTimeMillis":3000,"order":1,"note":"","unit":"PLATE","extraWeight":2.0},
   {"name":"Подтягивания","weight":0.0,"sets":2,"reps":8,"restTimeMillis":3000,"order":2,"note":"","unit":"BODYWEIGHT","extraWeight":0.0},
   {"name":"Squat","weight":60.0,"sets":2,"reps":8,"restTimeMillis":3000,"order":3,"note":"","unit":"KG","extraWeight":0.0}]},
 {"name":"Ноги","lastUseAt":2,"exercises":[
   {"name":"Squat sumo","weight":4.0,"sets":1,"reps":10,"restTimeMillis":3000,"order":1,"note":"","unit":"PLATE","extraWeight":0.0},
   {"name":"Жим ногами","weight":120.0,"sets":1,"reps":10,"restTimeMillis":3000,"order":2,"note":""}]}
]}
```

`$ADB -s emulator-5554 push "$SCRATCH/e2-import.json" /sdcard/Download/`, в приложении: список → «Экспорт и импорт» → «Импортировать тренировки» → выбрать файл (в пикере — через корень памяти устройства → `Download`; навигация — `$ADB -s emulator-5554 shell uiautomator dump /sdcard/ui.xml && $ADB -s emulator-5554 exec-out cat /sdcard/ui.xml` и `input tap` по координатам `bounds`).

Проверить справочник копией базы (как в E1: `force-stop`, затем `exec-out run-as ru.hopes.workouttimer cat databases/<файл>` для `workout_db`, `-wal`, `-shm`):

```bash
~/Library/Android/sdk/platform-tools/sqlite3 "$SCRATCH/workout_db" \
  "SELECT name, unit FROM exercise_catalog ORDER BY name; SELECT name, weight, extraWeight FROM exercises ORDER BY name;"
```

Expected: «Squat» KG, «Squat sumo» PLATE, «Подтягивания» BODYWEIGHT, «Тяга блока» PLATE, «Жим ногами» KG; у «Тяги блока» `5.0 | 2.0`, у «Подтягиваний» `0.0 | 0.0`.

Скриншот любого экрана: `$ADB -s emulator-5554 exec-out screencap -p > "$SCRATCH/<имя>.png"`; каждый просмотреть (Read) и сверить с ожиданием ниже.

- [ ] **Step 3: Экран подхода — плита с добавкой и без веса**

Начать «Спина».

- `e2-01-plate-tile.png` — первая плитка «5» с подписью «ПЛИТА +2 КГ», вторая «12» / «ПОВТ».
- Тап по плитке веса → лист: `e2-02-plate-wheels.png` — барабаны «ПЛИТА» (5), «+КГ» (2), «ПОВТ» (12). «Готово» без прокрутки — плитка не изменилась.
- «Закончить подход» (первый подход: плита 5 +2 кг) → отдых: строка «плита 5 +2 кг · 12 повторений». Тап по строке → лист → прокрутить «+КГ» на одно деление вниз до 2.5 (`input swipe` по барабану снизу вверх на высоту строки) → «Готово» → строка «плита 5 +2.5 кг · 12 повторений» (правка во время отдыха относится к следующему подходу).
- Дойти до «Подтягиваний»: `e2-03-bodyweight-tile.png` — одна плитка «8» / «ПОВТ» на всю ширину, плитки веса нет; строка отдыха после подхода — «8 повторений».
- Довести тренировку до конца.

- [ ] **Step 4: История — разворот сессии**

Список → меню «Спины» → «История» → тап по сессии: `e2-04-history-expanded.png` — строки «Тяга блока — плита 5 +2 кг × 12 · плита 5 +2.5 кг × 12», «Подтягивания — 8 · 8», «Squat — 60 кг × 8 · 60 × 8». Повторный тап сворачивает.

- [ ] **Step 5: Редактор — подсказки и барабаны по единице**

Меню «Ноги» → «Редактировать» → «+ Упражнение» → тап по полю названия, `$ADB -s emulator-5554 shell input text squ`:

- `e2-05-editor-suggestions.png` — под полем «Squat» (справа «кг») и «Squat sumo» («плита»).
- Тап «Squat sumo» → поле «Squat sumo», подсказки исчезли, барабаны — «ПЛИТА», «+КГ», «ПОДХ», «ПОВТ», «ОТДЫХ»: `e2-06-editor-plate-wheels.png`.
- Выйти без сохранения («Назад» → «Выйти»).

- [ ] **Step 6: Экран «Упражнения» — переименование и единица**

Список → иконка гантели в шапке:

- `e2-07-catalog.png` — по алфавиту: «Squat» («60 кг × 8 · сегодня»), «Squat sumo» («ещё не делали»), «Жим ногами», «Подтягивания» («8 · сегодня»), «Тяга блока» («плита 5 +2.5 кг × 12 · сегодня»). Поле поиска: `input text SUMO` → одна строка «Squat sumo»; очистить поле — снова весь список.
- ⋮ у «Squat sumo» → «Переименовать» → стереть поле, `input text squat` → «Сохранить»: `e2-08-rename-taken.png` — диалог открыт, под полем «Такое упражнение уже есть». Поправить на `Squat wide` → «Сохранить» → диалог закрылся, строка «Squat wide».
- ⋮ у «Squat» → «Единица» → «плита»: `e2-09-unit-dialog.png` (до тапа), затем `e2-10-catalog-after-unit.png` — строка «Squat» по-прежнему «60 кг × 8 · сегодня» (прошлые подходы в своей единице).
- Копия базы: `SELECT unit FROM exercise_catalog WHERE name = 'Squat'` → `PLATE`; `SELECT weight, extraWeight FROM exercises WHERE name = 'Squat'` → `30.0 | 0.0`; `SELECT unit, weight FROM session_sets s JOIN exercise_catalog c ON c.id = s.catalogId WHERE c.name = 'Squat'` → `KG | 60.0`; `SELECT name FROM exercises WHERE name LIKE 'Squat%'` содержит `Squat wide`.
- Повторно: ⋮ у «Тяга блока» → «Единица» → «плита» (текущая) → в базе у «Тяги блока» по-прежнему `5.0 | 2.5`.

- [ ] **Step 7: Итог**

Push и PR **не делать** — это делает контроллер после финального ревью. В отчёт: результаты Step 1 (числа тестов), перечень скриншотов с путями и что на каждом совпало или не совпало с ожиданием, найденные и исправленные проблемы (с SHA коммитов `fix:`).
