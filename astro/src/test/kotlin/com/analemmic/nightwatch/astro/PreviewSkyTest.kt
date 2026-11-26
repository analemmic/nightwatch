package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The picker thumbnail must look like an ordinary day: every edge real, no easter egg, the sun already up. */
class PreviewSkyTest {
    @Test
    fun previewIsAnOrdinaryOctoberMorning() {
        val stations = java.io.File("src/main/res/raw/tide_stations.bin").inputStream().use { Tides.parse(it) }
        val s = Astro.compute(PreviewSky.LAT, PreviewSky.LON, PreviewSky.AT, Tides.nearest(stations, PreviewSky.LAT, PreviewSky.LON))
        assertTrue("preview shows tides (${s.tides})", (s.tides?.minutes?.size ?: 0) >= 3)
        assertEquals("no equinox/solstice line", ABSENT, s.season)
        assertEquals("all twilight edges and moonrise/set real", 0xFF, s.flags)
        val hand = 15f * (7f + 40f / 60f - 12f) // 07:40 on the 24h dial
        assertTrue("crescent moon (age ${s.moonAge})", s.moonAge in 22f..27f)
        assertTrue("moon up at 07:40 (${s.moon.start}..${s.moon.end})", hand > s.moon.start && hand < s.moon.end)
        assertTrue("sun is up at 07:40 (civil dawn ${s.civil.start})", s.civil.start < hand && hand < s.civil.end)
    }
}
