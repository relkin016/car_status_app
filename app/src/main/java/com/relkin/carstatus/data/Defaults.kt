package com.relkin.carstatus.data

import java.time.LocalDate
import java.time.MonthDay

/** Базовый список ТО «из коробки»: (название, иконка, км, дни). Лимит 0 — не используется. */
object Defaults {
    val items = listOf(
        Item(name = "Моторное масло и фильтр", icon = "oil", kmInterval = 10_000, dayInterval = 365),
        Item(name = "Воздушный фильтр", icon = "air", kmInterval = 20_000, dayInterval = 730),
        Item(name = "Салонный фильтр", icon = "air", kmInterval = 15_000, dayInterval = 365),
        Item(name = "Топливный фильтр", icon = "fuel", kmInterval = 40_000, dayInterval = 1460),
        Item(name = "Тормозные колодки передние", icon = "brake", kmInterval = 30_000, dayInterval = 1095),
        Item(name = "Тормозные колодки задние", icon = "brake", kmInterval = 50_000, dayInterval = 1460),
        Item(name = "Тормозные диски", icon = "brake", kmInterval = 80_000, dayInterval = 2190),
        Item(name = "Тормозная жидкость", icon = "drop", kmInterval = 40_000, dayInterval = 730),
        Item(name = "Антифриз", icon = "drop", kmInterval = 60_000, dayInterval = 1095),
        Item(name = "Свечи зажигания", icon = "spark", kmInterval = 30_000, dayInterval = 1095),
        Item(name = "Масло КПП", icon = "oil", kmInterval = 60_000, dayInterval = 1825),
        Item(name = "Ремень / цепь ГРМ", icon = "belt", kmInterval = 60_000, dayInterval = 1825),
        Item(name = "Приводной ремень", icon = "belt", kmInterval = 60_000, dayInterval = 1460),
        Item(name = "Аккумулятор", icon = "battery", kmInterval = 0, dayInterval = 1825),
        Item(name = "Щётки стеклоочистителя", icon = "wiper", kmInterval = 0, dayInterval = 365),
        Item(name = "Шины (износ)", icon = "tire", kmInterval = 50_000, dayInterval = 2190),
        Item(name = "Развал-схождение", icon = "tire", kmInterval = 15_000, dayInterval = 365),
        Item(name = "Заправка кондиционера", icon = "ac", kmInterval = 0, dayInterval = 730),
        Item(name = "Страховка (ОСЦПВ)", icon = "doc", kmInterval = 0, dayInterval = 365),
    ).mapIndexed { i, it -> it.copy(position = i) }

    /** Иконки пунктов, доступные в редакторе. */
    val icons = listOf("oil", "air", "fuel", "brake", "drop", "spark", "belt", "battery", "wiper", "tire", "ac", "doc", "build", "light", "engine")
}

data class QuestStep(val key: String, val title: String, val hint: String = "")

/** Сезонный квест: окно [start]..[end] в году; прогресс хранится по году начала окна. */
data class Quest(
    val key: String,
    val title: String,
    val emoji: String,
    val start: MonthDay,
    val end: MonthDay,
    val steps: List<QuestStep>,
) {
    fun isActive(d: LocalDate = LocalDate.now()): Boolean {
        val md = MonthDay.from(d)
        return if (!start.isAfter(end)) !md.isBefore(start) && !md.isAfter(end) else !md.isBefore(start) || !md.isAfter(end)
    }

    /** Год сезона: окно, переходящее через Новый год, относится к году своего начала. */
    fun season(d: LocalDate = LocalDate.now()): Int =
        if (start.isAfter(end) && MonthDay.from(d).isBefore(start)) d.year - 1 else d.year

    fun startDate(year: Int): LocalDate = start.atYear(year)
    fun stepKey(season: Int, step: QuestStep) = "$key:$season:${step.key}"
}

