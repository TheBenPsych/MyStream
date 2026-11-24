package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import java.util.UUID

/**
 * Glowfin details pages (Ben's "fresh" pick, item 12): "Use the name instead" in More. The titles picked here
 * show their name as text on their page instead of the logo art. Kept on this device.
 */
object LogoChoice {
    private const val PREFS = "glowfin_logo_choice"
    private const val KEY = "name_instead"

    private var loaded = false
    var nameInstead by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        nameInstead = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
    }

    @Composable
    fun usesName(id: UUID?): Boolean {
        load(LocalContext.current.applicationContext)
        return id != null && id.toString() in nameInstead
    }

    fun set(
        context: Context,
        id: UUID,
        useName: Boolean,
    ) {
        load(context.applicationContext)
        nameInstead = if (useName) nameInstead + id.toString() else nameInstead - id.toString()
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY, nameInstead) }
    }
}
