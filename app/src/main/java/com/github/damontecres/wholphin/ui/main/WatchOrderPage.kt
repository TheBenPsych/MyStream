package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/** One movie or show in a franchise. The list order is the story order */
data class OrderEntry(
    val title: String,
    val year: Int,
    val mustWatch: Boolean = false,
    /** Other names the server might use, like the US title */
    val alt: List<String> = emptyList(),
    /** A TV show (one card for the whole show) rather than a movie */
    val show: Boolean = false,
)

/** A movie */
private fun e(
    title: String,
    year: Int,
    must: Boolean = false,
    alt: List<String> = emptyList(),
) = OrderEntry(title, year, must, alt)

/** A TV show */
private fun tv(
    title: String,
    year: Int,
    alt: List<String> = emptyList(),
) = OrderEntry(title, year, false, alt, show = true)

/** Big franchises, in story order, with the ones you really need marked essential */
enum class Franchise(
    val title: String,
    val entries: List<OrderEntry>,
) {
    MARVEL(
        "Marvel",
        listOf(
            // The official Disney+ timeline order
            e("Captain America: The First Avenger", 2011, true),
            e("Captain Marvel", 2019, true),
            e("Iron Man", 2008, true),
            e("Iron Man 2", 2010),
            e("The Incredible Hulk", 2008),
            e("Thor", 2011, true),
            e("The Avengers", 2012, true, listOf("Marvel's The Avengers", "Avengers Assemble")),
            e("Thor: The Dark World", 2013),
            e("Iron Man 3", 2013),
            e("Captain America: The Winter Soldier", 2014, true),
            e("Guardians of the Galaxy", 2014, true),
            e("Guardians of the Galaxy Vol. 2", 2017),
            e("Avengers: Age of Ultron", 2015, true),
            e("Ant-Man", 2015),
            e("Captain America: Civil War", 2016, true),
            e("Black Widow", 2021),
            e("Black Panther", 2018, true),
            e("Spider-Man: Homecoming", 2017),
            e("Doctor Strange", 2016, true),
            e("Thor: Ragnarok", 2017, true),
            e("Ant-Man and the Wasp", 2018),
            e("Avengers: Infinity War", 2018, true),
            e("Avengers: Endgame", 2019, true),
            tv("Loki", 2021),
            tv("What If...?", 2021, listOf("What If")),
            tv("WandaVision", 2021),
            tv("The Falcon and the Winter Soldier", 2021),
            e("Shang-Chi and the Legend of the Ten Rings", 2021),
            e("Eternals", 2021),
            e("Spider-Man: Far From Home", 2019, true),
            e("Spider-Man: No Way Home", 2021, true),
            e("Doctor Strange in the Multiverse of Madness", 2022),
            tv("Hawkeye", 2021),
            tv("Moon Knight", 2022),
            e("Black Panther: Wakanda Forever", 2022),
            tv("She-Hulk: Attorney at Law", 2022),
            tv("Ms. Marvel", 2022),
            e("Thor: Love and Thunder", 2022),
            e("Ant-Man and the Wasp: Quantumania", 2023),
            e("Guardians of the Galaxy Vol. 3", 2023, true),
            tv("Secret Invasion", 2023),
            e("The Marvels", 2023),
            tv("Echo", 2024),
            tv("Agatha All Along", 2024),
            e("Deadpool & Wolverine", 2024, true, listOf("Deadpool and Wolverine")),
            tv("Daredevil: Born Again", 2025),
            e("Captain America: Brave New World", 2025),
            e("Thunderbolts", 2025, alt = listOf("Thunderbolts*")),
            tv("Ironheart", 2025),
            e("The Fantastic Four: First Steps", 2025),
        ),
    ),
    STAR_WARS(
        "Star Wars",
        listOf(
            tv("The Acolyte", 2024),
            e("The Phantom Menace", 1999, true),
            e("Attack of the Clones", 2002, true),
            e("The Clone Wars", 2008),
            tv("The Clone Wars", 2008),
            e("Revenge of the Sith", 2005, true),
            tv("The Bad Batch", 2021),
            e("Solo: A Star Wars Story", 2018),
            tv("Obi-Wan Kenobi", 2022),
            tv("Andor", 2022),
            tv("Rebels", 2014),
            e("Rogue One: A Star Wars Story", 2016, true),
            e("A New Hope", 1977, true, listOf("Star Wars")),
            e("The Empire Strikes Back", 1980, true),
            e("Return of the Jedi", 1983, true),
            tv("The Mandalorian", 2019),
            tv("The Book of Boba Fett", 2021),
            tv("Ahsoka", 2023),
            tv("Skeleton Crew", 2024),
            tv("Resistance", 2018),
            e("The Force Awakens", 2015, true),
            e("The Last Jedi", 2017, true),
            e("The Rise of Skywalker", 2019, true),
        ),
    ),
    WIZARDING(
        "Harry Potter",
        listOf(
            e("Fantastic Beasts and Where to Find Them", 2016),
            e("Fantastic Beasts: The Crimes of Grindelwald", 2018),
            e("Fantastic Beasts: The Secrets of Dumbledore", 2022),
            e("Harry Potter and the Philosopher's Stone", 2001, true, listOf("Harry Potter and the Sorcerer's Stone")),
            e("Harry Potter and the Chamber of Secrets", 2002, true),
            e("Harry Potter and the Prisoner of Azkaban", 2004, true),
            e("Harry Potter and the Goblet of Fire", 2005, true),
            e("Harry Potter and the Order of the Phoenix", 2007, true),
            e("Harry Potter and the Half-Blood Prince", 2009, true),
            e("Harry Potter and the Deathly Hallows: Part 1", 2010, true),
            e("Harry Potter and the Deathly Hallows: Part 2", 2011, true),
        ),
    ),
    MIDDLE_EARTH(
        "Middle-earth",
        listOf(
            tv("The Lord of the Rings: The Rings of Power", 2022, listOf("The Rings of Power")),
            e("The Lord of the Rings: The War of the Rohirrim", 2024, alt = listOf("The War of the Rohirrim")),
            e("The Hobbit: An Unexpected Journey", 2012),
            e("The Hobbit: The Desolation of Smaug", 2013),
            e("The Hobbit: The Battle of the Five Armies", 2014),
            e("The Lord of the Rings: The Fellowship of the Ring", 2001, true),
            e("The Lord of the Rings: The Two Towers", 2002, true),
            e("The Lord of the Rings: The Return of the King", 2003, true),
        ),
    ),
}

