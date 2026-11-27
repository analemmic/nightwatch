package com.analemmic.nightwatch.astro

import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.MoonTimes
import org.shredzone.commons.suncalc.SunTimes
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Today's events (local calendar date) as rows for the tap-to-open table (owner 2026-11-23): sun, twilights, solar
 * noon, moon, tides, equinox/solstice, in time order. Clock times like weather apps (not dial angles).
 */
object DayTable {
    /** Which face element a row belongs to: picks its text color in the day palette (owner 2026-11-24). */
    enum class Kind { ASTRO, NAUTICAL, CIVIL, SUN, NOON, MOON, TIDE, SEASON }

    data class Row(val emoji: String, val label: String, val time: String?, val minute: Int, val kind: Kind)

    /** Pi Day row: March 14 at 01:59 (24-hour, like the face), only where dates read MM.DD. */
    const val PI = "\u03c0"

    /** [feet]: tide heights in feet (US measurement system), else meters. [monthFirst]: the user's date order is MM.DD
     *  (Pi Day only exists there). */
    fun rows(lat: Double, lon: Double, now: ZonedDateTime, tideStation: Tides.Station?, feet: Boolean = false,
             monthFirst: Boolean = true): List<Row> {
        val day = now.truncatedTo(ChronoUnit.DAYS)
        val rows = mutableListOf<Row>()
        val notes = mutableListOf<Row>()

        fun add(emoji: String, label: String, t: ZonedDateTime?, kind: Kind) {
            if (t == null) return
            val r = t.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES) // round to the minute, like the face labels
            if (r.toLocalDate() != day.toLocalDate()) return
            rows += Row(emoji, label, "%02d:%02d".format(r.hour, r.minute), r.hour * 60 + r.minute, kind)
        }

        fun sun(tw: SunTimes.Twilight, riseEmoji: String, rise: String, setEmoji: String, set: String, level: String, kind: Kind) {
            val r = SunTimes.compute().on(day).at(lat, lon).twilight(tw).oneDay().execute()
            add(riseEmoji, rise, r.rise, kind)
            add(setEmoji, set, r.set, kind)
            if (r.rise == null && r.set == null) {
                notes += Row(if (r.isAlwaysUp) "☀️" else "🌑", if (r.isAlwaysUp) "Never below $level" else "Never above $level", null, -1, kind)
            }
        }
        sun(SunTimes.Twilight.ASTRONOMICAL, "🌌", "Astro dawn", "🌌", "Astro dusk", "-18°", Kind.ASTRO)
        sun(SunTimes.Twilight.NAUTICAL, "⚓", "Nautical dawn", "⚓", "Nautical dusk", "-12°", Kind.NAUTICAL)
        sun(SunTimes.Twilight.CIVIL, "🏙️", "Civil dawn", "🏙️", "Civil dusk", "-6°", Kind.CIVIL)
        sun(SunTimes.Twilight.VISUAL, "🌅", "Sunrise", "🌇", "Sunset", "the horizon", Kind.SUN)
        add("☀️", "Solar noon", SunTimes.compute().on(day).at(lat, lon).execute().noon, Kind.NOON)

        val age = Astro.moonAge(now).toDouble()
        val phase = moonPhase(age, lat)
        val m = MoonTimes.compute().on(day).at(lat, lon).oneDay().execute()
        add(phase.first, "Moonrise", m.rise, Kind.MOON)
        add(phase.first, "Moonset", m.set, Kind.MOON)
        if (m.rise == null && m.set == null) notes += Row(phase.first, if (m.isAlwaysUp) "Moon up all day" else "Moon down all day", null, -1, Kind.MOON)

        tideStation?.let { st ->
            Tides.day(st, now)?.let { d ->
                d.minutes.forEachIndexed { i, min ->
                    val high = (i % 2 == 0) == d.firstHigh
                    // Owner 2026-11-25: with the height ("Hi"/"Lo" to make room) above the gauge's chart datum, like
                    // tide tables (TideDatums: MLLW in the US, LAT elsewhere).
                    val at = day.toLocalDate().atStartOfDay().plusMinutes(min.toLong()).atZone(day.zone).toInstant()
                    val h = tideHeight((Tides.height(st, at) + st.datumCm) / 100.0, feet)
                    rows += Row(if (high) "🌊" else "🐚", if (high) "Hi tide $h" else "Lo tide $h",
                        "%02d:%02d".format(min / 60, min % 60), min, Kind.TIDE)
                }
            }
        }

        // The easter eggs, with the same amber symbols as the face (owner 2026-11-27).
        Seasons.today(now)?.let { (event, t) -> add(event.sign, seasonName(event), t, Kind.SEASON) }
        if (monthFirst && day.monthValue == 3 && day.dayOfMonth == 14) add(PI, "Pi Day", day.withHour(1).withMinute(59), Kind.SEASON)

        rows.sortBy { it.minute }
        // Owner 2026-11-23: the phase goes on the moon's transit ("lunar noon", highest in the sky); on the ~1 day a
        // month with no transit, it stays as an untimed note.
        val transit = moonTransit(lat, lon, day)
        if (transit != null) {
            val tAge = Astro.moonAge(transit).toDouble()
            val tPhase = moonPhase(tAge, lat)
            val alt = MoonPosition.compute().on(transit).at(lat, lon).execute().altitude
            add(tPhase.first, tPhase.second + if (alt < 0) " ↓" else "", transit, Kind.MOON) // ↓: transit below the horizon (polar)
        } else {
            notes += Row(phase.first, phase.second, null, -1, Kind.MOON)
        }
        return rows + notes
    }

    /** Signed height, one decimal: +1.2′ (feet) or −0.6m. */
    fun tideHeight(meters: Double, feet: Boolean): String {
        val v = Math.round((if (feet) meters / 0.3048 else meters) * 10) / 10.0
        val s = "%.1f".format(java.util.Locale.ROOT, kotlin.math.abs(v))
        return (if (v < 0) "\u2212" else "+") + s + if (feet) "\u2032" else "m"
    }

    fun moonTransit(lat: Double, lon: Double, day: ZonedDateTime): ZonedDateTime? = Astro.moonTransit(lat, lon, day)

    /** Emoji and name for a moon age in days. Emoji shapes are drawn for the northern hemisphere; mirrored south. */
    fun moonPhase(age: Double, lat: Double): Pair<String, String> {
        val i = (((age / Astro.SYNODIC_DAYS) * 8 + 0.5).toInt()) % 8
        val north = listOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘")
        val names = listOf("New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon",
            "Waning gibbous", "Last quarter", "Waning crescent")
        val emoji = if (lat >= 0) north[i] else north[(8 - i) % 8]
        return emoji to names[i]
    }

    private fun seasonName(e: Seasons.Event) = when (e) {
        Seasons.Event.MARCH_EQUINOX -> "March equinox"
        Seasons.Event.JUNE_SOLSTICE -> "June solstice"
        Seasons.Event.SEPTEMBER_EQUINOX -> "September equinox"
        Seasons.Event.DECEMBER_SOLSTICE -> "December solstice"
    }

}
