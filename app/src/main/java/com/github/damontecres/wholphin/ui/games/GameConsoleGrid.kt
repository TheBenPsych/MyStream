package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.ServerGame
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import kotlinx.coroutines.launch

/** One heading in the console grid (null = no heading) and its games */
internal data class GridSection(
    val heading: String?,
    val games: List<ServerGame>,
)

/** All of one console's games in the order the sort asks for, with headings where they help */
internal fun consoleGridSections(
    games: List<ServerGame>,
    sort: GameSort,
    recentIds: List<Int>,
    playTimes: Map<Int, Long>,
): List<GridSection> {
    val sorted = games.sortedBy { it.name.lowercase() }
    return when (sort) {
        GameSort.AZ -> {
            if (sorted.size <= 24) {
                listOf(GridSection(null, sorted))
            } else {
                sorted
                    .groupBy { letterOf(it.name) }
                    .toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
                    .map { (letter, list) -> GridSection(letter, list) }
            }
        }

        GameSort.RECENT -> {
            val byId = sorted.associateBy { it.id }
            val played = recentIds.mapNotNull { byId[it] }
            val rest = sorted.filter { it.id !in recentIds }
            listOfNotNull(
                played.takeIf { it.isNotEmpty() }?.let { GridSection("Played lately", it) },
                rest.takeIf { it.isNotEmpty() }?.let { GridSection(if (played.isEmpty()) null else "Not played yet", it) },
            )
        }

        GameSort.MOST -> {
            val played = sorted.filter { (playTimes[it.id] ?: 0L) >= 60_000L }.sortedByDescending { playTimes[it.id] ?: 0L }
            val rest = sorted.filter { (playTimes[it.id] ?: 0L) < 60_000L }
            listOfNotNull(
                played.takeIf { it.isNotEmpty() }?.let { GridSection("Most played", it) },
                rest.takeIf { it.isNotEmpty() }?.let { GridSection(if (played.isEmpty()) null else "Hardly played", it) },
            )
        }

        GameSort.GENRE -> {
            val groups = sorted.groupBy { it.genres.firstOrNull() ?: "Other" }
            groups.keys
                .sortedWith(compareBy({ it == "Other" }, { -(groups[it]?.size ?: 0) }))
                .map { GridSection(it, groups.getValue(it)) }
        }

        GameSort.BEST -> {
            val rated = sorted.filter { it.rating > 0.0 }.sortedByDescending { it.rating }
            val rest = sorted.filter { it.rating <= 0.0 }
            listOfNotNull(
                rated.takeIf { it.isNotEmpty() }?.let { GridSection("Best loved", it) },
                rest.takeIf { it.isNotEmpty() }?.let { GridSection(if (rated.isEmpty()) null else "No score yet", it) },
            )
        }

        GameSort.YEAR -> {
            sorted
                .groupBy { if (it.year > 0) it.year.toString() else "Unknown year" }
                .toSortedMap(compareBy<String> { it == "Unknown year" }.thenByDescending { it })
                .map { (year, list) -> GridSection(year, list) }
        }
    }
}

private fun letterOf(name: String): String {
    val c = name.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c.toString() else "#"
}

/**
 * Benflix redesign (Games console page): a grid like All movies, with headings and, for A to Z,
 * a letter column on the right that jumps the grid. The grid scrolls by itself (it has its own
 * height), so even a console with hundreds of games only draws what's on screen.
 */
@Composable
internal fun ConsoleGameGrid(
    sections: List<GridSection>,
    cardWidth: Dp,
    height: Dp,
    showLetters: Boolean,
    card: @Composable (ServerGame) -> Unit,
) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Where each heading sits in the grid, for the letter column
    val headingIndex =
        remember(sections) {
            var index = 0
            buildMap {
                sections.forEach { s ->
                    if (s.heading != null) {
                        put(s.heading, index)
                        index++
                    }
                    index += s.games.size
                }
            }
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height),
    ) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Adaptive(cardWidth),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            sections.forEach { section ->
                section.heading?.let { heading ->
                    item(key = "h-$heading", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = heading,
                            fontSize = BenflixType.rowName,
                            fontWeight = FontWeight.Bold,
                            color = BenflixColors.Text,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                items(section.games, key = { "g-${section.heading}-${it.id}" }) { card(it) }
            }
        }
        if (showLetters && headingIndex.size > 1) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight(),
            ) {
                headingIndex.forEach { (letter, index) ->
                    LetterButton(letter) { scope.launch { state.scrollToItem(index) } }
                }
            }
        }
    }
}

@Composable
private fun LetterButton(
    letter: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Soft,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.size(width = 28.dp, height = 20.dp),
    ) {
        Text(
            text = letter,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
