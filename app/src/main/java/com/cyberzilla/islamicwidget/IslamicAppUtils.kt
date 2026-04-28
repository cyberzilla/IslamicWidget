package com.cyberzilla.islamicwidget

import android.content.Context
import com.cyberzilla.islamicwidget.utils.CalculationMethod
import com.cyberzilla.islamicwidget.utils.IslamicAstronomy
import com.cyberzilla.islamicwidget.utils.PrayerTimes
import io.github.cosinekitty.astronomy.EclipseKind
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
     * Mengganti nama bulan Hijriah bawaan Java dengan nama kustom yang bersih.
     * Mengambil nomor bulan dari HijrahDate lalu replace string bulan Java di output.
     */
    private fun replaceHijriMonthNames(text: String, dateObj: TemporalAccessor, langCode: String): String {
        if (dateObj !is java.time.chrono.HijrahDate) return text
        val monthIndex = dateObj.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1
        if (monthIndex !in 0..11) return text

        val customName = when {
            langCode.startsWith("ar") -> HIJRI_MONTHS_AR[monthIndex]
            langCode.startsWith("id") || langCode.startsWith("in") -> HIJRI_MONTHS_ID[monthIndex]
            else -> HIJRI_MONTHS_EN[monthIndex]
        }

        // Dapatkan nama bulan bawaan Java untuk locale ini
        val javaMonthName = try {
            val locale = Locale.forLanguageTag(langCode)
            java.time.format.DateTimeFormatter.ofPattern("MMMM", locale).format(dateObj)
        } catch (e: Exception) { return text }

        // Replace jika ditemukan di output
        return if (javaMonthName.isNotEmpty() && text.contains(javaMonthName)) {
            text.replace(javaMonthName, customName)
        } else text
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
                    var formattedText = DateTimeFormatter.ofPattern(pattern, locale).format(dateObj)

                    // Replace nama bulan Hijriah Java dengan nama kustom
                    formattedText = replaceHijriMonthNames(formattedText, dateObj, localeTag)

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
                var formattedText = DateTimeFormatter.ofPattern(inputStr, defaultLocale).format(dateObj)

                // Replace nama bulan Hijriah Java dengan nama kustom
                formattedText = replaceHijriMonthNames(formattedText, dateObj, defaultLocale.language)

                if (defaultLocale.language.lowercase() == "id") {
                    formattedText = formattedText.replace("Minggu", "Ahad", ignoreCase = true)
                }
                formattedText
            }
        } catch (e: Exception) {
            var fallbackText = DateTimeFormatter.ofPattern("dd MMMM yyyy", defaultLocale).format(dateObj)

            // Replace nama bulan Hijriah Java dengan nama kustom
            fallbackText = replaceHijriMonthNames(fallbackText, dateObj, defaultLocale.language)

            if (defaultLocale.language.lowercase() == "id") {
                fallbackText = fallbackText.replace("Minggu", "Ahad", ignoreCase = true)
            }
            fallbackText
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
        val cal = Calendar.getInstance().apply {
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
                in 1..7 -> sunnahList.add(context.getString(R.string.sunnah_dzulhijjah, hDay))
                8 -> sunnahList.add(context.getString(R.string.sunnah_tarwiyah))
                9 -> sunnahList.add(context.getString(R.string.sunnah_arafah))
            }
        }

        if (hDay in 13..15 && !(hMonth == 12 && hDay == 13)) {
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
