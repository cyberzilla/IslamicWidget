package com.cyberzilla.islamicwidget

import android.content.Context
import com.cyberzilla.islamicwidget.utils.CalculationMethod
import com.cyberzilla.islamicwidget.utils.HilalCriteria
import com.cyberzilla.islamicwidget.utils.IslamicAstronomy
import com.cyberzilla.islamicwidget.utils.PrayerTimes
import io.github.cosinekitty.astronomy.EclipseKind
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Single source of truth untuk tanggal Hijriah.
 * Semua komponen (widget, preview, lunar widget, hilal info)
 * HARUS menggunakan data class ini — bukan menghitung sendiri.
 *
 * @param day      Hari (raw dari astronomy engine atau HijrahDate). Bisa 30 di bulan 29 hari.
 * @param month    Bulan (1-12)
 * @param year     Tahun Hijriah
 * @param hijrahDate  Java HijrahDate terdekat (untuk holiday detection, fasting info, dll).
 *                    Mungkin berbeda dari day jika day=30 tapi Java bilang bulan itu 29 hari.
 * @param isFromAstronomy  True jika nilai berasal dari astronomy engine (auto mode).
 */
data class HijriDisplayDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val hijrahDate: HijrahDate,
    val isFromAstronomy: Boolean
)

object IslamicAppUtils {

