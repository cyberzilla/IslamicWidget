package com.cyberzilla.islamicwidget.utils

import android.content.Context
import com.cyberzilla.islamicwidget.R
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.searchMoonPhase
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Sistem kalkulasi hari libur nasional & peringatan multi-kalender.
 *
 * Mendukung 3 profil bahasa:
 * - Indonesia (id): Gregorian tetap + Hijriah + Imlek + Nyepi + Paskah + Waisak
 * - English (en): Hari libur universal + Islam
 * - Arabic (ar): Saudi Arabia + Islam
 */
object HolidayCalendar {

    data class HolidayInfo(
        val name: String,
        val isPublicHoliday: Boolean  // true = 🔴 libur/tanggal merah, false = 📌 peringatan
    )

    /**
     * Entry point utama — mengembalikan daftar hari libur/peringatan untuk tanggal tertentu.
     */
    fun getHolidaysForDate(
        context: Context,
        date: LocalDate,
        hijriDate: HijrahDate,
        langCode: String
    ): List<HolidayInfo> {
        return when {
            langCode.startsWith("id") || langCode.startsWith("in") ->
                getIndonesianHolidays(context, date, hijriDate)
            langCode.startsWith("ar") ->
                getSaudiHolidays(context, date, hijriDate)
            else ->
                getUniversalHolidays(context, date, hijriDate)
        }
    }

    // =====================================================================
    // PROFIL INDONESIA
    // =====================================================================
    private fun getIndonesianHolidays(
        context: Context,
        date: LocalDate,
        hijriDate: HijrahDate
    ): List<HolidayInfo> {
        val result = mutableListOf<HolidayInfo>()
        val m = date.monthValue
        val d = date.dayOfMonth
        val year = date.year

        // --- Hari Libur Nasional (tanggal Gregorian tetap) ---
        if (m == 1 && d == 1) result.add(HolidayInfo(context.getString(R.string.holiday_id_new_year), true))
        if (m == 5 && d == 1) result.add(HolidayInfo(context.getString(R.string.holiday_id_labor), true))
        if (m == 6 && d == 1) result.add(HolidayInfo(context.getString(R.string.holiday_id_pancasila), true))
        if (m == 8 && d == 17) result.add(HolidayInfo(context.getString(R.string.holiday_id_independence), true))
        if (m == 12 && d == 25) result.add(HolidayInfo(context.getString(R.string.holiday_id_christmas), true))

        // --- Imlek (Chinese New Year) ---
        try {
            val imlek = calculateChineseNewYear(year)
            if (date == imlek) result.add(HolidayInfo(context.getString(R.string.holiday_id_imlek), true))
        } catch (_: Exception) {}

        // --- Nyepi (Tahun Baru Saka) ---
        try {
            val nyepi = calculateNyepi(year)
            if (date == nyepi) result.add(HolidayInfo(context.getString(R.string.holiday_id_nyepi), true))
        } catch (_: Exception) {}

        // --- Paskah-based (Good Friday, Ascension) ---
        try {
            val easter = calculateEasterSunday(year)
            val goodFriday = easter.minusDays(2)
            val ascension = easter.plusDays(39)
            if (date == goodFriday) result.add(HolidayInfo(context.getString(R.string.holiday_id_good_friday), true))
            if (date == ascension) result.add(HolidayInfo(context.getString(R.string.holiday_id_ascension), true))
        } catch (_: Exception) {}

        // --- Waisak ---
        try {
            val waisak = calculateWaisak(year)
            if (date == waisak) result.add(HolidayInfo(context.getString(R.string.holiday_id_waisak), true))
        } catch (_: Exception) {}

        // --- Hari libur Islam (Hijriah) ---
        result.addAll(getIslamicHolidaysId(context, hijriDate))

        // --- Hari Peringatan (bukan libur) ---
        if (m == 4 && d == 21) result.add(HolidayInfo(context.getString(R.string.commemoration_id_kartini), false))
        if (m == 5 && d == 2) result.add(HolidayInfo(context.getString(R.string.commemoration_id_education), false))
        if (m == 5 && d == 20) result.add(HolidayInfo(context.getString(R.string.commemoration_id_awakening), false))
        if (m == 10 && d == 1) result.add(HolidayInfo(context.getString(R.string.commemoration_id_kesaktian), false))
        if (m == 10 && d == 28) result.add(HolidayInfo(context.getString(R.string.commemoration_id_sumpah_pemuda), false))
        if (m == 11 && d == 10) result.add(HolidayInfo(context.getString(R.string.commemoration_id_heroes), false))
        if (m == 12 && d == 22) result.add(HolidayInfo(context.getString(R.string.commemoration_id_mothers), false))

        return result
    }

