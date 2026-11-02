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
                val saved = savedFacts().filter(fresh)
                val builtIn = (BenflixTrivia.facts + BenflixTrivia.quotes).filter(fresh)
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
                    (BenflixTrivia.facts + BenflixTrivia.quotes).filter { keyOf(it) !in seenKeys().toHashSet() }.randomOrNull()?.also {
                        markSeen(it)
                    }
                }
            }

        private fun keyOf(fact: String) = fact.trim().lowercase().hashCode().toString()

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
                    ::taglineFact,
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

        private suspend fun taglineFact(
            api: ApiClient,
            userId: UUID,
        ): String? =
            movies(api, userId, SAMPLE, fields = listOf(ItemFields.TAGLINES))
                .firstOrNull { !it.taglines.isNullOrEmpty() && it.taglines!!.first().length in 10..120 }
                ?.let { "The poster for ${it.name} said: “${it.taglines!!.first()}”" }

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
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map { it.trim() }
                    .filter { it.length in MIN_SENTENCE..MAX_SENTENCE }
                    .filter { s -> GOOD_WORDS.any { s.contains(it, ignoreCase = true) } }
                    // Starts with a name, not "He", "It" or "This", so it makes sense on its own
                    .filter { s -> BAD_STARTS.none { s.startsWith("$it ") } }
            val best = sentences.firstOrNull { it.contains(name, ignoreCase = true) } ?: sentences.firstOrNull() ?: return null
            return if (best.contains(name, ignoreCase = true)) best else "$name: $best"
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
            private const val KEY = "facts"
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
            private val GOOD_WORDS =
                listOf(
                    "filmed",
                    "shot ",
                    "cast",
                    "budget",
                    "originally",
                    "improvis",
                    "real ",
                    "actual",
                    "practical",
                    "weeks",
                    "days",
                    "record",
                    "inspired",
                    "nickname",
                    "first time",
                    "voice",
                    "sound",
                    "props",
                    "costume",
                    "location",
                )
            private val BAD_STARTS = listOf("He", "She", "It", "They", "This", "These", "That", "His", "Her", "Their", "However", "Also")
        }
    }
