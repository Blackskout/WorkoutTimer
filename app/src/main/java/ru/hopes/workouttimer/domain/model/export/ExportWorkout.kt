package ru.hopes.workouttimer.domain.model.export

import kotlinx.serialization.Serializable

@Serializable
data class ExportWorkout(
    val name: String,
    val lastUseAt: Long,
    val exercises: List<ExportExercise>
)
