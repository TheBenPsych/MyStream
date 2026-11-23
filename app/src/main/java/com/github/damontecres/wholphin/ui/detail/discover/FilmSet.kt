package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.services.BenflixLibraryTmdb
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** Where one movie of a film series stands */
enum class PartState { HERE, ASKED, NOT_OUT, NOT_HERE }

data class FilmPart(
    val tmdbId: Int,
    val title: String,
    val year: String?,
    val state: PartState,
)

/** A film series ("The Dark Knight Collection") with its movies in release order */
data class FilmSet(
    val name: String,
    val parts: List<FilmPart>,
) {
    /** Movies that are out, not on Glowfin and not asked for yet */
    val missing: List<FilmPart> get() = parts.filter { it.state == PartState.NOT_HERE }

    companion object {
        /**
         * Reads Seerr's /collection/{id}. [owned] says if a TMDB movie id is really on Glowfin
         * (Seerr's own "available" often runs behind).
         */
        fun from(
            json: JsonObject,
            owned: (Int) -> Boolean,
            today: LocalDate = LocalDate.now(),
        ): FilmSet? {
            val name = json["name"].str()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parts =
                (json["parts"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull { part ->
                        val id = part["id"].str()?.toIntOrNull() ?: return@mapNotNull null
                        val title = part["title"].str() ?: part["name"].str() ?: return@mapNotNull null
                        val date = part["releaseDate"].str()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        // Seerr status: 1 unknown, 2 asked for, 3 on its way, 4 some, 5 here
                        val status = (part["mediaInfo"] as? JsonObject)?.get("status").str()?.toIntOrNull() ?: 1
                        val state =
                            when {
                                status >= 4 || owned(id) -> PartState.HERE
                                status in 2..3 -> PartState.ASKED
                                date == null || date.isAfter(today) -> PartState.NOT_OUT
                                else -> PartState.NOT_HERE
                            }
                        Triple(date, id, FilmPart(id, title, date?.year?.toString(), state))
                    }.sortedWith(compareBy({ it.first ?: LocalDate.MAX }, { it.second }))
                    .map { it.third }
            return if (parts.size >= 2) FilmSet(name, parts) else null
        }
    }
}

@HiltViewModel
class FilmSetViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        private val library: BenflixLibraryTmdb,
    ) : ViewModel() {
        private val _set = MutableStateFlow<FilmSet?>(null)
        val set: StateFlow<FilmSet?> = _set
        private var loadedFor: Int? = null

        fun load(collectionId: Int) {
            if (loadedFor == collectionId) return
            loadedFor = collectionId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val json =
                        seerrService.api.getRaw("/collection/$collectionId")?.let { Json.parseToJsonElement(it).jsonObject }
                            ?: return@launch
                    val owned = runCatching { library.keys() }.getOrDefault(emptyMap())
                    _set.value = FilmSet.from(json, owned = { owned.containsKey("MOVIE-$it") })
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load film series %s", collectionId)
                }
            }
        }

        fun askForMissing() {
            val set = _set.value ?: return
            val missing = set.missing
            if (missing.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                val asked = mutableSetOf<Int>()
                missing.forEach { part ->
                    try {
                        seerrService.api.requestApi.requestPost(
                            RequestPostRequest(
                                mediaId = part.tmdbId,
                                mediaType = RequestPostRequest.MediaType.MOVIE,
                                is4k = false,
                                tags = emptyList(),
                            ),
                        )
                        asked += part.tmdbId
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not ask for %s", part.title)
                    }
                }
                _set.value = set.copy(parts = set.parts.map { if (it.tmdbId in asked) it.copy(state = PartState.ASKED) else it })
                showToast(
                    context,
                    when (asked.size) {
                        0 -> "Couldn't ask for those. Try again in a minute."
                        1 -> "Asked for 1 movie"
                        else -> "Asked for ${asked.size} movies"
                    },
                )
            }
        }
    }

/**
 * Glowfin (Ben, 2026-11-21): "Part 2 of 4 · The Monsoon Collection" on the page of a movie you could ask
 * for, with every part and where it stands, and one button to ask for all the ones that aren't here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilmSetBlock(
    collectionId: Int,
    thisTmdbId: Int,
    canRequest: Boolean,
    modifier: Modifier = Modifier,
    viewModel: FilmSetViewModel = hiltViewModel(key = "film-set-$collectionId"),
) {
    LaunchedEffect(collectionId) { viewModel.load(collectionId) }
    val set by viewModel.set.collectAsState()
    val kids by KidsMode.active.collectAsState()
    val s = set ?: return
    val at = s.parts.indexOfFirst { it.tmdbId == thisTmdbId }
    val missing = s.missing
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = (if (at >= 0) "Part ${at + 1} of ${s.parts.size} · " else "${s.parts.size} movies · ") + s.name,
            color = BenflixColors.Text,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.parts.take(12).forEachIndexed { i, part ->
                val mine = part.tmdbId == thisTmdbId
                val note =
                    when {
                        mine -> "this one"
                        part.state == PartState.HERE -> "on Glowfin"
                        part.state == PartState.ASKED -> "asked for"
                        part.state == PartState.NOT_OUT -> "not out yet"
                        else -> "not here"
                    }
                Text(
                    text = "${i + 1}. ${part.title} · $note",
                    color = if (mine || part.state == PartState.HERE) BenflixColors.Text else BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    fontWeight = if (mine) FontWeight.ExtraBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .background(Color.White.copy(alpha = if (part.state == PartState.HERE) .14f else .06f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (missing.isNotEmpty() && canRequest && !kids) {
            Button(onClick = viewModel::askForMissing, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text(if (missing.size == 1) "Ask for the 1 you don't have" else "Ask for the ${missing.size} you don't have")
            }
        }
    }
}
