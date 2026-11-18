package com.github.damontecres.wholphin.services

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import java.util.UUID

/**
 * One thing on a channel, with when it starts and ends (epoch millis).
 * [noGuide]: a live channel with no TV guide, shown as one long "Live" block.
 */
data class ChannelProgram(
    val item: BaseItem,
    val startMs: Long,
    val endMs: Long,
    val noGuide: Boolean = false,
)

/** A free live TV channel from the server's Live TV (see server/benflix_livetv.py) */
data class LiveChannel(
    val id: UUID,
    val name: String,
    val item: BaseItem,
)

/**
 * The live channel types. The server numbers live channels in blocks of 100 by type
 * (keep in step with CATEGORIES in server/benflix_livetv.py). 1000 was Music, now left empty.
 */
val LiveTvGroups =
    listOf(
        100 to "News",
        200 to "Movies",
        300 to "Comedy",
        400 to "Drama & Crime",
        500 to "Reality",
        600 to "Kids & Family",
        700 to "Sports",
        800 to "Sci-Fi & Anime",
        900 to "Classic TV",
        1100 to "Documentaries & Nature",
        1200 to "Food & Home",
        1300 to "Holiday",
    )

/** The group heading a channel goes under in the guide */
fun ChannelSchedule.group(): String = LiveTvGroups.lastOrNull { number >= it.first }?.second ?: "Live TV"

/** The starred channels' group at the top of the guide */
const val FAVOURITES_GROUP = "Favourites"

/** The order of the channel types: the things live TV does best first (Ben, 2026-11-06) */
val ChannelTileOrder =
    listOf(
        FAVOURITES_GROUP,
        "News",
        "Sports",
        "Reality",
        "Classic TV",
        "Comedy",
        "Kids & Family",
        "Drama & Crime",
        "Food & Home",
        "Documentaries & Nature",
        "Sci-Fi & Anime",
        "Movies",
        "Holiday",
    )

/** A free live channel in the guide (Glowfin, Nov 2026: real live channels only, no home-made ones) */
data class ChannelSchedule(
    val number: Int,
    val programs: List<ChannelProgram>,
    val live: LiveChannel,
) {
    val title: String get() = live.name

    /** What favourites and Recently watched save the channel as (live numbers can change, names don't) */
    val key: String get() = "live:" + live.name.lowercase()

    fun programAt(timeMs: Long): ChannelProgram? = programs.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }

    fun between(
        fromMs: Long,
        toMs: Long,
    ): List<ChannelProgram> = programs.filter { it.endMs > fromMs && it.startMs < toMs }
}

/** What the watching banner shows: the channel, what's on with time left, what's next */
data class ChannelBannerInfo(
    val number: Int,
    val name: String,
    val now: String?,
    val startMs: Long,
    val endMs: Long,
    val next: String?,
    val nextStartMs: Long,
    val key: String,
    /** The live channel's id, for its logo */
    val channelId: UUID,
)

private fun bannerFor(
    schedule: ChannelSchedule,
    now: Long,
): ChannelBannerInfo {
    val on = schedule.programAt(now)?.takeIf { !it.noGuide }
    val next = on?.let { current -> schedule.programs.firstOrNull { it.startMs >= current.endMs && !it.noGuide } }
    return ChannelBannerInfo(
        number = schedule.number,
        name = schedule.title,
        now = on?.item?.title,
        startMs = on?.startMs ?: 0L,
        endMs = on?.endMs ?: 0L,
        next = next?.item?.title,
        nextStartMs = next?.startMs ?: 0L,
        key = schedule.key,
        channelId = schedule.live.id,
    )
}

/**
 * Today's live channels, and which one you're watching, so Up/Down in the player can flip channels
 */
object ChannelSession {
    private var schedules: List<ChannelSchedule> = emptyList()

    /** The channel being watched while watching from the guide */
    private var currentNumber: Int? = null
    private var currentItemId: UUID? = null

    private val _banner = MutableStateFlow<ChannelBannerInfo?>(null)

    /** The watching banner, shown for a moment after you change channel */
    val banner: StateFlow<ChannelBannerInfo?> = _banner

    fun clearBanner() {
        _banner.value = null
    }

    /** Leave channel mode, e.g. when the guide opens again */
    fun stopWatching() {
        currentNumber = null
        currentItemId = null
        _banner.value = null
    }

    val lastNumber: Int? get() = currentNumber

    /**
     * What's on the live channel [itemId] right now (for the player's Live TV bar), or null when it
     * isn't one of today's live channels. Its times are 0 when the channel has no TV guide.
     */
    fun liveNow(itemId: UUID?): ChannelBannerInfo? {
        if (itemId == null) return null
        val schedule = schedules.firstOrNull { it.live.id == itemId } ?: return null
        return bannerFor(schedule, System.currentTimeMillis())
    }

    /** Today's line-up, for the guide in the player */
    val lineup: List<ChannelSchedule> get() = schedules

    /** True while [playingItemId] is a channel picked from the guide (so the side guide can open) */
    fun isWatching(playingItemId: UUID?): Boolean = currentNumber != null && playingItemId != null && playingItemId == currentItemId

    /**
     * Today's live channels. [onProgress] gets the channels as they come in (in channel order).
     * Kids get none: live channels have no age ratings.
     */
    suspend fun load(
        api: ApiClient,
        userId: UUID?,
        kids: Boolean,
        onProgress: (List<ChannelSchedule>) -> Unit = {},
    ): List<ChannelSchedule> {
        if (kids) {
            schedules = emptyList()
            return emptyList()
        }
        val all = LiveChannels.load(api, userId, onProgress)
        schedules = all
        return all
    }

    /** Start watching [schedule], like turning on the TV */
    fun tune(
        navigationManager: NavigationManager,
        schedule: ChannelSchedule,
        replacePlayer: Boolean = false,
    ) {
        val live = schedule.live
        ChannelPrefs.addRecent(schedule.key)
        currentNumber = schedule.number
        currentItemId = live.id
        _banner.value = bannerFor(schedule, System.currentTimeMillis())
        if (replacePlayer) navigationManager.goBack()
        navigationManager.navigateTo(Destination.Playback(live.id, 0L))
    }

    /**
     * In the player while watching a channel: Up = next channel, Down = the one before
     * (also the remote's CH+/CH- buttons). Returns true when the press was used.
     */
    fun handlePlayerKey(
        event: KeyEvent,
        controlsVisible: Boolean,
        playingItemId: UUID?,
        navigationManager: NavigationManager,
    ): Boolean {
        val number = currentNumber ?: return false
        if (playingItemId == null || playingItemId != currentItemId || schedules.isEmpty()) return false
        val step =
            when (event.key) {
                Key.ChannelUp -> 1
                Key.ChannelDown -> -1
                Key.DirectionUp -> if (controlsVisible) return false else 1
                Key.DirectionDown -> if (controlsVisible) return false else -1
                else -> return false
            }
        // Act when the button is let go, but keep the press from the player either way
        if (event.type == KeyEventType.KeyUp) {
            val index = schedules.indexOfFirst { it.number == number }
            val next = schedules[Math.floorMod(index + step, schedules.size)]
            tune(navigationManager, next, replacePlayer = true)
        }
        return true
    }
}
