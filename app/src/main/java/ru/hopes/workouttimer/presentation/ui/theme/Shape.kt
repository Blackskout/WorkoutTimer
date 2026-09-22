package ru.hopes.workouttimer.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/** Горизонтальный отступ содержимого экрана. */
val ScreenPadding = 18.dp

/** Вертикальный шаг между карточками списка. */
val CardSpacing = 8.dp

/** Вертикальный шаг между секциями экрана. */
val SectionSpacing = 18.dp

/** Высота главной кнопки действия. */
val PrimaryButtonHeight = 58.dp
