package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.HighScore
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock

/** One tile in a Games row: name, a two-line description, and your best (Ben asked to keep descriptions) */
internal data class RowGame(
    val key: String,
    val title: String,
    val short: String,
    val colors: List<Color>,
    val icon: Int?,
    val badge: String,
    val onClick: () -> Unit,
    /** Small grey word under the name, like "Arcade" */
    val kind: String = "",
    /** The game's board (for the top area's top 3), if it has one */
    val boardId: String? = null,
    val playWith: List<PlayWith> = emptyList(),
)

/**
 * The Games page rows (Ben's pick A, roomier): Movie puzzles, Party and Arcade, each one sideways
 * row of tiles with the description always showing. About 4 and a half tiles per row on TV.
 */
@Composable
internal fun GameRows(
    viewModel: GamesViewModel,
    isTv: Boolean,
    /** Which rows to draw here: "puzzles", "party", "arcade" (the page puts other rows between them) */
    only: Set<String> = setOf("puzzles", "party", "arcade"),
    board: Map<String, List<HighScore>>? = null,
    onFocus: (GamePreview) -> Unit = {},
) {
    val kids = KidsMode.isActive

    fun open(id: String) = viewModel.navigationManager.navigateTo(Destination.GameInfo(id))

    fun badge(best: Int) = if (best > 0) "Best $best" else "New"

    val puzzles =
        MovieGame.entries
            .filter { it.group == MovieGameGroup.PUZZLE && it.visibleNow(kids) }
            .map { g ->
                RowGame(
                    g.name,
                    g.title,
                    g.short,
                    g.colors,
                    g.icon,
                    badge(g.boardKey?.let { viewModel.bestExtra(it) } ?: 0),
                    onClick = { open("movie:${g.name}") },
                    kind = "Movie puzzle",
                    boardId = g.boardKey,
                    playWith = listOf(PlayWith.REMOTE, PlayWith.PHONE),
                )
            }
    val party =
        listOfNotNull(
            if (!isTv) {
                RowGame(
                    "join",
                    "Join a party",
                    "A TV is hosting a party game? Join it with this phone.",
                    listOf(Color(0xFF444450), Color(0xFF1C1C22)),
                    R.string.fa_mobile_screen,
                    "Phone",
                    onClick = { viewModel.navigationManager.navigateTo(Destination.PartyJoin) },
                    kind = "Party",
                    playWith = listOf(PlayWith.PHONE),
                )
            } else {
                null
            },
        ) +
            MovieGame.entries
                .filter { it.group == MovieGameGroup.PARTY && it.visibleNow(kids) }
                .map { g ->
                    RowGame(
                        g.name,
                        g.title,
                        g.short,
                        g.colors,
                        g.icon,
                        "Party",
                        onClick = { open("movie:${g.name}") },
                        kind = "Party · Needs phones · 3 to 8 players",
                        playWith = listOf(PlayWith.PHONE),
                    )
                }
    val arcade =
        BuiltInGame.entries.map { g ->
            RowGame(
                g.name,
                g.title,
                g.tagline,
                g.colors,
                R.string.fa_bolt,
                badge(viewModel.best(g)),
                onClick = { open("arcade:${g.name}") },
                kind = "Arcade · Play alone",
                boardId = g.name,
                playWith = listOfNotNull(if (g.remoteOk) PlayWith.REMOTE else null, PlayWith.CONTROLLER, PlayWith.PHONE),
            )
        } +
            RowGame(
                "surprise",
                "Surprise me",
                "Starts a random arcade game.",
                listOf(Color(0xFF3A3A44), Color(0xFF16161B)),
                R.string.fa_dice,
                "Random",
                onClick = { viewModel.surprise(viewModel.featured()) },
                kind = "Arcade",
            )

    val focus: (RowGame) -> Unit = { g ->
        onFocus(
            GamePreview(
                key = g.key,
                kicker = g.kind,
                title = g.title,
                line = g.badge.takeIf { it.startsWith("Best") }?.replace("Best", "Your best"),
                about = g.short,
                top3 = topThree(board, g.boardId),
                colors = g.colors,
                icon = g.icon,
                playWith = g.playWith,
            ),
        )
    }
    if ("puzzles" in only) GameRow("Movie puzzles", puzzles, isTv, focus)
    if ("party" in only && party.isNotEmpty()) GameRow("Party", party, isTv, focus)
    if ("arcade" in only) GameRow("Arcade", arcade, isTv, focus)
}

