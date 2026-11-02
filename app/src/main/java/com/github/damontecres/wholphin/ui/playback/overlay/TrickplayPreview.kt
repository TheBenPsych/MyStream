package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.formatDuration
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration.Companion.seconds

@Composable
fun TrickplayPreview(
    seekProgressMs: Long,
    trickplayInfo: TrickplayInfo?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (trickplayInfo != null) {
            // Benflix scene strip (Look Book pick P3): the scene you're jumping to in the middle, with
            // smaller, dimmed scenes from just before and just after it on each side
            val wide = LocalConfiguration.current.screenWidthDp >= STRIP_WIDE_SCREEN_DP
            val sideCount = if (wide) 2 else 1
            val stepMs = trickplayInfo.interval.toLong().coerceAtLeast(MIN_STEP_MS)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (offset in -sideCount..sideCount) {
                    val sceneMs = seekProgressMs + offset * stepMs
                    val centre = offset == 0
                    SceneTile(
                        sceneMs = sceneMs,
                        trickplayInfo = trickplayInfo,
                        trickplayUrlFor = trickplayUrlFor,
                        height = if (centre) 160.dp else SIDE_HEIGHT,
                        // keep the strip the same width at the start of the video, so the middle scene stays put
                        hidden = sceneMs < 0,
                        modifier = if (centre) Modifier else Modifier.alpha(SIDE_ALPHA),
                    )
                }
            }
        }
        val resources = LocalResources.current
        val seekProgressSec = seekProgressMs / 1000L
        val seekText = remember(seekProgressSec) { resources.formatDuration(seekProgressSec.seconds) }
        Text(
            text = seekText,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier =
                Modifier
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** One scene picture from the trickplay grid, or an empty space of the same size */
@Composable
private fun SceneTile(
    sceneMs: Long,
    trickplayInfo: TrickplayInfo,
    trickplayUrlFor: (Int) -> String?,
    height: Dp,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
    val index = (sceneMs.coerceAtLeast(0) / trickplayInfo.interval).toInt() / tilesPerImage
    val imageUrl = remember(index) { trickplayUrlFor(index) }
    if (hidden || imageUrl == null) {
        Spacer(Modifier.size(height * (trickplayInfo.width.toFloat() / trickplayInfo.height), height))
    } else {
        SeekPreviewImage(
            modifier = modifier,
            previewImageUrl = imageUrl,
            seekProgressMs = sceneMs,
            trickPlayInfo = trickplayInfo,
            height = height,
        )
    }
}

/** How tall the smaller scenes on each side are */
private val SIDE_HEIGHT = 72.dp

/** The side scenes are dimmed so the middle one stands out */
private const val SIDE_ALPHA = 0.6f

/** Screens at least this wide show two scenes on each side, smaller ones show one */
private const val STRIP_WIDE_SCREEN_DP = 800

/** The side scenes are at least this far apart (the same as one skip) */
private const val MIN_STEP_MS = 10_000L
