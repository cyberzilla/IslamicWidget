package com.cyberzilla.islamicwidget

import android.app.NotificationManager
import android.app.AlarmManager
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "ACTION_PLAY_ADZAN" -> {
                val settings = SettingsManager(context)
                val prayerIdForLog = intent.getIntExtra("PRAYER_ID", 0)

                // === FIX: Guard fitur adzan audio ===
                // Cek master switch DAN per-prayer switch.
                // Jika adzan dimatikan secara global atau untuk sholat ini, abaikan.
                if (!settings.isAdzanEnabledForPrayer(prayerIdForLog)) {
                    AdzanLogger.log(context, AdzanLogger.Event.ADZAN_SKIPPED,
                        "Adzan untuk prayer ID=$prayerIdForLog diabaikan: adzan tidak aktif untuk sholat ini")
                    return
                }

                // === FIX: Guard duplikasi adzan ===
                // Jika adzan sudah sedang bermain, abaikan trigger duplikat.
                // Ini mencegah bug dimana cancel+reschedule alarm saat widget update
                // bisa menyebabkan alarm lama fire sebelum di-cancel.
                if (settings.isAdzanPlaying) {
                    AdzanLogger.log(context, AdzanLogger.Event.ADZAN_SKIPPED,
                        "Adzan untuk prayer ID=$prayerIdForLog diabaikan karena adzan lain masih bermain")
                    return
                }

                prefs.edit().putBoolean("PENDING_UNMUTE", false).apply()
                AdzanLogger.logAdzanFired(context, prayerIdForLog)

                // PENTING: TIDAK boleh executeMute() di sini!
                // Mute SEBELUM adzan mulai menyebabkan DND memblokir audio USAGE_ALARM
                // di beberapa OEM. Biarkan scheduled MUTE alarm menangani auto-silent,
                // dan AdzanService akan memastikan audio tetap terdengar via bypass DND.
                // Mute akan ditrigger oleh AdzanService SETELAH audio mulai bermain.

                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val bridgeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IslamicWidget:BridgeLock")
                bridgeLock.acquire(15 * 1000L)

                val serviceIntent = Intent(context, AdzanService::class.java).apply {
                    putExtra("IS_SUBUH", intent.getBooleanExtra("IS_SUBUH", false))
                    putExtra("PRAYER_ID", intent.getIntExtra("PRAYER_ID", 0))
                    putExtra("PRAYER_TIME_MILLIS", intent.getLongExtra("PRAYER_TIME_MILLIS", 0L))
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    if (bridgeLock.isHeld) bridgeLock.release()
                    Log.e(TAG, context.getString(R.string.log_error_play_adzan, e.message))
                    AdzanLogger.log(context, AdzanLogger.Event.ADZAN_ERROR, "Gagal start AdzanService: ${e.message}")
                }
            }

            "ACTION_WAKE_UP" -> {
                // === PRE-ADZAN WAKE-UP ===
                // Membangunkan device dari Doze mode 5 detik sebelum adzan.
                // Tanpa ini, proses bisa mati setelah alarm pertama (MUTE) diproses,
                // sehingga alarm ADZAN yang fire bersamaan/sesaat setelahnya tidak diproses.
                //
                // Menggunakan SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP
                // agar screen menyala sebentar — ini juga sinyal visual bagi user
                // bahwa adzan akan segera berkumandang.
                val prayerIdForLog = intent.getIntExtra("PRAYER_ID", 0)
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

                AdzanLogger.log(context, AdzanLogger.Event.DEVICE_WAKEUP,
                    "Pre-adzan wake-up untuk ${AdzanLogger.getPrayerName(prayerIdForLog)}, isInteractive=${pm.isInteractive}")

                if (!pm.isInteractive) {
                    // Device dalam keadaan screen off / Doze — bangunkan
                    @Suppress("DEPRECATION")
                    val screenLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                                PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "IslamicWidget:PreAdzanScreenWake"
                    )
                    screenLock.acquire(10_000L) // Tahan screen on 10 detik

                    // CPU WakeLock terpisah agar CPU tetap hidup walau screen mati lagi
                    val cpuLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "IslamicWidget:PreAdzanCpuWake"
                    )
                    cpuLock.acquire(30_000L) // Tahan CPU 30 detik sampai ADZAN alarm fire & service start
                }
            }

            "ACTION_STOP_ADZAN_BROADCAST" -> {
                val settings = SettingsManager(context)
                settings.isAdzanPlaying = false
                forceUpdateAllWidgets(context)

                val fadeOutIntent = Intent(context, AdzanService::class.java).apply {
                    action = "ACTION_FADE_OUT"
                    putExtra("INTERRUPT_SOURCE", "internal")
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
                val settings = SettingsManager(context)
                // === FIX: Guard utama — cegah stale alarm mute setelah reset/clear cache ===
                if (!settings.isAutoSilentEnabled) {
                    Log.d(TAG, "ACTION_MUTE diabaikan: isAutoSilentEnabled=false (kemungkinan stale alarm)")
                    AdzanLogger.log(context, AdzanLogger.Event.MUTE_SKIPPED,
                        "ACTION_MUTE diabaikan karena Auto Silent dimatikan (stale alarm?)")
                    return
                }
                // === FIX: Idempotency guard — cegah double/triple MUTE dari stale alarm overlap ===
                // Jika device sudah di-mute oleh app, skip untuk menghindari log noise
                // dan panggilan DND system yang redundan.
                val alreadyMuted = prefs.getBoolean("IS_MUTED_BY_APP_DND", false) ||
                                   prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false)
                if (alreadyMuted) {
                    Log.d(TAG, "ACTION_MUTE diabaikan: perangkat sudah di-mute oleh app (duplikat alarm?)")
                    AdzanLogger.log(context, AdzanLogger.Event.MUTE_SKIPPED,
                        "ACTION_MUTE diabaikan: sudah dalam state muted (stale/duplikat alarm)")
                    return
                }
                Log.d(TAG, "ACTION_MUTE: Mengeksekusi Silent Mode")
                AdzanLogger.log(context, AdzanLogger.Event.MUTE_EXECUTED, "ACTION_MUTE diterima")
                executeMute(context)
            }

            "ACTION_UNMUTE" -> {
                Log.d(TAG, "ACTION_UNMUTE: Jadwal Unmute tiba")
                val settings = SettingsManager(context)

                // === FIX: Idempotency guard — cegah double unmute ===
                // Jika perangkat sudah tidak dalam state muted oleh app, skip.
                // Ini mencegah alarm UNMUTE duplikat (dari reschedule) melakukan
                // restore DND yang tidak perlu.
                val wasMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP_DND", false) ||
                                    prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false)
                if (!wasMutedByApp) {
                    Log.d(TAG, "ACTION_UNMUTE diabaikan: perangkat tidak dalam state muted oleh app")
                    AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_EXECUTED,
                        "ACTION_UNMUTE diabaikan: perangkat sudah normal (tidak di-mute oleh app)")
                    return
                }

                if (settings.isAdzanPlaying) {
                    Log.w(TAG, "Adzan masih bermain! Menahan perintah unmute sampai Adzan selesai.")
                    AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_DEFERRED, "Unmute ditahan karena adzan masih bermain")
                    prefs.edit().putBoolean("PENDING_UNMUTE", true).apply()
                    return
                }

                // FIX: Cek apakah masih di dalam silent window sholat LAIN.
                // Tanpa ini, jika window Maghrib (after=35min) overlap dengan window Isya (before=30min),
                // UNMUTE Maghrib akan mencabut DND padahal window Isya sudah aktif.
                if (isInsideAnySilentWindow(context, settings)) {
                    Log.d(TAG, "ACTION_UNMUTE ditahan: masih di dalam silent window sholat lain")
                    AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_DEFERRED,
                        "Unmute ditahan: masih di dalam silent window sholat lain")
                    return
                }

                AdzanLogger.log(context, AdzanLogger.Event.UNMUTE_EXECUTED, "ACTION_UNMUTE dieksekusi")
                executeUnmute(context)
            }
        }
    }

    /**
     * Mengaktifkan DND (Do Not Disturb) untuk auto-silent saat adzan.
     *
     * ⚠️ PERINGATAN KRITIS — BACA SEBELUM MENGUBAH!
     *
     * Method ini HARUS set NotificationPolicy dengan PRIORITY_CATEGORY_ALARMS
     * SEBELUM mengaktifkan INTERRUPTION_FILTER_PRIORITY. Tanpa ini:
     *
     *   → HyperOS/MIUI akan memblokir USAGE_ALARM audio saat DND aktif
     *   → Adzan terpause ~60 detik setelah screen off
     *   → Resume zombie saat screen on (meski waktu sholat sudah lewat)
     *
     * Bug ini sudah terjadi BERULANG KALI dan sangat sulit di-debug karena:
     *   1. Hanya terjadi saat screen off (tidak bisa di-debug via USB)
     *   2. Gejalanya mirip Doze mode (menyesatkan analisis)
     *   3. Hanya terjadi di OEM tertentu (Xiaomi/HyperOS/MIUI)
     *
     * JANGAN PERNAH menghapus blok notificationPolicy = alarmSafePolicy
     * di bawah ini! Itu adalah FIX UTAMA untuk bug ini.
     *
     * @see AdzanService (lihat class-level KDoc untuk konteks lengkap)
     */
    private fun executeMute(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "Izin DND tidak ada. Menggunakan fallback Vibrate biasa.")
            try {
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_VIBRATE) {
                    prefs.edit()
                        .putInt("PREF_PREV_RINGER", audioManager.ringerMode)
                        .putBoolean("IS_MUTED_BY_APP_RINGER", true)
                        .apply()
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal fallback mute: ${e.message}")
            }
            return
        }

        val isMutedByApp = prefs.getBoolean("IS_MUTED_BY_APP_DND", false)
        val currentFilter = notificationManager.currentInterruptionFilter

        if (!isMutedByApp) {
            // Simpan filter dan policy lama untuk restore saat unmute
            val editor = prefs.edit()
                .putInt("PREF_PREV_FILTER", currentFilter)
                .putBoolean("IS_MUTED_BY_APP_DND", true)
                .putLong("LAST_MUTE_TIMESTAMP", System.currentTimeMillis())
            try {
                val currentPolicy = notificationManager.notificationPolicy
                editor.putInt("PREF_PREV_POLICY_CATEGORIES", currentPolicy.priorityCategories)
            } catch (_: Exception) {}
            editor.apply()
        }

        try {
            // === KRUSIAL: Set policy DND yang EKSPLISIT mengizinkan ALARM & MEDIA ===
            // Tanpa ini, HyperOS/MIUI bisa memblokir USAGE_ALARM audio saat DND aktif,
            // menyebabkan adzan terpause ~60 detik setelah screen off.
            // Policy ini memastikan:
            //   - Alarm (adzan) tetap terdengar
            //   - Media tetap lewat
            //   - Panggilan, SMS, notifikasi lain tetap di-block (tujuan auto-silent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val alarmSafePolicy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                            NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA,
                    0, 0
                )
                notificationManager.notificationPolicy = alarmSafePolicy
            } else {
                @Suppress("DEPRECATION")
                val alarmSafePolicy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                    0, 0
                )
                notificationManager.notificationPolicy = alarmSafePolicy
            }

            if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                AdzanLogger.logMuteExecuted(context, "DND Priority (alarm-safe policy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengaktifkan DND: ${e.message}")
        }
    }

    private fun executeUnmute(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

        val wasMutedByAppDnd = prefs.getBoolean("IS_MUTED_BY_APP_DND", false)
        val wasMutedByAppRinger = prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false)

        // === FIX: Gunakan commit() (synchronous), bukan apply() (async) ===
        // Jika proses crash antara clear flags dan restore DND state,
        // apply() bisa menyebabkan flags sudah clear tapi DND belum di-restore
        // → ponsel terjebak di DND permanen. commit() menjamin persistence.
        prefs.edit()
            .putBoolean("IS_MUTED_BY_APP_DND", false)
            .putBoolean("IS_MUTED_BY_APP_RINGER", false)
            .putBoolean("PENDING_UNMUTE", false)
            .putBoolean("IS_TEST_MODE_ACTIVE", false)
            .commit()

        try {
            if (wasMutedByAppDnd && notificationManager.isNotificationPolicyAccessGranted) {
                val prevFilter = prefs.getInt("PREF_PREV_FILTER", NotificationManager.INTERRUPTION_FILTER_ALL)
                notificationManager.setInterruptionFilter(prevFilter)

                // Restore policy lama jika tersimpan
                val prevPolicyCategories = prefs.getInt("PREF_PREV_POLICY_CATEGORIES", -1)
                if (prevPolicyCategories >= 0) {
                    try {
                        val restoredPolicy = NotificationManager.Policy(prevPolicyCategories, 0, 0)
                        notificationManager.notificationPolicy = restoredPolicy
                    } catch (_: Exception) {}
                }

                Log.d(TAG, "Perangkat sukses dikembalikan dari DND ke state Normal.")
                AdzanLogger.logUnmuteExecuted(context, "DND -> Normal")
            }

            if (wasMutedByAppRinger) {
                val prevRinger = prefs.getInt("PREF_PREV_RINGER", AudioManager.RINGER_MODE_NORMAL)
                audioManager.ringerMode = prevRinger
                Log.d(TAG, "Perangkat sukses dikembalikan dari Fallback Vibrate ke state Normal.")
            }
        } catch (e: Exception) {
            Log.e(TAG, context.getString(R.string.log_error_unmute, e.message))
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

    /**
     * Cek apakah waktu sekarang masih berada di dalam silent window salah satu sholat.
     * Digunakan untuk mencegah UNMUTE sholat A membatalkan DND sholat B
     * ketika silent windows mereka overlap.
     */
    private fun isInsideAnySilentWindow(context: Context, settings: SettingsManager): Boolean {
        val latString = settings.latitude ?: return false
        val lonString = settings.longitude ?: return false

        return try {
            val lat = latString.toDouble()
            val lon = lonString.toDouble()
            val today = java.time.LocalDate.now()
            val prayerTimes = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, today)
            val now = System.currentTimeMillis()

            val prayerDates = listOf(
                Pair(prayerTimes.fajr, 1),
                Pair(prayerTimes.dhuhr, 2),
                Pair(prayerTimes.asr, 3),
                Pair(prayerTimes.maghrib, 4),
                Pair(prayerTimes.isha, 5),
            )

            for ((prayerTime, id) in prayerDates) {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
                cal.time = prayerTime
                val isFriday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY

                val beforeMillis = when (id) {
                    1 -> settings.fajrBefore
                    2 -> if (isFriday) settings.fridayBefore else settings.dhuhrBefore
                    3 -> settings.asrBefore
                    4 -> settings.maghribBefore
                    5 -> settings.ishaBefore
                    else -> 0
                } * 60 * 1000L

                val afterMillis = when (id) {
                    1 -> settings.fajrAfter
                    2 -> if (isFriday) settings.fridayAfter else settings.dhuhrAfter
                    3 -> settings.asrAfter
                    4 -> settings.maghribAfter
                    5 -> settings.ishaAfter
                    else -> 0
                } * 60 * 1000L

                val muteTime = prayerTime.time - beforeMillis
                val unmuteTime = prayerTime.time + afterMillis
                // Grace period sama dengan di IslamicWidgetProvider
                val muteGraceMs = 60_000L
                if (now >= (muteTime - muteGraceMs) && now < unmuteTime) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking silent window in SilentModeReceiver", e)
            // Jika gagal hitung, amankan: anggap masih di window agar tidak premature unmute
            true
        }
    }
}
