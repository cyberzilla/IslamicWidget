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
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "ACTION_PLAY_ADZAN" -> {
                val settings = SettingsManager(context)
                val prayerIdForLog = intent.getIntExtra("PRAYER_ID", 0)

                // === FIX: Guard fitur adzan audio ===
                // Jika adzan audio dimatikan (atau stale alarm dari sebelum reset),
                // abaikan trigger ini sepenuhnya.
                if (!settings.isAdzanAudioEnabled) {
                    AdzanLogger.log(context, AdzanLogger.Event.ADZAN_SKIPPED,
                        "Adzan untuk prayer ID=$prayerIdForLog diabaikan: isAdzanAudioEnabled=false (kemungkinan stale alarm)")
                    return
                }

                // === FIX: Guard duplikasi adzan ===
                // Jika adzan sudah sedang bermain, abaikan trigger duplikat.
                // Ini mencegah bug dimana cancel+reschedule alarm saat widget update
                // bisa menyebabkan alarm lama fire sebelum di-cancel.
                if (settings.isAdzanPlaying) {
                    AdzanLogger.log(context, AdzanLogger.Event.ADZAN_SKIPPED,
                        "Adzan untuk prayer ID=$prayerIdForLog diabaikan karena adzan lain masih bermain")
                    return
                }

                prefs.edit().putBoolean("PENDING_UNMUTE", false).apply()
                AdzanLogger.logAdzanFired(context, prayerIdForLog)

                // PENTING: TIDAK boleh executeMute() di sini!
                // Mute SEBELUM adzan mulai menyebabkan DND memblokir audio USAGE_ALARM
                // di beberapa OEM. Biarkan scheduled MUTE alarm menangani auto-silent,
                // dan AdzanService akan memastikan audio tetap terdengar via bypass DND.
                // Mute akan ditrigger oleh AdzanService SETELAH audio mulai bermain.

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
                    if (bridgeLock.isHeld) bridgeLock.release()
                    Log.e(TAG, context.getString(R.string.log_error_play_adzan, e.message))
                    AdzanLogger.log(context, AdzanLogger.Event.ADZAN_ERROR, "Gagal start AdzanService: ${e.message}")
                }
            }

            "ACTION_STOP_ADZAN_BROADCAST" -> {
                val settings = SettingsManager(context)
                settings.isAdzanPlaying = false
                forceUpdateAllWidgets(context)

                val fadeOutIntent = Intent(context, AdzanService::class.java).apply {
                    action = "ACTION_FADE_OUT"
                    putExtra("INTERRUPT_SOURCE", "internal")
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
                val settings = SettingsManager(context)
                // === FIX: Guard utama — cegah stale alarm mute setelah reset/clear cache ===
                if (!settings.isAutoSilentEnabled) {
                    Log.d(TAG, "ACTION_MUTE diabaikan: isAutoSilentEnabled=false (kemungkinan stale alarm)")
                    AdzanLogger.log(context, AdzanLogger.Event.MUTE_SKIPPED,
                        "ACTION_MUTE diabaikan karena Auto Silent dimatikan (stale alarm?)")
                    return
                }
                Log.d(TAG, "ACTION_MUTE: Mengeksekusi Silent Mode")
                AdzanLogger.log(context, AdzanLogger.Event.MUTE_EXECUTED, "ACTION_MUTE diterima")
                executeMute(context)
            }

            "ACTION_UNMUTE" -> {
                Log.d(TAG, "ACTION_UNMUTE: Jadwal Unmute tiba")
                val settings = SettingsManager(context)

                // === FIX: Jika auto silent dimatikan tapi ada stale unmute, tetap jalankan ===
                // Ini penting agar perangkat yang terlanjur di-mute oleh stale alarm bisa di-unmute.
                val wasMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP_DND", false) ||
                                    prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false)
                if (!settings.isAutoSilentEnabled && !wasMutedByApp) {
                    Log.d(TAG, "ACTION_UNMUTE diabaikan: Auto Silent off dan tidak ada mute aktif dari app")
                    AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_EXECUTED,
                        "ACTION_UNMUTE diabaikan (stale alarm, tidak ada mute aktif)")
                    return
                }

                if (settings.isAdzanPlaying) {
                    Log.w(TAG, "Adzan masih bermain! Menahan perintah unmute sampai Adzan selesai.")
                    AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_DEFERRED, "Unmute ditahan karena adzan masih bermain")
                    prefs.edit().putBoolean("PENDING_UNMUTE", true).apply()
                    return
                }

                AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_EXECUTED, "ACTION_UNMUTE dieksekusi")
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
            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                AdzanLogger.logMuteExecuted(context, "DND Priority")
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
                AdzanLogger.logUnmuteExecuted(context, "DND -> Normal")
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
                .putBoolean("IS_TEST_MODE_ACTIVE", false)
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
