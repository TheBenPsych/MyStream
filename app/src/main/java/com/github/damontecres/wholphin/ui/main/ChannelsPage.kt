package com.github.damontecres.wholphin.ui.main

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BENFLIX_GROUP
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelProgram
import com.github.damontecres.wholphin.services.ChannelReminder
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.ChannelSession
import com.github.damontecres.wholphin.services.ChannelTileOrder
import com.github.damontecres.wholphin.services.CustomChannel
import com.github.damontecres.wholphin.services.DiskCache
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.MakeChannelPick
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import com.github.damontecres.wholphin.ui.theme.BenflixMotion
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
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

        private val pickPrefs get() = ChannelPrefs.prefsOrNull()

        /** This profile already saw "Pick your channels" on this device */
        fun pickedBefore(): Boolean = pickPrefs?.getBoolean("picked_" + serverRepository.currentUser?.id, false) ?: true

        fun markPicked() {
            pickPrefs?.edit()?.putBoolean("picked_" + serverRepository.currentUser?.id, true)?.apply()
        }

        init {
            reload()
        }

        private fun reload() {
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

        /** Make a channel, step 2: the shows, genres or collections on the server, A to Z */
        suspend fun loadPicks(kind: CustomChannel.Kind): List<MakeChannelPick> =
            withContext(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id
                try {
                    when (kind) {
                        CustomChannel.Kind.GENRE -> {
                            api.genresApi
                                .getGenres(userId = userId, includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES))
                                .content.items
                                .mapNotNull { dto -> dto.name?.let { MakeChannelPick(null, it) } }
                                .distinctBy { it.name.lowercase() }
                                .sortedBy { it.name.lowercase() }
                        }

                        else -> {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        includeItemTypes =
                                            listOf(if (kind == CustomChannel.Kind.SHOW) BaseItemKind.SERIES else BaseItemKind.BOX_SET),
                                        recursive = true,
                                        sortBy = listOf(ItemSortBy.SORT_NAME),
                                        enableImages = false,
                                        enableUserData = false,
                                        limit = 3000,
                                    ),
                                ).content.items
                                .map { MakeChannelPick(it.id, it.name.orEmpty()) }
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not list things for Make a channel")
                    emptyList()
                }
            }

        /** Saves a made channel and builds today's guide again with it in */
        fun makeChannel(channel: CustomChannel) {
            ChannelPrefs.addMine(channel)
            reload()
        }

        fun removeChannel(channel: CustomChannel) {
            ChannelPrefs.removeMine(channel.id)
            reload()
        }
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
    easy: Boolean = false,
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
        Text("No channels yet. Add some movies or shows first.", fontSize = BenflixType.lead, modifier = modifier.padding(32.dp))
        return
    }
    // Easy mode: three big choices first (My channels opens the normal page)
    var easyChoice by rememberSaveable { mutableStateOf(!easy) }
    if (!easyChoice && !KidsMode.isActive) {
        EasyChoices(
            list = list,
            onMyChannels = { easyChoice = true },
            onWatch = viewModel::watch,
            modifier = modifier,
        )
        return
    }
    // Benflix redesign: one page. The top area describes what's focused; Favourites, Recently
    // watched and Benflix channels rows come first, then Down carries on into the full guide.
    // Hidden channel types ("Edit types") are left out of the guide.
    val hidden by ChannelPrefs.hidden.collectAsState()
    val shown = remember(list, hidden) { list.filter { it.group() !in hidden } }
    BoxWithConstraints(modifier = modifier) {
        // Two hours across on a TV, an hour and a half on a phone
        val halfHours = if (maxWidth < 600.dp) 3L else GUIDE_HOURS * 2
        val dpm = ((maxWidth - CHANNEL_COL - 24.dp).value / (halfHours * 30f)).coerceAtLeast(1f)
        GuideGrid(all = list, list = shown, halfHours = halfHours, dpm = dpm, viewModel = viewModel)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GuideGrid(
    all: List<ChannelSchedule>,
    list: List<ChannelSchedule>,
    halfHours: Long,
    dpm: Float,
    viewModel: ChannelsViewModel,
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
    val byKey = remember(all) { all.associateBy { it.key } }
    val favouriteChannels = remember(favourites, byKey) { favourites.mapNotNull { byKey[it] } }
    val recentKeys by ChannelPrefs.recent.collectAsState()
    val recentChannels = remember(recentKeys, byKey) { recentKeys.mapNotNull { byKey[it] } }
    val benflixChannels = remember(all) { all.filter { it.group() == BENFLIX_GROUP } }
    val onRowFocus = { schedule: ChannelSchedule ->
        schedule.programAt(System.currentTimeMillis())?.let { focused = schedule to it }
    }
    var digits by remember { mutableStateOf("") }
    var showTypes by remember { mutableStateOf(false) }
    var addChannels by remember { mutableStateOf(false) }
    var makeChannel by remember { mutableStateOf(false) }
    var mineMenu by remember { mutableStateOf<ChannelSchedule?>(null) }
    val mine by ChannelPrefs.mine.collectAsState()
    var favouriteMenu by remember { mutableStateOf<ChannelSchedule?>(null) }
    var laterMenu by remember { mutableStateOf<Pair<ChannelSchedule, ChannelProgram>?>(null) }
    val reminders by ChannelPrefs.reminders.collectAsState()
    // Number buttons anywhere on the page: wait a moment for more digits, then turn that channel on
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(NUMBER_WAIT_MS)
        val number = digits.toIntOrNull()
        digits = ""
        list.firstOrNull { it.number == number }?.let(viewModel::watch)
    }
    val rowFocus = remember { FocusRequester() }
    var declinedNow by remember { mutableStateOf(emptySet<String>()) }
    val suggested =
        remember(recentKeys, favourites, byKey, declinedNow) {
            ChannelPrefs.suggestion(recentKeys)?.takeIf { it !in declinedNow }?.let { byKey[it] }
        }
    val hasRows = favouriteChannels.isNotEmpty() || benflixChannels.isNotEmpty() || recentChannels.isNotEmpty()
    LaunchedEffect(Unit) {
        delay(80)
        // Opens on the first favourite (or the first Benflix channel when there are none yet)
        if (hasRows) rowFocus.tryRequestFocus() else firstFocus.tryRequestFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .padding(top = 12.dp, start = 8.dp)
                .onPreviewKeyEvent { event ->
                    val digit = digitOf(event) ?: return@onPreviewKeyEvent false
                    if (addChannels || makeChannel) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp && digits.length < 4) digits += digit
                    true
                },
    ) {
        // What the focused channel or program is. Read only inside, so moving around redraws just this part
        GuideHeader(focused = { if (isTv) focused else selected ?: focused }, now = now)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Favourites (with "+ Add channels"), Recently watched, then Benflix's own channels
            item(key = "row-favourites") {
                ChannelTileRow(title = "Favourites") {
                    items(favouriteChannels, key = { "f-${it.key}" }) { schedule ->
                        RecentCard(
                            schedule = schedule,
                            width = if (isTv) 220.dp else 160.dp,
                            onClick = { viewModel.watch(schedule) },
                            onLongClick = { favouriteMenu = schedule },
                            modifier =
                                Modifier
                                    .then(if (schedule == favouriteChannels.first()) Modifier.focusRequester(rowFocus) else Modifier)
                                    .onFocusChanged { if (it.isFocused) onRowFocus(schedule) },
                        )
                    }
                    // Watched a channel 3 times and it isn't a favourite yet: offer to add it
                    suggested?.let { schedule ->
                        item(key = "suggest-${schedule.key}") {
                            SuggestFavouriteTile(
                                name = schedule.title,
                                onAdd = { ChannelPrefs.toggleFavourite(schedule.key) },
                                onNo = {
                                    ChannelPrefs.declineSuggestion(schedule.key)
                                    declinedNow = declinedNow + schedule.key
                                },
                            )
                        }
                    }
                    item(key = "add") {
                        AddChannelsTile(
                            onClick = { addChannels = true },
                            modifier =
                                if (favouriteChannels.isEmpty() &&
                                    benflixChannels.isEmpty()
                                ) {
                                    Modifier.focusRequester(rowFocus)
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
            if (recentChannels.isNotEmpty()) {
                item(key = "row-recent") {
                    ChannelTileRow(title = "Recently watched") {
                        items(recentChannels, key = { "r-${it.key}" }) { schedule ->
                            RecentCard(
                                schedule = schedule,
                                width = if (isTv) 220.dp else 160.dp,
                                onClick = { viewModel.watch(schedule) },
                                onLongClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                                modifier = Modifier.onFocusChanged { if (it.isFocused) onRowFocus(schedule) },
                            )
                        }
                    }
                }
            }
            run {
                item(key = "row-benflix") {
                    ChannelTileRow(title = "Benflix channels · No ads") {
                        items(benflixChannels, key = { "b-${it.key}" }) { schedule ->
                            RecentCard(
                                schedule = schedule,
                                width = if (isTv) 220.dp else 160.dp,
                                onClick = { viewModel.watch(schedule) },
                                onLongClick = {
                                    // A home-made channel can also be taken away again
                                    if (schedule.custom != null) mineMenu = schedule else ChannelPrefs.toggleFavourite(schedule.key)
                                },
                                modifier =
                                    Modifier
                                        .then(
                                            if (favouriteChannels.isEmpty() && schedule == benflixChannels.first()) {
                                                Modifier.focusRequester(rowFocus)
                                            } else {
                                                Modifier
                                            },
                                        ).onFocusChanged { if (it.isFocused) onRowFocus(schedule) },
                            )
                        }
                        // Approved "Make a channel": the last tile in the Benflix channels row
                        if (mine.size < CustomChannel.MAX) {
                            item(key = "make") {
                                AddChannelsTile(
                                    label = "+  Make a channel",
                                    onClick = { makeChannel = true },
                                )
                            }
                        }
                    }
                }
            }
            // The full guide: a heading with "Edit types", then the time ruler that stays at the top
            item(key = "guide-title") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(
                        text = "GUIDE",
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = Color(0xFFB8B8C0),
                    )
                    if (all.any { it.live != null }) {
                        Surface(
                            onClick = { showTypes = true },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF17171B),
                                    contentColor = Color(0xFFB8B8C0),
                                    focusedContainerColor = BenflixColors.LiveTv,
                                    focusedContentColor = BenflixColors.OnLiveTv,
                                ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        ) {
                            Text(
                                text = "Edit types",
                                fontSize = BenflixType.tag,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            stickyHeader(key = "ruler") {
                Row(Modifier.background(BenflixColors.Tray).padding(vertical = 2.dp)) {
                    Box(Modifier.width(CHANNEL_COL))
                    var t = windowStart
                    while (t < windowEnd) {
                        Text(
                            text = clock(t),
                            fontSize = BenflixType.tag,
                            color = Color(0xFF8B8B94),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(widthFor(HALF_HOUR)),
                        )
                        t += HALF_HOUR
                    }
                }
            }
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
                                fontSize = BenflixType.small,
                                color = if (starred) StarGold else BenflixColors.Muted,
                            )
                            Text(
                                text = "${schedule.number}",
                                fontFamily = BenflixFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = BenflixType.rowName,
                                textAlign = TextAlign.Center,
                            )
                            val live = schedule.live
                            ChannelLogo(schedule, size = 24.dp)
                            Column {
                                Text(
                                    text = schedule.title,
                                    fontSize = BenflixType.tag,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = BenflixType.tag * 1.3f,
                                    maxLines = if (live == null) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (live == null) {
                                    Text(
                                        text = "NO ADS",
                                        fontSize = BenflixType.tag,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = .8.sp,
                                        color = NoAdsGreen,
                                        lineHeight = BenflixType.tag * 1.3f,
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
                                    } else if (live) {
                                        viewModel.watch(schedule)
                                    } else {
                                        // A later show: Remind me, watch the channel now, or star it
                                        laterMenu = schedule to program
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
                                            focusedBorder =
                                                Border(
                                                    BorderStroke(3.dp, BenflixColors.Text),
                                                    shape = RoundedCornerShape(6.dp),
                                                ),
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
                                    val bell = reminders.any { it.key == schedule.key && it.startMs == program.startMs }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (bell) {
                                            com.github.damontecres.wholphin.ui.components.BenflixIconImage(
                                                com.github.damontecres.wholphin.ui.components.BenflixIcon.BELL,
                                                color = LocalContentColor.current,
                                                size = 13.dp,
                                                modifier = Modifier.padding(end = 4.dp),
                                            )
                                        }
                                        Text(
                                            text = program.name(),
                                            fontSize = BenflixType.small,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (!program.noGuide) {
                                        Text(
                                            text = clock(program.startMs),
                                            fontSize = BenflixType.tag,
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
    if (showTypes) {
        val groups = remember(all) { all.map { it.group() }.distinct() }
        val hiddenNow by ChannelPrefs.hidden.collectAsState()
        HideTypesDialog(groups = groups, hidden = hiddenNow) { showTypes = false }
    }
    if (addChannels) {
        AddChannelsDialog(list = all, favourites = favourites, onDismiss = { addChannels = false })
    }
    if (makeChannel) {
        MakeChannelFlow(
            loadPicks = viewModel::loadPicks,
            onMake = {
                makeChannel = false
                viewModel.makeChannel(it)
            },
            onDismiss = { makeChannel = false },
        )
    }
    mineMenu?.let { schedule ->
        val custom = schedule.custom
        if (custom != null) {
            val starred = schedule.key in favourites
            BenflixChoiceDialog(
                title = schedule.title,
                choices =
                    listOf(
                        BenflixChoice(
                            label = if (starred) "Take off Favourites" else "Add to Favourites",
                            onClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                        ),
                        BenflixChoice("Remove this channel", { viewModel.removeChannel(custom) }),
                    ),
                onDismissRequest = { mineMenu = null },
            )
        }
    }
    // Approved: a new profile's first visit opens "Pick your channels" (once per profile on this device)
    var picking by remember { mutableStateOf(!KidsMode.isActive && favourites.isEmpty() && !viewModel.pickedBefore()) }
    if (picking) {
        AddChannelsDialog(
            list = all.filter { it.live != null },
            favourites = favourites,
            onDismiss = {
                picking = false
                viewModel.markPicked()
            },
            picking = true,
        )
    }
    laterMenu?.let { (schedule, program) ->
        val reminded = reminders.any { it.key == schedule.key && it.startMs == program.startMs }
        val starred = schedule.key in favourites
        BenflixChoiceDialog(
            title = program.name(),
            subtitle = "${schedule.number}  ${schedule.title}  ·  ${clock(program.startMs)} – ${clock(program.endMs)}",
            choices =
                listOfNotNull(
                    if (KidsMode.isActive) {
                        null
                    } else {
                        BenflixChoice(
                            label = if (reminded) "Cancel the reminder" else "Remind me",
                            note = if (reminded) null else "A small note shows a minute before it starts",
                            onClick = { ChannelPrefs.toggleReminder(ChannelReminder(schedule.key, program.startMs, program.name())) },
                        )
                    },
                    if (KidsMode.isActive || program.noGuide) {
                        null
                    } else {
                        val every = ChannelPrefs.isEveryTime(schedule.key, program.name())
                        BenflixChoice(
                            label = if (every) "Stop reminding me every time" else "Remind me every time",
                            note = if (every) null else "Every time ${program.name()} is on this channel",
                            onClick = {
                                ChannelPrefs.toggleEveryTime(schedule.key, program.name())
                                ChannelPrefs.addEveryTime(ChannelSession.lineup)
                            },
                        )
                    },
                    BenflixChoice("Watch this channel now", { viewModel.watch(schedule) }),
                    BenflixChoice(
                        if (starred) "Remove from favourites" else "Add to favourites",
                        { ChannelPrefs.toggleFavourite(schedule.key) },
                    ),
                    if (schedule.live == null) {
                        BenflixChoice("Open its page", { viewModel.navigationManager.navigateTo(program.item.destination()) })
                    } else {
                        null
                    },
                ),
            onDismissRequest = { laterMenu = null },
        )
    }
    favouriteMenu?.let { schedule ->
        BenflixChoiceDialog(
            title = schedule.title,
            choices =
                listOf(
                    BenflixChoice("Move left", { ChannelPrefs.moveFavourite(schedule.key, -1) }),
                    BenflixChoice("Move right", { ChannelPrefs.moveFavourite(schedule.key, 1) }),
                    BenflixChoice("Remove from favourites", { ChannelPrefs.toggleFavourite(schedule.key) }),
                ),
            onDismissRequest = { favouriteMenu = null },
        )
    }
}

/** A titled row of channel tiles on the Live TV page */
@Composable
private fun ChannelTileRow(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color(0xFFB8B8C0),
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(end = 32.dp),
            content = content,
        )
    }
}

/** The "+ Add channels" tile at the end of Favourites */
@Composable
private fun AddChannelsTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "+  Add channels",
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF17171B),
                contentColor = Color(0xFFB8B8C0),
                focusedContainerColor = Color(0xFF17171B),
                focusedContentColor = Color.White,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), shape = RoundedCornerShape(10.dp)),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.width(160.dp).height(64.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
        }
    }
}

/** "Add <channel>?" with Add and No thanks, after a channel was watched 3 times */
@Composable
private fun SuggestFavouriteTile(
    name: String,
    onAdd: () -> Unit,
    onNo: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .height(64.dp)
                .background(Color(0xFF17171B), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
    ) {
        Text("Add $name?", fontSize = BenflixType.small, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        SuggestButton("Add", onAdd)
        SuggestButton("No thanks", onNo)
    }
}

@Composable
private fun SuggestButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF26262C),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Type a channel's name or number to add it to (or take it off) Favourites */
@Composable
private fun AddChannelsDialog(
    list: List<ChannelSchedule>,
    favourites: List<String>,
    onDismiss: () -> Unit,
    picking: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    // "Pick your channels" (a new profile's first visit) lists every channel by type before you type
    val results =
        remember(query, list) {
            if (picking && query.isBlank()) {
                list.sortedBy { ChannelTileOrder.indexOf(it.group()).let { i -> if (i < 0) 99 else i } }
            } else {
                searchChannels(list, query).take(30)
            }
        }
    com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .background(BenflixColors.Panel, RoundedCornerShape(16.dp))
                    .padding(18.dp),
        ) {
            Text(
                if (picking) "Pick your channels" else "Add channels",
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                color = BenflixColors.Text,
            )
            Text(
                if (picking) {
                    "Star the ones you like. They'll be first on this page. Press Back when you're done."
                } else {
                    "Type a channel's name or number, then pick it."
                },
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
            )
            val box = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(80)
                box.tryRequestFocus()
            }
            com.github.damontecres.wholphin.ui.components.SearchEditTextBox(
                value = query,
                onValueChange = { query = it },
                onSearchClick = {},
                modifier = Modifier.fillMaxWidth().focusRequester(box),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(320.dp),
            ) {
                items(results, key = { it.key }) { schedule ->
                    val starred = schedule.key in favourites
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelRow(
                            schedule,
                            onClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (starred) "★" else "☆",
                            fontSize = BenflixType.lead,
                            color = if (starred) StarGold else BenflixColors.Muted,
                            modifier = Modifier.padding(start = 10.dp),
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
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = if (live) BenflixRed else Color(0xFF8B8B94),
            )
            Text(
                text = program.name(),
                fontWeight = FontWeight.Bold,
                fontSize = if (isTv) BenflixType.pageTitle else BenflixType.lead,
                lineHeight = if (isTv) 32.sp else 22.sp,
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
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            program.item.data.overview?.let {
                Text(
                    text = it,
                    fontSize = BenflixType.small,
                    color = BenflixColors.Soft,
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
                            .background(Color(0xFF333338), RoundedCornerShape(6.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(BenflixRed, RoundedCornerShape(6.dp)),
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
                    .background(Color(0xFF17171B), RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp)),
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
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = BenflixColors.OnLiveTv,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(BenflixRed, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * Benflix redesign (wt-banner): the watching banner along the bottom after you change channel. The
 * channel number and name (with a star if it's a favourite), what's on with the time left and a thin
 * line, and what's next. While the channel starts it says "Tuning in…"; it hides 5 seconds after
 * the picture starts.
 */
@Composable
fun ChannelBanner(
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val banner by ChannelSession.banner.collectAsState()
    LaunchedEffect(banner, playing) {
        if (banner != null) {
            // If the channel never starts, don't leave "Tuning in…" up forever
            delay(if (playing) BANNER_MS else 20_000L)
            ChannelSession.clearBanner()
        }
    }
    val favourites by ChannelPrefs.favourites.collectAsState()
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn(BenflixMotion.enter()),
        exit = fadeOut(BenflixMotion.exit()),
        modifier = modifier,
    ) {
        val info = banner ?: return@AnimatedVisibility
        val now = remember(info) { System.currentTimeMillis() }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                    .padding(start = BenflixSpace.tvEdge, end = BenflixSpace.tvEdge, top = 40.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(info.number.toString(), fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(info.name, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                if (info.key in favourites) Text("★", fontSize = BenflixType.rowName, color = Color.White)
                if (info.own) {
                    Text(
                        text = "NO ADS",
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = .6.sp,
                        color = Color(0xFF04150B),
                        modifier =
                            Modifier
                                .background(NoAdsGreen, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            if (!playing) {
                Text("Tuning in…", fontSize = BenflixType.lead, color = BenflixColors.Soft)
            } else if (info.now != null) {
                val left = ((info.endMs - now) / 60_000L).coerceAtLeast(0L)
                Text(
                    text = "${info.now}  ·  ${if (left >= 60) "${left / 60} h ${left % 60} min" else "$left min"} left",
                    fontSize = BenflixType.lead,
                    color = Color.White,
                    maxLines = 1,
                )
                val length = (info.endMs - info.startMs).coerceAtLeast(1L)
                Box(
                    Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = .25f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(((now - info.startMs).toFloat() / length).coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Color.White),
                    )
                }
                info.next?.let { next ->
                    val at =
                        java.time.Instant
                            .ofEpochMilli(info.nextStartMs)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                            .format(
                                java.time.format.DateTimeFormatter
                                    .ofPattern("h:mm"),
                            )
                    Text("Next at $at  ·  $next", fontSize = BenflixType.small, color = BenflixColors.Muted, maxLines = 1)
                }
            }
        }
    }
}

private const val HALF_HOUR = 30L * 60_000L
private const val GUIDE_HOURS = 2L
private const val NUMBER_WAIT_MS = 1_500L
private const val BANNER_MS = 5_000L
private val CHANNEL_COL = 176.dp
private val HEADER_HEIGHT = 170.dp

/** On phones the header (and so its 16:9 picture) is shorter, leaving room for the title */
private val PHONE_HEADER_HEIGHT = 110.dp
private val BenflixRed = BenflixColors.LiveTv

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
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = .6.sp,
            color = if (own) Color(0xFF04150B) else Color.White,
            modifier =
                Modifier
                    .background(if (own) NoAdsGreen else Color(0xFF3A3A42), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Text(
            text = if (own) "BENFLIX CHANNELS · FROM YOUR SERVER" else name.uppercase(),
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color(0xFF8B8B94),
        )
    }
}

/** Easy mode's Live TV: three big buttons (approved f2-easy) */
@Composable
private fun EasyChoices(
    list: List<ChannelSchedule>,
    onMyChannels: () -> Unit,
    onWatch: (ChannelSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val news = remember(list) { list.firstOrNull { it.group() == "News" } }
    val benflix = remember(list) { list.firstOrNull { it.group() == BENFLIX_GROUP } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        first.tryRequestFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().padding(24.dp),
    ) {
        EasyButton("My channels", "Your favourites and the full guide", onMyChannels, Modifier.focusRequester(first))
        if (news != null) EasyButton("Put the news on", news.title, { onWatch(news) })
        if (benflix != null) EasyButton("Benflix channels", "No ads, from your own movies and shows", { onWatch(benflix) })
    }
}

@Composable
private fun EasyButton(
    title: String,
    note: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp)) {
            Text(title, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold)
            Text(note, fontSize = BenflixType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
