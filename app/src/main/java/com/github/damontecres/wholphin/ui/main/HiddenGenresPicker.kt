package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.HiddenGenresStore
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HiddenGenresViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val store: HiddenGenresStore,
    ) : ViewModel() {
        /** Every movie & show genre on Benflix, by name */
        private val _genres = MutableStateFlow<List<String>>(emptyList())
        val genres: StateFlow<List<String>> = _genres

        /** Hidden ones, lower case */
        private val _hidden = MutableStateFlow<Set<String>>(emptySet())
        val hidden: StateFlow<Set<String>> = _hidden

        private val saveLock = Mutex()

        /** "More of this" ones, lower case */
        private val _more = MutableStateFlow<Set<String>>(emptySet())
        val more: StateFlow<Set<String>> = _more

        /** More of this on/off. Turning it on also un-hides the genre */
        fun toggleMore(genre: String) {
            val key = genre.lowercase()
            _more.value = _more.value.let { if (key in it) it - key else it + key }
            if (key in _more.value && key in _hidden.value) toggle(genre)
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch
                try {
                    saveLock.withLock { store.saveMore(userId, _more.value) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't save liked genres")
                }
            }
        }

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch
                try {
                    _hidden.value = store.load(userId)
                    _more.value = store.loadMore(userId)
                    _genres.value =
                        api.genresApi
                            .getGenres(
                                GetGenresRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    sortBy = listOf(ItemSortBy.SORT_NAME),
                                    enableImages = false,
                                    enableTotalRecordCount = false,
                                ),
                            ).content.items
                            .mapNotNull { it.name?.trim()?.takeIf { name -> name.isNotEmpty() } }
                            .distinctBy { it.lowercase() }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't load genres")
                }
            }
        }

        fun toggle(genre: String) {
            val key = genre.lowercase()
            val now = _hidden.value.let { if (key in it) it - key else it + key }
            _hidden.value = now
            if (key in now && key in _more.value) toggleMore(genre)
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch
                try {
                    // One save at a time, each with the newest set, so quick taps can't finish out
                    // of order and leave an older set saved
                    saveLock.withLock { store.save(userId, _hidden.value) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't save hidden genres")
                }
            }
        }
    }

/**
 * Genre buttons: press one to hide it (it turns red with "Hidden"), press again to bring it back.
 * Used in the welcome setup and in Settings > Profile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiddenGenresPicker(
    modifier: Modifier = Modifier,
    focusFirst: Boolean = true,
    viewModel: HiddenGenresViewModel = hiltViewModel(),
) {
    val genres by viewModel.genres.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val isTv = rememberIsTv()
    val focus = remember { FocusRequester() }
    LaunchedEffect(genres.isNotEmpty()) { if (focusFirst && genres.isNotEmpty()) focus.tryRequestFocus() }
    if (genres.isEmpty()) {
        Text("Loading genres…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        genres.forEachIndexed { index, genre ->
            val off = genre.lowercase() in hidden
            Surface(
                onClick = { viewModel.toggle(genre) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = if (off) BenflixRed else Color(0xFF2A2A30),
                        contentColor = if (off) com.github.damontecres.wholphin.ui.theme.OnBenflixRed else Color.White,
                        focusedContainerColor = if (off) BenflixRed else Color(0xFF45454F),
                        focusedContentColor = if (off) com.github.damontecres.wholphin.ui.theme.OnBenflixRed else Color.White,
                    ),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), shape = RoundedCornerShape(50)),
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
            ) {
                Text(
                    text = if (off) "$genre · Hidden" else genre,
                    fontSize = BenflixType.body,
                    fontWeight = if (off) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Settings > Profile > Hide genres */
@Composable
fun HiddenGenresDialog(onDismiss: () -> Unit) {
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
        ) {
            Text("Hide genres", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold)
            Text(
                "Pick genres you never want to see. They won't show on Home, the banner, Surprise me " +
                    "or library pages for this profile. Search still finds them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HiddenGenresPicker()
            Button(onClick = onDismiss, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Done") }
        }
    }
}

/**
 * Benflix redesign (setup, approved p2-gen-rows): one row per genre with "More of this" and "Hide for me".
 * More of this gets its own row in the Movies and Shows doors; hidden is hidden everywhere for this person.
 */
@Composable
fun GenreRowsPicker(
    modifier: Modifier = Modifier,
    viewModel: HiddenGenresViewModel = hiltViewModel(),
) {
    val genres by viewModel.genres.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val more by viewModel.more.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(genres.isNotEmpty()) { if (genres.isNotEmpty()) focus.tryRequestFocus() }
    if (genres.isEmpty()) {
        Text("Loading genres…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        genres.forEachIndexed { index, genre ->
            val key = genre.lowercase()
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    genre,
                    fontSize = BenflixType.lead,
                    fontWeight = FontWeight.Bold,
                    color = if (key in hidden) Color.White.copy(alpha = .45f) else Color.White,
                    modifier = Modifier.weight(1f),
                )
                GenreChoice(
                    text = "More of this",
                    on = key in more,
                    onClick = { viewModel.toggleMore(genre) },
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                )
                GenreChoice(text = "Hide for me", on = key in hidden, onClick = { viewModel.toggle(genre) })
            }
        }
    }
}

@Composable
private fun GenreChoice(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (on) Color.White else Color(0xFF2A2A30),
                contentColor = if (on) Color(0xFF0B0C10) else Color.White,
                focusedContainerColor = if (on) Color.White else Color(0xFF45454F),
                focusedContentColor = if (on) Color(0xFF0B0C10) else Color.White,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), inset = (-3).dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            if (on) "✓ $text" else text,
            fontSize = BenflixType.body,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
