package ru.hopes.workouttimer.domain.model

/**
 * Строка виджета быстрого старта.
 * lastDurationMillis == null означает, что тренировку ещё ни разу не делали.
 */
data class WidgetWorkout(
    val id: Int,
    val name: String,
    val exerciseCount: Int,
    val lastDurationMillis: Long?
)
