package com.relkin.carstatus.data

import com.relkin.carstatus.util.tr

/** Даты — epochDay (LocalDate.toEpochDay), моменты — миллисекунды. */

data class Car(
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val vin: String = "",
    val plate: String = "",
    val engine: String = "",
    val fuel: String = "",
    val purchaseDate: Long? = null,
    val purchaseKm: Long? = null,
    val kind: VehicleKind = VehicleKind.PETROL,
    /** Средний пробег в месяц, пока по истории его не посчитать (0 — по виду транспорта). */
    val kmPerMonth: Int = 0,
) {
    val title: String get() = name.ifBlank { listOf(make, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { kind.defaultTitle } }
    /** Название с эмодзи вида — для списков и уведомлений. */
    val label: String get() = "${kind.emoji} $title"
    val kmPerMonthOrDefault: Int get() = kmPerMonth.takeIf { it > 0 } ?: kind.kmPerMonth
}

/** Источник показания одометра. */
object OdoSource {
    const val MANUAL = 0
    const val FUEL = 1
    const val OBD = 2
    const val REMINDER = 3
    const val RECORD = 4
    fun label(s: Int) = when (s) {
        FUEL -> tr("заправка", "заправка")
        OBD -> "OBD2"
        REMINDER -> tr("напоминание", "нагадування")
        RECORD -> tr("запись", "запис")
        else -> tr("вручную", "вручну")
    }
}

data class Odo(val id: Long, val date: Long, val km: Long, val source: Int)

/** Пункт ТО: два лимита — по пробегу и по времени (0 — лимит не используется). */
data class Item(
    val id: Long = 0,
    val name: String,
    val icon: String = "build",
    val kmInterval: Long = 0,
    val dayInterval: Long = 0,
    val lastKm: Long? = null,
    val lastDate: Long? = null,
    val enabled: Boolean = true,
    val position: Int = 0,
    val note: String = "",
    /** Ориентировочная стоимость (для прогноза расходов). */
    val cost: Double = 0.0,
)

/** Тип записи журнала расходов/обслуживания. */
object LogType {
    const val FUEL = 0
    const val SERVICE = 1
    const val PART = 2
    const val OTHER = 3
    /** Ремонт вне регламента ТО: проводка, кузов, подвеска и т.п. */
    const val WORK = 4
    val all = listOf(FUEL, SERVICE, WORK, PART, OTHER)
    fun label(t: Int) = when (t) {
        FUEL -> tr("Топливо", "Пальне")
        SERVICE -> tr("ТО", "ТО")
        WORK -> tr("Работы", "Роботи")
        PART -> tr("Запчасти", "Запчастини")
        else -> tr("Прочее", "Інше")
    }
}

/**
 * Запись: заправка, обслуживание, покупка запчасти, прочий расход.
 * Для ТО и работ [amount] — стоимость работ, [parts] — запчастей/материалов. Для заправки [liters] и [full] — полный бак.
 */
data class LogEntry(
    val id: Long = 0,
    val type: Int,
    val date: Long,
    val km: Long?,
    val itemId: Long? = null,
    val title: String = "",
    val amount: Double = 0.0,
    val parts: Double = 0.0,
    val liters: Double = 0.0,
    val full: Boolean = true,
    val note: String = "",
    /** Производители деталей/материалов (для ТО, работ, запчастей) — по строке на позицию, парно с [partNo]. */
    val brand: String = "",
    /** Артикулы (номера деталей) — по строке на позицию, парно с [brand]. */
    val partNo: String = "",
    /** Пробег введён вручную; иначе он подставлен по показаниям на дату записи. */
    val kmManual: Boolean = true,
) {
    val total: Double get() = amount + parts
    /** Позиции «бренд + артикул», пустые отброшены. */
    val partRefs: List<PartRef> get() = PartRef.decode(brand, partNo)
    /** «Бренд · арт. 123; Бренд2 · арт. 456» для списков и отчётов. */
    val partInfo: String get() = partRefs.joinToString("; ") { it.label }
}

/** Деталь в записи: производитель и артикул. */
data class PartRef(val brand: String = "", val partNo: String = "") {
    val isBlank: Boolean get() = brand.isBlank() && partNo.isBlank()
    val label: String get() = listOfNotNull(brand.trim().ifBlank { null }, partNo.trim().ifBlank { null }?.let { "арт. $it" }).joinToString(" · ")

    companion object {
        /** Позиции хранятся в колонках brand/part_no построчно; старые записи — одна позиция. */
        fun decode(brand: String, partNo: String): List<PartRef> {
            val b = brand.split('\n'); val p = partNo.split('\n')
            return List(maxOf(b.size, p.size)) { PartRef(b.getOrElse(it) { "" }.trim(), p.getOrElse(it) { "" }.trim()) }.filterNot { it.isBlank }
        }

        /** Обратно в пару строк (brand, partNo) для записи в БД. */
        fun encode(refs: List<PartRef>): Pair<String, String> {
            val list = refs.map { PartRef(it.brand.trim().replace('\n', ' '), it.partNo.trim().replace('\n', ' ')) }.filterNot { it.isBlank }
            return list.joinToString("\n") { it.brand } to list.joinToString("\n") { it.partNo }
        }
    }
}

/** Запись дневника: происшествие, странный звук, ДТП, поломка в пути. */
data class Incident(
    val id: Long = 0,
    val time: Long,
    val km: Long?,
    val title: String,
    val note: String = "",
    /** 0 — заметка, 1 — внимание, 2 — серьёзно. */
    val severity: Int = 0,
)

object PhotoOwner {
    const val NONE = 0
    const val LOG = 1
    const val INCIDENT = 2
    const val BREAKDOWN = 3
}

data class Photo(
    val id: Long = 0,
    val time: Long,
    val km: Long?,
    /** Имя файла в filesDir/photos. */
    val file: String,
    val caption: String = "",
    val ownerType: Int = PhotoOwner.NONE,
    val ownerId: Long = 0,
)

/** Список поломок: что нужно починить. */
data class Breakdown(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    /** 0 — терпит, 1 — скоро, 2 — срочно. */
    val priority: Int = 1,
    val done: Boolean = false,
    val createdDate: Long,
    val createdKm: Long? = null,
    val doneDate: Long? = null,
    val position: Int = 0,
)

/** Весь набор данных — неизменяемый снимок для экранов. */
data class Snapshot(
    val car: Car = Car(),
    val odo: List<Odo> = emptyList(),
    val items: List<Item> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val incidents: List<Incident> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val breakdowns: List<Breakdown> = emptyList(),
    /** Отмеченные шаги квестов: "квест:сезон:шаг". */
    val questDone: Set<String> = emptySet(),
    val setupDone: Boolean = false,
) {
    /** Текущий пробег — наибольшее известное показание. */
    val currentKm: Long by lazy {
        maxOf(odo.maxOfOrNull { it.km } ?: 0L, log.maxOfOrNull { it.km ?: 0L } ?: 0L, incidents.maxOfOrNull { it.km ?: 0L } ?: 0L)
    }

    /** Все показания одометра (дата, км), включая записи с введённым вручную пробегом (подставленный — производный от показаний). */
    val readings: List<Pair<Long, Long>> by lazy {
        (odo.map { it.date to it.km } + log.mapNotNull { e -> e.km?.takeIf { e.kmManual }?.let { e.date to it } })
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    val lastOdoDate: Long? by lazy { readings.maxOfOrNull { it.first } }

    /**
     * Пробег на дату — как есть из таблицы показаний, без расчётов:
     * последнее показание на эту дату или раньше; если раньше нет — самое первое.
     */
    fun kmAt(date: Long, exceptLog: Long = 0): Long {
        val r = odo.map { it.date to it.km }.sortedWith(compareBy({ it.first }, { it.second }))
        if (r.isEmpty()) return log.firstOrNull { it.id == exceptLog }?.km ?: currentKm
        return (r.lastOrNull { it.first <= date } ?: r.first()).second
    }

    val kind: VehicleKind get() = car.kind
    /** Средний суточный пробег, пока его не посчитать по истории. */
    val fallbackKmPerDay: Double get() = car.kmPerMonthOrDefault / 30.0

    fun item(id: Long?) = items.firstOrNull { it.id == id }
    fun photosOf(type: Int, id: Long) = photos.filter { it.ownerType == type && it.ownerId == id }
}
