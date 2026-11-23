package com.github.damontecres.wholphin.ui.discover

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.detail.title.ScorePill
import com.github.damontecres.wholphin.ui.detail.title.StripPart
import com.github.damontecres.wholphin.ui.door.DoorTab
import com.github.damontecres.wholphin.ui.preferences.GlowfinSettingRow
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShade
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Glowfin new look, Requests (Ben approved 2026-11-22, every tab YES). Drawn for the TCL TVs first:
 * - the tabs are see-through pills in the header's own row, then a round Search pill, a round Filters pill and,
 *   for Ben, the yellow Waiting pill with its number
 * - Movies and Shows land straight on a row; the top only describes the cover you're on: its art fills the page,
 *   the name, a facts line, a coloured status tag, a 2-line story and what OK does
 * - covers already asked for get a small coloured dot (amber waiting, aqua on its way, green here, red couldn't find)
 * - Filters open in one right-hand panel instead of chip rows at the bottom
 * Nothing grows and nothing blurs, so it stays smooth on the weak TCL TVs.
 */

/** The kinds of status tag, each with its own colour */
enum class RqTagKind(
    val dot: Color,
) {
    /** Not on Glowfin yet, nobody asked */
    NOT_YET(Color(0xFFF2B33D)),

    /** Asked, waiting for Ben's yes */
    WAITING(Color(0xFFF2B33D)),

    /** Ben said yes, Tower is getting it */
    ON_WAY(Glowfin.Aqua),

    /** Not out yet */
    OUT_LATER(Glowfin.Violet),

    /** On Glowfin, but a show is missing episodes, or Ben couldn't find it */
    PROBLEM(Color(0xFFFF6B6B)),

    /** On Glowfin */
    HERE(Color(0xFF57D68D)),
}

/** A coloured status tag: its kind and words ("On its way · asked by Mia 2 days ago") */
data class RqTag(
    val kind: RqTagKind,
    val words: String,
)

private val OUT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * The status tag for a cover. [askedBy] is "Mia 2 days ago" when the ask is known on this device,
 * [cantFind] = Ben looked but couldn't find it.
 */
fun DiscoverItem.rqTag(
    isAdmin: Boolean,
    cantFind: Boolean,
    askedBy: String?,
): RqTag {
    val by = askedBy?.let { " · asked by $it" }.orEmpty()
    val out = releaseDate?.takeIf { it.isAfter(LocalDate.now()) }
    return when {
        cantFind && availability != SeerrAvailability.AVAILABLE -> {
            RqTag(RqTagKind.PROBLEM, "Ben couldn't find it yet")
        }

        // New seasons for shows you watch: "Season 4 is out" (the show itself is on Glowfin)
        newSeasonOut() -> {
            RqTag(RqTagKind.NOT_YET, subtitle.orEmpty())
        }

        availability == SeerrAvailability.AVAILABLE -> {
            RqTag(RqTagKind.HERE, "On Glowfin")
        }

        availability == SeerrAvailability.PARTIALLY_AVAILABLE -> {
            RqTag(RqTagKind.PROBLEM, if (type == SeerrItemType.TV) "Missing episodes" else "Partly on Glowfin")
        }

        availability == SeerrAvailability.PENDING -> {
            RqTag(RqTagKind.WAITING, (if (isAdmin) "Waiting for you" else "Waiting for Ben") + by)
        }

        availability == SeerrAvailability.PROCESSING && out != null -> {
            RqTag(RqTagKind.OUT_LATER, "Out ${out.format(OUT_DATE)} · Glowfin gets it then$by")
        }

        availability == SeerrAvailability.PROCESSING -> {
            RqTag(RqTagKind.ON_WAY, "On its way$by")
        }

        out != null -> {
            RqTag(RqTagKind.OUT_LATER, "Out ${out.format(OUT_DATE)}" + if (out.year != LocalDate.now().year) ", ${out.year}" else "")
        }

        else -> {
            RqTag(RqTagKind.NOT_YET, "Not on Glowfin yet")
        }
    }
}

/** A show on the New seasons row whose newer season nobody has asked for yet */
private fun DiscoverItem.newSeasonOut(): Boolean =
    type == SeerrItemType.TV &&
        availability != SeerrAvailability.PENDING &&
        availability != SeerrAvailability.PROCESSING &&
        subtitle?.let { it.endsWith(" is out") || it.endsWith(" are out") } == true

/** The coloured dot on a cover already asked for (none for covers nobody asked for) */
fun DiscoverItem.rqDot(cantFind: Boolean): Color? =
    when {
        newSeasonOut() -> null
        cantFind && availability != SeerrAvailability.AVAILABLE -> RqTagKind.PROBLEM.dot
        availability == SeerrAvailability.PENDING -> RqTagKind.WAITING.dot
        availability == SeerrAvailability.PROCESSING -> RqTagKind.ON_WAY.dot
        availability == SeerrAvailability.PARTIALLY_AVAILABLE -> RqTagKind.PROBLEM.dot
        availability == SeerrAvailability.AVAILABLE -> RqTagKind.HERE.dot
        else -> null
    }

/** The status tag: a see-through pill in its colour, with a dot and the words */
@Composable
fun RqStatusTag(
    tag: RqTag,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val color = tag.kind.dot
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .background(Glowfin.Ink.copy(alpha = .55f), shape)
                .background(color.copy(alpha = .18f), shape)
                .border(1.dp, color.copy(alpha = .65f), shape)
                .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = tag.words,
            color = lerpWhite(color),
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The tag's words: its colour, made very light so it reads on any art */
private fun lerpWhite(c: Color): Color =
    Color(
        red = c.red + (1f - c.red) * .72f,
        green = c.green + (1f - c.green) * .72f,
        blue =
            c.blue + (1f - c.blue) * .72f,
    )

/** The small coloured dot at a cover's top right, with a dark edge so it shows on any poster */
@Composable
fun RqCoverDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(14.dp)
            .background(Glowfin.Ink.copy(alpha = .8f), CircleShape)
            .padding(2.dp)
            .background(color, CircleShape),
    )
}

