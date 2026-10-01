# Прогресс в упражнении, E3 — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Экран прогресса упражнения `exercise_progress/{catalogId}`: шапка с единицей, главная цифра (лучший подход за всё время) с изменением за период, график лучшего подхода по сессиям на своём Canvas с выбором точки, переключатель периода 1 мес / 3 мес / всё, список всех сессий как табличная замена графика; экран открывается тапом по строке экрана «Упражнения» и по упражнению в развёрнутой сессии истории. Схема базы не меняется.

**Architecture:** Два новых запроса `WorkoutDao` (запись справочника по id и все её подходы по времени сессий) → `ExerciseCatalogRepository` → `ObserveExerciseHistoryUseCase` отдаёт `ExerciseHistory` (запись + сессии). Вся логика экрана — чистые функции: доменные (`progressLevel`, `progressPoints`, `periodStart`, `progressDelta`, `summarizeProgress` в `domain/model/ExerciseProgress.kt`) и презентационные (подписи в `ProgressFormat.kt`, геометрия графика в `ChartGeometry.kt`), все с JVM-тестами. `ExerciseProgressViewModel` держит период и выбранную точку и пересчитывает `ProgressSummary`; `ProgressChart` только рисует по готовым позициям и отдаёт тап.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (Material3, тема «Dark Athletic»; Canvas + `TextMeasurer`, без библиотек графиков), Hilt, Room 2.8.4 (схема v8 без изменений), Coroutines/Flow, Navigation Compose 2.9, JUnit4 + MockK + coroutines-test (JVM), Compose UI test + in-memory Room (`androidTest`, эмулятор).

**Spec:** `docs/superpowers/specs/2026-10-01-exercise-progress-design.md` — раздел «E3 — экран прогресса» целиком; из E2 — «История тренировки» и «Экран «Упражнения»» (входы на прогресс, которые E2 оставил неактивными); E3-части разделов «Ошибки и краевые случаи» (удалённая тренировка), «Тестирование» (лучший подход и уровень точки для плиты, «график с выбором точки»), «Маршруты и строки» (`exercise_progress/{catalogId}`, префикс `progress_`).

## Global Constraints

