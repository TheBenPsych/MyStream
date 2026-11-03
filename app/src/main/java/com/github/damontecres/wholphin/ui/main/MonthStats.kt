package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Benflix: this month so far, on My Benflix */
data class MonthStats(
    val month: String,
    val hours: Int,
    val minutes: Int,
    val titles: Int,
    val topGenre: String?,
    val topShow: String?,
)

@HiltViewModel
class MonthStatsViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _stats = MutableStateFlow<MonthStats?>(null)
        val stats: StateFlow<MonthStats?> = _stats

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                _stats.value =
                    try {
                        build()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Month stats")
                        null
                    }
            }
        }

        /**
         * Jellyfin remembers when each thing was last watched, so this counts everything last watched this
         * month (and how far into it). A movie watched twice this month counts once.
         */
        private suspend fun build(): MonthStats? {
            val userId = serverRepository.currentUser?.id ?: return null
            val start = LocalDate.now().withDayOfMonth(1).atStartOfDay()
            val watched =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                            recursive = true,
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = listOf(ItemFields.GENRES),
                            enableImages = false,
                            limit = 600,
                        ),
                    ).content.items
                    .filter { it.userData?.lastPlayedDate?.isAfter(start) == true }
            if (watched.isEmpty()) return null
            val ticks =
                watched.sumOf { item ->
                    val runtime = item.runTimeTicks ?: 0L
                    if (item.userData?.played == true) runtime else (item.userData?.playbackPositionTicks ?: 0L).coerceAtMost(runtime)
                }
            val totalMinutes = (ticks / 600_000_000L).toInt()
            val topShowEntry =
                watched
                    .filter { it.type == BaseItemKind.EPISODE && !it.seriesName.isNullOrBlank() }
                    .groupBy { it.seriesId }
                    .maxByOrNull { it.value.size }
            // Episodes often have no genres of their own, so they count as their show's genres
            val showGenres =
                watched
                    .mapNotNull { it.seriesId }
                    .distinct()
                    .take(10)
                    .associateWith { id ->
                        try {
                            api.userLibraryApi
                                .getItem(id)
                                .content.genres
                                .orEmpty()
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            emptyList()
                        }
                    }
            val topGenre =
                watched
                    .flatMap { item -> item.genres?.takeIf { it.isNotEmpty() } ?: showGenres[item.seriesId].orEmpty() }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
            return MonthStats(
                month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                hours = totalMinutes / 60,
                minutes = totalMinutes % 60,
                titles = watched.size,
                topGenre = topGenre,
                topShow = topShowEntry?.value?.firstOrNull()?.seriesName,
            )
        }
    }

/** "Your November": hours watched, top genre and most-watched show */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthStatsCard(
    modifier: Modifier = Modifier,
    viewModel: MonthStatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    // Not shown on Kids
    val kids by KidsMode.active.collectAsState()
    if (kids) return
    val s = stats ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .widthIn(max = 720.dp)
                .background(Color(0xFF1C1C21), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = "YOUR ${s.month.uppercase()} SO FAR",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            color = Color(0xFFE50914),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Stat(
                value = if (s.hours > 0) "${s.hours} h ${s.minutes} m" else "${s.minutes} m",
                label = "watched",
            )
            Stat(value = s.titles.toString(), label = if (s.titles == 1) "thing watched" else "things watched")
            s.topGenre?.let { Stat(value = it, label = "top genre") }
            s.topShow?.let { Stat(value = it, label = "most watched show") }
        }
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
) {
    Column(modifier = Modifier.widthIn(max = 240.dp)) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = label, fontSize = 13.sp, color = Color.White.copy(alpha = .65f))
    }
}
