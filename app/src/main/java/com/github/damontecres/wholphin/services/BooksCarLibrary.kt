package com.github.damontecres.wholphin.services

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.github.damontecres.wholphin.data.ServerRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Idea Box 29 "Audiobooks in the car" (Ben, 2026-11-24): Glowfin audiobooks on the car's screen (Android Auto).
 * Keep listening first (books you're part way through), then every audiobook. Picking one plays it from your
 * place through the same audiobook player as the phone, so your place is saved and follows you to the TV.
 */
@Singleton
class BooksCarLibrary
    @Inject
    constructor(
        private val books: BenflixBooks,
        private val serverRepository: ServerRepository,
    ) {
        /** Like the music in the car: the car can ask before the phone app was opened, so sign back in first */
        private suspend fun signedIn(): Boolean {
            if (serverRepository.currentUser != null) return true
            return try {
                serverRepository.restoreLastSession()
                serverRepository.currentUser != null
            } catch (e: Exception) {
                Timber.w(e, "Couldn't sign in for audiobooks in the car")
                false
            }
        }

        fun root(): MediaItem = folder(ROOT, "Glowfin audiobooks")

        suspend fun children(parentId: String): List<MediaItem> {
            if (!signedIn()) return listOf(note("Open Glowfin on your phone first", "Then pick who's listening"))
            val all =
                books
                    .library()
                    .titles
                    .filter { it.kind == "audiobook" }
            return when (parentId) {
                ROOT -> {
                    val going = all.filter { it.status == "reading" }.sortedByDescending { it.progress }
                    if (all.isEmpty()) {
                        listOf(note("No audiobooks on Glowfin yet", "Ask for one in Books on your phone"))
                    } else {
                        going.map { book(it, "Keep listening") } + folder(ALL, "All audiobooks", "${all.size} books")
                    }
                }

                ALL -> {
                    all.sortedBy { it.name.lowercase() }.map { book(it, null) }
                }

                else -> {
                    emptyList()
                }
            }
        }

        /** The book's page (chapters, files, your place) for a picked [mediaId], or null when it isn't a book */
        suspend fun page(mediaId: String): BookPage? {
            if (!mediaId.startsWith(BOOK_PREFIX) || !signedIn()) return null
            return books.title(mediaId.removePrefix(BOOK_PREFIX))
        }

        private fun book(
            t: BookTile,
            group: String?,
        ): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(BOOK_PREFIX + t.id)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(t.name)
                        .setArtist(t.author)
                        .setSubtitle(
                            listOfNotNull(
                                t.author.takeIf { it.isNotBlank() },
                                if (t.progress > 0f && t.status != "done") "${(t.progress * 100).toInt()}%" else null,
                            ).joinToString(" · "),
                        ).setArtworkUri(t.cover?.let { Uri.parse(it) })
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                        .apply {
                            if (group != null) {
                                setExtras(Bundle().apply { putString(GROUP_TITLE_KEY, group) })
                            }
                        }.build(),
                ).build()

        private fun folder(
            id: String,
            title: String,
            subtitle: String? = null,
        ): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
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
            const val ROOT = "benflix-books-car-root"
            private const val ALL = "all-audiobooks"
            private const val NOTE = "note"
            private const val BOOK_PREFIX = "book:"

            /** Android Auto shows items with the same group title under one heading */
            private const val GROUP_TITLE_KEY = "android.media.mediaitem.CONTENT_STYLE_GROUP_TITLE"
        }
    }

/** The audiobook session's answers for the car: the lists above, and playing a picked book from your place */
@OptIn(UnstableApi::class)
class BooksCarCallback(
    private val car: BooksCarLibrary,
    private val startFromCar: suspend (BookPage) -> MediaSession.MediaItemsWithStartPosition,
) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(car.root(), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val all = car.children(parentId)
                val from = (page.coerceAtLeast(0).toLong() * pageSize.coerceAtLeast(1)).coerceAtMost(all.size.toLong()).toInt()
                val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
            } catch (e: Exception) {
                Timber.w(e, "Couldn't load audiobooks for the car: %s", parentId)
                future.set(LibraryResult.ofError(SessionError.ERROR_IO))
            }
        }
        return future
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val picked = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull()
        // Files that already know where to play from (the phone's own) go straight in
        if (picked == null || picked.localConfiguration != null) {
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            try {
                val page = car.page(picked.mediaId)
                if (page == null) {
                    future.setException(UnsupportedOperationException("Nothing to play for ${picked.mediaId}"))
                } else {
                    future.set(startFromCar(page))
                }
            } catch (e: Exception) {
                Timber.w(e, "Couldn't play an audiobook from the car")
                future.setException(e)
            }
        }
        return future
    }
}
