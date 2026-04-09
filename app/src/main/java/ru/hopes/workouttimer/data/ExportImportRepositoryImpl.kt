package ru.hopes.workouttimer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.data.mapper.toExport
import ru.hopes.workouttimer.domain.model.export.ExportData
import ru.hopes.workouttimer.domain.repository.ExportImportRepository
import ru.hopes.workouttimer.domain.repository.ImportResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportImportRepositoryImpl @Inject constructor(
    private val context: Context,
    private val dao: WorkoutDao
) : ExportImportRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun exportToJson(workoutsJson: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "workout_export_${getDateStamp()}.json"
                val file = File(context.cacheDir, fileName)
                file.writeText(workoutsJson)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                uri
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun shareJson(workoutsJson: String) {
        val uri = exportToJson(workoutsJson) ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Поделиться тренировками")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    override suspend fun importFromJson(uri: Uri): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val jsonContent = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().use { reader -> reader.readText() }
                } ?: return@withContext ImportResult(
                    success = false,
                    importedCount = 0,
                    skippedCount = 0,
                    errorMessage = "Не удалось прочитать файл"
                )

                val exportData = json.decodeFromString<ExportData>(jsonContent)

                if (exportData.workouts.isEmpty()) {
                    return@withContext ImportResult(
                        success = false,
                        importedCount = 0,
                        skippedCount = 0,
                        errorMessage = "Файл не содержит тренировок"
                    )
                }

                val existingNames = dao.getAllWorkouts()
                    .map { list -> list.map { it.name }.toSet() }
                    .first()

                var importedCount = 0
                var skippedCount = 0

                exportData.workouts.forEach { exportWorkout ->
                    val uniqueName = getUniqueName(exportWorkout.name, existingNames)
                    
                    val workoutId = dao.insertWorkout(
                        ru.hopes.workouttimer.data.entity.WorkoutEntity(
                            name = uniqueName,
                            lastUseAt = exportWorkout.lastUseAt
                        )
                    )

                    val exerciseEntities = exportWorkout.exercises.map { ex ->
                        ExerciseEntity(
                            workoutId = workoutId,
                            name = ex.name,
                            weight = ex.weight,
                            sets = ex.sets,
                            reps = ex.reps,
                            restTimeMillis = ex.restTimeMillis,
                            orderInWorkout = ex.order,
                            note = ex.note
                        )
                    }

                    dao.insertExercises(exerciseEntities)
                    importedCount++
                }

                ImportResult(
                    success = true,
                    importedCount = importedCount,
                    skippedCount = skippedCount
                )
            } catch (e: Exception) {
                ImportResult(
                    success = false,
                    importedCount = 0,
                    skippedCount = 0,
                    errorMessage = "Ошибка импорта: ${e.message}"
                )
            }
        }
    }

    override suspend fun getAllExistingWorkoutNames(): Flow<Set<String>> {
        return dao.getAllWorkouts().map { list ->
            list.map { it.name }.toSet()
        }
    }

    private fun getUniqueName(originalName: String, existingNames: Set<String>): String {
        if (!existingNames.contains(originalName)) {
            return originalName
        }

        var counter = 1
        var uniqueName = "$originalName (копия)"
        while (existingNames.contains(uniqueName)) {
            uniqueName = "$originalName (копия $counter)"
            counter++
        }
        return uniqueName
    }

    private fun getDateStamp(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return formatter.format(Date())
    }
}
