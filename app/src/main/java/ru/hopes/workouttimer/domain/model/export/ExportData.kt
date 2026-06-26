package ru.hopes.workouttimer.domain.model.export

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportDate: String,
    val appVersion: String,
    val workouts: List<ExportWorkout>
)
