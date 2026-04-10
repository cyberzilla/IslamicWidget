package com.cyberzilla.islamicwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.SunnahTimes
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class IslamicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "IslamicWidget"
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.applicationContext.resources.displayMetrics
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.cyberzilla.islamicwidget.ACTION_UPDATE_ADZAN_STATE") {
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val settings = SettingsManager(context)

            val fsClock = settings.fontSizeClock.toFloat()
            val fsDate = settings.fontSizeDate.toFloat()
            val fsPrayer = settings.fontSizePrayer.toFloat()
            val fsAdd = settings.fontSizeAdditional.toFloat()

            val fsInfoTitle = fsPrayer + 4f
            val fsInfoSub = fsAdd + 1f

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_islamic)
                views.setDisplayedChild(R.id.master_flipper, if (settings.isAdzanPlaying) 1 else 0)

                if (!settings.isAdzanPlaying) {
                    views.setTextViewTextSize(R.id.clock_widget, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsClock))
                    views.setTextViewTextSize(R.id.tv_gregorian_date, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsDate - 2f))
                    views.setTextViewTextSize(R.id.tv_hijri_date, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsDate))

                    val textViewsToResize = listOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
                    for (id in textViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsPrayer - 2f)) }

                    val timeViewsToResize = listOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)
                    for (id in timeViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsPrayer)) }

                    val additionalTextIds = listOf(
                        R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2
                    )
                    for (id in additionalTextIds) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd)) }
                } else {
                    views.setTextViewTextSize(R.id.tv_info_adzan_1, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
                    views.setTextViewTextSize(R.id.tv_info_adzan_2, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
                    views.setTextViewTextSize(R.id.tv_info_adzan_3, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
                    views.setTextViewTextSize(R.id.tv_info_sub, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoSub))
                }

                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        UpdateHelper.checkForUpdates(context)
        scheduleAllPrayers(context)

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun cancelExistingAlarms(context: Context, requestCodeId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val rcMute = 10000 + requestCodeId
        val rcUnmute = 20000 + requestCodeId
        val rcAdzan = 30000 + requestCodeId
        
        val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { 
            action = "ACTION_MUTE" 
        }
        val unmuteIntent = Intent(context, SilentModeReceiver::class.java).apply { 
            action = "ACTION_UNMUTE" 
        }
        val adzanIntent = Intent(context, SilentModeReceiver::class.java).apply { 
            action = "ACTION_PLAY_ADZAN" 
        }
        
        try {
            PendingIntent.getBroadcast(
                context, 
                rcMute, 
                muteIntent, 
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "Cancelled existing mute alarm for prayer ID: $requestCodeId")
            }
            
            PendingIntent.getBroadcast(
                context, 
                rcUnmute, 
                unmuteIntent, 
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "Cancelled existing unmute alarm for prayer ID: $requestCodeId")
            }
            
            PendingIntent.getBroadcast(
                context, 
                rcAdzan, 
                adzanIntent, 
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "Cancelled existing adzan alarm for prayer ID: $requestCodeId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarms for prayer ID: $requestCodeId", e)
        }
    }

    private fun getBeforeMillis(context: Context, requestCodeId: Int): Long {
        val settings = SettingsManager(context)
        val cal = java.util.Calendar.getInstance(TimeZone.getDefault())
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

    private fun scheduleAllPrayers(context: Context) {
        val settings = SettingsManager(context)
        val latString = settings.latitude
        val lonString = settings.longitude
        if (latString == null || lonString == null) {
            Log.w(TAG, "Location not set, skipping prayer scheduling")
            return
        }

        try {
            val lat = latString.toDouble()
            val lon = lonString.toDouble()
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            val todayPT = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, today)
            val tomorrowPT = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, tomorrow)
            val now = System.currentTimeMillis()

            fun getAfterMillis(id: Int): Long {
                val isFriday = today.dayOfWeek == java.time.DayOfWeek.FRIDAY
                return when (id) {
                    1 -> settings.fajrAfter
                    2 -> if (isFriday) settings.fridayAfter else settings.dhuhrAfter
                    3 -> settings.asrAfter
                    4 -> settings.maghribAfter
                    5 -> settings.ishaAfter
                    else -> 0
                } * 60 * 1000L
            }

            val prayerPairs = listOf(
                Triple(todayPT.fajr.asDate(), tomorrowPT.fajr.asDate(), 1),
                Triple(todayPT.dhuhr.asDate(), tomorrowPT.dhuhr.asDate(), 2),
                Triple(todayPT.asr.asDate(), tomorrowPT.asr.asDate(), 3),
                Triple(todayPT.maghrib.asDate(), tomorrowPT.maghrib.asDate(), 4),
                Triple(todayPT.isha.asDate(), tomorrowPT.isha.asDate(), 5),
            )

            for ((todayTime, tomorrowTime, id) in prayerPairs) {
                val beforeMillis = getBeforeMillis(context, id)
                val muteTimeToday = todayTime.time - beforeMillis
                val unmuteTimeToday = todayTime.time + getAfterMillis(id)
                
                Log.d(TAG, "Prayer ID $id - Today: ${Date(todayTime.time)}, Mute: ${Date(muteTimeToday)}, Unmute: ${Date(unmuteTimeToday)}, Now: ${Date(now)}")
                
                // Batalkan semua alarm yang ada untuk prayer ID ini
                cancelExistingAlarms(context, id)
                
                if (now >= muteTimeToday) {
                    // Waktu mute hari ini sudah lewat
                    if (now < unmuteTimeToday) {
                        // Masih dalam periode silent, trigger mute langsung
                        Log.d(TAG, "Prayer ID $id: In silent period, triggering mute immediately")
                        val muteIntent = Intent(context, SilentModeReceiver::class.java).apply {
                            action = "ACTION_MUTE"
                        }
                        context.sendBroadcast(muteIntent)
                    }
                    // Jadwalkan untuk besok
                    Log.d(TAG, "Prayer ID $id: Scheduling for tomorrow")
                    scheduleSilentMode(context, tomorrowTime, id, settings)
                } else {
                    // Jadwalkan untuk hari ini
                    Log.d(TAG, "Prayer ID $id: Scheduling for today")
                    scheduleSilentMode(context, todayTime, id, settings)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling prayers", e)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val settings = SettingsManager(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val isPortrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        var currentHeightDp = if (isPortrait) options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        if (currentHeightDp <= 0) currentHeightDp = 200

        val layoutId = if (currentHeightDp < 165) R.layout.widget_islamic_horizontal else R.layout.widget_islamic
        val views = RemoteViews(context.packageName, layoutId)

        val selectedLocale = Locale.forLanguageTag(settings.languageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(selectedLocale)
        val localizedContext = context.createConfigurationContext(config)

        var widthDp = if (isPortrait) options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        var heightDp = if (isPortrait) options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) else options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        if (widthDp <= 0) widthDp = 300
        if (heightDp <= 0) heightDp = 200

        val density = context.resources.displayMetrics.density
        var widthPx = (widthDp * density).toInt()
        var heightPx = (heightDp * density).toInt()
        var radiusPx = settings.widgetBgRadius * density

        val maxDimen = 600f
        if (widthPx > maxDimen || heightPx > maxDimen) {
            val scale = maxDimen / maxOf(widthPx, heightPx).toFloat()
            widthPx = (widthPx * scale).toInt()
            heightPx = (heightPx * scale).toInt()
            radiusPx *= scale
        }

        if (widthPx <= 0) widthPx = 1
        if (heightPx <= 0) heightPx = 1

        val bgColor = try { Color.parseColor(settings.widgetBgColor) } catch (e: Exception) { Color.parseColor("#00000000") }
        val bgBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bgBitmap)
        val paint = Paint().apply { isAntiAlias = true; color = bgColor }
        val rectF = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
        views.setImageViewBitmap(R.id.widget_bg, bgBitmap)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        try { bgBitmap.recycle() } catch (e: Exception) {}

        val today = LocalDate.now()
        var hijriDate = HijrahDate.from(today)
        var totalHijriOffset = settings.hijriOffset.toLong()

        val latString = settings.latitude
        val lonString = settings.longitude
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"

        val txtSunrise = localizedContext.getString(R.string.sunrise)
        val txtLastThird = localizedContext.getString(R.string.last_third)
        val txtQibla = localizedContext.getString(R.string.qibla)

        val fsClock = settings.fontSizeClock.toFloat()
        val fsDate = settings.fontSizeDate.toFloat()
        val fsPrayer = settings.fontSizePrayer.toFloat()
        val fsAdd = settings.fontSizeAdditional.toFloat()

        val fsInfoTitle = fsPrayer + 4f
        val fsInfoSub = fsAdd + 1f

        if (settings.isAdzanPlaying) {
            views.setDisplayedChild(R.id.master_flipper, 1)

            views.setTextViewTextSize(R.id.tv_info_adzan_1, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
            views.setTextViewTextSize(R.id.tv_info_adzan_2, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
            views.setTextViewTextSize(R.id.tv_info_adzan_3, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
            views.setTextViewTextSize(R.id.tv_info_sub, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoSub))

            views.setTextViewText(R.id.tv_info_adzan_1, localizedContext.getString(R.string.info_adzan_1))
            views.setTextViewText(R.id.tv_info_adzan_2, localizedContext.getString(R.string.info_adzan_2))
            views.setTextViewText(R.id.tv_info_adzan_3, localizedContext.getString(R.string.info_adzan_3))
            views.setTextViewText(R.id.tv_info_sub, localizedContext.getString(R.string.info_adzan_sub))
        } else {
            views.setDisplayedChild(R.id.master_flipper, 0)

            views.setViewVisibility(R.id.container_clock, if (settings.showClock) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.container_date, if (settings.showDate) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.container_prayer, if (settings.showPrayer) View.VISIBLE else View.GONE)

            val isFriday = today.dayOfWeek == DayOfWeek.FRIDAY

            views.setTextViewText(R.id.label_fajr, localizedContext.getString(R.string.fajr))
            views.setTextViewText(R.id.label_dhuhr, if (isFriday) localizedContext.getString(R.string.friday) else localizedContext.getString(R.string.dhuhr))
            views.setTextViewText(R.id.label_asr, localizedContext.getString(R.string.asr))
            views.setTextViewText(R.id.label_maghrib, localizedContext.getString(R.string.maghrib))
            views.setTextViewText(R.id.label_isha, localizedContext.getString(R.string.isha))

            views.setTextViewTextSize(R.id.clock_widget, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsClock))
            views.setTextViewTextSize(R.id.tv_gregorian_date, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsDate - 2f))
            views.setTextViewTextSize(R.id.tv_hijri_date, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsDate))

            val textViewsToResize = listOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
            for (id in textViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsPrayer - 2f)) }

            val timeViewsToResize = listOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)
            for (id in timeViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsPrayer)) }

            val textColor = try { Color.parseColor(settings.widgetTextColor) } catch (e: Exception) { Color.WHITE }
            views.setTextColor(R.id.clock_widget, textColor)
            views.setTextColor(R.id.tv_gregorian_date, textColor)
            views.setTextColor(R.id.tv_hijri_date, textColor)

            val additionalTextIds = listOf(
                R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2
            )
            for (id in additionalTextIds) {
                views.setTextColor(id, textColor)
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
            }

            if (is24Hour) {
                views.setCharSequence(R.id.clock_widget, "setFormat24Hour", "HH:mm")
                views.setCharSequence(R.id.clock_widget, "setFormat12Hour", "HH:mm")
            } else {
                if (settings.languageCode == "ar") {
                    val isAm = java.util.Calendar.getInstance().get(java.util.Calendar.AM_PM) == java.util.Calendar.AM
                    val amPmStr = if (isAm) "ص" else "م"
                    views.setCharSequence(R.id.clock_widget, "setFormat12Hour", "hh:mm '$amPmStr'")
                    views.setCharSequence(R.id.clock_widget, "setFormat24Hour", "hh:mm '$amPmStr'")
                    scheduleAmPmUpdate(context, appWidgetId)
                } else {
                    views.setCharSequence(R.id.clock_widget, "setFormat12Hour", "hh:mm a")
                    views.setCharSequence(R.id.clock_widget, "setFormat24Hour", "hh:mm a")
                }
            }

            val compassIntent = Intent(context, CompassActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val compassPendingIntent = PendingIntent.getActivity(
                context, appWidgetId, compassIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tv_qibla, compassPendingIntent)

            if (latString != null && lonString != null) {
                try {
                    val lat = latString.toDouble()
                    val lon = lonString.toDouble()

                    val prayerTimes = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, today)
                    val sunnahTimes = SunnahTimes(prayerTimes)
                    val qibla = Qibla(Coordinates(lat, lon))

                    val currentTime = Date()
                    var isAfterMaghrib = false
                    if (settings.isDayStartAtMaghrib) {
                        val maghribTime = prayerTimes.maghrib.asDate()
                        if (currentTime.after(maghribTime)) {
                            totalHijriOffset += 1L
                            isAfterMaghrib = true
                        }
                    }
                    val isBeforeFajr = currentTime.before(prayerTimes.fajr.asDate())

                    val timeFormatter = SimpleDateFormat(timePattern, selectedLocale)
                    timeFormatter.timeZone = TimeZone.getDefault()

                    views.setTextViewText(R.id.tv_fajr_time, timeFormatter.format(prayerTimes.fajr.asDate()))
                    views.setTextViewText(R.id.tv_dhuhr_time, timeFormatter.format(prayerTimes.dhuhr.asDate()))
                    views.setTextViewText(R.id.tv_asr_time, timeFormatter.format(prayerTimes.asr.asDate()))
                    views.setTextViewText(R.id.tv_maghrib_time, timeFormatter.format(prayerTimes.maghrib.asDate()))
                    views.setTextViewText(R.id.tv_isha_time, timeFormatter.format(prayerTimes.isha.asDate()))

                    views.setTextViewText(R.id.tv_sunrise, "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise.asDate())}")
                    views.setTextViewText(R.id.tv_last_third, "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight.asDate())}")
                    views.setTextViewText(R.id.tv_qibla, String.format(selectedLocale, "%s: %.1f°", txtQibla, qibla.direction))

                    // --- FITUR HIGHLIGHT NEXT SHOLAT ---
                    val now = Date()
                    val highlightColor = Color.parseColor("#FFC107")
                    val defaultColor = textColor

                    val prayerDates = arrayOf(
                        prayerTimes.fajr.asDate(),
                        prayerTimes.dhuhr.asDate(),
                        prayerTimes.asr.asDate(),
                        prayerTimes.maghrib.asDate(),
                        prayerTimes.isha.asDate()
                    )

                    val labels = arrayOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
                    val times = arrayOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)

                    var nextPrayerIndex = -1
                    for (i in prayerDates.indices) {
                        if (now.before(prayerDates[i])) {
                            nextPrayerIndex = i
                            break
                        }
                    }
                    if (nextPrayerIndex == -1) nextPrayerIndex = 0

                    for (i in labels.indices) {
                        val colorToUse = if (i == nextPrayerIndex) highlightColor else defaultColor
                        views.setTextColor(labels[i], colorToUse)
                        views.setTextColor(times[i], colorToUse)
                    }
                    // -----------------------------------

                    scheduleDateChangeUpdate(context, prayerTimes.maghrib.asDate(), prayerTimes.fajr.asDate(), appWidgetId, settings.isDayStartAtMaghrib)

                    if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)

                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersionName = packageInfo.versionName ?: "1.0"
                    val isUpdateAvailable = UpdateHelper.isVersionNewer(currentVersionName, settings.latestVersionName)
                    val sunnahInfo = IslamicAppUtils.getSunnahFastingInfo(localizedContext, hijriDate, today, isAfterMaghrib, isBeforeFajr)

                    if (settings.showAdditional) {
                        views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                        views.setViewVisibility(R.id.container_additional_flipper, View.VISIBLE)

                        views.removeAllViews(R.id.container_additional_flipper)

                        // 1. Info Normal Default
                        val normalView = RemoteViews(context.packageName, R.layout.item_flipper_normal)
                        normalView.setTextViewText(R.id.tv_sunrise_flip, "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise.asDate())}")
                        normalView.setTextViewText(R.id.tv_last_third_flip, "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight.asDate())}")
                        normalView.setTextViewText(R.id.tv_qibla_flip, String.format(selectedLocale, "%s: %.1f°", txtQibla, qibla.direction))

                        val flipTextIds = listOf(R.id.tv_sunrise_flip, R.id.tv_last_third_flip, R.id.tv_qibla_flip, R.id.tv_divider_1_flip, R.id.tv_divider_2_flip)
                        for (id in flipTextIds) {
                            normalView.setTextColor(id, textColor)
                            normalView.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                        }
                        normalView.setOnClickPendingIntent(R.id.tv_qibla_flip, compassPendingIntent)
                        views.addView(R.id.container_additional_flipper, normalView)

                        // 2. FITUR QUOTES (Chunking otomatis)
                        try {
                            var fullQuote = "Barangsiapa yang menempuh suatu jalan untuk mencari ilmu, maka Allah akan mudahkan baginya jalan menuju surga. (HR. Muslim)"
                            try {
                                val quoteHelper = QuoteDatabaseHelper(context)
                                val quotePair = quoteHelper.getRandomQuote()

                                if (quotePair != null) {
                                    fullQuote = "${quotePair.first} - ${quotePair.second}"
                                }
                            } catch (e: Exception) {}

                            val maxLengthPerSlide = 55
                            val words = fullQuote.split(" ")
                            var currentSlideText = ""

                            for (word in words) {
                                if (currentSlideText.length + word.length + 1 <= maxLengthPerSlide) {
                                    currentSlideText += if (currentSlideText.isEmpty()) word else " $word"
                                } else {
                                    val quoteView = RemoteViews(context.packageName, R.layout.item_flipper_text)
                                    quoteView.setTextViewText(R.id.tv_item_text, currentSlideText)
                                    quoteView.setTextViewTextSize(R.id.tv_item_text, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                                    quoteView.setTextColor(R.id.tv_item_text, Color.parseColor("#81D4FA"))

                                    val nullIntent = PendingIntent.getActivity(context, 999, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                                    quoteView.setOnClickPendingIntent(R.id.tv_item_text, nullIntent)

                                    views.addView(R.id.container_additional_flipper, quoteView)
                                    currentSlideText = word
                                }
                            }

                            if (currentSlideText.isNotEmpty()) {
                                val quoteView = RemoteViews(context.packageName, R.layout.item_flipper_text)
                                quoteView.setTextViewText(R.id.tv_item_text, currentSlideText)
                                quoteView.setTextViewTextSize(R.id.tv_item_text, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                                quoteView.setTextColor(R.id.tv_item_text, Color.parseColor("#81D4FA"))

                                val nullIntent = PendingIntent.getActivity(context, 999, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                                quoteView.setOnClickPendingIntent(R.id.tv_item_text, nullIntent)

                                views.addView(R.id.container_additional_flipper, quoteView)
                            }
                        } catch (e: Exception) {}

                        // 3. Info Puasa Sunnah
                        if (sunnahInfo.isNotEmpty()) {
                            val sunnahView = RemoteViews(context.packageName, R.layout.item_flipper_text)
                            sunnahView.setTextViewText(R.id.tv_item_text, sunnahInfo)
                            sunnahView.setTextViewTextSize(R.id.tv_item_text, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                            sunnahView.setTextColor(R.id.tv_item_text, Color.parseColor("#FFC107"))

                            val nullIntent = PendingIntent.getActivity(context, 999, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            sunnahView.setOnClickPendingIntent(R.id.tv_item_text, nullIntent)
                            views.addView(R.id.container_additional_flipper, sunnahView)
                        }

                        // 4. Info Update
                        if (isUpdateAvailable) {
                            val updateMessage = localizedContext.getString(R.string.update_available_msg, settings.latestVersionName)
                            val updateView = RemoteViews(context.packageName, R.layout.item_flipper_text)
                            updateView.setTextViewText(R.id.tv_item_text, updateMessage)
                            updateView.setTextViewTextSize(R.id.tv_item_text, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                            updateView.setTextColor(R.id.tv_item_text, Color.parseColor("#4CAF50"))

                            val updateIntent = Intent(context, UpdateReceiver::class.java).apply {
                                action = "ACTION_START_UPDATE_DOWNLOAD"
                                putExtra("APK_URL", settings.apkDownloadUrl)
                            }
                            val pendingUpdateIntent = PendingIntent.getBroadcast(
                                context,
                                appWidgetId + 9000,
                                updateIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            updateView.setOnClickPendingIntent(R.id.tv_item_text, pendingUpdateIntent)
                            views.addView(R.id.container_additional_flipper, updateView)
                        }
                    } else {
                        views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                        views.setViewVisibility(R.id.container_additional_flipper, View.GONE)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget", e)
                }
            } else {
                if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)
            }
        }

        try {
            val hDayOfMonth = hijriDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
            val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
            val lastUpdatedIconDay = prefs.getInt("lastIconHijriDay", -1)

            if (hDayOfMonth != lastUpdatedIconDay) {
                IconHelper.updateLauncherIcon(context, hDayOfMonth)
                prefs.edit().putInt("lastIconHijriDay", hDayOfMonth).apply()
            }
        } catch (e: Exception) {}

        val masehiFormatted = IslamicAppUtils.formatCustomDate(settings.dateFormat, today, selectedLocale)
        val hijriFormatted = IslamicAppUtils.formatCustomDate(settings.hijriFormat, hijriDate, selectedLocale)

        views.setTextViewText(R.id.tv_gregorian_date, masehiFormatted)
        views.setTextViewText(R.id.tv_hijri_date, hijriFormatted)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleDateChangeUpdate(context: Context, maghribTime: Date, fajrTime: Date, appWidgetId: Int, isDayStartAtMaghrib: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SilentModeReceiver::class.java).apply {
            action = "ACTION_UPDATE_WIDGETS_BROADCAST"
        }

        if (isDayStartAtMaghrib) {
            val pendingIntentMaghrib = PendingIntent.getBroadcast(
                context, appWidgetId + 5000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = maghribTime.time + 1000L
            if (Date().time < triggerTime) {
                try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerTime, pendingIntentMaghrib) } catch (e: SecurityException) {}
            }
        }

        val pendingIntentFajr = PendingIntent.getBroadcast(
            context, appWidgetId + 7000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fajrTriggerTime = fajrTime.time + 1000L
        if (Date().time < fajrTriggerTime) {
            try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, fajrTriggerTime, pendingIntentFajr) } catch (e: SecurityException) {}
        }

        val pendingIntentMidnight = PendingIntent.getBroadcast(
            context, appWidgetId + 6000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 1)

        try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pendingIntentMidnight) } catch (e: SecurityException) {}
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAmPmUpdate(context: Context, appWidgetId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, IslamicWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance()
        if (calendar.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 12)
        } else {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        }
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {}
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleSilentMode(context: Context, prayerTime: Date, requestCodeId: Int, settings: SettingsManager) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = java.util.Calendar.getInstance(TimeZone.getDefault())
        cal.time = prayerTime
        val isFriday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY
        val isJumat = isFriday && requestCodeId == 2

        val now = Date().time

        val RC_MUTE = 10000 + requestCodeId
        val RC_UNMUTE = 20000 + requestCodeId
        val RC_ADZAN = 30000 + requestCodeId

        // =======================================================================
        // PROACTIVE FLAG SYNC - Dari versi user yang brilian
        // =======================================================================
        val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
        val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (prefs.getBoolean("IS_MUTED_BY_APP", false) &&
            audioMgr.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            Log.d(TAG, "Proactive flag sync: Resetting stale mute flag for prayer ID: $requestCodeId")
            prefs.edit().putBoolean("IS_MUTED_BY_APP", false).apply()
        }

        if (settings.isAdzanAudioEnabled && now <= prayerTime.time && !isJumat) {
            val adzanIntent = Intent(context, SilentModeReceiver::class.java).apply {
                action = "ACTION_PLAY_ADZAN"
                putExtra("IS_SUBUH", requestCodeId == 1)
                putExtra("PRAYER_ID", requestCodeId)
            }
            val adzanPendingIntent = PendingIntent.getBroadcast(context, RC_ADZAN, adzanIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            try {
                val alarmInfo = AlarmManager.AlarmClockInfo(prayerTime.time, adzanPendingIntent)
                alarmManager.setAlarmClock(alarmInfo, adzanPendingIntent)
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to schedule adzan alarm", e)
            }
        }

        if (!settings.isAutoSilentEnabled) {
            Log.d(TAG, "Auto silent disabled, skipping for prayer ID: $requestCodeId")
            return
        }

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
            else -> {
                Log.w(TAG, "Invalid request code ID: $requestCodeId")
                return
            }
        }

        val muteTimeMillis = prayerTime.time - silentBeforeMillis
        val unmuteTimeMillis = prayerTime.time + silentAfterMillis

        Log.d(TAG, "Scheduling silent mode for prayer $requestCodeId: " +
                "prayer at ${Date(prayerTime.time)}, mute at ${Date(muteTimeMillis)}, unmute at ${Date(unmuteTimeMillis)}")

        if (now > unmuteTimeMillis) {
            Log.d(TAG, "Unmute time already passed, skipping for prayer ID: $requestCodeId")
            return
        }

        val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
        val mutePendingIntent = PendingIntent.getBroadcast(context, RC_MUTE, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val unmuteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
        val unmutePendingIntent = PendingIntent.getBroadcast(context, RC_UNMUTE, unmuteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            if (now <= muteTimeMillis) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, muteTimeMillis, mutePendingIntent)
                Log.d(TAG, "Scheduled mute alarm for: ${Date(muteTimeMillis)}")
            } else if (now in (muteTimeMillis + 1)..unmuteTimeMillis) {
                // =======================================================================
                // CRITICAL FIX - Dari analisis saya
                // =======================================================================
                Log.d(TAG, "Currently in silent period, cancelling stale alarm and triggering mute immediately for prayer ID: $requestCodeId")
                alarmManager.cancel(mutePendingIntent)
                context.sendBroadcast(muteIntent)
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unmuteTimeMillis, unmutePendingIntent)
            Log.d(TAG, "Scheduled unmute alarm for: ${Date(unmuteTimeMillis)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception scheduling silent mode", e)
        }
    }
}