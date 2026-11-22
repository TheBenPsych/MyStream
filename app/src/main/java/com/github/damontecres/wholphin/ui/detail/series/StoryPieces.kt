package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NextEpisode
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** One movie or special of an anime show, as Tower sees it (TheTVDB, AniList, Anime Filler List) */
data class StoryPiece(
    val movie: Boolean,
    val title: String,
    val ep: Int?,
    val episodeId: Int?,
    val tmdb: Long?,
    /** canon, mixed, filler, recap, extra or unsure */
    val label: String,
    val labelWords: String,
    val have: Boolean,
    val coming: Boolean,
    val where: String,
    /** Canon, mixed and not sure show; filler, recaps and extras hide */
    val shown: Boolean,
)

/** Missing pieces for one anime show (Ben 2026-11-22) */
data class StoryInfo(
    val tvdb: Long?,
    val complete: Boolean,
    val missing: List<StoryPiece>,
    val hidden: List<StoryPiece>,
    val canGet: Boolean,
)

internal fun parseStory(j: JSONObject): StoryInfo? {
    if (!j.optBoolean("anime")) return null
    val list = j.optJSONArray("pieces") ?: return null
    val pieces =
        (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            StoryPiece(
                movie = o.optString("kind") == "movie",
                title = o.optString("title"),
                ep = if (o.isNull("ep")) null else o.optInt("ep"),
                episodeId = if (o.isNull("ep_id")) null else o.optInt("ep_id"),
                tmdb = if (o.isNull("tmdb")) null else o.optLong("tmdb"),
                label = o.optString("label"),
                labelWords = o.optString("label_words"),
                have = o.optBoolean("have"),
                coming = o.optBoolean("coming"),
                where = o.optString("where"),
                shown = o.optBoolean("shown"),
            )
        }
    if (pieces.isEmpty()) return null
    return StoryInfo(
        tvdb = if (j.isNull("tvdb")) null else j.optLong("tvdb"),
        complete = j.optBoolean("complete"),
        missing = pieces.filter { it.shown && !it.have },
        hidden = pieces.filter { !it.shown && !it.have },
        canGet = j.optBoolean("can_get") && !KidsMode.isActive,
    )
}

/** "Movie · Canon · after Season 1 · on its way" */
internal fun StoryPiece.note(): String =
    listOfNotNull(
        if (movie) "Movie" else "Special",
        labelWords.takeIf { it.isNotBlank() },
        where.takeIf { it.isNotBlank() },
        if (coming) "On its way" else null,
    ).joinToString(" · ")

/**
 * Glowfin "Movies and specials" on an anime show's page (Ben 2026-11-22): Missing pieces with Get, the
 * whole show in story order (Watch order), and "Watch Mugen Train first?" before a season that follows a movie.
 */
