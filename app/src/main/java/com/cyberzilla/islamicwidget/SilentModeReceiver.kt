package com.cyberzilla.islamicwidget

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SilentModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Jika pengguna mencabut izin DND di pengaturan, kita hentikan prosesnya agar tidak error
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            return
        }

        when (intent.action) {
            "ACTION_MUTE" -> {
                // Aktifkan Mode Do Not Disturb (Hanya memprioritaskan alarm bangun tidur, sisanya senyap)
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            "ACTION_UNMUTE" -> {
                // Kembalikan ke Mode Normal (Suara dering dan notifikasi aktif lagi)
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
    }
}