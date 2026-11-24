package com.github.damontecres.wholphin.ui.detail.title

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.services.AniListFacts
import com.github.damontecres.wholphin.services.AniListService
import com.github.damontecres.wholphin.services.BenflixAnimeTracks
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.MalFacts
import com.github.damontecres.wholphin.services.MalScores
import com.github.damontecres.wholphin.ui.cards.PersonCard
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind

/** The Cast tab: one sideways row of faces, on TVs and phones */
@Composable
fun TitleCastRow(
    scope: TitleTabScope,
    people: List<Person>,
    onClick: (Person) -> Unit,
) {
    val list = remember(people) { people.distinctBy { it.id } }
    if (list.isEmpty()) return
    // Phones too (overhaul 2026-11-20): one sideways row. The old three-across grid ran for pages on a big cast.
    LazyRow(
        contentPadding = PaddingValues(horizontal = scope.side),
        horizontalArrangement = Arrangement.spacedBy(if (scope.wide) 14.dp else 10.dp),
    ) {
        itemsIndexed(list, key = { _, it -> it.id.toString() }) { _, p ->
            PersonCard(person = p, onClick = { onClick(p) }, onLongClick = {}, modifier = Modifier.width(if (scope.wide) 100.dp else 88.dp))
        }
    }
}

/**
 * The scores for the slim strip: movies and shows get IMDb and Rotten Tomatoes, anime AniList and
 * MyAnimeList (falling back to IMDb). Sites without a score are left out. An episode uses its show's.
 *
 * Ben 2026-11-24: anime often showed only IMDb. The scores came only from the Anime tab's background
 * matching, so a title page opened from Home or Search (or an episode page, which looked under the
 * episode's own id) had none. Now an anime title without them asks AniList (and MyAnimeList) itself.
 * Both are saved on the device (AniList for a week, MyAnimeList for a week), so they are not asked again
 * every time the app opens.
 */
@Composable
fun rememberScoreParts(item: BaseItem): List<StripPart> {
    val aniList by AniListFacts.cards.collectAsState()
    val mal by MalFacts.scores.collectAsState()
    val showId = item.data.seriesId?.takeIf { item.type == BaseItemKind.EPISODE || item.type == BaseItemKind.SEASON }
    val facts = aniList[item.id] ?: showId?.let { aniList[it] }
    val malScore = facts?.malId?.let { mal[it] }
    val context = LocalContext.current
    // Not matched yet: an anime movie or show asks AniList now (an episode asks for its show)
    val askFor = item.takeIf { facts == null && it.type in LOOKUP_KINDS && BenflixAnimeTracks.isAnime(it.data) }
    LaunchedEffect(askFor?.id) {
        if (askFor == null || KidsMode.isActive) return@LaunchedEffect
        try {
            val scores = animeScores(context)
            val dto =
                if (askFor.type == BaseItemKind.MOVIE || askFor.type == BaseItemKind.SERIES) {
                    askFor.data
                } else {
                    val id = askFor.data.seriesId ?: return@LaunchedEffect
                    scores
                        .api()
                        .userLibraryApi
                        .getItem(id)
                        .content
                }
            val info = scores.aniList().infoFor(dto) ?: return@LaunchedEffect
            info.idMal?.takeIf { it > 0 }?.let { scores.malScores().fetch(listOf(it)) }
        } catch (ex: Exception) {
            if (ex is kotlinx.coroutines.CancellationException) throw ex
            timber.log.Timber.w(ex, "Could not get the anime scores of %s", askFor.name)
        }
    }
    // Matched, but its MyAnimeList score isn't showing yet: the saved one, or ask Jikan
    val malId = facts?.malId?.takeIf { it > 0 && malScore == null }
    LaunchedEffect(malId) {
        if (malId == null) return@LaunchedEffect
        try {
            animeScores(context).malScores().fetch(listOf(malId))
        } catch (ex: Exception) {
            if (ex is kotlinx.coroutines.CancellationException) throw ex
        }
    }
    // Glowfin (Idea Box 27): Metacritic joins IMDb and Rotten Tomatoes (not for anime)
    val metacritic = if (facts == null) rememberMetacritic(item) else null
    return remember(item, facts, malScore, metacritic) {
        buildList {
            if (facts != null) {
                facts.scoreText?.let { add(StripPart("AniList", it)) }
                malScore?.let { add(StripPart("MAL", MalFacts.text(it))) }
            }
            if (isEmpty()) {
                item.data.communityRating?.takeIf { it > 0f }?.let {
                    add(StripPart("IMDb", String.format(java.util.Locale.US, "%.1f", it)))
                }
                item.data.criticRating
                    ?.takeIf { it > 0f }
                    ?.let { add(StripPart("Rotten Tomatoes", "${it.toInt()}%")) }
                metacritic?.let { add(StripPart("Metacritic", "$it")) }
            }
        }
    }
}

