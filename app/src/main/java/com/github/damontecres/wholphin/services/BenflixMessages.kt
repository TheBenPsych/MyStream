package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
 */
@Singleton
class BenflixMessages
    @Inject
    constructor(
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        private val _waiting = MutableStateFlow<List<BenflixMessage>>(emptyList())
        val waiting: StateFlow<List<BenflixMessage>> = _waiting

        /** A message that just came in from the server */
        fun showLive(
            header: String?,
            text: String,
        ) {
            if (text.isBlank()) return
            _waiting.update {
                it +
                    BenflixMessage(
                        id = "live-" + System.nanoTime(),
                        header = header?.takeIf { h -> h.isNotBlank() } ?: "Message from Ben",
                        text = text.trim(),
                        live = true,
                    )
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

        /** Looks for messages saved for [userId], shows them and takes them off so they show once */
        suspend fun checkSaved(userId: UUID) =
            withContext(WholphinDispatchers.IO) {
                try {
                    val saved =
                        decode(
                            displayPreferencesService.getDisplayPreferences(userId).customPrefs[KEY],
                        )
                    if (saved.isEmpty()) return@withContext
                    displayPreferencesService.updateDisplayPreferences(userId) { remove(KEY) }
                    _waiting.update { list ->
                        list +
                            saved
                                .filter { s -> list.none { it.id == "saved-" + s.id } }
                                .map { BenflixMessage("saved-" + it.id, it.header, it.text, live = false) }
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
        }
    }
