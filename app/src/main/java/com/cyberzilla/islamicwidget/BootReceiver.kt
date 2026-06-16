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

        // === FIX: Cancel semua stale alarm sebelum re-schedule ===
        val settings = SettingsManager(context)
        settings.cancelAllSilentAlarms()

        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isAdzanPlaying", false)
            .putLong("adzanPlayStartTime", 0L)
            .putBoolean("PENDING_UNMUTE", false)
            .putBoolean("IS_TEST_MODE_ACTIVE", false)
            // FIX: Clear mute flags saat boot. Sebelumnya flags ini persist across reboot,
            // menyebabkan idempotency guard di SilentModeReceiver men-skip MUTE yang legitimate
            // karena mengira device masih di-mute (padahal OS sudah reset DND saat boot).
            .putBoolean("IS_MUTED_BY_APP_DND", false)
            .putBoolean("IS_MUTED_BY_APP_RINGER", false)
            .remove("LAST_SCHEDULE_FINGERPRINT")
            // FIX: Tandai bahwa alarm perlu dijadwalkan ulang setelah boot.
            // onUpdate hanya akan menjalankan scheduleAllPrayers jika flag ini true.
            .putBoolean("NEEDS_RESCHEDULE", true)
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

        val quoteInterval = settings.quoteUpdateInterval
        if (quoteInterval > 0) {
            QuoteUpdateManager.setAutoUpdate(context, quoteInterval)
        }
    }
}
