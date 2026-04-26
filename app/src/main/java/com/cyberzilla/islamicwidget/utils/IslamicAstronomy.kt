package com.cyberzilla.islamicwidget.utils

import io.github.cosinekitty.astronomy.*
import java.util.Date
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

// =================================================================================
// 1. ENUM & DATA CLASSES UNTUK WAKTU SHOLAT
// =================================================================================

enum class Madhab(val shadowFactor: Double) {
    SHAFI(1.0),
    HANAFI(2.0)
}

enum class HighLatitudeRule {
    MIDDLE_OF_THE_NIGHT,
    SEVENTH_OF_THE_NIGHT,
    TWILIGHT_ANGLE
}

data class PrayerAdjustments(
    val imsak: Int = 0,
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuha: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0
)

enum class Prayer {
    NONE, IMSAK, FAJR, SUNRISE, DHUHA, DHUHR, ASR, MAGHRIB, ISHA
}

enum class CalculationMethod(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0,
    val maghribAngle: Double = 0.0,
    val maghribIntervalMinutes: Int = 0,
    val safetyOffsetMinutes: Int = 0 
) {
    KEMENAG(20.0, 18.0, 0, 0.0, 0, 2),
    JAKIM(20.0, 18.0),
    SINGAPORE(20.0, 18.0),
    MUSLIM_WORLD_LEAGUE(18.0, 17.0),
    NORTH_AMERICA(15.0, 15.0),
    MOON_SIGHTING_COMMITTEE(18.0, 18.0),
    EGYPTIAN(19.5, 17.5),
    KARACHI(18.0, 18.0),
    UMM_AL_QURA(18.5, 0.0, 90),
    DUBAI(18.2, 18.2),
    QATAR(18.0, 0.0, 90),
    KUWAIT(18.0, 17.5),
    TEHRAN(17.7, 14.0, 0, 4.5, 0),
    TURKEY(18.0, 17.0),
    MOROCCO(19.0, 17.0, 0, 0.0, 5),
    OTHER(0.0, 0.0)
}

data class SunnahTimes(
    val middleOfTheNight: Date,
    val lastThirdOfTheNight: Date
)

data class PrayerTimes(
    val date: Date,
    val imsak: Date,
    val fajr: Date,
    val sunrise: Date,
    val dhuha: Date,
    val dhuhr: Date,
    val asr: Date,
    val maghrib: Date,
    val isha: Date
) {
    val fastingDurationMinutes: Long
        get() = (maghrib.time - fajr.time) / 60000L

    fun currentPrayer(time: Date = Date()): Prayer {
        return when {
            time.before(imsak) -> Prayer.NONE
            time.before(fajr) -> Prayer.IMSAK
            time.before(sunrise) -> Prayer.FAJR
            time.before(dhuha) -> Prayer.SUNRISE
            time.before(dhuhr) -> Prayer.DHUHA
            time.before(asr) -> Prayer.DHUHR
            time.before(maghrib) -> Prayer.ASR
            time.before(isha) -> Prayer.MAGHRIB
            else -> Prayer.ISHA
        }
    }

    fun nextPrayer(time: Date = Date()): Prayer {
        return when {
            time.before(imsak) -> Prayer.IMSAK
            time.before(fajr) -> Prayer.FAJR
            time.before(sunrise) -> Prayer.SUNRISE
            time.before(dhuha) -> Prayer.DHUHA
            time.before(dhuhr) -> Prayer.DHUHR
            time.before(asr) -> Prayer.ASR
            time.before(maghrib) -> Prayer.MAGHRIB
            time.before(isha) -> Prayer.ISHA
            else -> Prayer.NONE
        }
    }
}

// =================================================================================
// 2. ENUM & DATA CLASSES UNTUK VISIBILITAS HILAL & KALENDER HIJRIAH
// =================================================================================

