package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Benflix look (redesign, Nov 2026): one calm dark tray for everyone, white words, neutral greys,
 * and four bright door colours. A door's colour only shows "about that door" (colour = place):
 * its tile on Home, the word beside the logo inside it, and its active filter chips.
 */
object BenflixColors {
    /** The dark background everything sits on */
    val Tray = Color(0xFF0B0C10)

    /** Main words */
    val Text = Color(0xFFF1F2F5)

    /** Softer words, like the clock */
    val Soft = Color(0xFFC9CBD2)

    /** Quiet words: hints, site names, small labels */
    val Muted = Color(0xFF9497A3)

    /** Pills, chips and small raised things */
    val Raised = Color(0xFF15171D)

    /** Menus and panels */
    val Panel = Color(0xFF16181E)

    /** The lighter side of a panel (profile card picture side) */
    val PanelHigh = Color(0xFF1D2027)

    /** Thin lines and panel edges */
    val Line = Color(0xFF2A2D35)

    /** The focused row in a list or menu */
    val FocusRow = Color(0xFF262932)

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

    val Movies = Color(0xFFFFB547)
    val OnMovies = Color(0xFF211500)
    val Shows = Color(0xFF7AA7FF)
    val OnShows = Color(0xFF0F1521)
    val LiveTv = Color(0xFF3DD6C6)
    val OnLiveTv = Color(0xFF081B19)
    val Games = Color(0xFFFF6F91)
    val OnGames = Color(0xFF210E12)

    /** Music, the fifth door: soft violet with dark words */
    val Music = Color(0xFFB69CFF)
    val OnMusic = Color(0xFF160F26)
}

/** The Home doors, in their fixed order (Music is the fifth) */
enum class BenflixDoor(
    val title: String,
    val description: String,
    val color: Color,
    val onColor: Color,
) {
    MOVIES("Movies", "Films for any night", BenflixColors.Movies, BenflixColors.OnMovies),
    SHOWS("Shows", "Series and episodes", BenflixColors.Shows, BenflixColors.OnShows),
    LIVE_TV("Live TV", "Channels playing now", BenflixColors.LiveTv, BenflixColors.OnLiveTv),
    GAMES("Games", "Play on the TV", BenflixColors.Games, BenflixColors.OnGames),
    MUSIC("Music", "Your songs and playlists", BenflixColors.Music, BenflixColors.OnMusic),
    ;

    companion object {
        /** The doors on Home: Kids profiles keep the four (no music on Kids) */
        fun forHome(kids: Boolean): List<BenflixDoor> = if (kids) entries.take(4) else entries
    }
}
