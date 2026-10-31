package com.relkin.carstatus.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_DAILY -> {
                Reminders.check(ctx)
                Reminders.reschedule(ctx)
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
