package ru.hopes.workouttimer.presentation.utils

fun Double.toCorrectNum(): String {
    return if (this == this.toInt().toDouble()) {
        this.toInt().toString()
    } else {
        this.toString()
    }
}