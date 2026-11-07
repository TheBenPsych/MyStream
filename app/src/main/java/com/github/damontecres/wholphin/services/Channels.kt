package com.github.damontecres.wholphin.services

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

private val MOVIE = listOf(BaseItemKind.MOVIE)
private val EPISODE = listOf(BaseItemKind.EPISODE)

/**
 * A Benflix channel: something from your own library is always "on", like regular TV.
 * A channel is a kind of thing ([types]) with any of [genres], from [years], or only from libraries
 * whose name contains [library] (like "anime" or "stand"). Channels with nothing on your server are left out.
 */
enum class BenflixChannel(
    val title: String,
    val types: List<BaseItemKind>,
    val genres: List<String> = emptyList(),
    val kids: Boolean = false,
    val years: IntRange? = null,
    val library: String? = null,
    /** One of the 10 channels everyone sees (the rest only show for Kids, or not at all) */
    val main: Boolean = false,
) {
    // Movies
    MOVIE_NIGHT("Movie Night", MOVIE, main = true),
    ACTION("Action", MOVIE, listOf("Action", "Adventure"), main = true),
    COMEDY("Comedy Movies", MOVIE, listOf("Comedy"), main = true),
    DRAMA_MOVIES("Drama Movies", MOVIE, listOf("Drama")),
    THRILLER("Thrillers", MOVIE, listOf("Thriller")),
    HORROR("Horror", MOVIE, listOf("Horror"), main = true),
    SCIFI("Sci-Fi & Fantasy", MOVIE, listOf("Science Fiction", "Fantasy"), main = true),
    ROMANCE("Romance", MOVIE, listOf("Romance")),
    CRIME_MOVIES("Crime Movies", MOVIE, listOf("Crime", "Mystery")),
    FAMILY("Family Movies", MOVIE, listOf("Family"), kids = true, main = true),
    ANIMATED_MOVIES("Animated Movies", MOVIE, listOf("Animation"), kids = true),
    WESTERNS("Westerns", MOVIE, listOf("Western")),
    WAR("War & History", MOVIE, listOf("War", "History")),
    MUSIC("Music & Musicals", MOVIE, listOf("Music", "Musical")),
    DOCS("Documentaries", MOVIE, listOf("Documentary")),
    EIGHTIES("80s Movies", MOVIE, years = 1980..1989),
    NINETIES("90s Movies", MOVIE, years = 1990..1999),
    TWO_THOUSANDS("2000s Movies", MOVIE, years = 2000..2009),
    CLASSICS("Classic Movies", MOVIE, years = 1920..1979),

    // Shows
    SITCOMS("Sitcoms", EPISODE, listOf("Comedy"), main = true),
    DRAMA("Drama Series", EPISODE, listOf("Drama")),
    CRIME("Crime & Mystery", EPISODE, listOf("Crime", "Mystery"), main = true),
    ACTION_SERIES("Action Series", EPISODE, listOf("Action & Adventure", "Action", "Adventure")),
    SCIFI_SERIES("Sci-Fi Series", EPISODE, listOf("Sci-Fi & Fantasy", "Science Fiction", "Fantasy")),
    REALITY("Reality TV", EPISODE, listOf("Reality")),
    DOCUSERIES("Docuseries", EPISODE, listOf("Documentary")),
    GAME_TALK("Game & Talk Shows", EPISODE, listOf("Game Show", "Talk")),
    CARTOONS("Cartoons", EPISODE, listOf("Animation"), kids = true),
    KIDS_TV("Kids TV", EPISODE, listOf("Kids", "Children"), kids = true),

    // Your own special libraries
    ANIME("Anime", EPISODE, library = "anime", main = true),
    ANIME_MOVIES("Anime Movies", MOVIE, library = "anime"),
    STAND_UP("Stand-Up", listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE), library = "stand", main = true),
}

@Serializable
private data class SavedProgram(
    val item: BaseItem,
    val startMs: Long,
    val endMs: Long,
)

@Serializable
private data class SavedChannel(
    val number: Int,
    val channel: String,
    val programs: List<SavedProgram>,
)

