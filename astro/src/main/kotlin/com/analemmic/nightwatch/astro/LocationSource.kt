package com.analemmic.nightwatch.astro

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Coarse location for the astronomy.
 *
 * [get] answers instantly so a complication request is never left waiting on a fix: a recent last-known fix
 * (< 6 h), else the fix saved in prefs (survives reboots), else any last-known fix. Only with nothing at all
 * (first run) does it wait for a fresh fix. Whenever the answer wasn't recent, a fresh fix is fetched in the
 * background and, if it moved us noticeably, every source is refreshed.
 *
 * Why: after a reboot the system's last-known fix is empty; the old code then waited up to 10 s for a fresh
 * fix inside the request and the system abandoned it, leaving the face blank until the next period.
 */
object LocationSource {
    private const val PREFS = "location"
    private const val RECENT_MS = 6 * 3600_000L
    private const val MOVED_DEG = 0.05 // ~5 km: below this the sun/moon times change by well under a minute

    data class LatLon(val lat: Double, val lon: Double, val source: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val refreshing = AtomicBoolean(false)

    suspend fun get(context: Context): LatLon? {
        val app = context.applicationContext
        val last = safely { lastKnown(app) }
        if (last != null && System.currentTimeMillis() - last.time < RECENT_MS) return save(app, last)

        val answer = cached(app) ?: last?.let { save(app, it) }
        if (answer != null) {
            refreshInBackground(app, answer)
            return answer
        }
        // Nothing known yet (first run, or permission just granted): worth waiting for.
        return safely { fresh(app) }?.let { save(app, it) }
    }

    /** Fresh fix outside the request; push new data to the face only if we actually moved. */
    private fun refreshInBackground(context: Context, was: LatLon) {
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val loc = safely { fresh(context) } ?: return@launch
                save(context, loc)
                if (abs(loc.latitude - was.lat) > MOVED_DEG || abs(loc.longitude - was.lon) > MOVED_DEG) {
                    Log.i(TAG, "moved to ${loc.latitude},${loc.longitude}: refreshing")
                    Refresh.all(context)
                }
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun save(context: Context, loc: Location): LatLon {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("lat", loc.latitude.toString()).putString("lon", loc.longitude.toString()).apply()
        return LatLon(loc.latitude, loc.longitude, loc.provider ?: "?")
    }

    private fun cached(context: Context): LatLon? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        return LatLon(lat, lon, "cache")
    }

    private inline fun <T> safely(block: () -> T?): T? = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "no location permission: ${e.message}")
        null
    }

    private fun providers(lm: LocationManager): List<String> {
        val all = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return all.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        val provider = providers(lm).firstOrNull() ?: return null
        return withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> cont.resume(loc) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        return providers(lm).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }
}
