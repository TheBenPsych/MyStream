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
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix fun facts for the Who's watching screen and the screensaver. Two kinds, mixed:
 * - the built-in list of fun movie and TV trivia in [BenflixTrivia]
 * - real behind-the-scenes facts from the "Production" part of Wikipedia articles about movies on the server
 * Never facts about the server itself (counts, ages, newest/oldest), since Ben found those boring.
 * Wikipedia facts are looked up in the background and kept on the device, so the screen never waits.
 * Only movies rated PG-13 or lower are used, since kids see this screen too.
 */
@Singleton
class BenflixFunFacts
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val helper: GameLibraryClient,
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
                    if (roll < SAVED_SHARE && saved.isNotEmpty()) {
                        saved.random()
                    } else {
                        builtIn.randomOrNull() ?: saved.randomOrNull()
                    }
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
        ): List<BaseItemDto> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        maxOfficialRating = "PG-13",
                        hasParentalRating = true,
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = limit,
                    ),
                ).content.items

        private val titleCache = java.util.concurrent.ConcurrentHashMap<UUID, List<String>>()

        /**
         * Benflix: every "Did you know" fact about this movie or show (an episode or season uses its show's),
         * for the small cycling spot on its page and the screensaver. Hand-checked facts come first, then
         * ones Tower found on Wikipedia, then built-in trivia that names the title. Empty when there are none.
         * When Tower can't be reached, falls back to a Wikipedia fact looked up on this device.
         */
        suspend fun forItem(item: com.github.damontecres.wholphin.data.model.BaseItem): List<String> =
            withContext(WholphinDispatchers.IO) {
                val showId = if (item.type == BaseItemKind.EPISODE || item.type == BaseItemKind.SEASON) item.data.seriesId else null
                val key = showId ?: item.id
                titleCache[key]?.let { return@withContext it }
                val name = if (showId != null) item.data.seriesName else item.name
                val fromTower =
                    safe {
                        val json = helper.getJson("/api/facts/${key.toString().replace("-", "")}")
                        val list = json.optJSONArray("facts")
                        (0 until (list?.length() ?: 0)).mapNotNull { i ->
                            list!!.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }
                        } to json.optBoolean("ready", true)
                    }
                val builtIn = name?.let { builtInAbout(it) }.orEmpty()
                val found =
                    if (fromTower != null) {
                        (fromTower.first + builtIn).distinctBy { keyOf(it) }
                    } else {
                        val wiki = if (showId == null) aboutItem(item) else null
                        listOfNotNull(wiki) + builtIn
                    }
                // Remember it for this session, unless Tower is still looking it up
                if (fromTower == null || fromTower.second || found.isNotEmpty()) titleCache[key] = found
                found
            }

        /** Built-in trivia lines that name this title (whole words, so "Up" doesn't match "set up") */
        private fun builtInAbout(name: String): List<String> {
            val clean = name.substringBefore(" (").trim()
            if (clean.length < 4) return emptyList()
            val pattern = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(clean) + "($|[^\\p{L}\\p{N}])")
            return BenflixTrivia.facts.filter { pattern.containsMatchIn(it) }
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
            private const val LOOKUPS = 6
            private const val MIN_SENTENCE = 50
            private const val MAX_SENTENCE = 190
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
                    "years ago",
                    "anniversary",
                    "years old",
                )

            private val BAD_STARTS = listOf("He", "She", "It", "They", "This", "These", "That", "His", "Her", "Their", "However", "Also")
        }
    }
