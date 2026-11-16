package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.components.BenflixNotices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Music redesign 3: when a song you asked for comes in, a small corner card offers to play it next, once.
 * Looked at when Music or Now playing opens (at most every 5 minutes). Songs that were already in the first
 * time this runs are never offered, so nobody gets a pile of cards after the update.
 */
@Singleton
class MusicArrivals
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val music: BenflixMusic,
        private val library: MusicLibrary,
        private val musicService: MusicService,
        private val serverRepository: ServerRepository,
    ) {
        private var lastAt = 0L

        fun check() {
            if (KidsMode.isActive || GuestMode.isActive) return
            val now = System.currentTimeMillis()
            if (now - lastAt < CHECK_MS) return
            lastAt = now
            scope.launch {
                try {
                    look()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Music arrivals")
                }
            }
        }

        private suspend fun look() {
            val user = serverRepository.currentUser ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "offered-${user.id}"
            val firstTime = !prefs.contains(key)
            val offered = prefs.getStringSet(key, emptySet()).orEmpty()
            val mine =
                music.status().requests.filter {
                    it.ready && it.kind == "song" && (it.who.isBlank() || it.who.equals(user.name, ignoreCase = true))
                }
            val fresh = mine.filter { it.id !in offered }
            prefs.edit().putStringSet(key, (offered + fresh.map { it.id }).toList().takeLast(MAX_KEPT).toSet()).apply()
            if (firstTime) return
            val asked = fresh.firstOrNull() ?: return
            val song =
                library.search(asked.title, BaseItemKind.AUDIO, 8).firstOrNull { s ->
                    s.title.equals(asked.title, ignoreCase = true) &&
                        (
                            asked.artist.isBlank() ||
                                s.data.artists
                                    .orEmpty()
                                    .any { it.contains(asked.artist, true) || asked.artist.contains(it, true) } ||
                                s.data.albumArtist?.contains(asked.artist, true) == true
                        )
                } ?: return
            BenflixNotices.card(
                BenflixNotices.Card(
                    id = "music-arrived-${asked.id}",
                    title = "\"${asked.title}\" is here",
                    line = "Press OK to play it next",
                    image = asked.cover,
                    whilePlaying = true,
                    onOpen = {
                        scope.launch {
                            if (musicService.state.value.queueSize == 0) {
                                musicService.playList(listOf(song), 0, false, song.id, "Songs you asked for")
                            } else {
                                musicService.playNextSong(song)
                            }
                            BenflixNotices.pill("\"${asked.title}\" plays next")
                        }
                    },
                ),
            )
        }

        companion object {
            private const val PREFS = "benflix_music_arrivals"
            private const val CHECK_MS = 5 * 60_000L
            private const val MAX_KEPT = 300
        }
    }
