package ru.hopes.workouttimer.presentation.screen.workoutExecution

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ru.hopes.workouttimer.R

/** Русские формы «повторение/повторения/повторений» не зависят от локали устройства. */
@RunWith(AndroidJUnit4::class)
class RestRepsPluralsTest {

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext
        .createConfigurationContext(Configuration().apply { setLocale(Locale("ru")) })
        .resources

    @Test
    fun повторения_склоняются_по_русским_правилам() {
        listOf(1 to "1 повторение", 2 to "2 повторения", 5 to "5 повторений", 21 to "21 повторение").forEach { (n, text) ->
            assertEquals(text, resources.getQuantityString(R.plurals.execution_rest_reps, n, n))
        }
    }

    @Test
    fun повторения_с_весом_склоняются_так_же() {
        assertEquals("60 кг · 2 повторения", resources.getQuantityString(R.plurals.execution_rest_weight_reps, 2, "60 кг", 2))
        assertEquals("60 кг · 11 повторений", resources.getQuantityString(R.plurals.execution_rest_weight_reps, 11, "60 кг", 11))
    }
}