- Ветка `feature/exercise-progress-e3` поверх `feature/exercise-progress-e2` (PR #22), та — поверх `feature/exercise-progress` (E1, PR #21). Ни одна не влита. Push и PR в этом плане не делаются — их делает контроллер после финального ревью.
- Схема базы **не меняется**: версия 8, `app/schemas/ru.hopes.workouttimer.data.dao.AppDatabase/8.json` не трогается, новых миграций и сущностей нет — только `@Query` к существующим таблицам. Если после правок `git status` показывает изменённый `8.json` — остановиться и разобраться, а не коммитить схему.
- `fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 4)` в `AppModule.kt` **не трогать**.
- Кириллицу в SQL приложения не сравнивать (`LOWER()` в SQLite — только ASCII). Запросы E3 фильтруют только по id.
- Регулярки — без флага `(?U)` (движок ICU на Android его не знает). Непечатные и типографские символы в исходниках Kotlin — только экранированными: ` `, `−`, никогда не литералом.
- В графике, главной цифре и изменении — **только подходы в текущей единице** записи (`exercise_catalog.unit`). Подходы в других единицах видны только в списке сессий, со своей подписью. `session_sets.unit` не переписывается никогда.
- Одна подпись подхода на всё приложение: `formatLoad` / `formatSet` / `formatSetList` в `presentation/utils/SetFormat.kt`. Один лучший подход: `bestSet()` в `domain/model/SessionSets.kt`. Один уровень точки по оси Y: `progressLevel()` (плита — `плита + добавка / 11`). Своих форматтеров веса на экране не заводить.
- Математика графика (деления оси Y, время → X, уровень → Y, попадание тапа) и правила прогресса (период, точки, изменение) — чистые функции с JVM-тестами. `ProgressChart` только рисует по готовым позициям и передаёт тап.
- Библиотеки графиков не подключать: `app/build.gradle.kts` и `gradle/libs.versions.toml` не меняются.
- Даты: время сессии — `finishedAt`. Периоды — календарные месяцы в часовом поясе устройства (`TimeZone.getDefault()`) через `java.util.Calendar`: minSdk 24, `java.time` без десугаринга недоступен. Названия месяцев — из `string-array`, не из `SimpleDateFormat` (ICU на Android пишет «сент.», а спека — «26 сен»).
- Все пользовательские тексты — в `app/src/main/res/values/strings.xml`, в секции своего экрана, с префиксами `progress_` (экран прогресса), `catalog_`, `history_` (подписи нажатия на входах). Тексты со счётом — `plurals`.
- Дизайн — существующие компоненты и токены: `EmptyState`, `SectionHeader`, `ScreenPadding`/`CardSpacing`/`SectionSpacing`, `MaterialTheme.colorScheme/shapes/typography`. Цвета графика — только из `colorScheme`: линия и точки `primary`, кольцо точки `surface` (цвет подложки карточки графика), сетка `outline`, подписи осей и подсказки `onSurfaceVariant`. Текст никогда не красится цветом линии; изменение за период — текстовым цветом, без «зелёный = хорошо». Новых цветов, шрифтов и размеров текста нет (главная цифра — `headlineMedium`); размеры геометрии графика (dp) — константы в `ProgressChart.kt`.
- Комментарии в коде — по-русски, коротко, объясняют «почему». Сообщения коммитов — по-русски с префиксом (`feat:`, `test:`, `refactor:`, `fix:`), последняя строка `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Инструментальные тесты — **только** на `emulator-5554`: перед запуском `~/Library/Android/sdk/platform-tools/adb devices` — если подключено что-то кроме `emulator-5554`, не запускать (`connectedAndroidTest` сносит приложение). Запуск: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`. Эмулятор не запущен — `~/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro -no-snapshot-save -no-audio &` и дождаться `adb shell getprop sys.boot_completed` = 1. Имена тестовых методов в `androidTest` — через подчёркивания.
- JVM-тесты: `./gradlew :app:testDebugUnitTest --tests '<pattern>'`. Room in-memory в JVM-тестах **не собирается** (см. спеку, «Тестирование»), тесты DAO — только `androidTest`. Поведение, зависящее от платформы (склонение `plurals`, `TextMeasurer`, жесты), проверяется Compose-тестами на эмуляторе, а не только на JVM.
- В конце каждой задачи: `./gradlew :app:testDebugUnitTest :app:lintDebug` зелёные, lint — 0 ошибок; задачи, трогающие `androidTest`, дополнительно гоняют свои инструментальные классы на эмуляторе. Строки `progress_*` появляются в Task 3, а используются с Task 5 — предупреждения `UnusedResources` в промежутке допустимы, к концу Task 7 их быть не должно.

## Review Focus

1. Единицу упражнения сменили после тренировок (подходы в кг, запись теперь «плита», плитовых подходов ещё нет): точек в текущей единице ноль, но сессии есть. Ожидается: не пустое состояние «Сделайте упражнение…», не падение на пустом списке точек, а пометка «Подходы в других единицах — в списке ниже» и список со старыми подписями «40 кг × 12»; главной цифры и графика нет. Тесты — Task 1 (`summarizeProgress`) и Task 6 (Compose).
2. Выбранная точка ушла из периода после переключения («3 мес» → «1 мес»): подпись точки и подсветка строки не должны висеть от точки, которой на графике нет. Ожидается: выбор снимается; точка, оставшаяся в периоде, остаётся выбранной. Тест — Task 4.
3. Плита с добавкой на границе плит: «плита 5 +10 кг» не должна сравняться с «плитой 6» на оси, а изменение «плита 5 +2 кг» → «плита 6» — это «+1 плита», при той же плите — «+2 кг». Деления оси — целые номера плит. Тесты — Task 1 (уровень, изменение), Task 3 (ось), Task 6 (текст «+1 плита»).
4. Тренировку удалили, а упражнение из неё делалось ещё и в другой тренировке: прогресс должен собрать подходы из обеих тренировок, включая сессии удалённой, по времени завершения. Тест — Task 2 (DAO).
5. Устаревший или неверный `catalogId` в маршруте (запись справочника удалена автоочисткой) и `catalogId` больше `Int.MAX_VALUE`: аргумент читается как `Long`, а не обнуляется; экран показывает «Упражнение не найдено», а не падает и не крутит пустоту. Тесты — Task 4 (ViewModel), Task 6 (Compose), Task 7 (маршрут).

## Решения по неоднозначностям спеки

- **Изменение в кг при том же весе.** Спека задаёт для кг только разницу веса. При равном весе показывается разница повторов («+2 повт»), при полном равенстве — «без изменений»: «+0 кг» ничего не говорит. Для плиты порядок из спеки: номер плиты → кг добавки → повторы.
- **Что фильтрует период.** Период фильтрует точки графика и изменение. Главная цифра — «за всё время» (так в спеке), список — «все подходы упражнения» (так в спеке). Переключатель стоит под графиком, как в спеке.
- **Ось X.** Реальная шкала времени от первой до последней точки периода (перерывы видны), без пустого поля до начала периода.
- **Подпись выбранной точки.** Строка над графиком внутри его карточки (вместо всплывающей подсказки — её не перекрывает палец); без выбора — приглушённая подсказка «Нажмите на точку…». Строка сессии подсвечивается рамкой и `selected`-семантикой; список к ней не прокручивается — прокрутка увела бы график из виду.
- **Меньше двух точек в периоде.** На месте графика — строка «График появится, когда за период будет две тренировки»; переключатель периода остаётся, если в текущей единице есть хоть одна точка, чтобы можно было выбрать «всё».
- **Без веса отдельно от перечня.** Главная цифра и подпись точки для упражнения без веса — «12 повт.» (как строка экрана «Упражнения»); в списке сессий — общий `formatSetList` («12 · 10»), как в истории.
- **Нет записи / сбой чтения.** Неизвестный `catalogId` — пустое состояние «Упражнение не найдено»; сбой чтения — пустое состояние «Не удалось загрузить прогресс». Экран только читает, снекбар не нужен.

## Карта файлов

- `domain/model/ExerciseProgress.kt` (новый) — `ProgressSession`, `ExerciseHistory`, `ProgressPeriod`, `ProgressPoint`, `ProgressDelta`, `ProgressSummary` и чистые функции прогресса.
- `data/dao/WorkoutDao.kt` — `observeCatalogEntry`, `observeSetsForCatalog`; `domain/repository/ExerciseCatalogRepository.kt` + `data/ExerciseCatalogRepositoryImpl.kt` — `observeExercise`, `observeSetsForExercise`; `domain/usecase/ObserveExerciseHistoryUseCase.kt` (новый).
- `presentation/screen/progress/` (новый пакет) — `ProgressFormat.kt` (подписи), `ChartGeometry.kt` (геометрия), `ExerciseProgressViewModel.kt`, `ProgressChart.kt` (Canvas), `ExerciseProgressScreen.kt` (экран и `ProgressContent`).
- `presentation/navigation/NavGraph.kt` — маршрут `Screen.ExerciseProgress` и входы; `presentation/screen/exercises/ExerciseCatalogScreen.kt`, `presentation/screen/workoutHistory/WorkoutHistoryScreen.kt` — нажатие строк.
- `app/src/main/res/values/strings.xml` — секция «Прогресс упражнения», `catalog_open_progress`, `history_open_progress`.

---

### Task 1: Доменные правила прогресса — точки, период, изменение

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/ExerciseProgress.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/model/ExerciseProgressTest.kt`

**Interfaces:**
- Consumes: `SessionSet`, `LoggedSet`, `CatalogExercise` (`domain/model/ExerciseCatalog.kt`), `ExerciseUnit`, `bestSet(sets: List<SessionSet>, unit: ExerciseUnit): SessionSet?` (`SessionSets.kt`), `PLATE_EXTRA_MAX = 10.0` (`ExerciseLoad.kt`).
- Produces:
  - `data class ProgressSession(val sessionId: Long, val finishedAt: Long, val sets: List<SessionSet>)`
  - `data class ExerciseHistory(val exercise: CatalogExercise, val sessions: List<ProgressSession>)`
  - `enum class ProgressPeriod(val months: Int?) { MONTH(1), QUARTER(3), ALL(null) }`
  - `data class ProgressPoint(val sessionId: Long, val finishedAt: Long, val best: SessionSet, val level: Double)`
  - `sealed interface ProgressDelta { data class Kg(val amount: Double); data class Plates(val amount: Int); data class Reps(val amount: Int); data object NoChange }`
  - `data class ProgressSummary(val best: SessionSet?, val hasPoints: Boolean, val points: List<ProgressPoint>, val delta: ProgressDelta?, val hasOtherUnits: Boolean)`
  - `fun groupProgressSessions(sets: List<LoggedSet>): List<ProgressSession>` — по возрастанию `finishedAt`
  - `fun progressLevel(set: SessionSet): Double`
  - `fun progressPoints(sessions: List<ProgressSession>, unit: ExerciseUnit): List<ProgressPoint>`
  - `fun periodStart(period: ProgressPeriod, now: Long, zone: TimeZone): Long?`
  - `fun progressDelta(first: SessionSet, last: SessionSet, unit: ExerciseUnit): ProgressDelta`
  - `fun summarizeProgress(sessions: List<ProgressSession>, unit: ExerciseUnit, period: ProgressPeriod, now: Long, zone: TimeZone): ProgressSummary`

- [ ] **Step 1: Написать падающие тесты**

`ExerciseProgressTest.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExerciseProgressTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L

    private fun at(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, dayOfMonth, 12, 0)
        }.timeInMillis

    private fun kg(id: Long, sessionId: Long, weight: Double, reps: Int) =
        SessionSet(id, sessionId, 1L, weight, 0.0, reps, ExerciseUnit.KG)

    private fun plate(id: Long, sessionId: Long, plate: Double, extra: Double, reps: Int) =
        SessionSet(id, sessionId, 1L, plate, extra, reps, ExerciseUnit.PLATE)

    private fun bodyweight(id: Long, sessionId: Long, reps: Int) =
        SessionSet(id, sessionId, 1L, 0.0, 0.0, reps, ExerciseUnit.BODYWEIGHT)

    private fun session(id: Long, finishedAt: Long, vararg sets: SessionSet) =
        ProgressSession(id, finishedAt, sets.toList())

    @Test
    fun `уровень плиты с добавкой не дотягивает до следующей плиты`() {
        assertEquals(5.0, progressLevel(plate(1, 1, 5.0, 0.0, 12)), 0.0)
        assertEquals(5.0 + 2.0 / 11, progressLevel(plate(1, 1, 5.0, 2.0, 12)), 1e-9)
        val maxExtra = progressLevel(plate(1, 1, 5.0, 10.0, 12))
        assertTrue(maxExtra < 6.0)
        assertTrue(maxExtra > progressLevel(plate(1, 1, 5.0, 9.5, 12)))
    }

    @Test
    fun `уровень кг — вес, без веса — повторы`() {
        assertEquals(62.5, progressLevel(kg(1, 1, 62.5, 6)), 0.0)
        assertEquals(12.0, progressLevel(bodyweight(1, 1, 12)), 0.0)
    }

    @Test
    fun `подходы собираются в сессии по времени, внутри — в порядке записи`() {
        val logged = listOf(
            LoggedSet(kg(5, 20, 70.0, 5), "Присед", 2_000L),
            LoggedSet(kg(1, 10, 60.0, 8), "Присед", 1_000L),
            LoggedSet(kg(2, 10, 62.5, 6), "Присед", 1_000L)
        )

        val sessions = groupProgressSessions(logged)

        assertEquals(listOf(10L, 20L), sessions.map { it.sessionId })
        assertEquals(listOf(1_000L, 2_000L), sessions.map { it.finishedAt })
        assertEquals(listOf(60.0, 62.5), sessions[0].sets.map { it.weight })
    }

    @Test
    fun `точка сессии — лучший подход в текущей единице, сессии в других единицах пропускаются`() {
        val sessions = listOf(
            session(1, 1_000, kg(1, 1, 40.0, 12)),
            session(2, 2_000, plate(2, 2, 5.0, 2.0, 12), plate(3, 2, 5.0, 2.5, 10), plate(4, 2, 4.0, 10.0, 15))
        )

        val points = progressPoints(sessions, ExerciseUnit.PLATE)

        assertEquals(listOf(2L), points.map { it.sessionId })
        assertEquals(plate(3, 2, 5.0, 2.5, 10), points.single().best)
        assertEquals(5.0 + 2.5 / 11, points.single().level, 1e-9)
    }

    @Test
    fun `начало периода считается календарными месяцами`() {
        // 31 марта минус месяц — 28 февраля, а не 3 марта.
        assertEquals(at(2026, 2, 28), periodStart(ProgressPeriod.MONTH, at(2026, 3, 31), utc))
        assertEquals(at(2026, 7, 1), periodStart(ProgressPeriod.QUARTER, at(2026, 10, 1), utc))
        assertNull(periodStart(ProgressPeriod.ALL, at(2026, 10, 1), utc))
    }

    @Test
    fun `изменение в кг — по весу, при том же весе — по повторам`() {
        assertEquals(ProgressDelta.Kg(2.5), progressDelta(kg(1, 1, 60.0, 8), kg(2, 2, 62.5, 6), ExerciseUnit.KG))
        assertEquals(ProgressDelta.Reps(2), progressDelta(kg(1, 1, 60.0, 8), kg(2, 2, 60.0, 10), ExerciseUnit.KG))
        assertEquals(ProgressDelta.NoChange, progressDelta(kg(1, 1, 60.0, 8), kg(2, 2, 60.0, 8), ExerciseUnit.KG))
    }

    @Test
    fun `изменение плиты — в плитах, при той же плите — в кг добавки, затем в повторах`() {
        assertEquals(
            ProgressDelta.Plates(1),
            progressDelta(plate(1, 1, 5.0, 2.0, 12), plate(2, 2, 6.0, 0.0, 10), ExerciseUnit.PLATE)
        )
        assertEquals(
            ProgressDelta.Kg(2.0),
            progressDelta(plate(1, 1, 5.0, 2.0, 12), plate(2, 2, 5.0, 4.0, 12), ExerciseUnit.PLATE)
        )
        assertEquals(
            ProgressDelta.Reps(2),
            progressDelta(plate(1, 1, 5.0, 2.0, 12), plate(2, 2, 5.0, 2.0, 14), ExerciseUnit.PLATE)
        )
    }

    @Test
    fun `изменение без веса — в повторах, может быть отрицательным`() {
        assertEquals(
            ProgressDelta.Reps(-2),
            progressDelta(bodyweight(1, 1, 10), bodyweight(2, 2, 8), ExerciseUnit.BODYWEIGHT)
        )
    }

    @Test
    fun `сводка — лучший за всё время, точки и изменение только за период`() {
        val now = at(2026, 10, 1)
        val sessions = listOf(
            session(1, now - 200 * day, kg(1, 1, 70.0, 3)),
            session(2, now - 60 * day, kg(2, 2, 60.0, 8)),
            session(3, now - 10 * day, kg(3, 3, 62.5, 6))
        )

        val quarter = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.QUARTER, now, utc)
        assertEquals(70.0, quarter.best!!.weight, 0.0)
        assertEquals(listOf(2L, 3L), quarter.points.map { it.sessionId })
        assertEquals(ProgressDelta.Kg(2.5), quarter.delta)

        val month = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.MONTH, now, utc)
        assertEquals(listOf(3L), month.points.map { it.sessionId })
        assertNull(month.delta)
        assertTrue(month.hasPoints)

        val all = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.ALL, now, utc)
        assertEquals(listOf(1L, 2L, 3L), all.points.map { it.sessionId })
        assertEquals(ProgressDelta.Kg(-7.5), all.delta)
    }

    @Test
    fun `после смены единицы старые подходы не попадают ни в главную цифру, ни в график`() {
        val now = at(2026, 10, 1)
        val sessions = listOf(session(1, now - 5 * day, kg(1, 1, 40.0, 12)))

        val summary = summarizeProgress(sessions, ExerciseUnit.PLATE, ProgressPeriod.QUARTER, now, utc)

        assertNull(summary.best)
        assertFalse(summary.hasPoints)
        assertTrue(summary.points.isEmpty())
        assertNull(summary.delta)
        assertTrue(summary.hasOtherUnits)
    }

    @Test
    fun `без подходов в других единицах пометки нет`() {
        val now = at(2026, 10, 1)

        val summary = summarizeProgress(
            listOf(session(1, now, kg(1, 1, 60.0, 8))), ExerciseUnit.KG, ProgressPeriod.ALL, now, utc
        )

        assertFalse(summary.hasOtherUnits)
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseProgressTest*'`
Expected: FAIL — компиляция: `Unresolved reference 'ProgressSession'`, `'progressLevel'` и т. д.

- [ ] **Step 3: Реализация**

`ExerciseProgress.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** Сессия, в которой делали упражнение: только его подходы, в порядке записи. */
data class ProgressSession(
    val sessionId: Long,
    val finishedAt: Long,
    val sets: List<SessionSet>
)

/** Запись справочника и все её сессии по возрастанию времени завершения. */
data class ExerciseHistory(
    val exercise: CatalogExercise,
    val sessions: List<ProgressSession>
)

/** Период графика; months == null — всё время. */
enum class ProgressPeriod(val months: Int?) { MONTH(1), QUARTER(3), ALL(null) }

/** Точка графика: лучший подход сессии в текущей единице и его высота по оси Y. */
data class ProgressPoint(
    val sessionId: Long,
    val finishedAt: Long,
    val best: SessionSet,
    val level: Double
)

/** Изменение лучшего подхода за период в тех единицах, в которых его понимает человек. */
sealed interface ProgressDelta {
    data class Kg(val amount: Double) : ProgressDelta
    data class Plates(val amount: Int) : ProgressDelta
    data class Reps(val amount: Int) : ProgressDelta
    data object NoChange : ProgressDelta
}

/**
 * Всё, что экран показывает над списком. [best] — за всё время, [points] и
 * [delta] — за период; [hasPoints] — есть ли точки в текущей единице вообще.
 */
data class ProgressSummary(
    val best: SessionSet?,
    val hasPoints: Boolean,
    val points: List<ProgressPoint>,
    val delta: ProgressDelta?,
    val hasOtherUnits: Boolean
)

/**
 * Подходы упражнения по сессиям. Сортировка по времени здесь, а не только в SQL:
 * график строится слева направо, и порядок не должен зависеть от запроса.
 */
fun groupProgressSessions(sets: List<LoggedSet>): List<ProgressSession> =
    sets.groupBy { it.set.sessionId }
        .map { (sessionId, group) -> ProgressSession(sessionId, group.first().finishedAt, group.map { it.set }) }
        .sortedWith(compareBy<ProgressSession>({ it.finishedAt }, { it.sessionId }))

/**
 * Высота точки по оси Y. Плита: добавка поднимает точку внутри промежутка до
 * следующей плиты, но не дотягивает до неё — делитель на единицу больше
 * максимальной добавки.
 */
fun progressLevel(set: SessionSet): Double = when (set.unit) {
    ExerciseUnit.KG -> set.weight
    ExerciseUnit.PLATE -> set.weight + set.extraWeight / (PLATE_EXTRA_MAX + 1)
    ExerciseUnit.BODYWEIGHT -> set.reps.toDouble()
}

/** Точки графика: сессии без подходов в текущей единице точки не дают. */
fun progressPoints(sessions: List<ProgressSession>, unit: ExerciseUnit): List<ProgressPoint> =
    sessions.mapNotNull { session ->
        bestSet(session.sets, unit)?.let { best ->
            ProgressPoint(session.sessionId, session.finishedAt, best, progressLevel(best))
        }
    }

/** Начало периода: календарные месяцы назад от [now] в поясе устройства; null — всё время. */
fun periodStart(period: ProgressPeriod, now: Long, zone: TimeZone): Long? {
    val months = period.months ?: return null
    return Calendar.getInstance(zone).apply {
        timeInMillis = now
        add(Calendar.MONTH, -months)
    }.timeInMillis
}

/**
 * Разница лучших подходов последней и первой сессии периода. «+0 кг» ничего не
 * говорит, поэтому при той же нагрузке считаются повторы.
 */
fun progressDelta(first: SessionSet, last: SessionSet, unit: ExerciseUnit): ProgressDelta {
    val reps = last.reps - first.reps
    val byReps = if (reps != 0) ProgressDelta.Reps(reps) else ProgressDelta.NoChange
    return when (unit) {
        ExerciseUnit.KG ->
            if (last.weight != first.weight) ProgressDelta.Kg(last.weight - first.weight) else byReps
        ExerciseUnit.PLATE -> when {
            last.weight != first.weight -> ProgressDelta.Plates((last.weight - first.weight).roundToInt())
            last.extraWeight != first.extraWeight -> ProgressDelta.Kg(last.extraWeight - first.extraWeight)
            else -> byReps
        }
        ExerciseUnit.BODYWEIGHT -> byReps
    }
}

/** Сводка экрана прогресса для текущей единицы записи и выбранного периода. */
fun summarizeProgress(
    sessions: List<ProgressSession>,
    unit: ExerciseUnit,
    period: ProgressPeriod,
    now: Long,
    zone: TimeZone
): ProgressSummary {
    val all = progressPoints(sessions, unit)
    val start = periodStart(period, now, zone)
    val inPeriod = if (start == null) all else all.filter { it.finishedAt >= start }
    return ProgressSummary(
        best = bestSet(sessions.flatMap { it.sets }, unit),
        hasPoints = all.isNotEmpty(),
        points = inPeriod,
        delta = if (inPeriod.size >= 2) progressDelta(inPeriod.first().best, inPeriod.last().best, unit) else null,
        hasOtherUnits = sessions.any { session -> session.sets.any { it.unit != unit } }
    )
}
```

- [ ] **Step 4: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseProgressTest*'`
Expected: PASS, 11 тестов.

- [ ] **Step 5: Полный прогон и коммит**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/model/ExerciseProgress.kt \
        app/src/test/java/ru/hopes/workouttimer/domain/model/ExerciseProgressTest.kt
git commit -m "$(cat <<'EOF'
feat: доменные правила прогресса — точки, период, изменение

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Подходы упражнения из всех тренировок

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt` (два запроса после `observeLastSessionSets()`)
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/repository/ExerciseCatalogRepository.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/ExerciseCatalogRepositoryImpl.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/ObserveExerciseHistoryUseCase.kt`
- Modify: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/creation/FakeExerciseCatalogRepository.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/ExerciseCatalogRepositoryImplTest.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/usecase/CatalogUseCasesTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/data/dao/CatalogDaoTest.kt`

**Interfaces:**
- Consumes: `LoggedSetRow`, `ExerciseCatalogEntity`, `toDomain()` из `data/mapper/CatalogMapper.kt` (E2); `ExerciseHistory`, `groupProgressSessions` (Task 1).
- Produces:
  - `WorkoutDao.observeCatalogEntry(id: Long): Flow<ExerciseCatalogEntity?>`
  - `WorkoutDao.observeSetsForCatalog(catalogId: Long): Flow<List<LoggedSetRow>>` — по `finishedAt`, затем id сессии, затем id подхода
  - `ExerciseCatalogRepository.observeExercise(id: Long): Flow<CatalogExercise?>`
  - `ExerciseCatalogRepository.observeSetsForExercise(id: Long): Flow<List<LoggedSet>>`
  - `class ObserveExerciseHistoryUseCase @Inject constructor(repo: ExerciseCatalogRepository)`, `operator fun invoke(catalogId: Long): Flow<ExerciseHistory?>` — `null`, если записи нет

- [ ] **Step 1: Падающие JVM-тесты**

`ExerciseCatalogRepositoryImplTest.kt` — добавить импорт `org.junit.Assert.assertNull` и два теста в конец класса:

```kotlin

    @Test
    fun `запись справочника по id отдаётся доменной, отсутствующая — null`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeCatalogEntry(1L) } returns flowOf(
            ExerciseCatalogEntity(id = 1, name = "Тяга блока", nameKey = "тяга блока", unit = "PLATE")
        )
        every { dao.observeCatalogEntry(2L) } returns flowOf(null)
        val repo = ExerciseCatalogRepositoryImpl(dao)

        assertEquals(CatalogExercise(1, "Тяга блока", ExerciseUnit.PLATE), repo.observeExercise(1L).first())
        assertNull(repo.observeExercise(2L).first())
    }

    @Test
    fun `подходы упражнения отдаются с названием и временем сессии`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.observeSetsForCatalog(1L) } returns flowOf(
            listOf(LoggedSetRow(5, 9, 1, "Тяга блока", 5.0, 2.0, 12, "PLATE", 7_000L))
        )

        val sets = ExerciseCatalogRepositoryImpl(dao).observeSetsForExercise(1L).first()

        assertEquals(
            listOf(LoggedSet(SessionSet(5, 9, 1, 5.0, 2.0, 12, ExerciseUnit.PLATE), "Тяга блока", 7_000L)),
            sets
        )
    }
