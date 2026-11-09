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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixWatchTime
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.WatchTime
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixPopups
import com.github.damontecres.wholphin.ui.components.popupTurn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixType
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
    /** Benflix music: the Wrapped music slide (null = not enough music this year) */
    val music: WrappedMusic? = null,
)

data class WrappedMusic(
    val minutes: Int,
    val songs: Int,
    val topArtists: List<String>,
    val topSong: String?,
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
        private val watchTime: BenflixWatchTime,
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
                    // Not on Kids or Guest: they'd see the grown-up's year and use up their one showing
                    if (KidsMode.isActive || GuestMode.isActive) return@launch
                    if (!prefs().getBoolean(key(today.year), false)) {
                        val year = build(today.year)
                        if (!KidsMode.isActive && !GuestMode.isActive) _wrapped.value = year
                    }
                }
            }
        }

        /** The "Your year so far" button on My watching: build this year's recap now, any month */
        private var manual = false
        private var handledPresses = 0

        fun showNow(press: Int) {
            if (press <= handledPresses) return
            handledPresses = press
            if (KidsMode.isActive || GuestMode.isActive) return
            manual = true
            viewModelScope.launch(WholphinDispatchers.IO) {
                val year = build(LocalDate.now().year)
                if (year != null && !KidsMode.isActive && !GuestMode.isActive) _wrapped.value = year
            }
        }

        fun done() {
            val year = _wrapped.value?.year ?: return
            // Opened with the button: the December pop-up still comes once
            if (manual) {
                manual = false
                _wrapped.value = null
                return
            }
            if (!KidsMode.isActive && !GuestMode.isActive) prefs().edit { putBoolean(key(year), true) }
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
                val real = serverRepository.currentUser?.id?.let { watchTime.read(it) }
                val music = music(year)
                summarize(year, played, real)?.copy(music = music)
                    ?: music?.let { WrappedYear(year, 0, 0, 0, emptyList(), null, null, "a Music Lover", it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not build Benflix Wrapped")
                null
            }

        /** Songs played this year: minutes (length x plays), top artists and the most played song */
        private suspend fun music(year: Int): WrappedMusic? =
            try {
                val songs =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                includeItemTypes = listOf(BaseItemKind.AUDIO),
                                recursive = true,
                                filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_PLAYED),
                                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                enableImages = false,
                                limit = 5_000,
                            ),
                        ).content
                        .items
                        .filter { it.userData?.lastPlayedDate?.year == year }
                if (songs.size < MIN_SONGS) {
                    null
                } else {
                    val plays = { s: BaseItemDto -> s.userData?.playCount?.coerceAtLeast(1) ?: 1 }
                    val minutes = (songs.sumOf { (it.runTimeTicks ?: 0L) * plays(it) } / TICKS_PER_MINUTE).toInt()
                    val artists =
                        songs
                            .groupBy { it.albumArtist ?: it.artists?.firstOrNull() }
                            .filterKeys { !it.isNullOrBlank() }
                            .mapValues { (_, list) -> list.sumOf(plays) }
                            .entries
                            .sortedByDescending { it.value }
                            .take(3)
                            .mapNotNull { it.key }
                    val top = songs.maxByOrNull(plays)
                    WrappedMusic(
                        minutes = minutes,
                        songs = songs.size,
                        topArtists = artists,
                        topSong = top?.let { listOfNotNull(it.name, it.albumArtist).joinToString(" by ") },
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Wrapped music")
                null
            }

        private fun summarize(
            year: Int,
            played: List<BaseItemDto>,
            real: WatchTime?,
        ): WrappedYear? {
            if (played.size < MIN_WATCHED) return null
            val movies = played.filter { it.type == BaseItemKind.MOVIE }
            val episodes = played.filter { it.type == BaseItemKind.EPISODE }
            // Benflix's own count of real watching; anything from before it started counting uses the full length
            val sinceMs = real?.sinceMs
            val ticks =
                played
                    .filter { item ->
                        sinceMs == null ||
                            (
                                item.userData
                                    ?.lastPlayedDate
                                    ?.atZone(java.time.ZoneId.systemDefault())
                                    ?.toInstant()
                                    ?.toEpochMilli() ?: 0L
                            ) < sinceMs
                    }.sumOf { (it.runTimeTicks ?: 0L) * (it.userData?.playCount?.coerceAtLeast(1) ?: 1) }
            val hours = (ticks / TICKS_PER_HOUR).toInt() + ((real?.seconds(year) ?: 0L) / 3600L).toInt()
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
            private const val TICKS_PER_MINUTE = 600_000_000L
            private const val MIN_SONGS = 10
        }
    }

