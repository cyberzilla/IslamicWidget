package com.cyberzilla.islamicwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AdzanService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_ADZAN") {
            stopAdzan()
            return START_NOT_STICKY
        }

        val isSubuh = intent?.getBooleanExtra("IS_SUBUH", false) ?: false
        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Adzan"

        createNotificationChannel()

        val stopIntent = Intent(this, AdzanService::class.java).apply { action = "ACTION_STOP_ADZAN" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "ADZAN_CHANNEL")
            .setContentTitle("Waktu $prayerName Telah Tiba")
            .setContentText("Adzan sedang berkumandang...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Matikan Adzan", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

        startForeground(1122, notification)
        playAdzan(isSubuh)

        return START_STICKY
    }

    private fun playAdzan(isSubuh: Boolean) {
        val settings = SettingsManager(this)
        val customUriString = if (isSubuh) settings.customAdzanSubuhUri else settings.customAdzanRegularUri

        mediaPlayer?.release()

        try {
            if (!customUriString.isNullOrEmpty()) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AdzanService, Uri.parse(customUriString))
                    prepare()
                    start()
                }
            } else {
                val rawResId = if (isSubuh) R.raw.adzan_subuh else R.raw.adzan_regular
                mediaPlayer = MediaPlayer.create(this, rawResId)
                mediaPlayer?.start()
            }

            mediaPlayer?.setOnCompletionListener {
                stopAdzan()
            }
        } catch (e: Exception) {
            val fallbackResId = if (isSubuh) R.raw.adzan_subuh else R.raw.adzan_regular
            mediaPlayer = MediaPlayer.create(this, fallbackResId)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { stopAdzan() }
        }
    }

    private fun stopAdzan() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ADZAN_CHANNEL",
                "Adzan Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat Adzan berkumandang"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}