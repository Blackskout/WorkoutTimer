# Прогресс в упражнении, E1 — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Миграция базы 7→8 со справочником упражнений и таблицей подходов; запись фактических подходов при завершении тренировки; транзакционные сохранение, удаление и импорт. Внешне приложение не меняется.

**Architecture:** Новые сущности Room `ExerciseCatalogEntity` и `SessionSetEntity`, у `ExerciseEntity` — `catalogId` и `extraWeight`. Миграция 7→8 на Kotlin (`migrate(connection: SQLiteConnection)`) собирает справочник из существующих упражнений по ключу названия. Все многошаговые записи — `@Transaction`-методы по умолчанию в `WorkoutDao`. `WorkoutExecutionViewModel` копит подходы в памяти и отдаёт их вместе с сессией в `FinishWorkoutSessionUseCase`.

**Tech Stack:** Kotlin 2.2, Room 2.8.4 (KSP, Gradle-плагин `androidx.room`, схемы в `app/schemas`), Hilt, Coroutines, JUnit4 + MockK (JVM), `MigrationTestHelper` на `BundledSQLiteDriver` (JVM), Compose UI test + in-memory Room (`androidTest`, эмулятор).

**Spec:** `docs/superpowers/specs/2026-10-01-exercise-progress-design.md` — разделы «Модель данных», «Миграция 7 → 8», «Правила данных», «E1», «Ошибки и краевые случаи», «Тестирование».

## Global Constraints

- Ветка `feature/exercise-progress`, база `master`. PR не вливать.
- Версия базы после E1 — **8**. Миграция добавляется в `ALL_MIGRATIONS` (`AppDatabase.kt`); `8.json` коммитится в `app/schemas/ru.hopes.workouttimer.data.dao.AppDatabase/`.
- `fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 4)` в `AppModule.kt` **не трогать**.
- Кириллицу в SQL не сравнивать (`LOWER()` в SQLite — только ASCII). Ключ названия — только `exerciseNameKey()` в Kotlin.
- Пустое название → `"Без названия"` **до** вычисления ключа.
- `ExerciseEntity.catalogId` — без значения по умолчанию в Kotlin; в базу не попадает 0 из путей сохранения и импорта.
- Сессии (`workout_sessions`) и подходы (`session_sets`) никогда не удаляются вместе с тренировкой.
- Все пользовательские тексты — в `app/src/main/res/values/strings.xml`.
- Комментарии в коде — по-русски, коротко, объясняют «почему». Сообщения коммитов — по-русски с префиксом (`feat:`, `test:`, `refactor:`), последняя строка `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Инструментальные тесты — **только** на `emulator-5554`: перед запуском `~/Library/Android/sdk/platform-tools/adb devices` — если подключено что-то кроме `emulator-5554`, не запускать (`connectedAndroidTest` сносит приложение). Запуск: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`. Эмулятор не запущен — `~/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro -no-snapshot-save -no-audio &` и дождаться `adb shell getprop sys.boot_completed` = 1. Имена тестовых методов в `androidTest` — через подчёркивания.
- JVM-тесты: `./gradlew :app:testDebugUnitTest --tests '<pattern>'`. Room in-memory в JVM-тестах **не собирается** (см. спеку, «Тестирование»), тесты DAO — только `androidTest`.
- В конце каждой задачи: `./gradlew :app:testDebugUnitTest :app:lintDebug` зелёные, lint — 0 ошибок.

## Review Focus

1. Телефон пользователя с базой v7, где есть одновременно упражнение с пустым названием и упражнение «Без названия», — миграция не падает, получается одна запись справочника. Тест — Task 2.
2. Два упражнения с одинаковым ключом в одном сохранении тренировки или одном файле импорта («Присед» и «присед ») — создаётся одна запись справочника, UNIQUE не срабатывает. Тест — Task 3 и Task 4.
3. Подход с `catalogId`, которого нет в справочнике (или 0), при завершении тренировки — сессия всё равно пишется, подход привязывается по названию. Тест — Task 3.
4. Быстрое двойное «Закончить подход» на последнем подходе — одна сессия, один последний подход. Тест — Task 5.
5. Правка веса на плитке и сразу «Закончить подход» — записываются новые значения, а не старые. Тест — Task 5.

---

### Task 1: Название упражнения, ключ и доменные типы

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/ExerciseName.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/ExerciseUnit.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/RecordedSet.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/model/Exercise.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/domain/model/ExerciseNameTest.kt`

**Interfaces:**
- Produces:
  - `const val UNTITLED_EXERCISE_NAME: String = "Без названия"`
  - `fun normalizedExerciseName(raw: String): String` — схлопнутые пробелы, края обрезаны, пустое → `UNTITLED_EXERCISE_NAME`
  - `fun exerciseNameKey(raw: String): String` — ключ от `normalizedExerciseName(raw)`: `lowercase(Locale.ROOT)`, `ё→е`
  - `enum class ExerciseUnit { KG, PLATE, BODYWEIGHT }`
  - `data class RecordedSet(val catalogId: Long, val exerciseName: String, val weight: Double, val extraWeight: Double, val reps: Int, val unit: ExerciseUnit)`
  - `Exercise` получает поля `catalogId: Long = 0L`, `unit: ExerciseUnit = ExerciseUnit.KG`, `extraWeight: Double = 0.0`

- [ ] **Step 1: Написать падающий тест**

```kotlin
package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseNameTest {

    @Test
    fun `normalized name trims edges and collapses inner whitespace including nbsp`() {
        assertEquals("Жим лёжа", normalizedExerciseName("  Жим    лёжа\t"))
    }

    @Test
    fun `blank name becomes untitled`() {
        assertEquals(UNTITLED_EXERCISE_NAME, normalizedExerciseName(""))
        assertEquals(UNTITLED_EXERCISE_NAME, normalizedExerciseName("   "))
    }

    @Test
    fun `key ignores case, extra spaces and yo`() {
        assertEquals(exerciseNameKey("Жим лёжа"), exerciseNameKey("  жим  ЛЕЖА "))
        assertEquals("жим лежа", exerciseNameKey("Жим Лёжа"))
    }

    @Test
    fun `blank and untitled share one key`() {
        assertEquals(exerciseNameKey(UNTITLED_EXERCISE_NAME), exerciseNameKey("   "))
    }

    @Test
    fun `different names keep different keys`() {
        assert(exerciseNameKey("Присед") != exerciseNameKey("Присед сумо"))
    }
}
```

- [ ] **Step 2: Запустить — падает**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseNameTest*'`
Expected: FAIL — `Unresolved reference 'normalizedExerciseName'`.

- [ ] **Step 3: Реализация**

`ExerciseName.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

import java.util.Locale

/**
 * Название для пустого упражнения. Захардкожено, а не в ресурсах: его пишут
 * миграция и DAO, где ресурсов нет. Текст совпадает с R.string.create_untitled.
 */
const val UNTITLED_EXERCISE_NAME = "Без названия"

// (?U)\s — Юникод-пробелы, включая неразрывный из вставленного текста.
// Голый \s видит только ASCII, а \p{javaWhitespace} пропускает U+00A0.
private val WHITESPACE_RUN = Regex("(?U)\\s+")

/** Название, как его хранит справочник: без лишних пробелов, пустое — «Без названия». */
fun normalizedExerciseName(raw: String): String {
    val collapsed = raw.replace(WHITESPACE_RUN, " ").trim()
    return collapsed.ifEmpty { UNTITLED_EXERCISE_NAME }
}

/**
 * Ключ, по которому упражнения сходятся в одну запись справочника.
 * Считается только здесь: LOWER() в SQLite складывает одну ASCII-латиницу,
 * и «Присед» с «присед» в SQL остались бы разными.
 */
fun exerciseNameKey(raw: String): String =
    normalizedExerciseName(raw).lowercase(Locale.ROOT).replace('ё', 'е')
```

`ExerciseUnit.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

/** Единица нагрузки упражнения. Имя константы хранится в базе как TEXT. */
enum class ExerciseUnit { KG, PLATE, BODYWEIGHT }
```

