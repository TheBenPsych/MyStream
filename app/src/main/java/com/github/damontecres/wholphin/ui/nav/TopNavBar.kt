package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.services.KidsMode
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/**
 * One entry in the [TopNavBar]
 */
data class TopNavTab(
    val title: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** The Jellyfin library behind this tab, if it is one */
    val libraryId: UUID? = null,
    /** Browse card picture and how many titles the library has */
    val imageUrl: String? = null,
    val count: Int? = null,
    /** More menu: the short grey line under the name, and its icon */
    val hint: String? = null,
    val glyph: String? = null,
    /** More menu: My Benflix, shown with your profile picture */
    val isProfile: Boolean = false,
    /** Benflix: a small red number, like requests waiting for Ben */
    val badge: Int? = null,
)

/** Height of the top line of the bar (logo and buttons) */
val TopNavBarHeight = 44.dp

/**
 * On door pages (TV) the picture goes up behind the top bar; this is how tall the bar is there, so the
 * page can start its words and rows below it. 0 everywhere else.
 */
val LocalTopBarOverlap = staticCompositionLocalOf { 0.dp }

/**
 * Where Down from the top bar goes on pages drawn under it. Compose's own guess sometimes finds nothing below
 * the bar's buttons (Ben: stuck in the top bar on a Daily mix), so a page can name its first button here.
 */
class TopBarDown {
    var target: androidx.compose.ui.focus.FocusRequester? = null
}

val LocalTopBarDown = staticCompositionLocalOf { TopBarDown() }

/** A page's way to say "Down from the top bar lands here" (while it's on screen) */
@androidx.compose.runtime.Composable
fun RegisterTopBarDown(target: androidx.compose.ui.focus.FocusRequester) {
    val holder = LocalTopBarDown.current
    androidx.compose.runtime.DisposableEffect(holder, target) {
        holder.target = target
        onDispose { if (holder.target === target) holder.target = null }
    }
}

/** Door pages draw their picture up behind the top bar (TV) */
fun Destination.underTopBar(): Boolean =
    this is Destination.MoviesDoor ||
        this is Destination.ShowsDoor ||
        this is Destination.MusicDoor ||
        isMusicHero()

/** Album, artist and playlist pages: the cover sits top right, up behind the bar (like a movie page) */
fun Destination.isMusicHero(): Boolean =
    (
        this is Destination.MediaItem &&
            (type == BaseItemKind.MUSIC_ALBUM || type == BaseItemKind.MUSIC_ARTIST || type == BaseItemKind.PLAYLIST)
    ) ||
        (this is Destination.MusicList && (kind == "liked" || kind == "mix" || kind == "songs"))

/** The Benflix logo (redesign): four soft door-colour squares and "Benflix". Kids get a small "kids" word after it. */
@Composable
fun BenflixLogo(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 26.dp,
    markModifier: Modifier = Modifier,
) {
    val kids by KidsMode.active.collectAsState()
    com.github.damontecres.wholphin.ui.components.BenflixWordLogo(
        height = height,
        extra = if (kids) "kids" else null,
        markModifier = markModifier,
        modifier = modifier,
    )
}
