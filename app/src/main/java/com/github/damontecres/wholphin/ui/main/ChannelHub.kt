package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.BENFLIX_GROUP
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.ChannelTileOrder
import com.github.damontecres.wholphin.services.FAVOURITES_GROUP
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/** How a channel type is named on its tile and in the side guide */
internal fun groupLabel(group: String): String =
    when (group) {
        BENFLIX_GROUP -> "Benflix · No ads"
        FAVOURITES_GROUP -> "★ Favourites"
        else -> group
    }

/** The tile colours and line picture for each channel type (24x24 outline paths) */
private class TileStyle(
    val from: Color,
    val to: Color,
    val icon: String,
)

private val TileStyles =
    mapOf(
        FAVOURITES_GROUP to
            TileStyle(Color(0xFF5E4A14), Color(0xFFC49A2E), "M12 2l3 7 7 .6-5.3 4.6 1.6 7.1L12 17.5 5.7 21.3l1.6-7.1L2 9.6 9 9z"),
        "News" to TileStyle(Color(0xFF1C2E5E), Color(0xFF4A6EC9), "M4 5h13v14H5a1 1 0 0 1-1-1zM17 9h3v9a1 1 0 0 1-2 0M7 8h7M7 12h7M7 16h4"),
        "Sports" to
            TileStyle(
                Color(0xFF1A3A2A),
                Color(0xFF3FBF7A),
                "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18",
            ),
        "Reality" to TileStyle(Color(0xFF5E2A14), Color(0xFFD9733A), "M4 7h16v11H4zM8 3l4 4 4-4"),
        "Classic TV" to TileStyle(Color(0xFF2A2A2A), Color(0xFF8A8A8A), "M3 7h18v12H3zM8 3l4 4 4-4M17 11v4"),
        "Comedy" to
            TileStyle(Color(0xFF5A1C5E), Color(0xFFE05AA0), "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8 14c2 3 6 3 8 0M9 9h.01M15 9h.01"),
        "Kids & Family" to
            TileStyle(
                Color(0xFF1C4A5E),
                Color(0xFF3FA8D9),
                "M7 5a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM17 5a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM2 20c0-4 3-6 5-6s5 2 5 6M12 20c0-4 3-6 5-6s5 2 5 6",
            ),
        "Drama & Crime" to TileStyle(Color(0xFF4A1A1A), Color(0xFFB9403A), "M10 4a6 6 0 1 0 0 12 6 6 0 0 0 0-12zM14.5 14.5L20 20"),
        "Food & Home" to TileStyle(Color(0xFF4A3A14), Color(0xFFC9A03A), "M6 3v8a2 2 0 0 0 4 0V3M8 11v10M16 3c-2 0-3 3-3 6s1 4 3 4v8"),
        "Documentaries & Nature" to
            TileStyle(Color(0xFF14442A), Color(0xFF3FB86A), "M12 21c-5-3-8-7-8-12 4 0 7 2 8 5 1-3 4-5 8-5 0 5-3 9-8 12z"),
        "Music" to
            TileStyle(
                Color(0xFF2A1C5E),
                Color(0xFF7A5AE0),
                "M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
            ),
        "Sci-Fi & Anime" to TileStyle(Color(0xFF14444A), Color(0xFF3FB8C4), "M12 2c3 3 4 8 3 13H9C8 10 9 5 12 2zM9 15l-3 4h3M15 15l3 4h-3"),
        "Movies" to TileStyle(Color(0xFF4A1A2A), Color(0xFFC9506E), "M3 6h18v12H3zM3 10h18M7 6l2 4M12 6l2 4M17 6l2 4"),
        BENFLIX_GROUP to TileStyle(Color(0xFF7A2A1C), Color(0xFFD98B3A), "M6 4l13 8-13 8z"),
    )
private val OtherStyle = TileStyle(Color(0xFF222226), Color(0xFF55555C), "M5 12h.01M12 12h.01M19 12h.01")

/** Channels matching what was typed: a channel number, or part of a channel's name */
internal fun searchChannels(
    list: List<ChannelSchedule>,
    query: String,
): List<ChannelSchedule> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    q.toIntOrNull()?.let { n ->
        return list.filter { it.number == n } + list.filter { it.number != n && it.number.toString().startsWith(q) }
    }
    return list.filter { it.title.contains(q, ignoreCase = true) }
}

/** The number on a remote's number button, or null for any other button */
internal fun digitOf(event: KeyEvent): Int? {
    val code = event.key.nativeKeyCode
    return when (code) {
        in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> code - android.view.KeyEvent.KEYCODE_0
        in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> code - android.view.KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }
}

/**
 * The Channels page's first screen: a search box, Recently watched, then one tile per channel type
 * (live TV's strong points first). Picking a tile opens the guide for just that type. On a TV,
 * pressing number buttons jumps straight to that channel.
 */
