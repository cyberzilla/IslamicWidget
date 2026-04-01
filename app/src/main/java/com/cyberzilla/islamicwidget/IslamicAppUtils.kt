package com.cyberzilla.islamicwidget

import android.content.Context
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun Instant.asDate() = java.util.Date(toEpochMilliseconds())

object IslamicAppUtils {

    fun formatCustomDate(inputStr: String, dateObj: TemporalAccessor, defaultLocale: Locale): String {
        val regex = Regex("([a-zA-Z0-9-]+)\\{([^}]+)\\}")
        return try {
            if (regex.containsMatchIn(inputStr)) {
                regex.replace(inputStr) { matchResult ->
                    val localeTag = matchResult.groupValues[1]
                    val pattern = matchResult.groupValues[2]
                    val locale = try { Locale.forLanguageTag(localeTag) } catch (e: Exception) { defaultLocale }
                    var formattedText = DateTimeFormatter.ofPattern(pattern, locale).format(dateObj)
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
                DateTimeFormatter.ofPattern(inputStr, defaultLocale).format(dateObj)
            }
        } catch (e: Exception) {
            DateTimeFormatter.ofPattern("dd MMMM yyyy", defaultLocale).format(dateObj)
        }
    }

    fun getCalculationMethod(methodStr: String): CalculationMethod {
        return when (methodStr) {
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
    }

    fun calculatePrayerTimes(lat: Double, lon: Double, methodStr: String, today: LocalDate): PrayerTimes {
        val coordinates = Coordinates(lat, lon)
        val dateComponents = DateComponents(today.year, today.monthValue, today.dayOfMonth)
        val method = getCalculationMethod(methodStr)
        return PrayerTimes(coordinates, dateComponents, method.parameters)
    }

    fun getSunnahFastingInfo(context: Context, hijriDate: HijrahDate, today: LocalDate, isAfterMaghrib: Boolean): String {
        val hDay = hijriDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        val hMonth = hijriDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)

        // Logika Akurat Syariat: Hari berganti setelah Maghrib
        val islamicDayOfWeek = if (isAfterMaghrib) today.plusDays(1).dayOfWeek else today.dayOfWeek

        val sunnahList = mutableListOf<String>()

        // 1. Muharram (Tasu'a & Asyura)
        if (hMonth == 1 && (hDay == 9 || hDay == 10)) {
            sunnahList.add(context.getString(R.string.sunnah_muharram))
        }

        // 2. Dzulhijjah (1-9)
        if (hMonth == 12) {
            when (hDay) {
                in 1..7 -> sunnahList.add(context.getString(R.string.sunnah_dzulhijjah, hDay))
                8 -> sunnahList.add(context.getString(R.string.sunnah_tarwiyah))
                9 -> sunnahList.add(context.getString(R.string.sunnah_arafah))
            }
        }

        // 3. Ayyamul Bidh (13, 14, 15) - Kecuali 13 Dzulhijjah (Tasyriq)
        if (hDay in 13..15 && !(hMonth == 12 && hDay == 13)) {
            val hariKe = hDay - 12
            sunnahList.add(context.getString(R.string.sunnah_ayyamul_bidh, hariKe))
        }

        // 4. Puasa Senin & Kamis (Menggunakan islamicDayOfWeek)
        if (islamicDayOfWeek == java.time.DayOfWeek.MONDAY) {
            sunnahList.add(context.getString(R.string.sunnah_monday))
        } else if (islamicDayOfWeek == java.time.DayOfWeek.THURSDAY) {
            sunnahList.add(context.getString(R.string.sunnah_thursday))
        }

        // 5. Sunnah Jumat (Al-Kahfi)
        if (islamicDayOfWeek == java.time.DayOfWeek.FRIDAY) {
            sunnahList.add(context.getString(R.string.sunnah_friday))
        }

        // Gabungkan semuanya menggunakan titik bulat (bullet) seperti kode asli Anda
        return sunnahList.joinToString(" • ")
    }
}