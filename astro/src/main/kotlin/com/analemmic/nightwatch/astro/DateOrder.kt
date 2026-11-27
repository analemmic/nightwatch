package com.analemmic.nightwatch.astro

import android.content.Context
import android.text.format.DateFormat

/**
 * The user's day/month order (owner 2026-11-24: honor the DD.MM / MM.DD preference, not a locale list).
 * DateFormat.getDateFormatOrder uses an explicit date-format setting if there is one, else the locale's convention
 * (US, Japan, China, Korea: month first; most others: day first). Feeds the scrubber and, via slot 5, the face.
 */
object DateOrder {
    fun dayFirst(context: Context): Boolean {
        val order = DateFormat.getDateFormatOrder(context)
        return order.indexOf('d') < order.indexOf('M')
    }
}
