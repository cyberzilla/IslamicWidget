package com.cyberzilla.islamicwidget.utils

import android.graphics.*
import io.github.cosinekitty.astronomy.*
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Renders a beautiful, detailed moon phase bitmap using Canvas.
 * Uses real astronomical data from astronomy.kt for accurate phase visualization.
 */
object MoonPhaseRenderer {

    // Moon surface colors
    private val COLOR_MOON_BRIGHT = Color.parseColor("#F5F0E8")
    private val COLOR_MOON_EDGE = Color.parseColor("#D9D0C0")
    private val COLOR_SHADOW_DARK = Color.parseColor("#1A1A2E")
    private val COLOR_CRATER_DARK = Color.parseColor("#60A89E88")   // ~38% opacity, visible dark maria
    private val COLOR_CRATER_LIGHT = Color.parseColor("#30FFFFFF") // ~19% opacity, visible highlights

    // Crater info for detailed texture rendering (must be top-level to avoid Android crash)
    private data class CraterInfo(val dx: Float, val dy: Float, val r: Float)

    /**
     * Data class holding all moon phase information needed for rendering.
     */
    data class MoonPhaseData(
        val phaseAngle: Double,       // 0-360: 0=New, 90=First Quarter, 180=Full, 270=Last Quarter
        val illuminationFraction: Double, // 0.0 to 1.0
        val isWaxing: Boolean         // true if waxing (growing), false if waning
    )

    /**
     * Calculates current moon phase data using astronomy.kt.
     */
    fun getCurrentMoonPhase(): MoonPhaseData {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val t = Time(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND).toDouble()
        )

        val phaseAngle = moonPhase(t)
        val illumInfo = illumination(Body.Moon, t)
        val fraction = illumInfo.phaseFraction

        // Waxing: phase angle 0-180, Waning: 180-360
        val isWaxing = phaseAngle < 180.0

