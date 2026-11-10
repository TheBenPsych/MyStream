package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelReminder
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.BenflixNotices
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** A big live event found in the Live TV guide */
data class BigEvent(
    val title: String,
    val channelKey: String,
    val channelName: String,
    val startMs: Long,
) {
    val id: String get() = "$startMs|$channelKey"
}

/**
 * Benflix "Big event alerts": spots big live events on the channels (the Super Bowl, the Oscars,
 * New Year's countdown, election night...) in the next few days and offers a one-tap reminder in
 * the usual corner note. Each event is offered once; No thanks hides it for good.
 */
@HiltViewModel
class BigEventViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_big_events", Context.MODE_PRIVATE)
        private val _offer = MutableStateFlow<BigEvent?>(null)
        val offer: StateFlow<BigEvent?> = _offer
        private var found: List<BigEvent> = emptyList()

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                // Wait until the app has settled (and someone is signed in)
                delay(START_DELAY_MS)
                while (true) {
                    try {
                        if (!KidsMode.isActive && serverRepository.currentUser != null) look()
                        pickOffer()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Big event check failed")
                    }
                    delay(CHECK_EVERY_MS)
                }
            }
        }

        private suspend fun look() {
            val now = System.currentTimeMillis()
            val last = prefs.getLong(LAST_LOOK, 0L)
            if (now - last < LOOK_EVERY_MS && found.isNotEmpty()) return
            val zone = ZoneId.systemDefault()
            val events = mutableListOf<BigEvent>()
            EVENTS.forEach { term ->
                val hits =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                searchTerm = term,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.LIVE_TV_PROGRAM),
                                fields = listOf(ItemFields.CHANNEL_INFO),
                                enableImages = false,
                                limit = 20,
                                enableTotalRecordCount = false,
                            ),
                        ).content.items
                hits.forEach { p ->
                    val start =
                        p.startDate
                            ?.atZone(zone)
                            ?.toInstant()
                            ?.toEpochMilli() ?: return@forEach
                    val channel = p.channelName ?: return@forEach
                    val name = p.name ?: return@forEach
                    // Repeats and highlights shows aren't the event
                    if (SKIP_WORDS.containsMatchIn(name)) return@forEach
                    if (start > now && start - now < AHEAD_MS) {
                        events += BigEvent(name, "live:" + channel.lowercase(), channel, start)
                    }
                }
            }
            // One channel per event (the first found)
            found = events.distinctBy { it.title.lowercase() + "|" + it.startMs / HOUR_MS }.sortedBy { it.startMs }
            prefs.edit().putLong(LAST_LOOK, now).apply()
        }

        private fun pickOffer() {
            val now = System.currentTimeMillis()
            val done = prefs.getStringSet(DONE, emptySet()).orEmpty()
            _offer.value =
                found.firstOrNull { e ->
                    e.id !in done &&
                        e.startMs - now in MIN_BEFORE_MS..OFFER_FROM_MS &&
                        !ChannelPrefs.hasReminder(e.channelKey, e.startMs)
                }
        }

        fun answer(remind: Boolean) {
            val event = _offer.value ?: return
            if (remind) ChannelPrefs.toggleReminder(ChannelReminder(event.channelKey, event.startMs, event.title))
            val done = prefs.getStringSet(DONE, emptySet()).orEmpty() + event.id
            prefs.edit().putStringSet(DONE, done).apply()
            pickOffer()
        }

        companion object {
            private const val LAST_LOOK = "last_look"
            private const val DONE = "done"
            private const val HOUR_MS = 3_600_000L
            private const val START_DELAY_MS = 60_000L
            private const val CHECK_EVERY_MS = 30 * 60_000L
            private const val LOOK_EVERY_MS = 12 * HOUR_MS
            private const val AHEAD_MS = 7 * 24 * HOUR_MS

            /** Offered from 4 days before, until half an hour before */
            private const val OFFER_FROM_MS = 4 * 24 * HOUR_MS
            private const val MIN_BEFORE_MS = 30 * 60_000L

            private val EVENTS =
                listOf(
                    "Super Bowl",
                    "Oscars",
                    "Academy Awards",
                    "Grammy",
                    "Emmy Awards",
                    "Golden Globe",
                    "New Year's Eve",
                    "New Year's Rockin",
                    "Election Night",
                    "Election Results",
                    "State of the Union",
                    "Inauguration",
                    "Thanksgiving Day Parade",
                    "World Series",
                    "NBA Finals",
                    "Stanley Cup Final",
                    "World Cup Final",
                    "Olympics Opening Ceremony",
                    "Kentucky Derby",
                    "Macy's 4th of July",
                )

            private val SKIP_WORDS =
                Regex("\\b(highlights|recap|classic|replay|rewind|encore|best of|preview|countdown to kickoff)\\b", RegexOption.IGNORE_CASE)
        }
    }

/**
 * The corner card for [BigEventViewModel] (Live TV only): "Big event coming up: Super Bowl", Sunday at 6:30 PM
 * on CBS, OK to get a reminder. It shows for 7 seconds and never takes the remote's focus; letting it pass
 * counts as No thanks.
 */
@Composable
fun BigEventOffer(
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    viewModel: BigEventViewModel = hiltViewModel(),
) {
    val offer by viewModel.offer.collectAsState()
    val reminders by ChannelPrefs.reminders.collectAsState()
    // A reminder that's due takes the corner first
    val now = System.currentTimeMillis()
    val busy = reminders.any { it.startMs - now <= 60_000L && now - it.startMs < 120_000L }
    val event = offer?.takeUnless { busy }
    val id = event?.let { "event-${it.id}" }
    DisposableEffect(id) {
        if (event != null && id != null) {
            BenflixNotices.card(
                BenflixNotices.Card(
                    id = id,
                    title = "Big event coming up: ${event.title}",
                    line = "${whenWords(event.startMs)} on ${event.channelName} · OK for a reminder",
                    onOpen = { viewModel.answer(true) },
                    onTimeout = { viewModel.answer(false) },
                ),
            )
        }
        // The card belongs to Live TV: leaving the page takes it away (it's offered again next time)
        onDispose { if (id != null) BenflixNotices.drop(id) }
    }
}

private fun whenWords(startMs: Long): String {
    val time =
        java.time.Instant
            .ofEpochMilli(startMs)
            .atZone(ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    val day =
        when (time.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> time.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        }
    return "$day at " + time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}
