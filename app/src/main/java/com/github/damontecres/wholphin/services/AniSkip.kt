package com.github.damontecres.wholphin.services

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.core.content.edit
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import timber.log.Timber
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Benflix: opening, ending and recap times for anime from AniSkip (api.aniskip.com), where fans
 * submit and vote on the times by hand. These are usually better than what the server works out
 * on its own, so for anime they replace the server's times of the same kind.
 *
 * AniSkip knows shows by their MyAnimeList number, while Jellyfin usually knows them by their
 * TheTVDB or TMDB number. The Fribb/anime-lists list on GitHub links the two, including which
 * season (and where in the season) each MyAnimeList entry starts.
 */
@Singleton
class AniSkipService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val http: OkHttpClient,
    ) {
        private val mapMutex = Mutex()

        @Volatile private var map: AniSkipMap? = null

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * AniSkip's times for this item turned into media segments, or an empty list when the item
         * isn't anime or AniSkip has nothing that fits this exact file.
         */
        suspend fun segmentsFor(
            api: ApiClient,
            itemId: UUID,
        ): List<MediaSegmentDto> =
            withContext(WholphinDispatchers.IO) {
                try {
                    val item = api.userLibraryApi.getItem(itemId).content
                    val lengthSec = (item.runTimeTicks ?: 0L) / TICKS_PER_SECOND.toDouble()
                    if (lengthSec <= 0) return@withContext emptyList()
                    val series =
                        when (item.type) {
                            BaseItemKind.EPISODE -> item.seriesId?.let { api.userLibraryApi.getItem(it).content } ?: return@withContext emptyList()
                            BaseItemKind.MOVIE -> null
                            else -> return@withContext emptyList()
                        }
                    if (!isAnime(api, item, series)) return@withContext emptyList()
                    val ids = (series ?: item).providerIds.orEmpty()
                    val target =
                        mapping()?.find(
                            ids = ids,
                            isMovie = item.type == BaseItemKind.MOVIE,
                            season = item.parentIndexNumber,
                            episode = item.indexNumber,
                        ) ?: run {
                            Timber.i("AniSkip: no MyAnimeList match for %s", item.name)
                            return@withContext emptyList()
                        }
                    val results = timesFor(target.malId, target.episode) ?: return@withContext emptyList()
                    val picked = AniSkipMatch.pick(results, lengthSec)
                    Timber.i(
                        "AniSkip: %s -> MAL %s ep %s, %s results, using %s",
                        item.name,
                        target.malId,
                        target.episode,
                        results.size,
                        picked.map { it.skipType },
                    )
                    picked.map { it.toSegment(item.id) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "AniSkip lookup failed")
                    emptyList()
                }
            }

        private suspend fun isAnime(
            api: ApiClient,
            item: BaseItemDto,
            series: BaseItemDto?,
        ): Boolean {
            if (listOfNotNull(item, series).any { BenflixAnimeTracks.isAnime(it) }) return true
            return try {
                api.libraryApi
                    .getAncestors(item.id)
                    .content
                    .any { it.name?.contains("anime", ignoreCase = true) == true }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                false
            }
        }

        /** AniSkip's raw times for one episode, saved for a week; null if not reachable */
        private fun timesFor(
            malId: Int,
            episode: Int,
        ): List<AniSkipResult>? {
            val key = "times-$malId-$episode"
            val saved = prefs().getString(key, null)
            val savedAt = prefs().getLong("$key-at", 0L)

            fun decodeSaved() = saved?.let { runCatching { json.decodeFromString<List<AniSkipResult>>(it) }.getOrNull() }
            if (saved != null && System.currentTimeMillis() - savedAt < WEEK_MS) return decodeSaved()
            val url =
                "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
                    "?types=op&types=ed&types=mixed-op&types=mixed-ed&types=recap&episodeLength=0"
            val results =
                try {
                    http
                        .newCall(Request.Builder().url(url).header("User-Agent", "Benflix").build())
                        .execute()
                        .use { response ->
                            when {
                                response.isSuccessful ->
                                    json.decodeFromString<AniSkipResponse>(response.body.string()).results

                                // AniSkip answers 404 when nobody has added times for this episode yet
                                response.code == 404 -> emptyList()

                                else -> return decodeSaved()
                            }
                        }
                } catch (ex: Exception) {
                    Timber.w(ex, "AniSkip not reachable")
                    return decodeSaved()
                }
            prefs().edit {
                putString(key, json.encodeToString(results))
                putLong("$key-at", System.currentTimeMillis())
            }
            return results
        }

        /** The TVDB/TMDB -> MyAnimeList list, downloaded every two weeks and kept on the device */
        private suspend fun mapping(): AniSkipMap? =
            mapMutex.withLock {
                val file = File(context.filesDir, MAP_FILE)
                val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAP_MAX_AGE_MS
                map?.takeIf { fresh }?.let { return@withLock it }
                if (!fresh) {
                    try {
                        download(file)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "AniSkip: couldn't download the anime list, using the saved one")
                    }
                }
                if (!file.exists()) return@withLock null
                AniSkipMap.parse(file.readLines()).also { map = it }
            }

        /** Reads the big list one entry at a time and keeps only the few numbers needed */
        private fun download(file: File) {
            val lines = mutableListOf<String>()
            http
                .newCall(Request.Builder().url(MAP_URL).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    JsonReader(InputStreamReader(response.body.byteStream(), Charsets.UTF_8)).use { reader ->
                        reader.beginArray()
                        while (reader.hasNext()) readEntry(reader)?.let { lines.addAll(it) }
                        reader.endArray()
                    }
                }
            if (lines.isEmpty()) error("empty list")
            val tmp = File(file.parentFile, "$MAP_FILE.tmp")
            tmp.writeText(lines.joinToString("\n"))
            tmp.renameTo(file)
            Timber.i("AniSkip: saved anime list with %s links", lines.size)
        }

        private fun readEntry(reader: JsonReader): List<String>? {
            var type = ""
            var mal: Int? = null
            var anilist: Int? = null
            var tvdb: Int? = null
            var tmdbTv: Int? = null
            val tmdbMovies = mutableListOf<Int>()
            var seasonTvdb = -1
            var seasonTmdb = -1
            var offsetTvdb = 0
            var offsetTmdb = 0
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> type = reader.nextStringOrNull().orEmpty()
                    "mal_id" -> mal = reader.nextIntOrNull()
                    "anilist_id" -> anilist = reader.nextIntOrNull()
                    "tvdb_id" -> tvdb = reader.nextIntOrNull()
                    "themoviedb_id" ->
                        reader.readObject { name ->
                            when (name) {
                                "tv" -> tmdbTv = reader.nextIntOrNull()
                                "movie" ->
                                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                        reader.beginArray()
                                        while (reader.hasNext()) reader.nextIntOrNull()?.let { tmdbMovies.add(it) }
                                        reader.endArray()
                                    } else {
                                        reader.nextIntOrNull()?.let { tmdbMovies.add(it) }
                                    }
                                else -> reader.skipValue()
                            }
                        }
                    "season" ->
                        reader.readObject { name ->
                            when (name) {
                                "tvdb" -> seasonTvdb = reader.nextIntOrNull() ?: -1
                                "tmdb" -> seasonTmdb = reader.nextIntOrNull() ?: -1
                                else -> reader.skipValue()
                            }
                        }
                    "episode_offset" ->
                        reader.readObject { name ->
                            when (name) {
                                "tvdb" -> offsetTvdb = reader.nextIntOrNull() ?: 0
                                "tmdb" -> offsetTmdb = reader.nextIntOrNull() ?: 0
                                else -> reader.skipValue()
                            }
                        }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            val malId = mal ?: return null
            return buildList {
                tvdb?.let { add(AniSkipMap.line("tvdb", it, seasonTvdb, offsetTvdb, type, malId)) }
                tmdbTv?.let { add(AniSkipMap.line("tmdb", it, seasonTmdb, offsetTmdb, type, malId)) }
                tmdbMovies.forEach { add(AniSkipMap.line("movie", it, -1, 0, type, malId)) }
                anilist?.let { add(AniSkipMap.line("anilist", it, -1, 0, type, malId)) }
            }
        }

        private fun JsonReader.nextIntOrNull(): Int? =
            when (peek()) {
                JsonToken.NUMBER -> nextLong().toInt()
                JsonToken.STRING -> nextString().toIntOrNull()
                else -> {
                    skipValue()
                    null
                }
            }

        private fun JsonReader.nextStringOrNull(): String? =
            if (peek() == JsonToken.NULL) {
                skipValue()
                null
            } else {
                nextString()
            }

        private inline fun JsonReader.readObject(onField: (String) -> Unit) {
            if (peek() != JsonToken.BEGIN_OBJECT) {
                skipValue()
                return
            }
            beginObject()
            while (hasNext()) onField(nextName())
            endObject()
        }

        companion object {
            private const val PREFS = "benflix_aniskip"
            private const val MAP_FILE = "aniskip-anime-list.tsv"
            private const val MAP_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
            private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
            private const val MAP_MAX_AGE_MS = 2 * WEEK_MS
            private const val TICKS_PER_SECOND = 10_000_000L

            private val json =
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
        }
    }

