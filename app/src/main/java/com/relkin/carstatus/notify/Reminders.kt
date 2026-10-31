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
import com.relkin.carstatus.data.Trigger
import com.relkin.carstatus.util.Fmt
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object Mode {
    const val OFF = 0
    const val NOTIFY = 1
    const val ALARM = 2
    val labels = listOf("Выкл", "Уведомление", "Будильник")
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
    private const val N_QUEST = 100
    private const val N_HP = 1000
    // Общие (ниже 10 000 — диапазон транспорта № 1 начинается с 10 000)
    private const val N_TEST = 11
    private const val N_TIP = 200

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_NOTIFY, "Напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Обслуживание, пробег, сезонные квесты и советы"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Будильник", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминания в режиме будильника: звук повторяется, пока не выключите"
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
        if (listOf(p.hpMode, p.odoMode, p.questMode, p.tipMode).all { it.value == Mode.OFF }) return
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
                        ih.kmLeft?.let { add(if (it > 0) "осталось ${Fmt.km(it)}" else "перепробег ${Fmt.km(-it)}") }
                        ih.daysLeft?.let { add(if (it > 0) "${Fmt.days(it)}" else "просрочено на ${Fmt.days(-it)}") }
                    }.joinToString(" · ")
                    val due = ih.due?.let { d -> " · срок ~${Fmt.day(d)} (${if (ih.trigger == Trigger.KM) "по пробегу" else "по времени"})" } ?: ""
                    show(
                        ctx, nid(vid, N_HP + it.id.toInt()),
                        who + (if (ih.overdue) "Просрочено: " else "HP $pct%: ") + it.name,
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
                        ctx, id, who + "Какой сейчас пробег?",
                        "Последнее показание: ${Fmt.km(s.currentKm)}" + (s.lastOdoDate?.let { " от ${Fmt.day(it)}" } ?: "") +
                            ". Обновите — прогноз ТО станет точнее.",
                        p.odoMode.value, odo, listOf("Ввести пробег" to odo),
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
                        (if (late) "До конца сезона ${Fmt.days(ChronoUnit.DAYS.between(today, end))}. " else "Сезон начался! ") +
                            "Выполнено $done из ${q.steps.size}. Следующий шаг: " +
                            (q.steps.firstOrNull { st -> q.stepKey(season, st) !in s.questDone }?.title ?: "—"),
                        p.questMode.value, open,
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
        show(ctx, N_TEST, "Проверка уведомлений", "Так будет выглядеть напоминание о ТО.", mode)

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
                .addAction(0, "Выключить", dismiss)
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
