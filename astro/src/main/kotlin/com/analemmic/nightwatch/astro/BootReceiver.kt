package com.analemmic.nightwatch.astro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * After a reboot (or an app update) ask the system to re-request every source right away, instead of
 * leaving the face blank until each source's next 15-minute period. Shipped in release builds.
 * Also on a language change, so the face's date order (slot 7, DateOrder) follows at once, and on a time zone
 * change, which can move the backup location (TzCenters).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "${intent.action}: refreshing all sources")
        Refresh.all(context)
    }
}
