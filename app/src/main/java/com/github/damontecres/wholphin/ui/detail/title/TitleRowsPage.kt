package com.github.damontecres.wholphin.ui.detail.title

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Glowfin title pages, D1 "Wholphin classic" (Ben picked 2026-11-18, sheet glowfin-ui/ui-9-title-pages.html).
 *
 * TV (and phones on their side): the still picture fills the screen, faded on the left and at the bottom.
 * Over it: the title, the info line with the scores, 3 lines of the story, the buttons. Under the buttons
 * every part of the page is a plain row, one after another (a show's season chips and episodes first,
 * then Cast and crew, More like this, Ask for similar...). It is ONE list, so Down always moves to the
 * next row and Up walks back to the buttons; going back to the buttons brings the whole top back.
 * The rows are drawn only as they come into view, so a weak TV only builds what is on screen.
 *
 * Phones: the picture across the top, the words, a full width Play button, a row of icon buttons, the
 * story, then the same rows. A slim Play bar shows once Play scrolls away.
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WideRowsTitlePage(
    title: String,
    accent: Color,
    picture: TitlePicture,
    info: String,
    rating: String?,
    scores: List<StripPart>,
    story: String?,
    infoExtra: (@Composable () -> Unit)?,
    originalTitle: String?,
    buttons: List<TitleButton>,
    sections: List<TitleTab>,
    statusLine: String?,
    big: Boolean,
    modifier: Modifier,
) {
    val isTv = rememberIsTv()
    val side = BenflixSpace.tvEdge
    val listState = rememberLazyListState()
    val coroutines = rememberCoroutineScope()
    val firstButton = remember { FocusRequester() }
    var focusedButton by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(60)
            firstButton.tryRequestFocus()
        }
    }
    // Once the rows cover the picture it dims, so the row names stay easy to read
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 } }
    val pictureAlpha by animateFloatAsState(if (scrolled) .3f else 1f, tween(250), label = "titlePicture")
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BenflixColors.Tray)) {
        val h = maxHeight
        val w = maxWidth
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = pictureAlpha }) {
            TitlePictureView(picture, wide = true)
        }
        // Ben 2026-11-18: moving along the buttons (Play, Trailer, My List, More) made the page bob up and down:
        // each button asked the list to scroll it into view, then the "back to the top" scroll pulled it up
        // again. While a button has the focus the list doesn't scroll for it at all; the top just stays put.
        val defaultSpec = LocalBringIntoViewSpec.current
        // Ben 2026-11-18: About (words only, nothing to press) sat below the last row and the remote could never
        // scroll down to it. Now reaching the last row you can press scrolls the page right to the end.
        var focusedRow by remember { mutableStateOf<Int?>(null) }
        val lastPressable = sections.indexOfLast { !it.info }
        val toEnd = focusedRow != null && focusedRow == lastPressable && lastPressable < sections.lastIndex
        LaunchedEffect(toEnd) {
            if (toEnd) listState.animateScrollToItem(sections.size)
        }
        val onButtons = rememberUpdatedState(focusedButton != null || toEnd)
        val holdOnButtons = remember(defaultSpec) { HoldStillSpec(defaultSpec) { onButtons.value } }
        CompositionLocalProvider(LocalBringIntoViewSpec provides holdOnButtons) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 56.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "head", contentType = "head") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = h * .64f)
                                .padding(start = side, end = side, top = h * .08f),
                    ) {
                        Column(Modifier.widthIn(max = w * .55f)) {
                            TitleWords(title = title, originalTitle = originalTitle, accent = accent, wide = true)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.widthIn(max = w * .75f),
                        ) {
                            InfoLine(info, rating)
                            ScoreLine(scores)
                            infoExtra?.invoke()
                        }
                        if (!story.isNullOrBlank()) {
                            Text(
                                text = story,
                                color = BenflixColors.Soft,
                                fontSize = BenflixType.body,
                                lineHeight = BenflixType.body * 1.4f,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = w * .5f),
                            )
                        }
                        Column(Modifier.padding(top = 6.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.focusRestorer(firstButton),
                            ) {
                                buttons.forEachIndexed { index, b ->
                                    TitleButtonView(
                                        button = b,
                                        primary = index == 0,
                                        big = big,
                                        onFocus = {
                                            if (it) {
                                                focusedButton = index
                                                // Back up on the buttons: show the whole top again
                                                coroutines.launch { listState.backToTop() }
                                            } else if (focusedButton == index) {
                                                focusedButton = null
                                            }
                                        },
                                        modifier = if (index == 0) Modifier.focusRequester(firstButton) else Modifier,
                                    )
                                }
                            }
                            val focused = focusedButton?.let { buttons.getOrNull(it) }
                            val line = focused?.off ?: focused?.note ?: statusLine
                            Box(Modifier.height(26.dp).padding(top = 8.dp)) {
                                if (!line.isNullOrBlank()) {
                                    Text(
                                        text = line,
                                        color = if (focused?.off != null) BenflixColors.Soft else BenflixColors.Muted,
                                        fontSize = BenflixType.small,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                sections.forEachIndexed { index, section ->
                    item(key = "row-${section.name}", contentType = "row") {
                        // (the rows' own sideways scrolling stays as it was)
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 22.dp)
                                    .onFocusChanged {
                                        if (it.hasFocus) {
                                            focusedRow = index
                                        } else if (focusedRow == index) {
                                            focusedRow = null
                                        }
                                    },
                            ) {
                                if (section.heading) RowHeading(section.name, section.note, side)
                                section.content(TitleTabScope(side = side, wide = true))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Scrolls like [base], except not at all while [hold] says so */
@OptIn(ExperimentalFoundationApi::class)
private class HoldStillSpec(
    private val base: BringIntoViewSpec,
    private val hold: () -> Boolean,
) : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float>
        get() = base.scrollAnimationSpec

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = if (hold()) 0f else base.calculateScrollDistance(offset, size, containerSize)
}

@Composable
internal fun PhoneRowsTitlePage(
    title: String,
    accent: Color,
    picture: TitlePicture,
    info: String,
    rating: String?,
    scores: List<StripPart>,
    story: String?,
    infoExtra: (@Composable () -> Unit)?,
    originalTitle: String?,
    buttons: List<TitleButton>,
    sections: List<TitleTab>,
    statusLine: String?,
    big: Boolean,
    modifier: Modifier,
) {
    val side = BenflixSpace.phoneEdge
    val listState = rememberLazyListState()
    var playGone by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BenflixColors.Tray)) {
        val h = maxHeight
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "head", contentType = "head") {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(h * .30f),
                    ) { TitlePictureView(picture, wide = false) }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = side, end = side),
                    ) {
                        TitleWords(title = title, originalTitle = originalTitle, accent = accent, wide = false)
                        InfoLine(info, rating)
                        ScoreLine(scores)
                        infoExtra?.invoke()
                        buttons.firstOrNull()?.let { b ->
                            TitleButtonView(
                                button = b,
                                primary = true,
                                big = big,
                                fill = true,
                                onFocus = {},
                                onOffTap = { why = it },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { playGone = it.positionInRoot().y + it.size.height < 0f },
                            )
                        }
                        if (buttons.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                buttons.drop(1).forEach { b ->
                                    PhoneIconButton(b, onOffTap = { why = it })
                                }
                            }
                        }
                        val line = why ?: statusLine
                        if (!line.isNullOrBlank()) {
                            Text(text = line, color = BenflixColors.Muted, fontSize = BenflixType.small)
                        }
                        if (!story.isNullOrBlank()) {
                            Text(
                                text = story,
                                color = BenflixColors.Soft,
                                fontSize = BenflixType.body,
                                lineHeight = BenflixType.body * 1.4f,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            sections.forEach { section ->
                item(key = "row-${section.name}", contentType = "row") {
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                        if (section.heading) RowHeading(section.name, section.note, side)
                        section.content(TitleTabScope(side = side, wide = false))
                    }
                }
            }
        }
        // The slim Play bar once Play has scrolled away
        val play = buttons.firstOrNull()
        if (play != null && playGone) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(BenflixColors.Panel)
                        .navigationBarsPadding()
                        .padding(horizontal = side, vertical = 10.dp),
            ) {
                Text(
                    text = title,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TitleButtonView(button = play, primary = true, big = false, onFocus = {}, onOffTap = { why = it })
            }
        }
    }
}

/** Scroll back to the very top (only when it isn't there already, so nothing moves for no reason) */
private suspend fun LazyListState.backToTop() {
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) animateScrollToItem(0)
}

/** "IMDb 7.8   Rotten Tomatoes 91%": quiet label, bright number */
@Composable
private fun ScoreLine(scores: List<StripPart>) {
    if (scores.isEmpty()) return
    val text =
        remember(scores) {
            buildAnnotatedString {
                scores.forEachIndexed { i, p ->
                    if (i > 0) append("   ")
                    withStyle(SpanStyle(color = BenflixColors.Muted, fontWeight = FontWeight.SemiBold)) { append(p.label) }
                    if (p.value != null) {
                        append(" ")
                        withStyle(SpanStyle(color = BenflixColors.Text, fontWeight = FontWeight.ExtraBold)) { append(p.value) }
                    }
                }
            }
        }
    Text(text = text, fontSize = BenflixType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A row's name in the title font, with an optional quiet note beside it */
@Composable
private fun RowHeading(
    name: String,
    note: String?,
    side: Dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = side, end = side, bottom = 10.dp),
    ) {
        Text(
            text = name,
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontFamily = GlowfinDisplayFont,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
