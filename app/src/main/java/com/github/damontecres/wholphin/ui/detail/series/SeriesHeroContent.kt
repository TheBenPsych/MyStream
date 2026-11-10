package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.ShowProgress
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.EpisodeStandIn
import com.github.damontecres.wholphin.ui.cards.hasOwnPicture
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.detail.AskShelf
import com.github.damontecres.wholphin.ui.detail.title.StripPart
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleCastRow
import com.github.damontecres.wholphin.ui.detail.title.TitleFacts
import com.github.damontecres.wholphin.ui.detail.title.TitleMoreItem
import com.github.damontecres.wholphin.ui.detail.title.TitleMorePanel
import com.github.damontecres.wholphin.ui.detail.title.TitleOverview
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitleParentsGuide
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitlePosterRow
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.detail.title.rememberScoreParts
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.door.progress
import com.github.damontecres.wholphin.ui.premiereDay
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

/** What the show page can do */
class SeriesHeroActions(
    val playNext: () -> Unit,
    val shuffle: () -> Unit,
    val rewatch: (() -> Unit)?,
    val playEpisode: (BaseItem, fromStart: Boolean) -> Unit,
    val setEpisodeWatched: (BaseItem, Boolean) -> Unit,
    val trailer: (Trailer) -> Unit,
    val setMyList: (Boolean) -> Unit,
    val rate: (Boolean?) -> Unit,
    val markShowWatched: () -> Unit,
    val pickSeason: (Int) -> Unit,
    val openItem: (BaseItem) -> Unit,
    val openPerson: (Person) -> Unit,
    val fixMatch: (() -> Unit)?,
    val request: (() -> Unit)?,
    val openAsk: (DiscoverItem) -> Unit,
    /** Admin only: Change poster */
    val pickPoster: (() -> Unit)? = null,
)

/**
 * Benflix Redesign 2: a show's own page on the shared title page frame. It opens on the Episodes tab
 * (season chips and one row of wide episode pictures). Buttons: Resume S2 E4 (or Play), Episodes,
 * Trailer (greyed out when there isn't one), My List, More. Tabs: Episodes, Overview, More like this,
 * Cast, Parents' guide, Did you know. Anime shows get Characters and voices instead of Parents' guide.
 */