`RecordedSet.kt`:

```kotlin
package ru.hopes.workouttimer.domain.model

/**
 * Подход, закрытый кнопкой «Закончить подход». Копится в памяти и пишется
 * вместе с сессией. exerciseName нужен на случай, если catalogId не найдётся:
 * тогда запись справочника ищется по названию, и сессия не теряется.
 */
data class RecordedSet(
    val catalogId: Long,
    val exerciseName: String,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: ExerciseUnit
)
```

`Exercise.kt` — добавить три поля в конец конструктора:

```kotlin
data class Exercise(
    val id: Int = 0,
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val timeMillis: Long = 120_000L,
    val order: Int,
    val note: String = "",
    val catalogId: Long = 0L,
    val unit: ExerciseUnit = ExerciseUnit.KG,
    val extraWeight: Double = 0.0
)
```

- [ ] **Step 4: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*ExerciseNameTest*'`
Expected: PASS, 5 тестов.

- [ ] **Step 5: Коммит**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/model app/src/test/java/ru/hopes/workouttimer/domain/model/ExerciseNameTest.kt
git commit -m "feat: ключ названия упражнения и доменные типы подходов"
```

---

### Task 2: Схема v8 и миграция 7→8

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/data/entity/ExerciseCatalogEntity.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/entity/SessionSetEntity.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/entity/ExerciseEntity.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/AppDatabase.kt`
- Modify: все места, где конструируется `ExerciseEntity(` — `data/WorkoutRepositoryImpl.kt` (2), `data/ExportImportRepositoryImpl.kt` (1), `presentation/screen/workouts/ListWorkoutScreen.kt` (превью), тесты `domain/usecase/GetWidgetWorkoutsUseCaseTest.kt`, `presentation/screen/workouts/ListWorkoutViewModelTest.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/mapper/WorkoutMapper.kt`
- Create (генерируется сборкой): `app/schemas/ru.hopes.workouttimer.data.dao.AppDatabase/8.json`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/dao/MigrationTest.kt`

**Interfaces:**
- Consumes: `normalizedExerciseName`, `exerciseNameKey`, `ExerciseUnit` (Task 1).
- Produces:
  - `ExerciseCatalogEntity(id: Long = 0, name: String, nameKey: String, unit: String = ExerciseUnit.KG.name)`, таблица `exercise_catalog`
  - `SessionSetEntity(id: Long = 0, sessionId: Long, catalogId: Long, weight: Double, extraWeight: Double = 0.0, reps: Int, unit: String)`, таблица `session_sets`
  - `ExerciseEntity(…, catalogId: Long /* без умолчания */, extraWeight: Double = 0.0)`
  - `val MIGRATION_7_8: Migration`, `ALL_MIGRATIONS = listOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)`
  - `WorkoutMapper`: `Exercise.catalogId = e.catalogId`, `Exercise.extraWeight = e.extraWeight` (unit в E1 не читается — всегда `KG`)

- [ ] **Step 1: Сущности**

`ExerciseCatalogEntity.kt`:

```kotlin
package ru.hopes.workouttimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.hopes.workouttimer.domain.model.ExerciseUnit

/** Запись справочника упражнений. nameKey считает только exerciseNameKey(). */
@Entity(
    tableName = "exercise_catalog",
    indices = [Index(value = ["nameKey"], unique = true)]
)
data class ExerciseCatalogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    @ColumnInfo(defaultValue = "KG") val unit: String = ExerciseUnit.KG.name
)
```

`SessionSetEntity.kt`:

```kotlin
package ru.hopes.workouttimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Фактический подход сессии. Порядок внутри сессии — порядок id: номера
 * подхода нет, между упражнениями можно прыгать и возвращаться.
 * RESTRICT на справочник не даёт автоочистке удалить запись с историей.
 */
@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseCatalogEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("sessionId"), Index("catalogId")]
)
data class SessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val catalogId: Long,
    val weight: Double,
    @ColumnInfo(defaultValue = "0") val extraWeight: Double = 0.0,
    val reps: Int,
    val unit: String
)
```

`ExerciseEntity.kt` — добавить индекс и две колонки:

```kotlin
package ru.hopes.workouttimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises", indices = [Index("catalogId")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutId: Long,
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeMillis: Long,
    val orderInWorkout: Int,
    val note: String = "",
    // Без умолчания в Kotlin намеренно: каждый путь вставки обязан проставить
    // запись справочника. DEFAULT 0 в базе нужен только для ALTER TABLE.
    @ColumnInfo(defaultValue = "0") val catalogId: Long,
    @ColumnInfo(defaultValue = "0") val extraWeight: Double = 0.0
)
```

`AppDatabase.kt`: в `entities` добавить `ExerciseCatalogEntity::class, SessionSetEntity::class`, `version = 8`.

- [ ] **Step 2: Дотянуть компиляцию и выгрузить схему**

Во всех местах `ExerciseEntity(` добавить `catalogId = 0L` — в `WorkoutRepositoryImpl.kt` и `ExportImportRepositoryImpl.kt` с комментарием `// проставит транзакция DAO (Task 3/4)`; в превью `ListWorkoutScreen.kt` и тестах — `catalogId = index.toLong() + 1` или `1L`. В `WorkoutMapper.toDomain()` добавить в `Exercise(...)` `catalogId = e.catalogId, extraWeight = e.extraWeight`.

Во временной заглушке `MIGRATION_7_8` пока нет — добавить в `AppDatabase.kt`:

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        TODO("Task 2, Step 4")
    }
}

