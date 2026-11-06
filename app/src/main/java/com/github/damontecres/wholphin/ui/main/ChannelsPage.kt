package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BENFLIX_GROUP
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelProgram
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.ChannelSession
import com.github.damontecres.wholphin.services.DiskCache
import com.github.damontecres.wholphin.services.FAVOURITES_GROUP
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val diskCache: DiskCache,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** null while loading */
        private val _channels = MutableStateFlow<List<ChannelSchedule>?>(null)
        val channels: StateFlow<List<ChannelSchedule>?> = _channels

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                _channels.value =
                    try {
                        ChannelSession.load(
                            api,
                            serverRepository.currentUser?.id,
                            KidsMode.isActive,
                            diskCache,
                            // The guide shows up as soon as the first channels are ready
                            onProgress = { if (it.isNotEmpty()) _channels.value = it },
                        )
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Channels failed")
                        emptyList()
                    }
            }
        }

        fun watch(schedule: ChannelSchedule) = ChannelSession.tune(navigationManager, schedule)
    }

/** What a program is called in the guide: the show's name for an episode */
internal fun ChannelProgram.name(): String =
    if (noGuide) {
        "Live"
    } else if (item.type == BaseItemKind.EPISODE) {
        item.data.seriesName ?: item.title.orEmpty()
    } else {
        item.title.orEmpty()
    }

/** A row of the guide: a group heading, or a channel */
private sealed interface GuideEntry {
    data class Heading(
        val name: String,
    ) : GuideEntry

