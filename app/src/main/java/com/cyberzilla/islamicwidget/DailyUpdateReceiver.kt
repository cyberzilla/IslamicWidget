package com.cyberzilla.islamicwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver untuk daily update yang berjalan INDEPENDEN dari widget.
 *
 * Ditrigger oleh alarm harian (midnight, fajr+5min, maghrib+5min) yang dijadwalkan
 * oleh PrayerScheduler.scheduleDailyUpdate().
 *
 * Tanggung jawab:
 * 1. Update icon launcher (tanggal Hijri)
 * 2. Reschedule alarm sholat (adzan, silent mode)
 * 3. Refresh widget jika ada
 * 4. Jadwalkan alarm daily berikutnya
 */
class DailyUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DailyUpdateReceiver"
        const val ACTION_DAILY_UPDATE = "com.cyberzilla.islamicwidget.ACTION_DAILY_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_UPDATE) return

        Log.d(TAG, "Daily update alarm diterima")
        AdzanLogger.log(context, AdzanLogger.Event.BOOT_RESCHEDULE,
            "DailyUpdateReceiver: memperbarui icon, reschedule alarm, dan refresh widget")

        // 1. Force reschedule prayer alarms (hari baru = jadwal baru)
        PrayerScheduler.clearScheduleCache(context)
        PrayerScheduler.scheduleAllPrayers(context, forceReschedule = true)

        // 2. Update launcher icon sesuai tanggal Hijri
        PrayerScheduler.updateIconIfNeeded(context)

        // 3. Jadwalkan alarm daily berikutnya
        PrayerScheduler.scheduleDailyUpdate(context)

        // 4. Refresh widget jika ada (agar UI ikut terupdate)
        refreshAllWidgets(context)
    }

    private fun refreshAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Islamic Widget
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

        // Lunar Widget
        val lunarIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, LunarWidgetProvider::class.java)
        )
        if (lunarIds.isNotEmpty()) {
            val lunarIntent = Intent(context, LunarWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, lunarIds)
            }
            context.sendBroadcast(lunarIntent)
        }

        // Quote Widget
        val quoteIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, QuoteWidgetProvider::class.java)
        )
        if (quoteIds.isNotEmpty()) {
            val quoteIntent = Intent(context, QuoteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quoteIds)
            }
            context.sendBroadcast(quoteIntent)
        }
    }
}
