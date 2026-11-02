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

        /** A fact to show now, or null when there isn't one yet */
        suspend fun pick(
            api: ApiClient,
            userId: UUID,
        ): String? =
            withContext(WholphinDispatchers.IO) {
                val saved = savedFacts()
                val useSaved = saved.isNotEmpty() && Math.random() < SAVED_SHARE
                val fact =
                    if (useSaved) {
                        saved.random()
                    } else {
                        safe { libraryFact(api, userId) } ?: saved.randomOrNull()
                    }
                fact
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

        /** One fact made from the library itself */
        private suspend fun libraryFact(
            api: ApiClient,
            userId: UUID,
        ): String? {
            val kinds = listOf(::actorFact, ::anniversaryFact, ::taglineFact, ::longestFact).shuffled()
            for (kind in kinds) {
                safe { kind(api, userId) }?.let { return it }
            }
            return null
        }

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
            private const val MAX_SAVED = 60
            private const val SAVED_SHARE = 0.6
            private const val SAMPLE = 60
            private const val LOOKUPS = 3
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
                )
            private val BAD_STARTS = listOf("He", "She", "It", "They", "This", "These", "That", "His", "Her", "Their", "However", "Also")
        }
    }
