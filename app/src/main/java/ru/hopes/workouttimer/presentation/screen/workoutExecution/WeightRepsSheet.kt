package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.presentation.ui.components.PlateExtraValues
import ru.hopes.workouttimer.presentation.ui.components.PlateValues
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.components.RepsValues
import ru.hopes.workouttimer.presentation.ui.components.DecimalPoint
import ru.hopes.workouttimer.presentation.ui.components.WeightFractionValues
import ru.hopes.workouttimer.presentation.ui.components.WeightWholeValues
import ru.hopes.workouttimer.presentation.ui.components.formatWeightFraction
import ru.hopes.workouttimer.presentation.ui.components.weightOf
import ru.hopes.workouttimer.presentation.ui.components.weightWheelsOf
import ru.hopes.workouttimer.presentation.ui.components.WheelPicker
import ru.hopes.workouttimer.presentation.ui.components.WheelRow
import ru.hopes.workouttimer.presentation.ui.components.wheelIndexOfNearest
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.toCorrectNum

/**
 * Тело листа правки нагрузки и повторов на ходу. Вынесено из [AppBottomSheet] отдельной
 * функцией по той же причине, что и содержимое других листов: ModalBottomSheet
 * требует Window и не рендерится ни в @Preview, ни в тесте композиции.
 *
 * Барабаны накапливают выбор локально, наружу он уходит только по «Готово» —
 * закрытие листа свайпом должно оставлять нагрузку прежней. Набор барабанов —
 * по единице: кг; плита и добавка; без веса — только повторы.
 */
@Composable
internal fun WeightRepsSheetContent(
    exerciseName: String,
    unit: ExerciseUnit,
    weight: Double,
    extraWeight: Double,
    reps: Int,
    onApply: (weight: Double, extraWeight: Double, reps: Int) -> Unit
) {
    var weightWheels by remember(weight) { mutableStateOf(weightWheelsOf(weight)) }
    var plateIndex by remember(weight) {
        mutableIntStateOf(wheelIndexOfNearest(PlateValues, weight))
    }
    var extraIndex by remember(extraWeight) {
        mutableIntStateOf(wheelIndexOfNearest(PlateExtraValues, extraWeight))
    }
    var repsIndex by remember(reps) {
        mutableIntStateOf(wheelIndexOfNearest(RepsValues.map { it.toDouble() }, reps.toDouble()))
    }

    Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp)
        )
        WheelRow(
            modifier = Modifier.padding(vertical = 12.dp),
            // Целые и доли кг — одно число «40.25» с общей точкой и одной подписью.
            decimalPoint = if (unit == ExerciseUnit.KG) DecimalPoint(0, stringResource(R.string.common_unit_kg)) else null
        ) {
            when (unit) {
                ExerciseUnit.KG -> {
                    WheelPicker(
                        items = WeightWholeValues,
                        selectedIndex = weightWheels.wholeIndex,
                        onSelected = { weightWheels = weightWheels.copy(wholeIndex = it) },
                        label = "",
                        itemAlignment = Alignment.End,
                        format = { it.toString() }
                    )
                    WheelPicker(
                        items = WeightFractionValues,
                        selectedIndex = weightWheels.fractionIndex,
                        onSelected = { weightWheels = weightWheels.copy(fractionIndex = it) },
                        label = "",
                        itemAlignment = Alignment.Start,
                        format = { formatWeightFraction(it) }
                    )
                }

                ExerciseUnit.PLATE -> {
                    WheelPicker(
                        items = PlateValues,
                        selectedIndex = plateIndex,
                        onSelected = { plateIndex = it },
                        label = stringResource(R.string.unit_name_plate),
                        format = { it.toCorrectNum() }
                    )
                    WheelPicker(
                        items = PlateExtraValues,
                        selectedIndex = extraIndex,
                        onSelected = { extraIndex = it },
                        label = stringResource(R.string.unit_plate_extra),
                        format = { it.toCorrectNum() }
                    )
                }

                ExerciseUnit.BODYWEIGHT -> Unit
            }
            WheelPicker(
                items = RepsValues,
                selectedIndex = repsIndex,
                onSelected = { repsIndex = it },
                label = stringResource(R.string.common_unit_reps),
                format = { it.toString() }
            )
        }
        PrimaryButton(
            text = stringResource(R.string.common_done),
            onClick = {
                val newReps = RepsValues[repsIndex]
                when (unit) {
                    ExerciseUnit.KG -> onApply(weightOf(weightWheels), 0.0, newReps)
                    ExerciseUnit.PLATE -> onApply(PlateValues[plateIndex], PlateExtraValues[extraIndex], newReps)
                    ExerciseUnit.BODYWEIGHT -> onApply(0.0, 0.0, newReps)
                }
            }
        )
    }
}

@Preview(backgroundColor = 0xFF14141A, showBackground = true)
@Composable
private fun WeightRepsSheetContentPreview() {
    WorkoutTimerTheme {
        WeightRepsSheetContent(
            exerciseName = "Жим лёжа",
            unit = ExerciseUnit.KG,
            weight = 80.0,
            extraWeight = 0.0,
            reps = 8,
            onApply = { _, _, _ -> }
        )
    }
}

@Preview(backgroundColor = 0xFF14141A, showBackground = true)
@Composable
private fun WeightRepsSheetContentPlatePreview() {
    WorkoutTimerTheme {
        WeightRepsSheetContent(
            exerciseName = "Тяга блока",
            unit = ExerciseUnit.PLATE,
            weight = 5.0,
            extraWeight = 2.0,
            reps = 12,
            onApply = { _, _, _ -> }
        )
    }
}
