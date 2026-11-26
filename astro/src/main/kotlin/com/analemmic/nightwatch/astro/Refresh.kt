package com.analemmic.nightwatch.astro

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/** Asks the system to re-request every source now (after a permission change, a new location, or a debug override). */
object Refresh {
    val SOURCES = listOf(
        SunSource::class, CivilSource::class, NauticalSource::class, AstroSource::class,
        MoonSource::class, TideSource::class, ExtrasSource::class,
    )

    fun all(context: Context) {
        SnapshotCache.invalidate()
        for (cls in SOURCES) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, cls.java)).requestUpdateAll()
        }
    }
}
