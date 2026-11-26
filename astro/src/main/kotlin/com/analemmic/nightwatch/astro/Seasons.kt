package com.analemmic.nightwatch.astro

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.cos

/**
 * Equinox and solstice instants (Meeus, Astronomical Algorithms, ch. 27: mean JDE for years 2000-3000 plus the
 * 24-term periodic correction). Accurate to about a minute, which is all the face shows (HHMM).
 */
object Seasons {
    enum class Event { MARCH_EQUINOX, JUNE_SOLSTICE, SEPTEMBER_EQUINOX, DECEMBER_SOLSTICE }

    // Mean JDE0 polynomial coefficients in Y = (year - 2000) / 1000, per event.
    private val MEAN = mapOf(
        Event.MARCH_EQUINOX to doubleArrayOf(2451623.80984, 365242.37404, 0.05169, -0.00411, -0.00057),
        Event.JUNE_SOLSTICE to doubleArrayOf(2451716.56767, 365241.62603, 0.00325, 0.00888, -0.00030),
        Event.SEPTEMBER_EQUINOX to doubleArrayOf(2451810.21715, 365242.01767, -0.11575, 0.00337, 0.00078),
        Event.DECEMBER_SOLSTICE to doubleArrayOf(2451900.05952, 365242.74049, -0.06223, -0.00823, 0.00032),
    )

    // Periodic terms (A, B deg, C deg/century), Meeus table 27.C.
    private val TERMS = arrayOf(
        doubleArrayOf(485.0, 324.96, 1934.136), doubleArrayOf(203.0, 337.23, 32964.467),
        doubleArrayOf(199.0, 342.08, 20.186), doubleArrayOf(182.0, 27.85, 445267.112),
        doubleArrayOf(156.0, 73.14, 45036.886), doubleArrayOf(136.0, 171.52, 22518.443),
        doubleArrayOf(77.0, 222.54, 65928.934), doubleArrayOf(74.0, 296.72, 3034.906),
        doubleArrayOf(70.0, 243.58, 9037.513), doubleArrayOf(58.0, 119.81, 33718.147),
        doubleArrayOf(52.0, 297.17, 150.678), doubleArrayOf(50.0, 21.02, 2281.226),
        doubleArrayOf(45.0, 247.54, 29929.562), doubleArrayOf(44.0, 325.15, 31555.956),
        doubleArrayOf(29.0, 60.93, 4443.417), doubleArrayOf(18.0, 155.12, 67555.328),
        doubleArrayOf(17.0, 288.79, 4562.452), doubleArrayOf(16.0, 198.04, 62894.029),
        doubleArrayOf(14.0, 199.76, 31436.921), doubleArrayOf(12.0, 95.39, 14577.848),
        doubleArrayOf(12.0, 287.11, 31931.756), doubleArrayOf(12.0, 320.81, 34777.259),
        doubleArrayOf(9.0, 227.73, 1222.114), doubleArrayOf(8.0, 15.45, 16859.074),
    )

    /** TT - UT. ~69 s through the 2020s; a fixed value is fine at minute resolution. */
    private const val DELTA_T_SECONDS = 69L

    fun instant(event: Event, year: Int): Instant {
        val y = (year - 2000) / 1000.0
        val c = MEAN.getValue(event)
        val jde0 = c[0] + c[1] * y + c[2] * y * y + c[3] * y * y * y + c[4] * y * y * y * y
        val t = (jde0 - 2451545.0) / 36525.0
        val w = Math.toRadians(35999.373 * t - 2.47)
        val dLambda = 1 + 0.0334 * cos(w) + 0.0007 * cos(2 * w)
        val s = TERMS.sumOf { (a, b, cc) -> a * cos(Math.toRadians(b + cc * t)) }
        val jde = jde0 + 0.00001 * s / dLambda
        // JD 2440587.5 = 1970-01-01T00:00 UT.
        val ttMillis = ((jde - 2440587.5) * 86_400_000.0).toLong()
        return Instant.ofEpochMilli(ttMillis).minus(Duration.ofSeconds(DELTA_T_SECONDS))
    }

    /** The event falling on [now]'s local calendar date, if any, as a local time. */
    fun today(now: ZonedDateTime): Pair<Event, ZonedDateTime>? {
        val date = now.toLocalDate()
        return Event.entries.firstNotNullOfOrNull { e ->
            instant(e, date.year).atZone(now.zone).takeIf { it.toLocalDate() == date }?.let { e to it }
        }
    }
}
