package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf

/**
 * Benflix: only one pop-up at a time. Each pop-up that wants to show says so here with a priority,
 * and only the most important one shows. The next one waits until it's gone.
 */
object BenflixPopups {
    internal val waiting = mutableStateMapOf<String, Int>()

    const val BEDTIME = 100
    const val WIND_DOWN = 40
    const val UPDATE = 30
    const val TIP = 10
}

/** True when the pop-up [id] may show now. Pass [wants] = false when it has nothing to show. */
@Composable
fun popupTurn(
    id: String,
    priority: Int,
    wants: Boolean,
): Boolean {
    DisposableEffect(id, wants, priority) {
        if (wants) BenflixPopups.waiting[id] = priority
        onDispose { BenflixPopups.waiting.remove(id) }
    }
    if (!wants) return false
    val top =
        BenflixPopups.waiting.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
    return top?.key == id
}
