package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One title's scores from OMDb. Null = OMDb has no score of that kind. */
@Serializable
data class OmdbScore(
    /** IMDb rating, 0 to 10 */
    val imdb: Double? = null,
    /** Rotten Tomatoes critics score (Tomatometer), 0 to 100 */
    val rtCritics: Int? = null,
)

/**
 * Benflix: IMDb and Rotten Tomatoes scores for anything on the Requests pages, from OMDb (omdbapi.com), using
 * a free key Ben typed once in Settings. The key is saved to every account on the server, so every
 * device and profile gets scores without setting anything up.
 *
 * Scores are saved on the device for a week, so each title costs one lookup a week at most (the free key
 * allows 1,000 a day).
 */
@Singleton
class OmdbScores
    @Inject
    constructor(
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val diskCache: DiskCache,
        private val displayPreferencesService: DisplayPreferencesService,
        private val serverRepository: ServerRepository,
        private val api: ApiClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val _key = MutableStateFlow<String?>(null)

        /** The key in use (null = none yet) */
        val key: StateFlow<String?> = _key

        @Volatile private var keyReadFor: String? = null

        /** Reads the shared key from the signed-in account (once per account) */
        suspend fun loadKey(): String? {
            val userId = serverRepository.currentUser?.id ?: return _key.value
            if (keyReadFor == userId.toString()) return _key.value
            return try {
                val saved =
                    withContext(WholphinDispatchers.IO) {
                        displayPreferencesService.getDisplayPreferences(userId).customPrefs[KEY_PREF]
                    }?.trim()?.takeIf { it.isNotBlank() }
                keyReadFor = userId.toString()
                _key.value = saved
                saved
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read the OMDb key")
                _key.value
            }
        }

        /**
         * Checks [newKey] works, then saves it to every account (admins only). Returns null when it worked,
         * or what went wrong in plain words.
         */
        suspend fun saveKey(newKey: String): String? =
            withContext(WholphinDispatchers.IO) {
                val cleaned = newKey.trim()
                if (cleaned.isNotEmpty()) {
                    // The Matrix: a quick test that the key is right
                    when (val test = ask(cleaned, "tt0133093")) {
                        is Answer.Problem -> return@withContext test.message
                        else -> {}
                    }
                }
                try {
                    val users = api.userApi.getUsers().content
                    users.forEach { user ->
                        try {
                            displayPreferencesService.updateDisplayPreferences(user.id) {
                                if (cleaned.isEmpty()) remove(KEY_PREF) else put(KEY_PREF, cleaned)
                            }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not share the OMDb key with %s", user.name)
                        }
                    }
                    _key.value = cleaned.takeIf { it.isNotEmpty() }
                    keyReadFor = serverRepository.currentUser?.id?.toString()
                    null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not save the OMDb key")
                    "Couldn't save it to the server. Try again in a moment."
                }
            }

        /** IMDb and Rotten Tomatoes scores for an IMDb id like "tt0214341"; null when there is no key or no answer */
        suspend fun scoresFor(imdbId: String): OmdbScore? {
            if (!imdbId.startsWith("tt")) return null
            val cacheName = "omdb-$imdbId"
            diskCache.read(cacheName, OmdbScore.serializer(), WEEK_MS)?.let { return it }
            val key = loadKey() ?: return null
            return withContext(WholphinDispatchers.IO) {
                when (val answer = ask(key, imdbId)) {
                    is Answer.Found -> {
                        diskCache.write(cacheName, OmdbScore.serializer(), answer.score)
                        answer.score
                    }

                    is Answer.Problem -> {
                        null
                    }
                }
            }
        }

        private sealed interface Answer {
            data class Found(
                val score: OmdbScore,
            ) : Answer

            data class Problem(
                val message: String,
            ) : Answer
        }

        private fun ask(
            key: String,
            imdbId: String,
        ): Answer =
            try {
                val url =
                    URL
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("i", imdbId)
                        .addQueryParameter("apikey", key)
                        .build()
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body.string()
                    val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                    val ok = (obj?.get("Response") as? JsonPrimitive)?.contentOrNull == "True"
                    val error = (obj?.get("Error") as? JsonPrimitive)?.contentOrNull
                    when {
                        response.code == 401 || error?.contains("key", ignoreCase = true) == true -> {
                            Answer.Problem("That key didn't work. Check it was activated from the OMDb email.")
                        }

                        error?.contains("limit", ignoreCase = true) == true -> {
                            Answer.Problem("The key has used up today's lookups. It works again tomorrow.")
                        }

                        obj != null && (ok || error?.contains("not found", ignoreCase = true) == true) -> {
                            Answer.Found(parse(obj))
                        }

                        else -> {
                            Answer.Problem("OMDb didn't answer (${response.code}). Try again later.")
                        }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "OMDb lookup failed for %s", imdbId)
                Answer.Problem("Couldn't reach OMDb. Check the internet connection.")
            }

        companion object {
            private const val URL = "https://www.omdbapi.com/"
            const val KEY_PREF = "benflix.omdb.key"
            private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

            /** Reads OMDb's answer: "imdbRating": "8.8" and "Ratings": [{"Source": "Rotten Tomatoes", "Value": "93%"}] */
            internal fun parse(obj: JsonObject): OmdbScore {
                val imdb =
                    (obj["imdbRating"] as? JsonPrimitive)
                        ?.contentOrNull
                        ?.toDoubleOrNull()
                        ?.takeIf { it > 0 }
                val rt =
                    (obj["Ratings"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .firstOrNull { (it["Source"] as? JsonPrimitive)?.contentOrNull == "Rotten Tomatoes" }
                        ?.let { (it["Value"] as? JsonPrimitive)?.contentOrNull }
                        ?.removeSuffix("%")
                        ?.toIntOrNull()
                return OmdbScore(imdb = imdb, rtCritics = rt)
            }
        }
    }
