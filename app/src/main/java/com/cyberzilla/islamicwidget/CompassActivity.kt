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
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var gravity = FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private var qiblaDegree = 0f

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
                    qiblaDegree = IslamicAstronomy.calculateQibla(latStr.toDouble(), lonStr.toDouble()).toFloat()

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
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            if (accelerometer == null || magnetometer == null) {
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

    override fun onResume() {
        super.onResume()
        try {
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
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
        val alpha = 0.95f

        try {
            synchronized(this) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) {
                    if (!isGravityInit) {
                        gravity[0] = event.values[0]
                        gravity[1] = event.values[1]
                        gravity[2] = event.values[2]
                        isGravityInit = true
                    } else {
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                    }
                }

                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD && event.values.size >= 3) {
                    if (!isGeomagneticInit) {
                        geomagnetic[0] = event.values[0]
                        geomagnetic[1] = event.values[1]
                        geomagnetic[2] = event.values[2]
                        isGeomagneticInit = true
                    } else {
                        geomagnetic[0] = alpha * geomagnetic[0] + (1 - alpha) * event.values[0]
                        geomagnetic[1] = alpha * geomagnetic[1] + (1 - alpha) * event.values[1]
                        geomagnetic[2] = alpha * geomagnetic[2] + (1 - alpha) * event.values[2]
                    }
                }

                if (isGravityInit && isGeomagneticInit) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)

                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (azimuth < 0) azimuth += 360f

                        val rawDelta = getShortestDelta(currentAzimuth, azimuth)
                        if (Math.abs(rawDelta) > 0.8f) {
                            currentAzimuth += rawDelta * 0.04f
                        }

                        val displayDegree = (currentAzimuth % 360 + 360) % 360
                        tvCenterHeading?.text = String.format(Locale.getDefault(), "%d°", displayDegree.toInt())

                        val targetDial = -currentAzimuth
                        val targetNeedle = qiblaDegree - currentAzimuth

                        val dialDelta = getShortestDelta(dialRotation, targetDial)
                        dialVelocity += dialDelta * 0.008f
                        dialVelocity *= 0.88f
                        dialRotation += dialVelocity

                        val needleDelta = getShortestDelta(needleRotation, targetNeedle)
                        needleVelocity += needleDelta * 0.002f
                        needleVelocity *= 0.95f
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
            }
        } catch (e: Exception) {
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
