package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixPopups
import com.github.damontecres.wholphin.ui.components.popupTurn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** One profile's year on Benflix */
data class WrappedYear(
    val year: Int,
    val hours: Int,
    val movies: Int,
    val episodes: Int,
    val topShows: List<String>,
    val topMovie: String?,
    val topGenre: String?,
    val watcherKind: String,
)

/**
 * Benflix Wrapped: in December, each profile gets a fun pop-up recap of their year once:
 * hours watched, top shows, top genre and a "you're a ___ watcher" card.
 */
@HiltViewModel
class BenflixWrappedViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _wrapped = MutableStateFlow<WrappedYear?>(null)
        val wrapped: StateFlow<WrappedYear?> = _wrapped

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun key(year: Int) = "shown-$year-" + (serverRepository.currentUser?.id?.toString() ?: "none")

        init {
            val today = LocalDate.now()
            if (today.monthValue == 12) {
                viewModelScope.launch(WholphinDispatchers.IO) {
                    // Let Home settle first
                    delay(6_000)
                    if (!prefs().getBoolean(key(today.year), false)) {
                        _wrapped.value = build(today.year)
                    }
                }
            }
        }

        fun done() {
            val year = _wrapped.value?.year ?: return
            prefs().edit { putBoolean(key(year), true) }
            _wrapped.value = null
        }

        private suspend fun build(year: Int): WrappedYear? =
            try {
                val played =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                recursive = true,
                                isPlayed = true,
                                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                fields = listOf(ItemFields.GENRES),
                                enableImages = false,
                                limit = 3_000,
                            ),
                        ).content
                        .items
                        .filter { it.userData?.lastPlayedDate?.year == year }
                summarize(year, played)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not build Benflix Wrapped")
                null
            }

        private fun summarize(
            year: Int,
            played: List<BaseItemDto>,
        ): WrappedYear? {
            if (played.size < MIN_WATCHED) return null
            val movies = played.filter { it.type == BaseItemKind.MOVIE }
            val episodes = played.filter { it.type == BaseItemKind.EPISODE }
            val ticks = played.sumOf { (it.runTimeTicks ?: 0L) * (it.userData?.playCount?.coerceAtLeast(1) ?: 1) }
            val hours = (ticks / TICKS_PER_HOUR).toInt()
            val topShows =
                episodes
                    .groupingBy { it.seriesName.orEmpty() }
                    .eachCount()
                    .filterKeys { it.isNotBlank() }
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }
            val topMovie = movies.maxByOrNull { it.userData?.playCount ?: 0 }?.name
            val genres =
                played
                    .flatMap { it.genres.orEmpty() }
                    .groupingBy { it }
                    .eachCount()
            val topGenre = genres.maxByOrNull { it.value }?.key
            return WrappedYear(
                year = year,
                hours = hours,
                movies = movies.size,
                episodes = episodes.size,
                topShows = topShows,
                topMovie = topMovie,
                topGenre = topGenre,
                watcherKind = watcherKind(movies.size, episodes.size, topGenre),
            )
        }

        private fun watcherKind(
            movies: Int,
            episodes: Int,
            genre: String?,
        ): String {
            val g = genre?.lowercase().orEmpty()
            return when {
                "anime" in g || "animation" in g -> "an Anime Explorer"
                "horror" in g -> "a Fearless Night Owl"
                "comedy" in g -> "a Laugh Collector"
                "documentary" in g -> "a Curious Mind"
                "action" in g || "adventure" in g -> "an Adrenaline Junkie"
                "romance" in g -> "a Hopeless Romantic"
                "science fiction" in g || "fantasy" in g -> "a World Traveller"
                episodes > movies * 8 -> "a Binge Champion"
                movies > episodes -> "a Movie Night Regular"
                else -> "an All-Rounder"
            }
        }

        companion object {
            private const val PREFS = "benflix_wrapped"
            private const val MIN_WATCHED = 5
            private const val TICKS_PER_HOUR = 36_000_000_000L
        }
    }

/** Sits on top of every page; waits while something is playing */
@Composable
fun BenflixWrappedLayer(
    currentDestination: Destination?,
    viewModel: BenflixWrappedViewModel = hiltViewModel(),
) {
    val wrapped by viewModel.wrapped.collectAsState()
    val playing = currentDestination is Destination.Playback || currentDestination is Destination.PlaybackList
    if (!popupTurn("wrapped", BenflixPopups.WRAPPED, wrapped != null && !playing)) return
    val w = wrapped ?: return
    val focus = remember { FocusRequester() }
    BasicDialog(onDismissRequest = viewModel::done) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF7F1D1D), Color(0xFF1E1B4B))),
                        RoundedCornerShape(16.dp),
                    ).padding(28.dp),
        ) {
            Text(
                text = "YOUR ${w.year} ON BENFLIX",
                color = Color(0xFFFFD27A),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = 14.sp,
            )
            Text(
                text = "You're ${w.watcherKind}!",
                fontFamily = BenflixTitleFont,
                fontSize = 34.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                WrappedStat("${w.hours}", if (w.hours == 1) "hour" else "hours")
                WrappedStat("${w.movies}", if (w.movies == 1) "movie" else "movies")
                WrappedStat("${w.episodes}", if (w.episodes == 1) "episode" else "episodes")
            }
            if (w.topShows.isNotEmpty()) {
                Text(
                    text = "Top shows: " + w.topShows.joinToString(", "),
                    color = Color.White,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
            w.topMovie?.let { Text("Favourite movie: $it", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center) }
            w.topGenre?.let { Text("Most watched: $it", color = Color(0xFFE5E5EA), fontSize = 16.sp) }
            Button(onClick = viewModel::done, modifier = Modifier.padding(top = 8.dp).focusRequester(focus)) {
                Text("Thanks, Benflix!")
            }
        }
    }
    LaunchedEffect(w.year) {
        delay(200)
        try {
            focus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}

@Composable
private fun WrappedStat(
    number: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 14.sp, color = Color(0xFFE5E5EA))
    }
}
