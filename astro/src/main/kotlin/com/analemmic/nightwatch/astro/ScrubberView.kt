package com.analemmic.nightwatch.astro

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.analemmic.nightwatch.astro.FaceSpec.C
import com.analemmic.nightwatch.astro.FaceSpec.R as RF
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The day scrubber's face (owner 2026-11-24): the watch face redrawn in Kotlin for any date, from a [Snapshot].
 * Mirrors face/tools/gen_face.py element by element (geometry and colors come from the generated [FaceSpec]).
 * Differences from the real face, by design: no hands; the center shows the date (white by day, gray at night; stacked like HH over MM,
 * month/day order as the face's date) and the bottom shows the year. Interactive mode only (no ambient); day or night
 * palette by today's sky right now, whatever date is shown.
 */
class ScrubberView(context: Context) : View(context) {
    var snapshot: Snapshot? = null
        set(v) { field = v; invalidate() }
    var date: LocalDate = LocalDate.now()
        set(v) { field = v; invalidate() }

    /** Day or night palette. Owner 2026-11-24: decided by TODAY's sunrise/sunset and the time right now (it's about
     *  what your eyes need now), never by the scrubbed date. Set by ScrubberActivity. */
    var day: Boolean = false
        set(v) { field = v; invalidate() }

    private val p: Palette
        get() = if (day) FaceSpec.DAY else FaceSpec.NIGHT

    /** Crown turned by [days] (positive = forward in time). */
    var onScrub: (days: Int) -> Unit = {}

    /** The today notch (owner 2026-11-24): crown input is ignored until this uptime, so today sticks for a moment. */
    private var holdUntil = 0L

    /** Landed on today: a stronger haptic than the per-day tick, and hold there for [TODAY_HOLD_MS]. */
    fun notch() {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        holdUntil = android.os.SystemClock.uptimeMillis() + TODAY_HOLD_MS
        crownAcc = 0f
    }
    var onCenterTap: () -> Unit = {}

    private val font: Typeface = context.resources.getFont(R.font.jetbrains_mono_thin) // all text is thin, like the face
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
    /** The small time labels are ExtraLight (gen_face FONT_LABEL): thin read too faint at that size. */
    private val labelText = Paint(text).apply { typeface = context.resources.getFont(R.font.jetbrains_mono_extralight) }
    private val sprite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val moonSprites = arrayOfNulls<Bitmap>(FaceSpec.MOON_SPRITES.size)
    // Owner 2026-11-24: the user's date order (same source as the face's date), not a locale list.
    private val usOrder = !DateOrder.dayFirst(context)

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        isHapticFeedbackEnabled = true
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    private var crownAcc = 0f
    private val scrollFactor = ViewConfiguration.get(context).scaledVerticalScrollFactor

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            if (event.eventTime < holdUntil) { crownAcc = 0f; return true } // sticking on today
            val axis = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            crownAcc += -axis * scrollFactor
            val days = (crownAcc / CROWN_PX_PER_DAY).toInt()
            if (days != 0) {
                crownAcc -= days * CROWN_PX_PER_DAY
                onScrub(days) // may call notch() (today), which replaces the tick
                if (event.eventTime >= holdUntil) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; downAt = e.eventTime }
            MotionEvent.ACTION_UP -> {
                val tap = hypot(e.x - downX, e.y - downY) < slop && e.eventTime - downAt < 400
                val s = FaceSpec.W / width
                if (tap && hypot(e.x * s - C, e.y * s - C) < FaceSpec.DISC * RF) onCenterTap()
            }
        }
        return true
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / FaceSpec.W, height / FaceSpec.W)
        snapshot?.let { s ->
            skyFills(canvas, s)
            ticks(canvas, s)
            noonLine(canvas, s)
            whiskers(canvas, s)
            season(canvas, s)
        } ?: ticks(canvas, null)
        centerDisc(canvas)
        snapshot?.let { s ->
            moonRing(canvas, s)
            tides(canvas, s)
            timeLabels(canvas, s)
            moonMarker(canvas, s)
        }
        dateText(canvas)
        canvas.restore()
    }

    private fun present(a: Float) = a > -500f // the face hides anything below -500 (ABSENT)

    /** Canvas arc angles start at 3 o'clock; the dial's start at the top (noon). */
    private fun arcRect(r: Float) = RectF(C - r, C - r, C + r, C + r)

    private fun radial(canvas: Canvas, angle: Float, r0: Float, r1: Float, color: Int, width: Float, round: Boolean = false) {
        stroke.color = color; stroke.strokeWidth = width
        stroke.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        canvas.save(); canvas.rotate(angle, C, C)
        canvas.drawLine(C, C - r0, C, C - r1, stroke)
        canvas.restore()
    }

    private fun skyFills(canvas: Canvas, s: Snapshot) {
        val rIn = FaceSpec.DISC * RF
        val rm = (rIn + RF) / 2
        stroke.strokeWidth = RF - rIn; stroke.strokeCap = Paint.Cap.BUTT
        listOf(s.astro, s.nautical, s.civil, s.sun).forEachIndexed { i, b ->
            if (!present(b.start) || !present(b.end)) return@forEachIndexed
            stroke.color = p.fills[i]
            canvas.drawArc(arcRect(rm), b.start - 90f, b.end - b.start, false, stroke)
        }
    }

    private fun ticks(canvas: Canvas, s: Snapshot?) {
        val n = s?.nautical
        for (i in 0 until 24) {
            val t = 15f * i
            val inside = n != null && present(n.start) && present(n.end) &&
                (((t - n.start) % 360f + 360f) % 360f) <= n.end - n.start
            val major = i % 6 == 0
            val color = if (inside) Color.BLACK else if (major) p.tickMajor else p.tickMinor
            if (major) radial(canvas, t, RF, FaceSpec.TICK_MAJOR_R * RF, color, 7f) else radial(canvas, t, RF, FaceSpec.TICK_MINOR_R * RF, color, 4f)
        }
    }

    private fun noonLine(canvas: Canvas, s: Snapshot) {
        if (!present(s.solarNoon)) return
        if (abs(wrap180(s.solarNoon)) < FaceSpec.NOON_BREAK_DEG) { // broken around the curved year (gen_face noon_line)
            radial(canvas, s.solarNoon, FaceSpec.DISC * RF, FaceSpec.NOON_BREAK_IN, p.noon, FaceSpec.NOON_W)
            radial(canvas, s.solarNoon, FaceSpec.NOON_BREAK_OUT, FaceSpec.TICK_MINOR_R * RF, p.noon, FaceSpec.NOON_W)
        } else {
            radial(canvas, s.solarNoon, FaceSpec.DISC * RF, FaceSpec.TICK_MINOR_R * RF, p.noon, FaceSpec.NOON_W)
        }
    }

    /** Sunrise and sunset lines only (twilights show as fills): center (under the disc) to the dial's edge. */
    private fun whiskers(canvas: Canvas, s: Snapshot) {
        for (a in listOfNotNull(sunSet(s), sunRise(s))) radial(canvas, a, 0f, RF, p.whiskers[0], 3f)
    }

    private fun wrap180(x: Float) = (((x % 360f) + 540f) % 360f) - 180f

    private fun sunRise(s: Snapshot) = s.sun.start.takeIf { present(it) && s.sun.startReal }
    private fun sunSet(s: Snapshot) = s.sun.end.takeIf { present(it) && s.sun.endReal }

    private fun season(canvas: Canvas, s: Snapshot) {
        if (present(s.season)) radial(canvas, s.season, 0f, RF, FaceSpec.AMBER, 3f)
    }

    /** True when the equinox/solstice line is close enough to this sun line that their labels would overlap. */
    private fun clash(s: Snapshot, line: Float) = present(s.season) && abs(wrap180(s.season - line)) < FaceSpec.SEASON_CLASH_DEG

    /** Sunrise/sunset (and equinox/solstice) times inside the disc; a sun label too close to the season's yields. */
    private fun timeLabels(canvas: Canvas, s: Snapshot) {
        if (present(s.season)) label(canvas, s.season, FaceSpec.AMBER) // amber like its line
        sunSet(s)?.let { if (!clash(s, it)) label(canvas, it) }
        sunRise(s)?.let { if (!clash(s, it)) label(canvas, it) }
    }

    /** HHMM inside the disc on the line's axis at [angle], reading left-to-right on both halves (gen_face time_label). */
    private fun label(canvas: Canvas, angle: Float, color: Int = p.label) {
        val m = ((angle / 15f + 12f) * 60f).roundToInt()
        val mm = ((m % 1440) + 1440) % 1440
        val cy = C - FaceSpec.LABEL_R
        canvas.save()
        canvas.rotate(angle, C, C)
        canvas.rotate(if (sin(Math.toRadians(angle.toDouble())) < 0) 90f else -90f, C, cy)
        centered(canvas, "%02d%02d".format(mm / 60, mm % 60), C, cy, FaceSpec.LABEL_SIZE, color, labelText)
        canvas.restore()
    }

    /** Text along the rim at 12, centered on radius DATE_R (gen_face curved_text), in the label font. */
    private fun curved(canvas: Canvas, s: String, size: Float, color: Int) {
        labelText.textSize = size; labelText.color = color
        val fm = labelText.fontMetrics
        val r = FaceSpec.DATE_R + (fm.ascent + fm.descent) / 2f // glyphs grow outward from the baseline: center them on DATE_R
        val path = android.graphics.Path().apply { addArc(RectF(C - r, C - r, C + r, C + r), 180f, 180f) }
        canvas.drawTextOnPath(s, path, 0f, 0f, labelText) // CENTER align centers it on the path: at 12
    }

    private fun centered(canvas: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, paint: Paint = text) {
        paint.textSize = size; paint.color = color
        val fm = paint.fontMetrics
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    /** Black disc over the line roots; no rim (the moon's arc alone marks the ring). */
    private fun centerDisc(canvas: Canvas) {
        fill.color = Color.BLACK
        canvas.drawCircle(C, C, FaceSpec.DISC * RF, fill)
    }

    /** When the moon is up: a thin arc from moonrise clockwise to moonset. */
    private fun moonRing(canvas: Canvas, s: Snapshot) {
        val m = s.moon
        if (!present(m.start) || !present(m.end)) return
        stroke.color = p.moon; stroke.strokeWidth = 2f; stroke.strokeCap = Paint.Cap.BUTT
        val end = m.end + if (m.end < m.start) 360f else 0f
        canvas.drawArc(arcRect(FaceSpec.DISC * RF), m.start - 90f, end - m.start, false, stroke)
    }

    /** A dot at each high water (outside the moon ring) and low water (inside). */
    private fun tides(canvas: Canvas, s: Snapshot) {
        val d = s.tides ?: return
        val rd = FaceSpec.DISC * RF
        fill.color = p.tide
        d.minutes.forEachIndexed { i, t ->
            val high = (i % 2 == 0) == d.firstHigh
            canvas.save(); canvas.rotate(t / 4f - 180f, C, C)
            canvas.drawCircle(C, C - rd + if (high) -FaceSpec.TIDE_OFF else FaceSpec.TIDE_OFF, FaceSpec.TIDE_DOT_R, fill)
            canvas.restore()
        }
    }

    private fun moonMarker(canvas: Canvas, s: Snapshot) {
        if (!present(s.moonTransit) || s.moonAge < 0) return
        val day = floor(s.moonAge).toInt().coerceIn(0, FaceSpec.MOON_SPRITES.size - 1)
        // The face's own sprites (gen_face.py copies them into this app).
        val bmp = moonSprites[day] ?: BitmapFactory.decodeResource(resources, FaceSpec.MOON_SPRITES[day]).also { moonSprites[day] = it }
        val rd = FaceSpec.DISC * RF
        val rm = FaceSpec.MOON_R
        canvas.save()
        canvas.rotate(s.moonTransit, C, C)
        canvas.rotate(-s.moonTransit, C, C - rd) // keep the lit side screen-fixed
        // White sprite tinted by the palette (white by day, the muted night gray at night; black stays black).
        sprite.colorFilter = LightingColorFilter(p.moonSprite and 0xFFFFFF, 0)
        canvas.drawBitmap(bmp, null, RectF(C - rm, C - rd - rm, C + rm, C - rd + rm), sprite)
        canvas.restore()
    }

    private fun dateText(canvas: Canvas) {
        val mm = "%02d".format(date.monthValue)
        val dd = "%02d".format(date.dayOfMonth)
        val (top, bottom) = if (usOrder) mm to dd else dd to mm
        // Owner 2026-11-24: white by day; a subtle gray at night (white was too brilliant).
        centered(canvas, top, C, C - FaceSpec.TIME_DY, FaceSpec.TIME_SIZE, p.scrubDate)
        centered(canvas, bottom, C, C + FaceSpec.TIME_DY, FaceSpec.TIME_SIZE, p.scrubDate)
        curved(canvas, date.year.toString(), FaceSpec.DATE_SIZE, p.date)
    }

    private companion object {
        /** Crown travel per day, in scaled scroll pixels. Tune on device. */
        const val CROWN_PX_PER_DAY = 40f
        /** How long today holds against the crown. Tune on device. */
        const val TODAY_HOLD_MS = 450L
    }
}
