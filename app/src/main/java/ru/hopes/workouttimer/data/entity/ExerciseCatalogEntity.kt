package ru.hopes.workouttimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.hopes.workouttimer.domain.model.ExerciseUnit

/** Запись справочника упражнений. nameKey считает только exerciseNameKey(). */
@Entity(
    tableName = "exercise_catalog",
    indices = [Index(value = ["nameKey"], unique = true)]
)
data class ExerciseCatalogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    @ColumnInfo(defaultValue = "KG") val unit: String = ExerciseUnit.KG.name
)
