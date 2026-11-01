package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One game from the game library on the server */
data class ServerGame(
    val id: Int,
    val name: String,
    val coverUrl: String?,
    val playable: Boolean,
)

/** One console and its games, e.g. "Nintendo 64" */
data class GameConsole(
    val name: String,
    val games: List<ServerGame>,
)

/** Which Moonlight computer to open, and which app on it */
data class PlayTarget(
    val computerUuid: String?,
    val computerName: String?,
    val appName: String,
)

/**
 * Talks to the Benflix games helper on the home server (port 8097, next to Jellyfin). The helper checks
 * the person is signed in to Jellyfin, so there's no separate game library login.
 */
@Singleton
class GameLibraryClient
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
    ) {
        private val prefs = context.getSharedPreferences("benflix_games_server", Context.MODE_PRIVATE)
        private val http =
            OkHttpClient
                .Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

        /** Address typed in by hand, if the automatic one doesn't work */
        val customAddress: String get() = prefs.getString("address", "") ?: ""

        /** The helper's address: the Jellyfin server's address with port 8097, unless one was typed in */
        val address: String
            get() {
                customAddress.takeIf { it.isNotBlank() }?.let { return it }
                val host = serverRepository.currentServer?.url?.toHttpUrlOrNull()?.host ?: return ""
                return "http://$host:$PORT"
            }

        /** Sent with every request (and box art), so the helper knows who's signed in */
        val token: String get() = serverRepository.currentUser?.accessToken ?: ""

        fun saveAddress(input: String) {
            val value = if (input.isBlank()) "" else normalizeAddress(input)
            prefs.edit().putString("address", value).apply()
        }

        /** Every game in the library, grouped by console. Throws with a friendly message when it can't. */
        suspend fun loadLibrary(): List<GameConsole> =
            withContext(Dispatchers.IO) {
                val base = address
                val json = JSONObject(call(Request.Builder().url("$base/api/games")))
                val consoles = json.optJSONArray("consoles") ?: JSONArray()
                (0 until consoles.length()).map { i ->
                    val c = consoles.getJSONObject(i)
                    val games = c.optJSONArray("games") ?: JSONArray()
                    GameConsole(
                        name = c.optString("name", "Games"),
                        games =
                            (0 until games.length()).map { j ->
                                val g = games.getJSONObject(j)
                                ServerGame(
                                    id = g.optInt("id"),
                                    name = g.optString("name", "Unknown game"),
                                    coverUrl = if (g.isNull("cover")) null else base + g.optString("cover"),
                                    playable = g.optBoolean("playable", true),
                                )
                            },
                    )
                }
            }

        /** Tells the server which game to start next. Returns where Moonlight should connect. */
        suspend fun prepare(game: ServerGame): PlayTarget =
            withContext(Dispatchers.IO) {
                val json =
                    JSONObject(
                        call(
                            Request
                                .Builder()
                                .url("$address/api/play/${game.id}")
                                .post("{}".toRequestBody("application/json".toMediaType())),
                        ),
                    )
                val computer = json.optJSONObject("computer")
                PlayTarget(
                    computerUuid = computer?.optStringOrNull("uuid"),
                    computerName = computer?.optStringOrNull("name"),
                    appName = json.optStringOrNull("app") ?: "Benflix",
                )
            }

        private fun call(builder: Request.Builder): String {
            if (token.isBlank()) throw GameLibraryException("Sign in to Benflix first.")
            if (address.isBlank()) throw GameLibraryException("Couldn't work out your server's address.", askAddress = true)
            val request =
                builder
                    .header("X-Benflix-Token", token)
                    .header("Accept", "application/json")
                    .build()
            val response =
                try {
                    http.newCall(request).execute()
                } catch (e: Exception) {
                    throw GameLibraryException(
                        "Couldn't reach the games helper at $address. Run the games setup command on the server " +
                            "(it starts the helper), or set the address by hand.",
                        askAddress = true,
                        cause = e,
                    )
                }
            response.use {
                val body = it.body.string()
                if (!it.isSuccessful) {
                    val message =
                        try {
                            JSONObject(body).optStringOrNull("error")
                        } catch (_: Exception) {
                            null
                        }
                    throw GameLibraryException(message ?: "The games helper answered with an error (${it.code}).")
                }
                return body
            }
        }

        companion object {
            const val PORT = 8097

            /** "10.0.0.58" -> "http://10.0.0.58:8097" */
            fun normalizeAddress(input: String): String {
                var a = input.trim().trimEnd('/')
                if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
                val url = a.toHttpUrlOrNull() ?: return a
                val hasPort = Regex("^https?://[^/]+:\\d+").containsMatchIn(a)
                return if (hasPort) url.toString().trimEnd('/') else "${url.scheme}://${url.host}:$PORT"
            }
        }
    }

class GameLibraryException(
    message: String,
    val askAddress: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
