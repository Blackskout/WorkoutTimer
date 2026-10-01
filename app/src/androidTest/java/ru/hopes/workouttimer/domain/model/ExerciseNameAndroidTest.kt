package ru.hopes.workouttimer.domain.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// Регулярка названия должна работать на Android (ICU), а не только на JVM.
@RunWith(AndroidJUnit4::class)
class ExerciseNameAndroidTest {

    @Test
    fun пробелы_схлопываются_включая_неразрывный_и_табуляцию() {
        assertEquals("Жим лёжа", normalizedExerciseName("  Жим\u00A0 \u00A0 лёжа\t"))
    }

    @Test
    fun перевод_строки_заменяется_пробелом() {
        assertEquals("Жим лёжа", normalizedExerciseName("Жим\nлёжа"))
    }

    @Test
    fun пустое_название_становится_без_названия() {
        assertEquals(UNTITLED_EXERCISE_NAME, normalizedExerciseName("   "))
    }

    @Test
    fun ключ_не_зависит_от_регистра_пробелов_и_ё() {
        assertEquals(exerciseNameKey("Жим Лёжа"), exerciseNameKey("  жим ЛЕЖА "))
    }
}
