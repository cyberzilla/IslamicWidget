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
        private var wakeLock: PowerManager.WakeLock? = null

        fun acquireWakeLock(context: Context) {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IslamicWidget:AdzanWakeLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        }

        fun releaseWakeLock() {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (e: Exception) {
                Log.e("SilentModeReceiver", "Failed to release WakeLock", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "ACTION_PLAY_ADZAN" -> {
                acquireWakeLock(context)

                val serviceIntent = Intent(context, AdzanService::class.java).apply {
                    putExtra("IS_SUBUH", intent.getBooleanExtra("IS_SUBUH", false))
                    putExtra("PRAYER_ID", intent.getIntExtra("PRAYER_ID", 0))
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("SilentModeReceiver", context.getString(R.string.log_error_play_adzan, e.message))
                    releaseWakeLock()
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
                if (!notificationManager.isNotificationPolicyAccessGranted) return
                try {
                    val currentMediaVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val currentRingerMode = audioManager.ringerMode

                    prefs.edit()
                        .putInt("PREF_PREV_MEDIA_VOL", currentMediaVol)
                        .putInt("PREF_PREV_RINGER_MODE", currentRingerMode)
                        .apply()

                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

                } catch (e: SecurityException) {
                    Log.e("SilentModeReceiver", context.getString(R.string.log_error_mute, e.message))
                }
            }

            "ACTION_UNMUTE" -> {
                if (!notificationManager.isNotificationPolicyAccessGranted) return
                try {
                    val prevMediaVol = prefs.getInt("PREF_PREV_MEDIA_VOL", -1)
                    val prevRingerMode = prefs.getInt("PREF_PREV_RINGER_MODE", AudioManager.RINGER_MODE_NORMAL)

                    audioManager.ringerMode = prevRingerMode

                    if (prevMediaVol != -1) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, prevMediaVol, 0)
                    }

                } catch (e: SecurityException) {
                    Log.e("SilentModeReceiver", context.getString(R.string.log_error_unmute, e.message))
                }
            }
        }
    }

    private fun forceUpdateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val islamicWidget = ComponentName(context, IslamicWidgetProvider::class.java)
        val islamicIds = appWidgetManager.getAppWidgetIds(islamicWidget)
        val updateIslamic = Intent(context, IslamicWidgetProvider::class.java).apply {
            action = "com.cyberzilla.islamicwidget.ACTION_UPDATE_ADZAN_STATE"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, islamicIds)
        }
        context.sendBroadcast(updateIslamic)

        val quotesWidget = ComponentName(context, QuoteWidgetProvider::class.java)
        val quotesIds = appWidgetManager.getAppWidgetIds(quotesWidget)
        val updateQuotes = Intent(context, QuoteWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quotesIds)
        }
        context.sendBroadcast(updateQuotes)
    }
}