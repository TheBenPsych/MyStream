package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: matches every show and movie in the anime libraries to AniList in the background,
 * so the AniList and MyAnimeList scores and fan genres (Isekai, Shounen...) show on cards and
 * info lines everywhere, not only after a show's own page has been opened.
 *
 * Runs when Home, Movies, Shows or Anime loads, at most once every few hours per library. The matches are saved on the
 * device for a week, so after the first run only new titles are looked up.
 */
@Singleton
class AniListLibraries
    @Inject
    constructor(
        private val api: ApiClient,
        private val aniList: AniListService,
        private val malScores: MalScores,
        private val sharedScores: SharedAnimeScores,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)
        private val lastRun = ConcurrentHashMap<UUID, Long>()

        @Volatile private var job: Job? = null

        /** Matches the anime libraries (any library with "anime" in its name) in the background */
        fun warm(
            libraryIds: List<UUID>,
            userId: UUID?,
        ) {
            if (libraryIds.isEmpty() || KidsMode.isActive || job?.isActive == true) return
            val now = System.currentTimeMillis()
            val due = libraryIds.filter { now - (lastRun[it] ?: 0L) > AGAIN_MS }
            if (due.isEmpty()) return
            job =
                scope.launch {
                    // First the scores already saved on the device, so anime shows AniList and MyAnimeList
                    // right away on every page (no waiting on the server, nothing asked again)
                    try {
                        malScores.publishSaved()
                        due.forEach { aniList.publishSaved(it) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not read the saved AniList matches")
                    }
                    // Then the new titles, a little later so the page's own rows load first on slower TVs
                    delay(SETTLE_MS)
                    // What other devices already looked up, shared through Tower (so AniList is asked far less)
                    val shared = sharedScores.get()
                    if (shared != null) {
                        aniList.addShared(shared.matches)
                        malScores.addShared(shared.mal)
                    }
                    val malIds = mutableListOf<Int>()
                    val newMatches = mutableMapOf<UUID, AniListIndexEntry>()
                    due.forEach { id ->
                        try {
                            val index = aniList.index(id, titlesIn(id, userId), shared?.matches.orEmpty())
                            malIds += index.values.mapNotNull { it.malId?.takeIf { m -> m > 0 } }
                            index.forEach { (k, e) -> if (e.id > 0 && shared?.matches?.get(k)?.id != e.id) newMatches[k] = e }
                            lastRun[id] = System.currentTimeMillis()
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not match anime library %s to AniList", id)
                        }
                    }
                    // Then their MyAnimeList scores (slower: one show at a time, only ones nobody has yet)
                    try {
                        malScores.fetch(malIds)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not get MyAnimeList scores")
                    }
                    // Hand Tower what it didn't have, for the other TVs, phones and browsers
                    if (shared != null) {
                        val newMal = malScores.savedFor(malIds).filterKeys { it !in shared.mal }
                        sharedScores.add(newMatches, newMal)
                    }
                }
        }

        /** Every show and movie in a library, with the names and ids AniList matching needs */
        suspend fun titlesIn(
            libraryId: UUID,
            userId: UUID?,
        ): List<BaseItemDto> {
            val out = mutableListOf<BaseItemDto>()
            var start = 0
            while (true) {
                val page =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                parentId = libraryId,
                                includeItemTypes = listOf(BaseItemKind.SERIES, BaseItemKind.MOVIE),
                                recursive = true,
                                fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.ORIGINAL_TITLE),
                                enableImages = false,
                                enableUserData = false,
                                startIndex = start,
                                limit = PAGE,
                            ),
                        ).content.items
                out += page
                if (page.size < PAGE) break
                start += PAGE
            }
            return out
        }

        companion object {
            private const val PAGE = 500
            private const val AGAIN_MS = 6L * 60 * 60 * 1000
            private const val SETTLE_MS = 8_000L
        }
    }
