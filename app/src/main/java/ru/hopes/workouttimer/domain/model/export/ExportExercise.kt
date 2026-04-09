package ru.hopes.workouttimer.domain.model.export

import kotlinx.serialization.Serializable

@Serializable
data class ExportExercise(
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeMillis: Long,
    val order: Int,
    val note: String = ""
)
