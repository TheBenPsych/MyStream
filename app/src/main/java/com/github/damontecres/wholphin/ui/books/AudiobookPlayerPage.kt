package com.github.damontecres.wholphin.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.AudiobookNow
import com.github.damontecres.wholphin.services.AudiobookService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AudiobookViewModel
    @Inject
    constructor(
        val audiobooks: AudiobookService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        fun open() = navigationManager.navigateTo(Destination.AudiobookPlayer)
    }

/**
 * The audiobook player (approved design, like Music's Now playing): the cover on the left, the book, the
 * chapter, a lime line with the time, back 15 / play / ahead 30, speed and the sleep timer; the chapters
 * in a column on the right (TV and tablets) or below (phones).
 */
@Composable
fun AudiobookPlayerPage(
    modifier: Modifier = Modifier,
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val now by viewModel.audiobooks.now.collectAsState()
    val n = now
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    if (n == null) {
        Column(Modifier.fillMaxSize().padding(edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nothing playing", color = BenflixColors.Text, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold)
            Text("Pick an audiobook on the Books door.", color = BenflixColors.Soft, fontSize = BenflixType.body)
            MusicButton("Books", BenflixIcon.BOOK, onClick = { viewModel.navigationManager.navigateTo(Destination.BooksDoor) })
        }
        return
    }
    var speedOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    if (speedOpen) {
        ChoiceDialog(
            title = "Speed",
            choices = AudiobookService.SPEEDS.map { speedLabel(it) },
            picked = AudiobookService.SPEEDS.indexOfFirst { kotlin.math.abs(it - n.speed) < 0.01f },
            onDismiss = { speedOpen = false },
            onPick = {
                speedOpen = false
                viewModel.audiobooks.setSpeed(AudiobookService.SPEEDS[it])
            },
        )
    }
    if (sleepOpen) {
        val options = listOf<Int?>(15, 30, 45, 60, 0, null)
        ChoiceDialog(
            title = "Sleep timer",
            choices = options.map { sleepLabel(it) },
            picked = -1,
            note = "When the timer ends the sound fades out." + (if (!isTv) " Shake the phone for 15 more minutes." else ""),
            onDismiss = { sleepOpen = false },
            onPick = {
                sleepOpen = false
                viewModel.audiobooks.sleep(options[it])
            },
        )
    }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.tryRequestFocus() }
    if (wide) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            modifier = modifier.fillMaxSize().padding(start = edge, end = edge, top = 12.dp, bottom = 24.dp),
        ) {
            BookCoverImage(n.cover, n.name, Modifier.fillMaxHeight(0.85f).aspectRatio(BOOK_RATIO), audio = true)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                PlayerInfo(n, viewModel, playFocus, onSpeed = { speedOpen = true }, onSleep = { sleepOpen = true })
            }
            Chapters(n, viewModel, Modifier.width(320.dp).fillMaxHeight())
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            item(key = "cover") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BookCoverImage(n.cover, n.name, Modifier.width(220.dp).aspectRatio(BOOK_RATIO), audio = true)
                }
            }
            item(key = "info") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PlayerInfo(n, viewModel, playFocus, onSpeed = { speedOpen = true }, onSleep = { sleepOpen = true })
                }
            }
            item(key = "chapters-title") { ChaptersTitle(n) }
            itemsIndexed(n.chapters, key = { i, _ -> "c$i" }) { i, c ->
                ChapterRow(i, c.title, c.seconds, i == n.chapter, onClick = { viewModel.audiobooks.chapter(i) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerInfo(
    n: AudiobookNow,
    viewModel: AudiobookViewModel,
    playFocus: FocusRequester,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
) {
    val svc = viewModel.audiobooks
    Text("AUDIOBOOK", color = BenflixColors.Books, fontSize = BenflixType.tag, fontWeight = FontWeight.ExtraBold)
    Text(
        n.name,
        color = BenflixColors.Text,
        fontSize = BenflixType.hero,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        listOf(
            n.author,
            n.narrator
                .takeIf {
                    it.isNotBlank()
                }?.let { "Read by $it" },
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("  ·  "),
        color = BenflixColors.Soft,
        fontSize = BenflixType.lead,
        maxLines = 2,
    )
    if (n.chapterTitle.isNotBlank()) {
        Text(n.chapterTitle, color = BenflixColors.Text, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, maxLines = 2)
    }
    // How far through this chapter, and the whole book
    val inChapter = (n.position - n.chapterStart).coerceAtLeast(0.0)
    val chapterLen = n.chapterLength.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(BenflixColors.Line, RoundedCornerShape(3.dp))) {
            Box(
                Modifier
                    .fillMaxWidth((inChapter / chapterLen).toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(BenflixColors.Books, RoundedCornerShape(3.dp)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(clockTime(inChapter.toLong()), color = BenflixColors.Muted, fontSize = BenflixType.small)
            Box(Modifier.weight(1f))
            Text(
                "-" +
                    clockTime(
                        (chapterLen - inChapter).toLong(),
                    ) + "  ·  " + hoursMinutes((n.total - n.position).toInt().coerceAtLeast(0)) +
                    " left in the book",
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
            )
        }
    }
    n.problem?.let { Text(it, color = BenflixColors.Warning, fontSize = BenflixType.small) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MusicButton(label = "15", icon = BenflixIcon.BACK15, onClick = svc::back, big = true)
        MusicButton(
            label = null,
            icon = if (n.playing) BenflixIcon.PAUSE else null,
            filledPlay = !n.playing,
            primary = true,
            big = true,
            onClick = svc::playPause,
            modifier = Modifier.focusRequester(playFocus),
        )
        MusicButton(label = "30", icon = BenflixIcon.AHEAD30, onClick = svc::ahead, big = true)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MusicButton("Speed ${speedLabel(n.speed)}", null, onClick = onSpeed)
        MusicButton(sleepNow(n), BenflixIcon.MOON, onClick = onSleep)
        MusicButton("Stop", BenflixIcon.STOP, onClick = {
            svc.stopSoon()
            viewModel.navigationManager.goBack()
        })
    }
}

@Composable
private fun ChaptersTitle(n: AudiobookNow) {
    Text(
        "Chapters (${n.chapters.size})",
        color = BenflixColors.Text,
        fontSize = BenflixType.rowName,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun Chapters(
    n: AudiobookNow,
    viewModel: AudiobookViewModel,
    modifier: Modifier,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (n.chapter - 2).coerceAtLeast(0))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChaptersTitle(n)
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(n.chapters, key = { i, _ -> "c$i" }) { i, c ->
                ChapterRow(i, c.title, c.seconds, i == n.chapter, onClick = { viewModel.audiobooks.chapter(i) })
            }
        }
    }
}

@Composable
private fun ChapterRow(
    i: Int,
    title: String,
    seconds: Int,
    current: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (current) BenflixColors.Raised else Color.Transparent,
                contentColor = if (current) BenflixColors.Books else BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("${i + 1}", fontSize = BenflixType.small, color = BenflixColors.Muted, modifier = Modifier.width(32.dp))
            Text(
                title,
                fontSize = BenflixType.body,
                fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (seconds > 0) Text(hoursMinutes(seconds), fontSize = BenflixType.tag, color = BenflixColors.Muted)
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<String>,
    picked: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    note: String? = null,
) {
    BasicDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 420.dp).padding(22.dp)) {
            Text(title, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            note?.let { Text(it, fontSize = BenflixType.small, color = BenflixColors.Soft) }
            choices.forEachIndexed { i, label ->
                MusicButton(
                    label,
                    if (i ==
                        picked
                    ) {
                        BenflixIcon.CHECK
                    } else {
                        null
                    },
                    primary = i == picked,
                    onClick = { onPick(i) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun speedLabel(s: Float): String = (if (s % 1f == 0f) "${s.toInt()}" else "$s".trimEnd('0')) + "x"

private fun sleepLabel(minutes: Int?): String =
    when (minutes) {
        null -> "Off"
        0 -> "End of this chapter"
        else -> "$minutes minutes"
    }

private fun sleepNow(n: AudiobookNow): String =
    when {
        n.fading -> {
            "Fading out"
        }

        n.sleepEndOfChapter -> {
            "Sleep: end of chapter"
        }

        n.sleepAt != null -> {
            val left = ((n.sleepAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
            "Sleep: ${left}m"
        }

        else -> {
            "Sleep timer"
        }
    }

/**
 * While an audiobook plays and you're anywhere outside Books (approved): a lime pill left of the clock on
 * TV with the book's name. OK opens the player.
 */
@Composable
fun AudiobookPill(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val now by viewModel.audiobooks.now.collectAsState()
    val n = now ?: return
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = viewModel::open,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.widthIn(max = 320.dp * scale),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        ) {
            BookCoverImage(n.cover, "", Modifier.size(width = 20.dp * scale, height = 28.dp * scale), audio = true)
            com.github.damontecres.wholphin.ui.components.BenflixIconImage(
                if (n.playing) BenflixIcon.HEADPHONES else BenflixIcon.PAUSE,
                color = BenflixColors.Books,
                size = 14.dp * scale,
            )
            Text(
                text = n.name,
                color = BenflixColors.Text,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Phones: a slim bar along the bottom while an audiobook plays, with play/pause and a thin lime line */
@Composable
fun AudiobookMiniBar(
    modifier: Modifier = Modifier,
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val now by viewModel.audiobooks.now.collectAsState()
    val n = now ?: return
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = viewModel::open,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.PanelHigh,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                BookCoverImage(n.cover, "", Modifier.size(width = 30.dp, height = 44.dp), audio = true)
                Column(Modifier.weight(1f)) {
                    Text(
                        n.name,
                        color = BenflixColors.Text,
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        n.chapterTitle,
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.tag,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MusicButton(null, BenflixIcon.BACK15, onClick = viewModel.audiobooks::back)
                MusicButton(
                    null,
                    if (n.playing) BenflixIcon.PAUSE else null,
                    filledPlay = !n.playing,
                    onClick = viewModel.audiobooks::playPause,
                )
            }
            Line(if (n.total > 0) (n.position / n.total).toFloat() else 0f, 2.dp)
        }
    }
}

@Composable
private fun Line(
    progress: Float,
    height: Dp,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(BenflixColors.Books))
    }
}
