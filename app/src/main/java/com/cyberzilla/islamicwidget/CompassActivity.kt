package com.cyberzilla.islamicwidget

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.cyberzilla.islamicwidget.utils.IslamicAstronomy
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class CompassActivity : AppCompatActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var useRotationVector = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var gravity = FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private var qiblaDegree = 0f
    private var magneticDeclination = 0f

    private var isGravityInit = false
    private var isGeomagneticInit = false

    private var currentAzimuth = 0f
    private var dialRotation = 0f
    private var dialVelocity = 0f
    private var needleRotation = 0f
    private var needleVelocity = 0f

    private var flDial: FrameLayout? = null
    private var ivNeedle: ImageView? = null
    private var ivStaticNeedle: ImageView? = null
    private var tvDegree: TextView? = null
    private var tvCenterHeading: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val settingsManager = SettingsManager(this)
            when (settingsManager.appTheme) {
                "LIGHT" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "DARK" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        } catch (e: Exception) {}

        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        try {
            setContentView(R.layout.activity_compass)

            flDial = findViewById(R.id.fl_compass_dial)
            ivNeedle = findViewById(R.id.iv_qibla_needle)
            ivStaticNeedle = findViewById(R.id.iv_static_needle)
            tvDegree = findViewById(R.id.tv_compass_degree)
            tvCenterHeading = findViewById(R.id.tv_center_heading)

            findViewById<ImageView>(R.id.btn_close_compass)?.setOnClickListener { finish() }

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            val settings = SettingsManager(this)
            val latStr = settings.latitude
            val lonStr = settings.longitude

            if (latStr != null && lonStr != null) {
                try {
                    val lat = latStr.toDouble()
                    val lon = lonStr.toDouble()
                    qiblaDegree = IslamicAstronomy.calculateQibla(lat, lon).toFloat()
                    computeDeclination(lat, lon)

                    val title = getString(R.string.compass_title_qibla)
                    tvDegree?.text = String.format(Locale.getDefault(), "%s %.1f°", title, qiblaDegree)
                } catch (e: Exception) {
                    tvDegree?.text = getString(R.string.compass_invalid_location)
                }
            } else {
                tvDegree?.text = getString(R.string.default_location)
            }

            fetchLatestLocation()

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

            // Prefer rotation vector sensor (fused, more accurate & smoother)
            rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationVectorSensor != null) {
                useRotationVector = true
            } else {
                // Fallback to accelerometer + magnetometer
                accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            }

            if (rotationVectorSensor == null && (accelerometer == null || magnetometer == null)) {
                Toast.makeText(this, getString(R.string.toast_no_compass_sensor), Toast.LENGTH_LONG).show()
                tvDegree?.text = getString(R.string.compass_sensor_unavailable)
            }

        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_compass_layout_error), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLatestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null && !isDestroyed) {
                        qiblaDegree = IslamicAstronomy.calculateQibla(location.latitude, location.longitude).toFloat()
                        computeDeclination(location.latitude, location.longitude)

                        val title = getString(R.string.compass_title_qibla)
                        tvDegree?.text = String.format(java.util.Locale.getDefault(), "%s %.1f°", title, qiblaDegree)

                        val settings = SettingsManager(this@CompassActivity)
                        settings.latitude = location.latitude.toString()
                        settings.longitude = location.longitude.toString()

                        try {
                            val geocoder = android.location.Geocoder(this@CompassActivity, java.util.Locale.getDefault())
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addrs ->
                                    val address = addrs.firstOrNull()
                                    val locName = listOfNotNull(address?.thoroughfare, address?.subLocality, address?.locality, address?.subAdminArea, address?.adminArea, address?.countryName).joinToString(", ").ifEmpty { getString(R.string.location_found) }
                                    settings.locationName = locName
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                                val locName = listOfNotNull(address?.thoroughfare, address?.subLocality, address?.locality, address?.subAdminArea, address?.adminArea, address?.countryName).joinToString(", ").ifEmpty { getString(R.string.location_found) }
                                settings.locationName = locName
                            }
                        } catch (e: Exception) {}

                        val updateIntent = Intent(this, IslamicWidgetProvider::class.java).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, IslamicWidgetProvider::class.java))
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        sendBroadcast(updateIntent)
                    }
                }
        }
    }

    /**
     * Hitung magnetic declination (selisih antara magnetic north dan true north)
     * menggunakan model IGRF/WMM via GeomagneticField.
     * Tanpa koreksi ini, kompas menunjuk ke magnetic north, bukan true north,
     * sehingga arah Qibla bisa meleset beberapa derajat.
     */
    private fun computeDeclination(lat: Double, lon: Double) {
        val geoField = GeomagneticField(
            lat.toFloat(), lon.toFloat(), 0f,
            System.currentTimeMillis()
        )
        magneticDeclination = geoField.declination
    }

    override fun onResume() {
        super.onResume()
        try {
            if (useRotationVector) {
                rotationVectorSensor?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
            } else {
                accelerometer?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
                magnetometer?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
            }
        } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {}
    }

    private fun getShortestDelta(current: Float, target: Float): Float {
        var diff = (target - current) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        try {
            synchronized(this) {
                var azimuth: Float? = null

                if (useRotationVector && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    // Rotation vector sensor: fused by hardware, lebih akurat & stabil
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                } else if (!useRotationVector) {
                    // Fallback: accelerometer + magnetometer manual fusion
                    val alpha = 0.95f

                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) {
                        if (!isGravityInit) {
                            gravity = event.values.copyOf()
                            isGravityInit = true
                        } else {
                            for (idx in 0..2) {
                                gravity[idx] = alpha * gravity[idx] + (1 - alpha) * event.values[idx]
                            }
                        }
                    }

                    if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD && event.values.size >= 3) {
                        if (!isGeomagneticInit) {
                            geomagnetic = event.values.copyOf()
                            isGeomagneticInit = true
                        } else {
                            for (idx in 0..2) {
                                geomagnetic[idx] = alpha * geomagnetic[idx] + (1 - alpha) * event.values[idx]
                            }
                        }
                    }

                    if (isGravityInit && isGeomagneticInit) {
                        val r = FloatArray(9)
                        val i = FloatArray(9)
                        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                            val orientation = FloatArray(3)
                            SensorManager.getOrientation(r, orientation)
                            azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        }
                    }
                }

                if (azimuth != null) {
                    // SELALU koreksi magnetic declination untuk semua tipe sensor.
                    // TYPE_ROTATION_VECTOR TIDAK dijamin mengembalikan true north
                    // di semua perangkat/OEM — banyak implementasi tetap berbasis
                    // magnetic north. Menambah declination di atas true north hanya
                    // menggeser ~0.8° (declination Indonesia kecil), aman dilakukan.
                    azimuth += magneticDeclination
                    if (azimuth < 0f) azimuth += 360f
                    if (azimuth >= 360f) azimuth -= 360f

                    val rawDelta = getShortestDelta(currentAzimuth, azimuth)
                    if (Math.abs(rawDelta) > 0.3f) {
                        currentAzimuth += rawDelta * 0.08f
                    }

                    val displayDegree = (currentAzimuth % 360 + 360) % 360
                    tvCenterHeading?.text = String.format(Locale.getDefault(), "%d°", displayDegree.toInt())

                    val targetDial = -currentAzimuth
                    val targetNeedle = qiblaDegree - currentAzimuth

                    val dialDelta = getShortestDelta(dialRotation, targetDial)
                    dialVelocity += dialDelta * 0.015f
                    dialVelocity *= 0.85f
                    dialRotation += dialVelocity

                    val needleDelta = getShortestDelta(needleRotation, targetNeedle)
                    needleVelocity += needleDelta * 0.01f
                    needleVelocity *= 0.90f
                    needleRotation += needleVelocity

                    flDial?.rotation = dialRotation
                    ivNeedle?.rotation = needleRotation

                    val qiblaDiff = Math.abs(getShortestDelta(displayDegree, qiblaDegree))
                    if (qiblaDiff < 2.0f) {
                        ivStaticNeedle?.setImageResource(R.drawable.ic_green_circle_needle)
                        ivNeedle?.setImageResource(R.drawable.ic_kaaba_indicator_green)
                    } else {
                        ivStaticNeedle?.setImageResource(R.drawable.ic_red_circle_needle)
                        ivNeedle?.setImageResource(R.drawable.ic_kaaba_indicator)
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
