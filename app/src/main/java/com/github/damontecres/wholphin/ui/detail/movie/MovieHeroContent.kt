package com.github.damontecres.wholphin.ui.detail.movie

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.AniListFacts
import com.github.damontecres.wholphin.services.MovieCollection
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.QrCode
import com.github.damontecres.wholphin.ui.detail.DidYouKnow
import com.github.damontecres.wholphin.ui.detail.HeroBackground
import com.github.damontecres.wholphin.ui.detail.HeroButton
import com.github.damontecres.wholphin.ui.detail.HeroSideColumn
import com.github.damontecres.wholphin.ui.detail.HeroTag
import com.github.damontecres.wholphin.ui.detail.KeepHeroAtTop
import com.github.damontecres.wholphin.ui.detail.ParentsGuideBlock
import com.github.damontecres.wholphin.ui.door.DoorPoster
import com.github.damontecres.wholphin.ui.door.ScoreLine
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.door.lengthText
import com.github.damontecres.wholphin.ui.door.metaLine
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.ui.premiereDay
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

/** What the movie page can do, so the page itself stays simple */
class MovieHeroActions(
    val play: (Duration) -> Unit,
    val trailer: (Trailer) -> Unit,
    val setMyList: (Boolean) -> Unit,
    val rate: (Boolean?) -> Unit,
    val setWatched: (Boolean) -> Unit,
    val streams: () -> Unit,
    val fileDetails: () -> Unit,
    val openItem: (BaseItem) -> Unit,
    val openPerson: (Person) -> Unit,
    val openCollection: (MovieCollection) -> Unit,
)