    /**
     * Hari libur Islam versi Indonesia (bahasa Indonesia).
     */
    private fun getIslamicHolidaysId(context: Context, hijriDate: HijrahDate): List<HolidayInfo> {
        val hDay = hijriDate.get(ChronoField.DAY_OF_MONTH)
        val hMonth = hijriDate.get(ChronoField.MONTH_OF_YEAR)
        val result = mutableListOf<HolidayInfo>()

        // 1 Muharram — Tahun Baru Islam
        if (hMonth == 1 && hDay == 1) result.add(HolidayInfo(context.getString(R.string.holiday_id_islamic_new_year), true))
        // 10 Muharram — Hari Asyura
        if (hMonth == 1 && hDay == 10) result.add(HolidayInfo(context.getString(R.string.commemoration_id_ashura), false))
        // 12 Rabiul Awal — Maulid Nabi
        if (hMonth == 3 && hDay == 12) result.add(HolidayInfo(context.getString(R.string.holiday_id_mawlid), true))
        // 27 Rajab — Isra Mi'raj
        if (hMonth == 7 && hDay == 27) result.add(HolidayInfo(context.getString(R.string.holiday_id_isra_miraj), true))
        // 1 Ramadhan — Awal Ramadhan
        if (hMonth == 9 && hDay == 1) result.add(HolidayInfo(context.getString(R.string.commemoration_id_ramadan_start), false))
        // 17 Ramadhan — Nuzulul Quran
        if (hMonth == 9 && hDay == 17) result.add(HolidayInfo(context.getString(R.string.commemoration_id_nuzulul_quran), false))
        // 1 Syawal — Idul Fitri
        if (hMonth == 10 && hDay == 1) result.add(HolidayInfo(context.getString(R.string.holiday_id_eid_fitri), true))
        // 2 Syawal — Idul Fitri hari ke-2
        if (hMonth == 10 && hDay == 2) result.add(HolidayInfo(context.getString(R.string.holiday_id_eid_fitri_2), true))
        // 10 Dzulhijjah — Idul Adha
        if (hMonth == 12 && hDay == 10) result.add(HolidayInfo(context.getString(R.string.holiday_id_eid_adha), true))

        return result
    }

    // =====================================================================
    // PROFIL ENGLISH (Universal)
    // =====================================================================
    private fun getUniversalHolidays(
        context: Context,
        date: LocalDate,
        hijriDate: HijrahDate
    ): List<HolidayInfo> {
        val result = mutableListOf<HolidayInfo>()
        val m = date.monthValue
        val d = date.dayOfMonth

        if (m == 1 && d == 1) result.add(HolidayInfo(context.getString(R.string.holiday_en_new_year), true))
        if (m == 5 && d == 1) result.add(HolidayInfo(context.getString(R.string.holiday_en_labor), true))
        if (m == 12 && d == 25) result.add(HolidayInfo(context.getString(R.string.holiday_en_christmas), true))

        // Hari libur Islam (English)
        val hDay = hijriDate.get(ChronoField.DAY_OF_MONTH)
        val hMonth = hijriDate.get(ChronoField.MONTH_OF_YEAR)

        if (hMonth == 1 && hDay == 1) result.add(HolidayInfo(context.getString(R.string.holiday_en_islamic_new_year), true))
        if (hMonth == 9 && hDay == 1) result.add(HolidayInfo(context.getString(R.string.commemoration_en_ramadan_start), false))
        if (hMonth == 10 && hDay == 1) result.add(HolidayInfo(context.getString(R.string.holiday_en_eid_fitri), true))
        if (hMonth == 12 && hDay == 10) result.add(HolidayInfo(context.getString(R.string.holiday_en_eid_adha), true))

        return result
    }

    // =====================================================================
    // PROFIL ARABIC (Saudi Arabia)
    // =====================================================================
    private fun getSaudiHolidays(
        context: Context,
        date: LocalDate,
        hijriDate: HijrahDate
    ): List<HolidayInfo> {
        val result = mutableListOf<HolidayInfo>()
        val m = date.monthValue
        val d = date.dayOfMonth

        // Hari nasional Saudi (Gregorian tetap)
        if (m == 2 && d == 22) result.add(HolidayInfo(context.getString(R.string.holiday_ar_founding), true))
        if (m == 9 && d == 23) result.add(HolidayInfo(context.getString(R.string.holiday_ar_national), true))

        // Hari libur Islam resmi Saudi (hanya Idul Fitri & Idul Adha)
        val hDay = hijriDate.get(ChronoField.DAY_OF_MONTH)
        val hMonth = hijriDate.get(ChronoField.MONTH_OF_YEAR)

        if (hMonth == 9 && hDay == 1) result.add(HolidayInfo(context.getString(R.string.commemoration_ar_ramadan_start), false))
        if (hMonth == 10 && (hDay in 1..3)) result.add(HolidayInfo(context.getString(R.string.holiday_ar_eid_fitri), true))
        if (hMonth == 12 && (hDay in 10..13)) result.add(HolidayInfo(context.getString(R.string.holiday_ar_eid_adha), true))

        return result
    }

