package com.analemmic.nightwatch.astro

import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import org.shredzone.commons.suncalc.MoonTimes
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sentinel the face treats as "absent" (polar day/night, no moon event found, or no location yet). */
const val ABSENT = -999f

/**
 * A lit span on the dial: the sun is above a twilight level (or the moon is above the horizon) from [start] clockwise
 * to [end], with start <= end <= start + 360. [startReal]/[endReal] say whether that edge is a real event (draw a line /
 * tick there) or just the window boundary (noon, for a one-sided day) or nothing (a full ring).
 *   normal        (dawn, dusk unwrapped)   real, real
 *   always above  (-180, 180) full ring    -, -
 *   always below  NONE (ABSENT)            -, -
 *   only dusk     (0 = noon, dusk)         -, real    sun above at the window start, sets, doesn't come back by noon
 *   only dawn     (dawn, 360 = noon)       real, -
 */
data class Band(val start: Float, val end: Float, val startReal: Boolean, val endReal: Boolean) {
    companion object {
        val NONE = Band(ABSENT, ABSENT, false, false)
        val FULL = Band(-180f, 180f, false, false)
    }
}

data class Snapshot(
    val sun: Band,       // true sunrise -> sunset (VISUAL: upper limb + refraction, as weather apps)
    val civil: Band,     // civil dawn -> dusk
    val nautical: Band,
    val astro: Band,
    val moon: Band,      // moonrise -> moonset, as marker angles at those instants
    val season: Float = ABSENT, // dial angle of today's equinox/solstice (local date), else ABSENT
    val solarNoon: Float = ABSENT, // dial angle of today's solar noon (sun highest), local date
    val moonAge: Float = ABSENT, // days since new moon, [0, 29.53): picks the face's phase sprite
    val tides: Tides.Day? = null, // today's high/low waters at the nearest gauge within 50 km, else null
    val moonTransit: Float = ABSENT, // dial angle of today's moon transit ("lunar noon"): the face's moon marker
)

/** All dial angles are clock positions: an event at local clock time T sits at 15 * (T - 12) (noon at the top). */
object Astro {

    fun compute(lat: Double, lon: Double, now: ZonedDateTime, tideStation: Tides.Station? = null): Snapshot {
        // The night being shown: window starts at the most recent local noon, so after noon it's
        // tonight's dusk + tomorrow's dawn, and in the morning it's last night's dusk + today's dawn.
        val noonToday = now.truncatedTo(ChronoUnit.DAYS).withHour(12)
        val windowStart = if (now.isBefore(noonToday)) noonToday.minusDays(1) else noonToday

        fun twilight(t: SunTimes.Twilight): Band {
            val r = SunTimes.compute().on(windowStart).at(lat, lon).twilight(t).oneDay().execute()
            return band(r.rise?.let { clockAngle(it) }, r.set?.let { clockAngle(it) }, r.isAlwaysUp, r.isAlwaysDown)
        }

        return Snapshot(
            sun = twilight(SunTimes.Twilight.VISUAL),
            civil = twilight(SunTimes.Twilight.CIVIL),
            nautical = twilight(SunTimes.Twilight.NAUTICAL),
            astro = twilight(SunTimes.Twilight.ASTRONOMICAL),
            moon = moonBand(lat, lon, now),
            season = Seasons.today(now)?.let { clockAngle(it.second) } ?: ABSENT,
            solarNoon = SunTimes.compute().on(now.truncatedTo(ChronoUnit.DAYS)).at(lat, lon).execute()
                .noon?.let { clockAngle(it) } ?: ABSENT,
            moonAge = moonAge(now),
            tides = tideStation?.let { Tides.day(it, now) },
            moonTransit = transitAngle(lat, lon, now),
        )
    }

    /** Lit span from the rise/set found in a noon-to-noon window (see [Band]). Angles in [-180, 180). */
    fun band(rise: Float?, set: Float?, alwaysUp: Boolean, alwaysDown: Boolean): Band = when {
        rise != null && set != null -> Band(rise, if (set < rise) set + 360f else set, true, true)
        rise == null && set == null && alwaysUp -> Band.FULL
        rise == null && set == null -> Band.NONE // always down (or nothing found)
        set != null -> Band(0f, sinceNoon(set!!), false, true)
        else -> Band(sinceNoon(rise!!), 360f, true, false)
    }.also { if (alwaysDown && it != Band.NONE) error("always down but $it") }

    /** Dial angle measured clockwise from the window start (noon at top), in (0, 360]. */
    private fun sinceNoon(angle: Float): Float = ((angle % 360f) + 360f) % 360f

    /** Dial angle of a local clock time, rounded to the whole minute (labels show HHMM). */
    fun clockAngle(t: ZonedDateTime): Float {
        val minutes = (t.hour * 60 + t.minute + t.second / 60.0).roundToInt() % 1440
        return 15f * (minutes / 60f - 12f)
    }

