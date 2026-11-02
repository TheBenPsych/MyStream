package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.detail.discover.str
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/** Newer seasons of a show that are already out but not on Benflix (and not asked for yet) */
data class NextSeasons(
    val tmdbId: Int,
    val seasons: List<Int>,
)

/**
 * Benflix: once you've watched everything of a show that's on Benflix, and a newer season is
 * already out, the series page offers "Season 4 is out. Ask for it" (one press, through Requests).
 * Hidden on Kids and when Requests isn't connected.
 */
@HiltViewModel
class NextSeasonAskViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val seerrService: SeerrService,
    ) : ViewModel() {
        private val _next = MutableStateFlow<NextSeasons?>(null)
        val next: StateFlow<NextSeasons?> = _next

        /** When the next episode comes out, for shows still airing */
        private val _nextAir = MutableStateFlow<LocalDate?>(null)
        val nextAir: StateFlow<LocalDate?> = _nextAir
        private var loadedFor: UUID? = null

        fun load(seriesId: UUID) {
            if (loadedFor == seriesId) return
            loadedFor = seriesId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    if (KidsMode.isActive || !seerrService.active.first()) return@launch
                    val userId = serverRepository.currentUser?.id
                    val series = api.userLibraryApi.getItem(seriesId, userId).content
                    val tmdb = series.providerIds?.get("Tmdb")?.toIntOrNull() ?: return@launch
                    val json =
                        seerrService.api.getRaw("/tv/$tmdb")?.let { Json.parseToJsonElement(it).jsonObject }
                            ?: return@launch
                    val today = LocalDate.now()
                    // "New episode Thursday": the next episode's date, if it's coming up
                    _nextAir.value =
                        (json["nextEpisodeToAir"] as? JsonObject)
                            ?.get("airDate")
                            .str()
                            ?.take(10)
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            ?.takeIf { !it.isBefore(today) }
                    // Only once everything on Benflix has been watched
                    val unwatched = series.userData?.unplayedItemCount ?: return@launch
                    if (unwatched > 0) return@launch
                    val onBenflix =
                        api.tvShowsApi
                            .getSeasons(seriesId = seriesId, userId = userId)
                            .content.items
                            .mapNotNull { it.indexNumber }
                            .filter { it > 0 }
                    val lastHere = onBenflix.maxOrNull() ?: return@launch
                    // Seerr status per season: 1 unknown, 2 asked for, 3 on its way, 4 some, 5 here
                    val statusBySeason =
                        ((json["mediaInfo"] as? JsonObject)?.get("seasons") as? JsonArray)
                            .orEmpty()
                            .mapNotNull { it as? JsonObject }
                            .mapNotNull { s ->
                                val n = s["seasonNumber"].str()?.toIntOrNull() ?: return@mapNotNull null
                                n to (s["status"].str()?.toIntOrNull() ?: 1)
                            }.toMap()
                    val newer =
                        (json["seasons"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull { it as? JsonObject }
                            .mapNotNull { s ->
                                val n = s["seasonNumber"].str()?.toIntOrNull() ?: return@mapNotNull null
                                val aired =
                                    s["airDate"]
                                        .str()
                                        ?.take(10)
                                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                        ?.let { !it.isAfter(today) } ?: false
                                val status = statusBySeason[n] ?: 1
                                if (n > lastHere && aired && status <= 1) n else null
                            }.sorted()
                    if (newer.isNotEmpty()) _next.value = NextSeasons(tmdb, newer)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not check for a newer season of %s", seriesId)
                }
            }
        }

        fun ask() {
            val next = _next.value ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    seerrService.api.requestApi.requestPost(
                        RequestPostRequest(
                            mediaId = next.tmdbId,
                            mediaType = RequestPostRequest.MediaType.TV,
                            seasons = next.seasons,
                            is4k = false,
                            tags = emptyList(),
                        ),
                    )
                    _next.value = null
                    showToast(context, "Asked for ${seasonWords(next.seasons)}")
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not ask for seasons %s", next.seasons)
                    showToast(context, "Couldn't ask for it. Try again from Requests.")
                }
            }
        }
    }

private fun seasonWords(seasons: List<Int>): String =
    if (seasons.size == 1) "Season ${seasons.first()}" else "Seasons ${seasons.first()} to ${seasons.last()}"

@Composable
fun NextSeasonAsk(
    seriesId: UUID,
    modifier: Modifier = Modifier,
    viewModel: NextSeasonAskViewModel = hiltViewModel(key = "next-season-$seriesId"),
) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val next by viewModel.next.collectAsState()
    val nextAir by viewModel.nextAir.collectAsState()
    val seasons = next?.seasons
    if (nextAir == null && seasons == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        nextAir?.let { date ->
            Text(
                text = "New episode ${airDayWords(date)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF46D369),
            )
        }
        if (seasons != null) {
            val words = seasonWords(seasons)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = viewModel::ask) {
                    Text("Ask for $words")
                }
                Text(
                    text = if (seasons.size == 1) "$words is out, but not on Benflix yet" else "$words are out, but not on Benflix yet",
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** "today", "tomorrow", "Thursday, Nov 6" (this week) or "Nov 20" */
private fun airDayWords(date: LocalDate): String {
    val today = LocalDate.now()
    val days =
        java.time.temporal.ChronoUnit.DAYS
            .between(today, date)
    return when {
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days < 7L -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
        else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
