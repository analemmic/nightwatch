package com.analemmic.nightwatch.astro

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val TAG = "NightwatchAstro"

/** One snapshot shared by all sources so they agree; recomputed at most once a minute. */
object SnapshotCache {
    private val lock = Mutex()
    private var at: Long = 0
    private var snap: Snapshot? = null

    fun invalidate() { at = 0 }

    suspend fun get(service: SuspendingComplicationDataSourceService): Snapshot? = lock.withLock {
        val nowMs = System.currentTimeMillis()
        if (snap != null && nowMs - at < 60_000) return snap
        val where = Sky.where(service) ?: run {
            Log.w(TAG, "no location available (grant permission / wait for a fix)")
            return null
        }
        val s = where.snapshot()
        Log.i(TAG, "snapshot at ${where.now} via ${where.source}, tide gauge ${where.station?.name ?: "none"}: $s")
        snap = s
        at = nowMs
        s
    }
}

/**
 * A face data slot: publishes one RANGED_VALUE packed by [SlotCodec]. The face binds each slot to its source by
 * class name (gen_face.py SLOTS), so renaming a source means reinstalling the face.
 */
abstract class SlotSource : SuspendingComplicationDataSourceService() {
    abstract fun data(s: Snapshot): SlotCodec.Data

    /** The watch-face picker's live thumbnail (see [PreviewSky]). */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.RANGED_VALUE) ranged(data(PreviewSky.snapshot(this))) else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val s = SnapshotCache.get(this) ?: return NoDataComplicationData()
        val d = data(s)
        Log.i(TAG, "${javaClass.simpleName} -> $d")
        return ranged(d)
    }

    private fun ranged(d: SlotCodec.Data) =
        RangedValueComplicationData.Builder(d.value, d.min, d.max, text(javaClass.simpleName))
            .setText(text("${d.min} ${d.value} ${d.max}"))
            .build()

    private fun text(s: String) = PlainComplicationText.Builder(s).build()
}

/** Slot 1: true sunrise/sunset, solar noon. */
class SunSource : SlotSource() {
    override fun data(s: Snapshot) = SlotCodec.band(s.sun, s.solarNoon)
}

/** Slot 2: civil dawn/dusk. */
class CivilSource : SlotSource() {
    override fun data(s: Snapshot) = SlotCodec.band(s.civil)
}

/** Slot 3: nautical dawn/dusk. */
class NauticalSource : SlotSource() {
    override fun data(s: Snapshot) = SlotCodec.band(s.nautical)
}

/** Slot 4: astronomical dawn/dusk. */
class AstroSource : SlotSource() {
    override fun data(s: Snapshot) = SlotCodec.band(s.astro)
}

/** Slot 5: moonrise/moonset, transit (the moon marker). */
class MoonSource : SlotSource() {
    override fun data(s: Snapshot) = SlotCodec.band(s.moon, s.moonTransit)
}

/** Slot 6: today's high/low waters 1-4 and their count. */
class TideSource : SlotSource() {
    override fun data(s: Snapshot) = SlotCodec.tides(s.tides)
}

/** Slot 7: tide 5, the user's date order, today's equinox/solstice, the moon's age (phase sprite). */
class ExtrasSource : SlotSource() {
    override fun data(s: Snapshot) = SlotCodec.extras(s.tides, DateOrder.dayFirst(this), s.season, s.moonAge)
}