val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
```

Run: `./gradlew :app:kspDebugKotlin`
Expected: BUILD SUCCESSFUL, появился `app/schemas/ru.hopes.workouttimer.data.dao.AppDatabase/8.json`. Открыть его и выписать `createSql` таблиц `exercise_catalog`, `session_sets` и всех индексов — Step 4 копирует их дословно (с заменой `${TABLE_NAME}` на имя таблицы). Проверить, как Room записал `DEFAULT` у `unit` (`'KG'`) — ровно так же пишется в миграции.

- [ ] **Step 3: Падающие тесты миграции**

Добавить в `MigrationTest.kt` (вспомогательные `insertWorkoutV5`, `textColumn` уже есть):

```kotlin
    @Test
    fun `7 to 8 builds catalog merging cyrillic names by key`() {
        helper.createDatabase(7).use { db ->
            insertWorkoutV5(db, id = 1, name = "Ноги", lastUseAt = 1L)
            insertWorkoutV5(db, id = 2, name = "Ноги Б", lastUseAt = 2L)
            db.execSQL(
                "INSERT INTO exercises (id, workoutId, name, weight, sets, reps, restTimeMillis, orderInWorkout, note) VALUES " +
                    "(1, 1, 'Присед', 60.0, 4, 8, 120000, 0, ''), " +
                    "(2, 2, '  присед ', 50.0, 4, 8, 120000, 0, ''), " +
                    "(3, 2, 'Жим лёжа', 40.0, 3, 10, 90000, 1, ''), " +
                    "(4, 1, 'ЖИМ ЛЕЖА', 45.0, 3, 10, 90000, 1, ''), " +
                    "(5, 1, 'Жим\u00A0лёжа', 45.0, 3, 10, 90000, 2, '')"
            )
            db.execSQL("INSERT INTO workout_sessions (id, workoutId, startedAt, finishedAt, durationMillis) VALUES (1, 1, 10, 20, 10)")
        }

        helper.runMigrationsAndValidate(8, listOf(MIGRATION_7_8)).use { db ->
            assertEquals(listOf("Жим лёжа", "Присед"), textColumn(db, "SELECT name FROM exercise_catalog ORDER BY name"))
            assertEquals(listOf("KG", "KG"), textColumn(db, "SELECT unit FROM exercise_catalog"))
            // одинаковый ключ — одна запись справочника
            assertEquals(listOf("1"), textColumn(db, "SELECT COUNT(DISTINCT catalogId) FROM exercises WHERE id IN (1, 2)"))
            assertEquals(listOf("1"), textColumn(db, "SELECT COUNT(DISTINCT catalogId) FROM exercises WHERE id IN (3, 4, 5)"))
            // упражнения носят название своей записи справочника
            assertEquals(listOf("Жим лёжа", "Жим лёжа", "Жим лёжа"), textColumn(db, "SELECT name FROM exercises WHERE id IN (3, 4, 5) ORDER BY id"))
            assertEquals(listOf("0"), textColumn(db, "SELECT COUNT(*) FROM exercises WHERE catalogId = 0"))
            // сессии не тронуты, FK чистые
            assertEquals(listOf("1"), textColumn(db, "SELECT COUNT(*) FROM workout_sessions"))
            assertEquals(emptyList<String>(), textColumn(db, "PRAGMA foreign_key_check"))
        }
    }

    @Test
    fun `7 to 8 removes ghost exercises of deleted workouts`() {
        helper.createDatabase(7).use { db ->
            insertWorkoutV5(db, id = 1, name = "Ноги", lastUseAt = 1L)
            db.execSQL(
                "INSERT INTO exercises (id, workoutId, name, weight, sets, reps, restTimeMillis, orderInWorkout, note) VALUES " +
                    "(1, 1, 'Присед', 60.0, 4, 8, 120000, 0, ''), " +
                    "(2, 99, 'Призрак', 10.0, 1, 1, 1000, 0, '')"
            )
        }

        helper.runMigrationsAndValidate(8, listOf(MIGRATION_7_8)).use { db ->
            assertEquals(listOf("1"), textColumn(db, "SELECT id FROM exercises"))
            assertEquals(listOf("Присед"), textColumn(db, "SELECT name FROM exercise_catalog"))
        }
    }

    @Test
    fun `7 to 8 turns blank names into untitled without clashing with existing untitled`() {
        helper.createDatabase(7).use { db ->
            insertWorkoutV5(db, id = 1, name = "Импорт", lastUseAt = 1L)
            db.execSQL(
                "INSERT INTO exercises (id, workoutId, name, weight, sets, reps, restTimeMillis, orderInWorkout, note) VALUES " +
                    "(1, 1, '   ', 0.0, 1, 1, 1000, 0, ''), " +
                    "(2, 1, 'Без названия', 0.0, 1, 1, 1000, 1, '')"
            )
        }

        helper.runMigrationsAndValidate(8, listOf(MIGRATION_7_8)).use { db ->
            assertEquals(listOf("Без названия"), textColumn(db, "SELECT name FROM exercise_catalog"))
            assertEquals(listOf("Без названия", "Без названия"), textColumn(db, "SELECT name FROM exercises ORDER BY id"))
        }
    }

    @Test
    fun `7 to 8 accepts a database without exercises`() {
        helper.createDatabase(7).use { db ->
            insertWorkoutV5(db, id = 1, name = "Пустая", lastUseAt = 1L)
        }

        helper.runMigrationsAndValidate(8, listOf(MIGRATION_7_8)).use { db ->
            assertEquals(listOf("0"), textColumn(db, "SELECT COUNT(*) FROM exercise_catalog"))
        }
    }
```

Также в существующем тесте `all migrations from 5 reach current version with data intact` добавить после проверок:

```kotlin
            assertEquals(listOf("Жим", "Подтягивания"), textColumn(db, "SELECT name FROM exercise_catalog ORDER BY name"))
```

Run: `./gradlew :app:testDebugUnitTest --tests '*MigrationTest*'`
Expected: FAIL — `NotImplementedError` из `TODO("Task 2, Step 4")`.

- [ ] **Step 4: Миграция**

Заменить заглушку `MIGRATION_7_8` в `AppDatabase.kt`. SQL `CREATE TABLE`/`CREATE INDEX` сверить с `8.json` из Step 2 — ниже ожидаемый вид; если Room записал иначе (порядок колонок, кавычки, `DEFAULT`), брать из `8.json`.

```kotlin
// Справочник собирается в Kotlin: LOWER() в SQLite не складывает кириллицу,
// и «Присед» с «присед» в SQL разошлись бы. Пустые названия становятся
// «Без названия» до группировки — иначе их группа столкнулась бы с настоящим
// «Без названия» на UNIQUE-индексе и уронила миграцию.
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_catalog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, `unit` TEXT NOT NULL DEFAULT 'KG')"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_catalog_nameKey` ON `exercise_catalog` (`nameKey`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `catalogId` INTEGER NOT NULL, `weight` REAL NOT NULL, " +
                "`extraWeight` REAL NOT NULL DEFAULT 0, `reps` INTEGER NOT NULL, `unit` TEXT NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`catalogId`) REFERENCES `exercise_catalog`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_session_sets_sessionId` ON `session_sets` (`sessionId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_session_sets_catalogId` ON `session_sets` (`catalogId`)")
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `catalogId` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `exercises` ADD COLUMN `extraWeight` REAL NOT NULL DEFAULT 0")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_catalogId` ON `exercises` (`catalogId`)")

        // Упражнения удалённых тренировок: deleteWorkout их не удалял, в интерфейсе они не видны.
        connection.execSQL("DELETE FROM `exercises` WHERE `workoutId` NOT IN (SELECT `id` FROM `workouts`)")

        val exercises = buildList {
            connection.prepare("SELECT `id`, `name` FROM `exercises` ORDER BY `id`").use { stmt ->
                while (stmt.step()) add(stmt.getLong(0) to normalizedExerciseName(stmt.getText(1)))
            }
        }

        // Упражнению пишется название его записи справочника — так же, как при сохранении.
        val catalogByKey = mutableMapOf<String, Pair<Long, String>>()
        for ((exerciseId, name) in exercises) {
            val key = exerciseNameKey(name)
            val (catalogId, catalogName) = catalogByKey.getOrPut(key) {
                connection.prepare(
                    "INSERT INTO `exercise_catalog` (`name`, `nameKey`, `unit`) VALUES (?, ?, 'KG')"
                ).use { stmt ->
                    stmt.bindText(1, name)
                    stmt.bindText(2, key)
                    stmt.step()
                }
                val id = connection.prepare("SELECT last_insert_rowid()").use { stmt ->
                    stmt.step()
                    stmt.getLong(0)
                }
                id to name
            }
            connection.prepare(
                "UPDATE `exercises` SET `catalogId` = ?, `name` = ? WHERE `id` = ?"
            ).use { stmt ->
                stmt.bindLong(1, catalogId)
                stmt.bindText(2, catalogName)
                stmt.bindLong(3, exerciseId)
                stmt.step()
            }
        }
    }
}
```

Импорты в `AppDatabase.kt`: `ru.hopes.workouttimer.domain.model.exerciseNameKey`, `ru.hopes.workouttimer.domain.model.normalizedExerciseName`, `ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity`, `ru.hopes.workouttimer.data.entity.SessionSetEntity`.

Имя справочника — название первого по `id` упражнения группы: цикл идёт по `ORDER BY id`, `getOrPut` вставляет запись на первом упражнении ключа.

- [ ] **Step 5: Запустить — проходит**

Run: `./gradlew :app:testDebugUnitTest --tests '*MigrationTest*'`
Expected: PASS, 7 тестов (3 старых + 4 новых). Если `runMigrationsAndValidate` ругается на расхождение схемы — сверить SQL из Step 4 с `8.json` дословно.

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: всё зелёное, lint 0 ошибок.

- [ ] **Step 6: Коммит**

```bash
git add app/schemas app/src/main/java/ru/hopes/workouttimer/data app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt app/src/test
git commit -m "feat: миграция 7→8 — справочник упражнений и таблица подходов"
```

---

### Task 3: Транзакции DAO — сохранение, удаление, завершение сессии

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/dao/SessionSetDraft.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt`
- Delete: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/AddWorkoutSessionUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/FinishWorkoutSessionUseCase.kt`
- Modify: `app/src/androidTest/java/ru/hopes/workouttimer/presentation/screen/creation/FakeWorkoutRepository.kt`
- Modify: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt`
- Modify: `app/src/test/java/ru/hopes/workouttimer/domain/usecase/SkipWorkoutUseCaseTest.kt:39` — `coVerify { repo.addWorkoutSession(any(), any(), any(), any()) }` заменить на `repo.finishWorkoutSession(any(), any(), any(), any(), any())`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModel.kt`, `CreateWorkoutScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/test/java/ru/hopes/workouttimer/presentation/screen/creation/CreateWorkoutViewModelTest.kt` (Step 4b)
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/data/dao/WorkoutDaoTest.kt`

