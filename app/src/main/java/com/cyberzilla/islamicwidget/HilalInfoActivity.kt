package com.cyberzilla.islamicwidget

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.cyberzilla.islamicwidget.utils.HilalCriteria
import com.cyberzilla.islamicwidget.utils.IslamicAstronomy
import com.cyberzilla.islamicwidget.utils.MoonPhaseRenderer
import java.text.SimpleDateFormat
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date
import java.util.Locale

/**
 * Transparent dialog activity yang menampilkan informasi Hilal lengkap
 * dengan visualisasi moon phase. Dipanggil dari widget Lunar 1x1.
 * Mengikuti pola desain yang sama dengan CompassActivity.
 */
class HilalInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = SettingsManager(this)
        when (settings.appTheme) {
            "LIGHT" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "DARK" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.activity_hilal_info)
        setFinishOnTouchOutside(true)

        // Close button (sama dengan compass)
        findViewById<ImageView>(R.id.btn_close_hilal)?.setOnClickListener { finish() }

        // Localized context
        val selectedLocale = Locale.forLanguageTag(settings.languageCode)
        val config = Configuration(resources.configuration)
        config.setLocale(selectedLocale)
        val ctx = createConfigurationContext(config)

        // Detect dark mode for color choices
        val isDark = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }

        val lat = settings.latitude?.toDoubleOrNull()
        val lon = settings.longitude?.toDoubleOrNull()

        if (lat == null || lon == null) {
            finish()
            return
        }

        val now = Date()
        val criteriaName = settings.hilalCriteria
        val criteria = HilalCriteria.fromName(criteriaName) ?: HilalCriteria.NEO_MABIMS

        // Calculate hilal data
        val hilalResult = try {
            IslamicAstronomy.calculateHijriDate(now, lat, lon, criteria = criteria)
        } catch (e: Exception) {
            finish()
            return
        }

        val hijri = hilalResult.hijriDate
        val report = hilalResult.hilalReport

        // Moon phase data
        val moonData = MoonPhaseRenderer.getCurrentMoonPhase()
        val phaseName = MoonPhaseRenderer.getPhaseName(moonData.phaseAngle)
        val illuminationPct = (moonData.illuminationFraction * 100).toInt()

        // Gregorian date
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", selectedLocale)
        val gregorianStr = dateFormat.format(now)

        // Hijri date
        val hijriDateStr = try {
            val hd = HijrahDate.now()
            val totalOffset = if (settings.isAutoHijriOffset) {
                IslamicAstronomy.calculateHijriOffset(lat, lon, criteria = criteria).toLong()
            } else {
                settings.hijriOffset.toLong()
            }
            val adjusted = hd.plus(totalOffset, java.time.temporal.ChronoUnit.DAYS)
            val day = adjusted.get(ChronoField.DAY_OF_MONTH)
            val month = adjusted.get(ChronoField.MONTH_OF_YEAR)
            val year = adjusted.get(ChronoField.YEAR)
            val monthName = com.cyberzilla.islamicwidget.utils.HIJRI_MONTH_NAMES[month - 1]
            "$day $monthName $year H"
        } catch (e: Exception) {
            hijri.toString()
        }

        // Time formatters
        val timeFormat = SimpleDateFormat("HH:mm", selectedLocale)
        val timeFormatFull = SimpleDateFormat("dd MMM yyyy, HH:mm", selectedLocale)

        // === Bind Views ===

        // Title
        findViewById<TextView>(R.id.tv_hilal_title).text = ctx.getString(R.string.hilal_dialog_title)

        // Moon phase image
        val moonSizePx = dpToPx(120f).toInt()
        val moonBitmap = MoonPhaseRenderer.renderMoonPhase(moonSizePx, latitude = lat, longitude = lon)
        findViewById<ImageView>(R.id.iv_hilal_moon).setImageBitmap(moonBitmap)

        // Dates
        findViewById<TextView>(R.id.tv_gregorian_date).text = "📅  $gregorianStr"
        val tvHijri = findViewById<TextView>(R.id.tv_hijri_date)
        tvHijri.text = "🌙  $hijriDateStr"
        tvHijri.setTextColor(Color.parseColor("#FFC107"))

        // === Build Hilal Data Text ===
        val sb = SpannableStringBuilder()
        val labelColor = if (isDark) Color.parseColor("#90A4AE") else Color.parseColor("#9E9E9E")

        // Moon phase
        val localizedPhase = getLocalizedPhaseName(ctx, phaseName)
        appendRow(sb, ctx.getString(R.string.hilal_phase), "$localizedPhase • $illuminationPct%", labelColor)

        // Conjunction time
        report.conjunctionTime?.let {
            appendRow(sb, ctx.getString(R.string.hilal_conjunction), timeFormatFull.format(it), labelColor)
        }

        // Moon age
        appendRow(sb, ctx.getString(R.string.hilal_moon_age),
            String.format(selectedLocale, "%.1f %s", report.moonAgeHours, ctx.getString(R.string.hilal_hours)), labelColor)

        sb.append("\n")
        appendSectionTitle(sb, ctx.getString(R.string.hilal_section_position))

        // Sunset & Moonset
        report.sunsetTime?.let {
            appendRow(sb, ctx.getString(R.string.hilal_sunset), timeFormat.format(it), labelColor)
        }
        report.moonsetTime?.let {
            appendRow(sb, ctx.getString(R.string.hilal_moonset), timeFormat.format(it), labelColor)
        }

        // Moon altitude & elongation
        appendRow(sb, ctx.getString(R.string.hilal_altitude),
            String.format(selectedLocale, "%.2f°", report.moonApparentAltitude), labelColor)
        appendRow(sb, ctx.getString(R.string.hilal_elongation),
            String.format(selectedLocale, "%.2f°", report.elongationTopo), labelColor)

        // Azimuths
        appendRow(sb, ctx.getString(R.string.hilal_sun_azimuth),
            String.format(selectedLocale, "%.2f°", report.sunAzimuth), labelColor)
        appendRow(sb, ctx.getString(R.string.hilal_moon_azimuth),
            String.format(selectedLocale, "%.2f°", report.moonAzimuth), labelColor)

        // Illumination
        appendRow(sb, ctx.getString(R.string.hilal_illumination),
            String.format(selectedLocale, "%.2f%%", report.illuminationFraction), labelColor)

        sb.append("\n")
        appendSectionTitle(sb, ctx.getString(R.string.hilal_section_criteria))

        // Criteria used
        appendRow(sb, ctx.getString(R.string.hilal_criteria_used), criteria.displayName, labelColor)
        appendRow(sb, "Yallop Q", String.format(selectedLocale, "%.4f", report.yallopQ), labelColor)
        appendRow(sb, "Odeh V", String.format(selectedLocale, "%.4f", report.odehV), labelColor)

        // Boolean checks
        appendRow(sb, ctx.getString(R.string.hilal_conj_before_sunset),
            if (report.isConjunctionBeforeSunset) "✅" else "❌", labelColor)
        appendRow(sb, ctx.getString(R.string.hilal_moonset_after_sunset),
            if (report.isMoonsetAfterSunset) "✅" else "❌", labelColor)

        sb.append("\n")

        // Visibility verdict
        val verdictText = if (report.isVisible)
            ctx.getString(R.string.hilal_visible)
        else
            ctx.getString(R.string.hilal_not_visible)
        val verdictColor = if (report.isVisible) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")

        val verdictStart = sb.length
        sb.append(verdictText)
        sb.setSpan(ForegroundColorSpan(verdictColor), verdictStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), verdictStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(RelativeSizeSpan(1.1f), verdictStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Bind hilal data text
        findViewById<TextView>(R.id.tv_hilal_data).text = sb
    }

    private fun appendSectionTitle(sb: SpannableStringBuilder, text: String) {
        val start = sb.length
        sb.append(text).append("\n")
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, start + text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(Color.parseColor("#FFC107")), start, start + text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendRow(sb: SpannableStringBuilder, label: String, value: String, labelColor: Int) {
        val start = sb.length
        sb.append(label)
        sb.setSpan(ForegroundColorSpan(labelColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("  $value\n")
    }

    private fun getLocalizedPhaseName(context: android.content.Context, englishName: String): String {
        return when (englishName) {
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
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}
