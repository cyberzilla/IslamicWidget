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
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.SunnahTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalTime::class)
class IslamicWidgetProvider : AppWidgetProvider() {

    private fun Instant.asDate() = Date(toEpochMilliseconds())

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.cyberzilla.islamicwidget.ACTION_UPDATE_ADZAN_STATE") {
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val settings = SettingsManager(context)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_islamic)
                views.setDisplayedChild(R.id.master_flipper, if (settings.isAdzanPlaying) 1 else 0)
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

    private fun formatCustomDate(inputStr: String, dateObj: TemporalAccessor, defaultLocale: Locale): String {
        val regex = Regex("([a-zA-Z0-9-]+)\\{([^}]+)\\}")

        return try {
            if (regex.containsMatchIn(inputStr)) {
                regex.replace(inputStr) { matchResult ->
                    val localeTag = matchResult.groupValues[1]
                    val pattern = matchResult.groupValues[2]

                    val locale = try { Locale.forLanguageTag(localeTag) } catch (e: Exception) { defaultLocale }
                    val formatter = DateTimeFormatter.ofPattern(pattern, locale)
                    var formattedText = formatter.format(dateObj)

                    if (localeTag.lowercase().startsWith("ar")) {
                        val arabicDigits = arrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
                        val builder = StringBuilder()
                        for (char in formattedText) {
                            if (char in '0'..'9') {
                                builder.append(arabicDigits[char - '0'])
                            } else {
                                builder.append(char)
                            }
                        }
                        formattedText = builder.toString()
                    }
                    formattedText
                }
            } else {
                val formatter = DateTimeFormatter.ofPattern(inputStr, defaultLocale)
                formatter.format(dateObj)
            }
        } catch (e: Exception) {
            val fallbackFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", defaultLocale)
            fallbackFormatter.format(dateObj)
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

            views.setTextViewTextSize(R.id.tv_info_adzan_1, TypedValue.COMPLEX_UNIT_DIP, fsInfoTitle)
            views.setTextViewTextSize(R.id.tv_info_adzan_2, TypedValue.COMPLEX_UNIT_DIP, fsInfoTitle)
            views.setTextViewTextSize(R.id.tv_info_adzan_3, TypedValue.COMPLEX_UNIT_DIP, fsInfoTitle)
            views.setTextViewTextSize(R.id.tv_info_sub, TypedValue.COMPLEX_UNIT_DIP, fsInfoSub)

            views.setTextViewText(R.id.tv_info_adzan_1, localizedContext.getString(R.string.info_adzan_1))
            views.setTextViewText(R.id.tv_info_adzan_2, localizedContext.getString(R.string.info_adzan_2))
            views.setTextViewText(R.id.tv_info_adzan_3, localizedContext.getString(R.string.info_adzan_3))
            views.setTextViewText(R.id.tv_info_sub, localizedContext.getString(R.string.info_adzan_sub))
        } else {
            views.setDisplayedChild(R.id.master_flipper, 0)

            views.setViewVisibility(R.id.container_clock, if (settings.showClock) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.container_date, if (settings.showDate) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.container_prayer, if (settings.showPrayer) View.VISIBLE else View.GONE)

            views.setTextViewText(R.id.label_fajr, localizedContext.getString(R.string.fajr))
            views.setTextViewText(R.id.label_dhuhr, localizedContext.getString(R.string.dhuhr))
            views.setTextViewText(R.id.label_asr, localizedContext.getString(R.string.asr))
            views.setTextViewText(R.id.label_maghrib, localizedContext.getString(R.string.maghrib))
            views.setTextViewText(R.id.label_isha, localizedContext.getString(R.string.isha))

            views.setTextViewTextSize(R.id.clock_widget, TypedValue.COMPLEX_UNIT_DIP, fsClock)
            views.setTextViewTextSize(R.id.tv_gregorian_date, TypedValue.COMPLEX_UNIT_DIP, fsDate - 2f)
            views.setTextViewTextSize(R.id.tv_hijri_date, TypedValue.COMPLEX_UNIT_DIP, fsDate)

            val textViewsToResize = listOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
            for (id in textViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, fsPrayer - 2f) }

            val timeViewsToResize = listOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)
            for (id in timeViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, fsPrayer) }

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
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, fsAdd)
            }

            views.setTextViewTextSize(R.id.tv_sunnah_reminder_flip, TypedValue.COMPLEX_UNIT_DIP, fsAdd)

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

            if (latString != null && lonString != null) {
                try {
                    val latitude = latString.toDouble()
                    val longitude = lonString.toDouble()
                    val coordinates = Coordinates(latitude, longitude)
                    val dateComponents = DateComponents(today.year, today.monthValue, today.dayOfMonth)

                    val method = when (settings.calculationMethod) {
                        "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
                        "EGYPTIAN" -> CalculationMethod.EGYPTIAN
                        "KARACHI" -> CalculationMethod.KARACHI
                        "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA
                        "DUBAI" -> CalculationMethod.DUBAI
                        "QATAR" -> CalculationMethod.QATAR
                        "KUWAIT" -> CalculationMethod.KUWAIT
                        "MOON_SIGHTING_COMMITTEE" -> CalculationMethod.MOON_SIGHTING_COMMITTEE
                        "SINGAPORE" -> CalculationMethod.SINGAPORE
                        else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
                    }

                    val prayerTimes = PrayerTimes(coordinates, dateComponents, method.parameters)
                    val sunnahTimes = SunnahTimes(prayerTimes)
                    val qibla = Qibla(coordinates)

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

                    scheduleSilentMode(context, localizedContext, prayerTimes.fajr.asDate(), 1, settings)
                    scheduleSilentMode(context, localizedContext, prayerTimes.dhuhr.asDate(), 2, settings)
                    scheduleSilentMode(context, localizedContext, prayerTimes.asr.asDate(), 3, settings)
                    scheduleSilentMode(context, localizedContext, prayerTimes.maghrib.asDate(), 4, settings)
                    scheduleSilentMode(context, localizedContext, prayerTimes.isha.asDate(), 5, settings)

                    if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)
                    val hijriDay = hijriDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
                    val hijriMonth = hijriDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)

                    val islamicDayOfWeek = if (isAfterMaghrib) today.plusDays(1).dayOfWeek else today.dayOfWeek
                    val sunnahInfo = java.lang.StringBuilder()

                    if (hijriMonth == 1 && (hijriDay == 9 || hijriDay == 10)) {
                        sunnahInfo.append(localizedContext.getString(R.string.sunnah_muharram))
                    }
                    if (hijriMonth == 12 && hijriDay in 1..9) {
                        if (sunnahInfo.isNotEmpty()) sunnahInfo.append(" • ")
                        if (hijriDay == 9) {
                            sunnahInfo.append(localizedContext.getString(R.string.sunnah_arafah))
                        } else {
                            sunnahInfo.append(localizedContext.getString(R.string.sunnah_dzulhijjah))
                        }
                    }
                    if (hijriDay in 13..15) {
                        if (sunnahInfo.isNotEmpty()) sunnahInfo.append(" • ")
                        sunnahInfo.append(localizedContext.getString(R.string.sunnah_ayyamul_bidh))
                    }

                    if (islamicDayOfWeek == java.time.DayOfWeek.MONDAY || islamicDayOfWeek == java.time.DayOfWeek.THURSDAY) {
                        if (sunnahInfo.isNotEmpty()) sunnahInfo.append(" • ")
                        sunnahInfo.append(localizedContext.getString(R.string.sunnah_monday_thursday))
                    }
                    if (islamicDayOfWeek == java.time.DayOfWeek.FRIDAY) {
                        if (sunnahInfo.isNotEmpty()) sunnahInfo.append(" • ")
                        sunnahInfo.append(localizedContext.getString(R.string.sunnah_friday))
                    }

                    if (settings.showAdditional) {
                        if (sunnahInfo.isNotEmpty()) {
                            views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                            views.setViewVisibility(R.id.container_additional_flipper, View.VISIBLE)
                            views.setTextViewText(R.id.tv_sunnah_reminder_flip, sunnahInfo.toString())
                        } else {
                            views.setViewVisibility(R.id.container_additional_normal, View.VISIBLE)
                            views.setViewVisibility(R.id.container_additional_flipper, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                        views.setViewVisibility(R.id.container_additional_flipper, View.GONE)
                    }

                } catch (e: Exception) {
                    // Biarkan jika gagal
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

        val masehiFormatted = formatCustomDate(settings.dateFormat, today, selectedLocale)
        val hijriFormatted = formatCustomDate(settings.hijriFormat, hijriDate, selectedLocale)

        views.setTextViewText(R.id.tv_gregorian_date, masehiFormatted)
        views.setTextViewText(R.id.tv_hijri_date, hijriFormatted)

        appWidgetManager.updateAppWidget(appWidgetId, views)
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
    private fun scheduleSilentMode(context: Context, localizedContext: Context, prayerTime: Date, requestCodeId: Int, settings: SettingsManager) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val isFriday = LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY

        if (settings.isAdzanAudioEnabled && Date().time <= prayerTime.time) {
            val adzanIntent = Intent(context, SilentModeReceiver::class.java).apply {
                action = "ACTION_PLAY_ADZAN"
                putExtra("IS_SUBUH", requestCodeId == 1)
                putExtra("PRAYER_ID", requestCodeId)
            }
            val adzanPendingIntent = PendingIntent.getBroadcast(context, requestCodeId + 200, adzanIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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

        if (Date().time > unmuteTimeMillis) return

        val muteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
        val mutePendingIntent = PendingIntent.getBroadcast(context, requestCodeId, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val unmuteIntent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
        val unmutePendingIntent = PendingIntent.getBroadcast(context, requestCodeId + 100, unmuteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            if (Date().time <= muteTimeMillis) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, muteTimeMillis, mutePendingIntent)
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unmuteTimeMillis, unmutePendingIntent)
        } catch (e: SecurityException) {}
    }
}