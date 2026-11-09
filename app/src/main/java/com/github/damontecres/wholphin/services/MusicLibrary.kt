package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.DetailItemFields
import com.github.damontecres.wholphin.ui.SongListFields
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A "Made for you" playlist that Tower makes every morning (approved: about 6 a day, mostly songs you play
 * a lot plus a few you rarely play). [line] is the small words under it, like "Static Bloom, Luna Vale and more".
 */
data class MusicMix(
    val key: String,
    val name: String,
    val line: String,
    val songIds: List<UUID>,
    /** Up to four album ids for the cover collage */
    val coverAlbumIds: List<UUID>,
)

/** Benflix music: everything the music pages need from Jellyfin (and the mixes from Tower) */
@Singleton
class MusicLibrary
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val helper: GameLibraryClient,
    ) {
        private val userId get() = serverRepository.currentUser?.id

        /** The person's music libraries (usually just "Music") */
        suspend fun libraries(): List<BaseItem> =
            api.userViewsApi
                .getUserViews(userId = userId)
                .content.items
                .filter { it.collectionType == CollectionType.MUSIC }
                .map { BaseItem(it, false) }

        private suspend fun items(request: GetItemsRequest): List<BaseItem> =
            api.itemsApi
                .getItems(request.copy(userId = userId))
                .content.items
                .map { BaseItem(it, false) }

        private val songFields = DetailItemFields + ItemFields.DATE_CREATED


        /** Albums, newest first, most played first, or by name. [ids] keeps their order. */
        suspend fun albums(
            sort: ItemSortBy = ItemSortBy.SORT_NAME,
            descending: Boolean = false,
            limit: Int? = null,
            start: Int? = null,
            genreId: UUID? = null,
            artistId: UUID? = null,
            ids: List<UUID>? = null,
        ): List<BaseItem> {
            val list =
                items(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                        recursive = true,
                        sortBy = listOf(sort, ItemSortBy.SORT_NAME),
                        sortOrder = listOf(if (descending) SortOrder.DESCENDING else SortOrder.ASCENDING),
                        limit = limit,
                        startIndex = start,
                        genreIds = genreId?.let { listOf(it) },
                        albumArtistIds = artistId?.let { listOf(it) },
                        ids = ids,
                        fields = listOf(ItemFields.DATE_CREATED, ItemFields.CHILD_COUNT, ItemFields.GENRES),
                    ),
                )
            return if (ids != null) list.sortedBy { ids.indexOf(it.id) } else list
        }

        /** Every album artist, by name (or by [ids], in that order) */
        suspend fun artists(
            limit: Int? = null,
            start: Int? = null,
            ids: List<UUID>? = null,
        ): List<BaseItem> {
            val list =
                items(
                    GetItemsRequest(
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        limit = limit,
                        startIndex = start,
                        ids = ids,
                    ),
                )
            return if (ids != null) list.sortedBy { ids.indexOf(it.id) } else list
        }

        suspend fun genres(): List<BaseItem> =
            api.genresApi
                .getGenres(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                ).content.items
                .map { BaseItem(it, false) }

        /** Songs on an album, in disc and track order */
        suspend fun albumSongs(albumId: UUID): List<BaseItem> =
            items(
                GetItemsRequest(
                    parentId = albumId,
                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
                    fields = songFields,
                ),
            )

        /** Songs in a playlist, in the playlist's order (order added, unless moved) */
        suspend fun playlistSongs(playlistId: UUID): List<BaseItem> =
            api.playlistsApi
                .getPlaylistItems(playlistId = playlistId, userId = userId, fields = SongListFields)
                .content.items
                .filter { it.type == BaseItemKind.AUDIO }
                .map { BaseItem(it, false) }

        /** Liked songs (Jellyfin favourites), newest like first isn't stored, so most recently added first */
        suspend fun likedSongs(): List<BaseItem> =
            items(
                GetItemsRequest(
                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                    recursive = true,
                    filters = listOf(ItemFilter.IS_FAVORITE),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = SongListFields,
                    limit = 2000,
                ),
            )

        /**
         * One page of every song on Benflix, A to Z (Browse by > Songs), plus how many songs there are
         * in all. The page asks for the first few hundred so the list shows straight away, then the rest.
         */
        suspend fun allSongsPage(
            start: Int,
            limit: Int,
        ): Pair<List<BaseItem>, Int> {
            val result =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.AUDIO),
                            recursive = true,
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            fields = SongListFields,
                            startIndex = start,
                            limit = limit,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            return result.items.map { BaseItem(it, false) } to result.totalRecordCount
        }

        suspend fun likedCount(): Int =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        recursive = true,
                        filters = listOf(ItemFilter.IS_FAVORITE),
                        limit = 0,
                        enableTotalRecordCount = true,
                    ),
                ).content.totalRecordCount

        /** The songs you play the most */
        suspend fun mostPlayedSongs(limit: Int): List<BaseItem> =
            items(
                GetItemsRequest(
                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                    recursive = true,
                    filters = listOf(ItemFilter.IS_PLAYED),
                    sortBy = listOf(ItemSortBy.PLAY_COUNT, ItemSortBy.DATE_PLAYED),
                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.DESCENDING),
                    fields = SongListFields,
                    limit = limit,
                ),
            )

        /** An artist's songs, most played first */
        suspend fun artistSongs(
            artistId: UUID,
            limit: Int,
        ): List<BaseItem> =
            items(
                GetItemsRequest(
                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                    recursive = true,
                    artistIds = listOf(artistId),
                    sortBy = listOf(ItemSortBy.PLAY_COUNT, ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.DESCENDING, SortOrder.ASCENDING),
                    fields = SongListFields,
                    limit = limit,
                ),
            )

        /** Songs by id, kept in the order given */
        suspend fun songs(ids: List<UUID>): List<BaseItem> {
            if (ids.isEmpty()) return emptyList()
            val found =
                ids.chunked(150).flatMap { chunk ->
                    items(GetItemsRequest(ids = chunk, fields = SongListFields))
                }
            val byId = found.associateBy { it.id }
            return ids.mapNotNull { byId[it] }
        }

        /** The person's music playlists (Liked songs isn't one; the Music page adds it first) */
        suspend fun playlists(): List<BaseItem> =
            items(
                GetItemsRequest(
                    includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                    recursive = true,
                    mediaTypes = listOf(MediaType.AUDIO),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = listOf(ItemFields.CHILD_COUNT),
                ),
            )

        /** Made for you: Tower's mixes for this profile, new every morning */
        suspend fun mixes(): List<MusicMix> =
            try {
                val j = helper.getJson("/api/music/mixes")
                val arr = j.optJSONArray("mixes")
                (0 until (arr?.length() ?: 0)).mapNotNull { i -> arr?.optJSONObject(i)?.let(::parseMix) }
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't get the made-for-you mixes from Tower")
                emptyList()
            }

        private fun parseMix(j: JSONObject): MusicMix? {
            val ids = j.optJSONArray("songs") ?: return null
            val covers = j.optJSONArray("covers")
            return MusicMix(
                key = j.optString("key"),
                name = j.optString("name"),
                line = j.optString("line"),
                songIds = (0 until ids.length()).mapNotNull { ids.optString(it).toUUIDOrNull() },
                coverAlbumIds = (0 until (covers?.length() ?: 0)).mapNotNull { covers?.optString(it)?.toUUIDOrNull() },
            ).takeIf { it.songIds.isNotEmpty() }
        }
    }
