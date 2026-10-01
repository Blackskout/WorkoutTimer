package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.formatSet
import ru.hopes.workouttimer.presentation.utils.toCorrectNum
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Шаблоны подписи изменения за период. [plates] склоняет «плита/плиты/плит»:
 * plurals без ресурсов Android не прочитать, поэтому JVM-тест подставляет свою функцию.
 */
class DeltaFormat(
    val kg: String,
    val reps: String,
    val none: String,
    val line: String,
    val plates: (count: Int, signed: String) -> String
)

// Типографский минус U+2212, а не дефис: отрицательное изменение не путается с тире.
private const val MINUS = "\u2212"

private fun String.fill(vararg args: Any): String = String.format(Locale.ROOT, this, *args)

/** «+2.5», «0» или типографский минус и число. Разность весов из импорта может выйти 0.30000000000000004 — режем до сотых. */
fun signedNumber(value: Double): String {
    val rounded = (value * 100).roundToLong() / 100.0
    return when {
        rounded > 0 -> "+" + rounded.toCorrectNum()
        rounded < 0 -> MINUS + (-rounded).toCorrectNum()
        else -> "0"
    }
}

/** «+2.5 кг за 3 мес», «+1 плита за 1 мес», «без изменений за всё время». */
fun formatDelta(delta: ProgressDelta, span: String, format: DeltaFormat): String {
    val value = when (delta) {
        is ProgressDelta.Kg -> format.kg.fill(signedNumber(delta.amount))
        is ProgressDelta.Plates -> format.plates(abs(delta.amount), signedNumber(delta.amount.toDouble()))
        is ProgressDelta.Reps -> format.reps.fill(signedNumber(delta.amount.toDouble()))
        ProgressDelta.NoChange -> format.none
    }
    return format.line.fill(value, span)
}

/**
 * «26 сен». Месяцы — из ресурсов, а не из SimpleDateFormat: ICU на Android
 * сокращает «сент.», JVM — по-своему, и подпись зависела бы от платформы.
 */
fun shortDate(timestamp: Long, template: String, months: List<String>, zone: TimeZone): String {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
    return template.fill(calendar.get(Calendar.DAY_OF_MONTH), months[calendar.get(Calendar.MONTH)])
}

/** Подход сам по себе (главная цифра, подпись точки): голое «12» непонятно — «12 повт.». */
fun progressSetText(set: SessionSet, format: SetFormat, repsTemplate: String): String =
    if (set.unit == ExerciseUnit.BODYWEIGHT) repsTemplate.fill(set.reps) else formatSet(set, format)

/** Шаблоны без подстановки: getString без аргументов возвращает текст с %1$s как есть. */
@Composable
fun deltaFormat(): DeltaFormat {
    val resources = LocalResources.current
    return DeltaFormat(
        kg = stringResource(R.string.progress_delta_kg),
        reps = stringResource(R.string.progress_delta_reps),
        none = stringResource(R.string.progress_delta_none),
        line = stringResource(R.string.progress_delta_line),
        plates = { count, signed -> resources.getQuantityString(R.plurals.progress_delta_plates, count, signed) }
    )
}

/** Подпись кнопки периода: «1 мес», «3 мес», «всё». */
@Composable
fun periodName(period: ProgressPeriod): String = stringResource(
    when (period) {
        ProgressPeriod.MONTH -> R.string.progress_period_month
        ProgressPeriod.QUARTER -> R.string.progress_period_quarter
        ProgressPeriod.ALL -> R.string.progress_period_all
    }
)

/** Хвост подписи изменения: «за 1 мес», «за 3 мес», «за всё время». */
@Composable
fun periodSpan(period: ProgressPeriod): String = stringResource(
    when (period) {
        ProgressPeriod.MONTH -> R.string.progress_span_month
        ProgressPeriod.QUARTER -> R.string.progress_span_quarter
        ProgressPeriod.ALL -> R.string.progress_span_all
    }
)
