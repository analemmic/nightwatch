package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

class TzCentersTest {
    private val centers = TzCenters.parse(File("src/main/res/raw/tz_centers.txt").readLines().asSequence())
    private val now = Instant.parse("2026-11-25T12:00:00Z")
    private fun fix(lat: Double, lon: Double) = LocationSource.LatLon(lat, lon, "test")
    private fun choose(f: LocationSource.LatLon?, zone: String) = TzCenters.choose(centers, f, ZoneId.of(zone), now)

    @Test fun everyZoneIsKnownToJava() {
        assert(centers.size > 400)
        for (c in centers) ZoneId.of(c.zone)
    }

    @Test fun noFixUsesTheZoneCity() {
        val l = choose(null, "America/New_York")!!
        assertEquals(40.71, l.lat, 0.01)
        assertEquals(-74.01, l.lon, 0.01)
    }

    @Test fun aFixInsideTheZoneIsKept() {
        val boston = fix(42.36, -71.06)
        assertSame(boston, choose(boston, "America/New_York"))
        val miami = fix(25.76, -80.19)   // 1750 km from New York, but its nearest zone city keeps Eastern time
        assertSame(miami, choose(miami, "America/New_York"))
    }

    @Test fun aFixNearABorderIsKept() {
        val vigo = fix(42.24, -8.72)     // nearest zone city: Lisbon (UTC+0), the watch on Madrid time (UTC+1)
        assertSame(vigo, choose(vigo, "Europe/Madrid"))
    }

    @Test fun aFixFromAnotherZoneFarAwayIsDropped() {
        assertEquals("timezone Asia/Tokyo", choose(fix(42.36, -71.06), "Asia/Tokyo")!!.source)
        assertEquals("timezone America/New_York", choose(fix(41.88, -87.63), "America/New_York")!!.source) // Chicago
    }

    @Test fun aZoneWithoutACityKeepsTheFixOrNothing() {
        val boston = fix(42.36, -71.06)
        assertSame(boston, choose(boston, "UTC"))
        assertNull(choose(null, "UTC"))
    }
}
