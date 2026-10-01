package ru.hopes.workouttimer.domain.model

import java.util.Locale

/**
 * Название для пустого упражнения. Захардкожено, а не в ресурсах: его пишут
 * миграция и DAO, где ресурсов нет. Текст совпадает с R.string.create_untitled.
 */
const val UNTITLED_EXERCISE_NAME = "Без названия"

// [\s\p{Z}] — Юникод-пробелы, включая неразрывный из вставленного текста.
// Голый \s на JVM видит только ASCII, а флаг (?U) Android (ICU) не знает и падает.
private val WHITESPACE_RUN = Regex("[\\s\\p{Z}]+")

/** Название, как его хранит справочник: без лишних пробелов, пустое — «Без названия». */
fun normalizedExerciseName(raw: String): String {
    val collapsed = raw.replace(WHITESPACE_RUN, " ").trim()
    return collapsed.ifEmpty { UNTITLED_EXERCISE_NAME }
}

/**
 * Ключ, по которому упражнения сходятся в одну запись справочника.
 * Считается только здесь: LOWER() в SQLite складывает одну ASCII-латиницу,
 * и «Присед» с «присед» в SQL остались бы разными.
 */
fun exerciseNameKey(raw: String): String =
    normalizedExerciseName(raw).lowercase(Locale.ROOT).replace('ё', 'е')
