package com.relkin.carstatus.data

import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/** Сезон комплекта шин. */
object TireSeason {
    const val SUMMER = 0
    const val WINTER = 1
    const val ALL = 2
    val all = listOf(SUMMER, WINTER, ALL)
    fun label(s: Int) = when (s) {
        WINTER -> tr("Зимние", "Зимові")
        ALL -> tr("Всесезонные", "Всесезонні")
        else -> tr("Летние", "Літні")
    }
    fun emoji(s: Int) = when (s) {
        WINTER -> "❄️"
        ALL -> "🌦️"
        else -> "☀️"
    }
}

/**
 * Комплект шин (или колёс). [kmBefore] — пробег комплекта до учёта в приложении,
 * [tread] — последний замер протектора, мм (0 — не мерили), [dot] — дата выпуска с боковины: «НННГГ» → неделя и год.
 */
data class TireSet(
    val id: Long = 0,
    val name: String,
    val season: Int = TireSeason.SUMMER,
    val brand: String = "",
    val model: String = "",
    val size: String = "",
    val dot: String = "",
    val purchaseDate: Long? = null,
    val price: Double = 0.0,
    val kmBefore: Long = 0,
    val tread: Double = 0.0,
    val treadDate: Long? = null,
    val storage: String = "",
    val note: String = "",
    val retired: Boolean = false,
    val position: Int = 0,
) {
    val title: String get() = name.ifBlank { listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { TireSeason.label(season) } }
    val label: String get() = "${TireSeason.emoji(season)} $title"
}

/** Период, когда комплект стоял на транспорте; открытый ([dateOff] = null) — стоит сейчас. */
data class TireMount(
    val id: Long = 0,
    val setId: Long,
    val dateOn: Long,
    val kmOn: Long,
    val dateOff: Long? = null,
    val kmOff: Long? = null,
)

/** Предупреждение по шинам: [key] — чтобы не напоминать повторно об одном и том же. */
data class TireWarn(val key: String, val text: String, val serious: Boolean)

object Tires {
    /** Шины ведут только для автомобилей и мотоциклов. */
    fun supported(kind: VehicleKind) = kind.isCar || kind == VehicleKind.MOTO

    /** Минимальный остаток протектора, мм: зимним нужна глубина для снега. */
    fun minTread(season: Int) = if (season == TireSeason.WINTER) 4.0 else 3.0
    /** Законный минимум для летних шин. */
    const val LEGAL_TREAD = 1.6
    /** С этого возраста резина дубеет и трескается, даже если протектор в норме. */
    const val OLD_YEARS = 6

    fun mounted(s: Snapshot): TireSet? = s.tireMounts.firstOrNull { it.dateOff == null }?.let { m -> s.tireSets.firstOrNull { it.id == m.setId } }
    fun openMount(s: Snapshot): TireMount? = s.tireMounts.firstOrNull { it.dateOff == null }
    fun mountsOf(s: Snapshot, setId: Long) = s.tireMounts.filter { it.setId == setId }.sortedByDescending { it.dateOn }

    /** Пробег комплекта: до учёта + все периоды на колёсах (текущий — до текущего пробега). */
    fun km(s: Snapshot, set: TireSet): Long =
        set.kmBefore + s.tireMounts.filter { it.setId == set.id }.sumOf { ((it.kmOff ?: s.currentKm) - it.kmOn).coerceAtLeast(0) }

    /** Дата выпуска по DOT: последние 4 цифры — неделя и год (2321 → 23-я неделя 2021). Старый 3-значный формат не разбираем. */
    fun made(dot: String): LocalDate? {
        val d = dot.filter { it.isDigit() }.takeLast(4).takeIf { it.length == 4 } ?: return null
        val week = d.take(2).toInt()
        val year = 2000 + d.takeLast(2).toInt()
        if (week !in 1..53) return null
        return runCatching { LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong()) }.getOrNull()
    }

    /** Возраст по DOT, полных лет. */
    fun ageYears(set: TireSet, today: LocalDate = LocalDate.now()): Long? =
        made(set.dot)?.let { ChronoUnit.YEARS.between(it, today).coerceAtLeast(0) }

    /** Протектор и возраст комплекта (списанные не проверяем). */
    fun warnings(set: TireSet, today: LocalDate = LocalDate.now()): List<TireWarn> = buildList {
        if (set.retired) return@buildList
        val t = set.tread
        if (t > 0 && t <= LEGAL_TREAD) add(TireWarn("tread:${set.id}:legal", tr("протектор ${Fmt.dec(t)} мм — ниже законного минимума $LEGAL_TREAD мм", "протектор ${Fmt.dec(t)} мм — нижче законного мінімуму $LEGAL_TREAD мм"), true))
        else if (t > 0 && t < minTread(set.season)) add(TireWarn("tread:${set.id}:low", tr("протектор ${Fmt.dec(t)} мм — пора менять (минимум ${Fmt.dec(minTread(set.season))} мм)", "протектор ${Fmt.dec(t)} мм — час міняти (мінімум ${Fmt.dec(minTread(set.season))} мм)"), false))
        val age = ageYears(set, today)
        if (age != null && age >= OLD_YEARS) add(TireWarn("age:${set.id}:$age", tr("шинам $age ${Fmt.plural(age, "год", "года", "лет")} — резина дубеет, проверьте трещины", "шинам $age ${Fmt.plural(age, "рік", "роки", "років")} — гума дубіє, перевірте тріщини"), age >= OLD_YEARS + 2))
    }

    /**
     * Не по сезону: зимние в мае–сентябре, летние в ноябре–марте. Ключ — на сезон, чтобы напомнить раз в год.
     * Всесезонные не проверяем.
     */
    fun seasonWarn(s: Snapshot, today: LocalDate = LocalDate.now()): TireWarn? {
        val set = mounted(s) ?: return null
        val m = today.monthValue
        return when {
            set.season == TireSeason.WINTER && m in 5..9 ->
                TireWarn("season:summer:${today.year}", tr("Стоят зимние шины — пора переобуться в летние", "Стоять зимові шини — час перевзутися в літні"), false)
            set.season == TireSeason.SUMMER && (m >= 11 || m <= 3) ->
                TireWarn("season:winter:${if (m >= 11) today.year else today.year - 1}", tr("Стоят летние шины — пора переобуться в зимние", "Стоять літні шини — час перевзутися в зимові"), true)
            else -> null
        }
    }
}