/**
 * The cover the top describes: follows [focused] once you've stopped on a cover for a moment, so moving
 * along a row doesn't load a picture for every cover you pass
 */
@Composable
fun rememberShownCover(
    focused: State<DiscoverItem?>,
    fallback: DiscoverItem?,
): DiscoverItem? {
    var shown by remember { mutableStateOf<DiscoverItem?>(null) }
    val target = focused.value
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (shown != null) delay(SHOW_DELAY_MS)
        shown = target
    }
    return shown ?: fallback
}

/**
 * The art of the cover you're on, filling the whole page (up behind the tabs too). A soft shade on the left
 * for the words and a fade at the bottom for the rows; the rest stays bright.
 */
@Composable
fun RequestArt(
    item: DiscoverItem?,
    modifier: Modifier = Modifier,
) = RequestArtUrl(item?.backDropUrl, modifier)

/** [RequestArt] for a picture that isn't a Requests item (an AniList banner on the Anime tab) */
@Composable
fun RequestArtUrl(
    url: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = url, animationSpec = tween(ART_FADE_MS), label = "requestArt") { shownUrl ->
            if (shownUrl != null) {
                AsyncImage(
                    model = shownUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(ART_LEFT))
        Box(Modifier.fillMaxSize().background(BenflixShade.bottom(.42f)))
    }
}

/**
 * The words at the top about the cover you're on: the name, "Movie · 2014 · PG-13", the coloured status tag,
 * a 2-line story and what OK does
 */
@Composable
fun RequestTopWords(
    item: DiscoverItem,
    tag: RqTag,
    age: String?,
    modifier: Modifier = Modifier,
) {
    val facts =
        listOfNotNull(
            when (item.type) {
                SeerrItemType.MOVIE -> if (item.isAnime) "Anime movie" else "Movie"
                SeerrItemType.TV -> if (item.isAnime) "Anime" else "Show"
                else -> null
            },
            item.releaseDate?.year?.toString(),
            age,
        ).joinToString(" · ")
    RequestTopText(
        title = item.title.orEmpty(),
        facts = facts,
        tags = listOf(tag),
        story = item.overview,
        hint =
            if (tag.kind == RqTagKind.NOT_YET || (tag.kind == RqTagKind.OUT_LATER && item.availability == SeerrAvailability.UNKNOWN)) {
                ASK_HINT
            } else {
                "Asked already, so OK just opens it"
            },
        modifier = modifier,
    )
}

