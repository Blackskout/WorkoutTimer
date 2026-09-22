package ru.hopes.workouttimer.presentation.widget

import androidx.compose.material3.darkColorScheme
import androidx.glance.material3.ColorProviders
import ru.hopes.workouttimer.presentation.ui.theme.Accent
import ru.hopes.workouttimer.presentation.ui.theme.Background
import ru.hopes.workouttimer.presentation.ui.theme.OnAccent
import ru.hopes.workouttimer.presentation.ui.theme.OutlineDark
import ru.hopes.workouttimer.presentation.ui.theme.SurfaceDark
import ru.hopes.workouttimer.presentation.ui.theme.TextPrimary
import ru.hopes.workouttimer.presentation.ui.theme.TextSecondary

// Переопределены только роли, которые сейчас реально читает разметка виджета
// (QuickStartWidget.kt: widgetBackground, onSurface, onSurfaceVariant) — остальные
// остаются на дефолтах Material3. `widgetBackground` не берётся из surface/background:
// glance-material3 вычисляет его из secondaryContainer (см. Material3Themes.kt,
// adjustColorToneForWidgetBackground), поэтому secondaryContainer задан явно. Если в
// разметку добавится обращение к новой роли — сначала проверить в исходниках
// glance-material3, из чего она реально выводится, а не полагаться на то, что роль
// просто существует на объекте ColorProviders.
private val WidgetScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = Background,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark,
    // Источник для GlanceTheme.colors.widgetBackground — см. комментарий выше.
    secondaryContainer = SurfaceDark
)

/** Виджет не следует системной теме: приложение всегда тёмное. */
val WidgetColorScheme = ColorProviders(light = WidgetScheme, dark = WidgetScheme)
