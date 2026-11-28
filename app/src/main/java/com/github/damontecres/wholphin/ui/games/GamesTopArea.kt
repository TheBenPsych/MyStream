package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.HighScore
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.door.CoverDoorTop
import com.github.damontecres.wholphin.ui.door.CoverTopWords
import com.github.damontecres.wholphin.ui.music.CoverGlow
import com.github.damontecres.wholphin.ui.music.CoverGlowColors
import com.github.damontecres.wholphin.ui.music.rememberCoverGlow
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.theme.glowFocus
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock

/**
 * What the Games top area describes (approved redesign, part 1): the game, console or puzzle
 * you're on, like the Movies door's top area.
 */
internal data class GamePreview(
    val key: String,
    /** Small line above the name, like "SNES" or "Arcade · Play alone" */
    val kicker: String,
    val title: String,
    /** Grey line under the name: "Played yesterday", "Your best 3,880", "109 games" */
    val line: String? = null,
    val about: String? = null,
    /** "1. BEN 3,880" for Benflix games */
    val top3: List<String> = emptyList(),
    val picture: String? = null,
    /** Box art from Tower needs the games token */
    val pictureToken: String? = null,
    /** Console photos are shown whole, box art and screenshots fill the space */
    val fitPicture: Boolean = false,
    val colors: List<Color> = emptyList(),
    val icon: Int? = null,
    val playWith: List<PlayWith> = emptyList(),
)

/** "1. BEN 3,880" lines for a game's board */
internal fun topThree(
    board: Map<String, List<HighScore>>?,
    id: String?,
): List<String> =
    if (id == null) {
        emptyList()
    } else {
        board
            ?.get(id)
            .orEmpty()
            .take(3)
            .mapIndexed { i, e -> "${i + 1}. ${e.name.uppercase()} ${"%,d".format(e.score)}" }
    }

/**
 * The top of the Games For you and Ember Road tabs on TVs and wide screens. Glowfin new look (part 3, Ben approved
 * 2026-11-22): the cover of the game you're on stands at the top right (box art keeps its own shape), its colours
 * glow behind the page, and the words on the left say what it is, like Home and Movies.
 */
@Composable
internal fun GamesTopArea(
    preview: GamePreview?,
    height: Dp,
    overlap: Dp,
    modifier: Modifier = Modifier,
    edge: Dp = BenflixSpace.tvEdge,
) {
    // Glowfin games have their own two colours; box art gives its colours like the Music and Books tops
    val fromArt = rememberCoverGlow(preview?.picture?.takeIf { preview.colors.isEmpty() }, BenflixColors.Games, preview?.pictureToken)
    val p =
        preview ?: run {
            // Nothing to describe yet (no game played): the space stays, so the rows don't slide up under the tabs
            Box(modifier.fillMaxWidth().height(height))
            return
        }
    val glow =
        p.colors
            .takeIf { it.isNotEmpty() }
            ?.let { CoverGlowColors(it.first().copy(alpha = .55f), it.last().copy(alpha = .40f)) }
            ?: fromArt
    val words = remember(p) { gameTopWords(p) }
    val ratio = p.picture?.let { BoxArt.measured[it] } ?: GAME_COVER_RATIO
    CoverDoorTop(
        shown = p,
        words = words,
        glow = glow,
        height = height,
        overlap = overlap,
        coverRatio = ratio.coerceIn(0.5f, 1.5f),
        modifier = modifier,
        edge = edge,
    ) { t, m -> GameTopCover(t, m) }
}

/** "SNES · Plays with controller or phone", "Played yesterday" in the tag, the game's line, its top 3 */
private fun gameTopWords(p: GamePreview): CoverTopWords {
    val with =
        p.playWith
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" or ") { playWithWord(it).lowercase() }
            ?.let { "Plays with $it" }
    return CoverTopWords(
        title = p.title,
        facts = listOfNotNull(p.kicker.takeIf { it.isNotBlank() }, with).joinToString(" · "),
        tag = p.line,
        story = p.about,
        extra = p.top3.takeIf { it.isNotEmpty() }?.joinToString("   "),
    )
}

