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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.nav.Destination

/** One tile in a Games row: name, a two-line description, and your best (Ben asked to keep descriptions) */
data class RowGame(
    val key: String,
    val title: String,
    val short: String,
    val colors: List<Color>,
    val icon: Int?,
    val badge: String,
    val onClick: () -> Unit,
)

/**
 * The Games page rows (Ben's pick A, roomier): Movie puzzles, Party and Arcade, each one sideways
 * row of tiles with the description always showing. About 4 and a half tiles per row on TV.
 */
@Composable
fun GameRows(
    viewModel: GamesViewModel,
    isTv: Boolean,
) {
    val kids = KidsMode.isActive

    fun open(id: String) = viewModel.navigationManager.navigateTo(Destination.GameInfo(id))

    fun badge(best: Int) = if (best > 0) "Best $best" else "New"

    val puzzles =
        MovieGame.entries
            .filter { it.group == MovieGameGroup.PUZZLE && it.visibleNow(kids) }
            .map { g ->
                RowGame(g.name, g.title, g.short, g.colors, g.icon, badge(g.boardKey?.let { viewModel.bestExtra(it) } ?: 0)) {
                    open("movie:${g.name}")
                }
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
                ) {
                    viewModel.navigationManager.navigateTo(Destination.PartyJoin)
                }
            } else {
                null
            },
        ) +
            MovieGame.entries
                .filter { it.group == MovieGameGroup.PARTY && it.visibleNow(kids) }
                .map { g -> RowGame(g.name, g.title, g.short, g.colors, g.icon, "Party") { open("movie:${g.name}") } }
    val arcade =
        BuiltInGame.entries.map { g ->
            RowGame(g.name, g.title, g.tagline, g.colors, null, badge(viewModel.best(g))) { open("arcade:${g.name}") }
        } +
            RowGame(
                "surprise",
                "Surprise me",
                "Starts a random arcade game.",
                listOf(Color(0xFF3A3A44), Color(0xFF16161B)),
                R.string.fa_dice,
                "Random",
            ) {
                viewModel.surprise(viewModel.featured())
            }

    GameRow("Movie puzzles", "Made from the movies and shows on Tower", puzzles, isTv)
    if (party.isNotEmpty()) GameRow("Party", "Everyone plays on their phone", party, isTv)
    GameRow("Arcade", null, arcade, isTv)
}

@Composable
private fun GameRow(
    title: String,
    sub: String?,
    games: List<RowGame>,
    isTv: Boolean,
) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            if (sub != null) {
                Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = if (isTv) 18.dp else 12.dp
            val width: Dp = if (isTv) (maxWidth - gap * 4) / 4.5f else maxWidth * 0.62f
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            ) {
                items(
                    games,
                    key = { it.key },
                ) { g -> RowTile(g, isTv, Modifier.size(width = width, height = if (isTv) width * 0.58f else 132.dp)) }
            }
        }
    }
}

@Composable
private fun RowTile(
    g: RowGame,
    isTv: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = g.onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(g.colors))
                .padding(if (isTv) 14.dp else 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.icon?.let {
                        Text(stringResource(it), fontFamily = FontAwesome, fontSize = 15.sp, color = Color.White.copy(alpha = .85f))
                    }
                    Text(
                        g.title,
                        fontSize = if (isTv) 19.sp else 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    g.short,
                    fontSize = if (isTv) 14.sp else 13.sp,
                    lineHeight = if (isTv) 18.sp else 17.sp,
                    color = Color.White.copy(alpha = .9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.weight(1f))
                Text(
                    g.badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(
                                Color.Black.copy(alpha = .5f),
                                RoundedCornerShape(6.dp),
                            ).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Ember Road as a slim banner under the leaderboards (the spot Ben picked, half as tall) */
@Composable
fun EmberBanner(
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (isTv) 64.dp else 72.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFFB8281E), Color(0xFF2B1B4D))))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.fa_dragon), fontFamily = FontAwesome, fontSize = 22.sp, color = Color.White)
            if (isTv) {
                Text("Ember Road", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    "Pixel adventure: carry a dragon egg across a wild land. Phones can join in.",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = .9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Column {
                    Text("Ember Road", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Pixel adventure. Phones can join in.", fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                }
            }
        }
    }
}
