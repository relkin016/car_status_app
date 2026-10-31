package com.relkin.carstatus.data

import com.relkin.carstatus.util.Fmt
import java.time.LocalDate

/** Markdown-документы: история обслуживания для покупателя и выписка расходов «как в банке». */
object Reports {
    private fun cell(s: String) = s.replace("|", "\\|").replace("\n", " ").trim().ifEmpty { "—" }
    private fun kmOr(km: Long?) = km?.let { Fmt.km(it) } ?: "—"

    private fun StringBuilder.carHeader(s: Snapshot) {
        val c = s.car
        appendLine("| Параметр | Значение |")
        appendLine("|---|---|")
        listOf(
            "Автомобиль" to listOf(c.make, c.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { c.title },
            "Год выпуска" to c.year, "VIN" to c.vin, "Госномер" to c.plate, "Двигатель" to c.engine, "Топливо" to c.fuel,
            "Текущий пробег" to Fmt.km(s.currentKm),
        ).filter { it.second.isNotBlank() }.forEach { (k, v) -> appendLine("| $k | ${cell(v)} |") }
        appendLine()
    }

    fun entryTitle(s: Snapshot, e: LogEntry): String = e.title.ifBlank {
        when (e.type) {
            LogType.FUEL -> "Заправка" + if (e.liters > 0) " ${Fmt.dec(e.liters)} л" else ""
            LogType.SERVICE -> s.item(e.itemId)?.name ?: "Обслуживание"
            LogType.PART -> "Запчасти"
            else -> "Расход"
        }
    }

    fun buyerHistory(s: Snapshot, h: CarHealth, version: String, withCosts: Boolean): String = buildString {
        val today = LocalDate.now()
        appendLine("# История обслуживания: ${s.car.title}")
        appendLine()
        appendLine("_Сформировано ${Fmt.day(today)} в приложении «Авто·Статус» $version. Данные вносились владельцем._")
        appendLine()
        appendLine("## Автомобиль")
        appendLine()
        carHeader(s)

        val services = s.log.filter { it.type == LogType.SERVICE || it.type == LogType.PART }.sortedWith(compareBy({ it.date }, { it.km ?: 0 }))
        val first = s.readings.firstOrNull()
        appendLine("## Кратко")
        appendLine()
        appendLine("- Записей об обслуживании: **${services.size}**")
        if (first != null) appendLine("- История ведётся с ${Fmt.day(first.first)} (пробег ${Fmt.km(first.second)})")
        h.overall?.let { appendLine("- Общее состояние по регламенту: **${(it * 100).toInt()}%**") }
        val overdue = h.items.filter { it.overdue }
        if (overdue.isNotEmpty()) appendLine("- Просрочено по регламенту: ${overdue.joinToString { it.item.name }}")
        val open = s.breakdowns.filter { !it.done }
        appendLine("- Открытых неисправностей: **${open.size}**")
        if (withCosts) {
            val r = Costs.report(s, null)
            appendLine("- Вложено в обслуживание и запчасти: **${Fmt.money((r.byCat[CostCat.SERVICE] ?: 0.0) + (r.byCat[CostCat.PARTS] ?: 0.0))}**")
            r.consumption?.let { appendLine("- Средний расход топлива: **${Fmt.dec(it)} л/100 км**") }
        }
        appendLine()

        appendLine("## Состояние по регламенту")
        appendLine()
        appendLine("| Узел | Последнее обслуживание | Интервал | Осталось | Состояние |")
        appendLine("|---|---|---|---|---|")
        for (ih in h.items.sortedBy { it.item.position }) {
            val it = ih.item
            val last = listOfNotNull(it.lastDate?.let { d -> Fmt.day(d) }, it.lastKm?.let { k -> Fmt.km(k) }).joinToString(", ")
            val interval = listOfNotNull(
                it.kmInterval.takeIf { v -> v > 0 }?.let { v -> Fmt.km(v) },
                it.dayInterval.takeIf { v -> v > 0 }?.let { v -> Fmt.days(v) },
            ).joinToString(" / ")
            val left = listOfNotNull(ih.kmLeft?.let { v -> Fmt.km(v) }, ih.daysLeft?.let { v -> Fmt.days(v) }).joinToString(" / ")
            val state = ih.percent?.let { p -> if (ih.overdue) "просрочено" else "$p%" } ?: "нет данных"
            appendLine("| ${cell(it.name)} | ${cell(last)} | ${cell(interval)} | ${cell(left)} | $state |")
        }
        appendLine()

        appendLine("## Журнал обслуживания")
        appendLine()
        if (services.isEmpty()) appendLine("_Записей нет._") else {
            appendLine(if (withCosts) "| Дата | Пробег | Работы | Стоимость | Примечание |" else "| Дата | Пробег | Работы | Примечание |")
            appendLine(if (withCosts) "|---|---|---|---|---|" else "|---|---|---|---|")
            for (e in services) {
                val cost = if (withCosts) " ${Fmt.money(e.total)} |" else ""
                appendLine("| ${Fmt.day(e.date)} | ${kmOr(e.km)} | ${cell(entryTitle(s, e))} |$cost ${cell(e.note)} |")
            }
        }
        appendLine()

        if (s.incidents.isNotEmpty()) {
            appendLine("## Дневник происшествий")
            appendLine()
            appendLine("| Дата | Пробег | Событие | Описание |")
            appendLine("|---|---|---|---|")
            for (i in s.incidents.sortedBy { it.time }) {
                val sev = when (i.severity) { 2 -> "‼️ "; 1 -> "⚠️ "; else -> "" }
                appendLine("| ${Fmt.dateTime(i.time)} | ${kmOr(i.km)} | $sev${cell(i.title)} | ${cell(i.note)} |")
            }
            appendLine()
        }

        if (s.breakdowns.isNotEmpty()) {
            appendLine("## Неисправности")
            appendLine()
            for (b in s.breakdowns.sortedWith(compareBy({ it.done }, { -it.priority }))) {
                val mark = if (b.done) "[x]" else "[ ]"
                val dates = "выявлено ${Fmt.day(b.createdDate)}" + (b.createdKm?.let { ", ${Fmt.km(it)}" } ?: "") +
                    (b.doneDate?.let { "; устранено ${Fmt.day(it)}" } ?: "")
                appendLine("- $mark **${b.title}** — $dates" + if (b.note.isNotBlank()) ". ${b.note.replace("\n", " ")}" else "")
            }
            appendLine()
        }

        val odo = s.readings
        if (odo.size >= 2) {
            appendLine("## Показания одометра")
            appendLine()
            appendLine("Пробег только рос: " + if (odo.zipWithNext().all { (a, b) -> b.second >= a.second }) "**да**" else "**нет — есть показания меньше предыдущих**")
            appendLine()
            appendLine("| Дата | Пробег |")
            appendLine("|---|---|")
            // По одному показанию на месяц — чтобы таблица не разрасталась
            odo.groupBy { LocalDate.ofEpochDay(it.first).withDayOfMonth(1) }.values.map { it.last() }
                .forEach { appendLine("| ${Fmt.day(it.first)} | ${Fmt.km(it.second)} |") }
            appendLine()
        }
        if (s.photos.isNotEmpty()) {
            appendLine("_В фото-журнале владельца: ${s.photos.size} фото (предоставляются по запросу)._")
            appendLine()
        }
    }

    /** Выписка как из банка: входящий/исходящий пробег, операции с нарастающим итогом, обороты по категориям. */
    fun statement(s: Snapshot, r: CostReport): String = buildString {
        val fromText = r.from?.let { Fmt.day(it) } ?: r.entries.firstOrNull()?.let { Fmt.day(it.date) } ?: Fmt.day(r.to)
        appendLine("# Выписка расходов на автомобиль")
        appendLine()
        appendLine("**${s.car.title}**" + if (s.car.plate.isNotBlank()) " · ${s.car.plate}" else "")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        appendLine("| Период | $fromText — ${Fmt.day(r.to)} |")
        appendLine("| Дата формирования | ${Fmt.day(LocalDate.now())} |")
        appendLine("| Пробег на начало | ${kmOr(r.startKm)} |")
        appendLine("| Пробег на конец | ${kmOr(r.endKm)} |")
        appendLine("| Пройдено | ${Fmt.km(r.km)} |")
        appendLine("| Операций | ${r.entries.size} |")
        appendLine("| **Итого расходов** | **${Fmt.money2(r.total)}** |")
        appendLine("| **Цена километра** | **${r.perKm?.let { Fmt.money2(it) + "/км" } ?: "—"}** |")
        appendLine()

        appendLine("## Операции")
        appendLine()
        appendLine("| № | Дата | Пробег | Операция | Категория | Сумма | Нарастающий итог |")
        appendLine("|---:|---|---:|---|---|---:|---:|")
        var run = 0.0
        r.entries.forEachIndexed { i, e ->
            run += e.total
            val cat = if (e.type == LogType.SERVICE && e.parts > 0 && e.amount > 0) "ТО + запчасти" else LogType.label(e.type)
            val name = entryTitle(s, e) + if (e.note.isNotBlank()) " (${e.note})" else ""
            appendLine("| ${i + 1} | ${Fmt.day(e.date)} | ${e.km?.let { Fmt.num(it) } ?: "—"} | ${cell(name)} | $cat | −${Fmt.money2(e.total)} | ${Fmt.money2(run)} |")
        }
        if (r.entries.isEmpty()) appendLine("| | | | _операций нет_ | | | |")
        appendLine()

        appendLine("## Обороты по категориям")
        appendLine()
        appendLine("| Категория | Сумма | Доля | грн/км |".replace("грн", Fmt.currency))
        appendLine("|---|---:|---:|---:|")
        for (c in CostCat.entries) {
            val v = r.byCat[c] ?: 0.0
            val share = if (r.total > 0) "${(v / r.total * 100).toInt()}%" else "—"
            appendLine("| ${c.label} | ${Fmt.money2(v)} | $share | ${r.perKm(c)?.let { Fmt.money2(it) } ?: "—"} |")
        }
        appendLine("| **Итого** | **${Fmt.money2(r.total)}** | 100% | **${r.perKm?.let { Fmt.money2(it) } ?: "—"}** |")
        appendLine()

        if (r.byMonth.size > 1) {
            appendLine("## По месяцам")
            appendLine()
            appendLine("| Месяц | Топливо | ТО | Запчасти | Прочее | Итого |")
            appendLine("|---|---:|---:|---:|---:|---:|")
            for ((m, v) in r.byMonth) {
                val t = v.values.sum()
                appendLine("| ${Fmt.month(m.atDay(1))} | ${CostCat.entries.joinToString(" | ") { Fmt.money(v[it] ?: 0.0) }} | ${Fmt.money(t)} |")
            }
            appendLine()
        }
        if (r.liters > 0) {
            appendLine("## Топливо")
            appendLine()
            appendLine("- Заправлено: ${Fmt.dec(r.liters)} л")
            r.pricePerLiter?.let { appendLine("- Средняя цена: ${Fmt.money2(it)}/л") }
            r.consumption?.let { appendLine("- Средний расход: ${Fmt.dec(it)} л/100 км") }
            appendLine()
        }
    }
}
