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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.games.LocalGames
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.tryRequestFocus

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

/** The big "Continue playing" card at the top of the Games page */
@Composable
fun ContinueBanner(
    ref: GameRef,
    playedText: String,
    picture: Any?,
    token: String,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    val tint = consoleColour(ref.slug)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF161616), focusedContainerColor = Color(0xFF161616)),
        modifier =
            modifier
                .fillMaxWidth()
                .height(if (isTv) 220.dp else 150.dp),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.85f), Color(0xFF161616)))),
        ) {
            // Where you left off (the save's picture), or the box art
            val image = picture ?: ref.coverUrl
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(if (picture != null) 4f / 3f else 3f / 4f)
                    .background(Color.Black),
            ) {
                if (image != null) {
                    AsyncImage(
                        model =
                            remember(image) {
                                ImageRequest
                                    .Builder(context)
                                    .data(image)
                                    .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                    .build()
                            },
                        contentDescription = ref.name,
                        contentScale = ContentScale.Crop,
                        filterQuality = if (picture != null) FilterQuality.None else FilterQuality.Low,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(if (isTv) 8.dp else 4.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = if (isTv) 28.dp else 14.dp),
            ) {
                Text(
                    "CONTINUE PLAYING",
                    fontSize = if (isTv) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = .8f),
                )
                Text(
                    ref.name,
                    fontSize = if (isTv) 30.sp else 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${ref.consoleName} · $playedText",
                    fontSize = if (isTv) 15.sp else 12.sp,
                    color = Color.White.copy(alpha = .8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(horizontal = if (isTv) 20.dp else 14.dp, vertical = if (isTv) 8.dp else 5.dp),
                ) {
                    Text("▶  Resume", fontSize = if (isTv) 16.sp else 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
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
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.7f), Color(0xFF0D0B0C), Color(0xFF0D0B0C)))),
        ) {
            val cover: @Composable (Modifier) -> Unit = { mod ->
                Box(
                    mod
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF242424)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ref.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
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
                    Text(ref.name, fontSize = if (isTv) 34.sp else 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                    val facts =
                        listOfNotNull(
                            ref.consoleName,
                            info?.year?.takeIf { it > 0 }?.toString(),
                            info?.players?.takeIf { it.isNotBlank() }?.let { if (it == "1") "1 player" else "$it players" },
                            info?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            playTimeMs.takeIf { it >= 60_000 }?.let { "Played ${playTimeText(it)}" },
                            if (ref.size > 0 && !choice.downloaded) "${LocalGames.mb(ref.size)} download" else null,
                        )
                    Text(facts.joinToString("  ·  "), fontSize = if (isTv) 15.sp else 12.sp, color = Color.White.copy(alpha = .8f))
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
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = .6f),
                        )
                    }
                    when {
                        info == null -> {
                            Text("Loading the story…", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
                        }

                        info.summary.isNotBlank() -> {
                            Text(
                                info.summary,
                                fontSize = if (isTv) 16.sp else 13.sp,
                                color = Color.White.copy(alpha = .9f),
                                modifier = Modifier.widthIn(max = 760.dp),
                            )
                        }
                    }
                    info?.companies?.takeIf { it.isNotEmpty() }?.let {
                        Text("Made by ${it.joinToString(", ")}", fontSize = 13.sp, color = Color.White.copy(alpha = .6f))
                    }
                    if (saves.isNotEmpty()) {
                        Text("Your saves", fontSize = if (isTv) 20.sp else 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            saves.forEach { spot -> SaveTile(spot, token, isTv) }
                        }
                        Text(
                            "Resume picks up where you last stopped. Load a slot from the game menu (hold Back, or the ☰ button).",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = .6f),
                        )
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
                .clip(RoundedCornerShape(8.dp))
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
        Text(spot.title, fontSize = if (isTv) 14.sp else 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(savedAgoText(spot.time), fontSize = if (isTv) 12.sp else 10.sp, color = Color.White.copy(alpha = .65f), maxLines = 1)
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) Color.White else Color(0xFF2A2A2A),
                contentColor = if (primary) Color.Black else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}
