package com.analemmic.nightwatch.astro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DEBUG BUILDS ONLY (src/debug): force a refresh, optionally pretending to be somewhere/somewhen.
 *   adb shell am broadcast -n com.analemmic.nightwatch.astro/.RefreshReceiver
 *   simulate: --es lat 69.65 --es lon 18.96 --es at '2026-06-21T13:00+02:00[Europe/Oslo]'   back to real: --ez clear true
 * Not shipped in release: an exported receiver that sets a fake location has no place in a public app.
 */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "refresh requested")
        if (intent.getBooleanExtra("clear", false)) Simulation.clear(context)
        val lat = intent.getStringExtra("lat")
        val lon = intent.getStringExtra("lon")
        if (lat != null && lon != null) Simulation.set(context, lat, lon, intent.getStringExtra("at"))
        Log.i(TAG, "simulation: ${Simulation.get(context)}")
        Refresh.all(context)
    }
}
