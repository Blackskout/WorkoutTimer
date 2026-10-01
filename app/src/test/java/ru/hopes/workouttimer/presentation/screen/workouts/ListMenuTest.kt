package ru.hopes.workouttimer.presentation.screen.workouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.DELETE
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.EDIT
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.HISTORY
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.RETURN
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.SKIP
import ru.hopes.workouttimer.presentation.screen.workouts.ListMenuAction.START

class ListMenuTest {

    private fun List<ListMenuEntry>.actions() = map { it.action }
    private fun List<ListMenuEntry>.enabled(action: ListMenuAction) = single { it.action == action }.enabled

    @Test
    fun `без сессии всё доступно`() {
        val entries = listMenuEntries(targetId = 1, runningWorkoutId = null, isSearching = false)

        assertEquals(listOf(START, SKIP, EDIT, HISTORY, DELETE), entries.actions())
        assertTrue(entries.all { it.enabled })
    }

    @Test
    fun `у идущей тренировки — вернуться, правка и удаление заблокированы`() {
        val entries = listMenuEntries(targetId = 1, runningWorkoutId = 1, isSearching = false)

        assertEquals(listOf(RETURN, SKIP, EDIT, HISTORY, DELETE), entries.actions())
        assertFalse(entries.enabled(EDIT))
        assertFalse(entries.enabled(DELETE))
        assertTrue(entries.enabled(HISTORY))
    }

    @Test
    fun `у другой тренировки при идущей сессии начать скрыт, правка доступна`() {
        val entries = listMenuEntries(targetId = 2, runningWorkoutId = 1, isSearching = false)

        assertEquals(listOf(SKIP, EDIT, HISTORY, DELETE), entries.actions())
        assertTrue(entries.all { it.enabled })
    }

    @Test
    fun `в поиске пропуска нет`() {
        assertEquals(
            listOf(START, EDIT, HISTORY, DELETE),
            listMenuEntries(targetId = 1, runningWorkoutId = null, isSearching = true).actions()
        )
    }

    @Test
    fun `кнопка карточки следующей`() {
        assertEquals(HeroAction.START, heroActionOf(workoutId = 1, runningWorkoutId = null))
        assertEquals(HeroAction.RETURN, heroActionOf(workoutId = 1, runningWorkoutId = 1))
        assertNull(heroActionOf(workoutId = 1, runningWorkoutId = 2))
    }
}