@Composable
fun SeriesHeroContent(
    series: BaseItem,
    state: SeriesState,
    easy: Boolean,
    actions: SeriesHeroActions,
    modifier: Modifier = Modifier,
) {
    var showMore by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<BaseItem?>(null) }
    val anime = state.subDub != null
    val likes = series.data.userData?.likes
    val trailer = state.trailers.firstOrNull()
    val buttons =
        buildList {
            add(
                TitleButton(
                    label = state.nextUpLabel ?: "Play",
                    icon = BenflixIcon.PLAY,
                    onClick = actions.playNext,
                    note = state.progress?.words(),
                ),
            )
            if (state.inlineEpisodes.isNotEmpty()) {
                add(TitleButton(label = "Episodes", icon = BenflixIcon.LIST, onClick = {}, opensTab = "Episodes"))
            }
            add(
                TitleButton(
                    label = "Trailer",
                    icon = BenflixIcon.TRAILER,
                    onClick = { trailer?.let(actions.trailer) },
                    off = if (trailer == null) "No trailer for this one yet" else null,
                ),
            )
            add(
                TitleButton(
                    label = "My List",
                    icon = if (series.favorite) BenflixIcon.CHECK else BenflixIcon.PLUS,
                    onClick = { actions.setMyList(!series.favorite) },
                    note = if (series.favorite) "On your list" else null,
                ),
            )
            add(TitleButton(label = "More", icon = BenflixIcon.MORE, onClick = { showMore = true }))
        }
    val scores = rememberScoreParts(series)
    val strip =
        remember(series, scores) {
            buildList {
                if (!easy) addAll(scores)
                showMakers(series)?.let { add(it) }
            }
        }
    val tabs =
        buildList {
            if (state.inlineEpisodes.isNotEmpty()) {
                add(
                    TitleTab("Episodes") { scope ->
                        EpisodesSection(
                            seasons = state.seasons,
                            seasonIndex = state.inlineSeasonIndex,
                            episodes = state.inlineEpisodes,
                            // Anime: "S3 E12 · #57", counting every episode of the seasons before
                            numberOffset = if (anime) animeOffset(state.seasons, state.inlineSeasonIndex) else null,
                            side = scope.side,
                            big = easy,
                            wide = scope.wide,
                            onPickSeason = actions.pickSeason,
                            onPlay = { actions.playEpisode(it, false) },
                            onHold = if (easy) null else ({ held = it }),
                        )
                        if (!easy) {
                            NextSeasonAsk(
                                seriesId = series.id,
                                modifier = Modifier.padding(start = scope.side, end = scope.side, top = 12.dp),
                            )
                        }
                    },
                )
            }
            add(
                TitleTab("Overview") { scope ->
                    TitleOverview(
                        scope = scope,
                        title = series.title.orEmpty(),
                        story = series.data.overview,
                        strip = strip,
                        extra = if (anime && !easy) ({ AnimeFacts(series) }) else null,
                    )
                },
            )
            if (anime && !easy) {
                add(TitleTab("Characters and voices") { scope -> AnimeRows(series, Modifier.padding(start = scope.side)) })
            }
            if (state.similar.isNotEmpty() || (!easy && state.discovered.isNotEmpty())) {
                add(
                    TitleTab("More like this") { scope ->
                        val both = state.similar.isNotEmpty() && !easy && state.discovered.isNotEmpty()
                        TitlePosterRow(scope, state.similar, actions.openItem, name = if (both) "More like this" else null)
                        if (!easy && state.discovered.isNotEmpty()) {
                            AskShelf(items = state.discovered, side = scope.side, showName = !scope.wide, onClick = actions.openAsk)
                        }
                    },
                )
            }
            if (state.people.isNotEmpty()) {
                add(TitleTab("Cast") { scope -> TitleCastRow(scope, state.people, actions.openPerson) })
            }
            if (!anime && state.parentsGuide.isNotEmpty()) {
                add(TitleTab("Parents' guide") { scope -> TitleParentsGuide(scope, state.parentsGuide) })
            }
            if (state.facts.isNotEmpty()) {
                add(TitleTab("Did you know") { scope -> TitleFacts(scope, state.facts, BenflixColors.Shows) })
            }
        }
    TitlePage(
        title = series.title.orEmpty(),
        originalTitle = series.data.originalTitle,
        accent = BenflixColors.Shows,
        picture = TitlePicture.Backdrop(series),
        info = remember(series, state.seasons.size, state.subDub) { showMeta(series, state) },
        rating = series.data.officialRating,
        buttons = buttons,
        tabs = tabs,
        openTab = "Episodes",
        big = easy,
        modifier = modifier,
    )

    if (showMore) {
        TitleMorePanel(
            title = series.title.orEmpty(),
            onDismiss = { showMore = false },
            items =
                buildList {
                    add(
                        TitleMoreItem(
                            if (series.played) "Mark the whole show as not watched" else "Mark the whole show as watched",
                            BenflixIcon.CHECK,
                            actions.markShowWatched,
                        ),
                    )
                    actions.rewatch?.let { add(TitleMoreItem("Watch again from the start", BenflixIcon.PLAY, it)) }
                    add(TitleMoreItem("Shuffle episodes", BenflixIcon.SHUFFLE, actions.shuffle))
                    if (!easy) {
                        add(
                            TitleMoreItem(
                                when (likes) {
                                    true -> if (series.favorite) "Rate (you loved it)" else "Rate (you liked it)"
                                    false -> "Rate (not for you)"
                                    null -> "Rate"
                                },
                                BenflixIcon.THUMB,
                                { showRate = true },
                            ),
                        )
                    }
                    actions.request?.let { add(TitleMoreItem("Ask for more seasons", BenflixIcon.REQUESTS, it)) }
                    actions.pickPoster?.let { add(TitleMoreItem("Pick a poster", BenflixIcon.IMAGE, it, youOnly = true)) }
                    actions.fixMatch?.let { add(TitleMoreItem("Wrong show? Fix the match", BenflixIcon.FLAG, it, youOnly = true)) }
                },
        )
    }
    if (showRate) {
        BenflixChoiceDialog(
            title = "Rate ${series.title}",
            subtitle = "This shapes what Benflix picks for you",
            onDismissRequest = { showRate = false },
            choices =
                listOf(
                    BenflixChoice("Not for me", { actions.rate(if (likes == false) null else false) }, selected = likes == false),
                    BenflixChoice(
                        "I like it",
                        { actions.rate(if (likes == true && !series.favorite) null else true) },
                        selected = likes == true && !series.favorite,
                    ),
                    BenflixChoice(
                        "Love it",
                        {
                            actions.rate(true)
                            if (!series.favorite) actions.setMyList(true)
                        },
                        note = "Also adds it to My List",
                        selected = likes == true && series.favorite,
                    ),
                ),
        )
    }
    val markUpTo: com.github.damontecres.wholphin.services.MarkWatchedUpToViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel()
    held?.let { episode ->
        BenflixChoiceDialog(
            title = episode.name,
            onDismissRequest = { held = null },
            choices =
                buildList {
                    add(BenflixChoice("Play from the start", { actions.playEpisode(episode, true) }, BenflixIcon.PLAY))
                    add(
                        BenflixChoice(
                            if (episode.played) "Mark as not watched" else "Mark as watched",
                            { actions.setEpisodeWatched(episode, !episode.played) },
                            BenflixIcon.CHECK,
                        ),
                    )
                    // Approved hold menu: Mark watched, Mark all before as watched, Play from the start
                    add(BenflixChoice("Mark all before as watched", { markUpTo.markUpTo(episode) }, BenflixIcon.CHECK))
                },
        )
    }
}

