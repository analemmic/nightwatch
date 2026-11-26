package com.analemmic.nightwatch.astro

import java.io.DataInputStream
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline tide prediction (owner 2026-11-23: high/low tide triangles on the moon ring, nearest gauge within 50 km).
 *
 * Data: TICON-4 harmonic constants (Hart-Davis, Dettmering, Seitz 2025, SEANOE doi:10.17882/109129, CC BY 4.0),
 * packed by astro/tools/build_tides.py into res/raw/tide_stations.bin. Prediction is the standard harmonic sum
 * h(t) = sum f * A * cos(V(t) + u - G) with Doodson arguments and Schureman/Pugh nodal factors.
 *
 * Conventions were validated against NOAA's published high/low predictions (Nov 2026; Boston, The Battery,
 * San Francisco, Seattle, Pensacola: median error 1-7 min). TICON phases need tau WITHOUT the usual +180 deg
 * (equivalently: diurnal phase offsets flipped); with +180 the diurnal-dominated stations were hours off.
 */
object Tides {
    const val MAX_KM = 50.0
    const val MAX_EVENTS = 5

    /** name -> Doodson digits (as in the TICON-4 manual, +5 offsets), extra phase (deg), nodal group. */
    private class Con(val name: String, doodson: String, val phase: Double, val group: String) {
        val d = doodson.filter { it.isDigit() }.map { it - '0' }
    }

    // Order must match astro/tools/build_tides.py.
    private val CONS = listOf(
        Con("SA", "056 554", 0.0, "1"), Con("SSA", "057 555", 0.0, "1"), Con("MM", "065 455", 0.0, "MM"),
        Con("MSF", "073 555", 0.0, "M2"), Con("MF", "075 555", 0.0, "MF"), Con("2Q1", "125 755", -90.0, "O1"),
        Con("SIGMA1", "127 555", -90.0, "O1"), Con("Q1", "135 655", -90.0, "O1"), Con("RHO1", "137 455", -90.0, "O1"),
        Con("O1", "145 555", -90.0, "O1"), Con("P1", "163 555", -90.0, "1"), Con("S1", "164 555", 180.0, "1"),
        Con("K1", "165 555", 90.0, "K1"), Con("J1", "175 455", 90.0, "J1"), Con("OO1", "185 555", 90.0, "OO1"),
        Con("EPS2", "227 655", 0.0, "M2"), Con("2N2", "235 755", 0.0, "M2"), Con("MU2", "237 555", 0.0, "M2"),
        Con("N2", "245 655", 0.0, "M2"), Con("NU2", "247 455", 0.0, "M2"), Con("M2", "255 555", 0.0, "M2"),
        Con("LAMBDA2", "263 655", 180.0, "M2"), Con("L2", "265 455", 180.0, "M2"), Con("T2", "272 556", 0.0, "1"),
        Con("S2", "273 555", 0.0, "1"), Con("R2", "274 554", 180.0, "1"), Con("K2", "275 555", 0.0, "K2"),
        Con("2SM2", "291 555", 0.0, "M2"), Con("MKS2", "245 555", 0.0, "MKS2"), Con("M3", "355 555", 180.0, "M3"),
        Con("S3", "382 555", 0.0, "1"), Con("MN4", "445 655", 0.0, "M2^2"), Con("M4", "455 555", 0.0, "M2^2"),
        Con("MS4", "473 555", 0.0, "M2"), Con("S4", "491 555", 0.0, "1"), Con("M6", "655 555", 0.0, "M2^3"),
        Con("2MS6", "673 555", 0.0, "M2^2"), Con("M8", "855 555", 0.0, "M2^4"),
    )

    /** [datumCm]: chart datum depth below mean sea level (TideDatums), 0 if unknown. */
    class Station(val lat: Double, val lon: Double, val name: String, val amp: FloatArray, val phase: FloatArray,
                  val datumCm: Float = 0f)

    /** A day's extremes: minutes after local midnight, and whether the first one is a high. */
    data class Day(val minutes: List<Int>, val firstHigh: Boolean)

    // ---- station database ----------------------------------------------------------------------------------------

