package com.analemmic.nightwatch.astro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Day scrubber (owner 2026-11-24): tapping the face's centre opens this, showing today. The crown moves the date;
 * the sky, moon and tides redraw for that day. Tapping the centre opens that day's table (TableActivity); swipe
 * right exits back to the face. Each day is computed at the current time of day, so today looks like the face.
 */
class ScrubberActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var view: ScrubberView
    private val targets = Channel<LocalDate>(Channel.CONFLATED)

    private var zone: ZoneId = ZoneId.systemDefault()
    private var timeOfDay: LocalTime = LocalTime.now()
    private var where: Where? = null
    private var direction = 1
    private var today: LocalDate = LocalDate.now()

    /** Recently computed days (LRU), so scrubbing back and forth is instant. */
    private val cache = object : LinkedHashMap<LocalDate, Snapshot>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, Snapshot>) = size > 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sim = Simulation.get(this)
        val start = sim?.at ?: ZonedDateTime.now()
        zone = start.zone
        timeOfDay = start.toLocalTime()
        today = start.toLocalDate()
        view = ScrubberView(this).apply {
            date = start.toLocalDate()
            onScrub = { days ->
                direction = if (days > 0) 1 else -1
                var next = date.plusDays(days.toLong())
                // The today notch (owner 2026-11-24): reaching or passing today stops on it, with a haptic and a hold.
                // Either direction, landing exactly on today or jumping past it.
                val crossesToday = (date < today && next >= today) || (date > today && next <= today)
                if (crossesToday) { next = today; notch() }
                date = next
                targets.trySend(date)
            }
            onCenterTap = {
                openChild(Intent(this@ScrubberActivity, TableActivity::class.java)
                    .putExtra(TableActivity.EXTRA_AT, at(date).toString())
                    .putExtra(TableActivity.EXTRA_DAY, day))
            }
        }
        setContentView(view)
        view.requestFocus() // rotary events go to the focused view
        // First run (owner 2026-11-24: the launcher opens the scrubber): location isn't allowed yet, so show the
        // setup screen on top; we load in onResume when the user comes back.
        if (sim == null && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            openChild(Intent(this, SetupActivity::class.java))
        }
    }

    /** True while one of our own screens (table, setup) is on top: then stopping doesn't end the session. */
    private var childOpen = false

    private fun openChild(i: Intent) {
        childOpen = true
        startActivity(i)
    }

    override fun onRestart() {
        super.onRestart()
        childOpen = false
    }

    private var loading = false

    override fun onResume() {
        super.onResume()
        if (loading) return
        // Without location permission the sky comes from the time zone's city (TzCentres).
        loading = true
        scope.launch {
            // No location at all (no fix, zone without a city such as UTC): only the date shows; retried on resume.
            where = withContext(Dispatchers.IO) { Sky.where(this@ScrubberActivity) } ?: run { loading = false; return@launch }
            // Palette: today's sunrise/sunset at the current time, fixed for the session (not the scrubbed date).
            // The face's palette setting can force day or night (Palettes.mode).
            view.day = Palettes.isDay(Palettes.mode(this@ScrubberActivity, intent), snapshot(today), at(today))
            targets.trySend(view.date)
            worker()
        }
    }

    /** Leaving for the face / launcher (or the screen timing out) ends the session: the next open starts on today. */
    override fun onStop() {
        super.onStop()
        if (!childOpen && !isChangingConfigurations) finish()
    }

    private fun at(d: LocalDate): ZonedDateTime = d.atTime(timeOfDay).atZone(zone)

    private suspend fun snapshot(d: LocalDate): Snapshot =
        cache[d] ?: withContext(Dispatchers.Default) {
            val t0 = System.nanoTime()
            where!!.snapshot(at(d)).also {
                android.util.Log.d(TAG, "scrubber: $d computed in ${(System.nanoTime() - t0) / 1_000_000} ms")
            }
        }.also { cache[d] = it }

    /** Latest-wins: show the newest requested day; when idle, precompute a few days ahead in the scrub direction. */
    private suspend fun worker() {
        while (true) {
            val d = targets.receive()
            val s = snapshot(d)
            if (view.date == d) view.snapshot = s
            for (k in 1..3) {
                if (!targets.isEmpty) break
                snapshot(d.plusDays((k * direction).toLong()))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
