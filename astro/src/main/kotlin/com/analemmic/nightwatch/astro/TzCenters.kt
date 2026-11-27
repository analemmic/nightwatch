package com.analemmic.nightwatch.astro

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Backup location from the time zone (owner 2026-11-25): each IANA zone's principal city (tools/build_tz.py ->
 * res/raw/tz_centres.txt). The watch always knows its zone, so with no fix the face still shows a plausible sky
 * (America/New_York -> New York City) instead of only the time. Tides use it too, accepting a possibly wrong gauge.
 *
 * Also when the saved fix doesn't fit the zone (travelled, no new fix yet): the fix is dropped when the zone city
 * nearest to it keeps a different UTC offset from the watch's zone AND the fix is over [FAR_KM] from that zone's city.
 * The distance guard keeps fixes near a zone border (Vigo's nearest zone city is Lisbon, an hour behind Madrid); a
 * Chicago fix on New York time (1150 km) is treated as stale. Accepted miss: far western China on Beijing time.
 */
object TzCentres {
    data class Centre(val zone: String, val lat: Double, val lon: Double)

    const val FAR_KM = 1000.0

    @Volatile private var all: List<Centre>? = null

    fun load(context: Context): List<Centre> = all ?: context.resources.openRawResource(R.raw.tz_centres)
        .bufferedReader().useLines { parse(it) }.also { all = it }

    fun parse(lines: Sequence<String>): List<Centre> = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.trim().split(' ') }.map { (z, lat, lon) -> Centre(z, lat.toDouble(), lon.toDouble()) }.toList()

    /** The location to use: [fix] unless it is missing or doesn't fit [zone]; null if neither is known. */
    fun choose(centres: List<Centre>, fix: LocationSource.LatLon?, zone: ZoneId, now: Instant = Instant.now()): LocationSource.LatLon? {
        val home = centres.firstOrNull { it.zone == zone.id }
            ?: return fix
        val backup = LocationSource.LatLon(home.lat, home.lon, "timezone ${home.zone}")
        if (fix == null) return backup
        val nearest = centres.minBy { km(fix.lat, fix.lon, it.lat, it.lon) }
        val sameOffset = runCatching { ZoneId.of(nearest.zone).rules.getOffset(now) == zone.rules.getOffset(now) }
            .getOrDefault(true)
        return if (!sameOffset && km(fix.lat, fix.lon, home.lat, home.lon) > FAR_KM) backup else fix
    }

    /** Great-circle distance. */
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(h))
    }
}
