package com.cyberzilla.islamicwidget

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

class SilentModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        // Pastikan aplikasi masih memiliki izin DND dari pengguna
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.e("SilentModeReceiver", "Akses DND (Notification Policy) belum diberikan.")
            return
        }

        when (intent.action) {
            "ACTION_MUTE" -> {
                try {
                    // 1. Simpan volume media dan mode dering saat ini agar bisa dikembalikan nanti
                    val currentMediaVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val currentRingerMode = audioManager.ringerMode

                    prefs.edit()
                        .putInt("PREF_PREV_MEDIA_VOL", currentMediaVol)
                        .putInt("PREF_PREV_RINGER_MODE", currentRingerMode)
                        .apply()

                    // 2. Ubah mode dering ke GETAR (Vibrate)
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

                    // 3. Paksa volume Media (Musik/Video/Game) menjadi 0
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

                } catch (e: SecurityException) {
                    Log.e("SilentModeReceiver", "Gagal melakukan mute: ${e.message}")
                }
            }

            "ACTION_UNMUTE" -> {
                try {
                    // 1. Ambil data volume lama dari penyimpanan
                    val prevMediaVol = prefs.getInt("PREF_PREV_MEDIA_VOL", -1)
                    val prevRingerMode = prefs.getInt("PREF_PREV_RINGER_MODE", AudioManager.RINGER_MODE_NORMAL)

                    // 2. Kembalikan mode dering seperti semula
                    audioManager.ringerMode = prevRingerMode

                    // 3. Kembalikan volume Media jika sebelumnya ada data yang tersimpan
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