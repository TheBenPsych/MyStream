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
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One game from the game library on the server */
data class ServerGame(
    val id: Int,
    val name: String,
    val coverUrl: String?,
    val playable: Boolean,
    /** Size of the game's file(s) on the server, 0 if unknown */
    val size: Long = 0,
    /** The year it came out, 0 if unknown */
    val year: Int = 0,
    val genres: List<String> = emptyList(),
    /** Rated for everyone, so it shows on the Kids profile */
    val kids: Boolean = false,
    /** How well loved it is, 0 to 100, from RomM's IGDB ratings (0 if unknown) */
    val rating: Double = 0.0,
)

/** One console and its games, e.g. "Nintendo 64" */
data class GameConsole(
    val name: String,
    val games: List<ServerGame>,
    /** The console's folder name in RomM, e.g. "gba" */
    val slug: String = "",
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

        /**
         * Benflix: the address that answered last time [pickAddress] looked. Away from home that's the
         * internet address (the same one Jellyfin uses, plus /benflix-games), at home the normal one.
         */
        @Volatile
        private var working: String? = null

        /** The helper's address: the one that works right now, else the Jellyfin server's address with port 8097 */
        val address: String
            get() = working ?: homeAddress

        /** The helper's address at home: the Jellyfin server's address with port 8097, unless one was typed in */
        private val homeAddress: String
            get() {
                customAddress.takeIf { it.isNotBlank() }?.let { return it }
                val host =
                    serverRepository.currentServer
                        ?.url
                        ?.toHttpUrlOrNull()
                        ?.host ?: return ""
                return "http://$host:$PORT"
            }

        /** Sent with every request (and box art), so the helper knows who's signed in */
        val token: String get() = serverRepository.currentUser?.accessToken ?: ""

        /** The Jellyfin server and user, for watching a show next to a game */
        val jellyfinUrl: String get() = serverRepository.currentServer?.url ?: ""
        val userId: String get() =
            serverRepository.currentUser
                ?.id
                ?.toString()
                ?.replace("-", "") ?: ""

        fun saveAddress(input: String) {
            val value = if (input.isBlank()) "" else normalizeAddress(input)
            prefs.edit().putString("address", value).apply()
        }

        /**
         * The helper through the internet, for playing away from home: Jellyfin's own https address with
         * /benflix-games on the end (Tower's web proxy sends that on to the helper). Null when Jellyfin isn't
         * reached over https.
         */
        private val awayAddress: String?
            get() {
                val url = serverRepository.currentServer?.url?.toHttpUrlOrNull() ?: return null
                if (!url.isHttps) return null
                return url.scheme + "://" + url.host + (if (url.port != 443) ":${url.port}" else "") + AWAY_PATH
            }

        private val quickHttp by lazy {
            http
                .newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }

        private fun answers(base: String): Boolean =
            try {
                quickHttp.newCall(Request.Builder().url("$base/health").build()).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }

        /** Benflix speed: whether the internet address was the one that answered last time */
        @Volatile
        private var lastWasAway: Boolean = prefs.getBoolean(KEY_LAST_AWAY, false)

        /**
         * Works out which address reaches the helper right now: home first, then the internet one.
         * Benflix speed: whichever answered last time is tried first, so away from home the phone no
         * longer waits for the home address to time out (about 3 seconds) every time Games opens.
         */
        suspend fun pickAddress() = withContext(Dispatchers.IO) { pickNow() }

        private fun pickNow() {
            val home = homeAddress
            val away = awayAddress
            val awayFirst = lastWasAway && away != null
            working =
                when {
                    awayFirst && answers(away) -> away
                    home.isNotBlank() && answers(home) -> null
                    !awayFirst && away != null && answers(away) -> away
                    else -> null
                }
            val nowAway = working != null
            if (nowAway != lastWasAway) {
                lastWasAway = nowAway
                prefs.edit().putBoolean(KEY_LAST_AWAY, nowAway).apply()
            }
            if (working != null) Timber.i("Games helper reached through the internet address")
        }

        /** Every game in the library, grouped by console. Throws with a friendly message when it can't. */
        suspend fun loadLibrary(): List<GameConsole> =
            withContext(Dispatchers.IO) {
                pickAddress()
                val text = call(Request.Builder().url("$address/api/games"))
                val consoles = parseLibrary(text, address)
                // Benflix speed: kept so Games opens straight away next time
                savedLibrary = SavedLibrary(token, text)
                try {
                    savedLibraryFile().writeText(token.hashCode().toString() + "\n" + text)
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't save the game list for next time")
                }
                consoles
            }

        private class SavedLibrary(
            val token: String,
            val text: String,
        )

        @Volatile
        private var savedLibrary: SavedLibrary? = null

        private fun savedLibraryFile() = java.io.File(context.cacheDir, "benflix-games-library.json")

        /**
         * Benflix speed: the game list from last time (this sign-in only), shown while the fresh one
         * loads. Null when there isn't one.
         */
        suspend fun savedLibrary(): List<GameConsole>? =
            withContext(Dispatchers.IO) {
                try {
                    val tok = token
                    if (tok.isBlank()) return@withContext null
                    val text =
                        savedLibrary?.takeIf { it.token == tok }?.text
                            ?: savedLibraryFile()
                                .takeIf { it.exists() }
                                ?.readText()
                                ?.let { saved ->
                                    val cut = saved.indexOf('\n')
                                    if (cut > 0 && saved.substring(0, cut) == tok.hashCode().toString()) {
                                        saved.substring(cut + 1)
                                    } else {
                                        null
                                    }
                                }
                            ?: return@withContext null
                    // Box art from the address that worked last time (home or internet)
                    val base = working ?: (if (lastWasAway) awayAddress else null) ?: address
                    parseLibrary(text, base)
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't read the saved game list")
                    null
                }
            }

        private fun parseLibrary(
            text: String,
            base: String,
        ): List<GameConsole> {
            val json = JSONObject(text)
            val consoles = json.optJSONArray("consoles") ?: JSONArray()
            return (0 until consoles.length()).map { i ->
                val c = consoles.getJSONObject(i)
                val games = c.optJSONArray("games") ?: JSONArray()
                GameConsole(
                    name = c.optString("name", "Games"),
                    slug = c.optString("slug", ""),
                    games =
                        (0 until games.length()).map { j ->
                            val g = games.getJSONObject(j)
                            ServerGame(
                                id = g.optInt("id"),
                                name = g.optString("name", "Unknown game"),
                                coverUrl = if (g.isNull("cover")) null else base + g.optString("cover"),
                                playable = g.optBoolean("playable", true),
                                size = g.optLong("size", 0L),
                                year = g.optInt("year", 0),
                                genres =
                                    g.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } }
                                        ?: emptyList(),
                                kids = g.optBoolean("kids", false),
                                rating = g.optDouble("rating", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                            )
                        },
                )
            }
        }

        /** A JSON answer from the helper, e.g. "/api/rom/12" */
        suspend fun getJson(path: String): JSONObject =
            withContext(Dispatchers.IO) { JSONObject(call(Request.Builder().url(address + path))) }

        /** Benflix Server page tools: sends [body] to the helper and returns its JSON answer */
        suspend fun postJson(
            path: String,
            body: JSONObject = JSONObject(),
        ): JSONObject =
            withContext(Dispatchers.IO) {
                JSONObject(
                    call(
                        Request
                            .Builder()
                            .url(address + path)
                            .post(body.toString().toRequestBody("application/json".toMediaType())),
                    ),
                )
            }

        /**
         * Downloads a file from the helper (a game file, BIOS or save) into [target], reporting bytes as they arrive.
         * Returns false if the helper says there's no such file.
         */
        suspend fun download(
            path: String,
            target: java.io.File,
            onBytes: (Long) -> Unit = {},
        ): Boolean =
            withContext(Dispatchers.IO) {
                if (token.isBlank()) throw GameLibraryException("Sign in to Benflix first.")
                val request =
                    Request
                        .Builder()
                        .url(address + path)
                        .header("X-Benflix-Token", token)
                        .build()
                val response =
                    try {
                        downloadHttp.newCall(request).execute()
                    } catch (e: Exception) {
                        throw GameLibraryException("Couldn't reach the games helper at $address.", askAddress = true, cause = e)
                    }
                response.use {
                    if (it.code == 404) return@withContext false
                    if (!it.isSuccessful) throw GameLibraryException("The games helper answered with an error (${it.code}).")
                    target.parentFile?.mkdirs()
                    val tmp = java.io.File(target.path + ".part")
                    var done = 0L
                    it.body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                onBytes(done)
                            }
                        }
                    }
                    if (!tmp.renameTo(target)) {
                        target.delete()
                        if (!tmp.renameTo(target)) throw GameLibraryException("Couldn't save the download on this device.")
                    }
                    true
                }
            }

        private val downloadHttp by lazy {
            http
                .newBuilder()
                .readTimeout(2, TimeUnit.MINUTES)
                .build()
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
                    try {
                        http.newCall(request).execute()
                    } catch (first: java.io.IOException) {
                        // Benflix: music, requests and the Server page also use the helper, often before Games has
                        // worked out home vs. away. Look again once and retry on the address that answers.
                        val before = address
                        pickNow()
                        val now = address
                        if (now == before || now.isBlank()) throw first
                        val url = request.url.toString()
                        if (!url.startsWith(before)) throw first
                        http.newCall(request.newBuilder().url(now + url.removePrefix(before)).build()).execute()
                    }
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
            private const val KEY_LAST_AWAY = "last_was_away"

            /** Path on Jellyfin's internet address that Tower's web proxy sends to the games helper */
            const val AWAY_PATH = "/benflix-games"

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

/**
 * Set while a game is playing (in its own process), so coming back to Benflix afterwards returns to the Games tab
 * instead of the "Who's watching?" screen
 */
object GameStreamReturn {
    @Volatile
    var pending: Boolean = false
}
