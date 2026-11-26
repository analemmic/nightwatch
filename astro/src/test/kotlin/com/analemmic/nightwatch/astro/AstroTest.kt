package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Polar / edge cases from CLAUDE.md "Corner cases". Pure JVM: Astro uses only commons-suncalc + java.time. */
class AstroTest {
    private val oslo = ZoneId.of("Europe/Oslo")
    private val ny = ZoneId.of("America/New_York")

    private data class Place(val name: String, val lat: Double, val lon: Double, val zone: ZoneId)
    private val nyc = Place("New York", 40.71, -74.01, ny)
    private val osloCity = Place("Oslo", 59.91, 10.75, oslo)
    private val tromso = Place("Tromso", 69.65, 18.96, oslo)
    private val svalbard = Place("Longyearbyen", 78.22, 15.65, oslo)

    private fun at(p: Place, date: String, hour: Int) =
        Astro.compute(p.lat, p.lon, ZonedDateTime.of(LocalDate.parse(date).atTime(hour, 0), p.zone))

    private fun span(b: Band) = if (b == Band.NONE) 0f else b.end - b.start

    private fun kind(b: Band) = when {
        b == Band.NONE -> "none"
        b == Band.FULL -> "full"
        b.startReal && b.endReal -> "normal"
        else -> "partial"
    }

    @Test fun nycIsAlwaysNormal() {
        var d = LocalDate.parse("2026-01-01")
        while (d.year == 2026) {
            for (h in listOf(6, 13)) {
                val s = at(nyc, d.toString(), h)
                for (b in listOf(s.civil, s.nautical, s.astro)) assertEquals("$d $h $b", "normal", kind(b))
                assertEquals(63, s.flags and 63)
            }
            d = d.plusDays(1)
        }
    }

    /** Solar noon sits midway between civil dawn and dusk (the noon-window pair straddles it) to within ~1 deg. */
    @Test fun solarNoonIsMidTwilight() {
        var d = LocalDate.parse("2026-01-01")
        while (d.year == 2026) {
            val s = at(nyc, d.toString(), 13)
            val mid = (s.civil.start + s.civil.end) / 2f
            assertTrue("$d noon=${s.solarNoon} mid=$mid", kotlin.math.abs(s.solarNoon - mid) < 1.5f)
            d = d.plusDays(7)
        }
        assertTrue(at(svalbard, "2026-12-21", 13).solarNoon != ABSENT)   // polar night still has a solar noon
    }

    @Test fun tromsoMidnightSunIsAllLight() {
        val s = at(tromso, "2026-06-21", 13)
        assertEquals(Band.FULL, s.civil); assertEquals(Band.FULL, s.nautical); assertEquals(Band.FULL, s.astro)
        assertEquals(0, s.flags and 63)
    }

    @Test fun osloMidsummerHasAllNightTwilight() {
        val s = at(osloCity, "2026-06-21", 13)
        assertEquals("normal", kind(s.civil))      // sun dips just below -6 deg around 01:20
        assertEquals(Band.FULL, s.nautical)        // never below -12: nautical twilight all night
        assertEquals(Band.FULL, s.astro)
        assertEquals(3, s.flags and 63)            // only the civil lines are real
    }

    @Test fun svalbardMidwinterHasNoCivilLight() {
        val s = at(svalbard, "2026-12-21", 13)
        assertEquals(Band.NONE, s.civil)           // noon sun ~ -11.7 deg: never reaches -6
        assertEquals(0, s.flags and 3)
    }

    /** Invariants over a whole year, 4 latitudes, morning and afternoon. Prints the partial days found. */
    @Test fun yearSweepInvariants() {
        val partials = mutableListOf<String>()
        val moonKinds = mutableMapOf<String, Int>()
        for (p in listOf(nyc, osloCity, tromso, svalbard)) {
            var d = LocalDate.parse("2026-01-01")
            while (d.year == 2026) {
                for (h in listOf(6, 13)) {
                    val s = at(p, d.toString(), h)
                    val bands = listOf("civil" to s.civil, "nautical" to s.nautical, "astro" to s.astro)
                    for ((n, b) in bands) {
                        if (b == Band.NONE) continue
                        assertTrue("$p $d $n $b", b.start <= b.end && b.end - b.start <= 360f)
                        when (kind(b)) {
                            "full" -> assertTrue(b.start == -180f && b.end == 180f)
                            "partial" -> {
                                assertTrue("$p $d $n $b", (!b.startReal && b.start == 0f) || (!b.endReal && b.end == 360f))
                                partials += "${p.name} $d ${h}h $n $b"
                            }
                        }
                    }
                    // Physically nested: civil-lit within nautical-lit within astro-lit (1 deg slack for minute rounding).
                    assertTrue("$p $d civil>naut", span(s.civil) <= span(s.nautical) + 1f)
                    assertTrue("$p $d naut>astro", span(s.nautical) <= span(s.astro) + 1f)
                    val mk = kind(s.moon) + (if (s.moon != Band.NONE && s.moon.start == s.moon.end) "-tickOnly" else "")
                    moonKinds["${p.name} $mk"] = (moonKinds["${p.name} $mk"] ?: 0) + 1
                    if (kind(s.moon) == "full" || kind(s.moon) == "none") assertEquals(0, (s.flags shr 6) and 3)
                }
                d = d.plusDays(1)
            }
        }
        println("PARTIAL DAYS (${partials.size}):"); partials.forEach { println("  $it") }
        println("MOON KINDS: $moonKinds")
        assertTrue("expected moon always-up days at 78N", (moonKinds["Longyearbyen full"] ?: 0) > 0)
        assertTrue("expected moon always-down days at 78N", (moonKinds["Longyearbyen none"] ?: 0) > 0)
    }
}
