package com.cyberzilla.islamicwidget

import android.content.Context
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
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla
import java.util.Locale

class CompassActivity : AppCompatActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

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
    private var tvDegree: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        try {
            setContentView(R.layout.activity_compass)

            flDial = findViewById(R.id.fl_compass_dial)
            ivNeedle = findViewById(R.id.iv_qibla_needle)
            tvDegree = findViewById(R.id.tv_compass_degree)

            findViewById<ImageView>(R.id.btn_close_compass)?.setOnClickListener { finish() }

            val settings = SettingsManager(this)
            val latStr = settings.latitude
            val lonStr = settings.longitude

            val titleQibla = try { getString(R.string.compass_title_qibla) } catch (e: Exception) { "Kiblat:" }

            if (latStr != null && lonStr != null) {
                try {
                    val coordinates = Coordinates(latStr.toDouble(), lonStr.toDouble())
                    qiblaDegree = Qibla(coordinates).direction.toFloat()
                    tvDegree?.text = String.format(Locale.getDefault(), "%s %.1f°", titleQibla, qiblaDegree)
                } catch (e: Exception) {
                    tvDegree?.text = try { getString(R.string.compass_invalid_location) } catch (e: Exception) { "Lokasi tidak valid" }
                }
            } else {
                tvDegree?.text = try { getString(R.string.default_location) } catch (e: Exception) { "Lokasi belum diatur" }
            }

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            if (accelerometer == null || magnetometer == null) {
                val errorToast = try { getString(R.string.toast_no_compass_sensor) } catch(e: Exception) { "Sensor kompas tidak ditemukan!" }
                val errorText = try { getString(R.string.compass_sensor_unavailable) } catch(e: Exception) { "Sensor kompas tidak tersedia" }
                Toast.makeText(this, errorToast, Toast.LENGTH_LONG).show()
                tvDegree?.text = errorText
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error XML: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            magnetometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
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
                        gravity[0] = event.values[0]; gravity[1] = event.values[1]; gravity[2] = event.values[2]
                        isGravityInit = true
                    } else {
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                    }
                }

                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD && event.values.size >= 3) {
                    if (!isGeomagneticInit) {
                        geomagnetic[0] = event.values[0]; geomagnetic[1] = event.values[1]; geomagnetic[2] = event.values[2]
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
                    }
                }
            }
        } catch (e: Exception) {}
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}