/** Season chips, the focused episode's story, then one row of wide episode pictures */
@Composable
private fun EpisodesSection(
    seasons: List<BaseItem?>,
    seasonIndex: Int,
    episodes: List<BaseItem>,
    numberOffset: Int?,
    side: Dp,
    big: Boolean,
    wide: Boolean,
    onPickSeason: (Int) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onHold: ((BaseItem) -> Unit)?,
) {
    var focused by remember(seasonIndex) { mutableStateOf<BaseItem?>(null) }
    val all = remember(episodes) { episodes.distinctBy { it.id } }
    // Some anime seasons already count on from the season before (S2 starts at E25): no "#" then
    val offset = remember(all, numberOffset) { numberOffset?.takeIf { all.firstOrNull { !it.isSpecial() }?.data?.indexNumber == 1 } }
    // Long seasons (more than 50 episodes) get group chips: 1-25, 26-50, ... (approved ep-chunks)
    val groups = remember(all) { if (all.size > LONG_SEASON) all.indices.chunked(GROUP_SIZE) else emptyList() }
    val firstUnwatched = remember(all) { all.indexOfFirst { !it.played }.coerceAtLeast(0) }
    var group by remember(seasonIndex, all.size) { mutableStateOf(if (groups.isEmpty()) 0 else firstUnwatched / GROUP_SIZE) }
    val shownEpisodes =
        remember(all, group, groups) {
            if (groups.isEmpty()) all else groups.getOrNull(group)?.map { all[it] }.orEmpty()
        }
    val shown = focused ?: shownEpisodes.firstOrNull { !it.played } ?: shownEpisodes.firstOrNull()
    val startIndex = remember(shownEpisodes) { shownEpisodes.indexOfFirst { !it.played }.coerceAtLeast(0) }
    val rowState = androidx.compose.runtime.key(seasonIndex, group) { rememberLazyListState(initialFirstVisibleItemIndex = startIndex) }
    val cardWidth =
        if (big) {
            300.dp
        } else if (wide) {
            // Fits the title page's tab area (the bottom 40% of the screen)
            210.dp
        } else {
            200.dp
        }
    Column(Modifier.fillMaxWidth()) {
        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                itemsIndexed(seasons) { index, season ->
                    SeasonChip(
                        text = season?.name ?: "Season ${index + 1}",
                        selected = index == seasonIndex,
                        onClick = { onPickSeason(index) },
                    )
                }
            }
        } else {
            SectionTitle(seasons.firstOrNull()?.name ?: "Episodes", side)
        }
        if (groups.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                itemsIndexed(groups) { index, range ->
                    // The episode numbers on the cards, so a season that counts on (E26 to E75) isn't labelled 1-25
                    val numbers = range.mapNotNull { i -> all[i].takeIf { !it.isSpecial() }?.data?.indexNumber }
                    val from = numbers.minOrNull() ?: (range.first() + 1)
                    val to = numbers.maxOrNull() ?: (range.last() + 1)
                    GroupChip(
                        text = "$from–$to",
                        selected = index == group,
                        onClick = { group = index },
                    )
                }
            }
        }
        shown?.let { ep ->
            Text(
                text =
                    listOfNotNull(
                        episodeNumber(ep, offset),
                        ep.name,
                        ep.data.overview?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(start = side, end = side, bottom = 14.dp)
                        .widthIn(max = 900.dp),
            )
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(shownEpisodes, key = { _, it -> it.id.toString() }) { _, ep ->
                EpisodeCard(
                    episode = ep,
                    number = episodeNumber(ep, offset),
                    width = cardWidth,
                    onClick = { onPlay(ep) },
                    onLongClick = onHold?.let { hold -> { hold(ep) } },
                    onFocus = { focused = ep },
                )
            }
        }
    }
}

