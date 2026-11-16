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
    val Tray: Color get() = KidsLook.palette?.tray ?: Color(0xFF0B0C10)

    /** Main words */
    val Text: Color get() = KidsLook.palette?.text ?: Color(0xFFF1F2F5)

    /** Softer words, like the clock */
    val Soft: Color get() = KidsLook.palette?.soft ?: Color(0xFFC9CBD2)

    /** Quiet words: hints, site names, small labels */
    val Muted: Color get() = KidsLook.palette?.muted ?: Color(0xFF9497A3)

    /** Pills, chips and small raised things */
    val Raised: Color get() = KidsLook.palette?.raised ?: Color(0xFF15171D)

    /** Menus and panels */
    val Panel: Color get() = KidsLook.palette?.panel ?: Color(0xFF16181E)

    /** The lighter side of a panel (profile card picture side) */
    val PanelHigh: Color get() = KidsLook.palette?.panelHigh ?: Color(0xFF1D2027)

    /** The "you are here" ring around whatever the remote is on: white, or sunny yellow on Kids */
    val Focus: Color get() = KidsLook.palette?.accent ?: Color(0xFFF1F2F5)

    /** A button the remote is on fills with this: white, or sunny yellow on Kids */
    val FocusFill: Color get() = KidsLook.palette?.accent ?: Color.White

    /** Words on [FocusFill] */
    val OnFocusFill: Color get() = KidsLook.palette?.onAccent ?: Color(0xFF0B0C10)

    /** Thin lines and panel edges */
    val Line: Color get() = KidsLook.palette?.line ?: Color(0xFF2A2D35)

    /** The focused row in a list or menu */
    val FocusRow: Color get() = KidsLook.palette?.focusRow ?: Color(0xFF262932)

    /** Dims the page behind a menu (no blur, so it stays quick on older TVs) */
    val Dim = Color(0xB8050608)

    /** Not on Benflix (Ask for it) */
    val NotHere = Color(0xFF858896)

    /** Behind the scenes warning */
    val Warning = Color(0xFFE8D44D)

    val Ok = Color(0xFF5BD18B)
    val Check = Color(0xFFE8D44D)
    val Problem = Color(0xFFFF7A7A)

    /** "Couldn't find it" on Requests */
    val CantFind = Color(0xFFFF8A8A)

    val Movies: Color get() = KidsLook.palette?.movies ?: Color(0xFFFFB547)
    val OnMovies: Color get() = KidsLook.palette?.onMovies ?: Color(0xFF211500)
    val Shows: Color get() = KidsLook.palette?.shows ?: Color(0xFF7AA7FF)
    val OnShows: Color get() = KidsLook.palette?.onShows ?: Color(0xFF0F1521)
    val LiveTv = Color(0xFF3DD6C6)
    val OnLiveTv = Color(0xFF081B19)
    val Games: Color get() = KidsLook.palette?.games ?: Color(0xFFFF6F91)
    val OnGames: Color get() = KidsLook.palette?.onGames ?: Color(0xFF210E12)

    /** Music, the fifth door: soft violet with dark words */
    val Music = Color(0xFFB69CFF)
    val OnMusic = Color(0xFF160F26)

    /** Books, the sixth door: lime with dark words */
    val Books = Color(0xFFB9E36A)
    val OnBooks = Color(0xFF141C05)
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
            }

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
            }

    companion object {
        /** The doors on Home: Kids profiles get five (Books where Music would be; no music on Kids) */
        fun forHome(kids: Boolean): List<BenflixDoor> = if (kids) entries.filter { it != MUSIC } else entries
    }
}
