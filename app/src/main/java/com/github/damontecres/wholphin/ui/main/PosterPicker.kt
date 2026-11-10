package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.BenflixPosters
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.PosterChoice
import com.github.damontecres.wholphin.services.PosterKind
import com.github.damontecres.wholphin.ui.discover.RequestsChip
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

private val SQUARE_PICTURES =
    setOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST, BaseItemKind.PLAYLIST, BaseItemKind.AUDIO_BOOK, BaseItemKind.AUDIO)
private val WIDE_PICTURES = setOf(BaseItemKind.EPISODE, BaseItemKind.TV_CHANNEL, BaseItemKind.LIVE_TV_CHANNEL, BaseItemKind.VIDEO)
private val COVERS = setOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.PLAYLIST, BaseItemKind.AUDIO_BOOK, BaseItemKind.BOOK, BaseItemKind.AUDIO)
private val PICTURES =
    setOf(BaseItemKind.MUSIC_ARTIST, BaseItemKind.PERSON, BaseItemKind.EPISODE, BaseItemKind.TV_CHANNEL, BaseItemKind.LIVE_TV_CHANNEL)

/** The tabs along the top of Change poster */
enum class PosterTab(
    val label: String,
) {
    POSTER("Poster"),
    SEASONS("Season posters"),
    BACKGROUND("Background"),
    LOGO("Title logo"),
    ;

    val kind: PosterKind
        get() =
            when (this) {
                BACKGROUND -> PosterKind.BACKGROUND
                LOGO -> PosterKind.LOGO
                else -> PosterKind.POSTER
            }
}

data class PosterPickerState(
    val item: BaseItemDto? = null,
    val failed: Boolean = false,
    val tab: PosterTab = PosterTab.POSTER,
    val seasons: List<BaseItemDto> = emptyList(),
    val seasonId: UUID? = null,
    /** null while looking */
    val choices: List<PosterChoice>? = null,
    /** null = every place */
    val source: String? = null,
    /** The one being tried on; null = the picture there now */
    val preview: PosterChoice? = null,
    /** The picture there now (a Jellyfin address), or null when there is none */
    val currentUrl: String? = null,
    val saving: Boolean = false,
) {
    val tabs: List<PosterTab>
        get() =
            when (item?.type) {
                BaseItemKind.SERIES -> PosterTab.entries

                BaseItemKind.SEASON, BaseItemKind.BOX_SET -> listOf(PosterTab.POSTER, PosterTab.BACKGROUND)

                BaseItemKind.MOVIE, null -> listOf(PosterTab.POSTER, PosterTab.BACKGROUND, PosterTab.LOGO)

                // Redesign 2 (rc-q-fix): every other kind (albums, artists, books, episodes, people, channels)
                // just has its one picture
                else -> listOf(PosterTab.POSTER)
            }

    /** The picture's shape: albums and audiobooks are square, episodes and channels wide, the rest tall */
    val pictureShape: Float
        get() =
            if (tab != PosterTab.POSTER) {
                tab.kind.shape
            } else {
                when (item?.type ?: BaseItemKind.MOVIE) {
                    in SQUARE_PICTURES -> 1f
                    in WIDE_PICTURES -> 16f / 9f
                    else -> tab.kind.shape
                }
            }

    /** What the picture is called: "poster", "cover", "picture" */
    val pictureWord: String
        get() =
            if (tab != PosterTab.POSTER) {
                tab.kind.label.lowercase()
            } else {
                when (item?.type ?: BaseItemKind.MOVIE) {
                    in COVERS -> "cover"
                    in PICTURES -> "picture"
                    else -> "poster"
                }
            }

    /** The movie, show, season or collection the picture goes on */
    val targetId: UUID? get() = if (tab == PosterTab.SEASONS) seasonId else item?.id

    val sources: List<String> get() = choices.orEmpty().map { it.provider }.distinct()

    val shown: List<PosterChoice> get() = choices.orEmpty().filter { source == null || it.provider == source }
}

/**
 * Benflix "Change poster" (main profile only): the picture there now sits big on the left. Moving along
 * the choices tries each one on; "Use this" keeps it. Pictures come from TMDB, TheTVDB and Fanart.tv
 * through Jellyfin (whichever plugins are on), English first.
 */
