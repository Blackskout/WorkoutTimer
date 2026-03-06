package ru.hopes.workouttimer.domain.model

    data class Exercise(
        val id: Int = 0,
        val name: String,
        val weight: Double,
        val sets: Int,
        val reps: Int,
        val timeMillis: Long = 120_000L,
        val order: Int
    )