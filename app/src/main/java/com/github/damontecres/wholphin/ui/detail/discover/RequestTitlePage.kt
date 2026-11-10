package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.DiscoverPersonRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleOverview
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import kotlin.math.roundToInt

/**
 * Benflix Redesign 2: a movie or show that isn't on Benflix yet, on the shared title page frame.
 * Buttons: Ask for it (or where the ask is: waiting, on its way, Watch now), Ask for the rest (shows
 * partly here), Trailer (greyed out when there isn't one), Cancel my ask. Tabs: Overview (story, scores,
 * who asked with Approve / Decline for Ben, tags), More to ask for, Cast.
 */
@Composable
fun RequestTitlePage(
    isMovie: Boolean,
    tmdbId: Int?,
    title: String,
    story: String?,
    info: String,
    backdropUrl: String?,
    availability: SeerrAvailability,
    isAdmin: Boolean,
    canRequest: Boolean,
    canCancel: Boolean,
    cantFind: Boolean,
    trailers: List<Trailer>,
    rating: DiscoverRating?,
    similar: List<DiscoverItem>,
    recommended: List<DiscoverItem>,
    people: List<DiscoverItem>,
    requestOnClick: () -> Unit,
    cancelOnClick: () -> Unit,
    goToOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    onClickItem: (DiscoverItem) -> Unit,
    onClickPerson: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kids by KidsMode.active.collectAsState()
    val here = availability == SeerrAvailability.AVAILABLE || availability == SeerrAvailability.PARTIALLY_AVAILABLE
    val blocked = cantFind && !here
    val first =
        when {
            blocked -> {
                TitleButton("Couldn't find it", BenflixIcon.WARNING, {}, off = "Ben looked, but couldn't find this one yet")
            }

            kids && availability == SeerrAvailability.UNKNOWN -> {
                TitleButton("Ask for it", BenflixIcon.REQUESTS, {}, off = "Ask a grown-up to ask for this one")
            }

            availability == SeerrAvailability.UNKNOWN -> {
                TitleButton(
                    "Ask for it",
                    BenflixIcon.REQUESTS,
                    requestOnClick,
                    off = if (canRequest) null else "Your profile can't ask for these",
                )
            }

            availability == SeerrAvailability.PENDING -> {
                TitleButton(
                    if (isAdmin) "Waiting for you" else "Asked · waiting for Ben",
                    BenflixIcon.CALENDAR,
                    {},
                    note = if (isAdmin) "Approve or decline it in Overview" else "Ben has to say yes first",
                )
            }

            availability == SeerrAvailability.PROCESSING -> {
                TitleButton("On its way", BenflixIcon.DOWNLOAD, {}, note = "Benflix is getting it now")
            }

            here -> {
                TitleButton("Watch now", BenflixIcon.PLAY, goToOnClick)
            }

            else -> {
                TitleButton("Ask for it", BenflixIcon.REQUESTS, {}, off = "This one can't be asked for")
            }
        }
    val trailer = trailers.firstOrNull()
    val buttons =
        buildList {
            add(first)
            if (availability == SeerrAvailability.PARTIALLY_AVAILABLE && !cantFind && !kids) {
                add(TitleButton("Ask for the rest", BenflixIcon.REQUESTS, requestOnClick, note = "Some of it isn't on Benflix yet"))
            }
            add(
                TitleButton(
                    "Trailer",
                    BenflixIcon.TRAILER,
                    { trailer?.let(trailerOnClick) },
                    off = if (trailer == null) "No trailer for this one yet" else null,
                ),
            )
            if (canCancel && !kids) {
                add(TitleButton("Cancel my ask", BenflixIcon.TRASH, cancelOnClick))
            }
        }
    val tabs =
        buildList {
            add(
                TitleTab("Overview") { scope ->
                    TitleOverview(
                        scope = scope,
                        title = title,
                        story = story,
                        strip = emptyList(),
                        extra =
                            tmdbId?.let { id ->
                                {
                                    Column(Modifier.fillMaxWidth()) {
                                        SeerrExtrasBlock(
                                            isMovie = isMovie,
                                            tmdbId = id,
                                            fallbackCritic = rating?.criticRating,
                                            fallbackAudience = rating?.audienceRating?.let { (it * 10).roundToInt() },
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        )
                                        // Who asked (one line), with Approve / Decline for Ben
                                        WhoAskedBlock(
                                            isMovie = isMovie,
                                            tmdbId = id,
                                            title = title,
                                            available = availability == SeerrAvailability.AVAILABLE,
                                            modifier = Modifier.padding(bottom = 4.dp),
                                        )
                                        com.github.damontecres.wholphin.ui.discover.CantFindBlock(
                                            isMovie = isMovie,
                                            tmdbId = id,
                                            available = availability == SeerrAvailability.AVAILABLE,
                                            modifier = Modifier.padding(bottom = 4.dp),
                                        )
                                        SeerrTagsBlock(isMovie = isMovie, tmdbId = id, modifier = Modifier.padding(top = 4.dp))
                                        AnimeGenresBlock(isMovie = isMovie, tmdbId = id, modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            },
                    )
                },
            )
            if (similar.isNotEmpty() || recommended.isNotEmpty()) {
                add(
                    TitleTab("More to ask for") { scope ->
                        val seen = mutableSetOf<String>()
                        val alike = similar.filter { seen.add("${it.type}-${it.id}") }
                        val second = recommended.filter { seen.add("${it.type}-${it.id}") }
                        listOf("More like this" to alike, "Recommended" to second).forEach { (name, list) ->
                            if (list.isNotEmpty()) {
                                ItemRow(
                                    title = name,
                                    items = list,
                                    onClickItem = { _, item -> onClickItem(item) },
                                    onLongClickItem = { _, _ -> },
                                    cardContent = { _, item, mod, onClick, onLongClick ->
                                        DiscoverItemCard(
                                            item = item,
                                            onClick = onClick,
                                            onLongClick = onLongClick,
                                            showOverlay = true,
                                            modifier = mod,
                                        )
                                    },
                                    horizontalPadding = scope.side,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                )
                            }
                        }
                    },
                )
            }
            if (people.isNotEmpty()) {
                add(
                    TitleTab("Cast") { scope ->
                        DiscoverPersonRow(
                            people = people,
                            onClick = onClickPerson,
                            modifier = Modifier.padding(start = scope.side),
                        )
                    },
                )
            }
        }
    TitlePage(
        title = title,
        accent = if (isMovie) BenflixColors.Movies else BenflixColors.Shows,
        picture = TitlePicture.Wide(backdropUrl),
        info = info,
        buttons = buttons,
        tabs = tabs,
        modifier = modifier,
    )
}
