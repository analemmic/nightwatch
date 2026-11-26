package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZonedDateTime

class DayTableTest {
    private val stations = File("src/main/res/raw/tide_stations.bin").inputStream().use { Tides.parse(it) }

    @Test
    fun newYorkNov23() {
        val now = ZonedDateTime.parse("2026-11-23T16:45:00-05:00[America/New_York]")
        val rows = DayTable.rows(40.71, -74.01, now, Tides.nearest(stations, 40.71, -74.01))
        rows.forEach { println("TABLE ${it.emoji} ${it.label} ${it.time ?: ""}") }
        val timed = rows.filter { it.time != null }
        assertEquals("time order", timed.map { it.minute }.sorted(), timed.map { it.minute })
        val labels = timed.map { it.label }
        for (l in listOf("Astro dawn", "Nautical dawn", "Civil dawn", "Sunrise", "Solar noon", "Sunset",
                         "Civil dusk", "Nautical dusk", "Astro dusk", "Moonrise", "Moonset")) assertTrue(l, l in labels)
        assertEquals("15:29", timed.first { it.label == "Moonrise" }.time) // as in MoonClockTest
        assertTrue("tides", labels.count { Regex("^(Hi|Lo) tide [+\u2212]\\d+\\.\\d(m|\u2032)$").matches(it) } >= 3)
        // Phase sits on the moon's transit, ~halfway between moonrise (15:29) and next moonset (07:05): ~23:20.
        val transit = timed.single { it.label.startsWith("Full moon") || it.label.startsWith("Waxing gibbous") }
        assertTrue("transit ${transit.time}", transit.minute in (22 * 60 + 30)..(24 * 60 - 1))
    }

    @Test
    fun tideHeightFormat() {
        assertEquals("+1.2m", DayTable.tideHeight(1.23, feet = false))
        assertEquals("\u22120.6m", DayTable.tideHeight(-0.61, feet = false))
        assertEquals("+4.0\u2032", DayTable.tideHeight(1.22, feet = true))
        assertEquals("+0.0m", DayTable.tideHeight(-0.04, feet = false))
    }

    @Test
    fun transitMatchesMaxAltitude() {
        val day = ZonedDateTime.parse("2026-11-23T00:00:00-05:00[America/New_York]")
        val t = DayTable.moonTransit(40.71, -74.01, day)!!
        fun alt(z: ZonedDateTime) = org.shredzone.commons.suncalc.MoonPosition.compute().on(z).at(40.71, -74.01).execute().altitude
        assertTrue(alt(t) >= alt(t.minusMinutes(20)) && alt(t) >= alt(t.plusMinutes(20)))
    }

    @Test
    fun solsticeRowOnTheDay() {
        val rows = DayTable.rows(40.71, -74.01, ZonedDateTime.parse("2026-12-21T12:00:00-05:00[America/New_York]"), null)
        assertTrue(rows.any { it.label == "December solstice" && it.emoji == "❄️" })
    }
}
