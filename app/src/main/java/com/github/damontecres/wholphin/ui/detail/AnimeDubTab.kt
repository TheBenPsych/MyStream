package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.BenflixDubChoice
import com.github.damontecres.wholphin.services.LibraryHomeService
import com.github.damontecres.wholphin.services.LibraryKind
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AnimeDubTabViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val libraryHome: LibraryHomeService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _items = MutableStateFlow<DataLoadingState<List<BaseItem>>>(DataLoadingState.Pending)
        val items: StateFlow<DataLoadingState<List<BaseItem>>> = _items

        fun load(
            parentId: UUID,
            kind: LibraryKind,
            dubbed: Boolean,
        ) {
            if (_items.value !is DataLoadingState.Pending) return
            _items.value = DataLoadingState.Loading
            viewModelScope.launchIO {
                try {
                    val ids = libraryHome.dubbedOrSubbed(parentId, kind, dubbed)
                    // Asked for in small batches so the web address doesn't get too long
                    val items =
                        ids
                            .chunked(BATCH)
                            .flatMap { batch ->
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            userId = serverRepository.currentUser?.id,
                                            ids = batch,
                                            fields = SlimItemFields,
                                            enableUserData = true,
                                        ),
                                    ).content.items
                            }.map { BaseItem(it) }
                            .sortedBy { it.sortName.lowercase() }
                    _items.value = DataLoadingState.Success(items)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load the %s tab", if (dubbed) "Dubbed" else "Subbed")
                    _items.value = DataLoadingState.Error(ex)
                }
            }
        }

        fun open(
            item: BaseItem,
            dubbed: Boolean,
            play: Boolean,
        ) {
            BenflixDubChoice.set(item.data.seriesId ?: item.id, if (dubbed) BenflixDubChoice.Mode.DUB else BenflixDubChoice.Mode.SUB)
            navigationManager.navigateTo(if (play) Destination.Playback(item) else item.destination())
        }

        companion object {
            private const val BATCH = 80
        }
    }

/**
 * Benflix: the Dubbed or Subbed tab of an anime page. Opening something here also makes it play
 * that way on this device: English voices for Dubbed, Japanese with English subtitles for Subbed.
 */
@Composable
fun AnimeDubTab(
    parentId: UUID,
    kind: LibraryKind,
    dubbed: Boolean,
    modifier: Modifier = Modifier,
    viewModel: AnimeDubTabViewModel = hiltViewModel(key = "$parentId-dub-$dubbed"),
) {
    LaunchedEffect(Unit) { viewModel.load(parentId, kind, dubbed) }
    val state by viewModel.items.collectAsState()
    when (val st = state) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Pending,
        DataLoadingState.Loading,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success -> {
            if (st.data.isEmpty()) {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    Text(
                        text =
                            if (dubbed) {
                                "No anime with English voices found yet"
                            } else {
                                "No anime with Japanese voices and subtitles found yet"
                            },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                val focusRequester = remember { FocusRequester() }
                Column(modifier = modifier) {
                    Text(
                        text =
                            if (dubbed) {
                                "Plays with English voices"
                            } else {
                                "Plays in Japanese with English subtitles"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                    CardGrid(
                        pager = st.data,
                        onClickItem = { _, item -> viewModel.open(item, dubbed, play = false) },
                        onLongClickItem = { _, _ -> },
                        onClickPlay = { _, item -> viewModel.open(item, dubbed, play = true) },
                        letterPosition = { 0 },
                        gridFocusRequester = focusRequester,
                        showJumpButtons = false,
                        showLetterButtons = false,
                        cardContent = @Composable { (item, _, onClick, onLongClick, widthPx, mod) ->
                            GridCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                fillWidth = widthPx,
                                modifier = mod,
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Benflix: works out what kind of library a page is (Movies, Anime Shows, Stand up...) to pick its tabs */
@HiltViewModel
class LibraryKindViewModel
    @Inject
    constructor(
        private val libraryHome: LibraryHomeService,
    ) : ViewModel() {
        private val _kind = MutableStateFlow<LibraryKind?>(null)
        val kind: StateFlow<LibraryKind?> = _kind

        fun load(parentId: UUID) {
            if (_kind.value != null) return
            viewModelScope.launchIO {
                _kind.value =
                    try {
                        libraryHome.kindOf(parentId)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not tell what kind of library %s is", parentId)
                        LibraryKind.OTHER
                    }
            }
        }
    }

/** The library's kind, or null for the moment it takes to find out */
@Composable
fun rememberLibraryKind(
    parentId: UUID,
    viewModel: LibraryKindViewModel = hiltViewModel(key = "$parentId-kind"),
): LibraryKind? {
    LaunchedEffect(parentId) { viewModel.load(parentId) }
    return viewModel.kind.collectAsState().value
}
