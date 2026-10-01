package ru.hopes.workouttimer.presentation.screen.workoutExecution

/**
 * Перехватывать ли «Назад» на экране выполнения. Обычное сворачивание — это просто pop
 * на экран ниже, и его выгоднее отдать навигации: тогда работает системная анимация
 * предпросмотра жеста. Перехват нужен в двух случаях: идёт запись завершения (свернуть
 * посреди неё значило бы получить Finished, которого никто не увидит) и под экраном
 * пусто (холодный старт из виджета) — тогда сворачивание открывает список.
 */
internal fun interceptsBack(isLive: Boolean, isFinishing: Boolean, hasScreenBelow: Boolean): Boolean =
    isLive && (isFinishing || !hasScreenBelow)
