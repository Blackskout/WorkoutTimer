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
    val errorMessage: String? = null
)
