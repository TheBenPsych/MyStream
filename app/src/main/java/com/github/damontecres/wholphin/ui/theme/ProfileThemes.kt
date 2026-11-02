package com.github.damontecres.wholphin.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.tv.material3.ColorScheme
import com.github.damontecres.wholphin.preferences.BenflixAccent
import java.util.UUID

/**
 * Benflix profile themes (Look Book pick C1 = full theme). Each person's colour is used for
 * buttons, progress bars and highlights, and also softly tints the page background and panels.
 * Benflix red keeps the classic red and black look, Mono is all greys.
 *
 * The order here is the order shown in Settings and in the welcome setup.
 */
val ProfileThemeChoices =
    listOf(
        BenflixAccent.ACCENT_RED,
        BenflixAccent.ACCENT_BLUE,
        BenflixAccent.ACCENT_GREEN,
        BenflixAccent.ACCENT_ORANGE,
        BenflixAccent.ACCENT_PURPLE,
        BenflixAccent.ACCENT_PINK,
        BenflixAccent.ACCENT_GOLD,
        BenflixAccent.ACCENT_MONO,
    )

/** Where a colour sits in [ProfileThemeChoices] (Teal from older versions shows as Ocean) */
fun BenflixAccent.themeIndex(): Int =
    ProfileThemeChoices.indexOf(this).takeIf { it >= 0 }
        ?: if (this == BenflixAccent.ACCENT_TEAL) 1 else 0

/** The page background and panels, softly tinted in the profile's colour */
fun ColorScheme.withProfileTheme(accent: BenflixAccent): ColorScheme {
    val color = accent.toColor()
    val tint = accent.tints()
    return copy(
        primary = color,
        onPrimary = if (accent == BenflixAccent.ACCENT_GOLD) Color.Black else Color.White,
        background = if (tint) lerp(background, color, BACKGROUND_TINT) else background,
        surface = if (tint) lerp(surface, color, SURFACE_TINT) else surface,
        surfaceVariant = if (tint) lerp(surfaceVariant, color, SURFACE_TINT) else surfaceVariant,
    )
}

/** The same profile theme for the phone (Material 3) colours */
fun androidx.compose.material3.ColorScheme.withProfileTheme(accent: BenflixAccent): androidx.compose.material3.ColorScheme {
    val color = accent.toColor()
    val tint = accent.tints()
    return copy(
        primary = color,
        onPrimary = if (accent == BenflixAccent.ACCENT_GOLD) Color.Black else Color.White,
        background = if (tint) lerp(background, color, BACKGROUND_TINT) else background,
        surface = if (tint) lerp(surface, color, SURFACE_TINT) else surface,
        surfaceVariant = if (tint) lerp(surfaceVariant, color, SURFACE_TINT) else surfaceVariant,
    )
}

/** Benflix red keeps the classic black look and Mono is all greys, so only the others tint the background */
private fun BenflixAccent.tints(): Boolean =
    this != BenflixAccent.ACCENT_RED && this != BenflixAccent.ACCENT_MONO && this != BenflixAccent.UNRECOGNIZED

private const val BACKGROUND_TINT = 0.06f
private const val SURFACE_TINT = 0.09f

/**
 * Remembers each person's colour on this device, so the Who's watching tiles can glow in
 * everyone's own colour (not just the person who is signed in right now).
 */
object ProfileColours {
    private val colours = mutableStateMapOf<UUID, BenflixAccent>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("benflix_profile_colours", Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            val id = runCatching { UUID.fromString(key) }.getOrNull()
            val accent = (value as? Int)?.let { BenflixAccent.forNumber(it) }
            if (id != null && accent != null) colours[id] = accent
        }
    }

    fun get(id: UUID): BenflixAccent? = colours[id]

    fun set(
        context: Context,
        id: UUID,
        accent: BenflixAccent,
    ) {
        if (accent == BenflixAccent.UNRECOGNIZED) return
        init(context)
        if (colours[id] == accent) return
        colours[id] = accent
        prefs?.edit()?.putInt(id.toString(), accent.number)?.apply()
    }
}