    /**
     * Moonrise/moonset for today's local date. If the moon never sets (or never rises) today: full ring (or nothing).
     * Owner rule: if today has a rise but no set (or vice versa), use the PREVIOUS day's missing event, searching back
     * up to 3 days but never across a day the moon was always up or always down. If still missing, only the found
     * event's tick is drawn (zero-length arc). Owner 2026-11-23: ticks sit at the events' CLOCK positions (like the
     * sun lines, matching weather apps) and the heavy arc rise -> set (clockwise, wrapping) is when the moon is up.
     */
    private fun moonBand(lat: Double, lon: Double, now: ZonedDateTime): Band {
        val today = now.truncatedTo(ChronoUnit.DAYS)
        val r0 = MoonTimes.compute().on(today).at(lat, lon).oneDay().execute()
        if (r0.rise == null && r0.set == null) return if (r0.isAlwaysUp) Band.FULL else Band.NONE
        var rise: ZonedDateTime? = r0.rise
        var set: ZonedDateTime? = r0.set
        for (back in 1..3) {
            if (rise != null && set != null) break
            val r = MoonTimes.compute().on(today.minusDays(back.toLong())).at(lat, lon).oneDay().execute()
            if (r.isAlwaysUp || r.isAlwaysDown) break
            if (rise == null) rise = r.rise
            if (set == null) set = r.set
        }
        val a = rise?.let { clockAngle(it) }
        val b = set?.let { clockAngle(it) }
        return when {
            a != null && b != null -> Band(a, if (b < a) b + 360f else b, true, true)
            a != null -> Band(a, a, true, false)
            else -> Band(b!!, b, false, true)
        }
    }

    const val SYNODIC_DAYS = 29.530589

    /** Days since new moon. MoonIllumination phase: -180 new, -90 first quarter, 0 full, +90 last quarter. */
    fun moonAge(t: ZonedDateTime): Float {
        val phase = MoonIllumination.compute().on(t).execute().phase
        return (((phase + 180.0) / 360.0 * SYNODIC_DAYS) % SYNODIC_DAYS).toFloat()
    }

    /**
     * The moon's upper transit on [day]'s local date: its hour angle crosses 0 going west, i.e. it's highest in the
     * sky. Null on the ~1 day a month with no transit.
     */
    fun moonTransit(lat: Double, lon: Double, day: ZonedDateTime): ZonedDateTime? =
        transitsBetween(lat, lon, day.truncatedTo(ChronoUnit.DAYS), day.truncatedTo(ChronoUnit.DAYS).plusDays(1)).firstOrNull()

    /**
     * Upper transits in [from, to): where the moon's hour angle crosses 0 going west. Owner 2026-11-24 (scrubber speed):
     * Newton steps from a guess instead of a 10-minute scan. The hour angle grows ~14.49 deg/h (one lunar day,
     * ~24.84 h, per 360 deg), so guess = now + (degrees to go) / rate, refined to < 1 s; the next transit is guessed one
     * lunar day later. MoonTransitTest checks it against the old 10-minute scan.
     */
    fun transitsBetween(lat: Double, lon: Double, from: ZonedDateTime, to: ZonedDateTime): List<ZonedDateTime> {
        fun ha(t: ZonedDateTime): Double {
            val m = MoonPosition.compute().on(t).at(lat, lon).execute()
            return norm(hourAngle(lat, m.azimuth, m.trueAltitude))
        }
        fun refine(guess: ZonedDateTime): ZonedDateTime {
            var t = guess
            repeat(8) {
                val h = ha(t)
                val step = (-h / HA_RATE * 3600.0).toLong()
                if (step == 0L) return t
                t = t.plusSeconds(step)
            }
            return t
        }
        val out = mutableListOf<ZonedDateTime>()
        val h0 = ha(from)
        var t = refine(from.plusSeconds(((if (h0 < 0) -h0 else 360.0 - h0) / HA_RATE * 3600.0).toLong()))
        while (t.isBefore(to)) {
            if (!t.isBefore(from)) out += t
            t = refine(t.plusSeconds(LUNAR_DAY_S))
        }
        return out
    }

    private const val HA_RATE = 360.0 / 24.8412   // deg per hour, mean
    private const val LUNAR_DAY_S = 89_428L        // 24.8412 h

    /**
     * Dial angle for the face's moon marker (owner 2026-11-23: the marker sits at the moon's transit, "lunar noon").
     * Today's transit; on the ~1 day a month without one, the nearest transit just before or after the date.
     */
    fun transitAngle(lat: Double, lon: Double, now: ZonedDateTime): Float {
        val day = now.truncatedTo(ChronoUnit.DAYS)
        val t = moonTransit(lat, lon, day)
            ?: transitsBetween(lat, lon, day.minusHours(3), day.plusHours(27))
                .minByOrNull { minOf(Duration.between(it, day).abs(), Duration.between(it, day.plusDays(1)).abs()) }
        return t?.let { clockAngle(it) } ?: ABSENT
    }

    /**
     * Hour angle (degrees, positive west of the meridian) from azimuth (0 = north, 90 = east) and altitude.
     * Horizon vector (N, E, Up) rotated into the equatorial frame: cos(dec) cos(H) = u cos(lat) - n sin(lat),
     * cos(dec) sin(H) = -e.
     */
    fun hourAngle(latDeg: Double, azDeg: Double, altDeg: Double): Double {
        val lat = Math.toRadians(latDeg)
        val az = Math.toRadians(azDeg)
        val alt = Math.toRadians(altDeg)
        val n = cos(alt) * cos(az)
        val e = cos(alt) * sin(az)
        val u = sin(alt)
        return Math.toDegrees(atan2(-e, u * cos(lat) - n * sin(lat)))
    }

    /** Normalize degrees to [-180, 180). */
    fun norm(deg: Double): Double = ((deg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}
