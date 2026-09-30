package ru.hopes.workouttimer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.data.mapper.toExport
import ru.hopes.workouttimer.domain.model.export.ExportData
import ru.hopes.workouttimer.domain.repository.ExportImportRepository
import ru.hopes.workouttimer.domain.repository.ImportError
import ru.hopes.workouttimer.domain.repository.ImportResult
import ru.hopes.workouttimer.domain.repository.WidgetUpdater
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportImportRepositoryImpl @Inject constructor(
    private val context: Context,
    private val dao: WorkoutDao,
    private val widgetUpdater: WidgetUpdater
) : ExportImportRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun exportToJson(workoutsJson: String): Uri {
        return withContext(Dispatchers.IO) {
            val fileName = "workout_export_${getDateStamp()}.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(workoutsJson)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    override suspend fun shareJson(workoutsJson: String) {
        val uri = exportToJson(workoutsJson)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
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
                    error = ImportError.ReadFailed
                )

                val exportData = json.decodeFromString<ExportData>(jsonContent)

                if (exportData.workouts.isEmpty()) {
                    return@withContext ImportResult(
                        success = false,
                        importedCount = 0,
                        skippedCount = 0,
                        error = ImportError.NoWorkouts
                    )
                }

                val existingNames = dao.getAllWorkoutNames().toMutableSet()
                var skippedCount = 0

                // Тренировка без единого названного упражнения открылась бы экраном ошибки — пропускаем целиком.
                val prepared = exportData.workouts.filter { w -> w.exercises.any { it.name.isNotBlank() } }.map { exportWorkout ->
                    val uniqueName = getUniqueName(exportWorkout.name, existingNames)
                    existingNames.add(uniqueName)
                    // Пустые названия редактор не пропускает, импорт — тоже; считаем их пропущенными.
                    val (blank, named) = exportWorkout.exercises.partition { it.name.isBlank() }
                    skippedCount += blank.size
                    WorkoutEntity(name = uniqueName, lastUseAt = exportWorkout.lastUseAt) to named.map { ex ->
                        ExerciseEntity(
                            workoutId = 0,
                            name = ex.name,
                            weight = ex.weight,
                            sets = ex.sets,
                            reps = ex.reps,
                            restTimeMillis = ex.restTimeMillis,
                            orderInWorkout = ex.order,
                            note = ex.note,
                            catalogId = 0L // проставит транзакция DAO
                        )
                    }
                }

                val importedCount = dao.importWorkouts(prepared)

                if (importedCount > 0) {
                    // Вызов внутри try/catch (e: Exception) ниже: исключение отсюда вернуло бы
                    // success = false при фактически успешном импорте. Сейчас это безопасно
                    // только потому, что GlanceWidgetUpdater сам глотает свои исключения и никогда
                    // не выбрасывает наружу. Если когда-нибудь появится вторая реализация
                    // WidgetUpdater, которая пробрасывает ошибки, эта связка молча сломается —
                    // не убирайте эту гарантию из реализации без пересмотра места вызова.
                    widgetUpdater.requestUpdate()
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
                    error = ImportError.Failed(e.message)
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
        var uniqueName = context.getString(R.string.export_copy_name, originalName)
        while (existingNames.contains(uniqueName)) {
            uniqueName = context.getString(R.string.export_copy_name_numbered, originalName, counter)
            counter++
        }
        return uniqueName
    }

    private fun getDateStamp(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return formatter.format(Date())
    }
}
