package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** TICON-4 harmonic predictor vs NOAA's published high/low tables (src/test/resources/noaa). */
class TidesTest {
    private val stations = File("src/main/res/raw/tide_stations.bin").inputStream().use { Tides.parse(it) }

    private fun noaa(id: String): List<Pair<Instant, Boolean>> {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return javaClass.getResource("/noaa/hilo_${id}_2026-11_gmt.csv")!!.readText().lines().drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
            .map { LocalDateTime.parse(it[0], fmt).toInstant(ZoneOffset.UTC) to (it[2].trim() == "H") }
    }

    /** Errors (minutes) of our extremes vs NOAA's, matching each NOAA event to our nearest one of the same type. */
    private fun errors(id: String, lat: Double, lon: Double): List<Long> {
        val st = Tides.nearest(stations, lat, lon, maxKm = 2.0)
        assertNotNull("gauge for $id", st)
        val ours = (1..29).flatMap { d ->
            val day = ZonedDateTime.of(2026, 11, d, 0, 0, 0, 0, ZoneOffset.UTC)
            val td = Tides.day(st!!, day) ?: return@flatMap emptyList()
            td.minutes.mapIndexed { i, m -> day.plusMinutes(m.toLong()).toInstant() to ((i % 2 == 0) == td.firstHigh) }
        }
        return noaa(id).filter { it.first < Instant.parse("2026-11-29T22:00:00Z") && it.first > Instant.parse("2026-11-01T02:00:00Z") }
            .map { (t, high) ->
                ours.filter { it.second == high }.minOf { Duration.between(t, it.first).toMinutes().let(::abs) }
            }
    }

    private fun check(id: String, lat: Double, lon: Double, medianMax: Long, p90Max: Long) {
        val e = errors(id, lat, lon).sorted()
        val median = e[e.size / 2]
        val p90 = e[e.size * 9 / 10]
        println("TIDES $id n=${e.size} median=$median p90=$p90 max=${e.last()}")
        assertTrue("$id median $median min", median <= medianMax)
        assertTrue("$id p90 $p90 min", p90 <= p90Max)
    }

    @Test fun boston() = check("8443970", 42.3548, -71.0534, 5, 10)
    // Regression: the nearest gauge here used to be an 1867-77 record running ~37 min late; pre-1990 records are dropped.
    @Test fun charlestownUsesAModernGauge() = check("8443970", 42.3717, -71.0550, 5, 12)
    @Test fun theBattery() = check("8518750", 40.7006, -74.0142, 10, 20)
    @Test fun sanFrancisco() = check("9414290", 37.8063, -122.4659, 10, 20)
    @Test fun seattle() = check("9447130", 47.6019, -122.3392, 10, 25)
    @Test fun pensacola() = check("8729840", 30.4044, -87.2112, 10, 30) // diurnal; flat near neaps

    /** The fast grid search (owner 2026-11-24: scrubber speed) must find the every-minute extremes (to the minute). */
    @Test
    fun fastDayMatchesEveryMinute() {
        val places = listOf(42.3548 to -71.0534, 40.7006 to -74.0142, 37.8063 to -122.4659, 47.6019 to -122.3392,
            30.4044 to -87.2112, 51.5 to 0.6, -33.86 to 151.21, 35.45 to 139.65, 64.15 to -21.95)
        val zone = java.time.ZoneId.of("America/New_York") // includes both DST switch days
        var days = 0
        for ((lat, lon) in places) {
            val st = Tides.nearest(stations, lat, lon) ?: continue
            for (d in 0 until 366) {
                val day = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone).plusDays(d.toLong())
                // Same events, to the minute. Ties (two minutes equal to ~1e-6 cm at a flat extreme) can round either
                // way, which can also add/drop a stand right at the day's last minute; ignore that minute.
                fun events(d: Tides.Day?) = d?.minutes?.mapIndexed { i, m -> m to ((i % 2 == 0) == d.firstHigh) }
                    ?.filter { it.first < 1439 }.orEmpty()
                val slow = events(Tides.dayEveryMinute(st, day))
                val fast = events(Tides.day(st, day))
                val what = "${st.name} ${day.toLocalDate()}: $slow vs $fast"
                assertEquals(what, slow.size, fast.size)
                slow.zip(fast).forEach { (a, b) -> assertTrue(what, a.second == b.second && abs(a.first - b.first) <= 1) }
                days++
            }
        }
        assertTrue(days > 2500)
    }

    @Test
    fun inlandHasNoGauge() = assertNull(Tides.nearest(stations, 39.74, -104.99)) // Denver
}
