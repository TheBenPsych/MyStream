package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Benflix look (redesign, Nov 2026): one calm dark tray for everyone, white words, neutral greys,
 * and four bright door colours. A door's colour only shows "about that door" (colour = place):
 * its tile on Home, the word beside the logo inside it, and its active filter chips.
 * On Kids most of these turn into the Kids look's colours ([KidsLook]).
 */
object BenflixColors {
    /** The dark background everything sits on */
    val Tray: Color get() = KidsLook.palette?.tray ?: Glowfin.Ink

    /** Main words */
    val Text: Color get() = KidsLook.palette?.text ?: Glowfin.Text

    /** Softer words, like the clock */
    val Soft: Color get() = KidsLook.palette?.soft ?: GlowfinTheme.look.soft

    /** Quiet words: hints, site names, small labels */
    val Muted: Color get() = KidsLook.palette?.muted ?: Glowfin.Dim

    /** Pills, chips and small raised things */
    val Raised: Color get() = KidsLook.palette?.raised ?: Glowfin.Raise

    /** Menus and panels */
    val Panel: Color get() = KidsLook.palette?.panel ?: Glowfin.Deep

    /** The lighter side of a panel (profile card picture side) */
    val PanelHigh: Color get() = KidsLook.palette?.panelHigh ?: Glowfin.Raise

    /** The "you are here" ring around whatever the remote is on: Glowfin aqua, or sunny yellow on Kids */
    val Focus: Color get() = KidsLook.palette?.accent ?: GlowfinTheme.look.focus

    /**
     * A button the remote is on fills with this. Glowfin new look "One focus look everywhere" (Ben 2026-11-22):
     * solid white with the aqua ring ([Focus]), or sunny yellow on Kids
     */
    val FocusFill: Color get() = KidsLook.palette?.accent ?: GlowfinTheme.look.focusFill

    /** Words on [FocusFill] */
    val OnFocusFill: Color get() = KidsLook.palette?.onAccent ?: GlowfinTheme.look.onFocusFill

    /** The outline round a lit poster or card: white (new look), or the Kids colour on Kids */
    val Outline: Color get() = KidsLook.palette?.accent ?: GlowfinTheme.look.outline

    /** Thin lines and panel edges */
    val Line: Color get() = KidsLook.palette?.line ?: Glowfin.Line

    /** The focused row in a list or menu */
    val FocusRow: Color get() = KidsLook.palette?.focusRow ?: Glowfin.FocusRow

    /** Dims the page behind a menu (no blur, so it stays quick on older TVs) */
    val Dim = Color(0xA80B0D1A)

    /** Not on Benflix (Ask for it) */
    val NotHere = Color(0xFF858896)

    /** Behind the scenes warning */
    val Warning = Color(0xFFE8D44D)

    val Ok = Color(0xFF5BD18B)
    val Check = Color(0xFFE8D44D)
    val Problem = Color(0xFFFF7A7A)

    /** "Couldn't find it" on Requests */
    val CantFind = Color(0xFFFF8A8A)

    // Glowfin (Ben 2026-11-18): every area uses the same blue that Shows always had. The old Benflix colour per
    // area (orange Movies, pink Games, aqua Live TV and Books, violet Music) is gone. Kids keeps its own colours.
    private val Area: Color get() = GlowfinTheme.look.area
    private val OnArea: Color get() = GlowfinTheme.look.onArea

    val Movies: Color get() = KidsLook.palette?.movies ?: Area
    val OnMovies: Color get() = KidsLook.palette?.onMovies ?: OnArea
    val Shows: Color get() = KidsLook.palette?.shows ?: Area
    val OnShows: Color get() = KidsLook.palette?.onShows ?: OnArea
    val LiveTv: Color get() = Area
    val OnLiveTv: Color get() = OnArea
    val Games: Color get() = KidsLook.palette?.games ?: Area
    val OnGames: Color get() = KidsLook.palette?.onGames ?: OnArea

    /** Music: the same Glowfin area blue, with dark words on it */
    val Music: Color get() = Area
    val OnMusic: Color get() = OnArea

    /** Books: the same Glowfin area blue, with dark words on it */
    val Books: Color get() = Area
    val OnBooks: Color get() = OnArea
}

/** The Home doors, in their fixed order (Music is the fifth) */
enum class BenflixDoor(
    val title: String,
    val description: String,
) {
    MOVIES("Movies", "Films for any night"),
    SHOWS("Shows", "Series and episodes"),
    LIVE_TV("Live TV", "Channels playing now"),
    GAMES("Games", "Play on the TV"),
    MUSIC("Music", "Your songs and playlists"),

    /** Ben: the Books door always says "Read and listen" */
    BOOKS("Books", "Read and listen"),

    /** Glowfin (ui-7, Ben's pick A): anime shows and movies in one place, under Shows in the menu */
    ANIME("Anime", "Anime shows and movies"),
    ;

    /** The door's colour (Movies, Shows and Games change on Kids, see [KidsLook]) */
    val color: Color
        get() =
            when (this) {
                MOVIES -> BenflixColors.Movies
                SHOWS -> BenflixColors.Shows
                LIVE_TV -> BenflixColors.LiveTv
                GAMES -> BenflixColors.Games
                MUSIC -> BenflixColors.Music
                BOOKS -> BenflixColors.Books
                ANIME -> BenflixColors.Shows
            }

    /** The words on the door itself */
    val labelColor: Color
        get() = onColor

    /** Words on top of [color] */
    val onColor: Color
        get() =
            when (this) {
                MOVIES -> BenflixColors.OnMovies
                SHOWS -> BenflixColors.OnShows
                LIVE_TV -> BenflixColors.OnLiveTv
                GAMES -> BenflixColors.OnGames
                MUSIC -> BenflixColors.OnMusic
                BOOKS -> BenflixColors.OnBooks
                ANIME -> BenflixColors.OnShows
            }

    companion object {
        /** The doors on Home: Kids profiles get five (Books where Music would be; no music on Kids) */
        fun forHome(kids: Boolean): List<BenflixDoor> = if (kids) entries.filter { it != MUSIC } else entries
    }
}
