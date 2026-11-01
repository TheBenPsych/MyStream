package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
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
)

private fun e(
    title: String,
    year: Int,
    must: Boolean = false,
    alt: List<String> = emptyList(),
) = OrderEntry(title, year, must, alt)

/** Big franchises, in story order, with the ones you really need marked must-watch */
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
            e("Loki", 2021),
            e("WandaVision", 2021),
            e("The Falcon and the Winter Soldier", 2021),
            e("Shang-Chi and the Legend of the Ten Rings", 2021),
            e("Eternals", 2021),
            e("Spider-Man: Far From Home", 2019, true),
            e("Spider-Man: No Way Home", 2021, true),
            e("Doctor Strange in the Multiverse of Madness", 2022),
            e("Hawkeye", 2021),
            e("Moon Knight", 2022),
            e("Black Panther: Wakanda Forever", 2022),
            e("She-Hulk: Attorney at Law", 2022),
            e("Ms. Marvel", 2022),
            e("Thor: Love and Thunder", 2022),
            e("Ant-Man and the Wasp: Quantumania", 2023),
            e("Guardians of the Galaxy Vol. 3", 2023, true),
            e("Secret Invasion", 2023),
            e("The Marvels", 2023),
            e("Echo", 2024),
            e("Agatha All Along", 2024),
            e("Deadpool & Wolverine", 2024, true, listOf("Deadpool and Wolverine")),
            e("Daredevil: Born Again", 2025),
            e("Captain America: Brave New World", 2025),
            e("Thunderbolts", 2025, alt = listOf("Thunderbolts*")),
            e("Ironheart", 2025),
            e("The Fantastic Four: First Steps", 2025),
        ),
    ),
    STAR_WARS(
        "Star Wars",
        listOf(
            e("The Phantom Menace", 1999, true),
            e("Attack of the Clones", 2002, true),
            e("Revenge of the Sith", 2005, true),
            e("Solo: A Star Wars Story", 2018),
            e("Andor", 2022),
            e("Rogue One: A Star Wars Story", 2016, true),
            e("A New Hope", 1977, true, listOf("Star Wars")),
            e("The Empire Strikes Back", 1980, true),
            e("Return of the Jedi", 1983, true),
            e("The Mandalorian", 2019),
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
            e("The Hobbit: An Unexpected Journey", 2012),
            e("The Hobbit: The Desolation of Smaug", 2013),
            e("The Hobbit: The Battle of the Five Armies", 2014),
            e("The Lord of the Rings: The Fellowship of the Ring", 2001, true),
            e("The Lord of the Rings: The Two Towers", 2002, true),
            e("The Lord of the Rings: The Return of the King", 2003, true),
        ),
    ),
}

/** A franchise entry found on the server, with its place in both orders */
data class OrderedItem(
    val item: BaseItem,
    val storyNumber: Int,
    val year: Int,
    val mustWatch: Boolean,
    /** When it first came out (epoch day), for release order; null if the server doesn't know */
    val releaseDay: Long? = null,
)

data class FranchiseList(
    val found: List<OrderedItem>,
    val missing: Int,
)

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
                                .filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.SERIES }
                        val byName = all.groupBy { norm(it.name.orEmpty()) }
                        Franchise.entries.associateWith { franchise ->
                            val found =
                                franchise.entries
                                    .mapIndexedNotNull { i, entry ->
                                        val key = norm(entry.title)
                                        val keys = listOf(key) + entry.alt.map { norm(it) }

                                        fun yearOk(y: Int?) = y == null || kotlin.math.abs(y - entry.year) <= 1
                                        val dto =
                                            keys.firstNotNullOfOrNull { k -> byName[k]?.firstOrNull { yearOk(it.productionYear) } }
                                                // e.g. "Star Wars: Episode IV - A New Hope" for "A New Hope"
                                                ?: all.firstOrNull {
                                                    norm(it.name.orEmpty()).endsWith(key) &&
                                                        it.productionYear?.let { y -> yearOk(y) } == true
                                                }
                                        dto?.let {
                                            OrderedItem(
                                                item = BaseItem(it),
                                                storyNumber = i + 1,
                                                year = it.productionYear ?: entry.year,
                                                mustWatch = entry.mustWatch,
                                                releaseDay = it.premiereDate?.toLocalDate()?.toEpochDay(),
                                            )
                                        }
                                    }.distinctBy { it.item.id }
                            FranchiseList(found, franchise.entries.size - found.size)
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
 * Watch in order: big franchises (Marvel, Star Wars...) in story order or release order,
 * with a switch to show only the must-watch ones. Only what's on your server is shown.
 */
@Composable
fun WatchOrderPage(
    modifier: Modifier = Modifier,
    viewModel: WatchOrderViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsState()
    var franchiseName by rememberSaveable { mutableStateOf(Franchise.MARVEL.name) }
    var storyOrder by rememberSaveable { mutableStateOf(true) }
    var mustOnly by rememberSaveable { mutableStateOf(false) }
    val franchise = Franchise.valueOf(franchiseName)
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrderChip("Story order", storyOrder, { storyOrder = true })
            OrderChip("Release order", !storyOrder, { storyOrder = false })
            OrderChip("Must-watch only", mustOnly, { mustOnly = !mustOnly })
        }
        val list = all[franchise]
        val shown =
            list
                ?.found
                .orEmpty()
                .filter { !mustOnly || it.mustWatch }
                .let { items ->
                    if (storyOrder) {
                        items
                    } else {
                        // By the day each came out (so 2021's Black Widow comes before Shang-Chi)
                        items.sortedWith(
                            compareBy<OrderedItem>({
                                it.releaseDay ?: java.time.LocalDate
                                    .of(it.year, 12, 31)
                                    .toEpochDay()
                            })
                                .thenBy { it.storyNumber },
                        )
                    }
                }
        if (shown.isEmpty()) {
            Text("None of these are on Benflix yet.", fontSize = 18.sp)
            return@Column
        }
        LazyVerticalGrid(
            // About 6 across on a TV, 2 or 3 on a phone
            columns = GridCells.Adaptive(140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shown, key = { it.item.id }) { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        GridCard(
                            item = entry.item,
                            onClick = { viewModel.navigationManager.navigateTo(entry.item.destination()) },
                            onLongClick = {},
                            showTitle = true,
                            fillWidth = POSTER_WIDTH_PX,
                        )
                        Text(
                            text = "${shown.indexOf(entry) + 1}",
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
                    Text(
                        text = if (entry.mustWatch) "${entry.year}  ·  MUST-WATCH" else "${entry.year}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.mustWatch) MaterialTheme.colorScheme.primary else Color(0xFFA69C9D),
                    )
                }
            }
            if ((list?.missing ?: 0) > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "${list?.missing} more in this series aren't on Benflix yet.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
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
