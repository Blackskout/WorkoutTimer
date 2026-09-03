package ru.hopes.workouttimer.presentation.widget

import ru.hopes.workouttimer.presentation.utils.DateFormatter

/**
 * Подпись строки виджета: «7 упр. · 52 мин».
 * Без Context — иначе юнит-тест проверял бы строки, подставленные моком ресурсов.
 */
fun formatWidgetSubtitle(exerciseCount: Int, lastDurationMillis: Long?): String {
    val duration = lastDurationMillis
        ?.let { DateFormatter.formatDurationToString(it) }
        ?: "ещё не делали"
    return "$exerciseCount упр. · $duration"
}