    fun parse(input: InputStream): List<Station> = DataInputStream(input.buffered()).use { s ->
        val magic = ByteArray(4).also { s.readFully(it) }
        require(String(magic) == "NWT1") { "bad tide db" }
        val names = List(s.readUnsignedShort()) { s.readUTF() }
        require(names == CONS.map { it.name }) { "tide db constituents don't match Tides.CONS" }
        List(s.readUnsignedShort()) {
            val lat = s.readFloat().toDouble()
            val lon = s.readFloat().toDouble()
            val name = s.readUTF()
            val amp = FloatArray(names.size)
            val pha = FloatArray(names.size)
            for (i in names.indices) {
                amp[i] = s.readUnsignedShort() / 10f   // mm -> cm
                pha[i] = s.readUnsignedShort() / 10f   // decidegrees -> degrees
            }
            Station(lat, lon, name, amp, pha)
        }
    }

    fun nearest(stations: List<Station>, lat: Double, lon: Double, maxKm: Double = MAX_KM): Station? =
        stations.map { it to km(lat, lon, it.lat, it.lon) }.filter { it.second <= maxKm }.minByOrNull { it.second }?.first

    private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }

    // ---- prediction -----------------------------------------------------------------------------------------------

    /** Predicted height (cm, relative to mean sea level) at [t]. */
    fun height(st: Station, t: Instant): Double = Prepared(st, t).height(t)

    /**
     * The harmonic sum set up around [ref] (owner 2026-11-24: the scrubber needs a day in a few ms). Nodal factors
     * f, u change over years (18.6-year node cycle), so they're computed once; each constituent's argument V is linear
     * in time, so it's V0 at [ref] plus a fixed rate. Within a day this matches the per-instant formula to ~1e-6 deg.
     */
    class Prepared(st: Station, private val ref: Instant) {
        private val amp: DoubleArray
        private val phase0: DoubleArray   // radians at ref: V0 + u - G
        private val rate: DoubleArray     // radians per second

        init {
            val days = Duration.between(J2000, ref).toMillis() / 86_400_000.0
            val T = days / 36525.0
            val s = 218.3164477 + 481267.88123421 * T
            val h = 280.46646 + 36000.76983 * T
            val p = 83.3532465 + 4069.0137287 * T
            val n = 125.04452 - 1934.136261 * T
            val p1 = 282.93735 + 1.71946 * T
            val utHours = ((ref.epochSecond % 86400 + 86400) % 86400) / 3600.0 + ref.nano / 3.6e12
            val tau = 15 * utHours + h - s // see class doc: no +180 for TICON phases
            val args = doubleArrayOf(tau, s, h, p, -n, p1)
            // degrees per hour of each argument (the T coefficients above / (36525 * 24)); tau also turns 15/h
            val perHour = DoubleArray(6)
            val c = 36525.0 * 24
            perHour[1] = 481267.88123421 / c
            perHour[2] = 36000.76983 / c
            perHour[3] = 4069.0137287 / c
            perHour[4] = 1934.136261 / c
            perHour[5] = 1.71946 / c
            perHour[0] = 15.0 + perHour[2] - perHour[1]
            val used = CONS.indices.filter { st.amp[it] != 0f }
            amp = DoubleArray(used.size)
            phase0 = DoubleArray(used.size)
            rate = DoubleArray(used.size)
            for ((j, i) in used.withIndex()) {
                val con = CONS[i]
                var v = con.d[0] * args[0] + con.phase
                var w = con.d[0] * perHour[0]
                for (k in 1..5) { v += (con.d[k] - 5) * args[k]; w += (con.d[k] - 5) * perHour[k] }
                val (f, u) = nodal(con.group, n)
                amp[j] = f * st.amp[i]
                phase0[j] = Math.toRadians(v + u - st.phase[i])
                rate[j] = Math.toRadians(w) / 3600.0
            }
        }

        fun height(t: Instant): Double = heightAt(Duration.between(ref, t).toMillis() / 1000.0)

        /** Height [sec] seconds after ref. */
        fun heightAt(sec: Double): Double {
            var sum = 0.0
            for (j in amp.indices) sum += amp[j] * cos(phase0[j] + rate[j] * sec)
            return sum
        }
    }

    /**
     * High/low waters on [day]'s local calendar date (at most [MAX_EVENTS]); null if there are none.
     * Same result as checking every minute (TidesTest compares), found fast: a 10-minute grid locates
     * each extreme, then the minutes around it pick the exact one.
     */
    fun day(st: Station, day: ZonedDateTime): Day? {
        val start = day.truncatedTo(ChronoUnit.DAYS)
        val minutes = Duration.between(start, start.plusDays(1)).toMinutes().toInt() // 1380/1440/1500 on DST days
        val prep = Prepared(st, start.toInstant())
        fun h(m: Int) = prep.heightAt(m * 60.0)
        val step = 10
        val grid = (-step..minutes + step step step).toList()
        val hs = grid.map { h(it) }
        val found = sortedMapOf<Int, Boolean>()
        for (k in 1 until grid.size - 1) {
            val high = hs[k] > hs[k - 1] && hs[k] >= hs[k + 1]
            val low = hs[k] < hs[k - 1] && hs[k] <= hs[k + 1]
            if (!high && !low) continue
            // Per-minute check (the every-minute rule) across the two grid intervals around this point.
            val lo = grid[k] - step
            val hm = DoubleArray(2 * step + 1) { h(lo + it) }
            for (i in 1 until hm.size - 1) {
                val m = lo + i
                if (m < 0 || m >= minutes) continue
                val hi2 = hm[i] > hm[i - 1] && hm[i] >= hm[i + 1]
                val lo2 = hm[i] < hm[i - 1] && hm[i] <= hm[i + 1]
                if ((high && hi2) || (low && lo2)) found[m] = high
            }
        }
        if (found.isEmpty()) return null
        val out = found.entries.map { (m, high) ->
            val local = start.plusMinutes(m.toLong())
            (local.hour * 60 + local.minute) to high
        }
        val kept = out.take(MAX_EVENTS)
        return Day(kept.map { it.first }, kept.first().second)
    }

    private val J2000: Instant = Instant.parse("2000-01-01T12:00:00Z")

    /** Nodal factor f and angle u (deg) for longitude of the moon's node [nDeg] (Schureman, via Pugh 1987). */
    private fun nodal(group: String, nDeg: Double): Pair<Double, Double> {
        val n = Math.toRadians(nDeg)
        val m2 = (1.0004 - 0.0373 * cos(n) + 0.0002 * cos(2 * n)) to (-2.14 * sin(n))
        return when (group) {
            "1" -> 1.0 to 0.0
            "M2" -> m2
            "M2^2", "M2^3", "M2^4" -> group.last().digitToInt().let { k -> m2.first.pow(k) to m2.second * k }
            "M3" -> m2.first.pow(1.5) to m2.second * 1.5
            "O1" -> (1.0089 + 0.1871 * cos(n) - 0.0147 * cos(2 * n) + 0.0014 * cos(3 * n)) to
                (10.80 * sin(n) - 1.34 * sin(2 * n) + 0.19 * sin(3 * n))
            "K1" -> (1.0060 + 0.1150 * cos(n) - 0.0088 * cos(2 * n) + 0.0006 * cos(3 * n)) to
                (-8.86 * sin(n) + 0.68 * sin(2 * n) - 0.07 * sin(3 * n))
            "K2" -> (1.0241 + 0.2863 * cos(n) + 0.0083 * cos(2 * n) - 0.0015 * cos(3 * n)) to
                (-17.74 * sin(n) + 0.68 * sin(2 * n) - 0.04 * sin(3 * n))
            "J1" -> (1.0129 + 0.1676 * cos(n) - 0.0170 * cos(2 * n) + 0.0016 * cos(3 * n)) to
                (-12.94 * sin(n) + 1.34 * sin(2 * n) - 0.19 * sin(3 * n))
            "OO1" -> (1.1027 + 0.6504 * cos(n) + 0.0317 * cos(2 * n) - 0.0014 * cos(3 * n)) to
                (-36.68 * sin(n) + 4.02 * sin(2 * n) - 0.57 * sin(3 * n))
            "MF" -> (1.043 + 0.414 * cos(n)) to (-23.7 * sin(n) + 2.7 * sin(2 * n) - 0.4 * sin(3 * n))
            "MM" -> (1.000 - 0.130 * cos(n)) to 0.0
            "MKS2" -> nodal("K2", nDeg).let { k -> m2.first * k.first to m2.second + k.second }
            else -> error("nodal group $group")
        }
    }
}
