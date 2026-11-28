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
    /** The ring round the button the remote is on, and the soft ring round a lit card */
    val focus: Color,
    /** The highlight colour (progress lines, the Play button, ticks) with [onAccent] words on it */
    val accent: Color,
    val onAccent: Color,
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
    /** The soft glow outside a lit card (off = just the outline) */
    val halo: Boolean = true,
    /** A thin frame round every card while the remote is somewhere else (none on Glowfin) */
    val frame: Color? = null,
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
        accent = Color(0xFF3FE0E8),
        onAccent = Color(0xFF0B0D1A),
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
        accent = Color(0xFFFFC24A),
        onAccent = Color(0xFF140B12),
        onFocusFill = Color(0xFF2A1420),
        area = Color(0xFFFFB36B),
        onArea = Color(0xFF24120A),
    )

/**
 * Slate (Ben 2026-11-27, inspired by the ElegantFin Jellyfin theme): calm blue-grey, every card in a thin
 * frame, softer square-ish corners, one indigo colour and a plain white outline with no glow.
 */
val SlateLook =
    ThemeLook(
        ink = Color(0xFF141B29),
        deep = Color(0xFF1E2836),
        raise = Color(0xFF1F2937),
        line = Color(0xFF31373F),
        focusRow = Color(0xFF2A3545),
        text = Color(0xFFF3F4F6),
        soft = Color(0xFFD1D5DB),
        dim = Color(0xFF9CA3AF),
        glowA = Color(0xFF5D55E7),
        glowB = Color(0xFF5D55E7),
        glowC = Color(0xFF756FE2),
        fillMid = Color(0xFF5D55E7),
        focus = Color.White,
        accent = Color(0xFF756FE2),
        onAccent = Color.White,
        outline = Color.White,
        focusFill = Color.White,
        onFocusFill = Color(0xFF111827),
        area = Color(0xFF756FE2),
        onArea = Color.White,
        small = RoundedCornerShape(8.dp),
        card = RoundedCornerShape(16.dp),
        panel = RoundedCornerShape(16.dp),
        button = RoundedCornerShape(10.dp),
        halo = false,
        frame = Color(0xFF31373F),
    )

/** Classic (Ben 2026-11-27): the plain stock-Jellyfin look. Near-black, square corners, one blue. */
val ClassicLook =
    ThemeLook(
        ink = Color(0xFF101010),
        deep = Color(0xFF1C1C1C),
        raise = Color(0xFF202020),
        line = Color(0xFF2E2E2E),
        focusRow = Color(0xFF2A2A2A),
        text = Color(0xFFF2F2F2),
        soft = Color(0xFFD0D0D0),
        dim = Color(0xFFA0A0A0),
        glowA = Color(0xFF00A4DC),
        glowB = Color(0xFF00A4DC),
        glowC = Color(0xFF00A4DC),
        fillMid = Color(0xFF00A4DC),
        focus = Color.White,
        accent = Color(0xFF00A4DC),
        onAccent = Color.White,
        outline = Color(0xFF00A4DC),
        focusFill = Color(0xFF00A4DC),
        onFocusFill = Color.White,
        area = Color(0xFF00A4DC),
        onArea = Color.White,
        small = RoundedCornerShape(3.dp),
        card = RoundedCornerShape(3.dp),
        panel = RoundedCornerShape(4.dp),
        button = RoundedCornerShape(4.dp),
        halo = false,
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
        ThemeChoice(GlowfinLook.LOOK_SLATE, "slate", "Slate", "Calm blue-grey with framed cards and indigo", SlateLook),
        ThemeChoice(GlowfinLook.LOOK_CLASSIC, "classic", "Classic", "Plain and square, like stock Jellyfin", ClassicLook),
    )

/** The theme for a saved pick (anything this version doesn't know shows as Blue-purple) */
fun GlowfinLook.toChoice(): ThemeChoice = ThemeChoices.firstOrNull { it.choice == this } ?: ThemeChoices.first()

/** The theme in use. Changing [look] redraws everything that reads a colour or corner. */
object GlowfinTheme {
    var look by mutableStateOf(BluePurpleLook)
}
