package com.analemmic.nightwatch.astro

import android.content.Context
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where and when to compute the sky for: the debug simulation if set, else the real location and now. */
data class Where(val lat: Double, val lon: Double, val now: ZonedDateTime, val station: Tides.Station?, val source: String) {
    fun snapshot(at: ZonedDateTime = now): Snapshot = Astro.compute(lat, lon, at, station)
}

object Sky {
    /** The location fix, else the time zone's city (TzCentres); null only when neither is known (zone UTC, no fix).
     *  Never waits on GPS except on first run. */
    suspend fun where(context: Context): Where? {
        val sim = Simulation.get(context)
        if (sim != null) return Where(sim.lat, sim.lon, sim.at, TideDb.nearest(context, sim.lat, sim.lon), "SIMULATED")
        val zone = ZoneId.of(android.icu.util.TimeZone.getCanonicalID(ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id)
        val loc = TzCentres.choose(TzCentres.load(context), LocationSource.get(context), zone) ?: return null
        return Where(loc.lat, loc.lon, ZonedDateTime.now(), TideDb.nearest(context, loc.lat, loc.lon), loc.source)
    }
}

/** Debug: pretend to be somewhere/somewhen (set via RefreshReceiver extras; the face's hands keep real time). */
object Simulation {
    data class Sim(val lat: Double, val lon: Double, val at: ZonedDateTime)
    private const val PREFS = "simulation"

    fun get(context: Context): Sim? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
        val at = p.getString("at", null)?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() } ?: ZonedDateTime.now()
        return Sim(lat, lon, at)
    }

    fun set(context: Context, lat: String, lon: String, at: String?) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("lat", lat).putString("lon", lon).putString("at", at).apply()

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

/** The bundled TICON-4 gauges (res/raw/tide_stations.bin), loaded once. */
object TideDb {
    @Volatile private var stations: List<Tides.Station>? = null
    fun nearest(context: Context, lat: Double, lon: Double): Tides.Station? {
        val all = stations ?: run {
            val db = context.resources.openRawResource(R.raw.tide_stations).use { it.readBytes() }
            val depths = context.resources.openRawResource(R.raw.tide_datums).use { TideDatums.parse(it, db) }
            TideDatums.attach(Tides.parse(db.inputStream()), depths)
        }.also { stations = it }
        return Tides.nearest(all, lat, lon)
    }
}

/**
 * Fixed sky for the watch-face picker's thumbnail: New York, 2026-10-06 07:40, an October morning with a classic
 * waning crescent high in the sky and no equinox/solstice. AT's time must match PREVIEW_TIME in gen_face.py.
 */
object PreviewSky {
    const val LAT = 40.71
    const val LON = -74.01
    val AT: ZonedDateTime = ZonedDateTime.parse("2026-10-06T07:40:00-04:00[America/New_York]")
    @Volatile private var snap: Snapshot? = null
    fun snapshot(context: Context): Snapshot =
        snap ?: Astro.compute(LAT, LON, AT, TideDb.nearest(context, LAT, LON)).also { snap = it }
}
