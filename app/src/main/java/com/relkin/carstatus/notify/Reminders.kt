package com.relkin.carstatus.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.relkin.carstatus.CarApp
import com.relkin.carstatus.MainActivity
import com.relkin.carstatus.R
import com.relkin.carstatus.data.Health
import com.relkin.carstatus.data.Quests
import com.relkin.carstatus.data.Tips
import com.relkin.carstatus.data.Tires
import com.relkin.carstatus.data.Trigger
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.tr
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object Mode {
    const val OFF = 0
    const val NOTIFY = 1
    const val ALARM = 2
    val labels: List<String> get() = listOf(tr("Выкл", "Вимк"), tr("Уведомление", "Сповіщення"), tr("Будильник", "Будильник"))
}

object Reminders {
    const val CH_NOTIFY = "reminders"
    const val CH_ALARM = "alarms"

    const val ACTION_DAILY = "com.relkin.carstatus.DAILY"
    const val ACTION_TEST = "com.relkin.carstatus.TEST"
    const val ACTION_DISMISS = "com.relkin.carstatus.DISMISS"

    const val EXTRA_MODE = "mode"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_NOTIF = "notif"
    const val EXTRA_VEHICLE = "vehicle"

    private const val CODE_DAILY = 1
    private const val CODE_TEST = 2

