package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.games.LocalGames
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.BenflixColors

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

/** The game's own page: box art, story, saves with pictures, play time and the Play / Resume buttons */
@OptIn(ExperimentalLayoutApi::class)
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
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val ref = choice.ref
    val tint = consoleColour(ref.slug)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { focus.tryRequestFocus() }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (isTv) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF0B0C10), Color(0xFF0B0C10)),
                        )
                    } else {
                        Brush.verticalGradient(listOf(tint.copy(alpha = 0.7f), Color(0xFF0D0B0C), Color(0xFF0D0B0C)))
                    },
                ),
        ) {
            val cover: @Composable (Modifier) -> Unit = { mod ->
                Box(
                    mod
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF242424)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ref.name, color = Color.White, fontSize = BenflixType.small, modifier = Modifier.padding(8.dp))
                    ref.coverUrl?.let { url ->
                        AsyncImage(
                            model =
                                remember(url) {
                                    ImageRequest
                                        .Builder(context)
                                        .data(url)
                                        .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                        .build()
                                },
                            contentDescription = ref.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (isTv) {
                TvGameHero(
                    choice = choice,
                    info = info,
                    saves = saves,
                    playTimeMs = playTimeMs,
                    favourite = favourite,
                    token = token,
                    focus = focus,
                    onPlay = onPlay,
                    onFavourite = onFavourite,
                    onRemove = onRemove,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 40.dp else 16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isTv) 56.dp else 16.dp, vertical = if (isTv) 36.dp else 16.dp),
                ) {
                    cover(Modifier.width(if (isTv) 260.dp else 110.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 8.dp),
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Text(ref.name, fontSize = if (isTv) BenflixType.hero else BenflixType.rowName, fontWeight = FontWeight.Black, color = Color.White)
                        val facts =
                            listOfNotNull(
                                ref.consoleName,
                                info?.year?.takeIf { it > 0 }?.toString(),
                                info?.players?.takeIf { it.isNotBlank() }?.let { if (it == "1") "1 player" else "$it players" },
                                info?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                                playTimeMs.takeIf { it >= 60_000 }?.let { "Played ${playTimeText(it)}" },
                                if (ref.size > 0 && !choice.downloaded) "${LocalGames.mb(ref.size)} download" else null,
                            )
                        Text(facts.joinToString("  ·  "), fontSize = if (isTv) BenflixType.body else BenflixType.tag, color = BenflixColors.Soft)
                        // Buttons
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (choice.resume) {
                                DetailButton("▶  Resume", primary = true, modifier = Modifier.focusRequester(focus)) { onPlay(true) }
                                DetailButton("Start over") { onPlay(false) }
                            } else {
                                DetailButton("▶  Play", primary = true, modifier = Modifier.focusRequester(focus)) { onPlay(false) }
                            }
                            DetailButton(if (favourite) "♥  Favourite" else "♡  Favourite", onClick = onFavourite)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!isTv) {
                                DetailButton(if (choice.pinned) "Stop keeping for trips" else "Keep for trips") { onKeep(!choice.pinned) }
                            }
                            // Kids can't remove games from the device
                            if (choice.downloaded && !KidsMode.isActive) DetailButton("Remove from this device", onClick = onRemove)
                        }
                        if (choice.downloaded && !KidsMode.isActive) {
                            Text(
                                "Removing frees up space. Your saves are kept on Tower.",
                                fontSize = BenflixType.tag,
                                color = BenflixColors.Muted,
                            )
                        }
                        when {
                            info == null -> {
                                Text("Loading the story…", fontSize = BenflixType.small, color = BenflixColors.Muted)
                            }

                            info.summary.isNotBlank() -> {
                                Text(
                                    info.summary,
                                    fontSize = if (isTv) BenflixType.body else BenflixType.small,
                                    color = BenflixColors.Text,
                                    modifier = Modifier.widthIn(max = 760.dp),
                                )
                            }
                        }
                        info?.companies?.takeIf { it.isNotEmpty() }?.let {
                            Text("Made by ${it.joinToString(", ")}", fontSize = BenflixType.small, color = BenflixColors.Muted)
                        }
                        if (saves.isNotEmpty()) {
                            Text(
                                "Your saves",
                                fontSize = if (isTv) BenflixType.rowName else BenflixType.body,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                saves.forEach { spot -> SaveTile(spot, token, isTv) }
                            }
                            Text(
                                "Resume picks up where you last stopped. Load a slot from the game menu (hold Back, or the ☰ button).",
                                fontSize = BenflixType.tag,
                                color = BenflixColors.Muted,
                            )
                        }
                    }
                }
            }
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
        Text(spot.title, fontSize = if (isTv) BenflixType.small else BenflixType.tag, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(savedAgoText(spot.time), fontSize = BenflixType.tag, color = BenflixColors.Muted, maxLines = 1)
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

@Composable
private fun DetailButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), shape = shape)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) Color.White else Color(0xFF2A2A2A),
                contentColor = if (primary) Color.Black else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier,
    ) {
        Text(text, fontSize = BenflixType.body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

/**
 * Benflix redesign (game page, TV): like a movie's page. The game's picture (your last save, or the box
 * art) fills the right side, the name, facts, story, a short how-to line and the buttons sit on the left,
 * and Your saves is a row underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvGameHero(
    choice: GameChoice,
    info: GameInfo?,
    saves: List<SaveSpot>,
    playTimeMs: Long,
    favourite: Boolean,
    token: String,
    focus: FocusRequester,
    onPlay: (resume: Boolean) -> Unit,
    onFavourite: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val ref = choice.ref
    val picture: Any? = saves.firstOrNull { it.picture != null }?.picture ?: ref.coverUrl
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.62f)
                .align(Alignment.TopEnd)
                .clipToBounds(),
        ) {
            picture?.let { pic ->
                AsyncImage(
                    model =
                        remember(pic) {
                            ImageRequest
                                .Builder(context)
                                .data(pic)
                                .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                .build()
                        },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Old games are tiny pictures: keep the pixels sharp when they're made big
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF0B0C10),
                        0.4f to Color(0xFF0B0C10).copy(alpha = .6f),
                        0.8f to Color.Transparent,
                    ),
                ),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 0.85f to Color(0xFF0B0C10))))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 56.dp, end = 56.dp, top = 40.dp, bottom = 32.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(0.5f)) {
                Text(ref.name, fontSize = BenflixType.hero, lineHeight = BenflixType.hero * 1.1f, fontWeight = FontWeight.Black, color = Color.White, maxLines = 2)
                val facts =
                    listOfNotNull(
                        ref.consoleName,
                        info?.year?.takeIf { it > 0 }?.toString(),
                        info?.players?.takeIf { it.isNotBlank() }?.let { if (it == "1") "1 player" else "$it players" },
                        info
                            ?.genres
                            ?.takeIf { it.isNotEmpty() }
                            ?.take(2)
                            ?.joinToString(", "),
                        playTimeMs.takeIf { it >= 60_000 }?.let { "Played ${playTimeText(it)}" },
                    )
                Text(facts.joinToString("  ·  "), fontSize = BenflixType.body, color = BenflixColors.Soft, maxLines = 2)
                when {
                    info == null -> {
                        Text("Loading the story…", fontSize = BenflixType.small, color = BenflixColors.Muted)
                    }

                    info.summary.isNotBlank() -> {
                        Text(
                            info.summary,
                            fontSize = BenflixType.body,
                            lineHeight = BenflixType.body * 1.35f,
                            color = BenflixColors.Text,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    "Hold Back (or the ☰ button) during the game for saves and the menu.",
                    fontSize = BenflixType.small,
                    color = BenflixColors.Muted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (choice.resume) {
                        DetailButton("▶  Keep playing", primary = true, modifier = Modifier.focusRequester(focus)) { onPlay(true) }
                        DetailButton("Start over") { onPlay(false) }
                    } else {
                        DetailButton("▶  Play", primary = true, modifier = Modifier.focusRequester(focus)) { onPlay(false) }
                    }
                    DetailButton(if (favourite) "♥  Favourite" else "♡  Favourite", onClick = onFavourite)
                    // Kids can't remove games from the device
                    if (choice.downloaded && !KidsMode.isActive) DetailButton("Remove from this TV", onClick = onRemove)
                }
            }
            if (saves.isNotEmpty()) {
                Text(
                    "Your saves",
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    saves.forEach { spot -> SaveTile(spot, token, true) }
                }
            }
        }
    }
}
