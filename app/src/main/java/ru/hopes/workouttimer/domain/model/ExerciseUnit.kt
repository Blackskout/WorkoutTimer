package ru.hopes.workouttimer.domain.model

/** Единица нагрузки упражнения. Имя константы хранится в базе и в файле экспорта как TEXT. */
enum class ExerciseUnit { KG, PLATE, BODYWEIGHT }

/**
 * Единица из базы или файла. Неизвестное или отсутствующее значение читается
 * как кг: так жили все данные до E2, и файл из будущей версии не должен ронять импорт.
 */
fun exerciseUnitOf(name: String?): ExerciseUnit =
    ExerciseUnit.entries.firstOrNull { it.name == name } ?: ExerciseUnit.KG