@Serializable
private data class SavedLineup(
    val channels: List<SavedChannel>,
)

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
 * (keep in step with CATEGORIES in server/benflix_livetv.py).
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
        1000 to "Music",
        1100 to "Documentaries & Nature",
        1200 to "Food & Home",
        1300 to "Other",
    )

/** The group heading a channel goes under on the Channels page */
fun ChannelSchedule.group(): String =
    if (live == null) {
        BENFLIX_GROUP
    } else {
        LiveTvGroups.lastOrNull { number >= it.first }?.second ?: "Live TV"
    }

const val BENFLIX_GROUP = "Benflix channels"

/** The starred channels' tile on the Channels page */
const val FAVOURITES_GROUP = "Favourites"

/**
 * The order of the type tiles on the Channels page: the things live TV does best first
 * (Ben, 2026-11-06), Benflix's own channels near the end
 */
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
        "Music",
        "Sci-Fi & Anime",
        "Movies",
        BENFLIX_GROUP,
        "Other",
    )

/** A channel in the guide: one of Benflix's own ([channel]) or a free live one ([live]) */
data class ChannelSchedule(
    val number: Int,
    val channel: BenflixChannel?,
    val programs: List<ChannelProgram>,
    val live: LiveChannel? = null,
) {
    val title: String get() = live?.name ?: channel?.title.orEmpty()

    /** What favourites and Recently watched save the channel as (live numbers can change, names don't) */
    val key: String get() = live?.let { "live:" + it.name.lowercase() } ?: "own:${channel?.name}"

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
    val own: Boolean,
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
        own = schedule.live == null,
    )
}

/**
 * Builds the day's channel line-up and remembers which channel you're watching,
 * so Up/Down in the player can flip channels
 */
object ChannelSession {
    private var schedules: List<ChannelSchedule> = emptyList()
    private var ownSchedules: List<ChannelSchedule> = emptyList()
    private var builtFor: String? = null

    /** The channel being watched, and the program on it, while watching from the guide */
    private var currentNumber: Int? = null
    private var currentItemId: UUID? = null

    private val _banner = MutableStateFlow<ChannelBannerInfo?>(null)

    /** The watching banner (approved wt-banner), shown for a moment after you change channel */
    val banner: StateFlow<ChannelBannerInfo?> = _banner

    /** True when [itemId] is a show playing on one of Benflix's own channels (it can start from the beginning) */
    fun isOwnChannelProgram(itemId: String?): Boolean = ownWatching && itemId != null && itemId == currentItemId?.toString()

    private var ownWatching = false

    fun clearBanner() {
        _banner.value = null
    }

    /** Leave channel mode, e.g. when the guide opens again */
    fun stopWatching() {
        currentNumber = null
        currentItemId = null
        ownWatching = false
    }

    val lastNumber: Int? get() = currentNumber

    /** Today's line-up, for the guide in the player */
    val lineup: List<ChannelSchedule> get() = schedules

    /** True while [playingItemId] is a channel picked from the guide (so the side guide can open) */
    fun isWatching(playingItemId: UUID?): Boolean = currentNumber != null && playingItemId != null && playingItemId == currentItemId

    /**
     * Today's line-up. [onProgress] gets the channels as they come in (in channel order, so new ones
     * only ever appear at the bottom of the guide). Today's line-up is also saved on the device,
     * so opening Channels again later today (even after a restart) is instant and shows the same thing.
     */
    suspend fun load(
        api: ApiClient,
        userId: UUID?,
        kids: Boolean,
        diskCache: DiskCache? = null,
        onProgress: (List<ChannelSchedule>) -> Unit = {},
    ): List<ChannelSchedule> {
        // Kids don't get live channels: they have no age ratings
        if (kids) {
            val own = loadOwn(api, userId, true, diskCache, onProgress)
            schedules = own
            return own
        }
        // Benflix's own channels and the live ones load side by side, each showing as soon as it's ready
        var ownNow = emptyList<ChannelSchedule>()
        var liveNow = emptyList<ChannelSchedule>()
        val lock = Any()

        fun show() = synchronized(lock) { onProgress(ownNow + liveNow) }
        val all =
            coroutineScope {
                val live =
                    async {
                        LiveChannels.load(api, userId) { partial ->
                            synchronized(lock) { liveNow = partial }
                            show()
                        }
                    }
                val own =
                    loadOwn(api, userId, false, diskCache) { partial ->
                        synchronized(lock) { ownNow = partial }
                        show()
                    }
                // A saved line-up comes back without any progress calls, so show it now
                synchronized(lock) { ownNow = own }
                show()
                own + live.await()
            }
        schedules = all
        return all
    }

