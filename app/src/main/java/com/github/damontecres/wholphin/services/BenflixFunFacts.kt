package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.core.content.edit
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix fun facts for the Who's watching screen. Two kinds, mixed:
 * - facts made from your own library ("Keanu Reeves is in 7 movies on Benflix")
 * - real behind-the-scenes facts from the "Production" part of the movie's Wikipedia article
 * Wikipedia facts are looked up in the background and kept on the device, so the screen never waits.
 * Only movies rated PG-13 or lower are used, since kids see this screen too.
 */
@Singleton
class BenflixFunFacts
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val http: OkHttpClient,
    ) {
        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * A fact to show now, or null when there isn't one yet. Benflix: never one this device has
         * already shown, until every fact has been seen; then the oldest ones come back first.
         */
        suspend fun pick(
            api: ApiClient,
            userId: UUID,
        ): String? =
            withContext(WholphinDispatchers.IO) {
                val seen = seenKeys().toHashSet()
                val fresh = { f: String -> keyOf(f) !in seen }
                val saved = savedFacts().filter { fresh(it) && looksComplete(it) }
                val builtIn = BenflixTrivia.facts.filter(fresh)
                val roll = Math.random()
                val fact =
                    when {
                        roll < SAVED_SHARE && saved.isNotEmpty() -> saved.random()
                        roll < SAVED_SHARE + BUILT_IN_SHARE && builtIn.isNotEmpty() -> builtIn.random()
                        else -> safe { libraryFact(api, userId, fresh) }
                    } ?: saved.randomOrNull() ?: builtIn.randomOrNull()
                if (fact != null) {
                    markSeen(fact)
                    fact
                } else {
                    // Everything has been shown: forget the older half and start over from those
                    forgetOldest()
                    BenflixTrivia.facts.filter { keyOf(it) !in seenKeys().toHashSet() }.randomOrNull()?.also {
                        markSeen(it)
                    }
                }
            }

        private fun keyOf(fact: String) =
            fact
                .trim()
                .lowercase()
                .hashCode()
                .toString()

        private fun seenKeys(): List<String> =
            prefs()
                .getString(SEEN_KEY, null)
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() }

        private fun markSeen(fact: String) {
            val all = (seenKeys() + keyOf(fact)).distinct().takeLast(MAX_SEEN)
            prefs().edit { putString(SEEN_KEY, all.joinToString(",")) }
        }

        private fun forgetOldest() {
            val all = seenKeys()
            prefs().edit { putString(SEEN_KEY, all.drop(all.size / 2).joinToString(",")) }
        }

        /** Looks up a few more Wikipedia facts for next time (call after showing one) */
        suspend fun learnMore(
            api: ApiClient,
            userId: UUID,
        ) {
            if (savedFacts().size >= MAX_SAVED) return
            withContext(WholphinDispatchers.IO) { safe { lookUp(api, userId) } }
        }

        private fun savedFacts(): List<String> =
            prefs()
                .getString(KEY, null)
                .orEmpty()
                .split(SEPARATOR)
                .filter { it.isNotBlank() }

        private fun save(new: List<String>) {
            val all = (savedFacts() + new).distinct().takeLast(MAX_SAVED)
            prefs().edit { putString(KEY, all.joinToString(SEPARATOR)) }
        }

        private suspend fun movies(
            api: ApiClient,
            userId: UUID,
            limit: Int,
            fields: List<ItemFields> = emptyList(),
            sortBy: ItemSortBy = ItemSortBy.RANDOM,
            sortOrder: SortOrder? = null,
        ): List<BaseItemDto> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        maxOfficialRating = "PG-13",
                        hasParentalRating = true,
                        fields = fields,
                        sortBy = listOf(sortBy),
                        sortOrder = sortOrder?.let { listOf(it) },
                        limit = limit,
                    ),
                ).content.items

        /** One fact made from the library itself, that this device hasn't shown yet */
        private suspend fun libraryFact(
            api: ApiClient,
            userId: UUID,
            fresh: (String) -> Boolean,
        ): String? {
            val kinds =
                listOf(
                    ::actorFact,
                    ::anniversaryFact,
                    ::fourKFact,
                    ::bingeFact,
                    ::episodesFact,
                    ::newestFact,
                    ::longestFact,
                    ::directorFact,
                    ::decadeFact,
                    ::oldestFact,
                    ::countFact,
                ).shuffled()
            for (kind in kinds) {
                safe { kind(api, userId) }?.takeIf(fresh)?.let { return it }
            }
            return null
        }

        private suspend fun directorFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val director =
                movies(api, userId, SAMPLE, fields = listOf(ItemFields.PEOPLE))
                    .flatMap { m -> m.people.orEmpty().filter { it.type == PersonKind.DIRECTOR } }
                    .groupBy { it.id }
                    .values
                    .shuffled()
                    .firstOrNull { it.size >= 2 }
                    ?.first() ?: return null
            val count = countMovies(api, userId, GetItemsRequest(personIds = listOf(director.id)))
            if (count < MIN_ACTOR_MOVIES || director.name.isNullOrBlank()) return null
            return "${director.name} directed $count movies on Benflix."
        }

        private suspend fun decadeFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val decade = listOf(1950, 1960, 1970, 1980, 1990, 2000, 2010).random()
            val count = countMovies(api, userId, GetItemsRequest(years = (decade until decade + 10).toList()))
            if (count < MIN_DECADE_MOVIES) return null
            return "Benflix has $count movies from the ${decade}s."
        }

        private suspend fun oldestFact(
            api: ApiClient,
            userId: UUID,
        ): String? =
            movies(api, userId, 1, sortBy = ItemSortBy.PREMIERE_DATE, sortOrder = SortOrder.ASCENDING)
                .firstOrNull()
                ?.let { m ->
                    val year = m.productionYear ?: m.premiereDate?.year ?: return null
                    "The oldest movie on Benflix is ${m.name}, from $year."
                }

        private suspend fun countFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val movies = countMovies(api, userId, GetItemsRequest())
            val shows =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                            limit = 0,
                            enableTotalRecordCount = true,
                        ),
                    ).content.totalRecordCount
            if (movies == 0) return null
            return "Benflix has $movies movies and $shows shows to pick from."
        }

        /** How many movies match [filter] (kid-safe ratings only, like every fun fact) */
        private suspend fun countMovies(
            api: ApiClient,
            userId: UUID,
            filter: GetItemsRequest,
        ): Int =
            api.itemsApi
                .getItems(
                    filter.copy(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        limit = 0,
                        enableTotalRecordCount = true,
                    ),
                ).content.totalRecordCount

        private suspend fun actorFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val sample = movies(api, userId, SAMPLE, fields = listOf(ItemFields.PEOPLE))
            val actor =
                sample
                    .flatMap { m ->
                        m.people
                            .orEmpty()
                            .filter { it.type == PersonKind.ACTOR }
                            .take(TOP_BILLED)
                    }.groupBy { it.id }
                    .maxByOrNull { it.value.size }
                    ?.value
                    ?.first() ?: return null
            val count =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            personIds = listOf(actor.id),
                            limit = 0,
                            enableTotalRecordCount = true,
                        ),
                    ).content.totalRecordCount
            if (count < MIN_ACTOR_MOVIES || actor.name.isNullOrBlank()) return null
            return "${actor.name} is in $count movies on Benflix."
        }

        private suspend fun anniversaryFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val today = LocalDate.now()
            return movies(api, userId, SAMPLE)
                .firstOrNull { it.premiereDate?.monthValue == today.monthValue && (it.premiereDate?.year ?: today.year) < today.year }
                ?.let {
                    val years = today.year - it.premiereDate!!.year
                    "${it.name} came out $years years ago this month."
                }
        }

        private suspend fun fourKFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val count = countMovies(api, userId, GetItemsRequest(is4k = true))
            if (count < MIN_DECADE_MOVIES) return null
            return "Benflix has $count movies in 4K."
        }

        /** How long it would take to watch every movie back to back */
        private suspend fun bingeFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val sample = movies(api, userId, SAMPLE).mapNotNull { it.runTimeTicks }
            if (sample.size < MIN_DECADE_MOVIES) return null
            val count = countMovies(api, userId, GetItemsRequest())
            val days = sample.average() * count / TICKS_PER_MINUTE / MINUTES_PER_DAY
            if (days < 1) return null
            return "Watching every movie on Benflix back to back, with no sleep, would take about ${days.toInt()} days."
        }

        private suspend fun episodesFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val episodes =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            limit = 0,
                            enableTotalRecordCount = true,
                        ),
                    ).content.totalRecordCount
            if (episodes < MIN_EPISODES) return null
            return "Benflix has $episodes TV episodes. At one a day, that's about ${episodes / 365} years of TV."
        }

        private suspend fun newestFact(
            api: ApiClient,
            userId: UUID,
        ): String? =
            movies(api, userId, 1, sortBy = ItemSortBy.DATE_CREATED, sortOrder = SortOrder.DESCENDING)
                .firstOrNull()
                ?.name
                ?.let { "The newest movie added to Benflix is $it." }

        private suspend fun longestFact(
            api: ApiClient,
            userId: UUID,
        ): String? =
            movies(api, userId, 1, sortBy = ItemSortBy.RUNTIME, sortOrder = SortOrder.DESCENDING)
                .firstOrNull()
                ?.let { m ->
                    val minutes = (m.runTimeTicks ?: return null) / TICKS_PER_MINUTE
                    "The longest movie on Benflix is ${m.name}, at ${minutes / 60} hr ${minutes % 60} min."
                }

        private val aboutCache = java.util.concurrent.ConcurrentHashMap<UUID, String>()

        /**
         * Benflix: a behind-the-scenes fact about this exact movie or show (for the screensaver, so the fact
         * matches the picture), or null if Wikipedia has none. Answers are remembered for this session.
         */
        suspend fun aboutItem(item: com.github.damontecres.wholphin.data.model.BaseItem): String? =
            withContext(WholphinDispatchers.IO) {
                aboutCache[item.id]?.let { return@withContext it.ifEmpty { null } }
                val name = item.name ?: return@withContext null
                val year = item.data.productionYear
                val fact =
                    safe {
                        when (item.type) {
                            BaseItemKind.MOVIE -> wikipediaFact(name, year)
                            BaseItemKind.SERIES -> seriesFact(name, year)
                            else -> null
                        }
                    }
                aboutCache[item.id] = fact.orEmpty()
                fact
            }

        private fun seriesFact(
            name: String,
            year: Int?,
        ): String? {
            val titles = listOfNotNull(year?.let { "$name ($it TV series)" }, "$name (TV series)", name)
            for (title in titles) {
                val text = wikipediaText(title) ?: continue
                if (!text.contains("series", ignoreCase = true)) continue
                return productionFact(name, text) ?: return null
            }
            return null
        }

        /** Looks up a few movies on Wikipedia and keeps the good production facts */
        private suspend fun lookUp(
            api: ApiClient,
            userId: UUID,
        ) {
            val found =
                movies(api, userId, LOOKUPS).mapNotNull { movie ->
                    val name = movie.name ?: return@mapNotNull null
                    safe { wikipediaFact(name, movie.productionYear) }
                }
            if (found.isNotEmpty()) save(found)
        }

        private fun wikipediaFact(
            name: String,
            year: Int?,
        ): String? {
            val titles = listOfNotNull(year?.let { "$name ($it film)" }, "$name (film)", name)
            for (title in titles) {
                val text = wikipediaText(title) ?: continue
                // Only use pages about the movie (not a book or a person with the same name)
                if (!text.contains("film", ignoreCase = true)) continue
                return productionFact(name, text) ?: return null
            }
            return null
        }

        private fun wikipediaText(title: String): String? {
            val url =
                "https://en.wikipedia.org/w/api.php"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("action", "query")
                    .addQueryParameter("prop", "extracts")
                    .addQueryParameter("explaintext", "1")
                    .addQueryParameter("redirects", "1")
                    .addQueryParameter("format", "json")
                    .addQueryParameter("titles", title)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", "Benflix/1.0 (home media app)")
                    .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val pages = JSONObject(response.body.string()).optJSONObject("query")?.optJSONObject("pages") ?: return null
                val key = pages.keys().asSequence().firstOrNull() ?: return null
                if (key == "-1") return null
                return pages.optJSONObject(key)?.optString("extract")?.takeIf { it.isNotBlank() }
            }
        }

        /** A stand-alone sentence from the Production part that reads well on its own */
        private fun productionFact(
            name: String,
            text: String,
        ): String? {
            val start = Regex("\n== *(Production|Development|Filming)[^=]*==\n").find(text) ?: return null
            val rest = text.substring(start.range.last + 1)
            val end = Regex("\n== [^=]+ ==\n").find(rest)?.range?.first ?: rest.length
            val section = rest.substring(0, end).replace(Regex("\n=+[^=]+=+\n"), "\n")
            val sentences =
                section
                    .split(SENTENCE_END)
                    .map { it.trim() }
                    .filter { it.length in MIN_SENTENCE..MAX_SENTENCE && looksComplete(it) }
                    .filter { s -> GOOD_WORDS.any { s.contains(it, ignoreCase = true) } }
                    .filter { s -> BORING_WORDS.none { s.contains(it, ignoreCase = true) } }
                    // Starts with a name, not "He", "It" or "This", so it makes sense on its own
                    .filter { s -> BAD_STARTS.none { s.startsWith("$it ") } }
            val best = sentences.firstOrNull { it.contains(name, ignoreCase = true) } ?: sentences.firstOrNull() ?: return null
            return if (best.contains(name, ignoreCase = true)) best else "$name: $best"
        }

        /**
         * False for a sentence that was cut in the wrong place, like one ending on the "B." of
         * "Courtney B. Vance", or one with an unclosed quote or bracket
         */
        private fun looksComplete(s: String): Boolean {
            if (!s.endsWith(".") && !s.endsWith("!") && !s.endsWith("?") && !s.endsWith(".”") && !s.endsWith(".\"")) return false
            if (Regex("(^|\\s)[A-Z]\\.$").containsMatchIn(s)) return false
            if (ABBREVIATIONS.any { s.endsWith(" $it.") }) return false
            if (s.count { it == '(' } != s.count { it == ')' }) return false
            if (s.count { it == '“' } != s.count { it == '”' } || s.count { it == '"' } % 2 != 0) return false
            return s.first().isUpperCase() || s.first().isDigit()
        }

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.v(ex, "Fun facts: skipped one")
                null
            }

        companion object {
            private const val PREFS = "benflix_fun_facts"
            private const val KEY = "facts2"
            private const val SEPARATOR = "\u001E"
            private const val MAX_SAVED = 400
            private const val SEEN_KEY = "seen"
            private const val MAX_SEEN = 1500
            private const val SAVED_SHARE = 0.35
            private const val BUILT_IN_SHARE = 0.4
            private const val MIN_DECADE_MOVIES = 5
            private const val SAMPLE = 60
            private const val LOOKUPS = 6
            private const val TOP_BILLED = 4
            private const val MIN_ACTOR_MOVIES = 3
            private const val MIN_SENTENCE = 50
            private const val MAX_SENTENCE = 190
            private const val TICKS_PER_MINUTE = 600_000_000L
            private const val MINUTES_PER_DAY = 1_440
            private const val MIN_EPISODES = 730
            private val GOOD_WORDS =
                listOf(
                    "budget",
                    "originally",
                    "improvis",
                    "real ",
                    "actual",
                    "practical",
                    "record",
                    "inspired",
                    "nickname",
                    "first time",
                    "props",
                    "turned down",
                    "accident",
                    "injur",
                    "secret",
                    "replaced",
                    "auditioned",
                    "ad-lib",
                    "by hand",
                    "for real",
                    "animatronic",
                    "puppet",
                    "trained",
                    "learned to",
                    "did his own",
                    "did her own",
                    "stunt",
                    "costume",
                )

            /** A sentence ends at . ! or ? and a space, but not after an initial ("Courtney B. Vance") or a title ("Dr.") */
            private val SENTENCE_END =
                Regex(
                    "(?<=[.!?])(?<!(^|\\s)[A-Z]\\.)" +
                        "(?<!\\b(Mr|Mrs|Ms|Dr|St|Jr|Sr|vs|Mt|No)\\.)\\s+(?=[A-Z0-9\"“])",
                )
            private val ABBREVIATIONS = listOf("Mr", "Mrs", "Ms", "Dr", "St", "Jr", "Sr", "vs", "Mt", "No")

            /** News-style lines (casting talks, release dates, money) that aren't fun to read */
            private val BORING_WORDS =
                listOf(
                    "was asked",
                    "announced",
                    "confirmed",
                    "in talks",
                    "negotiat",
                    "reported",
                    "principal photography",
                    "wrapped",
                    "release date",
                    "box office",
                    "joined the cast",
                    "was cast",
                    "were cast",
                    "signed on",
                    "set to",
                    "expected to",
                    "would return",
                    "screenplay",
                    "deal",
                )

            private val BAD_STARTS = listOf("He", "She", "It", "They", "This", "These", "That", "His", "Her", "Their", "However", "Also")
        }
    }
