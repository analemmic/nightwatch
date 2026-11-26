package com.analemmic.nightwatch.astro

import kotlin.math.roundToInt

/**
 * The wire format of the face's 7 data slots (constants generated from face/tools/gen_face.py CODEC into [Codec], so
 * this encoder and the face's decoder can't drift). Each slot is a RANGED_VALUE (min, value, max):
 *   min = -OFFSET - lo, max = OFFSET + hi   (lo, hi: non-negative integers, exact in a float below 2^24)
 *   value in [-OFFSET, OFFSET]               (so min <= value <= max always holds)
 * The face treats anything not shaped like this as absent (e.g. the system's placeholder data behind the lock screen).
 *
 *   slot  source    lo                              value               hi
 *   1     Sun       sunrise edge                    solar noon          sunset edge
 *   2-4   twilight  dawn edge                       0                   dusk edge
 *   5     Moon      moonrise edge                   transit             moonset edge
 *   6     Tide      t0 * PAIR + t1                  count * 2 + 1stHigh t2 * PAIR + t3
 *   7     Extras    t4 + PAIR * dayFirst            equinox/solstice    moon age * AGE_SCALE
 * Edge: round((angle + 180) * EDGE_SCALE) + EDGE_REAL if a real event (else a noon-window boundary); EDGE_NONE = none.
 * Tide times: minutes after local midnight.
 */
object SlotCodec {
    data class Data(val min: Float, val value: Float, val max: Float)

    private fun of(lo: Int, value: Float, hi: Int) = Data(-Codec.OFFSET - lo.toFloat(), value, Codec.OFFSET + hi.toFloat())

    fun edge(angle: Float, real: Boolean): Int =
        if (angle == ABSENT) Codec.EDGE_NONE
        else ((angle + 180f) * Codec.EDGE_SCALE).roundToInt() + if (real) Codec.EDGE_REAL else 0

    fun band(b: Band, value: Float = 0f) = of(edge(b.start, b.startReal), value, edge(b.end, b.endReal))

    fun tides(d: Tides.Day?): Data {
        val m = d?.minutes.orEmpty()
        fun t(i: Int) = m.getOrElse(i) { 0 }
        val meta = if (d == null) 0 else m.size * 2 + if (d.firstHigh) 1 else 0
        return of(t(0) * Codec.PAIR + t(1), meta.toFloat(), t(2) * Codec.PAIR + t(3))
    }

    fun extras(tides: Tides.Day?, dayFirst: Boolean, season: Float, moonAge: Float): Data {
        val t4 = tides?.minutes?.getOrElse(4) { 0 } ?: 0
        val age = if (moonAge == ABSENT) 99_999 else (moonAge * Codec.AGE_SCALE).roundToInt()
        return of(t4 + if (dayFirst) Codec.PAIR else 0, season, age)
    }

    // ---- decoding: the face's side, mirrored for tests ----------------------------------------------------------------

    fun lo(d: Data) = (-d.min - Codec.OFFSET).roundToInt()
    fun hi(d: Data) = (d.max - Codec.OFFSET).roundToInt()

    /** Dial angle of an edge, or ABSENT. */
    fun angle(enc: Int): Float =
        if (enc > Codec.EDGE_NONE - 500 || enc < 0) ABSENT else (enc % Codec.EDGE_REAL) / Codec.EDGE_SCALE.toFloat() - 180f

    fun real(enc: Int) = enc in Codec.EDGE_REAL until Codec.EDGE_NONE - 500
}
