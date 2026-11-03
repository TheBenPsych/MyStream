package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: which way this device plays an anime show or movie, picked by opening it from the
 * Dubbed or Subbed tab of an anime page. DUB = English voices (with signs & songs subtitles if
 * there are any), SUB = Japanese voices with the full English subtitles.
 * Saved on this device until it's opened from the other tab.
 */
object BenflixDubChoice {
    enum class Mode { DUB, SUB }

    private const val PREFS = "benflix_dub_choice"
    private val modes = ConcurrentHashMap<String, Mode>()

    @Volatile private var prefs: android.content.SharedPreferences? = null

    /** Called once when the app starts */
    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            (value as? String)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }?.let { modes[key] = it }
        }
    }

    /** The show (for an episode) or movie the choice is saved under */
    private fun keyFor(item: BaseItemDto): String = (item.seriesId ?: item.id).toString()

    fun set(
        id: UUID,
        mode: Mode,
    ) {
        modes[id.toString()] = mode
        prefs?.edit()?.putString(id.toString(), mode.name)?.apply()
        Timber.i("Anime %s will play %s on this device", id, mode)
    }

    fun modeFor(item: BaseItemDto): Mode? = modes[keyFor(item)]

    private val ENGLISH = setOf("eng", "en", "english")

    fun isEnglish(language: String?): Boolean = language?.lowercase() in ENGLISH

    /** The English voice track, or null if there is none */
    fun englishAudio(candidates: List<MediaStream>): MediaStream? =
        candidates
            .filter { isEnglish(it.language) && !(it.title.orEmpty().contains("commentary", true)) }
            .sortedWith(compareByDescending<MediaStream> { it.isDefault }.thenByDescending { it.channels })
            .firstOrNull()

    /** With English voices, only the subtitles for signs and songs (if the file has them) */
    fun signsSubtitles(candidates: List<MediaStream>): MediaStream? {
        fun name(track: MediaStream) = listOfNotNull(track.title, track.displayTitle).joinToString(" ").lowercase()
        return candidates
            .filter { it.language.isNullOrBlank() || isEnglish(it.language) }
            .firstOrNull { track ->
                track.isForced || listOf("sign", "song", "s&s").any { name(track).contains(it) }
            }
    }
}

/** Whether an anime has English voices (dub), Japanese voices with subtitles (sub), or both */
data class DubInfo(
    val dub: Boolean,
    val sub: Boolean,
)

/**
 * Benflix: finds which anime in a library have an English dub and which have Japanese with subtitles.
 * Movies are read in one go; for shows the first episode of each show is checked.
 * Saved on the device for 3 days.
 */
@Singleton
class AnimeDubIndex
    @Inject
    constructor(
        private val api: ApiClient,
        private val diskCache: DiskCache,
    ) {
        private val memory = ConcurrentHashMap<UUID, Map<UUID, DubInfo>>()

        suspend fun index(
            parentId: UUID,
            userId: UUID?,
            shows: Boolean,
        ): Map<UUID, DubInfo> {
            memory[parentId]?.let { return it }
            val cacheName = "anime-dub-$parentId"
            diskCache.read(cacheName, CACHE_SERIALIZER, CACHE_MS)?.let { saved ->
                val parsed = saved.mapNotNull { (k, v) -> runCatching { UUID.fromString(k) }.getOrNull()?.let { it to decode(v) } }.toMap()
                memory[parentId] = parsed
                return parsed
            }
            val found =
                withContext(WholphinDispatchers.IO) {
                    try {
                        if (shows) showIndex(parentId, userId) else movieIndex(parentId, userId)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not check dubs for library %s", parentId)
                        null
                    }
                } ?: return emptyMap()
            memory[parentId] = found
            diskCache.write(cacheName, CACHE_SERIALIZER, found.map { (k, v) -> k.toString() to encode(v) }.toMap())
            Timber.i("Anime dub check for %s: %s titles, %s dubbed", parentId, found.size, found.count { it.value.dub })
            return found
        }

        private suspend fun movieIndex(
            parentId: UUID,
            userId: UUID?,
        ): Map<UUID, DubInfo> {
            val out = mutableMapOf<UUID, DubInfo>()
            var start = 0
            while (true) {
                val page =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                parentId = parentId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                recursive = true,
                                fields = listOf(ItemFields.MEDIA_STREAMS),
                                enableImages = false,
                                enableUserData = false,
                                startIndex = start,
                                limit = PAGE,
                            ),
                        ).content.items
                page.forEach { dto -> infoFor(dto.mediaStreams)?.let { out[dto.id] = it } }
                if (page.size < PAGE) break
                start += PAGE
            }
            return out
        }

        private suspend fun showIndex(
            parentId: UUID,
            userId: UUID?,
        ): Map<UUID, DubInfo> {
            val shows =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            parentId = parentId,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                            recursive = true,
                            enableImages = false,
                            enableUserData = false,
                        ),
                    ).content.items
            val gate = Semaphore(SHOW_CONCURRENCY)
            return coroutineScope {
                shows
                    .map { show ->
                        async {
                            gate.withPermit {
                                try {
                                    val first =
                                        api.tvShowsApi
                                            .getEpisodes(
                                                GetEpisodesRequest(
                                                    seriesId = show.id,
                                                    userId = userId,
                                                    fields = listOf(ItemFields.MEDIA_STREAMS),
                                                    enableImages = false,
                                                    enableUserData = false,
                                                    limit = 4,
                                                ),
                                            ).content.items
                                            .firstOrNull { (it.parentIndexNumber ?: 1) > 0 && !it.mediaStreams.isNullOrEmpty() }
                                    first?.let { ep -> infoFor(ep.mediaStreams)?.let { show.id to it } }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    Timber.v(ex, "Could not check dub for %s", show.name)
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .toMap()
            }
        }

        private fun infoFor(streams: List<MediaStream>?): DubInfo? {
            val audio = streams.orEmpty().filter { it.type == MediaStreamType.AUDIO }
            if (audio.isEmpty()) return null
            val dub = audio.any { BenflixDubChoice.isEnglish(it.language) }
            val sub =
                audio.any { BenflixAnimeTracks.isJapanese(it.language) } &&
                    streams.orEmpty().any { it.type == MediaStreamType.SUBTITLE }
            return DubInfo(dub = dub, sub = sub)
        }

        private fun encode(info: DubInfo) = (if (info.dub) "D" else "") + (if (info.sub) "S" else "")

        private fun decode(value: String) = DubInfo(dub = value.contains('D'), sub = value.contains('S'))

        companion object {
            private const val PAGE = 200
            private const val SHOW_CONCURRENCY = 6
            private const val CACHE_MS = 3L * 24 * 60 * 60 * 1000
            private val CACHE_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
        }
    }
