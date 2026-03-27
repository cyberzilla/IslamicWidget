package com.cyberzilla.islamicwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalTime::class)
class IslamicWidgetProvider : AppWidgetProvider() {

    private fun Instant.asDate() = Date(toEpochMilliseconds())

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
        val views = RemoteViews(context.packageName, R.layout.widget_islamic)
        val settings = SettingsManager(context)

        val selectedLocale = Locale(settings.languageCode)
        Locale.setDefault(selectedLocale)
        val config = context.resources.configuration
        config.setLocale(selectedLocale)
        val localizedContext = context.createConfigurationContext(config)

        views.setTextViewText(R.id.label_fajr, localizedContext.getString(R.string.fajr))
        views.setTextViewText(R.id.label_dhuhr, localizedContext.getString(R.string.dhuhr))
        views.setTextViewText(R.id.label_asr, localizedContext.getString(R.string.asr))
        views.setTextViewText(R.id.label_maghrib, localizedContext.getString(R.string.maghrib))
        views.setTextViewText(R.id.label_isha, localizedContext.getString(R.string.isha))

        val baseFontSize = settings.widgetFontSize.toFloat()

        // Ukuran Dinamis (Responsive Scaling Control)
        views.setTextViewTextSize(R.id.clock_widget, TypedValue.COMPLEX_UNIT_SP, baseFontSize + 22f)
        views.setTextViewTextSize(R.id.tv_gregorian_date, TypedValue.COMPLEX_UNIT_SP, baseFontSize - 2f)
        views.setTextViewTextSize(R.id.tv_hijri_date, TypedValue.COMPLEX_UNIT_SP, baseFontSize)

        val textViewsToResize = listOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
        for (id in textViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, baseFontSize - 3f) }

        val timeViewsToResize = listOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)
        for (id in timeViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, baseFontSize - 1f) }

        val textColor = try { Color.parseColor(settings.widgetTextColor) } catch (e: Exception) { Color.WHITE }
        views.setTextColor(R.id.clock_widget, textColor)
        views.setTextColor(R.id.tv_gregorian_date, textColor)
        views.setTextColor(R.id.tv_hijri_date, textColor)
        for (id in textViewsToResize) { views.setTextColor(id, textColor) }
        for (id in timeViewsToResize) { views.setTextColor(id, textColor) }

        val opacityTextColor = try { Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor)) } catch (e: Exception) { Color.LTGRAY }
        views.setTextColor(R.id.tv_sunrise, opacityTextColor)
        views.setTextColor(R.id.tv_last_third, opacityTextColor)
        views.setTextColor(R.id.tv_qibla, textColor)
        views.setTextColor(R.id.tv_divider_1, opacityTextColor)
        views.setTextColor(R.id.tv_divider_2, opacityTextColor)

        val bgColor = try { Color.parseColor(settings.widgetBgColor) } catch (e: Exception) { Color.parseColor("#B3000000") }
        views.setInt(R.id.widget_bg, "setColorFilter", bgColor)

        val today = LocalDate.now()
        var hijriDate = HijrahDate.from(today)
        var totalHijriOffset = settings.hijriOffset.toLong()

        val latString = settings.latitude
        val lonString = settings.longitude

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

                if (settings.isDayStartAtMaghrib) {
                    val currentTime = Date()
                    val maghribTime = prayerTimes.maghrib.asDate()
                    if (currentTime.after(maghribTime)) totalHijriOffset += 1L
                }

                val timeFormatter = SimpleDateFormat("HH:mm", selectedLocale)
                timeFormatter.timeZone = TimeZone.getDefault()

                views.setTextViewText(R.id.tv_fajr_time, timeFormatter.format(prayerTimes.fajr.asDate()))
                views.setTextViewText(R.id.tv_dhuhr_time, timeFormatter.format(prayerTimes.dhuhr.asDate()))
                views.setTextViewText(R.id.tv_asr_time, timeFormatter.format(prayerTimes.asr.asDate()))
                views.setTextViewText(R.id.tv_maghrib_time, timeFormatter.format(prayerTimes.maghrib.asDate()))
                views.setTextViewText(R.id.tv_isha_time, timeFormatter.format(prayerTimes.isha.asDate()))

                val sunriseStr = timeFormatter.format(prayerTimes.sunrise.asDate())
                val lastThirdStr = timeFormatter.format(sunnahTimes.lastThirdOfTheNight.asDate())
                val qiblaStr = String.format(selectedLocale, "%.1f°", qibla.direction)

                views.setTextViewText(R.id.tv_sunrise, "Terbit: $sunriseStr")
                views.setTextViewText(R.id.tv_last_third, "1/3 Malam: $lastThirdStr")
                views.setTextViewText(R.id.tv_qibla, "Kiblat: $qiblaStr")

                scheduleSilentMode(context, prayerTimes.fajr.asDate(), 1, settings)
                scheduleSilentMode(context, prayerTimes.dhuhr.asDate(), 2, settings)
                scheduleSilentMode(context, prayerTimes.asr.asDate(), 3, settings)
                scheduleSilentMode(context, prayerTimes.maghrib.asDate(), 4, settings)
                scheduleSilentMode(context, prayerTimes.isha.asDate(), 5, settings)

            } catch (e: Exception) {
                // Biarkan jika gagal
            }
        }

        if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)

        val customPattern = settings.dateFormat
        val gregorianFormatter = try { DateTimeFormatter.ofPattern(customPattern, selectedLocale) } catch (e: Exception) { DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", selectedLocale) }
        val hijriFormatter = try { DateTimeFormatter.ofPattern(customPattern, selectedLocale) } catch (e: Exception) { DateTimeFormatter.ofPattern("dd MMMM yyyy", selectedLocale) }

        views.setTextViewText(R.id.tv_gregorian_date, today.format(gregorianFormatter))
        views.setTextViewText(R.id.tv_hijri_date, hijriDate.format(hijriFormatter) + " H")

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleSilentMode(context: Context, prayerTime: Date, requestCodeId: Int, settings: SettingsManager) {
        if (!settings.isAutoSilentEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val isFriday = LocalDate.now().dayOfWeek == DayOfWeek.FRIDAY

        val silentBeforeMillis: Long
        val silentAfterMillis: Long

        when (requestCodeId) {
            1 -> {
                silentBeforeMillis = settings.fajrBefore * 60 * 1000L
                silentAfterMillis = settings.fajrAfter * 60 * 1000L
            }
            2 -> {
                silentBeforeMillis = settings.dhuhrBefore * 60 * 1000L
                silentAfterMillis = if (isFriday) settings.dhuhrFriday * 60 * 1000L else settings.dhuhrAfter * 60 * 1000L
            }
            3 -> {
                silentBeforeMillis = settings.asrBefore * 60 * 1000L
                silentAfterMillis = settings.asrAfter * 60 * 1000L
            }
            4 -> {
                silentBeforeMillis = settings.maghribBefore * 60 * 1000L
                silentAfterMillis = settings.maghribAfter * 60 * 1000L
            }
            5 -> {
                silentBeforeMillis = settings.ishaBefore * 60 * 1000L
                silentAfterMillis = settings.ishaAfter * 60 * 1000L
            }
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
        } catch (e: SecurityException) {
            // Abaikan jika izin ditolak
        }
    }
}