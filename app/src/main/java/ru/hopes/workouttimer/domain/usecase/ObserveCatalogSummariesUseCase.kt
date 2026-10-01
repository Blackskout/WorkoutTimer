package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.hopes.workouttimer.domain.model.CatalogSummary
import ru.hopes.workouttimer.domain.model.summarizeCatalog
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

/** Строки экрана «Упражнения»: справочник по алфавиту с последним лучшим подходом. */
class ObserveCatalogSummariesUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(): Flow<List<CatalogSummary>> =
        combine(repo.observeCatalog(), repo.observeLastSessionSets()) { catalog, sets ->
            summarizeCatalog(catalog, sets)
        }
}
