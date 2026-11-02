package com.github.damontecres.wholphin.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.text.Normalizer
import java.util.UUID

/**
 * Benflix typo-forgiving search: when the server finds little or nothing ("avengrs", "shrek 3"
 * spelled wrong), compare the search with every movie and show title and offer the close ones.
 * The list of titles is fetched once and kept for 15 minutes.
 */
object FuzzyTitles {
    private data class Title(
        val id: UUID,
        val type: BaseItemKind,
        val clean: String,
        val words: List<String>,
    )

    private val mutex = Mutex()
    private var titles: List<Title> = emptyList()
    private var loadedFor: UUID? = null
    private var loadedAt = 0L

    fun clean(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Ids of titles that look like a misspelling of [query], closest first */
    suspend fun closeMatches(
        api: ApiClient,
        userId: UUID?,
        query: String,
        types: List<BaseItemKind>,
        limit: Int = 20,
    ): List<UUID> {
        val q = clean(query)
        if (q.length < 3) return emptyList()
        val all = load(api, userId)
        val allowed = (q.length / 4).coerceIn(1, 3)
        return all
            .asSequence()
            .filter { it.type in types }
            .mapNotNull { title -> distance(q, title)?.takeIf { it <= allowed }?.let { title to it } }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first.id }
            .toList()
    }

    /** How far the search is from this title: the whole title, its start, or any run of its words */
    private fun distance(
        q: String,
        title: Title,
    ): Int? {
        if (title.clean.isEmpty()) return null
        val qWords = q.split(" ").size
        var best = levenshtein(q, title.clean)
        if (title.clean.length > q.length) best = minOf(best, levenshtein(q, title.clean.take(q.length)))
        for (start in title.words.indices) {
            val end = minOf(title.words.size, start + qWords)
            val piece = title.words.subList(start, end).joinToString(" ")
            best = minOf(best, levenshtein(q, piece))
        }
        return best
    }

    private suspend fun load(
        api: ApiClient,
        userId: UUID?,
    ): List<Title> =
        mutex.withLock {
            val fresh = System.currentTimeMillis() - loadedAt < KEEP_MS && loadedFor == userId
            if (!fresh || titles.isEmpty()) {
                titles =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET),
                                enableImages = false,
                                enableUserData = false,
                                enableTotalRecordCount = false,
                            ),
                        ).content
                        .items
                        .mapNotNull { dto ->
                            val name = dto.name ?: return@mapNotNull null
                            val c = clean(name)
                            Title(dto.id, dto.type, c, c.split(" "))
                        }
                loadedFor = userId
                loadedAt = System.currentTimeMillis()
            }
            titles
        }

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }

    private const val KEEP_MS = 15L * 60 * 1000
}
