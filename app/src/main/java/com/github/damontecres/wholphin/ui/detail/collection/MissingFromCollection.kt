package com.github.damontecres.wholphin.ui.detail.collection

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import androidx.tv.material3.ButtonDefaults
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** A movie in the collection that isn't on Benflix yet */
data class MissingPart(
    val tmdbId: Int,
    val title: String,
)

/**
 * Benflix: on a collection page, the movies of that collection that aren't on Benflix yet
 * (already out, not already asked for), with one button to ask for them all.
 */
@HiltViewModel
class MissingFromCollectionViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val seerrService: SeerrService,
    ) : ViewModel() {
        private val _missing = MutableStateFlow<List<MissingPart>>(emptyList())
        val missing: StateFlow<List<MissingPart>> = _missing
        private var loadedFor: UUID? = null

        fun load(collectionId: UUID) {
            if (loadedFor == collectionId) return
            loadedFor = collectionId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    if (KidsMode.isActive || !seerrService.active.first()) return@launch
                    val set = api.userLibraryApi.getItem(collectionId, serverRepository.currentUser?.id).content
                    val tmdb = set.providerIds?.get("Tmdb") ?: set.providerIds?.get("TmdbCollection") ?: return@launch
                    val json =
                        seerrService.api.getRaw("/collection/$tmdb")?.let { Json.parseToJsonElement(it).jsonObject }
                            ?: return@launch
                    val today = LocalDate.now()
                    _missing.value =
                        (json["parts"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull { it as? JsonObject }
                            .mapNotNull { part ->
                                val id = part["id"].str()?.toIntOrNull() ?: return@mapNotNull null
                                val title = part["title"].str() ?: part["name"].str() ?: return@mapNotNull null
                                val status = (part["mediaInfo"] as? JsonObject)?.get("status").str()?.toIntOrNull()
                                val released =
                                    part["releaseDate"]
                                        .str()
                                        ?.take(10)
                                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                        ?.let { !it.isAfter(today) } ?: false
                                // Seerr status: 1 unknown, 2 asked for, 3 on its way, 4 some, 5 here
                                if (released && (status == null || status <= 1)) MissingPart(id, title) else null
                            }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not check what's missing from collection %s", collectionId)
                }
            }
        }

        fun askForAll() {
            val parts = _missing.value
            if (parts.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                var asked = 0
                parts.forEach { part ->
                    try {
                        seerrService.api.requestApi.requestPost(
                            RequestPostRequest(
                                mediaId = part.tmdbId,
                                mediaType = RequestPostRequest.MediaType.MOVIE,
                                is4k = false,
                                tags = emptyList(),
                            ),
                        )
                        asked++
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not ask for %s", part.title)
                    }
                }
                _missing.value = emptyList()
                showToast(context, if (asked == 1) "Asked for 1 movie" else "Asked for $asked movies")
            }
        }
    }

@Composable
fun MissingFromCollection(
    collectionId: UUID,
    modifier: Modifier = Modifier,
    viewModel: MissingFromCollectionViewModel = hiltViewModel(key = "missing-$collectionId"),
) {
    LaunchedEffect(collectionId) { viewModel.load(collectionId) }
    val missing by viewModel.missing.collectAsState()
    if (missing.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Button(onClick = viewModel::askForAll, scale = ButtonDefaults.scale(focusedScale = 1f)) {
            Text(if (missing.size == 1) "Ask for the missing movie" else "Ask for the ${missing.size} missing movies")
        }
        Text(
            text = "Not on Benflix yet: " + missing.joinToString(", ") { it.title },
            fontSize = BenflixType.small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