```

`CatalogUseCasesTest.kt` — два теста в конец класса (импорты `assertNull`, `CatalogExercise`, `ExerciseUnit` уже есть):

```kotlin

    @Test
    fun `история упражнения соединяет запись с её сессиями по времени`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeExercise(1L) } returns flowOf(CatalogExercise(1L, "Присед", ExerciseUnit.KG))
        every { repo.observeSetsForExercise(1L) } returns flowOf(
            listOf(
                logged(1, 10, 1, "Присед", 60.0, 8, 1_000L),
                logged(2, 10, 1, "Присед", 62.5, 6, 1_000L),
                logged(3, 11, 1, "Присед", 65.0, 5, 2_000L)
            )
        )

        val history = ObserveExerciseHistoryUseCase(repo)(1L).first()!!

        assertEquals("Присед", history.exercise.name)
        assertEquals(listOf(10L, 11L), history.sessions.map { it.sessionId })
        assertEquals(listOf(8, 6), history.sessions[0].sets.map { it.reps })
    }

    @Test
    fun `нет записи справочника — нет истории`() = runTest {
        val repo = mockk<ExerciseCatalogRepository>()
        every { repo.observeExercise(9L) } returns flowOf(null)
        every { repo.observeSetsForExercise(9L) } returns flowOf(emptyList())

        assertNull(ObserveExerciseHistoryUseCase(repo)(9L).first())
    }
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseCatalogRepositoryImplTest*' --tests '*CatalogUseCasesTest*'`
Expected: FAIL — компиляция: `Unresolved reference 'observeCatalogEntry'`, `'observeExercise'`, `'ObserveExerciseHistoryUseCase'`.

- [ ] **Step 3: Запросы DAO**

`WorkoutDao.kt` — сразу после `fun observeLastSessionSets(): Flow<List<LoggedSetRow>>`:

```kotlin

    // Поток, а не разовый запрос: после переименования или смены единицы на экране
    // «Упражнения» прогресс при возврате показывает новое название и единицу.
    @Query("SELECT * FROM exercise_catalog WHERE id = :id")
    fun observeCatalogEntry(id: Long): Flow<ExerciseCatalogEntity?>

    // Все подходы записи из всех тренировок, включая удалённые: сессии и подходы
    // живут дольше тренировки. JOIN только с workout_sessions — не с workouts.
    @Query(
        """
        SELECT s.id, s.sessionId, s.catalogId, c.name AS exerciseName, s.weight, s.extraWeight,
               s.reps, s.unit, ws.finishedAt
        FROM session_sets s
        JOIN workout_sessions ws ON ws.id = s.sessionId
        JOIN exercise_catalog c ON c.id = s.catalogId
        WHERE s.catalogId = :catalogId
        ORDER BY ws.finishedAt, ws.id, s.id
        """
    )
    fun observeSetsForCatalog(catalogId: Long): Flow<List<LoggedSetRow>>
```

- [ ] **Step 4: Репозиторий и use case**

`ExerciseCatalogRepository.kt` — в интерфейс после `observeSetsForWorkout`:

```kotlin

    /** Запись справочника; null — записи нет (неверный id или её убрала автоочистка). */
    fun observeExercise(id: Long): Flow<CatalogExercise?>

    /** Все подходы записи по времени сессий, внутри сессии — в порядке записи. */
    fun observeSetsForExercise(id: Long): Flow<List<LoggedSet>>
```

`ExerciseCatalogRepositoryImpl.kt` — после `observeSetsForWorkout`:

```kotlin

    override fun observeExercise(id: Long): Flow<CatalogExercise?> =
        dao.observeCatalogEntry(id).map { it?.toDomain() }

    override fun observeSetsForExercise(id: Long): Flow<List<LoggedSet>> =
        dao.observeSetsForCatalog(id).map { rows -> rows.map { it.toDomain() } }
```

`ObserveExerciseHistoryUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.hopes.workouttimer.domain.model.ExerciseHistory
import ru.hopes.workouttimer.domain.model.groupProgressSessions
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

/** Запись справочника и все её сессии для экрана прогресса; null — записи нет. */
class ObserveExerciseHistoryUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(catalogId: Long): Flow<ExerciseHistory?> =
        combine(repo.observeExercise(catalogId), repo.observeSetsForExercise(catalogId)) { exercise, sets ->
            exercise?.let { ExerciseHistory(it, groupProgressSessions(sets)) }
        }
}
```

`FakeExerciseCatalogRepository.kt` (androidTest) — после `observeSetsForWorkout`:

```kotlin

    override fun observeExercise(id: Long): Flow<CatalogExercise?> = flowOf(catalog.firstOrNull { it.id == id })

    override fun observeSetsForExercise(id: Long): Flow<List<LoggedSet>> = flowOf(emptyList())
```

- [ ] **Step 5: Запустить JVM — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseCatalogRepositoryImplTest*' --tests '*CatalogUseCasesTest*'`
Expected: PASS (4 + 4 теста).

- [ ] **Step 6: Тесты DAO на эмуляторе**

`CatalogDaoTest.kt` — добавить импорт `org.junit.Assert.assertNull` и два теста в конец класса:

```kotlin

    @Test
    fun подходы_упражнения_собираются_из_всех_тренировок_по_времени_включая_удалённую() = runBlocking {
        val legs = saveWorkout("Ноги", exercise("Присед"), exercise("Выпады", order = 1))
        val fullBody = saveWorkout("Фулбади", exercise("присед"))
        val squat = catalogId("Присед")
        val lunge = catalogId("Выпады")
        finish(legs, 300, kg(squat, "Присед", 65.0, 5))
        finish(
            fullBody, 100,
            kg(squat, "Присед", 60.0, 8), kg(lunge, "Выпады", 20.0, 10), kg(squat, "Присед", 62.5, 6)
        )
        // Тренировку удалили — её сессии остаются в прогрессе упражнения.
        dao.deleteWorkoutWithExercises(dao.getWorkoutById(fullBody.toInt())!!)

        val rows = dao.observeSetsForCatalog(squat).first()

        assertEquals(listOf(60.0, 62.5, 65.0), rows.map { it.weight })
        assertEquals(listOf(100L, 100L, 300L), rows.map { it.finishedAt })
        assertEquals(setOf("Присед"), rows.map { it.exerciseName }.toSet())
    }

    @Test
    fun запись_справочника_наблюдается_по_id_и_null_для_несуществующей() = runBlocking {
        saveWorkout("Ноги", exercise("Присед"))
        val squat = catalogId("Присед")
        dao.changeCatalogUnit(squat, ExerciseUnit.PLATE)

        assertEquals("PLATE", dao.observeCatalogEntry(squat).first()!!.unit)
        assertNull(dao.observeCatalogEntry(9_999L).first())
    }
```

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.data.dao.CatalogDaoTest`
Expected: PASS, 11 тестов.

- [ ] **Step 7: Полный прогон и коммит**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug && git status --short app/schemas`
Expected: PASS, lint 0 ошибок; `app/schemas` без изменений.

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: подходы упражнения из всех тренировок для прогресса

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Подписи и геометрия графика

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (новая секция «Прогресс упражнения» после секции «Справочник упражнений»)
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ProgressFormat.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ChartGeometry.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/progress/ProgressFormatTest.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/progress/ChartGeometryTest.kt`

**Interfaces:**
- Consumes: `SetFormat`, `formatSet`, `toCorrectNum` (`presentation/utils`); `ProgressDelta`, `ProgressPeriod`, `ProgressPoint` (Task 1).
- Produces:
  - `class DeltaFormat(val kg: String, val reps: String, val none: String, val line: String, val plates: (count: Int, signed: String) -> String)`
  - `fun signedNumber(value: Double): String`
  - `fun formatDelta(delta: ProgressDelta, span: String, format: DeltaFormat): String`
  - `fun shortDate(timestamp: Long, template: String, months: List<String>, zone: TimeZone): String`
  - `fun progressSetText(set: SessionSet, format: SetFormat, repsTemplate: String): String`
  - `@Composable fun deltaFormat(): DeltaFormat`, `@Composable fun periodName(period: ProgressPeriod): String`, `@Composable fun periodSpan(period: ProgressPeriod): String`
  - `data class YAxis(val min: Int, val max: Int, val ticks: List<Int>)`, `data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float)`, `data class ChartPosition(val x: Float, val y: Float)`
  - `fun yAxisFor(levels: List<Double>): YAxis`, `fun timeFraction(time: Long, start: Long, end: Long): Float`, `fun levelFraction(level: Double, axis: YAxis): Float`, `fun tickY(tick: Int, axis: YAxis, plot: PlotArea): Float`, `fun pointPositions(points: List<ProgressPoint>, axis: YAxis, plot: PlotArea): List<ChartPosition>`, `fun nearestPointIndex(xs: List<Float>, tapX: Float, maxDistance: Float): Int?`
  - Строки: `progress_best_label`, `progress_reps`, `progress_delta_line`, `progress_delta_kg`, `progress_delta_reps`, `progress_delta_none`, plurals `progress_delta_plates`, `progress_span_month|quarter|all`, `progress_period_month|quarter|all`, `progress_short_date`, string-array `progress_months`, `progress_point_label`, `progress_chart_hint`, `progress_chart_few`, plurals `progress_chart_description`, `progress_other_units`, `progress_sessions_title`, `progress_empty_title`, `progress_empty_subtitle`, `progress_missing_title`, `progress_error_load`

- [ ] **Step 1: Строки**

`strings.xml` — после строки `<string name="catalog_error_unit">…</string>` и пустой строки за ней:

```xml
    <!-- Прогресс упражнения. Шаблоны progress_delta_* собирает formatDelta. -->
    <string name="progress_best_label">Лучший подход</string>
    <string name="progress_reps">%1$d повт.</string>
    <string name="progress_delta_line">%1$s %2$s</string>
    <string name="progress_delta_kg">%1$s кг</string>
    <string name="progress_delta_reps">%1$s повт</string>
    <string name="progress_delta_none">без изменений</string>
    <plurals name="progress_delta_plates">
        <item quantity="one">%1$s плита</item>
        <item quantity="few">%1$s плиты</item>
        <item quantity="many">%1$s плит</item>
        <item quantity="other">%1$s плиты</item>
    </plurals>
    <string name="progress_span_month">за 1 мес</string>
    <string name="progress_span_quarter">за 3 мес</string>
    <string name="progress_span_all">за всё время</string>
    <string name="progress_period_month">1 мес</string>
    <string name="progress_period_quarter">3 мес</string>
    <string name="progress_period_all">всё</string>
    <string name="progress_short_date">%1$d %2$s</string>
    <!-- Месяцы в родительном падеже: «26 сен», «3 мая». -->
    <string-array name="progress_months">
        <item>янв</item>
        <item>фев</item>
        <item>мар</item>
        <item>апр</item>
        <item>мая</item>
        <item>июн</item>
        <item>июл</item>
        <item>авг</item>
        <item>сен</item>
        <item>окт</item>
        <item>ноя</item>
        <item>дек</item>
    </string-array>
    <string name="progress_point_label">%1$s · %2$s</string>
    <string name="progress_chart_hint">Нажмите на точку, чтобы увидеть подход</string>
    <string name="progress_chart_few">График появится, когда за период будет две тренировки</string>
    <plurals name="progress_chart_description">
        <item quantity="one">График лучшего подхода: %1$d тренировка</item>
        <item quantity="few">График лучшего подхода: %1$d тренировки</item>
        <item quantity="many">График лучшего подхода: %1$d тренировок</item>
        <item quantity="other">График лучшего подхода: %1$d тренировки</item>
    </plurals>
    <string name="progress_other_units">Подходы в других единицах — в списке ниже</string>
    <string name="progress_sessions_title">Тренировки</string>
    <string name="progress_empty_title">Сделайте упражнение — здесь появится прогресс</string>
    <string name="progress_empty_subtitle">Подход записывается, когда вы нажимаете «Закончить подход»</string>
    <string name="progress_missing_title">Упражнение не найдено</string>
    <string name="progress_error_load">Не удалось загрузить прогресс</string>

