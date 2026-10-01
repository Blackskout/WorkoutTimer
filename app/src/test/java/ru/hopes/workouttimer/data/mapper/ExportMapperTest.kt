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
