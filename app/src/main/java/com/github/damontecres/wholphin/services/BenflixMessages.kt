package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A pop-up message from Ben */
data class BenflixMessage(
    val id: String,
    val header: String,
    val text: String,
    /** True when it arrived live (closes by itself after a while), false when it was saved for later */
    val live: Boolean,
)

/**
 * Benflix pop-up messages from Ben.
 * - Live: the server's "display message" command now shows a proper Benflix pop-up instead of a tiny toast.
 * - Saved for later: when Ben sends a message to someone who isn't on Benflix right now, it's saved in
 *   their Jellyfin display settings (only an admin can do that) and pops up the next time they open Benflix.
 *   Once it's shown it's taken off, so it shows once.
 * Ben's pop-ups to picked people are always saved too, in case the screen that looked open didn't really show it.
 * When the live one does show, the saved copy (same text) is taken off, so it never shows twice.
 */
@Singleton
class BenflixMessages
    @Inject
    constructor(
        private val displayPreferencesService: DisplayPreferencesService,
        private val serverRepository: ServerRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)

        /** Texts shown live lately (text to when), so a saved copy of the same message doesn't show again */
        private val shownLive = java.util.concurrent.ConcurrentHashMap<String, Long>()

        /** Texts this device sent (text to when), so the sender's own screen doesn't grab a message meant for their TV */
        private val sentHere = java.util.concurrent.ConcurrentHashMap<String, Long>()

        private val _liveBlocked = MutableStateFlow(false)

        /**
         * True when this device can't hold a live connection to the server (usually the reverse proxy in front of
         * Jellyfin isn't letting "websocket" connections through). Live pop-ups and the phone remote can't arrive
         * then, so messages are saved instead and picked up by the regular check.
         */
        val liveBlocked: StateFlow<Boolean> = _liveBlocked

        fun setLiveBlocked(blocked: Boolean) {
            _liveBlocked.value = blocked
        }

        /** Remember that this device is sending [text], so it doesn't pop up here too */
        fun rememberSent(text: String) {
            sentHere[text.trim()] = System.currentTimeMillis()
        }

        private fun sentByMe(text: String) =
            sentHere[text]?.let { System.currentTimeMillis() - it < SHOWN_MEMORY_MS } == true

        private val _waiting = MutableStateFlow<List<BenflixMessage>>(emptyList())
        val waiting: StateFlow<List<BenflixMessage>> = _waiting

        /** A message that just came in from the server */
        fun showLive(
            header: String?,
            text: String,
        ) {
            if (text.isBlank()) return
            val clean = text.trim()
            // The sender's own screen: it was meant for someone else (or for their TV)
            if (sentByMe(clean)) return
            val now = System.currentTimeMillis()
            shownLive.filterValues { now - it > SHOWN_MEMORY_MS }.keys.forEach { shownLive.remove(it) }
            shownLive[clean] = now
            _waiting.update {
                // The same message may already be up (from the saved copy); don't show it twice
                if (it.any { m -> m.text == clean }) {
                    it
                } else {
                    it +
                        BenflixMessage(
                            id = "live-" + System.nanoTime(),
                            header = header?.takeIf { h -> h.isNotBlank() } ?: "Message from Ben",
                            text = clean,
                            live = true,
                        )
                }
            }
            // It showed, so take the saved copy off (if Ben's app saved one)
            val userId = serverRepository.currentUser?.id ?: return
            scope.launch { removeSaved(userId, clean) }
        }

        private suspend fun removeSaved(
            userId: UUID,
            text: String,
        ) {
            try {
                val saved = decode(displayPreferencesService.getDisplayPreferences(userId).customPrefs[KEY])
                if (saved.none { it.text == text }) return
                displayPreferencesService.updateDisplayPreferences(userId) {
                    val left = decode(get(KEY)).filterNot { it.text == text }
                    if (left.isEmpty()) remove(KEY) else put(KEY, encode(left))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not take off the saved copy of a message")
            }
        }

        fun done(message: BenflixMessage) = _waiting.update { list -> list.filter { it.id != message.id } }

        /** Saves a message for [userId] to see the next time they open Benflix */
        suspend fun saveFor(
            userId: UUID,
            header: String,
            text: String,
        ) = withContext(WholphinDispatchers.IO) {
            displayPreferencesService.updateDisplayPreferences(userId) {
                val saved = decode(get(KEY)) + Saved(System.currentTimeMillis().toString(), header, text.trim())
                put(KEY, encode(saved.takeLast(MAX_SAVED)))
            }
        }

        /**
         * Looks for messages saved for [userId], shows them and takes them off so they show once.
         * Messages this device sent itself are left for the person's other screens (like their TV).
         * One sent in the last few minutes counts as live: it shows even during playback and closes by itself.
         */
        suspend fun checkSaved(userId: UUID) =
            withContext(WholphinDispatchers.IO) {
                try {
                    val saved =
                        decode(
                            displayPreferencesService.getDisplayPreferences(userId).customPrefs[KEY],
                        )
                    val mine = saved.filter { sentByMe(it.text) }
                    val toShow = saved - mine.toSet()
                    if (toShow.isEmpty()) return@withContext
                    displayPreferencesService.updateDisplayPreferences(userId) {
                        // Read again, so a message saved a moment ago isn't lost
                        val left = decode(get(KEY)).filter { s -> toShow.none { it.id == s.id && it.text == s.text } }
                        if (left.isEmpty()) remove(KEY) else put(KEY, encode(left))
                    }
                    val now = System.currentTimeMillis()
                    _waiting.update { list ->
                        list +
                            toShow
                                .filter { s -> list.none { it.id == "saved-" + s.id || it.text == s.text } }
                                // Already popped up live on this screen
                                .filter { s -> shownLive[s.text]?.let { now - it < SHOWN_MEMORY_MS } != true }
                                .map {
                                    val fresh = it.id.toLongOrNull()?.let { sent -> now - sent in 0..FRESH_MS } == true
                                    BenflixMessage("saved-" + it.id, it.header, it.text, live = fresh)
                                }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not check for saved messages")
                }
            }

        private data class Saved(
            val id: String,
            val header: String,
            val text: String,
        )

        // One message per record, fields split by unit separators (they never appear in typed text)
        private fun encode(list: List<Saved>) = list.joinToString(RECORD) { it.id + FIELD + it.header + FIELD + it.text }

        private fun decode(value: String?): List<Saved> =
            value
                ?.split(RECORD)
                ?.mapNotNull { record ->
                    val parts = record.split(FIELD)
                    if (parts.size == 3 && parts[2].isNotBlank()) Saved(parts[0], parts[1], parts[2]) else null
                }.orEmpty()

        companion object {
            const val KEY = "benflix.messages"
            private const val RECORD = "\u001E"
            private const val FIELD = "\u001F"
            private const val MAX_SAVED = 5

            /** How long a live message is remembered, so its saved copy doesn't pop up again */
            private const val SHOWN_MEMORY_MS = 7 * 24 * 60 * 60_000L

            /** A saved message this new still counts as "right now", so it shows even while watching */
            private const val FRESH_MS = 10 * 60_000L
        }
    }
