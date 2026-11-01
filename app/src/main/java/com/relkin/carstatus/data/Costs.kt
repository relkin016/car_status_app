package com.relkin.carstatus.data

import com.relkin.carstatus.util.tr
import java.time.LocalDate
import java.time.YearMonth

/** Категории «реальной цены километра». */
enum class CostCat(private val ru: String, private val uk: String) {
    FUEL("Топливо", "Пальне"), SERVICE("ТО", "ТО"), WORK("Работы", "Роботи"), PARTS("Запчасти", "Запчастини"), OTHER("Прочее", "Інше");

    val label: String get() = tr(ru, uk)

    /** «Топливо» у электротранспорта — «Электричество». */
    fun label(kind: VehicleKind) = if (this == FUEL) kind.energyLabel else label
}

data class CostReport(
    val from: Long?,
    val to: Long,
    val entries: List<LogEntry>,
    val byCat: Map<CostCat, Double>,
    val total: Double,
    /** Пробег за период по показаниям одометра. */
    val km: Long,
    val startKm: Long?,
    val endKm: Long?,
    val liters: Double,
    /** л (или кВт·ч) на 100 км по методу полных баков, если хватает данных. */
    val consumption: Double?,
    val pricePerLiter: Double?,
    val byMonth: List<Pair<YearMonth, Map<CostCat, Double>>>,
) {
    val perKm: Double? get() = if (km > 0) total / km else null
    fun perKm(c: CostCat): Double? = if (km > 0) (byCat[c] ?: 0.0) / km else null
}

object Costs {
    fun split(e: LogEntry): Map<CostCat, Double> = when (e.type) {
        LogType.FUEL -> mapOf(CostCat.FUEL to e.total)
        LogType.SERVICE -> mapOf(CostCat.SERVICE to e.amount, CostCat.PARTS to e.parts)
        LogType.WORK -> mapOf(CostCat.WORK to e.amount, CostCat.PARTS to e.parts)
        LogType.PART -> mapOf(CostCat.PARTS to e.total)
        else -> mapOf(CostCat.OTHER to e.total)
    }

    fun report(s: Snapshot, from: Long?, to: Long = LocalDate.now().toEpochDay()): CostReport {
        val entries = s.log.filter { (from == null || it.date >= from) && it.date <= to }.sortedWith(compareBy({ it.date }, { it.km ?: 0 }, { it.id }))
        val byCat = CostCat.entries.associateWith { 0.0 }.toMutableMap()
        for (e in entries) for ((c, v) in split(e)) byCat[c] = byCat.getValue(c) + v

        // Пробег за период: от последнего показания до начала периода (или первого внутри) до последнего внутри
        val r = s.readings
        val inside = r.filter { (from == null || it.first >= from) && it.first <= to }
        // Одометр назад не идёт: показание до периода выше, чем внутри, — ошибочное (запись задним числом с текущим пробегом)
        val before = if (from == null) null else r.filter { it.first < from }.maxOfOrNull { it.second }
        val insideMin = inside.minOfOrNull { it.second }
        val startKm = listOfNotNull(before, insideMin).minOrNull()
        val endKm = inside.maxOfOrNull { it.second }
        val km = if (startKm != null && endKm != null) (endKm - startKm).coerceAtLeast(0) else 0

        val fuel = entries.filter { it.type == LogType.FUEL }
        val liters = fuel.sumOf { it.liters }
        val fuelMoney = fuel.filter { it.liters > 0 }.sumOf { it.total }
        val ppl = if (liters > 0) fuelMoney / liters else null

        val byMonth = entries.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }.toSortedMap().map { (m, list) ->
            val mm = CostCat.entries.associateWith { 0.0 }.toMutableMap()
            for (e in list) for ((c, v) in split(e)) mm[c] = mm.getValue(c) + v
            m to mm.toMap()
        }
        return CostReport(from, to, entries, byCat, byCat.values.sum(), km, startKm, endKm, liters, consumption(fuel), ppl, byMonth)
    }

    /** Расход по полным бакам: литры всех заправок после первого полного бака до последнего полного, на пройденные км. */
    fun consumption(fuel: List<LogEntry>): Double? {
        val withKm = fuel.filter { it.km != null && it.liters > 0 }.sortedBy { it.km }
        val firstFull = withKm.indexOfFirst { it.full }
        val lastFull = withKm.indexOfLast { it.full }
        if (firstFull < 0 || lastFull <= firstFull) return null
        val dist = withKm[lastFull].km!! - withKm[firstFull].km!!
        if (dist <= 0) return null
        val l = withKm.subList(firstFull + 1, lastFull + 1).sumOf { it.liters }
        return l / dist * 100
    }
}
