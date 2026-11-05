package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome

/** One spot on the Arcade wall */
internal sealed interface WallItem {
    data class Game(
        val game: BuiltInGame,
    ) : WallItem

    data object Surprise : WallItem
}

/**
 * The Arcade wall: every built-in game as a same-size tile (the one you played last comes first),
 * plus a "Surprise me" tile. TV: four tiles per row. Phone: two per row.
 */
@Composable
internal fun ArcadeWall(
    viewModel: GamesViewModel,
    isTv: Boolean,
    focusRequester: FocusRequester,
) {
    val last = remember { viewModel.lastPlayed() }
    val tiles =
        remember(last) {
            val games = listOfNotNull(last) + BuiltInGame.entries.filter { it != last }
            games.map { WallItem.Game(it) } + WallItem.Surprise
        }
    val gap = if (isTv) 16.dp else 10.dp
    val columns = if (isTv) 4 else 2

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = (maxWidth - gap * (columns - 1)) / columns
        val tileHeight = if (isTv) unit * .6f else 112.dp
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.padding(vertical = if (isTv) 12.dp else 4.dp),
        ) {
            tiles.chunked(columns).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { c, item ->
                        val mod =
                            Modifier
                                .size(width = unit, height = tileHeight)
                                .then(if (r == 0 && c == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        when (item) {
                            is WallItem.Game -> {
                                GameTile(
                                    game = item.game,
                                    best = viewModel.best(item.game),
                                    isTv = isTv,
                                    onClick = { viewModel.open(item.game) },
                                    modifier = mod,
                                )
                            }

                            WallItem.Surprise -> {
                                SurpriseTile(isTv = isTv, onClick = { viewModel.surprise(viewModel.featured()) }, modifier = mod)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A built-in game's tile: the game's picture, its name and your best score */
@Composable
internal fun GameTile(
    game: BuiltInGame,
    best: Int,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        modifier = modifier,
    ) {
        val textMeasurer = rememberTextMeasurer()
        // A still picture of the game, drawn by the game itself
        val preview = remember(game) { game.create() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(game.colors)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                preview.preparePreview(size.width, size.height)
                with(preview) { draw(textMeasurer) }
            }
            // Fade the picture behind the name so it stays easy to read
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to game.colors[1].copy(alpha = .95f),
                            0.45f to game.colors[1].copy(alpha = .6f),
                            0.75f to game.colors[1].copy(alpha = .15f),
                            1f to Color.Black.copy(alpha = .35f),
                        ),
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(if (isTv) 12.dp else 10.dp),
            ) {
                Text(
                    game.title,
                    fontSize = if (isTv) 20.sp else 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    lineHeight = if (isTv) 22.sp else 19.sp,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.weight(1f))
                Text(
                    text = if (best > 0) "Best $best" else "New",
                    fontSize = if (isTv) 12.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The "Surprise me" tile: starts a random built-in game */
@Composable
internal fun SurpriseTile(
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .25f)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C21), focusedContainerColor = Color(0xFF2A2A31)),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
        ) {
            Text(stringResource(R.string.fa_shuffle), fontFamily = FontAwesome, fontSize = 22.sp, color = Color(0xFFE50914))
            Text("Surprise me", fontSize = if (isTv) 16.sp else 17.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("A random game", fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
        }
    }
}

/**
 * "Your best scores": every built-in game with this profile's best, highest first.
 * TV: one slim strip across the page, a cell per game. Phone: a simple list.
 */
internal enum class PlayWith { REMOTE, CONTROLLER, PHONE }

/** A small round icon on a game card: remote, controller or phone */
@Composable
internal fun PlayIcon(kind: PlayWith) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(24.dp)
                .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(50)),
    ) {
        when (kind) {
            PlayWith.REMOTE -> {
                RemoteIcon(Modifier.size(width = 6.dp, height = 12.dp))
            }

            PlayWith.CONTROLLER -> {
                Text(stringResource(R.string.fa_gamepad), fontFamily = FontAwesome, fontSize = 11.sp, color = Color.White)
            }

            PlayWith.PHONE -> {
                Text(stringResource(R.string.fa_mobile_screen), fontFamily = FontAwesome, fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

/**
 * A small "works with" chip: an icon plus a word, e.g. a controller icon with "Controller"
 */
@Composable
internal fun Badge(
    kind: PlayWith,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
            Modifier
                .background(Color.White.copy(alpha = .1f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        when (kind) {
            PlayWith.REMOTE -> {
                RemoteIcon(Modifier.size(width = 8.dp, height = 15.dp))
            }

            PlayWith.CONTROLLER -> {
                Text(stringResource(R.string.fa_gamepad), fontFamily = FontAwesome, fontSize = 13.sp, color = Color.White)
            }

            PlayWith.PHONE -> {
                Text(stringResource(R.string.fa_mobile_screen), fontFamily = FontAwesome, fontSize = 13.sp, color = Color.White)
            }
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * The page's intro line plus one set of "works with" chips. Every game here works with a TV remote,
 * a controller, or a phone, so the chips live here once instead of on every tile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorksWith(
    text: String,
    isTv: Boolean,
) {
    @Composable
    fun Chips() {
        Badge(PlayWith.REMOTE, "Remote")
        Badge(PlayWith.CONTROLLER, "Controller")
        Badge(PlayWith.PHONE, "Android phone")
    }
    if (isTv) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("Works with", fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
            Chips()
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chips()
            }
        }
    }
}

/**
 * A colourful tile for the movie games (Guess the movie, Daily riddle, Higher or Lower).
 * The whole title and description always show: nothing is cut off. On TV the icon sits next to the
 * title so the text gets the full width of the tile.
 */
@Composable
internal fun MovieGameTile(
    title: String,
    subtitle: String,
    icon: Int,
    colors: List<Color>,
    onClick: () -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier = modifier,
    ) {
        @Composable
        fun IconBox(boxSize: Int) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(boxSize.dp)
                        .background(Color.White.copy(alpha = .16f), RoundedCornerShape(10.dp)),
            ) {
                Text(stringResource(icon), fontFamily = FontAwesome, fontSize = (boxSize * .45f).sp, color = Color.White)
            }
        }
        if (isTv) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .heightIn(min = 120.dp)
                        .background(Brush.linearGradient(colors))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBox(40)
                    Text(title, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Text(subtitle, fontSize = 14.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = .9f))
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 104.dp)
                        .background(Brush.linearGradient(colors))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                IconBox(48)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(title, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(subtitle, fontSize = 14.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = .9f))
                }
            }
        }
    }
}

/**
 * A simple TV remote drawing: a rounded stick with a round OK button and two small buttons
 */
@Composable
internal fun RemoteIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color.White, cornerRadius = CornerRadius(w * .35f, w * .35f))
        drawCircle(Color.Black, radius = w * .3f, center = Offset(w / 2, h * .27f))
        drawCircle(Color.Black, radius = w * .12f, center = Offset(w / 2, h * .6f))
        drawCircle(Color.Black, radius = w * .12f, center = Offset(w / 2, h * .8f))
    }
}
