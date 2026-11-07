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

/** Door pages draw their picture up behind the top bar (TV) */
fun Destination.underTopBar(): Boolean = this is Destination.MoviesDoor || this is Destination.ShowsDoor

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
