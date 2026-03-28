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
import androidx.activity.OnBackPressedCallback // IMPORT BARU
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.SunnahTimes
import com.batoulapps.adhan2.data.DateComponents
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    private val languageEntries = arrayOf("Indonesia", "English", "العربية (Arabic)")
    private val languageValues = arrayOf("id", "en", "ar")

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

    // VARIABEL DOUBLE SWIPE TO EXIT
    private var doubleBackToExitPressedOnce = false

    private val pickRegularAdzanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tempRegularUri = uri.toString()
            findViewById<TextView>(R.id.tv_adzan_regular_status).text = "Status: File Custom Terpilih"
            stopTestAdzan()
        }
    }

    private val pickSubuhAdzanLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            tempSubuhUri = uri.toString()
            findViewById<TextView>(R.id.tv_adzan_subuh_status).text = "Status: File Custom Terpilih"
            stopTestAdzan()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation()
        } else {
            Toast.makeText(this, "Izin lokasi dibutuhkan", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun Instant.asDate() = Date(toEpochMilliseconds())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsManager = SettingsManager(this)

        // =========================================================
        // FIX: CEGAT GESTUR BACK / SWIPE UNTUK DOUBLE EXIT
        // =========================================================
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (doubleBackToExitPressedOnce) {
                    finish() // Keluar aplikasi jika sudah swipe 2x
                    return
                }

                doubleBackToExitPressedOnce = true
                Toast.makeText(this@MainActivity, "Swipe/Tekan kembali sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()

                // Kembalikan ke false jika pengguna tidak melakukan swipe kedua dalam 2 detik
                Handler(Looper.getMainLooper()).postDelayed({
                    doubleBackToExitPressedOnce = false
                }, 2000)
            }
        })

        loadSettingsToUI()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        if (pendingDndPermission) {
            pendingDndPermission = false
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val switchDnd = findViewById<SwitchCompat>(R.id.switch_auto_silent)
                switchDnd.isChecked = true
                settingsManager.isAutoSilentEnabled = true
                Toast.makeText(this, "Izin DND berhasil diberikan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopTestAdzan()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTestAdzan()
    }

    private fun setupSlider(seekBarId: Int, textViewId: Int, min: Int, max: Int, initialValue: Int, labelPrefix: String, labelSuffix: String) {
        val seekBar = findViewById<SeekBar>(seekBarId)
        val textView = findViewById<TextView>(textViewId)

        seekBar.max = max - min
        seekBar.progress = initialValue - min
        textView.text = "$labelPrefix$initialValue$labelSuffix"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualValue = progress + min
                textView.text = "$labelPrefix$actualValue$labelSuffix"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateLocationUI() {
        val tvLocName = findViewById<TextView>(R.id.tv_location_name)
        val tvLatLon = findViewById<TextView>(R.id.tv_lat_lon)

        val lat = settingsManager.latitude
        val lon = settingsManager.longitude

        if (lat != null && lon != null) {
            tvLocName.text = settingsManager.locationName
            tvLatLon.text = "Lat: $lat, Lon: $lon"
        } else {
            tvLocName.text = "Belum Ada Lokasi"
            tvLatLon.text = "Lat: -, Lon: -"
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
                    val formatter = DateTimeFormatter.ofPattern(pattern, locale)
                    var formattedText = formatter.format(dateObj)

                    if (localeTag.lowercase().startsWith("ar")) {
                        val arabicDigits = arrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
                        val builder = StringBuilder()
                        for (char in formattedText) {
                            if (char in '0'..'9') {
                                builder.append(arabicDigits[char - '0'])
                            } else {
                                builder.append(char)
                            }
                        }
                        formattedText = builder.toString()
                    }
                    formattedText
                }
            } else {
                val formatter = DateTimeFormatter.ofPattern(inputStr, defaultLocale)
                formatter.format(dateObj)
            }
        } catch (e: Exception) {
            val fallbackFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", defaultLocale)
            fallbackFormatter.format(dateObj)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun updatePreview() {
        try {
            val previewBg = findViewById<ImageView>(R.id.widget_bg) ?: return

            val previewScaleProgress = findViewById<SeekBar>(R.id.sb_preview_scale).progress + 50
            val scaleMultiplier = previewScaleProgress / 100f

            val fsClock = (findViewById<SeekBar>(R.id.sb_fs_clock).progress + 20f) * scaleMultiplier
            val fsDate = (findViewById<SeekBar>(R.id.sb_fs_date).progress + 8f) * scaleMultiplier
            val fsPrayer = (findViewById<SeekBar>(R.id.sb_fs_prayer).progress + 8f) * scaleMultiplier
            val fsAdd = (findViewById<SeekBar>(R.id.sb_fs_additional).progress + 8f) * scaleMultiplier

            val isShowClock = findViewById<SwitchCompat>(R.id.sw_show_clock).isChecked
            val isShowDate = findViewById<SwitchCompat>(R.id.sw_show_date).isChecked
            val isShowPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer).isChecked
            val isShowAdd = findViewById<SwitchCompat>(R.id.sw_show_additional).isChecked

            val radiusDpRaw = findViewById<SeekBar>(R.id.sb_radius).progress.toFloat()
            val previewRadiusDp = radiusDpRaw * scaleMultiplier

            val offsetHijri = findViewById<SeekBar>(R.id.sb_hijri_offset).progress - 5

            val selectedLangStr = findViewById<AutoCompleteTextView>(R.id.spinner_language).text.toString()
            val langIdx = languageEntries.indexOf(selectedLangStr).takeIf { it >= 0 } ?: 0
            val previewLangCode = languageValues[langIdx]

            val txtSunrise = when(previewLangCode) { "en" -> "Sunrise"; "ar" -> "الشروق"; else -> "Terbit" }
            val txtLastThird = when(previewLangCode) { "en" -> "Last 1/3"; "ar" -> "الثلث الأخير"; else -> "1/3 Malam" }
            val txtQibla = when(previewLangCode) { "en" -> "Qibla"; "ar" -> "القبلة"; else -> "Kiblat" }

            val selectedCalcStr = findViewById<AutoCompleteTextView>(R.id.spinner_calc_method).text.toString()
            val calcIdx = calcEntries.indexOf(selectedCalcStr).takeIf { it >= 0 } ?: 0

            val masehiPattern = findViewById<EditText>(R.id.et_date_format).text.toString().ifEmpty { "id-ID{EEEE, dd MMMM yyyy}" }
            val hijriPattern = findViewById<EditText>(R.id.et_hijri_format).text.toString().ifEmpty { "ar-SA{dd MMMM yyyy} هـ" }

            val selectedLocale = Locale.forLanguageTag(previewLangCode)
            val config = Configuration(resources.configuration)
            config.setLocale(selectedLocale)
            val localizedContext = createConfigurationContext(config)

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

            val latString = settingsManager.latitude
            val lonString = settingsManager.longitude
            val is24Hour = DateFormat.is24HourFormat(this)
            val timePattern = if (is24Hour) "HH:mm" else "hh:mm a"

            if (latString != null && lonString != null) {
                try {
                    val latitude = latString.toDouble()
                    val longitude = lonString.toDouble()
                    val coordinates = Coordinates(latitude, longitude)
                    val dateComponents = DateComponents(today.year, today.monthValue, today.dayOfMonth)

                    val method = when (calcValues[calcIdx]) {
                        "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
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

                    if (findViewById<CheckBox>(R.id.cb_day_start).isChecked) {
                        val currentTime = Date()
                        val maghribTime = prayerTimes.maghrib.asDate()
                        if (currentTime.after(maghribTime)) {
                            totalHijriOffset += 1L
                        }
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

                } catch (e: Exception) {
                    setDummyPreviewTimes(previewLangCode)
                }
            } else {
                setDummyPreviewTimes(previewLangCode)
            }

            if (totalHijriOffset != 0L) {
                hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)
            }

            val masehiFormatted = formatCustomDate(masehiPattern, today, selectedLocale)
            val hijriFormatted = formatCustomDate(hijriPattern, hijriDate, selectedLocale)

            findViewById<TextView>(R.id.tv_gregorian_date)?.text = masehiFormatted
            findViewById<TextView>(R.id.tv_hijri_date)?.text = hijriFormatted

            val textColor = try { Color.parseColor(currentTextColor) } catch(e: Exception) { Color.WHITE }
            val opacityTextColor = Color.argb(200, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

            val bgColor = try { Color.parseColor(currentBgColor) } catch (e: Exception) { Color.TRANSPARENT }
            val alpha = Color.alpha(bgColor)
            val opaqueBg = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))

            val bgDrawable = ContextCompat.getDrawable(this, R.drawable.widget_bg_shape)?.mutate() as? GradientDrawable
            bgDrawable?.cornerRadius = previewRadiusDp * resources.displayMetrics.density
            previewBg.setImageDrawable(bgDrawable)
            previewBg.setColorFilter(opaqueBg)
            previewBg.imageAlpha = alpha

            findViewById<TextView>(R.id.clock_widget)?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fsClock)
                setTextColor(textColor)
            }

            findViewById<TextView>(R.id.tv_gregorian_date)?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fsDate - (2f * scaleMultiplier))
                setTextColor(textColor)
            }
            findViewById<TextView>(R.id.tv_hijri_date)?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fsDate)
                setTextColor(textColor)
            }

            val textViewsToResize = listOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
            for (id in textViewsToResize) {
                findViewById<TextView>(id)?.apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, fsPrayer - (2f * scaleMultiplier))
                    setTextColor(textColor)
                }
            }

            val timeViewsToResize = listOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)
            for (id in timeViewsToResize) {
                findViewById<TextView>(id)?.apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, fsPrayer)
                    setTextColor(textColor)
                }
            }

            findViewById<TextView>(R.id.tv_sunrise)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_last_third)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_qibla)?.apply { setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_divider_1)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            findViewById<TextView>(R.id.tv_divider_2)?.apply { setTextColor(opacityTextColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }

        } catch (e: Exception) {}
    }

    private fun setDummyPreviewTimes(langCode: String) {
        val txtSunrise = when(langCode) { "en" -> "Sunrise"; "ar" -> "الشروق"; else -> "Terbit" }
        val txtLastThird = when(langCode) { "en" -> "Last 1/3"; "ar" -> "الثلث الأخير"; else -> "1/3 Malam" }
        val txtQibla = when(langCode) { "en" -> "Qibla"; "ar" -> "القبلة"; else -> "Kiblat" }

        findViewById<TextView>(R.id.tv_fajr_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_dhuhr_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_asr_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_maghrib_time)?.text = "--:--"
        findViewById<TextView>(R.id.tv_isha_time)?.text = "--:--"

        findViewById<TextView>(R.id.tv_sunrise)?.text = "$txtSunrise: --:--"
        findViewById<TextView>(R.id.tv_last_third)?.text = "$txtLastThird: --:--"
        findViewById<TextView>(R.id.tv_qibla)?.text = "$txtQibla: --°"
    }

    private fun toggleTestAdzan(isSubuh: Boolean, btnPlay: Button) {
        if ((isSubuh && isTestingSubuh) || (!isSubuh && isTestingRegular)) {
            stopTestAdzan()
            return
        }

        stopTestAdzan()

        val uriString = if (isSubuh) tempSubuhUri else tempRegularUri

        try {
            testMediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                }

                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                val targetVolume = (maxVolume * findViewById<SeekBar>(R.id.sb_adzan_vol).progress) / 100
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, targetVolume, 0)

                if (!uriString.isNullOrEmpty()) {
                    setDataSource(this@MainActivity, Uri.parse(uriString))
                } else {
                    val rawResId = if (isSubuh) R.raw.adzan_subuh else R.raw.adzan_regular
                    val afd = resources.openRawResourceFd(rawResId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                prepare()
                start()

                if (isSubuh) {
                    isTestingSubuh = true
                    btnPlay.text = "Stop"
                    btnPlay.setTextColor(Color.RED)
                } else {
                    isTestingRegular = true
                    btnPlay.text = "Stop"
                    btnPlay.setTextColor(Color.RED)
                }

                setOnCompletionListener {
                    stopTestAdzan()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Gagal memutar audio kustom. Format mungkin tidak didukung.", Toast.LENGTH_SHORT).show()
            stopTestAdzan()
        }
    }

    private fun stopTestAdzan() {
        try {
            testMediaPlayer?.stop()
            testMediaPlayer?.release()
        } catch (e: Exception) {}

        testMediaPlayer = null
        isTestingRegular = false
        isTestingSubuh = false

        findViewById<Button>(R.id.btn_play_adzan_regular)?.apply {
            text = "Tes"
            setTextColor(Color.parseColor("#10B981"))
        }
        findViewById<Button>(R.id.btn_play_adzan_subuh)?.apply {
            text = "Tes"
            setTextColor(Color.parseColor("#10B981"))
        }
    }

    private fun loadSettingsToUI() {
        val savedScale = settingsManager.previewScale
        val sbScale = findViewById<SeekBar>(R.id.sb_preview_scale)
        val tvScaleLabel = findViewById<TextView>(R.id.tv_preview_scale_label)

        sbScale.max = 100
        sbScale.progress = savedScale - 50
        tvScaleLabel.text = "Skala: $savedScale%"

        sbScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualScale = progress + 50
                tvScaleLabel.text = "Skala: $actualScale%"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val langSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_language)
        langSpinner.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageEntries))
        val langIndex = languageValues.indexOf(settingsManager.languageCode).takeIf { it >= 0 } ?: 0
        langSpinner.setText(languageEntries[langIndex], false)

        langSpinner.setOnItemClickListener { _, _, _, _ -> updatePreview() }

        updateLocationUI()

        val calcSpinner = findViewById<AutoCompleteTextView>(R.id.spinner_calc_method)
        calcSpinner.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, calcEntries))
        val calcIndex = calcValues.indexOf(settingsManager.calculationMethod).takeIf { it >= 0 } ?: 0
        calcSpinner.setText(calcEntries[calcIndex], false)

        calcSpinner.setOnItemClickListener { _, _, _, _ -> updatePreview() }

        val swClock = findViewById<SwitchCompat>(R.id.sw_show_clock)
        val swDate = findViewById<SwitchCompat>(R.id.sw_show_date)
        val swPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer)
        val swAdd = findViewById<SwitchCompat>(R.id.sw_show_additional)

        swClock.isChecked = settingsManager.showClock
        swDate.isChecked = settingsManager.showDate
        swPrayer.isChecked = settingsManager.showPrayer
        swAdd.isChecked = settingsManager.showAdditional

        swClock.setOnCheckedChangeListener { _, _ -> updatePreview() }
        swDate.setOnCheckedChangeListener { _, _ -> updatePreview() }
        swPrayer.setOnCheckedChangeListener { _, _ -> updatePreview() }
        swAdd.setOnCheckedChangeListener { _, _ -> updatePreview() }

        setupSlider(R.id.sb_fs_clock, R.id.tv_fs_clock, 20, 80, settingsManager.fontSizeClock, "Font: ", "sp")
        setupSlider(R.id.sb_fs_date, R.id.tv_fs_date, 8, 30, settingsManager.fontSizeDate, "Font: ", "sp")
        setupSlider(R.id.sb_fs_prayer, R.id.tv_fs_prayer, 8, 30, settingsManager.fontSizePrayer, "Font: ", "sp")
        setupSlider(R.id.sb_fs_additional, R.id.tv_fs_additional, 8, 24, settingsManager.fontSizeAdditional, "Font: ", "sp")

        setupSlider(R.id.sb_radius, R.id.tv_label_radius, 0, 60, settingsManager.widgetBgRadius, "Radius: ", "dp")
        setupSlider(R.id.sb_hijri_offset, R.id.tv_label_hijri, -5, 5, settingsManager.hijriOffset, "", " Hari")

        currentTextColor = settingsManager.widgetTextColor
        currentBgColor = settingsManager.widgetBgColor
        updateColorButtons()

        findViewById<CheckBox>(R.id.cb_day_start).isChecked = settingsManager.isDayStartAtMaghrib

        val etDateFormat = findViewById<EditText>(R.id.et_date_format)
        val etHijriFormat = findViewById<EditText>(R.id.et_hijri_format)

        etDateFormat.setText(settingsManager.dateFormat)
        etHijriFormat.setText(settingsManager.hijriFormat)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        }
        etDateFormat.addTextChangedListener(textWatcher)
        etHijriFormat.addTextChangedListener(textWatcher)

        findViewById<CheckBox>(R.id.cb_day_start).setOnCheckedChangeListener { _, _ -> updatePreview() }

        val switchDnd = findViewById<SwitchCompat>(R.id.switch_auto_silent)
        switchDnd.setOnCheckedChangeListener(null)
        switchDnd.isChecked = settingsManager.isAutoSilentEnabled

        switchDnd.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    switchDnd.isChecked = false
                    pendingDndPermission = true
                    Toast.makeText(this, "Silakan berikan izin 'Do Not Disturb' terlebih dahulu", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }

        setupSlider(R.id.sb_fajr_bef, R.id.tv_fajr_bef, 0, 60, settingsManager.fajrBefore, "", "m")
        setupSlider(R.id.sb_fajr_aft, R.id.tv_fajr_aft, 0, 120, settingsManager.fajrAfter, "", "m")
        setupSlider(R.id.sb_dhuhr_bef, R.id.tv_dhuhr_bef, 0, 60, settingsManager.dhuhrBefore, "", "m")
        setupSlider(R.id.sb_dhuhr_aft, R.id.tv_dhuhr_aft, 0, 120, settingsManager.dhuhrAfter, "", "m")
        setupSlider(R.id.sb_fri_bef, R.id.tv_fri_bef, 0, 60, settingsManager.fridayBefore, "", "m")
        setupSlider(R.id.sb_fri_aft, R.id.tv_fri_aft, 0, 180, settingsManager.fridayAfter, "", "m")
        setupSlider(R.id.sb_asr_bef, R.id.tv_asr_bef, 0, 60, settingsManager.asrBefore, "", "m")
        setupSlider(R.id.sb_asr_aft, R.id.tv_asr_aft, 0, 120, settingsManager.asrAfter, "", "m")
        setupSlider(R.id.sb_maghrib_bef, R.id.tv_maghrib_bef, 0, 60, settingsManager.maghribBefore, "", "m")
        setupSlider(R.id.sb_maghrib_aft, R.id.tv_maghrib_aft, 0, 120, settingsManager.maghribAfter, "", "m")
        setupSlider(R.id.sb_isha_bef, R.id.tv_isha_bef, 0, 60, settingsManager.ishaBefore, "", "m")
        setupSlider(R.id.sb_isha_aft, R.id.tv_isha_aft, 0, 120, settingsManager.ishaAfter, "", "m")

        val switchAdzan = findViewById<SwitchCompat>(R.id.switch_audio_adzan)
        switchAdzan.isChecked = settingsManager.isAdzanAudioEnabled

        setupSlider(R.id.sb_adzan_vol, R.id.tv_adzan_vol, 0, 100, settingsManager.adzanVolume, "", "%")

        tempRegularUri = settingsManager.customAdzanRegularUri
        tempSubuhUri = settingsManager.customAdzanSubuhUri

        findViewById<TextView>(R.id.tv_adzan_regular_status).text = if (tempRegularUri.isNullOrEmpty()) "Status: Bawaan Aplikasi" else "Status: File Custom Aktif"
        findViewById<TextView>(R.id.tv_adzan_subuh_status).text = if (tempSubuhUri.isNullOrEmpty()) "Status: Bawaan Aplikasi" else "Status: File Custom Aktif"

        val btnPlayRegular = findViewById<Button>(R.id.btn_play_adzan_regular)
        val btnPlaySubuh = findViewById<Button>(R.id.btn_play_adzan_subuh)

        findViewById<Button>(R.id.btn_pick_adzan_regular).setOnClickListener { pickRegularAdzanLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btn_clear_adzan_regular).setOnClickListener {
            tempRegularUri = null
            findViewById<TextView>(R.id.tv_adzan_regular_status).text = "Status: Bawaan Aplikasi"
            stopTestAdzan()
        }
        btnPlayRegular.setOnClickListener { toggleTestAdzan(false, btnPlayRegular) }

        findViewById<Button>(R.id.btn_pick_adzan_subuh).setOnClickListener { pickSubuhAdzanLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btn_clear_adzan_subuh).setOnClickListener {
            tempSubuhUri = null
            findViewById<TextView>(R.id.tv_adzan_subuh_status).text = "Status: Bawaan Aplikasi"
            stopTestAdzan()
        }
        btnPlaySubuh.setOnClickListener { toggleTestAdzan(true, btnPlaySubuh) }

        updatePreview()
    }

    private fun updateColorButtons() {
        val btnText = findViewById<Button>(R.id.btn_pick_text_color)
        val btnBg = findViewById<Button>(R.id.btn_pick_bg_color)

        try { btnText.backgroundTintList = ColorStateList.valueOf(Color.parseColor(currentTextColor)) } catch (e: Exception) {}
        try { btnBg.backgroundTintList = ColorStateList.valueOf(Color.parseColor(currentBgColor)) } catch (e: Exception) {}
    }

    private fun showColorPickerDialog(title: String, initialColorHex: String, onColorSelected: (String) -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val colorPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200)
            setBackgroundColor(try { Color.parseColor(initialColorHex) } catch (e: Exception) { Color.BLACK })
        }

        val tvHex = TextView(this).apply {
            text = "Hex: $initialColorHex"
            textSize = 16f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 20, 0, 20)
        }

        var currentColor = try { Color.parseColor(initialColorHex) } catch (e: Exception) { Color.BLACK }

        fun createColorSlider(label: String, initialValue: Int, onValueChanged: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val tv = TextView(this).apply { text = label; layoutParams = LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT) }
            val sb = SeekBar(this).apply {
                max = 255; progress = initialValue; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        onValueChanged(progress)
                        val hex = String.format("#%02X%02X%02X%02X", Color.alpha(currentColor), Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))
                        colorPreview.setBackgroundColor(currentColor)
                        tvHex.text = "Hex: $hex"
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            row.addView(tv); row.addView(sb); return row
        }

        layout.addView(colorPreview)
        layout.addView(tvHex)
        layout.addView(createColorSlider("Alpha (Transparan)", Color.alpha(currentColor)) { currentColor = Color.argb(it, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)) })
        layout.addView(createColorSlider("Merah", Color.red(currentColor)) { currentColor = Color.argb(Color.alpha(currentColor), it, Color.green(currentColor), Color.blue(currentColor)) })
        layout.addView(createColorSlider("Hijau", Color.green(currentColor)) { currentColor = Color.argb(Color.alpha(currentColor), Color.red(currentColor), it, Color.blue(currentColor)) })
        layout.addView(createColorSlider("Biru", Color.blue(currentColor)) { currentColor = Color.argb(Color.alpha(currentColor), Color.red(currentColor), Color.green(currentColor), it) })

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("Pilih") { _, _ ->
                val finalHex = String.format("#%02X%02X%02X%02X", Color.alpha(currentColor), Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor))
                onColorSelected(finalHex)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun saveSettingsFromUI() {
        try {
            settingsManager.previewScale = findViewById<SeekBar>(R.id.sb_preview_scale).progress + 50

            val selectedLangStr = findViewById<AutoCompleteTextView>(R.id.spinner_language).text.toString()
            val langIdx = languageEntries.indexOf(selectedLangStr).takeIf { it >= 0 } ?: 0
            settingsManager.languageCode = languageValues[langIdx]

            val selectedCalcStr = findViewById<AutoCompleteTextView>(R.id.spinner_calc_method).text.toString()
            val calcIdx = calcEntries.indexOf(selectedCalcStr).takeIf { it >= 0 } ?: 0
            settingsManager.calculationMethod = calcValues[calcIdx]

            settingsManager.showClock = findViewById<SwitchCompat>(R.id.sw_show_clock).isChecked
            settingsManager.showDate = findViewById<SwitchCompat>(R.id.sw_show_date).isChecked
            settingsManager.showPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer).isChecked
            settingsManager.showAdditional = findViewById<SwitchCompat>(R.id.sw_show_additional).isChecked

            settingsManager.fontSizeClock = findViewById<SeekBar>(R.id.sb_fs_clock).progress + 20
            settingsManager.fontSizeDate = findViewById<SeekBar>(R.id.sb_fs_date).progress + 8
            settingsManager.fontSizePrayer = findViewById<SeekBar>(R.id.sb_fs_prayer).progress + 8
            settingsManager.fontSizeAdditional = findViewById<SeekBar>(R.id.sb_fs_additional).progress + 8

            settingsManager.widgetBgRadius = findViewById<SeekBar>(R.id.sb_radius).progress + 0
            settingsManager.hijriOffset = findViewById<SeekBar>(R.id.sb_hijri_offset).progress - 5

            settingsManager.widgetTextColor = currentTextColor
            settingsManager.widgetBgColor = currentBgColor

            settingsManager.isDayStartAtMaghrib = findViewById<CheckBox>(R.id.cb_day_start).isChecked

            settingsManager.dateFormat = findViewById<EditText>(R.id.et_date_format).text.toString().ifEmpty { "id-ID{EEEE, dd MMMM yyyy}" }
            settingsManager.hijriFormat = findViewById<EditText>(R.id.et_hijri_format).text.toString().ifEmpty { "ar-SA{dd MMMM yyyy} هـ" }

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

            Toast.makeText(this, "Pengaturan Disimpan!", Toast.LENGTH_SHORT).show()
            triggerWidgetUpdate()

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyimpan. Coba lagi.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_pick_text_color).setOnClickListener {
            showColorPickerDialog("Pilih Warna Teks", currentTextColor) { selectedHex ->
                currentTextColor = selectedHex
                updateColorButtons()
                updatePreview()
            }
        }

        findViewById<Button>(R.id.btn_pick_bg_color).setOnClickListener {
            showColorPickerDialog("Pilih Warna Latar", currentBgColor) { selectedHex ->
                currentBgColor = selectedHex
                updateColorButtons()
                updatePreview()
            }
        }

        findViewById<Button>(R.id.btn_update_gps).setOnClickListener { checkLocationPermission() }
        findViewById<Button>(R.id.btn_save_settings).setOnClickListener { saveSettingsFromUI() }

        findViewById<Button>(R.id.btn_restore).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Kembalikan Default?")
                .setMessage("Semua kustomisasi akan dihapus.")
                .setPositiveButton("Ya") { _, _ ->
                    settingsManager.restoreDefaults()
                    loadSettingsToUI()
                    triggerWidgetUpdate()
                    Toast.makeText(this, "Dikembalikan ke awal", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        findViewById<Button>(R.id.btn_about).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Tentang Developer")
                .setMessage("Islamic Widget\nVersi 1.0\n\nDikembangkan oleh:\ncyberzilla (Dedy)\n\nFitur Khusus:\n• Pemutar Adzan (Latar Depan)\n• Auto-Mute Volume Media\n• Custom Layout \n• KMP Engine")
                .setPositiveButton("Tutup", null)
                .setNeutralButton("GitHub") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cyberzilla"))) }
                .show()
        }
    }

    private fun processAddress(address: Address?) {
        if (address != null) {
            settingsManager.locationName = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Lokasi Ditemukan"
        } else {
            settingsManager.locationName = "Lokasi Tidak Diketahui"
        }
        updateLocationUI()
        updatePreview()
        triggerWidgetUpdate()
        Toast.makeText(this@MainActivity, "Lokasi GPS diperbarui!", Toast.LENGTH_LONG).show()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        Toast.makeText(this, "Mencari lokasi...", Toast.LENGTH_SHORT).show()

        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val priority = if (hasFineLocation) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        try {
            fusedLocationClient.getCurrentLocation(priority, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        settingsManager.latitude = location.latitude.toString()
                        settingsManager.longitude = location.longitude.toString()

                        try {
                            val geocoder = Geocoder(this@MainActivity, Locale.getDefault())

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                                    runOnUiThread { processAddress(addresses.firstOrNull()) }
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                processAddress(addresses?.firstOrNull())
                            }
                        } catch(e: Exception) {
                            settingsManager.locationName = "Koordinat: ${location.latitude}, ${location.longitude}"
                            updateLocationUI()
                            updatePreview()
                            triggerWidgetUpdate()
                            Toast.makeText(this@MainActivity, "Lokasi GPS diperbarui!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Gagal. Pastikan GPS menyala.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this@MainActivity, "Gagal GPS: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error GPS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerWidgetUpdate() {
        val intent = Intent(this, IslamicWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            ComponentName(application, IslamicWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }
}