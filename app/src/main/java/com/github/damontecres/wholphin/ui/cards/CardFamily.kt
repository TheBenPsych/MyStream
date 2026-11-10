package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.BaseItemKind

/*
 * Benflix Redesign 2, rows, posters and covers (approved rc-family): every kind of card is one family.
 *  - Every card in a row is the same height and keeps its own shape (never cropped).
 *  - On TV, posters (movies, shows, books) have no name: it's already on the art. Covers, episodes,
 *    channels, people and games get a 2 line name under them.
 *  - On phones every card gets the name under it: 2 lines, then a small grey line.
 *  - No picture = a dark card with the big white name and a short line in the door's colour at the top.
 *  - Cards show dark straight away and their pictures fade in.
 */

/** How long a picture takes to fade in. Cheap enough for the old TCL TVs. */
const val CARD_FADE_MS = 200

/** The colour of the door a title belongs to (for the short line on a card with no picture) */
fun BaseItem?.doorColor(): Color =
    when (this?.type) {
        BaseItemKind.MOVIE, BaseItemKind.BOX_SET, BaseItemKind.TRAILER -> {
            BenflixColors.Movies
        }

        BaseItemKind.TV_CHANNEL, BaseItemKind.LIVE_TV_CHANNEL, BaseItemKind.PROGRAM, BaseItemKind.LIVE_TV_PROGRAM -> {
            BenflixColors.LiveTv
        }

        BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST, BaseItemKind.AUDIO, BaseItemKind.PLAYLIST, BaseItemKind.MUSIC_VIDEO -> {
            BenflixColors.Music
        }

        BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK -> {
            BenflixColors.Books
        }

        else -> {
            BenflixColors.Shows
        }
    }

/**
 * Wraps a picture so it fades in when it arrives. Pictures already in memory show at once (Coil skips the
 * fade for those), so scrolling back along a row stays instant.
 */
@Composable
fun rememberFadeIn(data: Any?): Any? {
    val context = LocalContext.current
    return remember(data) {
        when (data) {
            null -> {
                null
            }

            is ImageRequest -> {
                data.newBuilder().crossfade(CARD_FADE_MS).build()
            }

            else -> {
                ImageRequest
                    .Builder(context)
                    .data(data)
                    .crossfade(CARD_FADE_MS)
                    .build()
            }
        }
    }
}

/**
 * The card for a title with no picture (approved rc-p-noart): dark, the name in big white letters, and a
 * short line in the door's colour at the top.
 */
@Composable
fun NoPictureCard(
    name: String,
    door: Color,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    centred: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(BenflixColors.Raised),
    ) {
        Box(
            Modifier
                .align(if (centred) Alignment.TopCenter else Alignment.TopStart)
                .padding(start = if (centred) 0.dp else 10.dp, top = 10.dp)
                .width(if (small) 18.dp else 26.dp)
                .height(4.dp)
                .background(door, RoundedCornerShape(4.dp)),
        )
        Text(
            text = name,
            color = BenflixColors.Text,
            fontSize = if (small) BenflixType.small else BenflixType.lead,
            lineHeight = if (small) BenflixType.small * 1.15f else BenflixType.lead * 1.15f,
            fontWeight = FontWeight.ExtraBold,
            textAlign = if (centred) TextAlign.Center else TextAlign.Start,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(if (centred) Alignment.Center else Alignment.CenterStart)
                    .padding(horizontal = 10.dp, vertical = 18.dp),
        )
    }
}

/**
 * A card's picture: a dark card at once, the picture fading in over it, and the no-picture card if there
 * isn't one or it fails to load.
 */
@Composable
fun CardPicture(
    model: Any?,
    name: String,
    door: Color,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    small: Boolean = false,
    centred: Boolean = false,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    var failed by remember(model) { mutableStateOf(false) }
    val request = rememberFadeIn(model)
    Box(modifier.background(BenflixColors.Raised)) {
        if (request == null || failed) {
            NoPictureCard(name = name, door = door, small = small, centred = centred)
        } else {
            AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = contentScale,
                onError = { failed = true },
                onSuccess = onSuccess,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The name under a card (approved rc-p-phone): up to 2 lines, then a small grey line (year, artist, episode,
 * what's on). Long anime names get both lines instead of being cut.
 */
@Composable
fun CardNameUnder(
    name: String,
    line: String?,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    centred: Boolean = false,
    strip: Boolean = false,
) {
    Column(
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
        modifier =
            modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .padding(top = if (strip) 4.dp else 6.dp)
                .then(
                    if (strip) {
                        Modifier
                            .background(BenflixColors.Panel, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Text(
            text = name,
            color = BenflixColors.Text,
            fontSize = BenflixType.small,
            lineHeight = BenflixType.small * 1.2f,
            fontWeight = FontWeight.Bold,
            // Always 2 lines tall, so every card in a row lines up
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = line.orEmpty(),
            color = BenflixColors.Muted,
            fontSize = BenflixType.tag,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        )
    }
}

/** How tall [CardNameUnder] is, so rows (and Top 10 numbers) can line up with it */
@Composable
fun cardNameHeight(strip: Boolean = false): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) {
        (BenflixType.small * 1.2f * 2).toDp() + (BenflixType.tag * 1.4f).toDp() + if (strip) 12.dp else 6.dp
    }
}

/**
 * Whether a card gets its name under it (approved rc-family). Phones and the web: always. TV: not on
 * posters (movies, shows, seasons, collections, books), whose art already shows the name; yes on
 * everything else (episodes, covers, channels, people, games).
 */
fun familyShowsName(
    isTv: Boolean,
    item: BaseItem?,
    aspectRatio: Float,
): Boolean {
    if (!isTv) return true
    val poster =
        aspectRatio < 0.9f &&
            item != null &&
            item.type in
            setOf(
                BaseItemKind.MOVIE,
                BaseItemKind.SERIES,
                BaseItemKind.SEASON,
                BaseItemKind.BOX_SET,
                BaseItemKind.BOOK,
                BaseItemKind.TRAILER,
            )
    return !poster
}

/**
 * Redesign 2 (rc-q-hold): the same hold menu on every kind of card. Returns what holding the card should
 * do: open the poster menu (play, watched, My List, go to, and for Ben's profile Pick a picture). The menu
 * is only built once a card has been held, so cards cost nothing extra until then.
 */
@Composable
fun rememberHoldMenu(item: BaseItem?): () -> Unit {
    val timesHeld = remember(item?.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    if (item != null && timesHeld.intValue > 0) {
        com.github.damontecres.wholphin.ui.components
            .PosterHoldMenu(item, timesHeld.intValue)
    }
    return remember(timesHeld) { { timesHeld.intValue += 1 } }
}
