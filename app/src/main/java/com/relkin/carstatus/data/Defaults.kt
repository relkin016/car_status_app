package com.relkin.carstatus.data

import java.time.LocalDate
import java.time.MonthDay

object Defaults {
    /** Иконки пунктов, доступные в редакторе. */
    val icons = listOf(
        "oil", "air", "fuel", "brake", "drop", "spark", "belt", "battery", "wiper", "tire", "ac", "doc", "build", "light", "engine",
        "chain", "bolt", "plug", "cable",
    )
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
    /** Для каких видов транспорта. */
    val kinds: Set<VehicleKind> = CARS,
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

private val CARS = setOf(VehicleKind.PETROL, VehicleKind.DIESEL, VehicleKind.ELECTRIC)
private val ICE_CARS = setOf(VehicleKind.PETROL, VehicleKind.DIESEL)
private val BIKES = setOf(VehicleKind.BICYCLE, VehicleKind.EBIKE, VehicleKind.ESCOOTER)
private val E_LIGHT = setOf(VehicleKind.EBIKE, VehicleKind.ESCOOTER)

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
        Quest(
            "moto_winter", "Консервация на зиму", "🧊", MonthDay.of(10, 15), MonthDay.of(12, 15),
            listOf(
                QuestStep("wash", "Мойка и сушка", "Смыть грязь и соль, высушить, обработать хром и цепь"),
                QuestStep("tank", "Полный бак + стабилизатор", "Полный бак не ржавеет изнутри; стабилизатор топлива — на зиму"),
                QuestStep("oil", "Замена масла перед хранением", "Отработка с кислотами за зиму разъедает двигатель"),
                QuestStep("battery", "Аккумулятор домой", "Снять, хранить в тепле, подзаряжать умным зарядным раз в месяц"),
                QuestStep("chain", "Цепь", "Почистить и обильно смазать"),
                QuestStep("tires", "Шины", "Поднять давление или поставить на подставки, чтобы не было плоских мест"),
                QuestStep("cover", "Чехол и место", "Сухое помещение, дышащий чехол, заглушка в глушитель от грызунов"),
            ),
            setOf(VehicleKind.MOTO),
        ),
        Quest(
            "moto_spring", "Расконсервация весной", "🌱", MonthDay.of(3, 1), MonthDay.of(5, 1),
            listOf(
                QuestStep("battery", "Аккумулятор", "Зарядить, проверить напряжение ≥ 12,6 В, клеммы"),
                QuestStep("tires", "Шины", "Давление, трещины, дата выпуска, протектор"),
                QuestStep("brakes", "Тормоза", "Колодки, уровень жидкости, ход рычага"),
                QuestStep("chain", "Цепь", "Натяжение, смазка, износ звёзд"),
                QuestStep("fluids", "Жидкости", "Масло, антифриз, тормозная — уровни и подтёки"),
                QuestStep("lights", "Свет и сигналы", "Фары, стопы, поворотники, сигнал"),
                QuestStep("docs", "Документы", "Страховка, права категории A"),
                QuestStep("gear", "Экипировка", "Шлем без трещин, перчатки, защита"),
            ),
            setOf(VehicleKind.MOTO),
        ),
        Quest(
            "bike_winter", "Хранение на зиму", "🧊", MonthDay.of(10, 15), MonthDay.of(12, 15),
            listOf(
                QuestStep("wash", "Мойка и сушка", "Смыть грязь и соль, не мыть подшипники под напором"),
                QuestStep("chain", "Цепь", "Почистить и смазать — чтобы не заржавела"),
                QuestStep("battery", "Аккумулятор в тепло", "Хранить при +10…+20 °C, заряд 40–60%, подзаряжать раз в 1–2 месяца", ),
                QuestStep("tires", "Шины", "Подкачать или подвесить, чтобы не было плоских мест"),
                QuestStep("place", "Сухое место", "Без перепадов температур и влажности; не на балконе"),
            ),
            BIKES,
        ),
        Quest(
            "bike_spring", "Подготовка к сезону", "🌱", MonthDay.of(3, 1), MonthDay.of(5, 1),
            listOf(
                QuestStep("tires", "Шины и давление", "Давление по боковине, трещины, порезы; герметик в бескамерке"),
                QuestStep("brakes", "Тормоза", "Колодки, тросики или прокачка гидравлики"),
                QuestStep("chain", "Цепь и переключатели", "Смазка, растяжение цепи, настройка переключения"),
                QuestStep("bolts", "Болты и люфты", "Руль, вынос, колёса, складной механизм — протянуть моментом"),
                QuestStep("battery", "Аккумулятор", "Полный цикл зарядки, проверить запас хода"),
                QuestStep("lights", "Свет и звонок", "Фара, задний фонарь, катафоты"),
            ),
            BIKES,
        ),
    )

    fun byKey(k: String) = all.firstOrNull { it.key == k }
    fun of(kind: VehicleKind) = all.filter { kind in it.kinds }
    fun active(kind: VehicleKind, d: LocalDate = LocalDate.now()) = of(kind).filter { it.isActive(d) }
}