@Composable
private fun GameRow(
    title: String,
    games: List<RowGame>,
    isTv: Boolean,
    onFocus: (RowGame) -> Unit,
) {
    if (games.isEmpty()) return
    // TV: the row name always comes into view with the tiles
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.scrollAsOneBlock()) {
        Text(
            title,
            fontSize = if (isTv) BenflixType.rowName else BenflixType.lead,
            fontWeight = FontWeight.SemiBold,
            color = BenflixColors.Text,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = if (isTv) 16.dp else 12.dp
            // Ben: tall cards like movie and show posters, with room for the whole name (up to three lines)
            val width: Dp = if (isTv) 150.dp else (maxWidth * 0.34f).coerceIn(104.dp, 150.dp)
            // TV: a little shorter than a poster so the whole row fits under the top area (Ben, 2026-11-08)
            val tileHeight = maxOf(width * if (isTv) 1.12f else 1.4f, posterTileHeight(isTv))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            ) {
                items(
                    games,
                    key = { it.key },
                ) { g ->
                    RowTile(
                        g,
                        isTv,
                        Modifier
                            .size(width = width, height = tileHeight)
                            .onFocusChanged { if (it.isFocused) onFocus(g) },
                    )
                }
            }
        }
    }
}

/**
 * How tall a game tile must be to show everything inside it: padding, the icon square, the name and the kind.
 * Worked out from the real text sizes (and the reader's text size setting), so the words are never cut in half.
 */
@Composable
internal fun gameTileHeight(
    isTv: Boolean,
    padding: Dp = if (isTv) 14.dp else 12.dp,
    top: Dp = 28.dp,
): Dp {
    val density = LocalDensity.current
    return with(density) {
        val name = (if (isTv) BenflixType.lead else BenflixType.body).toDp() * TILE_LINE
        val kind = BenflixType.tag.toDp() * TILE_LINE
        padding * 2 + top + name + kind + 8.dp
    }
}

/** How tall a poster-shaped game tile must be: padding, the big icon square, three lines of name and the kind */
@Composable
private fun posterTileHeight(isTv: Boolean): Dp {
    val density = LocalDensity.current
    return with(density) {
        val name = (if (isTv) BenflixType.lead else BenflixType.body).toDp() * TILE_LINE * nameLines(isTv)
        val kind = BenflixType.tag.toDp() * TILE_LINE
        (if (isTv) 14.dp else 12.dp) * 2 + PosterIconSize + name + kind + 12.dp
    }
}

private val PosterIconSize = 44.dp

/** TV cards are wider for their height, so two lines hold any name; phones get three */
private fun nameLines(isTv: Boolean): Int = if (isTv) 2 else 3

/** Room for one line of text, as a share of its size (Outfit sits a little tall) */
private const val TILE_LINE = 1.4f

/** A Benflix game tile: a tall dark card (poster shaped), colour square with the icon, the full name and kind. No grow. */
@Composable
private fun RowTile(
    g: RowGame,
    isTv: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = g.onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = BenflixColors.Raised, focusedContainerColor = BenflixColors.Raised),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(if (isTv) 14.dp else 12.dp),
        ) {
            GameIconSquare(g.colors, g.icon, size = PosterIconSize)
            Box(Modifier.weight(1f))
            Text(
                g.title,
                fontSize = if (isTv) BenflixType.lead else BenflixType.body,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Text,
                maxLines = nameLines(isTv),
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                g.kind.substringBefore(" · "),
                fontSize = BenflixType.tag,
                color = BenflixColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Ember Road as a slim banner under the leaderboards (the spot Ben picked, half as tall) */
@Composable
internal fun EmberBanner(
    isTv: Boolean,
    onClick: () -> Unit,
    onFocus: (GamePreview) -> Unit = {},
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier =
            Modifier.fillMaxWidth().onFocusChanged {
                if (it.isFocused) {
                    onFocus(
                        GamePreview(
                            key = "ember",
                            kicker = "Adventure",
                            title = "Ember Road",
                            about = "Pixel adventure: carry a dragon egg across a wild land. Android phones can join in.",
                            colors = EMBER_COLOURS,
                            icon = R.string.fa_dragon,
                            playWith = listOf(PlayWith.REMOTE, PlayWith.CONTROLLER, PlayWith.PHONE),
                        ),
                    )
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isTv) 64.dp else 72.dp)
                    .background(Brush.linearGradient(EMBER_COLOURS.map { it.copy(alpha = .5f) } + BenflixColors.Raised))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.fa_dragon), fontFamily = FontAwesome, fontSize = 22.sp, color = Color.White)
            if (isTv) {
                Text("Ember Road", fontSize = BenflixType.rowName, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    "Pixel adventure: carry a dragon egg across a wild land. Android phones can join in.",
                    fontSize = BenflixType.body,
                    color = BenflixColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Column {
                    Text("Ember Road", fontSize = BenflixType.lead, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Pixel adventure. Android phones can join in.", fontSize = BenflixType.small, color = BenflixColors.Text)
                }
            }
        }
    }
}

private val EMBER_COLOURS = listOf(Color(0xFFB8281E), Color(0xFF2B1B4D))
