package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TideDatumsTest {
    private val db = File("src/main/res/raw/tide_stations.bin").readBytes()
    private val depths = File("src/main/res/raw/tide_datums.bin").inputStream().use { TideDatums.parse(it, db) }
    private val stations = TideDatums.attach(Tides.parse(db.inputStream()), depths)
    private fun station(prefix: String) = stations.single { it.name.startsWith(prefix) }

    @Test fun datumsWereBuiltForThisStationDatabase() {
        assertNotNull("rebuild: NW_BUILD_DATUMS=1 ./gradlew :astro:testDebugUnitTest --tests '*BuildTideDatums*'", depths)
        assertEquals(stations.size, depths!!.size)
    }

    /** NOAA datums page, MSL - MLLW for the 1983-2001 epoch; harmonic-only estimates land within a few cm. */
    @Test fun mllwMatchesNoaa() {
        assertEquals(78.3, station("the_battery-8518750").datumCm.toDouble(), 6.0)
        assertEquals(158.6, station("boston-8443970").datumCm.toDouble(), 6.0)
    }

    /** NOAA hi/lo predictions for 2026-11-25 (feet above MLLW) against the day table's numbers. Measured on
     *  2026-11-25: Boston within 0.31 ft, The Battery 0.29-0.48 ft low (datum 0.16 ft shallow, rest the constants). */
    @Test fun heightsMatchNoaaTables() {
        val ny = ZoneId.of("America/New_York")
        fun ft(st: Tides.Station, hhmm: String): Double {
            val t = ZonedDateTime.of(java.time.LocalDate.of(2026, 11, 25), LocalTime.parse(hhmm), ny).toInstant()
            return (Tides.height(st, t) + st.datumCm) / 100.0 / 0.3048
        }
        val battery = station("the_battery-8518750")
        for ((t, v) in listOf("01:56" to -0.404, "07:53" to 6.055, "14:53" to -0.602, "20:32" to 4.723))
            assertEquals("Battery $t", v, ft(battery, t), 0.5)
        val boston = station("boston-8443970")
        for ((t, v) in listOf("04:48" to -0.073, "10:57" to 11.8, "17:30" to -1.404, "23:40" to 9.947))
            assertEquals("Boston $t", v, ft(boston, t), 0.5)
    }
}
