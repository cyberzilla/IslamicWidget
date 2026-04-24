package com.cyberzilla.islamicwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        AdzanLogger.log(context, AdzanLogger.Event.BOOT_RESCHEDULE, "Boot completed, menjadwalkan ulang semua alarm")

        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isAdzanPlaying", false)
            .putLong("adzanPlayStartTime", 0L)
            .putBoolean("PENDING_UNMUTE", false)
            .putBoolean("IS_TEST_MODE_ACTIVE", false)
            .remove("LAST_SCHEDULE_FINGERPRINT")
            .apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)

        val islamicIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, IslamicWidgetProvider::class.java)
        )
        if (islamicIds.isNotEmpty()) {
            val islamicIntent = Intent(context, IslamicWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, islamicIds)
            }
            context.sendBroadcast(islamicIntent)
        }

        val settings = SettingsManager(context)
        val quoteInterval = settings.quoteUpdateInterval
        if (quoteInterval > 0) {
            QuoteUpdateManager.setAutoUpdate(context, quoteInterval)
        }
    }
}
