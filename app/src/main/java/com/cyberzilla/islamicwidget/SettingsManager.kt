package com.cyberzilla.islamicwidget

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

    var languageCode: String
        get() = prefs.getString("PREF_LANGUAGE", "id") ?: "id"
        set(value) = prefs.edit().putString("PREF_LANGUAGE", value).apply()

    var widgetFontSize: Int
        get() = prefs.getInt("PREF_FONT_SIZE", 14)
        set(value) = prefs.edit().putInt("PREF_FONT_SIZE", value).apply()

    var widgetTextColor: String
        get() = prefs.getString("PREF_TEXT_COLOR", "#FFFFFF") ?: "#FFFFFF"
        set(value) = prefs.edit().putString("PREF_TEXT_COLOR", value).apply()

    var widgetBgColor: String
        get() = prefs.getString("PREF_BG_COLOR", "#B3000000") ?: "#B3000000"
        set(value) = prefs.edit().putString("PREF_BG_COLOR", value).apply()

    var widgetBgRadius: Int
        get() = prefs.getInt("PREF_BG_RADIUS", 16)
        set(value) = prefs.edit().putInt("PREF_BG_RADIUS", value).apply()

    var calculationMethod: String
        get() = prefs.getString("PREF_CALC_METHOD", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        set(value) = prefs.edit().putString("PREF_CALC_METHOD", value).apply()

    var hijriOffset: Int
        get() = prefs.getInt("PREF_HIJRI_OFFSET", 0)
        set(value) = prefs.edit().putInt("PREF_HIJRI_OFFSET", value).apply()

    var isDayStartAtMaghrib: Boolean
        get() = prefs.getBoolean("PREF_DAY_START_MAGHRIB", true)
        set(value) = prefs.edit().putBoolean("PREF_DAY_START_MAGHRIB", value).apply()

    var dateFormat: String
        get() = prefs.getString("PREF_DATE_FORMAT", "EEEE, dd MMMM yyyy") ?: "EEEE, dd MMMM yyyy"
        set(value) = prefs.edit().putString("PREF_DATE_FORMAT", value).apply()

    var isAutoSilentEnabled: Boolean
        get() = prefs.getBoolean("PREF_AUTO_SILENT", true)
        set(value) = prefs.edit().putBoolean("PREF_AUTO_SILENT", value).apply()

    // --- DURASI SILENT PER SHOLAT (BARU) ---
    var fajrBefore: Int get() = prefs.getInt("fajrBefore", 0); set(value) = prefs.edit().putInt("fajrBefore", value).apply()
    var fajrAfter: Int get() = prefs.getInt("fajrAfter", 15); set(value) = prefs.edit().putInt("fajrAfter", value).apply()

    var dhuhrBefore: Int get() = prefs.getInt("dhuhrBefore", 0); set(value) = prefs.edit().putInt("dhuhrBefore", value).apply()
    var dhuhrAfter: Int get() = prefs.getInt("dhuhrAfter", 20); set(value) = prefs.edit().putInt("dhuhrAfter", value).apply()
    var dhuhrFriday: Int get() = prefs.getInt("dhuhrFriday", 60); set(value) = prefs.edit().putInt("dhuhrFriday", value).apply()

    var asrBefore: Int get() = prefs.getInt("asrBefore", 0); set(value) = prefs.edit().putInt("asrBefore", value).apply()
    var asrAfter: Int get() = prefs.getInt("asrAfter", 20); set(value) = prefs.edit().putInt("asrAfter", value).apply()

    var maghribBefore: Int get() = prefs.getInt("maghribBefore", 0); set(value) = prefs.edit().putInt("maghribBefore", value).apply()
    var maghribAfter: Int get() = prefs.getInt("maghribAfter", 15); set(value) = prefs.edit().putInt("maghribAfter", value).apply()

    var ishaBefore: Int get() = prefs.getInt("ishaBefore", 0); set(value) = prefs.edit().putInt("ishaBefore", value).apply()
    var ishaAfter: Int get() = prefs.getInt("ishaAfter", 20); set(value) = prefs.edit().putInt("ishaAfter", value).apply()

    var latitude: String? get() = prefs.getString("LATITUDE", null); set(value) = prefs.edit().putString("LATITUDE", value).apply()
    var longitude: String? get() = prefs.getString("LONGITUDE", null); set(value) = prefs.edit().putString("LONGITUDE", value).apply()

    fun restoreDefaults() {
        val currentLat = latitude
        val currentLon = longitude
        prefs.edit().clear().apply()
        latitude = currentLat
        longitude = currentLon
    }
}