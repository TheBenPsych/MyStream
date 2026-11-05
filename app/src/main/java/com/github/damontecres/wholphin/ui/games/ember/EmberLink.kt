package com.github.damontecres.wholphin.ui.games.ember

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * Ember Road with phones: everyone on the couch can join the TV's game from their phone, vote on
 * choices, type commands and play the mini-games.
 *
 * The TV announces itself as a "_benflixember._udp" service, and phones send small text messages:
 *   BFE1 <phone id> HELLO <name>   join (sent every second to stay joined)
 *   BFE1 <phone id> DO <action>    press one of the buttons the TV shows (a vote during choices)
 *   BFE1 <phone id> TYPE <words>   type a command
 *   BFE1 <phone id> BYE            leave
 * The TV answers every phone with:  BFE1 STATE <json of [PhoneView]>
 */
object EmberLinkProtocol {
    const val SERVICE_TYPE = "_benflixember._udp"
    const val MAGIC = "BFE1"
    const val QUIET_MS = 5000L
}

/** What a phone shows: the TV's words and buttons */
@Serializable
data class PhoneView(
    val title: String = "",
    val text: String = "",
    val actions: List<PhoneAction> = emptyList(),
    /** The buttons are a vote */
    val vote: Boolean = false,
    /** This phone's vote so far (action id) */
    val mine: String? = null,
    /** Typing works right now */
    val typing: Boolean = false,
    /** Result of this phone's last typed command */
    val reply: String? = null,
    val status: String = "",
    /** A plain-words line saying what the buttons do right now (a vote, or straight away) */
    val hint: String = "",
    /** What's in the bag and who's in the party, for the phone's "Bag & journal" */
    val bag: String = "",
    /** Party games: "text", "number" or "draw" when the phone should show that kind of input */
    val input: String = "",
    /** Party games: a picture to show on the phone (a poster) */
    val image: String = "",
    /** Party games: a secret only this phone sees (the movie to draw) */
    val secret: String = "",
    /** Party games: changes whenever a new question starts, so the phone clears its input */
    val round: Int = 0,
)

@Serializable
data class PhoneAction(
    val id: String,
    val label: String,
    /** A short line under the button saying what it does */
    val hint: String = "",
)

/** A phone that joined the TV's game */
data class Joined(
    val id: String,
    val name: String,
)

