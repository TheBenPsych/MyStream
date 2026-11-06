package com.github.damontecres.wholphin.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RED = Color(0xFFE5484D)
private val PANEL = Color(0xFF18181C)
private val CHIP = Color(0xFF2C2C33)
private val MUTED = Color(0xFFA1A1AA)

/** Benflix Requests filters: decade */
enum class FindDecade(
    val label: String,
    val from: String?,
    val to: String?,
) {
    NEW("New this year", "${Year.now().value}-01-01", null),
    TENS("2010s", "2010-01-01", "2019-12-31"),
    NOUGHTS("2000s", "2000-01-01", "2009-12-31"),
    NINETIES("90s", "1990-01-01", "1999-12-31"),
    EIGHTIES("80s", "1980-01-01", "1989-12-31"),
    OLDER("Older", null, "1979-12-31"),
}

/** Benflix Requests filters: sort order (TMDB's names, which differ for movies and shows) */
enum class FindSort(
    val label: String,
    val movie: String,
    val tv: String,
) {
    POPULAR("Most popular", "popularity.desc", "popularity.desc"),
    RATED("Highest rated", "vote_average.desc", "vote_average.desc"),
    NEWEST("Newest", "primary_release_date.desc", "first_air_date.desc"),
    OLDEST("Oldest", "primary_release_date.asc", "first_air_date.asc"),
}

/** Benflix Requests filters: the language a title was made in */
enum class FindLanguage(
    val label: String,
    val code: String,
) {
    KOREAN("Korean", "ko"),
    JAPANESE("Japanese", "ja"),
    SPANISH("Spanish", "es"),
    FRENCH("French", "fr"),
    HINDI("Indian (Hindi)", "hi"),
    CHINESE("Chinese", "zh"),
    ITALIAN("Italian", "it"),
    GERMAN("German", "de"),
}

/** Everything picked in the Filters panel */
data class FindFilters(
    val genre: FindGenre? = null,
    val decade: FindDecade? = null,
    val minScore: Int? = null,
    val sort: FindSort? = null,
    val language: FindLanguage? = null,
) {
    /** More than a genre: show one list of results instead of the usual shelves */
    val narrowed: Boolean get() = decade != null || minScore != null || sort != null || language != null

    val isEmpty: Boolean get() = genre == null && !narrowed
}

/** "MOVIE-123", used to remember requested and hidden titles */
fun DiscoverItem.requestKey(): String = "${type.name}-$id"

/** Ages that count as family-friendly */
private val FAMILY_MOVIE = setOf("G", "PG", "PG-13", "U", "12", "12A")
private val FAMILY_TV = setOf("TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG")

/** An age rating and whether its tags look grown-up, for the Family-friendly switch */
data class ItemRating(
    val age: String?,
    val adultTags: Boolean,
) {
    fun familyFriendly(type: SeerrItemType): Boolean =
        !adultTags &&
            age != null &&
            (if (type == SeerrItemType.TV) age in FAMILY_TV else age in FAMILY_MOVIE)
}

private val COMING_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** "DEC 18" for a Coming soon card, or "2027" when it's far away */
fun comingSoonNote(item: DiscoverItem): String? {
    val date = item.releaseDate ?: return null
    if (date.isBefore(LocalDate.now())) return null
    return if (date.year == LocalDate.now().year || date.isBefore(LocalDate.now().plusMonths(6))) {
        date.format(COMING_DATE).uppercase()
    } else {
        date.year.toString()
    }
}

