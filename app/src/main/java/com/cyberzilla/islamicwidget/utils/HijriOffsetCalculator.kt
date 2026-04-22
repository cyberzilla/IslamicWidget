package com.cyberzilla.islamicwidget.utils

import android.util.Log
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date

object HijriOffsetCalculator {

    private const val TAG = "HijriOffsetCalc"

    fun calculateAutoOffset(
        latitude: Double,
        longitude: Double,
        criteria: HilalCriteria = HilalCriteria.NEO_MABIMS
    ): Int {
        return try {
            val calculator = HijriCalculator(latitude, longitude)
            val now = Date()

            val lunarHijri = calculator.calculateHijriDate(now, criteria)

            val today = LocalDate.now()
            val staticHijri = HijrahDate.from(today)
            val staticDay = staticHijri.get(ChronoField.DAY_OF_MONTH)
            val staticMonth = staticHijri.get(ChronoField.MONTH_OF_YEAR)

            val delta: Int

            if (lunarHijri.month == staticMonth) {
                delta = lunarHijri.day - staticDay
            } else {
                if (lunarHijri.day < staticDay) {
                    delta = lunarHijri.day - staticDay + 30
                } else {
                    delta = lunarHijri.day - staticDay - 30
                }
            }

            val clampedOffset = delta.coerceIn(-1, 1)

            Log.d(TAG, "Lunar Hijri: ${lunarHijri.day}/${lunarHijri.month}/${lunarHijri.year}" +
                    " | Static Hijri: $staticDay/$staticMonth" +
                    " | Delta: $delta → Offset: $clampedOffset" +
                    " | Criteria: ${criteria.displayName}")

            clampedOffset
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating auto offset, falling back to 0", e)
            0
        }
    }
}
