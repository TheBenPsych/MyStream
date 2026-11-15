package com.github.damontecres.wholphin.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Benflix: every address this device has seen Tower's Jellyfin answer at, remembered per server.
 *
 * Benflix used to keep just one address. If that one stopped working (Tower moved, the phone left home, the
 * internet name changed) the app was stuck until someone typed a new one, updated, or cleared the app.
 * Now it keeps a little address book:
 *  - every address that ever worked on this device,
 *  - Tower's own list (home address, Tower's name, the internet name people use, Tailscale), asked for while
 *    connected, from the Benflix helper's /api/addresses,
 *  - a list on GitHub (server/benflix-addresses.json), so a new address can reach every device without an app
 *    update, even one that can't reach Tower at all right now.
 *
 * When the saved address doesn't answer, [firstWorking] tries them all at once and picks one where the SAME server
 * (same server id) answers, so a stranger's server can never be picked.
 */
object ServerAddressBook {
    private const val PREFS = "benflix_address_book"
    private const val MAX = 16

    /** How often the lists are asked for again (Tower's and GitHub's) */
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L

    const val GITHUB_LIST =
        "https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/benflix-addresses.json"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun clean(url: String): String? {
        val u = url.trim().trimEnd('/')
        if (!u.startsWith("http://") && !u.startsWith("https://")) return null
        return try {
            URL(u).host.takeIf { it.isNotBlank() }?.let { u }
        } catch (_: Exception) {
            null
        }
    }

    /** All addresses known for [serverId], most recently working first */
    fun all(
        context: Context,
        serverId: UUID,
    ): List<String> =
        try {
            val arr = JSONArray(prefs(context).getString(serverId.toString(), "[]"))
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }

    /** Remembers [url] for [serverId]. [first] puts it at the front (it just worked). */
    fun remember(
        context: Context,
        serverId: UUID,
        urls: List<String>,
        first: Boolean = false,
    ) {
        val add = urls.mapNotNull { clean(it) }
        if (add.isEmpty()) return
        synchronized(this) {
            val old = all(context, serverId)
            val merged = (if (first) add + old else old + add).distinct().take(MAX)
            if (merged == old) return
            prefs(context).edit().putString(serverId.toString(), JSONArray(merged).toString()).apply()
        }
    }

    fun remember(
        context: Context,
        serverId: UUID,
        url: String,
        first: Boolean = false,
    ) = remember(context, serverId, listOf(url), first)

    /**
     * Tries every address in [candidates] at the same time and returns the first one where the same server answers,
     * or null. Waits at most about 6 seconds. Call off the main thread.
     */
    suspend fun firstWorking(
        candidates: List<String>,
        serverId: UUID,
    ): String? {
        val list = candidates.mapNotNull { clean(it) }.distinct()
        if (list.isEmpty()) return null
        // The checks run on their own (a slow one can't hold up the answer); the first one to say yes wins
        val answers = Channel<String?>(list.size)
        val checks = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        list.forEach { url -> checks.launch { answers.trySend(url.takeIf { serverIdAt(it) == serverId }) } }
        return withTimeoutOrNull(6000) {
            var winner: String? = null
            var heard = 0
            while (winner == null && heard < list.size) {
                winner = answers.receive()
                heard++
            }
            winner
        }.also { checks.cancel() }
    }

    /** The server id answering at [base], or null */
    fun serverIdAt(base: String): UUID? =
        try {
            val body = get(base.trimEnd('/') + "/System/Info/Public") ?: return null
            Regex("\"Id\"\\s*:\\s*\"([^\"]+)\"")
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.let { parseId(it) }
        } catch (_: Exception) {
            null
        }

    private fun parseId(s: String): UUID? =
        try {
            val hex = s.replace("-", "")
            if (hex.length != 32) {
                null
            } else {
                UUID.fromString(
                    "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                        "${hex.substring(16, 20)}-${hex.substring(20)}",
                )
            }
        } catch (_: Exception) {
            null
        }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 2500
        conn.readTimeout = 3000
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Where Tower's Benflix helper answers for a Jellyfin address: port 8097 at home, /benflix-games over https */
    private fun helperFor(jellyfinUrl: String): String? =
        try {
            val u = URL(jellyfinUrl)
            if (u.protocol == "https") {
                "https://" + u.host + (if (u.port > 0 && u.port != 443) ":${u.port}" else "") + "/benflix-games"
            } else {
                "http://" + u.host + ":8097"
            }
        } catch (_: Exception) {
            null
        }

    private fun addressesIn(body: String): List<String> =
        try {
            val arr = JSONObject(body).optJSONArray("addresses") ?: JSONArray()
            (0 until arr.length()).mapNotNull {
                when (val a = arr.opt(it)) {
                    is JSONObject -> a.optString("url")
                    is String -> a
                    else -> null
                }?.takeIf { s -> s.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * Asks Tower (through [workingUrl], which just answered) and GitHub for their address lists and remembers
     * them. Only every few hours unless [force]. Call off the main thread.
     */
    suspend fun refresh(
        context: Context,
        serverId: UUID,
        workingUrl: String,
        force: Boolean = false,
    ) {
        val p = prefs(context)
        val key = "checked-$serverId"
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong(key, 0) < REFRESH_MS) return
        p.edit().putLong(key, now).apply()
        coroutineScope {
            val fromTower =
                async(Dispatchers.IO) {
                    val helper = helperFor(workingUrl) ?: return@async emptyList()
                    val body = get("$helper/api/addresses") ?: return@async emptyList()
                    val id = runCatching { JSONObject(body).optString("id") }.getOrNull()?.let { parseId(it) }
                    // Only take Tower's list when it's about this server
                    if (id != null && id != serverId) emptyList() else addressesIn(body)
                }
            val fromGitHub =
                async(Dispatchers.IO) { get(GITHUB_LIST)?.let { addressesIn(it) }.orEmpty() }
            val found = fromTower.await() + fromGitHub.await()
            if (found.isNotEmpty()) {
                Timber.i("Address book for %s: %d addresses from Tower and GitHub", serverId, found.size)
                remember(context, serverId, found)
            }
        }
    }

    /** Only GitHub's list, for when nothing else answers (it might name a new address). Call off the main thread. */
    fun gitHubList(): List<String> = get(GITHUB_LIST)?.let { addressesIn(it) }.orEmpty()
}
