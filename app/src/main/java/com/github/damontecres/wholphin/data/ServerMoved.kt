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
internal object ServerMoved {
    /** Names a home router usually knows Tower by (Unraid's default name is "Tower") */
    private val NAMES = listOf("tower", "tower.local", "tower.lan", "tower.home")

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
}
