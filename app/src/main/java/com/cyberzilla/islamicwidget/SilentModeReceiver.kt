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
                Log.e(TAG, "Failed to release WakeLock", e)
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
                    Log.e(TAG, context.getString(R.string.log_error_play_adzan, e.message))
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
                Log.d(TAG, "ACTION_MUTE received")

                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    Log.w(TAG, "DND permission not granted")
                    return
                }

                var isMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP", false)
                val currentRingerMode = audioManager.ringerMode
                val currentMediaVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                // SELF-HEALING: Deteksi user unmute manual menggunakan ringerMode
                if (isMutedByApp && currentRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                    Log.d(TAG, "Self-healing: Detected manual unmute, resetting flag")
                    isMutedByApp = false
                    prefs.edit().putBoolean("IS_MUTED_BY_APP", false).apply()
                }

                if (!isMutedByApp) {
                    // MUTE PERTAMA KALI: Backup state lalu eksekusi mute
                    try {
                        Log.d(TAG, "First time mute: backing up state - mediaVol: $currentMediaVol, ringerMode: $currentRingerMode")

                        prefs.edit()
                            .putInt("PREF_PREV_MEDIA_VOL", currentMediaVol)
                            .putInt("PREF_PREV_RINGER_MODE", currentRingerMode)
                            .putBoolean("IS_MUTED_BY_APP", true)
                            .apply()

                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        Log.d(TAG, "Device muted successfully")
                    } catch (e: SecurityException) {
                        Log.e(TAG, context.getString(R.string.log_error_mute, e.message))
                    }
                } else {
                    // ENFORCE MUTE (Jika action dipanggil berkali-kali)
                    // CEK LOGIKA: Jangan panggil API jika sudah dalam keadaan mute untuk menghindari redundant process
                    if (currentRingerMode != AudioManager.RINGER_MODE_VIBRATE || currentMediaVol > 0) {
                        Log.d(TAG, "Already muted by app, but state altered. Enforcing mute state.")
                        try {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                            Log.d(TAG, "Mute state enforced")
                        } catch (e: SecurityException) {
                            Log.e(TAG, context.getString(R.string.log_error_mute, e.message))
                        }
                    } else {
                        // Jika sudah vibrate dan media volume 0, abaikan eksekusi hardware
                        Log.d(TAG, "Device is already silently enforced. Ignoring redundant trigger.")
                    }
                }
            }

            "ACTION_UNMUTE" -> {
                Log.d(TAG, "ACTION_UNMUTE received")

                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    Log.w(TAG, "DND permission not granted")
                    return
                }

                val wasMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP", false)
                Log.d(TAG, "wasMutedByApp: $wasMutedByApp")

                if (wasMutedByApp) {
                    try {
                        val prevMediaVol = prefs.getInt("PREF_PREV_MEDIA_VOL", -1)
                        val prevRingerMode = prefs.getInt("PREF_PREV_RINGER_MODE", AudioManager.RINGER_MODE_NORMAL)

                        Log.d(TAG, "Restoring previous state - mediaVol: $prevMediaVol, ringerMode: $prevRingerMode")

                        audioManager.ringerMode = prevRingerMode
                        if (prevMediaVol != -1) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, prevMediaVol, 0)
                        }
                        Log.d(TAG, "Device unmuted successfully")
                    } catch (e: SecurityException) {
                        Log.e(TAG, context.getString(R.string.log_error_unmute, e.message))
                    } finally {
                        // CRITICAL: Finally block memastikan flag selalu direset
                        Log.d(TAG, "Resetting mute flag in finally block")
                        prefs.edit().putBoolean("IS_MUTED_BY_APP", false).apply()
                    }
                } else {
                    Log.d(TAG, "Not muted by app, resetting flag anyway for clean state")
                    prefs.edit().putBoolean("IS_MUTED_BY_APP", false).apply()
                }
            }
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