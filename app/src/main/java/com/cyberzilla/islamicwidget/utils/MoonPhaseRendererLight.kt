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
import kotlin.math.sin
import kotlin.math.tan

/**
 * Renders a beautiful, realistic moon phase bitmap using a real moon texture PNG
 * with programmatic ALPHA MASKING (PorterDuff DST_OUT) for phase visualization.
 * Uses real astronomical data from astronomy.kt for accurate phase calculation.
 *
 * Technique: The full moon PNG is drawn inside an isolated layer. Instead of
 * stacking a dark shadow on top, the unlit portion of the moon is MATHEMATICALLY
 * ERASED (made transparent) using DST_OUT. This allows the widget background
 * or Android homescreen wallpaper to show through the dark side of the moon.
 */
object MoonPhaseRendererLight {

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
     * Renders a detailed moon phase bitmap using real texture + alpha masking.
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

        val lst = siderealTime(t) + longitude / 15.0
        val hourAngle = (lst - moonEq.ra) * 15.0
        val hRad = hourAngle * PI / 180.0
        val latRad = latitude * PI / 180.0
        val decRad = moonEq.dec * PI / 180.0

        val q = atan2(
            sin(hRad),
            tan(latRad) * cos(decRad) - sin(decRad) * cos(hRad)
        )

