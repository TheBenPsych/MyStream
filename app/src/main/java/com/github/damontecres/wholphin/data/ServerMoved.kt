package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.JellyfinServer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Benflix: finds Tower again after it gets a new home address (a move, a new router, a DHCP change).
 *
 * Devices that signed in with a home address like http://10.0.0.58:8096 used to be stuck on "can't reach
 * the server" until someone typed the new address on every TV. Now, when that saved home address stops
 * answering, Benflix asks the home network "where's Jellyfin?" (Jellyfin's own server finder), and also
 * tries Tower's name. If the same server (same server id) answers somewhere new, the new address is used
 * and saved, so it just keeps working.
 *
 * Internet addresses (like yohobox.duckdns.org) are never touched: they don't change when Tower moves.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ServerMovedEntryPoint {
    fun serverDao(): JellyfinServerDao

    fun mostRecentServer(): MostRecentServerProvider
}

internal object ServerMoved {
    /** Names a home router usually knows Tower by (Unraid's default name is "Tower") */
    private val NAMES = listOf("tower", "tower.local", "tower.lan", "tower.home", "tower.attlocal.net")

    /** True for home-network addresses like 10.x, 192.168.x and 172.16-31.x */
    fun isHomeAddress(url: String): Boolean {
        val host =
            try {
                URL(url).host
            } catch (_: Exception) {
                return false
            }
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 10 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31)
    }

    /** The server id at [base], or null when nothing answers quickly */
    private fun serverIdAt(base: String): String? =
        try {
            val conn = URL(base.trimEnd('/') + "/System/Info/Public").openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 3000
            try {
                if (conn.responseCode != 200) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Regex("\"Id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }

    private fun sameServer(
        id: String?,
        server: JellyfinServer,
    ): Boolean = id?.toUUIDOrNull() == server.id

    /**
     * Returns [server] with its new address when it moved, or [server] unchanged when the saved address still
     * works (or Tower can't be found). Call off the main thread.
     */
    suspend fun findAgain(
        jellyfin: Jellyfin,
        server: JellyfinServer,
    ): JellyfinServer {
        if (!isHomeAddress(server.url)) return server
        if (serverIdAt(server.url) != null) return server
        Timber.i("Saved home address %s doesn't answer, looking for the server on the home network", server.url)
        val found =
            try {
                withTimeoutOrNull(6000) {
                    jellyfin.discovery
                        .discoverLocalServers()
                        .firstOrNull { it.id.toUUIDOrNull() == server.id }
                        ?.address
                }
            } catch (e: Exception) {
                Timber.w(e, "Server finder didn't work")
                null
            }
        val port =
            try {
                URL(server.url).port.takeIf { it > 0 } ?: 8096
            } catch (_: Exception) {
                8096
            }
        val newUrl =
            found?.takeIf { sameServer(serverIdAt(it), server) }
                ?: NAMES.map { "http://$it:$port" }.firstOrNull { sameServer(serverIdAt(it), server) }
        if (newUrl == null) {
            Timber.i("Couldn't find the server at a new address")
            return server
        }
        Timber.i("Server moved: %s -> %s", server.url, newUrl)
        return server.copy(url = newUrl.trimEnd('/'))
    }

    /**
     * Benflix: "Change server address" on the can't-connect screen. Saves [input] (like 192.168.1.105:8096) as
     * the server's address when the same server answers there. Returns null when it worked, else a plain
     * sentence saying what went wrong. Call off the main thread.
     */
    fun useAddress(
        context: android.content.Context,
        input: String,
    ): String? {
        var a = input.trim().trimEnd('/')
        if (a.isBlank()) return "Type Tower's address, like 192.168.1.105:8096"
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
        val hasPort = Regex("^https?://[^/]+:\\d+").containsMatchIn(a)
        if (!hasPort && a.startsWith("http://")) {
            val u = URL(a)
            a = "http://" + u.host + ":8096" + u.path
        }
        val entry =
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, ServerMovedEntryPoint::class.java)
        val serverId =
            when (val recent = entry.mostRecentServer().get()) {
                is MostRecentServer.Server -> recent.serverId
                is MostRecentServer.ServerAndUser -> recent.serverId
                else -> null
            } ?: return "No server is saved on this device yet."
        val server = entry.serverDao().getServer(serverId)?.server ?: return "No server is saved on this device yet."
        val id = serverIdAt(a) ?: return "Nothing answered at $a. Check the address and that Tower is on."
        if (!sameServer(id, server)) return "A different Jellyfin server answered at $a."
        entry.serverDao().addOrUpdateServer(server.copy(url = a))
        Timber.i("Server address changed by hand: %s -> %s", server.url, a)
        return null
    }

    /**
     * Benflix: the "Is this your server?" screen shows the address Jellyfin says it's at. After a move that can
     * be the old one (Jellyfin's "published" address wasn't updated), so check it really answers, and if not
     * look for the same server under Tower's names. Returns the address that works, else [address] unchanged.
     * Call off the main thread.
     */
    fun reachableAddress(
        id: String,
        address: String,
    ): String {
        val want = id.toUUIDOrNull() ?: return address
        if (serverIdAt(address)?.toUUIDOrNull() == want) return address
        val port =
            try {
                URL(address).port.takeIf { it > 0 } ?: 8096
            } catch (_: Exception) {
                8096
            }
        return NAMES
            .map { "http://$it:$port" }
            .firstOrNull { serverIdAt(it)?.toUUIDOrNull() == want }
            ?.also { Timber.i("Found server answers at %s, not %s", it, address) }
            ?: address
    }
}