/** What OK does on a cover you can still ask for */
const val ASK_HINT = "OK opens it · hold OK to ask right away"

/** The top words themselves, for any kind of cover */
@Composable
fun RequestTopText(
    title: String,
    facts: String,
    tags: List<RqTag>,
    story: String?,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.widthIn(max = 520.dp)) {
        Text(
            text = title,
            color = BenflixColors.Text,
            fontFamily = GlowfinDisplayFont,
            fontSize = BenflixType.pageTitle,
            lineHeight = BenflixType.pageTitle * 1.1f,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = SOFT_EDGE),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (facts.isNotEmpty()) {
            Text(
                text = facts,
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = SOFT_EDGE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { RqStatusTag(it) } }
        }
        if (!story.isNullOrBlank()) {
            Text(
                text = story,
                color = BenflixColors.Text.copy(alpha = .92f),
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small * 1.35f,
                style = TextStyle(shadow = SOFT_EDGE),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hint != null) {
            Text(
                text = hint,
                color = BenflixColors.Text.copy(alpha = .62f),
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = SOFT_EDGE),
                maxLines = 1,
            )
        }
    }
}

/** The score pill at the top right: the age rating, then IMDb and the tomato when they're known */
@Composable
fun RequestScorePill(
    age: String?,
    scores: String?,
    modifier: Modifier = Modifier,
) {
    val parts =
        remember(scores) {
            scores
                ?.split(" · ")
                ?.mapNotNull { part ->
                    when {
                        part.startsWith("IMDb ") -> StripPart("imdb", part.removePrefix("IMDb ").trim())
                        part.startsWith("🍅 ") -> StripPart("rotten", part.removePrefix("🍅 ").trim())
                        else -> null
                    }
                }.orEmpty()
        }
    ScorePill(rating = age, scores = parts, modifier = modifier)
}

/*
 * The pills along the top: the tabs, a little gap, then the round Search and Filters pills and Ben's yellow
 * Waiting pill. On TV they sit in the header's own row; phones get them as one sideways row under the header.
 */

/** The top pills press these, and the tab that's showing answers (like [RequestsSearch]) */
internal object RequestsFilters {
    private val _presses = MutableStateFlow(0)
    val presses: StateFlow<Int> = _presses

    fun ask() {
        _presses.value += 1
    }
}

/** Runs [open] when Filters on the top pills is pressed while this tab is showing */
@Composable
internal fun OnRequestsFilters(open: () -> Unit) {
    val presses by RequestsFilters.presses.collectAsState()
    val seen = remember { RequestsFilters.presses.value }
    LaunchedEffect(presses) {
        if (presses != seen) open()
    }
}

@Composable
internal fun RequestsPillBar(
    tabs: List<String>,
    selected: Int,
    onTab: (Int) -> Unit,
    showSearch: Boolean,
    showFilters: Boolean,
    waitingCount: Int?,
    onWaiting: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val picked = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp),
        contentPadding = PaddingValues(horizontal = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .then(picked.getOrNull(selected)?.let { Modifier.focusRestorer(it) } ?: Modifier),
    ) {
        itemsIndexed(tabs, key = { i, _ -> "tab-$i" }) { i, label ->
            DoorTab(
                label = label,
                selected = i == selected,
                wide = wide,
                onClick = { if (i != selected) onTab(i) },
                modifier = Modifier.focusRequester(picked[i]),
            )
        }
        item(key = "gap") { Spacer(Modifier.width(if (wide) 10.dp else 4.dp)) }
        if (showSearch) {
            item(key = "search") { RoundPill(BenflixIcon.SEARCH, "Search", wide, RequestsSearch::ask) }
        }
        if (showFilters) {
            item(key = "filters") { RoundPill(BenflixIcon.FILTER, "Filters", wide, RequestsFilters::ask) }
        }
        if (waitingCount != null && waitingCount > 0) {
            item(key = "waiting") { WaitingPill(waitingCount, wide, onWaiting) }
        }
    }
}

