package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Glowfin loading screens (feel check 3, Ben 2026-11-21): while a page loads it shows plain grey blocks
 * in the shape of the page that is coming, so nothing jumps when the pictures arrive. Five shapes cover
 * every page. The blocks never move, which keeps menus smooth on slow TVs.
 */
enum class LoadingShape {
    /** Rows of posters with a title above each: Home, Movies, Shows, My watching */
    ROWS,

    /** A wall of posters: See all, My List, collections, genres, actor pages, search results */
    WALL,

    /** A title page: big picture area, name, facts, buttons, story */
    TITLE,

    /** A list of lines: requests, downloads, playlists, watch order */
    LIST,

    /** The Live TV guide: channel names down the side, programmes across */
    GUIDE,
}

@Composable
fun LoadingBlocks(
    shape: LoadingShape,
    modifier: Modifier = Modifier,
) {
    when (shape) {
        LoadingShape.ROWS -> StillPosters(modifier)
        LoadingShape.WALL -> StillWall(modifier)
        LoadingShape.TITLE -> StillTitle(modifier)
        LoadingShape.LIST -> StillList(modifier)
        LoadingShape.GUIDE -> StillGuide(modifier)
    }
}

@Composable
private fun Block(
    width: Dp,
    height: Dp,
    small: Boolean = false,
) {
    Box(
        Modifier
            .size(width = width, height = height)
            .background(BenflixColors.Raised, if (small) BenflixShapes.Small else BenflixShapes.Card),
    )
}

@Composable
private fun StillWall(modifier: Modifier = Modifier) {
    val isTv = rememberIsTv()
    val posterWidth = if (isTv) 120.dp else 100.dp
    val gap = if (isTv) 16.dp else 10.dp
    val edge = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val across = ((maxWidth - edge * 2 + gap) / (posterWidth + gap)).toInt().coerceIn(1, 12)
        val down = (maxHeight / (posterWidth * 1.5f + gap)).toInt().coerceIn(1, 6) + 1
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.padding(start = edge, top = if (isTv) 40.dp else 20.dp),
        ) {
            repeat(down) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(across) { Block(posterWidth, posterWidth * 1.5f) }
                }
            }
        }
    }
}

@Composable
private fun StillTitle(modifier: Modifier = Modifier) {
    val isTv = rememberIsTv()
    val edge = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val wide = (maxWidth - edge * 2).coerceAtMost(620.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = edge, top = if (isTv) 110.dp else 180.dp),
        ) {
            Block(wide * 0.7f, if (isTv) 44.dp else 34.dp, small = true)
            Block(wide * 0.45f, 18.dp, small = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                Block(if (isTv) 150.dp else 130.dp, 46.dp)
                Block(46.dp, 46.dp)
                Block(46.dp, 46.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Block(wide, 14.dp, small = true)
                Block(wide * 0.92f, 14.dp, small = true)
                Block(wide * 0.6f, 14.dp, small = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 14.dp)) {
                repeat(if (isTv) 4 else 3) { Block(if (isTv) 96.dp else 72.dp, 20.dp, small = true) }
            }
        }
    }
}

@Composable
private fun StillList(modifier: Modifier = Modifier) {
    val isTv = rememberIsTv()
    val edge = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val line = if (isTv) 64.dp else 56.dp
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val wide = (maxWidth - edge * 2).coerceAtMost(720.dp)
        val count = (maxHeight / (line + 12.dp)).toInt().coerceIn(1, 10) + 1
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = edge, top = if (isTv) 40.dp else 20.dp),
        ) {
            repeat(count) { index ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Block(line, line)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Block((wide - line - 14.dp) * (if (index % 2 == 0) 0.6f else 0.45f), 16.dp, small = true)
                        Block((wide - line - 14.dp) * 0.3f, 12.dp, small = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun StillGuide(modifier: Modifier = Modifier) {
    val isTv = rememberIsTv()
    val edge = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val line = if (isTv) 56.dp else 52.dp
    val name = if (isTv) 150.dp else 96.dp
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val rest = (maxWidth - edge - name - 8.dp).coerceAtLeast(120.dp)
        val count = (maxHeight / (line + 8.dp)).toInt().coerceIn(1, 12) + 1
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = edge, top = if (isTv) 40.dp else 20.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(18.dp)) {
                Box(Modifier.width(name + 8.dp))
                Block(rest * 0.2f, 14.dp, small = true)
            }
            repeat(count) { index ->
                // Programmes of different lengths, so it reads as a guide and not a table
                val first =
                    if (index % 3 == 0) {
                        0.5f
                    } else if (index % 3 == 1) {
                        0.3f
                    } else {
                        0.65f
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Block(name, line)
                    Block(rest * first, line)
                    Block(rest * (1f - first), line)
                }
            }
        }
    }
}
