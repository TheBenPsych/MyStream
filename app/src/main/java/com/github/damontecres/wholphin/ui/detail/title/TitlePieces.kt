package com.github.damontecres.wholphin.ui.detail.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.github.damontecres.wholphin.services.ParentsGuideLine
import com.github.damontecres.wholphin.ui.cards.PersonCard
import com.github.damontecres.wholphin.ui.detail.ParentsGuideBlock

/** The Cast tab: one row of faces on TV, 3 across on phones */
@Composable
fun TitleCastRow(
    scope: TitleTabScope,
    people: List<Person>,
    onClick: (Person) -> Unit,
) {
    val list = remember(people) { people.distinctBy { it.id } }
    if (list.isEmpty()) return
    if (scope.wide) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = scope.side),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(list, key = { _, it -> it.id.toString() }) { _, p ->
                PersonCard(person = p, onClick = { onClick(p) }, onLongClick = {}, modifier = Modifier.width(100.dp))
            }
        }
    } else {
        TitleGrid(scope, list.size) { index, width ->
            val p = list[index]
            PersonCard(person = p, onClick = { onClick(p) }, onLongClick = {}, modifier = Modifier.width(width))
        }
    }
}

/** The Parents' guide tab */
@Composable
fun TitleParentsGuide(
    scope: TitleTabScope,
    lines: List<ParentsGuideLine>,
) {
    ParentsGuideBlock(
        lines,
        Modifier
            .padding(horizontal = scope.side)
            .then(if (scope.wide) Modifier.widthIn(max = 700.dp) else Modifier),
    )
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
