package com.github.damontecres.wholphin.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One game from the RomM game library */
data class RommGame(
    val id: Int,
    val name: String,
    val platformName: String,
    val coverUrl: String?,
)

/** One console and its games, e.g. "Nintendo 64" */
data class RommConsole(
    val name: String,
    val games: List<RommGame>,
)

/**
 * Talks to the RomM game library on the home server (the "Jellyfin for games").
 * The address and login are saved on this device.
 */
@Singleton
class RommClient
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val prefs = context.getSharedPreferences("benflix_romm", Context.MODE_PRIVATE)
        private val http =
            OkHttpClient
                .Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        val address: String get() = prefs.getString("address", "") ?: ""
        val username: String get() = prefs.getString("username", "") ?: ""
        private val password: String get() = prefs.getString("password", "") ?: ""

        val isConfigured: Boolean get() = address.isNotBlank() && username.isNotBlank()

        /** The login header RomM accepts (HTTP Basic), also used for box art */
        val authHeader: String get() = Credentials.basic(username, password)

        fun save(
            address: String,
            username: String,
            password: String,
        ) {
            prefs
                .edit()
                .putString("address", normalizeAddress(address))
                .putString("username", username.trim())
                .putString("password", password)
                .apply()
        }

        fun forget() = prefs.edit().clear().apply()

        /**
         * Every game in the library, grouped by console. Throws with a friendly message when it can't connect.
         */
        suspend fun loadLibrary(): List<RommConsole> =
            withContext(Dispatchers.IO) {
                val base = address
                val games = mutableListOf<RommGame>()
                var offset = 0
                val pageSize = 250
                while (true) {
                    val url =
                        "$base/api/roms?limit=$pageSize&offset=$offset&order_by=name&order_dir=asc" +
                            "&with_char_index=false&with_filter_values=false&with_rom_id_index=false&with_provider_metadata=false"
                    val json = JSONObject(get(url))
                    val items = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        games.add(parseGame(base, items.getJSONObject(i)))
                    }
                    offset += items.length()
                    val total = if (json.isNull("total")) -1 else json.optInt("total", -1)
                    if (items.length() < pageSize || (total in 0..offset)) break
                }
                games
                    .groupBy { it.platformName }
                    .map { (name, list) -> RommConsole(name, list.sortedBy { it.name.lowercase() }) }
                    .sortedBy { it.name }
            }

        private fun parseGame(
            base: String,
            o: JSONObject,
        ): RommGame {
            val name =
                o.optStringOrNull("name")
                    ?: o.optStringOrNull("fs_name_no_tags")
                    ?: o.optStringOrNull("fs_name")
                    ?: "Unknown game"
            val platform =
                o.optStringOrNull("platform_display_name")
                    ?: o.optStringOrNull("platform_custom_name")
                    ?: o.optStringOrNull("platform_name")
                    ?: o.optStringOrNull("platform_slug")
                    ?: "Games"
            val cover =
                o.optStringOrNull("path_cover_large")?.let { "$base/assets/romm/resources/${it.trimStart('/')}" }
                    ?: o.optStringOrNull("path_cover_small")?.let { "$base/assets/romm/resources/${it.trimStart('/')}" }
                    ?: o.optStringOrNull("url_cover")?.let { if (it.startsWith("//")) "https:$it" else it }
            return RommGame(id = o.optInt("id"), name = name, platformName = platform, coverUrl = cover)
        }

        private fun get(url: String): String {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", authHeader)
                    .header("Accept", "application/json")
                    .build()
            val response =
                try {
                    http.newCall(request).execute()
                } catch (e: Exception) {
                    throw RommException("Couldn't reach your game library at $address. Check the address and that RomM is running.", e)
                }
            response.use {
                when {
                    it.code == 401 || it.code == 403 -> throw RommException("RomM didn't accept the username or password.")
                    !it.isSuccessful -> throw RommException("Your game library answered with an error (${it.code}).")
                }
                return it.body.string()
            }
        }

        companion object {
            /** "10.0.0.58:8095" -> "http://10.0.0.58:8095" */
            fun normalizeAddress(input: String): String {
                var a = input.trim().trimEnd('/')
                if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
                return a.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: a
            }
        }
    }

class RommException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
