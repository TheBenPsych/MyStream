package com.github.damontecres.wholphin.games

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException

/**
 * "Phone as a controller": phones on the home Wi-Fi send their button presses to the TV.
 *
 * The TV game player announces itself on the network (as a "_benflixpad._udp" service), and each phone
 * sends small text messages over UDP:
 *   BFP1 <phone id> HELLO          -> TV answers "BFP1 WELCOME <player> <console> <game name>"
 *   BFP1 <phone id> B <mask>       buttons held (sent many times a second, so a lost message doesn't matter)
 *   BFP1 <phone id> MENU           open or close the game menu
 *   BFP1 <phone id> BYE            the phone stopped
 * Buttons are let go if a phone goes quiet for a second.
 */
object PhonePadProtocol {
    const val SERVICE_TYPE = "_benflixpad._udp"
    const val MAGIC = "BFP1"
    const val QUIET_MS = 1000L
}

/** The TV side, running inside [GameActivity] while a game is on */
class PhonePadServer(
    context: Context,
    private val consoleKey: String,
    private val gameName: String,
    private val onButtons: (player: Int, mask: Int) -> Unit,
    private val onMenu: () -> Unit,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    private class Phone(
        val player: Int,
        var address: SocketAddress,
        var lastSeen: Long,
        var mask: Int = 0,
    )

    private val phones = mutableMapOf<String, Phone>()

    private val registration =
        object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Timber.i("Phone controllers can find this TV as ${info.serviceName}")

            override fun onRegistrationFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) = Timber.w("Couldn't announce the TV for phone controllers ($errorCode)")

            override fun onServiceUnregistered(info: NsdServiceInfo) {}

            override fun onUnregistrationFailed(
                info: NsdServiceInfo,
                errorCode: Int,
            ) {}
        }

    fun start() {
        try {
            val s =
                DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 250
                    bind(InetSocketAddress(0))
                }
            socket = s
            running = true
            val info =
                NsdServiceInfo().apply {
                    serviceName = "Benflix ${Build.MODEL}".take(60)
                    serviceType = PhonePadProtocol.SERVICE_TYPE
                    port = s.localPort
                    setAttribute("game", gameName.take(60))
                    setAttribute("console", consoleKey)
                }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
            thread = Thread({ loop(s) }, "phone-pad").apply { start() }
        } catch (e: Exception) {
            Timber.w(e, "Phone controllers not available")
        }
    }

    fun stop() {
        running = false
        try {
            nsd.unregisterService(registration)
        } catch (_: Exception) {
        }
        socket?.close()
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(512)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)
                handle(s, packet, String(packet.data, 0, packet.length, Charsets.UTF_8).trim())
            } catch (_: SocketTimeoutException) {
                // Just checking for quiet phones below
            } catch (e: Exception) {
                if (running) Timber.w(e, "Phone controller message failed")
            }
            val now = System.currentTimeMillis()
            phones.values.forEach { p ->
                if (p.mask != 0 && now - p.lastSeen > PhonePadProtocol.QUIET_MS) {
                    p.mask = 0
                    onButtons(p.player, 0)
                }
            }
        }
    }

    private fun handle(
        s: DatagramSocket,
        packet: DatagramPacket,
        text: String,
    ) {
        val parts = text.split(' ')
        if (parts.size < 3 || parts[0] != PhonePadProtocol.MAGIC) return
        val id = parts[1].take(40)
        val now = System.currentTimeMillis()
        var phone = phones[id]
        if (phone == null) {
            if (parts[2] == "BYE") return
            val used = phones.values.map { it.player }.toSet()
            val player = (0..3).firstOrNull { it !in used } ?: return
            phone = Phone(player, packet.socketAddress, now)
            phones[id] = phone
        }
        phone.address = packet.socketAddress
        phone.lastSeen = now
        when (parts[2]) {
            "HELLO" -> {
                val reply = "${PhonePadProtocol.MAGIC} WELCOME ${phone.player} $consoleKey ${gameName.replace('\n', ' ')}".toByteArray()
                s.send(DatagramPacket(reply, reply.size, phone.address))
            }

            "B" -> {
                val mask = parts.getOrNull(3)?.toIntOrNull() ?: return
                if (mask != phone.mask) {
                    phone.mask = mask
                    onButtons(phone.player, mask)
                }
            }

            "MENU" -> {
                onMenu()
            }

            "BYE" -> {
                if (phone.mask != 0) onButtons(phone.player, 0)
                phones.remove(id)
            }
        }
    }
}
