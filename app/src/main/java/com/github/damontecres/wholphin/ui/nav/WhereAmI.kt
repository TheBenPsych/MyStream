package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Benflix Idea Box 24 "Where am I" line (phones): a small "Movies › Action › Die Hard" line under the top
 * bar on deeper pages. Tap a part to jump back to it. Titles get their names from the posters you
 * passed on the way (each poster remembers its name when it's touched or focused).
 */
object WhereAmI {
    private val names = ConcurrentHashMap<UUID, String>()

    fun remember(item: BaseItem) {
        val name = item.name ?: return
        if (names.size > MAX_NAMES) names.clear()
        names[item.id] = name
    }

    /** The short name of one page, or null when it has none worth showing */
    fun label(destination: Destination): String? =
        when (destination) {
            is Destination.Home -> "Home"
            is Destination.Search -> "Search"
            is Destination.Requests -> "Requests"
            is Destination.MyBenflix -> "My Benflix"
            is Destination.Favorites -> "My List"
            is Destination.Settings -> "Settings"
            is Destination.ServerStatus -> "Server"
            is Destination.DoorRowGrid -> destination.title
            is Destination.DoorBrowse -> destination.what.replaceFirstChar { it.uppercase() }
            is Destination.MediaItem -> names[destination.itemId]
            is Destination.SeriesOverview -> names[destination.itemId]
            is Destination.BookTitle, is Destination.BookAbout -> null
            else -> destination.door()?.title
        }

    private const val MAX_NAMES = 500
}

/** Each step of the back stack with a name, oldest first (the same name twice in a row is shown once) */
fun whereAmISteps(
    stack: List<Destination>,
    maxSteps: Int = MAX_STEPS,
): List<Pair<Int, String>> =
    stack
        .mapIndexedNotNull { i, d -> WhereAmI.label(d)?.let { i to it } }
        .fold(mutableListOf<Pair<Int, String>>()) { acc, step ->
            if (acc.lastOrNull()?.second == step.second) acc[acc.lastIndex] = step else acc.add(step)
            acc
        }.takeLast(maxSteps)

/** The page behind this one, by name ("Action"), for the named Back button; null when it has no name */
fun backName(stack: List<Destination>): String? = stack.getOrNull(stack.size - 2)?.let { WhereAmI.label(it) }

/**
 * Shown when there are at least two pages behind this one. [stack] is the whole back stack, oldest first.
 */
@Composable
fun WhereAmILine(
    stack: List<Destination>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Approved rx-shortcrumb: narrow screens show just the last two parts, so it never wraps */
    maxSteps: Int = MAX_STEPS,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    padded: Boolean = true,
) {
    if (stack.size < 3) return
    val all = whereAmISteps(stack, maxSteps)
    if (all.size < 2) return
    // The line never scrolls: it used to slide to its end, which cut the first name in half behind the Back
    // button ("Boy Is a Realist", Ben's TV 2026-11-18). Now the oldest parts are left out until the rest
    // fits, and a name that is still too long ends in "…".
    BoxWithConstraints(modifier.then(if (padded) Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp) else Modifier)) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxPx = constraints.maxWidth
        val steps =
            remember(all, maxPx, fontSize) {
                val gap = with(density) { 6.dp.roundToPx() }
                val style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold)
                val sep = measurer.measure("›", style).size.width + gap * 2

                fun width(list: List<Pair<Int, String>>) =
                    list.sumOf { measurer.measure(it.second, style, maxLines = 1).size.width } + sep * (list.size - 1)
                var shown = all
                while (shown.size > 2 && width(shown) > maxPx) shown = shown.drop(1)
                shown
            }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            steps.forEachIndexed { n, (index, name) ->
                val last = index == stack.lastIndex
                if (n > 0) Text("›", color = BenflixColors.Muted, fontSize = fontSize, maxLines = 1)
                Text(
                    text = name,
                    color = if (last) BenflixColors.Text else BenflixColors.Muted,
                    fontSize = fontSize,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        if (n == steps.lastIndex) {
                            Modifier.weight(1f, fill = false).then(if (last) Modifier else Modifier.clickable { onPick(index) })
                        } else {
                            Modifier
                                .widthIn(max = maxWidth * .45f)
                                .clickable { onPick(index) }
                        },
                )
            }
        }
    }
}

private const val MAX_STEPS = 4