object Quests {
    val all = listOf(
        Quest(
            "winter", "Подготовка к зиме", "❄️", MonthDay.of(10, 1), MonthDay.of(12, 15),
            listOf(
                QuestStep("tires", "Зимние шины", "Протектор ≥ 4 мм, давление по табличке на стойке; переобуться при +7 °C и ниже"),
                QuestStep("battery", "Аккумулятор", "Напряжение без нагрузки ≥ 12,5 В, клеммы без окислов; АКБ старше 5 лет — под замену"),
                QuestStep("antifreeze", "Антифриз", "Уровень между MIN/MAX, защита до −30 °C (проверить ареометром/рефрактометром)"),
                QuestStep("washer", "Незамерзающая омывайка", "Слить летнюю жидкость, залить до −20…−30 °C"),
                QuestStep("wipers", "Щётки стеклоочистителя", "Без полос; зимние или бескаркасные"),
                QuestStep("heater", "Печка и обогревы", "Обогрев заднего стекла, зеркал, сидений"),
                QuestStep("oil", "Масло по сезону", "Если подходит замена — вязкость под холодный пуск (0W/5W)"),
                QuestStep("seals", "Уплотнители и замки", "Силиконовая смазка на резинки, смазка в личинки замков"),
                QuestStep("kit", "Зимний набор", "Щётка-скребок, трос, провода для прикуривания, лопатка"),
                QuestStep("lights", "Свет", "Все лампы, регулировка фар — темнеет рано"),
            ),
        ),
        Quest(
            "summer", "Подготовка к лету", "☀️", MonthDay.of(3, 15), MonthDay.of(5, 15),
            listOf(
                QuestStep("tires", "Летние шины", "Переобуться при стабильных +7 °C; проверить износ и дату выпуска"),
                QuestStep("wash", "Мойка днища", "Смыть реагенты, осмотреть кузов на сколы"),
                QuestStep("brakes", "Тормоза после зимы", "Колодки, диски, направляющие суппортов"),
                QuestStep("suspension", "Подвеска", "После ям: шаровые, рулевые наконечники, сайлентблоки"),
                QuestStep("alignment", "Развал-схождение", "Особенно если руль стоит криво"),
                QuestStep("ac", "Кондиционер", "Проверить работу, заменить салонный фильтр, антибактериальная обработка"),
                QuestStep("washer", "Летняя омывайка", "Хорошо смывает насекомых"),
                QuestStep("wipers", "Щётки", "После зимы обычно под замену"),
            ),
        ),
    )

    fun byKey(k: String) = all.firstOrNull { it.key == k }
    fun active(d: LocalDate = LocalDate.now()) = all.filter { it.isActive(d) }
}

/** Внесезонная подсказка: когда выгодно покупать. [months] — месяцы окна, уведомление в первый день окна. */
data class Tip(val key: String, val title: String, val text: String, val months: Set<Int>) {
    fun isActive(d: LocalDate = LocalDate.now()) = d.monthValue in months
    /** С месяца [m] начинается окно (предыдущий месяц в окно не входит). */
    fun startsIn(m: Int) = m in months && (m + 10) % 12 + 1 !in months
}

object Tips {
    val all = listOf(
        Tip("winter_tires", "Зимние шины — дешевле весной", "С марта по июнь магазины распродают остатки зимней резины: скидки 15–30% и есть все размеры.", setOf(3, 4, 5, 6)),
        Tip("summer_tires", "Летние шины — дешевле осенью", "С сентября по декабрь летняя резина уходит в распродажу. Проверьте дату выпуска (DOT) — не старше 1–2 лет.", setOf(9, 10, 11, 12)),
        Tip("antifreeze", "Антифриз и незамерзайка — летом", "Летом на них нет спроса — цены ниже, а к осени магазины поднимают цены и разбирают ходовые объёмы.", setOf(6, 7, 8)),
        Tip("battery", "Аккумулятор — летом", "Пик продаж АКБ — первые морозы. Летом проще выбрать свежую батарею (дата выпуска на корпусе) и без наценки.", setOf(6, 7, 8)),
        Tip("ac", "Кондиционер — заправка зимой/ранней весной", "С января по март на заправку кондиционера нет очередей и бывают акции; в мае цены растут.", setOf(1, 2, 3)),
        Tip("wipers", "Зимние щётки — летом", "Комплект зимних щёток летом стоит заметно дешевле, чем в ноябре.", setOf(7, 8)),
        Tip("tire_service", "Шиномонтаж — записаться заранее", "За 1–2 недели до сезонного переобувания очереди минимальны. Запишитесь, пока нет ажиотажа.", setOf(10, 3)),
    )

    fun active(d: LocalDate = LocalDate.now()) = all.filter { it.isActive(d) }
}