@HiltViewModel
class PosterPickerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val posters: BenflixPosters,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PosterPickerState())
        val state: StateFlow<PosterPickerState> = _state
        private var started: UUID? = null
        private var loading: Job? = null

        fun start(
            itemId: UUID,
            kind: String?,
        ) {
            if (started == itemId) return
            started = itemId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val item = posters.item(itemId)
                    val seasons = if (item.type == BaseItemKind.SERIES) posters.seasonsOf(item.id) else emptyList()
                    val tab =
                        when (kind) {
                            PosterKind.LOGO.name -> PosterTab.LOGO
                            PosterKind.BACKGROUND.name -> PosterTab.BACKGROUND
                            else -> PosterTab.POSTER
                        }
                    _state.value = PosterPickerState(item = item, seasons = seasons, seasonId = seasons.firstOrNull()?.id, tab = tab)
                    load()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Change poster")
                    _state.value = PosterPickerState(failed = true)
                }
            }
        }

        fun pickTab(tab: PosterTab) {
            if (tab == _state.value.tab) return
            _state.update { it.copy(tab = tab, choices = null, source = null, preview = null) }
            load()
        }

        fun pickSeason(id: UUID) {
            if (id == _state.value.seasonId) return
            _state.update { it.copy(seasonId = id, choices = null, source = null, preview = null) }
            load()
        }

        fun pickSource(source: String?) = _state.update { it.copy(source = source) }

        fun tryOn(choice: PosterChoice) = _state.update { it.copy(preview = choice) }

        private fun load() {
            loading?.cancel()
            loading =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    val s = _state.value
                    val target = s.targetId
                    if (target == null) {
                        _state.update { it.copy(choices = emptyList(), currentUrl = null) }
                        return@launch
                    }
                    val current = currentUrl(target, s.tab.kind)
                    val found =
                        try {
                            posters.choices(target, s.tab.kind)
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Change poster: looking online")
                            emptyList()
                        }
                    _state.update { it.copy(choices = found, currentUrl = current) }
                }
        }

        private suspend fun currentUrl(
            target: UUID,
            kind: PosterKind,
        ): String? =
            try {
                val dto = posters.item(target)
                val tag =
                    if (kind == PosterKind.BACKGROUND) {
                        dto.backdropImageTags?.firstOrNull()
                    } else {
                        dto.imageTags?.get(kind.type)
                    }
                tag?.let {
                    imageUrlService.getItemImageUrl(
                        itemId = target,
                        imageType = kind.type,
                        fillHeight = if (kind == PosterKind.POSTER) 900 else 540,
                        tag = it,
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                null
            }

        /** Puts the one being tried on onto the title */
        fun use() {
            val s = _state.value
            val choice = s.preview ?: return
            val target = s.targetId ?: return
            if (s.saving) return
            _state.update { it.copy(saving = true) }
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    posters.use(target, s.tab.kind, choice)
                    showToast(context, "New ${s.pictureWord} saved. It shows everywhere in about a minute.")
                    val current = currentUrl(target, s.tab.kind)
                    _state.update { it.copy(saving = false, currentUrl = current ?: choice.url, preview = null) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Change poster: saving")
                    showToast(context, "Couldn't save it. Try again in a moment.")
                    _state.update { it.copy(saving = false) }
                }
            }
        }
    }

