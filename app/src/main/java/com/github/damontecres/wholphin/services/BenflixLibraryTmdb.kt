package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix (Ben, 2026-11-16): what's really on Benflix, straight from Jellyfin, by TMDB number. Seerr's own
 * "available" often runs behind or is wrong, so Requests lists use this to leave out what you already have.
 * Keys are "MOVIE-123" / "TV-456" (the same as a request key), each to the title's Jellyfin id.
 */
@Singleton
class BenflixLibraryTmdb
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val seerrService: SeerrService,
        private val showArrival: ShowArrival,
    ) {
        private val lock = Mutex()
        private var cached: Pair<Long, Map<String, UUID>>? = null

        /** Looked up again after 10 minutes */
        suspend fun keys(): Map<String, UUID> =
            lock.withLock {
                cached?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000 }?.let { return@withLock it.second }
                val found = load() ?: return@withLock cached?.second ?: emptyMap()
                cached = System.currentTimeMillis() to found
                found
            }

        private suspend fun load(): Map<String, UUID>? =
            withContext(WholphinDispatchers.IO) {
                try {
                    val userId = serverRepository.currentUser?.id ?: return@withContext null
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                                fields = listOf(ItemFields.PROVIDER_IDS),
                                enableImages = false,
                                enableUserData = false,
                                enableTotalRecordCount = false,
                            ),
                        ).content.items
                        .mapNotNull { dto ->
                            val tmdb = dto.providerIds?.get("Tmdb")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val kind = if (dto.type == BaseItemKind.MOVIE) SeerrItemType.MOVIE else SeerrItemType.TV
                            "${kind.name}-$tmdb" to dto.id
                        }.toMap()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't list what's already on Benflix")
                    null
                }
            }

        /**
         * For a short row like "More like this": leaves out what's already on Benflix. A show that's here but
         * missing aired episodes stays, marked partly here (its card says "MISSING EPISODES").
         */
        suspend fun notOnBenflix(items: List<DiscoverItem>): List<DiscoverItem> =
            withContext(WholphinDispatchers.IO) {
                val library = keys()
                val semaphore = Semaphore(4)
                coroutineScope {
                    items
                        .map { item ->
                            async {
                                val key = "${item.type.name}-${item.id}"
                                val inJellyfin = library[key]
                                val seerrSays =
                                    item.availability == SeerrAvailability.AVAILABLE ||
                                        item.availability == SeerrAvailability.PARTIALLY_AVAILABLE
                                when {
                                    inJellyfin == null && !seerrSays -> {
                                        item
                                    }

                                    item.type == SeerrItemType.TV -> {
                                        val whole =
                                            semaphore.withPermit {
                                                try {
                                                    seerrService.api
                                                        .getRaw("/tv/${item.id}")
                                                        ?.let { Json.parseToJsonElement(it).jsonObject }
                                                        ?.let { showArrival.wholeShowHere(it, inJellyfin) }
                                                } catch (ex: CancellationException) {
                                                    throw ex
                                                } catch (ex: Exception) {
                                                    null
                                                }
                                            }
                                        when (whole) {
                                            false -> item.copy(availability = SeerrAvailability.PARTIALLY_AVAILABLE)

                                            true -> null

                                            // Couldn't check: here per Jellyfin means here
                                            null -> if (inJellyfin != null) null else item
                                        }
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                }
            }
    }
