package com.cyberzilla.islamicwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.cyberzilla.islamicwidget.utils.PrayerTimes as IAstroPrayerTimes
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Scheduler mandiri untuk jadwal sholat, adzan, auto-silent, dan daily update.
 * Didesain agar bisa berjalan TANPA widget terpasang di home screen.
 *
 * Sebelumnya, semua scheduling hidup di IslamicWidgetProvider.onUpdate()
 * sehingga tanpa widget = tidak ada scheduling sama sekali.
 */
object PrayerScheduler {

    private const val TAG = "PrayerScheduler"

    // Request codes for daily update alarms (widget-independent)
    private const val RC_DAILY_MIDNIGHT = 50000
    private const val RC_DAILY_MAGHRIB = 50001
    private const val RC_DAILY_FAJR = 50002

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Entry point: pastikan semua scheduling aktif.
     * Aman dipanggil dari BootReceiver, MainActivity, DailyUpdateReceiver, dll.
     */
    fun ensureScheduled(context: Context) {
        scheduleAllPrayers(context, forceReschedule = false)
        scheduleDailyUpdate(context)
        updateIconIfNeeded(context)
    }

    /**
     * Membersihkan cache jadwal, memaksa scheduleAllPrayers() berjalan penuh
     * pada panggilan berikutnya.
     */
    fun clearScheduleCache(context: Context) {
        context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
            .edit()
            .remove("LAST_SCHEDULE_FINGERPRINT")
            .apply()
    }

