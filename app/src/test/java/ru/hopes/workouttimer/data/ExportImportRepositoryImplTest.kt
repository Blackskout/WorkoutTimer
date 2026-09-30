package ru.hopes.workouttimer.data

import android.content.Context
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.domain.repository.ImportError
import ru.hopes.workouttimer.domain.repository.WidgetUpdater
import java.io.ByteArrayInputStream

class ExportImportRepositoryImplTest {

    private val dao = mockk<WorkoutDao>(relaxed = true)
    private val uri = mockk<Uri>()

    private fun repository(json: String): ExportImportRepositoryImpl {
        val context = mockk<Context>()
        every { context.contentResolver.openInputStream(uri) } returns ByteArrayInputStream(json.toByteArray())
        coEvery { dao.getAllWorkoutNames() } returns emptyList()
        coEvery { dao.importWorkouts(any()) } answers { firstArg<List<*>>().size }
        return ExportImportRepositoryImpl(context, dao, mockk<WidgetUpdater>(relaxed = true))
    }

    private fun exercise(name: String, order: Int) =
        """{"name":"$name","weight":10.0,"sets":3,"reps":8,"restTimeMillis":60000,"order":$order,"note":""}"""

    private fun workout(name: String, vararg exercises: String) =
        """{"name":"$name","lastUseAt":0,"exercises":[${exercises.joinToString(",")}]}"""

    private fun file(vararg workouts: String) =
        """{"version":1,"exportDate":"2026-10-01","appVersion":"1.0","workouts":[${workouts.joinToString(",")}]}"""

    @Test
    fun `file where every workout has only blank exercises is rejected and counts them`() = runTest {
        val json = file(
            workout("A", exercise("", 0), exercise("  ", 1)),
            workout("B", exercise("", 0))
        )

        val result = repository(json).importFromJson(uri)

        assertFalse(result.success)
        assertEquals(ImportError.NoWorkouts, result.error)
        assertEquals(0, result.importedCount)
        assertEquals(3, result.skippedCount)
        coVerify(exactly = 0) { dao.importWorkouts(any()) }
    }

    @Test
    fun `dropped workout and blank exercises of kept one are both counted as skipped`() = runTest {
        val json = file(
            workout("Пустая", exercise("", 0), exercise("", 1)),
            workout("Рабочая", exercise("", 0), exercise("Присед", 1))
        )
        val saved = slot<List<Pair<WorkoutEntity, List<ExerciseEntity>>>>()
        val repo = repository(json)
        coEvery { dao.importWorkouts(capture(saved)) } answers { saved.captured.size }

        val result = repo.importFromJson(uri)

        assertTrue(result.success)
        assertEquals(1, result.importedCount)
        assertEquals(3, result.skippedCount)
        assertEquals(1, saved.captured.size)
        assertEquals(listOf("Присед"), saved.captured.single().second.map { it.name })
    }
}
