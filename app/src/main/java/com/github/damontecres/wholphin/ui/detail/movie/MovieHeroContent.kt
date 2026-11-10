package com.github.damontecres.wholphin.ui.detail.movie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.AniListFacts
import com.github.damontecres.wholphin.services.MovieCollection
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.QrCode
import com.github.damontecres.wholphin.ui.detail.AskShelf
import com.github.damontecres.wholphin.ui.detail.HeroButton
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
import com.github.damontecres.wholphin.ui.detail.title.TitleTextLink
import com.github.damontecres.wholphin.ui.detail.title.rememberScoreParts
import com.github.damontecres.wholphin.ui.door.lengthText
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
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
    val openAsk: (DiscoverItem) -> Unit,
    /** Admin only: Change poster */
    val pickPoster: (() -> Unit)? = null,
)

/**
 * Benflix Redesign 2: a movie's own page on the shared title page frame. Buttons: Resume (or Play),
 * Trailer (greyed out when there isn't one), My List, Rate, More. Tabs: Overview, More like this (with
 * In this collection, Ask for more like this and More from the director), Cast, Parents' guide and
 * Did you know. Kids and Easy read get bigger buttons and no Rate or asking.
 */
@Composable
fun MovieHeroContent(
    movie: BaseItem,
    state: MovieState,
    easy: Boolean,
    isAdmin: Boolean,
    actions: MovieHeroActions,
    modifier: Modifier = Modifier,
) {
    val dto = movie.data
    val resume = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO
    val canResume = resume.inWholeSeconds > 30 && !movie.played
    val collection = state.collection
    val part = collection?.partOf(movie.id)
    var showMore by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }
    var showLinks by remember { mutableStateOf(false) }
    val left =
        if (canResume) {
            dto.runTimeTicks
                ?.ticks
                ?.minus(resume)
                ?.takeIf { it.isPositive() }
        } else {
            null
        }
    val likes = dto.userData?.likes
    val buttons =
        buildList {
            add(
                TitleButton(
                    label = if (canResume) "Resume" else "Play",
                    icon = BenflixIcon.PLAY,
                    onClick = { actions.play(if (canResume) resume else Duration.ZERO) },
                    note = left?.let { "${lengthText(it)} left" },
                ),
            )
            val trailer = state.trailers.firstOrNull()
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
                    icon = if (movie.favorite) BenflixIcon.CHECK else BenflixIcon.PLUS,
                    onClick = { actions.setMyList(!movie.favorite) },
                    note = if (movie.favorite) "On your list" else null,
                ),
            )
            if (!easy) {
                add(
                    TitleButton(
                        label =
                            when (likes) {
                                true -> if (movie.favorite) "Loved" else "Liked"
                                false -> "Not for me"
                                null -> "Rate"
                            },
                        icon = BenflixIcon.THUMB,
                        onClick = { showRate = true },
                    ),
                )
            }
            add(TitleButton(label = "More", icon = BenflixIcon.MORE, onClick = { showMore = true }))
        }
    val scores = rememberScoreParts(movie)
    val strip =
        remember(movie, state.chosenStreams, scores) {
            buildList {
                if (!easy) addAll(scores)
                audioLanguage(movie, state)?.let { add(StripPart(it)) }
                directors(movie)?.let { add(StripPart("Director", it)) }
            }
        }
    val tabs =
        buildList {
            add(
                TitleTab("Overview") { scope ->
                    TitleOverview(scope = scope, title = movie.title.orEmpty(), story = dto.overview, strip = strip)
                },
            )
            val hasMore =
                state.similar.isNotEmpty() || collection != null ||
                    (!easy && (state.discovered.isNotEmpty() || state.fromDirector.isNotEmpty()))
            if (hasMore) {
                add(
                    TitleTab("More like this") { scope ->
                        val several =
                            listOf(
                                state.similar.isNotEmpty(),
                                collection != null,
                                !easy && state.discovered.isNotEmpty(),
                                !easy && state.fromDirector.isNotEmpty(),
                            ).count { it } > 1
                        TitlePosterRow(
                            scope = scope,
                            items = state.similar,
                            onClick = actions.openItem,
                            name = if (several) "More like this" else null,
                        )
                        if (collection != null) {
                            TitlePosterRow(
                                scope = scope,
                                items = collection.items,
                                onClick = actions.openItem,
                                name = "In this collection",
                                current = movie,
                            )
                        }
                        if (!easy && state.discovered.isNotEmpty()) {
                            AskShelf(
                                items = state.discovered,
                                side = scope.side,
                                showName = !scope.wide,
                                onClick = actions.openAsk,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                        if (!easy) {
                            TitlePosterRow(
                                scope = scope,
                                items = state.fromDirector,
                                onClick = actions.openItem,
                                name = "More from ${state.directorName ?: "the director"}",
                            )
                        }
                    },
                )
            }
            if (state.people.isNotEmpty()) {
                add(TitleTab("Cast") { scope -> TitleCastRow(scope, state.people, actions.openPerson) })
            }
            if (state.parentsGuide.isNotEmpty()) {
                add(TitleTab("Parents' guide") { scope -> TitleParentsGuide(scope, state.parentsGuide) })
            }
            if (state.facts.isNotEmpty()) {
                add(TitleTab("Did you know") { scope -> TitleFacts(scope, state.facts, BenflixColors.Movies) })
            }
        }
    TitlePage(
        title = movie.title.orEmpty(),
        originalTitle = dto.originalTitle,
        accent = BenflixColors.Movies,
        picture = TitlePicture.Backdrop(movie),
        info = remember(movie) { movieInfo(movie) },
        rating = dto.officialRating,
        infoExtra =
            if (part != null && collection != null) {
                { TitleTextLink("Part ${part.first} of ${part.second} · ${collection.name}", { actions.openCollection(collection) }) }
            } else {
                null
            },
        buttons = buttons,
        tabs = tabs,
        big = easy,
        modifier = modifier,
    )

    if (showMore) {
        val aniList = AniListFacts.info.value[movie.id]
        TitleMorePanel(
            title = movie.title.orEmpty(),
            onDismiss = { showMore = false },
            items =
                buildList {
                    add(
                        TitleMoreItem(
                            if (movie.played) "Mark as not watched" else "Mark as watched",
                            BenflixIcon.CHECK,
                            { actions.setWatched(!movie.played) },
                        ),
                    )
                    if (canResume) add(TitleMoreItem("Play from the start", BenflixIcon.PLAY, { actions.play(Duration.ZERO) }))
                    add(TitleMoreItem("Audio, subtitles and version", BenflixIcon.GEAR, actions.streams))
                    if (collection != null) {
                        add(TitleMoreItem("Go to ${collection.name}", BenflixIcon.FILM, { actions.openCollection(collection) }))
                    }
                    if (aniList != null) add(TitleMoreItem("Open on AniList or MAL", BenflixIcon.ARROW, { showLinks = true }))
                    if (isAdmin) {
                        actions.pickPoster?.let { add(TitleMoreItem("Pick a poster", BenflixIcon.IMAGE, it, youOnly = true)) }
                        add(TitleMoreItem("File details", BenflixIcon.SERVER, actions.fileDetails, youOnly = true))
                    }
                },
        )
    }
    if (showRate) {
        BenflixChoiceDialog(
            title = "Rate ${movie.title}",
            subtitle = "This shapes what Benflix picks for you",
            onDismissRequest = { showRate = false },
            choices =
                listOf(
                    BenflixChoice("Not for me", { actions.rate(if (likes == false) null else false) }, selected = likes == false),
                    BenflixChoice(
                        "I like it",
                        { actions.rate(if (likes == true && !movie.favorite) null else true) },
                        selected = likes == true && !movie.favorite,
                    ),
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

/** "2024 · 2h 46m · Sci-fi · Adventure" (the age rating has its own small box) */
private fun movieInfo(movie: BaseItem): String {
    val d = movie.data
    return listOfNotNull(
        d.productionYear?.toString(),
        d.runTimeTicks?.ticks?.let(::lengthText),
        d.genres
            ?.take(2)
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" · "),
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun directors(movie: BaseItem): String? =
    movie.data.people
        .orEmpty()
        .filter { it.type == PersonKind.DIRECTOR }
        .mapNotNull { it.name }
        .take(2)
        .joinToString(", ")
        .takeIf { it.isNotBlank() }

/** The language of the sound track that will play ("English") */
private fun audioLanguage(
    movie: BaseItem,
    state: MovieState,
): String? {
    val streams =
        movie.data.mediaSources
            ?.firstOrNull()
            ?.mediaStreams
            .orEmpty()
    val stream =
        state.chosenStreams?.audioStream?.stream
            ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault }
            ?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
            ?: return null
    val code = stream.language
    if (!code.isNullOrBlank() && code != "und") {
        val name =
            try {
                java.util.Locale
                    .forLanguageTag(code)
                    .getDisplayLanguage(java.util.Locale.ENGLISH)
            } catch (ex: Exception) {
                null
            }
        if (!name.isNullOrBlank() && name != code) return name
    }
    return null
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
