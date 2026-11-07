package com.github.damontecres.wholphin.ui.games.puzzles

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.games.FitScreen
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

val PuzzleGreen = Color(0xFF46D37A)
val PuzzleGold = Color(0xFFFFC94D)
val PuzzleBg = Color(0xFF0B0C10)
val PuzzlePanel = Color(0xFF1C1C1F)

/** "Watch it after": open the answer's page, or add it to My List (Ben's pick) */
@Singleton
class PuzzleActions
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
        private val api: ApiClient,
    ) {
        fun open(title: PuzzleTitle) = navigationManager.navigateTo(Destination.MediaItem(title.id, title.kind))

        suspend fun addToMyList(title: PuzzleTitle): Boolean =
            try {
                api.userLibraryApi.markFavoriteItem(title.id)
                true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't add %s to My List", title.name)
                false
            }
    }

/** The dark game background, shrunk to fit on TV (a remote can't scroll) */
@Composable
fun PuzzleScreen(
    isTv: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(PuzzleBg)
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 20.dp else 12.dp),
    ) {
        FitScreen(isTv) { content() }
    }
}

@Composable
fun PuzzleHeader(
    title: String,
    line: String,
    isTv: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title.uppercase(), fontFamily = BenflixTitleFont, fontSize = if (isTv) 34.sp else 26.sp, color = Color.White)
        Text(line, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PuzzleGold, textAlign = TextAlign.Center)
    }
}

@Composable
fun PuzzleMessage(text: String) {
    Text(text, fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 32.dp))
}

/** A line saying if the last answer was right, kept the same height so nothing jumps */
@Composable
fun AnswerLine(
    text: String?,
    good: Boolean?,
) {
    Text(
        text = text ?: " ",
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color =
            if (good == true) {
                PuzzleGreen
            } else if (good == false) {
                BenflixRed
            } else {
                Color.White.copy(alpha = .8f)
            },
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}

/**
 * The answer buttons: 3 per row on TV, 2 on phones. Crossed-out names can't be picked again.
 * The first button takes the remote's focus whenever [focusKey] changes.
 */
@Composable
fun ChoiceGrid(
    choices: List<String>,
    isTv: Boolean,
    focusKey: Any,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    crossed: Set<String> = emptySet(),
    right: String? = null,
) {
    val first = remember(focusKey) { FocusRequester() }
    LaunchedEffect(focusKey) { first.tryRequestFocus() }
    val perRow = if (isTv) 3 else 2
    var focusedOnce = false
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        choices.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    val isCrossed = c in crossed
                    val isRight = c == right
                    val takeFocus = !focusedOnce && !isCrossed
                    if (takeFocus) focusedOnce = true
                    Button(
                        onClick = { if (!isCrossed) onPick(c) },
                        enabled = !isCrossed,
                        colors =
                            ButtonDefaults.colors(
                                containerColor = if (isRight) Color(0xFF2E8B57) else Color(0xFF2A2A2E),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black,
                                disabledContainerColor = Color(0xFF161618),
                                disabledContentColor = Color.White.copy(alpha = .3f),
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (takeFocus) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Text(
                            c,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** A poster with rounded corners (2:3) */
@Composable
fun PuzzlePoster(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(PuzzlePanel),
    ) {
        AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The end of a run: the score, your best, the answer's poster with "Watch it after" buttons
 * (open its page or add it to My List), and Play again.
 */
@Composable
fun PuzzleOver(
    title: String,
    body: String,
    answer: PuzzleTitle?,
    actions: PuzzleActions,
    isTv: Boolean,
    onAgain: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    val scope = rememberCoroutineScope()
    var added by remember(answer) { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(top = 16.dp),
    ) {
        if (answer != null) {
            PuzzlePoster(answer.poster, answer.name, Modifier.width(if (isTv) 180.dp else 110.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = BenflixTitleFont, fontSize = if (isTv) 40.sp else 30.sp, color = Color.White)
            Text(body, fontSize = 18.sp, lineHeight = 25.sp, color = Color.White.copy(alpha = .88f))
            Button(onClick = onAgain, modifier = Modifier.focusRequester(focus)) { Text("Play again") }
            if (answer != null) {
                Text("The last answer: ${answer.label}", fontSize = 15.sp, color = Color.White.copy(alpha = .7f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { actions.open(answer) }) { Text(if (answer.isSeries) "Open show" else "Open movie") }
                    Button(
                        onClick = {
                            if (!added) scope.launch { added = actions.addToMyList(answer) }
                        },
                    ) { Text(if (added) "✓ On My List" else "+ My List") }
                }
            }
        }
    }
}