@HiltViewModel
class StoryViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        private val _info = MutableStateFlow<StoryInfo?>(null)
        val info: StateFlow<StoryInfo?> = _info
        private var loadedFor: UUID? = null
        private var episodes: Pair<UUID, List<BaseItemDto>>? = null

        private fun prefs() = context.getSharedPreferences("glowfin_story", Context.MODE_PRIVATE)

        fun load(series: BaseItem) {
            if (loadedFor == series.id) return
            loadedFor = series.id
            val ids = series.data.providerIds.orEmpty()
            val tvdb = ids["Tvdb"]?.filter { it.isDigit() }.orEmpty()
            val tmdb = ids["Tmdb"]?.filter { it.isDigit() }.orEmpty()
            if (tvdb.isEmpty() && tmdb.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val q = if (tvdb.isNotEmpty()) "tvdb=$tvdb" else "tmdb=$tmdb"
                    _info.value = parseStory(helper.getJson("/api/specials/show?$q"))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // Older Tower, or not anime: the row just doesn't show
                    Timber.d(ex, "Missing pieces for %s", series.name)
                }
            }
        }

        fun get(
            piece: StoryPiece?,
            all: Boolean = false,
        ) {
            val tvdb = _info.value?.tvdb
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val body =
                        JSONObject().apply {
                            if (all && tvdb != null) {
                                put("all", true)
                                put("tvdb", tvdb)
                            }
                            piece?.tmdb?.let { put("tmdb", it) }
                            if (piece?.tmdb == null) piece?.episodeId?.let { put("episode_id", it) }
                        }
                    val said = helper.postJson("/api/specials/get", body).optString("message", "Asked Tower for it.")
                    showToast(context, said)
                    loadedFor = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    showToast(context, "Tower couldn't get it right now. Try again in a minute.")
                }
            }
        }

        /** Every episode and special of the show, in story order (kept while the page is open) */
        suspend fun storyOrder(seriesId: UUID): List<BaseItemDto> {
            episodes?.takeIf { it.first == seriesId }?.let { return NextEpisode.storyOrder(it.second) }
            val list =
                api.tvShowsApi
                    .getEpisodes(
                        GetEpisodesRequest(
                            seriesId = seriesId,
                            userId = serverRepository.currentUser?.id,
                            isMissing = false,
                            fields = listOf(ItemFields.SPECIAL_EPISODE_NUMBERS),
                            enableImages = false,
                            enableUserData = true,
                        ),
                    ).content.items
            episodes = seriesId to list
            return NextEpisode.storyOrder(list)
        }

        /** "Watch Mugen Train first?" once per movie, when it comes right before [episodeId] and isn't watched */
        suspend fun movieFirst(
            seriesId: UUID,
            episodeId: UUID,
        ): BaseItemDto? =
            try {
                storyOrder(seriesId)
                val movie = episodes?.second?.let { NextEpisode.movieBefore(it, episodeId) }
                movie?.takeIf { !prefs().getBoolean("asked-${it.id}", false) }?.also { m ->
                    prefs().edit { putBoolean("asked-${m.id}", true) }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                null
            }

        fun forget() {
            episodes = null
        }
    }

