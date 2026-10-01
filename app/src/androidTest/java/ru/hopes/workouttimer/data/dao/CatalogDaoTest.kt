package ru.hopes.workouttimer.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun завершение_сессии_с_неизвестной_записью_создаёт_её_с_единицей_плиты() = runBlocking {
        val workoutId = saveWorkout("Спина", exercise("Тяга блока"))

        finish(workoutId, 100, SessionSetDraft(9_999L, "Новая тяга", 5.0, 2.0, 12, "PLATE"))

        val created = dao.findCatalogByKey(exerciseNameKey("Новая тяга"))!!
        assertEquals("PLATE", created.unit)
        assertEquals(created.id, dao.observeSetsForWorkout(workoutId).first().single().catalogId)
    }

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
}
