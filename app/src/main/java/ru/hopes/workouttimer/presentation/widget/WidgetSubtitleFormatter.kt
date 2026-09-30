package ru.hopes.workouttimer.presentation.widget

import android.content.Context
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.utils.DateFormatter

/**
 * Что показать в подписи строки виджета: «7 упр. · 52 мин».
 *
 * Решение принимает [widgetSubtitleOf] без Context — иначе юнит-тест проверял бы
 * строки, подставленные моком ресурсов. Текст из него собирает [format].
 */
sealed interface WidgetSubtitle {
    val exerciseCount: Int

    data class NeverDone(override val exerciseCount: Int) : WidgetSubtitle

    data class Done(override val exerciseCount: Int, val durationMillis: Long?) : WidgetSubtitle
}

/**
 * lastUseAt == 0L — единственный источник истины «ещё не делали» (тот же контракт,
 * что и на остальных экранах приложения). lastDurationMillis — это длительность
 * последней записанной сессии, а не признак «делали/не делали»: тренировка,
 * выполненная до появления записи сессий, не имеет длительности, но не является
 * «ещё не делали».
 */
fun widgetSubtitleOf(exerciseCount: Int, lastUseAt: Long, lastDurationMillis: Long?): WidgetSubtitle =
    if (lastUseAt == 0L) {
        WidgetSubtitle.NeverDone(exerciseCount)
    } else {
        WidgetSubtitle.Done(exerciseCount, lastDurationMillis)
    }

fun WidgetSubtitle.format(context: Context): String = when (this) {
    is WidgetSubtitle.NeverDone ->
        context.getString(R.string.widget_subtitle_never_done, exerciseCount)

    is WidgetSubtitle.Done -> if (durationMillis != null) {
        context.getString(
            R.string.widget_subtitle_with_duration,
            exerciseCount,
            DateFormatter.formatDurationToString(context, durationMillis)
        )
    } else {
        context.getString(R.string.widget_subtitle, exerciseCount)
    }
}