/**
 * Benflix redesign: a movie's own page (approved dt-hero + h2-left + h2-side). The picture fills the
 * page; the name, info, scores, story and buttons sit on the left, a quiet column on the right names
 * the director, who stars and the audio. Below: In this collection (when it's in one), More like this,
 * Cast and Details. The trailer only plays when Trailer is pressed. Kids and Easy read get the plain
 * version: no scores, no story, bigger buttons.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MovieHeroContent(
    movie: BaseItem,
    state: MovieState,
    easy: Boolean,
    isAdmin: Boolean,
    actions: MovieHeroActions,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val dto = movie.data
    val resume = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO
    val canResume = resume.inWholeSeconds > 30 && !movie.played
    val collection = state.collection
    val part = collection?.partOf(movie.id)
    val listState = rememberLazyListState()
    var heroFocused by remember { mutableStateOf(false) }
    KeepHeroAtTop(listState, heroFocused)
    val scope = rememberCoroutineScope()
    val firstButton = remember { FocusRequester() }
    val collectionRow = remember { FocusRequester() }
    var showMore by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }
    var showLinks by remember { mutableStateOf(false) }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(60)
            firstButton.tryRequestFocus()
            focusedOnce = true
        }
    }
    val side = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val goToCollection: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(1)
            delay(150)
            collectionRow.tryRequestFocus()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        if (wide) {
            HeroBackground(
                item = movie,
                dim = {
                    if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        (listState.firstVisibleItemScrollOffset / (pageHeight.value * 2f)).coerceIn(0f, 1f)
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
                            HeroWords(
                                movie = movie,
                                easy = easy,
                                part = part,
                                collection = collection,
                                onPart = goToCollection,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .widthIn(max = 620.dp)
                                        // Coming back up to the buttons shows the whole top again (name included)
                                        .onFocusChanged { heroFocused = it.hasFocus },
                            ) {
                                HeroButtons(
                                    movie = movie,
                                    state = state,
                                    canResume = canResume,
                                    resume = resume,
                                    big = easy,
                                    firstButton = firstButton,
                                    actions = actions,
                                    onMore = { showMore = true },
                                    onRate = { showRate = true },
                                )
                                if (isTv && !easy) {
                                    Text(
                                        text = "More like this, cast and details below",
                                        color = BenflixColors.Muted,
                                        fontSize = BenflixType.small,
                                        modifier = Modifier.padding(top = 14.dp),
                                    )
                                }
                            }
                            if (!easy) {
                                Column(
                                    Modifier
                                        .padding(start = 32.dp, bottom = 4.dp)
                                        .widthIn(max = 280.dp),
                                ) {
                                    HeroSideColumn(lines = remember(movie, state.chosenStreams) { sideLines(movie, state) })
                                    DidYouKnow(
                                        facts = state.facts,
                                        accent = BenflixColors.Movies,
                                        card = false,
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    PhoneHero(movie, easy, part, collection, goToCollection) {
                        HeroButtons(
                            movie = movie,
                            state = state,
                            canResume = canResume,
                            resume = resume,
                            big = easy,
                            firstButton = firstButton,
                            actions = actions,
                            onMore = { showMore = true },
                            onRate = { showRate = true },
                        )
                        DidYouKnow(
                            facts = state.facts,
                            accent = BenflixColors.Movies,
                            card = true,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
            }
            if (collection != null) {
                item(key = "collection") {
                    PosterShelf(
                        title = "In this collection",
                        items = collection.items,
                        current = movie,
                        side = side,
                        showName = !isTv,
                        firstFocus = collectionRow,
                        onClick = actions.openItem,
                    )
                }
            }
            if (state.similar.isNotEmpty()) {
                item(key = "similar") {
                    PosterShelf(
                        title = "More like this",
                        items = state.similar,
                        current = null,
                        side = side,
                        showName = !isTv,
                        onClick = actions.openItem,
                    )
                }
            }
            if (!easy && state.fromDirector.isNotEmpty()) {
                item(key = "director") {
                    PosterShelf(
                        title = "More from ${state.directorName ?: "the director"}",
                        items = state.fromDirector,
                        current = null,
                        side = side,
                        showName = !isTv,
                        onClick = actions.openItem,
                    )
                }
            }
            if (state.parentsGuide.isNotEmpty()) {
                item(key = "parents") {
                    ParentsGuideBlock(state.parentsGuide, Modifier.padding(start = side, end = side))
                }
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
            if (!easy) {
                item(key = "details") {
                    DetailsBlock(movie, isAdmin, actions.fileDetails, Modifier.padding(start = side, end = side))
                }
            }
        }
    }

    if (showMore) {
        val aniList = AniListFacts.info.value[movie.id]
        BenflixChoiceDialog(
            title = movie.title,
            onDismissRequest = { showMore = false },
            choices =
                buildList {
                    if (canResume) add(BenflixChoice("Start over", { actions.play(Duration.ZERO) }, BenflixIcon.PLAY))
                    add(BenflixChoice("Audio, subtitles and version", actions.streams, BenflixIcon.GEAR))
                    add(
                        BenflixChoice(
                            if (movie.played) "Mark as not watched" else "Mark as watched",
                            { actions.setWatched(!movie.played) },
                            BenflixIcon.CHECK,
                        ),
                    )
                    if (collection != null) {
                        add(BenflixChoice("Go to ${collection.name}", { actions.openCollection(collection) }, BenflixIcon.FILM))
                    }
                    if (aniList != null) add(BenflixChoice("Open on AniList or MAL", { showLinks = true }, BenflixIcon.ARROW))
                },
        )
    }
    if (showRate) {
        val likes = dto.userData?.likes
        BenflixChoiceDialog(
            title = "Rate ${movie.title}",
            subtitle = "This shapes what Benflix picks for you",
            onDismissRequest = { showRate = false },
            choices =
                listOf(
                    BenflixChoice("Not for me", { actions.rate(if (likes == false) null else false) }, selected = likes == false),
                    BenflixChoice("I like it", {
                        actions.rate(
                            if (likes == true &&
                                !movie.favorite
                            ) {
                                null
                            } else {
                                true
                            },
                        )
                    }, selected = likes == true && !movie.favorite),
                    BenflixChoice(
                        "Love it",
                        {
                            actions.rate(true)
                            if (!movie.favorite) actions.setMyList(true)
                        },
                        note = "Also adds it to My List",
                        selected = likes == true && movie.favorite,
                    ),
                ),
        )
    }
    if (showLinks) {
        val info = AniListFacts.info.value[movie.id]
        if (info == null) {
            showLinks = false
        } else {
            LinksDialog(
                aniListUrl = "https://anilist.co/anime/${info.id}",
                malUrl = info.idMal?.let { "https://myanimelist.net/anime/$it" },
                onDismiss = { showLinks = false },
            )
        }
    }
}

/** Name, "Part 1 of 3", info, scores and story, then [buttons] */
@Composable
private fun HeroWords(
    movie: BaseItem,
    easy: Boolean,
    part: Pair<Int, Int>?,
    collection: MovieCollection?,
    onPart: () -> Unit,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = movie.title.orEmpty(),
            color = BenflixColors.Text,
            fontSize = BenflixType.hero,
            lineHeight = if (easy) 46.sp else 42.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (part != null && collection != null) {
            PartLink("Part ${part.first} of ${part.second} · ${collection.name}", onPart, Modifier.padding(top = 6.dp))
        }
        val meta = remember(movie) { metaLine(movie) }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!easy) {
            ScoreLine(movie, Modifier.padding(top = 6.dp))
            movie.data.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    lineHeight = BenflixType.body * 1.35f,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        // A Column, not a Box: the buttons and the "below" hint must sit one under the other
        Column(Modifier.padding(top = 18.dp)) { buttons() }
    }
}

