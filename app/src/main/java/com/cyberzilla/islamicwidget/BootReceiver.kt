package com.cyberzilla.islamicwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * BUG FIX #2: Receiver untuk ACTION_BOOT_COMPLETED.
 * AlarmManager dibersihkan saat device reboot. Receiver ini memastikan
 * semua alarm (silent mode, adzan, quote) dijadwalkan ulang otomatis
 * setelah device menyala kembali.
 *
 * Daftarkan di AndroidManifest.xml:
 * <receiver android:name=".BootReceiver" android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.BOOT_COMPLETED"/>
 *     </intent-filter>
 * </receiver>
 * Dan tambahkan permission: <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // =======================================================================
        // FIX B3: Reset flag Adzan & DND ghost state setelah reboot.
        // Jika AdzanService di-kill paksa/reboot sebelum onDestroy(), flag ini
        // bisa stuck true → phone terjebak silent mode tanpa batas.
        // =======================================================================
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isAdzanPlaying", false)
            .putBoolean("PENDING_UNMUTE", false)
            .putBoolean("IS_TEST_MODE_ACTIVE", false)
            .apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)

        // 1. Trigger full update IslamicWidgetProvider — ini akan memanggil
        //    onUpdate → scheduleAllPrayers → scheduleSilentMode & adzan untuk hari ini
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

        // 2. Jadwalkan ulang auto-update quote sesuai interval yang tersimpan
        val settings = SettingsManager(context)
        val quoteInterval = settings.quoteUpdateInterval
        if (quoteInterval > 0) {
            QuoteUpdateManager.setAutoUpdate(context, quoteInterval)
        }
    }
}
