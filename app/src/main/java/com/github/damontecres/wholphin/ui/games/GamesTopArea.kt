package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.HighScore
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.music.CoverGlow
import com.github.damontecres.wholphin.ui.music.CoverGlowColors
import com.github.damontecres.wholphin.ui.music.rememberCoverGlow
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
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
 * The top part of the Games page on TVs (and wide screens). Ben 2026-11-18 (approved games-books-top-1): words only
 * on a soft glow in the lit game's colours. No big box art on the right any more: every console's boxes have their
 * own shape, and the covers in the rows already show each one whole at its real shape.
 */
@Composable
internal fun GamesTopArea(
    preview: GamePreview?,
    height: Dp,
    modifier: Modifier = Modifier,
    /** How far the glow reaches up behind the tabs above */
    glowAbove: Dp = 0.dp,
) {
    // Glowfin games have their own two colours; box art gives its colours like the Music and Books tops
    val fromArt = rememberCoverGlow(preview?.picture?.takeIf { preview.colors.isEmpty() }, BenflixColors.Games, preview?.pictureToken)
    val glow =
        preview
            ?.colors
            ?.takeIf { it.isNotEmpty() }
            ?.let { CoverGlowColors(it.first().copy(alpha = .55f), it.last().copy(alpha = .40f)) }
            ?: fromArt
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        CoverGlow(
            glow,
            Modifier
                .fillMaxWidth()
                .offset(y = -glowAbove)
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height((height + glowAbove) * 1.6f),
        )
        if (preview != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 8.dp)
                        .widthIn(max = 620.dp),
            ) {
                Text(
                    text = preview.kicker,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preview.title,
                    color = BenflixColors.Text,
                    fontFamily = GlowfinDisplayFont,
                    fontSize = BenflixType.pageTitle,
                    lineHeight = BenflixType.pageTitle * 1.15f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    preview.line?.let {
                        Text(
                            it,
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Glowfin G1: what it works with, as word chips under the name
                if (preview.playWith.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        preview.playWith.forEach { Badge(it, playWithWord(it)) }
                    }
                }
                preview.about?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.small,
                        lineHeight = BenflixType.small * 1.35f,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (preview.top3.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.fa_trophy), fontFamily = FontAwesome, fontSize = 13.sp, color = BenflixColors.Soft)
                        preview.top3.forEach {
                            Text(
                                it,
                                color = BenflixColors.Soft,
                                fontSize = BenflixType.small,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
                val shape = RoundedCornerShape(10.dp)
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
                .background(colors.firstOrNull() ?: BenflixColors.Line, RoundedCornerShape(6.dp)),
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