@Serializable
data class AniSkipResponse(
    val found: Boolean = false,
    val results: List<AniSkipResult> = emptyList(),
)

@Serializable
data class AniSkipInterval(
    val startTime: Double = 0.0,
    val endTime: Double = 0.0,
)

@Serializable
data class AniSkipResult(
    val interval: AniSkipInterval = AniSkipInterval(),
    val skipType: String = "",
    val skipId: String = "",
    val episodeLength: Double = 0.0,
) {
    val segmentType: MediaSegmentType?
        get() =
            when (skipType) {
                "op", "mixed-op" -> MediaSegmentType.INTRO
                "ed", "mixed-ed" -> MediaSegmentType.OUTRO
                "recap" -> MediaSegmentType.RECAP
                else -> null
            }

    fun toSegment(itemId: UUID) =
        MediaSegmentDto(
            id = UUID.nameUUIDFromBytes("aniskip-$skipId-$itemId".toByteArray()),
            itemId = itemId,
            type = segmentType ?: MediaSegmentType.UNKNOWN,
            startTicks = (interval.startTime * 10_000_000L).toLong(),
            endTicks = (interval.endTime * 10_000_000L).toLong(),
        )
}

/** Picks which AniSkip times to trust for one file */
object AniSkipMatch {
    /** A submission is only used when it was made on a file this close in length to ours */
    const val LENGTH_TOLERANCE_SEC = 5.0