/** Resume (or Play), Trailer, My List, Rate, More */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroButtons(
    movie: BaseItem,
    state: MovieState,
    canResume: Boolean,
    resume: Duration,
    big: Boolean,
    firstButton: FocusRequester,
    actions: MovieHeroActions,
    onMore: () -> Unit,
    onRate: () -> Unit,
) {
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroButton(
                label = if (canResume) "Resume" else "Play",
                icon = BenflixIcon.PLAY,
                primary = true,
                big = big,
                onClick = { actions.play(if (canResume) resume else Duration.ZERO) },
                modifier = Modifier.focusRequester(firstButton),
            )
            state.trailers.firstOrNull()?.let { trailer ->
                HeroButton(label = "Trailer", icon = BenflixIcon.TRAILER, big = big, onClick = { actions.trailer(trailer) })
            }
            HeroButton(
                label = "My List",
                icon = if (movie.favorite) BenflixIcon.CHECK else BenflixIcon.PLUS,
                big = big,
                onClick = { actions.setMyList(!movie.favorite) },
            )
            if (!big) {
                val likes = movie.data.userData?.likes
                HeroButton(
                    label =
                        when (likes) {
                            true -> if (movie.favorite) "Loved" else "Liked"
                            false -> "Not for me"
                            null -> "Rate"
                        },
                    icon = BenflixIcon.THUMB,
                    onClick = onRate,
                )
            }
            HeroButton(label = "More", icon = BenflixIcon.MORE, big = big, onClick = onMore)
        }
        if (canResume) {
            val left =
                movie.data.runTimeTicks
                    ?.ticks
                    ?.minus(resume)
            if (left != null && left.isPositive()) {
                Text(
                    text = "${lengthText(left)} left",
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Phones: the picture across the top, then the words and buttons */
@Composable
private fun PhoneHero(
    movie: BaseItem,
    easy: Boolean,
    part: Pair<Int, Int>?,
    collection: MovieCollection?,
    onPart: () -> Unit,
    buttons: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
        ) {
            HeroBackground(item = movie, dim = { 0f })
        }
        HeroWords(
            movie = movie,
            easy = easy,
            part = part,
            collection = collection,
            onPart = onPart,
            modifier = Modifier.padding(horizontal = 16.dp),
            buttons = buttons,
        )
    }
}

/** "Part 1 of 3 · The Dark Knight Collection": jumps down to the In this collection row */
@Composable
private fun PartLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = BenflixColors.Text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/** A row of posters under the movie (In this collection marks this movie "You are here") */
@Composable
private fun PosterShelf(
    title: String,
    items: List<BaseItem>,
    current: BaseItem?,
    side: Dp,
    showName: Boolean,
    onClick: (BaseItem) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items.distinctBy { it.id }, key = { _, it -> it.id.toString() }) { index, item ->
                val here = current != null && item.id == current.id
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DoorPoster(
                        item = item,
                        width = if (showName) 104.dp else 100.dp,
                        showName = showName,
                        onClick = { if (!here) onClick(item) },
                        modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                    if (current != null) {
                        Box(Modifier.height(24.dp).padding(top = 6.dp)) {
                            if (here) HeroTag("You are here")
                        }
                    }
                }
            }
        }
    }
}

