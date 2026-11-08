package com.github.damontecres.wholphin.ui.door

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.time.format.TextStyle as DateTextStyle
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** How the All movies / All shows grid is ordered */
enum class TitleSort(
    val label: String,
) {
    /** The order the list came in (See all / My List) */
    AS_LISTED("Recently added"),
    NEWEST_EPISODE("Newest episode"),
    NEWEST("Newest added"),
    A_TO_Z("A to Z"),
    YEAR("Release year"),
    SCORE("Score"),
    LENGTH("Length"),
}

/** The Anime chip: tap to go round the three */
enum class AnimeFilter(
    val label: String,
) {
    ALL("Anime: All"),
    ONLY("Anime only"),
    HIDE("Hide anime"),
}

/** The Shows Watching chip */
enum class WatchingFilter(
    val label: String,
) {
    ALL("Watching: All"),
    NOT_STARTED("Not started"),
    WATCHING("Watching"),
    FINISHED("Finished"),
}

private sealed interface GridEntry {
    data class Heading(
        val text: String,
    ) : GridEntry

    data class Title(
        val item: BaseItem,
    ) : GridEntry
}

/**
 * Benflix redesign: the combined title grid used by All movies, All shows, See all and My List.
 * Slim chips (sort, Anime, Watching), a slim info line and a small search box on top; the grid has
 * headings that follow the sort (letters for A to Z). The chips are remembered per person.
 */
