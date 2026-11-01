package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.ui.launchIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets a movie's or show's full details while you rest on its poster, so the details page
 * opens instantly when you press OK.
 *
 * A fetched item is used once and only for a couple of minutes, so watched/favorite marks stay fresh.
 */
@Singleton
class DetailsPrefetch
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        private data class Entry(
            val item: BaseItemDto,
            val at: Long,
        )

        private val cache = ConcurrentHashMap<UUID, Entry>()
        private var job: Job? = null

        /** Start getting [itemId]'s details after a short rest on it (cancels the previous one) */
        fun prefetch(
            scope: CoroutineScope,
            itemId: UUID,
            type: BaseItemKind?,
        ) {
            if (type != BaseItemKind.MOVIE && type != BaseItemKind.SERIES) return
            job?.cancel()
            if (fresh(itemId) != null) return
            job =
                scope.launchIO {
                    delay(REST_MS)
                    try {
                        val item = api.userLibraryApi.getItem(itemId).content
                        cache[itemId] = Entry(item, System.currentTimeMillis())
                        trim()
                    } catch (ex: Exception) {
                        Timber.v(ex, "Prefetch failed for %s", itemId)
                    }
                }
        }

        /** The prefetched details for [itemId], if fresh. Each prefetch is handed out once. */
        fun take(itemId: UUID): BaseItemDto? = fresh(itemId)?.also { cache.remove(itemId) }

        private fun fresh(itemId: UUID): BaseItemDto? {
            val entry = cache[itemId] ?: return null
            return if (System.currentTimeMillis() - entry.at < MAX_AGE_MS) {
                entry.item
            } else {
                cache.remove(itemId)
                null
            }
        }

        private fun trim() {
            if (cache.size <= MAX_ITEMS) return
            cache.entries
                .sortedBy { it.value.at }
                .take(cache.size - MAX_ITEMS)
                .forEach { cache.remove(it.key) }
        }

        companion object {
            private const val REST_MS = 600L
            private const val MAX_AGE_MS = 2 * 60 * 1000L
            private const val MAX_ITEMS = 10
        }
    }
