package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.detail.discover.str
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
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
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** Something an actor or director made that isn't on Benflix yet */
data class MissingCredit(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val isTv: Boolean,
    val role: String?,
    val popularity: Double,
)

/**
 * Benflix "Ask for everything by someone": on an actor or director page, everything of theirs
 * that is not on Benflix (already out, not already asked for). Tick the ones you want and they
 * are all asked for at once.
 */
@HiltViewModel
class AskForTheRestViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
    ) : ViewModel() {
        private val _missing = MutableStateFlow<List<MissingCredit>>(emptyList())
        val missing: StateFlow<List<MissingCredit>> = _missing
        private val _sending = MutableStateFlow(false)
        val sending: StateFlow<Boolean> = _sending
        private var loadedFor: Int? = null

        fun load(tmdbPersonId: Int) {
            if (loadedFor == tmdbPersonId) return
            loadedFor = tmdbPersonId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    if (KidsMode.isActive || !seerrService.active.first()) return@launch
                    val json =
                        seerrService.api.getRaw("/person/$tmdbPersonId/combined_credits")?.let { Json.parseToJsonElement(it).jsonObject }
                            ?: return@launch
                    _missing.value = parseCredits(json, LocalDate.now())
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load what's missing for person %s", tmdbPersonId)
                }
            }
        }

        fun ask(picked: List<MissingCredit>) {
            if (picked.isEmpty() || _sending.value) return
            _sending.value = true
            viewModelScope.launch(WholphinDispatchers.IO) {
                var asked = 0
                picked.forEach { credit ->
                    try {
                        if (credit.isTv) {
                            val seasons =
                                seerrService.api
                                    .getRaw("/tv/${credit.tmdbId}")
                                    ?.let { Json.parseToJsonElement(it).jsonObject["seasons"] as? JsonArray }
                                    .orEmpty()
                                    .mapNotNull { (it as? JsonObject)?.get("seasonNumber").str()?.toIntOrNull() }
                                    .filter { it > 0 }
                            if (seasons.isEmpty()) return@forEach
                            seerrService.api.requestApi.requestPost(
                                RequestPostRequest(
                                    mediaId = credit.tmdbId,
                                    mediaType = RequestPostRequest.MediaType.TV,
                                    seasons = seasons,
                                    is4k = false,
                                    tags = emptyList(),
                                ),
                            )
                        } else {
                            seerrService.api.requestApi.requestPost(
                                RequestPostRequest(
                                    mediaId = credit.tmdbId,
                                    mediaType = RequestPostRequest.MediaType.MOVIE,
                                    is4k = false,
                                    tags = emptyList(),
                                ),
                            )
                        }
                        asked++
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not ask for %s", credit.title)
                    }
                }
                val sent = picked.map { it.tmdbId to it.isTv }.toSet()
                _missing.value = _missing.value.filter { (it.tmdbId to it.isTv) !in sent }
                _sending.value = false
                showToast(context, if (asked == 1) "Asked for 1 title" else "Asked for $asked titles")
            }
        }

        companion object {
            /** Talk shows, news and award shows aren't things to ask for */
            private val SKIP_GENRES = setOf(10763, 10767, 10764)
            private val SELF = Regex("^(self|himself|herself|themselves|narrator)\\b", RegexOption.IGNORE_CASE)
            private const val MAX = 60

            internal fun parseCredits(
                json: JsonObject,
                today: LocalDate,
            ): List<MissingCredit> {
                fun list(key: String) = (json[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                val all =
                    list("cast").map { it to it["character"].str() } +
                        list("crew")
                            .filter { it["job"].str() == "Director" }
                            .map { it to "Director" }
                return all
                    .mapNotNull { (credit, role) ->
                        val id = credit["id"].str()?.toIntOrNull() ?: return@mapNotNull null
                        val isTv = credit["mediaType"].str() == "tv"
                        val title = (if (isTv) credit["name"].str() else credit["title"].str()) ?: return@mapNotNull null
                        val date =
                            (if (isTv) credit["firstAirDate"].str() else credit["releaseDate"].str())
                                ?.take(10)
                                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                ?: return@mapNotNull null
                        if (date.isAfter(today)) return@mapNotNull null
                        val genres =
                            (credit["genreIds"] as? JsonArray).orEmpty().mapNotNull { it.str()?.toIntOrNull() }
                        if (genres.any { it in SKIP_GENRES }) return@mapNotNull null
                        if (role != null && SELF.containsMatchIn(role)) return@mapNotNull null
                        // Seerr status: 1 unknown, 2 asked for, 3 on its way, 4 some, 5 here
                        val status = (credit["mediaInfo"] as? JsonObject)?.get("status").str()?.toIntOrNull()
                        if (status != null && status > 1) return@mapNotNull null
                        MissingCredit(
                            tmdbId = id,
                            title = title,
                            year = date.year,
                            isTv = isTv,
                            role = role?.takeIf { it.isNotBlank() },
                            popularity = credit["popularity"].str()?.toDoubleOrNull() ?: 0.0,
                        )
                    }.distinctBy { it.tmdbId to it.isTv }
                    .sortedByDescending { it.popularity }
                    .take(MAX)
            }
        }
    }

/** The "Ask for the rest" button for a person's page; nothing shows if everything is already here */
@Composable
fun AskForTheRest(
    person: BaseItem,
    modifier: Modifier = Modifier,
    viewModel: AskForTheRestViewModel = hiltViewModel(key = "ask-rest-${person.id}"),
) {
    val tmdb =
        remember(person) {
            person.data.providerIds
                ?.get("Tmdb")
                ?.toIntOrNull()
        } ?: return
    LaunchedEffect(tmdb) { viewModel.load(tmdb) }
    val missing by viewModel.missing.collectAsState()
    val sending by viewModel.sending.collectAsState()
    var open by remember { mutableStateOf(false) }
    if (missing.isEmpty()) return
    Surface(
        onClick = { open = true },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Soft,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = "Ask for the rest (${missing.size})",
            fontSize = BenflixType.small,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
    if (open) {
        AskForTheRestDialog(
            name = person.name ?: "them",
            missing = missing,
            sending = sending,
            onAsk = {
                viewModel.ask(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun AskForTheRestDialog(
    name: String,
    missing: List<MissingCredit>,
    sending: Boolean,
    onAsk: (List<MissingCredit>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<MissingCredit>()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .width(560.dp)
                    .background(BenflixColors.Panel, RoundedCornerShape(16.dp))
                    .padding(24.dp),
        ) {
            Text(
                text = "Not on Benflix yet",
                color = BenflixColors.Text,
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Everything with $name that Benflix doesn't have. Tick the ones you want.",
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onAsk(picked.toList()) },
                    enabled = picked.isNotEmpty() && !sending,
                ) {
                    Text(
                        when (picked.size) {
                            0 -> "Tick some first"
                            1 -> "Ask for 1"
                            else -> "Ask for ${picked.size}"
                        },
                    )
                }
                Button(
                    onClick = { picked = if (picked.size == missing.size) emptySet() else missing.toSet() },
                ) {
                    Text(if (picked.size == missing.size) "Untick all" else "Tick all")
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 380.dp),
            ) {
                items(missing, key = { "${it.isTv}-${it.tmdbId}" }) { credit ->
                    val ticked = credit in picked
                    Surface(
                        onClick = { picked = if (ticked) picked - credit else picked + credit },
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = BenflixColors.PanelHigh,
                                focusedContainerColor = BenflixColors.FocusRow,
                            ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (credit == missing.first()) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(22.dp)
                                        .background(
                                            if (ticked) BenflixColors.Text else BenflixColors.Line,
                                            RoundedCornerShape(6.dp),
                                        ),
                            ) {
                                if (ticked) Text("✓", color = BenflixColors.Tray, fontWeight = FontWeight.Black)
                            }
                            Column(
                                Modifier
                                    .padding(start = 14.dp)
                                    .weight(1f),
                            ) {
                                Text(
                                    text = credit.title + (credit.year?.let { " ($it)" } ?: ""),
                                    color = BenflixColors.Text,
                                    fontSize = BenflixType.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOfNotNull(if (credit.isTv) "Show" else "Movie", credit.role).joinToString(" · "),
                                    color = BenflixColors.Muted,
                                    fontSize = BenflixType.tag,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
