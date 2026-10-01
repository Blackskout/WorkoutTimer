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
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.domain.model.ExerciseUnit

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
        val exercises = dao.getAllWorkoutsWithExercises().first()
            .single { it.workout.id.toLong() == id }.exercises.map { it.exercise }
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
}