@Composable
fun PosterPickerPage(
    itemId: UUID,
    kind: String?,
    modifier: Modifier = Modifier,
    viewModel: PosterPickerViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(itemId) { viewModel.start(itemId, kind) }
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    val useFocus = remember { FocusRequester() }
    val firstChip = remember { FocusRequester() }
    val edge = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val item = state.item
    if (state.failed) {
        Text("Couldn't open this title. Try again in a moment.", modifier = modifier.padding(edge))
        return
    }
    if (item == null) {
        com.github.damontecres.wholphin.ui.components
            .LoadingPage(modifier.fillMaxSize())
        return
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { firstChip.tryRequestFocus() }
    val kindShape = state.pictureShape
    val preview: @Composable (Modifier) -> Unit = { m ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = m) {
            val url = state.preview?.url ?: state.currentUrl
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(kindShape)
                        .clip(BenflixShapes.Card)
                        .background(Color(0xFF17181C)),
            ) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = if (state.tab.kind == PosterKind.LOGO) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("None yet", fontSize = BenflixType.small, color = Color(0xFF8D9098))
                }
            }
            Text(
                text = state.preview?.describe ?: if (state.currentUrl != null) "The one there now" else "There's none yet",
                fontSize = BenflixType.small,
                color = Color(0xFFA7A9B0),
                maxLines = 2,
            )
            Button(
                onClick = viewModel::use,
                enabled = state.preview != null && !state.saving,
                shape = ButtonDefaults.shape(BenflixShapes.Pill),
                colors =
                    ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                    ),
                scale = ButtonDefaults.scale(focusedScale = 1f),
                modifier = Modifier.focusRequester(useFocus),
            ) {
                Text(
                    text = if (state.saving) "Saving…" else "Use this ${state.pictureWord}",
                    fontWeight = FontWeight.Bold,
                    fontSize = BenflixType.body,
                )
            }
        }
    }
    val chooser: @Composable (Modifier) -> Unit = { m ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = m) {
            Text(
                text = listOfNotNull(item.name, item.productionYear?.toString()).joinToString(" · "),
                fontFamily = BenflixFont,
                fontWeight = FontWeight.Bold,
                fontSize = BenflixType.pageTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ChipRow(state.tabs, {
                if (it ==
                    PosterTab.POSTER
                ) {
                    state.pictureWord.replaceFirstChar { c -> c.uppercase() }
                } else {
                    it.label
                }
            }, { it == state.tab }, viewModel::pickTab, firstChip)
            if (state.tab == PosterTab.SEASONS) {
                ChipRow(
                    state.seasons,
                    { it.name ?: "Season ${it.indexNumber}" },
                    { it.id == state.seasonId },
                    { viewModel.pickSeason(it.id) },
                )
            }
            if (state.sources.size > 1) {
                ChipRow(
                    listOf<String?>(null) + state.sources,
                    { it ?: "Everywhere" },
                    { it == state.source },
                    viewModel::pickSource,
                )
            }
            val choices = state.choices
            when {
                choices == null -> {
                    Text("Looking online…", fontSize = BenflixType.body, color = Color(0xFFA7A9B0))
                }

                state.tab == PosterTab.SEASONS && state.seasons.isEmpty() -> {
                    Text("This show has no seasons in Benflix yet.", fontSize = BenflixType.body, color = Color(0xFFA7A9B0))
                }

                choices.isEmpty() -> {
                    Text(
                        text =
                            "Jellyfin couldn't find any online. If this is a show, turning on the TheTVDB plugin in " +
                                "Jellyfin finds more (Dashboard > Plugins > Catalog).",
                        fontSize = BenflixType.body,
                        color = Color(0xFFA7A9B0),
                    )
                }

                else -> {
                    val wide = kindShape > 1.1f
                    val columns =
                        when {
                            isTv && wide -> 3
                            isTv -> 6
                            wide -> 2
                            else -> 4
                        }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(4.dp, 4.dp, 4.dp, 40.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        itemsIndexed(state.shown, key = { i, c -> "$i-${c.url}" }) { _, choice ->
                            ChoiceTile(
                                choice = choice,
                                shape = kindShape,
                                logo = state.tab.kind == PosterKind.LOGO,
                                picked = choice == state.preview,
                                onFocus = { if (isTv) viewModel.tryOn(choice) },
                                onClick = {
                                    viewModel.tryOn(choice)
                                    if (isTv) useFocus.tryRequestFocus()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (isTv) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            modifier = modifier.fillMaxSize().padding(start = edge, end = edge, top = 24.dp),
        ) {
            preview(
                Modifier.width(
                    if (kindShape < 0.9f) {
                        260.dp
                    } else if (kindShape < 1.1f) {
                        300.dp
                    } else {
                        380.dp
                    },
                ),
            )
            chooser(Modifier.weight(1f))
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize().padding(start = edge, end = edge, top = 12.dp),
        ) {
            if (kindShape < 1.1f) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    preview(Modifier.width(if (kindShape < 0.9f) 150.dp else 190.dp))
                }
            } else {
                preview(Modifier.fillMaxWidth())
            }
            chooser(Modifier.weight(1f))
        }
    }
}

@Composable
private fun <T> ChipRow(
    list: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onPick: (T) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(list.size) { i ->
            val it = list[i]
            RequestsChip(
                label = label(it),
                selected = selected(it),
                onClick = { onPick(it) },
                modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun ChoiceTile(
    choice: PosterChoice,
    shape: Float,
    logo: Boolean,
    picked: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF17181C),
                focusedContainerColor = Color(0xFF17181C),
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White)),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(shape)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .then(if (picked) Modifier.border(3.dp, Color.White, BenflixShapes.Card) else Modifier),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = choice.thumb,
                contentDescription = choice.describe,
                contentScale = if (logo) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(if (logo) 8.dp else 0.dp),
            )
            Text(
                text = listOfNotNull(choice.provider, choice.language?.takeIf { it.isNotBlank() }?.uppercase()).joinToString(" · "),
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0x99000000), BenflixShapes.Small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
