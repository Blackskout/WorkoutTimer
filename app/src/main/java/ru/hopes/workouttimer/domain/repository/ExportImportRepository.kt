package ru.hopes.workouttimer.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ExportImportRepository {
    suspend fun exportToJson(workoutsJson: String): Uri
    suspend fun shareJson(workoutsJson: String)
    suspend fun importFromJson(uri: Uri): ImportResult
    suspend fun getAllExistingWorkoutNames(): Flow<Set<String>>
}

data class ImportResult(
    val success: Boolean,
    val importedCount: Int,
    val skippedCount: Int,
    val error: ImportError? = null
)

/** Причина неудачного импорта; текст для пользователя собирает экран. */
sealed interface ImportError {
    data object ReadFailed : ImportError
    data object NoWorkouts : ImportError
    data class Failed(val detail: String?) : ImportError
}
