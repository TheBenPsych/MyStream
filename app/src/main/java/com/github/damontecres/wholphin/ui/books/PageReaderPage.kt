package com.github.damontecres.wholphin.ui.books

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The manga and comic reader (approved design), also used for PDF books ("fit the page"). One page (the
 * default), two pages side by side, or one long scroll, picked per series. Right to left for manga unless you
 * change it. OK (or a tap in the middle) shows the bar with the modes and a strip of small pages to slide
 * through. At the end of a chapter or volume: a "Next chapter" card that opens it after 5 seconds.
 *
 * Phones (Ben 2026-11-21): pinch to zoom, and double tap the middle to zoom in on a panel (double tap again to
 * zoom back out). While zoomed, dragging moves the page instead of turning it.
 *
 * [onClose] and [onOpen] are for showing the reader on top of another page (Downloads, with no internet): Back and
 * the next chapter then go through them instead of the app's own navigation.
 */
@Composable
fun PageReaderPage(
    id: String,
    book: Boolean,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onOpen: ((String) -> Unit)? = null,
    viewModel: PageReaderViewModel = hiltViewModel(key = "pages-$id"),
) {
    LaunchedEffect(id) { viewModel.load(id, book) }
    val close: () -> Unit = onClose ?: { viewModel.navigationManager.goBack() }
    val openNext: (String) -> Unit = onOpen ?: { viewModel.openNext(it) }
    DisposableEffect(Unit) {
        viewModel.screensaver.keepScreenOn(true)
        onDispose { viewModel.screensaver.keepScreenOn(false) }
    }
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    if (!state.loaded) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(if (book) "Opening the book…" else "Opening…", color = BenflixColors.Muted, fontSize = BenflixType.body)
        }
        return
    }
    if (state.problem != null || state.pages <= 0) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize().background(BenflixColors.Tray).padding(32.dp),
        ) {
            Text("Couldn't open it", color = BenflixColors.Text, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold)
            Text(state.problem ?: "It has no pages Glowfin can read.", color = BenflixColors.Soft, fontSize = BenflixType.body)
            MusicButton("Back", BenflixIcon.BACK_ARROW, onClick = close)
        }
        return
    }
    var page by rememberSaveable(id) { mutableIntStateOf(state.startPage) }
    var bar by remember { mutableStateOf(false) }
    var lastTurn by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    BackHandler(bar) { bar = false }
    // The TV screensaver after 15 minutes on the same page
    LaunchedEffect(page, lastTurn) {
        viewModel.screensaver.keepScreenOn(true)
        delay(IDLE_MS)
        viewModel.screensaver.start()
    }
    // Save the page a moment after each turn
    LaunchedEffect(page) {
        delay(SAVE_DELAY_MS)
        viewModel.save(page)
    }
    LaunchedEffect(bar) { if (!bar) rootFocus.tryRequestFocus() }
    val units = remember(state.mode, state.pages) { unitsFor(state.mode, state.pages) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenPx = with(density) { config.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.roundToPx() }
    val stripPx = with(density) { STRIP_MAX.roundToPx() }
    // How wide Tower makes each page: no bigger than it will show here. One upright page on a TV is only about
    // three quarters of the screen's height wide, so the TV gets a far smaller picture than the full screen width.
    val pageWidth =
        when (state.mode) {
            PageMode.TWO -> screenPx / 2
            PageMode.SCROLL -> minOf(screenPx, stripPx)
            PageMode.ONE -> if (screenPx > screenHeightPx) minOf(screenPx, screenHeightPx * 3 / 4) else screenPx
        }.coerceIn(480, 1600)
    val scope = rememberCoroutineScope()
    val zoom = remember(id) { ZoomState() }
    // A new page starts un-zoomed; the next three pages are fetched ahead
    LaunchedEffect(page, pageWidth) {
        if (state.mode != PageMode.SCROLL) zoom.reset()
        viewModel.preload(page + (if (state.mode == PageMode.TWO) 1 else 0), pageWidth)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        key(state.mode) {
            if (state.mode == PageMode.SCROLL) {
                val list = rememberLazyListState(initialFirstVisibleItemIndex = page.coerceIn(0, state.pages - 1))
                LaunchedEffect(list) {
                    snapshotFlow { list.firstVisibleItemIndex }.collect { i ->
                        if (i < state.pages) page = i
                        lastTurn++
                    }
                }
                LazyColumn(
                    state = list,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pinchZoom(zoom, enabled = !isTv, onPanY = { dy -> list.dispatchRawDelta(-dy) })
                            .graphicsLayer {
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = zoom.offset.x
                            }.onPreviewKeyEvent { e ->
                                if (bar || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionDown, Key.PageDown -> {
                                        scope.launch { list.animateScrollBy(config.screenHeightDp * density.density * 0.8f) }
                                        true
                                    }

                                    Key.DirectionUp, Key.PageUp -> {
                                        scope.launch { list.animateScrollBy(-config.screenHeightDp * density.density * 0.8f) }
                                        true
                                    }

                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        bar = true
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            }.pointerInput(isTv) {
                                detectTapGestures(
                                    onTap = { bar = !bar },
                                    onDoubleTap = if (isTv) null else ({ at: Offset -> zoom.toggle(at, size) }),
                                )
                            },
                ) {
                    items(state.pages, key = { it }) { n ->
                        // On a TV or tablet the strip stays a comfortable reading width in the middle
                        PageImage(viewModel, n, state.pdf, pageWidth, Modifier.widthIn(max = STRIP_MAX).fillMaxWidth(), scroll = true)
                    }
                    item(key = "end") {
                        EndCard(
                            state,
                            viewModel,
                            active = list.layoutInfo.visibleItemsInfo.any { it.key == "end" },
                            isTv = isTv,
                            onClose = close,
                            onOpen = openNext,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
            } else {
                val startUnit = units.indexOfFirst { page in it }.coerceAtLeast(0)
                val pager = rememberPagerState(initialPage = startUnit) { units.size + 1 }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.currentPage }.collect { u ->
                        units.getOrNull(u)?.let { page = it.first() }
                        if (u == units.size) viewModel.save(state.pages - 1, finished = true)
                        lastTurn++
                    }
                }
                val goNext = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(units.size)) } }
                val goPrev = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }
                HorizontalPager(
                    state = pager,
                    reverseLayout = state.rtl,
                    beyondViewportPageCount = 1,
                    // Zoomed in: a drag moves the page; it doesn't turn it
                    userScrollEnabled = zoom.scale <= 1.01f,
                    key = { it },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pinchZoom(zoom, enabled = !isTv)
                            .graphicsLayer {
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = zoom.offset.x
                                translationY = zoom.offset.y
                            }.onPreviewKeyEvent { e ->
                                if (bar || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionRight -> {
                                        if (state.rtl) goPrev() else goNext()
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        if (state.rtl) goNext() else goPrev()
                                        true
                                    }

                                    Key.PageDown, Key.MediaFastForward, Key.Spacebar -> {
                                        goNext()
                                        true
                                    }

                                    Key.PageUp, Key.MediaRewind -> {
                                        goPrev()
                                        true
                                    }

                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> {
                                        bar = true
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            },
                ) { u ->
                    if (u >= units.size) {
                        EndCard(
                            state,
                            viewModel,
                            active = pager.currentPage == units.size,
                            isTv = isTv,
                            onClose = close,
                            onOpen = openNext,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            val unit = units[u]
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                // Two pages read in the book's direction: right to left puts the first page on the right
                                val shown = if (state.rtl) unit.reversed() else unit
                                shown.forEach { n ->
                                    PageImage(
                                        viewModel,
                                        n,
                                        state.pdf,
                                        pageWidth,
                                        Modifier.fillMaxHeight().weight(1f, fill = false),
                                    )
                                }
                            }
                            // Phones: tap the edges to turn, the middle for the bar, double tap the middle to zoom
                            if (!isTv) {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val third = constraints.maxWidth / 3f
                                    Row(Modifier.fillMaxSize()) {
                                        val tap = remember { MutableInteractionSource() }
                                        Box(
                                            Modifier.fillMaxHeight().weight(1f).clickable(tap, null) {
                                                if (state.rtl) goNext() else goPrev()
                                            },
                                        )
                                        Box(
                                            Modifier.fillMaxHeight().weight(1f).pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { bar = !bar },
                                                    onDoubleTap = { at ->
                                                        zoom.toggle(
                                                            Offset(at.x + third, at.y),
                                                            IntSize(constraints.maxWidth, constraints.maxHeight),
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                        Box(
                                            Modifier.fillMaxHeight().weight(1f).clickable(tap, null) {
                                                if (state.rtl) goPrev() else goNext()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // The bar's small pages jump straight there
                LaunchedEffect(page, bar) {
                    val u = units.indexOfFirst { page in it }
                    if (u >= 0 && u != pager.currentPage && bar) pager.scrollToPage(u)
                }
            }
        }
        if (bar) {
            ReaderBar(
                state = state,
                page = page,
                viewModel = viewModel,
                isTv = isTv,
                onPage = { page = it },
                onClose = { bar = false },
                onLeave = close,
            )
        }
    }
}

/** Which pages go together: one each, or the cover alone and then pairs */
private fun unitsFor(
    mode: PageMode,
    pages: Int,
): List<List<Int>> =
    if (mode != PageMode.TWO || pages < 3) {
        (0 until pages).map { listOf(it) }
    } else {
        listOf(listOf(0)) + (1 until pages step 2).map { if (it + 1 < pages) listOf(it, it + 1) else listOf(it) }
    }

@Composable
private fun PageImage(
    viewModel: PageReaderViewModel,
    n: Int,
    pdf: Boolean,
    widthPx: Int,
    modifier: Modifier,
    scroll: Boolean = false,
) {
    val scale = if (scroll) ContentScale.FillWidth else ContentScale.Fit
    if (pdf) {
        val bmp by produceState<Bitmap?>(null, n, widthPx) { value = viewModel.pdfPage(n, widthPx) }
        val b = bmp
        if (b == null) {
            Box(modifier.then(if (scroll) Modifier.aspectRatio(0.7f) else Modifier.fillMaxWidth()))
        } else {
            Image(
                bitmap = remember(b) { b.asImageBitmap() },
                contentDescription = "Page ${n + 1}",
                contentScale = scale,
                modifier =
                    if (scroll) {
                        modifier.aspectRatio(b.width.toFloat() / b.height)
                    } else {
                        modifier.aspectRatio(b.width.toFloat() / b.height, matchHeightConstraintsFirst = true)
                    },
            )
        }
    } else {
        AsyncImage(
            model = viewModel.pageModel(n, widthPx),
            contentDescription = "Page ${n + 1}",
            contentScale = scale,
            // Long scroll: a page that hasn't arrived yet still takes up room, so the whole chapter isn't asked for at once
            modifier = if (scroll) modifier.defaultMinSize(minHeight = 260.dp) else modifier,
        )
    }
}

/** The bar: Back, the name, One page / Two pages / Long scroll, the direction, and small pages to slide through */
@Composable
private fun ReaderBar(
    state: PageReaderState,
    page: Int,
    viewModel: PageReaderViewModel,
    isTv: Boolean,
    onPage: (Int) -> Unit,
    onClose: () -> Unit,
    onLeave: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    Box(Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xE6121318))
                    .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicButton(label = null, icon = BenflixIcon.BACK_ARROW, onClick = onLeave)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOf(state.series, state.name).filter { it.isNotBlank() }.joinToString(" · "),
                        color = BenflixColors.Text,
                        fontSize = BenflixType.lead,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Page ${page + 1} of ${state.pages}" + if (state.saved) " · saved on this phone" else "",
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                    )
                }
                MusicButton("Done", null, onClick = onClose, modifier = Modifier.focusRequester(first))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PageMode.entries, key = { it.name }) { m ->
                    BookChip(m.label, m == state.mode, onClick = { viewModel.setMode(m, state.rtl) })
                }
                item(key = "dir") {
                    BookChip(
                        if (state.rtl) "Right to left" else "Left to right",
                        false,
                        onClick = { viewModel.setMode(state.mode, !state.rtl) },
                    )
                }
            }
        }
        // Small pages along the bottom (quick slide)
        val strip = rememberLazyListState(initialFirstVisibleItemIndex = (page - 3).coerceAtLeast(0))
        LazyRow(
            state = strip,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = state.rtl,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xE6121318)),
        ) {
            items(state.pages, key = { it }) { n ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.tv.material3.Surface(
                        onClick = { onPage(n) },
                        shape =
                            androidx.tv.material3.ClickableSurfaceDefaults
                                .shape(com.github.damontecres.wholphin.ui.theme.BenflixShapes.Small),
                        border =
                            com.github.damontecres.wholphin.ui.door
                                .doorFocusBorder(com.github.damontecres.wholphin.ui.theme.BenflixShapes.Small),
                        scale =
                            androidx.tv.material3.ClickableSurfaceDefaults
                                .scale(focusedScale = 1f),
                        modifier = Modifier.width(if (isTv) 64.dp else 48.dp).height(if (isTv) 92.dp else 70.dp),
                    ) {
                        PageImage(viewModel, n, state.pdf, THUMB_PX, Modifier.fillMaxSize())
                    }
                    Text(
                        "${n + 1}",
                        color = if (n == page) BenflixColors.Books else BenflixColors.Muted,
                        fontSize = BenflixType.tag,
                        fontWeight = if (n == page) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** The end of a chapter or volume: "Next chapter" with a 5 second countdown, or "You're all caught up" */
@Composable
private fun EndCard(
    state: PageReaderState,
    viewModel: PageReaderViewModel,
    active: Boolean,
    isTv: Boolean,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = state.nextId
    var left by remember { mutableIntStateOf(COUNTDOWN) }
    var stopped by remember { mutableStateOf(false) }
    LaunchedEffect(active, stopped) {
        if (active) viewModel.save(state.pages - 1, finished = true)
        if (!active || next == null || stopped) {
            left = COUNTDOWN
            return@LaunchedEffect
        }
        while (left > 0) {
            delay(1_000)
            left--
        }
        onOpen(next)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active && isTv) focus.tryRequestFocus() }
    val word = if (state.unit == "vol") "volume" else "chapter"
    Box(modifier.background(BenflixColors.Tray), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            if (next != null) {
                Text("NEXT ${word.uppercase()}", color = BenflixColors.Books, fontSize = BenflixType.tag, fontWeight = FontWeight.ExtraBold)
                if (state.nextCover != null) {
                    BookCoverImage(state.nextCover, state.nextName ?: "", Modifier.width(150.dp).aspectRatio(BOOK_RATIO))
                }
                Text(state.nextName ?: "", color = BenflixColors.Text, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MusicButton(
                        label = if (stopped) "Read it" else "Read it ($left)",
                        icon = BenflixIcon.BOOK,
                        primary = true,
                        onClick = { onOpen(next) },
                        modifier = Modifier.focusRequester(focus),
                    )
                    if (!stopped) MusicButton("Not now", null, onClick = { stopped = true })
                }
            } else {
                Text(
                    if (state.pdf || state.series.isBlank()) "You finished ${state.name}" else "You're all caught up",
                    color = BenflixColors.Text,
                    fontSize = BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (!state.pdf && state.series.isNotBlank()) {
                    Text(
                        if (state.saved) {
                            "That was the last $word of ${state.series} saved on this phone."
                        } else {
                            "That was the last $word of ${state.series} on Glowfin."
                        },
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.body,
                    )
                }
                MusicButton(
                    "Back",
                    BenflixIcon.BACK_ARROW,
                    primary = true,
                    onClick = onClose,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }
    }
}

/** How far a page is pinched in on a phone, and where it has been dragged to */
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Double tap: zoom in on the spot that was tapped, or back out */
    fun toggle(
        at: Offset,
        size: IntSize,
    ) {
        if (scale > 1.01f) {
            reset()
        } else {
            scale = DOUBLE_TAP_ZOOM
            val centre = Offset(size.width / 2f, size.height / 2f)
            offset = clamp((centre - at) * (DOUBLE_TAP_ZOOM - 1f), size)
        }
    }

    fun clamp(
        o: Offset,
        size: IntSize,
    ): Offset {
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
}

/**
 * Phones: two fingers pinch the page bigger (up to 4 times); while it's bigger, one finger drags it around. The
 * touches are taken before the page turner sees them, so a pinch never turns the page. [onPanY] is for the long
 * scroll, where dragging up and down keeps scrolling the strip.
 */
private fun Modifier.pinchZoom(
    zoom: ZoomState,
    enabled: Boolean,
    onPanY: ((Float) -> Unit)? = null,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(zoom) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val fingers = event.changes.count { it.pressed }
                    if (fingers > 1 || zoom.scale > 1.01f) {
                        zoom.scale = (zoom.scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                        val pan = event.calculatePan()
                        if (onPanY != null) {
                            onPanY(pan.y / zoom.scale)
                            zoom.offset = zoom.clamp(Offset(zoom.offset.x + pan.x, 0f), size)
                        } else {
                            zoom.offset = zoom.clamp(zoom.offset + pan, size)
                        }
                        if (zoom.scale <= 1.01f) zoom.reset()
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }

private const val IDLE_MS = 15 * 60_000L
private const val SAVE_DELAY_MS = 1_500L
private const val COUNTDOWN = 5
private const val THUMB_PX = 160
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Long scroll: the widest the strip gets (TVs and tablets keep it a comfortable reading width) */
private val STRIP_MAX = 720.dp
