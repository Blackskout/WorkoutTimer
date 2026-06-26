package ru.hopes.workouttimer.domain.usecase

import android.net.Uri
import ru.hopes.workouttimer.domain.repository.ExportImportRepository
import ru.hopes.workouttimer.domain.repository.ImportResult
import javax.inject.Inject

class ImportWorkoutsUseCase @Inject constructor(
    private val repo: ExportImportRepository
) {
    suspend operator fun invoke(uri: Uri): ImportResult {
        return repo.importFromJson(uri)
    }
}
