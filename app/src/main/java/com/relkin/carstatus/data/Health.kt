package com.relkin.carstatus.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

enum class Trigger { KM, TIME }

/**
 * «Здоровье» пункта ТО. [hp] — доля оставшегося ресурса по худшему из двух лимитов (может быть < 0 — просрочено).
 * [due] — прогнозная дата, когда сработает первый лимит, [trigger] — какой именно.
 */
data class ItemHealth(
    val item: Item,
    val hp: Double?,
    val kmLeft: Long?,
    val daysLeft: Long?,
    val due: LocalDate?,
    val dueKm: Long?,
    val trigger: Trigger?,
) {
    val percent: Int? get() = hp?.let { (it.coerceIn(0.0, 1.0) * 100).toInt() }
    val overdue: Boolean get() = (hp ?: 1.0) <= 0.0
}

data class CarHealth(
    val items: List<ItemHealth>,
    /** Среднее по известным пунктам, 0..1. */
    val overall: Double?,
    val worst: ItemHealth?,
    val kmPerDay: Double,
    /** Средний пробег рассчитан по истории (а не взят из настроек). */
    val kmPerDayMeasured: Boolean,
)

object Health {
    /** Средний суточный пробег: по показаниям за последние полгода, иначе за всё время, иначе [fallback]. */
    fun kmPerDay(s: Snapshot, fallback: Double, today: LocalDate = LocalDate.now()): Pair<Double, Boolean> {
        val r = s.readings
        fun rate(points: List<Pair<Long, Long>>): Double? {
            if (points.size < 2) return null
            val span = points.last().first - points.first().first
            val delta = points.maxOf { it.second } - points.minOf { it.second }
            return if (span >= 14 && delta > 0) delta.toDouble() / span else null
        }
        val recent = r.filter { it.first >= today.toEpochDay() - 180 }
        val v = rate(recent) ?: rate(r)
        return if (v != null) v to true else fallback to false
    }

    fun item(it: Item, curKm: Long, kmPerDay: Double, today: LocalDate = LocalDate.now()): ItemHealth {
        val t = today.toEpochDay()
        val kmFrac = if (it.kmInterval > 0 && it.lastKm != null) 1.0 - (curKm - it.lastKm).toDouble() / it.kmInterval else null
        val dayFrac = if (it.dayInterval > 0 && it.lastDate != null) 1.0 - (t - it.lastDate).toDouble() / it.dayInterval else null
        val kmLeft = if (kmFrac != null) it.lastKm!! + it.kmInterval - curKm else null
        val daysLeft = if (dayFrac != null) it.lastDate!! + it.dayInterval - t else null
        val byKm = kmLeft?.let { left ->
            if (left <= 0) today else if (kmPerDay > 0) today.plusDays(ceil(left / kmPerDay).toLong()) else null
        }
        val byTime = daysLeft?.let { today.plusDays(max(0, it)) }
        val (due, trig) = when {
            byKm != null && byTime != null -> if (byKm.isBefore(byTime)) byKm to Trigger.KM else byTime to Trigger.TIME
            byKm != null -> byKm to Trigger.KM
            byTime != null -> byTime to Trigger.TIME
            // лимит по пробегу есть, а средний пробег неизвестен — дату не прогнозируем
            kmFrac != null -> null to Trigger.KM
            else -> null to null
        }
        val hp = listOfNotNull(kmFrac, dayFrac).minOrNull()
        return ItemHealth(it, hp, kmLeft, daysLeft, due, kmLeft?.let { curKm + it }, trig)
    }

    fun of(s: Snapshot, fallbackKmPerDay: Double, today: LocalDate = LocalDate.now()): CarHealth {
        val (rate, measured) = kmPerDay(s, fallbackKmPerDay, today)
        val list = s.items.filter { it.enabled }.map { item(it, s.currentKm, rate, today) }
            .sortedWith(compareBy<ItemHealth> { it.hp ?: 2.0 }.thenBy { it.item.position })
        val known = list.mapNotNull { it.hp?.coerceIn(0.0, 1.0) }
        return CarHealth(
            items = list,
            overall = if (known.isEmpty()) null else known.average(),
            worst = list.firstOrNull { it.hp != null },
            kmPerDay = rate,
            kmPerDayMeasured = measured,
        )
    }
}
