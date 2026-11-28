package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.preferences.GlowfinLook

/**
 * One theme = one of these (Ben 2026-11-27, "Themes"). It holds every colour and corner the app's pieces pick
 * from, so a new theme is one more [ThemeLook] and nothing else. Screens never read it directly: they keep
 * using [Glowfin], [BenflixColors] and [BenflixShapes], which hand out whatever the theme in use says.
 * Themes restyle pieces only; they never move things around a page. Kids always wins ([KidsLook]).
 */
@Immutable
data class ThemeLook(
    /** The background everything sits on */
    val ink: Color,
    /** Menus, panels and the open side menu */
    val deep: Color,
    /** Raised things: chips, cards without a picture, sheets */
    val raise: Color,
    /** Thin lines and panel edges */
    val line: Color,
    /** The row the remote is on in a list */
    val focusRow: Color,
    /** Main words, softer words, quiet words */
    val text: Color,
    val soft: Color,
    val dim: Color,
    /** The theme's three glow colours, start to end (a flat theme uses one colour three times) */
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    /** One colour for places that can't take the glow (filled buttons) */
    val fillMid: Color,
    /** The soft ring round whatever the remote is on, and the highlight colour (progress lines, Play) */
    val focus: Color,
    /** The outline round a lit poster or card */
    val outline: Color,
    /** A lit button fills with this, with [onFocusFill] words on it */
    val focusFill: Color,
    val onFocusFill: Color,
    /** The colour "about a place" (picked filter chips, door words) with [onArea] words on it */
    val area: Color,
    val onArea: Color,
    /** Corners: tags and chips, posters and cards, menus and panels, buttons */
    val small: RoundedCornerShape,
    val card: RoundedCornerShape,
    val panel: RoundedCornerShape,
    val button: RoundedCornerShape,
)

/** Glowfin as it has always looked: night blue with the logo's violet > blue > aqua glow */
val BluePurpleLook =
    ThemeLook(
        ink = Color(0xFF0B0D1A),
        deep = Color(0xFF12152A),
        raise = Color(0xFF1A1E38),
        line = Color(0xFF262A45),
        focusRow = Color(0xFF222645),
        text = Color(0xFFECEEFB),
        soft = Color(0xFFC6CBE8),
        dim = Color(0xFF9AA0C3),
        glowA = Color(0xFFA55BE0),
        glowB = Color(0xFF1FA8E6),
        glowC = Color(0xFF3FE0E8),
        fillMid = Color(0xFF6C6FE8),
        focus = Color(0xFF3FE0E8),
        outline = Color.White,
        focusFill = Color.White,
        onFocusFill = Color(0xFF1B1F3A),
        area = Color(0xFF7AA7FF),
        onArea = Color(0xFF0F1521),
        small = RoundedCornerShape(6.dp),
        card = RoundedCornerShape(10.dp),
        panel = RoundedCornerShape(16.dp),
        button = RoundedCornerShape(percent = 50),
    )

/** Sunset (Ben 2026-11-27): the same Glowfin shapes in rose > orange > gold on a warm dark background */
val SunsetLook =
    BluePurpleLook.copy(
        ink = Color(0xFF140B12),
        deep = Color(0xFF1F121B),
        raise = Color(0xFF2B1A25),
        line = Color(0xFF3B2733),
        focusRow = Color(0xFF38222F),
        text = Color(0xFFFBEFF2),
        soft = Color(0xFFE3CBD3),
        dim = Color(0xFFC7AAB6),
        glowA = Color(0xFFF0567A),
        glowB = Color(0xFFFF8A3D),
        glowC = Color(0xFFFFC24A),
        fillMid = Color(0xFFF7743F),
        focus = Color(0xFFFFC24A),
        onFocusFill = Color(0xFF2A1420),
        area = Color(0xFFFFB36B),
        onArea = Color(0xFF24120A),
    )

/**
 * One line of the Theme page: the saved [choice], the short [key] the browser version shares, the [title]
 * and [line] people read, and the [look] itself. A new theme is one more line here.
 */
class ThemeChoice(
    val choice: GlowfinLook,
    val key: String,
    val title: String,
    val line: String,
    val look: ThemeLook,
)

val ThemeChoices =
    listOf(
        ThemeChoice(GlowfinLook.LOOK_BLUE_PURPLE, "glowfin", "Blue-purple", "Glowfin as you know it", BluePurpleLook),
        ThemeChoice(GlowfinLook.LOOK_SUNSET, "sunset", "Sunset", "Rose, orange and gold on a warm dark", SunsetLook),
    )

/** The theme for a saved pick (anything this version doesn't know shows as Blue-purple) */
fun GlowfinLook.toChoice(): ThemeChoice = ThemeChoices.firstOrNull { it.choice == this } ?: ThemeChoices.first()

/** The theme in use. Changing [look] redraws everything that reads a colour or corner. */
object GlowfinTheme {
    var look by mutableStateOf(BluePurpleLook)
}
