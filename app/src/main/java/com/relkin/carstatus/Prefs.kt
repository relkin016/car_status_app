package com.relkin.carstatus

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** Вызывается при изменении настроек, влияющих на расписание уведомлений. */
    var onChanged: (() -> Unit)? = null

    inner class IntPref(val key: String, def: Int, private val reschedule: Boolean = false) {
        private val flow = MutableStateFlow(sp.getInt(key, def))
        val state: StateFlow<Int> get() = flow
        val value: Int get() = flow.value
        fun set(v: Int) {
            sp.edit().putInt(key, v).apply()
            flow.value = v
            if (reschedule) onChanged?.invoke()
        }
    }

    inner class StrPref(val key: String, def: String) {
        private val flow = MutableStateFlow(sp.getString(key, def) ?: def)
        val state: StateFlow<String> get() = flow
        val value: String get() = flow.value
        fun set(v: String) {
            sp.edit().putString(key, v).apply()
            flow.value = v
        }
    }

    /** 0 — как в системе, 1 — светлая, 2 — тёмная. */
    val theme = IntPref("theme", 0)
    val dynamicColor = IntPref("dynamic_color", 0)
    /** Переключение вкладок свайпом. */
    val swipeNav = IntPref("swipe_nav", 1)
    val currency = StrPref("currency", "грн")

    /** Режимы уведомлений: 0 — выкл, 1 — уведомление, 2 — будильник. */
    val hpMode = IntPref("hp_mode", 1, true)
    /** Порог «здоровья» пункта ТО, %, при котором приходит уведомление. */
    val hpThreshold = IntPref("hp_threshold", 20, true)
    val odoMode = IntPref("odo_mode", 1, true)
    /** Напомнить ввести пробег, если его не обновляли столько дней. */
    val odoDays = IntPref("odo_days", 14, true)
    val questMode = IntPref("quest_mode", 1, true)
    val tipMode = IntPref("tip_mode", 1, true)
    /** Время ежедневной проверки, минуты от начала суток. */
    val checkTime = IntPref("check_time", 10 * 60, true)

    /** Средний пробег в месяц, пока по истории его не посчитать. */
    val kmPerMonth = IntPref("km_per_month", 1200)

    /** Включать стоимость в историю для покупателя. */
    val reportCosts = IntPref("report_costs", 0)

    /** OBD2: адрес адаптера и чтение пробега при открытии вкладки. */
    val obdAddress = StrPref("obd_address", "")
    val obdAutoOdo = IntPref("obd_auto_odo", 1)
    /** Базовая точка для пробега через PID 0x31 (дистанция после сброса ошибок): "км_одометра:дистанция". */
    val obdBase = StrPref("obd_base", "")

    /** Что уже показано, чтобы не повторяться: ключи уведомлений. */
    var notified: Set<String>
        get() = sp.getStringSet("notified", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("notified", v).apply()

    var lastOdoRemind: Long
        get() = sp.getLong("last_odo_remind", 0)
        set(v) = sp.edit().putLong("last_odo_remind", v).apply()
}