enum class HilalCriteria(val displayName: String, val description: String) {
    NEO_MABIMS("Neo MABIMS", "Ketinggian >= 3° dan Elongasi >= 6.4°"),
    MABIMS_LAMA("MABIMS Lama", "Ketinggian >= 2°, Elongasi >= 3°, dan Umur >= 8 Jam"),
    WUJUDUL_HILAL("Wujudul Hilal", "Ketinggian > 0° dan Konjungsi sebelum matahari terbenam"),
    ISTANBUL_1978("Istanbul 1978", "Ketinggian >= 5° dan Elongasi >= 8°"),
    DANJON_LIMIT("Danjon Limit", "Elongasi >= 7° dan Ketinggian > 0°"),
    SAAO("SAAO Limit", "Ketinggian >= 3.5° dan Elongasi >= 7°"),
    UMM_AL_QURA("Umm Al-Qura", "Bulan terbenam setelah matahari dan konjungsi sebelum maghrib"),
    IJTIMA_QABLA_GHURUB("Ijtima Qabla Ghurub", "Konjungsi terjadi sebelum matahari terbenam"),
    YALLOP("Yallop (1998)", "Indeks q >= -0.014 (Mata telanjang)"),
    ODEH("Odeh (2006)", "Indeks V >= 2.0 (Mata telanjang atau dengan optik)"),
    ILYAS("Ilyas (1988/1994)", "Ketinggian >= 4° dan Elongasi >= 10.5°");

    companion object {
        fun fromName(name: String): HilalCriteria? {
            return values().find { 
                it.name.equals(name, ignoreCase = true) || 
                it.displayName.equals(name, ignoreCase = true) 
            }
        }
    }
}

data class HilalReport(
    val calculationDate: Date,
    val conjunctionTime: Date?,
    val sunsetTime: Date?,
    val moonsetTime: Date?,
    val sunAzimuth: Double,
    val moonAzimuth: Double,
    val moonGeometricAltitude: Double,
    val moonApparentAltitude: Double,
    val elongationTopo: Double,     
    val elongationGeo: Double,      
    val moonAgeHours: Double,
    val illuminationFraction: Double,
    val yallopQ: Double,
    val odehV: Double,
    val isConjunctionBeforeSunset: Boolean,
    val isMoonsetAfterSunset: Boolean,
    val criteriaUsed: HilalCriteria,
    val isVisible: Boolean
)

val HIJRI_MONTH_NAMES = arrayOf(
    "Muharram", "Safar", "Rabi'ul Awal", "Rabi'ul Akhir",
    "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban",
    "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
)

data class HijriDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val monthName: String
) {
    override fun toString(): String = "$day $monthName $year H"
}

data class HilalResult(
    val hijriDate: HijriDate,
    val hilalReport: HilalReport
)

// =================================================================================
// 3. ENGINE UTAMA : IslamicAstronomy
// =================================================================================

object IslamicAstronomy {
    
    private const val SUN_REFRACTION_ANGLE = -0.8333
    private const val MECCA_LATITUDE = 21.4225241
    private const val MECCA_LONGITUDE = 39.8261818

    fun calculateQibla(latitude: Double, longitude: Double): Double {
        val meccaLat = Math.toRadians(MECCA_LATITUDE)
        val meccaLng = Math.toRadians(MECCA_LONGITUDE)
        val lat = Math.toRadians(latitude)
        val lng = Math.toRadians(longitude)

        val y = sin(meccaLng - lng)
        val x = cos(lat) * tan(meccaLat) - sin(lat) * cos(meccaLng - lng)
        
        var qibla = Math.toDegrees(atan2(y, x))
        if (qibla < 0) qibla += 360.0
        return qibla
    }

    fun getSunnahTimes(today: PrayerTimes, tomorrow: PrayerTimes): SunnahTimes {
        val maghribTime = today.maghrib.time
        val nextFajrTime = tomorrow.fajr.time
        val nightDuration = nextFajrTime - maghribTime

        val middle = maghribTime + (nightDuration / 2)
        val lastThird = maghribTime + (nightDuration * 2 / 3)

        return SunnahTimes(Date(middle), Date(lastThird))
    }

