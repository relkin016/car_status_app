package com.relkin.carstatus.data

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
        FUEL -> "заправка"
        OBD -> "OBD2"
        REMINDER -> "напоминание"
        RECORD -> "запись"
        else -> "вручную"
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
    val all = listOf(FUEL, SERVICE, PART, OTHER)
    fun label(t: Int) = when (t) {
        FUEL -> "Топливо"
        SERVICE -> "ТО / работы"
        PART -> "Запчасти"
        else -> "Прочее"
    }
}

/**
 * Запись: заправка, обслуживание, покупка запчасти, прочий расход.
 * Для ТО [amount] — стоимость работ, [parts] — запчастей/материалов. Для заправки [liters] и [full] — полный бак.
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
) {
    val total: Double get() = amount + parts
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

    /** Все показания одометра (дата, км), включая записи с пробегом. */
    val readings: List<Pair<Long, Long>> by lazy {
        (odo.map { it.date to it.km } + log.mapNotNull { e -> e.km?.let { e.date to it } })
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    val lastOdoDate: Long? by lazy { readings.maxOfOrNull { it.first } }

    val kind: VehicleKind get() = car.kind
    /** Средний суточный пробег, пока его не посчитать по истории. */
    val fallbackKmPerDay: Double get() = car.kmPerMonthOrDefault / 30.0

    fun item(id: Long?) = items.firstOrNull { it.id == id }
    fun photosOf(type: Int, id: Long) = photos.filter { it.ownerType == type && it.ownerId == id }
}