/** One spot in a franchise's order: the movie or show on the server, or null when it isn't there */
data class OrderedItem(
    val entry: OrderEntry,
    val item: BaseItem?,
    val storyNumber: Int,
    val year: Int,
    /** When it first came out (epoch day), for release order */
    val releaseDay: Long,
)

data class FranchiseList(
    val all: List<OrderedItem>,
) {
    val onServer: Int get() = all.count { it.item != null }
}

/** The three tabs */
enum class OrderTab(
    val title: String,
    val blurb: String,
) {
    FULL("Full story", "Everything, in the order the story happens"),
    ESSENTIALS("Essentials", "Just the ones you need, in story order"),
    RELEASE("Release order", "Everything, in the order it came out"),
}

/** Letters and digits only, lower case, so "Spider-Man: Homecoming" and "Spider Man Homecoming" match */
private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

@HiltViewModel
class WatchOrderViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** null while loading */
        private val _lists = MutableStateFlow<Map<Franchise, FranchiseList>?>(null)
        val lists: StateFlow<Map<Franchise, FranchiseList>?> = _lists

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                _lists.value =
                    try {
                        val all =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = serverRepository.currentUser?.id,
                                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                        recursive = true,
                                        // Every movie on its own, never bundled into its collection (like "Thor Collection")
                                        collapseBoxSetItems = false,
                                        sortBy = listOf(ItemSortBy.SORT_NAME),
                                        fields = HomeItemFields,
                                    ),
                                ).content.items
                        val movies = all.filter { it.type == BaseItemKind.MOVIE }
                        val shows = all.filter { it.type == BaseItemKind.SERIES }
                        val moviesByName = movies.groupBy { norm(it.name.orEmpty()) }
                        val showsByName = shows.groupBy { norm(it.name.orEmpty()) }
                        Franchise.entries.associateWith { franchise ->
                            val used = mutableSetOf<java.util.UUID>()
                            FranchiseList(
                                franchise.entries.mapIndexed { i, entry ->
                                    val pool = if (entry.show) shows else movies
                                    val byName = if (entry.show) showsByName else moviesByName
                                    val key = norm(entry.title)
                                    val keys = listOf(key) + entry.alt.map { norm(it) }

                                    fun yearOk(y: Int?) = y == null || kotlin.math.abs(y - entry.year) <= 1
                                    val dto =
                                        keys.firstNotNullOfOrNull { k ->
                                            byName[k]?.firstOrNull { it.id !in used && yearOk(it.productionYear) }
                                        }
                                            // e.g. "Star Wars: Episode IV - A New Hope" for "A New Hope"
                                            ?: pool.firstOrNull {
                                                it.id !in used &&
                                                    norm(it.name.orEmpty()).endsWith(key) &&
                                                    it.productionYear?.let { y -> yearOk(y) } == true
                                            }
                                    dto?.let { used += it.id }
                                    OrderedItem(
                                        entry = entry,
                                        item = dto?.let { BaseItem(it) },
                                        storyNumber = i + 1,
                                        year = dto?.productionYear ?: entry.year,
                                        releaseDay =
                                            dto?.premiereDate?.toLocalDate()?.toEpochDay()
                                                ?: java.time.LocalDate
                                                    .of(entry.year, 12, 31)
                                                    .toEpochDay(),
                                    )
                                },
                            )
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Watch in order failed")
                        emptyMap()
                    }
            }
        }
    }