    fun calculatePrayerTimes(
        date: Date,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        method: CalculationMethod,
        madhab: Madhab = Madhab.SHAFI,
        highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
        adjustments: PrayerAdjustments = PrayerAdjustments(),
        customFajrAngle: Double = 18.0,
        customIshaAngle: Double = 18.0,
        isRamadan: Boolean = false,
        imsakOffsetMinutes: Int = 10,
        dhuhaOffsetMinutes: Int = 15
    ): PrayerTimes {
        val observer = Observer(latitude, longitude, elevation)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = date
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val noonOffsetHours = -longitude / 15.0
        val approxNoon = Time(year, month, day, 12, 0, 0.0).addDays(noonOffsetHours / 24.0)

        val activeFajrAngle = if (method == CalculationMethod.OTHER) customFajrAngle else method.fajrAngle
        val activeIshaAngle = if (method == CalculationMethod.OTHER) customIshaAngle else method.ishaAngle

        val activeIshaInterval = if (method == CalculationMethod.UMM_AL_QURA && isRamadan) 120 else method.ishaIntervalMinutes

        val transit = searchHourAngle(Body.Sun, observer, 0.0, approxNoon, -1)
        val dhuhrTime = transit.time
        val sunriseTime = searchAltitude(Body.Sun, observer, Direction.Rise, dhuhrTime.addDays(-0.5), 1.0, SUN_REFRACTION_ANGLE)
        val sunsetTime = searchAltitude(Body.Sun, observer, Direction.Set, dhuhrTime, 1.0, SUN_REFRACTION_ANGLE)

        val dhuhrEq = equator(Body.Sun, dhuhrTime, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val shadowTarget = madhab.shadowFactor + tan(abs((latitude * Math.PI / 180.0) - (dhuhrEq.dec * Math.PI / 180.0)))
        val asrTime = searchAltitude(Body.Sun, observer, Direction.Set, dhuhrTime, 1.0, (atan(1.0 / shadowTarget) * 180.0 / Math.PI))

        var maghribTime = if (method.maghribAngle > 0.0) {
            searchAltitude(Body.Sun, observer, Direction.Set, dhuhrTime, 1.0, -method.maghribAngle)
        } else {
            sunsetTime?.addDays(method.maghribIntervalMinutes / 1440.0)
        }

        var fajrTime = searchAltitude(Body.Sun, observer, Direction.Rise, dhuhrTime.addDays(-0.5), 1.0, -activeFajrAngle)
        var ishaTime = if (activeIshaInterval > 0) {
            maghribTime?.addDays(activeIshaInterval / 1440.0)
        } else {
            searchAltitude(Body.Sun, observer, Direction.Set, dhuhrTime, 1.0, -activeIshaAngle)
        }

        if ((fajrTime == null || ishaTime == null) && sunriseTime != null && sunsetTime != null) {
            val prevSunset = searchAltitude(Body.Sun, observer, Direction.Set, sunriseTime.addDays(-0.5), 1.0, SUN_REFRACTION_ANGLE)
            val prevNightDuration = if (prevSunset != null) sunriseTime.ut - prevSunset.ut else 0.0
            val nextSunrise = searchAltitude(Body.Sun, observer, Direction.Rise, sunsetTime, 1.0, SUN_REFRACTION_ANGLE)
            val nextNightDuration = if (nextSunrise != null) nextSunrise.ut - sunsetTime.ut else 0.0

            val portionFajr = calculateHighLatitudePortion(prevNightDuration, activeFajrAngle, highLatitudeRule)
            val portionIsha = calculateHighLatitudePortion(nextNightDuration, activeIshaAngle, highLatitudeRule)

            if (fajrTime == null) fajrTime = sunriseTime.addDays(-portionFajr)
            if (ishaTime == null && activeIshaInterval == 0) ishaTime = sunsetTime.addDays(portionIsha)
        }

        val baseOffsetMillis = method.safetyOffsetMinutes * 60000L
        val fallbackTime = dhuhrTime.toDate()

        val finalFajr = Date((fajrTime?.toDate()?.time ?: fallbackTime.time) + baseOffsetMillis + (adjustments.fajr * 60000L))
        val finalSunrise = Date((sunriseTime?.toDate()?.time ?: fallbackTime.time) + baseOffsetMillis + (adjustments.sunrise * 60000L))
        
        val finalImsak = Date(finalFajr.time - (imsakOffsetMinutes * 60000L) + (adjustments.imsak * 60000L))
        val finalDhuha = Date(finalSunrise.time + (dhuhaOffsetMinutes * 60000L) + (adjustments.dhuha * 60000L))

        return PrayerTimes(
            date = date,
            imsak = finalImsak,
            fajr = finalFajr,
            sunrise = finalSunrise,
            dhuha = finalDhuha,
            dhuhr = Date(dhuhrTime.toDate().time + baseOffsetMillis + (adjustments.dhuhr * 60000L)),
            asr = Date((asrTime?.toDate()?.time ?: fallbackTime.time) + baseOffsetMillis + (adjustments.asr * 60000L)),
            maghrib = Date((maghribTime?.toDate()?.time ?: fallbackTime.time) + baseOffsetMillis + (adjustments.maghrib * 60000L)),
            isha = Date((ishaTime?.toDate()?.time ?: fallbackTime.time) + baseOffsetMillis + (adjustments.isha * 60000L))
        )
    }

    // --- HIJRI & HILAL VISIBILITY ENGINE ---

    /**
     * Mencari titik fase Konjungsi (New Moon) paling terakhir yang terjadi sebelum tanggal referensi.
     */
    fun findPreviousNewMoon(date: Date): Time {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = date
        val t = Time(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), 12, 0, 0.0)
        
        val currentPhase = moonPhase(t)
        val daysSinceApproxNewMoon = (currentPhase / 360.0) * 29.530588
        val approxNewMoonTime = t.addDays(-daysSinceApproxNewMoon)
        
        // Pencarian presisi fase 0.0
        return searchMoonPhase(0.0, approxNewMoonTime.addDays(-2.5), 5.0) ?: approxNewMoonTime
    }

