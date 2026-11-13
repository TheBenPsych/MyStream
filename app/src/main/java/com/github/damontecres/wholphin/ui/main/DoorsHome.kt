package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.BenflixIntroState
import com.github.damontecres.wholphin.ui.DOOR_NOTE_VOLUME
import com.github.damontecres.wholphin.ui.IntroPace
import com.github.damontecres.wholphin.ui.IntroSounds
import com.github.damontecres.wholphin.ui.components.HomeNewsDot
import com.github.damontecres.wholphin.ui.components.UpdateNotice
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.runIntroTimeline
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.waitForSettled
import com.github.damontecres.wholphin.util.StartupTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * One row of Home's news panel (Redesign 2 Home, approved hm-tall 2026-11-10): a small square poster, a small
 * coloured line saying what it is ([kind], like "New episode · S3 E12"), then the name on up to 2 lines.
 * [destination] is where pressing it goes (null = nowhere, just reading). News goes away once opened.
 */
data class HomeRow(
    val id: String,
    /** The small coloured line: "New episode · S3 E12", "Ready to watch", "On the way · 2h left", "Did you know" */
    val kind: String,
    /** The name (or the fact), up to 2 lines; a longer one slides by when the row is picked */
    val title: String,
    /** Green = ready to watch, amber = on the way, teal = Did you know, grey = everything else */
    val kindColor: Color,
    val image: String?,
    val destination: Destination?,
    /** A square picture (music) instead of a poster */
    val square: Boolean = false,
    val news: Boolean = false,
    /** The whole message, shown in a pop-up when the row has nowhere to go (a note from Ben, a built-in fact) */
    val fullText: String? = null,
    /** A note that talks about one part of Benflix gets a button in its pop-up to go there ("Open Live TV") */
    val goTo: Pair<String, Destination>? = null,
    /** When it happened, so news is newest first */
    val at: Long = 0L,
)

/** The colours of the small kind line on Home's rows */
object HomeKind {
    val Ready = BenflixColors.Ok
    val OnTheWay = BenflixColors.Movies
    val Fact = BenflixColors.LiveTv
    val Other = BenflixColors.Muted
}

/** Everything Home could show: your news (newest first), your requests on the way, and Did you know facts */
data class HomeParts(
    val news: List<HomeRow> = emptyList(),
    val onTheWay: List<HomeRow> = emptyList(),
    val facts: List<HomeRow> = emptyList(),
) {
    fun without(ids: Set<String>) =
        HomeParts(news.filter { it.id !in ids }, onTheWay.filter { it.id !in ids }, facts.filter { it.id !in ids })

    /** Everything See all lists (facts aren't news, so they stay on Home only) */
    val all: List<HomeRow> get() = news + onTheWay

    val isEmpty: Boolean get() = news.isEmpty() && onTheWay.isEmpty() && facts.isEmpty()
}

/** What the panel shows: [rows], and how many more are under See all */
data class HomeList(
    val rows: List<HomeRow>,
    val more: Int,
)

/** The tab is called Live TV now, so old notes that say "under Channels" read right */
fun liveTvWording(text: String): String {
    val where = Regex("\\s*\\b(under|in|on|to) (the )?Channels\\b( (tab|page|door|section))?", RegexOption.IGNORE_CASE)
    val without = text.replace(where, "")
    return if (Regex("live ?tv", RegexOption.IGNORE_CASE).containsMatchIn(without)) {
        without
    } else {
        text.replace(where) { " ${it.groupValues[1]} Live TV" }
    }
}

/** Which part of Benflix a note from Ben talks about, if any */
fun noteLink(
    text: String,
    kids: Boolean,
): Pair<String, Destination>? {
    val t = text.lowercase()

    fun has(pattern: String) = Regex(pattern).containsMatchIn(t)
    return when {
        has("live ?tv|\\bchannels?\\b") -> "Open Live TV" to Destination.Channels
        has("\\bmusic\\b|\\bsongs?\\b|\\balbums?\\b") && !kids -> "Open Music" to Destination.MusicDoor
        has("\\bbooks?\\b|\\bmanga\\b|\\baudiobooks?\\b") -> "Open Books" to Destination.BooksDoor
        has("\\bgames?\\b") -> "Open Games" to Destination.Games
        has("\\brequests?\\b") -> "Open Requests" to Destination.Requests
        has("\\bshows?\\b|\\bepisodes?\\b|\\bseries\\b") -> "Open Shows" to Destination.ShowsDoor
        has("\\bmovies?\\b|\\bfilms?\\b") -> "Open Movies" to Destination.MoviesDoor
        else -> null
    }
}

/**
 * Home's greeting, weather and list (approved f3-panel, 2026-11-10): up to 4 rows. News about new episodes of
 * shows you watch, a new season of a show you watched, new music from artists you follow, a new book volume,
 * a request that's ready, and an app update; then your own requests that are still on the way; then a
 * "Did you know" fact that changes every time Home opens. Nothing from Live TV or Games. A news row goes away
 * once opened, after 3 days, or when newer news of the same kind pushes it out. Each profile only sees its own.
 * The weather comes from Tower (a town typed once on the Server page), never from the TV's or phone's location.
 */
