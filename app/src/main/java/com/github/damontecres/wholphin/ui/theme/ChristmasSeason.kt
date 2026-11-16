package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.Month

/**
 * Benflix Christmas look: on from 1 December to 1 January on TV, phone and web, unless it's switched off
 * in Settings > Look. The same dates as the web's xmas.js, so every screen matches.
 */
object ChristmasSeason {
    /** Settings > Look > Christmas look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isSeason(date: LocalDate = LocalDate.now()): Boolean =
        date.month == Month.DECEMBER || (date.month == Month.JANUARY && date.dayOfMonth == 1)

    fun on(date: LocalDate = LocalDate.now()): Boolean = !lookOff && isSeason(date)

    /**
     * Which recording of the room plays: presents pile up through December (L1, L2, L3), Santa's sleigh
     * flies past on Christmas Eve, and on Christmas Day the presents are opened.
     */
    fun variant(date: LocalDate = LocalDate.now()): String {
        if (date.month != Month.DECEMBER) return "AFTER"
        val d = date.dayOfMonth
        return when {
            d <= 8 -> "L1"
            d <= 16 -> "L2"
            d <= 23 -> "L3"
            d == 24 -> "EVE"
            d == 25 -> "DAY"
            else -> "AFTER"
        }
    }

    fun greeting(date: LocalDate = LocalDate.now()): String =
        when {
            date.month == Month.JANUARY -> "Happy New Year"
            date.dayOfMonth == 31 -> "Happy New Year's Eve"
            date.dayOfMonth == 24 -> "Merry Christmas Eve"
            else -> "Merry Christmas"
        }

    /** "12 sleeps to Christmas", up to Christmas Eve */
    fun countdown(date: LocalDate = LocalDate.now()): String? {
        if (date.month != Month.DECEMBER || date.dayOfMonth > 24) return null
        // Sleeps left: on 23 December it's one more night's sleep, then Santa comes
        return when (val sleeps = 24 - date.dayOfMonth) {
            0 -> "Santa comes tonight!"
            1 -> "1 sleep to Christmas"
            else -> "$sleeps sleeps to Christmas"
        }
    }
}