@Composable
fun RequestsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = if (selected) Color.White else CHIP,
                contentColor = if (selected) Color.Black else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier.height(40.dp),
    ) {
        Text(text = label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** A chip for a filter that's on, with an X to take it off */
@Composable
fun ActiveFilterChip(
    label: String,
    onRemove: () -> Unit,
) {
    Button(
        onClick = onRemove,
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = RED.copy(alpha = .25f),
                contentColor = Color.White,
                focusedContainerColor = RED,
                focusedContentColor = Color.White,
            ),
        modifier = Modifier.height(36.dp),
    ) {
        Text(text = "$label  ✕", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** The Filters panel: pick several things together, then press Show */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersDialog(
    section: FindSection,
    current: FindFilters,
    onDismiss: () -> Unit,
    onApply: (FindFilters) -> Unit,
    showLanguage: Boolean,
) {
    var draft by remember(current) { mutableStateOf(current) }
    val genres =
        remember(section) {
            FindGenres.filter { if (section == FindSection.MOVIES) it.movieId != null else it.tvId != null }
        }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            Text("Filters", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

            FilterGroup("Genre") {
                RequestsChip("Any", draft.genre == null, { draft = draft.copy(genre = null) })
                genres.forEach { g ->
                    RequestsChip(g.label, draft.genre == g, { draft = draft.copy(genre = if (draft.genre == g) null else g) })
                }
            }
            FilterGroup("When") {
                RequestsChip("Any", draft.decade == null, { draft = draft.copy(decade = null) })
                FindDecade.entries.forEach { d ->
                    RequestsChip(d.label, draft.decade == d, { draft = draft.copy(decade = if (draft.decade == d) null else d) })
                }
            }
            FilterGroup("Score at least") {
                RequestsChip("Any", draft.minScore == null, { draft = draft.copy(minScore = null) })
                listOf(6, 7, 8).forEach { score ->
                    RequestsChip("$score+", draft.minScore == score, {
                        draft = draft.copy(minScore = if (draft.minScore == score) null else score)
                    })
                }
            }
            FilterGroup("Sort by") {
                FindSort.entries.forEach { sort ->
                    val picked = (draft.sort ?: FindSort.POPULAR) == sort
                    RequestsChip(sort.label, picked, { draft = draft.copy(sort = sort.takeIf { it != FindSort.POPULAR }) })
                }
            }
            if (showLanguage) {
                FilterGroup("Made in") {
                    RequestsChip("Any language", draft.language == null, { draft = draft.copy(language = null) })
                    FindLanguage.entries.forEach { lang ->
                        RequestsChip(lang.label, draft.language == lang, {
                            draft = draft.copy(language = if (draft.language == lang) null else lang)
                        })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { onApply(draft) }) { Text("Show") }
                Button(onClick = { onApply(FindFilters()) }) { Text("Clear all") }
                Button(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MUTED)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/**
 * Search box on the Requests page.
 *
 * Phones type as soon as you tap it. On TV it can be passed over with the remote without the keyboard
 * popping up: press OK on it to start typing, Back to put the keyboard away.
 */
@Composable
fun RequestsSearchBox(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search any movie or show to ask for",
) {
    val isTv = rememberIsTv()
    val keyboardController = LocalSoftwareKeyboardController.current
    var typing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    BackHandler(isTv && focused && typing) {
        typing = false
        keyboardController?.hide()
    }
    OutlinedTextField(
        value = query,
        onValueChange = {
            typing = true
            onQuery(it)
        },
        singleLine = true,
        readOnly = isTv && !typing,
        placeholder = { androidx.compose.material3.Text(placeholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0xFF3F3F46),
                focusedContainerColor = PANEL,
                unfocusedContainerColor = PANEL,
                cursorColor = RED,
            ),
        shape = RoundedCornerShape(12.dp),
        modifier =
            modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) typing = false
                }.onPreviewKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                    if (isTv && ok && !typing && event.type == KeyEventType.KeyUp) {
                        typing = true
                        keyboardController?.show()
                        true
                    } else {
                        false
                    }
                },
    )
}

/**
 * Big banner across the top of a Requests tab: the top trending titles that aren't on Benflix,
 * turning every 7 seconds like Home.
 */
@Composable
fun RequestsBanner(
    items: List<DiscoverItem>,
    onAsk: (DiscoverItem) -> Unit,
    onInfo: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    // Lands on the banner's first button, so the page can start there like Home does
    focusRequester: FocusRequester? = null,
) {
    if (items.isEmpty()) return
    var index by remember(items.size) { mutableIntStateOf(0) }
    LaunchedEffect(index, items.size) {
        if (items.size > 1) {
            delay(BANNER_TURN_MS)
            index = (index + 1) % items.size
        }
    }
    val item = items[index.coerceIn(0, items.lastIndex)]
    val asked = item.availability == SeerrAvailability.PENDING || item.availability == SeerrAvailability.PROCESSING
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // At least this tall, and taller when the words need it (narrow phones), so the
                // buttons are never cut off
                .heightIn(min = 300.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PANEL),
    ) {
        AsyncImage(
            model = item.backDropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = .92f),
                            .55f to Color.Black.copy(alpha = .45f),
                            1f to Color.Transparent,
                        ),
                    ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 520.dp)
                    .padding(24.dp),
        ) {
            Text(
                text = "TRENDING, NOT ON BENFLIX",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                color = RED,
            )
            Text(
                text = item.title.orEmpty(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts =
                listOfNotNull(
                    item.releaseDate?.year?.toString(),
                    if (item.type == SeerrItemType.TV) "Show" else "Movie",
                    item.score?.let { "★ ${"%.1f".format(it)}" },
                ).joinToString("  ·  ")
            Text(text = facts, fontSize = 14.sp, color = MUTED)
            Text(
                text = item.overview.orEmpty(),
                fontSize = 15.sp,
                color = Color(0xFFDADADF),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                val askModifier = if (!asked && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
                val infoModifier = if (asked && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
                Button(onClick = { if (!asked) onAsk(item) }, enabled = !asked, modifier = askModifier) {
                    Text(if (asked) "Requested" else "Ask for it")
                }
                Button(onClick = { onInfo(item) }, modifier = infoModifier) { Text("More info") }
            }
            if (items.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
                    items.indices.forEach { i ->
                        Box(
                            modifier =
                                Modifier
                                    .size(width = if (i == index) 20.dp else 7.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(if (i == index) RED else Color.White.copy(alpha = .35f)),
                        )
                    }
                }
            }
        }
    }
}

private const val BANNER_TURN_MS = 7_000L

/** Hold OK (or long-press) on a card: Ask for it, More info, Hide this */
@Composable
fun QuickMenuDialog(
    item: DiscoverItem,
    onDismiss: () -> Unit,
    onAsk: () -> Unit,
    onInfo: () -> Unit,
    onHide: () -> Unit,
) {
    val canAsk = item.availability == SeerrAvailability.UNKNOWN || item.availability == SeerrAvailability.DELETED
    BasicDialog(onDismissRequest = onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .padding(22.dp),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(144.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CHIP),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = item.title.orEmpty(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (canAsk) {
                    Button(onClick = onAsk) { Text("Ask for it") }
                }
                Button(onClick = onInfo) { Text("More info") }
                Button(onClick = onHide) { Text("Hide this") }
            }
        }
    }
}

/** Asked, Approved, Downloading, Ready, with the current step lit up */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestSteps(
    approved: Boolean,
    downloading: Boolean,
    percent: Int?,
    ready: Boolean,
    declined: Boolean,
) {
    if (declined) return
    val steps =
        listOf(
            "Asked" to true,
            "Approved" to (approved || downloading || ready),
            (if (percent != null && !ready) "Downloading $percent%" else "Downloading") to (downloading || ready),
            "Ready" to ready,
        )
    // Benflix: wraps onto a second line on narrow phone screens instead of squeezing the words
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        steps.forEachIndexed { i, (label, done) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (i > 0) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 14.dp, height = 2.dp)
                                .background(if (done) Color(0xFF3DD68C) else Color(0xFF3F3F46)),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (done) Color(0xFF3DD68C) else Color(0xFF3F3F46)),
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
                    color = if (done) Color.White else MUTED,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** A heading between groups in the request lists */
@Composable
fun RequestGroupTitle(
    text: String,
    count: Int,
) {
    Text(
        text = "$text  ($count)",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

/** "Are you sure?" before approving every waiting request */
@Composable
fun ApproveAllDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .widthIn(max = 460.dp)
                    .padding(24.dp),
        ) {
            Text("Approve all $count?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Everything waiting for you gets a yes and starts downloading.",
                fontSize = 16.sp,
                color = MUTED,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConfirm) { Text("Approve all") }
                Button(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/** One season in the season picker */
data class PickableSeason(
    val number: Int,
    val name: String,
    val episodes: Int?,
    // Already on Benflix, downloading or asked for
    val taken: Boolean,
)

/** A show and its seasons, waiting for the person to pick which ones to ask for */
data class SeasonPick(
    val item: DiscoverItem,
    val seasons: List<PickableSeason>,
)

/** Pick which seasons of a show to ask for */
@Composable
fun SeasonPickDialog(
    pick: SeasonPick,
    onDismiss: () -> Unit,
    onAsk: (List<Int>) -> Unit,
) {
    val open = remember(pick) { pick.seasons.filter { !it.taken }.map { it.number } }
    var chosen by remember(pick) { mutableStateOf(setOf<Int>()) }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(22.dp),
        ) {
            Text(
                text = pick.item.title.orEmpty(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Pick the seasons you want", fontSize = 15.sp, color = MUTED)
            if (open.size > 1) {
                val all = chosen.containsAll(open)
                Button(onClick = { chosen = if (all) emptySet() else open.toSet() }) {
                    Text(if (all) "Clear all" else "All seasons (${open.size})", maxLines = 1)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                pick.seasons.forEach { season ->
                    val picked = season.number in chosen
                    Button(
                        onClick = { chosen = if (picked) chosen - season.number else chosen + season.number },
                        enabled = !season.taken,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val extra =
                            when {
                                season.taken -> "already asked for or on Benflix"
                                season.episodes != null && season.episodes > 0 -> "${season.episodes} episodes"
                                else -> null
                            }
                        Text(
                            text = listOfNotNull((if (picked) "✓  " else "") + season.name, extra).joinToString("  ·  "),
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onAsk(chosen.sorted()) },
                    enabled = chosen.isNotEmpty(),
                ) {
                    Text(
                        when (chosen.size) {
                            0 -> "Ask"
                            1 -> "Ask for 1 season"
                            else -> "Ask for ${chosen.size} seasons"
                        },
                        maxLines = 1,
                    )
                }
                Button(onClick = onDismiss) { Text("Cancel", maxLines = 1) }
            }
        }
    }
}