@Composable
internal fun ChannelHub(
    list: List<ChannelSchedule>,
    lastOpened: String?,
    onOpen: (String) -> Unit,
    onWatch: (ChannelSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    val favourites by ChannelPrefs.favourites.collectAsState()
    val recentKeys by ChannelPrefs.recent.collectAsState()
    val hidden by ChannelPrefs.hidden.collectAsState()
    val byGroup = remember(list) { list.groupBy { it.group() } }
    val byKey = remember(list) { list.associateBy { it.key } }
    val tiles =
        remember(byGroup, hidden) {
            ChannelTileOrder.filter { it == FAVOURITES_GROUP || (it in byGroup && it !in hidden) } +
                byGroup.keys.filter { it !in ChannelTileOrder && it !in hidden }
        }
    val recent = remember(recentKeys, byKey) { recentKeys.mapNotNull { byKey[it] } }
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    var showTypes by remember { mutableStateOf(false) }
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    // Number buttons: wait a moment for more digits, then turn that channel on
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(NUMBER_WAIT_MS)
        val number = digits.toIntOrNull()
        digits = ""
        list.firstOrNull { it.number == number }?.let(onWatch)
    }
    val tileFocus = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(Unit) {
        delay(80)
        val target = lastOpened?.takeIf { it in tiles } ?: tiles.firstOrNull { it != FAVOURITES_GROUP || favourites.isNotEmpty() }
        target?.let { tileFocus[it]?.tryRequestFocus() }
    }
    val results = remember(query, list) { searchChannels(list, query) }
    val gap = if (isTv) 10.dp else 8.dp
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val digit = digitOf(event) ?: return@onPreviewKeyEvent false
                    if (searchFocused) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp && digits.length < 4) digits += digit
                    true
                },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isTv) 6 else 3),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding =
                PaddingValues(
                    start = if (isTv) 8.dp else 12.dp,
                    end = if (isTv) 32.dp else 12.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                SearchEditTextBox(
                    value = query,
                    onValueChange = { query = it },
                    onSearchClick = {},
                    modifier =
                        Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .onFocusChanged { searchFocused = it.isFocused },
                )
            }
            if (query.isNotBlank()) {
                if (results.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                        Text("No channel called \"${query.trim()}\"", fontSize = 15.sp, color = Muted)
                    }
                }
                items(results, key = { "r-${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { schedule ->
                    ChannelRow(schedule, onClick = { onWatch(schedule) }, modifier = Modifier.widthIn(max = 620.dp))
                }
                return@LazyVerticalGrid
            }
            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HubTitle("Recently watched")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            items(recent, key = { it.key }) { schedule ->
                                RecentCard(schedule, width = if (isTv) 200.dp else 150.dp, onClick = { onWatch(schedule) })
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "types") { HubTitle("Channel types") }
            items(tiles, key = { "t-$it" }) { group ->
                val members =
                    if (group == FAVOURITES_GROUP) {
                        favourites.mapNotNull { byKey[it] }
                    } else {
                        byGroup[group].orEmpty()
                    }
                TypeTile(
                    group = group,
                    members = members,
                    onClick = { if (members.isNotEmpty()) onOpen(group) },
                    modifier = Modifier.focusRequester(tileFocus.getOrPut(group) { FocusRequester() }),
                )
            }
            item(key = "edit") {
                Surface(
                    onClick = { showTypes = true },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = tileColors(Color(0xFF17171B)),
                    border = tileBorder(),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.aspectRatio(4f / 3f),
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    ) {
                        Text("Show or hide", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("channel types", fontSize = 11.sp, color = Muted)
                    }
                }
            }
        }
        if (digits.isNotEmpty()) {
            Text(
                text = digits.padEnd(3, '_'),
                fontFamily = BenflixTitleFont,
                fontSize = 40.sp,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 40.dp)
                        .background(Color.Black.copy(alpha = .85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
    }
    if (showTypes) {
        HideTypesDialog(
            groups = ChannelTileOrder.filter { it in byGroup } + byGroup.keys.filter { it !in ChannelTileOrder },
            hidden = hidden,
        ) {
            showTypes = false
        }
    }
}

@Composable
private fun HubTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = Color(0xFFB8B8C0),
        modifier = Modifier.padding(top = 4.dp),
    )
}

private val Muted = Color(0xFF8B8B94)

@Composable
private fun tileColors(container: Color) =
    ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = Color.White,
        focusedContainerColor = container,
        focusedContentColor = Color.White,
    )

@Composable
private fun tileBorder() =
    ClickableSurfaceDefaults.border(
        focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp)),
    )

