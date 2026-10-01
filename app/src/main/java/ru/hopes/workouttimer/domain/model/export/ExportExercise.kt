package ru.hopes.workouttimer.domain.model.export

import kotlinx.serialization.Serializable

/**
 * Упражнение в файле экспорта. unit и extraWeight появились в E2; у старых
 * файлов их нет, и значения по умолчанию читают такие файлы как кг.
 */
@Serializable
data class ExportExercise(
    val name: String,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val restTimeMillis: Long,
    val order: Int,
    val note: String = "",
    val unit: String = "KG",
    val extraWeight: Double = 0.0
)
