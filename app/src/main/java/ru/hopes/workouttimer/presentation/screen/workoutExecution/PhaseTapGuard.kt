package ru.hopes.workouttimer.presentation.screen.workoutExecution

/**
 * Главная кнопка на месте меняет смысл: «Закончить подход» ↔ «Пропустить отдых». Второе
 * касание двойного тапа (или тап, совпавший с концом отдыха) попадало бы уже в новую кнопку,
 * поэтому сразу после смены фазы она нажатий не принимает.
 */
internal const val PHASE_TAP_GUARD_MILLIS = 600L

internal fun acceptsPhaseTap(phaseShownAt: Long, now: Long): Boolean =
    now - phaseShownAt >= PHASE_TAP_GUARD_MILLIS