**Interfaces:**
- Consumes: сущности и `normalizedExerciseName`/`exerciseNameKey` (Task 1–2), `RecordedSet`.
- Produces (`WorkoutDao`):
  - `suspend fun findCatalogByKey(nameKey: String): ExerciseCatalogEntity?`
  - `suspend fun findCatalogById(id: Long): ExerciseCatalogEntity?`
  - `suspend fun insertCatalog(entry: ExerciseCatalogEntity): Long`
  - `suspend fun findOrCreateCatalog(rawName: String): ExerciseCatalogEntity`
  - `suspend fun cleanupCatalog()`
  - `@Transaction suspend fun insertWorkoutResolvingCatalog(workout: WorkoutEntity, exercises: List<ExerciseEntity>): Long`
  - `@Transaction suspend fun updateWorkoutResolvingCatalog(workoutId: Int, name: String, exercises: List<ExerciseEntity>)`
  - `@Transaction suspend fun deleteWorkoutWithExercises(workout: WorkoutEntity)`
  - `suspend fun insertSession(session: WorkoutSessionEntity): Long` (было `Unit`)
  - `suspend fun insertSessionSet(set: SessionSetEntity): Long`
  - `@Transaction suspend fun finishSession(session: WorkoutSessionEntity, sets: List<SessionSetDraft>): Long`
  - `suspend fun getSessionSets(sessionId: Long): List<SessionSetEntity>` (для тестов и E2)
  - `suspend fun getCatalog(): List<ExerciseCatalogEntity>` (для тестов и E2)
- Produces (`WorkoutRepository`): `suspend fun finishWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long, sets: List<RecordedSet>)` вместо `addWorkoutSession`.
- Produces: `class FinishWorkoutSessionUseCase @Inject constructor(repo: WorkoutRepository) { suspend operator fun invoke(workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long, sets: List<RecordedSet>) }`
- `data class SessionSetDraft(val catalogId: Long, val exerciseName: String, val weight: Double, val extraWeight: Double, val reps: Int, val unit: String)`

- [ ] **Step 1: Падающие инструментальные тесты DAO**

`WorkoutDaoTest.kt`:

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

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

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

    private fun exercise(name: String, order: Int = 0) = ExerciseEntity(
        workoutId = 0, name = name, weight = 50.0, sets = 3, reps = 8,
        restTimeMillis = 60_000, orderInWorkout = order, catalogId = 0L
    )

    private fun session(workoutId: Long) =
        WorkoutSessionEntity(workoutId = workoutId, startedAt = 1, finishedAt = 2, durationMillis = 1)

    @Test
    fun сохранение_находит_одну_запись_для_одинаковых_ключей() = runBlocking {
        val id = dao.insertWorkoutResolvingCatalog(
            WorkoutEntity(name = "Ноги", lastUseAt = 0),
            listOf(exercise("Присед"), exercise("  присед ", order = 1))
        )
        val exercises = dao.getAllWorkoutsWithExercises().first().single { it.workout.id.toLong() == id }.exercises
        assertEquals(1, dao.getCatalog().size)
        assertEquals(listOf("Присед", "Присед"), exercises.sortedBy { it.orderInWorkout }.map { it.name })
        assertEquals(1, exercises.map { it.catalogId }.distinct().size)
        assert(exercises.all { it.catalogId > 0 })
    }

    @Test
    fun замена_упражнения_удаляет_неиспользуемую_запись_справочника() = runBlocking {
        val id = dao.insertWorkoutResolvingCatalog(WorkoutEntity(name = "Ноги", lastUseAt = 0), listOf(exercise("Присед")))
        dao.updateWorkoutResolvingCatalog(id.toInt(), "Ноги", listOf(exercise("Жим ногами")))
        assertEquals(listOf("Жим ногами"), dao.getCatalog().map { it.name })
    }

    @Test
    fun запись_с_подходами_не_удаляется_автоочисткой() = runBlocking {
        val id = dao.insertWorkoutResolvingCatalog(WorkoutEntity(name = "Ноги", lastUseAt = 0), listOf(exercise("Присед")))
        val catalogId = dao.getCatalog().single().id
        dao.finishSession(session(id), listOf(SessionSetDraft(catalogId, "Присед", 60.0, 0.0, 8, "KG")))
        dao.updateWorkoutResolvingCatalog(id.toInt(), "Ноги", listOf(exercise("Жим ногами")))
        assertEquals(listOf("Жим ногами", "Присед"), dao.getCatalog().map { it.name }.sorted())
    }

    @Test
    fun удаление_тренировки_удаляет_упражнения_и_не_трогает_сессии() = runBlocking {
        val id = dao.insertWorkoutResolvingCatalog(WorkoutEntity(name = "Ноги", lastUseAt = 0), listOf(exercise("Присед")))
        val catalogId = dao.getCatalog().single().id
        val sessionId = dao.finishSession(session(id), listOf(SessionSetDraft(catalogId, "Присед", 60.0, 0.0, 8, "KG")))
        val workout = dao.getWorkoutById(id.toInt())!!
        dao.deleteWorkoutWithExercises(workout)
        assertEquals(0, dao.getAllWorkoutsWithExercises().first().size)
        assertEquals(1, dao.getSessionSets(sessionId).size)
        assertEquals(listOf("Присед"), dao.getCatalog().map { it.name })
    }

    @Test
    fun завершение_с_неизвестным_catalogId_пишет_сессию_и_привязывает_подход_по_названию() = runBlocking {
        val id = dao.insertWorkoutResolvingCatalog(WorkoutEntity(name = "Ноги", lastUseAt = 0), listOf(exercise("Присед")))
        val sessionId = dao.finishSession(
            session(id),
            listOf(
                SessionSetDraft(0L, "Присед", 60.0, 0.0, 8, "KG"),
                SessionSetDraft(999L, "Выпады", 20.0, 0.0, 10, "KG")
            )
        )
        val sets = dao.getSessionSets(sessionId)
        assertEquals(2, sets.size)
        val catalogByName = dao.getCatalog().associate { it.id to it.name }
        assertEquals(listOf("Присед", "Выпады"), sets.map { catalogByName.getValue(it.catalogId) })
    }

    @Test
    fun подходы_хранятся_в_порядке_записи() = runBlocking {
        val id = dao.insertWorkoutResolvingCatalog(WorkoutEntity(name = "Ноги", lastUseAt = 0), listOf(exercise("Присед")))
        val catalogId = dao.getCatalog().single().id
        val sessionId = dao.finishSession(
            session(id),
            listOf(8, 7, 6).map { SessionSetDraft(catalogId, "Присед", 60.0, 0.0, it, "KG") }
        )
        assertEquals(listOf(8, 7, 6), dao.getSessionSets(sessionId).map { it.reps })
    }
}
```

Run (по правилам эмулятора из Global Constraints): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.data.dao.WorkoutDaoTest`
Expected: FAIL — компиляция: `Unresolved reference 'insertWorkoutResolvingCatalog'`.

- [ ] **Step 2: DAO**

`SessionSetDraft.kt`:

```kotlin
package ru.hopes.workouttimer.data.dao

/** Подход до записи: catalogId может оказаться неверным — тогда ищем по названию. */
data class SessionSetDraft(
    val catalogId: Long,
    val exerciseName: String,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: String
)
```

`WorkoutDao.kt`: сделать `deleteWorkout` `suspend`; `insertSession` вернуть `Long`; добавить (импорты сущностей, `ExerciseUnit`, `exerciseNameKey`, `normalizedExerciseName`):

