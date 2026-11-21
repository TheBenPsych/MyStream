package com.github.damontecres.wholphin.ui.detail.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** One short review from TMDb for the "What people said" row */
data class TitleReview(
    val author: String,
    /** Out of 10, or null when the writer gave no score */
    val score: Double?,
    val text: String,
)

/**
 * "What people said" (Ben 2026-11-21): two short reviews on a movie or show's title page. Tower fetches
 * them from TMDb (and keeps them a week); it sends none for Kids, or until Ben has added the free TMDb key.
 */
@HiltViewModel
class TitleReviewsViewModel
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        private val _reviews = MutableStateFlow<Map<String, List<TitleReview>>>(emptyMap())
        val reviews: StateFlow<Map<String, List<TitleReview>>> = _reviews
        private val asked = mutableSetOf<String>()

        fun load(
            kind: String,
            tmdb: String,
        ) {
            val key = "$kind:$tmdb"
            if (!asked.add(key)) return
            viewModelScope.launch {
                try {
                    val list = helper.getJson("/api/reviews?type=$kind&tmdb=$tmdb").optJSONArray("reviews")
                    val found =
                        (0 until (list?.length() ?: 0)).mapNotNull { i ->
                            val o = list?.optJSONObject(i) ?: return@mapNotNull null
                            TitleReview(
                                author = o.optString("author"),
                                score = if (o.isNull("score")) null else o.optDouble("score"),
                                text = o.optString("text"),
                            )
                        }
                    _reviews.update { it + (key to found) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // No reviews is fine: the row just doesn't show
                    Timber.d(ex, "Reviews for %s", key)
                }
            }
        }
    }

/** The reviews for [item] ("movie" or "tv"); empty until Tower answers, and when there are none */
@Composable
fun rememberTitleReviews(
    item: BaseItem,
    kind: String,
    viewModel: TitleReviewsViewModel = hiltViewModel(),
): List<TitleReview> {
    val tmdb =
        item.data.providerIds
            ?.get("Tmdb")
            ?.filter { it.isDigit() }
            ?.takeIf { it.isNotEmpty() } ?: return emptyList()
    LaunchedEffect(kind, tmdb) { viewModel.load(kind, tmdb) }
    val all by viewModel.reviews.collectAsState()
    return all["$kind:$tmdb"].orEmpty()
}

private fun reviewHead(r: TitleReview): String =
    (r.score?.let { "${if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()} / 10 · " } ?: "") + r.author

/** The row: each card shows the score, who wrote it and three lines. OK or a tap opens the whole review. */
@Composable
fun TitleReviewsRow(
    scope: TitleTabScope,
    reviews: List<TitleReview>,
) {
    var open by remember { mutableStateOf<TitleReview?>(null) }
    val shape = RoundedCornerShape(12.dp)
    LazyRow(
        contentPadding = PaddingValues(horizontal = scope.side),
        horizontalArrangement = Arrangement.spacedBy(if (scope.wide) 14.dp else 10.dp),
    ) {
        itemsIndexed(reviews) { _, r ->
            Surface(
                onClick = { open = r },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = BenflixColors.Raised,
                        contentColor = BenflixColors.Text,
                        focusedContainerColor = BenflixColors.FocusRow,
                        focusedContentColor = BenflixColors.Text,
                        pressedContainerColor = BenflixColors.FocusRow,
                        pressedContentColor = BenflixColors.Text,
                    ),
                border = doorFocusBorder(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.width(if (scope.wide) 380.dp else 290.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = reviewHead(r),
                        color = BenflixColors.Text,
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = r.text.replace(Regex("\\s+"), " "),
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                        maxLines = 3,
                        minLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    open?.let { r -> TitleStoryPanel(title = reviewHead(r), story = r.text, onDismiss = { open = null }) }
}