    /**
     * Mengkonversi titik Julian Date dari konjungsi menjadi representasi Bulan & Tahun Hijriah
     * berdasarkan Epok Hijriah standar (16 Juli 622 M / JD 1948439.5)
     */
    fun getHijriMonthYear(conjunctionTime: Time): Pair<Int, Int> {
        val hijriEpochJD = 1948439.5
        val conjunctionJD = conjunctionTime.ut + 2451545.0 // Offset ke J2000
        val synodicMonth = 29.53058868
        
        val monthsSinceEpoch = round((conjunctionJD - hijriEpochJD) / synodicMonth).toInt()
        
        var year = (monthsSinceEpoch / 12) + 1
        var month = (monthsSinceEpoch % 12) + 1
        
        if (month <= 0) {
            month += 12
            year -= 1
        }
        
        return Pair(month, year)
    }

    /**
     * Menemukan awal hari ke-1 (Waktu Maghrib) untuk bulan Hijriah baru setelah terjadinya konjungsi,
     * dengan mengevaluasi kriteria hilal regional.
     */
    fun findMonthStartAfterConjunction(
        conjunctionTime: Time,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        criteria: HilalCriteria
    ): Date {
        val observer = Observer(latitude, longitude, elevation)
        
        // Dapatkan sunset pada hari konjungsi tersebut
        val sunset = searchAltitude(Body.Sun, observer, Direction.Set, conjunctionTime, 1.0, SUN_REFRACTION_ANGLE) 
            ?: conjunctionTime.addDays(0.25)
            
        // Evaluasi apakah Hilal memenuhi kriteria di sunset pertama ini
        val reportDay1 = calculateHilal(sunset.toDate(), latitude, longitude, elevation, criteria)
        
        return if (reportDay1.isVisible) {
            // Hilal terlihat. 1 bulan baru dimulai SAAT sunset ini.
            reportDay1.sunsetTime ?: sunset.toDate()
        } else {
            // Istikmal (digenapkan 30 hari). Bulan baru dimulai di Maghrib esok harinya.
            val nextSunset = searchAltitude(Body.Sun, observer, Direction.Set, sunset.addDays(1.0), 1.0, SUN_REFRACTION_ANGLE) 
                ?: sunset.addDays(1.0)
            nextSunset.toDate()
        }
    }

