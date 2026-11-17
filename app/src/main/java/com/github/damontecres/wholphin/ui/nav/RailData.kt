package com.github.damontecres.wholphin.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.door.DoorDataService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** What a door's slide-out panel shows besides its places: the picture strip and Jump back in */
data class RailPanelData(
    /** A wide picture from this door (the same one all day), or null for the soft colour glow */
    val art: String? = null,
    val jumpBackIn: List<BaseItem> = emptyList(),
)

/**
 * Navigation redesign (approved pa-strip and s4-panel): finds each door's panel picture (one picture from your
 * Movies, Shows or Music, changing each day) and its Jump back in titles. Kept in memory, so opening the
 * same panel again is instant.
 */
@Singleton
class RailDataService
    @Inject
    constructor(
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val doorData: DoorDataService,
        private val serverRepository: ServerRepository,
    ) {
        private data class Art(
            val key: String,
            val url: String?,
        )

        private data class Jump(
            val key: String,
            val at: Long,
            val items: List<BaseItem>,
        )

        private val arts = ConcurrentHashMap<BenflixDoor, Art>()
        private val jumps = ConcurrentHashMap<BenflixDoor, Jump>()

        private fun who(): String = "${serverRepository.currentUser?.id}-${KidsMode.level}"

        /** What's already known for [door] (may be empty while it loads) */
        fun known(door: BenflixDoor): RailPanelData =
            RailPanelData(
                art = arts[door]?.takeIf { it.key == who() + LocalDate.now() }?.url,
                jumpBackIn = jumps[door]?.takeIf { it.key == who() }?.items.orEmpty(),
            )

        suspend fun load(door: BenflixDoor): RailPanelData {
            val userId = serverRepository.currentUser?.id ?: return RailPanelData()
            val artKey = who() + LocalDate.now()
            if (arts[door]?.key != artKey) {
                arts[door] = Art(artKey, findArt(door))
            }
            val jump = jumps[door]
            if (jump == null || jump.key != who() || System.currentTimeMillis() - jump.at > JUMP_KEEP_MS) {
                val items =
                    try {
                        when (door) {
                            BenflixDoor.MOVIES -> doorData.resumeMovies(userId)
                            BenflixDoor.SHOWS -> doorData.keepWatchingEpisodes(userId)
                            BenflixDoor.MUSIC -> recentAlbums()
                            else -> emptyList()
                        }.take(JUMP_COUNT)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Rail: Jump back in for %s", door)
                        emptyList()
                    }
                jumps[door] = Jump(who(), System.currentTimeMillis(), items)
            }
            return known(door)
        }

        private suspend fun recentAlbums(): List<BaseItem> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = serverRepository.currentUser?.id,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        isPlayed = true,
                        fields = SlimItemFields,
                        limit = JUMP_COUNT,
                        enableTotalRecordCount = false,
                    ),
                ).content.items
                .map { BaseItem(it) }

        /** One wide picture for the strip, the same all day: Movies, Shows and Music only */
        private suspend fun findArt(door: BenflixDoor): String? {
            val type =
                when (door) {
                    BenflixDoor.MOVIES -> BaseItemKind.MOVIE
                    BenflixDoor.SHOWS -> BaseItemKind.SERIES
                    BenflixDoor.MUSIC -> BaseItemKind.MUSIC_ARTIST
                    else -> return null
                }
            return try {
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                includeItemTypes = listOf(type),
                                recursive = true,
                                imageTypes = listOf(ImageType.BACKDROP),
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                enableImages = false,
                                limit = ART_POOL,
                                enableTotalRecordCount = false,
                            ),
                        ).content.items
                val pick = items.getOrNull(LocalDate.now().dayOfYear % items.size.coerceAtLeast(1)) ?: return null
                imageUrlService.getItemImageUrl(
                    itemId = pick.id,
                    imageType = ImageType.BACKDROP,
                    fillWidth = ART_WIDTH,
                    fillHeight = ART_HEIGHT,
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Rail: picture for %s", door)
                null
            }
        }

        private companion object {
            const val JUMP_COUNT = 3
            const val JUMP_KEEP_MS = 2 * 60 * 1000L
            const val ART_POOL = 40
            const val ART_WIDTH = 640
            const val ART_HEIGHT = 240
        }
    }

@HiltViewModel
class RailViewModel
    @Inject
    constructor(
        private val service: RailDataService,
    ) : ViewModel() {
        private val _panels = MutableStateFlow<Map<BenflixDoor, RailPanelData>>(emptyMap())
        val panels: StateFlow<Map<BenflixDoor, RailPanelData>> = _panels

        fun load(door: BenflixDoor) {
            _panels.update { it + (door to service.known(door)) }
            viewModelScope.launchIO {
                val data = service.load(door)
                _panels.update { it + (door to data) }
            }
        }
    }
