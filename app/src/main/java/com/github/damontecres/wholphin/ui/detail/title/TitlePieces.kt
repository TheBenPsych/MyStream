package com.github.damontecres.wholphin.ui.detail.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.services.AniListFacts
import com.github.damontecres.wholphin.services.MalFacts
import com.github.damontecres.wholphin.ui.cards.PersonCard

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
 * MyAnimeList (falling back to IMDb). Sites without a score are left out.
 */
@Composable
fun rememberScoreParts(item: BaseItem): List<StripPart> {
    val aniList by AniListFacts.cards.collectAsState()
    val mal by MalFacts.scores.collectAsState()
    val facts = aniList[item.id]
    val malScore = facts?.malId?.let { mal[it] }
    return remember(item, facts, malScore) {
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
            }
        }
    }
}

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
