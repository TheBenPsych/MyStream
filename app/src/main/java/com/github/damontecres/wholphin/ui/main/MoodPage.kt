package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/**
 * Loads a fresh random set of titles for one [HomeMood]
 */
@HiltViewModel
class MoodViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** null while loading */
        private val _items = MutableStateFlow<List<BaseItem>?>(null)
        val items: StateFlow<List<BaseItem>?> = _items

        fun load(mood: HomeMood) {
            _items.value = null
            viewModelScope.launch(WholphinDispatchers.IO) {
                _items.value =
                    try {
                        val userId = serverRepository.currentUser?.id ?: return@launch
                        val disliked = dislikes(userId)
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = mood.types,
                                    recursive = true,
                                    genres = mood.genres,
                                    minCommunityRating = mood.minRating,
                                    isPlayed = if (mood.unwatchedOnly) false else null,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = MOOD_PAGE_FETCH,
                                    fields = HomeItemFields + listOf(ItemFields.RECURSIVE_ITEM_COUNT, ItemFields.CHILD_COUNT),
                                ),
                            ).content.items
                            .filter { it.id !in disliked && it.seriesId?.let { s -> s in disliked } != true }
                            .map { BaseItem(it) }
                            .filter(mood.keep)
                            .take(MOOD_PAGE_MAX)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error loading mood %s", mood)
                        emptyList()
                    }
            }
        }

        /** Titles this profile gave a thumbs down, so they never show up */
        private suspend fun dislikes(userId: java.util.UUID): Set<java.util.UUID> =
            try {
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            filters = listOf(ItemFilter.DISLIKES),
                            limit = 300,
                        ),
                    ).content.items
                    .map { it.id }
                    .toSet()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Error loading dislikes")
                emptySet()
            }
    }

/**
 * A full page of picks for one mood, opened from the "Pick a mood" tiles on the home page
 */
@Composable
fun MoodPage(
    moodName: String,
    modifier: Modifier = Modifier,
    viewModel: MoodViewModel = hiltViewModel(),
) {
    val mood = remember(moodName) { HomeMood.entries.firstOrNull { it.name == moodName } ?: HomeMood.BINGE }
    LaunchedEffect(mood) { viewModel.load(mood) }
    val items by viewModel.items.collectAsState()
    val gridFocusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            // The mood's own colors, so it's clear which tile opened this page
            Box(
                Modifier
                    .width(8.dp)
                    .height(40.dp)
                    .background(Brush.verticalGradient(mood.colors), RoundedCornerShape(4.dp)),
            )
            // The title takes the space that's left, so the shuffle button always fits beside it
            Text(
                text = stringResource(mood.title),
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.load(mood) }) {
                Text(stringResource(R.string.mood_shuffle))
            }
        }
        Text(
            text = stringResource(R.string.mood_page_subtitle),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val list = items) {
            null -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            else -> {
                if (list.isEmpty()) {
                    Text(
                        text = stringResource(R.string.mood_nothing_found),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                } else {
                    LaunchedEffect(list) { gridFocusRequester.tryRequestFocus() }
                    CardGrid(
                        pager = list,
                        onClickItem = { index, item -> viewModel.navigationManager.navigateTo(item.destination(index)) },
                        onLongClickItem = { _, _ -> },
                        onClickPlay = { _, _ -> },
                        letterPosition = { -1 },
                        gridFocusRequester = gridFocusRequester,
                        showJumpButtons = false,
                        showLetterButtons = false,
                        modifier = Modifier.fillMaxSize(),
                        cardContent = { (item, _, onClick, onLongClick, widthPx, mod) ->
                            GridCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showTitle = true,
                                fillWidth = widthPx,
                                modifier = mod,
                            )
                        },
                        columns = 6,
                    )
                }
            }
        }
    }
}

private const val MOOD_PAGE_FETCH = 150
private const val MOOD_PAGE_MAX = 60
