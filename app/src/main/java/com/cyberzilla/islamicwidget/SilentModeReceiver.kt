package com.cyberzilla.islamicwidget

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log

class SilentModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "ACTION_PLAY_ADZAN" -> {
                val serviceIntent = Intent(context, AdzanService::class.java).apply {
                    putExtra("IS_SUBUH", intent.getBooleanExtra("IS_SUBUH", false))
                    putExtra("PRAYER_NAME", intent.getStringExtra("PRAYER_NAME"))
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("SilentModeReceiver", "Gagal memutar Adzan: ${e.message}")
                }
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
                    Log.e("SilentModeReceiver", "Gagal melakukan mute: ${e.message}")
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
                    Log.e("SilentModeReceiver", "Gagal mengembalikan volume: ${e.message}")
                }
            }
        }
    }
}