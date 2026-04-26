package com.cyberzilla.islamicwidget.utils

import android.graphics.*
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Renders a beautiful, detailed moon phase bitmap using Canvas.
 * Uses real astronomical data from astronomy.kt for accurate phase visualization.
 */
object MoonPhaseRenderer {

    // Moon surface colors
    private val COLOR_MOON_BRIGHT = Color.parseColor("#F5F0E8")
    private val COLOR_MOON_EDGE = Color.parseColor("#D9D0C0")
    private val COLOR_SHADOW_DARK = Color.parseColor("#1A1A2E")
    private val COLOR_CRATER_DARK = Color.parseColor("#1AC8BFA8")
    private val COLOR_CRATER_LIGHT = Color.parseColor("#0DFFFFFF")

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
     * @param sizePx  Size of the output bitmap in pixels (square)
     * @param bgColor Background color (transparent by default)
     */
    fun renderMoonPhase(sizePx: Int, bgColor: Int = Color.TRANSPARENT): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        val phaseData = getCurrentMoonPhase()
        drawMoon(canvas, sizePx, phaseData)

        return bitmap
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

            craterPaint.shader = RadialGradient(
                mariaX, mariaY, mariaR,
                intArrayOf(COLOR_CRATER_DARK, Color.TRANSPARENT),
                floatArrayOf(0.3f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(mariaX, mariaY, mariaR, craterPaint)
        }

        // Small craters (bright rings)
        val craters = listOf(
            Triple(0.0f, 0.35f, 0.06f),    // Tycho
            Triple(-0.12f, -0.10f, 0.04f),  // Copernicus
            Triple(0.10f, 0.15f, 0.03f),
            Triple(-0.05f, 0.10f, 0.025f),
            Triple(0.25f, -0.35f, 0.03f),
        )

        val craterRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.008f
            color = COLOR_CRATER_LIGHT
        }

        for ((dx, dy, r) in craters) {
            canvas.drawCircle(cx + dx * radius, cy + dy * radius, r * radius, craterRingPaint)
        }

        // Tycho rays
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(8, 255, 255, 255)
            strokeWidth = radius * 0.015f
            style = Paint.Style.STROKE
        }
        val tychoX = cx
        val tychoY = cy + 0.35f * radius
        for (angle in listOf(-30f, -60f, -100f, 15f, 50f, 80f, 120f)) {
            val radAngle = angle * PI.toFloat() / 180f
            val endX = tychoX + cos(radAngle) * radius * 0.5f
            val endY = tychoY - kotlin.math.sin(radAngle) * radius * 0.5f
            canvas.drawLine(tychoX, tychoY, endX, endY, rayPaint)
        }
    }

    /**
     * Draws the terminator (shadow boundary) to visualize the current phase.
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
            shadowPath.addArc(moonRect, 90f, 180f)
            val terminatorRect = RectF(cx - terminatorXRadius, cy - radius, cx + terminatorXRadius, cy + radius)
            if (phaseAngle < 90.0) {
                shadowPath.arcTo(terminatorRect, 270f, -180f, false)
            } else {
                shadowPath.arcTo(terminatorRect, 270f, 180f, false)
            }
            shadowPath.close()
        } else {
            val waneAngle = 360.0 - phaseAngle
            val cosWane = cos(waneAngle * PI / 180.0).toFloat()
            val terminatorXRadius = radius * abs(cosWane)
            shadowPath.addArc(moonRect, 270f, 180f)
            val terminatorRect = RectF(cx - terminatorXRadius, cy - radius, cx + terminatorXRadius, cy + radius)
            if (phaseAngle > 270.0) {
                shadowPath.arcTo(terminatorRect, 90f, 180f, false)
            } else {
                shadowPath.arcTo(terminatorRect, 90f, -180f, false)
            }
            shadowPath.close()
        }

        // Clip to moon circle
        canvas.save()
        val clipPath = Path()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SHADOW_DARK
            style = Paint.Style.FILL
        }
        canvas.drawPath(shadowPath, shadowPaint)

        // Soft terminator edge
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 26, 26, 46)
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.06f
            maskFilter = BlurMaskFilter(radius * 0.04f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(shadowPath, blurPaint)

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
