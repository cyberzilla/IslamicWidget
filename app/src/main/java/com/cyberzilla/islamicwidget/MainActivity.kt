package com.cyberzilla.islamicwidget

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
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
import android.util.Log
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
import com.cyberzilla.islamicwidget.utils.IslamicAstronomy
import com.cyberzilla.islamicwidget.utils.HilalCriteria
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
import com.cyberzilla.islamicwidget.BuildConfig
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.view.HapticFeedbackConstants
import com.google.android.material.button.MaterialButton


class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsManager: SettingsManager

    private val languageEntries = arrayOf("Indonesia", "English", "العربية")
    private val languageValues = arrayOf("id", "en", "ar")

    private lateinit var themeEntries: Array<String>
    private val themeValues = arrayOf("SYSTEM", "LIGHT", "DARK")

    private val calcEntries = arrayOf("Kemenag", "JAKIM", "Singapore", "Muslim World League", "North America (ISNA)", "Moonsighting Committee", "Egyptian", "Karachi", "Umm Al-Qura", "Dubai", "Qatar", "Kuwait", "Tehran", "Turkey", "Morocco")

    private val hilalCriteriaEntries = HilalCriteria.entries.map { it.displayName }.toTypedArray()
    private val hilalCriteriaValues = HilalCriteria.entries.map { it.name }.toTypedArray()
    private val calcValues = arrayOf("KEMENAG", "JAKIM", "SINGAPORE", "MUSLIM_WORLD_LEAGUE", "NORTH_AMERICA", "MOON_SIGHTING_COMMITTEE", "EGYPTIAN", "KARACHI", "UMM_AL_QURA", "DUBAI", "QATAR", "KUWAIT", "TEHRAN", "TURKEY", "MOROCCO")

    private var currentTextColor = "#FFFFFF"
    private var currentBgColor = "#00000000"

    private var pendingDndPermission = false
    private var tempRegularUri: String? = null
    private var tempSubuhUri: String? = null
    private var doubleBackToExitPressedOnce = false

    private var activeTextWatcher: android.text.TextWatcher? = null

    private val previewDebounceHandler = Handler(Looper.getMainLooper())
    private val previewDebounceRunnable = Runnable { updatePreview() }

    private fun schedulePreviewUpdate() {
        previewDebounceHandler.removeCallbacks(previewDebounceRunnable)
        previewDebounceHandler.postDelayed(previewDebounceRunnable, 100)
    }

    private val saveDebounceHandler = Handler(Looper.getMainLooper())
    private val saveDebounceRunnable = Runnable { performActualSave() }

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

        val devModeLayout = findViewById<View>(R.id.layoutDeveloperMode)

        if (BuildConfig.DEBUG) {
            devModeLayout.visibility = View.VISIBLE

            val devModeHelper = DeveloperModeHelper(this)
            devModeHelper.setup()
        } else {
            devModeLayout.visibility = View.GONE
        }

        checkBatteryOptimizations()
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

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            IconHelper.executePendingIconUpdate(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTestAdzan()
        previewDebounceHandler.removeCallbacks(previewDebounceRunnable)
        saveDebounceHandler.removeCallbacks(saveDebounceRunnable)
        performActualSave()
        IconHelper.executePendingIconUpdate(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            saveDebounceHandler.removeCallbacks(saveDebounceRunnable)
            previewDebounceHandler.removeCallbacks(previewDebounceRunnable)

            setContentView(R.layout.activity_main)

            window.decorView.requestApplyInsets()

            val devModeLayout = findViewById<View>(R.id.layoutDeveloperMode)
            if (BuildConfig.DEBUG) {
                devModeLayout.visibility = View.VISIBLE
                val devModeHelper = DeveloperModeHelper(this)
                devModeHelper.setup()
            } else {
                devModeLayout.visibility = View.GONE
            }

            loadSettingsToUI()
            setupButtons()
            updatePreview()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error on configuration change", e)
        }
    }

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
                if (fromUser) {
                    seekBar?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    schedulePreviewUpdate()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveSettingsQuietly()
            }
        })
    }

    private fun styleGlassBottomSheet(dialog: BottomSheetDialog) {
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT)
            bottomSheet.backgroundTintList = null
            bottomSheet.elevation = 0f

            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.skipCollapsed = true
            behavior.isFitToContents = true
            behavior.peekHeight = resources.displayMetrics.heightPixels

            // Tunda STATE_EXPANDED sampai setelah layout pass selesai
            // agar tinggi sheet sudah terukur dengan benar
            bottomSheet.post {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }

            (bottomSheet as? android.view.ViewGroup)?.clipChildren = false
            bottomSheet.parent?.let { parent ->
                (parent as? android.view.ViewGroup)?.clipChildren = false
            }

            val contentView = (bottomSheet as? android.view.ViewGroup)?.getChildAt(0)
            contentView?.apply {
                elevation = 24f
                clipToOutline = false
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        val radius = 28f * view.resources.displayMetrics.density
                        outline.setRoundRect(
                            0, 0,
                            view.width,
                            view.height + radius.toInt(),
                            radius
                        )
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dialog.window?.setBackgroundBlurRadius(50)
        }
    }

    private fun setupGlassBottomSheet(dialog: BottomSheetDialog) {
        dialog.setOnShowListener {
            styleGlassBottomSheet(dialog)
        }
    }

    private fun showBottomSheetSelector(title: String, items: Array<String>, currentItem: String, onSelected: (Int) -> Unit) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_selector, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<TextView>(R.id.tv_sheet_title)?.text = title

        // Batasi tinggi NestedScrollView agar tidak melebihi 55% layar
        val scrollView = findNestedScrollView(view)
        scrollView?.let { nsv ->
            val maxHeight = (resources.displayMetrics.heightPixels * 0.55).toInt()
            nsv.post {
                if (nsv.height > maxHeight) {
                    nsv.layoutParams = nsv.layoutParams.apply { height = maxHeight }
                }
            }
        }

        val container = view.findViewById<LinearLayout>(R.id.ll_sheet_items) ?: return
        val selectedIndex = items.indexOf(currentItem).takeIf { it >= 0 } ?: -1

        for (i in items.indices) {
            val tv = layoutInflater.inflate(R.layout.item_bottom_sheet, container, false) as TextView
            tv.text = items[i]

            if (i == selectedIndex) {
                tv.text = "\u2713  ${items[i]}"
                val typedValue = TypedValue()
                theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
                tv.setTextColor(typedValue.data)
                tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            }

            tv.setOnClickListener {
                onSelected(i)
                bottomSheetDialog.dismiss()
            }
            container.addView(tv)
        }

        setupGlassBottomSheet(bottomSheetDialog)
        bottomSheetDialog.show()
    }

    private fun findNestedScrollView(view: View): androidx.core.widget.NestedScrollView? {
        if (view is androidx.core.widget.NestedScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findNestedScrollView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
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
            val isAutoHijri = findViewById<SwitchCompat>(R.id.sw_auto_hijri)?.isChecked ?: settingsManager.isAutoHijriOffset
            findViewById<SeekBar>(R.id.sb_hijri_offset)?.isEnabled = !isAutoHijri
            var offsetHijri = (findViewById<SeekBar>(R.id.sb_hijri_offset)?.progress ?: 2) - 2

            if (isAutoHijri && settingsManager.latitude != null && settingsManager.longitude != null) {
                try {
                    val lat = settingsManager.latitude!!.toDouble()
                    val lon = settingsManager.longitude!!.toDouble()
                    val criteriaStr = hilalCriteriaValues[
                        hilalCriteriaEntries.indexOf(
                            findViewById<MaterialButton>(R.id.btn_hilal_criteria)?.text?.toString() ?: ""
                        ).takeIf { it >= 0 } ?: 0
                    ]
                    val criteria = HilalCriteria.fromName(criteriaStr)
                    offsetHijri = IslamicAstronomy.calculateHijriOffset(lat, lon, criteria = criteria ?: HilalCriteria.NEO_MABIMS)

                    val seekBar = findViewById<SeekBar>(R.id.sb_hijri_offset)
                    seekBar?.progress = offsetHijri + 2
                    val displayValue = if (offsetHijri > 0) "+$offsetHijri" else "$offsetHijri"
                    findViewById<TextView>(R.id.tv_label_hijri)?.text = "$displayValue ✦"
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Auto hijri offset error", e)
                }
            } else if (findViewById<SeekBar>(R.id.sb_hijri_offset) != null) {
                val displayValue = if (offsetHijri > 0) "+$offsetHijri" else "$offsetHijri"
                findViewById<TextView>(R.id.tv_label_hijri)?.text = "$displayValue Hari"
            }

            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val activeLangCode = if (!currentLocales.isEmpty) currentLocales[0]!!.language else settingsManager.languageCode

            val selectedLocale = Locale.forLanguageTag(activeLangCode)
            val config = Configuration(resources.configuration)
            config.setLocale(selectedLocale)
            val localizedContext = createConfigurationContext(config)

            val txtSunrise = localizedContext.getString(R.string.sunrise)
            val txtLastThird = localizedContext.getString(R.string.last_third)
            val txtQibla = localizedContext.getString(R.string.qibla)

            val selectedCalcStr = findViewById<MaterialButton>(R.id.btn_calc_method)?.text?.toString() ?: ""
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

            var nextPrayerIndexForPreview = -1

            if (settingsManager.latitude != null && settingsManager.longitude != null) {
                try {
                    val lat = settingsManager.latitude!!.toDouble()
                    val lon = settingsManager.longitude!!.toDouble()

                    val prayerTimes = IslamicAppUtils.calculatePrayerTimes(lat, lon, calcValues[calcIdx], today)
                    val tomorrowPrayer = IslamicAppUtils.calculatePrayerTimes(lat, lon, calcValues[calcIdx], today.plusDays(1))
                    val sunnahTimes = IslamicAstronomy.getSunnahTimes(prayerTimes, tomorrowPrayer)
                    val qiblaDegree = IslamicAstronomy.calculateQibla(lat, lon)

                    val currentTime = Date()
                    var isAfterMaghrib = false
                    if (findViewById<CheckBox>(R.id.cb_day_start)?.isChecked == true && currentTime.after(prayerTimes.maghrib)) {
                        totalHijriOffset += 1L
                        isAfterMaghrib = true
                    }
                    val isBeforeFajr = currentTime.before(prayerTimes.fajr)

                    val timeFormatter = SimpleDateFormat(timePattern, selectedLocale)
                    timeFormatter.timeZone = TimeZone.getDefault()

                    findViewById<TextView>(R.id.tv_fajr_time)?.text = timeFormatter.format(prayerTimes.fajr)
                    findViewById<TextView>(R.id.tv_dhuhr_time)?.text = timeFormatter.format(prayerTimes.dhuhr)
                    findViewById<TextView>(R.id.tv_asr_time)?.text = timeFormatter.format(prayerTimes.asr)
                    findViewById<TextView>(R.id.tv_maghrib_time)?.text = timeFormatter.format(prayerTimes.maghrib)
                    findViewById<TextView>(R.id.tv_isha_time)?.text = timeFormatter.format(prayerTimes.isha)

                    findViewById<TextView>(R.id.tv_sunrise)?.text = "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise)}"
                    findViewById<TextView>(R.id.tv_last_third)?.text = "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight)}"
                    findViewById<TextView>(R.id.tv_qibla)?.text = String.format(selectedLocale, "%s: %.1f°", txtQibla, qiblaDegree)

                    val prayerDatesForPreview = arrayOf(
                        prayerTimes.fajr,
                        prayerTimes.dhuhr,
                        prayerTimes.asr,
                        prayerTimes.maghrib,
                        prayerTimes.isha
                    )
                    for (i in prayerDatesForPreview.indices) {
                        if (Date().before(prayerDatesForPreview[i])) {
                            nextPrayerIndexForPreview = i
                            break
                        }
                    }
                    if (nextPrayerIndexForPreview == -1) nextPrayerIndexForPreview = 0

                    if (totalHijriOffset != 0L) hijriDate = hijriDate.plus(totalHijriOffset, ChronoUnit.DAYS)

                    val sunnahInfo = IslamicAppUtils.getSunnahFastingInfo(localizedContext, hijriDate, today, isAfterMaghrib, isBeforeFajr)
                    val flipper = findViewById<ViewFlipper>(R.id.container_additional_flipper)

                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    val currentVersionName = packageInfo.versionName ?: "1.0"
                    val isUpdateAvailable = UpdateHelper.isVersionNewer(currentVersionName, settingsManager.latestVersionName)

                    if (isShowAdd) {
                        findViewById<View>(R.id.container_additional_normal)?.visibility = View.GONE
                        flipper?.visibility = View.VISIBLE
                        flipper?.removeAllViews()

                        val normalView = layoutInflater.inflate(R.layout.item_flipper_normal, flipper, false)
                        normalView.findViewById<TextView>(R.id.tv_sunrise_flip)?.apply { text = "$txtSunrise: ${timeFormatter.format(prayerTimes.sunrise)}" }
                        normalView.findViewById<TextView>(R.id.tv_last_third_flip)?.apply { text = "$txtLastThird: ${timeFormatter.format(sunnahTimes.lastThirdOfTheNight)}" }
                        normalView.findViewById<TextView>(R.id.tv_qibla_flip)?.apply { text = String.format(selectedLocale, "%s: %.1f°", txtQibla, qiblaDegree) }
                        flipper?.addView(normalView)

                        try {
                            var fullQuote = "Barangsiapa yang menempuh suatu jalan untuk mencari ilmu, maka Allah akan mudahkan baginya jalan menuju surga. - HR. Muslim"
                            try {
                                val quoteHelper = QuoteDatabaseHelper.getInstance(this@MainActivity)
                                val quotePair = quoteHelper.getRandomQuote()
                                if (quotePair != null) {
                                    fullQuote = "${quotePair.first} - ${quotePair.second}"
                                }
                            } catch (e: Exception) {}

                            val maxLengthPerSlide = 55
                            val words = fullQuote.split(" ")
                            var currentSlideText = ""

                            for (word in words) {
                                if (currentSlideText.length + word.length + 1 <= maxLengthPerSlide) {
                                    currentSlideText += if (currentSlideText.isEmpty()) word else " $word"
                                } else {
                                    val quoteView = layoutInflater.inflate(R.layout.item_flipper_text, flipper, false) as TextView
                                    quoteView.text = currentSlideText
                                    quoteView.setTextColor(Color.parseColor("#81D4FA"))
                                    flipper?.addView(quoteView)
                                    currentSlideText = word
                                }
                            }

                            if (currentSlideText.isNotEmpty()) {
                                val quoteView = layoutInflater.inflate(R.layout.item_flipper_text, flipper, false) as TextView
                                quoteView.text = currentSlideText
                                quoteView.setTextColor(Color.parseColor("#81D4FA"))
                                flipper?.addView(quoteView)
                            }
                        } catch (e: Exception) {}

                        if (sunnahInfo.isNotEmpty()) {
                            val sunnahView = layoutInflater.inflate(R.layout.item_flipper_text, flipper, false) as TextView
                            sunnahView.text = sunnahInfo
                            sunnahView.setTextColor(Color.parseColor("#FFC107"))
                            flipper?.addView(sunnahView)
                        }

                        if (isUpdateAvailable) {
                            val updateMsg = localizedContext.getString(R.string.update_available_msg, settingsManager.latestVersionName)
                            val updateView = layoutInflater.inflate(R.layout.item_flipper_text, flipper, false) as TextView
                            updateView.text = updateMsg
                            updateView.setTextColor(Color.parseColor("#4CAF50"))
                            flipper?.addView(updateView)
                        }
                    } else {
                        findViewById<View>(R.id.container_additional_normal)?.visibility = View.GONE
                        flipper?.visibility = View.GONE
                    }

                } catch (e: Exception) { 
                    setDummyPreviewTimes(activeLangCode)
                    Log.e("MainActivity", "Error updating preview", e)
                }
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
            bgDrawable?.setColor(bgColor)
            previewBg.setImageDrawable(bgDrawable)
            previewBg.colorFilter = null

            findViewById<TextView>(R.id.clock_widget)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsClock); setTextColor(textColor) }
            findViewById<TextView>(R.id.tv_gregorian_date)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsDate - 2f); setTextColor(textColor) }
            findViewById<TextView>(R.id.tv_hijri_date)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsDate); setTextColor(textColor) }

            val prayerLabelIds = arrayOf(R.id.label_fajr, R.id.label_dhuhr, R.id.label_asr, R.id.label_maghrib, R.id.label_isha)
            val prayerTimeIds = arrayOf(R.id.tv_fajr_time, R.id.tv_dhuhr_time, R.id.tv_asr_time, R.id.tv_maghrib_time, R.id.tv_isha_time)

            for (id in prayerLabelIds) {
                findViewById<TextView>(id)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsPrayer - 2f); setTextColor(textColor) }
            }
            for (id in prayerTimeIds) {
                findViewById<TextView>(id)?.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, fsPrayer); setTextColor(textColor) }
            }

            if (nextPrayerIndexForPreview >= 0) {
                val highlightColor = Color.parseColor("#FFC107")
                for (i in prayerLabelIds.indices) {
                    val color = if (i == nextPrayerIndexForPreview) highlightColor else textColor
                    findViewById<TextView>(prayerLabelIds[i])?.setTextColor(color)
                    findViewById<TextView>(prayerTimeIds[i])?.setTextColor(color)
                }
            }

            val additionalTextIds = listOf(
                R.id.tv_sunrise, R.id.tv_last_third, R.id.tv_qibla, R.id.tv_divider_1, R.id.tv_divider_2,
                R.id.tv_sunrise_flip, R.id.tv_last_third_flip, R.id.tv_qibla_flip, R.id.tv_divider_1_flip, R.id.tv_divider_2_flip
            )
            for (id in additionalTextIds) {
                findViewById<TextView>(id)?.apply { setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd) }
            }

            val flipper = findViewById<ViewFlipper>(R.id.container_additional_flipper)
            if (flipper != null && flipper.childCount > 0) {
                for (i in 0 until flipper.childCount) {
                    val child = flipper.getChildAt(i)
                    if (child is TextView && child.id == R.id.tv_item_text) {
                        child.setTextSize(TypedValue.COMPLEX_UNIT_SP, fsAdd)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in updatePreview", e)
        }
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
        AudioAdzanManager.stopTestAdzan(this) {
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

        findViewById<MaterialButton>(R.id.btn_language)?.let { btn ->
            val initialIdx = languageValues.indexOf(activeLangCode).takeIf { it >= 0 } ?: 0
            btn.text = languageEntries[initialIdx]
            btn.setOnClickListener {
                showBottomSheetSelector(getString(R.string.sec_lang_theme), languageEntries, btn.text.toString()) { pos ->
                    val code = languageValues[pos]
                    if (code != activeLangCode) {
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

                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                            androidx.core.os.LocaleListCompat.forLanguageTags(code)
                        )

                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.btn_theme)?.let { btn ->
            val initialIdx = themeValues.indexOf(settingsManager.appTheme).takeIf { it >= 0 } ?: 0
            btn.text = themeEntries[initialIdx]
            btn.setOnClickListener {
                showBottomSheetSelector(getString(R.string.theme_label), themeEntries, btn.text.toString()) { pos ->
                    btn.text = themeEntries[pos]
                    settingsManager.appTheme = themeValues[pos]
                    updatePreview()
                    saveSettingsQuietly()
                    val nightMode = when (themeValues[pos]) {
                        "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
                        "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    AppCompatDelegate.setDefaultNightMode(nightMode)

                }
            }
        }

        setupSlider(R.id.sb_preview_scale, R.id.tv_preview_scale_label, 50, 150, settingsManager.previewScale, true, "")
        updateLocationUI()

        findViewById<MaterialButton>(R.id.btn_calc_method)?.let { btn ->
            val initialIdx = calcValues.indexOf(settingsManager.calculationMethod).takeIf { it >= 0 } ?: 0
            btn.text = calcEntries[initialIdx]
            btn.setOnClickListener {
                showBottomSheetSelector(getString(R.string.calc_method), calcEntries, btn.text.toString()) { pos ->
                    btn.text = calcEntries[pos]
                    updatePreview()
                    saveSettingsQuietly()
                }
            }
        }

        findViewById<SwitchCompat>(R.id.sw_show_clock)?.apply { isChecked = settingsManager.showClock; setOnCheckedChangeListener { view, _ -> if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); updatePreview(); saveSettingsQuietly() } }
        findViewById<SwitchCompat>(R.id.sw_show_date)?.apply { isChecked = settingsManager.showDate; setOnCheckedChangeListener { view, _ -> if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); updatePreview(); saveSettingsQuietly() } }
        findViewById<SwitchCompat>(R.id.sw_show_prayer)?.apply { isChecked = settingsManager.showPrayer; setOnCheckedChangeListener { view, _ -> if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); updatePreview(); saveSettingsQuietly() } }
        findViewById<SwitchCompat>(R.id.sw_show_additional)?.apply { isChecked = settingsManager.showAdditional; setOnCheckedChangeListener { view, _ -> if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); updatePreview(); saveSettingsQuietly() } }

        setupSlider(R.id.sb_fs_clock, R.id.tv_fs_clock, 20, 80, settingsManager.fontSizeClock, false, "sp")
        setupSlider(R.id.sb_fs_date, R.id.tv_fs_date, 8, 30, settingsManager.fontSizeDate, false, "sp")
        setupSlider(R.id.sb_fs_prayer, R.id.tv_fs_prayer, 8, 30, settingsManager.fontSizePrayer, false, "sp")
        setupSlider(R.id.sb_fs_additional, R.id.tv_fs_additional, 8, 24, settingsManager.fontSizeAdditional, false, "sp")
        setupSlider(R.id.sb_radius, R.id.tv_label_radius, 0, 60, settingsManager.widgetBgRadius, false, "dp")
        findViewById<SwitchCompat>(R.id.sw_auto_hijri)?.apply {
            isChecked = settingsManager.isAutoHijriOffset
            setOnCheckedChangeListener { view, isChecked ->
                if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                findViewById<SeekBar>(R.id.sb_hijri_offset)?.isEnabled = !isChecked
                findViewById<MaterialButton>(R.id.btn_hilal_criteria)?.visibility =
                    if (isChecked) View.VISIBLE else View.GONE
                updatePreview()
                saveSettingsQuietly()
            }
        }

        findViewById<MaterialButton>(R.id.btn_hilal_criteria)?.let { btn ->
            val savedIdx = hilalCriteriaValues.indexOf(settingsManager.hilalCriteria).takeIf { it >= 0 } ?: 0
            btn.text = hilalCriteriaEntries[savedIdx]
            btn.setOnClickListener {
                showBottomSheetSelector(getString(R.string.hilal_criteria_label), hilalCriteriaEntries, btn.text.toString()) { pos ->
                    btn.text = hilalCriteriaEntries[pos]
                    updatePreview()
                    saveSettingsQuietly()
                }
            }
        }

        findViewById<MaterialButton>(R.id.btn_hilal_criteria)?.visibility =
            if (settingsManager.isAutoHijriOffset) View.VISIBLE else View.GONE

        setupSlider(R.id.sb_hijri_offset, R.id.tv_label_hijri, -2, 2, settingsManager.hijriOffset, false, "")

        findViewById<SeekBar>(R.id.sb_hijri_offset)?.isEnabled = !settingsManager.isAutoHijriOffset

        currentTextColor = settingsManager.widgetTextColor
        currentBgColor = settingsManager.widgetBgColor
        updateColorButtons()

        findViewById<CheckBox>(R.id.cb_day_start)?.apply { isChecked = settingsManager.isDayStartAtMaghrib; setOnCheckedChangeListener { view, _ -> if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); updatePreview(); saveSettingsQuietly() } }

        val tw = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) { saveSettingsQuietly() }
        }
        activeTextWatcher?.let {
            findViewById<EditText>(R.id.et_date_format)?.removeTextChangedListener(it)
            findViewById<EditText>(R.id.et_hijri_format)?.removeTextChangedListener(it)
        }
        activeTextWatcher = tw
        findViewById<EditText>(R.id.et_date_format)?.apply { setText(settingsManager.dateFormat); addTextChangedListener(tw) }
        findViewById<EditText>(R.id.et_hijri_format)?.apply { setText(settingsManager.hijriFormat); addTextChangedListener(tw) }

        findViewById<SwitchCompat>(R.id.switch_auto_silent)?.apply {
            isChecked = settingsManager.isAutoSilentEnabled
            setOnCheckedChangeListener { view, isChecked ->
                if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (isChecked && !(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted) {
                    this.isChecked = false; pendingDndPermission = true
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } else {
                    saveSettingsQuietly()
                }
            }
        }

        val swAdvancedDnd = findViewById<SwitchCompat>(R.id.sw_advanced_dnd)
        val advancedDndLayout = findViewById<View>(R.id.layout_advanced_dnd)
        swAdvancedDnd?.isChecked = false
        swAdvancedDnd?.setOnCheckedChangeListener { view, isChecked ->
            if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            val parent = advancedDndLayout?.parent as? android.view.ViewGroup
            if (parent != null) {
                androidx.transition.TransitionManager.beginDelayedTransition(
                    parent,
                    androidx.transition.AutoTransition().apply { duration = 250 }
                )
            }
            advancedDndLayout?.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.btn_advanced_dnd)?.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            swAdvancedDnd?.toggle()
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
            setOnCheckedChangeListener { view, isChecked ->
                if (view.isPressed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        bottomSheetDialog.setContentView(dialogView)

        var currentColor = try { Color.parseColor(initialColorHex) } catch (e: Exception) { Color.BLACK }

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_color_title)
        tvTitle?.text = title

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

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_color_confirm)?.setOnClickListener {
            onColorSelected(String.format("#%08X", currentColor).uppercase())
            bottomSheetDialog.dismiss()
        }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_color_cancel)?.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        setupGlassBottomSheet(bottomSheetDialog)
        bottomSheetDialog.show()
    }

    private fun saveSettingsQuietly() {
        try {
            val calcSpinnerStr = findViewById<MaterialButton>(R.id.btn_calc_method)?.text?.toString() ?: ""

            val newInterval = findViewById<SeekBar>(R.id.sb_quote_interval)?.progress ?: 0

            settingsManager.saveAllSettings(
                previewScale = (findViewById<SeekBar>(R.id.sb_preview_scale)?.progress ?: 50) + 50,
                calculationMethod = calcValues[calcEntries.indexOf(calcSpinnerStr).takeIf { it >= 0 } ?: 0],
                showClock = findViewById<SwitchCompat>(R.id.sw_show_clock)?.isChecked ?: true,
                showDate = findViewById<SwitchCompat>(R.id.sw_show_date)?.isChecked ?: true,
                showPrayer = findViewById<SwitchCompat>(R.id.sw_show_prayer)?.isChecked ?: true,
                showAdditional = findViewById<SwitchCompat>(R.id.sw_show_additional)?.isChecked ?: true,
                fontSizeClock = (findViewById<SeekBar>(R.id.sb_fs_clock)?.progress ?: 16) + 20,
                fontSizeDate = (findViewById<SeekBar>(R.id.sb_fs_date)?.progress ?: 4) + 8,
                fontSizePrayer = (findViewById<SeekBar>(R.id.sb_fs_prayer)?.progress ?: 5) + 8,
                fontSizeAdditional = (findViewById<SeekBar>(R.id.sb_fs_additional)?.progress ?: 3) + 8,
                widgetBgRadius = findViewById<SeekBar>(R.id.sb_radius)?.progress ?: 16,
                hijriOffset = (findViewById<SeekBar>(R.id.sb_hijri_offset)?.progress ?: 2) - 2,
                isAutoHijriOffset = findViewById<SwitchCompat>(R.id.sw_auto_hijri)?.isChecked ?: true,
                hilalCriteria = hilalCriteriaValues[
                    hilalCriteriaEntries.indexOf(
                        findViewById<MaterialButton>(R.id.btn_hilal_criteria)?.text?.toString() ?: ""
                    ).takeIf { it >= 0 } ?: 0
                ],
                widgetTextColor = currentTextColor,
                widgetBgColor = currentBgColor,
                isDayStartAtMaghrib = findViewById<CheckBox>(R.id.cb_day_start)?.isChecked ?: true,
                dateFormat = findViewById<EditText>(R.id.et_date_format)?.text?.toString()?.ifEmpty { settingsManager.dateFormat } ?: settingsManager.dateFormat,
                hijriFormat = findViewById<EditText>(R.id.et_hijri_format)?.text?.toString()?.ifEmpty { settingsManager.hijriFormat } ?: settingsManager.hijriFormat,
                isAutoSilentEnabled = findViewById<SwitchCompat>(R.id.switch_auto_silent)?.isChecked ?: false,
                fajrBefore = findViewById<SeekBar>(R.id.sb_fajr_bef)?.progress ?: 5,
                fajrAfter = findViewById<SeekBar>(R.id.sb_fajr_aft)?.progress ?: 15,
                dhuhrBefore = findViewById<SeekBar>(R.id.sb_dhuhr_bef)?.progress ?: 5,
                dhuhrAfter = findViewById<SeekBar>(R.id.sb_dhuhr_aft)?.progress ?: 15,
                fridayBefore = findViewById<SeekBar>(R.id.sb_fri_bef)?.progress ?: 10,
                fridayAfter = findViewById<SeekBar>(R.id.sb_fri_aft)?.progress ?: 45,
                asrBefore = findViewById<SeekBar>(R.id.sb_asr_bef)?.progress ?: 5,
                asrAfter = findViewById<SeekBar>(R.id.sb_asr_aft)?.progress ?: 15,
                maghribBefore = findViewById<SeekBar>(R.id.sb_maghrib_bef)?.progress ?: 5,
                maghribAfter = findViewById<SeekBar>(R.id.sb_maghrib_aft)?.progress ?: 15,
                ishaBefore = findViewById<SeekBar>(R.id.sb_isha_bef)?.progress ?: 5,
                ishaAfter = findViewById<SeekBar>(R.id.sb_isha_aft)?.progress ?: 15,
                isAdzanAudioEnabled = findViewById<SwitchCompat>(R.id.switch_audio_adzan)?.isChecked ?: false,
                adzanVolume = findViewById<SeekBar>(R.id.sb_adzan_vol)?.progress ?: 80,
                customAdzanRegularUri = tempRegularUri,
                customAdzanSubuhUri = tempSubuhUri,
                quoteUpdateInterval = newInterval,
                quoteFontSize = (findViewById<SeekBar>(R.id.sb_quote_font)?.progress ?: 4) + 10,
                quoteBgAlpha = findViewById<SeekBar>(R.id.sb_quote_alpha)?.progress ?: 153
            )

            QuoteUpdateManager.setAutoUpdate(this, newInterval)

            saveDebounceHandler.removeCallbacks(saveDebounceRunnable)
            saveDebounceHandler.postDelayed(saveDebounceRunnable, 500)

            Log.d("MainActivity", "Settings saved to SharedPrefs, broadcast scheduled (debounced)")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving settings", e)
        }
    }

    private fun performActualSave() {
        try {
            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, IslamicWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                sendBroadcast(Intent(this, IslamicWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }

            val quoteIds = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, QuoteWidgetProvider::class.java))
            if (quoteIds.isNotEmpty()) {
                sendBroadcast(Intent(this, QuoteWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, quoteIds)
                })
            }

            Log.d("MainActivity", "Widget broadcast sent (debounced)")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error sending widget broadcast", e)
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_pin_main_widget)?.setOnClickListener { requestPinWidget(IslamicWidgetProvider::class.java) }
        findViewById<Button>(R.id.btn_pin_quote_widget)?.setOnClickListener { requestPinWidget(QuoteWidgetProvider::class.java) }

        findViewById<Button>(R.id.btn_pick_text_color)?.setOnClickListener { 
            showColorPickerDialog(getString(R.string.btn_text_color), currentTextColor) { selectedHex -> 
                currentTextColor = selectedHex
                updateColorButtons()
                updatePreview()
                saveSettingsQuietly() 
            } 
        }
        
        findViewById<Button>(R.id.btn_pick_bg_color)?.setOnClickListener { 
            showColorPickerDialog(getString(R.string.btn_bg_color), currentBgColor) { selectedHex -> 
                currentBgColor = selectedHex
                updateColorButtons()
                updatePreview()
                saveSettingsQuietly() 
            } 
        }

        findViewById<Button>(R.id.btn_update_gps)?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        findViewById<Button>(R.id.btn_restore)?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_reset_title))
                .setMessage(getString(R.string.dialog_reset_message))
                .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                    findViewById<FrameLayout>(R.id.loading_overlay)?.visibility = View.VISIBLE
                    Handler(Looper.getMainLooper()).postDelayed({
                        cancelAllScheduledAlarms()
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
            val bottomSheetDialog = BottomSheetDialog(this)
            val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
            bottomSheetDialog.setContentView(dialogView)

            val versionName = try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                packageInfo.versionName ?: "1.0"
            } catch (e: Exception) { 
                "1.0" 
            }

            dialogView.findViewById<TextView>(R.id.tv_app_version)?.text = "Version $versionName"

            dialogView.findViewById<Button>(R.id.btn_open_github)?.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cyberzilla")))
            }

            setupGlassBottomSheet(bottomSheetDialog)
            bottomSheetDialog.show()
        }
    }

    private fun cancelAllScheduledAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (prayerId in 1..5) {
            val rcMute = 10000 + prayerId
            val rcUnmute = 20000 + prayerId
            val rcAdzan = 30000 + prayerId

            val muteIntent = Intent(this, SilentModeReceiver::class.java).apply { action = "ACTION_MUTE" }
            val unmuteIntent = Intent(this, SilentModeReceiver::class.java).apply { action = "ACTION_UNMUTE" }
            val adzanIntent = Intent(this, SilentModeReceiver::class.java).apply { action = "ACTION_PLAY_ADZAN" }

            PendingIntent.getBroadcast(this, rcMute, muteIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { 
                alarmManager.cancel(it)
                it.cancel()
            }
            PendingIntent.getBroadcast(this, rcUnmute, unmuteIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { 
                alarmManager.cancel(it)
                it.cancel()
            }
            PendingIntent.getBroadcast(this, rcAdzan, adzanIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { 
                alarmManager.cancel(it)
                it.cancel()
            }
        }

        val quoteIntent = Intent(this, QuoteWidgetProvider::class.java).apply {
            action = QuoteWidgetProvider.ACTION_RANDOM_QUOTE
        }
        PendingIntent.getBroadcast(this, 1001, quoteIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { 
            alarmManager.cancel(it)
            it.cancel()
        }
        
        Log.d("MainActivity", "All scheduled alarms cancelled")
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        Toast.makeText(this, getString(R.string.toast_searching_location), Toast.LENGTH_SHORT).show()
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    settingsManager.latitude = location.latitude.toString()
                    settingsManager.longitude = location.longitude.toString()
                    try {
                        val geocoder = Geocoder(this, Locale.getDefault())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            geocoder.getFromLocation(location.latitude, location.longitude, 1) { addrs ->
                                runOnUiThread { updateAddr(addrs.firstOrNull()) }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            updateAddr(geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull())
                        }
                    } catch(e: Exception) { 
                        updateAddr(null)
                        Log.e("MainActivity", "Geocoder error", e)
                    }
                } else {
                    Toast.makeText(this, getString(R.string.toast_location_not_found), Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, getString(R.string.toast_location_error, exception.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Location fetch failed", exception)
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

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
