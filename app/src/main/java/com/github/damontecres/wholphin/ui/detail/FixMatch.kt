package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemLookupApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MovieInfo
import org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery
import org.jellyfin.sdk.model.api.RemoteSearchResult
import org.jellyfin.sdk.model.api.SeriesInfo
import org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery
import timber.log.Timber
import javax.inject.Inject

/** Benflix (admins): "Wrong match? Fix it" looks the movie or show up again and swaps in the right one */
@HiltViewModel
class FixMatchViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
    ) : ViewModel() {
        private val _results = MutableStateFlow<List<RemoteSearchResult>?>(null)
        val results: StateFlow<List<RemoteSearchResult>?> = _results

        private val _working = MutableStateFlow(false)
        val working: StateFlow<Boolean> = _working

        fun search(
            item: BaseItem,
            name: String,
            year: Int?,
        ) {
            _results.value = null
            viewModelScope.launchIO {
                _results.value =
                    try {
                        if (item.type == BaseItemKind.SERIES) {
                            api.itemLookupApi
                                .getSeriesRemoteSearchResults(
                                    SeriesInfoRemoteSearchQuery(
                                        searchInfo = SeriesInfo(name = name, year = year, isAutomated = false),
                                        itemId = item.id,
                                        includeDisabledProviders = false,
                                    ),
                                ).content
                        } else {
                            api.itemLookupApi
                                .getMovieRemoteSearchResults(
                                    MovieInfoRemoteSearchQuery(
                                        searchInfo = MovieInfo(name = name, year = year, isAutomated = false),
                                        itemId = item.id,
                                        includeDisabledProviders = false,
                                    ),
                                ).content
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Fix match search")
                        emptyList()
                    }
            }
        }

        fun apply(
            item: BaseItem,
            result: RemoteSearchResult,
            onDone: () -> Unit,
        ) {
            if (_working.value) return
            _working.value = true
            viewModelScope.launchIO {
                try {
                    api.itemLookupApi.applySearchCriteria(
                        itemId = item.id,
                        data = result,
                        replaceAllImages = true,
                    )
                    showToast(context, "Fixed! Jellyfin is updating the details and pictures now")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Fix match apply")
                    showToast(context, "Couldn't fix the match")
                } finally {
                    _working.value = false
                }
            }
        }
    }

@Composable
fun FixMatchDialog(
    item: BaseItem,
    onDismissRequest: () -> Unit,
    onFixed: () -> Unit,
    viewModel: FixMatchViewModel = hiltViewModel(key = "fix-match-${item.id}"),
) {
    var query by remember { mutableStateOf(item.name.orEmpty()) }
    val year = item.data.productionYear
    LaunchedEffect(item.id) { viewModel.search(item, query, year) }
    val results by viewModel.results.collectAsState()
    val working by viewModel.working.collectAsState()
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(.92f)
                    .padding(20.dp),
        ) {
            Text(
                text = if (item.type == BaseItemKind.SERIES) "Wrong show? Pick the right one" else "Wrong movie? Pick the right one",
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Benflix will swap in the right details and pictures from the internet.",
                fontSize = BenflixType.small,
                color = BenflixColors.Soft,
            )
            SearchEditTextBox(
                value = query,
                onValueChange = { query = it },
                onSearchClick = { viewModel.search(item, query, null) },
                modifier = Modifier.fillMaxWidth(),
            )
            when (val list = results) {
                null -> {
                    Text("Searching...", fontSize = BenflixType.small)
                }

                emptyList<RemoteSearchResult>() -> {
                    Text("Nothing found. Try a different name.", fontSize = BenflixType.small)
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(list) { result ->
                            Surface(
                                onClick = { viewModel.apply(item, result, onFixed) },
                                enabled = !working,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                modifier = Modifier.fillMaxWidth(),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp),
                                ) {
                                    AsyncImage(
                                        model = result.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .width(48.dp)
                                                .height(72.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.DarkGray),
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = listOfNotNull(result.name, result.productionYear?.let { "($it)" }).joinToString(" "),
                                            fontSize = BenflixType.body,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        result.overview?.let {
                                            Text(text = it, fontSize = BenflixType.tag, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        result.searchProviderName?.let {
                                            Text(text = it, fontSize = BenflixType.tag, color = BenflixColors.Muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
