package com.github.damontecres.wholphin.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.CantFindEntry
import com.github.damontecres.wholphin.ui.door.CoverTopWords
import com.github.damontecres.wholphin.ui.theme.BenflixColors

/*
 * Glowfin new look (Ben approved 2026-11-22): family members get Your asks as rows of covers instead of a list. Each
 * cover has its dot, and on TV the top tells them where it stands (waiting for Ben, on its way, here, or Ben said no
 * with his reason), so nobody has to ask Ben. Here now from your asks: OK plays it.
 */

private const val POSTER_RATIO = 2f / 3f

/** Where one ask stands, for its dot and the words at the top */
private fun RequestEntry.familyTag(
    cantFind: CantFindEntry?,
    reason: String?,
): RqTag {
    val asked = ago(request.createdAt)?.let { "you asked $it" }
    return when {
        available -> {
            RqTag(RqTagKind.HERE, "Here now on Glowfin · OK opens it")
        }

        status == RequestStatus.DECLINED -> {
            RqTag(RqTagKind.PROBLEM, if (reason != null) "Ben said no: $reason" else "Ben said no")
        }

        cantFind != null -> {
            RqTag(RqTagKind.PROBLEM, cantFind.note?.let { "Ben couldn't find it: $it" } ?: "Ben looked but couldn't find a copy")
        }

        status == RequestStatus.PENDING -> {
            RqTag(RqTagKind.WAITING, listOfNotNull("Waiting for Ben", asked).joinToString(" · "))
        }

        downloading -> {
            RqTag(RqTagKind.ON_WAY, downloadPercent?.let { "Downloading · $it%" } ?: "Downloading now")
        }

        status == RequestStatus.FAILURE -> {
            RqTag(RqTagKind.PROBLEM, "Stuck · Ben will have a look")
        }

        else -> {
            RqTag(RqTagKind.ON_WAY, "On its way · Ben said yes")
        }
    }
}

private fun DiscoverItem.familyFacts(): String =
    listOfNotNull(
        when (type) {
            SeerrItemType.MOVIE -> if (isAnime) "Anime movie" else "Movie"
            SeerrItemType.TV -> if (isAnime) "Anime" else "Show"
            else -> null
        },
        releaseDate?.year?.toString(),
    ).joinToString(" · ")

@Composable
internal fun FamilyAskRows(
    entries: List<RequestEntry>,
    others: List<AskLine>,
    cantFind: Map<String, CantFindEntry>,
    reasons: Map<String, String>,
    isTv: Boolean,
    onOpen: (DiscoverItem) -> Unit,
) {
    val newest = entries.sortedByDescending { it.request.createdAt.orEmpty() }

    fun card(entry: RequestEntry): RqCoverCard? {
        val item = entry.item ?: return null
        val tag =
            entry.familyTag(
                cantFind = if (entry.available) null else entry.cantFindKey()?.let { cantFind[it] },
                reason = entry.request.id?.let { reasons[DeclineWhy.key(it)] },
            )
        return RqCoverCard(
            key = "fam-${entry.request.id ?: item.id}",
            cover = item.posterUrl,
            name = item.title.orEmpty(),
            line = null,
            words = CoverTopWords(title = item.title.orEmpty(), facts = item.familyFacts(), tag = tag.words, story = item.overview),
            onClick = { onOpen(item) },
            dot = tag.kind.dot,
        )
    }
    val waiting = newest.filter { !it.available }.mapNotNull(::card)
    val here = newest.filter { it.available }.mapNotNull(::card)
    val songsAndBooks =
        others.map { line ->
            val dot =
                when (line.pill.state) {
                    AskState.READY -> RqTagKind.HERE.dot
                    AskState.GETTING -> RqTagKind.ON_WAY.dot
                    AskState.CANT -> RqTagKind.PROBLEM.dot
                    else -> RqTagKind.WAITING.dot
                }
            RqCoverCard(
                key = "fam-${line.key}",
                cover = line.cover,
                name = line.title,
                line = null,
                words =
                    CoverTopWords(
                        title = line.title,
                        facts = line.sub,
                        tag = line.pill.words,
                        extra = line.why.takeIf { it.isNotBlank() },
                    ),
                onClick = { line.onOpen?.invoke() },
                dot = dot,
            )
        }
    val rows =
        listOfNotNull(
            waiting.takeIf { it.isNotEmpty() }?.let { RqCoverRow.Covers("asks", "Your asks", it, note = "newest first") },
            here.takeIf { it.isNotEmpty() }?.let { RqCoverRow.Covers("here", "Here now from your asks", it) },
            songsAndBooks.takeIf { it.isNotEmpty() }?.let { RqCoverRow.Covers("other", "Songs and books you asked for", it) },
        )
    RequestCoverRows(
        rows = rows,
        isTv = isTv,
        ratio = POSTER_RATIO,
        glowFallback = BenflixColors.Movies,
        top = 0.dp,
    )
}
