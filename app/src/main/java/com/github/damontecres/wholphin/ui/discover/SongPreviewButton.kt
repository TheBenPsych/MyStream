package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.SongPreviewState
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** The words for a song's preview button: "Hear a preview", "Starting…", "Stop preview" or "No preview" */
fun previewLabel(
    state: SongPreviewState,
    songId: Long,
): String =
    when {
        state.missingId == songId -> "No preview for this one"
        state.songId == songId && state.loading -> "Starting the preview…"
        state.songId == songId -> "Stop the preview"
        else -> "Hear 30 seconds"
    }

/**
 * Benflix: the round play button beside "Ask for it" on music rows. Plays a 30 second preview of the song;
 * it turns into a stop square while playing.
 */
@Composable
fun SongPreviewButton(
    state: SongPreviewState,
    songId: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 44,
) {
    val playing = state.songId == songId
    val missing = state.missingId == songId
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (playing) BenflixColors.Text else BenflixColors.Raised,
                contentColor = if (playing) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        border = doorFocusBorder(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.size(size.dp),
    ) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            when {
                missing -> Text("–", fontSize = BenflixType.body, color = BenflixColors.Muted)
                playing && state.loading -> Text("…", fontSize = BenflixType.body)
                else ->
                    BenflixIconImage(
                        icon = if (playing) BenflixIcon.STOP else BenflixIcon.PLAY,
                        color = androidx.tv.material3.LocalContentColor.current,
                        size = (size * 0.45f).dp,
                        contentDescription = if (playing) "Stop the preview" else "Hear 30 seconds",
                    )
            }
        }
    }
}

/** The song preview player for the music rows on this screen (null where there are no previews) */
val LocalSongPreview = androidx.compose.runtime.staticCompositionLocalOf<com.github.damontecres.wholphin.services.SongPreview?> { null }

/**
 * Gives the music rows inside [content] their preview player, and stops any preview when the screen goes away
 * or the app goes to the background.
 */
@Composable
fun ProvideSongPreview(
    preview: com.github.damontecres.wholphin.services.SongPreview,
    content: @Composable () -> Unit,
) {
    androidx.lifecycle.compose.LifecycleResumeEffect(preview) {
        onPauseOrDispose { preview.stop() }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSongPreview provides preview, content = content)
}
