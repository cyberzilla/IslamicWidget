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
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.VolumeProviderCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import java.util.Locale

class AdzanService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var serviceWakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isFadingOut = false
    private var initialAlarmVolume = -1

    private var mediaSession: MediaSessionCompat? = null

    private var fadeHandler: Handler? = null
    private var fadeRunnable: Runnable? = null
    private var safetyHandler: Handler? = null
    private var safetyRunnable: Runnable? = null

    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var volumeReceiver: BroadcastReceiver? = null
    private var volumeObserver: ContentObserver? = null

    private var prayerTimeInMillis = 0L
    private var prayerId = 0

    companion object {
        private const val TAG = "AdzanService"
        private const val MAX_SERVICE_DURATION_MS = 15 * 60 * 1000L
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

        val isSubuh = intent.getBooleanExtra("IS_SUBUH", false)
        prayerId = intent.getIntExtra("PRAYER_ID", 0)
        prayerTimeInMillis = intent.getLongExtra("PRAYER_TIME_MILLIS", 0L)

        val selectedLocale = Locale.forLanguageTag(settings.languageCode)
        val config = Configuration(resources.configuration)
        config.setLocale(selectedLocale)
        val localizedContext = createConfigurationContext(config)

        val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
        val prayerName = when (prayerId) {
            1 -> localizedContext.getString(R.string.fajr)
            2 -> if (isFriday) localizedContext.getString(R.string.friday) else localizedContext.getString(R.string.dhuhr)
            3 -> localizedContext.getString(R.string.asr)
            4 -> localizedContext.getString(R.string.maghrib)
            5 -> localizedContext.getString(R.string.isha)
            else -> localizedContext.getString(R.string.prayer)
        }

        createNotificationChannel(localizedContext)

        val directStopIntent = Intent(this, AdzanService::class.java).apply { action = "ACTION_FADE_OUT" }
        val stopPendingIntent = PendingIntent.getService(this, 0, directStopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        mediaSession = MediaSessionCompat(this, "AdzanSessionHidden")
        mediaSession?.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .setActions(PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PAUSE)
                .build()
        )

        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }

                if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                    fadeOutAndStop()
                    return true
                }
                return super.onMediaButtonEvent(mediaButtonEvent)
            }
            override fun onStop() { fadeOutAndStop() }
            override fun onPause() { fadeOutAndStop() }
        })

        mediaSession?.isActive = true

        val notification = NotificationCompat.Builder(this, "ADZAN_CHANNEL_V2")
            .setContentTitle(localizedContext.getString(R.string.notif_title_adzan, prayerName))
            .setContentText(localizedContext.getString(R.string.notif_desc_adzan))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(stopPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1122, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1122, notification)
        }

        requestAudioFocusCompat()

        settings.isAdzanPlaying = true
        sendBroadcast(Intent(this, SilentModeReceiver::class.java).apply {
            action = "ACTION_UPDATE_WIDGETS_BROADCAST"
        })

        playAdzan(isSubuh, settings)

        safetyHandler = Handler(Looper.getMainLooper())
        safetyRunnable = Runnable { if (!isFadingOut) stopSelf() }
        safetyHandler?.postDelayed(safetyRunnable!!, MAX_SERVICE_DURATION_MS)

        return START_STICKY
    }

    private fun fadeOutAndStop() {
        if (isFadingOut) return
        isFadingOut = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        if (mediaPlayer == null) {
            stopSelf()
            return
        }

        var volume = 1.0f
        val fadeSteps = 10
        val fadeInterval = 500L / fadeSteps

        fadeHandler = Handler(Looper.getMainLooper())
        fadeRunnable = object : Runnable {
            override fun run() {
                volume -= (1.0f / fadeSteps)
                if (volume <= 0f) {
                    try { mediaPlayer?.setVolume(0f, 0f) } catch (e: Exception) {}
                    stopSelf()
                } else {
                    try {
                        val internalVol = volume * (SettingsManager(this@AdzanService).adzanVolume / 100f)
                        mediaPlayer?.setVolume(internalVol, internalVol)
                        fadeHandler?.postDelayed(this, fadeInterval)
                    } catch (e: Exception) { stopSelf() }
                }
            }
        }
        fadeHandler?.post(fadeRunnable!!)
    }

    private fun requestAudioFocusCompat() {
        val am = audioManager ?: return

        audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (!isAdzanStillRelevant()) {
                        stopSelf()
                        return@OnAudioFocusChangeListener
                    }
                    try {
                        val internalVol = SettingsManager(this).adzanVolume / 100f
                        mediaPlayer?.setVolume(internalVol, internalVol)
                        if (mediaPlayer?.isPlaying == false && !isFadingOut) {
                            mediaPlayer?.start()
                        }
                    } catch (e: Exception) {}
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // =======================================================================
                    // FIX ADZAN TERPOTONG KETIKA SCREEN OFF: Jangan ubah apapun.
                    // Jika OS mencuri audio sementara karena Screen-Off Sound, biarkan saja.
                    // =======================================================================
                    Log.d(TAG, "Audio focus transient loss diabaikan (Proteksi Screen Off)")
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.d(TAG, "Audio focus duck diabaikan")
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    fadeOutAndStop()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusListener!!, Handler(Looper.getMainLooper()))
                .build()
            am.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun isAdzanStillRelevant(): Boolean {
        if (prayerTimeInMillis == 0L) return true
        val settings = SettingsManager(this)
        val afterMinutes = when (prayerId) {
            1 -> settings.fajrAfter
            2 -> {
                val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
                if (isFriday) settings.fridayAfter else settings.dhuhrAfter
            }
            3 -> settings.asrAfter
            4 -> settings.maghribAfter
            5 -> settings.ishaAfter
            else -> 30
        }
        val expiryTime = prayerTimeInMillis + (afterMinutes * 60 * 1000L)
        return System.currentTimeMillis() <= expiryTime
    }

    private fun abandonAudioFocusCompat() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioFocusListener?.let { am.abandonAudioFocus(it) }
        }
        audioFocusRequest = null
        audioFocusListener = null
    }

    private fun playAdzan(isSubuh: Boolean, settings: SettingsManager) {
        val customUriString = if (isSubuh) settings.customAdzanSubuhUri else settings.customAdzanRegularUri
        val targetInternalVolume = settings.adzanVolume / 100f

        mediaPlayer?.release()

        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

                setVolume(targetInternalVolume, targetInternalVolume)
                prepare()

                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING && !isFadingOut) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isAdzanStillRelevant()) {
                                stopSelf()
                                return@postDelayed
                            }
                            if (mediaPlayer?.isPlaying == false) {
                                try { mediaPlayer?.start() } catch (e: Exception) {}
                            }
                        }, 500)
                        true
                    } else false
                }

                setOnCompletionListener { fadeOutAndStop() }

                start()
            }

            initialAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
            registerHardwareVolumeListener()

        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar adzan", e)
            stopSelf()
        }
    }

    private fun registerHardwareVolumeListener() {
        val volumeProvider = object : VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                Log.d(TAG, "Tombol Volume Hardware ditekan via Provider. Stop Adzan.")
                if (!isFadingOut) fadeOutAndStop()
            }
        }
        mediaSession?.setPlaybackToRemote(volumeProvider)

        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION" && !isFadingOut) {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_ALARM || streamType == AudioManager.STREAM_RING) {
                        // =======================================================================
                        // FIX ADZAN TERPOTONG KETIKA SCREEN OFF
                        // Wajib mengecek apakah angka volume aktual BENAR-BENAR bergeser, karena
                        // OS Android mengirim broadcast palsu ini ketika Screen Off / Rerouting audio!
                        // =======================================================================
                        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
                        if (currentVol != initialAlarmVolume && currentVol != -1) {
                            Log.d(TAG, "Deteksi perubahan Stream aktual via Broadcast. Stop Adzan.")
                            fadeOutAndStop()
                        }
                    }
                }
            }
        }
        try {
            registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        } catch (e: Exception) {}

        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (isFadingOut) return

                val currentAlarmVol = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
                if (currentAlarmVol != initialAlarmVolume && currentAlarmVol != -1) {
                    Log.d(TAG, "Observer mendeteksi nilai absolut Volume bergeser. Stop Adzan.")
                    fadeOutAndStop()
                }
            }
        }
        try {
            contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver!!)
        } catch (e: Exception) {}
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

        volumeReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {}
            volumeReceiver = null
        }
        volumeObserver?.let {
            try { contentResolver.unregisterContentObserver(it) } catch (e: Exception) {}
            volumeObserver = null
        }

        safetyRunnable?.let { safetyHandler?.removeCallbacks(it) }
        safetyHandler = null
        safetyRunnable = null

        fadeRunnable?.let { fadeHandler?.removeCallbacks(it) }
        fadeHandler = null
        fadeRunnable = null

        try { mediaPlayer?.stop() } catch (e: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null

        abandonAudioFocusCompat()

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null

        val settings = SettingsManager(this)
        if (settings.isAdzanPlaying) {
            settings.isAdzanPlaying = false
            sendBroadcast(Intent(this, SilentModeReceiver::class.java).apply {
                action = "ACTION_UPDATE_WIDGETS_BROADCAST"
            })
        }

        serviceWakeLock?.let { if (it.isHeld) it.release() }

        val prefs = getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("PENDING_UNMUTE", false)) {
            Log.d(TAG, "Mengeksekusi PENDING UNMUTE karena Adzan sudah selesai/dihentikan.")
            sendBroadcast(Intent(this, SilentModeReceiver::class.java).apply {
                action = "ACTION_UNMUTE"
            })
        }
    }
}