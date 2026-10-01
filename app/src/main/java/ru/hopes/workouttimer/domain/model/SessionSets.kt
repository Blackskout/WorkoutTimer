package ru.hopes.workouttimer.domain.model

/**
 * Лучший подход — одна функция на всё приложение. Сравниваются только подходы
 * в единице [unit]: «60 кг» и «плита 5» несравнимы. При полном равенстве
 * побеждает первый записанный (maxWithOrNull оставляет первый максимум).
 */
fun bestSet(sets: List<SessionSet>, unit: ExerciseUnit): SessionSet? {
    val comparator: Comparator<SessionSet> = when (unit) {
        ExerciseUnit.KG -> compareBy<SessionSet>({ it.weight }, { it.reps })
        ExerciseUnit.PLATE -> compareBy<SessionSet>({ it.weight }, { it.extraWeight }, { it.reps })
        ExerciseUnit.BODYWEIGHT -> compareBy<SessionSet> { it.reps }
    }
    return sets.filter { it.unit == unit }.maxWithOrNull(comparator)
}

/**
 * Подходы сессии по упражнениям в порядке первого появления. Вход — в порядке
 * записи (id): groupBy сохраняет порядок ключей и элементов.
 */
fun groupSetsByExercise(sets: List<LoggedSet>): List<SessionExerciseSets> =
    sets.groupBy { it.set.catalogId }.map { (catalogId, group) ->
        SessionExerciseSets(
            catalogId = catalogId,
            exerciseName = group.first().exerciseName,
            sets = group.map { it.set }
        )
    }

/**
 * Строки экрана «Упражнения». Алфавит — по ключу названия: он без регистра и с
 * «ё» как «е», а сырая строка поставила бы «Ёлочку» перед «А». Лучший подход —
 * в единице последнего подхода сессии: после смены единицы строка показывает
 * то, что реально было сделано, пока не появится новая сессия.
 */
fun summarizeCatalog(
    catalog: List<CatalogExercise>,
    lastSessionSets: List<LoggedSet>
): List<CatalogSummary> {
    val setsByCatalog = lastSessionSets.groupBy { it.set.catalogId }
    return catalog
        .sortedWith(compareBy<CatalogExercise>({ exerciseNameKey(it.name) }, { it.id }))
        .map { entry ->
            val sets = setsByCatalog[entry.id].orEmpty()
            val last = sets.lastOrNull()
            CatalogSummary(
                exercise = entry,
                lastBest = last?.let { bestSet(sets.map { logged -> logged.set }, it.set.unit) },
                lastDoneAt = last?.finishedAt
            )
        }
}
