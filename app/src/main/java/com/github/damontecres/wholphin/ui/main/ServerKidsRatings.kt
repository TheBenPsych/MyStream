package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemUpdateApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MetadataField
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** A movie or show that looks like it's for kids but has no rating, so Kids never sees it */
data class UnratedKidsTitle(
    val id: UUID,
    val name: String,
    val show: Boolean,
    val year: Int?,
)

/**
 * Benflix Server page (Tools): kids-looking titles with no rating (Redesign 2, section 11). Kids leaves out anything
 * without a rating, so some kids shows and anime never appeared. This lists the family, kids and animation titles with
 * no rating, so Ben can give each one a rating in one place. The rating is locked so Jellyfin doesn't take it away.
 */
@HiltViewModel
class KidsRatingsViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _titles = MutableStateFlow<List<UnratedKidsTitle>?>(null)

        /** null while looking */
        val titles: StateFlow<List<UnratedKidsTitle>?> = _titles

        private val _problem = MutableStateFlow<String?>(null)
        val problem: StateFlow<String?> = _problem

        fun load() {
            val userId = serverRepository.currentUser?.id ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _titles.value =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    recursive = true,
                                    genres = KIDS_GENRES,
                                    hasParentalRating = false,
                                    sortBy = listOf(ItemSortBy.SORT_NAME),
                                    enableImages = false,
                                    enableTotalRecordCount = false,
                                    limit = MAX_TITLES,
                                ),
                            ).content.items
                            .filter { it.officialRating.isNullOrBlank() }
                            .map {
                                UnratedKidsTitle(
                                    id = it.id,
                                    name = it.name.orEmpty(),
                                    show = it.type == BaseItemKind.SERIES,
                                    year = it.productionYear,
                                )
                            }
                    _problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Kids ratings")
                    _problem.value = ex.message ?: "Couldn't ask Jellyfin."
                }
            }
        }

        /** Gives [title] this rating and locks it, so a metadata refresh doesn't clear it again */
        fun setRating(
            title: UnratedKidsTitle,
            rating: String,
            onDone: (String) -> Unit,
        ) {
            val userId = serverRepository.currentUser?.id ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
                val words =
                    try {
                        val dto = api.userLibraryApi.getItem(title.id, userId).content
                        api.itemUpdateApi.updateItem(
                            title.id,
                            dto.copy(
                                officialRating = rating,
                                lockedFields = (dto.lockedFields.orEmpty() + MetadataField.OFFICIAL_RATING).distinct(),
                            ),
                        )
                        _titles.value = _titles.value?.filter { it.id != title.id }
                        "${title.name} is now $rating."
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Kids ratings: save")
                        "Couldn't save the rating. Try again."
                    }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(words) }
            }
        }

        companion object {
            /** Genres that usually mean a title is for children */
            val KIDS_GENRES = listOf("Family", "Kids", "Children", "Animation")
            private const val MAX_TITLES = 300

            /** The choices, kids ratings first. The last ones keep a title out of Kids (and Big kids). */
            val MOVIE_RATINGS = listOf("G", "PG", "PG-13", "R")
            val SHOW_RATINGS = listOf("TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA")
        }
    }

internal fun LazyListScope.kidsRatingItems() {
    item { SectionTitle("Kids titles with no rating") }
    item(key = "kids-ratings") { KidsRatingsRows() }
}

@Composable
private fun KidsRatingsRows(viewModel: KidsRatingsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val titles by viewModel.titles.collectAsState()
    val problem by viewModel.problem.collectAsState()
    var picking by remember { mutableStateOf<UnratedKidsTitle?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val list = titles
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            list == null && problem != null -> {
                ServerActionRow(
                    headline = "Couldn't look for unrated kids titles",
                    supporting = problem,
                    leading = { Dot(Light.UNKNOWN) },
                    onClick = { viewModel.load() },
                    actions = listOf("Try again" to { viewModel.load() }),
                )
            }

            list == null -> {
                LoadingBox("Looking for kids titles with no rating…")
            }

            list.isEmpty() -> {
                ServerActionRow(
                    headline = "Every kids title has a rating",
                    supporting = "Family, kids and animation movies and shows all have a rating, so Kids can find them.",
                    leading = { Dot(Light.GOOD) },
                )
            }

            else -> {
                ServerActionRow(
                    headline = if (list.size == 1) "1 kids title has no rating" else "${list.size} kids titles have no rating",
                    supporting =
                        "Kids and Big kids leave out anything with no rating. Give each one a rating and it shows up " +
                            "(G, PG, TV-Y, TV-G and TV-PG are for Kids; PG-13 and TV-14 for Big kids).",
                    leading = { Dot(Light.WARN) },
                )
                val shown = if (showAll) list else list.take(SHOW_FIRST)
                shown.forEach { title ->
                    ServerActionRow(
                        headline = title.name,
                        supporting = listOfNotNull(if (title.show) "Show" else "Movie", title.year?.toString()).joinToString(" · "),
                        onClick = { picking = title },
                        actions = listOf("Give it a rating" to { picking = title }),
                    )
                }
                if (!showAll && list.size > SHOW_FIRST) {
                    ServerActionRow(
                        headline = "Show all ${list.size}",
                        onClick = { showAll = true },
                        actions = listOf("Show all" to { showAll = true }),
                    )
                }
            }
        }
    }
    picking?.let { title ->
        BasicDialog(onDismissRequest = { picking = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(8.dp)) {
                Text(
                    text = title.name,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Pick its rating. Kids sees G, PG, TV-Y, TV-Y7, TV-G and TV-PG. Big kids also sees PG-13 and TV-14.",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                )
                val choices = if (title.show) KidsRatingsViewModel.SHOW_RATINGS else KidsRatingsViewModel.MOVIE_RATINGS
                choices.forEach { rating ->
                    Button(
                        onClick = {
                            picking = null
                            viewModel.setRating(title, rating) { words ->
                                com.github.damontecres.wholphin.ui.components.BenflixNotices
                                    .pill(words)
                            }
                        },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(rating, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** The first few show straight away; the rest behind "Show all" */
private const val SHOW_FIRST = 8
