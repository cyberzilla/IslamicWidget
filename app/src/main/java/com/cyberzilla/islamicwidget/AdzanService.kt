package com.cyberzilla.islamicwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.Locale

class AdzanService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var originalAlarmVolume: Int = -1
    private var serviceWakeLock: PowerManager.WakeLock? = null

    private var isFadingOut = false

    private var isScreenReceiverRegistered = false
    private var canInterruptFromScreen = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if ((action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) && canInterruptFromScreen) {
                fadeOutAndStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_FADE_OUT") {
            fadeOutAndStop()
            return START_NOT_STICKY
        }

        isFadingOut = false

        if (!isScreenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
            isScreenReceiverRegistered = true

            Handler(Looper.getMainLooper()).postDelayed({
                canInterruptFromScreen = true
            }, 1500)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        serviceWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IslamicWidget:AdzanServiceLock")

        if (serviceWakeLock?.isHeld == false) {
            serviceWakeLock?.acquire(10 * 60 * 1000L)
        }

        val settings = SettingsManager(this)

        val isSubuh = intent?.getBooleanExtra("IS_SUBUH", false) ?: false
        val prayerId = intent?.getIntExtra("PRAYER_ID", 0) ?: 0

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
            .setAutoCancel(true)
            .build()

        startForeground(1122, notification)

        settings.isAdzanPlaying = true

        val updateIntent = Intent(this, SilentModeReceiver::class.java).apply {
            action = "ACTION_UPDATE_WIDGETS_BROADCAST"
        }
        sendBroadcast(updateIntent)

        playAdzan(isSubuh, settings)

        return START_STICKY
    }

    private fun fadeOutAndStop() {
        if (isFadingOut || mediaPlayer == null) {
            stopSelf()
            return
        }
        isFadingOut = true
        var volume = 1.0f
        val fadeDuration = 2000L
        val fadeSteps = 20
        val fadeInterval = fadeDuration / fadeSteps

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                volume -= (1.0f / fadeSteps)
                if (volume <= 0f) {
                    try { mediaPlayer?.setVolume(0f, 0f) } catch (e: Exception) {}
                    stopSelf()
                } else {
                    try {
                        mediaPlayer?.setVolume(volume, volume)
                        handler.postDelayed(this, fadeInterval)
                    } catch (e: Exception) {
                        stopSelf()
                    }
                }
            }
        }
        handler.post(runnable)
    }

    private fun playAdzan(isSubuh: Boolean, settings: SettingsManager) {
        val customUriString = if (isSubuh) settings.customAdzanSubuhUri else settings.customAdzanRegularUri
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            if (originalAlarmVolume == -1) {
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolume = (maxVolume * settings.adzanVolume) / 100
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
        } catch (e: SecurityException) {}

        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
                start()
            }

            mediaPlayer?.setOnCompletionListener {
                val stopIntent = Intent(this@AdzanService, SilentModeReceiver::class.java).apply {
                    action = "ACTION_STOP_ADZAN_BROADCAST"
                }
                sendBroadcast(stopIntent)
            }
        } catch (e: Exception) {
            val stopIntent = Intent(this@AdzanService, SilentModeReceiver::class.java).apply {
                action = "ACTION_STOP_ADZAN_BROADCAST"
            }
            sendBroadcast(stopIntent)
        }
    }

    private fun restoreOriginalVolume() {
        if (originalAlarmVolume != -1) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
                originalAlarmVolume = -1
            } catch (e: SecurityException) {}
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

        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
                isScreenReceiverRegistered = false
                canInterruptFromScreen = false
            } catch (e: Exception) {}
        }

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        restoreOriginalVolume()

        val settings = SettingsManager(this)
        if (settings.isAdzanPlaying) {
            settings.isAdzanPlaying = false
            val updateIntent = Intent(this, SilentModeReceiver::class.java).apply {
                action = "ACTION_UPDATE_WIDGETS_BROADCAST"
            }
            sendBroadcast(updateIntent)
        }

        SilentModeReceiver.releaseWakeLock()
        serviceWakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }
}