    /** Longest believable opening, ending or recap */
    private const val MAX_SEGMENT_SEC = 300.0

    /** At most one of each kind: the top voted (AniSkip lists the best first) that fits this file */
    fun pick(
        results: List<AniSkipResult>,
        lengthSec: Double,
    ): List<AniSkipResult> =
        results
            .filter { it.segmentType != null }
            .filter { it.episodeLength > 0 && abs(it.episodeLength - lengthSec) <= LENGTH_TOLERANCE_SEC }
            .filter {
                val len = it.interval.endTime - it.interval.startTime
                it.interval.startTime >= 0 && len > 1 && len <= MAX_SEGMENT_SEC && it.interval.endTime <= lengthSec + 1
            }
            // A plain opening/ending beats a "mixed" one (opening playing over the story)
            .sortedBy { if (it.skipType.startsWith("mixed")) 1 else 0 }
            .distinctBy { it.segmentType }

    /**
     * Uses AniSkip's times in place of the server's for the same kind (opening, ending, recap),
     * and keeps the server's other kinds (like previews).
     */
    fun merge(
        server: List<MediaSegmentDto>,
        aniSkip: List<MediaSegmentDto>,
    ): List<MediaSegmentDto> {
        if (aniSkip.isEmpty()) return server
        val replaced = aniSkip.map { it.type }.toSet()
        return server.filter { it.type !in replaced } + aniSkip
    }
}

/** Which MyAnimeList entry and episode an item is */
data class AniSkipTarget(
    val malId: Int,
    val episode: Int,
)

/**
 * The TVDB/TMDB/AniList -> MyAnimeList links, one per line:
 * source, id, season (-1 = not given), episode offset, type, MyAnimeList id.
 */
class AniSkipMap private constructor(
    private val bySource: Map<String, Map<Int, List<Link>>>,
) {
    data class Link(
        val season: Int,
        val offset: Int,
        val type: String,
        val malId: Int,
    )

    /**
     * Finds the MyAnimeList entry for an episode (by its show's ids, season and episode number)
     * or a movie (by its own ids). When a TVDB/TMDB season is split into several MyAnimeList
     * entries, the entry that starts last before this episode is the one.
     */
    fun find(
        ids: Map<String, String?>,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
    ): AniSkipTarget? {
        fun id(vararg names: String) =
            ids.entries
                .firstOrNull { (key, _) -> names.any { it.equals(key, ignoreCase = true) } }
                ?.value
                ?.trim()
                ?.toIntOrNull()
        if (isMovie) {
            val tmdb = id("Tmdb") ?: return null
            val link = bySource["movie"]?.get(tmdb)?.firstOrNull() ?: return null
            return AniSkipTarget(link.malId, 1)
        }
        val ep = episode?.takeIf { it > 0 } ?: return null
        val s = season ?: 1
        if (s <= 0) return null
        for ((source, key) in listOf("tvdb" to id("Tvdb"), "tmdb" to id("Tmdb"))) {
            val links = key?.let { bySource[source]?.get(it) } ?: continue
            val inSeason =
                links
                    .filter { it.season == s && it.type != "MOVIE" && it.offset < ep }
                    .sortedWith(compareByDescending<Link> { it.offset }.thenBy { if (it.type == "TV") 0 else 1 })
            inSeason.firstOrNull()?.let { return AniSkipTarget(it.malId, ep - it.offset) }
        }
        // Shows matched with the AniList or MyAnimeList plugins: these ids are for the first season
        if (s == 1) {
            id("MyAnimeList", "Mal")?.let { return AniSkipTarget(it, ep) }
            id("AniList")?.let { anilist ->
                bySource["anilist"]?.get(anilist)?.firstOrNull()?.let { return AniSkipTarget(it.malId, ep) }
            }
        }
        return null
    }

    companion object {
        fun line(
            source: String,
            id: Int,
            season: Int,
            offset: Int,
            type: String,
            malId: Int,
        ) = "$source\t$id\t$season\t$offset\t$type\t$malId"

        fun parse(lines: List<String>): AniSkipMap {
            val out = mutableMapOf<String, MutableMap<Int, MutableList<Link>>>()
            lines.forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 6) return@forEach
                val id = parts[1].toIntOrNull() ?: return@forEach
                val mal = parts[5].toIntOrNull() ?: return@forEach
                out
                    .getOrPut(parts[0]) { mutableMapOf() }
                    .getOrPut(id) { mutableListOf() }
                    .add(Link(parts[2].toIntOrNull() ?: -1, parts[3].toIntOrNull() ?: 0, parts[4], mal))
            }
            return AniSkipMap(out)
        }
    }
}
