package com.cyberzilla.islamicwidget

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View // <-- INI IMPORT YANG HILANG SEBELUMNYA
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsManager: SettingsManager

    private val languageEntries = arrayOf("Indonesia", "English", "العربية (Arabic)")
    private val languageValues = arrayOf("id", "en", "ar")

    private val calcEntries = arrayOf("Muslim World League", "Egyptian", "Karachi", "Umm Al-Qura", "Dubai", "Qatar", "Kuwait", "Moonsighting Committee", "Singapore")
    private val calcValues = arrayOf("MUSLIM_WORLD_LEAGUE", "EGYPTIAN", "KARACHI", "UMM_AL_QURA", "DUBAI", "QATAR", "KUWAIT", "MOON_SIGHTING_COMMITTEE", "SINGAPORE")

    private var currentTextColor = "#FFFFFF"
    private var currentBgColor = "#B3000000"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsManager = SettingsManager(this)

        loadSettingsToUI()
        setupButtons()
    }

    private fun setupSlider(seekBarId: Int, textViewId: Int, min: Int, max: Int, initialValue: Int, labelPrefix: String, labelSuffix: String) {
        val seekBar = findViewById<SeekBar>(seekBarId)
        val textView = findViewById<TextView>(textViewId)

        seekBar.max = max - min
        seekBar.progress = initialValue - min
        textView.text = "$labelPrefix${initialValue}$labelSuffix"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actualValue = progress + min
                textView.text = "$labelPrefix${actualValue}$labelSuffix"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadSettingsToUI() {
        val langSpinner = findViewById<Spinner>(R.id.spinner_language)
        langSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageEntries)
        langSpinner.setSelection(languageValues.indexOf(settingsManager.languageCode).takeIf { it >= 0 } ?: 0)

        val calcSpinner = findViewById<Spinner>(R.id.spinner_calc_method)
        calcSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, calcEntries)
        calcSpinner.setSelection(calcValues.indexOf(settingsManager.calculationMethod).takeIf { it >= 0 } ?: 0)

        // Setup Sliders Visual & Hijri
        setupSlider(R.id.sb_font_size, R.id.tv_label_font, 10, 40, settingsManager.widgetFontSize, "Ukuran Font: ", "sp")
        setupSlider(R.id.sb_radius, R.id.tv_label_radius, 0, 60, settingsManager.widgetBgRadius, "Radius: ", "dp")
        setupSlider(R.id.sb_hijri_offset, R.id.tv_label_hijri, -5, 5, settingsManager.hijriOffset, "Koreksi Hijriyah: ", " Hari")

        currentTextColor = settingsManager.widgetTextColor
        currentBgColor = settingsManager.widgetBgColor
        updateColorButtons()

        findViewById<CheckBox>(R.id.cb_day_start).isChecked = settingsManager.isDayStartAtMaghrib
        findViewById<EditText>(R.id.et_date_format).setText(settingsManager.dateFormat)
        findViewById<Switch>(R.id.switch_auto_silent).isChecked = settingsManager.isAutoSilentEnabled

        // Setup Sliders Waktu Sholat
        setupSlider(R.id.sb_fajr_bef, R.id.tv_fajr_bef, 0, 60, settingsManager.fajrBefore, "Sblm: ", "m")
        setupSlider(R.id.sb_fajr_aft, R.id.tv_fajr_aft, 0, 120, settingsManager.fajrAfter, "Ssdh: ", "m")

        setupSlider(R.id.sb_dhuhr_bef, R.id.tv_dhuhr_bef, 0, 60, settingsManager.dhuhrBefore, "Sblm: ", "m")
        setupSlider(R.id.sb_dhuhr_aft, R.id.tv_dhuhr_aft, 0, 120, settingsManager.dhuhrAfter, "Ssdh: ", "m")
        setupSlider(R.id.sb_dhuhr_fri, R.id.tv_dhuhr_fri, 0, 180, settingsManager.dhuhrFriday, "Khusus Jumat: ", "m")

        setupSlider(R.id.sb_asr_bef, R.id.tv_asr_bef, 0, 60, settingsManager.asrBefore, "Sblm: ", "m")
        setupSlider(R.id.sb_asr_aft, R.id.tv_asr_aft, 0, 120, settingsManager.asrAfter, "Ssdh: ", "m")

        setupSlider(R.id.sb_maghrib_bef, R.id.tv_maghrib_bef, 0, 60, settingsManager.maghribBefore, "Sblm: ", "m")
        setupSlider(R.id.sb_maghrib_aft, R.id.tv_maghrib_aft, 0, 120, settingsManager.maghribAfter, "Ssdh: ", "m")

        setupSlider(R.id.sb_isha_bef, R.id.tv_isha_bef, 0, 60, settingsManager.ishaBefore, "Sblm: ", "m")
        setupSlider(R.id.sb_isha_aft, R.id.tv_isha_aft, 0, 120, settingsManager.ishaAfter, "Ssdh: ", "m")
    }

    private fun updateColorButtons() {
        val btnText = findViewById<Button>(R.id.btn_pick_text_color)
        val btnBg = findViewById<Button>(R.id.btn_pick_bg_color)

        try { btnText.setBackgroundColor(Color.parseColor(currentTextColor)) } catch (e: Exception) {}
        try { btnBg.setBackgroundColor(Color.parseColor(currentBgColor)) } catch (e: Exception) {}
    }

    // ==========================================
    // LOGIKA CUSTOM COLOR PICKER DENGAN TRANSPARAN
    // ==========================================
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
            val tv = TextView(this).apply { text = label; layoutParams = LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT) }
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
        layout.addView(createColorSlider("Alpha", Color.alpha(currentColor)) { currentColor = Color.argb(it, Color.red(currentColor), Color.green(currentColor), Color.blue(currentColor)) })
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
            settingsManager.languageCode = languageValues[findViewById<Spinner>(R.id.spinner_language).selectedItemPosition]
            settingsManager.calculationMethod = calcValues[findViewById<Spinner>(R.id.spinner_calc_method).selectedItemPosition]

            // Membaca nilai asli dari Progress SeekBar + Min Value yang kita atur di awal
            settingsManager.widgetFontSize = findViewById<SeekBar>(R.id.sb_font_size).progress + 10
            settingsManager.widgetBgRadius = findViewById<SeekBar>(R.id.sb_radius).progress + 0
            settingsManager.hijriOffset = findViewById<SeekBar>(R.id.sb_hijri_offset).progress - 5

            settingsManager.widgetTextColor = currentTextColor
            settingsManager.widgetBgColor = currentBgColor

            settingsManager.isDayStartAtMaghrib = findViewById<CheckBox>(R.id.cb_day_start).isChecked
            settingsManager.dateFormat = findViewById<EditText>(R.id.et_date_format).text.toString().ifEmpty { "EEEE, dd MMMM yyyy" }
            settingsManager.isAutoSilentEnabled = findViewById<Switch>(R.id.switch_auto_silent).isChecked

            settingsManager.fajrBefore = findViewById<SeekBar>(R.id.sb_fajr_bef).progress
            settingsManager.fajrAfter = findViewById<SeekBar>(R.id.sb_fajr_aft).progress
            settingsManager.dhuhrBefore = findViewById<SeekBar>(R.id.sb_dhuhr_bef).progress
            settingsManager.dhuhrAfter = findViewById<SeekBar>(R.id.sb_dhuhr_aft).progress
            settingsManager.dhuhrFriday = findViewById<SeekBar>(R.id.sb_dhuhr_fri).progress
            settingsManager.asrBefore = findViewById<SeekBar>(R.id.sb_asr_bef).progress
            settingsManager.asrAfter = findViewById<SeekBar>(R.id.sb_asr_aft).progress
            settingsManager.maghribBefore = findViewById<SeekBar>(R.id.sb_maghrib_bef).progress
            settingsManager.maghribAfter = findViewById<SeekBar>(R.id.sb_maghrib_aft).progress
            settingsManager.ishaBefore = findViewById<SeekBar>(R.id.sb_isha_bef).progress
            settingsManager.ishaAfter = findViewById<SeekBar>(R.id.sb_isha_aft).progress

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
            }
        }

        findViewById<Button>(R.id.btn_pick_bg_color).setOnClickListener {
            showColorPickerDialog("Pilih Warna Latar", currentBgColor) { selectedHex ->
                currentBgColor = selectedHex
                updateColorButtons()
            }
        }

        findViewById<Button>(R.id.btn_save_settings).setOnClickListener { saveSettingsFromUI() }
        findViewById<Button>(R.id.btn_req_location).setOnClickListener { checkLocationPermission() }
        findViewById<Button>(R.id.btn_req_dnd).setOnClickListener { requestDndPermission() }

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
                .setMessage("Islamic Widget\nVersi 1.0\n\nDikembangkan oleh:\ncyberzilla (Dedy)\n\nFitur:\n• Adhan2 KMP\n• Custom Slider & Color Picker\n• Responsive Widget")
                .setPositiveButton("Tutup", null)
                .setNeutralButton("GitHub") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cyberzilla"))) }
                .show()
        }
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
                        triggerWidgetUpdate()
                        Toast.makeText(this@MainActivity, "Lokasi GPS diperbarui!", Toast.LENGTH_LONG).show()
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

    private fun requestDndPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Izin DND aktif!", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
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