package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetailsText
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.listToDotString
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

@Composable
fun DiscoverMovieDetailsHeader(
    preferences: UserPreferences,
    movie: MovieDetails,
    rating: DiscoverRating?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        // Benflix redesign: the big white name, like the Movies page
        androidx.tv.material3.Text(
            text = movie.title.orEmpty(),
            color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
            fontSize = 40.sp,
            lineHeight = 42.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = HeaderUtils.startPadding),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier.fillMaxWidth(
                    com.github.damontecres.wholphin.ui.touch
                        .detailsTextFraction(),
                ),
        ) {
            val resources = LocalResources.current
            // Benflix (Ben's pick "A · Fact cards"): one short line, then the scores and facts boxes
            val details =
                remember(movie, resources) {
                    buildList {
                        movie.releaseDate
                            ?.take(4)
                            ?.takeIf { it.isNotNullOrBlank() }
                            ?.let(::add)
                        movie.runtime
                            ?.toDouble()
                            ?.minutes
                            ?.roundMinutes
                            ?.let { add(resources.formatDuration(it)) }
                        movie.genres
                            ?.mapNotNull { it.name }
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { add(it.joinToString(", ")) }
                    }.let { listToDotString(it, null, null) }
                }

            QuickDetailsText(details, Modifier.padding(start = HeaderUtils.startPadding, bottom = 6.dp))
            movie.id?.let { tmdbId ->
                SeerrExtrasBlock(
                    isMovie = true,
                    tmdbId = tmdbId,
                    fallbackCritic = rating?.criticRating,
                    fallbackAudience = rating?.audienceRating?.let { (it * 10).roundToInt() },
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, bottom = 6.dp),
                )
                // Benflix (admins): who already asked for this, with Approve / Decline
                val onBenflix = movie.mediaInfo?.status == com.github.damontecres.wholphin.data.model.SeerrAvailability.AVAILABLE.status
                WhoAskedBlock(
                    isMovie = true,
                    tmdbId = tmdbId,
                    title = movie.title,
                    available = onBenflix,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, bottom = 4.dp),
                )
                // Benflix: "Ben looked, but couldn't find this yet" (everyone sees it)
                com.github.damontecres.wholphin.ui.discover.CantFindBlock(
                    isMovie = true,
                    tmdbId = tmdbId,
                    available = onBenflix,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, bottom = 4.dp),
                )
            }

            // Description
            movie.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }

            // Benflix: 4 tags, and "+N more" for the rest
            movie.id?.let { tmdbId ->
                SeerrTagsBlock(
                    isMovie = true,
                    tmdbId = tmdbId,
                    onFocused = {
                        scope.launch(ExceptionHandler()) {
                            bringIntoViewRequester.bringIntoView()
                        }
                    },
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, top = 6.dp),
                )
                // Benflix: anime genres from AniList (Isekai, Slice of Life...); press one to see them all
                AnimeGenresBlock(
                    isMovie = true,
                    tmdbId = tmdbId,
                    onFocused = {
                        scope.launch(ExceptionHandler()) {
                            bringIntoViewRequester.bringIntoView()
                        }
                    },
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, top = 8.dp),
                )
            }
        }
    }
}
