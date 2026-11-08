package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import org.jellyfin.sdk.model.api.ImageType

/** True when the episode has its own picture on the server */
fun BaseItem.hasOwnPicture(): Boolean = data.imageTags?.get(ImageType.PRIMARY) != null

/**
 * Approved polish idea "nicer stand-in when an episode has no picture": instead of a grey box, the
 * show's backdrop, darkened, with a big episode number on it in the Shows blue (often anime and
 * older shows have no episode pictures).
 */
@Composable
fun EpisodeStandIn(
    episode: BaseItem,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val url =
        remember(episode.id) {
            imageUrlService.getItemImageUrl(
                episode,
                ImageType.BACKDROP,
                fillWidth = with(density) { width.roundToPx() },
            )
        }
    val number =
        remember(episode.id) {
            episode.data.indexNumber?.let { "E$it" } ?: if (episode.data.parentIndexNumber == 0) "SP" else "?"
        }
    Box(modifier.fillMaxSize().background(BenflixColors.Raised), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(BenflixColors.Tray.copy(alpha = .62f)))
        }
        Text(
            text = number,
            color = BenflixColors.Shows,
            fontFamily = BenflixFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (width.value * .2f).coerceIn(18f, 56f).sp,
        )
    }
}
