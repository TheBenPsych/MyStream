package com.github.damontecres.wholphin.ui.books

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * through. At the end of a volume: a "Next volume" card that opens it after 5 seconds.
 */
@Composable
fun PageReaderPage(
    id: String,
    book: Boolean,
    modifier: Modifier = Modifier,
    viewModel: PageReaderViewModel = hiltViewModel(key = "pages-$id"),
) {
    LaunchedEffect(id) { viewModel.load(id, book) }
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
            Text(state.problem ?: "It has no pages Benflix can read.", color = BenflixColors.Soft, fontSize = BenflixType.body)
            MusicButton("Back", BenflixIcon.BACK_ARROW, onClick = { viewModel.navigationManager.goBack() })
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
    val pageWidth = (if (state.mode == PageMode.TWO) screenPx / 2 else screenPx).coerceIn(480, 1600)
    val scope = rememberCoroutineScope()

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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { e ->
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
                            }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { bar = !bar },
                ) {
                    items(state.pages, key = { it }) { n ->
                        PageImage(viewModel, n, state.pdf, pageWidth, Modifier.fillMaxWidth(), scroll = true)
                    }
                    item(key = "end") {
                        EndCard(state, viewModel, active = list.layoutInfo.visibleItemsInfo.any { it.key == "end" }, isTv = isTv)
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
                    key = { it },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { e ->
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
                        EndCard(state, viewModel, active = pager.currentPage == units.size, isTv = isTv)
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
                            // Phones: tap the edges to turn, the middle for the bar
                            if (!isTv) {
                                Row(Modifier.fillMaxSize()) {
                                    val tap = remember { MutableInteractionSource() }
                                    Box(
                                        Modifier.fillMaxHeight().weight(1f).clickable(tap, null) {
                                            if (state.rtl) goNext() else goPrev()
                                        },
                                    )
                                    Box(Modifier.fillMaxHeight().weight(1f).clickable(tap, null) { bar = !bar })
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
                modifier = if (scroll) modifier.aspectRatio(b.width.toFloat() / b.height) else modifier.aspectRatio(b.width.toFloat() / b.height, matchHeightConstraintsFirst = true),
            )
        }
    } else {
        AsyncImage(
            model = viewModel.pageUrl(n, widthPx),
            contentDescription = "Page ${n + 1}",
            contentScale = scale,
            modifier = modifier,
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
                MusicButton(label = null, icon = BenflixIcon.BACK_ARROW, onClick = { viewModel.navigationManager.goBack() })
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOf(state.series, state.name).filter { it.isNotBlank() }.joinToString(" · "),
                        color = BenflixColors.Text,
                        fontSize = BenflixType.lead,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Page ${page + 1} of ${state.pages}", color = BenflixColors.Muted, fontSize = BenflixType.small)
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

/** The end of a volume: "Next volume" with a 5 second countdown, or "You finished it" */
@Composable
private fun EndCard(
    state: PageReaderState,
    viewModel: PageReaderViewModel,
    active: Boolean,
    isTv: Boolean,
) {
    val vol = state.vol
    val next = vol?.nextId
    var left by remember { mutableIntStateOf(COUNTDOWN) }
    var stopped by remember { mutableStateOf(false) }
    LaunchedEffect(active, stopped) {
        if (!active || next == null || stopped) {
            left = COUNTDOWN
            return@LaunchedEffect
        }
        viewModel.save(state.pages - 1, finished = true)
        while (left > 0) {
            delay(1_000)
            left--
        }
        viewModel.openNext(next)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active && isTv) focus.tryRequestFocus() }
    Box(Modifier.fillMaxSize().background(BenflixColors.Tray), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(24.dp)) {
            if (next != null && vol != null) {
                Text("NEXT VOLUME", color = BenflixColors.Books, fontSize = BenflixType.tag, fontWeight = FontWeight.ExtraBold)
                BookCoverImage(vol.nextCover, vol.nextName ?: "", Modifier.width(150.dp).aspectRatio(BOOK_RATIO))
                Text(vol.nextName ?: "", color = BenflixColors.Text, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MusicButton(
                        label = if (stopped) "Read it" else "Read it ($left)",
                        icon = BenflixIcon.BOOK,
                        primary = true,
                        onClick = { viewModel.openNext(next) },
                        modifier = Modifier.focusRequester(focus),
                    )
                    if (!stopped) MusicButton("Not now", null, onClick = { stopped = true })
                }
            } else {
                Text("You finished ${state.name}", color = BenflixColors.Text, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold)
                MusicButton(
                    "Back to the book",
                    BenflixIcon.BACK_ARROW,
                    primary = true,
                    onClick = { viewModel.navigationManager.goBack() },
                    modifier = Modifier.focusRequester(focus),
                )
            }
        }
    }
}

private const val IDLE_MS = 15 * 60_000L
private const val SAVE_DELAY_MS = 1_500L
private const val COUNTDOWN = 5
private const val THUMB_PX = 160
