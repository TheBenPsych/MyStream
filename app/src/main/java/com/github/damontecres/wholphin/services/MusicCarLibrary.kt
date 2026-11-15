package com.github.damontecres.wholphin.services

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix music in the car (Android Auto): the lists you can pick from on the car's screen.
 *
 * Shuffle, Recently played, Liked songs, Playlists and Albums. Picking a song plays it and the rest of its album or
 * list, through the same music player as the phone, so the lock screen, Up next and My watching all keep working.
 */
@Singleton
class MusicCarLibrary
    @Inject
    constructor(
        private val music: MusicLibrary,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
    ) {
        /** The top of the car's list */
        fun root(): MediaItem = folder(ROOT, "Benflix")

        /** What's inside [parentId] on the car's screen */
        suspend fun children(parentId: String): List<MediaItem> {
            if (serverRepository.currentUser == null) {
                return listOf(note("Open Benflix on your phone first", "Then pick who's listening"))
            }
            return when {
                parentId == ROOT ->
                    listOf(
                        playable(SHUFFLE, "Shuffle your favourites", "Most played and liked songs", null),
                        folder(RECENT, "Recently played"),
                        folder(LIKED, "Liked songs"),
                        folder(PLAYLISTS, "Playlists"),
                        folder(ALBUMS, "Albums"),
                    )

                parentId == RECENT -> music.albums(ItemSortBy.DATE_PLAYED, descending = true, limit = 40).map { albumFolder(it) }

                parentId == ALBUMS -> music.albums(limit = 500).map { albumFolder(it) }

                parentId == PLAYLISTS ->
                    music.playlists().map {
                        folder("$PLAYLIST_PREFIX${it.id}", it.data.name ?: "Playlist", null, art(it.id, it.data.imageTags?.get(ImageType.PRIMARY)))
                    }

                parentId == LIKED -> songItems("liked", "", music.likedSongs())

                parentId.startsWith(ALBUM_PREFIX) ->
                    parentId.removePrefix(ALBUM_PREFIX).toUUIDOrNull()?.let { songItems("album", it.toString(), music.albumSongs(it)) }.orEmpty()

                parentId.startsWith(PLAYLIST_PREFIX) ->
                    parentId
                        .removePrefix(PLAYLIST_PREFIX)
                        .toUUIDOrNull()
                        ?.let { songItems("playlist", it.toString(), music.playlistSongs(it)) }
                        .orEmpty()

                else -> emptyList()
            }
        }

        /** A list to play for something picked on the car's screen: the songs, where to start, and the list's id */
        data class ToPlay(
            val songs: List<BaseItem>,
            val start: Int,
            val sourceId: UUID?,
        )

        /** What to play when [mediaId] is picked, or null when it isn't a song or list from here */
        suspend fun toPlay(mediaId: String): ToPlay? {
            if (serverRepository.currentUser == null) return null
            if (mediaId == SHUFFLE) {
                val songs = (music.mostPlayedSongs(150) + music.likedSongs().take(150)).distinctBy { it.id }.shuffled()
                return ToPlay(songs, 0, null).takeIf { songs.isNotEmpty() }
            }
            if (!mediaId.startsWith(SONG_PREFIX)) return null
            // s:<kind>:<list id>:<index>:<song id>
            val parts = mediaId.removePrefix(SONG_PREFIX).split(":")
            if (parts.size < 4) return null
            val listId = parts[1].toUUIDOrNull()
            val songs =
                when (parts[0]) {
                    "liked" -> music.likedSongs()
                    "album" -> listId?.let { music.albumSongs(it) }
                    "playlist" -> listId?.let { music.playlistSongs(it) }
                    else -> null
                } ?: return null
            val songId = parts[3].toUUIDOrNull()
            val start =
                songs.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
                    ?: parts[2].toIntOrNull()?.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
                    ?: 0
            return ToPlay(songs, start, listId).takeIf { songs.isNotEmpty() }
        }

        private fun songItems(
            kind: String,
            listId: String,
            songs: List<BaseItem>,
        ): List<MediaItem> =
            songs.mapIndexed { i, song ->
                val artist = song.data.albumArtist ?: song.data.artists?.joinToString(", ")
                playable(
                    "$SONG_PREFIX$kind:$listId:$i:${song.id}",
                    song.data.name ?: "Song",
                    artist,
                    song.data.albumId?.let { art(it, song.data.albumPrimaryImageTag) },
                )
            }

        private fun albumFolder(album: BaseItem): MediaItem =
            folder(
                "$ALBUM_PREFIX${album.id}",
                album.data.name ?: "Album",
                album.data.albumArtist,
                art(album.id, album.data.imageTags?.get(ImageType.PRIMARY)),
            )

        private fun art(
            id: UUID,
            tag: String?,
        ): Uri? =
            try {
                imageUrlService.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, maxWidth = 400, tag = tag)?.let { Uri.parse(it) }
            } catch (_: Exception) {
                null
            }

        private fun folder(
            id: String,
            title: String,
            subtitle: String? = null,
            artwork: Uri? = null,
        ): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setArtworkUri(artwork)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build(),
                ).build()

        private fun playable(
            id: String,
            title: String,
            subtitle: String?,
            artwork: Uri?,
        ): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setArtist(subtitle)
                        .setSubtitle(subtitle)
                        .setArtworkUri(artwork)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build(),
                ).build()

        private fun note(
            title: String,
            subtitle: String,
        ): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(NOTE)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setIsBrowsable(false)
                        .setIsPlayable(false)
                        .build(),
                ).build()

        companion object {
            const val ROOT = "benflix-car-root"
            private const val SHUFFLE = "shuffle"
            private const val RECENT = "recent"
            private const val LIKED = "liked"
            private const val PLAYLISTS = "playlists"
            private const val ALBUMS = "albums"
            private const val NOTE = "note"
            private const val ALBUM_PREFIX = "album:"
            private const val PLAYLIST_PREFIX = "playlist:"
            private const val SONG_PREFIX = "s:"
        }
    }