    /**
     * Mengkalkulasi dinamis hari ke-berapa di dalam bulan Hijriah berjalan 
     * berdasarkan selisih waktu mutlak (Maghrib ke Maghrib).
     */
    fun calculateAutoOffset(targetDate: Date, monthStartDate: Date): Int {
        val diffMillis = targetDate.time - monthStartDate.time
        if (diffMillis < 0) return 1 
        
        val diffDays = floor(diffMillis / (1000.0 * 60 * 60 * 24)).toInt()
        return diffDays + 1
    }

    /**
     * Fungsi utama untuk mengkonversi tanggal Gregorian (Date) menjadi kalender Hijriah
     * berdasarkan kriteria astronomi dinamis (Hilal Visibility).
     */
    fun calculateHijriDate(
        date: Date,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        criteria: HilalCriteria
    ): HilalResult {
        val observer = Observer(latitude, longitude, elevation)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        
        val targetNoon = Time(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), 12, 0, 0.0)
            .addDays(-longitude / 15.0 / 24.0)
        val targetSunset = searchAltitude(Body.Sun, observer, Direction.Set, targetNoon, 1.0, SUN_REFRACTION_ANGLE)?.toDate() ?: date

        var currentConjunction = findPreviousNewMoon(date)
        var (month, year) = getHijriMonthYear(currentConjunction)
        var monthStartDate = findMonthStartAfterConjunction(currentConjunction, latitude, longitude, elevation, criteria)

        // Jika waktu evaluasi ternyata masih sebelum bulan baru dimulai (Maghrib),
        // maka jatuhkan ke siklus bulan lunasi sebelumnya.
        if (date.time < monthStartDate.time) {
            val prevDate = Date(currentConjunction.toDate().time - (29.5 * 86400000L).toLong())
            currentConjunction = findPreviousNewMoon(prevDate)
            val prevMY = getHijriMonthYear(currentConjunction)
            month = prevMY.first
            year = prevMY.second
            monthStartDate = findMonthStartAfterConjunction(currentConjunction, latitude, longitude, elevation, criteria)
        }

        val day = calculateAutoOffset(date, monthStartDate)
        val hijriDate = HijriDate(day, month, year, HIJRI_MONTH_NAMES[month - 1])
        val hilalReport = calculateHilal(date, latitude, longitude, elevation, criteria)