/**
 * Watch in order: big franchises (Marvel, Star Wars...) as Full story, Essentials or Release order.
 * Everything in the series is shown; ones not on your server are dimmed "Not on Benflix" cards,
 * so the order never has gaps.
 */
@Composable
fun WatchOrderPage(
    modifier: Modifier = Modifier,
    viewModel: WatchOrderViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsState()
    var franchiseName by rememberSaveable { mutableStateOf(Franchise.MARVEL.name) }
    var tabName by rememberSaveable { mutableStateOf(OrderTab.FULL.name) }
    val franchise = Franchise.valueOf(franchiseName)
    val tab = OrderTab.valueOf(tabName)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(lists != null) {
        delay(80)
        firstFocus.tryRequestFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(top = 16.dp, start = 8.dp),
    ) {
        Text(
            text = "WATCH IN ORDER",
            fontFamily = BenflixTitleFont,
            fontSize = 48.sp,
            letterSpacing = 1.sp,
        )
        val all = lists
        if (all == null) {
            LoadingPage(Modifier.fillMaxSize())
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Franchise.entries.forEachIndexed { i, f ->
                OrderChip(
                    text = f.title,
                    on = f == franchise,
                    onClick = { franchiseName = f.name },
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        val list = all[franchise]
        val shown =
            list
                ?.all
                .orEmpty()
                .let { items ->
                    when (tab) {
                        OrderTab.FULL -> items
                        OrderTab.ESSENTIALS -> items.filter { it.entry.mustWatch }
                        OrderTab.RELEASE -> items.sortedWith(compareBy<OrderedItem>({ it.releaseDay }).thenBy { it.storyNumber })
                    }
                }
        // Tabs: the one you're on is white with a red line under it
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrderTab.entries.forEach { t ->
                OrderTabButton(t.title, t == tab, { tabName = t.name })
            }
            Text(
                text = "${tab.blurb}  ·  ${shown.count { it.item != null }} of ${shown.size} on Benflix",
                fontSize = 13.sp,
                color = Color(0xFFB5ABAC),
            )
        }
        LazyVerticalGrid(
            // About 7 across on a TV, 2 or 3 on a phone
            columns = GridCells.Adaptive(130.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(shown, key = { _, it -> it.storyNumber }) { index, entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        val item = entry.item
                        if (item != null) {
                            GridCard(
                                item = item,
                                onClick = { viewModel.navigationManager.navigateTo(item.destination()) },
                                onLongClick = {},
                                showTitle = true,
                                fillWidth = POSTER_WIDTH_PX,
                            )
                        } else {
                            MissingCard(entry.entry)
                        }
                        Text(
                            text = "${index + 1}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .androidxBackground(),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (entry.entry.show) "${entry.year} · SHOW" else "${entry.year}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB5ABAC),
                        )
                        if (entry.entry.mustWatch && tab != OrderTab.ESSENTIALS) {
                            Text(
                                text = "ESSENTIAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = .5.sp,
                                color = Color.White,
                                modifier =
                                    Modifier
                                        .background(Color(0xFFE5303A), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A dimmed card for something in the series that isn't on your server */
@Composable
private fun MissingCard(entry: OrderEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = .3f)), RoundedCornerShape(6.dp))
                    .padding(8.dp),
        ) {
            Text(
                text = "NOT ON\nBENFLIX",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = .45f),
            )
        }
        Text(
            text = entry.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = .45f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OrderTabButton(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (on) Color.White else Color(0xFFB5ABAC),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        // As wide as the word, so the red line sits right under it
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(IntrinsicSize.Max)) {
            Text(text = text, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .background(if (on) Color(0xFFE5303A) else Color.Transparent)
                    .padding(top = 3.dp),
            )
        }
    }
}

private fun Modifier.androidxBackground(): Modifier =
    this
        .then(
            Modifier.background(Color.White, RoundedCornerShape(4.dp)),
        ).padding(horizontal = 8.dp, vertical = 2.dp)

@Composable
private fun OrderChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = if (on) Color(0xFFE5303A) else Color.White.copy(alpha = .1f),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier,
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val POSTER_WIDTH_PX = 320
