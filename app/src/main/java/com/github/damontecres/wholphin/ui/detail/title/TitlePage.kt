package com.github.damontecres.wholphin.ui.detail.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixPanelDialog
import com.github.damontecres.wholphin.ui.door.DoorPoster
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShade
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/*
 * Benflix Redesign 2, section 1 (approved 2026-11-10): ONE title page frame for every kind of title
 * (movie, show, episode, anime, requests, book, album, game, actor, channel).
 *
 * TV (and phones on their side), drawn to fixed places so nothing on the page ever scrolls or jumps:
 *   - a still picture fills the screen, shaded on the left and at the bottom (no trailer behind it)
 *   - the title 9% down (up to 2 lines, long names shrink, never cut) with a short line in the door's
 *     colour under it; a different original name shows small and grey
 *   - the info line 28% down
 *   - up to 5 buttons always start 36% down. Greyed-out buttons are faded but can be reached, and the
 *     line under the buttons says why
 *   - the tab line 50% down. A tab opens as soon as you land on it; empty tabs are left out
 *   - the tab's content fills the rest, from 60% down (it scrolls inside itself if it's long)
 * Phones: the picture across the top 30%, then the words, a full width Play button, a row of icon
 * buttons, tabs you can swipe, and the tab's content. Once Play scrolls away a slim Play bar shows at
 * the bottom.
 */

/** A button on a title page. [off] greys it out: it can still be reached and the page says [off] (why). */
class TitleButton(
    val label: String,
    val icon: BenflixIcon,
    val onClick: () -> Unit,
    val off: String? = null,
    /** A quiet line shown under the buttons while this one is focused ("1h 12m left") */
    val note: String? = null,
    /** Pressing it opens this tab instead (like Episodes on a show) */
    val opensTab: String? = null,
)

/** One tab: [content] is only drawn (and so only fetched) once you land on it */
class TitleTab(
    val name: String,
    val content: @Composable (TitleTabScope) -> Unit,
)

/** What a tab's content needs to know about where it is */
class TitleTabScope(
    /** Space at the left and right edge of the page */
    val side: Dp,
    /** True for the fixed TV layout (tabs content sits in the bottom 40%), false on phones */
    val wide: Boolean,
)

/** What fills the picture area */
sealed interface TitlePicture {
    /** The title's wide picture (movies, shows, episodes) */
    data class Backdrop(
        val item: BaseItem,
    ) : TitlePicture

    /** A picture from an address (requests, channels) */
    data class Wide(
        val url: String?,
    ) : TitlePicture

    /** A cover standing where the picture goes (books, albums, games, actors). [ratio] = width / height */
    data class Cover(
        val url: String?,
        val ratio: Float,
        val tint: Color,
        /** An image request to load instead of [url] (for pictures that need a sign-in header, like games) */
        val model: Any? = null,
    ) : TitlePicture
}

/** One part of the slim strip under the story: "IMDb **8.5**", "English", "Director **Denis Villeneuve**" */
class StripPart(
    val label: String,
    val value: String? = null,
)

/** One line in the More panel. [youOnly] marks admin-only items with a quiet "You only" */
class TitleMoreItem(
    val label: String,
    val icon: BenflixIcon,
    val onClick: () -> Unit,
    val youOnly: Boolean = false,
)

/** True when the page should use the fixed TV layout (TVs, and phones or tablets turned on their side) */
@Composable
fun rememberTitleWide(): Boolean {
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    return isTv || config.screenWidthDp > config.screenHeightDp
}