/** The big cover: Tower's box art, a Glowfin game's own drawn cover, or its colours and icon */
@Composable
private fun GameTopCover(
    p: GamePreview,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val shape = BenflixShapes.Card
    val drawn = remember(p.key) { gameCover(p.key) }
    Box(modifier.clip(shape).background(Glowfin.Raise)) {
        when {
            p.picture != null -> {
                AsyncImage(
                    model =
                        remember(p.picture, p.pictureToken) {
                            ImageRequest
                                .Builder(context)
                                .data(p.picture)
                                .apply { p.pictureToken?.let { httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", it).build()) } }
                                .build()
                        },
                    contentDescription = p.title,
                    contentScale = if (p.fitPicture) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            drawn != null -> {
                Image(
                    painter = painterResource(drawn),
                    contentDescription = p.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(p.colors.ifEmpty { listOf(BenflixColors.Raised, BenflixColors.Panel) })),
                ) {
                    GameIconSquare(p.colors, p.icon, size = 64.dp)
                }
            }
        }
    }
}

/** One game on the Leaderboards row */
internal data class BoardGame(
    val id: String,
    val title: String,
    val open: () -> Unit,
    val mine: Int,
)

/**
 * The Leaderboards row (approved, last on the Games page): every game, its top 3 and your place, all time.
 */
@Composable
internal fun LeaderboardRow(
    games: List<BoardGame>,
    board: Map<String, List<HighScore>>?,
    myName: String?,
    isTv: Boolean,
    onFocus: (GamePreview) -> Unit,
) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.scrollAsOneBlock()) {
        RowName("Leaderboards · all time", isTv)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        ) {
            items(games, key = { "board-${it.id}" }) { g ->
                val top = board?.get(g.id).orEmpty()
                val shape = BenflixShapes.Card
                Surface(
                    onClick = g.open,
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    border =
                        ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), shape = shape),
                        ),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = BenflixColors.Raised,
                            focusedContainerColor = BenflixColors.Raised,
                        ),
                    modifier =
                        Modifier
                            .glowFocus(shape)
                            .width(if (isTv) 220.dp else 180.dp)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    onFocus(
                                        GamePreview(
                                            key = "board-${g.id}",
                                            kicker = "Leaderboard · all time",
                                            title = g.title,
                                            line = if (g.mine > 0) "Your best ${"%,d".format(g.mine)}" else "You haven't played yet",
                                            top3 = topThree(board, g.id),
                                        ),
                                    )
                                }
                            },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.fa_trophy), fontFamily = FontAwesome, fontSize = 13.sp, color = BenflixColors.Soft)
                            Text(
                                g.title,
                                fontSize = BenflixType.body,
                                fontWeight = FontWeight.Bold,
                                color = BenflixColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (board == null) {
                            Text("Loading…", fontSize = BenflixType.small, color = BenflixColors.Muted)
                        } else if (top.isEmpty()) {
                            Text("No scores yet", fontSize = BenflixType.small, color = BenflixColors.Muted)
                        }
                        top.take(3).forEachIndexed { i, e ->
                            BoardScoreLine("${i + 1}. ${e.name.uppercase()}", e.score)
                        }
                        // Your place, when you're not already in the top 3
                        val place = myName?.let { n -> top.indexOfFirst { it.name.equals(n, ignoreCase = true) } } ?: -1
                        if (g.mine > 0 && place !in 0..2) {
                            BoardScoreLine(if (place >= 0) "${place + 1}. YOU" else "YOU", g.mine, muted = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardScoreLine(
    left: String,
    score: Int,
    muted: Boolean = false,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            left,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            color = if (muted) BenflixColors.Muted else BenflixColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%,d".format(score),
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            color = if (muted) BenflixColors.Muted else BenflixColors.Text,
        )
    }
}

/** A small square in the game's colour with its icon, used on the redesigned tiles */
@Composable
internal fun GameIconSquare(
    colors: List<Color>,
    icon: Int?,
    size: Dp = 28.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .background(colors.firstOrNull() ?: BenflixColors.Line, BenflixShapes.Small),
    ) {
        Text(
            text = stringResource(icon ?: R.string.fa_bolt),
            fontFamily = FontAwesome,
            fontSize = (size.value * 0.5f).sp,
            color = Color.Black.copy(alpha = .75f),
        )
    }
}

/** A game picked somewhere else (like Search) that the Games page should open once its list is ready */
object PendingGameOpen {
    @Volatile
    var id: Int? = null
}

/** The word on a "works with" chip */
internal fun playWithWord(kind: PlayWith): String =
    when (kind) {
        PlayWith.REMOTE -> "Remote"
        PlayWith.CONTROLLER -> "Controller"
        PlayWith.PHONE -> "Phone"
    }