```kotlin
    @Query("SELECT * FROM exercise_catalog WHERE nameKey = :nameKey")
    suspend fun findCatalogByKey(nameKey: String): ExerciseCatalogEntity?

    @Query("SELECT * FROM exercise_catalog WHERE id = :id")
    suspend fun findCatalogById(id: Long): ExerciseCatalogEntity?

    @Insert
    suspend fun insertCatalog(entry: ExerciseCatalogEntity): Long

    @Query("SELECT * FROM exercise_catalog ORDER BY name")
    suspend fun getCatalog(): List<ExerciseCatalogEntity>

    @Insert
    suspend fun insertSessionSet(set: SessionSetEntity): Long

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getSessionSets(sessionId: Long): List<SessionSetEntity>

    // Запись справочника живёт, пока её используют тренировки или у неё есть подходы.
    @Query(
        """
        DELETE FROM exercise_catalog
        WHERE id NOT IN (SELECT catalogId FROM exercises)
        AND id NOT IN (SELECT catalogId FROM session_sets)
        """
    )
    suspend fun cleanupCatalog()

    /** Ищет запись по ключу названия, создаёт с единицей «кг», если её нет. */
    suspend fun findOrCreateCatalog(rawName: String): ExerciseCatalogEntity {
        val name = normalizedExerciseName(rawName)
        val key = exerciseNameKey(name)
        findCatalogByKey(key)?.let { return it }
        val entry = ExerciseCatalogEntity(name = name, nameKey = key, unit = ExerciseUnit.KG.name)
        return entry.copy(id = insertCatalog(entry))
    }

    // В упражнение пишется название из справочника: «присед » сохраняется как «Присед».
    private suspend fun resolveCatalog(workoutId: Long, exercises: List<ExerciseEntity>): List<ExerciseEntity> =
        exercises.map { ex ->
            val entry = findOrCreateCatalog(ex.name)
            ex.copy(workoutId = workoutId, catalogId = entry.id, name = entry.name)
        }

    @Transaction
    suspend fun insertWorkoutResolvingCatalog(workout: WorkoutEntity, exercises: List<ExerciseEntity>): Long {
        val workoutId = insertWorkout(workout)
        insertExercises(resolveCatalog(workoutId, exercises))
        cleanupCatalog()
        return workoutId
    }

    @Transaction
    suspend fun updateWorkoutResolvingCatalog(workoutId: Int, name: String, exercises: List<ExerciseEntity>) {
        updateWorkout(workoutId, name)
        deleteExercisesByWorkoutId(workoutId.toLong())
        insertExercises(resolveCatalog(workoutId.toLong(), exercises))
        cleanupCatalog()
    }

    // Сессии и подходы не удаляются: на них держится прогресс упражнения.
    @Transaction
    suspend fun deleteWorkoutWithExercises(workout: WorkoutEntity) {
        deleteExercisesByWorkoutId(workout.id.toLong())
        deleteWorkout(workout)
        cleanupCatalog()
    }

    // Сессия не должна потеряться из-за подхода: неизвестный catalogId
    // привязывается по названию, а FK на справочник уже не сработает.
    @Transaction
    suspend fun finishSession(session: WorkoutSessionEntity, sets: List<SessionSetDraft>): Long {
        val sessionId = insertSession(session)
        for (draft in sets) {
            val catalogId = draft.catalogId.takeIf { it > 0 && findCatalogById(it) != null }
                ?: findOrCreateCatalog(draft.exerciseName).id
            insertSessionSet(
                SessionSetEntity(
                    sessionId = sessionId,
                    catalogId = catalogId,
                    weight = draft.weight,
                    extraWeight = draft.extraWeight,
                    reps = draft.reps,
                    unit = draft.unit
                )
            )
        }
        return sessionId
    }
```

Если KSP не принимает `private suspend fun` в интерфейсе DAO — сделать `resolveCatalog` публичным с KDoc «внутренний шаг транзакций, снаружи не вызывать».

- [ ] **Step 3: Репозиторий и use case**

`WorkoutRepository.kt`: заменить `addWorkoutSession(...)` на

```kotlin
        suspend fun finishWorkoutSession(
            workoutId: Int,
            startedAt: Long,
            finishedAt: Long,
            durationMillis: Long,
            sets: List<RecordedSet>
        )
```

`WorkoutRepositoryImpl.kt`:
- `addWorkout` → `dao.insertWorkoutResolvingCatalog(workoutEntity, exerciseEntities)` (сущности строятся как сейчас, `catalogId = 0L`, комментарий «проставит транзакция DAO»), затем `widgetUpdater.requestUpdate()`.
- `updateWorkout` → внутри `withContext(Dispatchers.IO)` один вызов `dao.updateWorkoutResolvingCatalog(workout.id, workout.name, exerciseEntities)`.
- `deleteWorkout` → `dao.deleteWorkoutWithExercises(workout)`.
- `addWorkoutSession` → `finishWorkoutSession`:

```kotlin
    override suspend fun finishWorkoutSession(
        workoutId: Int,
        startedAt: Long,
        finishedAt: Long,
        durationMillis: Long,
        sets: List<RecordedSet>
    ) {
        dao.finishSession(
            WorkoutSessionEntity(
                workoutId = workoutId.toLong(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMillis = durationMillis
            ),
            sets.map {
                SessionSetDraft(
                    catalogId = it.catalogId,
                    exerciseName = it.exerciseName,
                    weight = it.weight,
                    extraWeight = it.extraWeight,
                    reps = it.reps,
                    unit = it.unit.name
                )
            }
        )
    }
```

`FinishWorkoutSessionUseCase.kt`:

```kotlin
package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.model.RecordedSet
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class FinishWorkoutSessionUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(
        workoutId: Int,
        startedAt: Long,
        finishedAt: Long,
        durationMillis: Long,
        sets: List<RecordedSet>
    ) = repo.finishWorkoutSession(workoutId, startedAt, finishedAt, durationMillis, sets)
}
```

Удалить `AddWorkoutSessionUseCase.kt`. В `FakeWorkoutRepository.kt` и других реализациях `WorkoutRepository` в тестах заменить метод. `WorkoutExecutionViewModel` пока правится минимально, чтобы собралось: параметр конструктора `addWorkoutSessionUseCase: AddWorkoutSessionUseCase` становится **`finishWorkoutSessionUseCase: FinishWorkoutSessionUseCase`** (это имя использует Task 5), вызов — с `sets = emptyList()` (полностью — в Task 5). В `WorkoutExecutionViewModelTest` (около 25 тестов): `buildViewModel(..., addWorkoutSessionUseCase: AddWorkoutSessionUseCase)` → `finishWorkoutSessionUseCase: FinishWorkoutSessionUseCase`; каждый `mockk<AddWorkoutSessionUseCase>()` → `mockk<FinishWorkoutSessionUseCase>()`; в каждом `coEvery`/`coVerify` вызова use case с именованными аргументами добавить `sets = any()`. Все прежние тесты должны остаться зелёными.

- [ ] **Step 4: Обновить `WorkoutRepositoryImplTest`**

Тесты `addWorkoutSession …` (`:34`, `:138`) переписать на `finishWorkoutSession`: проверка, что `dao.finishSession` получил сессию с `workoutId.toLong()` и нужной длительностью и драфт с `unit = "KG"`; и что виджет не обновляется. Тесты `addWorkout`/`updateWorkout`/`deleteWorkout` проверяют вызов `dao.insertWorkoutResolvingCatalog` / `dao.updateWorkoutResolvingCatalog` / `dao.deleteWorkoutWithExercises` (`coVerify`) и обновление виджета; тест «updateWorkout не трогает lastUseAt» — что `dao.updateLastUseAt` не вызывался.

Пример:

```kotlin
    @Test
    fun `finishWorkoutSession hands session and drafts to the dao in one call`() = runTest {
        val dao = mockk<WorkoutDao>(relaxed = true)
        val sessionSlot = slot<WorkoutSessionEntity>()
        val draftsSlot = slot<List<SessionSetDraft>>()
        coEvery { dao.finishSession(capture(sessionSlot), capture(draftsSlot)) } returns 1L
        val repo = WorkoutRepositoryImpl(dao, mockk(relaxed = true))

        repo.finishWorkoutSession(
            workoutId = 7, startedAt = 100, finishedAt = 400, durationMillis = 250,
            sets = listOf(RecordedSet(3L, "Присед", 60.0, 0.0, 8, ExerciseUnit.KG))
        )

        assertEquals(7L, sessionSlot.captured.workoutId)
        assertEquals(250L, sessionSlot.captured.durationMillis)
        assertEquals(listOf(SessionSetDraft(3L, "Присед", 60.0, 0.0, 8, "KG")), draftsSlot.captured)
    }
```

