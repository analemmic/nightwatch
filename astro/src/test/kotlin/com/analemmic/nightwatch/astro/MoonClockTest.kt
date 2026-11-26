package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

/** Owner 2026-11-23: moon ticks at the CLOCK positions of today's moonrise/moonset (as weather apps list them). */
class MoonClockTest {
    @Test
    fun newYorkNov23MatchesPublishedTimes() {
        // NYC 2026-11-23: moonrise 15:29, moonset 05:46 (commons-suncalc; owner's weather app agrees within minutes).
        val s = Astro.compute(40.71, -74.01, ZonedDateTime.parse("2026-11-23T16:45:00-05:00[America/New_York]"))
        assertEquals(15f * (15f + 29f / 60f - 12f), s.moon.start, 0.3f)
        assertEquals(15f * (5f + 46f / 60f - 12f) + 360f, s.moon.end, 0.3f) // wraps clockwise past midnight
    }
}
