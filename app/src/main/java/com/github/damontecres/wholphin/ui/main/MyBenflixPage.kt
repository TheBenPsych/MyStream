package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MyBenflixViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** The profile picture, the same one the top bar shows */
        fun userImageUrl(userId: java.util.UUID): String = api.imageApi.getUserImageUrl(userId)

        private val _rows = MutableStateFlow<List<HubRow>?>(null)
        val rows: StateFlow<List<HubRow>?> = _rows

        fun load() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch

                suspend fun safe(block: suspend () -> List<BaseItem>): List<BaseItem> =
                    try {
                        block()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "My Benflix row failed")
                        emptyList()
                    }

                fun request(
                    filters: List<ItemFilter>? = null,
                    sortBy: ItemSortBy,
                    isPlayed: Boolean? = null,
                    types: List<BaseItemKind> = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                ) = GetItemsRequest(
                    userId = userId,
                    includeItemTypes = types,
                    recursive = true,
                    filters = filters,
                    isPlayed = isPlayed,
                    sortBy = listOf(sortBy),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = ROW_MAX,
                    fields = HomeItemFields,
                )
                val rows =
                    coroutineScope {
                        listOf(
                            async {
                                HubRow(
                                    "Continue watching",
                                    safe {
                                        api.itemsApi
                                            .getResumeItems(
                                                GetResumeItemsRequest(userId = userId, limit = ROW_MAX, fields = HomeItemFields),
                                            ).content.items
                                            .map { BaseItem(it, true) }
                                    },
                                )
                            },
                            async {
                                HubRow(
                                    "My List",
                                    safe {
                                        api.itemsApi
                                            .getItems(request(filters = listOf(ItemFilter.IS_FAVORITE), sortBy = ItemSortBy.DATE_CREATED))
                                            .content.items
                                            .map { BaseItem(it) }
                                    },
                                )
                            },
                            async {
                                HubRow(
                                    "Liked",
                                    safe {
                                        api.itemsApi
                                            .getItems(request(filters = listOf(ItemFilter.LIKES), sortBy = ItemSortBy.DATE_PLAYED))
                                            .content.items
                                            .map { BaseItem(it) }
                                    },
                                )
                            },
                            async {
                                HubRow(
                                    "Watch history",
                                    safe {
                                        api.itemsApi
                                            .getItems(
                                                request(
                                                    sortBy = ItemSortBy.DATE_PLAYED,
                                                    isPlayed = true,
                                                    types = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                                ),
                                            ).content.items
                                            .map { BaseItem(it, true) }
                                    },
                                )
                            },
                        ).awaitAll()
                    }
                _rows.value = rows.filter { it.items.isNotEmpty() }
            }
        }
    }

/**
 * Your own corner of Benflix on the TV: who's watching, what you're in the middle of,
 * your list, what you liked and what you watched lately
 */
@Composable
fun MyBenflixPage(
    modifier: Modifier = Modifier,
    viewModel: MyBenflixViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val rows by viewModel.rows.collectAsState()
    val user by viewModel.serverRepository.currentUserFlow.collectAsState(null)
    val kids by KidsMode.active.collectAsState()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 12.dp, start = 8.dp),
        ) {
            user?.let {
                UserIconCardImage(
                    id = it.id,
                    name = if (kids) "Kids" else it.name,
                    imageUrl = if (kids) null else remember(it.id) { viewModel.userImageUrl(it.id) },
                    modifier = Modifier.size(56.dp),
                )
            }
            Column {
                Text(
                    text = "MY BENFLIX",
                    fontFamily = BenflixTitleFont,
                    fontSize = 48.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = if (kids) "Kids" else user?.name.orEmpty(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
        if (!kids) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                if (userDto?.policy?.isAdministrator == true) {
                    Button(onClick = { viewModel.navigationManager.navigateTo(Destination.ServerStatus) }) {
                        Text("Server")
                    }
                }
                Button(onClick = { viewModel.navigationManager.navigateTo(Destination.HowTo) }) {
                    Text("How to use Benflix")
                }
            }
        }
        when (val list = rows) {
            null -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            else -> {
                if (list.isEmpty()) {
                    Text(
                        text = "Nothing here yet. Add things to My List or give them a thumbs up.",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 32.dp, start = 8.dp),
                    )
                } else {
                    HubRows(
                        rows = list,
                        onClickItem = { viewModel.navigationManager.navigateTo(it.destination()) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private const val ROW_MAX = 20
