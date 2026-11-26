package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Daylight saving time (owner 2026-11-24: confirmed on the watch that the scrubber jumps an hour; lock it in).
 * New York 2026: clocks go forward on Mar 8 (23-hour day) and back on Nov 1 (25-hour day).
 */
class DstTest {
    private val ny = ZoneId.of("America/New_York")
    private val lat = 40.71
    private val lon = -74.01

    /** Clock minutes of the morning's sunrise / evening's sunset on [date] (compute at 06:00: last night's window). */
    private fun sunMinutes(date: LocalDate): Pair<Float, Float> {
        val dawnSide = Astro.compute(lat, lon, date.atTime(6, 0).atZone(ny)).sun.start
        val duskSide = Astro.compute(lat, lon, date.atTime(13, 0).atZone(ny)).sun.end
        return (dawnSide / 15f + 12f) * 60f to (duskSide / 15f + 12f) * 60f
    }

    private fun assertJump(before: LocalDate, after: LocalDate, lo: Float, hi: Float) {
        val (r0, s0) = sunMinutes(before)
        val (r1, s1) = sunMinutes(after)
        assertTrue("sunrise $before -> $after moved ${r1 - r0} min", r1 - r0 in lo..hi)
        assertTrue("sunset $before -> $after moved ${s1 - s0} min", s1 - s0 in lo..hi)
    }

    @Test
    fun springForwardMovesSunTimesAnHourLater() =
        // +60 min, less ~1-2 min/day of normal March change (sunrise earlier, sunset later) over 2 days
        assertJump(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 9), 55f, 64f)

    @Test
    fun fallBackMovesSunTimesAnHourEarlier() =
        assertJump(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 2), -64f, -55f)

    private val boston = File("src/main/res/raw/tide_stations.bin").inputStream().use { Tides.parse(it) }
        .let { Tides.nearest(it, 42.3548, -71.0534)!! }

    /** On the 23/25-hour days, each local-clock tide time must be the same instant as the tide computed in UTC. */
    private fun assertTidesMatchUtc(date: LocalDate) {
        val local = Tides.day(boston, date.atStartOfDay(ny))!!
        val utcEvents = (-1L..1L).flatMap { k ->
            val d = date.plusDays(k).atStartOfDay(ZoneOffset.UTC)
            Tides.day(boston, d)?.minutes?.map { d.plusMinutes(it.toLong()).toInstant() }.orEmpty()
        }
        assertTrue(local.minutes.size >= 3)
        for (m in local.minutes) {
            val instant = date.atTime(m / 60, m % 60).atZone(ny).toInstant()
            val nearest = utcEvents.minOf { abs(Duration.between(instant, it).toMinutes()) }
            assertTrue("$date local ${m / 60}:${"%02d".format(m % 60)} is $nearest min from the UTC tide", nearest <= 1)
        }
    }

    @Test
    fun tidesOnSpringForwardDayUseLocalClock() = assertTidesMatchUtc(LocalDate.of(2026, 3, 8))

    @Test
    fun tidesOnFallBackDayUseLocalClock() = assertTidesMatchUtc(LocalDate.of(2026, 11, 1))

    @Test
    fun dayLengthsAre23And25Hours() {
        fun hours(d: LocalDate) = Duration.between(d.atStartOfDay(ny), d.plusDays(1).atStartOfDay(ny)).toHours()
        assertEquals(23, hours(LocalDate.of(2026, 3, 8)))
        assertEquals(25, hours(LocalDate.of(2026, 11, 1)))
    }

    /** The scrubber computes each day at the current time of day; one inside the spring-forward gap must still work. */
    @Test
    fun timeInsideTheSpringGapStillComputes() {
        val inGap: ZonedDateTime = LocalDate.of(2026, 3, 8).atTime(2, 30).atZone(ny) // 02:30 doesn't exist -> 03:30
        assertEquals(3, inGap.hour)
        val s = Astro.compute(lat, lon, inGap)
        assertTrue(s.sun.start > -500f && s.moonTransit > -500f)
    }
}
