package com.cyberzilla.islamicwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object QuoteUpdateManager {

    @SuppressLint("ScheduleExactAlarm")
    fun setAutoUpdate(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, QuoteWidgetProvider::class.java).apply {
            action = QuoteWidgetProvider.ACTION_RANDOM_QUOTE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        if (intervalMinutes > 0) {
            val intervalMillis = intervalMinutes * 60 * 1000L
            val triggerAt = System.currentTimeMillis() + intervalMillis

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC, triggerAt, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent)
            }
        }
    }
}
