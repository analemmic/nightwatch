package com.analemmic.nightwatch.astro

import android.app.Activity
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A day's events as a table (owner 2026-11-23). Opened by tapping the centre of the day scrubber, for whichever day it
 * shows ([EXTRA_AT]); a single tap anywhere closes it. Night: everything through a red colour filter (night vision).
 * Day (sunrise -> sunset right now, owner 2026-11-24): native emoji colours, and each row's text in its
 * element's colour on the face (FaceSpec.DAY).
 */
class TableActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val close = View.OnClickListener { finish() }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(34), dp(26), dp(40))
            setOnClickListener(close)
        }
        nightVision(true) // until we know it's daytime
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            setOnClickListener(close)
            addView(column)
        }
        setContentView(scroll)
        scroll.requestFocus() // rotating crown / bezel scrolls
        line("…", 14f)
        scope.launch { load() }
    }

    /** Night: everything (incl. colour emoji) through the face's red. Day (owner 2026-11-24): native colours. */
    private fun nightVision(on: Boolean) {
        if (on) column.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(RED_FILTER) })
        else column.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    /** Day palette (null at night: plain white text under the red filter). */
    private var day: Palette? = null

    /** Owner 2026-11-24: by day each row's text takes its element's colour on the face. */
    private fun color(k: DayTable.Kind): Int {
        val p = day ?: return Color.WHITE
        return when (k) {
            DayTable.Kind.SUN -> p.whiskers[0]
            DayTable.Kind.CIVIL -> p.whiskers[1]
            DayTable.Kind.NAUTICAL -> p.whiskers[2]
            DayTable.Kind.ASTRO -> p.whiskers[3]
            DayTable.Kind.NOON -> p.noon
            DayTable.Kind.MOON -> p.moon
            DayTable.Kind.TIDE -> p.tide
            DayTable.Kind.SEASON -> FaceSpec.AMBER
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Feet for tide heights where the locale measures in US units. */
    private fun usUnits() = android.icu.util.LocaleData.getMeasurementSystem(android.icu.util.ULocale.getDefault()) ==
        android.icu.util.LocaleData.MeasurementSystem.US

    private suspend fun load() {
        val w = withContext(Dispatchers.IO) { Sky.where(this@TableActivity) }
        val now = intent.getStringExtra(EXTRA_AT)?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() }
            ?: w?.now ?: ZonedDateTime.now()
        val rows = w?.let { withContext(Dispatchers.Default) { DayTable.rows(it.lat, it.lon, now, it.station, feet = usUnits()) } }
        // Same day/night rule as the face, at the time shown (now, or the scrubber's day at the current time of day).
        // Palette: right now's day/night (owner 2026-11-24: about your eyes now, not the date shown). From the
        // scrubber it passes its choice; otherwise today's sky at the current time.
        val isDay = if (intent.hasExtra(EXTRA_DAY)) intent.getBooleanExtra(EXTRA_DAY, false)
            else w?.let { withContext(Dispatchers.Default) { Palettes.isDay(it.snapshot(), Palettes.angle(it.now)) } } == true
        day = FaceSpec.DAY.takeIf { isDay }
        nightVision(day == null)
        column.removeAllViews()
        val pattern = if (now.year == ZonedDateTime.now().year) "EEE d MMM" else "EEE d MMM yyyy"
        line(now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())), 15f, bold = true, color = day?.digits)
        if (rows != null) {
            for (r in rows) row(r)
        } else {
            line(getString(R.string.table_no_location), 13f)
        }
        // The setup screen (location, licenses) lives here since the launcher opens the scrubber (owner 2026-11-24).
        column.addView(text("ⓘ " + getString(R.string.table_setup), 12f, color = day?.date).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(14), 0, dp(10))
            isClickable = true
            setOnClickListener { startActivity(android.content.Intent(this@TableActivity, SetupActivity::class.java)) }
        })
    }

    private fun row(r: DayTable.Row) {
        column.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            // Owner 2026-11-23: emoji, time, label.
            addView(text(r.emoji, 14f), LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT))
            val c = color(r.kind)
            addView(text(r.time ?: "", 13.5f, tabular = true, color = c), LinearLayout.LayoutParams(dp(46), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(text(r.label, 12.5f, color = c).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun line(s: String, sp: Float, bold: Boolean = false, color: Int? = null) {
        column.addView(text(s, sp, bold = bold, color = color).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(6))
        })
    }

    private fun text(s: String, sp: Float, bold: Boolean = false, tabular: Boolean = false, color: Int? = null) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color ?: Color.WHITE) // at night white becomes the face's red through the layer filter
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (tabular) fontFeatureSettings = "tnum" // fixed-width digits so the times line up
        isClickable = false
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        /** ZonedDateTime (ISO string) of the day to show; default: now (or the debug simulation). */
        const val EXTRA_AT = "at"
        /** Boolean: use the day palette (the scrubber's current choice). Default: decided from right now. */
        const val EXTRA_DAY = "day"

        // Luminance -> RGB of the face's main red (#c41a10).
        private fun lum(k: Float) = floatArrayOf(0.299f * k, 0.587f * k, 0.114f * k, 0f, 0f)
        private val RED_FILTER = ColorMatrix(lum(196 / 255f) + lum(26 / 255f) + lum(16 / 255f) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
    }
}
