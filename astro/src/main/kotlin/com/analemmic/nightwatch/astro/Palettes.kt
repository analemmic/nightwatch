package com.analemmic.nightwatch.astro

import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/** Day / night palette choice (owner 2026-11-24), shared by the scrubber and the day table. Mirrors gen_face is_day. */
object Palettes {
    /** Dial angle of a local clock time (noon at top, clockwise). */
    fun angle(t: ZonedDateTime): Float = 15f * (t.hour + t.minute / 60f - 12f)

    /** True while [angle] lies between the snapshot's true sunrise and sunset (polar day: always; polar night: never). */
    fun isDay(s: Snapshot, angle: Float): Boolean {
        val b = s.sun
        if (b.start <= -500f || b.end <= -500f) return false
        return (((angle - b.start) % 360f) + 360f) % 360f <= b.end - b.start
    }

    fun of(s: Snapshot, t: ZonedDateTime): Palette = if (isDay(s, angle(t))) FaceSpec.DAY else FaceSpec.NIGHT

    /** The face editor's palette setting (owner 2026-11-25). The companion can't read the face's settings, so the
     *  face's centre tap opens the scrubber through one activity alias per option; we remember the last one heard,
     *  for opens from the launcher (stale until the next tap after a change, which is fine). */
    enum class Mode { AUTO, DAY, NIGHT }

    fun mode(context: Context, launch: Intent?): Mode {
        val prefs = context.getSharedPreferences("palette", Context.MODE_PRIVATE)
        val heard = when (launch?.component?.className?.substringAfterLast('.')) {
            "ScrubberAuto" -> Mode.AUTO
            "ScrubberDay" -> Mode.DAY
            "ScrubberNight" -> Mode.NIGHT
            else -> null
        }
        if (heard != null) prefs.edit().putString("mode", heard.name).apply()
        return heard ?: prefs.getString("mode", null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.AUTO
    }

    fun isDay(mode: Mode, s: Snapshot, t: ZonedDateTime): Boolean = when (mode) {
        Mode.DAY -> true
        Mode.NIGHT -> false
        Mode.AUTO -> isDay(s, angle(t))
    }
}
