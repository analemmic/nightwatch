package com.analemmic.nightwatch.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The slots' wire format round-trips, and always satisfies the face's validity check (gen_face.py VALID). */
class SlotCodecTest {
    private fun valid(d: SlotCodec.Data) =
        d.min <= -Codec.OFFSET && d.min >= -2_100_000 && d.value >= -Codec.OFFSET && d.value <= Codec.OFFSET

    @Test
    fun bandsRoundTripWithTheirRealEventFlags() {
        for (b in listOf(Band(-78.5f, 64.25f, true, true), Band(150f, 400.25f, true, true), Band.FULL, Band.NONE,
                         Band(0f, 211.75f, false, true), Band(37.5f, 360f, true, false), Band(-180f, 540f, true, true))) {
            val d = SlotCodec.band(b, value = -7.25f)
            assertTrue("$b -> $d", valid(d))
            val lo = SlotCodec.lo(d); val hi = SlotCodec.hi(d)
            assertEquals(b.start, SlotCodec.angle(lo), 0.13f)
            assertEquals(b.end, SlotCodec.angle(hi), 0.13f)
            assertEquals(b.startReal, SlotCodec.real(lo))
            assertEquals(b.endReal, SlotCodec.real(hi))
            assertEquals(-7.25f, d.value)
        }
    }

    @Test
    fun tidesRoundTrip() {
        val day = Tides.Day(listOf(5, 380, 760, 1139, 1439), firstHigh = false)
        val d = SlotCodec.tides(day)
        assertTrue(valid(d))
        val a = SlotCodec.lo(d); val b = SlotCodec.hi(d)
        assertEquals(listOf(5, 380, 760, 1139), listOf(a / Codec.PAIR, a % Codec.PAIR, b / Codec.PAIR, b % Codec.PAIR))
        assertEquals(5 * 2 + 0f, d.value)
        assertEquals(0f, SlotCodec.tides(null).value) // no gauge: count 0
        // the largest pair stays exact in a float
        val late = SlotCodec.tides(Tides.Day(listOf(1439, 1439, 1439, 1439), true))
        assertEquals(1439 * Codec.PAIR + 1439, SlotCodec.lo(late))
        assertTrue(valid(late))
    }

    @Test
    fun extrasRoundTrip() {
        val day = Tides.Day(listOf(5, 380, 760, 1139, 1439), firstHigh = false)
        for (dayFirst in listOf(true, false)) {
            val d = SlotCodec.extras(day, dayFirst, season = -64.5f, moonAge = 15.29f)
            assertTrue(valid(d))
            assertEquals(1439, SlotCodec.lo(d) % Codec.PAIR)
            assertEquals(if (dayFirst) 1 else 0, SlotCodec.lo(d) / Codec.PAIR)
            assertEquals(-64.5f, d.value)
            assertEquals(15.29f, SlotCodec.hi(d) / Codec.AGE_SCALE.toFloat(), 0.01f)
        }
        val none = SlotCodec.extras(null, false, ABSENT, ABSENT)
        assertTrue(valid(none))
        assertTrue("absent age decodes above 30 days", SlotCodec.hi(none) / Codec.AGE_SCALE.toFloat() > 30f)
    }

    @Test
    fun placeholderDataIsNotValid() {
        assertFalse(valid(SlotCodec.Data(0f, 0f, 100f)))
    }
}
