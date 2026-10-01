package ru.hopes.workouttimer.data.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import ru.hopes.workouttimer.data.entity.ExerciseCatalogEntity
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.SessionSetEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity
import ru.hopes.workouttimer.domain.model.exerciseNameKey
import ru.hopes.workouttimer.domain.model.normalizedExerciseName

// Схема каждой версии выгружается в app/schemas: по ней MigrationTestHelper
// проверяет миграции. Выгруженные файлы коммитятся и задним числом не меняются.
@Database(
    entities = [
        WorkoutEntity::class, ExerciseEntity::class, WorkoutSessionEntity::class,
        ExerciseCatalogEntity::class, SessionSetEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}

// Миграции переопределяют вариант с SQLiteConnection: на устройстве Room
// передаёт сюда обёртку над SupportSQLiteDatabase, а в JVM-тестах —
// соединение bundled-драйвера. Так тесты гоняют ровно тот же код.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE exercises ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
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

// Один список и для приложения, и для тестов: если поднять версию базы и не
// дописать сюда миграцию, упадёт тест полного пути, а не история пользователя.
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
