package com.relkin.carstatus.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Fmt {
    private val sym = DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = ' '; decimalSeparator = ',' }
    private val int = DecimalFormat("#,##0", sym)
    private val dec2 = DecimalFormat("#,##0.00", sym)
    private val dec1 = DecimalFormat("#,##0.#", sym)
    private val day = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val dayShort = DateTimeFormatter.ofPattern("dd.MM")
    private val month get() = DateTimeFormatter.ofPattern("LLLL yyyy", Lang.locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    /** Валюта отображения (настройка). */
    var currency = "грн"

    fun km(v: Long) = int.format(v) + " км"
    fun num(v: Long) = int.format(v)
    fun money(v: Double) = (if (abs(v - v.roundToLong()) < 0.005) int.format(v) else dec2.format(v)) + " " + currency
    fun money2(v: Double) = dec2.format(v) + " " + currency
    fun dec(v: Double) = dec1.format(v)
    fun day(d: LocalDate) = d.format(day)
    fun day(epochDay: Long) = LocalDate.ofEpochDay(epochDay).format(day)
    fun dayShort(d: LocalDate) = d.format(dayShort)
    fun month(d: LocalDate) = d.format(month).replaceFirstChar { it.uppercase() }
    fun dateTime(ms: Long): String {
        val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return z.toLocalDate().format(day) + " " + z.format(time)
    }
    fun hm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** Значение для поля ввода: без разделителей групп, точка как десятичный знак. */
    fun edit(v: Double): String = if (abs(v - v.roundToLong()) < 1e-9) v.roundToLong().toString() else "%.2f".format(Locale.ROOT, v).trimEnd('0').trimEnd('.')

    fun days(n: Long): String = "$n " + plural(n, tr("день", "день"), tr("дня", "дні"), tr("дней", "днів"))

    /** Формы множественного числа — правило одно для русского и украинского. */
    fun plural(n: Long, one: String, few: String, many: String): String {
        val m = abs(n) % 100
        val d = m % 10
        return when {
            m in 11..14 -> many
            d == 1L -> one
            d in 2..4 -> few
            else -> many
        }
    }
}

/** Вычисляет выражения из полей ввода: 45000+1200, 600*2 и т.п. */
object Calc {
    fun eval(input: String): Double? {
        val s = input.replace(',', '.').replace('×', '*').replace('÷', '/').replace('−', '-')
            .replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        return runCatching {
            val p = Parser(s)
            val v = p.expr()
            if (p.pos != s.length || v.isNaN() || v.isInfinite()) null else v
        }.getOrNull()
    }

    fun isExpression(input: String): Boolean = input.drop(1).any { it in "+-*/×÷−" }

    private class Parser(val s: String) {
        var pos = 0
        fun expr(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun term(): Double {
            var v = factor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val r = factor()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        fun factor(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -factor() }
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = expr()
                require(pos < s.length && s[pos] == ')')
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}

fun today(): LocalDate = LocalDate.now()
fun todayEpoch(): Long = LocalDate.now().toEpochDay()
