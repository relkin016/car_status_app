package com.relkin.carstatus

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.relkin.carstatus.notify.Reminders
import com.relkin.carstatus.ui.CarRoot
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Системные строки (выбор даты и времени) — по-русски, как и интерфейс. */
    override fun attachBaseContext(base: Context) {
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CarRoot(application as CarApp) }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val open = intent?.getStringExtra(EXTRA_OPEN) ?: return
        intent.removeExtra(EXTRA_OPEN)
        Reminders.dismiss(this, intent.getIntExtra(Reminders.EXTRA_NOTIF, 0))
        (application as CarApp).pendingOpen.value = open
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_ODO = "odo"
    }
}