@HiltViewModel
class HomeDoorsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val requestReadyService: RequestReadyService,
        private val books: com.github.damontecres.wholphin.services.BenflixBooks,
        private val helper: com.github.damontecres.wholphin.services.GameLibraryClient,
        private val funFacts: com.github.damontecres.wholphin.services.BenflixFunFacts,
        private val displayPreferencesService: com.github.damontecres.wholphin.services.DisplayPreferencesService,
        private val notes: com.github.damontecres.wholphin.services.BenflixNotes,
        private val doorData: com.github.damontecres.wholphin.ui.door.DoorDataService,
        private val localGames: com.github.damontecres.wholphin.games.LocalGames,
        val navigationManager: NavigationManager,
        private val setupCheck: com.github.damontecres.wholphin.services.TowerSetupCheck,
    ) : ViewModel() {
        private val _rows = MutableStateFlow(HomeParts())
        val rows: StateFlow<HomeParts> = _rows

        private val _weather = MutableStateFlow<String?>(null)

        /** "Tuesday · 52°, cloudy · rain after 6 pm", or null when Tower has no town set */
        val weather: StateFlow<String?> = _weather

        /** The list has been looked up (or there is none to look up), so Home's layout won't shift */
        private val _loaded = MutableStateFlow(false)
        val loaded: StateFlow<Boolean> = _loaded

        val userName: String
            get() =
                when {
                    KidsMode.isActive -> KidsMode.displayName
                    GuestMode.isActive -> "Guest"
                    else -> serverRepository.currentUser?.name.orEmpty()
                }

        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun openedKey() = "opened-" + (serverRepository.currentUser?.id?.toString() ?: "none")

        // The fact titles shown last time, so the next Home open shows different ones
        private var lastFacts: List<String> = emptyList()

        /**
         * Rows this profile cleared (the X, Clear all, or opening news). Saved on the server with the profile, so
         * the TV, phone and web all hide the same rows. Facts are only hidden until Home opens again (they change anyway).
         */
        private var cleared: Set<String> = emptySet()
        private val clearedNow = mutableSetOf<String>()

        private fun shown(rows: List<HomeRow>) = rows.filter { it.id !in cleared && it.id !in clearedNow }

        private fun seenKey() = "seen-" + (serverRepository.currentUser?.id?.toString() ?: "none")

        /** The white dot on your picture: news on Home you haven't looked at yet */
        private fun updateDot() {
            val seen = prefs.getStringSet(seenKey(), emptySet()).orEmpty()
            HomeNewsDot.set(_rows.value.news.any { it.id !in seen })
        }

        /** You looked at Home's list (moved into it, scrolled past it, or opened See all): the dot goes */
        fun markSeen() {
            val ids = _rows.value.news.map { it.id }
            HomeNewsDot.set(false)
            if (ids.isEmpty()) return
            val seen = prefs.getStringSet(seenKey(), emptySet()).orEmpty()
            if (seen.containsAll(ids)) return
            prefs.edit().putStringSet(seenKey(), (seen + ids).toList().takeLast(MAX_SEEN).toSet()).apply()
        }

        private fun familyKey(kind: FamilyKind) = "${serverRepository.currentUser?.id}-${KidsMode.level}-${kind.name}"

        private val _family = MutableStateFlow(FamilyHomeData())

        /** What Kids, Easy mode and Guest show under their doors. Each profile has its own (never another's rows). */
        val family: StateFlow<FamilyHomeData> = _family

        /** The smaller Homes: Keep watching (Kids, Easy mode) or Popular here this week (Guest), and the Kids door lines */
        fun loadFamily(kind: FamilyKind) {
            val key = familyKey(kind)
            // Last time's rows for this same profile show at once; anyone else's are never shown
            _family.value = familyMemory[key] ?: FamilyHomeData(key = key)
            val userId = serverRepository.currentUser?.id ?: return
            viewModelScope.launchIO {
                try {
                    val cards =
                        if (kind == FamilyKind.GUEST) popularCards(userId) else keepWatchingCards(userId)
                    val lines = if (kind == FamilyKind.KIDS) kidsDoorLines(userId) else emptyMap()
                    // Only if this profile is still the one on screen
                    if (familyKey(kind) != key) return@launchIO
                    val data = FamilyHomeData(key = key, cards = cards, lines = lines, loaded = true)
                    familyMemory[key] = data
                    _family.value = data
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Home: family rows")
                    if (familyKey(kind) == key) _family.value = _family.value.copy(loaded = true)
                }
            }
        }

        private fun card(item: BaseItem): FamilyCard {
            val showId = if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) item.data.seriesId else null
            return FamilyCard(
                id = (showId ?: item.id).toString(),
                title = item.title ?: item.name.orEmpty(),
                image = poster(showId ?: item.id),
                destination = item.destination(),
            )
        }

        /** Movies and shows started but not finished, newest first (Kids only ever gets kids titles back) */
        private suspend fun keepWatchingCards(userId: java.util.UUID): List<FamilyCard> =
            coroutineScope {
                val movies = async { doorData.resumeMovies(userId) }
                val episodes = async { doorData.keepWatchingEpisodes(userId) }
                (movies.await() + episodes.await())
                    .sortedByDescending { it.data.userData?.lastPlayedDate }
                    .map { card(it) }
                    .distinctBy { it.id }
                    .take(FAMILY_ROW)
            }

        /** Popular here this week: what the household watched most (from Tower), or else what's trending on Benflix */
        private suspend fun popularCards(userId: java.util.UUID): List<FamilyCard> {
            val household =
                try {
                    val answer = helper.getJson("/api/popular").optJSONArray("items")
                    (0 until (answer?.length() ?: 0)).mapNotNull {
                        answer?.optJSONObject(it)?.optString("id")?.let { id -> runCatching { java.util.UUID.fromString(id) }.getOrNull() }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.i("Guest popular: %s", ex.message)
                    emptyList()
                }
            val picks = doorData.byIds(userId, household)
            val items =
                if (picks.size >= POPULAR_MIN) {
                    picks
                } else {
                    coroutineScope {
                        val movies =
                            async { doorData.top10(userId, org.jellyfin.sdk.model.api.BaseItemKind.MOVIE, emptySet(), anime = false) }
                        val shows =
                            async { doorData.top10(userId, org.jellyfin.sdk.model.api.BaseItemKind.SERIES, emptySet(), anime = false) }
                        val m = movies.await()
                        val s = shows.await()
                        (0 until maxOf(m.size, s.size)).flatMap { listOfNotNull(m.getOrNull(it), s.getOrNull(it)) }
                    }
                }
            return items.map { card(it) }.distinctBy { it.id }.take(FAMILY_ROW)
        }

        /** Kids doors name a couple of titles from this profile: "Bluey, Frozen and more" */
        private suspend fun kidsDoorLines(userId: java.util.UUID): Map<BenflixDoor, String> =
            coroutineScope {
                fun words(names: List<String>): String? =
                    names
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(
                            2,
                        ).takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?.let { "$it and more" }

                suspend fun newest(type: org.jellyfin.sdk.model.api.BaseItemKind): List<String> =
                    try {
                        api.itemsApi
                            .getItems(
                                org.jellyfin.sdk.model.api.request.GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(type),
                                    recursive = true,
                                    sortBy =
                                        listOf(
                                            org.jellyfin.sdk.model.api.ItemSortBy.DATE_PLAYED,
                                            org.jellyfin.sdk.model.api.ItemSortBy.DATE_CREATED,
                                        ),
                                    sortOrder = listOf(org.jellyfin.sdk.model.api.SortOrder.DESCENDING),
                                    enableImages = false,
                                    enableTotalRecordCount = false,
                                    limit = 4,
                                ),
                            ).content.items
                            .mapNotNull { it.name }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.d(ex, "Kids door line")
                        emptyList()
                    }
                val movies = async { newest(org.jellyfin.sdk.model.api.BaseItemKind.MOVIE) }
                val shows = async { newest(org.jellyfin.sdk.model.api.BaseItemKind.SERIES) }
                val games =
                    try {
                        localGames.downloaded().filter { it.ref.kids }.map { it.ref.name }
                    } catch (ex: Exception) {
                        emptyList()
                    }
                listOfNotNull(
                    words(movies.await())?.let { BenflixDoor.MOVIES to it },
                    words(shows.await())?.let { BenflixDoor.SHOWS to it },
                    (words(games) ?: "Games to play on the TV").let { BenflixDoor.GAMES to it },
                ).toMap()
            }

        /** This profile's id (Easy mode's door order is kept per profile) */
        val userId: java.util.UUID? get() = serverRepository.currentUser?.id

        /** [small]: Kids, Easy mode and Guest have no news list, so nothing is looked up for it */
        fun load(small: Boolean = false) {
            if (small || KidsMode.isActive || GuestMode.isActive) {
                HomeNewsDot.set(false)
                _rows.value = HomeParts()
                _weather.value = null
                _loaded.value = true
                return
            }
            // The last weather Tower gave shows straight away (if it's from the last 3 hours), so nothing pops in
            _weather.value = savedWeather()
            viewModelScope.launchIO {
                var news: List<HomeRow> = emptyList()
                var facts: List<HomeRow> = emptyList()
                try {
                    val opened = prefs.getStringSet(openedKey(), emptySet()).orEmpty()
                    clearedNow.clear()
                    // Benflix speed: everything is asked at the same time, so a slow Tower can't hold the doors up
                    coroutineScope {
                        val episodes = async { episodeNews() }
                        val music = async { musicNews() }
                        val bookRows = async { bookNews() }
                        val requests = async { requestNews() }
                        val fact = async { withTimeoutOrNull(FACT_WAIT_MS) { factRows() }.orEmpty() }
                        val weather = async { withTimeoutOrNull(WEATHER_WAIT_MS) { towerWeather() } }
                        val saved = async { withTimeoutOrNull(PREFS_WAIT_MS) { savedPrefs() }.orEmpty() }
                        val setup = async { withTimeoutOrNull(WEATHER_WAIT_MS) { setupNews() }.orEmpty() }
                        val custom = saved.await()
                        cleared = clearedFrom(custom)
                        news =
                            shown(
                                setup.await() + benNews(custom) + episodes.await() + music.await() + bookRows.await() +
                                    requests.await() + updateNews(),
                            ).filter { it.id !in opened }
                                .sortedByDescending { it.at }
                                .take(MAX_NEWS)
                        facts = fact.await()
                        weather.await()?.let { _weather.value = it.ifBlank { null } }
                    }
                    _rows.value = HomeParts(news, shown(onTheWayRows()), facts)
                    updateDot()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load Home's list")
                } finally {
                    _loaded.value = true
                }
                // Then, quietly: a fresh look at your requests, so On the way is up to date (the list only
                // changes if something did)
                try {
                    requestReadyService.refresh(minGapMs = REQUESTS_GAP_MS)
                    val again = HomeParts(shown(news), shown(onTheWayRows()), shown(facts))
                    if (again != _rows.value) _rows.value = again
                    if (_weather.value == null) towerWeather()?.let { _weather.value = it.ifBlank { null } }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Home: second look")
                }
            }
        }

        fun open(row: HomeRow) {
            if (row.news) {
                val opened = prefs.getStringSet(openedKey(), emptySet()).orEmpty()
                // Keep the list short: only the newest 60 opened rows are remembered
                prefs.edit().putStringSet(openedKey(), (opened + row.id).toList().takeLast(60).toSet()).apply()
                _rows.value = _rows.value.without(setOf(row.id))
                saveCleared(listOf(row.id))
            }
            row.destination?.let { navigationManager.navigateTo(it) }
        }

        /** The X on a row: it goes away, and stays away on every TV, phone and browser for this profile */
        fun clear(row: HomeRow) {
            _rows.value = _rows.value.without(setOf(row.id))
            clearedNow += row.id
            saveCleared(listOf(row.id))
        }

        /** Clear all: the whole list goes away (new news still shows when it comes) */
        fun clearAll() {
            val parts = _rows.value
            val ids = (parts.all + parts.facts).map { it.id }
            HomeNewsDot.set(false)
            _rows.value = HomeParts()
            clearedNow += ids
            saveCleared(ids)
        }

        private fun saveCleared(ids: List<String>) {
            // Facts change every time Home opens, so they aren't saved
            val keep = ids.filterNot { it.startsWith("fact-") }
            if (keep.isEmpty()) return
            cleared = cleared + keep
            val userId = serverRepository.currentUser?.id ?: return
            viewModelScope.launchIO {
                try {
                    displayPreferencesService.updateDisplayPreferences(userId) {
                        val old = get(CLEARED_KEY).orEmpty().split("\n").filter { it.isNotBlank() }
                        put(CLEARED_KEY, ((old - keep.toSet()) + keep).takeLast(MAX_CLEARED).joinToString("\n"))
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not save the cleared Home rows")
                }
            }
        }

        /** This profile's saved settings on the server (the note from Ben, "Ben fixed it", cleared rows) */
        private suspend fun savedPrefs(): Map<String, String?> {
            val userId = serverRepository.currentUser?.id ?: return emptyMap()
            return try {
                displayPreferencesService.getDisplayPreferences(userId).customPrefs
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Home: saved settings")
                emptyMap()
            }
        }

        private fun clearedFrom(custom: Map<String, String?>): Set<String> =
            custom[CLEARED_KEY]
                .orEmpty()
                .split("\n")
                .filter { it.isNotBlank() }
                .toSet()

        /** A note from Ben (Server page) and "Ben fixed it" after a problem report, newest first */
        private fun benNews(custom: Map<String, String?>): List<HomeRow> {
            val out = mutableListOf<HomeRow>()
            val userId = serverRepository.currentUser?.id
            notes
                .parse(custom[com.github.damontecres.wholphin.services.NoteKind.NOTE.key])
                // Closed on this TV or phone before the list could be cleared
                ?.takeUnless { userId != null && notes.isDismissed(userId, it) }
                ?.let { note ->
                    val text = liveTvWording(note.text)
                    out +=
                        HomeRow(
                            id = "note-${note.id}",
                            kind = "A note from Ben",
                            title = text.lineSequence().first(),
                            kindColor = HomeKind.Other,
                            image = null,
                            destination = null,
                            news = true,
                            fullText = text,
                            goTo = noteLink(text, KidsMode.isActive),
                            // A note from Ben always leads
                            at = Long.MAX_VALUE,
                        )
                }
            custom["benflix.fixed"]
                .orEmpty()
                .split("\n")
                .lastOrNull { it.isNotBlank() }
                ?.let { line ->
                    val at = line.substringBefore("|").toLongOrNull() ?: return@let
                    val title = line.substringAfter("|").substringBefore("|")
                    if (System.currentTimeMillis() - at > NEWS_DAYS * DAY_MS) return@let
                    out +=
                        HomeRow(
                            id = "fixed-$at",
                            kind = "Ben fixed it",
                            title = title,
                            kindColor = HomeKind.Ready,
                            image = null,
                            destination = null,
                            news = true,
                            fullText = "Ben fixed $title. Try it again!",
                            at = at,
                        )
                }
            return out
        }

        private fun poster(id: java.util.UUID?): String? =
            id?.let {
                runCatching {
                    api.imageApi.getItemImageUrl(itemId = it, imageType = ImageType.PRIMARY, maxHeight = POSTER_HEIGHT)
                }.getOrNull()
            }

        /** A new episode (or a new season) of a show you're watching, added in the last 3 days */
        private suspend fun episodeNews(): List<HomeRow> {
            val cutoff = LocalDateTime.now().minusDays(NEWS_DAYS)
            val nextUp =
                try {
                    api.tvShowsApi
                        .getNextUp(
                            GetNextUpRequest(
                                userId = serverRepository.currentUser?.id,
                                fields = listOf(ItemFields.DATE_CREATED),
                                limit = 30,
                                enableResumable = false,
                                enableUserData = true,
                            ),
                        ).content.items
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Home news: next up")
                    emptyList()
                }
            return nextUp
                .filter { it.dateCreated?.isAfter(cutoff) == true }
                .sortedByDescending { it.dateCreated }
                .distinctBy { it.seriesId }
                .take(MAX_NEWS)
                .map { ep ->
                    val season = ep.parentIndexNumber
                    val newSeason = ep.indexNumber == 1 && (season ?: 1) > 1
                    val show = ep.seriesName ?: ep.name.orEmpty()
                    HomeRow(
                        id = "ep-${ep.id}",
                        // The episode number sits in the small line, so the name gets the whole width
                        kind =
                            when {
                                newSeason -> "New season"
                                season != null && ep.indexNumber != null -> "New episode · S$season E${ep.indexNumber}"
                                else -> "New episode"
                            },
                        title = if (newSeason) "$show · Season $season" else show,
                        kindColor = HomeKind.Other,
                        image = poster(ep.seriesId ?: ep.id),
                        destination = BaseItem(ep).destination(),
                        news = true,
                        at =
                            ep.dateCreated
                                ?.atZone(java.time.ZoneId.systemDefault())
                                ?.toInstant()
                                ?.toEpochMilli() ?: 0L,
                    )
                }
        }

        /** Benflix music: a new album or single from an artist you follow (Follow on the artist page) */
        private suspend fun musicNews(): List<HomeRow> {
            val cutoff = LocalDateTime.now().minusDays(NEWS_DAYS)
            return try {
                val followed =
                    api.itemsApi
                        .getItems(
                            org.jellyfin.sdk.model.api.request.GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST),
                                recursive = true,
                                filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE),
                                limit = 200,
                            ),
                        ).content.items
                        .map { it.id }
                if (followed.isEmpty()) return emptyList()
                api.itemsApi
                    .getItems(
                        org.jellyfin.sdk.model.api.request.GetItemsRequest(
                            userId = serverRepository.currentUser?.id,
                            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM),
                            recursive = true,
                            albumArtistIds = followed,
                            sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(org.jellyfin.sdk.model.api.SortOrder.DESCENDING),
                            fields = listOf(ItemFields.DATE_CREATED),
                            limit = 10,
                        ),
                    ).content.items
                    .filter { it.dateCreated?.isAfter(cutoff) == true }
                    .distinctBy { it.albumArtist }
                    .take(2)
                    .map { album ->
                        HomeRow(
                            id = "music-${album.id}",
                            kind = "New music",
                            title = listOfNotNull(album.albumArtist, album.name).joinToString(" · "),
                            kindColor = HomeKind.Other,
                            image = poster(album.id),
                            destination = BaseItem(album).destination(),
                            square = true,
                            news = true,
                            at =
                                album.dateCreated
                                    ?.atZone(java.time.ZoneId.systemDefault())
                                    ?.toInstant()
                                    ?.toEpochMilli() ?: 0L,
                        )
                    }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Home news: music")
                emptyList()
            }
        }

        /** Books: a new volume of a manga you read, or a book you asked for is here (Tower works these out) */
        private suspend fun bookNews(): List<HomeRow> =
            try {
                withTimeoutOrNull(BOOK_NEWS_WAIT_MS) { books.news() }.orEmpty().take(2).map {
                    val (kind, title) = bookWords(it)
                    HomeRow(
                        id = "book-${it.id}",
                        kind = kind,
                        title = title,
                        kindColor = if (it.id.startsWith("bkr-")) HomeKind.Ready else HomeKind.Other,
                        image = books.url(it.cover),
                        destination = Destination.BookTitle(it.titleId),
                        news = true,
                        at = it.at,
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // Tower's helper isn't set up for books yet (or can't be reached): no book news
                Timber.d(ex, "Home news: books")
                emptyList()
            }

        /** Something you asked for in Requests is here */
        private suspend fun requestNews(): List<HomeRow> =
            requestReadyService.ready.value
                .mapNotNull { it.jellyfinId }
                .take(3)
                .mapIndexedNotNull { i, id ->
                    try {
                        val item = api.userLibraryApi.getItem(id).content
                        HomeRow(
                            id = "req-$id",
                            kind = "Ready to watch",
                            title = "${item.name.orEmpty()} · you asked",
                            kindColor = HomeKind.Ready,
                            image = poster(item.id),
                            destination = BaseItem(item).destination(),
                            news = true,
                            at =
                                item.dateCreated
                                    ?.atZone(java.time.ZoneId.systemDefault())
                                    ?.toInstant()
                                    ?.toEpochMilli()
                                    ?: (System.currentTimeMillis() - i),
                        )
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        null
                    }
                }

        /**
         * Redesign 2 (approved ss-p-setup): Ben's note when Tower's helper is older than this app needs.
         * Pressing it opens Server, where the yellow card has the command with Copy. Clearing it hides it until
         * the next time a new setup is needed.
         */
        private suspend fun setupNews(): List<HomeRow> {
            if (serverRepository.currentUserDto?.policy?.isAdministrator != true) return emptyList()
            val need = setupCheck.check() ?: return emptyList()
            return listOf(
                HomeRow(
                    id = "setup-${need.need}",
                    kind = "Tower needs the setup command",
                    title = "New things are waiting. Open Server to copy the command",
                    kindColor = BenflixColors.Warning,
                    at = System.currentTimeMillis(),
                    image = null,
                    destination = Destination.ServerStatus,
                    news = true,
                ),
            )
        }

        /**
         * A new Benflix version. The main profile sees it right away; everyone else once this TV or phone
         * has been a day behind (Kids and Guest never get news). After 3 days the update screen asks too.
         */
        private fun updateNews(): List<HomeRow> {
            val admin = serverRepository.currentUserDto?.policy?.isAdministrator == true
            val version = UpdateNotice.version.value ?: return emptyList()
            if (!admin && UpdateNotice.daysBehind.value < 1) return emptyList()
            return listOf(
                HomeRow(
                    id = "update-$version",
                    kind = "Benflix update",
                    title = "A new version is ready to install",
                    kindColor = HomeKind.Other,
                    image = null,
                    destination = Destination.UpdateApp,
                    news = true,
                    at = System.currentTimeMillis() - UpdateNotice.daysBehind.value * DAY_MS,
                ),
            )
        }

        /** Your own requests still on the way (pressing one opens Requests) */
        private fun onTheWayRows(): List<HomeRow> =
            requestReadyService.onTheWay.value.map {
                HomeRow(
                    id = "way-${it.requestId}",
                    kind = onTheWayKind(it.line),
                    title = it.title,
                    kindColor = HomeKind.OnTheWay,
                    image = it.posterUrl,
                    destination = Destination.Requests,
                )
            }

        /**
         * Two "Did you know" facts about movies or shows this person can watch (different every time Home opens).
         * Tower picks them; if it can't be reached, one of the built-in facts (not about one title) instead.
         */
        private suspend fun factRows(): List<HomeRow> {
            val fromTower =
                try {
                    val skip = lastFacts.joinToString(",")
                    helper
                        .getJson("/api/facts/home?n=2&max=$FACT_MAX_LENGTH&skip=$skip")
                        .optJSONArray("facts")
                        ?.let { list -> (0 until list.length()).mapNotNull { list.optJSONObject(it) } }
                        .orEmpty()
                        .mapNotNull { j ->
                            val id = j.optString("id").toUUIDOrNull() ?: return@mapNotNull null
                            val name = j.optString("name")
                            val show = j.optString("type") == "Series"
                            var fact = j.optString("fact").trim()
                            if (fact.isBlank()) return@mapNotNull null
                            // Say which title it's about when the fact doesn't
                            if (name.isNotBlank() && !fact.contains(name.substringBefore(":").trim(), ignoreCase = true)) {
                                fact = "$name: $fact"
                            }
                            HomeRow(
                                id = "fact-$id",
                                kind = "Did you know",
                                title = fact,
                                kindColor = HomeKind.Fact,
                                image = poster(id),
                                destination = Destination.MediaItem(id, if (show) BaseItemKind.SERIES else BaseItemKind.MOVIE),
                            )
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Home: Tower facts")
                    emptyList()
                }
            if (fromTower.isNotEmpty()) {
                lastFacts = fromTower.map { it.id.removePrefix("fact-") }
                return fromTower
            }
            val userId = serverRepository.currentUser?.id ?: return emptyList()
            return listOfNotNull(
                funFacts.pick(api, userId)?.let {
                    HomeRow(
                        id = "fact-built-in",
                        kind = "Did you know",
                        title = it,
                        kindColor = HomeKind.Fact,
                        image = null,
                        destination = null,
                        fullText = it,
                    )
                },
            )
        }

        /** Tower's weather line with today's name in front ("" = no town set), saved for next time */
        private suspend fun towerWeather(): String? =
            try {
                val j = helper.getJson("/api/home/weather")
                val line =
                    if (j.optBoolean("ready")) {
                        java.time.LocalDate
                            .now()
                            .dayOfWeek
                            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " · " +
                            j.optString("line")
                    } else {
                        ""
                    }
                prefs
                    .edit()
                    .putString(WEATHER_KEY, line)
                    .putLong(WEATHER_AT_KEY, System.currentTimeMillis())
                    .apply()
                line
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // An older Tower helper (no weather yet) or Tower can't be reached
                Timber.d(ex, "Home: weather")
                null
            }

        private fun savedWeather(): String? {
            val at = prefs.getLong(WEATHER_AT_KEY, 0L)
            if (System.currentTimeMillis() - at > WEATHER_KEEP_MS) return null
            val line = prefs.getString(WEATHER_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
            // The day name is put in front when it's saved, so a line from yesterday evening isn't shown
            val today =
                java.time.LocalDate
                    .now()
                    .dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            return line.takeIf { it.startsWith(today) }
        }

        companion object {
            /** The smaller Homes' rows, kept per profile while Benflix is open */
            private val familyMemory = java.util.concurrent.ConcurrentHashMap<String, FamilyHomeData>()
            private const val FAMILY_ROW = 12
            private const val POPULAR_MIN = 5
            private const val PREFS = "benflix_home_news"
            private const val WEATHER_KEY = "weather-line"
            private const val WEATHER_AT_KEY = "weather-at"
            private const val WEATHER_KEEP_MS = 3 * 3_600_000L
            private const val NEWS_DAYS = 3L
            private const val DAY_MS = 86_400_000L
            private const val PREFS_WAIT_MS = 2_000L

            /** Shared with Benflix on the web (server/web/js/start.js) */
            const val CLEARED_KEY = "benflix.cleared"
            private const val MAX_CLEARED = 80
            private const val BOOK_NEWS_WAIT_MS = 1_200L
            private const val FACT_WAIT_MS = 1_200L
            private const val WEATHER_WAIT_MS = 1_200L
            private const val REQUESTS_GAP_MS = 5 * 60_000L
            private const val FACT_MAX_LENGTH = 130
            private const val POSTER_HEIGHT = 240
            const val MAX_NEWS = 12
            private const val MAX_SEEN = 80
        }
    }

/**
 * Home's news panel (approved hm-tall): up to [slots] lines (6 on a TV, 5 on phones). Your news newest first, then
 * On the way, then one Did you know at the bottom. A quiet day fills up with On the way and Did you know (two facts
 * when there's very little), so the panel never looks empty. A busy day shows what fits and keeps the last line
 * for "4 more" and See all.
 */
fun arrangeHomeRows(
    parts: HomeParts,
    slots: Int,
): HomeList {
    val all = parts.all
    if (all.size > slots) return HomeList(all.take(slots - 1), all.size - (slots - 1))
    val out = all.toMutableList()
    if (out.size < slots) out += parts.facts.take(1)
    if (out.size < QUIET_ROWS && parts.facts.size > 1) out += parts.facts[1]
    return HomeList(out.take(slots), 0)
}

/** On a quiet day the panel is topped up with a second fact until it has this many rows */
private const val QUIET_ROWS = 4

/** "On the way, about 20 minutes left" becomes the small line "On the way · 20 min left" */
fun onTheWayKind(line: String): String {
    val l = line.lowercase()
    val short =
        when {
            "waiting for a yes" in l -> {
                "waiting for a yes"
            }

            "looking for a copy" in l -> {
                "searching"
            }

            "almost here" in l -> {
                "almost here"
            }

            "an hour left" in l -> {
                "1h left"
            }

            else -> {
                Regex("about (\\d+) (minutes|hours) left").find(l)?.let {
                    it.groupValues[1] + if (it.groupValues[2] == "hours") "h left" else " min left"
                } ?: Regex("(\\d+)% downloaded").find(l)?.let { "${it.groupValues[1]}%" }
            }
        }
    return if (short == null) "On the way" else "On the way · $short"
}

/** Books news: the small line and the name ("New volume", "One Piece · Vol. 109") */
fun bookWords(news: com.github.damontecres.wholphin.services.BookNews): Pair<String, String> {
    val name = news.name ?: news.text
    return when {
        news.id.startsWith("bkr-") -> {
            "Ready to read" to name
        }

        news.id.startsWith("bks-") -> {
            "On your phone" to name
        }

        else -> {
            val vol =
                news.line
                    ?.removePrefix("New: ")
                    ?.removeSuffix(" is here")
                    ?.takeIf { it.isNotBlank() && it != news.line }
            "New volume" to (if (vol != null) "$name · $vol" else name)
        }
    }
}

/** Home's news panel on a TV (and tablets, and phones turned sideways) */
const val MAX_HOME_ROWS = 6

/** Phones and the web show one line fewer */
const val MAX_PHONE_ROWS = 5

/** Lets the pop-up finish closing before the remote's focus moves back to the doors */
private const val REFOCUS_DELAY_MS = 150L

/** "Good morning", "Good afternoon" or "Good evening", or the holiday on the day itself */
fun greetingFor(
    name: String,
    now: LocalTime = LocalTime.now(),
): String {
    val holiday = holidayFor(java.time.LocalDate.now())
    if (holiday != null) return holiday.text + if (name.isNotBlank()) ", $name" else ""
    val part =
        when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    return if (name.isBlank()) part else "$part, $name"
}

/**
 * The redesigned Home. Everyone but Kids and Easy mode gets the approved Today board (f3-panel, 2026-11-10):
 * the greeting with Tower's weather line under it, six square doors 3 by 2, and a soft list panel as tall as
 * the doors with up to 4 rows (news, On the way, Did you know). Phones put the list under the doors.
 * Kids and Easy mode keep the big doors: TV (and phones turned sideways, and tablets) get tall columns, phones
 * a grid, with bigger words and no descriptions or list. Focus always starts on Movies.
 *
 * Right after someone is picked on Who's watching, the doors grow out of the logo's squares, one
 * after another, with soft mallet notes; presses wait until they've landed. The list fades in as the last lands.
 */
@Composable
fun DoorsHomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeDoorsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val kids by KidsMode.active.collectAsState()
    val guestOn by GuestMode.active.collectAsState()
    val easyPref = preferences.appPreferences.benflixPreferences.easyRead
    // Kids, Big kids, Guest and Easy mode get their own smaller Home (Redesign 2, section 11)
    val familyKind = remember(kids, guestOn, easyPref) { familyKindNow(easyPref) }
    val familyDoorSet = remember(familyKind) { familyKind?.let { familyDoors(it, context, viewModel.userId) } }
    val familyData by viewModel.family.collectAsState()
    LifecycleStartEffect(familyKind) {
        viewModel.load(small = familyKind != null)
        familyKind?.let { viewModel.loadFamily(it) }
        onStopOrDispose { }
    }
    // Benflix: the admin's alert (new problem report, disk nearly full) only shows while Home is on screen
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.github.damontecres.wholphin.ui.components.ServerAlertNotice
                .clear()
        }
    }
    // The first-time setup for a new profile
    WelcomeGate()
    LaunchedEffect(Unit) {
        StartupTimer.mark(StartupTimer.HOME_ROWS)
        StartupTimer.finish(context)
    }
    // Music is the fifth door on the grown-up Home; the smaller Homes pick their own doors
    val boardDoors = remember(kids) { BenflixDoor.forHome(kids) }
    val doors = familyDoorSet?.shown ?: boardDoors
    val lite =
        remember {
            com.github.damontecres.wholphin.services.LiteMode
                .isOn(context)
        }
    val liveRows by viewModel.rows.collectAsState()
    val liveWeather by viewModel.weather.collectAsState()
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    // Phones turned sideways and tablets use the TV layout
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600

    // The doors-grow moment, once, right after a profile is picked
    val grow = remember { BenflixIntroState.doorsPending }
    val progress = remember { BenflixDoor.entries.map { mutableFloatStateOf(if (grow) 0f else 1f) } }
    val doorRects = remember { mutableStateOf(arrayOfNulls<Rect>(BenflixDoor.entries.size)) }
    var animating by remember { mutableStateOf(grow) }
    // The doors' spots are only locked in once they've settled and the growing starts
    var started by remember { mutableStateOf(!grow) }
    // The list and weather as they were when the doors started growing (null = show the live ones)
    var frozenRows by remember { mutableStateOf<HomeParts?>(null) }
    var frozenWeather by remember { mutableStateOf<String?>(null) }
    val moviesFocus = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (grow) {
            BenflixIntroState.doorsPending = false
            // Get everything ready before anything moves, so the doors grow smoothly and the notes
            // land with them on every TV: the notes in memory, Home's news in (so the doors don't
            // shift), and the doors and logo measured and settled for a few frames.
            IntroSounds.awaitReady(context)
            withTimeoutOrNull(WAIT_FOR_NEWS_MS) { viewModel.loaded.first { it } }
            waitForSettled(WAIT_FOR_DOORS_MS) {
                val rects = doors.map { doorRects.value[it.ordinal] }
                val mark = BenflixIntroState.barMark
                if (rects.any { it == null } || mark == null) {
                    null
                } else {
                    // One box that changes whenever any door or the logo moves
                    Rect(
                        rects.sumOf { it!!.left.toDouble() }.toFloat() + mark.left,
                        rects.sumOf { it!!.top.toDouble() }.toFloat() + mark.top,
                        rects.sumOf { it!!.right.toDouble() }.toFloat() + mark.right,
                        rects.sumOf { it!!.bottom.toDouble() }.toFloat() + mark.bottom,
                    )
                }
            }
            // Wake the speaker just before the first note (while the pace is measured): some TVs put the sound
            // output to sleep a few seconds after the notes loaded, and the first notes then came out late
            IntroSounds.warmUp(context)
            val pace = IntroPace.measure(context, frames = 6)
            val f = pace.stretch
            frozenRows = viewModel.rows.value
            frozenWeather = viewModel.weather.value
            started = true
            // Each door starts growing a moment after the one before, with its own note on that frame
            val stagger = DOOR_STAGGER_MS * f
            val growMs = DOOR_GROW_MS * f
            // Ben: the violet Music door grows out of the small gap in the middle of the logo, one beat
            // after Games, with its own (slightly louder) note
            val musicStart = MUSIC_DOOR_START_MS * f
            val musicGrow = MUSIC_DOOR_GROW_MS * f
            // Books grows out of the same middle spot one beat after Music (on Music's beat on Kids, which has no Music)
            val booksStart = if (BenflixDoor.MUSIC in doors) musicStart + stagger else musicStart
            val starts =
                doors.map {
                    when (it) {
                        BenflixDoor.MUSIC -> musicStart
                        BenflixDoor.BOOKS -> booksStart
                        else -> it.ordinal * stagger
                    }
                }
            val lengths = doors.map { if (it == BenflixDoor.MUSIC || it == BenflixDoor.BOOKS) musicGrow else growMs }
            val total = doors.indices.maxOf { starts[it] + lengths[it] }
            var played = 0
            runIntroTimeline(total, pace) { t ->
                while (played < doors.size && t >= starts[played]) {
                    if (doors[played] == BenflixDoor.MUSIC) {
                        IntroSounds.play(context, IntroSounds.MUSIC_DOOR, MUSIC_NOTE_VOLUME)
                    } else if (doors[played] == BenflixDoor.BOOKS) {
                        IntroSounds.play(context, IntroSounds.BOOKS_DOOR, MUSIC_NOTE_VOLUME)
                    } else {
                        IntroSounds.DOORS.getOrNull(played)?.let { IntroSounds.play(context, it, DOOR_NOTE_VOLUME) }
                    }
                    played++
                }
                doors.forEachIndexed { i, door ->
                    val p = ((t - starts[i]) / lengths[i]).coerceIn(0f, 1f)
                    progress[door.ordinal].floatValue = FastOutSlowInEasing.transform(p)
                }
            }
            animating = false
            frozenRows = null
            frozenWeather = null
        }
        if (!focusedOnce || grow) {
            focusedOnce = true
            moviesFocus.tryRequestFocus()
        }
    }

    val onDoor: (BenflixDoor) -> Unit = { door ->
        DoorUse.opened(context, viewModel.userId, door)
        // Approved door colour fill: the door's colour grows to fill the screen, then the page opens
        DoorWipe.start(door.color, doorRects.value.getOrNull(door.ordinal)) {
            viewModel.navigationManager.navigateTo(
                when (door) {
                    BenflixDoor.MOVIES -> Destination.MoviesDoor
                    BenflixDoor.SHOWS -> Destination.ShowsDoor
                    BenflixDoor.LIVE_TV -> Destination.Channels
                    BenflixDoor.GAMES -> Destination.Games
                    BenflixDoor.MUSIC -> Destination.MusicDoor
                    BenflixDoor.BOOKS -> Destination.BooksDoor
                },
            )
        }
    }

    val doorModifier: (Int) -> Modifier = { i ->
        Modifier
            .onGloballyPositioned { coords ->
                if (!started || !animating) {
                    val now = coords.boundsInWindow()
                    if (doorRects.value[i] != now) {
                        val copy = doorRects.value.copyOf()
                        copy[i] = now
                        doorRects.value = copy
                    }
                }
            }.graphicsLayer {
                val p = progress[i].value
                if (p >= 1f) return@graphicsLayer
                if (lite) {
                    // Lite mode: a simple fade, nothing moves
                    alpha = p
                    return@graphicsLayer
                }
                val door = doorRects.value[i]
                val mark = BenflixIntroState.barMark
                if (door == null || mark == null || door.width <= 0f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                // The matching logo square: top left Movies, top right Shows, bottom left Live TV, bottom right
                // Games. Music starts as a tiny square in the gap at the very middle of the logo.
                val gap = mark.width * 0.1f
                // Music and Books both grow out of the tiny gap in the middle of the logo
                val centre = i == BenflixDoor.MUSIC.ordinal || i == BenflixDoor.BOOKS.ordinal
                val sq = if (centre) gap else (mark.width - gap) / 2f
                val sx =
                    when {
                        centre -> mark.left + (mark.width - gap) / 2f
                        i % 2 == 0 -> mark.left
                        else -> mark.left + sq + gap
                    }
                val sy =
                    when {
                        centre -> mark.top + (mark.height - gap) / 2f
                        i < 2 -> mark.top
                        else -> mark.top + sq + gap
                    }
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = sq / door.width + (1f - sq / door.width) * p
                scaleY = sq / door.height + (1f - sq / door.height) * p
                translationX = (sx - door.left) * (1f - p)
                translationY = (sy - door.top) * (1f - p)
            }
    }
    // News that arrives while the doors are growing waits until they've landed, so nothing shifts under
    // the animation (a jump mid-grow was the stutter Ben saw)
    val rows = frozenRows ?: liveRows
    val weather = if (frozenRows != null) frozenWeather else liveWeather
    // A row with nowhere to go (a note from Ben, "Ben fixed it", a built-in fact) opens a pop-up with all its words
    var reading by remember { mutableStateOf<HomeRow?>(null) }
    // The row that opened the pop-up is gone after "Got it, clear it", so the remote goes back to the doors
    var refocusDoors by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(refocusDoors) {
        if (refocusDoors > 0) {
            kotlinx.coroutines.delay(REFOCUS_DELAY_MS)
            moviesFocus.tryRequestFocus()
        }
    }
    reading?.let { row ->
        HomeMessageDialog(
            row = row,
            onClear = {
                viewModel.clear(row)
                reading = null
                refocusDoors++
            },
            onGo = { place ->
                viewModel.clear(row)
                reading = null
                viewModel.navigationManager.navigateTo(place)
            },
            onClose = { reading = null },
        )
    }
    val labelAlpha: (Int) -> Float = { i -> ((progress[i].value - 0.75f) / 0.25f).coerceIn(0f, 1f) }
    // The list fades in as the last door lands
    val lastDoor = doors.last().ordinal
    val listAlpha: () -> Float = { ((progress[lastDoor].value - 0.6f) / 0.4f).coerceIn(0f, 1f) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Presses wait until the doors have landed
                .onPreviewKeyEvent { animating }
                .pointerInput(animating) {
                    if (animating) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                        }
                    }
                },
    ) {
        if (familyKind == null || familyDoorSet == null) {
            // The approved Today board (f3-panel): six square doors 3 by 2, the list beside them (TV) or under them (phone)
            HomeBoard(
                greeting = greetingFor(viewModel.userName),
                weather = weather,
                doors = doors,
                rows = rows,
                wide = wide,
                labelAlpha = labelAlpha,
                listAlpha = listAlpha,
                doorModifier = { door ->
                    doorModifier(door.ordinal).then(if (door.ordinal == 0) Modifier.focusRequester(moviesFocus) else Modifier)
                },
                onDoor = onDoor,
                onRow = { row -> if (row.destination == null) reading = row else viewModel.open(row) },
                onClear = viewModel::clear,
                onClearAll = viewModel::clearAll,
                onSeen = viewModel::markSeen,
                lite = lite,
                focusDoors = { moviesFocus.tryRequestFocus() },
            )
        } else {
            // Kids (kd-family), Easy mode (ez-family) and Guest (gs-family): big doors and one row
            FamilyHome(
                kind = familyKind,
                greeting =
                    when (familyKind) {
                        FamilyKind.GUEST -> "Welcome"
                        FamilyKind.KIDS -> "Hi there!"
                        else -> greetingFor(viewModel.userName)
                    },
                doors = familyDoorSet,
                // Never show rows that belong to someone else, even for a moment
                data = if (familyData.key.endsWith("-" + familyKind.name)) familyData else FamilyHomeData(),
                wide = wide,
                labelAlpha = labelAlpha,
                listAlpha = listAlpha,
                doorModifier = { door ->
                    doorModifier(door.ordinal).then(if (door == BenflixDoor.MOVIES) Modifier.focusRequester(moviesFocus) else Modifier)
                },
                onDoor = onDoor,
                onCard = { card -> viewModel.navigationManager.navigateTo(card.destination) },
            )
        }
    }
}

private const val DOOR_STAGGER_MS = 70f
private const val DOOR_GROW_MS = 560f

/**
 * The Music door grows out of the middle of the logo right on the next beat after Games (same spacing as the
 * four, so its note keeps the rhythm; Ben heard a gap at 330 ms), and grows only a little slower than the others
 */
private const val MUSIC_DOOR_START_MS = 4 * DOOR_STAGGER_MS
private const val MUSIC_DOOR_GROW_MS = 640f

/** The Music door's note is a little louder than the other four */
private const val MUSIC_NOTE_VOLUME = 0.46f

/** The longest Home waits for its news before the doors grow (a slow server goes on anyway) */
private const val WAIT_FOR_NEWS_MS = 1500L

/** The longest Home waits for the doors to settle in place */
private const val WAIT_FOR_DOORS_MS = 1000L
