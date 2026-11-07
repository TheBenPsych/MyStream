package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.detail.HeroBackground
import com.github.damontecres.wholphin.ui.detail.HeroButton
import com.github.damontecres.wholphin.ui.detail.HeroSideColumn
import com.github.damontecres.wholphin.ui.door.DoorPoster
import com.github.damontecres.wholphin.ui.door.ScoreLine
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.door.progress
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind
import java.time.LocalDateTime

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
)

/**
 * Benflix redesign: a show's own page, the same as a movie's (full-screen picture, words and buttons on
 * the left, a quiet right column with Created by, Starring and Network). The main button says where you
 * are ("Resume S2 E5"). Below: season chips and one row of wide episode pictures (the story of the
 * focused episode sits above the row), then More like this, Cast and the anime extras.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesHeroContent(
    series: BaseItem,
    state: SeriesState,
    easy: Boolean,
    actions: SeriesHeroActions,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val side = if (wide) 40.dp else 16.dp
    val listState = rememberLazyListState()
    val firstButton = remember { FocusRequester() }
    var showMore by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf<BaseItem?>(null) }
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(60)
            firstButton.tryRequestFocus()
        }
    }
    val buttons: @Composable () -> Unit = {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroButton(
                label = state.nextUpLabel ?: "Play",
                icon = BenflixIcon.PLAY,
                primary = true,
                big = easy,
                onClick = actions.playNext,
                modifier = Modifier.focusRequester(firstButton),
            )
            state.trailers.firstOrNull()?.let { trailer ->
                HeroButton(label = "Trailer", icon = BenflixIcon.FILM, big = easy, onClick = { actions.trailer(trailer) })
            }
            HeroButton(
                label = "My List",
                icon = if (series.favorite) BenflixIcon.CHECK else BenflixIcon.PLUS,
                big = easy,
                onClick = { actions.setMyList(!series.favorite) },
            )
            if (!easy) {
                val likes = series.data.userData?.likes
                HeroButton(
                    label =
                        when (likes) {
                            true -> if (series.favorite) "Loved" else "Liked"
                            false -> "Not for me"
                            null -> "Rate"
                        },
                    icon = BenflixIcon.THUMB,
                    onClick = { showRate = true },
                )
            }
            HeroButton(label = "More", icon = BenflixIcon.MORE, big = easy, onClick = { showMore = true })
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        if (wide) {
            // The show's picture stays behind the page while you scroll down to the episodes
            HeroBackground(
                item = series,
                dim = {
                    if (listState.firstVisibleItemIndex > 0) {
                        0.75f
                    } else {
                        (listState.firstVisibleItemScrollOffset / (pageHeight.value * 2f)).coerceIn(0f, 0.75f)
                    }
                },
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                if (wide) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(pageHeight),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(start = side, end = side, bottom = 28.dp),
                        ) {
                            ShowWords(
                                series = series,
                                state = state,
                                easy = easy,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .widthIn(max = 620.dp),
                            ) {
                                buttons()
                                if (isTv && !easy) {
                                    Text(
                                        text = "Episodes, more like this and cast below",
                                        color = BenflixColors.Muted,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 14.dp),
                                    )
                                }
                            }
                            if (!easy) {
                                HeroSideColumn(
                                    lines = remember(series) { showSideLines(series) },
                                    modifier = Modifier.padding(start = 32.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f),
                        ) { HeroBackground(item = series, dim = { 0f }) }
                        ShowWords(series, state, easy, Modifier.padding(horizontal = 16.dp), buttons)
                    }
                }
            }
            if (state.inlineEpisodes.isNotEmpty()) {
                item(key = "episodes") {
                    EpisodesSection(
                        seasons = state.seasons,
                        seasonIndex = state.inlineSeasonIndex,
                        episodes = state.inlineEpisodes,
                        side = side,
                        big = easy,
                        wide = wide,
                        onPickSeason = actions.pickSeason,
                        onPlay = { actions.playEpisode(it, false) },
                        onHold = if (easy) null else ({ held = it }),
                    )
                }
            }
            if (state.similar.isNotEmpty()) {
                item(key = "similar") {
                    Column(Modifier.fillMaxWidth()) {
                        SectionTitle("More like this", side)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = side),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(state.similar.distinctBy { it.id }, key = { _, it -> it.id.toString() }) { _, item ->
                                DoorPoster(item = item, width = 100.dp, showName = !isTv, onClick = { actions.openItem(item) })
                            }
                        }
                    }
                }
            }
            if (!easy) {
                item(key = "anime") { AnimeRows(series) }
            }
            if (state.people.isNotEmpty()) {
                item(key = "cast") {
                    PersonRow(
                        people = state.people,
                        onClick = actions.openPerson,
                        modifier = Modifier.padding(start = side, end = side),
                    )
                }
            }
        }
    }

    if (showMore) {
        BenflixChoiceDialog(
            title = series.title,
            onDismissRequest = { showMore = false },
            choices =
                buildList {
                    add(BenflixChoice("Shuffle episodes", actions.shuffle, BenflixIcon.SWITCH))
                    actions.rewatch?.let { add(BenflixChoice("Watch again from the start", it, BenflixIcon.PLAY)) }
                    add(
                        BenflixChoice(
                            if (series.played) "Mark the whole show as not watched" else "Mark the whole show as watched",
                            actions.markShowWatched,
                            BenflixIcon.CHECK,
                        ),
                    )
                    actions.request?.let { add(BenflixChoice("Ask for more seasons", it, BenflixIcon.REQUESTS)) }
                    actions.fixMatch?.let { add(BenflixChoice("Wrong show? Fix the match", it, BenflixIcon.FLAG)) }
                },
        )
    }
    if (showRate) {
        val likes = series.data.userData?.likes
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
                },
        )
    }
}

@Composable
private fun ShowWords(
    series: BaseItem,
    state: SeriesState,
    easy: Boolean,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = series.title.orEmpty(),
            color = BenflixColors.Text,
            fontSize = if (easy) 44.sp else 40.sp,
            lineHeight = if (easy) 46.sp else 42.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = remember(series, state.seasons.size, state.subDub) { showMeta(series, state) }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                color = BenflixColors.Soft,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!easy) {
            ScoreLine(series, Modifier.padding(top = 6.dp))
            AnimeFacts(series, Modifier.padding(top = 4.dp))
            series.data.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = BenflixColors.Soft,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        Box(Modifier.padding(top = 18.dp)) { buttons() }
        state.progress?.words()?.let {
            Text(text = it, color = BenflixColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Season chips, the focused episode's story, then one row of wide episode pictures */
