package com.github.damontecres.wholphin.games

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * Turns this phone into a game controller for a game playing on a TV in the house. Finds TVs that are
 * playing a game (on the same Wi-Fi), then shows the on-screen buttons for that console and sends every
 * press to the TV.
 */
class PhonePadActivity : ComponentActivity() {
    private class FoundTv(
        val name: String,
        val game: String,
        val host: InetAddress,
        val port: Int,
    )

    private val found = mutableStateListOf<FoundTv>()
    private var connected by mutableStateOf<FoundTv?>(null)
    private var layout by mutableStateOf(TouchLayout.SNES)
    private var player by mutableIntStateOf(0)
    private var lost by mutableStateOf(false)

    @Volatile private var mask = 0

    @Volatile private var running = true

    @Volatile private var lastWelcome = 0L
    private val id = UUID.randomUUID().toString().take(8)

    /** Shown on the TV when this phone joins, e.g. "Mom's Pixel" */
    private val phoneName by lazy {
        (Settings.Global.getString(contentResolver, "device_name") ?: Build.MODEL).replace('\n', ' ').take(40)
    }
    private var socket: DatagramSocket? = null
    private lateinit var nsd: NsdManager
    private var discovering = false

    private val discovery =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) = Timber.w("Phone controller search failed ($errorCode)")

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                resolve(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                runOnUiThread { found.removeAll { it.name == info.serviceName } }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        socket = DatagramSocket().apply { soTimeout = 300 }
        Thread({ sendLoop() }, "pad-send").start()
        Thread({ receiveLoop() }, "pad-receive").start()
        setContent { Screen() }
    }

    override fun onStart() {
        super.onStart()
        if (!discovering) {
            try {
                nsd.discoverServices(PhonePadProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
                discovering = true
            } catch (e: Exception) {
                Timber.w(e, "Couldn't search for TVs")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (discovering) {
            try {
                nsd.stopServiceDiscovery(discovery)
            } catch (_: Exception) {
            }
            discovering = false
        }
        mask = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        send("BYE")
        running = false
        socket?.close()
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
                        val game = info.attributes["game"]?.toString(Charsets.UTF_8).orEmpty()
                        val tv = FoundTv(info.serviceName.removePrefix("Benflix "), game, host, info.port)
                        runOnUiThread {
                            found.removeAll { it.name == tv.name }
                            found.add(tv)
                            // Only one TV playing? Connect straight away.
                            if ((connected == null || lost) && found.size == 1) connect(tv)
                        }
                    }
                },
            )
        } catch (e: Exception) {
            Timber.w(e, "Resolving a TV failed")
        }
    }

    private fun connect(tv: FoundTv) {
        connected = tv
        lost = false
        lastWelcome = System.currentTimeMillis()
        send("HELLO $phoneName")
    }

    private fun send(text: String) {
        val tv = connected ?: return
        val s = socket ?: return
        val bytes = "${PhonePadProtocol.MAGIC} $id $text".toByteArray()
        Thread {
            try {
                s.send(DatagramPacket(bytes, bytes.size, tv.host, tv.port))
            } catch (_: Exception) {
            }
        }.start()
    }

    /** Buttons go out 20 times a second, and a hello once a second to check the TV is still there */
    private fun sendLoop() {
        var tick = 0
        while (running) {
            val tv = connected
            val s = socket
            if (tv != null && s != null) {
                try {
                    val b = "${PhonePadProtocol.MAGIC} $id B $mask".toByteArray()
                    s.send(DatagramPacket(b, b.size, tv.host, tv.port))
                    if (tick % 20 == 0) {
                        val h = "${PhonePadProtocol.MAGIC} $id HELLO $phoneName".toByteArray()
                        s.send(DatagramPacket(h, h.size, tv.host, tv.port))
                    }
                } catch (_: Exception) {
                }
                if (System.currentTimeMillis() - lastWelcome > 4000 && !lost) runOnUiThread { lost = true }
            }
            tick++
            Thread.sleep(50)
        }
    }

    private fun receiveLoop() {
        val buf = ByteArray(512)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket?.receive(packet) ?: return
                val parts = String(packet.data, 0, packet.length, Charsets.UTF_8).split(' ', limit = 5)
                if (parts.size >= 4 && parts[0] == PhonePadProtocol.MAGIC && parts[1] == "WELCOME") {
                    lastWelcome = System.currentTimeMillis()
                    val p = parts[2].toIntOrNull() ?: 0
                    val console = LocalConsole.ALL.firstOrNull { it.key == parts[3] }
                    runOnUiThread {
                        player = p
                        console?.let { layout = it.touch }
                        lost = false
                    }
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running) return
            }
        }
    }

    @Composable
    private fun Screen() {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0B0C)),
        ) {
            val tv = connected
            if (tv != null && !lost) {
                Text(
                    text = "Player ${player + 1} · ${tv.game.ifBlank { tv.name }}",
                    color = Color.White.copy(alpha = .7f),
                    fontSize = 14.sp,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp),
                )
                TouchPad(
                    touch = layout,
                    onButtons = { mask = it },
                    onMenu = { send("MENU") },
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 460.dp)
                            .padding(24.dp),
                ) {
                    Text(
                        text = if (lost) "The game on the TV ended" else "Looking for a TV playing a game…",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Start a game on the TV in Benflix. This phone needs to be on the same Wi-Fi.",
                        color = Color.White.copy(alpha = .75f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    found.forEach { f ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .background(Color(0xFF2A2A2A), RoundedCornerShape(24.dp))
                                    .border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(24.dp))
                                    .clickable { connect(f) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "${f.name}${if (f.game.isNotBlank()) " · ${f.game}" else ""}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .clickable { finish() }
                                .padding(12.dp),
                    ) {
                        Text("Back to Benflix", color = Color.White.copy(alpha = .8f), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