    data class Channel(
        val schedule: ChannelSchedule,
    ) : GuideEntry
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun clock(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * Channels: a TV guide of Benflix channels made from your own library (no ads, always something on),
 * then the server's free live TV channels grouped by type, with chips up top to jump to a group.
 * Pick what's on now to jump in where the channel is; in the player, Up/Down changes channel.
 */
@Composable
fun ChannelsPage(
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsState()
    // Back in the guide means you're not flipping channels any more
    LaunchedEffect(Unit) { ChannelSession.stopWatching() }
    val list = channels
    if (list == null) {
        LoadingPage(modifier.fillMaxSize())
        return
    }
    if (list.isEmpty()) {
        Text("No channels yet. Add some movies or shows first.", fontSize = 18.sp, modifier = modifier.padding(32.dp))
        return
    }
    // Kids (and servers without Live TV) only have Benflix's own channels: just the guide
    val hasLive = remember(list) { list.any { it.live != null } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOpened by rememberSaveable { mutableStateOf<String?>(null) }
    val favourites by ChannelPrefs.favourites.collectAsState()
    val shown =
        remember(list, open, favourites) {
            when (val group = open) {
                null -> list
                FAVOURITES_GROUP -> list.filter { it.key in favourites }
                else -> list.filter { it.group() == group }
            }
        }
    if (hasLive && open == null) {
        ChannelHub(
            list = list,
            lastOpened = lastOpened,
            onOpen = {
                open = it
                lastOpened = it
            },
            onWatch = viewModel::watch,
            modifier = modifier,
        )
        return
    }
    BackHandler(enabled = hasLive) { open = null }
    if (shown.isEmpty()) {
        Text("No channels here yet.", fontSize = 18.sp, modifier = modifier.padding(32.dp))
        return
    }
    BoxWithConstraints(modifier = modifier) {
        // Three hours on a TV, an hour and a half on a phone
        val halfHours = if (maxWidth < 600.dp) 3L else GUIDE_HOURS * 2
        val dpm = ((maxWidth - CHANNEL_COL - 24.dp).value / (halfHours * 30f)).coerceAtLeast(1f)
        GuideGrid(shown, halfHours, dpm, viewModel, title = open, onBack = { open = null })
    }
}

@Composable
private fun GuideGrid(
    list: List<ChannelSchedule>,
    halfHours: Long,
    dpm: Float,
    viewModel: ChannelsViewModel,
    title: String?,
    onBack: () -> Unit,
) {
    fun widthFor(ms: Long): Dp = (ms / 60_000f * dpm).dp

    // The guide starts at the last half hour and shows the next few hours. The clock ticks each
    // minute, so "on now" and the red line keep up while the guide stays open
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    val windowStart = now - now % HALF_HOUR
    val windowEnd = windowStart + halfHours * HALF_HOUR
    var focused by remember { mutableStateOf<Pair<ChannelSchedule, ChannelProgram>?>(null) }
    // Phones have no remote focus: the first tap on a program picks it (shown up top with a white
    // border) and a second tap on the same one plays or opens it
    val isTv = rememberIsTv()
    var selected by remember { mutableStateOf<Pair<ChannelSchedule, ChannelProgram>?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        firstFocus.tryRequestFocus()
    }
    // Headings between the types (only when more than one shows, like in Favourites)
    val severalTypes = remember(list) { list.map { it.group() }.distinct().size > 1 }
    val favourites by ChannelPrefs.favourites.collectAsState()
    val entries =
        remember(list) {
            buildList<GuideEntry> {
                var last: String? = null
                list.forEach { schedule ->
                    val group = schedule.group()
                    if (severalTypes && group != last) add(GuideEntry.Heading(group))
                    last = group
                    add(GuideEntry.Channel(schedule))
                }
            }
        }
    val listState = rememberLazyListState()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 12.dp, start = 8.dp),
    ) {
        // What the focused program is. Read only inside, so moving around the guide redraws just this part
        GuideHeader(focused = { if (isTv) focused else selected ?: focused }, now = now)
        if (title != null) {
            // Back to all the channel types (the Back button does the same)
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF17171B),
                        contentColor = Color(0xFFB8B8C0),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    text = "‹  All channel types  ·  ${groupLabel(title)}  ·  ${list.size} channels",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        // Time ruler
        Row {
            Box(Modifier.width(CHANNEL_COL))
            var t = windowStart
            while (t < windowEnd) {
                Text(
                    text = clock(t),
                    fontSize = 12.sp,
                    color = Color(0xFF8B8B94),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(widthFor(HALF_HOUR)),
                )
                t += HALF_HOUR
            }
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                entries,
                key = {
                    when (it) {
                        is GuideEntry.Heading -> {
                            "h-${it.name}"
                        }

                        is GuideEntry.Channel -> {
                            it.schedule.live
                                ?.id
                                ?.toString() ?: "b-${it.schedule.number}"
                        }
                    }
                },
            ) { entry ->
                if (entry is GuideEntry.Heading) {
                    GroupHeading(entry.name)
                    return@items
                }
                val schedule = (entry as GuideEntry.Channel).schedule
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(ROW_HEIGHT),
                ) {
                    // Channel number, logo or badge, and the name. Press it to star the channel
                    val starred = schedule.key in favourites
                    Surface(
                        onClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF17171B),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFF3A3A42),
                                focusedContentColor = Color.White,
                            ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.width(CHANNEL_COL).fillMaxHeight(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        ) {
                            Text(
                                text = if (starred) "★" else "☆",
                                fontSize = 13.sp,
                                color = if (starred) StarGold else Color(0xFF55555C),
                            )
                            Text(
                                text = "${schedule.number}",
                                fontFamily = BenflixTitleFont,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center,
                            )
                            val live = schedule.live
                            ChannelLogo(schedule, size = 24.dp)
                            Column {
                                Text(
                                    text = schedule.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 14.sp,
                                    maxLines = if (live == null) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (live == null) {
                                    Text(
                                        text = "NO ADS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = .8.sp,
                                        color = NoAdsGreen,
                                        lineHeight = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.width(widthFor(windowEnd - windowStart)).fillMaxHeight()) {
                        val shownPrograms = remember(schedule, windowStart) { schedule.between(windowStart, windowEnd) }
                        shownPrograms.forEach { program ->
                            val from = maxOf(program.startMs, windowStart)
                            val to = minOf(program.endMs, windowEnd)
                            val live = now >= program.startMs && now < program.endMs
                            val isFirst = schedule == list.first() && live
                            val isSelected =
                                !isTv &&
                                    selected?.let { (s, p) -> s.number == schedule.number && p.startMs == program.startMs } == true
                            Surface(
                                // Hold OK (or press and hold on a phone) to star the channel
                                onLongClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                                onClick = {
                                    if (!isTv && !isSelected) {
                                        selected = schedule to program
                                    } else if (live || schedule.live != null) {
                                        // A live channel always plays what's on now
                                        viewModel.watch(schedule)
                                    } else {
                                        viewModel.navigationManager.navigateTo(program.item.destination())
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                colors =
                                    ClickableSurfaceDefaults.colors(
                                        containerColor = if (live) Color(0xFF26262D) else Color(0xFF1C1C21),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black,
                                    ),
                                border =
                                    if (isSelected) {
                                        ClickableSurfaceDefaults.border(
                                            border = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(6.dp)),
                                            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(6.dp)),
                                        )
                                    } else {
                                        ClickableSurfaceDefaults.border()
                                    },
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier =
                                    Modifier
                                        .offset(x = widthFor(from - windowStart))
                                        .width(widthFor(to - from) - 3.dp)
                                        .fillMaxHeight()
                                        .then(if (isFirst) Modifier.focusRequester(firstFocus) else Modifier)
                                        .onFocusChanged { if (it.isFocused) focused = schedule to program },
                            ) {
                                // On now: a thin red bar along the bottom up to where the show is
                                if (live && to > from) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxHeight()
                                                .wrapContentHeight(Alignment.Bottom)
                                                .fillMaxWidth(((now - from).toFloat() / (to - from)).coerceIn(0f, 1f))
                                                .height(3.dp)
                                                .background(BenflixRed),
                                    )
                                }
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = program.name(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!program.noGuide) {
                                        Text(
                                            text = clock(program.startMs),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        // The "now" line
                        Box(
                            modifier =
                                Modifier
                                    .offset(x = widthFor(now - windowStart))
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(BenflixRed),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideHeader(
    focused: () -> Pair<ChannelSchedule, ChannelProgram>?,
    now: Long,
) {
    val imageUrlService = LocalImageUrlService.current
    // Phones get a smaller picture and title, so the title isn't squeezed by a TV-sized picture
    val isTv = rememberIsTv()
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 28.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (isTv) HEADER_HEIGHT else PHONE_HEADER_HEIGHT)
                .padding(end = if (isTv) 32.dp else 8.dp),
    ) {
        val pair = focused()
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            if (pair == null) return@Column
            val (schedule, program) = pair
            val live = now >= program.startMs && now < program.endMs
            Text(
                text =
                    if (program.noGuide) {
                        "${schedule.number}  ·  ${schedule.title.uppercase()}  ·  LIVE"
                    } else {
                        "${schedule.number}  ·  ${schedule.title.uppercase()}  ·  " +
                            (if (live) "ON NOW  ·  " else "") +
                            "${clock(program.startMs)} – ${clock(program.endMs)}" +
                            (if (schedule.live == null) "  ·  NO ADS" else "")
                    },
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = if (live) BenflixRed else Color(0xFF8B8B94),
            )
            Text(
                text = program.name().uppercase(),
                fontFamily = BenflixTitleFont,
                fontSize = if (isTv) 36.sp else 22.sp,
                lineHeight = if (isTv) 36.sp else 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (program.item.type == BaseItemKind.EPISODE) {
                val season = program.item.data.parentIndexNumber
                val episode = program.item.data.indexNumber
                Text(
                    text =
                        listOfNotNull(
                            if (season != null && episode != null) "S$season E$episode" else null,
                            program.item.name,
                        ).joinToString("  ·  "),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            program.item.data.overview?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = .72f),
                    maxLines = if (isTv) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 620.dp),
                )
            }
            if (live && !program.noGuide) {
                val progress = ((now - program.startMs).toFloat() / (program.endMs - program.startMs)).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .width(220.dp)
                            .height(4.dp)
                            .background(Color(0xFF333338), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(BenflixRed, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        // The picture window: the episode's own still, or the movie's backdrop
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF17171B), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            if (pair != null) {
                val (schedule, program) = pair
                // The guide loads its shows without picture info (to stay fast), so ask the server
                // for pictures by the show's id alone, trying the next kind if one is missing
                val urls =
                    remember(program.item.id) {
                        val id = program.item.id
                        val seriesId = program.item.data.seriesId

                        fun url(
                            itemId: java.util.UUID,
                            type: ImageType,
                        ) = imageUrlService.getItemImageUrl(
                            itemId = itemId,
                            imageType = type,
                            fillWidth = 480,
                            fillHeight = 270,
                        )
                        val liveId = schedule.live?.id
                        if (liveId != null) {
                            // A live show's own picture, else the station's logo
                            listOfNotNull(
                                if (program.noGuide) null else url(id, ImageType.PRIMARY),
                                url(liveId, ImageType.PRIMARY),
                            )
                        } else if (program.item.type == BaseItemKind.EPISODE) {
                            listOfNotNull(
                                url(id, ImageType.PRIMARY),
                                seriesId?.let { url(it, ImageType.BACKDROP) },
                                seriesId?.let { url(it, ImageType.THUMB) },
                                seriesId?.let { url(it, ImageType.PRIMARY) },
                            )
                        } else {
                            listOfNotNull(
                                url(id, ImageType.BACKDROP),
                                url(id, ImageType.THUMB),
                                url(id, ImageType.PRIMARY),
                            )
                        }
                    }
                var attempt by remember(program.item.id) { mutableIntStateOf(0) }
                val isLogo = schedule.live != null && attempt == urls.lastIndex
                AsyncImage(
                    model = urls.getOrNull(attempt),
                    contentDescription = null,
                    contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                    onError = { if (attempt < urls.lastIndex) attempt++ },
                    modifier = Modifier.fillMaxSize(),
                )
                if (now >= program.startMs && now < program.endMs) {
                    Text(
                        text = "ON NOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(BenflixRed, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * The little "3  Sitcoms · The Office" card in the corner of the player after you change channel
 */
@Composable
fun ChannelBanner(modifier: Modifier = Modifier) {
    val banner by ChannelSession.banner.collectAsState()
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(BANNER_MS)
            ChannelSession.clearBanner()
        }
    }
    AnimatedVisibility(visible = banner != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text = banner.orEmpty(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier =
                Modifier
                    .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

private const val HALF_HOUR = 30L * 60_000L
private const val GUIDE_HOURS = 3L
private const val BANNER_MS = 4_000L
private val CHANNEL_COL = 176.dp
private val HEADER_HEIGHT = 170.dp

/** On phones the header (and so its 16:9 picture) is shorter, leaving room for the title */
private val PHONE_HEADER_HEIGHT = 110.dp
private val BenflixRed = Color(0xFFE5484D)

/** Each channel's badge colours */
internal val BadgeColors =
    listOf(
        Color(0xFF7A2A1C) to Color(0xFFD98B3A),
        Color(0xFF1C3A6B) to Color(0xFF4AA3C9),
        Color(0xFF5A1C5E) to Color(0xFFE05AA0),
        Color(0xFF1D4D3A) to Color(0xFF7AD0A0),
        Color(0xFF3B2A12) to Color(0xFFD9B04A),
        Color(0xFF14444A) to Color(0xFF3FB8C4),
        Color(0xFF4A1A2A) to Color(0xFFD9506E),
    )
private val ROW_HEIGHT = 56.dp
private val NoAdsGreen = Color(0xFF3FBF7A)

/** "No ads · Benflix channels" or "Live · free with ads · News" between the groups */
@Composable
private fun GroupHeading(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    ) {
        val own = name == BENFLIX_GROUP
        Text(
            text = if (own) "NO ADS" else "LIVE · FREE WITH ADS",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = .6.sp,
            color = if (own) Color(0xFF04150B) else Color.White,
            modifier =
                Modifier
                    .background(if (own) NoAdsGreen else Color(0xFF3A3A42), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Text(
            text = if (own) "BENFLIX CHANNELS · FROM YOUR SERVER" else name.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color(0xFF8B8B94),
        )
    }
}
