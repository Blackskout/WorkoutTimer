package ru.hopes.workouttimer.presentation.screen.workouts

enum class ListMenuAction { START, RETURN, SKIP, EDIT, HISTORY, DELETE }

data class ListMenuEntry(val action: ListMenuAction, val enabled: Boolean = true)

/**
 * Меню строки списка. Пока сессия идёт: у идущей тренировки «Начать» становится
 * «Вернуться», а «Редактировать» и «Удалить» неактивны — редактор пересоздаёт строки
 * упражнений, и правки сессии ушли бы в никуда, а удаление оставило бы сессию без
 * тренировки. У остальных тренировок «Начать» скрыт: вторую сессию не начать.
 */
fun listMenuEntries(targetId: Int, runningWorkoutId: Int?, isSearching: Boolean): List<ListMenuEntry> {
    val isRunningTarget = runningWorkoutId == targetId
    return buildList {
        when {
            runningWorkoutId == null -> add(ListMenuEntry(ListMenuAction.START))
            isRunningTarget -> add(ListMenuEntry(ListMenuAction.RETURN))
        }
        // В режиме поиска очередь не видна целиком, поэтому пропуск скрыт:
        // он переставил бы порядок, которого пользователь сейчас не наблюдает.
        if (!isSearching) add(ListMenuEntry(ListMenuAction.SKIP))
        add(ListMenuEntry(ListMenuAction.EDIT, enabled = !isRunningTarget))
        add(ListMenuEntry(ListMenuAction.HISTORY))
        add(ListMenuEntry(ListMenuAction.DELETE, enabled = !isRunningTarget))
    }
}

enum class HeroAction { START, RETURN }

/** Кнопка карточки «Следующая»: без сессии — «НАЧАТЬ», у идущей — «ВЕРНУТЬСЯ», у другой — нет. */
fun heroActionOf(workoutId: Int, runningWorkoutId: Int?): HeroAction? = when (runningWorkoutId) {
    null -> HeroAction.START
    workoutId -> HeroAction.RETURN
    else -> null
}
