package com.cyberzilla.islamicwidget.utils

import android.util.Log
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date

/**
 * Utility class yang menjadi jembatan antara kalkulasi lunar (HijriCalculator)
 * dan tabel statis HijrahDate dari Java.
 *
 * Membandingkan hari Hijri dari kedua sumber untuk menentukan offset
 * yang diperlukan agar HijrahDate (tabel statis) sesuai dengan realitas astronomi.
 */
object HijriOffsetCalculator {

    private const val TAG = "HijriOffsetCalc"

    /**
     * Menghitung offset yang perlu diterapkan ke HijrahDate (tabel statis)
     * agar sesuai dengan hasil kalkulasi lunar aktual.
     *
     * @param latitude  Latitude lokasi pengamat
     * @param longitude Longitude lokasi pengamat
     * @param criteria  Kriteria visibilitas hilal yang digunakan
     * @return offset dalam range -1..+1
     */
    fun calculateAutoOffset(
        latitude: Double,
        longitude: Double,
        criteria: HilalCriteria = HilalCriteria.NEO_MABIMS
    ): Int {
        return try {
            val calculator = HijriCalculator(latitude, longitude)
            val now = Date()

            // === Sumber 1: Lunar calculation (astronomical) ===
            val lunarHijri = calculator.calculateHijriDate(now, criteria)

            // === Sumber 2: Tabel statis Java ===
            val today = LocalDate.now()
            val staticHijri = HijrahDate.from(today)
            val staticDay = staticHijri.get(ChronoField.DAY_OF_MONTH)
            val staticMonth = staticHijri.get(ChronoField.MONTH_OF_YEAR)

            // === Hitung delta ===
            val delta: Int

            if (lunarHijri.month == staticMonth) {
                // Bulan sama → delta langsung
                delta = lunarHijri.day - staticDay
            } else {
                // Bulan berbeda → edge case di pergantian bulan
                // Jika lunar sudah masuk bulan baru tapi static masih bulan lama
                // Contoh: lunar = 1 Ramadhan, static = 29 Sya'ban → delta = +1
                // Atau sebaliknya: lunar = 29 Sya'ban, static = 1 Ramadhan → delta = -1
                if (lunarHijri.day < staticDay) {
                    // Lunar di awal bulan baru, static masih di akhir bulan lama
                    // Artinya static tertinggal → perlu +1 atau +2
                    delta = lunarHijri.day - staticDay + 30 // approximate month length
                    // Normalize: biasanya ini menghasilkan +1 atau +2
                } else {
                    // Lunar di akhir bulan lama, static sudah masuk bulan baru
                    // Artinya static terlalu cepat → perlu -1 atau -2
                    delta = lunarHijri.day - staticDay - 30 // approximate month length
                    // Normalize: biasanya ini menghasilkan -1 atau -2
                }
            }

            // Clamp ke range -1..+1
            val clampedOffset = delta.coerceIn(-1, 1)

            Log.d(TAG, "Lunar Hijri: ${lunarHijri.day}/${lunarHijri.month}/${lunarHijri.year}" +
                    " | Static Hijri: $staticDay/$staticMonth" +
                    " | Delta: $delta → Offset: $clampedOffset" +
                    " | Criteria: ${criteria.displayName}")

            clampedOffset
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating auto offset, falling back to 0", e)
            0 // Fallback: tidak ada offset
        }
    }
}
