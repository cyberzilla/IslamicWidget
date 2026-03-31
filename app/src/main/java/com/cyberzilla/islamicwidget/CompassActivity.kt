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
    private var ivStaticNeedle: ImageView? = null
    private var tvDegree: TextView? = null
    private var tvCenterHeading: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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

            val settings = SettingsManager(this)
            val latStr = settings.latitude
            val lonStr = settings.longitude

            if (latStr != null && lonStr != null) {
                try {
                    val coordinates = Coordinates(latStr.toDouble(), lonStr.toDouble())
                    qiblaDegree = Qibla(coordinates).direction.toFloat()
                    val title = try { getString(R.string.compass_title_qibla) } catch (e: Exception) { "Kiblat:" }
                    tvDegree?.text = String.format(Locale.getDefault(), "%s %.1f°", title, qiblaDegree)
                } catch (e: Exception) {
                    tvDegree?.text = "Lokasi tidak valid"
                }
            }

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        } catch (e: Exception) {
            Toast.makeText(this, "Error memuat UI Kompas", Toast.LENGTH_SHORT).show()
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
                // Kalkulasi Accelerometer
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

                // Kalkulasi Magnetometer
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

                        // 1. Dapatkan azimuth dan normalisasi awal
                        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (azimuth < 0) azimuth += 360f

                        // 2. Filter penstabil gerakan (Anti-jitter)
                        val rawDelta = getShortestDelta(currentAzimuth, azimuth)
                        if (Math.abs(rawDelta) > 0.8f) {
                            currentAzimuth += rawDelta * 0.04f
                        }

                        // 3. Tampilkan derajat secara real-time di tengah layar (selalu 0-360)
                        val displayDegree = (currentAzimuth % 360 + 360) % 360
                        tvCenterHeading?.text = String.format(Locale.getDefault(), "%d°", displayDegree.toInt())

                        // 4. Hitung target rotasi untuk dial (piringan) dan needle (jarum Ka'bah)
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

                        // 5. Eksekusi putaran animasi
                        flDial?.rotation = dialRotation
                        ivNeedle?.rotation = needleRotation

                        // 6. Cek kesesuaian arah HP dengan arah Kiblat (Toleransi 2.0 derajat)
                        val qiblaDiff = Math.abs(getShortestDelta(displayDegree, qiblaDegree))
                        if (qiblaDiff < 2.0f) {
                            // Berubah warna menjadi Hijau jika pas
                            ivStaticNeedle?.setImageResource(R.drawable.ic_green_circle_needle)
                            ivNeedle?.setImageResource(R.drawable.ic_kaaba_indicator_green)
                        } else {
                            // Kembali ke warna default (Merah & Abu-abu) jika tidak pas
                            ivStaticNeedle?.setImageResource(R.drawable.ic_red_circle_needle)
                            ivNeedle?.setImageResource(R.drawable.ic_kaaba_indicator)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Mengabaikan error sesaat jika sensor memberikan data yang tidak valid
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}