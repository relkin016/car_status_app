package com.relkin.carstatus

import android.app.Application
import com.relkin.carstatus.data.Repo
import com.relkin.carstatus.notify.Reminders
import com.relkin.carstatus.obd.Obd
import com.relkin.carstatus.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow

class CarApp : Application() {
    lateinit var prefs: Prefs
    lateinit var repo: Repo
    lateinit var obd: Obd

    /** Экран, который нужно открыть по уведомлению (MainActivity.OPEN_*). */
    val pendingOpen = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Fmt.currency = prefs.currency.value
        repo = Repo(this)
        obd = Obd(this, prefs)
        Reminders.createChannels(this)
        prefs.onChanged = { Reminders.reschedule(this) }
        Reminders.reschedule(this)
    }
}