```

- [ ] **Step 2: Падающие тесты подписей**

`ProgressFormatTest.kt` — шаблоны читаются из настоящего `strings.xml`, как в `SetFormatTest`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.utils.SetFormat
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

class ProgressFormatTest {

    // Рабочий каталог JVM-тестов — модуль app/, поэтому путь относительный.
    private val doc: Document by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
    }

    private val strings: Map<String, String> by lazy {
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private val months: List<String> by lazy {
        val arrays = doc.getElementsByTagName("string-array")
        val array = (0 until arrays.length).map { arrays.item(it) as Element }
            .single { it.getAttribute("name") == "progress_months" }
        val items = array.getElementsByTagName("item")
        (0 until items.length).map { items.item(it).textContent }
    }

    // plurals склоняет Android; здесь проверяется, что в склонение уходит модуль числа.
    private val templates by lazy {
        DeltaFormat(
            kg = strings.getValue("progress_delta_kg"),
            reps = strings.getValue("progress_delta_reps"),
            none = strings.getValue("progress_delta_none"),
            line = strings.getValue("progress_delta_line"),
            plates = { count, signed -> "$signed плит($count)" }
        )
    }

    private val setFormat by lazy {
        SetFormat(
            loadKg = strings.getValue("unit_load_kg"),
            loadPlate = strings.getValue("unit_load_plate"),
            loadPlateExtra = strings.getValue("unit_load_plate_extra"),
            set = strings.getValue("unit_set")
        )
    }

    private val utc = TimeZone.getTimeZone("UTC")

    private fun utcNoon(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, dayOfMonth, 12, 0)
        }.timeInMillis

    @Test
    fun `знак изменения — плюс, типографский минус или ноль`() {
        assertEquals("+2.5", signedNumber(2.5))
        assertEquals("−2", signedNumber(-2.0))
        assertEquals("+0.3", signedNumber(0.30000000000000004))
        assertEquals("0", signedNumber(0.0))
    }

    @Test
    fun `изменение в кг, повторах и без изменений — с периодом`() {
        assertEquals(
            "+2.5 кг за 3 мес",
            formatDelta(ProgressDelta.Kg(2.5), strings.getValue("progress_span_quarter"), templates)
        )
        assertEquals(
            "+2 повт за 1 мес",
            formatDelta(ProgressDelta.Reps(2), strings.getValue("progress_span_month"), templates)
        )
        assertEquals(
            "без изменений за всё время",
            formatDelta(ProgressDelta.NoChange, strings.getValue("progress_span_all"), templates)
        )
    }

    @Test
    fun `плиты склоняются по модулю числа, знак остаётся в тексте`() {
        assertEquals(
            "−2 плит(2) за 3 мес",
            formatDelta(ProgressDelta.Plates(-2), strings.getValue("progress_span_quarter"), templates)
        )
    }

    @Test
    fun `короткая дата — день и месяц из ресурсов`() {
        val template = strings.getValue("progress_short_date")

        assertEquals(12, months.size)
        assertEquals("26 сен", shortDate(utcNoon(2026, 9, 26), template, months, utc))
        assertEquals("3 мая", shortDate(utcNoon(2026, 5, 3), template, months, utc))
    }

    @Test
    fun `подход без веса подписан повторениями, с весом — общей подписью`() {
        val reps = strings.getValue("progress_reps")

        assertEquals(
            "12 повт.",
            progressSetText(SessionSet(1, 1, 1, 0.0, 0.0, 12, ExerciseUnit.BODYWEIGHT), setFormat, reps)
        )
        assertEquals(
            "плита 5 +2 кг × 12",
            progressSetText(SessionSet(1, 1, 1, 5.0, 2.0, 12, ExerciseUnit.PLATE), setFormat, reps)
        )
        assertEquals(
            "62.5 кг × 6",
            progressSetText(SessionSet(1, 1, 1, 62.5, 0.0, 6, ExerciseUnit.KG), setFormat, reps)
        )
    }
}
```

- [ ] **Step 3: Падающие тесты геометрии**

`ChartGeometryTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.SessionSet

class ChartGeometryTest {

    private val day = 86_400_000L

    private fun point(sessionId: Long, finishedAt: Long, level: Double) = ProgressPoint(
        sessionId, finishedAt, SessionSet(sessionId, sessionId, 1L, level, 0.0, 8, ExerciseUnit.KG), level
    )

    @Test
    fun `ось кг — целые деления с шагом 1 вокруг значений`() {
        assertEquals(YAxis(60, 63, listOf(60, 61, 62, 63)), yAxisFor(listOf(60.0, 62.5)))
        assertEquals(YAxis(8, 12, listOf(8, 9, 10, 11, 12)), yAxisFor(listOf(8.0, 12.0)))
    }

    @Test
    fun `широкий разброс — не больше четырёх промежутков круглого шага`() {
        assertEquals(YAxis(40, 100, listOf(40, 60, 80, 100)), yAxisFor(listOf(40.0, 100.0)))
        assertEquals(YAxis(0, 30, listOf(0, 10, 20, 30)), yAxisFor(listOf(1.0, 30.0)))
    }

    @Test
    fun `ровная линия — посередине оси`() {
        assertEquals(YAxis(4, 6, listOf(4, 5, 6)), yAxisFor(listOf(5.0, 5.0)))
        assertEquals(YAxis(0, 1, listOf(0, 1)), yAxisFor(listOf(0.0)))
    }

    @Test
    fun `плита с добавкой лежит между делениями своей плиты и следующей`() {
        val level = 5.0 + 2.0 / 11
        val axis = yAxisFor(listOf(level, 5.0 + 4.0 / 11))

        assertEquals(listOf(5, 6), axis.ticks)
        val fraction = levelFraction(level, axis)
        assertTrue(fraction > 0f && fraction < 1f)
    }

    @Test
    fun `ось X — реальное время, перерыв виден`() {
        assertEquals(0f, timeFraction(0, 0, 100 * day), 0f)
        assertEquals(0.1f, timeFraction(10 * day, 0, 100 * day), 1e-6f)
        assertEquals(1f, timeFraction(100 * day, 0, 100 * day), 0f)
        assertEquals(0.5f, timeFraction(5, 5, 5), 0f)
    }

    @Test
    fun `точки ложатся в область графика, выше — больше`() {
        val axis = YAxis(60, 63, listOf(60, 61, 62, 63))
        val plot = PlotArea(left = 40f, top = 10f, right = 340f, bottom = 190f)

        val positions = pointPositions(
            listOf(point(1, 0, 60.0), point(2, 50, 63.0), point(3, 100, 61.5)), axis, plot
        )

        assertEquals(
            listOf(ChartPosition(40f, 190f), ChartPosition(190f, 10f), ChartPosition(340f, 100f)),
            positions
        )
        assertEquals(10f, tickY(63, axis, plot), 0f)
        assertEquals(190f, tickY(60, axis, plot), 0f)
    }

    @Test
    fun `тап выбирает ближайшую по X точку в пределах зоны попадания`() {
        val xs = listOf(40f, 190f, 340f)

        assertEquals(1, nearestPointIndex(xs, 200f, 30f))
        assertEquals(2, nearestPointIndex(xs, 330f, 30f))
        assertNull(nearestPointIndex(xs, 115f, 30f))
        assertNull(nearestPointIndex(emptyList(), 10f, 30f))
    }
}
```

- [ ] **Step 4: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProgressFormatTest*' --tests '*ChartGeometryTest*'`
Expected: FAIL — компиляция: `Unresolved reference 'DeltaFormat'`, `'yAxisFor'` и т. д.

- [ ] **Step 5: Подписи**

`ProgressFormat.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.formatSet
import ru.hopes.workouttimer.presentation.utils.toCorrectNum
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Шаблоны подписи изменения за период. [plates] склоняет «плита/плиты/плит»:
 * plurals без ресурсов Android не прочитать, поэтому JVM-тест подставляет свою функцию.
 */
class DeltaFormat(
    val kg: String,
    val reps: String,
    val none: String,
    val line: String,
    val plates: (count: Int, signed: String) -> String
)

// Типографский минус, а не дефис: «−2 кг» не путается с тире.
private const val MINUS = "−"

private fun String.fill(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

/** «+2.5», «−2», «0». Разность весов из импорта может выйти 0.30000000000000004 — режем до сотых. */
fun signedNumber(value: Double): String {
    val rounded = (value * 100).roundToLong() / 100.0
    return when {
        rounded > 0 -> "+" + rounded.toCorrectNum()
        rounded < 0 -> MINUS + (-rounded).toCorrectNum()
        else -> "0"
    }
}

/** «+2.5 кг за 3 мес», «+1 плита за 1 мес», «без изменений за всё время». */
fun formatDelta(delta: ProgressDelta, span: String, format: DeltaFormat): String {
    val value = when (delta) {
        is ProgressDelta.Kg -> format.kg.fill(signedNumber(delta.amount))
        is ProgressDelta.Plates -> format.plates(abs(delta.amount), signedNumber(delta.amount.toDouble()))
        is ProgressDelta.Reps -> format.reps.fill(signedNumber(delta.amount.toDouble()))
        ProgressDelta.NoChange -> format.none
    }
    return format.line.fill(value, span)
}

/**
 * «26 сен». Месяцы — из ресурсов, а не из SimpleDateFormat: ICU на Android
 * сокращает «сент.», JVM — по-своему, и подпись зависела бы от платформы.
 */
fun shortDate(timestamp: Long, template: String, months: List<String>, zone: TimeZone): String {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
    return template.fill(calendar.get(Calendar.DAY_OF_MONTH), months[calendar.get(Calendar.MONTH)])
}

/** Подход сам по себе (главная цифра, подпись точки): голое «12» непонятно — «12 повт.». */
fun progressSetText(set: SessionSet, format: SetFormat, repsTemplate: String): String =
    if (set.unit == ExerciseUnit.BODYWEIGHT) repsTemplate.fill(set.reps) else formatSet(set, format)

/** Шаблоны без подстановки: getString без аргументов возвращает текст с %1$s как есть. */
@Composable
fun deltaFormat(): DeltaFormat {
    val resources = LocalResources.current
    return DeltaFormat(
        kg = stringResource(R.string.progress_delta_kg),
        reps = stringResource(R.string.progress_delta_reps),
        none = stringResource(R.string.progress_delta_none),
        line = stringResource(R.string.progress_delta_line),
        plates = { count, signed -> resources.getQuantityString(R.plurals.progress_delta_plates, count, signed) }
    )
}

/** Подпись кнопки периода: «1 мес», «3 мес», «всё». */
@Composable
fun periodName(period: ProgressPeriod): String = stringResource(
    when (period) {
        ProgressPeriod.MONTH -> R.string.progress_period_month
        ProgressPeriod.QUARTER -> R.string.progress_period_quarter
        ProgressPeriod.ALL -> R.string.progress_period_all
    }
)

/** Хвост подписи изменения: «за 1 мес», «за 3 мес», «за всё время». */
@Composable
fun periodSpan(period: ProgressPeriod): String = stringResource(
    when (period) {
        ProgressPeriod.MONTH -> R.string.progress_span_month
        ProgressPeriod.QUARTER -> R.string.progress_span_quarter
        ProgressPeriod.ALL -> R.string.progress_span_all
    }
)
```

- [ ] **Step 6: Геометрия**

`ChartGeometry.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import ru.hopes.workouttimer.domain.model.ProgressPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Ось Y: границы и подписанные деления — всегда целые (спека: «Подписи оси — целые значения»). */
data class YAxis(val min: Int, val max: Int, val ticks: List<Int>)

/** Область линии внутри Canvas в пикселях: слева место под подписи оси, снизу — под даты. */
data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Своя пара координат вместо Offset: геометрия считается и проверяется без Compose. */
data class ChartPosition(val x: Float, val y: Float)

private val NICE_BASES = listOf(1, 2, 5)

// Больше четырёх промежутков на 200dp — подписи слипаются и сетка шумит.
private const val MAX_INTERVALS = 4

/** Целая ось вокруг уровней точек с круглым шагом 1, 2, 5, 10, 20, 50… */
fun yAxisFor(levels: List<Double>): YAxis {
    require(levels.isNotEmpty()) { "Ось строится хотя бы по одной точке" }
    val rawLo = floor(levels.min()).toInt()
    val rawHi = ceil(levels.max()).toInt()
    // Ровная линия рисуется посередине, а не прижатой к краю.
    val flat = rawHi == rawLo
    val lo0 = if (flat) (rawLo - 1).coerceAtLeast(0) else rawLo
    val hi0 = if (flat) rawLo + 1 else rawHi
    val stride = niceStep(hi0 - lo0)
    val lo = Math.floorDiv(lo0, stride) * stride
    val hi = -Math.floorDiv(-hi0, stride) * stride
    return YAxis(lo, hi, (lo..hi step stride).toList())
}

private fun niceStep(span: Int): Int {
    var magnitude = 1
    while (true) {
        for (base in NICE_BASES) {
            val step = base * magnitude
            if ((span + step - 1) / step <= MAX_INTERVALS) return step
        }
        magnitude *= 10
    }
}

/** Доля оси X: реальное время, перерыв между тренировками виден. Одна дата — середина. */
fun timeFraction(time: Long, start: Long, end: Long): Float =
    if (end <= start) 0.5f else ((time - start).toDouble() / (end - start)).toFloat()

/** Доля оси Y снизу вверх. */
fun levelFraction(level: Double, axis: YAxis): Float =
    ((level - axis.min) / (axis.max - axis.min)).toFloat()

fun tickY(tick: Int, axis: YAxis, plot: PlotArea): Float =
    plot.bottom - levelFraction(tick.toDouble(), axis) * (plot.bottom - plot.top)

/** Пиксели точек: X — от первой до последней даты периода, Y — уровень по оси. */
fun pointPositions(points: List<ProgressPoint>, axis: YAxis, plot: PlotArea): List<ChartPosition> {
    if (points.isEmpty()) return emptyList()
    val start = points.minOf { it.finishedAt }
    val end = points.maxOf { it.finishedAt }
    return points.map { point ->
        ChartPosition(
            x = plot.left + timeFraction(point.finishedAt, start, end) * (plot.right - plot.left),
            y = plot.bottom - levelFraction(point.level, axis) * (plot.bottom - plot.top)
        )
    }
}

/**
 * Тап выбирает ближайшую по X точку: человек целится в дату, а не в кружок
 * 8dp. Дальше [maxDistance] — мимо, выбор снимается.
 */
fun nearestPointIndex(xs: List<Float>, tapX: Float, maxDistance: Float): Int? {
    val index = xs.indices.minByOrNull { abs(xs[it] - tapX) } ?: return null
    return index.takeIf { abs(xs[index] - tapX) <= maxDistance }
}
```

- [ ] **Step 7: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProgressFormatTest*' --tests '*ChartGeometryTest*'`
Expected: PASS (5 + 7 тестов).

