package com.cyberzilla.islamicwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.cyberzilla.islamicwidget.utils.IslamicAstronomy
import com.cyberzilla.islamicwidget.utils.HilalCriteria
import com.cyberzilla.islamicwidget.utils.HolidayCalendar
import com.cyberzilla.islamicwidget.utils.PrayerTimes as IAstroPrayerTimes
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class IslamicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "IslamicWidget"
        private const val STATE_NORMAL = 0
        private const val STATE_LOADING = 1
        private const val STATE_ADZAN = 2

        /**
         * Membersihkan cache jadwal, memaksa scheduleAllPrayers() berjalan penuh
         * pada panggilan berikutnya.
         */
        fun clearScheduleCache(context: Context) {
            PrayerScheduler.clearScheduleCache(context)
        }
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

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_islamic)

                val targetState = if (settings.isAdzanPlaying) STATE_ADZAN else STATE_NORMAL
                views.setDisplayedChild(R.id.master_flipper, targetState)

                if (settings.isAdzanPlaying) {
                    applyFontSizesToAdzanMode(context, views, settings)
                } else {
                    applyFontSizesToNormalMode(context, views, settings)
                }

                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }

        if (intent.action == "com.cyberzilla.islamicwidget.ACTION_FORCE_REFRESH") {
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Cooldown: abaikan tap berulang dalam 3 detik untuk hemat resource
            val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
            val lastRefresh = prefs.getLong("LAST_FORCE_REFRESH", 0L)
            val now = System.currentTimeMillis()
            if (now - lastRefresh < 3_000L) return
            prefs.edit().putLong("LAST_FORCE_REFRESH", now).apply()

            // OPTIMASI: Tidak lagi clearScheduleCache() saat tap widget.
            // Tap hanya refresh tampilan, bukan reschedule alarm.
            // Reschedule hanya terjadi jika fingerprint berubah (lokasi/hari/setting).

            // Force check update dengan cooldown pendek (2 menit) agar tidak spam
            UpdateHelper.checkForUpdates(context, force = true)

            // Tampilkan skeleton loading
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_islamic)
                views.setDisplayedChild(R.id.master_flipper, STATE_LOADING)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }

            // FIX: goAsync() memberi tahu Android agar TIDAK mematikan proses
            // selama delay 600ms. Tanpa ini, Handler.postDelayed bisa tidak jalan
            // karena BroadcastReceiver lifecycle berakhir setelah onReceive() return.
            val pendingResult = goAsync()
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    onUpdate(context, appWidgetManager, appWidgetIds)
                } finally {
                    pendingResult.finish()
                }
            }, 600)
        }
    }

    private fun applyFontSizesToNormalMode(context: Context, views: RemoteViews, settings: SettingsManager) {
        val fsClock = settings.fontSizeClock.toFloat()
        val fsDate = settings.fontSizeDate.toFloat()
        val fsPrayer = settings.fontSizePrayer.toFloat()
        val fsAdd = settings.fontSizeAdditional.toFloat()

        views.setTextViewTextSize(R.id.clock_widget, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsClock))
        views.setTextViewTextSize(R.id.tv_gregorian_date, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsDate - 2f))
        views.setTextViewTextSize(R.id.tv_hijri_date, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsDate))

        val textViewsToResize = listOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
        for (id in textViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsPrayer - 2f)) }

        val timeViewsToResize = listOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)
        for (id in timeViewsToResize) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsPrayer)) }

        val additionalTextIds = listOf(R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2)
        for (id in additionalTextIds) { views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd)) }
    }

    private fun applyFontSizesToAdzanMode(context: Context, views: RemoteViews, settings: SettingsManager) {
        val fsPrayer = settings.fontSizePrayer.toFloat()
        val fsAdd = settings.fontSizeAdditional.toFloat()
        val fsInfoTitle = fsPrayer + 4f
        val fsInfoSub = fsAdd + 1f

        views.setTextViewTextSize(R.id.tv_info_adzan_1, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
        views.setTextViewTextSize(R.id.tv_info_adzan_2, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
        views.setTextViewTextSize(R.id.tv_info_adzan_3, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoTitle))
        views.setTextViewTextSize(R.id.tv_info_sub, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsInfoSub))
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        val views = RemoteViews(context.packageName, R.layout.widget_islamic)
        views.setDisplayedChild(R.id.master_flipper, STATE_LOADING)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)

        Handler(Looper.getMainLooper()).postDelayed({
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }, 300)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        UpdateHelper.checkForUpdates(context)
        AdzanLogger.log(context, AdzanLogger.Event.WIDGET_UPDATE, "onUpdate dipanggil untuk ${appWidgetIds.size} widget")
        PrayerScheduler.scheduleAllPrayers(context, forceReschedule = false)

        for (appWidgetId in appWidgetIds) {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                Log.e(TAG, "updateAppWidget gagal untuk widget $appWidgetId, lanjut ke widget berikutnya", e)
            }
        }

        // Ikut refresh Lunar Widget agar fase bulan selalu up-to-date
        // tanpa overhead alarm tambahan
        refreshLunarWidget(context, appWidgetManager)
    }

    private fun refreshLunarWidget(context: Context, appWidgetManager: AppWidgetManager) {
        try {
            val lunarComponent = ComponentName(context, LunarWidgetProvider::class.java)
            val lunarIds = appWidgetManager.getAppWidgetIds(lunarComponent)
            if (lunarIds.isNotEmpty()) {
                val intent = Intent(context, LunarWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, lunarIds)
                }
                context.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing lunar widget", e)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
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
        val latString = settings.latitude
        val lonString = settings.longitude

        // SINGLE SOURCE OF TRUTH: satu panggilan untuk semua kebutuhan tanggal Hijri
        val hijriDisplay = IslamicAppUtils.getCanonicalHijriDate(context)
        val hijriDate = hijriDisplay.hijrahDate

        var cachedPrayerTimes: IAstroPrayerTimes? = null
        var cachedLat = 0.0
        var cachedLon = 0.0

        if (latString != null && lonString != null) {
            try {
                cachedLat = latString.toDouble()
                cachedLon = lonString.toDouble()

                // OPTIMASI: Cache prayer times di SharedPreferences.
                // Hanya hitung ulang jika tanggal/lokasi/method berubah.
                val ptPrefs = context.getSharedPreferences("PrayerTimesCache", Context.MODE_PRIVATE)
                val roundedLat = String.format(java.util.Locale.US, "%.3f", cachedLat)
                val roundedLon = String.format(java.util.Locale.US, "%.3f", cachedLon)
                val ptCacheKey = "$today|$roundedLat|$roundedLon|${settings.calculationMethod}"
                val lastPTCacheKey = ptPrefs.getString("PT_CACHE_KEY", null)

                if (lastPTCacheKey == ptCacheKey && ptPrefs.contains("PT_FAJR")) {
                    // Baca dari cache — hemat kalkulasi astronomi
                    cachedPrayerTimes = IAstroPrayerTimes(
                        date = Date(ptPrefs.getLong("PT_DATE", 0)),
                        imsak = Date(ptPrefs.getLong("PT_IMSAK", 0)),
                        fajr = Date(ptPrefs.getLong("PT_FAJR", 0)),
                        sunrise = Date(ptPrefs.getLong("PT_SUNRISE", 0)),
                        dhuha = Date(ptPrefs.getLong("PT_DHUHA", 0)),
                        dhuhr = Date(ptPrefs.getLong("PT_DHUHR", 0)),
                        asr = Date(ptPrefs.getLong("PT_ASR", 0)),
                        maghrib = Date(ptPrefs.getLong("PT_MAGHRIB", 0)),
                        isha = Date(ptPrefs.getLong("PT_ISHA", 0))
                    )
                } else {
                    // Hitung baru dan simpan ke cache
                    cachedPrayerTimes = IslamicAppUtils.calculatePrayerTimes(cachedLat, cachedLon, settings.calculationMethod, today)
                    ptPrefs.edit()
                        .putString("PT_CACHE_KEY", ptCacheKey)
                        .putLong("PT_DATE", cachedPrayerTimes!!.date.time)
                        .putLong("PT_IMSAK", cachedPrayerTimes!!.imsak.time)
                        .putLong("PT_FAJR", cachedPrayerTimes!!.fajr.time)
                        .putLong("PT_SUNRISE", cachedPrayerTimes!!.sunrise.time)
                        .putLong("PT_DHUHA", cachedPrayerTimes!!.dhuha.time)
                        .putLong("PT_DHUHR", cachedPrayerTimes!!.dhuhr.time)
                        .putLong("PT_ASR", cachedPrayerTimes!!.asr.time)
                        .putLong("PT_MAGHRIB", cachedPrayerTimes!!.maghrib.time)
                        .putLong("PT_ISHA", cachedPrayerTimes!!.isha.time)
                        .apply()
                }
            } catch (e: Exception) {}
        }

        // === Holiday Detection ===
        val holidays = try {
            HolidayCalendar.getHolidaysForDate(localizedContext, today, hijriDate, settings.languageCode)
        } catch (_: Exception) { emptyList() }
        val isPublicHoliday = HolidayCalendar.isPublicHoliday(holidays)

        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"

        val txtSunrise = localizedContext.getString(R.string.sunrise)
        val txtLastThird = localizedContext.getString(R.string.last_third)
        val txtQibla = localizedContext.getString(R.string.qibla)

        applyFontSizesToNormalMode(context, views, settings)

        val fsAdd = settings.fontSizeAdditional.toFloat()

        val targetState = if (settings.isAdzanPlaying) STATE_ADZAN else STATE_NORMAL
        views.setDisplayedChild(R.id.master_flipper, targetState)

        if (settings.isAdzanPlaying) {
            applyFontSizesToAdzanMode(context, views, settings)

            views.setTextViewText(R.id.tv_info_adzan_1, localizedContext.getString(R.string.info_adzan_1))
            views.setTextViewText(R.id.tv_info_adzan_2, localizedContext.getString(R.string.info_adzan_2))
            views.setTextViewText(R.id.tv_info_adzan_3, localizedContext.getString(R.string.info_adzan_3))
            views.setTextViewText(R.id.tv_info_sub, localizedContext.getString(R.string.info_adzan_sub))
        } else {
            views.setViewVisibility(R.id.container_clock, if (settings.showClock) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.container_date, if (settings.showDate) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.container_prayer, if (settings.showPrayer) View.VISIBLE else View.GONE)

            val isFriday = today.dayOfWeek == DayOfWeek.FRIDAY

            views.setTextViewText(R.id.label_fajr, localizedContext.getString(R.string.fajr))
            views.setTextViewText(R.id.label_dhuhr, if (isFriday) localizedContext.getString(R.string.friday) else localizedContext.getString(R.string.dhuhr))
            views.setTextViewText(R.id.label_asr, localizedContext.getString(R.string.asr))
            views.setTextViewText(R.id.label_maghrib, localizedContext.getString(R.string.maghrib))
            views.setTextViewText(R.id.label_isha, localizedContext.getString(R.string.isha))

            val textColor = try { Color.parseColor(settings.widgetTextColor) } catch (e: Exception) { Color.WHITE }
            views.setTextColor(R.id.clock_widget, textColor)
            // Warna merah pada Gregorian saat hari libur (tanggal merah)
            val gregorianColor = if (isPublicHoliday) Color.parseColor("#EF5350") else textColor
            views.setTextColor(R.id.tv_gregorian_date, gregorianColor)
            views.setTextColor(R.id.tv_hijri_date, textColor)

            val additionalTextIds = listOf(R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2)
            for (id in additionalTextIds) {
                views.setTextColor(id, textColor)
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

            val compassIntent = Intent(context, CompassActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val compassPendingIntent = PendingIntent.getActivity(context, appWidgetId, compassIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.tv_qibla, compassPendingIntent)

            val forceRefreshIntent = Intent(context, IslamicWidgetProvider::class.java).apply {
                action = "com.cyberzilla.islamicwidget.ACTION_FORCE_REFRESH"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 9999, forceRefreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_bg, refreshPendingIntent)

            if (latString != null && lonString != null && cachedPrayerTimes != null) {
                try {
                    val prayerTimes = cachedPrayerTimes!!
                    val tomorrowPrayer = IslamicAppUtils.calculatePrayerTimes(cachedLat, cachedLon, settings.calculationMethod, today.plusDays(1))
                    val sunnahTimes = IslamicAstronomy.getSunnahTimes(prayerTimes, tomorrowPrayer)
                    val qiblaDegree = IslamicAstronomy.calculateQibla(cachedLat, cachedLon)

                    val currentTime = Date()
                    val maghribTime = prayerTimes.maghrib

                    if (isFriday && currentTime.after(maghribTime)) {
                        views.setTextViewText(R.id.label_dhuhr, localizedContext.getString(R.string.dhuhr))
                    }

                    var isAfterMaghrib = false
                    if (settings.isDayStartAtMaghrib || settings.isAutoHijriOffset) {
                        if (currentTime.after(maghribTime)) {
                            isAfterMaghrib = true
                        }
                    }
                    val isBeforeFajr = currentTime.before(prayerTimes.fajr)

                    val timeFormatter = SimpleDateFormat(timePattern, selectedLocale)
                    timeFormatter.timeZone = TimeZone.getDefault()

                    views.setTextViewText(R.id.tv_fajr_time, timeFormatter.format(prayerTimes.fajr))
                    views.setTextViewText(R.id.tv_dhuhr_time, timeFormatter.format(prayerTimes.dhuhr))
                    views.setTextViewText(R.id.tv_asr_time, timeFormatter.format(prayerTimes.asr))
                    views.setTextViewText(R.id.tv_maghrib_time, timeFormatter.format(prayerTimes.maghrib))
                    views.setTextViewText(R.id.tv_isha_time, timeFormatter.format(prayerTimes.isha))

                    // Diagnostic: log waktu sholat yang ditampilkan di widget vs epoch asli
                    val diagFmt2 = SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                    AdzanLogger.log(context, AdzanLogger.Event.WIDGET_UPDATE,
                        "Display: Subuh=${timeFormatter.format(prayerTimes.fajr)}(${diagFmt2.format(prayerTimes.fajr)}) " +
                        "Dzuhur=${timeFormatter.format(prayerTimes.dhuhr)}(${diagFmt2.format(prayerTimes.dhuhr)}) " +
                        "Ashar=${timeFormatter.format(prayerTimes.asr)}(${diagFmt2.format(prayerTimes.asr)}) " +
                        "loc=($cachedLat,$cachedLon)"
                    )

                    views.setTextViewText(R.id.tv_sunrise, "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise)}")
                    views.setTextViewText(R.id.tv_last_third, "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight)}")
                    views.setTextViewText(R.id.tv_qibla, String.format(selectedLocale, "%s: %.1f°", txtQibla, qiblaDegree))

                    val now = Date()
                    val nowMillis = now.time
                    val highlightColor = Color.parseColor("#FFC107")
                    val defaultColor = textColor

                    val prayerDates = arrayOf(prayerTimes.fajr, prayerTimes.dhuhr, prayerTimes.asr, prayerTimes.maghrib, prayerTimes.isha)
                    val prayerNames = arrayOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
                    val labels = arrayOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
                    val times = arrayOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)

                    // === DIAGNOSTIC HIGHLIGHT LOG ===
                    val diagTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", java.util.Locale.US)
                    diagTimeFmt.timeZone = TimeZone.getDefault()
                    val diagUtcFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
                    diagUtcFmt.timeZone = TimeZone.getTimeZone("UTC")

                    val sb = StringBuilder()
                    sb.append("=== HIGHLIGHT DEBUG ===\n")
                    sb.append("  TZ=${TimeZone.getDefault().id} offset=${TimeZone.getDefault().rawOffset/3600000}h\n")
                    sb.append("  now: millis=$nowMillis local=${diagTimeFmt.format(now)} utc=${diagUtcFmt.format(now)}\n")
                    sb.append("  prayerTimes.date: millis=${prayerTimes.date.time} local=${diagTimeFmt.format(prayerTimes.date)}\n")
                    sb.append("  calcMethod=${settings.calculationMethod} loc=($cachedLat,$cachedLon)\n")

                    var nextPrayerIndex = -1
                    for (i in prayerDates.indices) {
                        val pMillis = prayerDates[i].time
                        val diff = pMillis - nowMillis
                        val diffSec = diff / 1000
                        val beforeResult = now.before(prayerDates[i])
                        val millisCmp = nowMillis < pMillis
                        sb.append("  ${prayerNames[i]}: millis=$pMillis local=${diagTimeFmt.format(prayerDates[i])} diff=${diffSec}s before=$beforeResult millisCmp=$millisCmp\n")
                        if (nowMillis < pMillis && nextPrayerIndex == -1) {
                            nextPrayerIndex = i
                        }
                    }
                    // Fallback: setelah Isya lewat, highlight Subuh (sholat berikutnya besok)
                    if (nextPrayerIndex == -1) nextPrayerIndex = 0
                    sb.append("  RESULT: nextPrayerIndex=$nextPrayerIndex (${prayerNames[nextPrayerIndex]})")

                    AdzanLogger.log(context, AdzanLogger.Event.WIDGET_UPDATE, sb.toString())

                    for (i in labels.indices) {
                        val colorToUse = if (i == nextPrayerIndex) highlightColor else defaultColor
                        views.setTextColor(labels[i], colorToUse)
                        views.setTextColor(times[i], colorToUse)
                    }

                    // FIX: Juga update widget saat Maghrib ketika auto hijri offset aktif,
                    // karena auto koreksi menghitung hari sunset-to-sunset.
                    scheduleDateChangeUpdate(context, prayerTimes.maghrib, prayerTimes.fajr, appWidgetId, settings.isDayStartAtMaghrib || settings.isAutoHijriOffset)

                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersionName = packageInfo.versionName ?: "1.0"
                    val isUpdateAvailable = UpdateHelper.isVersionNewer(currentVersionName, settings.latestVersionName)
                    val sunnahInfo = IslamicAppUtils.getSunnahFastingInfo(localizedContext, hijriDate, today, isAfterMaghrib, isBeforeFajr)

                    if (settings.showAdditional) {
                        views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                        views.setViewVisibility(R.id.container_additional_flipper, View.VISIBLE)
                        views.removeAllViews(R.id.container_additional_flipper)

                        val normalView = RemoteViews(context.packageName, R.layout.item_flipper_normal)
                        normalView.setTextViewText(R.id.tv_sunrise_flip, "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise)}")
                        normalView.setTextViewText(R.id.tv_last_third_flip, "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight)}")
                        normalView.setTextViewText(R.id.tv_qibla_flip, String.format(selectedLocale, "%s: %.1f°", txtQibla, qiblaDegree))

                        val flipTextIds = listOf(R.id.tv_sunrise_flip, R.id.tv_last_third_flip, R.id.tv_qibla_flip, R.id.tv_divider_1_flip, R.id.tv_divider_2_flip)
                        for (id in flipTextIds) {
                            normalView.setTextColor(id, textColor)
                            normalView.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                        }
                        normalView.setOnClickPendingIntent(R.id.tv_qibla_flip, compassPendingIntent)
                        views.addView(R.id.container_additional_flipper, normalView)

                        try {
                            var fullQuote = "Barangsiapa yang menempuh suatu jalan untuk mencari ilmu, maka Allah akan mudahkan baginya jalan menuju surga. (HR. Muslim)"
                            try {
                                val quoteHelper = QuoteDatabaseHelper.getInstance(context)
                                val quotePair = quoteHelper.getRandomQuote()
                                if (quotePair != null) fullQuote = "${quotePair.first} - ${quotePair.second}"
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

                        if (sunnahInfo.isNotEmpty()) {
                            val sunnahView = RemoteViews(context.packageName, R.layout.item_flipper_text)
                            sunnahView.setTextViewText(R.id.tv_item_text, sunnahInfo)
                            sunnahView.setTextViewTextSize(R.id.tv_item_text, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                            sunnahView.setTextColor(R.id.tv_item_text, Color.parseColor("#FFC107"))
                            val nullIntent = PendingIntent.getActivity(context, 999, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            sunnahView.setOnClickPendingIntent(R.id.tv_item_text, nullIntent)
                            views.addView(R.id.container_additional_flipper, sunnahView)
                        }

                        // === Holiday info slide ===
                        val holidayText = HolidayCalendar.formatHolidayInfo(holidays)
                        if (holidayText.isNotEmpty()) {
                            val holidayView = RemoteViews(context.packageName, R.layout.item_flipper_text)
                            holidayView.setTextViewText(R.id.tv_item_text, holidayText)
                            holidayView.setTextViewTextSize(R.id.tv_item_text, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                            holidayView.setTextColor(R.id.tv_item_text, Color.parseColor("#E57373"))
                            val nullIntent = PendingIntent.getActivity(context, 999, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            holidayView.setOnClickPendingIntent(R.id.tv_item_text, nullIntent)
                            views.addView(R.id.container_additional_flipper, holidayView)
                        }

                        // Eclipse reminder (Sholat Gerhana)
                        val eclipseInfo = IslamicAppUtils.getEclipseReminderInfo(localizedContext, cachedLat, cachedLon)
                        if (eclipseInfo.isNotEmpty()) {
                            val eclipseView = RemoteViews(context.packageName, R.layout.item_flipper_text)
                            eclipseView.setTextViewText(R.id.tv_item_text, eclipseInfo)
                            eclipseView.setTextViewTextSize(R.id.tv_item_text, TypedValue.COMPLEX_UNIT_PX, dpToPx(context, fsAdd))
                            eclipseView.setTextColor(R.id.tv_item_text, Color.parseColor("#FF7043"))
                            val nullIntent = PendingIntent.getActivity(context, 999, Intent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            eclipseView.setOnClickPendingIntent(R.id.tv_item_text, nullIntent)
                            views.addView(R.id.container_additional_flipper, eclipseView)
                        }

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
                            val pendingUpdateIntent = PendingIntent.getBroadcast(context, appWidgetId + 9000, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            updateView.setOnClickPendingIntent(R.id.tv_item_text, pendingUpdateIntent)
                            views.addView(R.id.container_additional_flipper, updateView)
                        }
                    } else {
                        views.setViewVisibility(R.id.container_additional_normal, View.GONE)
                        views.setViewVisibility(R.id.container_additional_flipper, View.GONE)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error update widget", e)
                }
            }
        }

        try {
            val prefs = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
            val lastUpdatedIconDay = prefs.getInt("lastIconHijriDay", -1)

            if (hijriDisplay.day != lastUpdatedIconDay) {
                IconHelper.updateLauncherIcon(context, hijriDisplay.day)
            }
        } catch (e: Exception) {}

        val masehiFormatted = IslamicAppUtils.formatCustomDate(settings.dateFormat, today, selectedLocale)
        val hijriFormatted = IslamicAppUtils.formatHijriDisplay(settings.hijriFormat, hijriDisplay, selectedLocale)

        views.setTextViewText(R.id.tv_gregorian_date, masehiFormatted)
        views.setTextViewText(R.id.tv_hijri_date, hijriFormatted)

        appWidgetManager.updateAppWidget(appWidgetId, views)
        bgBitmap.recycle()
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleDateChangeUpdate(context: Context, maghribTime: Date, fajrTime: Date, appWidgetId: Int, isDayStartAtMaghrib: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SilentModeReceiver::class.java).apply { action = "ACTION_UPDATE_WIDGETS_BROADCAST" }

        if (isDayStartAtMaghrib) {
            val pendingIntentMaghrib = PendingIntent.getBroadcast(context, appWidgetId + 5000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // FIX: Sebelumnya +1 detik → bertabrakan dengan alarm ADZAN Maghrib.
            // Widget update men-trigger scheduleAllPrayers → cancelExistingAlarms
            // yang membatalkan alarm ADZAN yang pending (delayed oleh Doze).
            // +5 menit = setelah adzan selesai (~4 menit), aman dari race condition.
            val triggerTime = maghribTime.time + 5 * 60 * 1000L
            if (Date().time < triggerTime) {
                try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerTime, pendingIntentMaghrib) } catch (e: SecurityException) {}
            }
        }

        val pendingIntentFajr = PendingIntent.getBroadcast(context, appWidgetId + 7000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // FIX: Sama seperti Maghrib — Fajr+1s bertabrakan dengan alarm ADZAN Subuh.
        // Log 20260502: Subuh delay 107 detik karena cancelExistingAlarms dari widget update.
        val fajrTriggerTime = fajrTime.time + 5 * 60 * 1000L
        if (Date().time < fajrTriggerTime) {
            try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, fajrTriggerTime, pendingIntentFajr) } catch (e: SecurityException) {}
        }

        val pendingIntentMidnight = PendingIntent.getBroadcast(context, appWidgetId + 6000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 1)

        try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pendingIntentMidnight) } catch (e: SecurityException) {}
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAmPmUpdate(context: Context, appWidgetId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, IslamicWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val pendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 1000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val calendar = java.util.Calendar.getInstance()
        if (calendar.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) {
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 12)
        } else {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        }
        calendar.set(java.util.Calendar.MINUTE, 0); calendar.set(java.util.Calendar.SECOND, 0); calendar.set(java.util.Calendar.MILLISECOND, 0)

        try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, calendar.timeInMillis, pendingIntent) } catch (e: SecurityException) {}
    }
}
