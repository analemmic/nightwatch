package com.analemmic.nightwatch.astro

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The companion's only screen. Explains what the app does, asks for location (coarse; then optionally
 * "Allow all the time" so the face's data can follow you when you travel), saves a fix for the data sources,
 * and shows copyright + third-party licenses. Plain views, no extra dependencies; padded for a round screen.
 */
class SetupActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var column: LinearLayout
    private var showingLicenses = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val side = dp(22); val top = dp(30)
            setPadding(side, top, side, top)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
        if (hasForeground()) saveLocationAndRefresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Activity back handling")
    override fun onBackPressed() {
        if (showingLicenses) { showingLicenses = false; render() } else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun hasForeground() = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun hasBackground() = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Take a fix now (the app is in the foreground, so foreground permission is enough) and push it to the face. */
    private fun saveLocationAndRefresh() = scope.launch {
        val loc = LocationSource.get(this@SetupActivity)
        if (loc != null) Refresh.all(this@SetupActivity)
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
        if (hasForeground()) saveLocationAndRefresh()
    }

    private fun render() {
        column.removeAllViews()
        if (showingLicenses) return renderLicenses()

        text(getString(R.string.app_name), 17f, bold = true, color = RED)
        text(getString(R.string.setup_what), 13f)
        text(getString(R.string.setup_privacy), 12f, color = DIM)

        val status = when {
            !hasForeground() -> getString(R.string.setup_status_none)
            !hasBackground() -> getString(R.string.setup_status_foreground)
            else -> getString(R.string.setup_status_all)
        }
        text(status, 13f, bold = true)

        when {
            !hasForeground() -> button(getString(R.string.setup_allow)) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_FOREGROUND)
            }
            // Android 11+: background location can't be granted from a dialog; this opens the app's location
            // permission page, where the user picks "Allow all the time".
            !hasBackground() -> button(getString(R.string.setup_allow_always)) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND)
            }
        }

        text(getString(R.string.setup_face_hint), 12f, color = DIM)
        text(getString(R.string.setup_not_for_navigation), 12f, color = DIM)
        button(getString(R.string.setup_licenses)) { showingLicenses = true; render() }
        text(getString(R.string.copyright), 11f, color = DIM)
    }

    private fun renderLicenses() {
        val body = resources.openRawResource(R.raw.licenses).bufferedReader().use { it.readText() }
        button(getString(R.string.setup_back)) { showingLicenses = false; render() }
        text(body, 10f, color = DIM, mono = true, center = false)
    }

    private fun text(s: String, sp: Float, bold: Boolean = false, color: Int = INK, mono: Boolean = false,
                     center: Boolean = true) {
        column.addView(TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            gravity = if (center) Gravity.CENTER_HORIZONTAL else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        })
    }

    private fun button(label: String, onClick: (View) -> Unit) {
        column.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener(onClick)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(6), 0, dp(6)) })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_FOREGROUND = 1
        const val REQ_BACKGROUND = 2
        val RED = Color.parseColor("#e0574d")
        val INK = Color.parseColor("#e9dcd6")
        val DIM = Color.parseColor("#9a817b")
    }
}