    // Внутри диапазона транспорта (см. nid)
    private const val N_OPEN = 1
    private const val N_ODO = 10
    private const val N_TIRE = 50
    private const val N_QUEST = 100
    private const val N_HP = 1000
    // Общие (ниже 10 000 — диапазон транспорта № 1 начинается с 10 000)
    private const val N_TEST = 11
    private const val N_TIP = 200
    private const val N_COST = 13

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_NOTIFY, tr("Напоминания", "Нагадування"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Обслуживание, пробег, сезонные квесты и советы", "Обслуговування, пробіг, сезонні квести й поради")
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, tr("Будильник", "Будильник"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = tr("Напоминания в режиме будильника: звук повторяется, пока не выключите", "Нагадування в режимі будильника: звук повторюється, доки не вимкнете")
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            }
        )
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun canFullScreen(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun at(date: LocalDate, minutes: Int): Long =
        date.atTime(minutes / 60, minutes % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun receiverIntent(ctx: Context, action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(ctx: Context, time: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
    }

    /** Ежедневная проверка в заданное время (если включён хоть один вид уведомлений). */
    fun reschedule(ctx: Context) {
        val p = (ctx.applicationContext as CarApp).prefs
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = receiverIntent(ctx, ACTION_DAILY, CODE_DAILY)
        am.cancel(pi)
        if (listOf(p.hpMode, p.odoMode, p.questMode, p.tipMode, p.tireMode, p.costMode).all { it.value == Mode.OFF } && p.backupDays.value == 0) return
        var t = at(LocalDate.now(), p.checkTime.value)
        if (t <= System.currentTimeMillis()) t = at(LocalDate.now().plusDays(1), p.checkTime.value)
        schedule(ctx, t, pi)
    }

    /** Диапазон id уведомлений транспорта: у каждого свой, чтобы уведомления разных транспортов не затирали друг друга. */
    private fun nid(vehicle: Long, n: Int) = vehicle.toInt() * 10_000 + n

    /** Ключ «уже показано»: у первого транспорта без префикса (совместимость с 0.1). */
    private fun vkey(vehicle: Long, key: String) = if (vehicle == 1L) key else "v$vehicle:$key"
    private val HP_KEY = Regex("^(v\\d+:)?hp:")

    /**
     * Все проверки по всем транспортам всех гаражей, где включены уведомления (не только текущего):
     * «здоровье» расходников, давно не вводили пробег, сезонные квесты; внесезонные покупки — общие.
     */
    fun check(ctx: Context) {
        val app = ctx.applicationContext as CarApp
        val p = app.prefs
        val g = app.garage
        val today = LocalDate.now()
        val notified = p.notified.toMutableSet()
        val shown = HashSet<String>()
        val vehicles = g.all.filter { g.s.garage(it.garageId)?.notify != false }
            .map { it.id to g.repo(it.id).s }.filter { it.second.setupDone }
        // имя транспорта в заголовке — когда их несколько
        val named = g.s.vehicles.size > 1

        for ((vid, s) in vehicles) {
            val who = if (named) s.car.label + ": " else ""
            val open = openIntent(ctx, nid(vid, N_OPEN), vid)

            if (p.hpMode.value != Mode.OFF) {
                val h = Health.of(s, s.fallbackKmPerDay, today)
                for (ih in h.items) {
                    val pct = ih.percent ?: continue
                    if (pct > p.hpThreshold.value) continue
                    val it = ih.item
                    val stage = if (ih.overdue) "over" else "low"
                    val key = vkey(vid, "hp:${it.id}:${it.lastKm}:${it.lastDate}:$stage")
                    shown += key
                    if (key in notified) continue
                    val left = buildList {
                        ih.kmLeft?.let { add(if (it > 0) tr("осталось ", "залишилось ") + Fmt.km(it) else tr("перепробег ", "перепробіг ") + Fmt.km(-it)) }
                        ih.daysLeft?.let { add(if (it > 0) Fmt.days(it) else tr("просрочено на ", "прострочено на ") + Fmt.days(-it)) }
                    }.joinToString(" · ")
                    val due = ih.due?.let { d -> tr(" · срок ~", " · строк ~") + Fmt.day(d) + " (" + (if (ih.trigger == Trigger.KM) tr("по пробегу", "за пробігом") else tr("по времени", "за часом")) + ")" } ?: ""
                    show(
                        ctx, nid(vid, N_HP + it.id.toInt()),
                        who + (if (ih.overdue) tr("Просрочено: ", "Прострочено: ") else "HP $pct%: ") + it.name,
                        left + due, p.hpMode.value, open,
                    )
                }
            }

            if (p.odoMode.value != Mode.OFF) {
                val last = s.lastOdoDate ?: 0
                val n = p.odoDays.value
                if (today.toEpochDay() - last >= n && today.toEpochDay() - p.lastOdoRemind(vid) >= n) {
                    p.setLastOdoRemind(vid, today.toEpochDay())
                    val id = nid(vid, N_ODO)
                    val odo = PendingIntent.getActivity(
                        ctx, id,
                        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ODO).putExtra(EXTRA_NOTIF, id).putExtra(EXTRA_VEHICLE, vid),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    show(
                        ctx, id, who + tr("Какой сейчас пробег?", "Який зараз пробіг?"),
                        tr("Последнее показание: ", "Останній показник: ") + Fmt.km(s.currentKm) + (s.lastOdoDate?.let { tr(" от ", " від ") + Fmt.day(it) } ?: "") +
                            tr(". Обновите — прогноз ТО станет точнее.", ". Оновіть — прогноз ТО стане точнішим."),
                        p.odoMode.value, odo, listOf(tr("Ввести пробег", "Ввести пробіг") to odo),
                    )
                }
            }

            if (p.questMode.value != Mode.OFF) {
                Quests.active(s.kind, today).forEachIndexed { i, q ->
                    val season = q.season(today)
                    val done = q.steps.count { st -> q.stepKey(season, st) in s.questDone }
                    if (done == q.steps.size) return@forEachIndexed
                    val end = q.end.atYear(if (q.start.isAfter(q.end)) season + 1 else season)
                    val late = ChronoUnit.DAYS.between(today, end) <= 14
                    val key = vkey(vid, "quest:${q.key}:$season:" + if (late) "late" else "start")
                    if (key in notified) return@forEachIndexed
                    notified += key
                    show(
                        ctx, nid(vid, N_QUEST + i), "${q.emoji} ${who}Квест: ${q.title}",
                        (if (late) tr("До конца сезона ", "До кінця сезону ") + Fmt.days(ChronoUnit.DAYS.between(today, end)) + ". " else tr("Сезон начался! ", "Сезон почався! ")) +
                            tr("Выполнено $done из ${q.steps.size}. Следующий шаг: ", "Виконано $done з ${q.steps.size}. Наступний крок: ") +
                            (q.steps.firstOrNull { st -> q.stepKey(season, st) !in s.questDone }?.title ?: "—"),
                        p.questMode.value, open,
                    )
                }
            }

            if (p.tireMode.value != Mode.OFF && Tires.supported(s.kind)) {
                // сезон — по стоящему комплекту, протектор и возраст — по всем, кроме списанных
                val warns = listOfNotNull(Tires.seasonWarn(s, today)?.let { null to it }) +
                    s.tireSets.flatMap { set -> Tires.warnings(set, today).map { set to it } }
                warns.forEachIndexed { i, (set, w) ->
                    val key = vkey(vid, "tire:${w.key}")
                    if (key in notified || i >= 50) return@forEachIndexed
                    notified += key
                    show(
                        ctx, nid(vid, N_TIRE + i), "🛞 $who" + (set?.title?.let { tr("Шины «$it»", "Шини «$it»") } ?: tr("Шины", "Шини")),
                        w.text.replaceFirstChar { it.uppercase() }, p.tireMode.value, open,
                    )
                }
            }
        }

        if (p.tipMode.value != Mode.OFF) {
            Tips.active(vehicles.map { it.second.kind }.toSet(), today).forEachIndexed { i, t ->
                // начало текущего окна: идём назад, пока месяц входит в окно
                var start = today.withDayOfMonth(1)
                repeat(12) { if (!t.startsIn(start.monthValue)) start = start.minusMonths(1) }
                val key = "tip:${t.key}:${start.year}-${start.monthValue}"
                if (key in notified) return@forEachIndexed
                notified += key
                show(ctx, N_TIP + i, "💡 ${t.title}", t.text, p.tipMode.value)
            }
        }

        if (p.costMode.value != Mode.OFF && vehicles.isNotEmpty() && p.lastCostRemind.value != today.toEpochDay()) {
            val (due, what) = when (p.costPeriod.value) {
                0 -> true to tr("сегодня", "сьогодні")
                1 -> (today.dayOfWeek == DayOfWeek.SUNDAY) to tr("за неделю", "за тиждень")
                else -> (today.plusDays(1).dayOfMonth == 1) to tr("за месяц", "за місяць")
            }
            if (due) {
                p.lastCostRemind.set(today.toEpochDay())
                val add = PendingIntent.getActivity(
                    ctx, N_COST,
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ADD).putExtra(EXTRA_NOTIF, N_COST),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                show(
                    ctx, N_COST, "🧾 " + tr("Внесите расходы", "Внесіть витрати"),
                    tr("Запишите заправки, ремонт и прочие траты $what — цена километра будет точной.", "Запишіть заправки, ремонт та інші витрати $what — ціна кілометра буде точною."),
                    p.costMode.value, add, listOf(tr("Добавить", "Додати") to add),
                )
            }
        }

        // Ключи «здоровья», которые больше не актуальны (пункт обслужен), убираем — при следующем износе напомним снова
        p.notified = notified.filter { !HP_KEY.containsMatchIn(it) || it in shown }.toSet() + shown
    }

    /** Открыть приложение на транспорте [vehicle]. */
    private fun openIntent(ctx: Context, code: Int, vehicle: Long): PendingIntent = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_VEHICLE, vehicle),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun test(ctx: Context, mode: Int) {
        val pi = receiverIntent(ctx, ACTION_TEST, CODE_TEST) { putExtra(EXTRA_MODE, mode) }
        schedule(ctx, System.currentTimeMillis() + 5000, pi)
    }

    fun showTest(ctx: Context, mode: Int) =
        show(ctx, N_TEST, tr("Проверка уведомлений", "Перевірка сповіщень"), tr("Так будет выглядеть напоминание о ТО.", "Так виглядатиме нагадування про ТО."), mode)

    fun show(
        ctx: Context, notifId: Int, title: String, text: String, mode: Int,
        open: PendingIntent? = null, actions: List<Pair<String, PendingIntent>> = emptyList(),
    ) {
        if (mode == Mode.OFF || !canNotify(ctx)) return
        val content = open ?: PendingIntent.getActivity(
            ctx, notifId, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, if (mode == Mode.ALARM) CH_ALARM else CH_NOTIFY)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (mode == Mode.ALARM) {
            val full = PendingIntent.getActivity(
                ctx, notifId,
                Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_NOTIF, notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val dismiss = receiverIntent(ctx, ACTION_DISMISS, 100_000 + notifId) { putExtra(EXTRA_NOTIF, notifId) }
            b.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(full, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(0, tr("Выключить", "Вимкнути"), dismiss)
        } else {
            b.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }
        actions.forEach { (label, pi) -> b.addAction(0, label, pi) }
        val n = b.build()
        if (mode == Mode.ALARM) n.flags = n.flags or Notification.FLAG_INSISTENT
        try {
            NotificationManagerCompat.from(ctx).notify(notifId, n)
        } catch (_: SecurityException) {
        }
    }

    fun dismiss(ctx: Context, notifId: Int) = NotificationManagerCompat.from(ctx).cancel(notifId)
}
