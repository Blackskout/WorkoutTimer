package ru.hopes.workouttimer.data.dao

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// Миграции гоняются на JVM через bundled SQLite против схем из app/schemas.
// 5.json и 6.json восстановлены из коммитов c1fba81 и 0970bc3 — тех версий,
// что стояли на телефонах.
class MigrationTest {

    private val dbDir: Path = Files.createTempDirectory("migration-test")

    @get:Rule
    val helper = MigrationTestHelper(
        schemaDirectoryPath = SCHEMAS_DIR,
        databasePath = dbDir.resolve("workout_db"),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class
    )

    @After
    fun tearDown() {
        dbDir.toFile().deleteRecursively()
    }

    @Test
    fun `5 to 6 keeps exercises and fills note with empty string`() {
        helper.createDatabase(5).use { db ->
            insertWorkoutV5(db, id = 1, name = "Ноги", lastUseAt = 1_000L)
            db.execSQL(
                "INSERT INTO exercises (id, workoutId, name, weight, sets, reps, restTimeMillis, orderInWorkout) " +
                    "VALUES (10, 1, 'Присед', 82.5, 5, 5, 180000, 0)"
            )
        }

        helper.runMigrationsAndValidate(6, listOf(MIGRATION_5_6)).use { db ->
            db.prepare(
                "SELECT name, weight, sets, reps, restTimeMillis, orderInWorkout, note " +
                    "FROM exercises WHERE id = 10"
            ).use { stmt ->
                check(stmt.step()) { "упражнение пропало после миграции" }
                assertEquals("Присед", stmt.getText(0))
                assertEquals(82.5, stmt.getDouble(1), 0.0)
                assertEquals(5L, stmt.getLong(2))
                assertEquals(5L, stmt.getLong(3))
                assertEquals(180_000L, stmt.getLong(4))
                assertEquals(0L, stmt.getLong(5))
                assertEquals("", stmt.getText(6))
            }
        }
    }

    @Test
    fun `6 to 7 keeps workouts and creates working sessions table`() {
        helper.createDatabase(6).use { db ->
            insertWorkoutV5(db, id = 1, name = "Спина", lastUseAt = 2_000L)
            db.execSQL(
                "INSERT INTO exercises (id, workoutId, name, weight, sets, reps, restTimeMillis, orderInWorkout, note) " +
                    "VALUES (20, 1, 'Тяга', 60.0, 4, 8, 90000, 0, 'хват шире')"
            )
        }

        helper.runMigrationsAndValidate(7, listOf(MIGRATION_6_7)).use { db ->
            assertEquals(listOf("Спина"), textColumn(db, "SELECT name FROM workouts"))
            assertEquals(listOf("хват шире"), textColumn(db, "SELECT note FROM exercises WHERE id = 20"))

            db.execSQL(
                "INSERT INTO workout_sessions (workoutId, startedAt, finishedAt, durationMillis) " +
                    "VALUES (1, 100, 400, 300)"
            )
            db.prepare("SELECT id, workoutId, durationMillis FROM workout_sessions").use { stmt ->
                check(stmt.step()) { "сессия не записалась" }
                assertEquals(1L, stmt.getLong(0))
                assertEquals(1L, stmt.getLong(1))
                assertEquals(300L, stmt.getLong(2))
            }
        }
    }

    // Полный путь с самой старой поддерживаемой версии до текущей по тому же
    // списку, что и в AppModule. Новая версия без миграции уронит этот тест.
    @Test
    fun `all migrations from 5 reach current version with data intact`() {
        helper.createDatabase(5).use { db ->
            insertWorkoutV5(db, id = 1, name = "Фулбоди", lastUseAt = 3_000L)
            insertWorkoutV5(db, id = 2, name = "Кардио", lastUseAt = 4_000L)
            db.execSQL(
                "INSERT INTO exercises (id, workoutId, name, weight, sets, reps, restTimeMillis, orderInWorkout) " +
                    "VALUES (1, 1, 'Жим', 70.0, 3, 10, 120000, 0), " +
                    "(2, 1, 'Подтягивания', 0.0, 3, 12, 90000, 1)"
            )
        }

        helper.runMigrationsAndValidate(currentVersion, ALL_MIGRATIONS).use { db ->
            assertEquals(listOf("Фулбоди", "Кардио"), textColumn(db, "SELECT name FROM workouts ORDER BY id"))
            assertEquals(
                listOf("Жим", "Подтягивания"),
                textColumn(db, "SELECT name FROM exercises WHERE workoutId = 1 ORDER BY orderInWorkout")
            )
            assertEquals(listOf("", ""), textColumn(db, "SELECT note FROM exercises ORDER BY id"))
            assertEquals(listOf("0"), textColumn(db, "SELECT COUNT(*) FROM workout_sessions"))
            assertEquals(listOf("Жим", "Подтягивания"), textColumn(db, "SELECT name FROM exercise_catalog ORDER BY name"))
        }
    }

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

    // @Database не виден через рефлексию, поэтому текущую версию берём из
    // самой свежей схемы: сборка выгружает её из аннотации перед тестами.
    private val currentVersion: Int
        get() = Files.list(SCHEMAS_DIR.resolve(AppDatabase::class.java.name)).use { files ->
            files.toList().maxOf { it.fileName.toString().removeSuffix(".json").toInt() }
        }

    private fun insertWorkoutV5(db: SQLiteConnection, id: Int, name: String, lastUseAt: Long) {
        db.execSQL("INSERT INTO workouts (id, name, lastUseAt) VALUES ($id, '$name', $lastUseAt)")
    }

    private fun textColumn(db: SQLiteConnection, sql: String): List<String> =
        db.prepare(sql).use { stmt ->
            buildList { while (stmt.step()) add(stmt.getText(0)) }
        }

    private companion object {
        // Gradle запускает unit-тесты из каталога модуля app.
        val SCHEMAS_DIR: Path = Paths.get("schemas")
    }
}
