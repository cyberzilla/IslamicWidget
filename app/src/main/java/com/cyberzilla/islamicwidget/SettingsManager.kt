package com.cyberzilla.islamicwidget

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)
    
    var languageCode: String get() = prefs.getString("PREF_LANGUAGE", "en") ?: "en"; set(value) = prefs.edit().putString("PREF_LANGUAGE", value).apply()

    var appTheme: String get() = prefs.getString("PREF_THEME", "SYSTEM") ?: "SYSTEM"; set(value) = prefs.edit().putString("PREF_THEME", value).apply()

    var widgetTextColor: String get() = prefs.getString("PREF_TEXT_COLOR", "#FFFFFF") ?: "#FFFFFF"; set(value) = prefs.edit().putString("PREF_TEXT_COLOR", value).apply()
    var widgetBgColor: String get() = prefs.getString("PREF_BG_COLOR", "#00000000") ?: "#00000000"; set(value) = prefs.edit().putString("PREF_BG_COLOR", value).apply()
    var widgetBgRadius: Int get() = prefs.getInt("PREF_BG_RADIUS", 16); set(value) = prefs.edit().putInt("PREF_BG_RADIUS", value).apply()

    var showClock: Boolean get() = prefs.getBoolean("PREF_SHOW_CLOCK", true); set(value) = prefs.edit().putBoolean("PREF_SHOW_CLOCK", value).apply()
    var showDate: Boolean get() = prefs.getBoolean("PREF_SHOW_DATE", true); set(value) = prefs.edit().putBoolean("PREF_SHOW_DATE", value).apply()
    var showPrayer: Boolean get() = prefs.getBoolean("PREF_SHOW_PRAYER", true); set(value) = prefs.edit().putBoolean("PREF_SHOW_PRAYER", value).apply()
    var showAdditional: Boolean get() = prefs.getBoolean("PREF_SHOW_ADDITIONAL", true); set(value) = prefs.edit().putBoolean("PREF_SHOW_ADDITIONAL", value).apply()

    var fontSizeClock: Int get() = prefs.getInt("PREF_FS_CLOCK", 36); set(value) = prefs.edit().putInt("PREF_FS_CLOCK", value).apply()
    var fontSizeDate: Int get() = prefs.getInt("PREF_FS_DATE", 12); set(value) = prefs.edit().putInt("PREF_FS_DATE", value).apply()
    var fontSizePrayer: Int get() = prefs.getInt("PREF_FS_PRAYER", 13); set(value) = prefs.edit().putInt("PREF_FS_PRAYER", value).apply()
    var fontSizeAdditional: Int get() = prefs.getInt("PREF_FS_ADDITIONAL", 11); set(value) = prefs.edit().putInt("PREF_FS_ADDITIONAL", value).apply()

    var calculationMethod: String get() = prefs.getString("PREF_CALC_METHOD", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"; set(value) = prefs.edit().putString("PREF_CALC_METHOD", value).apply()
    var hijriOffset: Int get() = prefs.getInt("PREF_HIJRI_OFFSET", 0); set(value) = prefs.edit().putInt("PREF_HIJRI_OFFSET", value).apply()
    var isDayStartAtMaghrib: Boolean get() = prefs.getBoolean("PREF_DAY_START_MAGHRIB", true); set(value) = prefs.edit().putBoolean("PREF_DAY_START_MAGHRIB", value).apply()

    // =======================================================
    // FIX: DEFAULT FORMAT TANGGAL JUGA MENYESUAIKAN INGGRIS
    // =======================================================
    var dateFormat: String get() = prefs.getString("PREF_DATE_FORMAT", "en-US{EEEE, dd MMMM yyyy}") ?: "en-US{EEEE, dd MMMM yyyy}"; set(value) = prefs.edit().putString("PREF_DATE_FORMAT", value).apply()
    var hijriFormat: String get() = prefs.getString("PREF_HIJRI_FORMAT", "en-US{dd MMMM yyyy} AH") ?: "en-US{dd MMMM yyyy} AH"; set(value) = prefs.edit().putString("PREF_HIJRI_FORMAT", value).apply()

    var isAutoSilentEnabled: Boolean get() = prefs.getBoolean("PREF_AUTO_SILENT", false); set(value) = prefs.edit().putBoolean("PREF_AUTO_SILENT", value).apply()
    var isAdzanAudioEnabled: Boolean get() = prefs.getBoolean("PREF_ADZAN_AUDIO", true); set(value) = prefs.edit().putBoolean("PREF_ADZAN_AUDIO", value).apply()
    var adzanVolume: Int get() = prefs.getInt("PREF_ADZAN_VOL", 100); set(value) = prefs.edit().putInt("PREF_ADZAN_VOL", value).apply()

    var customAdzanRegularUri: String? get() = prefs.getString("PREF_ADZAN_REGULAR_URI", null); set(value) = prefs.edit().putString("PREF_ADZAN_REGULAR_URI", value).apply()
    var customAdzanSubuhUri: String? get() = prefs.getString("PREF_ADZAN_SUBUH_URI", null); set(value) = prefs.edit().putString("PREF_ADZAN_SUBUH_URI", value).apply()
    var previewScale: Int get() = prefs.getInt("PREF_PREVIEW_SCALE", 100); set(value) = prefs.edit().putInt("PREF_PREVIEW_SCALE", value).apply()

    var fajrBefore: Int get() = prefs.getInt("fajrBefore", 0); set(value) = prefs.edit().putInt("fajrBefore", value).apply()
    var fajrAfter: Int get() = prefs.getInt("fajrAfter", 15); set(value) = prefs.edit().putInt("fajrAfter", value).apply()
    var dhuhrBefore: Int get() = prefs.getInt("dhuhrBefore", 0); set(value) = prefs.edit().putInt("dhuhrBefore", value).apply()
    var dhuhrAfter: Int get() = prefs.getInt("dhuhrAfter", 20); set(value) = prefs.edit().putInt("dhuhrAfter", value).apply()
    var fridayBefore: Int get() = prefs.getInt("fridayBefore", 0); set(value) = prefs.edit().putInt("fridayBefore", value).apply()
    var fridayAfter: Int get() = prefs.getInt("fridayAfter", 60); set(value) = prefs.edit().putInt("fridayAfter", value).apply()
    var asrBefore: Int get() = prefs.getInt("asrBefore", 0); set(value) = prefs.edit().putInt("asrBefore", value).apply()
    var asrAfter: Int get() = prefs.getInt("asrAfter", 20); set(value) = prefs.edit().putInt("asrAfter", value).apply()
    var maghribBefore: Int get() = prefs.getInt("maghribBefore", 0); set(value) = prefs.edit().putInt("maghribBefore", value).apply()
    var maghribAfter: Int get() = prefs.getInt("maghribAfter", 15); set(value) = prefs.edit().putInt("maghribAfter", value).apply()
    var ishaBefore: Int get() = prefs.getInt("ishaBefore", 0); set(value) = prefs.edit().putInt("ishaBefore", value).apply()
    var ishaAfter: Int get() = prefs.getInt("ishaAfter", 20); set(value) = prefs.edit().putInt("ishaAfter", value).apply()

    var latitude: String? get() = prefs.getString("LATITUDE", null); set(value) = prefs.edit().putString("LATITUDE", value).apply()
    var longitude: String? get() = prefs.getString("LONGITUDE", null); set(value) = prefs.edit().putString("LONGITUDE", value).apply()
    var locationName: String get() = prefs.getString("LOCATION_NAME", "Belum ada lokasi") ?: "Belum ada lokasi"; set(value) = prefs.edit().putString("LOCATION_NAME", value).apply()

    fun restoreDefaults() {
        val currentLat = latitude
        val currentLon = longitude
        val currentLocName = locationName
        prefs.edit().clear().apply()
        latitude = currentLat
        longitude = currentLon
        locationName = currentLocName
    }
}