/** A round see-through pill with one icon (Search, Filters) */
@Composable
private fun RoundPill(
    icon: BenflixIcon,
    name: String,
    wide: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Glowfin.Ink.copy(alpha = .35f),
                contentColor = Glowfin.Text,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
                pressedContainerColor = Color.White,
                pressedContentColor = Glowfin.Ink,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .14f)), shape = CircleShape),
                focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Focus), shape = CircleShape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            Modifier
                .size(if (wide) 34.dp else 32.dp)
                .onFocusChanged { focused = it.isFocused },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BenflixIconImage(icon, if (focused) Glowfin.Ink else Glowfin.Text, if (wide) 17.dp else 16.dp, contentDescription = name)
        }
    }
}

/** Ben's yellow Waiting pill: a dot and how many asks are waiting for his yes. Opens Manage. */
@Composable
private fun WaitingPill(
    count: Int,
    wide: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = WAIT_FILL,
                contentColor = WAIT_TEXT,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
                pressedContainerColor = Color.White,
                pressedContentColor = Glowfin.Ink,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, RqTagKind.WAITING.dot.copy(alpha = .6f)), shape = shape),
                focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Focus), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.height(if (wide) 34.dp else 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
        ) {
            Box(Modifier.size(7.dp).background(RqTagKind.WAITING.dot, CircleShape))
            Text(
                text = if (wide) "$count waiting" else count.toString(),
                fontFamily = GlowfinDisplayFont,
                fontSize = if (wide) BenflixType.body else BenflixType.small,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/*
 * The Filters panel (Movies and Shows): one tall panel on the right, like Settings. Switches flip straight away;
 * Genre, Decade, Score, Sort and Language open their choices in the same panel. Back closes it.
 */

/** Everything the Filters panel changes on a Movies or Shows tab */
data class RequestFilterState(
    val filters: FindFilters,
    val hideOwned: Boolean,
    val hideAsked: Boolean,
    val familyOnly: Boolean,
)

@Composable
fun RequestFiltersPanel(
    section: FindSection,
    state: RequestFilterState,
    isAdmin: Boolean,
    showLanguage: Boolean,
    onChange: (RequestFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = rememberIsTv()
    var picking by remember { mutableStateOf<String?>(null) }
    val genres =
        remember(section) {
            FindGenres.filter { if (section == FindSection.MOVIES) it.movieId != null else it.tvId != null }
        }
    Dialog(
        onDismissRequest = { if (picking != null) picking = null else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .then(if (isTv) Modifier.width(380.dp) else Modifier.fillMaxWidth(.92f))
                        .background(PANEL_FADE)
                        .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 12.dp),
            ) {
                val f = state.filters
                val title =
                    when (picking) {
                        null -> "Filters"
                        else -> picking.orEmpty()
                    }
                Text(
                    text = title,
                    color = Glowfin.Text,
                    fontFamily = GlowfinDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = BenflixType.rowName,
                    maxLines = 1,
                )
                Text(
                    text =
                        if (picking ==
                            null
                        ) {
                            "${if (section == FindSection.MOVIES) "Movies" else "Shows"} tab. Changes show straight away."
                        } else {
                            "Back goes to Filters"
                        },
                    color = Glowfin.Dim,
                    fontSize = BenflixType.small,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                val first = remember(picking) { FocusRequester() }
                LaunchedEffect(picking) { first.tryRequestFocus() }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (picking) {
                        null -> {
                            item(key = "owned") {
                                GlowfinSettingRow(
                                    title = "Hide what's already on Glowfin",
                                    value = onOff(state.hideOwned),
                                    explain = "Shows with missing episodes always stay",
                                    onClick = { onChange(state.copy(hideOwned = !state.hideOwned)) },
                                    modifier = Modifier.focusRequester(first),
                                )
                            }
                            item(key = "asked") {
                                GlowfinSettingRow(
                                    title = "Hide what's already asked for",
                                    value = onOff(state.hideAsked),
                                    explain = null,
                                    onClick = { onChange(state.copy(hideAsked = !state.hideAsked)) },
                                )
                            }
                            item(key = "family") {
                                GlowfinSettingRow(
                                    title = "Family-friendly only",
                                    value = onOff(state.familyOnly),
                                    explain = if (isAdmin) "On by default for everyone but you" else "Up to PG-13 and TV-PG",
                                    onClick = { onChange(state.copy(familyOnly = !state.familyOnly)) },
                                )
                            }
                            item(key = "genre") {
                                GlowfinSettingRow("Genre", f.genre?.label ?: "Any", null, { picking = "Genre" })
                            }
                            item(key = "decade") {
                                GlowfinSettingRow("Decade", f.decade?.label ?: "Any", null, { picking = "Decade" })
                            }
                            item(key = "score") {
                                GlowfinSettingRow(
                                    "Score at least",
                                    f.minScore?.let { "$it+" } ?: "Any",
                                    null,
                                    { picking = "Score at least" },
                                )
                            }
                            item(key = "sort") {
                                GlowfinSettingRow("Sort by", (f.sort ?: FindSort.POPULAR).label, null, { picking = "Sort by" })
                            }
                            if (showLanguage) {
                                item(key = "lang") {
                                    GlowfinSettingRow("Spoken language", f.language?.label ?: "Any", null, { picking = "Spoken language" })
                                }
                            }
                            if (!f.isEmpty) {
                                item(key = "clear") {
                                    GlowfinSettingRow("Clear genre, decade, score and sort", null, null, {
                                        onChange(state.copy(filters = FindFilters()))
                                    })
                                }
                            }
                        }

                        else -> {
                            val choices: List<Pair<String, FindFilters>> =
                                when (picking) {
                                    "Genre" -> {
                                        listOf("Any" to f.copy(genre = null)) + genres.map { it.label to f.copy(genre = it) }
                                    }

                                    "Decade" -> {
                                        listOf("Any" to f.copy(decade = null)) +
                                            FindDecade.entries.map { it.label to f.copy(decade = it) }
                                    }

                                    "Score at least" -> {
                                        listOf("Any" to f.copy(minScore = null)) +
                                            listOf(6, 7, 8).map { "$it+" to f.copy(minScore = it) }
                                    }

                                    "Sort by" -> {
                                        FindSort.entries.map { it.label to f.copy(sort = it.takeIf { s -> s != FindSort.POPULAR }) }
                                    }

                                    else -> {
                                        listOf("Any" to f.copy(language = null)) +
                                            FindLanguage.entries.map { it.label to f.copy(language = it) }
                                    }
                                }
                            items(choices, key = { it.first }) { (label, next) ->
                                val on = next == f
                                GlowfinSettingRow(
                                    title = label,
                                    value = if (on) "✓" else null,
                                    explain = null,
                                    onClick = {
                                        onChange(state.copy(filters = next))
                                        picking = null
                                    },
                                    modifier =
                                        if (on ||
                                            (label == choices.first().first && choices.none { it.second == f })
                                        ) {
                                            Modifier.focusRequester(first)
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Back closes it",
                    color = Glowfin.Dim,
                    fontSize = BenflixType.tag,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun onOff(on: Boolean) = if (on) "On" else "Off"

/** The panel: nearly solid on the right, fading in from the left so the page shows faintly beside it */
private val PANEL_FADE =
    Brush.horizontalGradient(
        0f to Glowfin.Deep.copy(alpha = .9f),
        .2f to Glowfin.Deep.copy(alpha = .97f),
        1f to Glowfin.Deep,
    )

/** The soft shade on the left of the art, only as wide as the words */
private val ART_LEFT =
    Brush.horizontalGradient(
        0f to BenflixColors.Tray.copy(alpha = .82f),
        .38f to BenflixColors.Tray.copy(alpha = .5f),
        .65f to Color.Transparent,
    )

private val SOFT_EDGE = Shadow(color = Color.Black.copy(alpha = .6f), offset = Offset(0f, 1.5f), blurRadius = 4f)
private val WAIT_FILL = Color(0xFFF2B33D).copy(alpha = .2f)
private val WAIT_TEXT = Color(0xFFFFE3A6)
private const val ART_FADE_MS = 400
private const val SHOW_DELAY_MS = 450L