/** A type tile: its colours and line picture, three of its channels' logos, the name and how many */
@Composable
private fun TypeTile(
    group: String,
    members: List<ChannelSchedule>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = TileStyles[group] ?: OtherStyle
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = tileColors(Color.Transparent),
        border = tileBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.aspectRatio(4f / 3f),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(style.from, style.to)), RoundedCornerShape(8.dp)),
        ) {
            TileIcon(
                path = style.icon,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .fillMaxHeight(.62f)
                        .aspectRatio(1f),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    members.take(3).forEach { ChannelLogo(it, size = 20.dp) }
                }
                Text(
                    text = groupLabel(group).uppercase(),
                    fontFamily = BenflixTitleFont,
                    fontSize = 17.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
                Text(
                    text =
                        when {
                            members.isEmpty() && group == FAVOURITES_GROUP -> "Star a channel to add it"
                            members.size == 1 -> "1 channel"
                            else -> "${members.size} channels"
                        },
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = .8f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The tile's big see-through line picture, drawn from a 24x24 outline path */
@Composable
private fun TileIcon(
    path: String,
    modifier: Modifier = Modifier,
) {
    val shape = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, pivot = Offset.Zero) {
            drawPath(
                shape,
                color = Color.White.copy(alpha = .28f),
                style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** A channel's logo on white (live channels), or its coloured letter badge (Benflix channels) */
@Composable
internal fun ChannelLogo(
    schedule: ChannelSchedule,
    size: Dp,
) {
    val live = schedule.live
    if (live != null) {
        val imageUrlService = LocalImageUrlService.current
        val logo = remember(live.id) { imageUrlService.getItemImageUrl(itemId = live.id, imageType = ImageType.PRIMARY, maxWidth = 80) }
        Box(
            modifier =
                Modifier
                    .size(size)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .padding(2.dp),
        ) {
            AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    } else {
        val (from, to) = BadgeColors[(schedule.channel?.ordinal ?: 0) % BadgeColors.size]
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).background(Brush.linearGradient(listOf(from, to)), RoundedCornerShape(4.dp)),
        ) {
            Text(
                text = schedule.title.take(1).uppercase(),
                fontFamily = BenflixTitleFont,
                fontSize = (size.value * .62f).sp,
                color = Color.White,
            )
        }
    }
}

/** One line for a channel: logo, number and name, and what's on now. Used by search and the side guide */
@Composable
internal fun ChannelRow(
    schedule: ChannelSchedule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    val now = System.currentTimeMillis()
    val on = schedule.programAt(now)
    val favourites by ChannelPrefs.favourites.collectAsState()
    Surface(
        onClick = onClick,
        onLongClick = { ChannelPrefs.toggleFavourite(schedule.key) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (playing) Color(0xFF2A2A32) else Color(0xFF1C1C21),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        ) {
            ChannelLogo(schedule, size = 30.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${schedule.number}  ${schedule.title}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (playing) "Playing now" else on?.name() ?: "",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (schedule.key in favourites) Text("★", fontSize = 14.sp, color = StarGold)
        }
    }
}

internal val StarGold = Color(0xFFE0A54A)

/** A Recently watched card: logo, number and name, and what's on now with how far in it is */
@Composable
private fun RecentCard(
    schedule: ChannelSchedule,
    width: Dp,
    onClick: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val on = schedule.programAt(now)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = tileColors(Color(0xFF1C1C21)),
        border = tileBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = Modifier.width(width).height(64.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            ) {
                ChannelLogo(schedule, size = 30.dp)
                Column {
                    Text(
                        text = "${schedule.number}  ${schedule.title}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = on?.name().orEmpty(),
                        fontSize = 11.sp,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (on != null && !on.noGuide && on.endMs > on.startMs) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(((now - on.startMs).toFloat() / (on.endMs - on.startMs)).coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Color(0xFFE5484D)),
                )
            }
        }
    }
}

/** Pick which channel types show on the Channels page and in the side guide (saved per profile) */
@Composable
private fun HideTypesDialog(
    groups: List<String>,
    hidden: Set<String>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .background(Color(0xFF17171B), RoundedCornerShape(12.dp))
                    .padding(18.dp),
        ) {
            Text("Channel types", fontFamily = BenflixTitleFont, fontSize = 26.sp, color = Color.White)
            Text("Turn off the types you never watch.", fontSize = 13.sp, color = Muted)
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(80)
                first.tryRequestFocus()
            }
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(360.dp),
            ) {
                items(groups, key = { it }) { group ->
                    val shown = group !in hidden
                    Surface(
                        onClick = { ChannelPrefs.setHidden(group, shown) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF222228),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black,
                            ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (group == groups.first()) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(groupLabel(group), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                text = if (shown) "Shown" else "Hidden",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (shown) Color(0xFF3FBF7A) else Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val NUMBER_WAIT_MS = 1_500L