    /**
     * Update launcher icon sesuai tanggal Hijri jika berubah.
     */
    fun updateIconIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
            val lastUpdatedIconDay = prefs.getInt("lastIconHijriDay", -1)
            val hijriDisplay = IslamicAppUtils.getCanonicalHijriDate(context)
            if (hijriDisplay.day != lastUpdatedIconDay) {
                IconHelper.updateLauncherIcon(context, hijriDisplay.day)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating icon", e)
        }
    }

    /**
     * Schedule daily update alarms (midnight, maghrib+5min, fajr+5min).
     * Berjalan INDEPENDEN dari widget — untuk icon update dan prayer rescheduling.
     */
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleDailyUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val settings = SettingsManager(context)
        val now = System.currentTimeMillis()

        // Always schedule midnight alarm
        val midnightIntent = Intent(context, DailyUpdateReceiver::class.java).apply {
            action = DailyUpdateReceiver.ACTION_DAILY_UPDATE
        }
        val midnightPI = PendingIntent.getBroadcast(
            context, RC_DAILY_MIDNIGHT, midnightIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 1)

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, midnightPI)
        } catch (e: SecurityException) {
            Log.e(TAG, "Gagal jadwalkan midnight alarm", e)
        }

        // Schedule maghrib+5min and fajr+5min if location available
        val latStr = settings.latitude
        val lonStr = settings.longitude
        if (latStr != null && lonStr != null) {
            try {
                val lat = latStr.toDouble()
                val lon = lonStr.toDouble()
                val today = LocalDate.now()
                val pt = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, today)

                // Maghrib+5min for hijri date change (if day starts at maghrib or auto hijri)
                if (settings.isDayStartAtMaghrib || settings.isAutoHijriOffset) {
                    val maghribTrigger = pt.maghrib.time + 5 * 60 * 1000L
                    if (now < maghribTrigger) {
                        val maghribIntent = Intent(context, DailyUpdateReceiver::class.java).apply {
                            action = DailyUpdateReceiver.ACTION_DAILY_UPDATE
                        }
                        val maghribPI = PendingIntent.getBroadcast(
                            context, RC_DAILY_MAGHRIB, maghribIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        try {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maghribTrigger, maghribPI)
                        } catch (_: SecurityException) {}
                    }
                }

                // Fajr+5min for recalculation after fajr
                val fajrTrigger = pt.fajr.time + 5 * 60 * 1000L
                if (now < fajrTrigger) {
                    val fajrIntent = Intent(context, DailyUpdateReceiver::class.java).apply {
                        action = DailyUpdateReceiver.ACTION_DAILY_UPDATE
                    }
                    val fajrPI = PendingIntent.getBroadcast(
                        context, RC_DAILY_FAJR, fajrIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fajrTrigger, fajrPI)
                    } catch (_: SecurityException) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling maghrib/fajr daily alarm", e)
            }
        }
    }

    // ========================================================================
    // PRAYER SCHEDULING (extracted from IslamicWidgetProvider)
    // ========================================================================

    fun scheduleAllPrayers(context: Context, forceReschedule: Boolean = false) {
        val settings = SettingsManager(context)
        val latString = settings.latitude
        val lonString = settings.longitude
        if (latString == null || lonString == null) return

        try {
            val lat = latString.toDouble()
            val lon = lonString.toDouble()
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            val todayPT = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, today)
            val tomorrowPT = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, tomorrow)
            val now = System.currentTimeMillis()

            // === OPTIMASI: Cek apakah jadwal sudah di-schedule dengan data identik ===
            val roundedLat = String.format(Locale.US, "%.3f", lat)
            val roundedLon = String.format(Locale.US, "%.3f", lon)
            val fingerprint = "$today|$roundedLat|$roundedLon|${todayPT.fajr.time / 1000}|${todayPT.dhuhr.time / 1000}|" +
                    "${todayPT.asr.time / 1000}|${todayPT.maghrib.time / 1000}|${todayPT.isha.time / 1000}"

            val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
            val lastFingerprint = prefs.getString("LAST_SCHEDULE_FINGERPRINT", null)

            // Rate-limit: jangan reschedule jika terakhir < 60 detik yang lalu
            val lastScheduleTime = prefs.getLong("LAST_SCHEDULE_TIME", 0L)
            val timeSinceLastSchedule = System.currentTimeMillis() - lastScheduleTime

            if (!forceReschedule && fingerprint == lastFingerprint) {
                if (!checkAndEnforceSilentWindow(context, todayPT, settings, now)) {
                    return
                }
                Log.d(TAG, "Missed alarm terdeteksi via enforce, force reschedule")
            }

            if (!forceReschedule && timeSinceLastSchedule < 60_000L && lastFingerprint != null) {
                if (!checkAndEnforceSilentWindow(context, todayPT, settings, now)) {
                    return
                }
                Log.d(TAG, "Missed alarm terdeteksi (rate-limited), force reschedule")
            }

            val prayerPairs = listOf(
                Triple(todayPT.fajr, tomorrowPT.fajr, 1),
                Triple(todayPT.dhuhr, tomorrowPT.dhuhr, 2),
                Triple(todayPT.asr, tomorrowPT.asr, 3),
                Triple(todayPT.maghrib, tomorrowPT.maghrib, 4),
                Triple(todayPT.isha, tomorrowPT.isha, 5),
            )

            // Diagnostic: log semua waktu sholat yang dihitung untuk hari ini
            val diagFmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            val prayerNames = arrayOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
            val diagSb = StringBuilder("scheduleAllPrayers [$today] loc=($lat,$lon) method=${settings.calculationMethod}\n")
            for ((i, pair) in prayerPairs.withIndex()) {
                diagSb.append("  ${prayerNames[i]}: ${diagFmt.format(pair.first)} (epoch=${pair.first.time})\n")
            }
            AdzanLogger.log(context, AdzanLogger.Event.ALARM_SCHEDULED, diagSb.toString().trim())

            var isInsideAnySilentWindow = false

            for ((todayTime, tomorrowTime, id) in prayerPairs) {
                val beforeMillis = getBeforeMillis(settings, id, todayTime)
                val muteTimeToday = todayTime.time - beforeMillis
                val unmuteTimeToday = todayTime.time + getAfterMillis(settings, id, todayTime)

                cancelExistingAlarms(context, id)

                // FIX: Grace period 60s
                val muteGraceMs = 60_000L
                if (now >= (muteTimeToday - muteGraceMs) && now < unmuteTimeToday) {
                    isInsideAnySilentWindow = true
                    scheduleSilentMode(context, todayTime, id, settings)
                } else if (now >= unmuteTimeToday) {
                    scheduleSilentMode(context, tomorrowTime, id, settings)
                } else {
                    scheduleSilentMode(context, todayTime, id, settings)
                }
            }

            // Simpan fingerprint dan timestamp
            prefs.edit()
                .putString("LAST_SCHEDULE_FINGERPRINT", fingerprint)
                .putLong("LAST_SCHEDULE_TIME", System.currentTimeMillis())
                .apply()

            val currentlyMutedDnd = prefs.getBoolean("IS_MUTED_BY_APP_DND", false)
            val currentlyMutedRinger = prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false)
            val isTestMode = prefs.getBoolean("IS_TEST_MODE_ACTIVE", false)

            if (isInsideAnySilentWindow) {
                if (!(currentlyMutedDnd || currentlyMutedRinger) && settings.isAutoSilentEnabled && !isTestMode) {
                    val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
                    context.sendBroadcast(muteIntent)
                }
                else if (currentlyMutedDnd && settings.isAutoSilentEnabled && !isTestMode) {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        if (nm.isNotificationPolicyAccessGranted &&
                            nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
                            Log.d(TAG, "DND DESYNC (full path): flag=muted tapi system=FILTER_ALL. Re-applying DND.")
                            prefs.edit().putBoolean("IS_MUTED_BY_APP_DND", false).commit()
                            val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
                            context.sendBroadcast(muteIntent)
                        }
                    } catch (_: Exception) {}
                }
            } else {
                val lastMuteTs = prefs.getLong("LAST_MUTE_TIMESTAMP", 0L)
                val muteCooldownMs = 5 * 60 * 1000L
                val isMuteCooldownActive = (System.currentTimeMillis() - lastMuteTs) < muteCooldownMs
                if ((currentlyMutedDnd || currentlyMutedRinger) && !isTestMode && !settings.isAdzanPlaying && !isMuteCooldownActive) {
                    val unmuteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
                    context.sendBroadcast(unmuteIntent)
                } else if (isMuteCooldownActive && (currentlyMutedDnd || currentlyMutedRinger)) {
                    Log.d(TAG, "Corrective UNMUTE ditahan: mute cooldown aktif (${(System.currentTimeMillis() - lastMuteTs)/1000}s sejak mute)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error menjadwalkan sholat", e)
        }
    }

    /**
     * Lightweight check: hanya cek apakah saat ini berada di dalam silent window
     * dan enforce mute/unmute state, TANPA re-schedule alarm.
     *
     * @return true jika alarm missed terdeteksi → perlu reschedule penuh.
     */
    fun checkAndEnforceSilentWindow(
        context: Context,
        prayerTimes: IAstroPrayerTimes,
        settings: SettingsManager,
        now: Long
    ): Boolean {
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        val isTestMode = prefs.getBoolean("IS_TEST_MODE_ACTIVE", false)
        val currentlyMutedDnd = prefs.getBoolean("IS_MUTED_BY_APP_DND", false)
        val currentlyMutedRinger = prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false)

        val prayerDates = listOf(
            Pair(prayerTimes.fajr, 1),
            Pair(prayerTimes.dhuhr, 2),
            Pair(prayerTimes.asr, 3),
            Pair(prayerTimes.maghrib, 4),
            Pair(prayerTimes.isha, 5),
        )

        var isInsideAnySilentWindow = false
        for ((prayerTime, id) in prayerDates) {
            val beforeMillis = getBeforeMillis(settings, id, prayerTime)
            val afterMillis = getAfterMillis(settings, id, prayerTime)

            val muteTime = prayerTime.time - beforeMillis
            val unmuteTime = prayerTime.time + afterMillis
            val muteGraceMs = 60_000L
            if (now >= (muteTime - muteGraceMs) && now < unmuteTime) {
                isInsideAnySilentWindow = true
                break
            }
        }

        if (isInsideAnySilentWindow) {
            if (!(currentlyMutedDnd || currentlyMutedRinger) && settings.isAutoSilentEnabled && !isTestMode) {
                val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
                context.sendBroadcast(muteIntent)
                return true
            }

            if ((currentlyMutedDnd) && settings.isAutoSilentEnabled && !isTestMode) {
                try {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (nm.isNotificationPolicyAccessGranted &&
                        nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
                        Log.d(TAG, "DND DESYNC terdeteksi: flag=muted tapi system=FILTER_ALL. Re-applying DND.")
                        AdzanLogger.log(context, AdzanLogger.Event.MUTE_EXECUTED,
                            "DND desync: flag muted tapi system off, re-apply MUTE")
                        prefs.edit().putBoolean("IS_MUTED_BY_APP_DND", false).commit()
                        val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
                        context.sendBroadcast(muteIntent)
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking DND desync", e)
                }
            }
        } else {
            val lastMuteTs = prefs.getLong("LAST_MUTE_TIMESTAMP", 0L)
            val muteCooldownMs = 5 * 60 * 1000L
            val isMuteCooldownActive = (System.currentTimeMillis() - lastMuteTs) < muteCooldownMs
            if ((currentlyMutedDnd || currentlyMutedRinger) && !isTestMode && !settings.isAdzanPlaying && !isMuteCooldownActive) {
                val unmuteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
                context.sendBroadcast(unmuteIntent)
            } else if (isMuteCooldownActive && (currentlyMutedDnd || currentlyMutedRinger)) {
                Log.d(TAG, "Corrective UNMUTE (enforce) ditahan: mute cooldown aktif (${(System.currentTimeMillis() - lastMuteTs)/1000}s)")
            }
        }

        return false
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    fun getBeforeMillis(settings: SettingsManager, requestCodeId: Int, prayerTime: Date): Long {
        val cal = java.util.Calendar.getInstance(TimeZone.getDefault())
        cal.time = prayerTime
        val isFriday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY

        return when (requestCodeId) {
            1 -> settings.fajrBefore
            2 -> if (isFriday) settings.fridayBefore else settings.dhuhrBefore
            3 -> settings.asrBefore
            4 -> settings.maghribBefore
            5 -> settings.ishaBefore
            else -> 0
        } * 60 * 1000L
    }

    private fun getAfterMillis(settings: SettingsManager, id: Int, prayerTime: Date): Long {
        val cal = java.util.Calendar.getInstance(TimeZone.getDefault())
        cal.time = prayerTime
        val isFriday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY

        return when (id) {
            1 -> settings.fajrAfter
            2 -> if (isFriday) settings.fridayAfter else settings.dhuhrAfter
            3 -> settings.asrAfter
            4 -> settings.maghribAfter
            5 -> settings.ishaAfter
            else -> 0
        } * 60 * 1000L
    }

    private fun cancelExistingAlarms(context: Context, requestCodeId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val rcMute = 10000 + requestCodeId
        val rcUnmute = 20000 + requestCodeId
        val rcAdzan = 30000 + requestCodeId
        val rcWakeUp = 40000 + requestCodeId

        val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
        val unmuteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
        val adzanIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_PLAY_ADZAN" }
        val wakeUpIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_WAKE_UP" }

        try {
            PendingIntent.getBroadcast(context, rcMute, muteIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager.cancel(it); it.cancel()
            }
            PendingIntent.getBroadcast(context, rcUnmute, unmuteIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager.cancel(it); it.cancel()
            }
            PendingIntent.getBroadcast(context, rcAdzan, adzanIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager.cancel(it); it.cancel()
            }
            PendingIntent.getBroadcast(context, rcWakeUp, wakeUpIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager.cancel(it); it.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error membatalkan alarm", e)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleSilentMode(context: Context, prayerTime: Date, requestCodeId: Int, settings: SettingsManager) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = java.util.Calendar.getInstance(TimeZone.getDefault())
        cal.time = prayerTime
        val isFriday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY
        val isJumat = isFriday && requestCodeId == 2
        val now = Date().time

        val RC_MUTE = 10000 + requestCodeId
        val RC_UNMUTE = 20000 + requestCodeId
        val RC_ADZAN = 30000 + requestCodeId
        val RC_WAKEUP = 40000 + requestCodeId

        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (prefs.getBoolean("IS_MUTED_BY_APP_RINGER", false) && audioMgr.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            prefs.edit().putBoolean("IS_MUTED_BY_APP_RINGER", false).apply()
        }

        // FIX: Reconcile DND flag dengan state aktual sistem.
        if (prefs.getBoolean("IS_MUTED_BY_APP_DND", false)) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
                    prefs.edit().putBoolean("IS_MUTED_BY_APP_DND", false).apply()
                    Log.d(TAG, "Reconciled stale IS_MUTED_BY_APP_DND flag (DND sudah tidak aktif)")
                }
            } catch (_: Exception) {}
        }

        // Grace period 2 menit
        val adzanGracePeriod = 120_000L
        if (settings.isAdzanEnabledForPrayer(requestCodeId) && now <= prayerTime.time + adzanGracePeriod && !isJumat && !settings.isAdzanPlaying) {
            val adzanIntent = Intent(context, SilentModeReceiver::class.java).apply {
                action = "ACTION_PLAY_ADZAN"
                putExtra("IS_SUBUH", requestCodeId == 1)
                putExtra("PRAYER_ID", requestCodeId)
                putExtra("PRAYER_TIME_MILLIS", prayerTime.time)
            }
            val adzanPendingIntent = PendingIntent.getBroadcast(context, RC_ADZAN, adzanIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmInfo = AlarmManager.AlarmClockInfo(prayerTime.time, adzanPendingIntent)
                    alarmManager.setAlarmClock(alarmInfo, adzanPendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, prayerTime.time, adzanPendingIntent)
                }
                AdzanLogger.logScheduled(context, requestCodeId, prayerTime.time, "ADZAN")
            } catch (e: SecurityException) {
                Log.e(TAG, "Gagal menjadwalkan alarm adzan", e)
                AdzanLogger.log(context, AdzanLogger.Event.ADZAN_ERROR, "SecurityException saat menjadwalkan adzan ID=$requestCodeId: ${e.message}")
            }

            // === PRE-ADZAN WAKE-UP ===
            val wakeUpTimeMillis = prayerTime.time - 5_000L
            if (now < wakeUpTimeMillis) {
                try {
                    val wakeUpIntent = Intent(context, SilentModeReceiver::class.java).apply {
                        action = "ACTION_WAKE_UP"
                        putExtra("PRAYER_ID", requestCodeId)
                    }
                    val wakeUpPendingIntent = PendingIntent.getBroadcast(context, RC_WAKEUP, wakeUpIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val wakeAlarmInfo = AlarmManager.AlarmClockInfo(wakeUpTimeMillis, wakeUpPendingIntent)
                        alarmManager.setAlarmClock(wakeAlarmInfo, wakeUpPendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeUpTimeMillis, wakeUpPendingIntent)
                    }
                    AdzanLogger.logScheduled(context, requestCodeId, wakeUpTimeMillis, "WAKE_UP")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Gagal menjadwalkan wake-up alarm", e)
                }
            }
        }

        if (!settings.isAutoSilentEnabled) return

        val silentBeforeMillis: Long
        val silentAfterMillis: Long

        when (requestCodeId) {
            1 -> { silentBeforeMillis = settings.fajrBefore * 60 * 1000L; silentAfterMillis = settings.fajrAfter * 60 * 1000L }
            2 -> {
                if (isFriday) { silentBeforeMillis = settings.fridayBefore * 60 * 1000L; silentAfterMillis = settings.fridayAfter * 60 * 1000L }
                else { silentBeforeMillis = settings.dhuhrBefore * 60 * 1000L; silentAfterMillis = settings.dhuhrAfter * 60 * 1000L }
            }
            3 -> { silentBeforeMillis = settings.asrBefore * 60 * 1000L; silentAfterMillis = settings.asrAfter * 60 * 1000L }
            4 -> { silentBeforeMillis = settings.maghribBefore * 60 * 1000L; silentAfterMillis = settings.maghribAfter * 60 * 1000L }
            5 -> { silentBeforeMillis = settings.ishaBefore * 60 * 1000L; silentAfterMillis = settings.ishaAfter * 60 * 1000L }
            else -> return
        }

        val muteTimeMillis = prayerTime.time - silentBeforeMillis
        val unmuteTimeMillis = prayerTime.time + silentAfterMillis

        if (now > unmuteTimeMillis) return

        val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
        val mutePendingIntent = PendingIntent.getBroadcast(context, RC_MUTE, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val unmuteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
        val unmutePendingIntent = PendingIntent.getBroadcast(context, RC_UNMUTE, unmuteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            if (now <= muteTimeMillis) {
                val muteAlarmInfo = AlarmManager.AlarmClockInfo(muteTimeMillis, mutePendingIntent)
                alarmManager.setAlarmClock(muteAlarmInfo, mutePendingIntent)
                AdzanLogger.logScheduled(context, requestCodeId, muteTimeMillis, "MUTE")
            } else {
                alarmManager.cancel(mutePendingIntent)
                mutePendingIntent.cancel()
            }

            if (now < unmuteTimeMillis) {
                val unmuteAlarmInfo = AlarmManager.AlarmClockInfo(unmuteTimeMillis, unmutePendingIntent)
                alarmManager.setAlarmClock(unmuteAlarmInfo, unmutePendingIntent)
                AdzanLogger.logScheduled(context, requestCodeId, unmuteTimeMillis, "UNMUTE")
            } else {
                alarmManager.cancel(unmutePendingIntent)
                unmutePendingIntent.cancel()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException menjadwalkan silent mode", e)
        }
    }
}