- [ ] **Step 8: Полный прогон и коммит**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок (предупреждения `UnusedResources` о строках `progress_*` до Task 5–6 допустимы).

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/progress
git commit -m "$(cat <<'EOF'
feat: подписи и геометрия графика прогресса

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: ViewModel экрана прогресса

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressViewModelTest.kt`

**Interfaces:**
- Consumes: `ObserveExerciseHistoryUseCase` (Task 2); `ExerciseHistory`, `ProgressPeriod`, `ProgressSession`, `ProgressSummary`, `summarizeProgress` (Task 1). Ошибка чтения показывается состоянием `loadFailed`, без снекбара: экран только читает.
- Produces:
  - `ExerciseProgressViewModel(observeExerciseHistoryUseCase: ObserveExerciseHistoryUseCase)`; `fun load(catalogId: Long)`, `fun selectPeriod(period: ProgressPeriod)`, `fun selectPoint(sessionId: Long?)`; `val state: StateFlow<ExerciseProgressState>`
  - `data class ExerciseProgressState(val isLoaded: Boolean = false, val loadFailed: Boolean = false, val exercise: CatalogExercise? = null, val sessions: List<ProgressSession> = emptyList(), val period: ProgressPeriod = ProgressPeriod.QUARTER, val summary: ProgressSummary? = null, val selectedSessionId: Long? = null)`

- [ ] **Step 1: Падающие тесты**

`ExerciseProgressViewModelTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseHistory
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.usecase.ObserveExerciseHistoryUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseProgressViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val day = 86_400_000L
    private val squat = CatalogExercise(1L, "Присед", ExerciseUnit.KG)

    private fun session(id: Long, daysAgo: Int, weight: Double, reps: Int) = ProgressSession(
        sessionId = id,
        finishedAt = System.currentTimeMillis() - daysAgo * day,
        sets = listOf(SessionSet(id, id, 1L, weight, 0.0, reps, ExerciseUnit.KG))
    )

    // 200, 60 и 10 дней назад: в «1 мес» одна точка, в «3 мес» две, во «всё» три — в любом поясе.
    private val sessions = listOf(session(1, 200, 70.0, 3), session(2, 60, 60.0, 8), session(3, 10, 62.5, 6))

    private fun useCase(flow: Flow<ExerciseHistory?>): ObserveExerciseHistoryUseCase {
        val mock = mockk<ObserveExerciseHistoryUseCase>()
        every { mock(1L) } returns flow
        return mock
    }

    private fun loaded(flow: Flow<ExerciseHistory?> = flowOf(ExerciseHistory(squat, sessions))): ExerciseProgressViewModel {
        val vm = ExerciseProgressViewModel(useCase(flow))
        vm.load(1L)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `по умолчанию период 3 мес — две точки, изменение и лучший за всё время`() = runTest(dispatcher) {
        val state = loaded().state.value

        assertTrue(state.isLoaded)
        assertEquals(squat, state.exercise)
        assertEquals(ProgressPeriod.QUARTER, state.period)
        assertEquals(listOf(2L, 3L), state.summary!!.points.map { it.sessionId })
        assertEquals(ProgressDelta.Kg(2.5), state.summary!!.delta)
        assertEquals(70.0, state.summary!!.best!!.weight, 0.0)
    }

    @Test
    fun `смена периода пересчитывает точки и снимает выбор, ушедший из периода`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectPoint(2L)
        assertEquals(2L, vm.state.value.selectedSessionId)

        vm.selectPeriod(ProgressPeriod.MONTH)

        assertEquals(ProgressPeriod.MONTH, vm.state.value.period)
        assertEquals(listOf(3L), vm.state.value.summary!!.points.map { it.sessionId })
        assertNull(vm.state.value.selectedSessionId)
    }

    @Test
    fun `выбор остаётся, если точка есть и в новом периоде`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectPoint(3L)

        vm.selectPeriod(ProgressPeriod.ALL)

        assertEquals(3L, vm.state.value.selectedSessionId)
        assertEquals(3, vm.state.value.summary!!.points.size)
    }

    @Test
    fun `повторный тап по точке и тап мимо снимают выбор`() = runTest(dispatcher) {
        val vm = loaded()

        vm.selectPoint(3L)
        vm.selectPoint(3L)
        assertNull(vm.state.value.selectedSessionId)

        vm.selectPoint(2L)
        vm.selectPoint(null)
        assertNull(vm.state.value.selectedSessionId)
    }

    @Test
    fun `несуществующая запись — загружено, упражнения нет`() = runTest(dispatcher) {
        val state = loaded(flowOf(null)).state.value

        assertTrue(state.isLoaded)
        assertFalse(state.loadFailed)
        assertNull(state.exercise)
        assertNull(state.summary)
    }

    @Test
    fun `сбой чтения — состояние ошибки, а не падение`() = runTest(dispatcher) {
        val state = loaded(flow { throw IllegalStateException("база недоступна") }).state.value

        assertTrue(state.isLoaded)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `повторный load того же упражнения не подписывается второй раз`() = runTest(dispatcher) {
        val observe = useCase(flowOf(ExerciseHistory(squat, sessions)))
        val vm = ExerciseProgressViewModel(observe)

        vm.load(1L)
        vm.load(1L)
        dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { observe(1L) }
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseProgressViewModelTest*'`
Expected: FAIL — компиляция: `Unresolved reference 'ExerciseProgressViewModel'`.

- [ ] **Step 3: Реализация**

`ExerciseProgressViewModel.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseHistory
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.ProgressSummary
import ru.hopes.workouttimer.domain.model.summarizeProgress
import ru.hopes.workouttimer.domain.usecase.ObserveExerciseHistoryUseCase
import java.util.TimeZone
import javax.inject.Inject

private const val TAG = "ExerciseProgressVM"

@HiltViewModel
class ExerciseProgressViewModel @Inject constructor(
    private val observeExerciseHistoryUseCase: ObserveExerciseHistoryUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseProgressState())
    val state = _state.asStateFlow()

    private var loadedId: Long? = null

    fun load(catalogId: Long) {
        // LaunchedEffect перезапускается после поворота экрана, ViewModel — нет:
        // второй подписчик на ту же запись только удвоил бы пересчёты.
        if (loadedId == catalogId) return
        loadedId = catalogId
        observeExerciseHistoryUseCase(catalogId)
            .onEach { history -> _state.update { it.withHistory(history) } }
            .catch { e ->
                Log.e(TAG, "Прогресс не прочитан", e)
                _state.update { it.copy(isLoaded = true, loadFailed = true) }
            }
            .launchIn(viewModelScope)
    }

    fun selectPeriod(period: ProgressPeriod) {
        _state.update { it.copy(period = period).recomputed() }
    }

    /** Тап по уже выбранной точке и тап мимо точек (null) снимают выбор. */
    fun selectPoint(sessionId: Long?) {
        _state.update { it.copy(selectedSessionId = if (sessionId == it.selectedSessionId) null else sessionId) }
    }
}

data class ExerciseProgressState(
    val isLoaded: Boolean = false,
    val loadFailed: Boolean = false,
    val exercise: CatalogExercise? = null,
    val sessions: List<ProgressSession> = emptyList(),
    val period: ProgressPeriod = ProgressPeriod.QUARTER,
    val summary: ProgressSummary? = null,
    val selectedSessionId: Long? = null
)

private fun ExerciseProgressState.withHistory(history: ExerciseHistory?): ExerciseProgressState =
    copy(isLoaded = true, exercise = history?.exercise, sessions = history?.sessions.orEmpty()).recomputed()

/**
 * Сводка пересчитывается при новых данных и смене периода. «Сейчас» берётся в
 * момент пересчёта: «1 мес» отсчитывается от текущей даты. Выбор точки, которой
 * в новом периоде нет, снимается — иначе подпись висела бы от невидимой точки.
 */
private fun ExerciseProgressState.recomputed(): ExerciseProgressState {
    val unit = exercise?.unit ?: return copy(summary = null, selectedSessionId = null)
    val summary = summarizeProgress(sessions, unit, period, System.currentTimeMillis(), TimeZone.getDefault())
    val selected = selectedSessionId?.takeIf { id -> summary.points.any { it.sessionId == id } }
    return copy(summary = summary, selectedSessionId = selected)
}
```

- [ ] **Step 4: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseProgressViewModelTest*'`
Expected: PASS, 7 тестов.

- [ ] **Step 5: Полный прогон и коммит**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressViewModel.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat: ViewModel экрана прогресса — период и выбор точки

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: График прогресса на Canvas

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ProgressChart.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/progress/ProgressChartTest.kt`

**Interfaces:**
- Consumes: `ProgressPoint` (Task 1); `yAxisFor`, `PlotArea`, `pointPositions`, `tickY`, `nearestPointIndex`, `shortDate` (Task 3); строки `progress_short_date`, `progress_months`, `progress_chart_description` (Task 3).
- Produces:
  - `const val PROGRESS_CHART_TAG = "progress_chart"`
  - `@Composable internal fun ProgressChart(points: List<ProgressPoint>, selectedSessionId: Long?, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier)` — рисует при `points.size >= 2`; тап отдаёт `sessionId` ближайшей по X точки в зоне 24dp либо `null`

Оформление (dataviz-правила для одной серии): одна линия 2dp `primary` со скруглёнными стыками, точки диаметром 8dp (выбранная — 12dp) с кольцом 2dp цвета подложки `surface`, сетка — сплошные волоски 1dp `outline` по делениям, подписи делений и дат — `bodySmall` (табличные цифры) цветом `onSurfaceVariant`, легенды нет (линию называет шапка экрана), одна ось Y. У выбранной точки — вертикальный волосок `onSurfaceVariant`. Высота 200dp включает полосу дат под осью — подписи не обрезаются.

- [ ] **Step 1: Падающий Compose-тест**

`ProgressChartTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.percentOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@RunWith(AndroidJUnit4::class)
class ProgressChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day = 86_400_000L

    private fun point(sessionId: Long, finishedAt: Long, weight: Double) = ProgressPoint(
        sessionId, finishedAt, SessionSet(sessionId, sessionId, 1L, weight, 0.0, 8, ExerciseUnit.KG), weight
    )

    // Дни 0, 1 и 100: середина оси X далеко от всех точек, правый край — у последней.
    private val points = listOf(point(1, 0, 55.0), point(2, day, 57.5), point(3, 100 * day, 60.0))

    private fun show(onSelect: (Long?) -> Unit) = composeRule.setContent {
        WorkoutTimerTheme { ProgressChart(points = points, selectedSessionId = null, onSelect = onSelect) }
    }

    @Test
    fun тап_у_правого_края_выбирает_последнюю_точку() {
        var picked: Long? = null
        show { picked = it }

        // Зона попадания шире точки: палец в нескольких dp от кружка всё равно его выбирает.
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(percentOffset(0.98f, 0.5f)) }

        composeRule.runOnIdle { assertEquals(3L, picked) }
    }

    @Test
    fun тап_вдали_от_точек_снимает_выбор() {
        var picked: Long? = -1L
        show { picked = it }

        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(percentOffset(0.5f, 0.5f)) }

        composeRule.runOnIdle { assertNull(picked) }
    }

    @Test
    fun график_описан_для_TalkBack_числом_тренировок() {
        show {}

        composeRule.onNodeWithContentDescription("График лучшего подхода: 3 тренировки").assertExists()
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `Unresolved reference 'ProgressChart'`, `'PROGRESS_CHART_TAG'`.

- [ ] **Step 3: Реализация**

`ProgressChart.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import java.util.TimeZone

const val PROGRESS_CHART_TAG = "progress_chart"

// Высота включает полосу дат под осью: подписи не обрезаются и не дают вложенной прокрутки.
private val ChartHeight = 200.dp
// Поля сверху и справа вмещают выбранную точку с кольцом (6 + 2 dp).
private val PlotTopPadding = 12.dp
private val PlotEndPadding = 12.dp
private val XAxisBand = 28.dp
private val DateLabelGap = 10.dp
private val AxisLabelGap = 8.dp
private val LineWidth = 2.dp
private val GridWidth = 1.dp
private val MarkerRadius = 4.dp
private val SelectedMarkerRadius = 6.dp
private val RingWidth = 2.dp
// Зона попадания тапа по X — с запасом шире кружка 8dp.
private val HitRadius = 24.dp

/**
 * Линия лучшего подхода по сессиям. Вся математика — в ChartGeometry.kt;
 * здесь только рисование и передача тапа. Подпись выбранной точки рисует
 * не Canvas, а карточка над ним — её текст читает TalkBack и находят тесты.
 */
@Composable
internal fun ProgressChart(
    points: List<ProgressPoint>,
    selectedSessionId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    // Кольцо цвета подложки: точка читается поверх линии и соседних точек.
    val ringColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Текст — цветом текста, никогда цветом линии.
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val months = stringArrayResource(R.array.progress_months).toList()
    val dateTemplate = stringResource(R.string.progress_short_date)
    val zone = remember { TimeZone.getDefault() }
    val description = pluralStringResource(R.plurals.progress_chart_description, points.size, points.size)
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)

    val axis = remember(points) { yAxisFor(points.map { it.level }) }
    val tickLabels = remember(axis, labelStyle) {
        axis.ticks.map { textMeasurer.measure(it.toString(), labelStyle) }
    }
    val firstDateText = shortDate(points.first().finishedAt, dateTemplate, months, zone)
    val lastDateText = shortDate(points.last().finishedAt, dateTemplate, months, zone)
    val firstDate = remember(firstDateText, labelStyle) { textMeasurer.measure(firstDateText, labelStyle) }
    val lastDate = remember(lastDateText, labelStyle) { textMeasurer.measure(lastDateText, labelStyle) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val plot = with(density) {
        PlotArea(
            left = tickLabels.maxOf { it.size.width } + AxisLabelGap.toPx(),
            top = PlotTopPadding.toPx(),
            right = canvasSize.width - PlotEndPadding.toPx(),
            bottom = canvasSize.height - XAxisBand.toPx()
        )
    }
    val positions = remember(points, axis, plot) { pointPositions(points, axis, plot) }
    val hitRadius = with(density) { HitRadius.toPx() }
    val selectedIndex = points.indexOfFirst { it.sessionId == selectedSessionId }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .onSizeChanged { canvasSize = it }
            .pointerInput(points, positions) {
                detectTapGestures { tap ->
                    val index = nearestPointIndex(positions.map { it.x }, tap.x, hitRadius)
                    currentOnSelect(index?.let { points[it].sessionId })
                }
            }
            .semantics { contentDescription = description }
            .testTag(PROGRESS_CHART_TAG)
    ) {
        if (canvasSize == IntSize.Zero) return@Canvas
        val hairline = GridWidth.toPx()

        axis.ticks.forEachIndexed { i, tick ->
            val y = tickY(tick, axis, plot)
            drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = hairline)
            val label = tickLabels[i]
            drawText(
                label,
                topLeft = Offset(plot.left - AxisLabelGap.toPx() - label.size.width, y - label.size.height / 2f)
            )
        }

        val dateTop = plot.bottom + DateLabelGap.toPx()
        drawText(firstDate, topLeft = Offset(plot.left, dateTop))
        // Все точки в один день — одна подпись, а не две наложенные.
        if (lastDateText != firstDateText) {
            drawText(lastDate, topLeft = Offset(plot.right - lastDate.size.width, dateTop))
        }

        if (selectedIndex >= 0) {
            val x = positions[selectedIndex].x
            drawLine(crosshairColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = hairline)
        }

        val line = Path().apply {
            positions.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(
            line,
            lineColor,
            style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        positions.forEachIndexed { i, p ->
            val radius = (if (i == selectedIndex) SelectedMarkerRadius else MarkerRadius).toPx()
            val center = Offset(p.x, p.y)
            drawCircle(ringColor, radius = radius + RingWidth.toPx(), center = center)
            drawCircle(lineColor, radius = radius, center = center)
        }
    }
}

@Preview(backgroundColor = 0xFF16161C, showBackground = true)
@Composable
private fun ProgressChartPreview() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    fun point(id: Long, daysAgo: Int, weight: Double) = ProgressPoint(
        id, now - daysAgo * day, SessionSet(id, id, 1L, weight, 0.0, 8, ExerciseUnit.KG), weight
    )
    WorkoutTimerTheme {
        ProgressChart(
            points = listOf(point(1, 80, 55.0), point(2, 45, 57.5), point(3, 20, 60.0), point(4, 5, 62.5)),
            selectedSessionId = 3L,
            onSelect = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}
```

- [ ] **Step 4: Запустить на эмуляторе — проходит**

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.progress.ProgressChartTest`
Expected: PASS, 3 теста.

- [ ] **Step 5: Полный прогон и коммит**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ProgressChart.kt \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/progress/ProgressChartTest.kt
git commit -m "$(cat <<'EOF'
feat: график прогресса на Canvas с выбором точки

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Экран прогресса упражнения

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressScreen.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressContentTest.kt`

**Interfaces:**
- Consumes: `ExerciseProgressViewModel`, `ExerciseProgressState` (Task 4); `ProgressChart`, `PROGRESS_CHART_TAG` (Task 5); `progressSetText`, `formatDelta`, `deltaFormat`, `periodName`, `periodSpan`, `shortDate` (Task 3); `setFormat()`, `formatSetList`, `unitName`, `DateFormatter.formatSessionDateTime` (`presentation/utils`); `EmptyState`, `SectionHeader`.
- Produces:
  - `@Composable fun ExerciseProgressScreen(catalogId: Long, onNavigateBack: () -> Unit, viewModel: ExerciseProgressViewModel = hiltViewModel())`
  - `@Composable internal fun ProgressContent(state: ExerciseProgressState, onPeriodChange: (ProgressPeriod) -> Unit, onSelectPoint: (Long?) -> Unit)`
  - `const val PROGRESS_HERO_TAG = "progress_hero"`, `const val PROGRESS_LIST_TAG = "progress_list"`

Порядок сверху вниз — по спеке: шапка (название, справа единица) → главная цифра с изменением → карточка графика (подпись выбранной точки или подсказка, затем график; при < 2 точках — строка «График появится…») → пометка о других единицах → период → «Тренировки» и строки сессий, новые сверху.

- [ ] **Step 1: Падающие Compose-тесты**

`ExerciseProgressContentTest.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.and
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.summarizeProgress
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import java.util.Calendar
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class ExerciseProgressContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    private fun kg(id: Long, weight: Double, reps: Int) = SessionSet(id, id, 1L, weight, 0.0, reps, ExerciseUnit.KG)

    private fun plate(id: Long, plate: Double, extra: Double, reps: Int) =
        SessionSet(id, id, 1L, plate, extra, reps, ExerciseUnit.PLATE)

    private fun bodyweight(id: Long, reps: Int) = SessionSet(id, id, 1L, 0.0, 0.0, reps, ExerciseUnit.BODYWEIGHT)

    private fun session(daysAgo: Int, set: SessionSet) = ProgressSession(set.sessionId, now - daysAgo * day, listOf(set))

    private fun localNoon(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, dayOfMonth, 12, 0)
        }.timeInMillis

    private fun stateOf(
        unit: ExerciseUnit,
        sessions: List<ProgressSession>,
        period: ProgressPeriod = ProgressPeriod.QUARTER,
        selected: Long? = null
    ) = ExerciseProgressState(
        isLoaded = true,
        exercise = CatalogExercise(1L, "Присед", unit),
        sessions = sessions,
        period = period,
        summary = summarizeProgress(sessions, unit, period, now, TimeZone.getDefault()),
        selectedSessionId = selected
    )

    private fun show(state: ExerciseProgressState) = composeRule.setContent {
        WorkoutTimerTheme { ProgressContent(state = state, onPeriodChange = {}, onSelectPoint = {}) }
    }

    @Test
    fun главная_цифра_и_изменение_за_период_в_кг() {
        show(stateOf(ExerciseUnit.KG, listOf(session(60, kg(1, 60.0, 8)), session(10, kg(2, 62.5, 6)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("62.5 кг × 6")
        composeRule.onNodeWithText("+2.5 кг за 3 мес").assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Нажмите на точку, чтобы увидеть подход").assertIsDisplayed()
    }

    @Test
    fun изменение_плиты_склоняется_по_числу() {
        show(stateOf(ExerciseUnit.PLATE, listOf(session(60, plate(1, 5.0, 2.0, 12)), session(10, plate(2, 6.0, 0.0, 10)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("плита 6 × 10")
        composeRule.onNodeWithText("+1 плита за 3 мес").assertIsDisplayed()
    }

    @Test
    fun без_веса_главная_цифра_в_повторениях() {
        show(stateOf(ExerciseUnit.BODYWEIGHT, listOf(session(60, bodyweight(1, 8)), session(10, bodyweight(2, 10)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("10 повт.")
        composeRule.onNodeWithText("+2 повт за 3 мес").assertIsDisplayed()
    }

    @Test
    fun без_сессий_пустое_состояние() {
        show(stateOf(ExerciseUnit.KG, emptyList()))

        composeRule.onNodeWithText("Сделайте упражнение — здесь появится прогресс").assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertDoesNotExist()
    }

    @Test
    fun одна_сессия_главная_цифра_и_список_без_графика() {
        show(stateOf(ExerciseUnit.KG, listOf(session(10, kg(1, 60.0, 8)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("60 кг × 8")
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("График появится, когда за период будет две тренировки").assertIsDisplayed()
        composeRule.onNodeWithText("за 3 мес", substring = true).assertDoesNotExist()
    }

    @Test
    fun переключение_периода_пересчитывает_график_и_изменение() {
        val sessions = listOf(session(60, kg(1, 60.0, 8)), session(10, kg(2, 62.5, 6)))
        var period by mutableStateOf(ProgressPeriod.QUARTER)
        composeRule.setContent {
            WorkoutTimerTheme {
                ProgressContent(state = stateOf(ExerciseUnit.KG, sessions, period), onPeriodChange = { period = it }, onSelectPoint = {})
            }
        }
        composeRule.onNodeWithText("+2.5 кг за 3 мес").assertIsDisplayed()

        composeRule.onNodeWithText("1 мес").performClick()

        composeRule.onNodeWithText("1 мес").assertIsSelected()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("График появится, когда за период будет две тренировки").assertIsDisplayed()
        composeRule.onNodeWithText("+2.5 кг за 3 мес").assertDoesNotExist()
    }

    @Test
    fun подходы_в_других_единицах_отмечены_и_видны_в_списке_со_своей_подписью() {
        show(
            stateOf(
                ExerciseUnit.PLATE,
                listOf(session(80, kg(1, 40.0, 12)), session(60, plate(2, 4.0, 0.0, 12)), session(10, plate(3, 5.0, 2.0, 12)))
            )
        )

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertTextEquals("плита 5 +2 кг × 12")
        composeRule.onNodeWithText("Подходы в других единицах — в списке ниже").assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_LIST_TAG).performScrollToNode(hasText("40 кг × 12"))
        composeRule.onNodeWithText("40 кг × 12").assertIsDisplayed()
    }

    @Test
    fun только_другие_единицы_нет_цифры_и_графика_но_есть_список() {
        show(stateOf(ExerciseUnit.PLATE, listOf(session(5, kg(1, 40.0, 12)))))

        composeRule.onNodeWithTag(PROGRESS_HERO_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Сделайте упражнение — здесь появится прогресс").assertDoesNotExist()
        composeRule.onNodeWithText("Подходы в других единицах — в списке ниже").assertIsDisplayed()
        composeRule.onNodeWithText("40 кг × 12").assertIsDisplayed()
    }

    @Test
    fun тап_по_точке_показывает_подпись_и_подсвечивает_строку_сессии() {
        val sessions = listOf(
            ProgressSession(1L, localNoon(2026, 9, 20), listOf(kg(1, 55.0, 8))),
            ProgressSession(2L, localNoon(2026, 9, 26), listOf(kg(2, 60.0, 8)))
        )
        var selected by mutableStateOf<Long?>(null)
        composeRule.setContent {
            WorkoutTimerTheme {
                ProgressContent(
                    state = stateOf(ExerciseUnit.KG, sessions, ProgressPeriod.ALL, selected),
                    onPeriodChange = {},
                    onSelectPoint = { selected = if (it == selected) null else it }
                )
            }
        }
        val sessionRow = { text: String -> hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) }

        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(percentOffset(0.98f, 0.5f)) }

        composeRule.onNodeWithText("26 сен · 60 кг × 8").assertIsDisplayed()
        composeRule.onNode(sessionRow("60 кг × 8")).assertIsSelected()
        composeRule.onNode(sessionRow("55 кг × 8")).assertIsNotSelected()
    }

    @Test
    fun неизвестное_упражнение_не_найдено() {
        show(ExerciseProgressState(isLoaded = true))

        composeRule.onNodeWithText("Упражнение не найдено").assertIsDisplayed()
    }

    @Test
    fun сбой_чтения_показан_вместо_экрана() {
        show(ExerciseProgressState(isLoaded = true, loadFailed = true))

        composeRule.onNodeWithText("Не удалось загрузить прогресс").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `Unresolved reference 'ProgressContent'`, `'PROGRESS_HERO_TAG'`, `'PROGRESS_LIST_TAG'`.

- [ ] **Step 3: Экран**

`ExerciseProgressScreen.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.summarizeProgress
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.SectionSpacing
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.formatSetList
import ru.hopes.workouttimer.presentation.utils.setFormat
import ru.hopes.workouttimer.presentation.utils.unitName
import java.util.TimeZone

const val PROGRESS_HERO_TAG = "progress_hero"
const val PROGRESS_LIST_TAG = "progress_list"

@Composable
fun ExerciseProgressScreen(
    catalogId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ExerciseProgressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(catalogId) {
        viewModel.load(catalogId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            ProgressHeader(exercise = state.exercise, onNavigateBack = onNavigateBack)
            ProgressContent(
                state = state,
                onPeriodChange = viewModel::selectPeriod,
                onSelectPoint = viewModel::selectPoint
            )
        }
    }
}

/** Шапка: название упражнения, справа — его текущая единица. */
@Composable
private fun ProgressHeader(exercise: CatalogExercise?, onNavigateBack: () -> Unit) {
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
            text = exercise?.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (exercise != null) {
            Text(
                text = unitName(exercise.unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Тело экрана под шапкой. Вынесено из [ExerciseProgressScreen], чтобы
 * превьюшить и тестировать без `hiltViewModel()`. Список сессий — табличная
 * замена графика для TalkBack: каждое значение графика есть и в нём.
 */
@Composable
internal fun ProgressContent(
    state: ExerciseProgressState,
    onPeriodChange: (ProgressPeriod) -> Unit,
    onSelectPoint: (Long?) -> Unit
) {
    // До первого ответа базы пустое состояние не показываем — оно мигнуло бы.
    if (!state.isLoaded) return
    if (state.loadFailed) {
        EmptyState(icon = Icons.Default.Timeline, title = stringResource(R.string.progress_error_load))
        return
    }
    if (state.exercise == null) {
        EmptyState(icon = Icons.Default.Timeline, title = stringResource(R.string.progress_missing_title))
        return
    }
    val summary = state.summary ?: return
    if (state.sessions.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Timeline,
            title = stringResource(R.string.progress_empty_title),
            subtitle = stringResource(R.string.progress_empty_subtitle)
        )
        return
    }
    val format = setFormat()
    val repsTemplate = stringResource(R.string.progress_reps)
    val newestFirst = remember(state.sessions) { state.sessions.asReversed() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PROGRESS_LIST_TAG),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(CardSpacing)
    ) {
        summary.best?.let { best ->
            item(key = "hero") {
                ProgressHero(best = best, delta = summary.delta, period = state.period, format = format, repsTemplate = repsTemplate)
            }
        }
        // Нет ни одной точки в текущей единице — нечего ни рисовать, ни фильтровать.
        if (summary.hasPoints) {
            item(key = "chart") {
                ChartCard(
                    points = summary.points,
                    selectedSessionId = state.selectedSessionId,
                    format = format,
                    repsTemplate = repsTemplate,
                    onSelectPoint = onSelectPoint
                )
            }
        }
        if (summary.hasOtherUnits) {
            item(key = "other_units") {
                Text(
                    text = stringResource(R.string.progress_other_units),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (summary.hasPoints) {
            item(key = "period") {
                PeriodSelector(current = state.period, onChange = onPeriodChange)
            }
        }
        item(key = "sessions_title") {
            SectionHeader(
                text = stringResource(R.string.progress_sessions_title),
                modifier = Modifier.padding(top = SectionSpacing - CardSpacing)
            )
        }
        items(newestFirst, key = { it.sessionId }) { session ->
            SessionRow(session = session, isSelected = session.sessionId == state.selectedSessionId, format = format)
        }
    }
}

/**
 * Главная цифра — лучший подход за всё время, под ней изменение за период.
 * Изменение — текстовым цветом: рост веса не всегда «хорошо», а падение не «плохо».
 */
@Composable
private fun ProgressHero(
    best: SessionSet,
    delta: ProgressDelta?,
    period: ProgressPeriod,
    format: SetFormat,
    repsTemplate: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        SectionHeader(text = stringResource(R.string.progress_best_label))
        Text(
            text = progressSetText(best, format, repsTemplate),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = 6.dp)
                .testTag(PROGRESS_HERO_TAG)
        )
        if (delta != null) {
            Text(
                text = formatDelta(delta, periodSpan(period), deltaFormat()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Карточка графика: подпись выбранной точки над линией; меньше двух точек — объяснение вместо линии. */
@Composable
private fun ChartCard(
    points: List<ProgressPoint>,
    selectedSessionId: Long?,
    format: SetFormat,
    repsTemplate: String,
    onSelectPoint: (Long?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        if (points.size < 2) {
            Text(
                text = stringResource(R.string.progress_chart_few),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val selected = points.firstOrNull { it.sessionId == selectedSessionId }
            val months = stringArrayResource(R.array.progress_months).toList()
            val dateTemplate = stringResource(R.string.progress_short_date)
            // Строка держит высоту и без выбора — график не прыгает при первом тапе.
            Text(
                text = if (selected != null) {
                    stringResource(
                        R.string.progress_point_label,
                        shortDate(selected.finishedAt, dateTemplate, months, TimeZone.getDefault()),
                        progressSetText(selected.best, format, repsTemplate)
                    )
                } else {
                    stringResource(R.string.progress_chart_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            ProgressChart(
                points = points,
                selectedSessionId = selectedSessionId,
                onSelect = onSelectPoint,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Период: одна строка, три равные кнопки; выбранная — подложкой, а не цветом линии. */
@Composable
private fun PeriodSelector(current: ProgressPeriod, onChange: (ProgressPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProgressPeriod.entries.forEach { period ->
            val selected = period == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .selectable(selected = selected, onClick = { onChange(period) }, role = Role.RadioButton),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = periodName(period),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Строка сессии: дата и все подходы со своими подписями. Выбранная точка подсвечивает её рамкой. */
@Composable
private fun SessionRow(session: ProgressSession, isSelected: Boolean, format: SetFormat) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape
            )
            .semantics(mergeDescendants = true) { selected = isSelected }
            .padding(14.dp)
    ) {
        Text(
            text = DateFormatter.formatSessionDateTime(session.finishedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatSetList(session.sets, format),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true, heightDp = 900)
@Composable
private fun ProgressContentPreview() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    fun session(id: Long, daysAgo: Int, weight: Double, reps: Int) = ProgressSession(
        id, now - daysAgo * day, listOf(SessionSet(id, id, 1L, weight, 0.0, reps, ExerciseUnit.KG))
    )
    val sessions = listOf(session(1, 80, 55.0, 8), session(2, 45, 57.5, 8), session(3, 20, 60.0, 8), session(4, 5, 62.5, 6))
    WorkoutTimerTheme {
        ProgressContent(
            state = ExerciseProgressState(
                isLoaded = true,
                exercise = CatalogExercise(1L, "Присед", ExerciseUnit.KG),
                sessions = sessions,
                summary = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.QUARTER, now, TimeZone.getDefault()),
                selectedSessionId = 3L
            ),
            onPeriodChange = {},
            onSelectPoint = {}
        )
    }
}
```

- [ ] **Step 4: Запустить на эмуляторе — проходит**

Run (после проверки `adb devices`): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.screen.progress.ExerciseProgressContentTest`
Expected: PASS, 11 тестов. Если `тап_по_точке…` не находит «26 сен · 60 кг × 8» — сначала проверить, что тап попал в Canvas (`printToLog` дерева), а не подгонять процент: зона попадания 24dp, последняя точка — в 12dp от правого края.

- [ ] **Step 5: Полный прогон и коммит**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressScreen.kt \
        app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/progress/ExerciseProgressContentTest.kt
git commit -m "$(cat <<'EOF'
feat: экран прогресса упражнения — главная цифра, график, период, сессии

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Маршрут прогресса и входы из «Упражнений» и истории

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt` (`Screen.ExerciseProgress`, блоки `History` и `Exercises`, новый `composable`)
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/exercises/ExerciseCatalogScreen.kt` (`ExerciseCatalogScreen`, `CatalogContent`, `CatalogRow`, превью)
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryScreen.kt` (`WorkoutHistoryScreen`, `HistoryContent`, `SessionCard`, превью)
- Modify: `app/src/main/res/values/strings.xml` (`catalog_open_progress`, `history_open_progress`)
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/navigation/ScreenDeepLinkTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/navigation/ExerciseProgressRouteTest.kt` (новый)
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/exercises/ExerciseCatalogContentTest.kt`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/HistoryContentTest.kt`

**Interfaces:**
- Consumes: `ExerciseProgressScreen(catalogId: Long, onNavigateBack: () -> Unit, viewModel = hiltViewModel())` (Task 6); `SessionExerciseSets.catalogId`, `CatalogSummary.exercise` (E2).
- Produces:
  - `Screen.ExerciseProgress` — `route = "exercise_progress/{catalogId}"`, `val arguments: List<NamedNavArgument>` (`NavType.LongType`), `fun createRoute(catalogId: Long): String`, `fun getCatalogId(arguments: Bundle?): Long`
  - `ExerciseCatalogScreen(viewModel = hiltViewModel(), onNavigateBack: () -> Unit, onOpenProgress: (CatalogExercise) -> Unit)`; `CatalogContent(state: ExerciseCatalogState, onOpen: (CatalogExercise) -> Unit, onMenu: (CatalogExercise) -> Unit)`
  - `WorkoutHistoryScreen(viewModel = hiltViewModel(), workoutId: Int, onNavigateBack: () -> Unit, onExerciseClick: (Long) -> Unit)`; `HistoryContent(sessions, setsBySession, onExerciseClick: (Long) -> Unit)`
  - Строки `catalog_open_progress`, `history_open_progress`

- [ ] **Step 1: Падающие тесты маршрута**

`ScreenDeepLinkTest.kt` — заменить комментарий над тестом `exercises screen has its own route` на `// Маршруты из спеки: экран «Упражнения» (E2) и прогресс упражнения (E3).` и добавить тест в конец класса:

```kotlin

    @Test
    fun `маршрут прогресса строится из id записи справочника`() {
        assertEquals("exercise_progress/{catalogId}", Screen.ExerciseProgress.route)
        assertEquals("exercise_progress/7", Screen.ExerciseProgress.createRoute(7L))
    }
```

`ExerciseProgressRouteTest.kt` (androidTest) — значение больше `Int.MAX_VALUE` ловит аргумент, объявленный как `IntType`: `getLong` по нему вернул бы 0:

```kotlin
package ru.hopes.workouttimer.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseProgressRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun маршрут_прогресса_передаёт_id_записи_как_long() {
        lateinit var nav: NavHostController
        composeRule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Text("Список") }
                composable(
                    route = Screen.ExerciseProgress.route,
                    arguments = Screen.ExerciseProgress.arguments
                ) { entry ->
                    Text("id ${Screen.ExerciseProgress.getCatalogId(entry.arguments)}")
                }
            }
        }

        composeRule.runOnIdle { nav.navigate(Screen.ExerciseProgress.createRoute(4_000_000_000L)) }

        composeRule.onNodeWithText("id 4000000000").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Падающие тесты входов**

`ExerciseCatalogContentTest.kt` — добавить импорт `org.junit.Assert.assertNull`; помощник `showRows` и тест меню заменить, новый тест добавить:

```kotlin
    private fun showRows(
        vararg rows: CatalogSummary,
        onOpen: (CatalogExercise) -> Unit = {},
        onMenu: (CatalogExercise) -> Unit = {}
    ) = composeRule.setContent {
        WorkoutTimerTheme {
            CatalogContent(
                state = ExerciseCatalogState(rows = rows.toList(), isLoaded = true),
                onOpen = onOpen,
                onMenu = onMenu
            )
        }
    }
```

```kotlin
    @Test
    fun меню_строки_открывается_кнопкой_и_не_открывает_прогресс() {
        var menuFor: CatalogExercise? = null
        var opened: CatalogExercise? = null
        showRows(CatalogSummary(squat, null, null), onOpen = { opened = it }, onMenu = { menuFor = it })

        composeRule.onNodeWithContentDescription("Действия с упражнением").performClick()

        assertEquals(squat, menuFor)
        assertNull(opened)
    }

    @Test
    fun тап_по_строке_открывает_прогресс_упражнения() {
        var opened: CatalogExercise? = null
        showRows(CatalogSummary(squat, null, null), onOpen = { opened = it })

        composeRule.onNodeWithText("Присед").performClick()

        assertEquals(squat, opened)
    }
```

(Старый тест `меню_строки_открывается_кнопкой` удаляется — его заменяет первый из двух.)

`HistoryContentTest.kt` — добавить импорт `org.junit.Assert.assertEquals`; помощник `show` заменить, тест добавить:

```kotlin
    private fun show(
        setsBySession: Map<Long, List<SessionExerciseSets>>?,
        onExerciseClick: (Long) -> Unit = {}
    ) = composeRule.setContent {
        WorkoutTimerTheme {
            HistoryContent(sessions = listOf(session), setsBySession = setsBySession, onExerciseClick = onExerciseClick)
        }
    }

    @Test
    fun тап_по_упражнению_открывает_его_прогресс_и_не_сворачивает_карточку() {
        var opened: Long? = null
        show(mapOf(1L to listOf(SessionExerciseSets(7L, "Присед", listOf(set(1, 60.0, 8))))), onExerciseClick = { opened = it })
        composeRule.onNodeWithText(DateFormatter.formatSessionDateTime(session.finishedAt)).performClick()

        composeRule.onNodeWithText("Присед — 60 кг × 8").performClick()

        assertEquals(7L, opened)
        composeRule.onNodeWithText("Присед — 60 кг × 8").assertIsDisplayed()
    }
```

- [ ] **Step 3: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ScreenDeepLinkTest*'`
Expected: FAIL — компиляция: `Unresolved reference 'ExerciseProgress'`.

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `No parameter with name 'onOpen'`, `No parameter with name 'onExerciseClick'`.

- [ ] **Step 4: Строки**

`strings.xml` — в секции «История» после `history_state_collapsed`:

```xml
    <string name="history_open_progress">Открыть прогресс</string>
```

В секции «Справочник упражнений» после `catalog_actions`:

```xml
    <string name="catalog_open_progress">Открыть прогресс</string>
```

- [ ] **Step 5: Маршрут**

`NavGraph.kt` — импорты:

```kotlin
import androidx.navigation.NamedNavArgument
import ru.hopes.workouttimer.presentation.screen.progress.ExerciseProgressScreen
```

В `sealed class Screen` после `data object History`:

```kotlin

    data object ExerciseProgress : Screen("exercise_progress/{catalogId}") {
        private const val CATALOG_ID = "catalogId"

        // Long, а не Int: id справочника — Long, и getLong по аргументу IntType вернул бы 0.
        // Геттер, а не поле: JVM-тест маршрута не должен собирать аргументы навигации.
        val arguments: List<NamedNavArgument>
            get() = listOf(navArgument(CATALOG_ID) { type = NavType.LongType })

        fun createRoute(catalogId: Long): String = "exercise_progress/$catalogId"

        fun getCatalogId(arguments: Bundle?): Long = arguments?.getLong(CATALOG_ID) ?: 0L
    }
```

Блок истории — заменить вызов `WorkoutHistoryScreen(...)`:

```kotlin
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
```

Блок «Упражнений» — заменить целиком и добавить экран прогресса после него:

```kotlin
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
```

- [ ] **Step 6: Строка экрана «Упражнения» нажимается**

`ExerciseCatalogScreen.kt`:

1. Импорт `androidx.compose.foundation.clickable`.
2. Сигнатура экрана:

```kotlin
@Composable
fun ExerciseCatalogScreen(
    viewModel: ExerciseCatalogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenProgress: (CatalogExercise) -> Unit
) {
```

3. Вызов в конце `Scaffold`: `CatalogContent(state = state, onOpen = onOpenProgress, onMenu = { menuFor = it })`.
4. `CatalogContent` — сигнатура и строка списка:

```kotlin
@Composable
internal fun CatalogContent(
    state: ExerciseCatalogState,
    onOpen: (CatalogExercise) -> Unit,
    onMenu: (CatalogExercise) -> Unit
) {
```

```kotlin
        items(state.rows, key = { it.exercise.id }) { summary ->
            CatalogRow(
                summary = summary,
                format = format,
                onOpen = { onOpen(summary.exercise) },
                onMenu = { onMenu(summary.exercise) }
            )
        }
```

5. `CatalogRow` целиком:

```kotlin
/** Строка справочника: тап открывает прогресс упражнения, ⋮ — меню переименования и единицы. */
@Composable
private fun CatalogRow(
    summary: CatalogSummary,
    format: SetFormat,
    onOpen: () -> Unit,
    onMenu: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.catalog_open_progress),
                onClick = onOpen
            )
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
```

6. В `CatalogContentPreview` вызов: `onOpen = {},` перед `onMenu = {}`.

- [ ] **Step 7: Упражнение в развёрнутой сессии истории нажимается**

`WorkoutHistoryScreen.kt`:

1. Импорты:

```kotlin
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
```

2. `WorkoutHistoryScreen` — параметр `onExerciseClick: (Long) -> Unit` после `onNavigateBack`; вызов тела: `HistoryContent(sessions = state.sessions, setsBySession = state.setsBySession, onExerciseClick = onExerciseClick)`.
3. `HistoryContent` — параметр `onExerciseClick: (Long) -> Unit` последним; вызов карточки:

```kotlin
                SessionCard(
                    session,
                    setsBySession?.get(session.id.toLong()).orEmpty(),
                    loaded = setsBySession != null,
                    onExerciseClick = onExerciseClick
                )
```

4. `SessionCard` — сигнатура `private fun SessionCard(session: WorkoutSession, exercises: List<SessionExerciseSets>, loaded: Boolean, onExerciseClick: (Long) -> Unit)`; ветку `else if (expanded)` заменить:

```kotlin
        } else if (expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                // Свой clickable внутри карточки забирает тап: карточка при этом не сворачивается.
                exercises.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.history_open_progress)
                            ) { onExerciseClick(group.catalogId) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.history_exercise_sets,
                                group.exerciseName,
                                formatSetList(group.sets, format)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
```

5. Оба превью: `onExerciseClick = {}` последним аргументом `HistoryContent`.

- [ ] **Step 8: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок и без новых `UnusedResources` — все строки `progress_*`, `catalog_open_progress`, `history_open_progress` используются. Если предупреждение есть — найти строку без вызова.

Run (после проверки `adb devices`):

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.presentation.navigation.ExerciseProgressRouteTest,ru.hopes.workouttimer.presentation.screen.exercises.ExerciseCatalogContentTest,ru.hopes.workouttimer.presentation.screen.workoutHistory.HistoryContentTest
```

Expected: PASS — 1 + 8 + 5 тестов.

- [ ] **Step 9: Коммит**

```bash
git add app/src
git commit -m "$(cat <<'EOF'
feat: прогресс открывается из «Упражнений» и из истории тренировки

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Проверка на эмуляторе

**Files:** нет изменений кода (кроме исправлений, если проверка что-то найдёт; каждое исправление — отдельный коммит `fix:` с тестом, который ловит найденное).

- [ ] **Step 1: Полный прогон**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
git status --short app/schemas          # пусто: схема v8 не менялась
git diff feature/exercise-progress-e2 --stat -- app/build.gradle.kts gradle/libs.versions.toml   # пусто: библиотек нет
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Expected: всё зелёное. Новые и изменённые инструментальные классы E3: `CatalogDaoTest` (11), `ProgressChartTest` (3), `ExerciseProgressContentTest` (11), `ExerciseProgressRouteTest` (1), `ExerciseCatalogContentTest` (8), `HistoryContentTest` (5).

- [ ] **Step 2: Данные — справочник из файла, сессии с датами из SQL**

Настоящая тренировка даёт сессии только «сегодня», а графику нужны даты за месяцы. Поэтому тренировки и справочник приходят импортом (как в E2), а сессии и подходы с прошлыми датами дописываются в копию базы хостовым `sqlite3`.

```bash
SCRATCH=<scratchpad текущей сессии>/e3-check
mkdir -p "$SCRATCH"
ADB=~/Library/Android/sdk/platform-tools/adb
SQLITE=~/Library/Android/sdk/platform-tools/sqlite3
PKG=ru.hopes.workouttimer
$ADB -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Если на эмуляторе остались данные проверки E2 — сначала `$ADB -s emulator-5554 shell pm clear $PKG`.

Файл `$SCRATCH/e3-import.json` (латиница «Squat» — чтобы искать через `adb shell input text`):

```json
{"version":1,"exportDate":"2026-10-01","appVersion":"1.0","workouts":[
 {"name":"Спина","lastUseAt":1,"exercises":[
   {"name":"Тяга блока","weight":5.0,"sets":2,"reps":12,"restTimeMillis":3000,"order":1,"note":"","unit":"PLATE","extraWeight":2.0},
   {"name":"Подтягивания","weight":0.0,"sets":2,"reps":8,"restTimeMillis":3000,"order":2,"note":"","unit":"BODYWEIGHT","extraWeight":0.0},
   {"name":"Squat","weight":60.0,"sets":2,"reps":8,"restTimeMillis":3000,"order":3,"note":"","unit":"KG","extraWeight":0.0}]},
 {"name":"Ноги","lastUseAt":2,"exercises":[
   {"name":"Squat sumo","weight":4.0,"sets":1,"reps":10,"restTimeMillis":3000,"order":1,"note":"","unit":"PLATE","extraWeight":0.0},
   {"name":"Жим ногами","weight":120.0,"sets":1,"reps":10,"restTimeMillis":3000,"order":2,"note":"","unit":"KG","extraWeight":0.0}]}
]}
```

`$ADB -s emulator-5554 push "$SCRATCH/e3-import.json" /sdcard/Download/`, в приложении: список → «Экспорт и импорт» → «Импортировать тренировки» → выбрать файл (навигация по пикеру — `$ADB -s emulator-5554 shell uiautomator dump /sdcard/ui.xml && $ADB -s emulator-5554 exec-out cat /sdcard/ui.xml` и `input tap` по координатам `bounds`).

Сессии и подходы (точное сравнение названий в SQL проверки — не код приложения; 25 дней, а не 30, — чтобы не попасть на границу «1 мес»):

```bash
NOW=$(( $(date +%s) * 1000 )); DAY=86400000; DUR=3000000
cat > "$SCRATCH/seed.sql" <<EOF
INSERT INTO workout_sessions (id, workoutId, startedAt, finishedAt, durationMillis) VALUES
 (9001, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 100*$DAY - $DUR, $NOW - 100*$DAY, $DUR),
 (9002, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 80*$DAY - $DUR, $NOW - 80*$DAY, $DUR),
 (9003, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 60*$DAY - $DUR, $NOW - 60*$DAY, $DUR),
 (9004, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 45*$DAY - $DUR, $NOW - 45*$DAY, $DUR),
 (9005, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 25*$DAY - $DUR, $NOW - 25*$DAY, $DUR),
 (9006, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 20*$DAY - $DUR, $NOW - 20*$DAY, $DUR),
 (9007, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 10*$DAY - $DUR, $NOW - 10*$DAY, $DUR),
 (9008, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 5*$DAY - $DUR, $NOW - 5*$DAY, $DUR),
 (9009, (SELECT id FROM workouts WHERE name = 'Спина'), $NOW - 3*$DAY - $DUR, $NOW - 3*$DAY, $DUR),
 (9010, (SELECT id FROM workouts WHERE name = 'Ноги'), $NOW - 7*$DAY - $DUR, $NOW - 7*$DAY, $DUR);
INSERT INTO session_sets (sessionId, catalogId, weight, extraWeight, reps, unit) VALUES
 (9001, (SELECT id FROM exercise_catalog WHERE name = 'Тяга блока'), 40, 0, 12, 'KG'),
 (9002, (SELECT id FROM exercise_catalog WHERE name = 'Squat'), 55, 0, 8, 'KG'),
 (9003, (SELECT id FROM exercise_catalog WHERE name = 'Тяга блока'), 4, 0, 12, 'PLATE'),
 (9004, (SELECT id FROM exercise_catalog WHERE name = 'Squat'), 57.5, 0, 8, 'KG'),
 (9004, (SELECT id FROM exercise_catalog WHERE name = 'Squat'), 57.5, 0, 8, 'KG'),
 (9004, (SELECT id FROM exercise_catalog WHERE name = 'Подтягивания'), 0, 0, 6, 'BODYWEIGHT'),
 (9004, (SELECT id FROM exercise_catalog WHERE name = 'Подтягивания'), 0, 0, 6, 'BODYWEIGHT'),
 (9005, (SELECT id FROM exercise_catalog WHERE name = 'Тяга блока'), 5, 2, 12, 'PLATE'),
 (9006, (SELECT id FROM exercise_catalog WHERE name = 'Squat'), 60, 0, 8, 'KG'),
 (9007, (SELECT id FROM exercise_catalog WHERE name = 'Подтягивания'), 0, 0, 8, 'BODYWEIGHT'),
 (9007, (SELECT id FROM exercise_catalog WHERE name = 'Подтягивания'), 0, 0, 7, 'BODYWEIGHT'),
 (9008, (SELECT id FROM exercise_catalog WHERE name = 'Squat'), 62.5, 0, 6, 'KG'),
 (9008, (SELECT id FROM exercise_catalog WHERE name = 'Squat'), 60, 0, 8, 'KG'),
 (9009, (SELECT id FROM exercise_catalog WHERE name = 'Тяга блока'), 5, 4, 12, 'PLATE'),
 (9009, (SELECT id FROM exercise_catalog WHERE name = 'Подтягивания'), 0, 0, 10, 'BODYWEIGHT'),
 (9009, (SELECT id FROM exercise_catalog WHERE name = 'Подтягивания'), 0, 0, 9, 'BODYWEIGHT'),
 (9010, (SELECT id FROM exercise_catalog WHERE name = 'Жим ногами'), 120, 0, 10, 'KG');
EOF

$ADB -s emulator-5554 shell am force-stop $PKG
for f in workout_db workout_db-wal workout_db-shm; do
  $ADB -s emulator-5554 exec-out run-as $PKG cat databases/$f > "$SCRATCH/$f"
done
$SQLITE "$SCRATCH/workout_db" "PRAGMA wal_checkpoint(TRUNCATE);"
$SQLITE "$SCRATCH/workout_db" < "$SCRATCH/seed.sql"
$SQLITE "$SCRATCH/workout_db" "PRAGMA foreign_key_check; SELECT COUNT(*) FROM session_sets;"   # пусто; 17
$ADB -s emulator-5554 push "$SCRATCH/workout_db" /data/local/tmp/workout_db
$ADB -s emulator-5554 shell chmod 644 /data/local/tmp/workout_db
$ADB -s emulator-5554 shell run-as $PKG rm -f databases/workout_db-wal databases/workout_db-shm
$ADB -s emulator-5554 shell run-as $PKG cp /data/local/tmp/workout_db databases/workout_db
```

Если `run-as … cp` не читает `/data/local/tmp` — запасной путь: `$ADB -s emulator-5554 exec-in run-as $PKG sh -c 'cat > databases/workout_db' < "$SCRATCH/workout_db"`.

Скриншот: `$ADB -s emulator-5554 exec-out screencap -p > "$SCRATCH/<имя>.png"`; каждый просмотреть (Read) и сверить с ожиданием ниже. Тап по точке графика: в дампе `uiautomator` у графика `content-desc` «График лучшего подхода: …» и `bounds`; последняя точка — у правого края, тап в `(right − 40, (top + bottom) / 2)`.

- [ ] **Step 3: Прогресс в кг — из экрана «Упражнения»**

Список → иконка гантели → тап по строке «Squat»:

- `e3-01-kg-quarter.png` — шапка «Squat», справа «кг»; «ЛУЧШИЙ ПОДХОД» и «62.5 кг × 6»; под ней «+7.5 кг за 3 мес» серым, не лаймом; в карточке подсказка «Нажмите на точку, чтобы увидеть подход», линия из 4 точек с кольцами, деления оси — целые с шагом 2 (54, 56 … 64), под осью две даты; переключатель «1 мес / 3 мес / всё» с выбранным «3 мес»; «ТРЕНИРОВКИ» и строки, новые сверху: «62.5 кг × 6 · 60 × 8», «60 кг × 8», «57.5 кг × 8 · 57.5 × 8», «55 кг × 8». Пометки о других единицах нет.
- Тап по последней точке: `e3-02-kg-selected.png` — над графиком «<дата 5 дней назад> · 62.5 кг × 6», точка крупнее, вертикальный волосок; строка верхней сессии в лаймовой рамке.
- «1 мес»: `e3-03-kg-month.png` — «+2.5 кг за 1 мес», две точки, выбор той же точки сохранился.
- «всё»: те же четыре точки и «+7.5 кг за всё время» (кг-подходов старше 80 дней нет).
- «Назад» → экран «Упражнения».

- [ ] **Step 4: Плита с добавкой и пометка о других единицах**

Тап по «Тяга блока»:

- `e3-04-plate.png` — справа в шапке «плита»; «плита 5 +4 кг × 12»; «+1 плита за 3 мес»; три точки, вторая и третья — между делениями 5 и 6, ниже деления 6; под графиком «Подходы в других единицах — в списке ниже».
- «1 мес»: `e3-05-plate-month.png` — «+2 кг за 1 мес».
- Прокрутить вниз: `e3-06-plate-list.png` — нижняя строка «40 кг × 12» (подход до смены единицы, со своей подписью).

- [ ] **Step 5: Без веса, одна сессия, нет сессий**

- «Подтягивания»: `e3-07-bodyweight.png` — справа «без веса»; «10 повт.»; «+4 повт за 3 мес»; строки «10 · 9», «8 · 7», «6 · 6».
- «Жим ногами»: `e3-08-one-session.png` — «120 кг × 10», изменения нет, на месте графика «График появится, когда за период будет две тренировки», переключатель есть, одна строка сессии.
- «Squat sumo»: `e3-09-empty.png` — пустое состояние «Сделайте упражнение — здесь появится прогресс» с подсказкой про «Закончить подход».

- [ ] **Step 6: Вход из истории**

Список → меню «Спины» → «История» → тап по верхней сессии (3 дня назад): строки «Тяга блока — плита 5 +4 кг × 12 ›», «Подтягивания — 10 · 9 ›».

- Тап по строке «Подтягивания»: `e3-10-from-history.png` — экран прогресса «Подтягивания», карточка истории при тапе не свернулась (вернуться «Назад» — `e3-11-history-back.png`: сессия по-прежнему развёрнута).
- Повернуть экран на прогрессе (`$ADB -s emulator-5554 shell settings put system accelerometer_rotation 0 && $ADB -s emulator-5554 shell settings put system user_rotation 1`, затем вернуть `user_rotation 0`) — выбранный период и данные на месте, экран не мигает пустым состоянием.

- [ ] **Step 7: Итог**

Push и PR **не делать** — это делает контроллер после финального ревью. В отчёт: результаты Step 1 (числа тестов), перечень скриншотов с путями и что на каждом совпало или не совпало с ожиданием, найденные и исправленные проблемы (с SHA коммитов `fix:`).
