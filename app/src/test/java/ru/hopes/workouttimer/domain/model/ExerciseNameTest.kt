package ru.hopes.workouttimer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseNameTest {

    @Test
    fun `normalized name trims edges and collapses inner whitespace including nbsp`() {
        assertEquals("Жим лёжа", normalizedExerciseName("  Жим    лёжа\t"))
    }

    @Test
    fun `blank name becomes untitled`() {
        assertEquals(UNTITLED_EXERCISE_NAME, normalizedExerciseName(""))
        assertEquals(UNTITLED_EXERCISE_NAME, normalizedExerciseName("   "))
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
