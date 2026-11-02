package com.relkin.carstatus.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.relkin.carstatus.data.AutoBackup
import kotlin.concurrent.thread

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_DAILY -> {
                Reminders.check(ctx)
                Reminders.reschedule(ctx)
                // копия с фото может писаться дольше, чем живёт обычный onReceive
                if (AutoBackup.due(ctx)) {
                    val pending = goAsync()
                    thread { try { AutoBackup.runIfDue(ctx) } finally { pending.finish() } }
                }
            }
            Reminders.ACTION_TEST -> Reminders.showTest(ctx, intent.getIntExtra(Reminders.EXTRA_MODE, Mode.NOTIFY))
            Reminders.ACTION_DISMISS -> Reminders.dismiss(ctx, intent.getIntExtra(Reminders.EXTRA_NOTIF, 0))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.reschedule(ctx)
    }
}
