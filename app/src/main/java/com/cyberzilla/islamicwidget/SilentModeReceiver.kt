package com.cyberzilla.islamicwidget

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

class SilentModeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SilentModeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "ACTION_PLAY_ADZAN" -> {
                executeMute(context)

                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val bridgeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IslamicWidget:BridgeLock")
                bridgeLock.acquire(15 * 1000L)

                val serviceIntent = Intent(context, AdzanService::class.java).apply {
                    putExtra("IS_SUBUH", intent.getBooleanExtra("IS_SUBUH", false))
                    putExtra("PRAYER_ID", intent.getIntExtra("PRAYER_ID", 0))
                    putExtra("PRAYER_TIME_MILLIS", intent.getLongExtra("PRAYER_TIME_MILLIS", 0L))
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, context.getString(R.string.log_error_play_adzan, e.message))
                }
            }

            "ACTION_STOP_ADZAN_BROADCAST" -> {
                val settings = SettingsManager(context)
                settings.isAdzanPlaying = false
                forceUpdateAllWidgets(context)

                val fadeOutIntent = Intent(context, AdzanService::class.java).apply {
                    action = "ACTION_FADE_OUT"
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(fadeOutIntent)
                    } else {
                        context.startService(fadeOutIntent)
                    }
                } catch (e: Exception) {
                    context.stopService(Intent(context, AdzanService::class.java))
                }
            }

            "ACTION_UPDATE_WIDGETS_BROADCAST" -> {
                forceUpdateAllWidgets(context)
            }

            "ACTION_MUTE" -> {
                Log.d(TAG, "ACTION_MUTE: Mengeksekusi Silent Mode")
                executeMute(context)
            }

            "ACTION_UNMUTE" -> {
                Log.d(TAG, "ACTION_UNMUTE: Jadwal Unmute tiba")
                val settings = SettingsManager(context)

                if (settings.isAdzanPlaying) {
                    Log.w(TAG, "Adzan masih bermain! Menahan perintah unmute sampai Adzan selesai.")
                    prefs.edit().putBoolean("PENDING_UNMUTE", true).apply()
                    return
                }

                executeUnmute(context)
            }
        }
    }

    private fun executeMute(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "Izin DND tidak ada. Menggunakan fallback Vibrate biasa.")
            try {
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_VIBRATE) {
                    prefs.edit()
                        .putInt("PREF_PREV_RINGER", audioManager.ringerMode)
                        .putBoolean("IS_MUTED_BY_APP_RINGER", true)
                        .apply()
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal fallback mute: ${e.message}")
            }
            return
        }

        val isMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP_DND", false)
        val currentFilter = notificationManager.currentInterruptionFilter

        if (!isMutedByApp) {
            prefs.edit()
                .putInt("PREF_PREV_FILTER", currentFilter)
                .putBoolean("IS_MUTED_BY_APP_DND", true)
                .apply()
        }

        try {
            // =======================================================================
            // FIX TAMPILAN ICON: Menggunakan INTERRUPTION_FILTER_PRIORITY.
            // Ini adalah level DND yang sama persis dengan menekan tombol manual di
            // Notification Manager. Hanya memunculkan icon bulan sabit tanpa merusak
            // Ringer Mode menjadi Vibrate (tanpa lonceng tercoret).
            // =======================================================================
            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                Log.d(TAG, "DND 'Priority' berhasil diaktifkan. Hanya Moon Icon yang muncul.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengaktifkan DND: ${e.message}")
        }
    }

    private fun executeUnmute(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        val wasMutedByAppDnd = prefs.getBoolean("IS_MUTED_BY_APP_DND", false)
        val wasMutedByAppRinger = prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false)

        try {
            if (wasMutedByAppDnd && notificationManager.isNotificationPolicyAccessGranted) {
                val prevFilter = prefs.getInt("PREF_PREV_FILTER", NotificationManager.INTERRUPTION_FILTER_ALL)
                notificationManager.setInterruptionFilter(prevFilter)
                Log.d(TAG, "Perangkat sukses dikembalikan dari DND ke state Normal.")
            }

            if (wasMutedByAppRinger) {
                val prevRinger = prefs.getInt("PREF_PREV_RINGER", AudioManager.RINGER_MODE_NORMAL)
                audioManager.ringerMode = prevRinger
                Log.d(TAG, "Perangkat sukses dikembalikan dari Fallback Vibrate ke state Normal.")
            }
        } catch (e: Exception) {
            Log.e(TAG, context.getString(R.string.log_error_unmute, e.message))
        } finally {
            prefs.edit()
                .putBoolean("IS_MUTED_BY_APP_DND", false)
                .putBoolean("IS_MUTED_BY_APP_RINGER", false)
                .putBoolean("PENDING_UNMUTE", false)
                .apply()
        }
    }

    private fun forceUpdateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val islamicWidget = ComponentName(context, IslamicWidgetProvider::class.java)
        val islamicIds = appWidgetManager.getAppWidgetIds(islamicWidget)
        if (islamicIds.isNotEmpty()) {
            val updateIslamic = Intent(context, IslamicWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, islamicIds)
            }
            context.sendBroadcast(updateIslamic)
        }

        val quotesWidget = ComponentName(context, QuoteWidgetProvider::class.java)
        val quotesIds = appWidgetManager.getAppWidgetIds(quotesWidget)
        if (quotesIds.isNotEmpty()) {
            val updateQuotes = Intent(context, QuoteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quotesIds)
            }
            context.sendBroadcast(updateQuotes)
        }
    }
}