    // =====================================================================
    // KALKULASI KALENDER NON-GREGORIAN
    // =====================================================================

    /**
     * Computus — Meeus/Jones/Butcher algorithm untuk Paskah (Gregorian).
     * Akurat untuk tahun 1583+.
     */
    fun calculateEasterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    /**
     * Tahun Baru Imlek via android.icu.util.ChineseCalendar.
     * Hari pertama bulan pertama kalender Tionghoa.
     */
    fun calculateChineseNewYear(year: Int): LocalDate {
        val cc = android.icu.util.ChineseCalendar()
        cc.clear()
        // Chinese calendar: EXTENDED_YEAR dimulai dari tahun 2637 SM
        // Untuk tahun Gregorian, kita set ke tahun Gregorian langsung
        // lalu cari bulan 1 hari 1
        cc.set(android.icu.util.ChineseCalendar.EXTENDED_YEAR, year - 2636)
        cc.set(android.icu.util.ChineseCalendar.MONTH, 0)  // bulan pertama (0-indexed)
        cc.set(android.icu.util.ChineseCalendar.IS_LEAP_MONTH, 0)
        cc.set(android.icu.util.ChineseCalendar.DAY_OF_MONTH, 1)

        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = cc.timeInMillis
        return LocalDate.of(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Nyepi — Tahun Baru Saka Bali.
     * Jatuh pada hari setelah Tilem (new moon) Sasih Kasanga.
     *
     * Sasih Kasanga = bulan ke-9 Saka dimulai setelah new moon yang jatuh
     * di sekitar Februari-Maret. Nyepi = day after new moon tersebut.
     *
     * Pendekatan: cari new moon terdekat sebelum equinox Maret.
     * Nyepi = hari setelah new moon tersebut.
     */
    fun calculateNyepi(year: Int): LocalDate {
        // Equinox Maret biasanya ~20-21 Maret
        // Cari new moon terakhir sebelum atau pada equinox Maret
        // Mulai pencarian dari 1 Februari (cukup mundur ~50 hari)
        val searchStart = Time(year, 2, 1, 0, 0, 0.0)

        // Cari new moon pertama mulai dari awal Februari
        var newMoon = searchMoonPhase(0.0, searchStart, 60.0)
            ?: return LocalDate.of(year, 3, 21) // fallback

        // Cari new moon berikutnya dalam range ~Maret-April
        // Kita butuh new moon yang jatuh di sekitar Maret
        val marchEquinoxApprox = Time(year, 3, 21, 0, 0, 0.0)

        // Iterasi: cari new moon terdekat sebelum atau pada equinox
        var bestNewMoon = newMoon
        while (true) {
            val next = searchMoonPhase(0.0, newMoon.addDays(1.0), 40.0) ?: break
            if (next.ut > marchEquinoxApprox.ut + 15) break // terlalu jauh setelah equinox
            bestNewMoon = next
            newMoon = next
        }

        // Nyepi = hari setelah new moon
        val cal = java.util.GregorianCalendar()
        cal.timeInMillis = (bestNewMoon.toMillisecondsSince1970())
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        return LocalDate.of(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Waisak — Full moon pertama di bulan Mei (konvensi Indonesia/Theravada).
     * Jika tidak ada full moon di Mei, cari di April-Juni.
     */
    fun calculateWaisak(year: Int): LocalDate {
        // Cari full moon mulai dari akhir April
        val searchStart = Time(year, 4, 20, 0, 0, 0.0)
        val fullMoon = searchMoonPhase(180.0, searchStart, 45.0)
            ?: return LocalDate.of(year, 5, 15) // fallback

        val cal = java.util.GregorianCalendar()
        cal.timeInMillis = fullMoon.toMillisecondsSince1970()
        return LocalDate.of(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Format output hari libur untuk ditampilkan di widget/preview.
     * Menambahkan emoji 🔴 untuk libur dan 📌 untuk peringatan.
     */
    fun formatHolidayInfo(holidays: List<HolidayInfo>): String {
        if (holidays.isEmpty()) return ""
        return holidays.joinToString(" • ") {
            val prefix = if (it.isPublicHoliday) "🔴" else "📌"
            "$prefix ${it.name}"
        }
    }

    /**
     * Cek apakah hari ini adalah hari libur (tanggal merah).
     * Digunakan untuk mengubah warna font Gregorian.
     */
    fun isPublicHoliday(holidays: List<HolidayInfo>): Boolean {
        return holidays.any { it.isPublicHoliday }
    }
}
