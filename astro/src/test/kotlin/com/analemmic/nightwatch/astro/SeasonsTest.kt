package com.analemmic.nightwatch.astro

import com.analemmic.nightwatch.astro.Seasons.Event.DECEMBER_SOLSTICE
import com.analemmic.nightwatch.astro.Seasons.Event.JUNE_SOLSTICE
import com.analemmic.nightwatch.astro.Seasons.Event.MARCH_EQUINOX
import com.analemmic.nightwatch.astro.Seasons.Event.SEPTEMBER_EQUINOX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class SeasonsTest {
    /** Published UTC times (to the minute). The face shows HHMM, so within 2 minutes is the bar. */
    @Test fun matchesPublishedTimes() {
        val known = listOf(
            Triple(MARCH_EQUINOX, 2024, "2024-03-20T03:06:00Z"), Triple(JUNE_SOLSTICE, 2024, "2024-06-20T20:51:00Z"),
            Triple(SEPTEMBER_EQUINOX, 2024, "2024-09-22T12:44:00Z"), Triple(DECEMBER_SOLSTICE, 2024, "2024-12-21T09:20:00Z"),
            Triple(MARCH_EQUINOX, 2025, "2025-03-20T09:01:00Z"), Triple(JUNE_SOLSTICE, 2025, "2025-06-21T02:42:00Z"),
            Triple(SEPTEMBER_EQUINOX, 2025, "2025-09-22T18:19:00Z"), Triple(DECEMBER_SOLSTICE, 2025, "2025-12-21T15:03:00Z"),
        )
        for ((e, y, iso) in known) {
            val got = Seasons.instant(e, y)
            val diff = abs(Duration.between(Instant.parse(iso), got).toSeconds())
            println("$e $y: computed $got, published $iso, diff ${diff}s")
            assertTrue("$e $y off by ${diff}s", diff <= 120)
        }
    }

    @Test fun todayUsesLocalDate() {
        val ny = ZoneId.of("America/New_York")
        val june = Seasons.instant(JUNE_SOLSTICE, 2026).atZone(ny)
        val hit = Seasons.today(june.withHour(23).withMinute(30))
        assertEquals(JUNE_SOLSTICE, hit?.first)
        assertNull(Seasons.today(june.plusDays(1)))
        assertNull(Seasons.today(ZonedDateTime.of(2026, 11, 23, 12, 0, 0, 0, ny)))
        println("2026 events (New York): " + Seasons.Event.entries.map { Seasons.instant(it, 2026).atZone(ny) })
    }

    @Test fun snapshotCarriesSeasonAngle() {
        val ny = ZoneId.of("America/New_York")
        val t = Seasons.instant(DECEMBER_SOLSTICE, 2026).atZone(ny)
        val s = Astro.compute(40.71, -74.01, t.withHour(12).withMinute(0))
        assertEquals(Astro.clockAngle(t), s.season, 0.001f)
        assertEquals(ABSENT, Astro.compute(40.71, -74.01, t.plusDays(2)).season, 0f)
    }
}