    // Nama bulan Hijriah kustom per bahasa — menggantikan output Java yang aneh
    // (misal "Rabiʻ I", "Dhuʻl-Qiʻdah") dengan nama yang bersih dan konsisten
    private val HIJRI_MONTHS_ID = arrayOf(
        "Muharram", "Safar", "Rabi'ul Awal", "Rabi'ul Akhir",
        "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban",
        "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
    )
    private val HIJRI_MONTHS_EN = arrayOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
        "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhul-Qi'dah", "Dhul-Hijjah"
    )
    private val HIJRI_MONTHS_AR = arrayOf(
        "محرّم", "صفر", "ربيع الأوّل", "ربيع الآخر",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوّال", "ذو القعدة", "ذو الحجّة"
    )

    /**
     * Pre-processes a date format pattern for HijrahDate objects.
     * Replaces MMMM/MMM tokens with literal custom month names BEFORE formatting,
     * completely bypassing Java's inconsistent HijrahDate month names
     * (e.g. "Dhuʻl-Qaʻdah", "Dhū al-Ḥijjah" with Unicode diacritics).
     *
     * Returns the original pattern unchanged for non-HijrahDate objects.
     */
    private fun preProcessHijriPattern(pattern: String, dateObj: TemporalAccessor, langCode: String): String {
        if (dateObj !is java.time.chrono.HijrahDate) return pattern
        val monthIndex = dateObj.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1
        if (monthIndex !in 0..11) return pattern

        val fullName = when {
            langCode.startsWith("ar") -> HIJRI_MONTHS_AR[monthIndex]
            langCode.startsWith("id") || langCode.startsWith("in") -> HIJRI_MONTHS_ID[monthIndex]
            else -> HIJRI_MONTHS_EN[monthIndex]
        }

        // Replace MMMM first (4 chars), then MMM (3 chars) to avoid partial match.
        // Wrap custom name in single quotes so DateTimeFormatter treats it as literal text.
        // Escape any existing single quotes in month names (e.g. Rabi'ul → Rabi''ul)
        val escapedFull = fullName.replace("'", "''")
        var result = pattern.replace("MMMM", "'$escapedFull'")
        if (result == pattern) {
            // MMMM wasn't found, try MMM (abbreviated)
            result = pattern.replace("MMM", "'$escapedFull'")
        }
        return result
    }

    /**
     * Mendapatkan nama bulan Hijriah sesuai bahasa aktif.
     * @param monthOfYear nomor bulan (1-12)
     * @param langCode kode bahasa ("id", "en", "ar")
     */
    fun getLocalizedHijriMonthName(monthOfYear: Int, langCode: String): String {
        val index = (monthOfYear - 1).coerceIn(0, 11)
        return when {
            langCode.startsWith("ar") -> HIJRI_MONTHS_AR[index]
            langCode.startsWith("id") || langCode.startsWith("in") -> HIJRI_MONTHS_ID[index]
            else -> HIJRI_MONTHS_EN[index]
        }
    }

    fun formatCustomDate(inputStr: String, dateObj: TemporalAccessor, defaultLocale: Locale): String {
        val regex = Regex("([a-zA-Z0-9-]+)\\{([^}]+)\\}")
        return try {
            if (regex.containsMatchIn(inputStr)) {
                regex.replace(inputStr) { matchResult ->
                    val localeTag = matchResult.groupValues[1]
                    val pattern = matchResult.groupValues[2]
                    val locale = try { Locale.forLanguageTag(localeTag) } catch (e: Exception) { defaultLocale }

                    // Pre-process: replace MMMM/MMM with custom Hijri month name before formatting
                    val processedPattern = preProcessHijriPattern(pattern, dateObj, localeTag)
                    var formattedText = DateTimeFormatter.ofPattern(processedPattern, locale).format(dateObj)

                    if (localeTag.lowercase().startsWith("id")) {
                        formattedText = formattedText.replace("Minggu", "Ahad", ignoreCase = true)
                    }

                    if (localeTag.lowercase().startsWith("ar")) {
                        val arabicDigits = arrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
                        val builder = StringBuilder()
                        for (char in formattedText) {
                            if (char in '0'..'9') builder.append(arabicDigits[char - '0']) else builder.append(char)
                        }
                        formattedText = builder.toString()
                    }
                    formattedText
                }
            } else {
                val processedPattern = preProcessHijriPattern(inputStr, dateObj, defaultLocale.language)
                var formattedText = DateTimeFormatter.ofPattern(processedPattern, defaultLocale).format(dateObj)

                if (defaultLocale.language.lowercase() == "id") {
                    formattedText = formattedText.replace("Minggu", "Ahad", ignoreCase = true)
                }
                formattedText
            }
        } catch (e: Exception) {
            val processedPattern = preProcessHijriPattern("dd MMMM yyyy", dateObj, defaultLocale.language)
            var fallbackText = DateTimeFormatter.ofPattern(processedPattern, defaultLocale).format(dateObj)

            if (defaultLocale.language.lowercase() == "id") {
                fallbackText = fallbackText.replace("Minggu", "Ahad", ignoreCase = true)
            }
            fallbackText
        }
    }

    /**
     * Format tanggal Hijriah dari raw day/month/year TANPA bergantung pada HijrahDate.
     *
     * Dibutuhkan karena Java HijrahDate (Umm Al-Qura) tidak bisa merepresentasikan
     * hari ke-30 di bulan yang Java bilang hanya 29 hari. Tapi astronomy engine
     * dengan istikmal rule bisa menghasilkan day=30 untuk bulan tersebut.
     *
     * Mendukung format pattern yang sama seperti formatCustomDate:
     * - locale{pattern} wrapper (misal "id{dd MMMM yyyy G}")
     * - Token: dd/d (hari), MMMM/MMM (nama bulan), yyyy (tahun), G (era=AH)
     * - Arabic digit conversion untuk locale ar
     */
    fun formatHijriManual(inputStr: String, day: Int, month: Int, year: Int, defaultLocale: Locale): String {
        val regex = Regex("([a-zA-Z0-9-]+)\\{([^}]+)\\}")
        return try {
            if (regex.containsMatchIn(inputStr)) {
                regex.replace(inputStr) { matchResult ->
                    val localeTag = matchResult.groupValues[1]
                    val pattern = matchResult.groupValues[2]
                    formatHijriPatternManual(pattern, day, month, year, localeTag)
                }
            } else {
                formatHijriPatternManual(inputStr, day, month, year, defaultLocale.language)
            }
        } catch (e: Exception) {
            formatHijriPatternManual("dd MMMM yyyy", day, month, year, defaultLocale.language)
        }
    }

    /**
     * Format satu pattern Hijri dari raw values. Urutan replacement penting:
     * MMMM sebelum MMM, dd sebelum d, yyyy sebelum yy.
     */
    private fun formatHijriPatternManual(pattern: String, day: Int, month: Int, year: Int, langCode: String): String {
        val monthName = getLocalizedHijriMonthName(month, langCode)

        var result = pattern
        // 1. Buang single-quote escape pairs ('' → placeholder, 'literal' → literal)
        //    Simpan literal yang di-quote agar tidak terkena token replacement
        val literals = mutableListOf<String>()
        result = Regex("'([^']*)'").replace(result) { match ->
            literals.add(match.groupValues[1])
            "\u0000LITERAL${literals.size - 1}\u0000"
        }

        // 2. Replace month tokens (terpanjang dulu)
        result = result.replace("MMMM", monthName)
        result = result.replace("MMM", monthName)
        // MM dan M numerik — jarang dipakai untuk Hijri tapi support saja
        result = result.replace("MM", String.format("%02d", month))

        // 3. Replace day tokens (dd sebelum d)
        result = result.replace("dd", String.format("%02d", day))
        // standalone 'd' — hanya jika bukan bagian dari kata lain
        result = Regex("(?<![a-zA-Z])d(?![a-zA-Z])").replace(result, day.toString())

        // 4. Replace year tokens
        result = result.replace("yyyy", year.toString())
        result = result.replace("yy", (year % 100).toString().padStart(2, '0'))

        // 5. Replace era
        result = result.replace("GGGG", "Anno Hegirae")
        result = result.replace("G", "AH")

        // 6. Restore quoted literals
        for ((i, literal) in literals.withIndex()) {
            result = result.replace("\u0000LITERAL$i\u0000", literal)
        }

        // 7. Locale-specific post-processing
        if (langCode.lowercase().startsWith("ar")) {
            val arabicDigits = arrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
            val builder = StringBuilder()
            for (char in result) {
                if (char in '0'..'9') builder.append(arabicDigits[char - '0']) else builder.append(char)
            }
            result = builder.toString()
        }

        return result
    }

    // =========================================================================
    // SINGLE SOURCE OF TRUTH: Tanggal Hijriah
    // =========================================================================

    /**
     * Versi convenience — baca semua parameter dari SettingsManager.
     * Gunakan ini dari widget, service, receiver, dll.
     */
    fun getCanonicalHijriDate(context: Context): HijriDisplayDate {
        val s = SettingsManager(context)
        return getCanonicalHijriDate(
            isAutoHijri = s.isAutoHijriOffset,
            latitude = s.latitude?.toDoubleOrNull(),
            longitude = s.longitude?.toDoubleOrNull(),
            hilalCriteria = s.hilalCriteria,
            manualOffset = s.hijriOffset,
            isDayStartAtMaghrib = s.isDayStartAtMaghrib,
            calculationMethod = s.calculationMethod
        )
    }

    /**
     * Versi lengkap dengan parameter eksplisit — untuk preview di MainActivity
     * yang membaca nilai dari UI (belum tentu tersimpan ke SettingsManager).
     *
     * Logika:
     * - Auto mode ON → panggil calculateHijriDate langsung, simpan raw day/month/year.
     * - Auto mode OFF → gunakan HijrahDate + manual offset + isDayStartAtMaghrib.
     */
    fun getCanonicalHijriDate(
        isAutoHijri: Boolean,
        latitude: Double?,
        longitude: Double?,
        hilalCriteria: String,
        manualOffset: Int = 0,
        isDayStartAtMaghrib: Boolean = false,
        calculationMethod: String = "KEMENAG"
    ): HijriDisplayDate {
        val today = LocalDate.now()
        var hijriDate = HijrahDate.from(today)

        // === AUTO MODE: Direct astronomy ===
        if (isAutoHijri && latitude != null && longitude != null) {
            try {
                val criteria = HilalCriteria.fromName(hilalCriteria) ?: HilalCriteria.NEO_MABIMS
                val result = IslamicAstronomy.calculateHijriDate(Date(), latitude, longitude, criteria = criteria)
                val aDay = result.hijriDate.day
                val aMonth = result.hijriDate.month
                val aYear = result.hijriDate.year

                val closestHijrahDate = try {
                    HijrahDate.of(aYear, aMonth, aDay)
                } catch (e: java.time.DateTimeException) {
                    // Day 30 tapi Java bilang bulan itu cuma 29 hari → clamp
                    val temp = HijrahDate.of(aYear, aMonth, 1)
                    HijrahDate.of(aYear, aMonth, aDay.coerceAtMost(temp.lengthOfMonth()))
                }

                return HijriDisplayDate(aDay, aMonth, aYear, closestHijrahDate, true)
            } catch (e: Exception) {
                // Fallback ke manual mode
                android.util.Log.e("IslamicAppUtils", "Auto hijri error, fallback to manual", e)
            }
        }

        // === MANUAL MODE: HijrahDate + offset ===
        var totalOffset = manualOffset.toLong()

        if (isDayStartAtMaghrib && latitude != null && longitude != null) {
            try {
                val prayerTimes = calculatePrayerTimes(latitude, longitude, calculationMethod, today)
                val nowCal = Calendar.getInstance()
                val maghribCal = Calendar.getInstance().apply { time = prayerTimes.maghrib }
                val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
                val magMin = maghribCal.get(Calendar.HOUR_OF_DAY) * 60 + maghribCal.get(Calendar.MINUTE)
                if (nowMin >= magMin) totalOffset += 1L
            } catch (_: Exception) {}
        }

        if (totalOffset != 0L) {
            try {
                hijriDate = hijriDate.plus(totalOffset, ChronoUnit.DAYS)
            } catch (_: Exception) {}
        }

        val day = hijriDate.get(ChronoField.DAY_OF_MONTH)
        val month = hijriDate.get(ChronoField.MONTH_OF_YEAR)
        val year = hijriDate.get(ChronoField.YEAR)
        return HijriDisplayDate(day, month, year, hijriDate, false)
    }

    /**
     * Format tanggal Hijri untuk display — otomatis pilih formatter yang tepat.
     * Jika dari astronomy engine (day bisa 30 di bulan 29), pakai formatHijriManual.
     * Jika dari manual mode, pakai formatCustomDate biasa.
     */
    fun formatHijriDisplay(pattern: String, hijri: HijriDisplayDate, locale: Locale): String {
        return if (hijri.isFromAstronomy) {
            formatHijriManual(pattern, hijri.day, hijri.month, hijri.year, locale)
        } else {
            formatCustomDate(pattern, hijri.hijrahDate, locale)
        }
    }

    fun getCalculationMethod(methodStr: String): CalculationMethod {
        return try {
            CalculationMethod.valueOf(methodStr)
        } catch (e: Exception) {
            CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
    }

    fun calculatePrayerTimes(lat: Double, lon: Double, methodStr: String, today: LocalDate): PrayerTimes {
        val method = getCalculationMethod(methodStr)
        // FIX: Gunakan UTC Calendar agar epoch millis menunjuk ke 12:00 UTC (noon UT)
        // pada tanggal yang diminta. Sebelumnya pakai local TZ (GMT+8), sehingga
        // "12:00 local" = "04:00 UTC" → IslamicAstronomy meng-extract day=17 (benar),
        // tapi Time(y,m,d,12,0,0) selalu 12:00 UT. noonOffset = -lon/15 menggeser ke
        // noon lokal. dhuhrTime.addDays(-0.5) lalu mencari Fajr dari 12 jam sebelum noon.
        // Untuk longitude +119° (WITA), noon UT≈04:00 UTC, sehingga -0.5d = ~16:00 UTC
        // kemarin → searchAltitude menemukan FAJR KEMARIN.
        // Dengan UTC Calendar, 12:00 UTC → day extract tetap benar, dan karena
        // noonOffset = -7.96h, approxNoon = 12:00 UT + (-7.96/24)d ≈ 04:02 UT = 12:02 WITA.
        // Ini identik dengan sebelumnya, karena IslamicAstronomy SELALU extract date
        // dengan UTC Calendar lalu buat Time(y,m,d,12,0,0) UT. Yang penting hanyalah
        // year/month/day yang di-extract dari epoch millis via UTC Calendar.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, today.year)
            set(Calendar.MONTH, today.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, today.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return IslamicAstronomy.calculatePrayerTimes(
            date = cal.time,
            latitude = lat,
            longitude = lon,
            method = method
        )
    }

    fun getSunnahFastingInfo(context: Context, hijriDate: HijrahDate, today: LocalDate, isAfterMaghrib: Boolean, isBeforeFajr: Boolean): String {
        val hDay = hijriDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        val hMonth = hijriDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)

        val islamicDayOfWeek = if (isAfterMaghrib) today.plusDays(1).dayOfWeek else today.dayOfWeek

        val sunnahList = mutableListOf<String>()

        if (hMonth == 1 && (hDay == 9 || hDay == 10)) {
            sunnahList.add(context.getString(R.string.sunnah_muharram))
        }

        if (hMonth == 12) {
            when (hDay) {
                in 1..8 -> sunnahList.add(context.getString(R.string.sunnah_dzulhijjah, hDay))
                9 -> sunnahList.add(context.getString(R.string.sunnah_arafah))
                in 11..13 -> sunnahList.add(context.getString(R.string.info_tasyrik))
            }
        }

        // Ayyamul Bidh (13-15 setiap bulan Hijriah) — kecuali Dzulhijjah
        // karena sudah diwakili puasa 1-9 Dzulhijjah, dan 13 termasuk hari tasyrik
        if (hDay in 13..15 && hMonth != 12) {
            val hariKe = hDay - 12
            sunnahList.add(context.getString(R.string.sunnah_ayyamul_bidh, hariKe))
        }

        if (islamicDayOfWeek == java.time.DayOfWeek.MONDAY) {
            sunnahList.add(context.getString(R.string.sunnah_monday))
        } else if (islamicDayOfWeek == java.time.DayOfWeek.THURSDAY) {
            sunnahList.add(context.getString(R.string.sunnah_thursday))
        }

        if (islamicDayOfWeek == java.time.DayOfWeek.FRIDAY) {
            sunnahList.add(context.getString(R.string.sunnah_friday))
        }

        if (sunnahList.isEmpty()) return ""

        val joinedInfo = sunnahList.joinToString(" • ")

        return if (isAfterMaghrib || isBeforeFajr) {
            context.getString(R.string.reminder_fasting_tomorrow, joinedInfo)
        } else {
            joinedInfo
        }
    }

    /**
     * Menghasilkan teks reminder gerhana (Kusuf/Khusuf) yang akan datang.
     * Hanya gerhana yang terlihat dari lokasi user dan terjadi dalam 1 hari ke depan.
     * Gerhana penumbra tidak dimasukkan.
     */
    fun getEclipseReminderInfo(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        return try {
            val eclipses = IslamicAstronomy.getUpcomingEclipses(latitude, longitude, lookAheadDays = 1)
            if (eclipses.isEmpty()) return ""

            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"
            val dateTimePattern = if (is24Hour) "dd/MM HH:mm" else "dd/MM hh:mm a"
            val timeFormatter = SimpleDateFormat(timePattern, Locale.getDefault())
            val dateTimeFormatter = SimpleDateFormat(dateTimePattern, Locale.getDefault())
            timeFormatter.timeZone = TimeZone.getDefault()
            dateTimeFormatter.timeZone = TimeZone.getDefault()

            val reminderList = mutableListOf<String>()

            for (eclipse in eclipses) {
                val kindStr = when (eclipse.kind) {
                    EclipseKind.Total -> context.getString(R.string.eclipse_kind_total)
                    EclipseKind.Partial -> context.getString(R.string.eclipse_kind_partial)
                    EclipseKind.Annular -> context.getString(R.string.eclipse_kind_annular)
                    else -> continue // Skip Penumbral
                }

                val timeStr = when (eclipse.daysUntil) {
                    0 -> context.getString(R.string.eclipse_today, timeFormatter.format(eclipse.peakTime))
                    1 -> context.getString(R.string.eclipse_tomorrow, timeFormatter.format(eclipse.peakTime))
                    else -> context.getString(R.string.eclipse_in_days, eclipse.daysUntil, dateTimeFormatter.format(eclipse.peakTime))
                }

                val reminderText = if (eclipse.type == "SOLAR") {
                    context.getString(R.string.sunnah_solar_eclipse, kindStr) + " • $timeStr"
                } else {
                    context.getString(R.string.sunnah_lunar_eclipse, kindStr) + " • $timeStr"
                }

                reminderList.add(reminderText)
            }

            reminderList.joinToString("\n")
        } catch (e: Exception) {
            ""
        }
    }
}
