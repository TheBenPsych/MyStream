package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.BenflixPosters
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PosterChoice
import com.github.damontecres.wholphin.services.PosterKind
import com.github.damontecres.wholphin.services.PosterProblem
import com.github.damontecres.wholphin.ui.nav.Destination
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
import timber.log.Timber
import javax.inject.Inject

data class PosterFixerState(
    /** null while looking */
    val problems: List<PosterProblem>? = null,
    val failed: Boolean = false,
    /** How many there were at the start, for "4 of 23" */
    val total: Int = 0,
    val done: Int = 0,
    val suggestion: PosterChoice? = null,
    val lookingForSuggestion: Boolean = false,
    val saving: Boolean = false,
    val note: String? = null,
) {
    val current: PosterProblem? get() = problems?.firstOrNull()
}

/**
 * Benflix "Fix posters" (main profile only), one title at a time: the picture there now, the best one
 * Benflix found, and Use this one / Pick another / Skip / Leave it. Made for the TV remote.
 */
@HiltViewModel
class PosterFixerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val posters: BenflixPosters,
        private val imageUrlService: ImageUrlService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PosterFixerState())
        val state: StateFlow<PosterFixerState> = _state
        private var suggesting: Job? = null

        init {
            load()
        }

        fun load() {
            _state.value = PosterFixerState()
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val found = posters.problems()
                    _state.value = PosterFixerState(problems = found, total = found.size)
                    suggest()
                    val note = posters.nightNote()
                    _state.update { it.copy(note = note) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Fix posters")
                    _state.value = PosterFixerState(failed = true)
                }
            }
        }

        private fun suggest() {
            suggesting?.cancel()
            val problem = _state.value.current ?: return
            _state.update { it.copy(suggestion = null, lookingForSuggestion = true) }
            suggesting =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    val best = posters.suggestion(problem)
                    _state.update { if (it.current == problem) it.copy(suggestion = best, lookingForSuggestion = false) else it }
                }
        }

        private fun next(countDone: Boolean) {
            _state.update {
                it.copy(
                    problems = it.problems?.drop(1),
                    done = if (countDone) it.done + 1 else it.done,
                    suggestion = null,
                    saving = false,
                )
            }
            suggest()
        }

        fun use() {
            val s = _state.value
            val problem = s.current ?: return
            val choice = s.suggestion ?: return
            if (s.saving) return
            _state.update { it.copy(saving = true) }
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    posters.use(problem.itemId, problem.kind, choice)
                    next(countDone = true)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Fix posters: saving")
                    showToast(context, "Couldn't save it. Try again in a moment.")
                    _state.update { it.copy(saving = false) }
                }
            }
        }

        /** Skipped ones come back next time */
        fun skip() {
            _state.update { s -> s.copy(problems = s.problems?.let { if (it.size > 1) it.drop(1) + it.first() else it }) }
            if ((_state.value.problems?.size ?: 0) > 1) suggest() else next(countDone = false)
        }

        /** Never asks about this title again */
        fun leaveAlone() {
            val problem = _state.value.current ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
                posters.leaveAlone(problem.itemId)
            }
            next(countDone = false)
        }

        fun pickAnother() {
            val problem = _state.value.current ?: return
            navigationManager.navigateTo(Destination.PosterPicker(problem.itemId, problem.kind.name))
        }

        /** Back from the picker: if it got fixed there, move on */
        fun recheck() {
            val problem = _state.value.current ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
                if (!posters.stillAProblem(problem) && _state.value.current == problem) next(countDone = true)
            }
        }

        fun imageOf(problem: PosterProblem): String? =
            problem.currentTag?.let {
                imageUrlService.getItemImageUrl(
                    itemId = problem.itemId,
                    imageType = problem.kind.type,
                    fillHeight = 600,
                    tag = it,
                )
            }
    }

