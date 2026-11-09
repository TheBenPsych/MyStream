package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One line of the parents' guide, like "Violence" to "A little" */
@Serializable
data class ParentsGuideLine(
    val label: String,
    val level: String,
    /** 0 none, 1 a little, 2 some, 3 a lot */
    val rank: Int,
)

/**
 * Benflix "Parents' guide": how much violence, swearing, scary bits and adult scenes a movie or show
 * has, from IMDb's public parents guide (the same one on imdb.com, voted on by parents).
 *
 * Saved on the device for a month, so each title costs one lookup a month at most. Nothing shows when
 * IMDb has no guide for it or can't be reached.
 */
@Singleton
class ParentsGuide
    @Inject
    constructor(
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val diskCache: DiskCache,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /** The guide for [item], or an empty list when there is none */
        suspend fun guideFor(item: BaseItemDto): List<ParentsGuideLine> {
            val imdbId =
                item.providerIds
                    ?.entries
                    ?.firstOrNull { it.key.equals("Imdb", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it.startsWith("tt") } ?: return emptyList()
            val cacheName = "parents-$imdbId"
            diskCache.read(cacheName, ListSerializer(ParentsGuideLine.serializer()), MONTH_MS)?.let { return it }
            return withContext(WholphinDispatchers.IO) {
                val lines = ask(imdbId) ?: return@withContext emptyList()
                diskCache.write(cacheName, ListSerializer(ParentsGuideLine.serializer()), lines)
                lines
            }
        }

        /** Null = IMDb couldn't be asked (try again next time), empty = IMDb has no guide */
        private fun ask(imdbId: String): List<ParentsGuideLine>? =
            try {
                val body =
                    buildJsonObject {
                        put("query", QUERY)
                        putJsonObject("variables") { put("id", imdbId) }
                    }.toString()
                val request =
                    Request
                        .Builder()
                        .url(URL)
                        .header("Referer", "https://www.imdb.com/")
                        .header("User-Agent", "Mozilla/5.0")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("Parents guide: IMDb answered %s for %s", response.code, imdbId)
                        return null
                    }
                    parse(json.parseToJsonElement(response.body.string()) as? JsonObject)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Parents guide lookup failed for %s", imdbId)
                null
            }

        companion object {
            private const val URL = "https://graphql.imdb.com/"
            private const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
            private const val QUERY =
                "query ParentsGuide(\$id: ID!) { title(id: \$id) { parentsGuide { categories " +
                    "{ category { id text } severity { text } } } } }"

            /** Plain names for IMDb's categories, in the order they show */
            private val NAMES =
                linkedMapOf(
                    "VIOLENCE" to "Violence",
                    "PROFANITY" to "Swearing",
                    "FRIGHTENING" to "Scary bits",
                    "NUDITY" to "Adult scenes",
                    "ALCOHOL" to "Drinking and drugs",
                )

            internal fun parse(root: JsonObject?): List<ParentsGuideLine> {
                val categories =
                    ((((root?.get("data") as? JsonObject)?.get("title") as? JsonObject)?.get("parentsGuide") as? JsonObject)
                        ?.get("categories") as? JsonArray)
                        ?: return emptyList()
                val found =
                    categories.mapNotNull { el ->
                        val obj = el as? JsonObject ?: return@mapNotNull null
                        val id = ((obj["category"] as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        val severity = ((obj["severity"] as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull
                        val name = NAMES[id.uppercase()] ?: return@mapNotNull null
                        val rank =
                            when (severity?.lowercase()) {
                                "none" -> 0
                                "mild" -> 1
                                "moderate" -> 2
                                "severe" -> 3
                                else -> return@mapNotNull null
                            }
                        id.uppercase() to ParentsGuideLine(name, LEVELS[rank], rank)
                    }.toMap()
                return NAMES.keys.mapNotNull { found[it] }
            }

            private val LEVELS = listOf("None", "A little", "Some", "A lot")
        }
    }
