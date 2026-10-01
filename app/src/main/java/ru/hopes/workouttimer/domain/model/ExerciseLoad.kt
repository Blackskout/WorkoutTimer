package ru.hopes.workouttimer.domain.model

import kotlin.math.roundToInt

/** Номера плит в стопке тренажёра. */
const val PLATE_MIN = 1
const val PLATE_MAX = 30

/** Добавка к плите — гантель поверх стопки. */
const val PLATE_EXTRA_MAX = 10.0
const val PLATE_EXTRA_STEP = 0.5

/** Нагрузка шаблона: вес в кг или номер плиты и добавка к плите. */
data class Load(val weight: Double, val extraWeight: Double)

/**
 * Нагрузка, приведённая к сетке единицы, без лишних потерь. Так сохраняется шаблон
 * (редактор, импорт): добавка к плите остаётся, у кг и без веса её не бывает.
 */
fun fitLoadToUnit(weight: Double, extraWeight: Double, unit: ExerciseUnit): Load = when (unit) {
    ExerciseUnit.KG -> Load(weight, 0.0)
    ExerciseUnit.PLATE -> Load(
        weight = weight.roundToInt().coerceIn(PLATE_MIN, PLATE_MAX).toDouble(),
        extraWeight = ((extraWeight / PLATE_EXTRA_STEP).roundToInt() * PLATE_EXTRA_STEP)
            .coerceIn(0.0, PLATE_EXTRA_MAX)
    )
    ExerciseUnit.BODYWEIGHT -> Load(0.0, 0.0)
}

/**
 * Смена единицы на экране «Упражнения». Прежняя добавка к новой единице не
 * относится: → плита — номер в 1..30 и добавка 0; → кг — число веса
 * сохраняется; → без веса — всё 0.
 */
fun convertLoadToUnit(weight: Double, extraWeight: Double, unit: ExerciseUnit): Load =
    fitLoadToUnit(weight, if (unit == ExerciseUnit.PLATE) 0.0 else extraWeight, unit)
