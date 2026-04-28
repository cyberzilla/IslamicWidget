package com.cyberzilla.islamicwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.cyberzilla.islamicwidget.utils.HilalCriteria
import com.cyberzilla.islamicwidget.utils.IslamicAstronomy
import com.cyberzilla.islamicwidget.utils.MoonPhaseRenderer
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import java.util.Locale

class LunarWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "LunarWidget"
        // Approximate cell width in dp (Android standard ~73dp per cell including margin)
        private const val CELL_WIDTH_DP = 100
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.applicationContext.resources.displayMetrics
        )
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // goAsync() bisa return null jika dipanggil lebih dari sekali per onReceive
        val pendingResult = goAsync()
        if (pendingResult == null) {
            // Fallback: langsung update tanpa delay
            for (appWidgetId in appWidgetIds) {
                try {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    updateWidget(context, appWidgetManager, appWidgetId, options)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating lunar widget (sync)", e)
                }
            }
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                for (appWidgetId in appWidgetIds) {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    updateWidget(context, appWidgetManager, appWidgetId, options)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating lunar widget", e)
            } finally {
                pendingResult.finish()
            }
        }, 300)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Called when widget is resized — re-render with new size awareness
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    // onReceive tidak perlu di-override untuk ACTION_APPWIDGET_UPDATE
    // karena super.onReceive() (AppWidgetProvider) sudah otomatis memanggil onUpdate().
    // Override sebelumnya menyebabkan onUpdate() dipanggil 2x → goAsync() NPE crash.

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle) {
        val settings = SettingsManager(context)

        // Localized context
        val lang = settings.languageCode
        val locale = Locale(lang)
        val config = context.resources.configuration
        config.setLocale(locale)
        val localizedContext = context.createConfigurationContext(config)

        // Detect widget width to decide layout mode
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 40)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
        val showDates = minWidthDp > CELL_WIDTH_DP // Show dates if wider than ~1 cell

        // Render moon at high resolution for crisp display, minimum 256px
        val moonSizePx = dpToPx(context, minHeightDp.coerceAtLeast(80).toFloat()).toInt().coerceAtLeast(256)

        // === Moon Phase Rendering ===
        val lat = settings.latitude?.toDoubleOrNull()
        val lon = settings.longitude?.toDoubleOrNull()
        val moonData = MoonPhaseRenderer.getCurrentMoonPhase()
        val moonBitmap = MoonPhaseRenderer.renderMoonPhase(moonSizePx, latitude = lat, longitude = lon)
        val phaseName = MoonPhaseRenderer.getPhaseName(moonData.phaseAngle)
        val illuminationPct = (moonData.illuminationFraction * 100).toInt()

        // Build views
        val views = RemoteViews(context.packageName, R.layout.widget_lunar)

        // Background
        val bgColor = try { Color.parseColor(settings.widgetBgColor) } catch (_: Exception) { Color.TRANSPARENT }
        views.setInt(R.id.widget_bg, "setColorFilter", bgColor)

        // Moon phase bitmap
        views.setImageViewBitmap(R.id.iv_moon_phase, moonBitmap)

        // Show/hide date container based on widget width
        if (showDates) {
            views.setViewVisibility(R.id.container_date_info, View.VISIBLE)

            // === Date Calculation ===
            val latString = settings.latitude
            val lonString = settings.longitude
            val lat = latString?.toDoubleOrNull() ?: -6.2
            val lon = lonString?.toDoubleOrNull() ?: 106.8

            val today = LocalDate.now()
            var hijriDate = HijrahDate.from(today)

            var totalHijriOffset: Long
            if (settings.isAutoHijriOffset && latString != null && lonString != null) {
                try {
                    val criteria = HilalCriteria.fromName(settings.hilalCriteria)
                    totalHijriOffset = IslamicAstronomy.calculateHijriOffset(
                        lat, lon, criteria = criteria ?: HilalCriteria.NEO_MABIMS
                    ).toLong()
                } catch (e: Exception) {
                    totalHijriOffset = settings.hijriOffset.toLong()
                    Log.e(TAG, "Auto hijri offset error", e)
                }
            } else {
                totalHijriOffset = settings.hijriOffset.toLong()
            }

            if (totalHijriOffset != 0L) {
                hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)
            }

            val masehiPattern = settings.dateFormat.ifEmpty { "en-US{EEEE, dd MMMM yyyy}" }
            val hijriPattern = settings.hijriFormat.ifEmpty { "en-US{dd MMMM yyyy} H" }
            val gregorianStr = IslamicAppUtils.formatCustomDate(masehiPattern, today, locale)
            val hijriStr = IslamicAppUtils.formatCustomDate(hijriPattern, hijriDate, locale)

            val textColor = try { Color.parseColor(settings.widgetTextColor) } catch (_: Exception) { Color.WHITE }
            val phaseNameLocalized = getLocalizedPhaseName(localizedContext, phaseName, illuminationPct)

            views.setTextViewText(R.id.tv_hijri_date, hijriStr)
            views.setTextViewText(R.id.tv_gregorian_date, gregorianStr)
            views.setTextViewText(R.id.tv_moon_phase_name, phaseNameLocalized)

            views.setTextColor(R.id.tv_hijri_date, Color.parseColor("#FFC107"))
            views.setTextColor(R.id.tv_gregorian_date, textColor)
            views.setTextColor(R.id.tv_moon_phase_name, Color.parseColor("#B0BEC5"))
        } else {
            views.setViewVisibility(R.id.container_date_info, View.GONE)
        }

        // Click behavior based on widget size
        if (!showDates) {
            // 1x1 widget: tap moon → show Hilal info dialog
            val hilalIntent = Intent(context, HilalInfoActivity::class.java)
            val pendingHilal = PendingIntent.getActivity(
                context, appWidgetId + 7000, hilalIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_moon_phase, pendingHilal)
        } else {
            // Wider widget: tap moon → open main app
            val openAppIntent = Intent(context, MainActivity::class.java)
            val pendingOpenApp = PendingIntent.getActivity(
                context, appWidgetId + 7000, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_moon_phase, pendingOpenApp)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getLocalizedPhaseName(context: Context, englishName: String, illumination: Int): String {
        val phaseNameStr = when (englishName) {
            "New Moon" -> context.getString(R.string.moon_new)
            "Waxing Crescent" -> context.getString(R.string.moon_waxing_crescent)
            "First Quarter" -> context.getString(R.string.moon_first_quarter)
            "Waxing Gibbous" -> context.getString(R.string.moon_waxing_gibbous)
            "Full Moon" -> context.getString(R.string.moon_full)
            "Waning Gibbous" -> context.getString(R.string.moon_waning_gibbous)
            "Last Quarter" -> context.getString(R.string.moon_last_quarter)
            "Waning Crescent" -> context.getString(R.string.moon_waning_crescent)
            else -> englishName
        }
        return "$phaseNameStr • $illumination%"
    }
}
