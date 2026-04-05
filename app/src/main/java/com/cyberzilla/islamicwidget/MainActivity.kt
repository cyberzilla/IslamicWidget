package com.cyberzilla.islamicwidget

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.SunnahTimes
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsManager: SettingsManager

    private val languageEntries = arrayOf("Indonesia", "English", "العربية")
    private val languageValues = arrayOf("id", "en", "ar")

    private lateinit var themeEntries: Array<String>
    private val themeValues = arrayOf("SYSTEM", "LIGHT", "DARK")

    private val calcEntries = arrayOf("Muslim World League", "Egyptian", "Karachi", "Umm Al-Qura", "Dubai", "Qatar", "Kuwait", "Moonsighting Committee", "Singapore")
    private val calcValues = arrayOf("MUSLIM_WORLD_LEAGUE", "EGYPTIAN", "KARACHI", "UMM_AL_QURA", "DUBAI", "QATAR", "KUWAIT", "MOON_SIGHTING_COMMITTEE", "SINGAPORE")

    private var currentTextColor = "#FFFFFF"
    private var currentBgColor = "#00000000"

    private var pendingDndPermission = false
    private var tempRegularUri: String? = null
    private var tempSubuhUri: String? = null
    private var doubleBackToExitPressedOnce = false

    private val pickRegularAdzanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tempRegularUri = uri.toString()
            findViewById<TextView>(R.id.tv_adzan_regular_status)?.text = getString(R.string.status_custom)
            stopTestAdzan()
            saveSettingsQuietly()
        }
    }

    private val pickSubuhAdzanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tempSubuhUri = uri.toString()
            findViewById<TextView>(R.id.tv_adzan_subuh_status)?.text = getString(R.string.status_custom)
            stopTestAdzan()
            saveSettingsQuietly()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation()
        } else {
            Toast.makeText(this, getString(R.string.toast_location_needed), Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.toast_notification_granted), Toast.LENGTH_SHORT).show()
            findViewById<SwitchCompat>(R.id.switch_audio_adzan)?.isChecked = true
        } else {
            findViewById<SwitchCompat>(R.id.switch_audio_adzan)?.isChecked = false
            settingsManager.isAdzanAudioEnabled = false
            saveSettingsQuietly()
            Toast.makeText(this, getString(R.string.toast_notification_needed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            settingsManager = SettingsManager(this)
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            if (currentLocales.isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(settingsManager.languageCode))
            }
            applyAppTheme(settingsManager.appTheme)
        } catch (e: Exception) {}

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkBatteryOptimizations()

        // Memeriksa update otomatis di background
        UpdateHelper.checkForUpdates(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (doubleBackToExitPressedOnce) { finish(); return }
                doubleBackToExitPressedOnce = true
                Toast.makeText(this@MainActivity, getString(R.string.toast_exit_warning), Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
            }
        })

        try {
            loadSettingsToUI()
            setupButtons()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_xml_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun applyAppTheme(themeVal: String) {
        when (themeVal) {
            "LIGHT" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "DARK" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingDndPermission) {
            pendingDndPermission = false
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                findViewById<SwitchCompat>(R.id.switch_auto_silent)?.isChecked = true
                settingsManager.isAutoSilentEnabled = true
                saveSettingsQuietly()
                Toast.makeText(this, getString(R.string.toast_dnd_granted), Toast.LENGTH_SHORT).show()
            }
        }

        updateLocationUI()
        updatePreview()
    }

    override fun onPause() { super.onPause(); stopTestAdzan() }
    override fun onDestroy() { super.onDestroy(); stopTestAdzan() }

    private fun setupSlider(seekBarId: Int, textViewId: Int, min: Int, max: Int, initialValue: Int, isFormatScale: Boolean = false, suffix: String = "") {
        val seekBar = findViewById<SeekBar>(seekBarId) ?: return
        val textView = findViewById<TextView>(textViewId) ?: return

        seekBar.max = max - min
        seekBar.progress = initialValue - min

        if (isFormatScale) {
            textView.text = getString(R.string.preview_scale, initialValue)
        } else {
            val displayValue = if (seekBarId == R.id.sb_hijri_offset && initialValue > 0) "+$initialValue" else "$initialValue"
            textView.text = "$displayValue$suffix"
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualValue = progress + min
                if (isFormatScale) {
                    textView.text = getString(R.string.preview_scale, actualValue)
                } else {
                    val displayValue = if (seekBarId == R.id.sb_hijri_offset && actualValue > 0) "+$actualValue" else "$actualValue"
                    textView.text = "$displayValue$suffix"
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveSettingsQuietly()
            }
        })
    }

    private fun updateLocationUI() {
        val tvLocName = findViewById<TextView>(R.id.tv_location_name) ?: return
        val tvLatLon = findViewById<TextView>(R.id.tv_lat_lon) ?: return
        if (settingsManager.latitude != null && settingsManager.longitude != null) {
            tvLocName.text = settingsManager.locationName
            tvLatLon.text = getString(R.string.format_lat_lon, settingsManager.latitude, settingsManager.longitude)
        } else {
            tvLocName.text = getString(R.string.location_not_found)
            tvLatLon.text = getString(R.string.format_lat_lon, "-", "-")
        }
    }

    private fun updatePreview() {
        try {
            val previewBg = findViewById<ImageView>(R.id.widget_bg) ?: return

            val scaleProgress = findViewById<SeekBar>(R.id.sb_preview_scale)?.progress ?: 50
            val scaleMultiplier = (scaleProgress + 50) / 100f

            val fsClock = ((findViewById<SeekBar>(R.id.sb_fs_clock)?.progress ?: 20) + 20f) * scaleMultiplier
            val fsDate = ((findViewById<SeekBar>(R.id.sb_fs_date)?.progress ?: 4) + 8f) * scaleMultiplier
            val fsPrayer = ((findViewById<SeekBar>(R.id.sb_fs_prayer)?.progress ?: 5) + 8f) * scaleMultiplier
            val fsAdd = ((findViewById<SeekBar>(R.id.sb_fs_additional)?.progress ?: 3) + 8f) * scaleMultiplier

            val isShowClock = findViewById<SwitchCompat>(R.id.sw_show_clock)?.isChecked ?: true
            val isShowDate = findViewById<SwitchCompat>(R.id.sw_show_date)?.isChecked ?: true
            val isShowPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer)?.isChecked ?: true
            val isShowAdd = findViewById<SwitchCompat>(R.id.sw_show_additional)?.isChecked ?: true
            val previewRadiusDp = (findViewById<SeekBar>(R.id.sb_radius)?.progress?.toFloat() ?: 16f) * scaleMultiplier
            val offsetHijri = (findViewById<SeekBar>(R.id.sb_hijri_offset)?.progress ?: 2) - 2

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val activeLangCode = if (!currentLocales.isEmpty) currentLocales[0]!!.language else settingsManager.languageCode

            val selectedLocale = Locale.forLanguageTag(activeLangCode)
            val config = Configuration(resources.configuration)
            config.setLocale(selectedLocale)
            val localizedContext = createConfigurationContext(config)

            val txtSunrise = localizedContext.getString(R.string.sunrise)
            val txtLastThird = localizedContext.getString(R.string.last_third)
            val txtQibla = localizedContext.getString(R.string.qibla)

            val selectedCalcStr = findViewById<AutoCompleteTextView>(R.id.spinner_calc_method)?.text?.toString() ?: ""
            val calcIdx = calcEntries.indexOf(selectedCalcStr).takeIf { it >= 0 } ?: 0

            val masehiPattern = findViewById<EditText>(R.id.et_date_format)?.text?.toString()?.ifEmpty { "en-US{EEEE, dd MMMM yyyy}" } ?: "en-US{EEEE, dd MMMM yyyy}"
            val hijriPattern = findViewById<EditText>(R.id.et_hijri_format)?.text?.toString()?.ifEmpty { "en-US{dd MMMM yyyy} AH" } ?: "en-US{dd MMMM yyyy} AH"

            findViewById<ViewFlipper>(R.id.master_flipper)?.displayedChild = 0
            findViewById<View>(R.id.container_clock)?.visibility = if (isShowClock) View.VISIBLE else View.GONE
            findViewById<View>(R.id.container_date)?.visibility = if (isShowDate) View.VISIBLE else View.GONE
            findViewById<View>(R.id.container_prayer)?.visibility = if (isShowPrayer) View.VISIBLE else View.GONE

            val isFriday = LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY

            findViewById<TextView>(R.id.label_fajr)?.text = localizedContext.getString(R.string.fajr)
            findViewById<TextView>(R.id.label_dhuhr)?.text = if (isFriday) localizedContext.getString(R.string.friday) else localizedContext.getString(R.string.dhuhr)
            findViewById<TextView>(R.id.label_asr)?.text = localizedContext.getString(R.string.asr)
            findViewById<TextView>(R.id.label_maghrib)?.text = localizedContext.getString(R.string.maghrib)
            findViewById<TextView>(R.id.label_isha)?.text = localizedContext.getString(R.string.isha)

            val today = LocalDate.now()
            var hijriDate = HijrahDate.from(today)
            var totalHijriOffset = offsetHijri.toLong()

            val is24Hour = DateFormat.is24HourFormat(this)
            val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"

            if (settingsManager.latitude != null && settingsManager.longitude != null) {
                try {
                    val lat = settingsManager.latitude!!.toDouble()
                    val lon = settingsManager.longitude!!.toDouble()

                    val prayerTimes = IslamicAppUtils.calculatePrayerTimes(lat, lon, calcValues[calcIdx], today)
                    val sunnahTimes = SunnahTimes(prayerTimes)
                    val qibla = Qibla(Coordinates(lat, lon))

                    var isAfterMaghrib = false
                    if (findViewById<CheckBox>(R.id.cb_day_start)?.isChecked == true && Date().after(prayerTimes.maghrib.asDate())) {
                        totalHijriOffset += 1L
                        isAfterMaghrib = true
                    }

                    val timeFormatter = SimpleDateFormat(timePattern, selectedLocale)
                    timeFormatter.timeZone = TimeZone.getDefault()

                    findViewById<TextView>(R.id.tv_fajr_time)?.text = timeFormatter.format(prayerTimes.fajr.asDate())
                    findViewById<TextView>(R.id.tv_dhuhr_time)?.text = timeFormatter.format(prayerTimes.dhuhr.asDate())
                    findViewById<TextView>(R.id.tv_asr_time)?.text = timeFormatter.format(prayerTimes.asr.asDate())
                    findViewById<TextView>(R.id.tv_maghrib_time)?.text = timeFormatter.format(prayerTimes.maghrib.asDate())
                    findViewById<TextView>(R.id.tv_isha_time)?.text = timeFormatter.format(prayerTimes.isha.asDate())

                    findViewById<TextView>(R.id.tv_sunrise)?.text = "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise.asDate())}"
                    findViewById<TextView>(R.id.tv_last_third)?.text = "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight.asDate())}"
                    findViewById<TextView>(R.id.tv_qibla)?.text = String.format(selectedLocale, "%s: %.1f°", txtQibla, qibla.direction)

                    findViewById<TextView>(R.id.tv_sunrise_flip)?.text = "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise.asDate())}"
                    findViewById<TextView>(R.id.tv_last_third_flip)?.text = "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight.asDate())}"
                    findViewById<TextView>(R.id.tv_qibla_flip)?.text = String.format(selectedLocale, "%s: %.1f°", txtQibla, qibla.direction)

                    if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)

                    val sunnahInfo = IslamicAppUtils.getSunnahFastingInfo(localizedContext, hijriDate, today, isAfterMaghrib)

                    if (isShowAdd) {
                        if (sunnahInfo.isNotEmpty()) {
                            findViewById<View>(R.id.container_additional_normal)?.visibility = View.GONE
                            findViewById<View>(R.id.container_additional_flipper)?.visibility = View.VISIBLE
                            findViewById<TextView>(R.id.tv_sunnah_reminder_flip)?.text = sunnahInfo
                        } else {
                            findViewById<View>(R.id.container_additional_normal)?.visibility = View.VISIBLE
                            findViewById<View>(R.id.container_additional_flipper)?.visibility = View.GONE
                        }
                    } else {
                        findViewById<View>(R.id.container_additional_normal)?.visibility = View.GONE
                        findViewById<View>(R.id.container_additional_flipper)?.visibility = View.GONE
                    }

                } catch (e: Exception) { setDummyPreviewTimes(activeLangCode) }
            } else {
                setDummyPreviewTimes(activeLangCode)
                if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)
            }

            findViewById<TextView>(R.id.tv_gregorian_date)?.text = IslamicAppUtils.formatCustomDate(masehiPattern, today, selectedLocale)
            findViewById<TextView>(R.id.tv_hijri_date)?.text = IslamicAppUtils.formatCustomDate(hijriPattern, hijriDate, selectedLocale)

            val textColor = try { Color.parseColor(currentTextColor) } catch(e: Exception) { Color.WHITE }
            val bgColor = try { Color.parseColor(currentBgColor) } catch (e: Exception) { Color.TRANSPARENT }

            val bgDrawable = ContextCompat.getDrawable(this, R.drawable.widget_bg_shape)?.mutate() as? GradientDrawable
            bgDrawable?.cornerRadius = previewRadiusDp * resources.displayMetrics.density
            previewBg.setImageDrawable(bgDrawable)
            previewBg.setColorFilter(Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor)))
            previewBg.imageAlpha = Color.alpha(bgColor)

            findViewById<TextView>(R.id.clock_widget)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsClock); setTextColor(textColor) }
            findViewById<TextView>(R.id.tv_gregorian_date)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsDate - 2f); setTextColor(textColor) }
            findViewById<TextView>(R.id.tv_hijri_date)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsDate); setTextColor(textColor) }

            listOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha).forEach {
                findViewById<TextView>(it)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsPrayer - 2f); setTextColor(textColor) }
            }
            listOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time).forEach {
                findViewById<TextView>(it)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsPrayer); setTextColor(textColor) }
            }

            val additionalTextIds = listOf(
                R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2,
                R.id.tv_sunrise_flip, R.id.tv_last_third_flip, R.id.tv_qibla_flip, R.id.tv_divider_1_flip, R.id.tv_divider_2_flip
            )
            for (id in additionalTextIds) {
                findViewById<TextView>(id)?.apply { setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            }

            findViewById<TextView>(R.id.tv_sunnah_reminder_flip)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }

        } catch (e: Exception) {}
    }

    private fun setDummyPreviewTimes(langCode: String) {
        findViewById<TextView>(R.id.tv_fajr_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_dhuhr_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_asr_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_maghrib_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_isha_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_sunrise)?.text = "..."
        findViewById<TextView>(R.id.tv_last_third)?.text = "..."
        findViewById<TextView>(R.id.tv_qibla)?.text = "..."
    }

    private fun toggleTestAdzan(isSubuh: Boolean, btnPlay: Button?) {
        if (btnPlay == null) return
        val uri = if (isSubuh) tempSubuhUri else tempRegularUri
        val vol = findViewById<SeekBar>(R.id.sb_adzan_vol)?.progress ?: 80

        AudioAdzanManager.toggleTestAdzan(this, isSubuh, btnPlay, uri, vol) {
            findViewById<Button>(R.id.btn_play_adzan_regular)?.apply { text = getString(R.string.btn_test); setTextColor(Color.parseColor("#10B981")) }
            findViewById<Button>(R.id.btn_play_adzan_subuh)?.apply { text = getString(R.string.btn_test); setTextColor(Color.parseColor("#10B981")) }
        }
    }

    private fun stopTestAdzan() {
        AudioAdzanManager.stopTestAdzan {
            findViewById<Button>(R.id.btn_play_adzan_regular)?.apply { text = getString(R.string.btn_test); setTextColor(Color.parseColor("#10B981")) }
            findViewById<Button>(R.id.btn_play_adzan_subuh)?.apply { text = getString(R.string.btn_test); setTextColor(Color.parseColor("#10B981")) }
        }
    }

    private fun showPauseActivityWarningDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_permission_title))
                .setMessage(getString(R.string.dialog_disable_pause_activity))
                .setPositiveButton(getString(R.string.dialog_settings)) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e: Exception) {}
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }
    }

    private fun requestPinWidget(providerClass: Class<*>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val myProvider = ComponentName(this, providerClass)

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val successIntent = Intent(this, WidgetPinReceiver::class.java)
                val successPendingIntent = PendingIntent.getBroadcast(
                    this, 0, successIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successPendingIntent)
            } else {
                Toast.makeText(this, getString(R.string.pin_unsupported), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.pin_os_unsupported), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettingsToUI() {
        themeEntries = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val activeLangCode = if (!currentLocales.isEmpty) currentLocales[0]!!.language else settingsManager.languageCode

        findViewById<AutoCompleteTextView>(R.id.spinner_language)?.let { langSpinner ->
            langSpinner.isSaveEnabled = false
            langSpinner.setDropdownItems(languageEntries)
            langSpinner.setText(languageEntries[languageValues.indexOf(activeLangCode).takeIf { it >= 0 } ?: 0], false)

            langSpinner.setOnItemClickListener { _, _, pos, _ ->
                val code = languageValues[pos]
                if (code != activeLangCode) {
                    findViewById<FrameLayout>(R.id.loading_overlay)?.visibility = View.VISIBLE
                    Handler(Looper.getMainLooper()).postDelayed({
                        settingsManager.languageCode = code

                        val newDateFormat: String
                        val newHijriFormat: String

                        when (code) {
                            "id" -> {
                                newDateFormat = "id-ID{EEEE, dd MMMM yyyy}"
                                newHijriFormat = "id-ID{dd MMMM yyyy} H"
                            }
                            "en" -> {
                                newDateFormat = "en-US{EEEE, dd MMMM yyyy}"
                                newHijriFormat = "en-US{dd MMMM yyyy} AH"
                            }
                            "ar" -> {
                                newDateFormat = "ar-SA{EEEE, dd MMMM yyyy}"
                                newHijriFormat = "ar-SA{dd MMMM yyyy} هـ"
                            }
                            else -> {
                                newDateFormat = "en-US{EEEE, dd MMMM yyyy}"
                                newHijriFormat = "en-US{dd MMMM yyyy} AH"
                            }
                        }

                        settingsManager.dateFormat = newDateFormat
                        settingsManager.hijriFormat = newHijriFormat

                        findViewById<EditText>(R.id.et_date_format)?.setText(newDateFormat)
                        findViewById<EditText>(R.id.et_hijri_format)?.setText(newHijriFormat)

                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                        saveSettingsQuietly()

                        findViewById<FrameLayout>(R.id.loading_overlay)?.visibility = View.GONE
                    }, 300)
                }
            }
        }

        findViewById<AutoCompleteTextView>(R.id.spinner_theme)?.let { themeSpinner ->
            themeSpinner.isSaveEnabled = false
            themeSpinner.setDropdownItems(themeEntries)
            themeSpinner.setText(themeEntries[themeValues.indexOf(settingsManager.appTheme).takeIf { it >= 0 } ?: 0], false)

            themeSpinner.setOnItemClickListener { _, _, pos, _ ->
                val newTheme = themeValues[pos]
                if (settingsManager.appTheme != newTheme) {
                    findViewById<FrameLayout>(R.id.loading_overlay)?.visibility = View.VISIBLE
                    Handler(Looper.getMainLooper()).postDelayed({
                        settingsManager.appTheme = newTheme
                        applyAppTheme(newTheme)
                        saveSettingsQuietly()

                        findViewById<FrameLayout>(R.id.loading_overlay)?.visibility = View.GONE
                    }, 300)
                }
            }
        }

        setupSlider(R.id.sb_preview_scale, R.id.tv_preview_scale_label, 50, 150, settingsManager.previewScale, true, "")
        updateLocationUI()

        findViewById<AutoCompleteTextView>(R.id.spinner_calc_method)?.let { calcSpinner ->
            calcSpinner.isSaveEnabled = false
            calcSpinner.setDropdownItems(calcEntries)
            calcSpinner.setText(calcEntries[calcValues.indexOf(settingsManager.calculationMethod).takeIf { it >= 0 } ?: 0], false)
            calcSpinner.setOnItemClickListener { _, _, _, _ -> updatePreview(); saveSettingsQuietly() }
        }

        findViewById<SwitchCompat>(R.id.sw_show_clock)?.apply { isChecked = settingsManager.showClock; setOnCheckedChangeListener { _,_ -> updatePreview(); saveSettingsQuietly() } }
        findViewById<SwitchCompat>(R.id.sw_show_date)?.apply { isChecked = settingsManager.showDate; setOnCheckedChangeListener { _,_ -> updatePreview(); saveSettingsQuietly() } }
        findViewById<SwitchCompat>(R.id.sw_show_prayer)?.apply { isChecked = settingsManager.showPrayer; setOnCheckedChangeListener { _,_ -> updatePreview(); saveSettingsQuietly() } }
        findViewById<SwitchCompat>(R.id.sw_show_additional)?.apply { isChecked = settingsManager.showAdditional; setOnCheckedChangeListener { _,_ -> updatePreview(); saveSettingsQuietly() } }

        setupSlider(R.id.sb_fs_clock, R.id.tv_fs_clock, 20, 80, settingsManager.fontSizeClock, false, "sp")
        setupSlider(R.id.sb_fs_date, R.id.tv_fs_date, 8, 30, settingsManager.fontSizeDate, false, "sp")
        setupSlider(R.id.sb_fs_prayer, R.id.tv_fs_prayer, 8, 30, settingsManager.fontSizePrayer, false, "sp")
        setupSlider(R.id.sb_fs_additional, R.id.tv_fs_additional, 8, 24, settingsManager.fontSizeAdditional, false, "sp")
        setupSlider(R.id.sb_radius, R.id.tv_label_radius, 0, 60, settingsManager.widgetBgRadius, false, "dp")
        setupSlider(R.id.sb_hijri_offset, R.id.tv_label_hijri, -2, 2, settingsManager.hijriOffset, false, "")

        currentTextColor = settingsManager.widgetTextColor
        currentBgColor = settingsManager.widgetBgColor
        updateColorButtons()

        findViewById<CheckBox>(R.id.cb_day_start)?.apply { isChecked = settingsManager.isDayStartAtMaghrib; setOnCheckedChangeListener { _,_ -> updatePreview(); saveSettingsQuietly() } }

        val tw = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) { saveSettingsQuietly() }
        }
        findViewById<EditText>(R.id.et_date_format)?.apply { setText(settingsManager.dateFormat); addTextChangedListener(tw) }
        findViewById<EditText>(R.id.et_hijri_format)?.apply { setText(settingsManager.hijriFormat); addTextChangedListener(tw) }

        findViewById<SwitchCompat>(R.id.switch_auto_silent)?.apply {
            isChecked = settingsManager.isAutoSilentEnabled
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted) {
                    this.isChecked = false; pendingDndPermission = true
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } else {
                    saveSettingsQuietly()
                }
            }
        }

        setupSlider(R.id.sb_fajr_bef, R.id.tv_fajr_bef, 0, 60, settingsManager.fajrBefore, false, "m")
        setupSlider(R.id.sb_fajr_aft, R.id.tv_fajr_aft, 0, 120, settingsManager.fajrAfter, false, "m")
        setupSlider(R.id.sb_dhuhr_bef, R.id.tv_dhuhr_bef, 0, 60, settingsManager.dhuhrBefore, false, "m")
        setupSlider(R.id.sb_dhuhr_aft, R.id.tv_dhuhr_aft, 0, 120, settingsManager.dhuhrAfter, false, "m")
        setupSlider(R.id.sb_fri_bef, R.id.tv_fri_bef, 0, 60, settingsManager.fridayBefore, false, "m")
        setupSlider(R.id.sb_fri_aft, R.id.tv_fri_aft, 0, 180, settingsManager.fridayAfter, false, "m")
        setupSlider(R.id.sb_asr_bef, R.id.tv_asr_bef, 0, 60, settingsManager.asrBefore, false, "m")
        setupSlider(R.id.sb_asr_aft, R.id.tv_asr_aft, 0, 120, settingsManager.asrAfter, false, "m")
        setupSlider(R.id.sb_maghrib_bef, R.id.tv_maghrib_bef, 0, 60, settingsManager.maghribBefore, false, "m")
        setupSlider(R.id.sb_maghrib_aft, R.id.tv_maghrib_aft, 0, 120, settingsManager.maghribAfter, false, "m")
        setupSlider(R.id.sb_isha_bef, R.id.tv_isha_bef, 0, 60, settingsManager.ishaBefore, false, "m")
        setupSlider(R.id.sb_isha_aft, R.id.tv_isha_aft, 0, 120, settingsManager.ishaAfter, false, "m")

        findViewById<SwitchCompat>(R.id.switch_audio_adzan)?.apply {
            isChecked = settingsManager.isAdzanAudioEnabled
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            this.isChecked = false
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@setOnCheckedChangeListener
                        }
                    }
                    settingsManager.isAdzanAudioEnabled = true
                    showPauseActivityWarningDialog()
                    saveSettingsQuietly()
                } else {
                    settingsManager.isAdzanAudioEnabled = false
                    saveSettingsQuietly()
                }
            }
        }

        setupSlider(R.id.sb_adzan_vol, R.id.tv_adzan_vol, 0, 100, settingsManager.adzanVolume, false, "%")

        tempRegularUri = settingsManager.customAdzanRegularUri
        tempSubuhUri = settingsManager.customAdzanSubuhUri

        findViewById<TextView>(R.id.tv_adzan_regular_status)?.text = if (tempRegularUri.isNullOrEmpty()) getString(R.string.status_default) else getString(R.string.status_custom)
        findViewById<TextView>(R.id.tv_adzan_subuh_status)?.text = if (tempSubuhUri.isNullOrEmpty()) getString(R.string.status_default) else getString(R.string.status_custom)

        val btnPlayReg = findViewById<Button>(R.id.btn_play_adzan_regular)
        val btnPlaySub = findViewById<Button>(R.id.btn_play_adzan_subuh)

        findViewById<Button>(R.id.btn_pick_adzan_regular)?.setOnClickListener { pickRegularAdzanLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btn_clear_adzan_regular)?.setOnClickListener { tempRegularUri = null; findViewById<TextView>(R.id.tv_adzan_regular_status)?.text = getString(R.string.status_default); stopTestAdzan(); saveSettingsQuietly() }
        btnPlayReg?.setOnClickListener { toggleTestAdzan(false, btnPlayReg) }

        findViewById<Button>(R.id.btn_pick_adzan_subuh)?.setOnClickListener { pickSubuhAdzanLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btn_clear_adzan_subuh)?.setOnClickListener { tempSubuhUri = null; findViewById<TextView>(R.id.tv_adzan_subuh_status)?.text = getString(R.string.status_default); stopTestAdzan(); saveSettingsQuietly() }
        btnPlaySub?.setOnClickListener { toggleTestAdzan(true, btnPlaySub) }

        setupSlider(R.id.sb_quote_interval, R.id.tv_quote_interval, 0, 120, settingsManager.quoteUpdateInterval, false, "m")
        setupSlider(R.id.sb_quote_font, R.id.tv_quote_font, 10, 40, settingsManager.quoteFontSize, false, "sp")
        setupSlider(R.id.sb_quote_alpha, R.id.tv_quote_alpha, 0, 255, settingsManager.quoteBgAlpha, false, "")

        updatePreview()
    }

    private fun updateColorButtons() {
        try { findViewById<Button>(R.id.btn_pick_text_color)?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(currentTextColor)) } catch (e: Exception) {}
        try { findViewById<Button>(R.id.btn_pick_bg_color)?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(currentBgColor)) } catch (e: Exception) {}
    }

    private fun showColorPickerDialog(title: String, initialColorHex: String, onColorSelected: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)

        var currentColor = try { Color.parseColor(initialColorHex) } catch (e: Exception) { Color.BLACK }

        val cardPreview = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_color_preview)
        val tvHex = dialogView.findViewById<TextView>(R.id.tv_hex_code)

        val sbAlpha = dialogView.findViewById<SeekBar>(R.id.sb_alpha)
        val sbRed = dialogView.findViewById<SeekBar>(R.id.sb_red)
        val sbGreen = dialogView.findViewById<SeekBar>(R.id.sb_green)
        val sbBlue = dialogView.findViewById<SeekBar>(R.id.sb_blue)

        val tvAlphaVal = dialogView.findViewById<TextView>(R.id.tv_alpha_val)
        val tvRedVal = dialogView.findViewById<TextView>(R.id.tv_red_val)
        val tvGreenVal = dialogView.findViewById<TextView>(R.id.tv_green_val)
        val tvBlueVal = dialogView.findViewById<TextView>(R.id.tv_blue_val)

        fun updateUI() {
            cardPreview.setCardBackgroundColor(currentColor)
            tvHex.text = String.format("#%08X", currentColor).uppercase()

            sbAlpha.progress = Color.alpha(currentColor)
            sbRed.progress = Color.red(currentColor)
            sbGreen.progress = Color.green(currentColor)
            sbBlue.progress = Color.blue(currentColor)

            tvAlphaVal.text = sbAlpha.progress.toString()
            tvRedVal.text = sbRed.progress.toString()
            tvGreenVal.text = sbGreen.progress.toString()
            tvBlueVal.text = sbBlue.progress.toString()
        }

        updateUI()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentColor = Color.argb(sbAlpha.progress, sbRed.progress, sbGreen.progress, sbBlue.progress)
                    updateUI()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        sbAlpha.setOnSeekBarChangeListener(listener)
        sbRed.setOnSeekBarChangeListener(listener)
        sbGreen.setOnSeekBarChangeListener(listener)
        sbBlue.setOnSeekBarChangeListener(listener)

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                onColorSelected(String.format("#%08X", currentColor).uppercase())
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun saveSettingsQuietly() {
        try {
            settingsManager.previewScale = (findViewById<SeekBar>(R.id.sb_preview_scale)?.progress ?: 50) + 50

            val calcSpinnerStr = findViewById<AutoCompleteTextView>(R.id.spinner_calc_method)?.text?.toString() ?: ""
            settingsManager.calculationMethod = calcValues[calcEntries.indexOf(calcSpinnerStr).takeIf { it >= 0 } ?: 0]

            settingsManager.showClock = findViewById<SwitchCompat>(R.id.sw_show_clock)?.isChecked ?: true
            settingsManager.showDate = findViewById<SwitchCompat>(R.id.sw_show_date)?.isChecked ?: true
            settingsManager.showPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer)?.isChecked ?: true
            settingsManager.showAdditional = findViewById<SwitchCompat>(R.id.sw_show_additional)?.isChecked ?: true

            settingsManager.fontSizeClock = (findViewById<SeekBar>(R.id.sb_fs_clock)?.progress ?: 16) + 20
            settingsManager.fontSizeDate = (findViewById<SeekBar>(R.id.sb_fs_date)?.progress ?: 4) + 8
            settingsManager.fontSizePrayer = (findViewById<SeekBar>(R.id.sb_fs_prayer)?.progress ?: 5) + 8
            settingsManager.fontSizeAdditional = (findViewById<SeekBar>(R.id.sb_fs_additional)?.progress ?: 3) + 8
            settingsManager.widgetBgRadius = findViewById<SeekBar>(R.id.sb_radius)?.progress ?: 16
            settingsManager.hijriOffset = (findViewById<SeekBar>(R.id.sb_hijri_offset)?.progress ?: 2) - 2

            settingsManager.widgetTextColor = currentTextColor
            settingsManager.widgetBgColor = currentBgColor
            settingsManager.isDayStartAtMaghrib = findViewById<CheckBox>(R.id.cb_day_start)?.isChecked ?: true

            settingsManager.dateFormat = findViewById<EditText>(R.id.et_date_format)?.text?.toString()?.ifEmpty { "en-US{EEEE, dd MMMM yyyy}" } ?: "en-US{EEEE, dd MMMM yyyy}"
            settingsManager.hijriFormat = findViewById<EditText>(R.id.et_hijri_format)?.text?.toString()?.ifEmpty { "en-US{dd MMMM yyyy} AH" } ?: "en-US{dd MMMM yyyy} AH"

            settingsManager.isAutoSilentEnabled = findViewById<SwitchCompat>(R.id.switch_auto_silent)?.isChecked ?: false
            settingsManager.fajrBefore = findViewById<SeekBar>(R.id.sb_fajr_bef)?.progress ?: 5
            settingsManager.fajrAfter = findViewById<SeekBar>(R.id.sb_fajr_aft)?.progress ?: 15
            settingsManager.dhuhrBefore = findViewById<SeekBar>(R.id.sb_dhuhr_bef)?.progress ?: 5
            settingsManager.dhuhrAfter = findViewById<SeekBar>(R.id.sb_dhuhr_aft)?.progress ?: 15
            settingsManager.fridayBefore = findViewById<SeekBar>(R.id.sb_fri_bef)?.progress ?: 10
            settingsManager.fridayAfter = findViewById<SeekBar>(R.id.sb_fri_aft)?.progress ?: 45
            settingsManager.asrBefore = findViewById<SeekBar>(R.id.sb_asr_bef)?.progress ?: 5
            settingsManager.asrAfter = findViewById<SeekBar>(R.id.sb_asr_aft)?.progress ?: 15
            settingsManager.maghribBefore = findViewById<SeekBar>(R.id.sb_maghrib_bef)?.progress ?: 5
            settingsManager.maghribAfter = findViewById<SeekBar>(R.id.sb_maghrib_aft)?.progress ?: 15
            settingsManager.ishaBefore = findViewById<SeekBar>(R.id.sb_isha_bef)?.progress ?: 5
            settingsManager.ishaAfter = findViewById<SeekBar>(R.id.sb_isha_aft)?.progress ?: 15
            settingsManager.isAdzanAudioEnabled = findViewById<SwitchCompat>(R.id.switch_audio_adzan)?.isChecked ?: false
            settingsManager.adzanVolume = findViewById<SeekBar>(R.id.sb_adzan_vol)?.progress ?: 80
            settingsManager.customAdzanRegularUri = tempRegularUri
            settingsManager.customAdzanSubuhUri = tempSubuhUri

            val newInterval = findViewById<SeekBar>(R.id.sb_quote_interval)?.progress ?: 0
            settingsManager.quoteUpdateInterval = newInterval
            settingsManager.quoteFontSize = (findViewById<SeekBar>(R.id.sb_quote_font)?.progress ?: 4) + 10
            settingsManager.quoteBgAlpha = findViewById<SeekBar>(R.id.sb_quote_alpha)?.progress ?: 153

            QuoteUpdateManager.setAutoUpdate(this, newInterval)

            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, IslamicWidgetProvider::class.java))
            sendBroadcast(Intent(this, IslamicWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE; putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids) })

            val quoteIds = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, QuoteWidgetProvider::class.java))
            sendBroadcast(Intent(this, QuoteWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE; putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quoteIds) })
        } catch (e: Exception) {}
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_pin_main_widget)?.setOnClickListener { requestPinWidget(IslamicWidgetProvider::class.java) }
        findViewById<Button>(R.id.btn_pin_quote_widget)?.setOnClickListener { requestPinWidget(QuoteWidgetProvider::class.java) }

        findViewById<Button>(R.id.btn_pick_text_color)?.setOnClickListener { showColorPickerDialog(getString(R.string.btn_text_color), currentTextColor) { selectedHex -> currentTextColor = selectedHex; updateColorButtons(); updatePreview(); saveSettingsQuietly() } }
        findViewById<Button>(R.id.btn_pick_bg_color)?.setOnClickListener { showColorPickerDialog(getString(R.string.btn_bg_color), currentBgColor) { selectedHex -> currentBgColor = selectedHex; updateColorButtons(); updatePreview(); saveSettingsQuietly() } }

        findViewById<Button>(R.id.btn_update_gps)?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation() else requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        findViewById<Button>(R.id.btn_restore)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_reset_title))
                .setMessage(getString(R.string.dialog_reset_message))
                .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->

                    findViewById<FrameLayout>(R.id.loading_overlay)?.visibility = View.VISIBLE
                    Handler(Looper.getMainLooper()).postDelayed({
                        settingsManager.restoreDefaults()
                        applyAppTheme("SYSTEM")
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        loadSettingsToUI()
                        saveSettingsQuietly()

                        findViewById<FrameLayout>(R.id.loading_overlay)?.visibility = View.GONE
                    }, 300)
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }

        findViewById<Button>(R.id.btn_about)?.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .show()

            dialogView.findViewById<Button>(R.id.btn_open_github)?.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cyberzilla"))
                startActivity(intent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        Toast.makeText(this, getString(R.string.toast_searching_location), Toast.LENGTH_SHORT).show()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
            if (location != null) {
                settingsManager.latitude = location.latitude.toString()
                settingsManager.longitude = location.longitude.toString()
                try {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addrs -> runOnUiThread { updateAddr(addrs.firstOrNull()) } }
                    } else { @Suppress("DEPRECATION") updateAddr(geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()) }
                } catch(e: Exception) { updateAddr(null) }
            }
        }
    }

    private fun updateAddr(address: Address?) {
        val locationParts = listOfNotNull(
            address?.thoroughfare,
            address?.subLocality,
            address?.locality,
            address?.subAdminArea,
            address?.adminArea,
            address?.countryName
        )

        settingsManager.locationName = if (locationParts.isNotEmpty()) {
            locationParts.joinToString(", ")
        } else {
            getString(R.string.location_found)
        }

        updateLocationUI()
        updatePreview()
        saveSettingsQuietly()
    }

    private fun AutoCompleteTextView.setDropdownItems(items: Array<String>) {
        val noFilterAdapter = object : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, items) {
            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        return FilterResults().apply { values = items; count = items.size }
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }
        this.setAdapter(noFilterAdapter)
    }

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}