/** The row of buttons under Episodes on an anime show's page */
@Composable
fun StoryRow(
    series: BaseItem,
    side: androidx.compose.ui.unit.Dp,
    onPlay: (BaseItemDto) -> Unit,
    viewModel: StoryViewModel = hiltViewModel(key = "story-${series.id}"),
) {
    LaunchedEffect(series.id) { viewModel.load(series) }
    val info by viewModel.info.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var showHidden by remember { mutableStateOf(false) }
    val story = info
    LaunchedEffect(dialog) {
        if (dialog == "order") {
            order =
                try {
                    kotlinx.coroutines.withContext(WholphinDispatchers.IO) { viewModel.storyOrder(series.id) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    emptyList()
                }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = side, end = side, top = 12.dp)) {
        if (story != null) {
            Text(
                text =
                    when {
                        story.complete && story.missing.isEmpty() -> "Story complete: you have every canon and mixed movie and special"
                        story.missing.isEmpty() -> "Nothing important is missing"
                        story.missing.size == 1 -> "1 movie or special that helps complete the story isn't here yet"
                        else -> "${story.missing.size} movies or specials that help complete the story aren't here yet"
                    },
                color = if (story.complete) BenflixColors.Text else BenflixColors.Soft,
                fontSize = BenflixType.small,
                fontWeight = if (story.complete) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { dialog = "order" }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Watch order") }
            if (story != null && (story.missing.isNotEmpty() || story.hidden.isNotEmpty())) {
                Button(onClick = { dialog = "missing" }, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                    Text(if (story.missing.isEmpty()) "Missing pieces" else "Missing pieces (${story.missing.size})")
                }
            }
            if (story != null && story.canGet && story.missing.any { it.label == "canon" || it.label == "mixed" }) {
                Button(onClick = { viewModel.get(null, all = true) }, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                    Text("Get all canon + mixed")
                }
            }
        }
    }
    when (dialog) {
        "order" -> {
            BenflixChoiceDialog(
                title = "Watch order",
                subtitle = if (order.isEmpty()) "Loading…" else "Seasons, movies and specials in story order",
                choices =
                    order.map { ep ->
                        val special = ep.parentIndexNumber == 0
                        BenflixChoice(
                            label =
                                if (special) {
                                    (if (NextEpisode.isMovieSpecial(ep)) "Movie · " else "Special · ") + (ep.name ?: "")
                                } else {
                                    "S${ep.parentIndexNumber} E${ep.indexNumber} · ${ep.name ?: ""}"
                                },
                            icon = if (special) BenflixIcon.STAR else null,
                            selected = ep.userData?.played == true,
                            onClick = { onPlay(ep) },
                        )
                    },
                onDismissRequest = { dialog = null },
            )
        }

        "missing" -> {
            val s = story ?: return
            val list = if (showHidden) s.missing + s.hidden else s.missing
            BenflixChoiceDialog(
                title = "Missing pieces",
                subtitle = "Canon and mixed help complete the story. Filler, recaps and extras are hidden.",
                choices =
                    list.map { p ->
                        BenflixChoice(
                            label = p.title,
                            note = p.note(),
                            icon = if (s.canGet && !p.coming) BenflixIcon.PLUS else null,
                            onClick = { if (s.canGet && !p.coming) viewModel.get(p) },
                        )
                    } +
                        BenflixChoice(
                            label =
                                if (showHidden) "Hide filler, recaps and extras" else "Show filler, recaps and extras (${s.hidden.size})",
                            onClick = {
                                showHidden = !showHidden
                                dialog = "missing"
                            },
                        ),
                onDismissRequest = { dialog = null },
            )
        }
    }
}

/**
 * Glowfin Requests (Ben 2026-11-22): after asking for an anime show, offer its canon and mixed movies with one press
 */
@HiltViewModel
class AnimeMoviesAskViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        private val _offer = MutableStateFlow<List<StoryPiece>>(emptyList())
        val offer: StateFlow<List<StoryPiece>> = _offer

        fun check(tmdbTv: Int) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val info = parseStory(helper.getJson("/api/specials/show?tmdb=$tmdbTv")) ?: return@launch
                    if (!info.canGet) return@launch
                    _offer.value =
                        info.missing.filter { p ->
                            p.movie && p.tmdb != null && !p.coming && (p.label == "canon" || p.label == "mixed")
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "No movie offer for %s", tmdbTv)
                }
            }
        }

        fun getAll() {
            val list = _offer.value
            _offer.value = emptyList()
            viewModelScope.launch(WholphinDispatchers.IO) {
                var n = 0
                list.forEach { p ->
                    try {
                        helper.postJson("/api/specials/get", JSONObject().put("tmdb", p.tmdb))
                        n++
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Couldn't get %s", p.title)
                    }
                }
                showToast(
                    context,
                    if (n == 1) {
                        "Getting 1 movie. It goes into the show by itself."
                    } else {
                        "Getting $n movies. They go into the show by themselves."
                    },
                )
            }
        }

        fun dismiss() {
            _offer.value = emptyList()
        }
    }

@Composable
fun AnimeMoviesAsk(
    showName: String,
    viewModel: AnimeMoviesAskViewModel,
) {
    val offer by viewModel.offer.collectAsState()
    if (offer.isEmpty()) return
    BenflixChoiceDialog(
        title = "Also get its movies?",
        subtitle =
            "$showName has ${offer.size} canon or mixed movie${if (offer.size == 1) "" else "s"}: " +
                offer.joinToString(", ") { it.title },
        choices =
            listOf(
                BenflixChoice(
                    label = if (offer.size == 1) "Yes, get the movie too" else "Yes, get all ${offer.size} movies",
                    icon = BenflixIcon.PLUS,
                    note = "They go into the show's Specials, in story order",
                    onClick = viewModel::getAll,
                ),
                BenflixChoice(label = "No thanks", onClick = viewModel::dismiss),
            ),
        onDismissRequest = viewModel::dismiss,
    )
}
