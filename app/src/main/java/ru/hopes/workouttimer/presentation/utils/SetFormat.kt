package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionSet
import java.util.Locale

/**
 * Шаблоны подписи подхода из strings.xml. Сами функции форматирования чистые
 * и получают шаблоны параметром — так их проверяет JVM-тест без ресурсов Android.
 */
data class SetFormat(
    val loadKg: String,
    val loadPlate: String,
    val loadPlateExtra: String,
    val set: String
)

/** Шаблоны без подстановки: getString без аргументов возвращает текст с %1$s как есть. */
@Composable
fun setFormat(): SetFormat = SetFormat(
    loadKg = stringResource(R.string.unit_load_kg),
    loadPlate = stringResource(R.string.unit_load_plate),
    loadPlateExtra = stringResource(R.string.unit_load_plate_extra),
    set = stringResource(R.string.unit_set)
)

/** Название единицы для меню и подсказок: «кг», «плита», «без веса». */
@Composable
fun unitName(unit: ExerciseUnit): String = stringResource(
    when (unit) {
        ExerciseUnit.KG -> R.string.unit_name_kg
        ExerciseUnit.PLATE -> R.string.unit_name_plate
        ExerciseUnit.BODYWEIGHT -> R.string.unit_name_bodyweight
    }
)

// Разделитель подходов — знак препинания, а не текст: в ресурсы не выносится.
private const val SET_SEPARATOR = " · "

private fun String.fill(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

/** Нагрузка без повторов: «60 кг», «плита 5», «плита 5 +2 кг». У упражнения без веса её нет. */
fun formatLoad(unit: ExerciseUnit, weight: Double, extraWeight: Double, format: SetFormat): String? =
    when (unit) {
        ExerciseUnit.KG -> format.loadKg.fill(weight.toCorrectNum())
        ExerciseUnit.PLATE ->
            if (extraWeight > 0.0) {
                format.loadPlateExtra.fill(weight.toCorrectNum(), extraWeight.toCorrectNum())
            } else {
                format.loadPlate.fill(weight.toCorrectNum())
            }
        ExerciseUnit.BODYWEIGHT -> null
    }

/**
 * Подпись подхода по значениям, без SessionSet: план из шаблона тренировки на экране
 * просмотра — «60 кг × 8», «плита 5 +2 кг × 12», «12». То же правило, что у сделанного подхода.
 */
fun formatPlannedSet(unit: ExerciseUnit, weight: Double, extraWeight: Double, reps: Int, format: SetFormat): String {
    val load = formatLoad(unit, weight, extraWeight, format) ?: return reps.toString()
    return format.set.fill(load, reps)
}

/**
 * Подпись подхода — одна на всё приложение: «60 кг × 8», «плита 5 +2 кг × 12», «12».
 * [bareKg] — для перечисления: следующие подходы в кг пишутся без единицы, «60 × 8».
 */
fun formatSet(set: SessionSet, format: SetFormat, bareKg: Boolean = false): String {
    if (bareKg && set.unit == ExerciseUnit.KG) return format.set.fill(set.weight.toCorrectNum(), set.reps)
    return formatPlannedSet(set.unit, set.weight, set.extraWeight, set.reps, format)
}

/** Подходы одного упражнения: «60 кг × 8 · 60 × 8 · 62.5 × 6». */
fun formatSetList(sets: List<SessionSet>, format: SetFormat): String =
    sets.mapIndexed { index, set ->
        formatSet(set, format, bareKg = index > 0 && sets[index - 1].unit == set.unit)
    }.joinToString(SET_SEPARATOR)
