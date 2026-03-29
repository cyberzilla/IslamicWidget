package com.cyberzilla.islamicwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object QuoteUpdateManager {

    /**
     * @param intervalMinutes Set > 0 untuk menyalakan, set 0 untuk mematikan.
     */
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

            // Set alarm berulang sesuai interval
            alarmManager.setRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + intervalMillis,
                intervalMillis,
                pendingIntent
            )
        }
    }
}