@Composable
fun PosterFixerPage(
    modifier: Modifier = Modifier,
    viewModel: PosterFixerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    val edge = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val useFocus = remember { FocusRequester() }
    val firstResume = remember { booleanArrayOf(true) }
    LifecycleResumeEffect(Unit) {
        if (!firstResume[0]) viewModel.recheck()
        firstResume[0] = false
        onPauseOrDispose { }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxSize()
                .padding(start = edge, end = edge, top = if (isTv) 24.dp else 12.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Fix posters",
                fontFamily = BenflixFont,
                fontWeight = FontWeight.Bold,
                fontSize = BenflixType.pageTitle,
                modifier = Modifier.weight(1f),
            )
            val left = state.problems?.size
            if (left != null && left > 0) {
                Text("${state.done + 1} of ${state.done + left}", fontSize = BenflixType.small, color = Color(0xFFA7A9B0))
            }
        }
        state.note?.let { Text(it, fontSize = BenflixType.small, color = Color(0xFFA7A9B0)) }
        val problem = state.current
        when {
            state.failed -> {
                Text("Couldn't check the posters. Try again in a moment.", fontSize = BenflixType.body)
                FixButton("Try again", white = true, onClick = viewModel::load)
            }

            state.problems == null -> {
                Text("Looking for posters that need a look…", fontSize = BenflixType.body, color = Color(0xFFA7A9B0))
            }

            problem == null -> {
                Text(
                    text = if (state.done > 0) "All done! You fixed ${state.done}." else "Nothing needs a look. Every poster looks good.",
                    fontSize = BenflixType.lead,
                    fontWeight = FontWeight.Bold,
                )
                FixButton("Check again", white = true, onClick = viewModel::load)
            }

            else -> {
                LaunchedEffect(problem.key, state.suggestion != null) { useFocus.tryRequestFocus() }
                val shape = problem.kind.shape
                val picWidth =
                    when {
                        problem.kind != PosterKind.POSTER && isTv -> 340.dp
                        problem.kind != PosterKind.POSTER -> 130.dp
                        isTv -> 200.dp
                        else -> 120.dp
                    }
                val pictures: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Picture(viewModel.imageOf(problem), shape, picWidth, problem.problem, logo = problem.kind == PosterKind.LOGO)
                        Text("→", fontSize = BenflixType.hero, color = Color(0xFF8D9098))
                        val s = state.suggestion
                        Picture(
                            url = s?.url,
                            shape = shape,
                            width = picWidth,
                            caption =
                                when {
                                    s != null -> s.describe
                                    state.lookingForSuggestion -> "Looking…"
                                    else -> "Nothing found online"
                                },
                            logo = problem.kind == PosterKind.LOGO,
                        )
                    }
                }
                val buttons: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(problem.title, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold)
                        Text(problem.subtitle, fontSize = BenflixType.small, color = Color(0xFFA7A9B0))
                        Text(problem.problem, fontSize = BenflixType.body, color = Color(0xFFFF8A8A))
                        FixButton(
                            text = if (state.saving) "Saving…" else "Use this one",
                            white = true,
                            enabled = state.suggestion != null && !state.saving,
                            onClick = viewModel::use,
                            modifier = Modifier.focusRequester(useFocus),
                        )
                        FixButton("Pick another", onClick = viewModel::pickAnother)
                        FixButton("Skip", onClick = viewModel::skip)
                        FixButton("Leave it", onClick = viewModel::leaveAlone)
                        Text(
                            "Skip asks again next time. Leave it never asks about this one again.",
                            fontSize = BenflixType.tag,
                            color = Color(0xFF8D9098),
                        )
                    }
                }
                if (isTv) {
                    Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        pictures()
                        Box(Modifier.width(320.dp)) { buttons() }
                    }
                } else {
                    pictures()
                    buttons()
                }
            }
        }
    }
}

@Composable
private fun Picture(
    url: String?,
    shape: Float,
    width: androidx.compose.ui.unit.Dp,
    caption: String,
    logo: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .width(width)
                    .aspectRatio(shape)
                    .clip(BenflixShapes.Card)
                    .background(Color(0xFF17181C)),
        ) {
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = if (logo) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("None", fontSize = BenflixType.small, color = Color(0xFF8D9098))
            }
        }
        Text(
            text = caption,
            fontSize = BenflixType.tag,
            color = Color(0xFFA7A9B0),
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(width),
        )
    }
}

@Composable
private fun FixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    white: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonDefaults.shape(BenflixShapes.Pill),
        colors =
            ButtonDefaults.colors(
                containerColor = if (white) Color.White else Color(0xFF1E2026),
                contentColor = if (white) Color.Black else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = BenflixType.body)
    }
}
