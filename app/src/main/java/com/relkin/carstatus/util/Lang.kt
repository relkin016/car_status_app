package com.relkin.carstatus.util

import android.content.res.Resources
import java.util.Locale

/** Язык интерфейса: русский или украинский. Строки — прямо в коде через [tr]. */
object Lang {
    /** Режимы настройки: 0 — как в системе, 1 — русский, 2 — українська. */
    const val AUTO = 0
    const val RU = 1
    const val UK = 2

    @Volatile var uk = false
        private set

    fun apply(mode: Int) {
        uk = when (mode) {
            RU -> false
            UK -> true
            else -> systemUk()
        }
    }

    /** Первый из языков системы, который мы знаем (uk/ru); если ни одного — русский. */
    fun systemUk(): Boolean {
        val list = Resources.getSystem().configuration.locales
        for (i in 0 until list.size()) when (list[i].language) {
            "uk" -> return true
            "ru" -> return false
        }
        return false
    }

    val tag: String get() = if (uk) "uk" else "ru"
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

/** Строка на текущем языке. */
fun tr(ru: String, uk: String): String = if (Lang.uk) uk else ru
