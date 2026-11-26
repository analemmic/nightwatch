package com.analemmic.nightwatch.astro

import org.shredzone.commons.suncalc.MoonPosition
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Reference implementations and helpers used only by tests: the slow, obvious versions the fast code is checked against.

/** Which band edges are real events: civil bits 0/1, nautical 2/3, astro 4/5, moon 6/7 (handy for assertions). */
val Snapshot.flags: Int
    get() = listOf(civil, nautical, astro, moon).withIndex().sumOf { (k, b) ->
        (if (b.startReal) 1 shl (2 * k) else 0) + (if (b.endReal) 1 shl (2 * k + 1) else 0)
    }

/** Reference: every minute of the day (the original method; ~30x slower). Tests check [day] against it. */
fun Tides.dayEveryMinute(st: Tides.Station, day: ZonedDateTime): Tides.Day? {
    val start = day.truncatedTo(ChronoUnit.DAYS)
    val minutes = Duration.between(start, start.plusDays(1)).toMinutes().toInt()
    val hs = DoubleArray(minutes + 2) { height(st, start.plusMinutes(it - 1L).toInstant()) }
    val out = mutableListOf<Pair<Int, Boolean>>()
    for (i in 1..minutes) {
        val high = hs[i] > hs[i - 1] && hs[i] >= hs[i + 1]
        val low = hs[i] < hs[i - 1] && hs[i] <= hs[i + 1]
        if (high || low) {
            val local = start.plusMinutes(i - 1L)
            out += (local.hour * 60 + local.minute) to high
        }
    }
    if (out.isEmpty()) return null
    val kept = out.take(MAX_EVENTS)
    return Tides.Day(kept.map { it.first }, kept.first().second)
}

/** The original 10-minute scan + bisection (reference for MoonTransitTest). */
fun Astro.transitsBetweenScan(lat: Double, lon: Double, from: ZonedDateTime, to: ZonedDateTime): List<ZonedDateTime> {
    fun ha(t: ZonedDateTime): Double {
        val m = MoonPosition.compute().on(t).at(lat, lon).execute()
        return Astro.hourAngle(lat, m.azimuth, m.trueAltitude)
    }
    val out = mutableListOf<ZonedDateTime>()
    val end = to
    var a = from
    var haA = ha(a)
    while (a.isBefore(end)) {
        val b = a.plusMinutes(10)
        val haB = ha(b)
        if (haA < 0 && haB >= 0 && haB - haA < 90) { // upward zero crossing (not the +-180 wrap)
            var lo = a; var hi = b
            while (Duration.between(lo, hi).seconds > 1) {
                val mid = lo.plusSeconds(Duration.between(lo, hi).seconds / 2)
                if (ha(mid) < 0) lo = mid else hi = mid
            }
            if (lo.isBefore(end)) out += lo
        }
        a = b; haA = haB
    }
    return out
}
