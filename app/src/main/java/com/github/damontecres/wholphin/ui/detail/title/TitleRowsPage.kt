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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.nav.ReturnSpot
import com.github.damontecres.wholphin.ui.nav.underRail
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
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

/**
 * Where Down from the buttons should land. Ben 2026-11-22 (TV photo, One Piece): Down from Resume went past
 * the season buttons straight onto the 1-25 group buttons. The first row can set this (a show's season
 * buttons do, landing on the season you are on); when nothing is set Down works as before.
 */
class TitleDownTarget {
    var enter: (() -> Boolean)? = null
}

val LocalTitleDownTarget = staticCompositionLocalOf<TitleDownTarget?> { null }

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
    // TV: the same left edge as Home and the Movies/Shows pages (Ben 2026-11-20: it started further right)
    val side = if (isTv) TitleTvSide else BenflixSpace.tvEdge
    val listState = rememberLazyListState()
    val coroutines = rememberCoroutineScope()
    val firstButton = remember { FocusRequester() }
    var focusedButton by remember { mutableStateOf<Int?>(null) }
    var storyFocused by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    // A very bright picture: the words get a faint edge so they stand out
    var brightArt by remember { mutableStateOf(false) }
    val baseText = LocalTextStyle.current
    val headText = remember(brightArt, baseText) { if (brightArt) baseText.copy(shadow = WORD_EDGE) else baseText }
    val downTarget = remember { TitleDownTarget() }
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(60)
            // Back from a title opened from this page: the remote goes back to that card, not to Play
            if (!ReturnSpot.settle()) firstButton.tryRequestFocus()
        }
    }
    // Once the rows cover the picture it dims, so the row names stay easy to read
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 } }
    val pictureAlpha by animateFloatAsState(if (scrolled) .3f else 1f, tween(250), label = "titlePicture")
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BenflixColors.Tray)) {
        val h = maxHeight
        val w = maxWidth
        // (under the side menu too, Ben 2026-11-23: no hard line where the menu ends)
        Box(
            Modifier
                .fillMaxSize()
                .underRail()
                .background(BenflixColors.Tray)
                .graphicsLayer { alpha = pictureAlpha },
        ) {
            TitlePictureView(picture, wide = true, soft = true, onBright = { brightArt = it })
        }
        // Ben 2026-11-18: moving along the buttons (Play, Trailer, My List, More) made the page bob up and down:
        // each button asked the list to scroll it into view, then the "back to the top" scroll pulled it up
        // again. While a button has the focus the list doesn't scroll for it at all; the top just stays put.
        val defaultSpec = LocalBringIntoViewSpec.current
        // Ben 2026-11-18: About (words only, nothing to press) sat below the last row and the remote could never
        // scroll down to it. Now reaching the last row you can press scrolls the page right to the end.
        var focusedRow by remember { mutableStateOf<Int?>(null) }
        val lastPressable = sections.indexOfLast { !it.info }
        // Ben 2026-11-22: on anime pages the last row you can press holds three rows (Watch order: movies,
        // Fan genres, Characters and voices). Jumping to the end as soon as it had the focus pushed the
        // movies off the top, so the focus sat on something you couldn't see. Now it only jumps to the end
        // when the thing you're on still fits on screen after the jump; otherwise the page scrolls normally.
        val sectionHeights = remember { mutableStateMapOf<Int, Int>() }
        var lastSection by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var focusedGap by remember { mutableStateOf(0) }
        val viewport = with(LocalDensity.current) { h.roundToPx() }
        val bottomPad = with(LocalDensity.current) { 56.dp.roundToPx() }
        val tail = (lastPressable + 1..sections.lastIndex).sumOf { sectionHeights[it] ?: 0 } + bottomPad
        val fitsAtEnd = focusedGap <= viewport / 2 && focusedGap + tail <= viewport
        val toEnd = focusedRow != null && focusedRow == lastPressable && lastPressable < sections.lastIndex && fitsAtEnd
        LaunchedEffect(toEnd) {
            if (toEnd) listState.animateScrollToItem(sections.size)
        }
        // Ben 2026-11-21 (TV photo): Down from the buttons onto Cast and crew left the row half off the screen.
        // The "hold still" answer was only refreshed when the page next redrew, so the row's own "show me"
        // request was often still answered with "stay put". It reads what has the focus right now instead.
        val lastRow = rememberUpdatedState(sections.lastIndex)
        val lastPress = rememberUpdatedState(lastPressable)
        val endFits = rememberUpdatedState(fitsAtEnd)
        val holdOnButtons =
            remember(defaultSpec) {
                HoldStillSpec(defaultSpec) {
                    val row = focusedRow
                    focusedButton != null || storyFocused || (row != null && row == lastPress.value && row < lastRow.value && endFits.value)
                }
            }
        CompositionLocalProvider(LocalBringIntoViewSpec provides holdOnButtons, LocalTitleDownTarget provides downTarget) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 56.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "head", contentType = "head") {
                    CompositionLocalProvider(LocalTextStyle provides headText) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    // Ben's "fresh" pick: the first row (seasons, or a movie's first row) peeks at the bottom
                                    .heightIn(min = h * .84f)
                                    // Ben 2026-11-23: the title sits up in the top left corner, level with the clock
                                    .padding(start = side, end = side, top = TITLE_TOP),
                        ) {
                            // The title box: always the same size and spot, so the page never moves
                            // Ben 2026-11-23 (logo plan): the full logo, any shape, in a slightly bigger fixed box
                            TitleBox(title = title, wide = true, height = 100.dp, width = 340.dp, anyShape = true)
                            Spacer(Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.widthIn(max = w * .62f),
                            ) {
                                InfoLine(info, null)
                                QualityChips()
                            }
                            // Ben 2026-11-24: the age rating, scores and "94% for you" sit in one row under the year line
                            // (they were in the top right, under the clock)
                            TitleScoreRow(rating = rating, scores = scores)
                            infoExtra?.let { Box(Modifier.widthIn(max = w * .6f)) { it() } }
                            if (!story.isNullOrBlank() || sections.any { it.info }) {
                                StoryOpener(
                                    story = story,
                                    wide = true,
                                    onOpen = { showDetails = true },
                                    onFocus = {
                                        storyFocused = it
                                        if (it) coroutines.launch { listState.backToTop() }
                                    },
                                    modifier = Modifier.widthIn(max = w * .5f),
                                )
                            }
                            Column(Modifier.padding(top = 4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier =
                                        Modifier
                                            .focusRestorer(firstButton)
                                            .onPreviewKeyEvent {
                                                it.type == KeyEventType.KeyDown &&
                                                    it.key == Key.DirectionDown &&
                                                    downTarget.enter?.invoke() == true
                                            },
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
                }
                sections.forEachIndexed { index, section ->
                    item(key = "row-${section.name}", contentType = "row") {
                        // (the rows' own sideways scrolling stays as it was)
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 22.dp)
                                    .onSizeChanged { sectionHeights[index] = it.height }
                                    .then(
                                        if (index == lastPressable) {
                                            Modifier
                                                .onGloballyPositioned { lastSection = it }
                                                .onFocusedBoundsChanged { focused ->
                                                    val box = lastSection
                                                    if (focused != null && box != null && box.isAttached && focused.isAttached) {
                                                        focusedGap = (box.size.height - box.localPositionOf(focused, Offset.Zero).y).toInt()
                                                    }
                                                }
                                        } else {
                                            Modifier
                                        },
                                    ).onFocusChanged {
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
        val topAlpha by animateFloatAsState(if (scrolled) 0f else 1f, tween(250), label = "titleTop")
        // Down into the rows: the top shrinks to a small title and the facts (a plain fade, no blur)
        if (scrolled || topAlpha < 1f) {
            MiniTop(
                title = title,
                info = info,
                side = side,
                modifier = Modifier.align(Alignment.TopStart).graphicsLayer { alpha = 1f - topAlpha },
            )
        }
    }
    if (showDetails) {
        TitleDetailsPanel(
            title = title,
            story = story,
            details = sections.filter { it.info },
            onDismiss = { showDetails = false },
        )
    }
}

/**
 * The age rating and scores as bare icons (Ben 2026-11-24): one row under the year line, the same as on Home,
 * Movies, Shows and Anime. The "for you" percent was removed (Ben 2026-11-26).
 */
@Composable
private fun TitleScoreRow(
    rating: String?,
    scores: List<StripPart>,
) {
    if (rating.isNullOrBlank() && scores.none { !it.value.isNullOrBlank() }) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScoreIcons(rating = rating, scores = scores)
    }
}

/** The small top once you've gone down into the rows: a small logo (or the name) and the facts */
@Composable
private fun MiniTop(
    title: String,
    info: String,
    side: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to BenflixColors.Tray.copy(alpha = .92f),
                        .7f to BenflixColors.Tray.copy(alpha = .7f),
                        1f to Color.Transparent,
                    ),
                ).padding(start = side, end = side, top = 14.dp, bottom = 18.dp),
    ) {
        TitleBox(title = title, wide = true, height = 32.dp, width = 180.dp)
        if (info.isNotBlank()) {
            Text(
                text = info,
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The story, 3 lines (5 on phones), with "Story and details ›" under it so it's clear there's more.
 * TV: Up from the buttons lands on it and OK opens the panel. Phones: tap. Browser: click.
 */
@Composable
private fun StoryOpener(
    story: String?,
    wide: Boolean,
    onOpen: () -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BenflixShapes.Card
    Surface(
        onClick = onOpen,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = Color.White.copy(alpha = .1f),
                focusedContentColor = BenflixColors.Text,
                pressedContainerColor = Color.White.copy(alpha = .1f),
                pressedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.offset(x = (-8).dp).onFocusChanged { onFocus(it.isFocused) },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (!story.isNullOrBlank()) {
                Text(
                    text = story,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    lineHeight = BenflixType.body * 1.4f,
                    maxLines = if (wide) 3 else 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Story and details ›",
                color = BenflixColors.Text,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** The faint edge on the words over a very bright picture (a plain text shadow, cheap on weak TVs) */
private val WORD_EDGE = Shadow(color = Color.Black.copy(alpha = .45f), offset = Offset(0f, 1.5f), blurRadius = 3f)

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
    var showDetails by remember { mutableStateOf(false) }
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
                    ) { TitlePictureView(picture, wide = false, soft = true) }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = side, end = side),
                    ) {
                        // Upright phones: the title box gets the full width (Ben 2026-11-24: the score box beside it moved
                        // under the year line)
                        TitleBox(title = title, wide = false, width = 220.dp, anyShape = true)
                        InfoLine(info, null)
                        TitleScoreRow(rating = rating, scores = scores)
                        QualityChips()
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
                        if (!story.isNullOrBlank() || sections.any { it.info }) {
                            StoryOpener(story = story, wide = false, onOpen = { showDetails = true }, onFocus = {})
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
    if (showDetails) {
        TitleDetailsPanel(
            title = title,
            story = story,
            details = sections.filter { it.info },
            onDismiss = { showDetails = false },
        )
    }
}

/** Scroll back to the very top (only when it isn't there already, so nothing moves for no reason) */
private suspend fun LazyListState.backToTop() {
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) animateScrollToItem(0)
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

/** How far down the title box starts on TV: level with the clock (the page goes up behind the header) */
private val TITLE_TOP = 18.dp
