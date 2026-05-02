package com.cyberzilla.islamicwidget.utils

import android.content.Context
import android.graphics.*
import com.cyberzilla.islamicwidget.R
import io.github.cosinekitty.astronomy.*
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

/**
 * Renders a beautiful, realistic moon phase bitmap using a real moon texture PNG
 * with programmatic shadow masking for phase visualization.
 * Uses real astronomical data from astronomy.kt for accurate phase calculation.
 *
 * Technique: The full moon PNG (drawable-nodpi/moon_full_texture.png) is drawn
 * clipped to a circle, then the terminator shadow is composited on top using
 * Path-based masking to visualize the current lunar phase.
 */
object MoonPhaseRenderer {

    // Shadow color for the dark side of the moon
    private val COLOR_SHADOW_DARK = Color.parseColor("#1A1A2E")

    // Cached source texture to avoid repeated resource decoding
    private var cachedTexture: WeakReference<Bitmap>? = null

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
     * Loads the moon texture bitmap from resources, with WeakReference caching.
     * Returns null if the resource is not found.
     */
    private fun loadMoonTexture(context: Context): Bitmap? {
        cachedTexture?.get()?.let { if (!it.isRecycled) return it }

        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.moon_full_texture, options)
            cachedTexture = WeakReference(bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Renders a detailed moon phase bitmap using real texture + shadow masking.
     * @param context  Android context for loading texture resource
     * @param sizePx   Size of the output bitmap in pixels (square)
     * @param bgColor  Background color (transparent by default)
     * @param latitude Observer latitude (null = no rotation, Northern Hemisphere default)
     * @param longitude Observer longitude (null = no rotation)
     */
    fun renderMoonPhase(
        context: Context,
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
        // Terminator sudah benar (waxing=bright RIGHT), parallactic angle positif
        // merotasi bright dari RIGHT ke UPPER-LEFT untuk pengamat di Indonesia
        if (latitude != null && longitude != null) {
            val parallacticAngle = calculateParallacticAngle(latitude, longitude)
            canvas.save()
            canvas.rotate(parallacticAngle.toFloat(), sizePx / 2f, sizePx / 2f)
            drawMoon(context, canvas, sizePx, phaseData)
            canvas.restore()
        } else {
            drawMoon(context, canvas, sizePx, phaseData)
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

        // Hour angle H = LST - RA (dalam derajat)
        val lst = siderealTime(t) + longitude / 15.0 // Local sidereal time in hours
        val hourAngle = (lst - moonEq.ra) * 15.0 // Convert to degrees
        val hRad = hourAngle * PI / 180.0
        val latRad = latitude * PI / 180.0
        val decRad = moonEq.dec * PI / 180.0

        // Parallactic angle: q = atan2(sin(H), tan(φ)·cos(δ) − sin(δ)·cos(H))
        // Formula ini valid untuk semua altitude bulan, termasuk di bawah horizon.
        // Tidak perlu fallback — hasilnya tetap benar secara matematis.
        val q = atan2(
            sin(hRad),
            tan(latRad) * cos(decRad) - sin(decRad) * cos(hRad)
        )

        return q * 180.0 / PI // Return in degrees
    }

    /**
     * Main drawing function that composes all moon layers.
     */
    private fun drawMoon(context: Context, canvas: Canvas, size: Int, data: MoonPhaseData) {
        val cx = size / 2f
        val cy = size / 2f
        val radius = size * 0.46f
        val glowRadius = size * 0.49f

        // 1. Draw outer glow
        drawGlow(canvas, cx, cy, radius, glowRadius, data.illuminationFraction)

        // 2. Draw moon surface from real PNG texture
        drawMoonFromTexture(context, canvas, cx, cy, radius)

        // 3. Draw terminator shadow (the key phase visualization)
        drawTerminator(canvas, cx, cy, radius, data)

        // 4. Draw lunar eclipse shadow (if active)
        drawLunarEclipse(canvas, cx, cy, radius)

        // 5. Draw edge highlight (limb darkening)
        drawLimbDarkening(canvas, cx, cy, radius)
    }

    /**
     * Draws the moon surface using the real moon texture PNG.
     * The texture is clipped to a circle and scaled to fit the moon radius.
     * Falls back to a simple gradient circle if texture is unavailable.
     */
    private fun drawMoonFromTexture(context: Context, canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val texture = loadMoonTexture(context)

        if (texture == null) {
            // Fallback: draw a simple moon base without texture
            drawFallbackMoonBase(canvas, cx, cy, radius)
            return
        }

        // Clip to circle for clean anti-aliased edges
        canvas.save()
        val circlePath = Path()
        circlePath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(circlePath)

        // Draw texture scaled to fill the moon circle
        val destRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(texture, null, destRect, paint)

        canvas.restore()
    }

    /**
     * Fallback moon base when texture PNG is not available.
     * Draws a simple gradient circle resembling the moon.
     */
    private fun drawFallbackMoonBase(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - radius * 0.15f, cy - radius * 0.15f, radius * 1.1f,
                intArrayOf(
                    Color.parseColor("#F5F0E8"),
                    Color.parseColor("#D9D0C0"),
                    Color.parseColor("#C4BAA8")
                ),
                floatArrayOf(0f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, basePaint)
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

        // Skip shadow for near-full moon. Use illumination fraction as the
        // primary guard — this is more reliable than phase angle thresholds.
        // At >98% illumination (phase ~165°-195°), the shadow sliver is
        // sub-pixel and Path.Op.DIFFERENCE can produce artifacts due to
        // floating-point instability when bright/dark arcs nearly overlap.
        if (data.illuminationFraction > 0.98) return

        val cosPhase = cos(phaseAngle * PI / 180.0).toFloat()
        val isWaxing = phaseAngle < 180.0
        val moonRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // === Build shadow path DIRECTLY (not via DIFFERENCE) ===
        // This avoids Path.Op.DIFFERENCE numerical instability entirely.
        //
        // Shadow is on LEFT for waxing, RIGHT for waning.
        // The shadow region = one semicircle of the moon + one semicircle of
        // the terminator ellipse (bowing inward or outward depending on phase).
        val shadowPath = Path()

        if (isWaxing) {
            // Waxing: shadow on LEFT
            val terminatorXRadius = radius * abs(cosPhase)
            val terminatorRect = RectF(cx - terminatorXRadius, cy - radius, cx + terminatorXRadius, cy + radius)

            // LEFT semicircle of moon (top→left→bottom)
            shadowPath.addArc(moonRect, 270f, -180f)

            if (phaseAngle < 90.0) {
                // Waxing crescent: terminator bows RIGHT (toward bright)
                // Shadow = left of moon + right of terminator
                shadowPath.arcTo(terminatorRect, 90f, 180f, false)
            } else {
                // Waxing gibbous: terminator bows LEFT (toward dark)
                // Shadow = left of moon + left of terminator (inverted)
                shadowPath.arcTo(terminatorRect, 90f, -180f, false)
            }
            shadowPath.close()
        } else {
            // Waning: shadow on RIGHT
            val waneAngle = 360.0 - phaseAngle
            val cosWane = cos(waneAngle * PI / 180.0).toFloat()
            val terminatorXRadius = radius * abs(cosWane)
            val terminatorRect = RectF(cx - terminatorXRadius, cy - radius, cx + terminatorXRadius, cy + radius)

            // RIGHT semicircle of moon (top→right→bottom)
            shadowPath.addArc(moonRect, 270f, 180f)

            if (phaseAngle > 270.0) {
                // Waning crescent: terminator bows LEFT (toward bright)
                // Shadow = right of moon + left of terminator
                shadowPath.arcTo(terminatorRect, 90f, -180f, false)
            } else {
                // Waning gibbous: terminator bows RIGHT (toward dark)
                // Shadow = right of moon + right of terminator (inverted)
                shadowPath.arcTo(terminatorRect, 90f, 180f, false)
            }
            shadowPath.close()
        }

        // Clip to moon circle — all blur is contained within the moon boundary
        canvas.save()
        val moonCirclePath = Path()
        moonCirclePath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(moonCirclePath)

        // Layer 1: Wide soft penumbra (extends furthest into the lit area)
        val penumbraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 26, 26, 46)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(radius * 0.15f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(shadowPath, penumbraPaint)

        // Layer 2: Medium transition shadow
        val midShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 26, 26, 46)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(radius * 0.08f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(shadowPath, midShadowPaint)

        // Layer 3: Core shadow (semi-transparent, slightly blurred for smoothness)
        val coreShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 26, 26, 46)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(radius * 0.03f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(shadowPath, coreShadowPaint)
        canvas.restore()
    }

    // ==========================================================================
    // LUNAR ECLIPSE VISUALIZATION
    // ==========================================================================

    /**
     * State data for a lunar eclipse currently in progress.
     */
    private data class LunarEclipseState(
        val kind: EclipseKind,
        val progress: Float,           // 0.0 = partial start, 0.5 = peak, 1.0 = partial end
        val inTotalPhase: Boolean,
        val inPartialPhase: Boolean,
        val inPenumbralOnly: Boolean,
        val penumbralProgress: Float,  // progress within penumbral-only phase
        val obscuration: Double        // peak obscuration fraction
    )

    /**
     * Detects if a lunar eclipse is currently in progress by searching recent
     * eclipse data and checking if the current time falls within any phase.
     */
    private fun detectLunarEclipse(): LunarEclipseState? {
        return try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val now = Time(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND).toDouble()
            )

            // Search from 12 hours ago to catch any ongoing eclipse
            val eclipse = searchLunarEclipse(now.addDays(-0.5))

            val peakUt = eclipse.peak.ut
            val nowUt = now.ut

            // Semi-durations in minutes → convert to days
            val sdPenumDays = eclipse.sdPenum / 1440.0
            val sdPartialDays = eclipse.sdPartial / 1440.0
            val sdTotalDays = eclipse.sdTotal / 1440.0

            // Phase time boundaries
            val penStart = peakUt - sdPenumDays
            val penEnd = peakUt + sdPenumDays
            val partStart = peakUt - sdPartialDays
            val partEnd = peakUt + sdPartialDays
            val totStart = peakUt - sdTotalDays
            val totEnd = peakUt + sdTotalDays

            // Are we inside the eclipse window?
            if (nowUt < penStart || nowUt > penEnd) return null

            val inTotal = sdTotalDays > 0 && nowUt >= totStart && nowUt <= totEnd
            val inPartial = sdPartialDays > 0 && nowUt >= partStart && nowUt <= partEnd
            val inPenOnly = !inPartial

            // Progress within partial phase (0→1): most visually significant
            val partProgress = if (inPartial && sdPartialDays > 0) {
                ((nowUt - partStart) / (partEnd - partStart)).toFloat().coerceIn(0f, 1f)
            } else 0f

            val penProgress = if (inPenOnly && sdPenumDays > 0) {
                ((nowUt - penStart) / (penEnd - penStart)).toFloat().coerceIn(0f, 1f)
            } else 0f

            LunarEclipseState(
                kind = eclipse.kind,
                progress = partProgress,
                inTotalPhase = inTotal,
                inPartialPhase = inPartial,
                inPenumbralOnly = inPenOnly,
                penumbralProgress = penProgress,
                obscuration = eclipse.obscuration
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders the lunar eclipse shadow on the moon if an eclipse is currently active.
     *
     * - Penumbral: subtle uniform darkening
     * - Partial: Earth's umbral shadow (dark reddish circle) moves across the moon
     * - Total: full blood-moon tint (deep copper/red)
     */
    private fun drawLunarEclipse(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val eclipse = detectLunarEclipse() ?: return

        // Safety: skip eclipse rendering if obscuration is negligible
        // This prevents false-positive darkening on regular full moons
        if (eclipse.obscuration < 0.01) return

        canvas.save()
        val moonClip = Path()
        moonClip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(moonClip)

        // --- Penumbral-only phase: subtle darkening ---
        if (eclipse.inPenumbralOnly) {
            // Intensity peaks at center of penumbral phase
            val intensity = sin(eclipse.penumbralProgress * PI).toFloat()
            val alpha = (35 * intensity).toInt().coerceIn(0, 35)
            val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 30, 15, 10)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, penPaint)
            canvas.restore()
            return
        }

        // --- Partial / Total phase: Earth's umbral shadow ---
        val shadowRadius = radius * 1.35f  // Earth umbra ≈ 1.35× moon radius

        // Shadow moves from right→left across the moon
        // progress: 0.0 = entering from right, 0.5 = peak, 1.0 = leaving to left
        val maxOffset = shadowRadius + radius
        val peakOffset = if (eclipse.kind == EclipseKind.Total) {
            0f  // Shadow fully covers moon at peak
        } else {
            // Partial: shadow center doesn't reach moon center
            (shadowRadius - radius * eclipse.obscuration.toFloat() * 1.2f).coerceAtLeast(0f)
        }

        val t = eclipse.progress
        val offsetX = if (t <= 0.5f) {
            lerp(maxOffset, peakOffset, t / 0.5f)
        } else {
            lerp(peakOffset, -maxOffset, (t - 0.5f) / 0.5f)
        }

        val shadowCx = cx + offsetX
        val shadowCy = cy

        // Draw umbral shadow with gradient for soft realistic edges
        val umbraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                shadowCx, shadowCy, shadowRadius,
                intArrayOf(
                    Color.argb(235, 20, 8, 5),     // Near-black center
                    Color.argb(200, 35, 12, 8),    // Very dark red
                    Color.argb(140, 65, 22, 12),   // Reddish-brown
                    Color.argb(60, 85, 30, 18),    // Soft edge
                    Color.argb(0, 100, 40, 25)     // Transparent
                ),
                floatArrayOf(0f, 0.55f, 0.78f, 0.92f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(shadowCx, shadowCy, shadowRadius, umbraPaint)

        // Blood moon tint during total phase
        if (eclipse.inTotalPhase) {
            val bloodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(
                        Color.argb(90, 180, 50, 15),   // Deep copper center
                        Color.argb(70, 200, 65, 25),   // Copper-orange
                        Color.argb(50, 150, 35, 10)    // Dark red edge
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, bloodPaint)
        }

        canvas.restore()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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