/** The TV side: runs while Ember Road is open on a TV */
class EmberLinkHost(
    context: Context,
    private val onJoin: (Joined) -> Unit,
    private val onLeave: (Joined) -> Unit,
    private val onDo: (Joined, String) -> Unit,
    private val onType: (Joined, String) -> Unit,
    /** Benflix party games use the same link with their own name, so phones only find party TVs */
    private val serviceType: String = EmberLinkProtocol.SERVICE_TYPE,
    private val label: String = "Ember Road",
    /** Party "Draw the movie": a phone sends the lines it draws */
    private val onDraw: (Joined, String) -> Unit = { _, _ -> },
    private val maxArg: Int = 200,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var socket: DatagramSocket? = null

    @Volatile private var running = false

    private class Phone(
        val joined: Joined,
        var address: SocketAddress,
        var lastSeen: Long,
    )

    private val phones = mutableMapOf<String, Phone>()

    /** Builds what each phone should show right now */
    @Volatile var viewFor: (Joined) -> PhoneView = { PhoneView() }

    private val registration =
        object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Timber.i("Ember Road phones can find this TV as ${info.serviceName}")

            override fun onRegistrationFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) = Timber.w("Couldn't announce Ember Road for phones ($errorCode)")

            override fun onServiceUnregistered(info: NsdServiceInfo) {}

            override fun onUnregistrationFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) {}
        }

    fun start() {
        if (running) return
        try {
            val s =
                DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 500
                    bind(InetSocketAddress(0))
                }
            socket = s
            running = true
            val info =
                NsdServiceInfo().apply {
                    serviceName = "$label on ${Build.MODEL}".take(60)
                    serviceType = this@EmberLinkHost.serviceType
                    port = s.localPort
                }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
            Thread({ loop(s) }, "ember-link").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Timber.w(e, "Ember Road phones not available")
        }
    }

    fun stop() {
        if (!running) return
        running = false
        synchronized(phones) {
            phones.values.forEach { send(it, "${EmberLinkProtocol.MAGIC} BYE") }
            phones.clear()
        }
        try {
            nsd.unregisterService(registration)
        } catch (_: Exception) {
        }
        socket?.close()
        socket = null
    }

    /** Sends every phone its view now (call after anything changes) */
    fun push() {
        if (!running) return
        Thread {
            synchronized(phones) { phones.values.toList() }.forEach { p -> sendView(p) }
        }.start()
    }

    private fun sendView(p: Phone) {
        val json = EmberJson.encodeToString(PhoneView.serializer(), viewFor(p.joined))
        send(p, "${EmberLinkProtocol.MAGIC} STATE $json")
    }

    private fun send(
        p: Phone,
        text: String,
    ) {
        val s = socket ?: return
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            s.send(DatagramPacket(bytes, bytes.size, p.address))
        } catch (_: Exception) {
        }
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(8192)
        var lastPush = 0L
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)
                handle(packet, String(packet.data, 0, packet.length, Charsets.UTF_8).trim())
            } catch (_: SocketTimeoutException) {
            } catch (e: Exception) {
                if (running) Timber.w(e, "Ember Road phone message failed")
            }
            val now = System.currentTimeMillis()
            val gone =
                synchronized(phones) {
                    val g = phones.values.filter { now - it.lastSeen > EmberLinkProtocol.QUIET_MS }
                    g.forEach { phones.remove(it.joined.id) }
                    g
                }
            gone.forEach { onLeave(it.joined) }
            // A refresh every 2 seconds, in case a message got lost
            if (now - lastPush > 2000) {
                lastPush = now
                synchronized(phones) { phones.values.toList() }.forEach { sendView(it) }
            }
        }
    }

    private fun handle(
        packet: DatagramPacket,
        text: String,
    ) {
        val parts = text.split(' ', limit = 4)
        if (parts.size < 3 || parts[0] != EmberLinkProtocol.MAGIC) return
        val id = parts[1].take(40)
        val arg = parts.getOrNull(3).orEmpty().take(maxArg)
        val now = System.currentTimeMillis()
        var isNew = false
        val phone =
            synchronized(phones) {
                var p = phones[id]
                if (p == null) {
                    if (parts[2] == "BYE" || phones.size >= 8) return
                    val name = (if (parts[2] == "HELLO") arg else "").trim().ifBlank { "Phone ${phones.size + 1}" }.take(20)
                    p = Phone(Joined(id, name), packet.socketAddress, now)
                    phones[id] = p
                    isNew = true
                }
                p.address = packet.socketAddress
                p.lastSeen = now
                p
            }
        if (isNew) onJoin(phone.joined)
        when (parts[2]) {
            "HELLO" -> {
                sendView(phone)
            }

            "DO" -> {
                onDo(phone.joined, arg)
            }

            "TYPE" -> {
                onType(phone.joined, arg)
            }

            "DRAW" -> {
                onDraw(phone.joined, arg)
            }

            "BYE" -> {
                synchronized(phones) { phones.remove(id) }
                onLeave(phone.joined)
            }
        }
    }
}

/** A TV that's running Ember Road, found on the home Wi-Fi */
data class FoundEmberTv(
    val name: String,
    val host: InetAddress,
    val port: Int,
)

