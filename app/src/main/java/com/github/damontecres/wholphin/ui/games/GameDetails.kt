package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.background
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.games.LocalGames
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.title.StripPart
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleMoreItem
import com.github.damontecres.wholphin.ui.detail.title.TitleMorePanel
import com.github.damontecres.wholphin.ui.detail.title.TitleOverview
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.glowFocus

/** What the game details page shows besides the buttons */
data class GameInfo(
    val summary: String = "",
    val year: Int = 0,
    val genres: List<String> = emptyList(),
    val players: String = "",
    val companies: List<String> = emptyList(),
)

/** One save on the details page: where you last stopped, or slot 1 to 3 */
data class SaveSpot(
    val title: String,
    /** A local file path, or a URL on Tower */
    val picture: Any?,
    val time: Long,
)

/** Each console's own colour for its tile, like the console's box or logo */
fun consoleColour(slug: String): Color =
    when (slug) {
        "nes", "famicom" -> Color(0xFFB71C1C)
        "snes", "sfam" -> Color(0xFF5E35B1)
        "genesis", "genesis-slash-megadrive", "megadrive" -> Color(0xFF1A237E)
        "sms" -> Color(0xFFC62828)
        "gamegear" -> Color(0xFF263238)
        "gb" -> Color(0xFF6B8E23)
        "gbc" -> Color(0xFF00897B)
        "gba" -> Color(0xFF3949AB)
        "psx" -> Color(0xFF455A64)
        "n64" -> Color(0xFF2E7D32)
        "atari2600", "atari-2600" -> Color(0xFF6D4C41)
        "atari7800", "atari-7800" -> Color(0xFF424242)
        "lynx", "atari-lynx" -> Color(0xFFE65100)
        "tg16", "turbografx16--1", "turbografx-16", "pce", "pc-engine", "pcengine" -> Color(0xFFF57C00)
        "ngp", "ngpc", "neo-geo-pocket", "neo-geo-pocket-color" -> Color(0xFF0277BD)
        "wonderswan", "wonderswan-color", "ws", "wsc" -> Color(0xFF00838F)
        "virtualboy", "virtual-boy", "vb" -> Color(0xFFB71C1C)
        "colecovision", "coleco" -> Color(0xFF37474F)
        "arcade", "fbneo", "mame", "cps1", "cps2", "cps3", "neogeoaes", "neogeomvs" -> Color(0xFF6A1B9A)
        else -> Color(0xFF37474F)
    }

/** "12 min", "3 h 20 min" */
fun playTimeText(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "Less than a minute"
        min < 60 -> "$min min"
        else -> "${min / 60} h ${min % 60} min"
    }
}

/**
 * The game's own page (Glowfin G1): the movie-page rows layout with the box art where the picture goes.
 * Buttons: Keep playing (or Play), the heart, More (keep for trips, remove from this device). What it works
 * with sits under the buttons. Rows underneath: Your saves (with a New game tile), More like this, About.
 */
