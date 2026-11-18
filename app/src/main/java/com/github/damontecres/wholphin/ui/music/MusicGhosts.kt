package com.github.damontecres.wholphin.ui.music

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import com.github.damontecres.wholphin.services.MusicAlbum
import com.github.damontecres.wholphin.services.MusicArtist
import com.github.damontecres.wholphin.services.MusicSong
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SongPreview
import com.github.damontecres.wholphin.ui.discover.SongPreviewButton
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/*
 * Music redesign 3, Pages: songs and albums Benflix doesn't have yet, shown greyed beside the ones it has
 * (albums, artist pages, Search), each with a 30-second preview and Ask. Also the cover colour at page tops.
 */

/** A song name, simplified so "Don't Stop - Remastered 2004" and "Don't Stop" count as the same song */
fun songKey(title: String?): String =
    (title ?: "")
        .lowercase()
        .replace(Regex("""\s*[(\[].*?[)\]]"""), "")
        .replace(Regex("""\s+-\s+.*(remaster|version|edit|mix|live|mono|stereo).*$"""), "")
        .filter { it.isLetterOrDigit() }

/** Something in the music catalogue to open on Ask for music (an album or artist page there) */
object MusicAskNav {
    /** A [MusicAlbum] or [MusicArtist] waiting for Ask for music to open it */
    var open by mutableStateOf<Any?>(null)

    /** Glowfin (M1): the Import tile on Music opens Ask for music straight on Import a playlist */
    @Volatile
    var importJump = false
}

/** Opens a catalogue album or artist on Ask for music, from any music page */
fun openInAsk(
    navigationManager: NavigationManager,
    what: Any,
) {
    MusicAskNav.open = what
    // Glowfin (M1): Ask for music opens over the Music page
    MusicPlaceNav.openAsk()
    val stack = navigationManager.backStack
    val at = stack.indexOfLast { it is Destination.MusicDoor }
    when {
        stack.lastOrNull() is Destination.MusicDoor -> {}

        at >= 0 -> {
            navigationManager.goBackTo(at)
        }

        else -> {
            navigationManager.navigateTo(Destination.MusicDoor)
        }
    }
}

/**
 * A song Benflix doesn't have: greyed, with its 30-second preview (pressing the row plays it) and Ask beside it.
 * [askWords] is the words on Ask ("Asked ✓", "Downloading 40%") once it was asked for.
 */
@Composable
fun MissingSongRow(
    song: MusicSong,
    number: Int?,
    preview: SongPreview?,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
    withCover: Boolean = false,
    big: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val previewState = preview?.state?.collectAsState()?.value
    val asked = song.asked
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            onClick = { preview?.toggle(song.id) },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = BenflixColors.Muted,
                    focusedContainerColor = BenflixColors.FocusRow,
                    focusedContentColor = BenflixColors.Soft,
                ),
            border = doorFocusBorder(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = if (big) 10.dp else 6.dp),
            ) {
                if (withCover) {
                    Box(Modifier.size(if (big) 48.dp else 40.dp)) {
                        CoverImage(song.cover, Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp))
                        Box(Modifier.fillMaxSize().background(Color(0x88000000), RoundedCornerShape(6.dp)))
                    }
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(28.dp)) {
                        Text(
                            text = number?.toString().orEmpty(),
                            color = BenflixColors.Muted.copy(alpha = .6f),
                            fontSize = if (big) BenflixType.lead else BenflixType.body,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = BenflixColors.Muted,
                        fontSize = if (big) BenflixType.rowName else BenflixType.body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (withCover) "${song.artist} · Not on Glowfin" else "Not on Glowfin",
                        color = BenflixColors.Muted.copy(alpha = .75f),
                        fontSize = BenflixType.small,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (song.seconds > 0) {
                    Text(
                        "%d:%02d".format(song.seconds / 60, song.seconds % 60),
                        color = BenflixColors.Muted.copy(alpha = .7f),
                        fontSize = BenflixType.small,
                    )
                }
            }
        }
        if (previewState != null) {
            SongPreviewButton(state = previewState, songId = song.id, onClick = { preview.toggle(song.id) }, size = 40)
        }
        AskPill(asked ?: "Ask", done = asked != null, onClick = onAsk)
    }
}