/** The phone side: finds the TV, joins, and passes on button presses and typing */
class EmberLinkClient(
    context: Context,
    private val phoneName: String,
    private val onFound: (List<FoundEmberTv>) -> Unit,
    private val onView: (PhoneView?) -> Unit,
    private val serviceType: String = EmberLinkProtocol.SERVICE_TYPE,
    private val label: String = "Ember Road",
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val id = UUID.randomUUID().toString().take(8)
    private val found = mutableListOf<FoundEmberTv>()
    private var socket: DatagramSocket? = null

    @Volatile private var tv: FoundEmberTv? = null

    @Volatile private var running = false

    @Volatile private var lastHeard = 0L
    private var discovering = false

    private val discovery =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) = Timber.w("Couldn't look for Ember Road TVs ($errorCode)")

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) {}

            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)

            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(found) {
                    found.removeAll { it.name == info.serviceName.removePrefix("$label on ") }
                    onFound(found.toList())
                }
            }
        }

    fun start() {
        if (running) return
        running = true
        try {
            socket =
                DatagramSocket().apply { soTimeout = 500 }
            Thread({ receiveLoop() }, "ember-phone-rx").apply {
                isDaemon = true
                start()
            }
            Thread({ helloLoop() }, "ember-phone-hello").apply {
                isDaemon = true
                start()
            }
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery)
            discovering = true
        } catch (e: Exception) {
            Timber.w(e, "Ember Road phone link failed")
        }
    }

    fun stop() {
        if (!running) return
        send("BYE")
        running = false
        if (discovering) {
            try {
                nsd.stopServiceDiscovery(discovery)
            } catch (_: Exception) {
            }
            discovering = false
        }
        Thread {
            Thread.sleep(200)
            socket?.close()
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        try {
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(
                        info: NsdServiceInfo,
                        errorCode: Int,
                    ) = Timber.i("Couldn't reach ${info.serviceName} ($errorCode)")

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host ?: return
                        val t = FoundEmberTv(info.serviceName.removePrefix("$label on "), host, info.port)
                        synchronized(found) {
                            found.removeAll { it.name == t.name }
                            found.add(t)
                            onFound(found.toList())
                        }
                        // Only one TV? Join it straight away.
                        if (tv == null && synchronized(found) { found.size } == 1) join(t)
                    }
                },
            )
        } catch (e: Exception) {
            Timber.w(e, "Resolving an Ember Road TV failed")
        }
    }

    fun join(t: FoundEmberTv) {
        tv = t
        lastHeard = System.currentTimeMillis()
        send("HELLO $phoneName")
    }

    fun leave() {
        send("BYE")
        tv = null
        onView(null)
    }

    fun press(action: String) = send("DO $action")

    fun type(words: String) = send("TYPE ${words.replace('\n', ' ').take(400)}")

    /** Party "Draw the movie": a line drawn on the phone ("x,y x,y …" out of 1000), or "clear" */
    fun draw(line: String) = send("DRAW $line")

    private fun send(text: String) {
        val t = tv ?: return
        val s = socket ?: return
        val bytes = "${EmberLinkProtocol.MAGIC} $id $text".toByteArray(Charsets.UTF_8)
        Thread {
            try {
                s.send(DatagramPacket(bytes, bytes.size, t.host, t.port))
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun helloLoop() {
        while (running) {
            if (tv != null) send("HELLO $phoneName")
            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun receiveLoop() {
        val buf = ByteArray(65000)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket?.receive(packet) ?: return
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val parts = text.split(' ', limit = 3)
                if (parts.size >= 2 && parts[0] == EmberLinkProtocol.MAGIC) {
                    when (parts[1]) {
                        "STATE" -> {
                            lastHeard = System.currentTimeMillis()
                            val view =
                                runCatching {
                                    EmberJson.decodeFromString(
                                        PhoneView.serializer(),
                                        parts.getOrNull(2).orEmpty(),
                                    )
                                }.getOrNull()
                            if (view != null) onView(view)
                        }

                        "BYE" -> {
                            tv = null
                            onView(null)
                        }
                    }
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running) return
            }
            if (tv != null && System.currentTimeMillis() - lastHeard > 8000) {
                tv = null
                onView(null)
            }
        }
    }
}
