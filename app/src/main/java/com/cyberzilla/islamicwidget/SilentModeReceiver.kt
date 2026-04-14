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
                Log.e(TAG, "Gagal melepas WakeLock", e)
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
                Log.d(TAG, "ACTION_MUTE diterima")

                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    Log.w(TAG, "Izin DND belum diberikan")
                    return
                }

                var isMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP", false)
                val currentRingerMode = audioManager.ringerMode
                val currentMediaVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                // SELF-HEALING: Deteksi user unmute manual menggunakan ringerMode
                if (isMutedByApp && currentRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                    Log.d(TAG, "Self-healing: Terdeteksi unmute manual oleh user, mereset flag")
                    isMutedByApp = false
                    prefs.edit().putBoolean("IS_MUTED_BY_APP", false).apply()
                }

                if (!isMutedByApp) {
                    // MUTE PERTAMA KALI: Backup state lalu eksekusi mute
                    try {
                        Log.d(TAG, "Mute pertama: backup state - mediaVol: $currentMediaVol, ringerMode: $currentRingerMode")

                        prefs.edit()
                            .putInt("PREF_PREV_MEDIA_VOL", currentMediaVol)
                            .putInt("PREF_PREV_RINGER_MODE", currentRingerMode)
                            .putBoolean("IS_MUTED_BY_APP", true)
                            .apply()

                        if (currentRingerMode != AudioManager.RINGER_MODE_VIBRATE) {
                            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        }
                        if (currentMediaVol > 0) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        }
                        Log.d(TAG, "Perangkat berhasil di-mute")
                    } catch (e: SecurityException) {
                        Log.e(TAG, context.getString(R.string.log_error_mute, e.message))
                    }
                } else {
                    // =======================================================================
                    // FIX — ROOT CAUSE BUG NOTIFIKASI SILENT DUPLIKAT
                    //
                    // MASALAH LAMA: Kondisi `if (currentRingerMode != VIBRATE || currentMediaVol > 0)`
                    // menggunakan operator OR (||). Akibatnya jika ringerMode SUDAH VIBRATE
                    // tapi mediaVol > 0, blok ini tetap masuk dan memanggil:
                    //   audioManager.ringerMode = RINGER_MODE_VIBRATE  ← SUDAH VIBRATE!
                    // Memanggil ringerMode = VIBRATE saat sudah VIBRATE tetap memicu
                    // Android menampilkan notifikasi sistem "Vibrate" sekali lagi.
                    //
                    // SOLUSI: Pisahkan pengecekan ringerMode dan mediaVol menjadi dua
                    // kondisi IF independen. Masing-masing hanya dipanggil jika BENAR-BENAR
                    // perlu berubah. Dengan ini, pemanggilan ringerMode hanya terjadi jika
                    // statusnya bukan VIBRATE, dan notifikasi sistem tidak muncul lagi.
                    // =======================================================================
                    val ringerNeedsEnforce = currentRingerMode != AudioManager.RINGER_MODE_VIBRATE
                    val mediaVolNeedsEnforce = currentMediaVol > 0

                    if (ringerNeedsEnforce || mediaVolNeedsEnforce) {
                        Log.d(TAG, "ENFORCE MUTE: ringerNeedsChange=$ringerNeedsEnforce, mediaVolNeedsChange=$mediaVolNeedsEnforce")
                        try {
                            // Panggil ringerMode HANYA jika belum VIBRATE
                            // Ini mencegah notifikasi sistem muncul berulang
                            if (ringerNeedsEnforce) {
                                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                                Log.d(TAG, "Ringer mode diubah ke VIBRATE")
                            }
                            // Tangani media volume secara independen
                            if (mediaVolNeedsEnforce) {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                                Log.d(TAG, "Media volume direset ke 0")
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, context.getString(R.string.log_error_mute, e.message))
                        }
                    } else {
                        // Sudah sepenuhnya dalam keadaan mute — tidak ada yang perlu dilakukan
                        Log.d(TAG, "Perangkat sudah dalam keadaan silent penuh. Abaikan trigger redundan.")
                    }
                }
            }

            "ACTION_UNMUTE" -> {
                Log.d(TAG, "ACTION_UNMUTE diterima")

                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    Log.w(TAG, "Izin DND belum diberikan")
                    return
                }

                val wasMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP", false)
                Log.d(TAG, "wasMutedByApp: $wasMutedByApp")

                if (wasMutedByApp) {
                    try {
                        val prevMediaVol = prefs.getInt("PREF_PREV_MEDIA_VOL", -1)
                        val prevRingerMode = prefs.getInt("PREF_PREV_RINGER_MODE", AudioManager.RINGER_MODE_NORMAL)

                        Log.d(TAG, "Memulihkan state sebelumnya - mediaVol: $prevMediaVol, ringerMode: $prevRingerMode")

                        audioManager.ringerMode = prevRingerMode
                        if (prevMediaVol != -1) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, prevMediaVol, 0)
                        }
                        Log.d(TAG, "Perangkat berhasil di-unmute")
                    } catch (e: SecurityException) {
                        Log.e(TAG, context.getString(R.string.log_error_unmute, e.message))
                    } finally {
                        // CRITICAL: Finally block memastikan flag selalu direset
                        Log.d(TAG, "Mereset flag mute di finally block")
                        prefs.edit().putBoolean("IS_MUTED_BY_APP", false).apply()
                    }
                } else {
                    Log.d(TAG, "Tidak di-mute oleh app, mereset flag untuk clean state")
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
