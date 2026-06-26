package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.data.mapper.toExport
import ru.hopes.workouttimer.domain.model.export.ExportData
import ru.hopes.workouttimer.domain.repository.WorkoutRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class ExportAllWorkoutsUseCase @Inject constructor(
    private val repo: WorkoutRepository
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend operator fun invoke(): String {
        return withContext(Dispatchers.IO) {
            val workoutsWithExercises = repo.getAllWorkoutsWithExercise().first()
            val workouts = workoutsWithExercises.map { it.toDomain() }
            val exportWorkouts = workouts.map { it.toExport() }

            val exportData = ExportData(
                version = 1,
                exportDate = getCurrentDateTime(),
                appVersion = "1.0",
                workouts = exportWorkouts
            )

            json.encodeToString(exportData)
        }
    }

    private fun getCurrentDateTime(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
