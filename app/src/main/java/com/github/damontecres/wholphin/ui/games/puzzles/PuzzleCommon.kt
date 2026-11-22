package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.games.FitScreen
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixType
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
val PuzzleAqua = Color(0xFF3FE0E8)
private val BarFill = Color(0xFF1A1E38)

/** The game's own two colours (from MovieGame), for the glow behind it and the scoreboard bar */
val LocalPuzzleAccent = staticCompositionLocalOf { listOf(Color(0xFF7E22CE), Color(0xFF3B0764)) }

/** One right answer in a run, for the end screen's "Your run" posters */
data class RunPick(
    val title: PuzzleTitle,
    /** What shows on its corner, like "+300" or its year */
    val badge: String,
)

/**
 * A ring around a picture that empties as time runs out ([left] 1 = full time, 0 = none), so it's
 * always clear how long is left (Ben's pick: the new game look).
 */
fun Modifier.timerRing(
    left: Float,
    color: Color = PuzzleAqua,
    corner: Dp = 12.dp,
    width: Dp = 5.dp,
): Modifier =
    drawWithContent {
        drawContent()
        val w = width.toPx()
        val path =
            Path().apply {
                addRoundRect(RoundRect(Rect(w / 2, w / 2, size.width - w / 2, size.height - w / 2), CornerRadius(corner.toPx())))
            }
        drawPath(path, Color.White.copy(alpha = .14f), style = Stroke(w))
        val measure = PathMeasure()
        measure.setPath(path, false)
        val part = Path()
        measure.getSegment(0f, measure.length * left.coerceIn(0f, 1f), part, true)
        drawPath(part, if (left < .25f) Color(0xFFFF7A7A) else color, style = Stroke(w, cap = StrokeCap.Round))
    }

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
    val accent = LocalPuzzleAccent.current
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(PuzzleBg)
                // A soft glow of the game's own colour from the top
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(accent.first().copy(alpha = .38f), Color.Transparent),
                            center = Offset(size.width / 2, -size.height * .1f),
                            radius = size.width * .6f,
                        ),
                    )
                }.padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 20.dp else 12.dp),
    ) {
        FitScreen(isTv) { content() }
    }
}

/**
 * The scoreboard bar along the top: the game's colour square and name, then the round, points and
 * best as chips ([line] split on " · "), and dots for guesses left when the game has them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PuzzleHeader(
    title: String,
    line: String,
    isTv: Boolean,
    guessesLeft: Int? = null,
    guessesOf: Int = 3,
) {
    val accent = LocalPuzzleAccent.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BarFill.copy(alpha = .78f))
                .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(16.dp))
                .padding(horizontal = if (isTv) 18.dp else 12.dp, vertical = if (isTv) 10.dp else 8.dp),
    ) {
        Box(
            Modifier
                .size(if (isTv) 30.dp else 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(accent)),
        )
        Text(
            title,
            fontFamily = BenflixFont,
            fontWeight = FontWeight.Bold,
            fontSize = if (isTv) 22.sp else 18.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            line
                .split("·")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { BarChip(it, isTv) }
            if (guessesLeft != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = .07f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Guesses", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCFD3F0))
                    repeat(guessesOf) { i ->
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (i < guessesLeft) Color(0xFFFF7A7A) else Color(0xFF3A3E5C)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChip(
    text: String,
    isTv: Boolean,
) {
    Text(
        text,
        fontSize = if (isTv) 15.sp else 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (text.contains("best", ignoreCase = true)) PuzzleGold else Color.White,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = .07f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Hints as gold labels under a picture */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HintChips(hints: List<String>) {
    if (hints.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hints.forEach {
            Text(
                it,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFD36B),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF3B2F10))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
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
        overflow = TextOverflow.Ellipsis,
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
                                containerColor = if (isRight) Color(0xFF2E8B57) else BarFill,
                                contentColor = Color.White,
                                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
                                disabledContainerColor = Color(0xFF161618),
                                disabledContentColor = Color.White.copy(alpha = .3f),
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = if (isTv) 52.dp else 46.dp)
                                .then(if (takeFocus) Modifier.focusRequester(first) else Modifier),
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // The number on the remote's or keyboard's keys that picks it
                            Text(
                                "${choices.indexOf(c) + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = .12f))
                                        .padding(top = 3.dp),
                            )
                            Text(
                                c,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
 * The end of a run (Ben's pick: the better end screen): the score big, a NEW BEST label, the posters of
 * every title you got right with what each earned, and "The one that got you" with Open and My List.
 */
@Composable
fun PuzzleOver(
    title: String,
    body: String,
    answer: PuzzleTitle?,
    actions: PuzzleActions,
    isTv: Boolean,
    onAgain: () -> Unit,
    run: List<RunPick> = emptyList(),
    score: Int? = null,
    newBest: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    val scope = rememberCoroutineScope()
    var added by remember(answer) { mutableStateOf(false) }
    val left: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (newBest) Text("NEW BEST!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PuzzleAqua, letterSpacing = 1.5.sp)
            if (score != null) {
                Text(
                    "%,d".format(score),
                    fontFamily = BenflixFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTv) 56.sp else 44.sp,
                    color = Color.White,
                )
            } else {
                Text(title, fontFamily = BenflixFont, fontWeight = FontWeight.Bold, fontSize = BenflixType.pageTitle, color = Color.White)
            }
            Text(body, fontSize = 17.sp, lineHeight = 24.sp, color = Color.White.copy(alpha = .85f))
            Button(
                onClick = onAgain,
                modifier = Modifier.focusRequester(focus),
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) { Text("Play again") }
            if (answer != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(BarFill)
                            .padding(12.dp),
                ) {
                    PuzzlePoster(answer.poster, answer.name, Modifier.width(if (isTv) 70.dp else 56.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("The one that got you", fontSize = 13.sp, color = Color.White.copy(alpha = .6f))
                        Text(
                            answer.label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                actions.open(answer)
                            }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text(if (answer.isSeries) "Open show" else "Open movie") }
                            Button(
                                onClick = {
                                    if (!added) scope.launch { added = actions.addToMyList(answer) }
                                },
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                            ) { Text(if (added) "✓ On My List" else "+ My List") }
                        }
                    }
                }
            }
        }
    }
    val posters: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your run", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .6f))
            run.takeLast(if (isTv) 8 else 6).chunked(if (isTv) 4 else 3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pick ->
                        Box(Modifier.weight(1f)) {
                            PuzzlePoster(pick.title.poster, pick.title.name, Modifier.fillMaxWidth())
                            Text(
                                pick.badge,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PuzzleGreen,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = .75f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    repeat((if (isTv) 4 else 3) - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (isTv) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth().padding(top = 16.dp),
        ) {
            Box(Modifier.weight(if (run.isEmpty()) 1f else .9f)) { left() }
            if (run.isNotEmpty()) Box(Modifier.weight(1.1f)) { posters() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            left()
            if (run.isNotEmpty()) posters()
        }
    }
}

/**
 * The "not enough to play" line. It only points at the anime switch when the switch is narrowing
 * the titles (on "Anime: mixed in" the switch can't help, so it isn't mentioned).
 */
fun notEnough(
    what: String,
    mode: AnimeMode,
): String =
    when (mode) {
        AnimeMode.MIXED -> "$what Add more to Tower, then try again."
        AnimeMode.ONLY -> "$what There isn't enough anime yet. Set the game's page to \"Anime: mixed in\"."
        AnimeMode.NONE -> "$what Set the game's page to \"Anime: mixed in\"."
    }
