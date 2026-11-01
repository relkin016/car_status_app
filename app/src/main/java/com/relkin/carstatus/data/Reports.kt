package com.relkin.carstatus.data

import com.relkin.carstatus.util.Block
import com.relkin.carstatus.util.Block.Col
import com.relkin.carstatus.util.Block.Row
import com.relkin.carstatus.util.Doc
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.Tone
import java.time.LocalDate

/** Документы (рисуются в PDF): история обслуживания для покупателя и выписка расходов «как в банке». */
object Reports {
    private fun kmOr(km: Long?) = km?.let { Fmt.km(it) } ?: "—"
    private fun footer(version: String) = "Авто·Статус $version · сформировано ${Fmt.day(LocalDate.now())} · данные вносились владельцем"

    private fun carTable(s: Snapshot): Block {
        val c = s.car
        val rows = listOf(
            "Транспорт" to listOf(c.make, c.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { c.title },
            "Вид" to c.kind.label,
            "Год выпуска" to c.year, "VIN / номер рамы" to c.vin, "Госномер" to c.plate, "Двигатель / мотор" to c.engine,
            c.kind.energyLabel to c.fuel,
            "Текущий пробег" to Fmt.km(s.currentKm),
        ).filter { it.second.isNotBlank() }.map { (k, v) -> Row(listOf(k, v)) }
        return Block.Table(listOf(Col("", 1f), Col("", 2.2f)), rows)
    }

    fun entryTitle(s: Snapshot, e: LogEntry): String = e.title.ifBlank {
        when (e.type) {
            LogType.FUEL -> s.kind.refuel + if (e.liters > 0) " ${Fmt.dec(e.liters)} ${s.kind.unit}" else ""
            LogType.SERVICE -> s.item(e.itemId)?.name ?: "Обслуживание"
            LogType.WORK -> "Работы"
            LogType.PART -> "Запчасти"
            else -> "Расход"
        }
    }

    fun buyerHistory(s: Snapshot, h: CarHealth, version: String, withCosts: Boolean): Doc {
        val b = mutableListOf<Block>()
        b += Block.Heading(if (s.kind.isCar) "Автомобиль" else s.kind.label)
        b += carTable(s)

        val services = s.log.filter { it.type == LogType.SERVICE || it.type == LogType.WORK || it.type == LogType.PART }.sortedWith(compareBy({ it.date }, { it.km ?: 0 }))
        val first = s.readings.firstOrNull()
        val open = s.breakdowns.filter { !it.done }
        b += Block.Heading("Кратко")
        b += Block.Stats(buildList {
            add(services.size.toString() to "записей об обслуживании")
            h.overall?.let { add("${(it * 100).toInt()}%" to "состояние по регламенту") }
            add(open.size.toString() to "открытых неисправностей")
            if (first != null) add(Fmt.day(first.first) to "история с пробега ${Fmt.km(first.second)}")
            if (withCosts) {
                val r = Costs.report(s, null)
                add(Fmt.money((r.byCat[CostCat.SERVICE] ?: 0.0) + (r.byCat[CostCat.WORK] ?: 0.0) + (r.byCat[CostCat.PARTS] ?: 0.0)) to "вложено в ТО, работы и запчасти")
                r.consumption?.let { add("${Fmt.dec(it)} ${s.kind.unit}" to "средний расход на 100 км") }
            }
        })
        val overdue = h.items.filter { it.overdue }
        if (overdue.isNotEmpty()) b += Block.Para("Просрочено по регламенту: ${overdue.joinToString { it.item.name }}")

        b += Block.Heading("Состояние по регламенту")
        b += Block.Table(
            listOf(Col("Узел", 1.6f), Col("Последнее обслуживание", 1.4f), Col("Интервал", 1.2f), Col("Осталось", 1.2f), Col("Состояние", 0.9f, true)),
            h.items.sortedBy { it.item.position }.map { ih ->
                val it = ih.item
                val last = listOfNotNull(it.lastDate?.let { d -> Fmt.day(d) }, it.lastKm?.let { k -> Fmt.km(k) }).joinToString(", ")
                val interval = listOfNotNull(
                    it.kmInterval.takeIf { v -> v > 0 }?.let { v -> Fmt.km(v) },
                    it.dayInterval.takeIf { v -> v > 0 }?.let { v -> Fmt.days(v) },
                ).joinToString(" / ")
                val left = listOfNotNull(ih.kmLeft?.let { v -> Fmt.km(v) }, ih.daysLeft?.let { v -> Fmt.days(v) }).joinToString(" / ")
                val state = ih.percent?.let { p -> if (ih.overdue) "просрочено" else "$p%" } ?: "нет данных"
                Row(listOf(it.name, last, interval, left, state), if (ih.overdue) Tone.BAD else Tone.NORMAL)
            },
            empty = "Пункты регламента не заданы",
        )

        b += Block.Heading("Журнал обслуживания")
        val cols = listOfNotNull(
            Col("Дата", 0.8f), Col("Пробег", 0.85f, true), Col("Работы", 1.7f), Col("Бренд / артикул", 1.3f),
            if (withCosts) Col("Стоимость", 0.9f, true) else null, Col("Примечание", 1.5f),
        )
        b += Block.Table(cols, services.map { e ->
            Row(listOfNotNull(Fmt.day(e.date), kmOr(e.km), entryTitle(s, e), e.partInfo, if (withCosts) Fmt.money(e.total) else null, e.note))
        })

        if (s.incidents.isNotEmpty()) {
            b += Block.Heading("Дневник происшествий")
            b += Block.Table(
                listOf(Col("Дата", 1f), Col("Пробег", 0.85f, true), Col("Событие", 1.6f), Col("Описание", 2.5f)),
                s.incidents.sortedBy { it.time }.map { i ->
                    Row(listOf(Fmt.dateTime(i.time), kmOr(i.km), i.title, i.note), when (i.severity) { 2 -> Tone.BAD; 1 -> Tone.WARN; else -> Tone.NORMAL })
                },
            )
        }

        if (s.breakdowns.isNotEmpty()) {
            b += Block.Heading("Неисправности")
            b += Block.Table(
                listOf(Col("Статус", 0.8f), Col("Неисправность", 1.8f), Col("Выявлено", 1.1f), Col("Устранено", 0.8f), Col("Заметка", 2f)),
                s.breakdowns.sortedWith(compareBy({ it.done }, { -it.priority })).map { br ->
                    Row(
                        listOf(
                            if (br.done) "устранено" else when (br.priority) { 2 -> "срочно"; 1 -> "скоро"; else -> "терпит" },
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
            b += Block.Heading("Показания одометра")
            val grows = odo.zipWithNext().all { (a, c) -> c.second >= a.second }
            b += Block.Para("Пробег только рос: " + if (grows) "да" else "нет — есть показания меньше предыдущих")
            // По одному показанию на месяц — чтобы таблица не разрасталась
            b += Block.Table(
                listOf(Col("Дата", 1f), Col("Пробег", 1f, true)),
                odo.groupBy { LocalDate.ofEpochDay(it.first).withDayOfMonth(1) }.values.map { it.last() }
                    .map { Row(listOf(Fmt.day(it.first), Fmt.km(it.second))) },
            )
        }
        if (s.photos.isNotEmpty()) b += Block.Para("В фото-журнале владельца: ${s.photos.size} фото (предоставляются по запросу).", muted = true)

        return Doc("История обслуживания: ${s.car.title}", "Сформировано ${Fmt.day(LocalDate.now())} · пробег ${Fmt.km(s.currentKm)}", footer(version), b)
    }

    /** Выписка как из банка: входящий/исходящий пробег, операции с нарастающим итогом, обороты по категориям. */
    fun statement(s: Snapshot, r: CostReport, version: String): Doc {
        val fromText = r.from?.let { Fmt.day(it) } ?: r.entries.firstOrNull()?.let { Fmt.day(it.date) } ?: Fmt.day(r.to)
        val b = mutableListOf<Block>()
        b += Block.Stats(
            listOf(
                Fmt.money2(r.total) to "итого расходов",
                (r.perKm?.let { Fmt.money2(it) + "/км" } ?: "—") to "цена километра",
                Fmt.km(r.km) to "пройдено за период",
            ),
        )
        b += Block.Table(
            listOf(Col("", 1f), Col("", 2.2f)),
            listOf(
                Row(listOf("Транспорт", s.car.title + if (s.car.plate.isNotBlank()) " · ${s.car.plate}" else "")),
                Row(listOf("Период", "$fromText — ${Fmt.day(r.to)}")),
                Row(listOf("Пробег на начало", kmOr(r.startKm))),
                Row(listOf("Пробег на конец", kmOr(r.endKm))),
                Row(listOf("Операций", r.entries.size.toString())),
            ),
        )

        b += Block.Heading("Операции")
        var run = 0.0
        b += Block.Table(
            listOf(
                Col("№", 0.35f, true), Col("Дата", 0.8f), Col("Пробег", 0.75f, true), Col("Операция", 2.2f),
                Col("Категория", 1f), Col("Сумма", 0.95f, true), Col("Итог", 1f, true),
            ),
            r.entries.mapIndexed { i, e ->
                run += e.total
                val cat = if ((e.type == LogType.SERVICE || e.type == LogType.WORK) && e.parts > 0 && e.amount > 0) LogType.label(e.type) + " + запчасти" else if (e.type == LogType.FUEL) s.kind.refuel else LogType.label(e.type)
                val name = entryTitle(s, e) + (if (e.partInfo.isNotBlank()) "\n${e.partInfo}" else "") + if (e.note.isNotBlank()) "\n${e.note}" else ""
                Row(listOf("${i + 1}", Fmt.day(e.date), e.km?.let { Fmt.num(it) } ?: "—", name, cat, "−${Fmt.money2(e.total)}", Fmt.money2(run)))
            },
            empty = "Операций за период нет",
        )

        b += Block.Heading("Обороты по категориям")
        b += Block.Table(
            listOf(Col("Категория", 1.4f), Col("Сумма", 1f, true), Col("Доля", 0.6f, true), Col("${Fmt.currency}/км", 0.8f, true)),
            CostCat.entries.map { c ->
                val v = r.byCat[c] ?: 0.0
                val share = if (r.total > 0) "${(v / r.total * 100).toInt()}%" else "—"
                Row(listOf(c.label(s.kind), Fmt.money2(v), share, r.perKm(c)?.let { Fmt.money2(it) } ?: "—"), if (v == 0.0) Tone.MUTED else Tone.NORMAL)
            } + Row(listOf("Итого", Fmt.money2(r.total), "100%", r.perKm?.let { Fmt.money2(it) } ?: "—"), Tone.STRONG),
        )

        if (r.byMonth.size > 1) {
            b += Block.Heading("По месяцам")
            b += Block.Table(
                listOf(Col("Месяц", 1.3f)) + CostCat.entries.map { Col(it.label(s.kind), 1f, true) } + Col("Итого", 1.1f, true),
                r.byMonth.map { (m, v) ->
                    Row(listOf(Fmt.month(m.atDay(1))) + CostCat.entries.map { Fmt.money(v[it] ?: 0.0) } + Fmt.money(v.values.sum()))
                },
            )
        }
        if (r.liters > 0) {
            val u = s.kind.unit
            b += Block.Heading(s.kind.energyLabel)
            b += Block.Stats(listOfNotNull(
                "${Fmt.dec(r.liters)} $u" to if (s.kind.energy == Energy.ELECTRIC) "заряжено" else "заправлено",
                r.pricePerLiter?.let { "${Fmt.money2(it)}/$u" to "средняя цена" },
                r.consumption?.let { "${Fmt.dec(it)} $u" to "средний расход на 100 км" },
            ))
        }
        return Doc(
            "Выписка расходов: ${s.car.title}",
            "${s.kind.label} · период $fromText — ${Fmt.day(r.to)}",
            footer(version), b,
        )
    }
}