    private suspend fun loadOwn(
        api: ApiClient,
        userId: UUID?,
        kids: Boolean,
        diskCache: DiskCache?,
        onProgress: (List<ChannelSchedule>) -> Unit,
    ): List<ChannelSchedule> {
        val today = LocalDate.now()
        // Kids and Big kids get different line-ups, and hiding a genre changes today's guide
        val level =
            if (!kids) {
                "off"
            } else if (KidsMode.isBig) {
                "bigkids"
            } else {
                "kids"
            }
        val genres =
            HiddenGenres.hidden
                .sorted()
                .joinToString("|")
                .hashCode()
        val key = "$userId-$level-$genres-$today"
        if (key == builtFor && ownSchedules.isNotEmpty()) return ownSchedules
        val fileName = "channels-$key-v$CACHE_VERSION"
        diskCache?.read(fileName, SavedLineup.serializer(), CACHE_MAX_AGE_MS)?.let { saved ->
            val restored =
                saved.channels.mapNotNull { c ->
                    val channel = BenflixChannel.entries.firstOrNull { it.name == c.channel } ?: return@mapNotNull null
                    ChannelSchedule(c.number, channel, c.programs.map { ChannelProgram(it.item, it.startMs, it.endMs) })
                }
            if (restored.isNotEmpty()) {
                ownSchedules = restored
                builtFor = key
                return restored
            }
        }
        val midnight =
            today
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val wanted = BenflixChannel.entries.filter { if (kids) it.kids else it.main }
        val libraries =
            if (wanted.any { it.library != null }) {
                try {
                    api.userViewsApi
                        .getUserViews(userId = userId)
                        .content.items
                        .map { it.id to it.name.orEmpty() }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not list libraries for channels")
                    emptyList()
                }
            } else {
                emptyList()
            }
        // A few channels at a time, so the server (and the TV) stay quick
        val permits = Semaphore(CHANNELS_PARALLEL)
        val done = arrayOfNulls<ChannelSchedule>(wanted.size)
        val finished = BooleanArray(wanted.size)
        val lock = Any()
        var shownUpTo = 0
        coroutineScope {
            wanted
                .mapIndexed { i, channel ->
                    async {
                        val programs =
                            permits.withPermit {
                                try {
                                    val libs =
                                        channel.library?.let { word ->
                                            libraries.filter { it.second.contains(word, ignoreCase = true) }.map { it.first }
                                        }
                                    buildChannel(api, userId, channel, libs, today.toEpochDay(), midnight)
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Channel ${channel.title} failed")
                                    null
                                }
                            }
                        synchronized(lock) {
                            done[i] = programs?.let { ChannelSchedule(0, channel, it) }
                            finished[i] = true
                            // Show every channel up to the first one still loading
                            var upTo = shownUpTo
                            while (upTo < wanted.size && finished[upTo]) upTo++
                            if (upTo > shownUpTo) {
                                shownUpTo = upTo
                                onProgress(numbered(done.take(upTo)))
                            }
                        }
                    }
                }.awaitAll()
        }
        val built = numbered(done.toList())
        ownSchedules = built
        builtFor = key
        if (built.isNotEmpty()) {
            diskCache?.write(
                fileName,
                SavedLineup.serializer(),
                SavedLineup(
                    built.map { sch ->
                        SavedChannel(
                            sch.number,
                            sch.channel?.name.orEmpty(),
                            sch.programs.map { SavedProgram(it.item, it.startMs, it.endMs) },
                        )
                    },
                ),
            )
        }
        return built
    }

