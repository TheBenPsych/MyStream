package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Which collection each movie is in, as saved on the device (movie id to collection id and name) */
@Serializable
private data class CollectionLookupFile(
    val byMovie: Map<String, String>,
    val names: Map<String, String>,
)

/** A movie's collection: its id and name, and where the movie sits in it */
data class MovieCollection(
    val id: UUID,
    val name: String,
    val items: List<BaseItem>,
) {
    fun partOf(movieId: UUID): Pair<Int, Int>? {
        val index = items.indexOfFirst { it.id == movieId }
        return if (index < 0) null else (index + 1) to items.size
    }
}

/**
 * Benflix redesign: "Part 1 of 3" and the "In this collection" row on a movie's page. Jellyfin can't say
 * which collection a movie is in, so every collection is read once in the background and the answer
 * is saved on the device for a day.
 */
@Singleton
class CollectionLookup
    @Inject
    constructor(
        private val api: ApiClient,
        private val diskCache: DiskCache,
    ) {
        private val lock = Mutex()

        @Volatile private var index: Pair<String, CollectionLookupFile>? = null

        private fun key(userId: UUID) = "collection-lookup-$userId-${KidsMode.level}"

        /** The collection this movie is in, with its movies in release order; null when it isn't in one */
        suspend fun collectionFor(
            userId: UUID,
            movieId: UUID,
        ): MovieCollection? {
            val file = load(userId) ?: return null
            val collectionId = file.byMovie[movieId.toString()] ?: return null
            val name = file.names[collectionId] ?: return null
            return try {
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                parentId = UUID.fromString(collectionId),
                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                fields = SlimItemFields,
                                enableUserData = true,
                                sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR),
                                sortOrder = listOf(SortOrder.ASCENDING, SortOrder.ASCENDING),
                            ),
                        ).content.items
                        .map { BaseItem(it) }
                if (items.size < 2) null else MovieCollection(UUID.fromString(collectionId), name, items)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read collection %s", collectionId)
                null
            }
        }

        private suspend fun load(userId: UUID): CollectionLookupFile? =
            lock.withLock {
                val key = key(userId)
                index?.takeIf { it.first == key }?.let { return@withLock it.second }
                diskCache.read(key, CollectionLookupFile.serializer(), MAX_AGE_MS)?.let {
                    index = key to it
                    return@withLock it
                }
                val built = build(userId) ?: return@withLock null
                diskCache.write(key, CollectionLookupFile.serializer(), built)
                index = key to built
                built
            }

        private suspend fun build(userId: UUID): CollectionLookupFile? =
            withContext(WholphinDispatchers.IO) {
                try {
                    val collections =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                    recursive = true,
                                    enableImages = false,
                                    limit = MAX_COLLECTIONS,
                                ),
                            ).content.items
                    val permits = Semaphore(PARALLEL)
                    val byMovie =
                        coroutineScope {
                            collections
                                .map { collection ->
                                    async {
                                        permits.withPermit {
                                            try {
                                                api.itemsApi
                                                    .getItems(
                                                        GetItemsRequest(
                                                            userId = userId,
                                                            parentId = collection.id,
                                                            enableImages = false,
                                                            enableUserData = false,
                                                            limit = 200,
                                                        ),
                                                    ).content.items
                                                    .map { it.id.toString() to collection.id.toString() }
                                            } catch (ex: CancellationException) {
                                                throw ex
                                            } catch (ex: Exception) {
                                                emptyList()
                                            }
                                        }
                                    }
                                }.awaitAll()
                                .flatten()
                                .toMap()
                        }
                    CollectionLookupFile(
                        byMovie = byMovie,
                        names = collections.associate { it.id.toString() to (it.name ?: "Collection") },
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not read the collections")
                    null
                }
            }

        companion object {
            private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
            private const val MAX_COLLECTIONS = 400
            private const val PARALLEL = 6
        }
    }