        return MoonPhaseData(
            phaseAngle = phaseAngle,
            illuminationFraction = fraction,
            isWaxing = isWaxing
        )
    }

    /**
     * Renders a detailed moon phase bitmap.
     * @param sizePx   Size of the output bitmap in pixels (square)
     * @param bgColor  Background color (transparent by default)
     * @param latitude Observer latitude (null = no rotation, Northern Hemisphere default)
     * @param longitude Observer longitude (null = no rotation)
     */
    fun renderMoonPhase(
        sizePx: Int,
        bgColor: Int = Color.TRANSPARENT,
        latitude: Double? = null,
        longitude: Double? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        val phaseData = getCurrentMoonPhase()

        // Rotate canvas by parallactic angle so the moon appears as seen from user's location
        if (latitude != null && longitude != null) {
            val parallacticAngle = calculateParallacticAngle(latitude, longitude)
            canvas.save()
            canvas.rotate(parallacticAngle.toFloat(), sizePx / 2f, sizePx / 2f)
            drawMoon(canvas, sizePx, phaseData)
            canvas.restore()
        } else {
            drawMoon(canvas, sizePx, phaseData)
        }

        return bitmap
    }

    /**
     * Menghitung parallactic angle — sudut rotasi bulan sebagaimana terlihat
     * dari lokasi pengamat. Efek:
     * - Belahan Bumi Utara: ~0° (sabit vertikal, standar buku teks)
     * - Equator (Indonesia): ~±90° (sabit miring/horizontal, "perahu")
     * - Belahan Bumi Selatan: ~180° (sabit terbalik)
     *
     * Formula: q = atan2(sin(H), tan(φ)·cos(δ) − sin(δ)·cos(H))
     * di mana H = hour angle, φ = latitude, δ = declination bulan
     */
    private fun calculateParallacticAngle(latitude: Double, longitude: Double): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val t = Time(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND).toDouble()
        )

        val observer = Observer(latitude, longitude, 0.0)
        val moonEq = equator(Body.Moon, t, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val moonHor = horizon(t, observer, moonEq.ra, moonEq.dec, Refraction.Normal)

        // Jika bulan di bawah horizon, gunakan posisi transit terakhir sebagai approximasi
        if (moonHor.altitude < -5.0) {
            // Fallback: gunakan latitude-based approximation
            // Di equator ~90°, di kutub ~0°, di selatan ~180°
            return (90.0 - latitude) * 0.7
        }

        // Hour angle H = LST - RA (dalam derajat)
        val lst = siderealTime(t) + longitude / 15.0 // Local sidereal time in hours
        val hourAngle = (lst - moonEq.ra) * 15.0 // Convert to degrees
        val hRad = hourAngle * PI / 180.0
        val latRad = latitude * PI / 180.0
        val decRad = moonEq.dec * PI / 180.0

        val q = atan2(
            sin(hRad),
            tan(latRad) * cos(decRad) - sin(decRad) * cos(hRad)
        )

        return q * 180.0 / PI // Return in degrees
    }

    /**
     * Main drawing function that composes all moon layers.
     */
    private fun drawMoon(canvas: Canvas, size: Int, data: MoonPhaseData) {
        val cx = size / 2f
        val cy = size / 2f
        val radius = size * 0.46f
        val glowRadius = size * 0.49f

        // 1. Draw outer glow
        drawGlow(canvas, cx, cy, radius, glowRadius, data.illuminationFraction)

        // 2. Draw moon base (full bright circle)
        drawMoonBase(canvas, cx, cy, radius)

        // 3. Draw surface craters/maria texture
        drawMoonTexture(canvas, cx, cy, radius)

        // 4. Draw terminator shadow (the key phase visualization)
        drawTerminator(canvas, cx, cy, radius, data)

        // 5. Draw edge highlight (limb darkening)
        drawLimbDarkening(canvas, cx, cy, radius)
    }

    /**
     * Draws the soft glow around the illuminated moon.
     */
    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, moonR: Float, glowR: Float, illumination: Double) {
        if (illumination < 0.02) return

        val alpha = (illumination * 80).toInt().coerceIn(0, 80)
        val innerColor = Color.argb(alpha, 255, 253, 231)
        val outerColor = Color.argb(0, 255, 253, 231)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, glowR, innerColor, outerColor, Shader.TileMode.CLAMP)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, glowR, glowPaint)
    }

    /**
     * Draws the base moon circle with a subtle radial gradient for realism.
     */
    private fun drawMoonBase(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * 0.15f, cy - radius * 0.15f, radius * 1.1f,
                intArrayOf(COLOR_MOON_BRIGHT, COLOR_MOON_EDGE, Color.parseColor("#C4BAA8")),
                floatArrayOf(0f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, basePaint)
    }

    /**
     * Draws realistic crater/maria textures on the moon surface.
     * Includes major maria, detailed craters with depth shadows/highlights, and Tycho rays.
     */
    private fun drawMoonTexture(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        // Major maria (dark patches) - positioned to roughly match real moon
        val maria = listOf(
            Triple(-0.20f, -0.30f, 0.18f),  // Mare Imbrium
            Triple(0.10f, -0.25f, 0.12f),    // Mare Serenitatis
            Triple(0.18f, -0.05f, 0.14f),    // Mare Tranquillitatis
            Triple(0.35f, -0.20f, 0.08f),    // Mare Crisium
            Triple(-0.10f, 0.25f, 0.12f),    // Mare Nubium
            Triple(-0.32f, 0.0f, 0.20f),     // Oceanus Procellarum
            Triple(0.28f, 0.12f, 0.10f),     // Mare Fecunditatis
            Triple(-0.28f, 0.22f, 0.07f)     // Mare Humorum
        )

        for ((dx, dy, r) in maria) {
            val mariaX = cx + dx * radius
            val mariaY = cy + dy * radius
            val mariaR = r * radius

            // Core dark fill — strong center, gradual fade
            craterPaint.shader = RadialGradient(
                mariaX, mariaY, mariaR,
                intArrayOf(
                    COLOR_CRATER_DARK,
                    Color.argb(70, 150, 140, 120),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(mariaX, mariaY, mariaR, craterPaint)

            // Subtle inner shadow for depth
            val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    mariaX - mariaR * 0.15f, mariaY - mariaR * 0.15f, mariaR * 0.7f,
                    intArrayOf(
                        Color.argb(40, 80, 70, 50),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
            canvas.drawCircle(mariaX, mariaY, mariaR * 0.7f, innerShadowPaint)
        }

        // Detailed craters with shadow/highlight for depth effect
        // Each crater: dx, dy, radius, has shadow on upper-left and highlight on lower-right

        val detailedCraters = listOf(
            CraterInfo(0.0f, 0.35f, 0.06f),     // Tycho
            CraterInfo(-0.12f, -0.10f, 0.045f),  // Copernicus
            CraterInfo(0.10f, 0.15f, 0.032f),    // Kepler
            CraterInfo(-0.05f, 0.10f, 0.025f),   // Small crater
            CraterInfo(0.25f, -0.35f, 0.035f),   // Proclus
            CraterInfo(-0.35f, -0.15f, 0.03f),   // Aristarchus
            CraterInfo(0.05f, -0.40f, 0.028f),   // Plato region
            CraterInfo(-0.22f, 0.12f, 0.022f),   // Near Oceanus
            CraterInfo(0.30f, -0.05f, 0.025f),   // Near Tranquillitatis
            CraterInfo(-0.15f, 0.38f, 0.02f),    // Southern crater
            CraterInfo(0.20f, 0.28f, 0.018f),    // SE crater
            CraterInfo(-0.38f, -0.30f, 0.022f),  // NW crater
            CraterInfo(0.15f, -0.15f, 0.02f),    // Central-N crater
            CraterInfo(-0.08f, -0.38f, 0.025f),  // Plato
            CraterInfo(0.38f, 0.05f, 0.018f),    // Eastern limb crater
        )

        val shadowOffset = radius * 0.012f // Light source offset for depth

        for (crater in detailedCraters) {
            val craterX = cx + crater.dx * radius
            val craterY = cy + crater.dy * radius
            val craterR = crater.r * radius

            // Crater floor (dark center) — much more visible
            val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    craterX, craterY, craterR,
                    intArrayOf(
                        Color.argb(90, 110, 100, 80),
                        Color.argb(60, 140, 130, 110),
                        Color.argb(20, 160, 150, 130),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
            canvas.drawCircle(craterX, craterY, craterR, floorPaint)

            // Crater rim shadow (upper-left, darker) — strongly visible
            val rimShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.012f
                color = Color.argb(80, 60, 50, 35)
            }
            val shadowArc = RectF(
                craterX - craterR - shadowOffset,
                craterY - craterR - shadowOffset,
                craterX + craterR - shadowOffset,
                craterY + craterR - shadowOffset
            )
            canvas.drawArc(shadowArc, 200f, 160f, false, rimShadowPaint)

            // Crater rim highlight (lower-right, brighter) — clearly visible
            val rimHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.010f
                color = Color.argb(75, 255, 255, 240)
            }
            val highlightArc = RectF(
                craterX - craterR + shadowOffset,
                craterY - craterR + shadowOffset,
                craterX + craterR + shadowOffset,
                craterY + craterR + shadowOffset
            )
            canvas.drawArc(highlightArc, 20f, 160f, false, rimHighlightPaint)
        }

        // Bright crater ring outlines for the most prominent craters
        val prominentRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.008f
            color = Color.argb(65, 255, 255, 255)
        }
        for (crater in detailedCraters.take(6)) {
            canvas.drawCircle(
                cx + crater.dx * radius,
                cy + crater.dy * radius,
                crater.r * radius,
                prominentRingPaint
            )
        }

        // Tycho rays (bright ejecta lines) — clearly visible
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 255, 255, 245)
            strokeWidth = radius * 0.018f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val tychoX = cx
        val tychoY = cy + 0.35f * radius
        for (angle in listOf(-30f, -60f, -100f, 15f, 50f, 80f, 120f, -140f, 160f)) {
            val radAngle = angle * PI.toFloat() / 180f
            val endX = tychoX + cos(radAngle) * radius * 0.55f
            val endY = tychoY - sin(radAngle) * radius * 0.55f
            canvas.drawLine(tychoX, tychoY, endX, endY, rayPaint)
        }
        // Outer glow around rays
        val rayGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(18, 255, 255, 235)
            strokeWidth = radius * 0.035f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(radius * 0.02f, BlurMaskFilter.Blur.NORMAL)
        }
        for (angle in listOf(-30f, -60f, -100f, 15f, 50f, 80f, 120f, -140f, 160f)) {
            val radAngle = angle * PI.toFloat() / 180f
            val endX = tychoX + cos(radAngle) * radius * 0.50f
            val endY = tychoY - sin(radAngle) * radius * 0.50f
            canvas.drawLine(tychoX, tychoY, endX, endY, rayGlowPaint)
        }

        // Copernicus rays (shorter) — visible
        val copRayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(30, 255, 255, 240)
            strokeWidth = radius * 0.012f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val copX = cx - 0.12f * radius
        val copY = cy - 0.10f * radius
        for (angle in listOf(0f, 60f, 120f, 180f, 240f, 300f)) {
            val radAngle = angle * PI.toFloat() / 180f
            val endX = copX + cos(radAngle) * radius * 0.22f
            val endY = copY - sin(radAngle) * radius * 0.22f
            canvas.drawLine(copX, copY, endX, endY, copRayPaint)
        }
    }

    /**
     * Draws the terminator (shadow boundary) to visualize the current phase.
     *
     * Phase angle convention (from moonPhase()):
     *   0° = New Moon, 90° = First Quarter, 180° = Full Moon, 270° = Last Quarter
     *
     * Waxing (0°-180°): shadow on LEFT side, illuminated on RIGHT
     * Waning (180°-360°): shadow on RIGHT side, illuminated on LEFT
     *
     * The terminator is an ellipse whose x-radius = |cos(phaseAngle)| * moonRadius.
     * For crescent phases the terminator bows toward the bright limb;
     * for gibbous phases it bows toward the dark limb.
     */
    private fun drawTerminator(canvas: Canvas, cx: Float, cy: Float, radius: Float, data: MoonPhaseData) {
        val phaseAngle = data.phaseAngle

        if (phaseAngle < 1.0 || phaseAngle > 359.0) {
            // New moon - entire surface in shadow
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_SHADOW_DARK
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, shadowPaint)
            val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.02f
                color = Color.argb(40, 200, 200, 200)
            }
            canvas.drawCircle(cx, cy, radius, edgePaint)
            return
        }

        if (abs(phaseAngle - 180.0) < 1.0) return // Full moon

        val cosPhase = cos(phaseAngle * PI / 180.0).toFloat()
        val isWaxing = phaseAngle < 180.0
        val moonRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val shadowPath = Path()

        if (isWaxing) {
            val terminatorXRadius = radius * abs(cosPhase)
            // Bright area boundary: left semicircle + terminator curve
            shadowPath.addArc(moonRect, 90f, 180f)
            val terminatorRect = RectF(cx - terminatorXRadius, cy - radius, cx + terminatorXRadius, cy + radius)
            if (phaseAngle < 90.0) {
                // Waxing crescent
                shadowPath.arcTo(terminatorRect, 270f, -180f, false)
            } else {
                // Waxing gibbous
                shadowPath.arcTo(terminatorRect, 270f, 180f, false)
            }
            shadowPath.close()
        } else {
            val waneAngle = 360.0 - phaseAngle
            val cosWane = cos(waneAngle * PI / 180.0).toFloat()
            val terminatorXRadius = radius * abs(cosWane)
            // Bright area boundary: right semicircle + terminator curve
            shadowPath.addArc(moonRect, 270f, 180f)
            val terminatorRect = RectF(cx - terminatorXRadius, cy - radius, cx + terminatorXRadius, cy + radius)
            if (phaseAngle > 270.0) {
                // Waning crescent
                shadowPath.arcTo(terminatorRect, 90f, 180f, false)
            } else {
                // Waning gibbous
                shadowPath.arcTo(terminatorRect, 90f, -180f, false)
            }
            shadowPath.close()
        }

        // The path above defines the BRIGHT area.
        // Compute shadow = moon circle MINUS bright area.
        val moonCirclePath = Path()
        moonCirclePath.addCircle(cx, cy, radius, Path.Direction.CW)

        val actualShadowPath = Path()
        actualShadowPath.op(moonCirclePath, shadowPath, Path.Op.DIFFERENCE)

        // Clip to moon circle
        canvas.save()
        canvas.clipPath(moonCirclePath)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SHADOW_DARK
            style = Paint.Style.FILL
        }
        canvas.drawPath(actualShadowPath, shadowPaint)

        // Soft terminator edge along the boundary
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 26, 26, 46)
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.06f
            maskFilter = BlurMaskFilter(radius * 0.04f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(actualShadowPath, blurPaint)

        canvas.restore()
    }

    /**
     * Draws limb darkening effect.
     */
    private fun drawLimbDarkening(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(35, 0, 0, 0)),
                floatArrayOf(0f, 0.82f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, limbPaint)

        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.012f
            color = Color.argb(20, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, radius * 0.99f, edgePaint)
    }

    /**
     * Returns a human-readable name for the current moon phase.
     */
    fun getPhaseName(phaseAngle: Double): String {
        return when {
            phaseAngle < 11.25 || phaseAngle >= 348.75 -> "New Moon"
            phaseAngle < 78.75 -> "Waxing Crescent"
            phaseAngle < 101.25 -> "First Quarter"
            phaseAngle < 168.75 -> "Waxing Gibbous"
            phaseAngle < 191.25 -> "Full Moon"
            phaseAngle < 258.75 -> "Waning Gibbous"
            phaseAngle < 281.25 -> "Last Quarter"
            else -> "Waning Crescent"
        }
    }
}