/** The teal Ask button (teal = asking for music) */
@Composable
fun AskPill(
    words: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = { if (!done) onClick() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (done) BenflixColors.Raised else MusicAskColor.copy(alpha = .18f),
                contentColor = if (done) BenflixColors.Muted else MusicAskColor,
                focusedContainerColor = if (done) BenflixColors.PanelHigh else MusicAskColor,
                focusedContentColor = if (done) BenflixColors.Soft else BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = words,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).widthIn(max = 180.dp),
        )
    }
}

/** An album in the catalogue that isn't on Benflix: greyed cover with a "Not on Benflix" tag */
@Composable
fun CatalogueAlbumCard(
    album: MusicAlbum,
    size: Dp,
    onClick: () -> Unit,
) {
    MusicCardFrame(
        name = album.title,
        line =
            album.asked ?: listOf(album.year, if (album.type == "album") "" else album.type.replaceFirstChar { it.uppercase() })
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        size = size,
        onClick = onClick,
    ) {
        Box(Modifier.size(size)) {
            CoverImage(album.cover, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x66000000), BenflixShapes.Card))
            Text(
                "Not on Glowfin",
                color = BenflixColors.Tray,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.ExtraBold,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(MusicAskColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Fans also like: an artist from the catalogue (round photo). [line] says if they're on Benflix */
@Composable
fun CatalogueArtistCard(
    artist: MusicArtist,
    line: String,
    size: Dp,
    onClick: () -> Unit,
) {
    MusicCardFrame(
        name = artist.name,
        line = line,
        size = size,
        onClick = onClick,
        shape = CircleShape,
        centred = true,
    ) {
        CoverImage(artist.picture, Modifier.size(size), shape = CircleShape)
    }
}

/** A row of catalogue artists */
@Composable
fun CatalogueArtistRow(
    title: String,
    artists: List<Pair<MusicArtist, Boolean>>,
    side: Dp,
    size: Dp,
    onClick: (MusicArtist, Boolean) -> Unit,
) {
    Column(Modifier.padding(top = 28.dp)) {
        MusicRowName(title, Modifier.padding(start = side, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(artists, key = { "fan-${it.first.id}" }) { (artist, here) ->
                CatalogueArtistCard(artist, if (here) "On Glowfin" else "None yet", size) { onClick(artist, here) }
            }
        }
    }
}

// ---------- Cover colour at the top of a page (a little, page tops only: Ben 2026-11-16) ----------

private val tintCache = LruCache<String, Int>(64)

/** The cover's own colour, dark enough for white words, or null while it loads (or when it can't) */
@Composable
fun rememberCoverTint(url: String?): Color? {
    val context = LocalContext.current
    var tint by remember(url) { mutableStateOf(url?.let { tintCache.get(it) }?.let { Color(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || tint != null) return@LaunchedEffect
        val rgb =
            withContext(WholphinDispatchers.IO) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(96)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.asDrawable(context.resources).toBitmap(config = Bitmap.Config.ARGB_8888)
                        val p = Palette.from(bitmap).generate()
                        (p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.dominantSwatch ?: p.darkMutedSwatch)?.rgb
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    Timber.d(ex, "Cover colour")
                    null
                }
            }
        if (rgb != null) {
            tintCache.put(url, rgb)
            tint = Color(rgb)
        }
    }
    return tint
}

/** Makes a cover colour dark enough to sit behind white words */
fun Color.forPageTop(): Color {
    val most = maxOf(red, green, blue)
    val scale = if (most > .55f) .55f / most else 1f
    return Color(red * scale, green * scale, blue * scale, 1f)
}

/** The soft wash of the cover's colour at the top of a page, fading into the dark page */
@Composable
fun CoverWash(
    tint: Color?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    if (tint == null) return
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(0f to tint.forPageTop().copy(alpha = .55f), 1f to Color.Transparent)),
    )
}