        return q * 180.0 / PI
    }

    /**
     * Main drawing function that composes all moon layers using Alpha Masking.
     */
    private fun drawMoon(context: Context, canvas: Canvas, size: Int, data: MoonPhaseData) {
        val cx = size / 2f
        val cy = size / 2f
        val radius = size * 0.46f
        val glowRadius = size * 0.49f

        // 1. Draw outer glow (Behind everything, so it remains solid)
        drawGlow(canvas, cx, cy, radius, glowRadius, data.illuminationFraction)

        // 2. ISOLATE LAYER: Crucial for PorterDuff.Mode.DST_OUT to only erase the moon, not the glow/background
        val layerRect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.saveLayer(layerRect, null)

        // 3. Draw Base Full Moon Texture
        drawMoonFromTexture(context, canvas, cx, cy, radius)

        // 4. Draw lunar eclipse shadow (Tints the base moon before erasure)
        drawLunarEclipse(canvas, cx, cy, radius)

        // 5. Draw edge highlight (limb darkening)
        drawLimbDarkening(canvas, cx, cy, radius)

        // 6. ERASER MASK: Mathematically punch a hole to make the dark side transparent
        eraseDarkSide(canvas, cx, cy, radius, data)

        // Commit isolated layer back to main canvas
        canvas.restore()
    }

    private fun drawMoonFromTexture(context: Context, canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val texture = loadMoonTexture(context)

        canvas.save()
        val circlePath = Path()
        circlePath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(circlePath)

        if (texture != null) {
            val destRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(texture, null, destRect, paint)
        } else {
            drawFallbackMoonBase(canvas, cx, cy, radius)
        }
        canvas.restore()
    }

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
     * Acts as a digital eraser. Uses PorterDuff.Mode.DST_OUT to fade and completely
     * remove pixels from the dark side of the moon, revealing absolute transparency.
     */
    private fun eraseDarkSide(canvas: Canvas, cx: Float, cy: Float, radius: Float, data: MoonPhaseData) {
        val phaseAngle = data.phaseAngle

        // Setup the digital Eraser brush
        val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            style = Paint.Style.FILL
            color = Color.BLACK // Color doesn't matter for DST_OUT, only alpha matters
        }

        if (phaseAngle < 1.0 || phaseAngle > 359.0) {
            // New moon - Erase everything. We add +2f to ensure subpixel edges are annihilated.
            canvas.drawCircle(cx, cy, radius + 2f, eraserPaint)
            return
        }

        if (data.illuminationFraction > 0.98) return // Full moon, nothing to erase

        val cosPhase = cos(phaseAngle * PI / 180.0).toFloat()
        val isWaxing = phaseAngle < 180.0
        val moonRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Build a giant D-shape path covering the dark side
        val eraserPath = Path()
        val terminatorXRadius = radius * abs(cosPhase)
        val terminatorRect = RectF(cx - terminatorXRadius, cy - radius, cx + terminatorXRadius, cy + radius)

        if (isWaxing) {
            // Waxing: Erase LEFT side
            eraserPath.moveTo(cx, cy - radius)
            if (phaseAngle < 90.0) {
                // Crescent: Terminator bows right
                eraserPath.arcTo(terminatorRect, 270f, 180f, false)
            } else {
                // Gibbous: Terminator bows left
                eraserPath.arcTo(terminatorRect, 270f, -180f, false)
            }
            // Draw huge bounding box off-screen to the left to hold the blur
            eraserPath.lineTo(cx - radius * 2, cy + radius)
            eraserPath.lineTo(cx - radius * 2, cy - radius)
            eraserPath.close()
        } else {
            // Waning: Erase RIGHT side
            val waneAngle = 360.0 - phaseAngle
            eraserPath.moveTo(cx, cy - radius)
            if (phaseAngle > 270.0) {
                // Crescent: Terminator bows left
                eraserPath.arcTo(terminatorRect, 270f, -180f, false)
            } else {
                // Gibbous: Terminator bows right
                eraserPath.arcTo(terminatorRect, 270f, 180f, false)
            }
            // Draw huge bounding box off-screen to the right to hold the blur
            eraserPath.lineTo(cx + radius * 2, cy + radius)
            eraserPath.lineTo(cx + radius * 2, cy - radius)
            eraserPath.close()
        }

        canvas.save()
        // Clip to the moon so our eraser doesn't accidentally erase anything outside the moon bounds
        val moonCirclePath = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        canvas.clipPath(moonCirclePath)

        // Eraser Layer 1: Soft wide fade (Penumbra).
        // DIPERKECIL radius blurnya dari 0.15f ke 0.06f agar lebih tajam dan tidak terlalu memudar ke area terang.
        eraserPaint.maskFilter = BlurMaskFilter(radius * 0.06f, BlurMaskFilter.Blur.NORMAL)
        eraserPaint.alpha = 180
        canvas.drawPath(eraserPath, eraserPaint)

        // Eraser Layer 2: Core solid eraser.
        // DIPERKECIL radius blurnya dari 0.05f ke 0.02f agar inti potongan tegas.
        eraserPaint.maskFilter = BlurMaskFilter(radius * 0.02f, BlurMaskFilter.Blur.NORMAL)
        eraserPaint.alpha = 255
        canvas.drawPath(eraserPath, eraserPaint)

        canvas.restore() // Remove the moon clip

        // =====================================================================
        // THE PERFECT SEALANT (Layer 4)
        // Draw an unclipped DST_OUT stroke exactly on the outer dark perimeter.
        // =====================================================================
        val solidEraser = Color.BLACK // Erases completely
        val transEraser = Color.TRANSPARENT // Doesn't erase at all

        val colors: IntArray
        val positions: FloatArray

        if (isWaxing) {
            // Seal the Left edge.
            // Posisi diperketat sedikit (0.24 & 0.26) mengimbangi ketajaman blur yang baru
            colors = intArrayOf(transEraser, transEraser, solidEraser, solidEraser, transEraser, transEraser)
            positions = floatArrayOf(0f, 0.24f, 0.26f, 0.74f, 0.76f, 1f)
        } else {
            // Seal the Right edge.
            colors = intArrayOf(solidEraser, solidEraser, transEraser, transEraser, solidEraser, solidEraser)
            positions = floatArrayOf(0f, 0.24f, 0.26f, 0.74f, 0.76f, 1f)
        }

        val edgeEraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            style = Paint.Style.STROKE
            strokeWidth = 6f
            shader = SweepGradient(cx, cy, colors, positions)
        }

        // Draw the sealant arc directly over the edge to eat away the bleeding texture
        if (isWaxing) {
            canvas.drawArc(moonRect, 90f, 180f, false, edgeEraserPaint)
        } else {
            canvas.drawArc(moonRect, 270f, 180f, false, edgeEraserPaint)
        }
    }

    private fun drawLunarEclipse(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val eclipse = detectLunarEclipse() ?: return
        if (eclipse.obscuration < 0.01) return

        canvas.save()
        val moonClip = Path()
        moonClip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(moonClip)

        if (eclipse.inPenumbralOnly) {
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

        val shadowRadius = radius * 1.35f
        val maxOffset = shadowRadius + radius
        val peakOffset = if (eclipse.kind == EclipseKind.Total) {
            0f
        } else {
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

        val umbraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                shadowCx, shadowCy, shadowRadius,
                intArrayOf(
                    Color.argb(235, 20, 8, 5),
                    Color.argb(200, 35, 12, 8),
                    Color.argb(140, 65, 22, 12),
                    Color.argb(60, 85, 30, 18),
                    Color.argb(0, 100, 40, 25)
                ),
                floatArrayOf(0f, 0.55f, 0.78f, 0.92f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(shadowCx, shadowCy, shadowRadius, umbraPaint)

        if (eclipse.inTotalPhase) {
            val bloodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(
                        Color.argb(90, 180, 50, 15),
                        Color.argb(70, 200, 65, 25),
                        Color.argb(50, 150, 35, 10)
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

    private data class LunarEclipseState(
        val kind: EclipseKind,
        val progress: Float,
        val inTotalPhase: Boolean,
        val inPartialPhase: Boolean,
        val inPenumbralOnly: Boolean,
        val penumbralProgress: Float,
        val obscuration: Double
    )

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

            val eclipse = searchLunarEclipse(now.addDays(-0.5))

            val peakUt = eclipse.peak.ut
            val nowUt = now.ut

            val sdPenumDays = eclipse.sdPenum / 1440.0
            val sdPartialDays = eclipse.sdPartial / 1440.0
            val sdTotalDays = eclipse.sdTotal / 1440.0

            val penStart = peakUt - sdPenumDays
            val penEnd = peakUt + sdPenumDays
            val partStart = peakUt - sdPartialDays
            val partEnd = peakUt + sdPartialDays
            val totStart = peakUt - sdTotalDays
            val totEnd = peakUt + sdTotalDays

            if (nowUt < penStart || nowUt > penEnd) return null

            val inTotal = sdTotalDays > 0 && nowUt >= totStart && nowUt <= totEnd
            val inPartial = sdPartialDays > 0 && nowUt >= partStart && nowUt <= partEnd
            val inPenOnly = !inPartial

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

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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