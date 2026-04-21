package com.cyberzilla.islamicwidget

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("IslamicWidgetPrefs", Context.MODE_PRIVATE)

    var languageCode: String
        get() = prefs.getString("languageCode", "en") ?: "en"
        set(value) = prefs.edit().putString("languageCode", value).apply()

    var appTheme: String
        get() = prefs.getString("appTheme", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("appTheme", value).apply()

    var calculationMethod: String
        get() = prefs.getString("calculationMethod", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        set(value) = prefs.edit().putString("calculationMethod", value).apply()

    var previewScale: Int
        get() = prefs.getInt("previewScale", 85)
        set(value) = prefs.edit().putInt("previewScale", value).apply()

    var latitude: String?
        get() = prefs.getString("latitude", null)
        set(value) = prefs.edit().putString("latitude", value).apply()

    var longitude: String?
        get() = prefs.getString("longitude", null)
        set(value) = prefs.edit().putString("longitude", value).apply()

    var locationName: String
        get() = prefs.getString("locationName", context.getString(R.string.default_location)) ?: context.getString(R.string.default_location)
        set(value) = prefs.edit().putString("locationName", value).apply()

    var showClock: Boolean
        get() = prefs.getBoolean("showClock", true)
        set(value) = prefs.edit().putBoolean("showClock", value).apply()

    var showDate: Boolean
        get() = prefs.getBoolean("showDate", true)
        set(value) = prefs.edit().putBoolean("showDate", value).apply()

    var showPrayer: Boolean
        get() = prefs.getBoolean("showPrayer", true)
        set(value) = prefs.edit().putBoolean("showPrayer", value).apply()

    var showAdditional: Boolean
        get() = prefs.getBoolean("showAdditional", true)
        set(value) = prefs.edit().putBoolean("showAdditional", value).apply()

    var fontSizeClock: Int
        get() = prefs.getInt("fontSizeClock", 35)
        set(value) = prefs.edit().putInt("fontSizeClock", value).apply()

    var fontSizeDate: Int
        get() = prefs.getInt("fontSizeDate", 19)
        set(value) = prefs.edit().putInt("fontSizeDate", value).apply()

    var fontSizePrayer: Int
        get() = prefs.getInt("fontSizePrayer", 16)
        set(value) = prefs.edit().putInt("fontSizePrayer", value).apply()

    var fontSizeAdditional: Int
        get() = prefs.getInt("fontSizeAdditional", 15)
        set(value) = prefs.edit().putInt("fontSizeAdditional", value).apply()

    var widgetBgRadius: Int
        get() = prefs.getInt("widgetBgRadius", 16)
        set(value) = prefs.edit().putInt("widgetBgRadius", value).apply()

    var hijriOffset: Int
        get() = prefs.getInt("hijriOffset", 0)
        set(value) = prefs.edit().putInt("hijriOffset", value).apply()

    var widgetTextColor: String
        get() = prefs.getString("widgetTextColor", "#FFFFFF") ?: "#FFFFFF"
        set(value) = prefs.edit().putString("widgetTextColor", value).apply()

    var widgetBgColor: String
        get() = prefs.getString("widgetBgColor", "#00000000") ?: "#00000000"
        set(value) = prefs.edit().putString("widgetBgColor", value).apply()

    var isDayStartAtMaghrib: Boolean
        get() = prefs.getBoolean("isDayStartAtMaghrib", true)
        set(value) = prefs.edit().putBoolean("isDayStartAtMaghrib", value).apply()

    var dateFormat: String
        get() = prefs.getString("dateFormat", "en-US{EEEE, dd MMMM yyyy}") ?: "en-US{EEEE, dd MMMM yyyy}"
        set(value) = prefs.edit().putString("dateFormat", value).apply()

    var hijriFormat: String
        get() = prefs.getString("hijriFormat", "en-US{dd MMMM yyyy} H") ?: "en-US{dd MMMM yyyy} H"
        set(value) = prefs.edit().putString("hijriFormat", value).apply()

    var isAutoSilentEnabled: Boolean
        get() = prefs.getBoolean("isAutoSilentEnabled", false)
        set(value) = prefs.edit().putBoolean("isAutoSilentEnabled", value).apply()

    var fajrBefore: Int get() = prefs.getInt("fajrBefore", 0); set(value) = prefs.edit().putInt("fajrBefore", value).apply()
    var fajrAfter: Int get() = prefs.getInt("fajrAfter", 45); set(value) = prefs.edit().putInt("fajrAfter", value).apply()
    var dhuhrBefore: Int get() = prefs.getInt("dhuhrBefore", 0); set(value) = prefs.edit().putInt("dhuhrBefore", value).apply()
    var dhuhrAfter: Int get() = prefs.getInt("dhuhrAfter", 35); set(value) = prefs.edit().putInt("dhuhrAfter", value).apply()
    var fridayBefore: Int get() = prefs.getInt("fridayBefore", 10); set(value) = prefs.edit().putInt("fridayBefore", value).apply()
    var fridayAfter: Int get() = prefs.getInt("fridayAfter", 61); set(value) = prefs.edit().putInt("fridayAfter", value).apply()
    var asrBefore: Int get() = prefs.getInt("asrBefore", 5); set(value) = prefs.edit().putInt("asrBefore", value).apply()
    var asrAfter: Int get() = prefs.getInt("asrAfter", 35); set(value) = prefs.edit().putInt("asrAfter", value).apply()
    var maghribBefore: Int get() = prefs.getInt("maghribBefore", 5); set(value) = prefs.edit().putInt("maghribBefore", value).apply()
    var maghribAfter: Int get() = prefs.getInt("maghribAfter", 35); set(value) = prefs.edit().putInt("maghribAfter", value).apply()
    var ishaBefore: Int get() = prefs.getInt("ishaBefore", 0); set(value) = prefs.edit().putInt("ishaBefore", value).apply()
    var ishaAfter: Int get() = prefs.getInt("ishaAfter", 55); set(value) = prefs.edit().putInt("ishaAfter", value).apply()

    var isAdzanAudioEnabled: Boolean
        get() = prefs.getBoolean("isAdzanAudioEnabled", false)
        set(value) = prefs.edit().putBoolean("isAdzanAudioEnabled", value).apply()

    var adzanVolume: Int
        get() = prefs.getInt("adzanVolume", 80)
        set(value) = prefs.edit().putInt("adzanVolume", value).apply()

    var customAdzanRegularUri: String?
        get() = prefs.getString("customAdzanRegularUri", null)
        set(value) = prefs.edit().putString("customAdzanRegularUri", value).apply()

    var customAdzanSubuhUri: String?
        get() = prefs.getString("customAdzanSubuhUri", null)
        set(value) = prefs.edit().putString("customAdzanSubuhUri", value).apply()

    var isAdzanPlaying: Boolean
        get() = prefs.getBoolean("isAdzanPlaying", false)
        set(value) = prefs.edit().putBoolean("isAdzanPlaying", value).apply()

    /**
     * BUG FIX #11: Menyimpan volume alarm asli ke SharedPreferences, bukan hanya variabel lokal.
     * Jika AdzanService di-crash paksa oleh sistem sebelum onDestroy() dipanggil, volume alarm
     * device user akan permanen berubah karena variabel lokal (originalAlarmVolume) ikut hilang
     * bersama proses. Dengan menyimpan ke SharedPreferences, nilai ini tetap ada dan bisa
     * di-restore saat service berikutnya dijalankan.
     * Nilai -1 berarti tidak ada backup (volume belum pernah diubah oleh adzan).
     */
    var adzanOriginalVolumeBackup: Int
        get() = prefs.getInt("adzanOriginalVolumeBackup", -1)
        set(value) = prefs.edit().putInt("adzanOriginalVolumeBackup", value).apply()

    var quoteUpdateInterval: Int
        get() = prefs.getInt("quoteUpdateInterval", 3)
        set(value) = prefs.edit().putInt("quoteUpdateInterval", value).apply()

    var quoteFontSize: Int
        get() = prefs.getInt("quoteFontSize", 18)
        set(value) = prefs.edit().putInt("quoteFontSize", value).apply()

    var quoteDisplayedChild: Int
        get() = prefs.getInt("quoteDisplayedChild", 0)
        set(value) = prefs.edit().putInt("quoteDisplayedChild", value).apply()

    var quoteBgAlpha: Int
        get() = prefs.getInt("quoteBgAlpha", 24)
        set(value) = prefs.edit().putInt("quoteBgAlpha", value).apply()

    var latestVersionName: String
        get() = prefs.getString("latestVersionName", "1.0") ?: "1.0"
        set(value) = prefs.edit().putString("latestVersionName", value).apply()

    var apkDownloadUrl: String
        get() = prefs.getString("apkDownloadUrl", "") ?: ""
        set(value) = prefs.edit().putString("apkDownloadUrl", value).apply()

    var latestDownloadId: Long
        get() = prefs.getLong("latestDownloadId", -1L)
        set(value) = prefs.edit().putLong("latestDownloadId", value).apply()

    fun restoreDefaults() {
        val currentLat = latitude
        val currentLon = longitude
        val currentLocName = locationName

        prefs.edit().clear().apply()

        // FIX E5: Restore juga reset ghost DND flags agar tidak stuck silent
        prefs.edit()
            .putBoolean("IS_MUTED_BY_APP_DND", false)
            .putBoolean("IS_MUTED_BY_APP_RINGER", false)
            .putBoolean("PENDING_UNMUTE", false)
            .putBoolean("IS_TEST_MODE_ACTIVE", false)
            .putBoolean("isAdzanPlaying", false)
            .apply()

        latitude = currentLat
        longitude = currentLon
        locationName = currentLocName
    }

    // =======================================================================
    // FIX A2: Batch save — menulis SEMUA settings dalam 1 disk I/O operation.
    // Mencegah data inconsistency dan I/O thrashing dari 30+ individual writes.
    // =======================================================================
    fun saveAllSettings(
        previewScale: Int, calculationMethod: String,
        showClock: Boolean, showDate: Boolean, showPrayer: Boolean, showAdditional: Boolean,
        fontSizeClock: Int, fontSizeDate: Int, fontSizePrayer: Int, fontSizeAdditional: Int,
        widgetBgRadius: Int, hijriOffset: Int,
        widgetTextColor: String, widgetBgColor: String, isDayStartAtMaghrib: Boolean,
        dateFormat: String, hijriFormat: String,
        isAutoSilentEnabled: Boolean,
        fajrBefore: Int, fajrAfter: Int, dhuhrBefore: Int, dhuhrAfter: Int,
        fridayBefore: Int, fridayAfter: Int, asrBefore: Int, asrAfter: Int,
        maghribBefore: Int, maghribAfter: Int, ishaBefore: Int, ishaAfter: Int,
        isAdzanAudioEnabled: Boolean, adzanVolume: Int,
        customAdzanRegularUri: String?, customAdzanSubuhUri: String?,
        quoteUpdateInterval: Int, quoteFontSize: Int, quoteBgAlpha: Int
    ) {
        prefs.edit()
            .putInt("previewScale", previewScale)
            .putString("calculationMethod", calculationMethod)
            .putBoolean("showClock", showClock)
            .putBoolean("showDate", showDate)
            .putBoolean("showPrayer", showPrayer)
            .putBoolean("showAdditional", showAdditional)
            .putInt("fontSizeClock", fontSizeClock)
            .putInt("fontSizeDate", fontSizeDate)
            .putInt("fontSizePrayer", fontSizePrayer)
            .putInt("fontSizeAdditional", fontSizeAdditional)
            .putInt("widgetBgRadius", widgetBgRadius)
            .putInt("hijriOffset", hijriOffset)
            .putString("widgetTextColor", widgetTextColor)
            .putString("widgetBgColor", widgetBgColor)
            .putBoolean("isDayStartAtMaghrib", isDayStartAtMaghrib)
            .putString("dateFormat", dateFormat)
            .putString("hijriFormat", hijriFormat)
            .putBoolean("isAutoSilentEnabled", isAutoSilentEnabled)
            .putInt("fajrBefore", fajrBefore)
            .putInt("fajrAfter", fajrAfter)
            .putInt("dhuhrBefore", dhuhrBefore)
            .putInt("dhuhrAfter", dhuhrAfter)
            .putInt("fridayBefore", fridayBefore)
            .putInt("fridayAfter", fridayAfter)
            .putInt("asrBefore", asrBefore)
            .putInt("asrAfter", asrAfter)
            .putInt("maghribBefore", maghribBefore)
            .putInt("maghribAfter", maghribAfter)
            .putInt("ishaBefore", ishaBefore)
            .putInt("ishaAfter", ishaAfter)
            .putBoolean("isAdzanAudioEnabled", isAdzanAudioEnabled)
            .putInt("adzanVolume", adzanVolume)
            .putString("customAdzanRegularUri", customAdzanRegularUri)
            .putString("customAdzanSubuhUri", customAdzanSubuhUri)
            .putInt("quoteUpdateInterval", quoteUpdateInterval)
            .putInt("quoteFontSize", quoteFontSize)
            .putInt("quoteBgAlpha", quoteBgAlpha)
            .apply()
    }
}
