package com.relkin.carstatus.data

import com.relkin.carstatus.util.Block
import com.relkin.carstatus.util.Block.Col
import com.relkin.carstatus.util.Block.Row
import com.relkin.carstatus.util.Doc
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.Tone
import com.relkin.carstatus.util.tr
import java.time.LocalDate

/** Документы (рисуются в PDF): история обслуживания для покупателя и выписка расходов «как в банке». */
object Reports {
    private fun kmOr(km: Long?) = km?.let { Fmt.km(it) } ?: "—"
    private fun footer(version: String) = tr("Авто·Статус $version · сформировано ${Fmt.day(LocalDate.now())} · данные вносились владельцем", "Авто·Статус $version · сформовано ${Fmt.day(LocalDate.now())} · дані вносив власник")

    private fun carTable(s: Snapshot): Block {
        val c = s.car
        val rows = listOf(
            tr("Транспорт", "Транспорт") to listOf(c.make, c.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { c.title },
            tr("Вид", "Вид") to c.kind.label,
            tr("Год выпуска", "Рік випуску") to c.year, tr("VIN / номер рамы", "VIN / номер рами") to c.vin, tr("Госномер", "Держномер") to c.plate, tr("Двигатель / мотор", "Двигун / мотор") to c.engine,
            c.kind.energyLabel to c.fuel,
            tr("Текущий пробег", "Поточний пробіг") to Fmt.km(s.currentKm),
        ).filter { it.second.isNotBlank() }.map { (k, v) -> Row(listOf(k, v)) }
        return Block.Table(listOf(Col("", 1f), Col("", 2.2f)), rows)
    }

    fun entryTitle(s: Snapshot, e: LogEntry): String = e.title.ifBlank {
        when (e.type) {
            LogType.FUEL -> s.kind.refuel + if (e.liters > 0) " ${Fmt.dec(e.liters)} ${s.kind.unit}" else ""
            LogType.SERVICE -> s.item(e.itemId)?.name ?: tr("Обслуживание", "Обслуговування")
            LogType.WORK -> tr("Работы", "Роботи")
            LogType.PART -> tr("Запчасти", "Запчастини")
            else -> tr("Расход", "Витрата")
        }
    }

    fun buyerHistory(s: Snapshot, h: CarHealth, version: String, withCosts: Boolean): Doc {
        val b = mutableListOf<Block>()
        b += Block.Heading(if (s.kind.isCar) tr("Автомобиль", "Автомобіль") else s.kind.label)
        b += carTable(s)

        val services = s.log.filter { it.type == LogType.SERVICE || it.type == LogType.WORK || it.type == LogType.PART }.sortedWith(compareBy({ it.date }, { it.km ?: 0 }))
        val first = s.readings.firstOrNull()
        val open = s.breakdowns.filter { !it.done }
        b += Block.Heading(tr("Кратко", "Коротко"))
        b += Block.Stats(buildList {
            add(services.size.toString() to tr("записей об обслуживании", "записів про обслуговування"))
            h.overall?.let { add("${(it * 100).toInt()}%" to tr("состояние по регламенту", "стан за регламентом")) }
            add(open.size.toString() to tr("открытых неисправностей", "відкритих несправностей"))
            if (first != null) add(Fmt.day(first.first) to tr("история с пробега ${Fmt.km(first.second)}", "історія з пробігу ${Fmt.km(first.second)}"))
            if (withCosts) {
                val r = Costs.report(s, null)
                add(Fmt.money((r.byCat[CostCat.SERVICE] ?: 0.0) + (r.byCat[CostCat.WORK] ?: 0.0) + (r.byCat[CostCat.PARTS] ?: 0.0)) to tr("вложено в ТО, работы и запчасти", "вкладено в ТО, роботи й запчастини"))
                r.consumption?.let { add("${Fmt.dec(it)} ${s.kind.unit}" to tr("средний расход на 100 км", "середня витрата на 100 км")) }
            }
        })
        val overdue = h.items.filter { it.overdue }
        if (overdue.isNotEmpty()) b += Block.Para(tr("Просрочено по регламенту: ${overdue.joinToString { it.item.name }}", "Прострочено за регламентом: ${overdue.joinToString { it.item.name }}"))

        b += Block.Heading(tr("Состояние по регламенту", "Стан за регламентом"))
        b += Block.Table(
            listOf(Col(tr("Узел", "Вузол"), 1.6f), Col(tr("Последнее обслуживание", "Останнє обслуговування"), 1.4f), Col(tr("Интервал", "Інтервал"), 1.2f), Col(tr("Осталось", "Залишилось"), 1.2f), Col(tr("Состояние", "Стан"), 0.9f, true)),
            h.items.sortedBy { it.item.position }.map { ih ->
                val it = ih.item
                val last = listOfNotNull(it.lastDate?.let { d -> Fmt.day(d) }, it.lastKm?.let { k -> Fmt.km(k) }).joinToString(", ")
                val interval = listOfNotNull(
                    it.kmInterval.takeIf { v -> v > 0 }?.let { v -> Fmt.km(v) },
                    it.dayInterval.takeIf { v -> v > 0 }?.let { v -> Fmt.days(v) },
                ).joinToString(" / ")
                val left = listOfNotNull(ih.kmLeft?.let { v -> Fmt.km(v) }, ih.daysLeft?.let { v -> Fmt.days(v) }).joinToString(" / ")
                val state = ih.percent?.let { p -> if (ih.overdue) tr("просрочено", "прострочено") else "$p%" } ?: tr("нет данных", "немає даних")
                Row(listOf(it.name, last, interval, left, state), if (ih.overdue) Tone.BAD else Tone.NORMAL)
            },
            empty = tr("Пункты регламента не заданы", "Пункти регламенту не задано"),
        )

        b += Block.Heading(tr("Журнал обслуживания", "Журнал обслуговування"))
        val cols = listOfNotNull(
            Col(tr("Дата", "Дата"), 0.8f), Col(tr("Пробег", "Пробіг"), 0.85f, true), Col(tr("Работы", "Роботи"), 1.7f), Col(tr("Бренд / артикул", "Бренд / артикул"), 1.3f),
            if (withCosts) Col(tr("Стоимость", "Вартість"), 0.9f, true) else null, Col(tr("Примечание", "Примітка"), 1.5f),
        )
        b += Block.Table(cols, services.map { e ->
            Row(listOfNotNull(Fmt.day(e.date), kmOr(e.km), entryTitle(s, e), e.partInfo, if (withCosts) Fmt.money(e.total) else null, e.note))
        })

        if (s.incidents.isNotEmpty()) {
            b += Block.Heading(tr("Дневник происшествий", "Щоденник подій"))
            b += Block.Table(
                listOf(Col(tr("Дата", "Дата"), 1f), Col(tr("Пробег", "Пробіг"), 0.85f, true), Col(tr("Событие", "Подія"), 1.6f), Col(tr("Описание", "Опис"), 2.5f)),
                s.incidents.sortedBy { it.time }.map { i ->
                    Row(listOf(Fmt.dateTime(i.time), kmOr(i.km), i.title, i.note), when (i.severity) { 2 -> Tone.BAD; 1 -> Tone.WARN; else -> Tone.NORMAL })
                },
            )
        }

        if (s.breakdowns.isNotEmpty()) {
            b += Block.Heading(tr("Неисправности", "Несправності"))
            b += Block.Table(
                listOf(Col(tr("Статус", "Статус"), 0.8f), Col(tr("Неисправность", "Несправність"), 1.8f), Col(tr("Выявлено", "Виявлено"), 1.1f), Col(tr("Устранено", "Усунено"), 0.8f), Col(tr("Заметка", "Нотатка"), 2f)),
                s.breakdowns.sortedWith(compareBy({ it.done }, { -it.priority })).map { br ->
                    Row(
                        listOf(
                            if (br.done) tr("устранено", "усунено") else when (br.priority) { 2 -> tr("срочно", "терміново"); 1 -> tr("скоро", "скоро"); else -> tr("терпит", "терпить") },
                            br.title, Fmt.day(br.createdDate) + (br.createdKm?.let { ", ${Fmt.km(it)}" } ?: ""),
                            br.doneDate?.let { Fmt.day(it) } ?: "", br.note,
                        ),
                        if (br.done) Tone.MUTED else if (br.priority == 2) Tone.BAD else Tone.NORMAL,
                    )
                },
            )
        }

        val odo = s.readings
        if (odo.size >= 2) {
            b += Block.Heading(tr("Показания одометра", "Показники одометра"))
            val grows = odo.zipWithNext().all { (a, c) -> c.second >= a.second }
            b += Block.Para(tr("Пробег только рос: ", "Пробіг лише зростав: ") + if (grows) tr("да", "так") else tr("нет — есть показания меньше предыдущих", "ні — є показники, менші за попередні"))
            // По одному показанию на месяц — чтобы таблица не разрасталась
            b += Block.Table(
                listOf(Col(tr("Дата", "Дата"), 1f), Col(tr("Пробег", "Пробіг"), 1f, true)),
                odo.groupBy { LocalDate.ofEpochDay(it.first).withDayOfMonth(1) }.values.map { it.last() }
                    .map { Row(listOf(Fmt.day(it.first), Fmt.km(it.second))) },
            )
        }
        if (s.photos.isNotEmpty()) b += Block.Para(tr("В фото-журнале владельца: ${s.photos.size} фото (предоставляются по запросу).", "У фотожурналі власника: ${s.photos.size} фото (надаються на запит)."), muted = true)

        return Doc(tr("История обслуживания: ${s.car.title}", "Історія обслуговування: ${s.car.title}"), tr("Сформировано ${Fmt.day(LocalDate.now())} · пробег ${Fmt.km(s.currentKm)}", "Сформовано ${Fmt.day(LocalDate.now())} · пробіг ${Fmt.km(s.currentKm)}"), footer(version), b)
    }

    /** Выписка как из банка: входящий/исходящий пробег, операции с нарастающим итогом, обороты по категориям. */
    fun statement(s: Snapshot, r: CostReport, version: String): Doc {
        val fromText = r.from?.let { Fmt.day(it) } ?: r.entries.firstOrNull()?.let { Fmt.day(it.date) } ?: Fmt.day(r.to)
        val b = mutableListOf<Block>()
        b += Block.Stats(
            listOf(
                Fmt.money2(r.total) to tr("итого расходов", "усього витрат"),
                (r.perKm?.let { Fmt.money2(it) + "/км" } ?: "—") to tr("цена километра", "ціна кілометра"),
                Fmt.km(r.km) to tr("пройдено за период", "пройдено за період"),
            ),
        )
        b += Block.Table(
            listOf(Col("", 1f), Col("", 2.2f)),
            listOf(
                Row(listOf(tr("Транспорт", "Транспорт"), s.car.title + if (s.car.plate.isNotBlank()) " · ${s.car.plate}" else "")),
                Row(listOf(tr("Период", "Період"), "$fromText — ${Fmt.day(r.to)}")),
                Row(listOf(tr("Пробег на начало", "Пробіг на початок"), kmOr(r.startKm))),
                Row(listOf(tr("Пробег на конец", "Пробіг на кінець"), kmOr(r.endKm))),
                Row(listOf(tr("Операций", "Операцій"), r.entries.size.toString())),
            ),
        )

        b += Block.Heading(tr("Операции", "Операції"))
        var run = 0.0
        b += Block.Table(
            listOf(
                Col("№", 0.35f, true), Col(tr("Дата", "Дата"), 0.8f), Col(tr("Пробег", "Пробіг"), 0.75f, true), Col(tr("Операция", "Операція"), 2.2f),
                Col(tr("Категория", "Категорія"), 1f), Col(tr("Сумма", "Сума"), 0.95f, true), Col(tr("Итог", "Підсумок"), 1f, true),
            ),
            r.entries.mapIndexed { i, e ->
                run += e.total
                val cat = if ((e.type == LogType.SERVICE || e.type == LogType.WORK) && e.parts > 0 && e.amount > 0) LogType.label(e.type) + tr(" + запчасти", " + запчастини") else if (e.type == LogType.FUEL) s.kind.refuel else LogType.label(e.type)
                val name = entryTitle(s, e) + (if (e.partInfo.isNotBlank()) "\n${e.partInfo}" else "") + if (e.note.isNotBlank()) "\n${e.note}" else ""
                Row(listOf("${i + 1}", Fmt.day(e.date), e.km?.let { Fmt.num(it) } ?: "—", name, cat, "−${Fmt.money2(e.total)}", Fmt.money2(run)))
            },
            empty = tr("Операций за период нет", "Операцій за період немає"),
        )

        b += Block.Heading(tr("Обороты по категориям", "Обороти за категоріями"))
        b += Block.Table(
            listOf(Col(tr("Категория", "Категорія"), 1.4f), Col(tr("Сумма", "Сума"), 1f, true), Col(tr("Доля", "Частка"), 0.6f, true), Col("${Fmt.currency}/км", 0.8f, true)),
            CostCat.entries.map { c ->
                val v = r.byCat[c] ?: 0.0
                val share = if (r.total > 0) "${(v / r.total * 100).toInt()}%" else "—"
                Row(listOf(c.label(s.kind), Fmt.money2(v), share, r.perKm(c)?.let { Fmt.money2(it) } ?: "—"), if (v == 0.0) Tone.MUTED else Tone.NORMAL)
            } + Row(listOf(tr("Итого", "Разом"), Fmt.money2(r.total), "100%", r.perKm?.let { Fmt.money2(it) } ?: "—"), Tone.STRONG),
        )

        if (r.byMonth.size > 1) {
            b += Block.Heading(tr("По месяцам", "За місяцями"))
            b += Block.Table(
                listOf(Col(tr("Месяц", "Місяць"), 1.3f)) + CostCat.entries.map { Col(it.label(s.kind), 1f, true) } + Col(tr("Итого", "Разом"), 1.1f, true),
                r.byMonth.map { (m, v) ->
                    Row(listOf(Fmt.month(m.atDay(1))) + CostCat.entries.map { Fmt.money(v[it] ?: 0.0) } + Fmt.money(v.values.sum()))
                },
            )
        }
        if (r.liters > 0) {
            val u = s.kind.unit
            b += Block.Heading(s.kind.energyLabel)
            b += Block.Stats(listOfNotNull(
                "${Fmt.dec(r.liters)} $u" to if (s.kind.energy == Energy.ELECTRIC) tr("заряжено", "заряджено") else tr("заправлено", "заправлено"),
                r.pricePerLiter?.let { "${Fmt.money2(it)}/$u" to tr("средняя цена", "середня ціна") },
                r.consumption?.let { "${Fmt.dec(it)} $u" to tr("средний расход на 100 км", "середня витрата на 100 км") },
            ))
        }
        return Doc(
            tr("Выписка расходов: ${s.car.title}", "Виписка витрат: ${s.car.title}"),
            tr("${s.kind.label} · период $fromText — ${Fmt.day(r.to)}", "${s.kind.label} · період $fromText — ${Fmt.day(r.to)}"),
            footer(version), b,
        )
    }
}
