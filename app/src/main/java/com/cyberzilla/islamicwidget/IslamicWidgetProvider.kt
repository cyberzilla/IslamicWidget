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
import android.os.Bundle
import android.text.format.DateFormat
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
                        R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2,
                        R.id.tv_sunrise_flip, R.id.tv_last_third_flip, R.id.tv_qibla_flip, R.id.tv_divider_1_flip, R.id.tv_divider_2_flip
                    )
                    for (id in additionalTextIds) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd)) }

                    views.setTextViewTextSize(R.id.tv_sunnah_reminder_flip, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
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
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
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
            for (id in textViewsToResize) { views.setTextColor(id, textColor) }
            for (id in timeViewsToResize) { views.setTextColor(id, textColor) }

            val additionalTextIds = listOf(
                R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2,
                R.id.tv_sunrise_flip, R.id.tv_last_third_flip, R.id.tv_qibla_flip, R.id.tv_divider_1_flip, R.id.tv_divider_2_flip
            )
            for (id in additionalTextIds) {
                views.setTextColor(id, textColor)
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
            }

            views.setTextViewTextSize(R.id.tv_sunnah_reminder_flip, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))

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
            views.setOnClickPendingIntent(R.id.tv_qibla_flip, compassPendingIntent)

            if (latString != null && lonString != null) {
                try {
                    val lat = latString.toDouble()
                    val lon = lonString.toDouble()

                    val prayerTimes = IslamicAppUtils.calculatePrayerTimes(lat, lon, settings.calculationMethod, today)
                    val sunnahTimes = SunnahTimes(prayerTimes)
                    val qibla = Qibla(Coordinates(lat, lon))

                    var isAfterMaghrib = false
                    if (settings.isDayStartAtMaghrib) {
                        val currentTime = Date()
                        val maghribTime = prayerTimes.maghrib.asDate()
                        if (currentTime.after(maghribTime)) {
                            totalHijriOffset += 1L
                            isAfterMaghrib = true
                        }
                    }

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

                    views.setTextViewText(R.id.tv_sunrise_flip, "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise.asDate())}")
                    views.setTextViewText(R.id.tv_last_third_flip, "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight.asDate())}")
                    views.setTextViewText(R.id.tv_qibla_flip, String.format(selectedLocale, "%s: %.1f°", txtQibla, qibla.direction))

                    scheduleDateChangeUpdate(context, prayerTimes.maghrib.asDate(), appWidgetId, settings.isDayStartAtMaghrib)

                    scheduleSilentMode(context, prayerTimes.fajr.asDate(), 1, settings)
                    scheduleSilentMode(context, prayerTimes.dhuhr.asDate(), 2, settings)
                    scheduleSilentMode(context, prayerTimes.asr.asDate(), 3, settings)
                    scheduleSilentMode(context, prayerTimes.maghrib.asDate(), 4, settings)
                    scheduleSilentMode(context, prayerTimes.isha.asDate(), 5, settings)

                    if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)

                    val sunnahInfo = IslamicAppUtils.getSunnahFastingInfo(localizedContext, hijriDate, today, isAfterMaghrib)

                    if (settings.showAdditional) {
                        if (sunnahInfo.isNotEmpty()) {
                            views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                            views.setViewVisibility(R.id.container_additional_flipper, View.VISIBLE)
                            views.setTextViewText(R.id.tv_sunnah_reminder_flip, sunnahInfo)
                        } else {
                            views.setViewVisibility(R.id.container_additional_normal, View.VISIBLE)
                            views.setViewVisibility(R.id.container_additional_flipper, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                        views.setViewVisibility(R.id.container_additional_flipper, View.GONE)
                    }

                } catch (e: Exception) {
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
    private fun scheduleDateChangeUpdate(context: Context, maghribTime: Date, appWidgetId: Int, isDayStartAtMaghrib: Boolean) {
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

        val cal = java.util.Calendar.getInstance()
        cal.time = prayerTime
        val isFriday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY
        val isJumat = isFriday && requestCodeId == 2

        val now = Date().time

        val RC_MUTE = 10000 + requestCodeId
        val RC_UNMUTE = 20000 + requestCodeId
        val RC_ADZAN = 30000 + requestCodeId

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
            } catch (e: SecurityException) {}
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
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, muteTimeMillis, mutePendingIntent)
            } else if (now in (muteTimeMillis + 1)..unmuteTimeMillis) {
                context.sendBroadcast(muteIntent)
            }

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unmuteTimeMillis, unmutePendingIntent)
        } catch (e: SecurityException) {}
    }
}