private val LOOKUP_KINDS = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnimeScoresEntryPoint {
    fun aniList(): AniListService

    fun malScores(): MalScores

    fun api(): ApiClient
}

private fun animeScores(context: Context): AnimeScoresEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, AnimeScoresEntryPoint::class.java)

/**
 * The Details list for the Overview tab (the same as the web): studio (network for shows), released,
 * genres, country, and for the admin the file name, its size and the picture size.
 */
fun titleDetails(
    item: BaseItem,
    admin: Boolean,
    show: Boolean = false,
): List<Pair<String, String>> {
    val d = item.data
    return buildList {
        d.studios
            ?.mapNotNull { it.name }
            ?.take(2)
            ?.takeIf { it.isNotEmpty() }
            ?.let { add((if (show) "Network" else "Studio") to it.joinToString(", ")) }
        d.premiereDate?.let {
            val day =
                try {
                    it.format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH),
                    )
                } catch (ex: Exception) {
                    null
                }
            if (day != null) add((if (show) "First aired" else "Released") to day)
        }
        d.genres?.takeIf { it.isNotEmpty() }?.let { add("Genres" to it.joinToString(", ")) }
        d.productionLocations
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { add("Country" to it) }
        val source = d.mediaSources?.firstOrNull()
        if (admin && source != null) {
            source.path
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.takeIf { it.isNotBlank() }
                ?.let { add("File" to it) }
            source.size?.takeIf { it > 0 }?.let { add("Size" to String.format(java.util.Locale.ENGLISH, "%.1f GB", it / 1073741824.0)) }
            source.mediaStreams
                ?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
                ?.let { v ->
                    val range = v.videoRangeType?.serialName?.takeIf { it != "SDR" && it != "Unknown" }
                    listOfNotNull(
                        if (v.width != null && v.height != null) "${v.width}×${v.height}" else null,
                        v.codec?.uppercase(),
                        range,
                    ).joinToString(" · ").takeIf { it.isNotBlank() }?.let { add("Video" to it) }
                }
        }
    }
}

/**
 * The small tags after a title's info line (Ben 2026-11-21): picture size, HDR kind, sound and whether
 * English subtitles are there. Empty when the file's details aren't known (a show, or a title still loading).
 */
fun qualityChips(item: BaseItem): List<String> {
    val streams =
        item.data.mediaSources
            ?.firstOrNull()
            ?.mediaStreams
            ?: item.data.mediaStreams
            ?: return emptyList()
    val video = streams.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO } ?: return emptyList()
    val audio =
        streams.filter { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }.let { all ->
            all.firstOrNull { it.isDefault } ?: all.firstOrNull()
        }
    return buildList {
        val w = video.width ?: 0
        val h = video.height ?: 0
        when {
            w > 2560 || h > 1440 -> add("4K")
            w > 1280 || h > 962 -> add("1080p")
            w > 1024 || h > 576 -> add("720p")
            w > 0 -> add("SD")
        }
        val range = video.videoRangeType?.serialName.orEmpty()
        when {
            range.startsWith("DOVI", ignoreCase = true) -> add("Dolby Vision")
            range.startsWith("HDR", ignoreCase = true) || range.equals("HLG", ignoreCase = true) -> add("HDR")
        }
        if (audio != null) {
            val channels = audio.channels ?: 0
            when {
                audio.profile?.contains("Atmos", ignoreCase = true) == true ||
                    audio.displayTitle?.contains("Atmos", ignoreCase = true) == true -> add("Atmos")

                channels >= 8 -> add("7.1")

                channels >= 6 -> add("5.1")

                channels == 2 -> add("Stereo")
            }
        }
        val englishSubs =
            streams.any {
                it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE &&
                    it.language?.lowercase() in setOf("eng", "en", "english")
            }
        if (englishSubs) add("CC")
    }
}
