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
            // FIX: Clear PREF_PREV agar executeMute menyimpan state bersih setelah boot.
            // Tanpa ini, PREF_PREV bisa berisi PRIORITY (orphan dari session sebelumnya)
            // → executeUnmute restore ke PRIORITY → DND stuck selamanya.
            .remove("PREF_PREV_FILTER")
            .remove("PREF_PREV_POLICY_CATEGORIES")
            .remove("PREF_PREV_RINGER")
            .remove("LAST_SCHEDULE_FINGERPRINT")
            // FIX: Tandai bahwa alarm perlu dijadwalkan ulang setelah boot.
            // onUpdate hanya akan menjalankan scheduleAllPrayers jika flag ini true.
            .putBoolean("NEEDS_RESCHEDULE", true)
            .apply()

        // FIX: Clear orphan DND dari session sebelum reboot.
        // Saat app MUTE DND sebelum reboot, system DND bisa survive reboot di beberapa OEM.
        // Flag IS_MUTED_BY_APP_DND sudah di-clear di atas, tapi system DND masih ON.
        // Jika tidak di-clear, executeMute (dari mid-window fix) akan save PRIORITY
        // sebagai PREF_PREV_FILTER → UNMUTE restore ke PRIORITY → DND stuck selamanya.
        // scheduleAllPrayers akan re-apply MUTE jika masih di dalam silent window.
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.isNotificationPolicyAccessGranted &&
                nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_EXECUTED,
                    "Boot: orphan DND dari session sebelumnya di-clear. scheduleAllPrayers akan re-apply jika perlu.")
            }
        } catch (e: Exception) {
            // Gagal clear DND → tidak fatal, mid-window fix akan handle
        }

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
