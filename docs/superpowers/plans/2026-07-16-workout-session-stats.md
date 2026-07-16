# Статистика времени тренировки — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Записывать фактическое время выполнения тренировки (wall-clock, только при полном завершении), показывать его в диалоге завершения, в списке тренировок и в отдельном экране истории сессий.

**Architecture:** Новая Room-таблица `workout_sessions`, привязанная к `workoutId`. Запись создаётся в `WorkoutExecutionViewModel` только при достижении состояния `Finished` (все упражнения пройдены). Чтение — двумя путями: агрегированный запрос "последняя сессия на тренировку" (для карточек списка, инвалидируется реактивно через Room `Flow`) и полный список сессий на тренировку (для экрана истории).

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.4, Hilt, Coroutines/Flow. Тесты: JUnit4 + MockK (новая зависимость) + kotlinx-coroutines-test (новая зависимость).

## Global Constraints

- Миграция БД 6→7 обязана быть явной (`MIGRATION_6_7`) — в проекте включён `fallbackToDestructiveMigration(dropAllTables = true)`, без явной миграции апгрейд сотрёт все данные пользователей.
- Сессия сохраняется **только** при полном завершении всех упражнений тренировки. Досрочный выход ("назад") не создаёт запись.
- "Время тренировки" = `finishedAt - startedAt` (простая wall-clock разница, без учёта пауз).
- Экспорт/импорт JSON не меняется — история сессий туда не попадает.
- Никаких новых обязательных Android-инструментальных (`androidTest`) шагов — в текущем окружении нет подключённого эмулятора/устройства, поэтому все автотесты в этом плане — JVM unit-тесты (`testDebugUnitTest`).
- Спецификация: `docs/superpowers/specs/2026-07-16-workout-session-stats-design.md`.

---

## Часть A: Данные и слой доступа

### Task 1: Room-схема — WorkoutSessionEntity, миграция 6→7, DAO

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/data/entity/WorkoutSessionEntity.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/dao/LastSessionDuration.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/dao/AppDatabase.kt:10-23`
- Modify: `app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt`

**Interfaces:**
- Produces: `WorkoutSessionEntity(id: Int, workoutId: Long, startedAt: Long, finishedAt: Long, durationMillis: Long)`, `LastSessionDuration(workoutId: Long, durationMillis: Long)`, `WorkoutDao.insertSession(session: WorkoutSessionEntity)` (suspend), `WorkoutDao.getSessionsForWorkout(workoutId: Long): Flow<List<WorkoutSessionEntity>>`, `WorkoutDao.getLastSessionDurations(): Flow<List<LastSessionDuration>>`.

Здесь нет пригодной для JUnit-без-БД логики (аннотации Room и SQL нельзя проверить без реальной SQLite/эмулятора, которых в этом окружении нет) — верификация через компиляцию и последующие задачи, которые используют эти типы через мок `WorkoutDao`.

- [ ] **Step 1: Создать `WorkoutSessionEntity`**

```kotlin
package ru.hopes.workouttimer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val workoutId: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMillis: Long
)
```

- [ ] **Step 2: Создать `LastSessionDuration`**

```kotlin
package ru.hopes.workouttimer.data.dao

data class LastSessionDuration(
    val workoutId: Long,
    val durationMillis: Long
)
```

- [ ] **Step 3: Добавить таблицу и версию 7 в `AppDatabase.kt`**

Текущее содержимое (строки 1-23):

```kotlin
package ru.hopes.workouttimer.data.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity

@Database(
    entities = [WorkoutEntity::class, ExerciseEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE exercises ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}
```

Заменить на:

```kotlin
package ru.hopes.workouttimer.data.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity

@Database(
    entities = [WorkoutEntity::class, ExerciseEntity::class, WorkoutSessionEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE exercises ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_sessions` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `workoutId` INTEGER NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `finishedAt` INTEGER NOT NULL,
                `durationMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
```

- [ ] **Step 4: Добавить методы сессий в `WorkoutDao.kt`**

Текущий импорт-блок (строки 1-10):

```kotlin
package ru.hopes.workouttimer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
```

Заменить на:

```kotlin
package ru.hopes.workouttimer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
```

Добавить в конец интерфейса `WorkoutDao` (перед закрывающей `}` файла, после `insertWorkoutWithExercises`):

```kotlin

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE workoutId = :workoutId ORDER BY finishedAt DESC")
    fun getSessionsForWorkout(workoutId: Long): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT ws.workoutId AS workoutId, ws.durationMillis AS durationMillis
        FROM workout_sessions ws
        INNER JOIN (
            SELECT workoutId, MAX(finishedAt) AS maxFinishedAt
            FROM workout_sessions
            GROUP BY workoutId
        ) latest ON ws.workoutId = latest.workoutId AND ws.finishedAt = latest.maxFinishedAt
        """
    )
    fun getLastSessionDurations(): Flow<List<LastSessionDuration>>
```

- [ ] **Step 5: Зарегистрировать миграцию в `AppModule.kt`**

Заменить:

```kotlin
import ru.hopes.workouttimer.data.dao.MIGRATION_5_6
```

на:

```kotlin
import ru.hopes.workouttimer.data.dao.MIGRATION_5_6
import ru.hopes.workouttimer.data.dao.MIGRATION_6_7
```

Заменить:

```kotlin
            .addMigrations(MIGRATION_5_6)
```

на:

```kotlin
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
```

- [ ] **Step 6: Проверить, что проект компилируется**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/data/entity/WorkoutSessionEntity.kt \
        app/src/main/java/ru/hopes/workouttimer/data/dao/LastSessionDuration.kt \
        app/src/main/java/ru/hopes/workouttimer/data/dao/WorkoutDao.kt \
        app/src/main/java/ru/hopes/workouttimer/data/dao/AppDatabase.kt \
        app/src/main/java/ru/hopes/workouttimer/di/AppModule.kt
git commit -m "feat: add workout_sessions table, migration 6->7 and session DAO queries"
```

---

### Task 2: Domain-модель `WorkoutSession` и маппер

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/model/WorkoutSession.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/data/mapper/WorkoutSessionMapper.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/mapper/WorkoutSessionMapperTest.kt`

**Interfaces:**
- Consumes: `WorkoutSessionEntity` (Task 1).
- Produces: `WorkoutSession(id: Int, workoutId: Int, startedAt: Long, finishedAt: Long, durationMillis: Long)`, `fun WorkoutSessionEntity.toDomain(): WorkoutSession`.

- [ ] **Step 1: Написать падающий тест маппера**

```kotlin
package ru.hopes.workouttimer.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity

class WorkoutSessionMapperTest {

    @Test
    fun `toDomain maps all fields and converts workoutId to Int`() {
        val entity = WorkoutSessionEntity(
            id = 5,
            workoutId = 42L,
            startedAt = 1_000L,
            finishedAt = 4_000L,
            durationMillis = 3_000L
        )

        val domain = entity.toDomain()

        assertEquals(5, domain.id)
        assertEquals(42, domain.workoutId)
        assertEquals(1_000L, domain.startedAt)
        assertEquals(4_000L, domain.finishedAt)
        assertEquals(3_000L, domain.durationMillis)
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.mapper.WorkoutSessionMapperTest"`
Expected: FAIL — ошибка компиляции `unresolved reference: toDomain` (и `WorkoutSession` ещё не существует).

- [ ] **Step 3: Создать `WorkoutSession` и маппер**

```kotlin
package ru.hopes.workouttimer.domain.model

data class WorkoutSession(
    val id: Int = 0,
    val workoutId: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val durationMillis: Long
)
```

```kotlin
package ru.hopes.workouttimer.data.mapper

import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.domain.model.WorkoutSession

fun WorkoutSessionEntity.toDomain(): WorkoutSession {
    return WorkoutSession(
        id = id,
        workoutId = workoutId.toInt(),
        startedAt = startedAt,
        finishedAt = finishedAt,
        durationMillis = durationMillis
    )
}
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.mapper.WorkoutSessionMapperTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/model/WorkoutSession.kt \
        app/src/main/java/ru/hopes/workouttimer/data/mapper/WorkoutSessionMapper.kt \
        app/src/test/java/ru/hopes/workouttimer/data/mapper/WorkoutSessionMapperTest.kt
git commit -m "feat: add WorkoutSession domain model and entity mapper"
```

---

### Task 3: Тестовые зависимости (MockK, coroutines-test) + методы сессий в `WorkoutRepository`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `WorkoutDao.insertSession/getSessionsForWorkout/getLastSessionDurations` (Task 1), `WorkoutSessionEntity.toDomain()` (Task 2).
- Produces: `WorkoutRepository.addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long): Long` (suspend, возвращает `durationMillis`), `WorkoutRepository.getSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSession>>`, `WorkoutRepository.getLastSessionDurations(): Flow<Map<Int, Long>>`.

- [ ] **Step 1: Добавить MockK и kotlinx-coroutines-test в каталог версий**

В `gradle/libs.versions.toml`, в блок `[versions]` (после строки `hiltNavigationCompose = "1.3.0"`) добавить:

```toml
mockk = "1.13.13"
kotlinxCoroutinesTest = "1.10.2"
```

В блок `[libraries]` (после строки `androidx-media = ...`) добавить:

```toml
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
```

- [ ] **Step 2: Подключить зависимости и разрешить дефолтные значения для unit-тестов в `app/build.gradle.kts`**

В блок `android { ... }` (сразу после `buildFeatures { compose = true }`) добавить:

```kotlin
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
```

В блок `dependencies { ... }` (после строки `testImplementation(libs.junit)`) добавить:

```kotlin
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: Написать падающий тест `WorkoutRepositoryImpl`**

```kotlin
package ru.hopes.workouttimer.data

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.data.dao.LastSessionDuration
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity

class WorkoutRepositoryImplTest {

    @Test
    fun `addWorkoutSession computes duration and inserts entity with converted workoutId`() = runTest {
        val dao = mockk<WorkoutDao>()
        val entitySlot = slot<WorkoutSessionEntity>()
        coEvery { dao.insertSession(capture(entitySlot)) } just io.mockk.Runs
        val repo = WorkoutRepositoryImpl(dao)

        val duration = repo.addWorkoutSession(workoutId = 7, startedAt = 1_000L, finishedAt = 6_500L)

        assertEquals(5_500L, duration)
        assertEquals(7L, entitySlot.captured.workoutId)
        assertEquals(1_000L, entitySlot.captured.startedAt)
        assertEquals(6_500L, entitySlot.captured.finishedAt)
        assertEquals(5_500L, entitySlot.captured.durationMillis)
    }

    @Test
    fun `getSessionsForWorkout maps entities to domain sessions`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.getSessionsForWorkout(7L) } returns flowOf(
            listOf(
                WorkoutSessionEntity(id = 1, workoutId = 7L, startedAt = 100L, finishedAt = 200L, durationMillis = 100L)
            )
        )
        val repo = WorkoutRepositoryImpl(dao)

        val sessions = repo.getSessionsForWorkout(7).first()

        assertEquals(1, sessions.size)
        assertEquals(7, sessions[0].workoutId)
        assertEquals(100L, sessions[0].durationMillis)
    }

    @Test
    fun `getLastSessionDurations maps to a workoutId-to-duration map`() = runTest {
        val dao = mockk<WorkoutDao>()
        every { dao.getLastSessionDurations() } returns flowOf(
            listOf(
                LastSessionDuration(workoutId = 7L, durationMillis = 5_500L),
                LastSessionDuration(workoutId = 9L, durationMillis = 2_000L)
            )
        )
        val repo = WorkoutRepositoryImpl(dao)

        val durations = repo.getLastSessionDurations().first()

        assertEquals(mapOf(7 to 5_500L, 9 to 2_000L), durations)
    }
}
```

- [ ] **Step 4: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.WorkoutRepositoryImplTest"`
Expected: FAIL — ошибка компиляции (`addWorkoutSession`, `getSessionsForWorkout(Int)`, `getLastSessionDurations` ещё не объявлены в `WorkoutRepositoryImpl`).

- [ ] **Step 5: Добавить методы в `WorkoutRepository`**

Текущее содержимое (`app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt`):

```kotlin
package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Workout

interface WorkoutRepository {
        fun getAllWorkouts(): Flow<List<WorkoutEntity>>
        fun getAllWorkoutsWithExercise(): Flow<List<WorkoutWithExercises>>
        suspend fun getWorkoutById(id: Int): Workout?
        suspend fun addWorkout(workout: Workout)
        suspend fun updateWorkout(workout: Workout)
        suspend fun deleteWorkout(workout: WorkoutEntity)
        fun searchWorkoutUseCase(query: String): Flow<List<WorkoutEntity>>
        suspend fun updateLastUseAt(workoutId: Int)
        suspend fun updateExerciseNote(exerciseId: Int, note: String)
}
```

Заменить на:

```kotlin
package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WorkoutSession

interface WorkoutRepository {
        fun getAllWorkouts(): Flow<List<WorkoutEntity>>
        fun getAllWorkoutsWithExercise(): Flow<List<WorkoutWithExercises>>
        suspend fun getWorkoutById(id: Int): Workout?
        suspend fun addWorkout(workout: Workout)
        suspend fun updateWorkout(workout: Workout)
        suspend fun deleteWorkout(workout: WorkoutEntity)
        fun searchWorkoutUseCase(query: String): Flow<List<WorkoutEntity>>
        suspend fun updateLastUseAt(workoutId: Int)
        suspend fun updateExerciseNote(exerciseId: Int, note: String)
        suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long): Long
        fun getSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSession>>
        fun getLastSessionDurations(): Flow<Map<Int, Long>>
}
```

- [ ] **Step 6: Реализовать методы в `WorkoutRepositoryImpl`**

Добавить импорт (в начало `app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt`, рядом с остальными):

```kotlin
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.domain.model.WorkoutSession
```

Добавить в конец класса `WorkoutRepositoryImpl` (перед закрывающей `}`, после `updateExerciseNote`):

```kotlin

    override suspend fun addWorkoutSession(workoutId: Int, startedAt: Long, finishedAt: Long): Long {
        val durationMillis = finishedAt - startedAt
        dao.insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId.toLong(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMillis = durationMillis
            )
        )
        return durationMillis
    }

    override fun getSessionsForWorkout(workoutId: Int): Flow<List<WorkoutSession>> {
        return dao.getSessionsForWorkout(workoutId.toLong()).map { sessions -> sessions.map { it.toDomain() } }
    }

    override fun getLastSessionDurations(): Flow<Map<Int, Long>> {
        return dao.getLastSessionDurations().map { list ->
            list.associate { it.workoutId.toInt() to it.durationMillis }
        }
    }
```

- [ ] **Step 7: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.data.WorkoutRepositoryImplTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/ru/hopes/workouttimer/domain/repository/WorkoutRepository.kt \
        app/src/main/java/ru/hopes/workouttimer/data/WorkoutRepositoryImpl.kt \
        app/src/test/java/ru/hopes/workouttimer/data/WorkoutRepositoryImplTest.kt
git commit -m "feat: add session methods to WorkoutRepository; add MockK/coroutines-test"
```

---

### Task 4: Use case'ы для сессий

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/AddWorkoutSessionUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWorkoutSessionsUseCase.kt`
- Create: `app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetLastSessionDurationsUseCase.kt`

**Interfaces:**
- Consumes: `WorkoutRepository.addWorkoutSession/getSessionsForWorkout/getLastSessionDurations` (Task 3).
- Produces: `AddWorkoutSessionUseCase.invoke(workoutId: Int, startedAt: Long, finishedAt: Long): Long` (suspend), `GetWorkoutSessionsUseCase.invoke(workoutId: Int): Flow<List<WorkoutSession>>`, `GetLastSessionDurationsUseCase.invoke(): Flow<Map<Int, Long>>`.

Это тонкие делегирующие классы — по образцу существующих `AddWorkoutUseCase`/`GetAllWorkoutsUseCase` в проекте, у которых тоже нет отдельных тестов. Корректность проверяется тестами задач 6, 8 и 10, которые используют эти use case'ы через мок.

- [ ] **Step 1: Создать `AddWorkoutSessionUseCase`**

```kotlin
package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class AddWorkoutSessionUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    suspend operator fun invoke(workoutId: Int, startedAt: Long, finishedAt: Long): Long {
        return repo.addWorkoutSession(workoutId, startedAt, finishedAt)
    }
}
```

- [ ] **Step 2: Создать `GetWorkoutSessionsUseCase`**

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class GetWorkoutSessionsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(workoutId: Int): Flow<List<WorkoutSession>> {
        return repo.getSessionsForWorkout(workoutId)
    }
}
```

- [ ] **Step 3: Создать `GetLastSessionDurationsUseCase`**

```kotlin
package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import javax.inject.Inject

class GetLastSessionDurationsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    operator fun invoke(): Flow<Map<Int, Long>> {
        return repo.getLastSessionDurations()
    }
}
```

- [ ] **Step 4: Проверить компиляцию**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/domain/usecase/AddWorkoutSessionUseCase.kt \
        app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetWorkoutSessionsUseCase.kt \
        app/src/main/java/ru/hopes/workouttimer/domain/usecase/GetLastSessionDurationsUseCase.kt
git commit -m "feat: add use cases for recording and reading workout sessions"
```

---

## Часть B: Запись сессии и экран завершения

### Task 5: `DateFormatter` — форматирование длительности и даты сессии

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/utils/DateFormatter.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/utils/DateFormatterTest.kt`

**Interfaces:**
- Produces: `DateFormatter.formatDurationToString(millis: Long): String`, `DateFormatter.formatSessionDateTime(timestamp: Long): String`.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package ru.hopes.workouttimer.presentation.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DateFormatterTest {

    @Test
    fun `formatDurationToString shows only minutes under an hour`() {
        assertEquals("42 мин", DateFormatter.formatDurationToString(42 * 60_000L))
    }

    @Test
    fun `formatDurationToString shows hours and minutes over an hour`() {
        assertEquals("1 ч 15 мин", DateFormatter.formatDurationToString(75 * 60_000L))
    }

    @Test
    fun `formatDurationToString rounds down partial minutes`() {
        assertEquals("0 мин", DateFormatter.formatDurationToString(59_000L))
    }

    @Test
    fun `formatSessionDateTime formats date and time in ru locale`() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JULY, 16, 18, 32, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val result = DateFormatter.formatSessionDateTime(calendar.timeInMillis)

        assertEquals("16 июля 2026, 18:32", result)
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.utils.DateFormatterTest"`
Expected: FAIL — ошибка компиляции (`formatDurationToString`/`formatSessionDateTime` ещё не объявлены).

- [ ] **Step 3: Реализовать функции**

Текущее содержимое `DateFormatter.kt`:

```kotlin
package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

object DateFormatter {

    private val milesInHour = TimeUnit.HOURS.toMillis(1)
    private val milesInDay = TimeUnit.DAYS.toMillis(1)
    private val formatter = SimpleDateFormat.getDateInstance(DateFormat.SHORT)


    fun formatCurrentDate(): String {
        return formatter.format(System.currentTimeMillis())
    }

    @Composable
    fun formatDateToString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < milesInHour -> stringResource(R.string.just_now)
            diff < milesInDay -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                stringResource(R.string.h_ago, hours)
            }

            else -> {
                formatter.format(timestamp)
            }
        }
    }
}
```

Заменить на:

```kotlin
package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateFormatter {

    private val milesInHour = TimeUnit.HOURS.toMillis(1)
    private val milesInDay = TimeUnit.DAYS.toMillis(1)
    private val formatter = SimpleDateFormat.getDateInstance(DateFormat.SHORT)
    private val sessionDateTimeFormatter = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))


    fun formatCurrentDate(): String {
        return formatter.format(System.currentTimeMillis())
    }

    @Composable
    fun formatDateToString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < milesInHour -> stringResource(R.string.just_now)
            diff < milesInDay -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                stringResource(R.string.h_ago, hours)
            }

            else -> {
                formatter.format(timestamp)
            }
        }
    }

    fun formatDurationToString(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "$hours ч $minutes мин"
        } else {
            "$minutes мин"
        }
    }

    fun formatSessionDateTime(timestamp: Long): String {
        return sessionDateTimeFormatter.format(timestamp)
    }
}
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.utils.DateFormatterTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/utils/DateFormatter.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/utils/DateFormatterTest.kt
git commit -m "feat: add duration and session date-time formatting to DateFormatter"
```

---

### Task 6: `WorkoutExecutionViewModel` — запись сессии и `Finished(durationMillis)`

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt`

**Interfaces:**
- Consumes: `AddWorkoutSessionUseCase.invoke(workoutId, startedAt, finishedAt): Long` (Task 4).
- Produces: `WorkoutExecutionState.Finished(val durationMillis: Long)` (заменяет `data object Finished`) — используется в Task 7.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutExecution

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import ru.hopes.workouttimer.domain.usecase.AddWorkoutSessionUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.presentation.utils.SoundPlayer
import ru.hopes.workouttimer.presentation.utils.VibrationManager
import ru.hopes.workouttimer.presentation.utils.WakeLockHelper

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutExecutionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
        workoutRepository: WorkoutRepository,
        addWorkoutSessionUseCase: AddWorkoutSessionUseCase
    ): WorkoutExecutionViewModel {
        return WorkoutExecutionViewModel(
            context = mockk<Context>(relaxed = true),
            soundPlayer = mockk<SoundPlayer>(relaxed = true),
            getWorkoutByIdUseCase = getWorkoutByIdUseCase,
            vibrationManager = mockk<VibrationManager>(relaxed = true),
            wakeLockHelper = mockk<WakeLockHelper>(relaxed = true),
            workoutRepository = workoutRepository,
            addWorkoutSessionUseCase = addWorkoutSessionUseCase
        )
    }

    @Test
    fun `finishing the only exercise saves a session and exposes its duration`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 1, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>()
        coEvery { workoutRepository.updateLastUseAt(1) } returns Unit
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()
        coEvery {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any())
        } returns 1_234L

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)
        viewModel.onExerciseFinished()

        val state = viewModel.uiState.value
        assertTrue(state is WorkoutExecutionState.Finished)
        assertEquals(1_234L, (state as WorkoutExecutionState.Finished).durationMillis)
        coVerify(exactly = 1) {
            addWorkoutSessionUseCase(workoutId = 1, startedAt = any(), finishedAt = any())
        }
    }

    @Test
    fun `loading a workout does not save a session by itself`() = runTest {
        val exercise = Exercise(id = 1, name = "Push", weight = 10.0, sets = 2, reps = 5, timeMillis = 1_000, order = 1)
        val workout = Workout(id = 1, name = "Test", exercises = listOf(exercise), lastUseAt = 0L)
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(1) } returns workout
        val workoutRepository = mockk<WorkoutRepository>(relaxed = true)
        val addWorkoutSessionUseCase = mockk<AddWorkoutSessionUseCase>()

        val viewModel = buildViewModel(getWorkoutByIdUseCase, workoutRepository, addWorkoutSessionUseCase)
        viewModel.loadWorkout(1)

        coVerify(exactly = 0) {
            addWorkoutSessionUseCase(workoutId = any(), startedAt = any(), finishedAt = any())
        }
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: FAIL — ошибка компиляции (конструктор `WorkoutExecutionViewModel` ещё не принимает `addWorkoutSessionUseCase`, `Finished` ещё не принимает `durationMillis`).

- [ ] **Step 3: Добавить зависимость и поле старта сессии**

Заменить блок конструктора (строки 30-42):

```kotlin
@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soundPlayer: SoundPlayer,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val vibrationManager: VibrationManager,
    private val wakeLockHelper: WakeLockHelper,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private var workout: Workout? = null
    var exercises: List<Exercise> = emptyList()
    private var exerciseIndex = 0
```

на:

```kotlin
@HiltViewModel
class WorkoutExecutionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soundPlayer: SoundPlayer,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val vibrationManager: VibrationManager,
    private val wakeLockHelper: WakeLockHelper,
    private val workoutRepository: WorkoutRepository,
    private val addWorkoutSessionUseCase: AddWorkoutSessionUseCase
) : ViewModel() {

    private var workout: Workout? = null
    var exercises: List<Exercise> = emptyList()
    private var exerciseIndex = 0
    private var sessionStartedAt: Long = 0L
```

Добавить импорт (рядом с остальными `domain.usecase` импортами):

```kotlin
import ru.hopes.workouttimer.domain.usecase.AddWorkoutSessionUseCase
```

- [ ] **Step 4: Зафиксировать старт сессии в `loadWorkout`**

Заменить:

```kotlin
            if (loadedWorkout != null && loadedWorkout.exercises.isNotEmpty()) {
                workout = loadedWorkout
                exercises = loadedWorkout.exercises.sortedBy { it.order }
                exerciseIndex = 0
                
                // Начинаем с первого упражнения в состоянии Rest
```

на:

```kotlin
            if (loadedWorkout != null && loadedWorkout.exercises.isNotEmpty()) {
                workout = loadedWorkout
                exercises = loadedWorkout.exercises.sortedBy { it.order }
                exerciseIndex = 0
                sessionStartedAt = System.currentTimeMillis()

                // Начинаем с первого упражнения в состоянии Rest
```

- [ ] **Step 5: Сохранить сессию и передать длительность в `moveToNextExercise`**

Заменить:

```kotlin
    private fun moveToNextExercise() {
        if (exerciseIndex < exercises.size - 1) {
            exerciseIndex++
            val nextExercise = exercises[exerciseIndex]
            _uiState.value = WorkoutExecutionState.Rest(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets,
                restTimeMillis = nextExercise.timeMillis,
                totalRestTimeMillis = nextExercise.timeMillis
            )
            startRestTimer()
        } else {
            workout?.id?.let { id ->
                viewModelScope.launch { workoutRepository.updateLastUseAt(id) }
            }
            _uiState.value = WorkoutExecutionState.Finished
        }
    }
```

на:

```kotlin
    private fun moveToNextExercise() {
        if (exerciseIndex < exercises.size - 1) {
            exerciseIndex++
            val nextExercise = exercises[exerciseIndex]
            _uiState.value = WorkoutExecutionState.Rest(
                exercise = nextExercise,
                currentSet = 1,
                totalSets = nextExercise.sets,
                restTimeMillis = nextExercise.timeMillis,
                totalRestTimeMillis = nextExercise.timeMillis
            )
            startRestTimer()
        } else {
            val workoutId = workout?.id ?: return
            viewModelScope.launch {
                workoutRepository.updateLastUseAt(workoutId)
                val finishedAt = System.currentTimeMillis()
                val durationMillis = addWorkoutSessionUseCase(
                    workoutId = workoutId,
                    startedAt = sessionStartedAt,
                    finishedAt = finishedAt
                )
                _uiState.value = WorkoutExecutionState.Finished(durationMillis = durationMillis)
            }
        }
    }
```

- [ ] **Step 6: Изменить `WorkoutExecutionState.Finished` на data class**

Заменить:

```kotlin
    data object Finished : WorkoutExecutionState()
```

на:

```kotlin
    data class Finished(val durationMillis: Long) : WorkoutExecutionState()
```

- [ ] **Step 7: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModel.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionViewModelTest.kt
git commit -m "feat: save workout session on full completion, expose duration in Finished state"
```

---

### Task 7: Диалог "Тренировка завершена"

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt`

**Interfaces:**
- Consumes: `WorkoutExecutionState.Finished(durationMillis: Long)` (Task 6), `DateFormatter.formatDurationToString(Long): String` (Task 5).

Compose UI без instrumentation-окружения автоматическим тестом не проверяется — верификация вручную (Step 3).

- [ ] **Step 1: Убрать автонавигацию и добавить импорт `DateFormatter`**

Заменить:

```kotlin
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.components.SystemMediaControllerCompat
import ru.hopes.workouttimer.presentation.utils.toCorrectNum
```

на:

```kotlin
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.components.SystemMediaControllerCompat
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.toCorrectNum
```

Заменить:

```kotlin
    // Обрабатываем завершение тренировки
    LaunchedEffect(uiState) {
        if (uiState is WorkoutExecutionState.Finished) {
            onExerciseCompleted()
        }
    }
```

на:

```kotlin
```

(блок удаляется целиком — навигация теперь происходит по кнопке "ОК" в диалоге завершения).

- [ ] **Step 2: Показать пустой блок вместо `LoadingState()` для `Finished` во внутреннем `when` и добавить диалог завершения**

Заменить:

```kotlin
                        is WorkoutExecutionState.Finished -> {
                            // Это состояние обрабатывается в LaunchedEffect выше
                            LoadingState()
                        }
```

на:

```kotlin
                        is WorkoutExecutionState.Finished -> {
                            // Отображается диалогом завершения ниже
                        }
```

Заменить:

```kotlin
    // Диалог редактирования заметки
    currentEditingExercise?.let { exercise ->
        if (showNoteDialog) {
            NoteEditDialog(
                exercise = exercise,
                onDismiss = { showNoteDialog = false },
                onSave = { note ->
                    viewModel.updateExerciseNote(exercise.id, note)
                    showNoteDialog = false
                }
            )
        }
    }
}
```

на:

```kotlin
    // Диалог редактирования заметки
    currentEditingExercise?.let { exercise ->
        if (showNoteDialog) {
            NoteEditDialog(
                exercise = exercise,
                onDismiss = { showNoteDialog = false },
                onSave = { note ->
                    viewModel.updateExerciseNote(exercise.id, note)
                    showNoteDialog = false
                }
            )
        }
    }

    // Диалог завершения тренировки
    val finishedState = uiState as? WorkoutExecutionState.Finished
    if (finishedState != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Тренировка завершена") },
            text = { Text("Время: ${DateFormatter.formatDurationToString(finishedState.durationMillis)}") },
            confirmButton = {
                TextButton(onClick = onExerciseCompleted) {
                    Text("ОК")
                }
            }
        )
    }
}
```

- [ ] **Step 3: Ручная проверка**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

Установить APK, пройти тренировку до конца, убедиться, что после последнего упражнения появляется диалог "Тренировка завершена" с текстом вида "Время: N мин", и что экран закрывается только по нажатию "ОК".

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutExecution/WorkoutExecutionScreen.kt
git commit -m "feat: show workout completion dialog with total duration"
```

---

## Часть C: Карточка в списке тренировок

### Task 8: `ListWorkoutState`/`ListWorkoutViewModel` — последняя длительность на тренировку

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModelTest.kt`

**Interfaces:**
- Consumes: `GetLastSessionDurationsUseCase.invoke(): Flow<Map<Int, Long>>` (Task 4).
- Produces: `ListWorkoutState.lastSessionDurations: Map<Int, Long>` — используется в Task 9.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ListWorkoutViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state combines workouts with their last session durations`() = runTest {
        val workouts = listOf(WorkoutEntity(id = 1, name = "Leg day", lastUseAt = 0L))
        val getAllWorkoutsUseCase = mockk<GetAllWorkoutsUseCase>()
        every { getAllWorkoutsUseCase() } returns flowOf(workouts)
        val getLastSessionDurationsUseCase = mockk<GetLastSessionDurationsUseCase>()
        every { getLastSessionDurationsUseCase() } returns flowOf(mapOf(1 to 2_500L))

        val viewModel = ListWorkoutViewModel(
            getAllWorkoutsUseCase = getAllWorkoutsUseCase,
            searchWorkoutsUseCase = mockk(relaxed = true),
            addWorkoutUseCase = mockk(relaxed = true),
            getWorkoutByIdUseCase = mockk(relaxed = true),
            deleteWorkoutUseCase = mockk(relaxed = true),
            getLastSessionDurationsUseCase = getLastSessionDurationsUseCase,
            savedStateHandle = SavedStateHandle()
        )

        val state = viewModel.state.value

        assertEquals(workouts, state.workouts)
        assertEquals(mapOf(1 to 2_500L), state.lastSessionDurations)
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutViewModelTest"`
Expected: FAIL — ошибка компиляции (конструктор `ListWorkoutViewModel` ещё не принимает `getLastSessionDurationsUseCase`, `ListWorkoutState` ещё не содержит `lastSessionDurations`).

- [ ] **Step 3: Добавить параметр конструктора и импорт**

Заменить:

```kotlin
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.DeleteWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.SearchWorkoutsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListWorkoutViewModel @Inject constructor(
    private val getAllWorkoutsUseCase: GetAllWorkoutsUseCase,
    private val searchWorkoutsUseCase: SearchWorkoutsUseCase,
    private val addWorkoutUseCase: AddWorkoutUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val deleteWorkoutUseCase: DeleteWorkoutUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
```

на:

```kotlin
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.usecase.AddWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.DeleteWorkoutUseCase
import ru.hopes.workouttimer.domain.usecase.GetAllWorkoutsUseCase
import ru.hopes.workouttimer.domain.usecase.GetLastSessionDurationsUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.SearchWorkoutsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListWorkoutViewModel @Inject constructor(
    private val getAllWorkoutsUseCase: GetAllWorkoutsUseCase,
    private val searchWorkoutsUseCase: SearchWorkoutsUseCase,
    private val addWorkoutUseCase: AddWorkoutUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase,
    private val deleteWorkoutUseCase: DeleteWorkoutUseCase,
    private val getLastSessionDurationsUseCase: GetLastSessionDurationsUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
```

- [ ] **Step 4: Скомбинировать потоки в `init`**

Заменить:

```kotlin
        query
            .onEach { input ->
                _state.update { it.copy(query = input) }
            }
            .flatMapLatest { input ->
                if (input.isBlank()) {
                    getAllWorkoutsUseCase()
                } else {
                    searchWorkoutsUseCase(input)
                }
            }.onEach { workouts ->
                _state.update { it.copy(workouts = workouts) }
            }
            .launchIn(viewModelScope)
```

на:

```kotlin
        query
            .onEach { input ->
                _state.update { it.copy(query = input) }
            }
            .flatMapLatest { input ->
                val workoutsFlow = if (input.isBlank()) {
                    getAllWorkoutsUseCase()
                } else {
                    searchWorkoutsUseCase(input)
                }
                workoutsFlow.combine(getLastSessionDurationsUseCase()) { workouts, durations ->
                    workouts to durations
                }
            }.onEach { (workouts, durations) ->
                _state.update { it.copy(workouts = workouts, lastSessionDurations = durations) }
            }
            .launchIn(viewModelScope)
```

Добавить импорт (рядом с остальными `kotlinx.coroutines.flow` импортами):

```kotlin
import kotlinx.coroutines.flow.combine
```

- [ ] **Step 5: Добавить поле в `ListWorkoutState`**

Заменить:

```kotlin
data class ListWorkoutState(
    val query: String = "",
    val workouts: List<WorkoutEntity> = listOf()
)
```

на:

```kotlin
data class ListWorkoutState(
    val query: String = "",
    val workouts: List<WorkoutEntity> = listOf(),
    val lastSessionDurations: Map<Int, Long> = emptyMap()
)
```

- [ ] **Step 6: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModel.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutViewModelTest.kt
git commit -m "feat: combine last session durations into ListWorkoutState"
```

---

### Task 9: `WorkoutCard` — отображение длительности последней сессии

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt`

**Interfaces:**
- Consumes: `ListWorkoutState.lastSessionDurations: Map<Int, Long>` (Task 8), `DateFormatter.formatDurationToString(Long): String` (Task 5).

Compose UI без instrumentation-окружения не тестируется автоматически — верификация вручную (Step 4).

- [ ] **Step 1: Добавить импорты `Column` и `Alignment`**

Заменить:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
```

на:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
```

Заменить:

```kotlin
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
```

на:

```kotlin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
```

- [ ] **Step 2: Передать длительность в `WorkoutCard` из списка**

Заменить:

```kotlin
                WorkoutCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    workout = workout,
                    onWorkoutClick = onWorkoutClick,
                    onLongClick = onLongClick,
                    onEditClick = onEditClick,
                    onDeleteClick = {
                        workoutToDelete = it
                    },
                    backgroundColor = MaterialTheme.colorScheme.surface
                )
```

на:

```kotlin
                WorkoutCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    workout = workout,
                    lastSessionDurationMillis = state.lastSessionDurations[workout.id],
                    onWorkoutClick = onWorkoutClick,
                    onLongClick = onLongClick,
                    onEditClick = onEditClick,
                    onDeleteClick = {
                        workoutToDelete = it
                    },
                    backgroundColor = MaterialTheme.colorScheme.surface
                )
```

- [ ] **Step 3: Добавить параметр и вторую строку в `WorkoutCard`**

Заменить:

```kotlin
@Composable
fun WorkoutCard(
    modifier: Modifier = Modifier,
    workout: WorkoutEntity,
    backgroundColor: Color,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onDeleteClick: (WorkoutEntity) -> Unit = {}
) {
```

на:

```kotlin
@Composable
fun WorkoutCard(
    modifier: Modifier = Modifier,
    workout: WorkoutEntity,
    backgroundColor: Color,
    lastSessionDurationMillis: Long? = null,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onDeleteClick: (WorkoutEntity) -> Unit = {}
) {
```

Заменить:

```kotlin
            Text(
                modifier = Modifier.weight(1f),
                text = DateFormatter.formatDateToString(workout.lastUseAt),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
```

на:

```kotlin
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = DateFormatter.formatDateToString(workout.lastUseAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastSessionDurationMillis != null) {
                    Text(
                        text = DateFormatter.formatDurationToString(lastSessionDurationMillis),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
```

- [ ] **Step 4: Ручная проверка**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

Установить APK, завершить тренировку до конца, вернуться в список — под датой должна появиться длительность последней сессии. Для тренировки без завершённых сессий вторая строка не показывается.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt
git commit -m "feat: show last session duration on workout card"
```

---

## Часть D: Экран истории сессий

### Task 10: `WorkoutHistoryViewModel`

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryViewModel.kt`
- Test: `app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `GetWorkoutSessionsUseCase.invoke(workoutId: Int): Flow<List<WorkoutSession>>`, `GetWorkoutByIdUseCase.invoke(id: Int): Workout?` (Task 4 / existing).
- Produces: `WorkoutHistoryViewModel.loadHistory(workoutId: Int)`, `WorkoutHistoryState(workoutName: String, sessions: List<WorkoutSession>)` — используется в Task 11.

Именно `loadHistory(workoutId)` (а не `SavedStateHandle`) выбран, чтобы соответствовать уже используемому в проекте паттерну (см. `WorkoutExecutionViewModel.loadWorkout`, `CreateWorkoutScreen` — id тренировки достаётся в `NavGraph` и передаётся экрану как параметр, а не через Hilt-инъекцию nav-аргументов).

- [ ] **Step 1: Написать падающий тест**

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutHistory

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.hopes.workouttimer.domain.model.Workout
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadHistory exposes workout name and sessions`() = runTest {
        val workout = Workout(id = 3, name = "Leg day", exercises = emptyList(), lastUseAt = 0L)
        val sessions = listOf(
            WorkoutSession(id = 1, workoutId = 3, startedAt = 1_000L, finishedAt = 4_000L, durationMillis = 3_000L)
        )
        val getWorkoutByIdUseCase = mockk<GetWorkoutByIdUseCase>()
        coEvery { getWorkoutByIdUseCase(3) } returns workout
        val getWorkoutSessionsUseCase = mockk<GetWorkoutSessionsUseCase>()
        every { getWorkoutSessionsUseCase(3) } returns flowOf(sessions)

        val viewModel = WorkoutHistoryViewModel(getWorkoutSessionsUseCase, getWorkoutByIdUseCase)
        viewModel.loadHistory(3)

        val state = viewModel.state.value
        assertEquals("Leg day", state.workoutName)
        assertEquals(sessions, state.sessions)
    }
}
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutHistory.WorkoutHistoryViewModelTest"`
Expected: FAIL — ошибка компиляции (`WorkoutHistoryViewModel` ещё не существует).

- [ ] **Step 3: Реализовать `WorkoutHistoryViewModel`**

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutHistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.domain.usecase.GetWorkoutByIdUseCase
import ru.hopes.workouttimer.domain.usecase.GetWorkoutSessionsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val getWorkoutSessionsUseCase: GetWorkoutSessionsUseCase,
    private val getWorkoutByIdUseCase: GetWorkoutByIdUseCase
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
    }
}

data class WorkoutHistoryState(
    val workoutName: String = "",
    val sessions: List<WorkoutSession> = emptyList()
)
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.hopes.workouttimer.presentation.screen.workoutHistory.WorkoutHistoryViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryViewModel.kt \
        app/src/test/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryViewModelTest.kt
git commit -m "feat: add WorkoutHistoryViewModel"
```

---

### Task 11: `WorkoutHistoryScreen`

**Files:**
- Create: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryScreen.kt`

**Interfaces:**
- Consumes: `WorkoutHistoryViewModel.loadHistory(workoutId: Int)`, `WorkoutHistoryViewModel.state: StateFlow<WorkoutHistoryState>` (Task 10), `DateFormatter.formatSessionDateTime(Long): String` / `formatDurationToString(Long): String` (Task 5).
- Produces: `@Composable fun WorkoutHistoryScreen(viewModel: WorkoutHistoryViewModel = hiltViewModel(), workoutId: Int, onNavigateBack: () -> Unit)` — используется в Task 12.

Compose UI без instrumentation-окружения не тестируется автоматически — верификация вручную выполняется в Task 12 (Step 4), вместе с проверкой навигации.

- [ ] **Step 1: Создать файл экрана**

```kotlin
package ru.hopes.workouttimer.presentation.screen.workoutHistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.presentation.utils.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
    workoutId: Int,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(workoutId) {
        viewModel.loadHistory(workoutId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = state.workoutName.ifEmpty { "История" }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "История пуста",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRow(session)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkoutSession) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = DateFormatter.formatSessionDateTime(session.finishedAt),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = DateFormatter.formatDurationToString(session.durationMillis),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workoutHistory/WorkoutHistoryScreen.kt
git commit -m "feat: add WorkoutHistoryScreen"
```

---

### Task 12: Пункт меню "История" и навигация

**Files:**
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt`
- Modify: `app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `WorkoutHistoryScreen(viewModel, workoutId, onNavigateBack)` (Task 11).

- [ ] **Step 1: Добавить иконку `History` и параметр `onHistoryClick` в `ListWorkoutScreen.kt`**

Заменить:

```kotlin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
```

на:

```kotlin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
```

Заменить сигнатуру `ListWorkoutScreen`:

```kotlin
fun ListWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: ListWorkoutViewModel = hiltViewModel(),
    onAddWorkoutClick: () -> Unit,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onExportImportClick: () -> Unit = {}
) {
```

на:

```kotlin
fun ListWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: ListWorkoutViewModel = hiltViewModel(),
    onAddWorkoutClick: () -> Unit,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onExportImportClick: () -> Unit = {},
    onHistoryClick: (WorkoutEntity) -> Unit = {}
) {
```

Заменить вызов `WorkoutCard` (добавлен на Task 9, сейчас содержит `lastSessionDurationMillis`):

```kotlin
                WorkoutCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    workout = workout,
                    lastSessionDurationMillis = state.lastSessionDurations[workout.id],
                    onWorkoutClick = onWorkoutClick,
                    onLongClick = onLongClick,
                    onEditClick = onEditClick,
                    onDeleteClick = {
                        workoutToDelete = it
                    },
                    backgroundColor = MaterialTheme.colorScheme.surface
                )
```

на:

```kotlin
                WorkoutCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    workout = workout,
                    lastSessionDurationMillis = state.lastSessionDurations[workout.id],
                    onWorkoutClick = onWorkoutClick,
                    onLongClick = onLongClick,
                    onEditClick = onEditClick,
                    onDeleteClick = {
                        workoutToDelete = it
                    },
                    onHistoryClick = onHistoryClick,
                    backgroundColor = MaterialTheme.colorScheme.surface
                )
```

- [ ] **Step 2: Добавить пункт меню в `WorkoutCard`**

Заменить сигнатуру `WorkoutCard` (после изменений Task 9):

```kotlin
@Composable
fun WorkoutCard(
    modifier: Modifier = Modifier,
    workout: WorkoutEntity,
    backgroundColor: Color,
    lastSessionDurationMillis: Long? = null,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onDeleteClick: (WorkoutEntity) -> Unit = {}
) {
```

на:

```kotlin
@Composable
fun WorkoutCard(
    modifier: Modifier = Modifier,
    workout: WorkoutEntity,
    backgroundColor: Color,
    lastSessionDurationMillis: Long? = null,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onLongClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onDeleteClick: (WorkoutEntity) -> Unit = {},
    onHistoryClick: (WorkoutEntity) -> Unit = {}
) {
```

Заменить блок `DropdownMenu`:

```kotlin
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Редактировать") },
                onClick = {
                    showMenu = false
                    onEditClick(workout)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = {
                    showMenu = false
                    onDeleteClick(workout)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
```

на:

```kotlin
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Редактировать") },
                onClick = {
                    showMenu = false
                    onEditClick(workout)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("История") },
                onClick = {
                    showMenu = false
                    onHistoryClick(workout)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = {
                    showMenu = false
                    onDeleteClick(workout)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
```

- [ ] **Step 3: Добавить route и связать экраны в `NavGraph.kt`**

Заменить импорты:

```kotlin
import ru.hopes.workouttimer.presentation.screen.creation.CreateWorkoutScreen
import ru.hopes.workouttimer.presentation.screen.exportImport.ExportImportScreen
import ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionScreen
import ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutScreen
```

на:

```kotlin
import ru.hopes.workouttimer.presentation.screen.creation.CreateWorkoutScreen
import ru.hopes.workouttimer.presentation.screen.exportImport.ExportImportScreen
import ru.hopes.workouttimer.presentation.screen.workoutExecution.WorkoutExecutionScreen
import ru.hopes.workouttimer.presentation.screen.workoutHistory.WorkoutHistoryScreen
import ru.hopes.workouttimer.presentation.screen.workouts.ListWorkoutScreen
```

Заменить вызов `ListWorkoutScreen` в `NavHost`:

```kotlin
        composable(Screen.Workouts.route) {
            ListWorkoutScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(),
                onAddWorkoutClick = {
                    navController.navigate(Screen.CreateWorkout.route)
                },
                onLongClick = {
                    // TODO
                },
                // КОГДА КЛИКНУЛИ:
                // Мы собираем ссылку вручную: "execution_screen/5"
                onWorkoutClick = { workout ->
                    navController.navigate(Screen.Execution.createRoute(workout.id))
                },
                onEditClick = { workout ->
                    navController.navigate(Screen.EditWorkout.createRoute(workout.id))
                },
                onExportImportClick = {
                    navController.navigate(Screen.ExportImport.route)
                }
            )
        }
```

на:

```kotlin
        composable(Screen.Workouts.route) {
            ListWorkoutScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = hiltViewModel(),
                onAddWorkoutClick = {
                    navController.navigate(Screen.CreateWorkout.route)
                },
                onLongClick = {
                    // TODO
                },
                // КОГДА КЛИКНУЛИ:
                // Мы собираем ссылку вручную: "execution_screen/5"
                onWorkoutClick = { workout ->
                    navController.navigate(Screen.Execution.createRoute(workout.id))
                },
                onEditClick = { workout ->
                    navController.navigate(Screen.EditWorkout.createRoute(workout.id))
                },
                onExportImportClick = {
                    navController.navigate(Screen.ExportImport.route)
                },
                onHistoryClick = { workout ->
                    navController.navigate(Screen.History.createRoute(workout.id))
                }
            )
        }
```

Заменить блок экспорта/импорта, добавив после него новый route:

```kotlin
        // Экран экспорта/импорта
        composable(Screen.ExportImport.route) {
            ExportImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
```

на:

```kotlin
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
                }
            )
        }
    }
}
```

Заменить объявление `Screen.Execution` (конец файла), добавив `Screen.History` рядом:

```kotlin
    // ВАЖНО: Маршрут должен содержать placeholder {workout_id}
    data object Execution : Screen("execution/{workout_id}") {

        // ВАЖНО: Формируем ссылку, которая совпадает с названием экрана (было "edit_note", стало "execution")
        fun createRoute(workoutId: Int): String {
            return "execution/$workoutId"
        }

        fun getWorkoutId(arguments: Bundle?): Int {
            return arguments?.getInt("workout_id") ?: 0
        }
    }
}
```

на:

```kotlin
    // ВАЖНО: Маршрут должен содержать placeholder {workout_id}
    data object Execution : Screen("execution/{workout_id}") {

        // ВАЖНО: Формируем ссылку, которая совпадает с названием экрана (было "edit_note", стало "execution")
        fun createRoute(workoutId: Int): String {
            return "execution/$workoutId"
        }

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
}
```

- [ ] **Step 4: Ручная сквозная проверка**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

Установить APK и пройти полный сценарий:
1. Завершить тренировку целиком → диалог "Тренировка завершена" с временем → "ОК" → возврат в список.
2. В списке под тренировкой видна длительность последней сессии.
3. Долгий тап по карточке → "История" → открывается список сессий (дата + время, длительность), самая новая сверху.
4. Для тренировки без сессий — "История пуста".
5. Досрочный выход из тренировки (кнопка "Назад" на экране выполнения до последнего упражнения) — сессия НЕ появляется в истории.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/hopes/workouttimer/presentation/screen/workouts/ListWorkoutScreen.kt \
        app/src/main/java/ru/hopes/workouttimer/presentation/navigation/NavGraph.kt
git commit -m "feat: wire workout session history screen into navigation"
```
