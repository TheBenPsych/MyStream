package com.github.damontecres.wholphin.services

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/** A Benflix channel: something from your own library is always "on", like regular TV */
enum class BenflixChannel(
    val title: String,
    val type: BaseItemKind,
    val genres: List<String>,
    val kids: Boolean = false,
) {
    COMEDY("Comedy Movies", BaseItemKind.MOVIE, listOf("Comedy")),
    ACTION("Action", BaseItemKind.MOVIE, listOf("Action", "Adventure")),
    SITCOMS("Sitcoms", BaseItemKind.EPISODE, listOf("Comedy")),
    DRAMA("Drama Series", BaseItemKind.EPISODE, listOf("Drama")),
    SCIFI("Sci-Fi & Fantasy", BaseItemKind.MOVIE, listOf("Science Fiction", "Fantasy")),
    HORROR("Horror", BaseItemKind.MOVIE, listOf("Horror")),
    CRIME("Crime & Mystery", BaseItemKind.EPISODE, listOf("Crime", "Mystery")),
    THRILLER("Thrillers", BaseItemKind.MOVIE, listOf("Thriller")),
    FAMILY("Family Movies", BaseItemKind.MOVIE, listOf("Family", "Animation"), kids = true),
    CARTOONS("Cartoons", BaseItemKind.EPISODE, listOf("Animation"), kids = true),
    DOCS("Documentaries", BaseItemKind.MOVIE, listOf("Documentary")),
}

/** One thing on a channel, with when it starts and ends (epoch millis) */
data class ChannelProgram(
    val item: BaseItem,
    val startMs: Long,
    val endMs: Long,
)

data class ChannelSchedule(
    val number: Int,
    val channel: BenflixChannel,
    val programs: List<ChannelProgram>,
) {
    fun programAt(timeMs: Long): ChannelProgram? = programs.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }

    fun between(
        fromMs: Long,
        toMs: Long,
    ): List<ChannelProgram> = programs.filter { it.endMs > fromMs && it.startMs < toMs }
}

/**
 * Builds the day's channel line-up and remembers which channel you're watching,
 * so Up/Down in the player can flip channels
 */
object ChannelSession {
    private var schedules: List<ChannelSchedule> = emptyList()
    private var builtFor: String? = null

    /** The channel being watched, and the program on it, while watching from the guide */
    private var currentNumber: Int? = null
    private var currentItemId: UUID? = null

    private val _banner = MutableStateFlow<String?>(null)

    /** "3  Sitcoms · The Office", shown for a moment after you change channel */
    val banner: StateFlow<String?> = _banner

    fun clearBanner() {
        _banner.value = null
    }

    /** Leave channel mode, e.g. when the guide opens again */
    fun stopWatching() {
        currentNumber = null
        currentItemId = null
    }

    val lastNumber: Int? get() = currentNumber

    suspend fun load(
        api: ApiClient,
        userId: UUID?,
        kids: Boolean,
    ): List<ChannelSchedule> {
        val today = LocalDate.now()
        val key = "$userId-$kids-$today"
        if (key == builtFor && schedules.isNotEmpty()) return schedules
        val midnight =
            today
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val wanted = BenflixChannel.entries.filter { !kids || it.kids }
        val built =
            coroutineScope {
                wanted
                    .map { channel ->
                        async {
                            try {
                                buildChannel(api, userId, channel, today.toEpochDay(), midnight)
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Channel ${channel.title} failed")
                                null
                            }
                        }
                    }.awaitAll()
            }.filterNotNull()
                .mapIndexed { i, (channel, programs) -> ChannelSchedule(i + 1, channel, programs) }
        schedules = built
        builtFor = key
        return built
    }

    private suspend fun buildChannel(
        api: ApiClient,
        userId: UUID?,
        channel: BenflixChannel,
        epochDay: Long,
        midnight: Long,
    ): Pair<BenflixChannel, List<ChannelProgram>>? {
        val pool =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userId,
                        includeItemTypes = listOf(channel.type),
                        recursive = true,
                        genres = channel.genres,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        limit = CHANNEL_POOL,
                        fields = HomeItemFields,
                    ),
                ).content.items
                .filter { (it.runTimeTicks ?: 0L) > MIN_RUNTIME_TICKS }
                // The same line-up all day for everyone, a new one tomorrow
                .shuffled(Random(epochDay * 7919 + channel.ordinal))
        if (pool.isEmpty()) return null
        val end = midnight + SCHEDULE_HOURS * 3_600_000L
        val programs = mutableListOf<ChannelProgram>()
        var t = midnight
        var i = 0
        while (t < end) {
            val dto = pool[i % pool.size]
            val length = (dto.runTimeTicks ?: 0L) / 10_000L
            programs.add(ChannelProgram(BaseItem(dto, useSeriesForPrimary = false), t, t + length))
            t += length
            i++
        }
        return channel to programs
    }

    /** Start watching [schedule] where it is right now, like turning on the TV */
    fun tune(
        navigationManager: NavigationManager,
        schedule: ChannelSchedule,
        replacePlayer: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val program = schedule.programAt(now) ?: return
        currentNumber = schedule.number
        currentItemId = program.item.id
        _banner.value = "${schedule.number}  ${schedule.channel.title}  ·  ${program.item.title.orEmpty()}"
        if (replacePlayer) navigationManager.goBack()
        navigationManager.navigateTo(Destination.Playback(program.item.id, (now - program.startMs).coerceAtLeast(0L)))
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

private const val CHANNEL_POOL = 300
private const val SCHEDULE_HOURS = 30L

// Skip anything under 5 minutes (bumpers, extras)
private const val MIN_RUNTIME_TICKS = 5L * 60L * 10_000_000L
