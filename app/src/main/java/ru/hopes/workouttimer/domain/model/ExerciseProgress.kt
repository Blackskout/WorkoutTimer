package ru.hopes.workouttimer.domain.model

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** Сессия, в которой делали упражнение: только его подходы, в порядке записи. */
data class ProgressSession(
    val sessionId: Long,
    val finishedAt: Long,
    val sets: List<SessionSet>
)

/** Запись справочника и все её сессии по возрастанию времени завершения. */
data class ExerciseHistory(
    val exercise: CatalogExercise,
    val sessions: List<ProgressSession>
)

/** Период графика; months == null — всё время. */
enum class ProgressPeriod(val months: Int?) { MONTH(1), QUARTER(3), ALL(null) }

/** Точка графика: лучший подход сессии в текущей единице и его высота по оси Y. */
data class ProgressPoint(
    val sessionId: Long,
    val finishedAt: Long,
    val best: SessionSet,
    val level: Double
)

/** Изменение лучшего подхода за период в тех единицах, в которых его понимает человек. */
sealed interface ProgressDelta {
    data class Kg(val amount: Double) : ProgressDelta
    data class Plates(val amount: Int) : ProgressDelta
    data class Reps(val amount: Int) : ProgressDelta
    data object NoChange : ProgressDelta
}

/**
 * Всё, что экран показывает над списком. [best] — за всё время, [points] и
 * [delta] — за период; [hasPoints] — есть ли точки в текущей единице вообще.
 */
data class ProgressSummary(
    val best: SessionSet?,
    val hasPoints: Boolean,
    val points: List<ProgressPoint>,
    val delta: ProgressDelta?,
    val hasOtherUnits: Boolean
)

/**
 * Подходы упражнения по сессиям. Сортировка по времени здесь, а не только в SQL:
 * график строится слева направо, и порядок не должен зависеть от запроса.
 */
fun groupProgressSessions(sets: List<LoggedSet>): List<ProgressSession> =
    sets.groupBy { it.set.sessionId }
        .map { (sessionId, group) -> ProgressSession(sessionId, group.first().finishedAt, group.map { it.set }) }
        .sortedWith(compareBy<ProgressSession>({ it.finishedAt }, { it.sessionId }))

/**
 * Высота точки по оси Y. Плита: добавка поднимает точку внутри промежутка до
 * следующей плиты, но не дотягивает до неё — делитель на единицу больше
 * максимальной добавки.
 */
fun progressLevel(set: SessionSet): Double = when (set.unit) {
    ExerciseUnit.KG -> set.weight
    ExerciseUnit.PLATE -> set.weight + set.extraWeight / (PLATE_EXTRA_MAX + 1)
    ExerciseUnit.BODYWEIGHT -> set.reps.toDouble()
}

/** Точки графика: сессии без подходов в текущей единице точки не дают. */
fun progressPoints(sessions: List<ProgressSession>, unit: ExerciseUnit): List<ProgressPoint> =
    sessions.mapNotNull { session ->
        bestSet(session.sets, unit)?.let { best ->
            ProgressPoint(session.sessionId, session.finishedAt, best, progressLevel(best))
        }
    }

/** Начало периода: календарные месяцы назад от [now] в поясе устройства; null — всё время. */
fun periodStart(period: ProgressPeriod, now: Long, zone: TimeZone): Long? {
    val months = period.months ?: return null
    return Calendar.getInstance(zone).apply {
        timeInMillis = now
        add(Calendar.MONTH, -months)
    }.timeInMillis
}

/**
 * Разница лучших подходов последней и первой сессии периода. «+0 кг» ничего не
 * говорит, поэтому при той же нагрузке считаются повторы.
 */
fun progressDelta(first: SessionSet, last: SessionSet, unit: ExerciseUnit): ProgressDelta {
    val reps = last.reps - first.reps
    val byReps = if (reps != 0) ProgressDelta.Reps(reps) else ProgressDelta.NoChange
    return when (unit) {
        ExerciseUnit.KG ->
            if (last.weight != first.weight) ProgressDelta.Kg(last.weight - first.weight) else byReps
        ExerciseUnit.PLATE -> when {
            last.weight != first.weight -> ProgressDelta.Plates((last.weight - first.weight).roundToInt())
            last.extraWeight != first.extraWeight -> ProgressDelta.Kg(last.extraWeight - first.extraWeight)
            else -> byReps
        }
        ExerciseUnit.BODYWEIGHT -> byReps
    }
}

/** Сводка экрана прогресса для текущей единицы записи и выбранного периода. */
fun summarizeProgress(
    sessions: List<ProgressSession>,
    unit: ExerciseUnit,
    period: ProgressPeriod,
    now: Long,
    zone: TimeZone
): ProgressSummary {
    val all = progressPoints(sessions, unit)
    val start = periodStart(period, now, zone)
    val inPeriod = if (start == null) all else all.filter { it.finishedAt >= start }
    return ProgressSummary(
        best = bestSet(sessions.flatMap { it.sets }, unit),
        hasPoints = all.isNotEmpty(),
        points = inPeriod,
        delta = if (inPeriod.size >= 2) progressDelta(inPeriod.first().best, inPeriod.last().best, unit) else null,
        hasOtherUnits = sessions.any { session -> session.sets.any { it.unit != unit } }
    )
}
