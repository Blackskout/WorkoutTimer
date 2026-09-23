package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.components.RepsValues
import ru.hopes.workouttimer.presentation.ui.components.WeightValues
import ru.hopes.workouttimer.presentation.ui.components.WheelPicker
import ru.hopes.workouttimer.presentation.ui.components.WheelRow
import ru.hopes.workouttimer.presentation.ui.components.wheelIndexOfNearest
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.toCorrectNum

/**
 * Тело листа правки веса и повторов на ходу. Вынесено из [AppBottomSheet] отдельной
 * функцией по той же причине, что и содержимое других листов: ModalBottomSheet
 * требует Window и не рендерится ни в @Preview, ни в тесте композиции.
 *
 * Барабаны накапливают выбор локально, наружу он уходит только по «Готово» —
 * закрытие листа свайпом должно оставлять вес прежним.
 */
@Composable
internal fun WeightRepsSheetContent(
    exerciseName: String,
    weight: Double,
    reps: Int,
    onApply: (Double, Int) -> Unit
) {
    var weightIndex by remember(weight) {
        mutableIntStateOf(wheelIndexOfNearest(WeightValues, weight))
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
        WheelRow(modifier = Modifier.padding(vertical = 12.dp)) {
            WheelPicker(
                items = WeightValues,
                selectedIndex = weightIndex,
                onSelected = { weightIndex = it },
                label = "кг",
                format = { it.toCorrectNum() }
            )
            WheelPicker(
                items = RepsValues,
                selectedIndex = repsIndex,
                onSelected = { repsIndex = it },
                label = "повт",
                format = { it.toString() }
            )
        }
        PrimaryButton(
            text = "Готово",
            onClick = { onApply(WeightValues[weightIndex], RepsValues[repsIndex]) }
        )
    }
}

@Preview(backgroundColor = 0xFF14141A, showBackground = true)
@Composable
private fun WeightRepsSheetContentPreview() {
    WorkoutTimerTheme {
        WeightRepsSheetContent(
            exerciseName = "Жим лёжа",
            weight = 80.0,
            reps = 8,
            onApply = { _, _ -> }
        )
    }
}