    /** Channel numbers 1, 2, 3... for the channels that have something on */
    private fun numbered(list: List<ChannelSchedule?>): List<ChannelSchedule> =
        list.filterNotNull().mapIndexed { i, s -> s.copy(number = i + 1) }

    private fun request(
        userId: UUID?,
        channel: BenflixChannel,
        types: List<BaseItemKind>,
        parentId: UUID?,
        genres: List<String>?,
        limit: Int,
    ) = GetItemsRequest(
        userId = userId,
        parentId = parentId,
        includeItemTypes = types,
        recursive = true,
        genres = genres?.ifEmpty { null },
        years = channel.years?.toList(),
        sortBy = listOf(ItemSortBy.RANDOM),
        limit = limit,
        // Only what the guide shows: small answers load fast
        fields = listOf(ItemFields.OVERVIEW),
        enableImages = false,
        enableUserData = false,
    )

    private suspend fun buildChannel(
        api: ApiClient,
        userId: UUID?,
        channel: BenflixChannel,
        libraryIds: List<UUID>?,
        epochDay: Long,
        midnight: Long,
    ): List<ChannelProgram>? {
        // A channel for a library you don't have (like Anime) just isn't shown
        if (libraryIds != null && libraryIds.isEmpty()) return null
        val parents: List<UUID?> = libraryIds ?: listOf(null)
        var found =
            parents.flatMap { parent ->
                api.itemsApi
                    .getItems(request(userId, channel, channel.types, parent, channel.genres, CHANNEL_POOL))
                    .content.items
            }
        // Some servers only put genres on the show, not on each episode: then pick shows, and use their episodes
        if (found.isEmpty() && channel.types == EPISODE && channel.genres.isNotEmpty()) {
            val shows =
                parents.flatMap { parent ->
                    api.itemsApi
                        .getItems(request(userId, channel, listOf(BaseItemKind.SERIES), parent, channel.genres, SHOWS_PER_CHANNEL))
                        .content.items
                }
            found =
                shows.flatMap { show ->
                    api.itemsApi
                        .getItems(request(userId, channel, EPISODE, show.id, null, EPISODES_PER_SHOW).copy(years = null))
                        .content.items
                }
        }
        val pool =
            found
                .filter { (it.runTimeTicks ?: 0L) > MIN_RUNTIME_TICKS }
                .distinctBy { it.id }
                .sortedBy { it.id }
                // The same line-up all day, a new one tomorrow
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
        return programs
    }

    /** Start watching [schedule] where it is right now, like turning on the TV */
    fun tune(
        navigationManager: NavigationManager,
        schedule: ChannelSchedule,
        replacePlayer: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val live = schedule.live
        ChannelPrefs.addRecent(schedule.key)
        if (live != null) {
            // A live channel: just turn it on
            currentNumber = schedule.number
            currentItemId = live.id
            ownWatching = false
            _banner.value = bannerFor(schedule, now)
            if (replacePlayer) navigationManager.goBack()
            navigationManager.navigateTo(Destination.Playback(live.id, 0L))
            return
        }
        val program = schedule.programAt(now) ?: return
        currentNumber = schedule.number
        currentItemId = program.item.id
        ownWatching = true
        _banner.value = bannerFor(schedule, now)
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

// Enough for a whole day on one channel without repeating much
private const val CHANNEL_POOL = 120
private const val CHANNELS_PARALLEL = 6
private const val SHOWS_PER_CHANNEL = 12
private const val EPISODES_PER_SHOW = 15
private const val CACHE_VERSION = 2
private const val CACHE_MAX_AGE_MS = 26L * 3_600_000L
private const val SCHEDULE_HOURS = 30L

// Skip anything under 5 minutes (bumpers, extras)
private const val MIN_RUNTIME_TICKS = 5L * 60L * 10_000_000L