@Composable
private fun SeasonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) BenflixColors.Shows else BenflixColors.Raised,
                contentColor = if (selected) BenflixColors.OnShows else BenflixColors.Soft,
                focusedContainerColor = if (selected) BenflixColors.Shows else BenflixColors.FocusRow,
                focusedContentColor = if (selected) BenflixColors.OnShows else BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** A wide episode picture with "E5 · Name" under it, a progress line and a small tick when watched */
@Composable
private fun EpisodeCard(
    episode: BaseItem,
    number: String?,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocus: () -> Unit,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val height = width * 9f / 16f
    val url =
        remember(episode.id) {
            imageUrlService.getItemImageUrl(
                episode,
                ImageType.PRIMARY,
                fillWidth = with(density) { width.roundToPx() },
                useSeriesForPrimary = false,
            )
        }
    val progress = remember(episode) { episode.progress() }
    val upcoming = episode.data.premiereDay?.isAfter(java.time.LocalDate.now()) == true
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = BenflixColors.Raised,
                    focusedContainerColor = BenflixColors.Raised,
                ),
            border = doorFocusBorder(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier =
                Modifier
                    .size(width, height)
                    .onFocusChanged { if (it.isFocused) onFocus() },
        ) {
            Box(Modifier.fillMaxSize()) {
                var failed by remember(episode.id) { mutableStateOf(false) }
                if (url != null && episode.hasOwnPicture() && !failed) {
                    AsyncImage(
                        model = url,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        alpha = if (upcoming) 0.4f else 1f,
                        onError = { failed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // No picture of its own: the show's backdrop with a big episode number
                    EpisodeStandIn(episode, width)
                }
                if (episode.isSpecial()) {
                    Text(
                        text = "SPECIAL",
                        color = BenflixColors.Tray,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(BenflixColors.Text, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (episode.played) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(18.dp)
                                .background(BenflixColors.Text, CircleShape),
                    ) { BenflixIconImage(BenflixIcon.CHECK, color = BenflixColors.Tray, size = 13.dp) }
                }
                if (progress != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x99000000)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(BenflixColors.Text),
                        )
                    }
                }
            }
        }
        Text(
            text = listOfNotNull(number, episode.name).joinToString(" · "),
            color = if (upcoming) BenflixColors.Muted else BenflixColors.Text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (upcoming) {
            episode.data.premiereDay?.let {
                // "Coming Sat, Nov 8" instead of "Coming 2026-11-08"
                val day =
                    remember(it) {
                        it.format(
                            java.time.format.DateTimeFormatter
                                .ofPattern("EEE, MMM d", java.util.Locale.getDefault()),
                        )
                    }
                Text(text = "Coming $day", color = BenflixColors.Muted, fontSize = BenflixType.tag)
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    side: Dp,
) {
    Text(
        text = title,
        color = BenflixColors.Text,
        fontSize = BenflixType.lead,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
    )
}

/** "2019 to 2023 · 4 seasons · TV-14 · Drama · Ended" */
private fun showMeta(
    series: BaseItem,
    state: SeriesState,
): String {
    val d = series.data
    val seasons = state.seasons.size
    val status =
        when {
            d.status.equals("Ended", true) -> {
                "Ended"
            }

            // Approved: "New episodes on Fridays" in the info line when Jellyfin knows the day
            d.status.equals("Continuing", true) -> {
                d.airDays
                    ?.takeIf { it.size in 1..2 }
                    ?.joinToString(" and ") { day -> day.name.lowercase().replaceFirstChar { it.uppercase() } + "s" }
                    ?.let { "New episodes on $it" }
                    ?: "Still airing"
            }

            else -> {
                null
            }
        }
    return listOfNotNull(
        d.productionYear?.toString(),
        seasons.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
        d.genres?.firstOrNull(),
        state.subDub,
        status,
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

/** "Created by **Dan Erickson**" (or the network) for the slim strip */
private fun showMakers(series: BaseItem): StripPart? {
    val created =
        series.data.people
            .orEmpty()
            .filter { it.type == PersonKind.CREATOR }
            .mapNotNull { it.name }
            .take(2)
            .joinToString(", ")
    if (created.isNotBlank()) return StripPart("Created by", created)
    val network =
        series.data.studios
            ?.mapNotNull { it.name }
            ?.take(2)
            ?.joinToString(", ")
    return network?.takeIf { it.isNotBlank() }?.let { StripPart("Network", it) }
}

/** "12 episodes left · 8 h 20 m" under the buttons once you've started a show */
private fun ShowProgress.words(): String? {
    if (!started || episodesLeft <= 0) return null
    val time =
        when {
            minutesLeft <= 0 -> null
            minutesLeft < 60 -> "$minutesLeft m"
            minutesLeft % 60 == 0L -> "${minutesLeft / 60} h"
            else -> "${minutesLeft / 60} h ${minutesLeft % 60} m"
        }
    val count = if (episodesLeft == 1) "1 episode left" else "$episodesLeft episodes left"
    return listOfNotNull(count, time).joinToString(" · ")
}

/** Seasons with more than this many episodes get group chips */
private const val LONG_SEASON = 50

/** Episodes per group chip */
private const val GROUP_SIZE = 25

/** A special Jellyfin shows inside a season (where it aired) */
private fun BaseItem.isSpecial(): Boolean = data.parentIndexNumber == 0

/** "E5", "Special" for specials, or on anime "E12 · #57" (counting every episode of the seasons before) */
private fun episodeNumber(
    episode: BaseItem,
    offset: Int?,
): String? {
    if (episode.isSpecial()) return "Special"
    val number = episode.data.indexNumber ?: return null
    return if (offset != null && offset > 0) "E$number · #${offset + number}" else "E$number"
}

/** How many episodes the seasons before this one have (not counting Specials), or null if unknown */
private fun animeOffset(
    seasons: List<BaseItem?>,
    seasonIndex: Int,
): Int? {
    if (seasonIndex <= 0) return 0
    var total = 0
    seasons.take(seasonIndex).forEach { season ->
        if (season == null) return null
        if (season.data.indexNumber == 0) return@forEach
        total += season.data.childCount ?: return null
    }
    return total
}

@Composable
private fun GroupChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Approved gc-square: squarer white chips for groups (seasons stay round and blue)
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) BenflixColors.Text else BenflixColors.Raised,
                contentColor = if (selected) BenflixColors.Tray else BenflixColors.Soft,
                focusedContainerColor = if (selected) BenflixColors.Text else BenflixColors.FocusRow,
                focusedContentColor = if (selected) BenflixColors.Tray else BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
