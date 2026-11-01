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
) {
    // Movies
    MOVIE_NIGHT("Movie Night", MOVIE),
    ACTION("Action", MOVIE, listOf("Action", "Adventure")),
    COMEDY("Comedy Movies", MOVIE, listOf("Comedy")),
    DRAMA_MOVIES("Drama Movies", MOVIE, listOf("Drama")),
    THRILLER("Thrillers", MOVIE, listOf("Thriller")),
    HORROR("Horror", MOVIE, listOf("Horror")),
    SCIFI("Sci-Fi & Fantasy", MOVIE, listOf("Science Fiction", "Fantasy")),
    ROMANCE("Romance", MOVIE, listOf("Romance")),
    CRIME_MOVIES("Crime Movies", MOVIE, listOf("Crime", "Mystery")),
    FAMILY("Family Movies", MOVIE, listOf("Family"), kids = true),
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
    SITCOMS("Sitcoms", EPISODE, listOf("Comedy")),
    DRAMA("Drama Series", EPISODE, listOf("Drama")),
    CRIME("Crime & Mystery", EPISODE, listOf("Crime", "Mystery")),
    ACTION_SERIES("Action Series", EPISODE, listOf("Action & Adventure", "Action", "Adventure")),
    SCIFI_SERIES("Sci-Fi Series", EPISODE, listOf("Sci-Fi & Fantasy", "Science Fiction", "Fantasy")),
    REALITY("Reality TV", EPISODE, listOf("Reality")),
    DOCUSERIES("Docuseries", EPISODE, listOf("Documentary")),
    GAME_TALK("Game & Talk Shows", EPISODE, listOf("Game Show", "Talk")),
    CARTOONS("Cartoons", EPISODE, listOf("Animation"), kids = true),
    KIDS_TV("Kids TV", EPISODE, listOf("Kids", "Children"), kids = true),

    // Your own special libraries
    ANIME("Anime", EPISODE, library = "anime"),
    ANIME_MOVIES("Anime Movies", MOVIE, library = "anime"),
    STAND_UP("Stand-Up", listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE), library = "stand"),
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
        val today = LocalDate.now()
        val key = "$userId-$kids-$today"
        if (key == builtFor && schedules.isNotEmpty()) return schedules
        val fileName = "channels-$key-v$CACHE_VERSION"
        diskCache?.read(fileName, SavedLineup.serializer(), CACHE_MAX_AGE_MS)?.let { saved ->
            val restored =
                saved.channels.mapNotNull { c ->
                    val channel = BenflixChannel.entries.firstOrNull { it.name == c.channel } ?: return@mapNotNull null
                    ChannelSchedule(c.number, channel, c.programs.map { ChannelProgram(it.item, it.startMs, it.endMs) })
                }
            if (restored.isNotEmpty()) {
                schedules = restored
                builtFor = key
                return restored
            }
        }
        val midnight =
            today
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val wanted = BenflixChannel.entries.filter { !kids || it.kids }
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
        schedules = built
        builtFor = key
        if (built.isNotEmpty()) {
            diskCache?.write(
                fileName,
                SavedLineup.serializer(),
                SavedLineup(
                    built.map { sch ->
                        SavedChannel(sch.number, sch.channel.name, sch.programs.map { SavedProgram(it.item, it.startMs, it.endMs) })
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

// Enough for a whole day on one channel without repeating much
private const val CHANNEL_POOL = 120
private const val CHANNELS_PARALLEL = 6
private const val SHOWS_PER_CHANNEL = 12
private const val EPISODES_PER_SHOW = 15
private const val CACHE_VERSION = 1
private const val CACHE_MAX_AGE_MS = 26L * 3_600_000L
private const val SCHEDULE_HOURS = 30L

// Skip anything under 5 minutes (bumpers, extras)
private const val MIN_RUNTIME_TICKS = 5L * 60L * 10_000_000L