@Composable
fun TitlePage(
    title: String,
    accent: Color,
    picture: TitlePicture,
    info: String,
    buttons: List<TitleButton>,
    tabs: List<TitleTab>,
    modifier: Modifier = Modifier,
    originalTitle: String? = null,
    /** A boxed age rating at the start of the info line ("PG-13") */
    rating: String? = null,
    /** A small extra under the info line on phones and beside it on TV (like "Part 1 of 3 · Collection") */
    infoExtra: (@Composable () -> Unit)? = null,
    /** The quiet line under the buttons when nothing greyed out is focused ("S2 E4 · 20 min left") */
    statusLine: String? = null,
    /** The tab the page opens on (by name). Falls back to the first tab. */
    openTab: String? = null,
    big: Boolean = false,
) {
    val shownTabs = tabs
    // The tab you're on is kept by name, so a tab that turns up later (say Parents' guide) doesn't move you
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val tab =
        shownTabs.indexOfFirst { it.name == picked }.takeIf { it >= 0 }
            ?: shownTabs.indexOfFirst { it.name == openTab }.coerceAtLeast(0)
    // A button that opens a tab (Episodes) switches to it and moves to the tab line
    var jump by remember { mutableIntStateOf(0) }
    val shownButtons =
        buttons.take(MAX_BUTTONS).map { b ->
            val to = b.opensTab
            if (to == null) {
                b
            } else {
                TitleButton(b.label, b.icon, {
                    picked = to
                    jump++
                }, b.off, b.note)
            }
        }
    if (rememberTitleWide()) {
        WideTitlePage(
            title = title,
            accent = accent,
            picture = picture,
            info = info,
            rating = rating,
            infoExtra = infoExtra,
            originalTitle = originalTitle,
            buttons = shownButtons,
            tabs = shownTabs,
            tab = tab,
            onTab = { picked = shownTabs.getOrNull(it)?.name },
            statusLine = statusLine,
            big = big,
            jump = jump,
            modifier = modifier,
        )
    } else {
        PhoneTitlePage(
            title = title,
            accent = accent,
            picture = picture,
            info = info,
            rating = rating,
            infoExtra = infoExtra,
            originalTitle = originalTitle,
            buttons = shownButtons,
            tabs = shownTabs,
            tab = tab,
            onTab = { picked = shownTabs.getOrNull(it)?.name },
            statusLine = statusLine,
            big = big,
            jump = jump,
            modifier = modifier,
        )
    }
}

private const val MAX_BUTTONS = 5

