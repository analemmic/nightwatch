package com.analemmic.nightwatch.astro

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.CRC32

/**
 * Build tool, not a test (skipped unless NW_BUILD_DATUMS=1): computes each gauge's chart datum with the app's own
 * tide model and writes res/raw/tide_datums.bin (format: TideDatums.kt).
 *
 *   NW_BUILD_DATUMS=1 ./gradlew :astro:testDebugUnitTest --tests '*BuildTideDatums*'
 *
 * Over the US National Tidal Datum Epoch (1983-2001, one full 18.6-year node cycle), sampled every 6 minutes with each
 * low refined by a parabola: US gauges get MLLW (mean of each day's lower low water, as NOAA), all others LAT (the
 * lowest predicted low, the usual chart datum elsewhere). Stored as the datum's height below mean sea level.
 */
class BuildTideDatums {
    @Test
    fun build() {
        assumeTrue(System.getenv("NW_BUILD_DATUMS") == "1")
        val db = File("src/main/res/raw/tide_stations.bin")
        val stations = db.inputStream().use { Tides.parse(it) }
        val t0 = System.nanoTime()
        val out = stations.parallelStream().map { datum(it) }.toList()
        val crc = CRC32().apply { update(db.readBytes()) }.value
        DataOutputStream(File("src/main/res/raw/tide_datums.bin").outputStream().buffered()).use { o ->
            o.writeBytes(TideDatums.MAGIC)
            o.writeInt(crc.toInt())
            o.writeShort(stations.size)
            for (d in out) o.writeShort(Math.round(d).toInt().coerceIn(0, 32767))
        }
        println("datums for ${stations.size} gauges in ${(System.nanoTime() - t0) / 1_000_000_000} s")
        for ((s, d) in stations.zip(out)) {
            if (listOf("the_battery-", "boston-8443970", "charles_river_new", "san_francisco-9414290", "newlyn")
                    .any { s.name.startsWith(it) }) println("DATUM ${s.name}: ${"%.1f".format(d)} cm below MSL")
        }
    }

    /** Chart datum depth below mean sea level, cm (positive). */
    private fun datum(st: Tides.Station): Double {
        val us = TideDatums.isUs(st.name)
        var day = LocalDate.of(1983, 1, 1)
        val end = LocalDate.of(2002, 1, 1)
        var sumLower = 0.0
        var days = 0
        var lowest = Double.MAX_VALUE
        val step = 360.0 // seconds
        val n = 240
        while (day < end) {
            val ref: Instant = day.atStartOfDay(ZoneOffset.UTC).toInstant()
            val p = Tides.Prepared(st, ref)
            var prev2 = p.heightAt(-step)
            var prev = p.heightAt(0.0)
            var dayLow = Double.MAX_VALUE
            for (k in 1..n) {
                val h = p.heightAt(k * step)
                if (prev < prev2 && prev <= h) { // a low at sample k-1: parabola through the three samples
                    val denom = prev2 - 2 * prev + h
                    val low = if (denom > 0) prev - (prev2 - h) * (prev2 - h) / (8 * denom) else prev
                    if (low < dayLow) dayLow = low
                }
                prev2 = prev; prev = h
            }
            if (dayLow < Double.MAX_VALUE) {
                sumLower += dayLow; days++
                if (dayLow < lowest) lowest = dayLow
            }
            day = day.plusDays(1)
        }
        if (days == 0) return 0.0
        return -(if (us) sumLower / days else lowest)
    }
}
