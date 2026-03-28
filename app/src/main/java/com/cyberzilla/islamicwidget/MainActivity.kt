package com.cyberzilla.islamicwidget

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
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
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.SunnahTimes
import com.batoulapps.adhan2.data.DateComponents
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    private var testMediaPlayer: MediaPlayer? = null
    private var isTestingRegular = false
    private var isTestingSubuh = false
    private var doubleBackToExitPressedOnce = false

    private val pickRegularAdzanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tempRegularUri = uri.toString()
            findViewById<TextView>(R.id.tv_adzan_regular_status).text = getString(R.string.status_custom)
            stopTestAdzan()
        }
    }

    private val pickSubuhAdzanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tempSubuhUri = uri.toString()
            findViewById<TextView>(R.id.tv_adzan_subuh_status).text = getString(R.string.status_custom)
            stopTestAdzan()
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
            findViewById<SwitchCompat>(R.id.switch_audio_adzan).isChecked = true
            settingsManager.isAdzanAudioEnabled = true
            Toast.makeText(this, getString(R.string.toast_notification_granted), Toast.LENGTH_SHORT).show()
            showPauseActivityWarningDialog()
        } else {
            findViewById<SwitchCompat>(R.id.switch_audio_adzan).isChecked = false
            settingsManager.isAdzanAudioEnabled = false
            Toast.makeText(this, getString(R.string.toast_notification_needed), Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun Instant.asDate() = Date(toEpochMilliseconds())

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsManager = SettingsManager(this)

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(settingsManager.languageCode))
        }

        applyAppTheme(settingsManager.appTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (doubleBackToExitPressedOnce) { finish(); return }
                doubleBackToExitPressedOnce = true
                Toast.makeText(this@MainActivity, getString(R.string.toast_exit_warning), Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
            }
        })

        loadSettingsToUI()
        setupButtons()
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
                findViewById<SwitchCompat>(R.id.switch_auto_silent).isChecked = true
                settingsManager.isAutoSilentEnabled = true
                Toast.makeText(this, getString(R.string.toast_dnd_granted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() { super.onPause(); stopTestAdzan() }
    override fun onDestroy() { super.onDestroy(); stopTestAdzan() }

    private fun setupSlider(seekBarId: Int, textViewId: Int, min: Int, max: Int, initialValue: Int, isFormatScale: Boolean = false, suffix: String = "") {
        val seekBar = findViewById<SeekBar>(seekBarId)
        val textView = findViewById<TextView>(textViewId)

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
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateLocationUI() {
        val tvLocName = findViewById<TextView>(R.id.tv_location_name)
        val tvLatLon = findViewById<TextView>(R.id.tv_lat_lon)
        if (settingsManager.latitude != null && settingsManager.longitude != null) {
            tvLocName.text = settingsManager.locationName
            tvLatLon.text = getString(R.string.format_lat_lon, settingsManager.latitude, settingsManager.longitude)
        } else {
            tvLocName.text = getString(R.string.location_not_found)
            tvLatLon.text = getString(R.string.format_lat_lon, "-", "-")
        }
    }

    private fun formatCustomDate(inputStr: String, dateObj: TemporalAccessor, defaultLocale: Locale): String {
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

    @OptIn(ExperimentalTime::class)
    private fun updatePreview() {
        try {
            val previewBg = findViewById<ImageView>(R.id.widget_bg) ?: return
            val scaleMultiplier = (findViewById<SeekBar>(R.id.sb_preview_scale).progress + 50) / 100f
            val fsClock = (findViewById<SeekBar>(R.id.sb_fs_clock).progress + 20f) * scaleMultiplier
            val fsDate = (findViewById<SeekBar>(R.id.sb_fs_date).progress + 8f) * scaleMultiplier
            val fsPrayer = (findViewById<SeekBar>(R.id.sb_fs_prayer).progress + 8f) * scaleMultiplier
            val fsAdd = (findViewById<SeekBar>(R.id.sb_fs_additional).progress + 8f) * scaleMultiplier

            val isShowClock = findViewById<SwitchCompat>(R.id.sw_show_clock).isChecked
            val isShowDate = findViewById<SwitchCompat>(R.id.sw_show_date).isChecked
            val isShowPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer).isChecked
            val isShowAdd = findViewById<SwitchCompat>(R.id.sw_show_additional).isChecked
            val previewRadiusDp = findViewById<SeekBar>(R.id.sb_radius).progress.toFloat() * scaleMultiplier
            val offsetHijri = findViewById<SeekBar>(R.id.sb_hijri_offset).progress - 2

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val activeLangCode = if (!currentLocales.isEmpty) currentLocales[0]!!.language else settingsManager.languageCode

            val selectedLocale = Locale.forLanguageTag(activeLangCode)
            val config = Configuration(resources.configuration)
            config.setLocale(selectedLocale)
            val localizedContext = createConfigurationContext(config)

            val txtSunrise = localizedContext.getString(R.string.sunrise)
            val txtLastThird = localizedContext.getString(R.string.last_third)
            val txtQibla = localizedContext.getString(R.string.qibla)

            val selectedCalcStr = findViewById<AutoCompleteTextView>(R.id.spinner_calc_method).text.toString()
            val calcIdx = calcEntries.indexOf(selectedCalcStr).takeIf { it >= 0 } ?: 0

            val masehiPattern = findViewById<EditText>(R.id.et_date_format).text.toString().ifEmpty { "en-US{EEEE, dd MMMM yyyy}" }
            val hijriPattern = findViewById<EditText>(R.id.et_hijri_format).text.toString().ifEmpty { "en-US{dd MMMM yyyy} AH" }

            findViewById<View>(R.id.container_clock)?.visibility = if (isShowClock) View.VISIBLE else View.GONE
            findViewById<View>(R.id.container_date)?.visibility = if (isShowDate) View.VISIBLE else View.GONE
            findViewById<View>(R.id.container_prayer)?.visibility = if (isShowPrayer) View.VISIBLE else View.GONE
            findViewById<View>(R.id.container_additional)?.visibility = if (isShowAdd) View.VISIBLE else View.GONE

            findViewById<TextView>(R.id.label_fajr)?.text = localizedContext.getString(R.string.fajr)
            findViewById<TextView>(R.id.label_dhuhr)?.text = localizedContext.getString(R.string.dhuhr)
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
                    val coordinates = Coordinates(settingsManager.latitude!!.toDouble(), settingsManager.longitude!!.toDouble())
                    val dateComponents = DateComponents(today.year, today.monthValue, today.dayOfMonth)
                    val method = when (calcValues[calcIdx]) {
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

                    val prayerTimes = PrayerTimes(coordinates, dateComponents, method.parameters)
                    val sunnahTimes = SunnahTimes(prayerTimes)
                    val qibla = Qibla(coordinates)

                    if (findViewById<CheckBox>(R.id.cb_day_start).isChecked && Date().after(prayerTimes.maghrib.asDate())) {
                        totalHijriOffset += 1L
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

                } catch (e: Exception) { setDummyPreviewTimes(activeLangCode) }
            } else { setDummyPreviewTimes(activeLangCode) }

            if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)

            findViewById<TextView>(R.id.tv_gregorian_date)?.text = formatCustomDate(masehiPattern, today, selectedLocale)
            findViewById<TextView>(R.id.tv_hijri_date)?.text = formatCustomDate(hijriPattern, hijriDate, selectedLocale)

            val textColor = try { Color.parseColor(currentTextColor) } catch(e: Exception) { Color.WHITE }
            val opacityTextColor = Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor))
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

            findViewById<TextView>(R.id.tv_sunrise)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_last_third)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_qibla)?.apply { setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_divider_1)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_divider_2)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }

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

    private fun toggleTestAdzan(isSubuh: Boolean, btnPlay: Button) {
        if ((isSubuh && isTestingSubuh) || (!isSubuh && isTestingRegular)) { stopTestAdzan(); return }
        stopTestAdzan()
        val uriString = if (isSubuh) tempSubuhUri else tempRegularUri

        try {
            testMediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                } else { @Suppress("DEPRECATION") setAudioStreamType(android.media.AudioManager.STREAM_ALARM) }

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                val targetVolume = (maxVolume * findViewById<SeekBar>(R.id.sb_adzan_vol).progress) / 100
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, targetVolume, 0)

                if (!uriString.isNullOrEmpty()) setDataSource(this@MainActivity, Uri.parse(uriString))
                else {
                    val afd = resources.openRawResourceFd(if (isSubuh) R.raw.adzan_subuh else R.raw.adzan_regular)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
                prepare(); start()

                if (isSubuh) {
                    isTestingSubuh = true
                    btnPlay.text = getString(R.string.btn_stop)
                    btnPlay.setTextColor(Color.RED)
                } else {
                    isTestingRegular = true
                    btnPlay.text = getString(R.string.btn_stop)
                    btnPlay.setTextColor(Color.RED)
                }
                setOnCompletionListener { stopTestAdzan() }
            }
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, getString(R.string.toast_audio_error), Toast.LENGTH_SHORT).show()
            stopTestAdzan()
        }
    }

    private fun stopTestAdzan() {
        try { testMediaPlayer?.stop(); testMediaPlayer?.release() } catch (e: Exception) {}
        testMediaPlayer = null; isTestingRegular = false; isTestingSubuh = false
        findViewById<Button>(R.id.btn_play_adzan_regular)?.apply { text = getString(R.string.btn_test); setTextColor(Color.parseColor("#10B981")) }
        findViewById<Button>(R.id.btn_play_adzan_subuh)?.apply { text = getString(R.string.btn_test); setTextColor(Color.parseColor("#10B981")) }
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

    private fun loadSettingsToUI() {
        themeEntries = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val activeLangCode = if (!currentLocales.isEmpty) currentLocales[0]!!.language else settingsManager.languageCode

        val langSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_language)
        langSpinner.isSaveEnabled = false
        langSpinner.setDropdownItems(languageEntries)
        langSpinner.setText(languageEntries[languageValues.indexOf(activeLangCode).takeIf { it >= 0 } ?: 0], false)

        langSpinner.setOnItemClickListener { _, _, pos, _ ->
            val code = languageValues[pos]
            if (code != activeLangCode) {
                settingsManager.languageCode = code

                when (code) {
                    "id" -> {
                        settingsManager.dateFormat = "id-ID{EEEE, dd MMMM yyyy}"
                        settingsManager.hijriFormat = "id-ID{dd MMMM yyyy} H"
                    }
                    "en" -> {
                        settingsManager.dateFormat = "en-US{EEEE, dd MMMM yyyy}"
                        settingsManager.hijriFormat = "en-US{dd MMMM yyyy} AH"
                    }
                    "ar" -> {
                        settingsManager.dateFormat = "ar-SA{EEEE, dd MMMM yyyy}"
                        settingsManager.hijriFormat = "ar-SA{dd MMMM yyyy} هـ"
                    }
                }

                findViewById<EditText>(R.id.et_date_format).setText(settingsManager.dateFormat)
                findViewById<EditText>(R.id.et_hijri_format).setText(settingsManager.hijriFormat)

                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
            }
        }

        val themeSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_theme)
        themeSpinner.isSaveEnabled = false
        themeSpinner.setDropdownItems(themeEntries)
        themeSpinner.setText(themeEntries[themeValues.indexOf(settingsManager.appTheme).takeIf { it >= 0 } ?: 0], false)

        themeSpinner.setOnItemClickListener { _, _, pos, _ ->
            settingsManager.appTheme = themeValues[pos]
            applyAppTheme(themeValues[pos])
        }

        setupSlider(R.id.sb_preview_scale, R.id.tv_preview_scale_label, 50, 150, settingsManager.previewScale, true, "")

        updateLocationUI()

        val calcSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_calc_method)
        calcSpinner.isSaveEnabled = false
        calcSpinner.setDropdownItems(calcEntries)
        calcSpinner.setText(calcEntries[calcValues.indexOf(settingsManager.calculationMethod).takeIf { it >= 0 } ?: 0], false)
        calcSpinner.setOnItemClickListener { _, _, _, _ -> updatePreview() }

        findViewById<SwitchCompat>(R.id.sw_show_clock).apply { isChecked = settingsManager.showClock; setOnCheckedChangeListener { _,_ -> updatePreview() } }
        findViewById<SwitchCompat>(R.id.sw_show_date).apply { isChecked = settingsManager.showDate; setOnCheckedChangeListener { _,_ -> updatePreview() } }
        findViewById<SwitchCompat>(R.id.sw_show_prayer).apply { isChecked = settingsManager.showPrayer; setOnCheckedChangeListener { _,_ -> updatePreview() } }
        findViewById<SwitchCompat>(R.id.sw_show_additional).apply { isChecked = settingsManager.showAdditional; setOnCheckedChangeListener { _,_ -> updatePreview() } }

        setupSlider(R.id.sb_fs_clock, R.id.tv_fs_clock, 20, 80, settingsManager.fontSizeClock, false, "sp")
        setupSlider(R.id.sb_fs_date, R.id.tv_fs_date, 8, 30, settingsManager.fontSizeDate, false, "sp")
        setupSlider(R.id.sb_fs_prayer, R.id.tv_fs_prayer, 8, 30, settingsManager.fontSizePrayer, false, "sp")
        setupSlider(R.id.sb_fs_additional, R.id.tv_fs_additional, 8, 24, settingsManager.fontSizeAdditional, false, "sp")

        setupSlider(R.id.sb_radius, R.id.tv_label_radius, 0, 60, settingsManager.widgetBgRadius, false, "dp")
        setupSlider(R.id.sb_hijri_offset, R.id.tv_label_hijri, -2, 2, settingsManager.hijriOffset, false, "")

        currentTextColor = settingsManager.widgetTextColor
        currentBgColor = settingsManager.widgetBgColor
        updateColorButtons()

        findViewById<CheckBox>(R.id.cb_day_start).apply { isChecked = settingsManager.isDayStartAtMaghrib; setOnCheckedChangeListener { _,_ -> updatePreview() } }

        val tw = object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}; override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { updatePreview() }; override fun afterTextChanged(s: Editable?) {} }
        findViewById<EditText>(R.id.et_date_format).apply { setText(settingsManager.dateFormat); addTextChangedListener(tw) }
        findViewById<EditText>(R.id.et_hijri_format).apply { setText(settingsManager.hijriFormat); addTextChangedListener(tw) }

        findViewById<SwitchCompat>(R.id.switch_auto_silent).apply {
            isChecked = settingsManager.isAutoSilentEnabled
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted) {
                    this.isChecked = false; pendingDndPermission = true
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
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

        findViewById<SwitchCompat>(R.id.switch_audio_adzan).apply {
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
                } else {
                    settingsManager.isAdzanAudioEnabled = false
                }
            }
        }
        setupSlider(R.id.sb_adzan_vol, R.id.tv_adzan_vol, 0, 100, settingsManager.adzanVolume, false, "%")

        tempRegularUri = settingsManager.customAdzanRegularUri
        tempSubuhUri = settingsManager.customAdzanSubuhUri

        findViewById<TextView>(R.id.tv_adzan_regular_status).text = if (tempRegularUri.isNullOrEmpty()) getString(R.string.status_default) else getString(R.string.status_custom)
        findViewById<TextView>(R.id.tv_adzan_subuh_status).text = if (tempSubuhUri.isNullOrEmpty()) getString(R.string.status_default) else getString(R.string.status_custom)

        val btnPlayReg = findViewById<Button>(R.id.btn_play_adzan_regular)
        val btnPlaySub = findViewById<Button>(R.id.btn_play_adzan_subuh)

        findViewById<Button>(R.id.btn_pick_adzan_regular).setOnClickListener { pickRegularAdzanLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btn_clear_adzan_regular).setOnClickListener { tempRegularUri = null; findViewById<TextView>(R.id.tv_adzan_regular_status).text = getString(R.string.status_default); stopTestAdzan() }
        btnPlayReg.setOnClickListener { toggleTestAdzan(false, btnPlayReg) }

        findViewById<Button>(R.id.btn_pick_adzan_subuh).setOnClickListener { pickSubuhAdzanLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btn_clear_adzan_subuh).setOnClickListener { tempSubuhUri = null; findViewById<TextView>(R.id.tv_adzan_subuh_status).text = getString(R.string.status_default); stopTestAdzan() }
        btnPlaySub.setOnClickListener { toggleTestAdzan(true, btnPlaySub) }

        updatePreview()
    }

    private fun updateColorButtons() {
        try { findViewById<Button>(R.id.btn_pick_text_color).backgroundTintList = ColorStateList.valueOf(Color.parseColor(currentTextColor)) } catch (e: Exception) {}
        try { findViewById<Button>(R.id.btn_pick_bg_color).backgroundTintList = ColorStateList.valueOf(Color.parseColor(currentBgColor)) } catch (e: Exception) {}
    }

    private fun showColorPickerDialog(title: String, initialColorHex: String, onColorSelected: (String) -> Unit) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 50, 50, 50) }
        var currentColor = try { Color.parseColor(initialColorHex) } catch (e: Exception) { Color.BLACK }
        val colorPreview = View(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200); setBackgroundColor(currentColor) }
        val tvHex = TextView(this).apply { text = getString(R.string.color_hex, initialColorHex); textSize = 16f; textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER; setPadding(0, 20, 0, 20) }

        fun createColorSlider(label: String, initVal: Int, onValChange: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val tv = TextView(this@MainActivity).apply { text = label; layoutParams = LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT) }
            val sb = SeekBar(this@MainActivity).apply { max = 255; progress = initVal; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        onValChange(progress)
                        val hex = String.format("#%02X%02X%02X%02X", Color.alpha(currentColor), Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))
                        colorPreview.setBackgroundColor(currentColor); tvHex.text = getString(R.string.color_hex, hex)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(tv); row.addView(sb); return row
        }

        layout.addView(colorPreview); layout.addView(tvHex)
        layout.addView(createColorSlider(getString(R.string.color_alpha), Color.alpha(currentColor)) { currentColor = Color.argb(it, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)) })
        layout.addView(createColorSlider(getString(R.string.color_red), Color.red(currentColor)) { currentColor = Color.argb(Color.alpha(currentColor), it, Color.green(currentColor), Color.blue(currentColor)) })
        layout.addView(createColorSlider(getString(R.string.color_green), Color.green(currentColor)) { currentColor = Color.argb(Color.alpha(currentColor), Color.red(currentColor), it, Color.blue(currentColor)) })
        layout.addView(createColorSlider(getString(R.string.color_blue), Color.blue(currentColor)) { currentColor = Color.argb(Color.alpha(currentColor), Color.red(currentColor), Color.green(currentColor), it) })

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ -> onColorSelected(String.format("#%02X%02X%02X%02X", Color.alpha(currentColor), Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))) }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun saveSettingsFromUI() {
        try {
            settingsManager.previewScale = findViewById<SeekBar>(R.id.sb_preview_scale).progress + 50
            settingsManager.calculationMethod = calcValues[calcEntries.indexOf(findViewById<AutoCompleteTextView>(R.id.spinner_calc_method).text.toString()).takeIf { it >= 0 } ?: 0]
            settingsManager.showClock = findViewById<SwitchCompat>(R.id.sw_show_clock).isChecked
            settingsManager.showDate = findViewById<SwitchCompat>(R.id.sw_show_date).isChecked
            settingsManager.showPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer).isChecked
            settingsManager.showAdditional = findViewById<SwitchCompat>(R.id.sw_show_additional).isChecked
            settingsManager.fontSizeClock = findViewById<SeekBar>(R.id.sb_fs_clock).progress + 20
            settingsManager.fontSizeDate = findViewById<SeekBar>(R.id.sb_fs_date).progress + 8
            settingsManager.fontSizePrayer = findViewById<SeekBar>(R.id.sb_fs_prayer).progress + 8
            settingsManager.fontSizeAdditional = findViewById<SeekBar>(R.id.sb_fs_additional).progress + 8
            settingsManager.widgetBgRadius = findViewById<SeekBar>(R.id.sb_radius).progress
            settingsManager.hijriOffset = findViewById<SeekBar>(R.id.sb_hijri_offset).progress - 2

            settingsManager.widgetTextColor = currentTextColor
            settingsManager.widgetBgColor = currentBgColor
            settingsManager.isDayStartAtMaghrib = findViewById<CheckBox>(R.id.cb_day_start).isChecked

            settingsManager.dateFormat = findViewById<EditText>(R.id.et_date_format).text.toString().ifEmpty { "en-US{EEEE, dd MMMM yyyy}" }
            settingsManager.hijriFormat = findViewById<EditText>(R.id.et_hijri_format).text.toString().ifEmpty { "en-US{dd MMMM yyyy} AH" }

            settingsManager.isAutoSilentEnabled = findViewById<SwitchCompat>(R.id.switch_auto_silent).isChecked
            settingsManager.fajrBefore = findViewById<SeekBar>(R.id.sb_fajr_bef).progress
            settingsManager.fajrAfter = findViewById<SeekBar>(R.id.sb_fajr_aft).progress
            settingsManager.dhuhrBefore = findViewById<SeekBar>(R.id.sb_dhuhr_bef).progress
            settingsManager.dhuhrAfter = findViewById<SeekBar>(R.id.sb_dhuhr_aft).progress
            settingsManager.fridayBefore = findViewById<SeekBar>(R.id.sb_fri_bef).progress
            settingsManager.fridayAfter = findViewById<SeekBar>(R.id.sb_fri_aft).progress
            settingsManager.asrBefore = findViewById<SeekBar>(R.id.sb_asr_bef).progress
            settingsManager.asrAfter = findViewById<SeekBar>(R.id.sb_asr_aft).progress
            settingsManager.maghribBefore = findViewById<SeekBar>(R.id.sb_maghrib_bef).progress
            settingsManager.maghribAfter = findViewById<SeekBar>(R.id.sb_maghrib_aft).progress
            settingsManager.ishaBefore = findViewById<SeekBar>(R.id.sb_isha_bef).progress
            settingsManager.ishaAfter = findViewById<SeekBar>(R.id.sb_isha_aft).progress
            settingsManager.isAdzanAudioEnabled = findViewById<SwitchCompat>(R.id.switch_audio_adzan).isChecked
            settingsManager.adzanVolume = findViewById<SeekBar>(R.id.sb_adzan_vol).progress
            settingsManager.customAdzanRegularUri = tempRegularUri
            settingsManager.customAdzanSubuhUri = tempSubuhUri

            Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, IslamicWidgetProvider::class.java))
            sendBroadcast(Intent(this, IslamicWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE; putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids) })
        } catch (e: Exception) {}
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_pick_text_color).setOnClickListener { showColorPickerDialog(getString(R.string.btn_text_color), currentTextColor) { selectedHex -> currentTextColor = selectedHex; updateColorButtons(); updatePreview() } }
        findViewById<Button>(R.id.btn_pick_bg_color).setOnClickListener { showColorPickerDialog(getString(R.string.btn_bg_color), currentBgColor) { selectedHex -> currentBgColor = selectedHex; updateColorButtons(); updatePreview() } }
        findViewById<Button>(R.id.btn_update_gps).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation() else requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        findViewById<Button>(R.id.btn_save_settings).setOnClickListener { saveSettingsFromUI() }

        findViewById<Button>(R.id.btn_restore).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_reset_title))
                .setMessage(getString(R.string.dialog_reset_message))
                .setPositiveButton(getString(R.string.dialog_yes)) { _, _ -> settingsManager.restoreDefaults(); loadSettingsToUI() }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }

        findViewById<Button>(R.id.btn_about).setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .show()

            dialogView.findViewById<Button>(R.id.btn_open_github).setOnClickListener {
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
        settingsManager.locationName = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: getString(R.string.location_found)
        updateLocationUI(); updatePreview()
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
}