@Composable
private fun EpisodesSection(
    seasons: List<BaseItem?>,
    seasonIndex: Int,
    episodes: List<BaseItem>,
    side: Dp,
    big: Boolean,
    wide: Boolean,
    onPickSeason: (Int) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onHold: ((BaseItem) -> Unit)?,
) {
    var focused by remember(seasonIndex) { mutableStateOf<BaseItem?>(null) }
    val shown = focused ?: episodes.firstOrNull { !it.played } ?: episodes.firstOrNull()
    val startIndex = remember(seasonIndex, episodes) { episodes.indexOfFirst { !it.played }.coerceAtLeast(0) }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val cardWidth =
        if (big) {
            300.dp
        } else if (wide) {
            250.dp
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
        shown?.let { ep ->
            Text(
                text =
                    listOfNotNull(
                        ep.data.indexNumber?.let { "E$it" },
                        ep.name,
                        ep.data.overview?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                color = BenflixColors.Soft,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = side, end = side, bottom = 10.dp),
            )
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(episodes.distinctBy { it.id }, key = { _, it -> it.id.toString() }) { _, ep ->
                EpisodeCard(
                    episode = ep,
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
            fontSize = 14.sp,
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
    val upcoming = episode.data.premiereDate?.isAfter(LocalDateTime.now()) == true
    val shape = RoundedCornerShape(8.dp)
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
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        alpha = if (upcoming) 0.4f else 1f,
                        modifier = Modifier.fillMaxSize(),
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
                            .clip(RoundedCornerShape(2.dp))
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
            text = listOfNotNull(episode.data.indexNumber?.let { "E$it" }, episode.name).joinToString(" · "),
            color = if (upcoming) BenflixColors.Muted else BenflixColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (upcoming) {
            episode.data.premiereDate?.let {
                Text(text = "Coming ${it.toLocalDate()}", color = BenflixColors.Muted, fontSize = 12.sp)
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
        fontSize = 17.sp,
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
            d.status.equals("Ended", true) -> "Ended"
            d.status.equals("Continuing", true) -> "Still airing"
            else -> null
        }
    return listOfNotNull(
        d.productionYear?.toString(),
        seasons.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
        d.officialRating,
        d.genres?.firstOrNull(),
        state.subDub,
        status,
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun showSideLines(series: BaseItem): List<Pair<String, String>> {
    val people = series.data.people.orEmpty()
    val created =
        people
            .filter { it.type == PersonKind.CREATOR }
            .mapNotNull { it.name }
            .take(2)
            .joinToString(", ")
    val starring =
        people
            .filter { it.type == PersonKind.ACTOR }
            .mapNotNull { it.name }
            .take(3)
            .joinToString(", ")
    val network =
        series.data.studios
            ?.mapNotNull { it.name }
            ?.take(2)
            ?.joinToString(", ")
    return listOfNotNull(
        created.takeIf { it.isNotBlank() }?.let { "Created by" to it },
        starring.takeIf { it.isNotBlank() }?.let { "Starring" to it },
        network?.takeIf { it.isNotBlank() }?.let { "Network" to it },
    )
}