        return HilalResult(hijriDate, hilalReport)
    }

    fun calculateHilal(
        date: Date,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        criteria: HilalCriteria
    ): HilalReport {
        val observer = Observer(latitude, longitude, elevation)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = date
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val noonOffsetHours = -longitude / 15.0
        val approxNoon = Time(year, month, day, 12, 0, 0.0).addDays(noonOffsetHours / 24.0)

        val sunsetTime = searchAltitude(Body.Sun, observer, Direction.Set, approxNoon, 1.0, SUN_REFRACTION_ANGLE)
        val moonsetTime = searchAltitude(Body.Moon, observer, Direction.Set, approxNoon, 1.0, SUN_REFRACTION_ANGLE)

        val evaluationTime = sunsetTime ?: approxNoon
        val conjunctionTime = findPreviousNewMoon(evaluationTime.toDate())

        val sunEq = equator(Body.Sun, evaluationTime, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val sunHor = horizon(evaluationTime, observer, sunEq.ra, sunEq.dec, Refraction.Normal)

        val moonEqGeo = equator(Body.Moon, evaluationTime, observer, EquatorEpoch.OfDate, Aberration.None)
        val moonEqTopo = equator(Body.Moon, evaluationTime, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        
        val moonHorGeometric = horizon(evaluationTime, observer, moonEqGeo.ra, moonEqGeo.dec, Refraction.None)
        val moonHorApparent = horizon(evaluationTime, observer, moonEqTopo.ra, moonEqTopo.dec, Refraction.Normal)

        val elongationGeo = angleFromSun(Body.Moon, evaluationTime)
        
        val sv = sunEq.vec; val mv = moonEqTopo.vec
        val dotProduct = sv.x * mv.x + sv.y * mv.y + sv.z * mv.z
        val magSun = sv.length()
        val magMoon = mv.length()
        val elongationTopo = acos((dotProduct / (magSun * magMoon)).coerceIn(-1.0, 1.0)) * 180.0 / PI

        val illuminationInfo = illumination(Body.Moon, evaluationTime)
        val phaseFraction = illuminationInfo.phaseFraction * 100.0
        
        val ageHours = (evaluationTime.ut - conjunctionTime.ut) * 24.0

        val alt = moonHorApparent.altitude
        val elo = elongationTopo
        
        val moonSD = 15.5 
        val W = moonSD * (1.0 - cos(elo * PI / 180.0)) 
        val ARCV = alt - sunHor.altitude 
        
        val yallopQ = (ARCV - (11.8371 - 6.3226 * W + 0.7319 * W.pow(2) - 0.1018 * W.pow(3))) / 10.0
        val odehV = ARCV - (7.1651 - 6.3226 * W + 0.7319 * W.pow(2) - 0.1018 * W.pow(3))

        val isConjBefore = ageHours > 0.0
        val isMoonsetAfter = if (sunsetTime != null && moonsetTime != null) moonsetTime.ut > sunsetTime.ut else false

        val isVisible = when (criteria) {
            HilalCriteria.NEO_MABIMS -> alt >= 3.0 && elo >= 6.4
            HilalCriteria.MABIMS_LAMA -> alt >= 2.0 && elo >= 3.0 && ageHours >= 8.0
            HilalCriteria.WUJUDUL_HILAL -> alt > 0.0 && isConjBefore
            HilalCriteria.ISTANBUL_1978 -> alt >= 5.0 && elo >= 8.0
            HilalCriteria.DANJON_LIMIT -> elo >= 7.0 && alt > 0.0
            HilalCriteria.SAAO -> alt >= 3.5 && elo >= 7.0
            HilalCriteria.UMM_AL_QURA -> isConjBefore && isMoonsetAfter
            HilalCriteria.IJTIMA_QABLA_GHURUB -> isConjBefore
            HilalCriteria.YALLOP -> yallopQ >= -0.014 
            HilalCriteria.ODEH -> odehV >= 2.0 
            HilalCriteria.ILYAS -> alt >= 4.0 && elo >= 10.5
        }

        return HilalReport(
            calculationDate = date,
            conjunctionTime = conjunctionTime.toDate(),
            sunsetTime = sunsetTime?.toDate(),
            moonsetTime = moonsetTime?.toDate(),
            sunAzimuth = sunHor.azimuth,
            moonAzimuth = moonHorApparent.azimuth,
            moonGeometricAltitude = moonHorGeometric.altitude,
            moonApparentAltitude = alt,
            elongationTopo = elo,
            elongationGeo = elongationGeo,
            moonAgeHours = ageHours,
            illuminationFraction = phaseFraction,
            yallopQ = yallopQ,
            odehV = odehV,
            isConjunctionBeforeSunset = isConjBefore,
            isMoonsetAfterSunset = isMoonsetAfter,
            criteriaUsed = criteria,
            isVisible = isVisible
        )
    }

    /**
     * Menghitung delta offset antara hasil kalkulasi astronomi dan java.time.chrono.HijrahDate.
     * Mengembalikan -1, 0, atau +1 untuk digunakan pada widget.
     */
    fun calculateHijriOffset(
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        criteria: HilalCriteria
    ): Int {
        return try {
            val now = Date()
            val result = calculateHijriDate(now, latitude, longitude, elevation, criteria)

            val today = java.time.LocalDate.now()
            val staticHijri = java.time.chrono.HijrahDate.from(today)
            val staticDay = staticHijri.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
            val staticMonth = staticHijri.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)

            val lunarDay = result.hijriDate.day
            val lunarMonth = result.hijriDate.month

            val delta = if (lunarMonth == staticMonth) {
                lunarDay - staticDay
            } else {
                if (lunarDay < staticDay) lunarDay - staticDay + 30
                else lunarDay - staticDay - 30
            }

            delta.coerceIn(-1, 1)
        } catch (e: Exception) {
            0
        }
    }

    private fun calculateHighLatitudePortion(nightDuration: Double, angle: Double, rule: HighLatitudeRule): Double {
        return when (rule) {
            HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> nightDuration / 2.0
            HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> nightDuration / 7.0
            HighLatitudeRule.TWILIGHT_ANGLE -> nightDuration * (angle / 60.0)
        }
    }

    private fun Time.toDate(): Date {
        return Date(this.toMillisecondsSince1970())
    }

    // =================================================================================
    // ECLIPSE PREDICTION ENGINE (Sholat Gerhana Kusuf/Khusuf)
    // =================================================================================

    data class EclipseReminder(
        val type: String,           // "SOLAR" atau "LUNAR"
        val kind: EclipseKind,      // Total, Partial, Annular (no Penumbral)
        val peakTime: Date,         // Waktu puncak gerhana (UTC -> local)
        val daysUntil: Int          // 0 = hari ini, 1 = besok
    )

    /**
     * Mencari gerhana bulan dan matahari yang akan datang dan terlihat dari lokasi user.
     * Hanya mengembalikan gerhana yang terjadi dalam [lookAheadDays] hari ke depan.
     * Gerhana penumbra difilter (tidak disyariatkan sholat gerhana).
     */
    fun getUpcomingEclipses(
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        lookAheadDays: Int = 1
    ): List<EclipseReminder> {
        val results = mutableListOf<EclipseReminder>()
        val observer = Observer(latitude, longitude, elevation)
        val now = Date()
        val nowTime = Time.fromMillisecondsSince1970(now.time)
        val cutoffMillis = now.time + (lookAheadDays.toLong() + 1) * 86400000L

        try {
            // --- Gerhana Bulan (Lunar Eclipse) ---
            // Gerhana bulan terlihat dari seluruh sisi malam bumi
            val lunarEclipse = searchLunarEclipse(nowTime)
            val lunarPeakDate = lunarEclipse.peak.toDate()
            if (lunarPeakDate.time <= cutoffMillis && lunarEclipse.kind != EclipseKind.Penumbral) {
                val daysUntil = ((lunarPeakDate.time - now.time) / 86400000L).toInt().coerceAtLeast(0)
                results.add(
                    EclipseReminder(
                        type = "LUNAR",
                        kind = lunarEclipse.kind,
                        peakTime = lunarPeakDate,
                        daysUntil = daysUntil
                    )
                )
            }
        } catch (e: Exception) {
            // Silently ignore lunar eclipse search errors
        }

        try {
            // --- Gerhana Matahari Lokal (Solar Eclipse visible from user location) ---
            val solarEclipse = searchLocalSolarEclipse(nowTime, observer)
            val solarPeakDate = solarEclipse.peak.time.toDate()
            if (solarPeakDate.time <= cutoffMillis && solarEclipse.peak.altitude > 0.0) {
                val daysUntil = ((solarPeakDate.time - now.time) / 86400000L).toInt().coerceAtLeast(0)
                results.add(
                    EclipseReminder(
                        type = "SOLAR",
                        kind = solarEclipse.kind,
                        peakTime = solarPeakDate,
                        daysUntil = daysUntil
                    )
                )
            }
        } catch (e: Exception) {
            // Silently ignore solar eclipse search errors
        }

        return results
    }
}