/** The plain facts at the bottom, and the file details for the main profile */
@Composable
private fun DetailsBlock(
    movie: BaseItem,
    isAdmin: Boolean,
    onFileDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = movie.data
    val facts =
        remember(movie) {
            listOfNotNull(
                dto.genres?.takeIf { it.isNotEmpty() }?.let { "Genres" to it.joinToString(", ") },
                dto.studios
                    ?.mapNotNull { it.name }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { "Studio" to it.take(3).joinToString(", ") },
                dto.premiereDay?.let { "Released" to formatDate(it) },
                dto.runTimeTicks?.ticks?.let { "Length" to lengthText(it) },
                dto.officialRating?.let { "Rated" to it },
                dto.people
                    ?.filter { it.type == PersonKind.WRITER }
                    ?.mapNotNull { it.name }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { "Written by" to it.take(3).joinToString(", ") },
            )
        }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Details",
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        facts.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(text = label, color = BenflixColors.Muted, fontSize = BenflixType.small, modifier = Modifier.width(120.dp))
                Text(text = value, color = BenflixColors.Soft, fontSize = BenflixType.small)
            }
        }
        if (isAdmin) {
            Box(Modifier.padding(top = 12.dp)) {
                HeroButton(label = "File details", icon = BenflixIcon.SERVER, onClick = onFileDetails)
            }
        }
    }
}

/** On TV: QR codes to open the anime on AniList or MAL with a phone. Phones open the link. */
@Composable
private fun LinksDialog(
    aniListUrl: String,
    malUrl: String?,
    onDismiss: () -> Unit,
) {
    val isTv = rememberIsTv()
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!isTv) {
        BenflixChoiceDialog(
            title = "Open on",
            onDismissRequest = onDismiss,
            choices =
                listOfNotNull(
                    BenflixChoice("AniList", { openLink(context, aniListUrl) }),
                    malUrl?.let { BenflixChoice("MyAnimeList", { openLink(context, it) }) },
                ),
        )
        return
    }
    com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = onDismiss) {
        val close = remember { FocusRequester() }
        LaunchedEffect(Unit) { close.tryRequestFocus() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .background(BenflixColors.Panel, RoundedCornerShape(16.dp))
                    .padding(24.dp),
        ) {
            Text("Scan with your phone", color = BenflixColors.Text, fontSize = BenflixType.rowName, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 18.dp)) {
                LinkCode("AniList", aniListUrl)
                malUrl?.let { LinkCode("MyAnimeList", it) }
            }
            HeroButton(label = "Close", icon = BenflixIcon.BACK, onClick = onDismiss, modifier = Modifier.focusRequester(close))
        }
    }
}

@Composable
private fun LinkCode(
    label: String,
    url: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        QrCode(text = url, modifier = Modifier.width(150.dp).aspectRatio(1f))
        Text(label, color = BenflixColors.Soft, fontSize = BenflixType.body, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun openLink(
    context: android.content.Context,
    url: String,
) {
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    } catch (ex: Exception) {
        timber.log.Timber.w(ex, "Could not open %s", url)
    }
}

/** The quiet right column: Director, Starring, Audio */
private fun sideLines(
    movie: BaseItem,
    state: MovieState,
): List<Pair<String, String>> {
    val people = movie.data.people.orEmpty()
    val director =
        people
            .filter { it.type == PersonKind.DIRECTOR }
            .mapNotNull { it.name }
            .take(2)
            .joinToString(", ")
    val starring =
        people
            .filter { it.type == PersonKind.ACTOR }
            .mapNotNull { it.name }
            .take(3)
            .joinToString(", ")
    val audio =
        state.chosenStreams
            ?.audioStream
            ?.stream
            ?.displayTitle
            ?: movie.data.mediaSources
                ?.firstOrNull()
                ?.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault }
                ?.displayTitle
            ?: movie.data.mediaSources
                ?.firstOrNull()
                ?.mediaStreams
                ?.firstOrNull { it.type == MediaStreamType.AUDIO }
                ?.displayTitle
    return listOfNotNull(
        director.takeIf { it.isNotBlank() }?.let { "Director" to it },
        starring.takeIf { it.isNotBlank() }?.let { "Starring" to it },
        audio?.takeIf { it.isNotBlank() }?.let { "Audio" to it },
    )
}