- [ ] **Step 4b: Сбой сохранения в редакторе — снекбар, а не падение**

Транзакция сохранения теперь может бросить (например, UNIQUE справочника при гонке). Сейчас `CreateWorkoutCommand.Save` (`CreateWorkoutViewModel.kt:97-129`) без перехвата — исключение из `viewModelScope.launch` уронит приложение.

Тест в `CreateWorkoutViewModelTest` (JVM; файл использует `StandardTestDispatcher` и фабрику `viewModel(add = …)`, `:25-29`). Добавить импорты `org.junit.Assert.assertFalse`, `org.junit.Assert.assertTrue`:

```kotlin
    @Test
    fun `сбой сохранения оставляет редактор открытым и сообщает об ошибке`() = runTest(dispatcher) {
        val add = mockk<AddWorkoutUseCase>()
        coEvery { add(any()) } throws IllegalStateException("db")
        val vm = viewModel(add = add)

        vm.processCommand(CreateWorkoutCommand.ChangeWorkoutName("Ноги"))
        vm.processCommand(CreateWorkoutCommand.AddExercise())
        val exerciseId = vm.state.value.exercises.first().id
        vm.processCommand(
            CreateWorkoutCommand.UpdateExercise(
                exerciseId,
                vm.state.value.exercises.first().copy(name = "Присед")
            )
        )
        vm.processCommand(CreateWorkoutCommand.Save)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isFinished)
        assertTrue(vm.state.value.saveFailed)

        vm.processCommand(CreateWorkoutCommand.DismissSaveError)
        assertFalse(vm.state.value.saveFailed)
    }
```

Реализация: в `CreateWorkoutState` поле `val saveFailed: Boolean = false`; в `Save` обернуть вызов use case:

```kotlin
                        try {
                            if (editingWorkoutId != null) updateWorkoutUseCase(workout) else addWorkoutUseCase(workout)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _state.update { it.copy(saveFailed = true) }
                            return@launch
                        }
                        _state.update { it.copy(isFinished = true) }
```

новая команда `data object DismissSaveError : CreateWorkoutCommand` в `processCommand` сбрасывает флаг (`CancellationException` — `kotlinx.coroutines.CancellationException`). В `CreateWorkoutScreen.kt` — `SnackbarHostState`, `snackbarHost` у `Scaffold` (`:113`), `LaunchedEffect(state.saveFailed)` показывает `R.string.create_save_error` («Не удалось сохранить тренировку») и шлёт `DismissSaveError`. Строку добавить в секцию «Создание и редактирование тренировки» `strings.xml`.

- [ ] **Step 5: Запустить**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS, lint 0 ошибок.

Run (эмулятор): `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.hopes.workouttimer.data.dao.WorkoutDaoTest`
Expected: PASS, 6 тестов.

- [ ] **Step 6: Коммит**

```bash
git add -A app/src
git commit -m "feat: транзакции справочника, удаление с упражнениями и запись сессии с подходами"
```

---

### Task 4: Импорт в одной транзакции со справочником

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/ExportImportRepositoryImpl.kt:67-150`
- Test: `app/src/androidTest/java/ru/hopes/workouttimer/data/dao/WorkoutDaoTest.kt`

**Interfaces:**
- Consumes: `insertWorkoutResolvingCatalog`, `findOrCreateCatalog`, `cleanupCatalog` (Task 3).
- Produces:
  - `@Query("SELECT name FROM workouts") suspend fun getAllWorkoutNames(): List<String>`
  - `@Transaction suspend fun importWorkouts(workouts: List<Pair<WorkoutEntity, List<ExerciseEntity>>>): Int` — число вставленных тренировок; всё или ничего.

- [ ] **Step 1: Падающие тесты**

Добавить в `WorkoutDaoTest`:

```kotlin
    @Test
    fun импорт_сводит_одинаковые_ключи_из_одного_файла_в_одну_запись() = runBlocking {
        val count = dao.importWorkouts(
            listOf(
                WorkoutEntity(name = "А", lastUseAt = 0) to listOf(exercise("Присед")),
                WorkoutEntity(name = "Б", lastUseAt = 0) to listOf(exercise("ПРИСЕД "))
            )
        )
        assertEquals(2, count)
        assertEquals(listOf("Присед"), dao.getCatalog().map { it.name })
    }

    @Test
    fun сбой_посреди_импорта_откатывает_весь_файл() = runBlocking {
        val broken = exercise("Жим").copy(id = 1) // одинаковый первичный ключ со следующим
        val result = runCatching {
            dao.importWorkouts(
                listOf(
                    WorkoutEntity(name = "А", lastUseAt = 0) to listOf(broken),
                    WorkoutEntity(name = "Б", lastUseAt = 0) to listOf(broken)
                )
            )
        }
        assert(result.isFailure)
        assertEquals(emptyList<String>(), dao.getAllWorkoutNames())
        assertEquals(0, dao.getCatalog().size)
    }
```

Run (эмулятор, класс `WorkoutDaoTest`). Expected: FAIL — `Unresolved reference 'importWorkouts'`.

- [ ] **Step 2: DAO**

```kotlin
    @Query("SELECT name FROM workouts")
    suspend fun getAllWorkoutNames(): List<String>

    // Весь файл — одна транзакция: сбой посреди файла не оставляет половину тренировок.
    @Transaction
    suspend fun importWorkouts(workouts: List<Pair<WorkoutEntity, List<ExerciseEntity>>>): Int {
        for ((workout, exercises) in workouts) {
            val workoutId = insertWorkout(workout)
            insertExercises(resolveCatalog(workoutId, exercises))
        }
        cleanupCatalog()
        return workouts.size
    }
```

- [ ] **Step 3: Репозиторий импорта**

В `ExportImportRepositoryImpl.importFromJson` заменить блок от `val existingNames = …` до `importedCount++` / `widgetUpdater.requestUpdate()`:

```kotlin
                val existingNames = dao.getAllWorkoutNames().toMutableSet()
                var skippedCount = 0

                // Тренировка без единого названного упражнения открылась бы экраном ошибки — пропускаем целиком.
                val prepared = exportData.workouts.filter { w -> w.exercises.any { it.name.isNotBlank() } }.map { exportWorkout ->
                    val uniqueName = getUniqueName(exportWorkout.name, existingNames)
                    // (тренировки, отброшенные фильтром выше, в skippedCount не входят — их упражнения пустые)
                    existingNames.add(uniqueName)
                    // Пустые названия редактор не пропускает, импорт — тоже; считаем их пропущенными.
                    val (blank, named) = exportWorkout.exercises.partition { it.name.isBlank() }
                    skippedCount += blank.size
                    WorkoutEntity(name = uniqueName, lastUseAt = exportWorkout.lastUseAt) to named.map { ex ->
                        ExerciseEntity(
                            workoutId = 0,
                            name = ex.name,
                            weight = ex.weight,
                            sets = ex.sets,
                            reps = ex.reps,
                            restTimeMillis = ex.restTimeMillis,
                            orderInWorkout = ex.order,
                            note = ex.note,
                            catalogId = 0L // проставит транзакция DAO
                        )
                    }
                }

                val importedCount = dao.importWorkouts(prepared)

                if (importedCount > 0) {
                    // (прежний комментарий про GlanceWidgetUpdater оставить как есть)
                    widgetUpdater.requestUpdate()
                }
```

и в `ImportResult(success = true, …)` передать `skippedCount = skippedCount`. Импорт `ru.hopes.workouttimer.data.entity.WorkoutEntity` вместо полного имени.

- [ ] **Step 4: Запустить**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug` — PASS.
Run (эмулятор, `WorkoutDaoTest`) — PASS, 8 тестов.

