package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.BenflixTower
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/**
 * Glowfin: "What's the hold-up?" for something Ben said yes to that hasn't arrived. Tower's helper asks
 * Radarr / Sonarr and answers in plain words (see holdup in server/benflix_server_tools.py).
 */
data class HoldUp(
    val line: String,
    val detail: String,
    val canSearch: Boolean,
    val canSeeCopies: Boolean,
    /** The stuck download that "Try another copy" throws away: which app and its id there */
    val replaceApp: String? = null,
    val replaceId: String? = null,
    /** Filled in after "See what copies are out there" */
    val copiesLine: String? = null,
    val copiesDetail: String? = null,
    /** Buttons already pressed, so they go away */
    val used: Set<String> = emptySet(),
)

@HiltViewModel
class HoldUpViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val tower: BenflixTower,
    ) : ViewModel() {
        private val _holdUp = MutableStateFlow<HoldUp?>(null)
        val holdUp: StateFlow<HoldUp?> = _holdUp
        private var loadedFor: String? = null
        private var kind = "movie"
        private var tmdb = 0

        fun load(
            isMovie: Boolean,
            tmdbId: Int,
        ) {
            val key = "$isMovie-$tmdbId"
            if (loadedFor == key) return
            loadedFor = key
            kind = if (isMovie) "movie" else "tv"
            tmdb = tmdbId
            viewModelScope.launch(WholphinDispatchers.IO) {
                // Away from home (or Tower not updated yet) there is no answer: the box just stays away
                val j = ask { tower.holdUp(kind, tmdb) } ?: return@launch
                val line = j.optString("line").takeIf { it.isNotBlank() } ?: return@launch
                val replace = j.optJSONObject("replace")
                _holdUp.value =
                    HoldUp(
                        line = line,
                        detail = j.optString("detail"),
                        canSearch = j.optBoolean("search"),
                        canSeeCopies = j.optBoolean("copies"),
                        replaceApp = replace?.optString("app")?.takeIf { it.isNotBlank() },
                        replaceId = replace?.optString("id")?.takeIf { it.isNotBlank() },
                    )
            }
        }

        private suspend fun <T> ask(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Hold-up check failed")
                null
            }

        private fun used(what: String) {
            _holdUp.value = _holdUp.value?.let { it.copy(used = it.used + what) }
        }

        fun lookAgain() {
            used("search")
            viewModelScope.launch(WholphinDispatchers.IO) {
                showToast(context, ask { tower.holdUpSearch(kind, tmdb) } ?: "Couldn't reach Tower. Try again in a minute.")
            }
        }

        fun tryAnotherCopy() {
            val h = _holdUp.value ?: return
            val app = h.replaceApp ?: return
            val id = h.replaceId ?: return
            used("replace")
            viewModelScope.launch(WholphinDispatchers.IO) {
                showToast(context, ask { tower.holdUpReplace(app, id) } ?: "Couldn't reach Tower. Try again in a minute.")
            }
        }

        fun seeCopies() {
            used("copies")
            viewModelScope.launch(WholphinDispatchers.IO) {
                val j: JSONObject? = ask { tower.holdUpCopies(tmdb) }
                if (j == null) {
                    showToast(context, "Couldn't reach Tower. Try again in a minute.")
                } else {
                    _holdUp.value = _holdUp.value?.copy(copiesLine = j.optString("line"), copiesDetail = j.optString("detail"))
                }
            }
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HoldUpBlock(
    isMovie: Boolean,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    viewModel: HoldUpViewModel = hiltViewModel(key = "hold-up-$isMovie-$tmdbId"),
) {
    LaunchedEffect(isMovie, tmdbId) { viewModel.load(isMovie, tmdbId) }
    val holdUp by viewModel.holdUp.collectAsState()
    val h = holdUp ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = .07f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("What's the hold-up?", color = BenflixColors.Soft, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
        Text(h.line, color = BenflixColors.Text, fontSize = BenflixType.body, fontWeight = FontWeight.ExtraBold)
        if (h.detail.isNotBlank()) Text(h.detail, color = BenflixColors.Soft, fontSize = BenflixType.small)
        h.copiesLine?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = BenflixColors.Text, fontSize = BenflixType.body, fontWeight = FontWeight.ExtraBold)
        }
        h.copiesDetail?.takeIf { it.isNotBlank() }?.let { Text(it, color = BenflixColors.Soft, fontSize = BenflixType.small) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            val scale = ButtonDefaults.scale(focusedScale = 1f)
            if (h.canSearch && "search" !in h.used) {
                Button(onClick = viewModel::lookAgain, scale = scale) { Text("Look again now") }
            }
            if (h.replaceApp != null && h.replaceId != null && "replace" !in h.used) {
                Button(onClick = viewModel::tryAnotherCopy, scale = scale) { Text("Try another copy") }
            }
            if (h.canSeeCopies && isMovie && "copies" !in h.used) {
                Button(onClick = viewModel::seeCopies, scale = scale) { Text("See what copies are out there") }
            }
        }
    }
}
