package com.cyberzilla.islamicwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AdzanService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    // Variabel untuk menyimpan volume asli sebelum adzan berbunyi
    private var originalAlarmVolume: Int = -1

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

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // ========================================================
        // 1. TINGKATKAN VOLUME SEMENTARA (BOOST VOLUME)
        // ========================================================
        try {
            // Simpan volume alarm saat ini (jika belum tersimpan)
            if (originalAlarmVolume == -1) {
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }

            // Ambil batas volume maksimal di HP pengguna
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

            // Paksa set volume alarm ke maksimal agar adzan pasti terdengar
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        } catch (e: SecurityException) {
            // Abaikan jika sistem DND HP sangat ketat menolak perubahan volume
        }

        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                // Pastikan suaranya lewat jalur Alarm, bukan jalur Media
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }

                // Cek sumber suara
                if (!customUriString.isNullOrEmpty()) {
                    setDataSource(this@AdzanService, Uri.parse(customUriString))
                } else {
                    val rawResId = if (isSubuh) R.raw.adzan_subuh else R.raw.adzan_regular
                    val afd = resources.openRawResourceFd(rawResId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                prepare()
                start()
            }

            // Jika lagu/adzan selesai dengan sendirinya
            mediaPlayer?.setOnCompletionListener {
                stopAdzan()
            }
        } catch (e: Exception) {
            stopAdzan()
        }
    }

    private fun stopAdzan() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        // 2. KEMBALIKAN VOLUME KE AWAL SEBELUM SERVICE MATI
        restoreOriginalVolume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    // Fungsi khusus untuk mengembalikan volume yang disimpan
    private fun restoreOriginalVolume() {
        if (originalAlarmVolume != -1) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
                originalAlarmVolume = -1 // Reset memori
            } catch (e: SecurityException) {
                // Abaikan
            }
        }
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

        // Jaga-jaga: Kembalikan volume jika Service tiba-tiba di-kill oleh Android
        restoreOriginalVolume()
    }
}