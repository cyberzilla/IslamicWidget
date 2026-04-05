package com.cyberzilla.islamicwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object QuoteUpdateManager {

    /**
     * BUG FIX #10: Mengganti setRepeating() dengan setExactAndAllowWhileIdle().
     *
     * Sejak Android 6.0 (API 23), AlarmManager.setRepeating() tidak lagi dieksekusi
     * tepat waktu karena sistem melakukan batching alarm (Doze Mode). Quote widget
     * bisa terlambat update hingga puluhan menit dari interval yang diset user.
     *
     * Solusi: Gunakan pola "chain alarm" — set satu alarm exact, lalu saat alarm
     * tersebut terpicu (di QuoteWidgetProvider.onReceive), jadwalkan alarm berikutnya.
     * Ini menjamin setiap alarm dieksekusi tepat waktu.
     */
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

        // Selalu batalkan alarm lama dulu
        alarmManager.cancel(pendingIntent)

        if (intervalMinutes > 0) {
            val intervalMillis = intervalMinutes * 60 * 1000L
            val triggerAt = System.currentTimeMillis() + intervalMillis

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // setExactAndAllowWhileIdle — tetap berjalan meski Doze Mode aktif
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC, triggerAt, pendingIntent)
                }
            } catch (e: SecurityException) {
                // Fallback jika SCHEDULE_EXACT_ALARM tidak diberikan
                alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent)
            }
        }
    }
}