@Composable
private fun WideTitlePage(
    title: String,
    accent: Color,
    picture: TitlePicture,
    info: String,
    rating: String?,
    infoExtra: (@Composable () -> Unit)?,
    originalTitle: String?,
    buttons: List<TitleButton>,
    tabs: List<TitleTab>,
    tab: Int,
    onTab: (Int) -> Unit,
    statusLine: String?,
    big: Boolean,
    jump: Int,
    modifier: Modifier,
) {
    val isTv = rememberIsTv()
    val side = BenflixSpace.tvEdge
    val firstButton = remember { FocusRequester() }
    val firstTab = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    var focusedButton by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(60)
            firstButton.tryRequestFocus()
        }
    }
    LaunchedEffect(jump) {
        if (jump > 0) {
            delay(60)
            tabFocus.tryRequestFocus()
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BenflixColors.Tray)) {
        val h = maxHeight
        TitlePictureView(picture, wide = true)
        // Title + door colour line: 9% down
        Column(
            modifier =
                Modifier
                    .padding(start = side, top = h * .09f)
                    .widthIn(max = maxWidth * .55f)
                    .height(h * .19f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            TitleWords(title = title, originalTitle = originalTitle, accent = accent, wide = true)
        }
        // Info line: 28% down
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .padding(start = side, end = side, top = h * .28f)
                    .widthIn(max = maxWidth * .7f),
        ) {
            InfoLine(info, rating)
            infoExtra?.invoke()
        }
        // Buttons: always 36% down
        Column(Modifier.padding(start = side, end = side, top = h * .36f)) {
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
        // Tabs: 50% down
        if (tabs.isNotEmpty()) {
            TabLine(
                tabs = tabs,
                tab = tab,
                onTab = onTab,
                wide = true,
                firstTab = firstTab,
                selected = tabFocus,
                modifier = Modifier.padding(start = side, end = side, top = h * .5f),
            )
            // The tab's content: from 60% down to the bottom
            Box(
                Modifier
                    .padding(top = h * .6f)
                    .fillMaxWidth()
                    .height(h * .37f),
            ) {
                androidx.compose.runtime.key(tab) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        tabs.getOrNull(tab)?.content?.invoke(TitleTabScope(side = side, wide = true))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneTitlePage(
    title: String,
    accent: Color,
    picture: TitlePicture,
    info: String,
    rating: String?,
    infoExtra: (@Composable () -> Unit)?,
    originalTitle: String?,
    buttons: List<TitleButton>,
    tabs: List<TitleTab>,
    tab: Int,
    onTab: (Int) -> Unit,
    statusLine: String?,
    big: Boolean,
    jump: Int,
    modifier: Modifier,
) {
    val side = BenflixSpace.phoneEdge
    val listState = rememberLazyListState()
    var playGone by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf<String?>(null) }
    val firstTab = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(jump) {
        if (jump > 0) listState.animateScrollToItem(1)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BenflixColors.Tray)) {
        val h = maxHeight
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "head") {
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
                    }
                }
            }
            if (tabs.isNotEmpty()) {
                item(key = "tabs") {
                    TabLine(
                        tabs = tabs,
                        tab = tab,
                        onTab = onTab,
                        wide = false,
                        firstTab = firstTab,
                        selected = tabFocus,
                        modifier = Modifier.padding(top = 18.dp, bottom = 12.dp),
                    )
                }
                item(key = "tab-${tabs.getOrNull(tab)?.name}") {
                    Column(Modifier.fillMaxWidth()) {
                        tabs.getOrNull(tab)?.content?.invoke(TitleTabScope(side = side, wide = false))
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = BenflixColors.Text,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    (play.note ?: statusLine)?.let {
                        Text(
                            text = it,
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.tag,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TitleButtonView(button = play, primary = true, big = false, onFocus = {}, onOffTap = { why = it })
            }
        }
    }
}

/** The picture (or cover) behind the words, with the shade that keeps the words readable */
@Composable
private fun BoxScope.TitlePictureView(
    picture: TitlePicture,
    wide: Boolean,
) {
    val imageUrlService = LocalImageUrlService.current
    val width = LocalConfiguration.current.screenWidthDp
    when (picture) {
        is TitlePicture.Backdrop -> {
            val item = picture.item
            // Lite: loaded at the screen's own size, never bigger than 1920 wide
            val url =
                remember(item.id) {
                    imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = (width * 2).coerceAtMost(1920))
                }
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        is TitlePicture.Wide -> {
            if (picture.url != null) {
                AsyncImage(
                    model = picture.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        is TitlePicture.Cover -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            0f to picture.tint.copy(alpha = .22f),
                            1f to BenflixColors.Tray,
                        ),
                    ),
            )
            if (picture.url != null || picture.model != null) {
                AsyncImage(
                    model = picture.model ?: picture.url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier =
                        if (wide) {
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 48.dp, end = 64.dp)
                                .fillMaxHeight(if (picture.ratio >= 1f) .52f else .64f)
                                .aspectRatio(picture.ratio)
                        } else {
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(top = 16.dp, bottom = 8.dp)
                                .fillMaxHeight()
                                .aspectRatio(picture.ratio)
                        },
                )
            }
        }
    }
    if (picture !is TitlePicture.Cover) {
        Box(Modifier.fillMaxSize().background(BenflixShade.heroSide))
        Box(Modifier.fillMaxSize().background(BenflixShade.bottom(if (wide) .35f else .55f)))
    }
}

