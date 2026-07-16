package ru.hopes.workouttimer.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.hopes.workouttimer.data.entity.WorkoutSessionEntity

class WorkoutSessionMapperTest {

    @Test
    fun `toDomain maps all fields and converts workoutId to Int`() {
        val entity = WorkoutSessionEntity(
            id = 5,
            workoutId = 42L,
            startedAt = 1_000L,
            finishedAt = 4_000L,
            durationMillis = 3_000L
        )

        val domain = entity.toDomain()

        assertEquals(5, domain.id)
        assertEquals(42, domain.workoutId)
        assertEquals(1_000L, domain.startedAt)
        assertEquals(4_000L, domain.finishedAt)
        assertEquals(3_000L, domain.durationMillis)
    }
}
