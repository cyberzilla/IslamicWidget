package com.cyberzilla.islamicwidget.utils

import io.github.cosinekitty.astronomy.*
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

data class HilalResult(
    val isHilalVisible: Boolean,
    val moonAltitudeAtSunset: Double,
    val elongationAtSunset: Double,
    val sunsetTime: Date?,
    val moonAgeHours: Double = 0.0,
    val isMoonsetAfterSunset: Boolean = false,
    val isConjunctionBeforeSunset: Boolean = false,
    val criteriaUsed: String = "",
    val conjunctionTime: Date? = null,
    val moonsetTime: Date? = null,
    val sunAzimuth: Double = 0.0,
    val moonAzimuth: Double = 0.0,
    val lagMinutes: Double = 0.0,
    val illuminationFraction: Double = 0.0
)

data class HijriDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val monthName: String,
    val monthStartDate: Date? = null
) {
    override fun toString(): String = "$day $monthName $year H"
}

class HijriCalculator(
    private val latitude: Double,
    private val longitude: Double,
    private val elevationInMeters: Double = 0.0
) {
    private val observer = Observer(latitude, longitude, elevationInMeters)

    companion object {
        private const val SYNODIC_MONTH = 29.530588
        private const val REF_HIJRI_YEAR = 1446
        private const val REF_HIJRI_MONTH = 1

        val HIJRI_MONTH_NAMES = arrayOf(
            "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir",
            "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban",
            "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
        )
    }

    fun evaluateHilal(
        dateToEvaluate: Date,
        criteria: HilalCriteria = HilalCriteria.NEO_MABIMS
    ): HilalResult {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = dateToEvaluate
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val checkTime = Time.fromMillisecondsSince1970(calendar.timeInMillis)

        val sunsetAstroTime = searchRiseSet(Body.Sun, observer, Direction.Set, checkTime, 1.0)
            ?: return HilalResult(false, 0.0, 0.0, null, criteriaUsed = criteria.displayName)

        val moonEq = equator(Body.Moon, sunsetAstroTime, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val moonHor = horizon(sunsetAstroTime, observer, moonEq.ra, moonEq.dec, Refraction.Normal)

        val sunEq = equator(Body.Sun, sunsetAstroTime, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val sunHor = horizon(sunsetAstroTime, observer, sunEq.ra, sunEq.dec, Refraction.Normal)

        val elongation = calculateAngularDistance(sunEq.ra, sunEq.dec, moonEq.ra, moonEq.dec)

        val lastConjunction = findPreviousNewMoon(sunsetAstroTime)
        val moonAgeHours = (sunsetAstroTime.ut - lastConjunction.ut) * 24.0

        val isConjunctionBeforeSunset = lastConjunction < sunsetAstroTime

        val moonsetAstroTime = searchRiseSet(Body.Moon, observer, Direction.Set, sunsetAstroTime, 1.0)
        val isMoonsetAfterSunset = moonsetAstroTime != null && moonsetAstroTime > sunsetAstroTime

        val lagMinutes = if (moonsetAstroTime != null && moonsetAstroTime > sunsetAstroTime) {
            (moonsetAstroTime.ut - sunsetAstroTime.ut) * 24.0 * 60.0
        } else {
            0.0
        }

        val elongationRad = Math.toRadians(elongation)
        val illuminationFraction = (1.0 - cos(elongationRad)) / 2.0 * 100.0

        val isVisible = evaluateVisibility(
            criteria = criteria,
            altitude = moonHor.altitude,
            elongation = elongation,
            moonAgeHours = moonAgeHours,
            isConjunctionBeforeSunset = isConjunctionBeforeSunset,
            isMoonsetAfterSunset = isMoonsetAfterSunset
        )

        val sunsetDate = Date(sunsetAstroTime.toMillisecondsSince1970())
        val conjunctionDate = Date(lastConjunction.toMillisecondsSince1970())
        val moonsetDate = if (moonsetAstroTime != null) Date(moonsetAstroTime.toMillisecondsSince1970()) else null

        return HilalResult(
            isHilalVisible = isVisible,
            moonAltitudeAtSunset = moonHor.altitude,
            elongationAtSunset = elongation,
            sunsetTime = sunsetDate,
            moonAgeHours = moonAgeHours,
            isMoonsetAfterSunset = isMoonsetAfterSunset,
            isConjunctionBeforeSunset = isConjunctionBeforeSunset,
            criteriaUsed = criteria.displayName,
            conjunctionTime = conjunctionDate,
            moonsetTime = moonsetDate,
            sunAzimuth = sunHor.azimuth,
            moonAzimuth = moonHor.azimuth,
            lagMinutes = lagMinutes,
            illuminationFraction = illuminationFraction
        )
    }

    fun calculateHijriDate(
        date: Date,
        criteria: HilalCriteria = HilalCriteria.NEO_MABIMS
    ): HijriDate {
        val targetTime = Time.fromMillisecondsSince1970(date.time)
        val recentNewMoon = findPreviousNewMoon(targetTime)
        val monthStartGregorian = findMonthStartAfterConjunction(recentNewMoon, criteria)
        val targetDayStart = getLocalDateStart(date)

        if (targetDayStart.before(monthStartGregorian)) {
            val prevNewMoon = findPreviousNewMoon(recentNewMoon.addDays(-2.0))
            val prevMonthStart = findMonthStartAfterConjunction(prevNewMoon, criteria)
            val dayOfMonth = daysBetween(prevMonthStart, targetDayStart) + 1
            val (month, year) = getHijriMonthYear(prevNewMoon)
            return HijriDate(dayOfMonth, month, year, HIJRI_MONTH_NAMES[month - 1], prevMonthStart)
        } else {
            val dayOfMonth = daysBetween(monthStartGregorian, targetDayStart) + 1
            val (month, year) = getHijriMonthYear(recentNewMoon)
            return HijriDate(dayOfMonth, month, year, HIJRI_MONTH_NAMES[month - 1], monthStartGregorian)
        }
    }

    private fun evaluateVisibility(
        criteria: HilalCriteria,
        altitude: Double,
        elongation: Double,
        moonAgeHours: Double,
        isConjunctionBeforeSunset: Boolean,
        isMoonsetAfterSunset: Boolean
    ): Boolean {
        return when (criteria) {
            HilalCriteria.NEO_MABIMS -> altitude >= 3.0 && elongation >= 6.4
            HilalCriteria.MABIMS_LAMA -> altitude >= 2.0 && elongation >= 3.0 && moonAgeHours >= 8.0
            HilalCriteria.WUJUDUL_HILAL -> altitude > 0.0 && isConjunctionBeforeSunset
            HilalCriteria.ISTANBUL_1978 -> altitude >= 5.0 && elongation >= 8.0
            HilalCriteria.DANJON_LIMIT -> elongation >= 7.0 && altitude > 0.0
            HilalCriteria.SAAO -> altitude >= 3.5 && elongation >= 7.0
            HilalCriteria.UMM_AL_QURA -> isConjunctionBeforeSunset && isMoonsetAfterSunset
            HilalCriteria.IJTIMA_QABLA_GHURUB -> isConjunctionBeforeSunset
        }
    }

    private fun findMonthStartAfterConjunction(conjunction: Time, criteria: HilalCriteria): Date {
        for (dayOffset in 0..3) {
            val checkTime = conjunction.addDays(dayOffset.toDouble())
            val sunsetTime = searchRiseSet(Body.Sun, observer, Direction.Set, checkTime, 1.0)
                ?: continue

            if (sunsetTime < conjunction) continue

            val moonEq = equator(Body.Moon, sunsetTime, observer, EquatorEpoch.OfDate, Aberration.Corrected)
            val moonHor = horizon(sunsetTime, observer, moonEq.ra, moonEq.dec, Refraction.Normal)
            val sunEq = equator(Body.Sun, sunsetTime, observer, EquatorEpoch.OfDate, Aberration.Corrected)
            val elongation = calculateAngularDistance(sunEq.ra, sunEq.dec, moonEq.ra, moonEq.dec)
            val moonAgeHours = (sunsetTime.ut - conjunction.ut) * 24.0
            val moonsetTime = searchRiseSet(Body.Moon, observer, Direction.Set, sunsetTime, 1.0)
            val isMoonsetAfterSunset = moonsetTime != null && moonsetTime > sunsetTime

            val isVisible = evaluateVisibility(
                criteria = criteria,
                altitude = moonHor.altitude,
                elongation = elongation,
                moonAgeHours = moonAgeHours,
                isConjunctionBeforeSunset = true,
                isMoonsetAfterSunset = isMoonsetAfterSunset
            )

            if (isVisible) {
                return getNextGregorianDay(Date(sunsetTime.toMillisecondsSince1970()))
            }
        }

        val fallbackTime = conjunction.addDays(3.0)
        return getNextGregorianDay(Date(fallbackTime.toMillisecondsSince1970()))
    }

    private fun getHijriMonthYear(conjunction: Time): Pair<Int, Int> {
        val refConjunction = Time(2024, 7, 5, 22, 57, 0.0)
        val daysDiff = conjunction.ut - refConjunction.ut
        val lunationCount = round(daysDiff / SYNODIC_MONTH).toInt()
        val totalMonths = (REF_HIJRI_MONTH - 1) + lunationCount

        val hijriYear: Int
        val hijriMonth: Int

        if (totalMonths >= 0) {
            hijriYear = REF_HIJRI_YEAR + (totalMonths / 12)
            hijriMonth = (totalMonths % 12) + 1
        } else {
            val absMonths = -totalMonths
            hijriYear = REF_HIJRI_YEAR - ((absMonths + 11) / 12)
            hijriMonth = 12 - ((absMonths - 1) % 12)
        }

        return Pair(hijriMonth, hijriYear)
    }

    private fun findPreviousNewMoon(fromTime: Time): Time {
        return searchMoonPhase(0.0, fromTime, -35.0)
            ?: throw IllegalStateException("Could not find previous new moon from $fromTime")
    }

    private fun getNextGregorianDay(date: Date): Date {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.time = date
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun getLocalDateStart(date: Date): Date {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.time = date
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun daysBetween(start: Date, end: Date): Int {
        val diffMs = end.time - start.time
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun calculateAngularDistance(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double {
        val ra1Rad = Math.toRadians(ra1 * 15.0)
        val dec1Rad = Math.toRadians(dec1)
        val ra2Rad = Math.toRadians(ra2 * 15.0)
        val dec2Rad = Math.toRadians(dec2)

        val cosTheta = sin(dec1Rad) * sin(dec2Rad) + cos(dec1Rad) * cos(dec2Rad) * cos(ra1Rad - ra2Rad)
        return Math.toDegrees(acos(cosTheta.coerceIn(-1.0, 1.0)))
    }
}
