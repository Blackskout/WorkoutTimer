package ru.hopes.workouttimer.data.dao

import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.domain.model.ExerciseUnit

/**
 * Упражнение до привязки к справочнику. unitIfNew — единица новой записи, если
 * записи с таким ключом ещё нет: редактор создаёт кг, импорт — единицу из файла.
 * У существующей записи своя единица главнее.
 */
data class ExerciseDraft(
    val exercise: ExerciseEntity,
    val unitIfNew: ExerciseUnit
)
