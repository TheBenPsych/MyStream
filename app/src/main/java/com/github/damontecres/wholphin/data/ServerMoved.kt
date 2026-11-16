package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.JellyfinServer
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
 * It also picks the home address over the internet one when on the home Wi-Fi, and an internet one when away.
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

    /** True for addresses that only work inside the house: 192.168.x / 10.x / 172.16-31.x, or Tower's names */
    fun isLocal(url: String): Boolean {
        if (isHomeAddress(url)) return true
        val host =
            try {
                URL(url).host.lowercase()
            } catch (_: Exception) {
                return false
            }
        return host in NAMES
    }

    /** True when this device is on Wi-Fi or a cable (so Tower's home address might answer) */
    fun onHomeKindOfNetwork(context: android.content.Context): Boolean =
        try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps == null ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            true
        }

    private fun portOf(url: String): Int =
        try {
            URL(url).port.takeIf { it > 0 && !url.startsWith("https") } ?: 8096
        } catch (_: Exception) {
            8096
        }

    /**
     * Benflix: picks the best address for [server] right now. Every address this device knows (the saved one, home,
     * Tower's names, the internet name, Tailscale) is tried AT THE SAME TIME, and on Wi-Fi the home network is also
     * asked "where's Jellyfin?". On Wi-Fi a home address wins over the internet one (AT&T's box won't let phones
     * inside the house use the internet address, which left phones on "You're offline" at home).
     *
     * Returns [server] with the address to use, or unchanged when nothing answers. Call off the main thread.
     */
    suspend fun findAgain(
        context: android.content.Context,
        server: JellyfinServer,
    ): JellyfinServer {
        val saved = server.url.trimEnd('/')
        val port = portOf(saved)
        val onWifi = onHomeKindOfNetwork(context)
        val candidates =
            (
                listOf(saved) +
                    ServerAddressBook.all(context, server.id) +
                    NAMES.map { "http://$it:$port" }
            ).distinct()
        val askWifi: suspend () -> String? = { findOnWifi().firstOrNull { it.id == server.id }?.url }
        val finders = if (onWifi) listOf(askWifi) else emptyList()
        val best =
            ServerAddressBook.bestWorking(
                candidates = candidates,
                serverId = server.id,
                preferHome = onWifi,
                isHome = ::isLocal,
                finders = finders,
            )
        if (best != null) {
            if (best != saved) Timber.i("Using a better server address: %s -> %s", saved, best)
            return if (best == saved) server else server.copy(url = best)
        }
        // Nothing this device knows answers: GitHub's list might name a new address
        Timber.i("No known address answers for %s, checking GitHub's list", saved)
        ServerAddressBook.firstWorking(ServerAddressBook.gitHubList(), server.id)?.let {
            Timber.i("Server answers at an address from GitHub's list: %s", it)
            return server.copy(url = it)
        }
        Timber.i("Couldn't reach the server at any address")
        return server
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
        ServerAddressBook.remember(context, server.id, a, first = true)
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
            ?: senderAddress(want, port)
            ?: address
    }

    /**
     * Benflix: asks the home network "who is Jellyfin?" (the same question Jellyfin's own apps ask) and uses the
     * address the answer actually CAME FROM, not the address written inside it, which can be stale after a move.
     * Returns a working http address for server [want], or null.
     */
    private fun senderAddress(
        want: java.util.UUID,
        port: Int,
    ): String? {
        val senders = mutableListOf<String>()
        try {
            java.net.DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1500
                val ask = "who is JellyfinServer?".toByteArray()
                socket.send(
                    java.net.DatagramPacket(ask, ask.size, java.net.InetAddress.getByName("255.255.255.255"), 7359),
                )
                val buf = ByteArray(4096)
                val end = System.currentTimeMillis() + 2500
                while (System.currentTimeMillis() < end) {
                    val packet = java.net.DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                    val body = String(packet.data, 0, packet.length)
                    val id = Regex("\"Id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    if (id?.toUUIDOrNull() == want) packet.address?.hostAddress?.let { senders.add(it) }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Server finder (sender address) didn't work")
        }
        return senders
            .distinct()
            .map { "http://$it:$port" }
            .firstOrNull { serverIdAt(it)?.toUUIDOrNull() == want }
            ?.also { Timber.i("Found server by where its answer came from: %s", it) }
    }

    /** A Jellyfin server found on the home Wi-Fi: its id, name and an address that answers */
    data class Found(
        val id: java.util.UUID,
        val name: String?,
        val url: String,
    )

    /**
     * Benflix: finds Tower on the home Wi-Fi even when Jellyfin's own finder doesn't answer (Jellyfin in Docker
     * often can't hear the question) or answers with an old address. Asks Jellyfin (port 7359) and Tower's Benflix
     * helper (port 7360, which always hears it), uses the address each answer came from, and also tries Tower's
     * names. Only addresses that really answer are returned. Call off the main thread.
     */
    fun findOnWifi(): List<Found> {
        val found = linkedMapOf<java.util.UUID, Found>()

        fun tryUrl(url: String) {
            val body = publicInfo(url) ?: return
            val id =
                Regex("\"Id\"\\s*:\\s*\"([^\"]+)\"")
                    .find(body)
                    ?.groupValues
                    ?.get(1)
                    ?.toUUIDOrNull() ?: return
            if (id in found) return
            val name = Regex("\"ServerName\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
            found[id] = Found(id, name, url)
        }
        try {
            java.net.DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1200
                val ask = "who is JellyfinServer?".toByteArray()
                val all = java.net.InetAddress.getByName("255.255.255.255")
                for (port in listOf(7359, 7360)) socket.send(java.net.DatagramPacket(ask, ask.size, all, port))
                val buf = ByteArray(4096)
                val end = System.currentTimeMillis() + 2500
                val answers = mutableListOf<Pair<String, Int>>()
                while (System.currentTimeMillis() < end) {
                    val packet = java.net.DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                    val body = String(packet.data, 0, packet.length)
                    val port =
                        Regex("\"Address\"\\s*:\\s*\"[^\"]*:(\\d+)")
                            .find(body)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull() ?: 8096
                    packet.address?.hostAddress?.let { answers.add(it to port) }
                }
                answers.distinct().forEach { (ip, port) -> tryUrl("http://$ip:$port") }
            }
        } catch (e: Exception) {
            Timber.w(e, "Wi-Fi server finder didn't work")
        }
        if (found.isEmpty()) NAMES.forEach { tryUrl("http://$it:8096") }
        return found.values.toList()
    }

    private fun publicInfo(base: String): String? =
        try {
            val conn = URL(base.trimEnd('/') + "/System/Info/Public").openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 3000
            try {
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
}
