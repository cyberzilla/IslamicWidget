package com.cyberzilla.islamicwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.Locale

class AdzanService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var serviceWakeLock: PowerManager.WakeLock? = null
    private var isFadingOut = false

    private var fadeHandler: Handler? = null
    private var fadeRunnable: Runnable? = null

    private var safetyHandler: Handler? = null
    private var safetyRunnable: Runnable? = null

    companion object {
        // Safety timeout absolut — paksa service mati setelah durasi ini
        private const val MAX_SERVICE_DURATION_MS = 15 * 60 * 1000L // 15 menit
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == "ACTION_FADE_OUT") {
            fadeOutAndStop()
            return START_NOT_STICKY
        }

        isFadingOut = false
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        serviceWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IslamicWidget:AdzanServiceLock")

        if (serviceWakeLock?.isHeld == false) {
            serviceWakeLock?.acquire(16 * 60 * 1000L)
        }

        val settings = SettingsManager(this)

        // Restore volume alarm jika ada sisa backup
        val leftoverVolume = settings.adzanOriginalVolumeBackup
        if (leftoverVolume != -1) {
            try {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, leftoverVolume, 0)
            } catch (e: SecurityException) {}
            settings.adzanOriginalVolumeBackup = -1
        }

        val isSubuh = intent.getBooleanExtra("IS_SUBUH", false)
        val prayerId = intent.getIntExtra("PRAYER_ID", 0)

        val selectedLocale = Locale.forLanguageTag(settings.languageCode)
        val config = Configuration(resources.configuration)
        config.setLocale(selectedLocale)
        val localizedContext = createConfigurationContext(config)

        val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY

        val prayerName = when(prayerId) {
            1 -> localizedContext.getString(R.string.fajr)
            2 -> if (isFriday) localizedContext.getString(R.string.friday) else localizedContext.getString(R.string.dhuhr)
            3 -> localizedContext.getString(R.string.asr)
            4 -> localizedContext.getString(R.string.maghrib)
            5 -> localizedContext.getString(R.string.isha)
            else -> localizedContext.getString(R.string.prayer)
        }

        createNotificationChannel(localizedContext)

        val stopIntent = Intent(this, SilentModeReceiver::class.java).apply {
            action = "ACTION_STOP_ADZAN_BROADCAST"
        }

        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deletePendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "ADZAN_CHANNEL_V2")
            .setContentTitle(localizedContext.getString(R.string.notif_title_adzan, prayerName))
            .setContentText(localizedContext.getString(R.string.notif_desc_adzan))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(stopPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()

        // METODE DARI PROJECT SEDERHANA: Deklarasi tipe Service untuk Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1122, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1122, notification)
        }

        settings.isAdzanPlaying = true

        val updateIntent = Intent(this, SilentModeReceiver::class.java).apply {
            action = "ACTION_UPDATE_WIDGETS_BROADCAST"
        }
        sendBroadcast(updateIntent)

        playAdzan(isSubuh, settings)

        // Safety timeout
        safetyHandler = Handler(Looper.getMainLooper())
        safetyRunnable = Runnable {
            if (!isFadingOut) stopSelf()
        }
        safetyHandler?.postDelayed(safetyRunnable!!, MAX_SERVICE_DURATION_MS)

        return START_STICKY // Gunakan START_STICKY seperti di project sederhana
    }

    private fun fadeOutAndStop() {
        if (isFadingOut || mediaPlayer == null) {
            stopSelf()
            return
        }
        isFadingOut = true
        var volume = 1.0f
        val fadeSteps = 20
        val fadeInterval = 2000L / fadeSteps

        fadeHandler = Handler(Looper.getMainLooper())
        fadeRunnable = object : Runnable {
            override fun run() {
                volume -= (1.0f / fadeSteps)
                if (volume <= 0f) {
                    try { mediaPlayer?.setVolume(0f, 0f) } catch (e: Exception) {}
                    stopSelf()
                } else {
                    try {
                        mediaPlayer?.setVolume(volume, volume)
                        fadeHandler?.postDelayed(this, fadeInterval)
                    } catch (e: Exception) { stopSelf() }
                }
            }
        }
        fadeHandler?.post(fadeRunnable!!)
    }

    private fun playAdzan(isSubuh: Boolean, settings: SettingsManager) {
        val customUriString = if (isSubuh) settings.customAdzanSubuhUri else settings.customAdzanRegularUri

        try {
            if (settings.adzanOriginalVolumeBackup == -1) {
                val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
                if (currentVol != -1) {
                    settings.adzanOriginalVolumeBackup = currentVol
                }
            }
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, (maxVol * settings.adzanVolume) / 100, 0)
        } catch (e: SecurityException) {}

        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                // METODE DARI PROJECT SEDERHANA: Tanpa menggunakan Audio Focus, langsung set properties tertinggi
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // Gunakan MUSIC sesuai project test Anda
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }

                if (!customUriString.isNullOrEmpty()) {
                    setDataSource(this@AdzanService, Uri.parse(customUriString))
                } else {
                    val rawResId = if (isSubuh) R.raw.adzan_subuh else R.raw.adzan_regular
                    val afd = resources.openRawResourceFd(rawResId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                prepare()

                setOnCompletionListener {
                    val stopIntent = Intent(this@AdzanService, SilentModeReceiver::class.java).apply {
                        action = "ACTION_STOP_ADZAN_BROADCAST"
                    }
                    sendBroadcast(stopIntent)
                }

                start()
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun restoreOriginalVolume() {
        val settings = SettingsManager(this)
        val savedVolume = settings.adzanOriginalVolumeBackup
        if (savedVolume != -1) {
            try {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            } catch (e: SecurityException) {}
            settings.adzanOriginalVolumeBackup = -1
        }
    }

    private fun createNotificationChannel(localizedContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ADZAN_CHANNEL_V2",
                localizedContext.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localizedContext.getString(R.string.notif_channel_desc)
                setSound(null, null)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        safetyRunnable?.let { safetyHandler?.removeCallbacks(it) }
        safetyHandler = null
        safetyRunnable = null

        fadeRunnable?.let { fadeHandler?.removeCallbacks(it) }
        fadeHandler = null
        fadeRunnable = null

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        restoreOriginalVolume()

        val settings = SettingsManager(this)
        if (settings.isAdzanPlaying) {
            settings.isAdzanPlaying = false
            sendBroadcast(Intent(this, SilentModeReceiver::class.java).apply { action = "ACTION_UPDATE_WIDGETS_BROADCAST" })
        }

        SilentModeReceiver.releaseWakeLock()
        serviceWakeLock?.let { if (it.isHeld) it.release() }
    }
}