package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.ChannelProgram
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.ChannelSession
import com.github.damontecres.wholphin.services.DiskCache
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
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
private fun ChannelProgram.name(): String =
    if (item.type == BaseItemKind.EPISODE) item.data.seriesName ?: item.title.orEmpty() else item.title.orEmpty()

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun clock(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * Channels: a TV guide of Benflix channels made from your own library. Something is always on.
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
    BoxWithConstraints(modifier = modifier) {
        // Three hours on a TV, an hour and a half on a phone
        val halfHours = if (maxWidth < 600.dp) 3L else GUIDE_HOURS * 2
        val dpm = ((maxWidth - CHANNEL_COL - 24.dp).value / (halfHours * 30f)).coerceAtLeast(1f)
        GuideGrid(list, halfHours, dpm, viewModel)
    }
}

@Composable
private fun GuideGrid(
    list: List<ChannelSchedule>,
    halfHours: Long,
    dpm: Float,
    viewModel: ChannelsViewModel,
) {
    fun widthFor(ms: Long): Dp = (ms / 60_000f * dpm).dp

    // The guide starts at the last half hour and shows the next few hours
    val now = remember { System.currentTimeMillis() }
    val windowStart = now - now % HALF_HOUR
    val windowEnd = windowStart + halfHours * HALF_HOUR
    var focused by remember { mutableStateOf<Pair<ChannelSchedule, ChannelProgram>?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        firstFocus.tryRequestFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 12.dp, start = 8.dp),
    ) {
        // What the focused program is. Read only inside, so moving around the guide redraws just this part
        GuideHeader(focused = { focused }, now = now)
        // Time ruler
        Row {
            Box(Modifier.width(CHANNEL_COL))
            var t = windowStart
            while (t < windowEnd) {
                Text(
                    text = clock(t),
                    fontSize = 12.sp,
                    color = Color(0xFFA69C9D),
                    modifier = Modifier.width(widthFor(HALF_HOUR)),
                )
                t += HALF_HOUR
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.number }) { schedule ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(ROW_HEIGHT),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .width(CHANNEL_COL)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = .06f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("${schedule.number}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            schedule.channel.title,
                            fontSize = 12.sp,
                            color = Color(0xFFA69C9D),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.width(widthFor(windowEnd - windowStart)).fillMaxHeight()) {
                        val shownPrograms = remember(schedule, windowStart) { schedule.between(windowStart, windowEnd) }
                        shownPrograms.forEach { program ->
                            val from = maxOf(program.startMs, windowStart)
                            val to = minOf(program.endMs, windowEnd)
                            val live = now >= program.startMs && now < program.endMs
                            val isFirst = schedule == list.first() && live
                            Surface(
                                onClick = {
                                    if (live) {
                                        viewModel.watch(schedule)
                                    } else {
                                        viewModel.navigationManager.navigateTo(program.item.destination())
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                colors =
                                    ClickableSurfaceDefaults.colors(
                                        containerColor = if (live) Color(0xFF2A1416) else Color(0xFF1A1616),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black,
                                    ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier =
                                    Modifier
                                        .offset(x = widthFor(from - windowStart))
                                        .width(widthFor(to - from) - 3.dp)
                                        .fillMaxHeight()
                                        .then(if (isFirst) Modifier.focusRequester(firstFocus) else Modifier)
                                        .onFocusChanged { if (it.isFocused) focused = schedule to program },
                            ) {
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
                                    Text(
                                        text = clock(program.startMs),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
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
                                    .background(Color(0xFFE5303A)),
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
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(110.dp),
    ) {
        Text(text = "CHANNELS", fontFamily = BenflixTitleFont, fontSize = 40.sp, letterSpacing = 1.sp)
        focused()?.let { (schedule, program) ->
            val live = now >= program.startMs && now < program.endMs
            Text(
                text =
                    "${schedule.number}  ${schedule.channel.title.uppercase()}  ·  " +
                        "${clock(program.startMs)} - ${clock(program.endMs)}" +
                        if (live) "  ·  ON NOW" else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = if (live) Color(0xFFE5303A) else Color(0xFFA69C9D),
            )
            Text(
                text =
                    program.name() +
                        if (program.item.type == BaseItemKind.EPISODE) "  ·  ${program.item.name.orEmpty()}" else "",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            program.item.data.overview?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = .75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
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
private val CHANNEL_COL = 130.dp
private val ROW_HEIGHT = 56.dp