/** The title (shrinks a little when long, never cut off), the original name and the door colour line */
@Composable
private fun TitleWords(
    title: String,
    originalTitle: String?,
    accent: Color,
    wide: Boolean,
) {
    val original = originalTitle?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
    val size: TextUnit =
        when {
            !wide && title.length > 40 -> 22.sp
            !wide && title.length > 24 -> 26.sp
            !wide -> 30.sp
            title.length > 52 -> 26.sp
            title.length > 30 || original != null -> 32.sp
            else -> 40.sp
        }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = BenflixColors.Text,
            fontSize = size,
            lineHeight = size * 1.1f,
            fontWeight = FontWeight.ExtraBold,
            maxLines = if (wide) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (original != null) {
            Text(
                text = original,
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(width = 44.dp, height = 4.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
    }
}

/** "2024 · [PG-13] · 2h 46m · Sci-fi · Adventure" */
@Composable
private fun InfoLine(
    info: String,
    rating: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!rating.isNullOrBlank()) {
            Text(
                text = rating,
                color = BenflixColors.Soft,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(BenflixColors.Raised.copy(alpha = .8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (info.isNotBlank()) {
            Text(
                text = info,
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A title page button: the first is white with its full words, the rest grey with an icon and a word.
 * A greyed-out one ([TitleButton.off]) is faded but still reachable; pressing it does nothing (phones
 * show why).
 */
@Composable
private fun TitleButtonView(
    button: TitleButton,
    primary: Boolean,
    big: Boolean,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    onOffTap: (String) -> Unit = {},
) {
    val shape = RoundedCornerShape(10.dp)
    val container = if (primary) BenflixColors.Text else BenflixColors.FocusRow.copy(alpha = .92f)
    val content = if (primary) BenflixColors.Tray else BenflixColors.Text
    val off = button.off
    Surface(
        onClick = { if (off != null) onOffTap(off) else button.onClick() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = content,
                focusedContainerColor = if (primary) BenflixColors.Text else BenflixColors.PanelHigh,
                focusedContentColor = content,
                pressedContainerColor = container,
                pressedContentColor = content,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .alpha(if (off != null) .45f else 1f)
                .onFocusChanged { onFocus(it.isFocused) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, if (fill) Alignment.CenterHorizontally else Alignment.Start),
            modifier =
                Modifier
                    .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = if (big) 22.dp else 16.dp, vertical = if (big) 13.dp else 10.dp),
        ) {
            BenflixIconImage(button.icon, color = content, size = if (big) 22.dp else 18.dp)
            Text(
                text = button.label,
                color = content,
                fontSize = if (big) BenflixType.lead else BenflixType.body,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Phones: the other buttons as an icon with a small word under it */
@Composable
private fun PhoneIconButton(
    button: TitleButton,
    onOffTap: (String) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val off = button.off
    Surface(
        onClick = { if (off != null) onOffTap(off) else button.onClick() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
                pressedContainerColor = BenflixColors.FocusRow,
                pressedContentColor = BenflixColors.Text,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.alpha(if (off != null) .45f else 1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            BenflixIconImage(button.icon, color = BenflixColors.Text, size = 22.dp)
            Text(
                text = button.label,
                color = BenflixColors.Soft,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 84.dp),
            )
        }
    }
}

/** The line of tabs: underlined white for the one you're on. On TV a tab opens as soon as you land on it. */
@Composable
private fun TabLine(
    tabs: List<TitleTab>,
    tab: Int,
    onTab: (Int) -> Unit,
    wide: Boolean,
    firstTab: FocusRequester,
    selected: FocusRequester,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(if (wide) 30.dp else 22.dp),
        contentPadding = if (wide) PaddingValues(0.dp) else PaddingValues(horizontal = BenflixSpace.phoneEdge),
        modifier = modifier.fillMaxWidth().focusRestorer(selected),
    ) {
        itemsIndexed(tabs, key = { _, it -> it.name }) { index, t ->
            val on = index == tab
            val shape = RoundedCornerShape(6.dp)
            Surface(
                onClick = { onTab(index) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = BenflixColors.Text,
                        focusedContainerColor = Color.Transparent,
                        focusedContentColor = BenflixColors.Text,
                        pressedContainerColor = Color.Transparent,
                        pressedContentColor = BenflixColors.Text,
                    ),
                border = doorFocusBorder(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier =
                    Modifier
                        .then(if (on) Modifier.focusRequester(selected) else Modifier)
                        .then(if (index == 0) Modifier.focusRequester(firstTab) else Modifier)
                        .onFocusChanged { if (it.isFocused && index != tab) onTab(index) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = t.name,
                        color = if (on) BenflixColors.Text else BenflixColors.Muted,
                        fontSize = if (wide) BenflixType.lead else BenflixType.body,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .height(3.dp)
                            .width(28.dp)
                            .background(if (on) BenflixColors.Text else Color.Transparent, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

// ---------- Pieces tabs are made of ----------

/**
 * The Overview tab: the story in 2 lines (3 on phones), More (opens the whole story in a side panel or
 * a sheet on phones), then the slim strip of scores, language and who made it (2 lines at most).
 */
@Composable
fun TitleOverview(
    scope: TitleTabScope,
    title: String,
    story: String?,
    strip: List<StripPart>,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    var showStory by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .padding(start = scope.side, end = scope.side)
                .then(if (scope.wide) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth()),
    ) {
        if (!story.isNullOrBlank()) {
            Text(
                text = story,
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                lineHeight = BenflixType.body * 1.4f,
                maxLines = if (scope.wide) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
            TitleTextLink("More", onClick = { showStory = true })
        }
        if (strip.isNotEmpty()) {
            val text =
                remember(strip) {
                    buildAnnotatedString {
                        strip.forEachIndexed { i, p ->
                            if (i > 0) append("     ")
                            withStyle(SpanStyle(color = BenflixColors.Muted, fontWeight = FontWeight.SemiBold)) { append(p.label) }
                            if (p.value != null) {
                                append(" ")
                                withStyle(SpanStyle(color = BenflixColors.Text, fontWeight = FontWeight.ExtraBold)) { append(p.value) }
                            }
                        }
                    }
                }
            Text(text = text, fontSize = BenflixType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        extra?.invoke()
    }
    if (showStory && !story.isNullOrBlank()) {
        TitleStoryPanel(title = title, story = story, onDismiss = { showStory = false })
    }
}

/** A small focusable word link like "More" */
@Composable
fun TitleTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
                pressedContainerColor = BenflixColors.FocusRow,
                pressedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = BenflixColors.Text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

/** The whole story, in the one Benflix panel (side panel on TV, sheet on phones). Long ones scroll. */
@Composable
fun TitleStoryPanel(
    title: String,
    story: String,
    onDismiss: () -> Unit,
) {
    BenflixPanelDialog(onDismissRequest = onDismiss) {
        val close = remember { FocusRequester() }
        LaunchedEffect(Unit) { close.tryRequestFocus() }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Text("The story", color = BenflixColors.Text, fontSize = BenflixType.rowName, fontWeight = FontWeight.ExtraBold)
            Text(title, color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold)
            Text(story, color = BenflixColors.Soft, fontSize = BenflixType.body, lineHeight = BenflixType.body * 1.5f)
            TitleTextLink("Close", onClick = onDismiss, modifier = Modifier.focusRequester(close))
        }
    }
}

/** The More panel: everything that isn't one of the 5 buttons. "You only" items show only for the admin. */
@Composable
fun TitleMorePanel(
    title: String,
    items: List<TitleMoreItem>,
    onDismiss: () -> Unit,
) {
    com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog(
        title = "More",
        subtitle = title,
        onDismissRequest = onDismiss,
        choices =
            items.map {
                com.github.damontecres.wholphin.ui.components.BenflixChoice(
                    label = it.label,
                    onClick = it.onClick,
                    icon = it.icon,
                    note = if (it.youOnly) "You only" else null,
                )
            },
    )
}

/**
 * A row of posters inside a tab: one row on TV (press Down to reach it), 3 across on phones.
 * [name] shows above it when a tab holds more than one row. [current] gets a "You are here" tag.
 */
@Composable
fun TitlePosterRow(
    scope: TitleTabScope,
    items: List<BaseItem>,
    onClick: (BaseItem) -> Unit,
    name: String? = null,
    current: BaseItem? = null,
) {
    val list = remember(items) { items.distinctBy { it.id } }
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = if (scope.wide) 16.dp else 20.dp)) {
        if (name != null) TitleRowName(name, scope)
        if (scope.wide) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = scope.side),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(list, key = { _, it -> it.id.toString() }) { _, item ->
                    val here = current != null && item.id == current.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoorPoster(item = item, width = 96.dp, showName = false, onClick = { if (!here) onClick(item) })
                        if (here) {
                            Text(
                                "You are here",
                                color = BenflixColors.Text,
                                fontSize = BenflixType.tag,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        } else {
            TitleGrid(scope, list.size) { index, width ->
                val item = list[index]
                DoorPoster(item = item, width = width, showName = true, onClick = { if (current?.id != item.id) onClick(item) })
            }
        }
    }
}

/** A small row name inside a tab */
@Composable
fun TitleRowName(
    name: String,
    scope: TitleTabScope,
) {
    Text(
        text = name,
        color = BenflixColors.Text,
        fontSize = BenflixType.body,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = scope.side, end = scope.side, bottom = 8.dp),
    )
}

/** Phones: [count] things 3 across, each [width] wide */
@Composable
fun TitleGrid(
    scope: TitleTabScope,
    count: Int,
    across: Int = 3,
    cell: @Composable (index: Int, width: Dp) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = scope.side)) {
        val gap = 10.dp
        val w = (maxWidth - gap * (across - 1)) / across
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            (0 until count).chunked(across).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { cell(it, w) }
                    repeat(across - row.size) { Spacer(Modifier.width(w)) }
                }
            }
        }
    }
}

/** A plain message when a tab has nothing yet */
@Composable
fun TitleEmpty(
    text: String,
    scope: TitleTabScope,
) {
    Text(text, color = BenflixColors.Muted, fontSize = BenflixType.body, modifier = Modifier.padding(horizontal = scope.side))
}
