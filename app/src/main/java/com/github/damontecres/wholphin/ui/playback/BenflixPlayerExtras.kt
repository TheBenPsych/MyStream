package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.detail.series.NextSeasonAsk
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

/** Benflix: what a button on a player heads-up does */
enum class NoticeAction {
    WATCH_SKIPPED,
    SKIP_FILLER,
    ALWAYS_SKIP_FILLER,
    WATCH_FILLER_ANYWAY,
    STOP_SKIPPING_FILLER,
    LOWER_QUALITY,
    STOP_SKIPPING_INTRO,
    STOP_SKIPPING_OUTRO,
    DISMISS,
}

/**
 * Benflix: a small heads-up card in the player, like "You haven't seen episode 4" or
 * "This is a filler episode". It goes away by itself after [autoHideMs].
 */
data class PlayerNotice(
    val title: String,
    val message: String? = null,
    val actions: List<Pair<String, NoticeAction>> = emptyList(),
    val autoHideMs: Long = 12_000L,
    /** Put the remote on the first button (TV), so it can be pressed straight away */
    val takeFocus: Boolean = actions.isNotEmpty(),
    /** The episode or show this is about (for the button that's pressed) */
    val targetId: UUID? = null,
    val extraIds: List<UUID> = emptyList(),
    /** The "Benflix is busy" note, which goes away once the video starts */
    val busy: Boolean = false,
)

/** Benflix: "You finished the show!" with a few similar shows */
data class ShowDone(
    val series: BaseItem,
    val similar: List<BaseItem>,
    /** The show is still going (more episodes to come), so it's "all caught up" */
    val caughtUp: Boolean,
)

/** Benflix player settings kept on this device */
object BenflixPlayerPrefs {
    private const val FILE = "benflix_player"
    private const val ALWAYS_SKIP_FILLER = "alwaysSkipFiller"
    private const val LOW_CAP = "stutterCap"
    private const val LOW_CAP_DAY = "stutterCapDay"

    /** Filler episodes the person said to play anyway (this app run only) */
    val fillerAllowed: MutableSet<UUID> =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet()

    /** Items already checked for missing subtitles (this app run only) */
    val subtitlesChecked: MutableSet<UUID> =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet()

    /** A heads-up to show when the next video starts (like "Skipped 2 filler episodes") */
    @Volatile var pendingNotice: PlayerNotice? = null

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun alwaysSkipFiller(context: Context) = prefs(context).getBoolean(ALWAYS_SKIP_FILLER, false)

    fun setAlwaysSkipFiller(
        context: Context,
        value: Boolean,
    ) = prefs(context).edit { putBoolean(ALWAYS_SKIP_FILLER, value) }

    /** The lower quality picked after the video kept stopping, only for today (so it never gets stuck low) */
    fun todaysLowerQuality(context: Context): Long? {
        val p = prefs(context)
        if (p.getString(LOW_CAP_DAY, null) != LocalDate.now().toString()) return null
        return p.getLong(LOW_CAP, 0L).takeIf { it > 0 }
    }

    fun setTodaysLowerQuality(
        context: Context,
        bitrate: Long,
    ) = prefs(context).edit {
        putLong(LOW_CAP, bitrate)
        putString(LOW_CAP_DAY, LocalDate.now().toString())
    }

    /** One step down in quality from [current] */
    fun lowerStep(current: Long): Long =
        when {
            current > 6_000_000L -> 4_000_000L
            current > 2_500_000L -> 2_000_000L
            else -> 1_000_000L
        }

    /** Shows like talk shows, news and reality TV don't need to be watched in order */
    fun watchInAnyOrder(genres: List<String>?): Boolean =
        genres.orEmpty().any { g ->
            val name = g.lowercase()
            name.contains("talk") || name.contains("news") || name.contains("reality") || name.contains("game show")
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerNoticeCard(
    notice: PlayerNotice,
    isTv: Boolean,
    onAction: (NoticeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(notice) {
        if (notice.takeFocus && isTv) focusRequester.tryRequestFocus()
        delay(notice.autoHideMs)
        onAction(NoticeAction.DISMISS)
    }
    BackHandler(notice.actions.isNotEmpty()) { onAction(NoticeAction.DISMISS) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .padding(if (isTv) 32.dp else 16.dp)
                .widthIn(max = 520.dp)
                .background(Color(0xF0141418), RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(text = notice.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        notice.message?.let {
            Text(text = it, fontSize = 14.sp, color = Color.White.copy(alpha = .8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (notice.actions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                notice.actions.forEachIndexed { index, (label, action) ->
                    Button(
                        onClick = { onAction(action) },
                        modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

/** Benflix: shown when the last episode of a show ends, instead of just going back */
@Composable
fun ShowDoneCard(
    done: ShowDone,
    isTv: Boolean,
    onOpen: (BaseItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(done) { focusRequester.tryRequestFocus() }
    BackHandler { onClose() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = .88f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .widthIn(max = 900.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(if (isTv) 40.dp else 16.dp),
        ) {
            Text(
                text = if (done.caughtUp) "YOU'RE ALL CAUGHT UP ON" else "YOU FINISHED",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BenflixRed,
            )
            Text(
                text = done.series.name ?: "the show",
                fontSize = if (isTv) 32.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            NextSeasonAsk(seriesId = done.series.id)
            if (done.similar.isNotEmpty()) {
                Text(text = "Want something like it?", fontSize = 16.sp, color = Color.White.copy(alpha = .8f))
                Row(horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp)) {
                    done.similar.forEachIndexed { index, item ->
                        Surface(
                            onClick = { onOpen(item) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                            modifier =
                                Modifier
                                    .width(if (isTv) 150.dp else 100.dp)
                                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                        ) {
                            Column {
                                AsyncImage(
                                    model = LocalImageUrlService.current.rememberImageUrl(item),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray),
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onClose,
                modifier = if (done.similar.isEmpty()) Modifier.focusRequester(focusRequester) else Modifier,
            ) {
                Text("Done")
            }
        }
    }
}