/** Sits on top of every page; waits while something is playing */
@Composable
fun BenflixWrappedLayer(
    currentDestination: Destination?,
    viewModel: BenflixWrappedViewModel = hiltViewModel(),
) {
    val asked by BenflixWrappedButton.presses.collectAsState()
    LaunchedEffect(asked) { if (asked > 0) viewModel.showNow(asked) }
    val wrapped by viewModel.wrapped.collectAsState()
    val playing = currentDestination is Destination.Playback || currentDestination is Destination.PlaybackList
    // Never on Kids or Guest (e.g. if the profile changed after the recap was built)
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    if (!popupTurn("wrapped", BenflixPopups.WRAPPED, wrapped != null && !playing && !kids && !guest)) return
    val w = wrapped ?: return
    val focus = remember { FocusRequester() }
    // Benflix music: a second slide for your year in music (or the only one, if you only listened)
    var slide by remember(w) { mutableIntStateOf(if (w.movies + w.episodes == 0 && w.music != null) 1 else 0) }
    if (slide == 1 && w.music != null) {
        WrappedMusicSlide(w.year, w.music, onDone = viewModel::done)
        return
    }
    BasicDialog(onDismissRequest = viewModel::done) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .background(
                        com.github.damontecres.wholphin.ui.theme.BenflixColors.Panel,
                        RoundedCornerShape(16.dp),
                    ).padding(28.dp),
        ) {
            Text(
                text = "YOUR ${w.year} ON BENFLIX",
                color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Muted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = BenflixType.small,
            )
            Text(
                text = "You're ${w.watcherKind}!",
                fontWeight = FontWeight.Bold,
                fontSize = BenflixType.pageTitle,
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
                    fontSize = BenflixType.lead,
                    textAlign = TextAlign.Center,
                )
            }
            w.topMovie?.let { Text("Favourite movie: $it", color = Color.White, fontSize = BenflixType.lead, textAlign = TextAlign.Center) }
            w.topGenre?.let { Text("Most watched: $it", color = Color(0xFFE5E5EA), fontSize = BenflixType.body) }
            Button(
                onClick = { if (w.music != null) slide = 1 else viewModel.done() },
                modifier = Modifier.padding(top = 8.dp).focusRequester(focus),
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) {
                Text(if (w.music != null) "Next: your music" else "Thanks, Benflix!")
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
        Text(number, fontSize = BenflixType.hero, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = BenflixType.small, color = Color(0xFFE5E5EA))
    }
}

/** Pressing "Your year so far" on My watching asks the Wrapped pop-up to open */
object BenflixWrappedButton {
    val presses = MutableStateFlow(0)

    fun press() {
        presses.value += 1
    }
}

/** Benflix Wrapped, the music slide (approved mw-wrapped): minutes listened, top artists and your song of the year */
@Composable
private fun WrappedMusicSlide(
    year: Int,
    m: WrappedMusic,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    BasicDialog(onDismissRequest = onDone) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .background(
                        com.github.damontecres.wholphin.ui.theme.BenflixColors.Panel,
                        RoundedCornerShape(16.dp),
                    ).padding(28.dp),
        ) {
            Text(
                text = "YOUR $year IN MUSIC",
                color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Music,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = BenflixType.small,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                WrappedStat("%,d".format(m.minutes), if (m.minutes == 1) "minute" else "minutes")
                WrappedStat("${m.songs}", if (m.songs == 1) "song" else "songs")
            }
            if (m.topArtists.isNotEmpty()) {
                Text(
                    text = "Top artists: " + m.topArtists.joinToString(", "),
                    color = Color.White,
                    fontSize = BenflixType.lead,
                    textAlign = TextAlign.Center,
                )
            }
            m.topSong?.let { Text("Song of the year: $it", color = Color.White, fontSize = BenflixType.lead, textAlign = TextAlign.Center) }
            Button(
                onClick = onDone,
                modifier = Modifier.padding(top = 8.dp).focusRequester(focus),
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) {
                Text("Thanks, Benflix!")
            }
        }
    }
    LaunchedEffect(year) {
        delay(200)
        try {
            focus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}