- [ ] **Step 5: Коммит**

```bash
git add app/src
git commit -m "feat: импорт одной транзакцией через справочник упражнений"
```

---

### Task 5: Запись подходов на экране выполнения

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`

**Interfaces:**
- Consumes: `FinishWorkoutSessionUseCase`, `RecordedSet`, `Exercise.catalogId/unit/extraWeight`.
- Produces:
  - `WorkoutExecutionState.Active` получает `val extraWeight: Double = exercise.extraWeight`
  - `val finishError: StateFlow<Boolean>`, `fun dismissFinishError()`
  - `internal val recordedSets: List<RecordedSet>` (для тестов)

- [ ] **Step 1: Падающие тесты**

В `WorkoutExecutionViewModelTest` (`buildViewModel` уже принимает `FinishWorkoutSessionUseCase` после Task 3) добавить:

```kotlin
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
        val vm = buildViewModel(getWorkout, repo, finish)

        vm.loadWorkout(1)
        vm.onExerciseFinished()   // подход 1 → отдых
        vm.skipRest()
        vm.onExerciseFinished()   // подход 2 — последний

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
        val vm = buildViewModel(getWorkout, mockk(relaxed = true), finish)

        vm.loadWorkout(1)
        vm.onExerciseFinished()            // присед, подход 1
        vm.moveToSelectedExercise(press)   // бросили присед
        vm.onExerciseFinished()            // жим — последнее упражнение, последний подход

        assertEquals(listOf("Присед", "Жим"), setsSlot.captured.map { it.exerciseName })
    }

    @Test
    fun `leaving without finishing records nothing`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 3))
        val finish = mockk<FinishWorkoutSessionUseCase>(relaxed = true)
        val vm = buildViewModel(getWorkout, mockk(relaxed = true), finish)

        vm.loadWorkout(1)
        vm.onExerciseFinished()

        coVerify(exactly = 0) { finish(any(), any(), any(), any(), any()) }
        assertEquals(1, vm.recordedSets.size)
    }

    @Test
    fun `edited tiles are recorded right away even if the db write is slow`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val repo = mockk<WorkoutRepository>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.updateExerciseWeightAndReps(any(), any(), any()) } coAnswers { gate.await() }
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val setsSlot = slot<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(setsSlot)) } returns Unit
        val vm = buildViewModel(getWorkout, repo, finish)

        vm.loadWorkout(1)
        vm.updateExerciseWeightAndReps(exerciseId = 1, weight = 62.5, reps = 6) // запись в БД висит
        vm.onExerciseFinished()

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
        val vm = buildViewModel(getWorkout, mockk(relaxed = true), finish)

        vm.loadWorkout(1)
        vm.onExerciseFinished()
        vm.onExerciseFinished()   // второй тап, пока запись висит
        gate.complete(Unit)

        coVerify(exactly = 1) { finish(any(), any(), any(), any(), any()) }
        assertEquals(1, vm.recordedSets.size)
    }

    @Test
    fun `failed finish keeps sets, reports error and retry does not duplicate the last set`() = runTest {
        val getWorkout = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkout(1) } returns workoutOf(ex(1, "Присед", sets = 1))
        val finish = mockk<FinishWorkoutSessionUseCase>()
        val attempts = mutableListOf<List<RecordedSet>>()
        coEvery { finish(any(), any(), any(), any(), capture(attempts)) } throws
            IllegalStateException("disk full") andThen Unit
        val vm = buildViewModel(getWorkout, mockk(relaxed = true), finish)

        vm.loadWorkout(1)
        vm.onExerciseFinished()
        assertTrue(vm.finishError.value)
        assertTrue(vm.uiState.value is WorkoutExecutionState.Active)

        vm.dismissFinishError()
        vm.onExerciseFinished()   // повтор

        assertEquals(1, attempts.last().size)
        assertTrue(vm.uiState.value is WorkoutExecutionState.Finished)
    }
```

Импорты: `kotlinx.coroutines.CompletableDeferred`, `io.mockk.coVerify`, `org.junit.Assert.assertTrue`, `ru.hopes.workouttimer.domain.model.RecordedSet`, `ru.hopes.workouttimer.domain.model.ExerciseUnit`.

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutExecutionViewModelTest*'`
Expected: FAIL — `recordedSets`/`finishError` не существуют.

- [ ] **Step 2: ViewModel**

В `WorkoutExecutionViewModel.kt`:

1. Поля:

```kotlin
    private val _recordedSets = mutableListOf<RecordedSet>()
    internal val recordedSets: List<RecordedSet> get() = _recordedSets

    // Поднимается синхронно до запуска записи: второй тап по последнему подходу,
    // пока корутина пишет сессию, ничего не делает.
    private var isFinishing = false

    // Последний подход уже в списке, но сессия не записалась: повторное нажатие
    // пробует записать снова, не добавляя подход второй раз.
    private var finishPending = false

    private val _finishError = MutableStateFlow(false)
    val finishError: StateFlow<Boolean> = _finishError.asStateFlow()

    fun dismissFinishError() {
        _finishError.value = false
    }
```

2. В `loadWorkout` при успешной загрузке: `_recordedSets.clear(); finishPending = false; isFinishing = false`.

3. `onExerciseFinished()`:

```kotlin
    fun onExerciseFinished() {
        if (isFinishing) return
        registerInteraction()
        val currentState = _uiState.value
        if (currentState is WorkoutExecutionState.Active) {
            // Подход снимается до перехода: на последнем подходе moveToNextExercise
            // сразу пишет сессию, и снимать будет уже поздно.
            val set = RecordedSet(
                catalogId = currentState.exercise.catalogId,
                exerciseName = currentState.exercise.name,
                weight = currentState.weight,
                extraWeight = currentState.extraWeight,
                reps = currentState.reps,
                unit = currentState.exercise.unit
            )
            // Повтор после сбоя записи: последний подход уже в списке — заменяем
            // его текущими значениями (плитку могли поправить), а не дублируем.
            if (finishPending && _recordedSets.isNotEmpty()) {
                _recordedSets[_recordedSets.lastIndex] = set
            } else {
                _recordedSets += set
            }
            if (currentState.currentSet < currentState.totalSets) {
                // … прежний код перехода к отдыху без изменений
            } else {
                moveToNextExercise()
            }
        }
    }
```

4. Ветка завершения в `moveToNextExercise()` (сейчас `:309-327`):

```kotlin
        } else {
            val workoutId = workout?.id ?: return
            isFinishing = true
            finishPending = true
            viewModelScope.launch {
                val finishedAt = System.currentTimeMillis()
                val rawDurationMillis = finishedAt - sessionStartedAt
                val durationMillis = (rawDurationMillis - excludedIdleMillis).coerceAtLeast(0L)
                try {
                    finishWorkoutSessionUseCase(
                        workoutId = workoutId,
                        startedAt = sessionStartedAt,
                        finishedAt = finishedAt,
                        durationMillis = durationMillis,
                        sets = _recordedSets.toList()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Подходы остаются в памяти, экран — на последнем подходе:
                    // повторное «Закончить подход» попробует записать ещё раз.
                    isFinishing = false
                    _finishError.value = true
                    return@launch
                }
                finishPending = false
                // (прежний комментарий про порядок Finished/updateLastUseAt оставить)
                _uiState.value = WorkoutExecutionState.Finished(durationMillis = durationMillis)
                workoutRepository.updateLastUseAt(workoutId)
                scheduleIdleReminderIfActive()
            }
        }
```

`exerciseIndex` в этой ветке не меняется, поэтому при сбое состояние остаётся `Active` последнего подхода. Импорт `kotlinx.coroutines.CancellationException`.

5. `moveToSelectedExercise`: после `registerInteraction()` добавить `finishPending = false` (ушли с последнего подхода — следующий закрытый подход записывается заново).

6. `updateExerciseWeightAndReps` — сначала состояние, потом база:

