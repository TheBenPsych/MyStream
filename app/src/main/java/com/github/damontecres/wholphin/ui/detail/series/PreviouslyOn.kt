package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

/** The last episode you watched of a show, to remind you what happened */
data class PreviouslyOnInfo(
    val label: String,
    val overview: String,
    val daysAgo: Long,
)

@HiltViewModel
class PreviouslyOnViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _info = MutableStateFlow<PreviouslyOnInfo?>(null)
        val info: StateFlow<PreviouslyOnInfo?> = _info
        private var loadedFor: UUID? = null

        fun load(seriesId: UUID) {
            if (loadedFor == seriesId) return
            loadedFor = seriesId
            viewModelScope.launch(WholphinDispatchers.IO) {
                _info.value =
                    try {
                        val last =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = serverRepository.currentUser?.id,
                                        parentId = seriesId,
                                        recursive = true,
                                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                                        isPlayed = true,
                                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                        sortOrder = listOf(SortOrder.DESCENDING),
                                        limit = 1,
                                        fields = listOf(ItemFields.OVERVIEW),
                                    ),
                                ).content.items
                                .firstOrNull()
                        val played = last?.userData?.lastPlayedDate
                        val overview = last?.overview
                        if (last == null || played == null || overview.isNullOrBlank()) {
                            null
                        } else {
                            val days =
                                java.time.Duration
                                    .between(played, LocalDateTime.now(ZoneOffset.UTC))
                                    .toDays()
                            // Only worth a reminder after a break
                            if (days < MIN_DAYS_AWAY) {
                                null
                            } else {
                                val season = last.parentIndexNumber
                                val episode = last.indexNumber
                                val number = if (season != null && episode != null) "S$season:E$episode  ·  " else ""
                                PreviouslyOnInfo("$number${last.name.orEmpty()}", overview, days)
                            }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Previously on failed")
                        null
                    }
            }
        }
    }

/**
 * "Previously on": when you come back to a show after a few days away, a short reminder
 * of the last episode you watched
 */
@Composable
fun PreviouslyOn(
    seriesId: UUID,
    modifier: Modifier = Modifier,
    viewModel: PreviouslyOnViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val info by viewModel.info.collectAsState()
    val shown = info ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .widthIn(max = 720.dp)
                .background(Color.White.copy(alpha = .07f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (shown.daysAgo >= 2) "PREVIOUSLY ON  ·  ${awayText(shown.daysAgo)}" else "PREVIOUSLY ON",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            color = Color(0xFFE5303A),
        )
        Text(text = shown.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(
            text = shown.overview,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = .8f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun awayText(days: Long): String =
    when {
        days >= 60 -> "YOU WATCHED THIS ${days / 30} MONTHS AGO"
        days >= 14 -> "YOU WATCHED THIS ${days / 7} WEEKS AGO"
        else -> "YOU WATCHED THIS $days DAYS AGO"
    }

private const val MIN_DAYS_AWAY = 3L
