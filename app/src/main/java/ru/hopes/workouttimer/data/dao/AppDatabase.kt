package ru.hopes.workouttimer.data.dao

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity

// Схема каждой версии выгружается в app/schemas: по ней MigrationTestHelper
// проверяет миграции. Выгруженные файлы коммитятся и задним числом не меняются.
@Database(
    entities = [WorkoutEntity::class, ExerciseEntity::class, WorkoutSessionEntity::class],
    version = 7,
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

// Один список и для приложения, и для тестов: если поднять версию базы и не
// дописать сюда миграцию, упадёт тест полного пути, а не история пользователя.
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_5_6, MIGRATION_6_7)