```kotlin
    fun updateExerciseWeightAndReps(exerciseId: Int, weight: Double, reps: Int) {
        registerInteraction()
        val index = exercises.indexOfFirst { it.id == exerciseId }
        if (index == -1) return
        val updatedExercise = exercises[index].copy(weight = weight, reps = reps)
        exercises = exercises.toMutableList().apply { set(index, updatedExercise) }

        // Сначала экран, потом база: «Закончить подход» сразу после правки
        // должен записать новые значения, а не ждать окончания записи.
        _uiState.update { state ->
            when {
                state is WorkoutExecutionState.Active && state.exercise.id == exerciseId ->
                    state.copy(exercise = updatedExercise, weight = weight, reps = reps)

                state is WorkoutExecutionState.Rest && state.exercise.id == exerciseId ->
                    state.copy(exercise = updatedExercise)

                else -> state
            }
        }
        viewModelScope.launch {
            workoutRepository.updateExerciseWeightAndReps(exerciseId, weight, reps)
        }
    }
```

7. `WorkoutExecutionState.Active` — добавить `val extraWeight: Double = exercise.extraWeight` после `reps`.

- [ ] **Step 3: Снекбар на экране выполнения**

`strings.xml`, секция «Выполнение тренировки»:

```xml
    <string name="execution_finish_error">Не удалось сохранить тренировку</string>
```

`WorkoutExecutionScreen.kt`: перед `Scaffold(`:

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    val finishError by viewModel.finishError.collectAsState()
    val finishErrorText = stringResource(R.string.execution_finish_error)
    LaunchedEffect(finishError) {
        if (finishError) {
            snackbarHostState.showSnackbar(finishErrorText)
            viewModel.dismissFinishError()
        }
    }
```

и в `Scaffold(` добавить параметр `snackbarHost = { SnackbarHost(snackbarHostState) },`. Импорты `androidx.compose.material3.SnackbarHost`, `androidx.compose.material3.SnackbarHostState`.

- [ ] **Step 4: Запустить**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: PASS (включая все прежние тесты `WorkoutExecutionViewModelTest`), lint 0 ошибок.

- [ ] **Step 5: Коммит**

```bash
git add app/src
git commit -m "feat: запись фактических подходов при завершении тренировки"
```

---

### Task 6: Проверка обновления поверх настоящей базы v7 и PR

**Files:** нет изменений кода (кроме исправлений, если проверка что-то найдёт).

- [ ] **Step 1: Полный прогон**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
~/Library/Android/sdk/platform-tools/adb devices   # только emulator-5554!
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Expected: всё зелёное (инструментальных тестов стало на 8 больше, чем на `master`).

- [ ] **Step 2: База v7 с «трудными» данными**

Собрать APK с `master` (v7) и поставить на эмулятор; наполнить базу через импорт — ввести кириллицу через `adb input` нельзя:

```bash
SCRATCH=/private/tmp/claude-501/-Users-rikarti-AndroidStudioProjects-WorkoutTimer/c52b4edf-1c73-4923-9742-ba3452f2cb24/scratchpad
git worktree add "$SCRATCH/wt-master" master
cp /Users/rikarti/AndroidStudioProjects/WorkoutTimer/local.properties "$SCRATCH/wt-master/"
(cd "$SCRATCH/wt-master" && ./gradlew :app:assembleDebug -q)
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB -s emulator-5554 uninstall ru.hopes.workouttimer || true
$ADB -s emulator-5554 install "$SCRATCH/wt-master/app/build/outputs/apk/debug/app-debug.apk"
```

Файл `$SCRATCH/e1-import.json`:

```json
{"version":1,"exportDate":"2026-10-01","appVersion":"1.0","workouts":[
 {"name":"Ноги А","lastUseAt":1,"exercises":[
   {"name":"Присед","weight":60.0,"sets":2,"reps":8,"restTimeMillis":3000,"order":1,"note":""},
   {"name":"Жим лёжа","weight":40.0,"sets":1,"reps":10,"restTimeMillis":3000,"order":2,"note":""}]},
 {"name":"Ноги Б","lastUseAt":2,"exercises":[
   {"name":"  присед ","weight":50.0,"sets":1,"reps":8,"restTimeMillis":3000,"order":1,"note":""},
   {"name":"ЖИМ ЛЕЖА","weight":45.0,"sets":1,"reps":10,"restTimeMillis":3000,"order":2,"note":""},
   {"name":"","weight":0.0,"sets":1,"reps":1,"restTimeMillis":3000,"order":3,"note":""},
   {"name":"Без названия","weight":0.0,"sets":1,"reps":1,"restTimeMillis":3000,"order":4,"note":""}]},
 {"name":"Удаляемая","lastUseAt":3,"exercises":[
   {"name":"Призрак","weight":1.0,"sets":1,"reps":1,"restTimeMillis":3000,"order":1,"note":""}]}
]}
```

Поля сверены с `ExportData`/`ExportWorkout`/`ExportExercise` на 01.10.2026. `adb push "$SCRATCH/e1-import.json" /sdcard/Download/`, в приложении: список → «Экспорт и импорт» → «Импортировать тренировки» → выбрать файл. В системном пикере идти через корень памяти устройства → `Download` (раздел «Загрузки» может не видеть файл до медиасканирования); навигация — `uiautomator dump` и тапы.

Запасной путь, если пикер не даётся: запустить сборку v7 один раз, `force-stop`, скопировать базу на хост (как в Step 3), вставить строки хостовым `sqlite3` (тренировки и упражнения из JSON выше, «Призрак» — с `workoutId` несуществующей тренировки), затем `adb push` в `/data/local/tmp/` и `adb shell run-as ru.hopes.workouttimer cp /data/local/tmp/workout_db databases/` (то же для `-wal`/`-shm`, либо удалить их после `PRAGMA wal_checkpoint(TRUNCATE)` на хосте). Затем удалить тренировку «Удаляемая» (меню → Удалить) и один раз пройти «Ноги А» до конца, чтобы в v7 была сессия.

- [ ] **Step 3: Обновление до E1**

```bash
./gradlew :app:assembleDebug
$ADB -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s emulator-5554 shell am start -n ru.hopes.workouttimer/.presentation.MainActivity
```

Проверить (скриншотами и данными). На эмуляторе (`google_apis_playstore`) `sqlite3` нет — база копируется на хост и читается хостовым `sqlite3` (есть в `~/Library/Android/sdk/platform-tools/`):

```bash
$ADB -s emulator-5554 shell am force-stop ru.hopes.workouttimer   # иначе копия может быть несогласованной
for f in workout_db workout_db-wal workout_db-shm; do
  $ADB -s emulator-5554 exec-out run-as ru.hopes.workouttimer cat databases/$f > "$SCRATCH/$f"
done
~/Library/Android/sdk/platform-tools/sqlite3 "$SCRATCH/workout_db" "SELECT name FROM exercise_catalog ORDER BY name; SELECT COUNT(*) FROM exercises WHERE catalogId = 0; PRAGMA foreign_key_check;"
```

- приложение открылось, обе тренировки и история «Ноги А» на месте;
- справочник — ровно `Без названия`, `Жим лёжа`, `Присед` (без «Призрака»); упражнений с `catalogId = 0` — 0; `foreign_key_check` пуст;
- `PRAGMA foreign_keys` в копии не проверяется (настройка живёт в соединении приложения) — что FK объявлены, видно по `foreignKeys` таблицы `session_sets` в `8.json`, а что работают — по `WorkoutDaoTest`;
- пройти «Ноги Б» до конца, снова `force-stop` и копия — в `session_sets` появились подходы с `catalogId` из справочника.

Удалить временный worktree: `git worktree remove "$SCRATCH/wt-master"`.

- [ ] **Step 4: PR**

```bash
git push -u origin feature/exercise-progress
gh pr create --base master --title "feat: справочник упражнений и запись подходов (E1)" --body-file <файл с описанием>
gh pr checks --watch
```

Описание PR (по-русски): что сделано по спеке, миграция и её проверки (JVM-тесты, обновление поверх v7 на эмуляторе с конкретными результатами), числа тестов, что внешне ничего не меняется, что дальше E2. Последняя строка: `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PR **не вливать**.