@Composable
fun TitleGrid(
    title: String,
    items: List<BaseItem>,
    animeIds: Set<UUID>,
    movies: Boolean,
    /** Where the picks are saved, e.g. "<user>-MOVIES-all" */
    rememberKey: String,
    sorts: List<TitleSort>,
    onClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var sort by remember(rememberKey) {
        mutableStateOf(
            prefs
                .getString("$rememberKey-sort", null)
                ?.let { name -> sorts.firstOrNull { it.name == name } }
                ?: sorts.first(),
        )
    }
    var anime by remember(rememberKey) {
        mutableStateOf(AnimeFilter.entries.firstOrNull { it.name == prefs.getString("$rememberKey-anime", null) } ?: AnimeFilter.ALL)
    }
    var watching by remember(rememberKey) {
        mutableStateOf(
            WatchingFilter.entries.firstOrNull { it.name == prefs.getString("$rememberKey-watching", null) } ?: WatchingFilter.ALL,
        )
    }
    var search by remember { mutableStateOf("") }
    var pickSort by remember { mutableStateOf(false) }
    val save = { key: String, value: String -> prefs.edit { putString("$rememberKey-$key", value) } }

    val shown =
        remember(items, animeIds, sort, anime, watching, search) {
            items
                .asSequence()
                .filter {
                    when (anime) {
                        AnimeFilter.ALL -> true
                        AnimeFilter.ONLY -> it.id in animeIds
                        AnimeFilter.HIDE -> it.id !in animeIds
                    }
                }.filter {
                    when (watching) {
                        WatchingFilter.ALL -> true
                        WatchingFilter.NOT_STARTED -> it.data.userData?.played != true && it.data.userData?.lastPlayedDate == null
                        WatchingFilter.WATCHING -> it.data.userData?.played != true && it.data.userData?.lastPlayedDate != null
                        WatchingFilter.FINISHED -> it.data.userData?.played == true
                    }
                }.filter { search.isBlank() || it.name?.contains(search.trim(), ignoreCase = true) == true }
                .toList()
                .sortedFor(sort)
        }
    val entries = remember(shown, sort) { withHeadings(shown, sort) }
    val letters =
        remember(entries, sort) {
            if (sort != TitleSort.A_TO_Z) {
                emptyList()
            } else {
                entries.mapIndexedNotNull { index, entry -> (entry as? GridEntry.Heading)?.let { it.text to index } }
            }
        }

    val isTv = rememberIsTv()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTv && items.isNotEmpty()) first.tryRequestFocus() }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = title,
            color = BenflixColors.Text,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(end = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item { GridChip(label = "Sort: ${sort.label}", onClick = { pickSort = true }) }
            if (animeIds.isNotEmpty()) {
                item {
                    GridChip(
                        label = anime.label,
                        selected = anime != AnimeFilter.ALL,
                        onClick = {
                            anime = AnimeFilter.entries[(anime.ordinal + 1) % AnimeFilter.entries.size]
                            save("anime", anime.name)
                        },
                    )
                }
            }
            if (!movies) {
                item {
                    GridChip(
                        label = watching.label,
                        selected = watching != WatchingFilter.ALL,
                        onClick = {
                            watching = WatchingFilter.entries[(watching.ordinal + 1) % WatchingFilter.entries.size]
                            save("watching", watching.name)
                        },
                    )
                }
            }
            item { SearchBox(value = search, onValueChange = { search = it }) }
        }
        val count = shown.size
        val noun = if (movies) (if (count == 1) "movie" else "movies") else (if (count == 1) "show" else "shows")
        val watched = shown.count { it.data.userData?.played == true }
        Text(
            text = "%,d %s · %,d watched · %s".format(count, noun, watched, sort.label),
            color = BenflixColors.Muted,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        if (letters.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(end = 16.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                items(letters, key = { it.first }) { (letter, index) ->
                    GridChip(
                        label = letter,
                        small = true,
                        onClick = { scope.launch { gridState.scrollToItem(index) } },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            Text(
                text = if (items.isEmpty()) "Nothing here yet" else "Nothing matches",
                color = BenflixColors.Muted,
                fontSize = BenflixType.body,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (isTv) 8 else ((maxWidth / 110.dp).toInt()).coerceIn(3, 8)
            val width = (maxWidth - 12.dp * (columns - 1) - 8.dp) / columns
            var firstTitle = -1
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, end = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                entries.forEachIndexed { index, entry ->
                    when (entry) {
                        is GridEntry.Heading -> {
                            item(key = "h-${entry.text}-$index", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = entry.text,
                                    color = BenflixColors.Soft,
                                    fontSize = BenflixType.lead,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        is GridEntry.Title -> {
                            if (firstTitle < 0) firstTitle = index
                            val isFirst = firstTitle == index
                            item(key = entry.item.id.toString()) {
                                Box(contentAlignment = Alignment.TopCenter) {
                                    DoorPoster(
                                        item = entry.item,
                                        width = width,
                                        showName = !isTv,
                                        onClick = { onClick(entry.item) },
                                        modifier = if (isFirst) Modifier.focusRequester(first) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (pickSort) {
        BenflixChoiceDialog(
            title = "Sort by",
            choices =
                sorts.map { option ->
                    BenflixChoice(
                        label = option.label,
                        selected = option == sort,
                        onClick = {
                            sort = option
                            save("sort", option.name)
                            scope.launch { gridState.scrollToItem(0) }
                        },
                    )
                },
            onDismissRequest = { pickSort = false },
        )
    }
}

private fun List<BaseItem>.sortedFor(sort: TitleSort): List<BaseItem> =
    when (sort) {
        TitleSort.AS_LISTED -> {
            this
        }

        TitleSort.NEWEST_EPISODE -> {
            sortedByDescending { it.data.dateLastMediaAdded ?: it.data.dateCreated }
        }

        TitleSort.NEWEST -> {
            sortedByDescending { it.data.dateCreated }
        }

        TitleSort.A_TO_Z -> {
            sortedBy { (it.sortName.ifBlank { it.name.orEmpty() }).lowercase() }
        }

        TitleSort.YEAR -> {
            sortedByDescending {
                it.data.premiereDate?.toLocalDate()
                    ?: it.data.productionYear?.let { y -> LocalDate.of(y, 1, 1) }
            }
        }

        TitleSort.SCORE -> {
            sortedByDescending { it.data.communityRating ?: -1f }
        }

        TitleSort.LENGTH -> {
            sortedBy { it.data.runTimeTicks ?: Long.MAX_VALUE }
        }
    }

private fun withHeadings(
    items: List<BaseItem>,
    sort: TitleSort,
): List<GridEntry> {
    if (sort == TitleSort.AS_LISTED) return items.map { GridEntry.Title(it) }
    val today = LocalDate.now()
    val out = ArrayList<GridEntry>(items.size + 40)
    var last: String? = null
    items.forEach { item ->
        val heading = headingFor(item, sort, today)
        if (heading != last) {
            out.add(GridEntry.Heading(heading))
            last = heading
        }
        out.add(GridEntry.Title(item))
    }
    return out
}

private fun headingFor(
    item: BaseItem,
    sort: TitleSort,
    today: LocalDate,
): String =
    when (sort) {
        TitleSort.AS_LISTED -> {
            ""
        }

        TitleSort.NEWEST, TitleSort.NEWEST_EPISODE -> {
            val date =
                (if (sort == TitleSort.NEWEST_EPISODE) item.data.dateLastMediaAdded ?: item.data.dateCreated else item.data.dateCreated)
                    ?.toLocalDate()
            when {
                date == null -> "Some time ago"
                !date.isBefore(today.minusDays(7)) -> "This week"
                date.year == today.year && date.month == today.month -> "This month"
                else -> date.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) + " " + date.year
            }
        }

        TitleSort.A_TO_Z -> {
            val c = (item.sortName.ifBlank { item.name.orEmpty() }).firstOrNull()?.uppercaseChar()
            if (c != null && c.isLetter()) c.toString() else "#"
        }

        TitleSort.YEAR -> {
            val year = item.data.premiereDate?.year ?: item.data.productionYear
            when {
                year == null -> "Year unknown"
                year >= today.year - 1 -> year.toString()
                else -> "${year / 10 * 10}s"
            }
        }

        TitleSort.SCORE -> {
            val score = item.data.communityRating
            when {
                score == null -> "No score"
                score >= 8f -> "8 and up"
                score >= 7f -> "7 to 8"
                score >= 6f -> "6 to 7"
                else -> "Under 6"
            }
        }

        TitleSort.LENGTH -> {
            val minutes = item.data.runTimeTicks?.let { it / 600_000_000L }
            when {
                minutes == null -> "Length unknown"
                minutes < 90 -> "Under 90 minutes"
                minutes <= 120 -> "90 minutes to 2 hours"
                else -> "Over 2 hours"
            }
        }
    }

@Composable
private fun GridChip(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    small: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) BenflixColors.Text else BenflixColors.Raised,
                contentColor = if (selected) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
                pressedContainerColor = BenflixColors.Text,
                pressedContentColor = BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 4.dp else 7.dp),
        )
    }
}

@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = BenflixColors.Text, fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(BenflixColors.Text),
        modifier =
            Modifier
                .width(180.dp)
                .onFocusChanged { focused = it.isFocused }
                .background(BenflixColors.Raised, shape)
                .border(if (focused) 3.dp else 1.dp, if (focused) BenflixColors.Text else BenflixColors.Line, shape),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Box {
                    if (value.isEmpty()) Text("Search this list", color = BenflixColors.Muted, fontSize = BenflixType.small, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

private const val PREFS = "benflix-grid"
