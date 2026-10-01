package ru.hopes.workouttimer.presentation.screen.creation

import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.exerciseNameKey

private const val MAX_SUGGESTIONS = 5

/**
 * Подсказки под полем названия: до пяти записей, чей ключ содержит ключ ввода;
 * сначала начинающиеся с него, потом по алфавиту. Пустое поле проверяется до
 * ключа: exerciseNameKey("") — это ключ «Без названия», а не пустая строка.
 * Точное совпадение с записью гасит подсказки — выбирать уже нечего.
 */
fun exerciseSuggestions(query: String, catalog: List<CatalogExercise>): List<CatalogExercise> {
    if (query.isBlank()) return emptyList()
    val key = exerciseNameKey(query)
    val keyed = catalog.map { it to exerciseNameKey(it.name) }
    if (keyed.any { it.second == key }) return emptyList()
    return keyed
        .filter { it.second.contains(key) }
        .sortedWith(compareBy<Pair<CatalogExercise, String>>({ !it.second.startsWith(key) }, { it.second }))
        .take(MAX_SUGGESTIONS)
        .map { it.first }
}

/**
 * Единица упражнения в редакторе — единица записи с тем же ключом. Новое название
 * станет записью в кг (так создаёт её сохранение), поэтому и показывается кг.
 */
fun unitForName(name: String, catalog: List<CatalogExercise>): ExerciseUnit {
    if (name.isBlank()) return ExerciseUnit.KG
    val key = exerciseNameKey(name)
    return catalog.firstOrNull { exerciseNameKey(it.name) == key }?.unit ?: ExerciseUnit.KG
}