/** Внесезонная подсказка: когда выгодно покупать. [months] — месяцы окна, уведомление в первый день окна. */
data class Tip(val key: String, val title: String, val text: String, val months: Set<Int>, val kinds: Set<VehicleKind> = CARS) {
    fun isActive(d: LocalDate = LocalDate.now()) = d.monthValue in months
    /** С месяца [m] начинается окно (предыдущий месяц в окно не входит). */
    fun startsIn(m: Int) = m in months && (m + 10) % 12 + 1 !in months
}

object Tips {
    val all = listOf(
        Tip("winter_tires", "Зимние шины — дешевле весной", "С марта по июнь магазины распродают остатки зимней резины: скидки 15–30% и есть все размеры.", setOf(3, 4, 5, 6)),
        Tip("summer_tires", "Летние шины — дешевле осенью", "С сентября по декабрь летняя резина уходит в распродажу. Проверьте дату выпуска (DOT) — не старше 1–2 лет.", setOf(9, 10, 11, 12)),
        Tip("antifreeze", "Антифриз и незамерзайка — летом", "Летом на них нет спроса — цены ниже, а к осени магазины поднимают цены и разбирают ходовые объёмы.", setOf(6, 7, 8), ICE_CARS),
        Tip("battery", "Аккумулятор — летом", "Пик продаж АКБ — первые морозы. Летом проще выбрать свежую батарею (дата выпуска на корпусе) и без наценки.", setOf(6, 7, 8), CARS + VehicleKind.MOTO),
        Tip("ac", "Кондиционер — заправка зимой/ранней весной", "С января по март на заправку кондиционера нет очередей и бывают акции; в мае цены растут.", setOf(1, 2, 3)),
        Tip("wipers", "Зимние щётки — летом", "Комплект зимних щёток летом стоит заметно дешевле, чем в ноябре.", setOf(7, 8)),
        Tip("tire_service", "Шиномонтаж — записаться заранее", "За 1–2 недели до сезонного переобувания очереди минимальны. Запишитесь, пока нет ажиотажа.", setOf(10, 3)),
        Tip("moto_gear", "Мотоэкипировка — дешевле осенью", "С октября по декабрь магазины распродают шлемы, куртки и перчатки прошлого сезона.", setOf(10, 11, 12), setOf(VehicleKind.MOTO)),
        Tip("moto_service", "ТО мотоцикла — зимой без очередей", "Зимой мотосервисы свободны и часто дают скидки; весной запись на недели вперёд.", setOf(1, 2), setOf(VehicleKind.MOTO)),
        Tip("bike_buy", "Велосипеды и самокаты — дешевле осенью и зимой", "С октября по февраль распродают модели уходящего года: скидки до 20–40%.", setOf(10, 11, 12, 1, 2), BIKES),
        Tip("bike_service", "Велосервис — зимой без очередей", "Зимой мастерские свободны: ТО, прокачка тормозов и замена трансмиссии быстрее и дешевле.", setOf(1, 2), BIKES),
        Tip("e_battery", "Аккумулятор зимой — не на морозе", "Литий-ионную батарею самоката или велосипеда нельзя заряжать на холоде и хранить разряженной.", setOf(11, 12), E_LIGHT),
    )

    fun of(kind: VehicleKind) = all.filter { kind in it.kinds }
    fun active(kind: VehicleKind, d: LocalDate = LocalDate.now()) = of(kind).filter { it.isActive(d) }
    /** Подсказки хотя бы для одного из видов [kinds] — для общих уведомлений по всем гаражам. */
    fun active(kinds: Set<VehicleKind>, d: LocalDate = LocalDate.now()) = all.filter { it.isActive(d) && it.kinds.any { k -> k in kinds } }
}
