package ru.hopes.workouttimer.domain.model

/**
 * Строка виджета быстрого старта.
 * lastUseAt == 0L — единственный источник истины «тренировку ещё ни разу не делали»
 * (тот же контракт, что и в Workout). lastDurationMillis — длительность последней
 * записанной сессии; может быть null и для уже выполнявшейся тренировки, если сессии
 * тогда ещё не записывались.
 */
data class WidgetWorkout(
    val id: Int,
    val name: String,
    val exerciseCount: Int,
    val lastUseAt: Long,
    val lastDurationMillis: Long?
)
