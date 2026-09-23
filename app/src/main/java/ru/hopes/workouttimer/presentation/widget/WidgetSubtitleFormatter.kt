package ru.hopes.workouttimer.presentation.widget

import ru.hopes.workouttimer.presentation.utils.DateFormatter

/**
 * Подпись строки виджета: «7 упр. · 52 мин».
 * Без Context — иначе юнит-тест проверял бы строки, подставленные моком ресурсов.
 *
 * lastUseAt == 0L — единственный источник истины «ещё не делали» (тот же контракт,
 * что и на остальных экранах приложения). lastDurationMillis — это длительность
 * последней записанной сессии, а не признак «делали/не делали»: тренировка,
 * выполненная до появления записи сессий, не имеет длительности, но не является
 * «ещё не делали».
 */
fun formatWidgetSubtitle(exerciseCount: Int, lastUseAt: Long, lastDurationMillis: Long?): String {
    if (lastUseAt == 0L) {
        return "$exerciseCount упр. · ещё не делали"
    }
    val duration = lastDurationMillis?.let { DateFormatter.formatDurationToString(it) }
    return if (duration != null) "$exerciseCount упр. · $duration" else "$exerciseCount упр."
}
