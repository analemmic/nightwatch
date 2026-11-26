package com.analemmic.nightwatch.astro

import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.CRC32

/**
 * Each tide gauge's chart datum, so the day table's heights read like tide tables (owner 2026-11-25): MLLW for US
 * gauges (NOAA), LAT elsewhere. Precomputed with this app's own model by the BuildTideDatums tool into
 * res/raw/tide_datums.bin: "NWD1", i32 CRC-32 of tide_stations.bin, u16 count, then per gauge (same order) u16
 * datum depth below mean sea level in cm. A file built for another station database is ignored (heights then stay
 * relative to mean sea level).
 */
object TideDatums {
    const val MAGIC = "NWD1"

    fun isUs(stationName: String) = stationName.contains("-usa-")

    /** Datum depths (cm) in station order, or null if [datums] wasn't built from [stationDb]. */
    fun parse(datums: InputStream, stationDb: ByteArray): FloatArray? = DataInputStream(datums.buffered()).use { s ->
        val magic = ByteArray(4).also { s.readFully(it) }
        if (String(magic) != MAGIC) return null
        val crc = CRC32().apply { update(stationDb) }.value.toInt()
        if (s.readInt() != crc) return null
        FloatArray(s.readUnsignedShort()) { s.readUnsignedShort().toFloat() }
    }

    /** The stations with their datums attached (unchanged if [depths] doesn't fit). */
    fun attach(stations: List<Tides.Station>, depths: FloatArray?): List<Tides.Station> =
        if (depths == null || depths.size != stations.size) stations
        else stations.mapIndexed { i, s -> Tides.Station(s.lat, s.lon, s.name, s.amp, s.phase, depths[i]) }
}