@Composable
fun GameDetailsPage(
    choice: GameChoice,
    info: GameInfo?,
    saves: List<SaveSpot>,
    playTimeMs: Long,
    favourite: Boolean,
    token: String,
    isTv: Boolean,
    onPlay: (resume: Boolean) -> Unit,
    onFavourite: () -> Unit,
    onKeep: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    /** Other games on the same console that share a genre */
    moreLike: List<GameRef> = emptyList(),
    moreLikeCard: @Composable (GameRef) -> Unit = {},
    /** "Works with: Remote · Controller · Phone" */
    worksWith: String? = null,
) {
    val context = LocalContext.current
    val ref = choice.ref
    val tint = consoleColour(ref.slug)
    var showMore by remember { mutableStateOf(false) }
    val coverModel =
        remember(ref.coverUrl) {
            ref.coverUrl?.let { url ->
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                    .build()
            }
        }
    val kids = KidsMode.isActive
    val canMore = !isTv || (choice.downloaded && !kids)
    val buttons =
        buildList {
            val played = playTimeMs.takeIf { it >= 60_000 }?.let { "Played ${playTimeText(it)}" }
            if (choice.resume) {
                add(TitleButton("Keep playing", BenflixIcon.PLAY, { onPlay(true) }, note = played))
                // With save slots, starting over is the New game tile in Your saves instead
                if (saves.isEmpty()) add(TitleButton("Start over", BenflixIcon.REFRESH, { onPlay(false) }))
            } else {
                add(
                    TitleButton(
                        "Play",
                        BenflixIcon.PLAY,
                        { onPlay(false) },
                        note = if (ref.size > 0 && !choice.downloaded) "${LocalGames.mb(ref.size)} download first" else played,
                    ),
                )
            }
            add(TitleButton(if (favourite) "Favourite" else "Favourite it", BenflixIcon.HEART, onFavourite))
            add(
                TitleButton(
                    "More",
                    BenflixIcon.MORE,
                    { showMore = true },
                    off = if (canMore) null else "Nothing else for this game yet",
                ),
            )
        }
    val tabs =
        buildList {
            if (saves.isNotEmpty()) {
                add(
                    TitleTab("Your saves") { scope ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = scope.side)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                saves.forEach { spot -> SaveTile(spot, token, scope.wide) }
                                NewGameTile(scope.wide) { onPlay(false) }
                            }
                            Text(
                                "Keep playing picks up where you last stopped. Load a slot from the game menu (hold Back, or press Menu).",
                                fontSize = BenflixType.tag,
                                color = BenflixColors.Muted,
                            )
                        }
                    },
                )
            }
            if (moreLike.isNotEmpty()) {
                add(
                    TitleTab("More like this") { scope ->
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(horizontal = scope.side, vertical = 8.dp),
                        ) {
                            items(moreLike.size, key = { "like-${moreLike[it].id}" }) { moreLikeCard(moreLike[it]) }
                        }
                    },
                )
            }
            add(
                TitleTab("About this game") { scope ->
                    TitleOverview(
                        scope = scope,
                        title = ref.name,
                        // The story sits under the name now (rows layout), so this row has who made it
                        story = null,
                        strip =
                            listOfNotNull(
                                info?.companies?.takeIf { it.isNotEmpty() }?.let { StripPart("Made by", it.joinToString(", ")) },
                                info?.genres?.takeIf { it.isNotEmpty() }?.let { StripPart(it.take(3).joinToString(", ")) },
                            ),
                        extra =
                            if (choice.downloaded && !kids) {
                                {
                                    Text(
                                        "Removing it from this device frees up space. Your saves are kept on Tower.",
                                        fontSize = BenflixType.tag,
                                        color = BenflixColors.Muted,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                },
            )
        }
    val infoLine =
        listOfNotNull(
            ref.consoleName,
            info?.year?.takeIf { it > 0 }?.toString(),
            info?.players?.takeIf { it.isNotBlank() }?.let { if (it == "1") "1 player" else "$it players" },
        ).joinToString("  ·  ")
    // Ben 2026-11-18: the game page fills the page area, not the whole screen, so the left menu stays
    // (Left on the remote reaches it, like on movie pages). Back closes it.
    BackHandler(enabled = !showMore) { onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            // Right past the last thing on the page goes nowhere (the Games page is tucked away to the right)
            .focusProperties { onExit = { if (requestedFocusDirection == FocusDirection.Right) cancelFocusChange() } }
            .focusGroup(),
    ) {
        Box(Modifier.fillMaxSize().background(BenflixColors.Tray)) {
            TitlePage(
                title = ref.name,
                accent = tint,
                picture = TitlePicture.Cover(ref.coverUrl, 3f / 4f, tint, model = coverModel),
                info = infoLine,
                buttons = buttons,
                tabs = tabs,
                rows = true,
                story = info?.summary?.takeIf { it.isNotBlank() },
                statusLine = worksWith,
            )
        }
        if (showMore) {
            TitleMorePanel(
                title = ref.name,
                onDismiss = { showMore = false },
                items =
                    buildList {
                        if (!isTv) {
                            add(
                                TitleMoreItem(if (choice.pinned) "Stop keeping for trips" else "Keep for trips", BenflixIcon.DOWNLOAD, {
                                    onKeep(!choice.pinned)
                                }),
                            )
                        }
                        // Kids can't remove games from the device
                        if (choice.downloaded && !kids) {
                            add(TitleMoreItem(if (isTv) "Remove from this TV" else "Remove from this device", BenflixIcon.TRASH, onRemove))
                        }
                    },
            )
        }
    }
}

@Composable
private fun SaveTile(
    spot: SaveSpot,
    token: String,
    isTv: Boolean,
) {
    val context = LocalContext.current
    Column(Modifier.width(if (isTv) 170.dp else 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
        ) {
            spot.picture?.let { pic ->
                AsyncImage(
                    model =
                        remember(pic) {
                            ImageRequest
                                .Builder(context)
                                .data(pic)
                                .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                .build()
                        },
                    contentDescription = spot.title,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            spot.title,
            fontSize = if (isTv) BenflixType.small else BenflixType.tag,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            savedAgoText(spot.time),
            fontSize = BenflixType.tag,
            color = BenflixColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun savedAgoText(time: Long): String {
    val min = (System.currentTimeMillis() - time) / 60_000
    return when {
        time <= 0 -> ""
        min < 1 -> "Saved just now"
        min < 60 -> "Saved $min min ago"
        min < 24 * 60 -> "Saved ${min / 60} h ago"
        else -> "Saved ${min / (24 * 60)} days ago"
    }
}

/** Glowfin G1: the dashed "+ New game" tile at the end of Your saves: starts the game from the beginning */
@Composable
private fun NewGameTile(
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(shape),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults
                .scale(focusedScale = 1f),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                border =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(2.dp, com.github.damontecres.wholphin.ui.theme.Glowfin.Line),
                        shape = shape,
                    ),
                focusedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus),
                        shape = shape,
                    ),
            ),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.Glowfin.Raise,
            ),
        modifier =
            Modifier
                .glowFocus(shape)
                .width(if (isTv) 170.dp else 96.dp)
                .aspectRatio(4f / 3f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "+ New game",
                fontSize = if (isTv) BenflixType.small else BenflixType.tag,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Soft,
            )
        }
    }
}
