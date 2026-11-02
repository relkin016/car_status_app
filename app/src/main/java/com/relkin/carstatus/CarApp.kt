package com.relkin.carstatus

import android.app.Application
import com.relkin.carstatus.data.AutoBackup
import com.relkin.carstatus.data.Garage
import com.relkin.carstatus.data.Repo
import com.relkin.carstatus.notify.Reminders
import com.relkin.carstatus.obd.Obd
import com.relkin.carstatus.util.Fmt
import com.relkin.carstatus.util.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.thread

class CarApp : Application() {
    lateinit var prefs: Prefs
    lateinit var garage: Garage
    lateinit var obd: Obd

    /** База текущего транспорта (выбранного в гараже). */
    val repo: Repo get() = garage.current

    /** Экран, который нужно открыть по уведомлению (MainActivity.OPEN_*). */
    val pendingOpen = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Lang.apply(prefs.lang.value)
        Fmt.currency = prefs.currency.value
        garage = Garage(this)
        if (garage.created) migrateFrom01()
        obd = Obd(this, prefs) { garage.s.current }
        Reminders.createChannels(this)
        prefs.onChanged = { Reminders.reschedule(this) }
        Reminders.reschedule(this)
        // ежедневная проверка могла не сработать (телефон был выключен) — догоняем при запуске
        if (AutoBackup.due(this)) thread { AutoBackup.runIfDue(this) }
    }

    /** С 0.1: средний пробег из общих настроек — в профиль первого транспорта (вид угадывается по полю «Топливо»). */
    private fun migrateFrom01() {
        val r = garage.repo(1)
        val km = prefs.legacyKmPerMonth ?: return
        if (r.s.setupDone && r.s.car.kmPerMonth == 0) r.saveCar(r.s.car.copy(kmPerMonth = km))
    }
}
