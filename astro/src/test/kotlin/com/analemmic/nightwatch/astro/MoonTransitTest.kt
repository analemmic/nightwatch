package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** Owner 2026-11-23: the face's moon marker sits at the moon's transit ("lunar noon"). */
class MoonTransitTest {
    private val day = ZonedDateTime.parse("2026-11-23T16:45:00-05:00[America/New_York]")

    @Test
    fun markerAtTodaysTransit() {
        val t = Astro.moonTransit(40.71, -74.01, day)!!
        assertEquals(Astro.clockAngle(t), Astro.compute(40.71, -74.01, day).moonTransit, 0.01f)
        assertEquals(23, t.hour) // 23:11, within minutes of the rise (15:29) / next set (07:05) midpoint
    }

    @Test
    fun dayWithoutTransitStillHasAMarker() {
        // Transits come ~50 min later each day, so within any month one local date has none.
        val noTransit = (0L..40L).map { day.plusDays(it) }.first { Astro.moonTransit(40.71, -74.01, it) == null }
        val angle = Astro.transitAngle(40.71, -74.01, noTransit)
        assertNotEquals(ABSENT, angle)
        assertTrue("near midnight ($angle)", angle > 165f || angle < -165f)
    }

    /** Newton transits (owner 2026-11-24: scrubber speed) match the original scan to a few seconds, incl. polar. */
    @Test
    fun newtonMatchesScan() {
        val places = listOf(40.71 to -74.01, 78.22 to 15.65, 69.65 to 18.96, -33.86 to 151.21, 0.0 to 0.0, -54.8 to -68.3)
        for ((lat, lon) in places) for (d in 0L until 400L step 3) {
            val from = day.truncatedTo(java.time.temporal.ChronoUnit.DAYS).plusDays(d)
            val to = from.plusDays(1)
            val a = Astro.transitsBetweenScan(lat, lon, from, to)
            val b = Astro.transitsBetween(lat, lon, from, to)
            val what = "$lat,$lon $from: $a vs $b"
            if (a.size != b.size) {
                // A transit within seconds of midnight can land on either side of the boundary.
                assertTrue(what, (a + b).all { java.time.Duration.between(from, it).abs().seconds < 5 ||
                    java.time.Duration.between(to, it).abs().seconds < 5 })
                continue
            }
            a.zip(b).forEach { (x, y) -> assertTrue(what, java.time.Duration.between(x, y).abs().seconds <= 3) }
        }
    }
}
