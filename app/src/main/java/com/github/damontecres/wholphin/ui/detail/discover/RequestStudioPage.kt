package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.BenflixLibraryTmdb
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.LoadingShape
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** What one studio (movies) or network (shows) made that isn't on Glowfin yet; null [items] = still loading */
data class StudioWall(
    val items: List<DiscoverItem>? = null,
    val owned: Int = 0,
)

/**
 * Glowfin: everything one studio or network made that you can ask for, opened from "See everything from"
 * in the Made by box on a request page. Things already on Glowfin are left out and counted.
 */
@HiltViewModel
class RequestStudioViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
        private val seerrService: SeerrService,
        private val library: BenflixLibraryTmdb,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        private val _wall = MutableStateFlow(StudioWall())
        val wall: StateFlow<StudioWall> = _wall
        private var loadedFor: String? = null

        fun load(
            id: Int,
            network: Boolean,
        ) {
            val key = "$network-$id"
            if (loadedFor == key) return
            loadedFor = key
            viewModelScope.launch(WholphinDispatchers.IO) {
                backdropService.clearBackdrop()
                val path = if (network) "/discover/tv/network/$id" else "/discover/movies/studio/$id"
                val type = if (network) SeerrItemType.TV else SeerrItemType.MOVIE
                val found = LinkedHashMap<Int, DiscoverItem>()
                var pages = 1
                var page = 1
                while (page <= pages && page <= MAX_PAGES) {
                    val json = page("$path?page=$page") ?: break
                    pages = json["totalPages"].str()?.toIntOrNull() ?: pages
                    val before = found.size
                    (json["results"] as? JsonArray)?.forEach { el ->
                        (el as? JsonObject)?.let { item(it, type) }?.let { found.putIfAbsent(it.id, it) }
                    }
                    // The same page twice means the page number was ignored: stop asking
                    if (found.size == before) break
                    page++
                }
                val all = found.values.toList()
                val have = runCatching { library.keys() }.getOrDefault(emptyMap())
                val prefix = if (network) "TV-" else "MOVIE-"
                val missing =
                    all.filter {
                        it.availability != SeerrAvailability.AVAILABLE && it.jellyfinItemId == null && "$prefix${it.id}" !in have
                    }
                _wall.value = StudioWall(items = missing, owned = all.size - missing.size)
            }
        }

        private suspend fun page(path: String): JsonObject? =
            try {
                seerrService.api.getRaw(path)?.let { Json.parseToJsonElement(it).jsonObject }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read %s", path)
                null
            }

        private suspend fun item(
            o: JsonObject,
            type: SeerrItemType,
        ): DiscoverItem? {
            val id = o["id"].str()?.toIntOrNull() ?: return null
            val poster = o["posterPath"].str() ?: return null
            val mediaInfo = o["mediaInfo"] as? JsonObject
            val date = o["releaseDate"].str() ?: o["firstAirDate"].str()
            return DiscoverItem(
                id = id,
                type = type,
                title = o["title"].str() ?: o["name"].str(),
                subtitle = null,
                overview = o["overview"].str(),
                availability = SeerrAvailability.from(mediaInfo?.get("status").str()?.toIntOrNull()) ?: SeerrAvailability.UNKNOWN,
                releaseDate = date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
                posterUrl = seerrService.createImageUrl(ImageType.PRIMARY, poster, null),
                backDropUrl = seerrService.createImageUrl(ImageType.BACKDROP, o["backdropPath"].str(), null),
                logoUrl = null,
                jellyfinItemId = null,
                score = o["voteAverage"].str()?.toDoubleOrNull()?.takeIf { it > 0 },
            )
        }

        companion object {
            private const val MAX_PAGES = 5
        }
    }

@Composable
fun RequestStudioPage(
    destination: Destination.RequestStudio,
    modifier: Modifier = Modifier,
    viewModel: RequestStudioViewModel = hiltViewModel(key = "studio-${destination.network}-${destination.id}"),
) {
    LaunchedEffect(destination) { viewModel.load(destination.id, destination.network) }
    val wall by viewModel.wall.collectAsState()
    val items = wall.items
    if (items == null) {
        LoadingPage(modifier.focusable(), shape = LoadingShape.WALL)
        return
    }
    val owner = StudioOwners.of(destination.name)
    val note =
        listOfNotNull(
            owner?.takeIf { !it.owner.equals(destination.name, ignoreCase = true) }?.let { "Part of ${it.owner}" },
            if (wall.owned > 0) "${wall.owned} already on Glowfin (hidden)" else null,
        ).joinToString(" · ")
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) focusRequester.tryRequestFocus() }
    Column(modifier = modifier) {
        Text(
            text = destination.name,
            color = BenflixColors.Text,
            fontSize = BenflixType.pageTitle,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (note.isNotEmpty()) {
            Text(
                text = note,
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (items.isEmpty()) {
            Text(
                text = if (wall.owned > 0) "You already have everything from here." else "Nothing to show from here right now.",
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().padding(top = 40.dp).focusable(),
            )
        } else {
            CardGrid(
                pager = items,
                onClickItem = { _: Int, item: DiscoverItem ->
                    viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item))
                },
                onLongClickItem = { _: Int, _: DiscoverItem -> },
                onClickPlay = { _, _ -> },
                initialPosition = 0,
                letterPosition = { _: Char -> 0 },
                gridFocusRequester = focusRequester,
                showJumpButtons = false,
                showLetterButtons = false,
                spacing = 16.dp,
                cardContent = @Composable { (item, _, onClick, onLongClick, _, mod) ->
                    DiscoverItemCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        showOverlay = true,
                        modifier = mod,
                    )
                },
                columns = 6,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
