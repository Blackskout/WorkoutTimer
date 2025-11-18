package ru.hopes.workouttimer.domain.model

data class Workout(
    val id: Int = 0,
    val name: String,
    val exercises: List<Exercise>,
    val lastUseAt: Long
)