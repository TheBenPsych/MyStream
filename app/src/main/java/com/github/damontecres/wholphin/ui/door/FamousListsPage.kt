package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ImageType
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** What the Famous lists tab shows */
data class FamousState(
    val rows: List<DoorRowData> = emptyList(),
    val loaded: Boolean = false,
    /** Tower is still gathering the lists the first time */
    val gathering: Boolean = false,
    val offline: Boolean = false,
)

/**
 * Glowfin Famous lists (Idea Box 28, Ben 2026-11-24): IMDb Top 250, Best Picture winners, Palme d'Or (Movies),
 * IMDb Top 250 TV and the Emmy winners (Shows). Tower gathers them once a week (benflix_famous.py).
 * Titles already here open as usual; the rest say "Ask for it" and open their Ask page.
 */
@HiltViewModel
class FamousListsViewModel
    @Inject
    constructor(
        private val helper: GameLibraryClient,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(FamousState())
        val state: StateFlow<FamousState> = _state
        private var started: Boolean? = null

        fun start(shows: Boolean) {
            if (started == shows) return
            started = shows
            viewModelScope.launch {
                // The very first time Tower gathers the lists, it takes a few minutes: look again now and then
                repeat(20) { attempt ->
                    val got = load(shows)
                    _state.value = got
                    if (got.rows.isNotEmpty() || got.offline) return@launch
                    delay(if (attempt < 3) 10_000L else 30_000L)
                }
            }
        }

        private suspend fun load(shows: Boolean): FamousState =
            try {
                val json = helper.getJson("/api/famous/${if (shows) "shows" else "movies"}")
                val rows = json.optJSONArray("rows")
                val out = mutableListOf<DoorRowData>()
                for (i in 0 until (rows?.length() ?: 0)) {
                    rows!!.optJSONObject(i)?.let { row(it) }?.let { out.add(it) }
                }
                FamousState(rows = out, loaded = true, gathering = out.isEmpty())
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.i("Famous lists not available: %s", ex.message)
                FamousState(loaded = true, offline = true)
            }

        private suspend fun row(json: JSONObject): DoorRowData? {
            val list = json.optJSONArray("items") ?: return null
            val items = mutableListOf<DiscoverItem>()
            for (i in 0 until list.length()) {
                val it = list.optJSONObject(i) ?: continue
                val jf = it.optString("jellyfinId").takeIf { s -> s.length >= 32 }?.let { s -> parseId(s) }
                val type = if (it.optString("mediaType") == "tv") SeerrItemType.TV else SeerrItemType.MOVIE
                val year = it.optString("year").toIntOrNull()
                items.add(
                    DiscoverItem(
                        id = it.optInt("id"),
                        type = type,
                        title = it.optString("title").ifBlank { null },
                        subtitle = null,
                        overview = null,
                        availability = if (jf != null) SeerrAvailability.AVAILABLE else SeerrAvailability.UNKNOWN,
                        releaseDate = year?.let { y -> java.time.LocalDate.of(y, 1, 1) },
                        posterUrl = seerrService.createImageUrl(ImageType.PRIMARY, it.optString("posterPath").ifBlank { null }, null),
                        backDropUrl = null,
                        logoUrl = null,
                        jellyfinItemId = jf,
                    ),
                )
            }
            if (items.isEmpty()) return null
            val have = json.optInt("have")
            val total = json.optInt("total", items.size)
            return DoorRowData(
                key = json.optString("key"),
                title = json.optString("title"),
                items = emptyList(),
                style = DoorRowStyle.DISCOVER,
                discover = items,
                note = "You have $have of $total",
                askNote = true,
            )
        }

        private fun parseId(text: String): UUID? =
            try {
                val hex = text.replace("-", "")
                UUID.fromString(
                    "${hex.substring(
                        0,
                        8,
                    )}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}",
                )
            } catch (_: Exception) {
                null
            }
    }

/** The Famous lists tab: one row per list, the same rows (and row names) as every other door row */
@Composable
internal fun FamousListsPage(
    shows: Boolean,
    modifier: Modifier,
    viewModel: FamousListsViewModel = hiltViewModel(key = "famous-$shows"),
) {
    LaunchedEffect(shows) { viewModel.start(shows) }
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    val first = remember { FocusRequester() }
    val firstKey = state.rows.firstOrNull()?.key
    LaunchedEffect(firstKey) {
        if (isTv && firstKey != null) {
            delay(120)
            first.tryRequestFocus()
        }
    }
    if (state.rows.isEmpty()) {
        Box(modifier.fillMaxSize().padding(top = 24.dp)) {
            Text(
                text =
                    when {
                        !state.loaded -> "Loading the famous lists..."
                        state.offline -> "Couldn't reach Tower for the famous lists. Try again in a minute."
                        else -> "Tower is gathering the famous lists. This takes a few minutes the first time."
                    },
                color = BenflixColors.Muted,
                fontSize = BenflixType.body,
            )
        }
        return
    }
    val posterWidth = if (isTv) 120.dp else 104.dp
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 18.dp),
    ) {
        items(state.rows, key = { it.key }) { row ->
            DoorRow(
                row = row,
                posterWidth = posterWidth,
                showName = !isTv,
                startIndex = 0,
                focusRequester = if (row.key == firstKey) first else null,
                focusIndex = 0,
                onClick = {},
                onSeeAll = {},
                onFocus = { _, _ -> },
                side = 0.dp,
                onOpen = { viewModel.navigationManager.navigateTo(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
