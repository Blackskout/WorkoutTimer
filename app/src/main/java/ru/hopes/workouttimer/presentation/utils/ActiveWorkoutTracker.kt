package ru.hopes.workouttimer.presentation.utils

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Идёт ли сейчас тренировка в этом процессе. По ней MainActivity не даёт тапу по виджету
 * сбросить экран выполнения с таймером.
 *
 * Отметку держит конкретный владелец (ViewModel экрана выполнения): старый экземпляр,
 * уходящий позже нового, не снимет чужую отметку.
 */
@Singleton
class ActiveWorkoutTracker @Inject constructor() {

    @Volatile
    private var owner: Any? = null

    val isActive: Boolean
        get() = owner != null

    fun start(owner: Any) {
        this.owner = owner
    }

    fun stop(owner: Any) {
        if (this.owner === owner) this.owner = null
    }
}
