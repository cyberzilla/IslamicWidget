package com.cyberzilla.islamicwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.Locale

class AdzanService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var originalAlarmVolume: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = SettingsManager(this)

        if (intent?.action == "ACTION_STOP_ADZAN") {
            stopAdzan(settings)
            return START_NOT_STICKY
        }

        val isSubuh = intent?.getBooleanExtra("IS_SUBUH", false) ?: false
        val prayerId = intent?.getIntExtra("PRAYER_ID", 0) ?: 0

        val selectedLocale = Locale.forLanguageTag(settings.languageCode)
        val config = Configuration(resources.configuration)
        config.setLocale(selectedLocale)
        val localizedContext = createConfigurationContext(config)

        val prayerName = when(prayerId) {
            1 -> localizedContext.getString(R.string.fajr)
            2 -> localizedContext.getString(R.string.dhuhr)
            3 -> localizedContext.getString(R.string.asr)
            4 -> localizedContext.getString(R.string.maghrib)
            5 -> localizedContext.getString(R.string.isha)
            else -> localizedContext.getString(R.string.prayer)
        }

        createNotificationChannel(localizedContext)

        val stopIntent = Intent(this, AdzanService::class.java).apply { action = "ACTION_STOP_ADZAN" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // PERBAIKAN: Ubah deletePendingIntent untuk mengirim broadcast ke SilentModeReceiver
        val clearIntent = Intent(this, SilentModeReceiver::class.java).apply {
            action = "ACTION_ADZAN_DISMISSED"
        }
        val deletePendingIntent = PendingIntent.getBroadcast(this, 1, clearIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "ADZAN_CHANNEL_V2")
            .setContentTitle(localizedContext.getString(R.string.notif_title_adzan, prayerName))
            .setContentText(localizedContext.getString(R.string.notif_desc_adzan))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, localizedContext.getString(R.string.notif_action_stop), stopPendingIntent)
            .setDeleteIntent(deletePendingIntent) // Dipanggil saat user swipe/clear notifikasi
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        startForeground(1122, notification)

        settings.isAdzanPlaying = true
        updateWidgetNow()

        playAdzan(isSubuh, settings)

        return START_STICKY
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
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
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
                stopAdzan(settings)
            }
        } catch (e: Exception) {
            stopAdzan(settings)
        }
    }

    private fun stopAdzan(settings: SettingsManager) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        restoreOriginalVolume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        settings.isAdzanPlaying = false

        forceFullWidgetUpdate()
        stopSelf()
    }

    private fun updateWidgetNow() {
        val intent = Intent(this, IslamicWidgetProvider::class.java).apply {
            action = "com.cyberzilla.islamicwidget.ACTION_UPDATE_ADZAN_STATE"
            val ids = AppWidgetManager.getInstance(application)
                .getAppWidgetIds(ComponentName(application, IslamicWidgetProvider::class.java))
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(intent)
    }

    private fun forceFullWidgetUpdate() {
        val appWidgetManager = AppWidgetManager.getInstance(application)

        // Update Islamic Widget
        val islamicWidget = ComponentName(application, IslamicWidgetProvider::class.java)
        val islamicIds = appWidgetManager.getAppWidgetIds(islamicWidget)
        val updateIslamic = Intent(application, IslamicWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, islamicIds)
        }
        sendBroadcast(updateIslamic)

        val quotesWidget = ComponentName(application, QuoteWidgetProvider::class.java)
        val quotesIds = appWidgetManager.getAppWidgetIds(quotesWidget)
        val updateQuotes = Intent(application, QuoteWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quotesIds)
        }
        sendBroadcast(updateQuotes)
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
        mediaPlayer?.release()
        mediaPlayer = null
        restoreOriginalVolume()
        val settings = SettingsManager(this)
        if (settings.isAdzanPlaying) {
            settings.isAdzanPlaying = false
            forceFullWidgetUpdate()
        }
    }
}