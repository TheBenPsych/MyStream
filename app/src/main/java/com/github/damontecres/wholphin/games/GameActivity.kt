package com.github.damontecres.wholphin.games

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.damontecres.wholphin.ui.touch.isTvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/**
 * The Benflix game player. Runs in its own process (":game"), so an emulator can never crash Benflix
 * itself, and each game starts fresh.
 *
 * Back (or Start + Select, or the Xbox / PlayStation button) opens the menu. Quitting saves your exact
 * spot and the game's own save, and sends both to Tower.
 */
class GameActivity : ComponentActivity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var console: LocalConsole
    private lateinit var saveDir: File
    private var gameId = 0
    private var gameName = ""
    private var address = ""
    private var token = ""
    private var isTv = true

    // Game state, used on the GL thread
    @Volatile private var loaded = false

    @Volatile private var paused = false

    @Volatile private var quitting = false
    private var fps = 60.0
    private var displayHz = 60.0
    private var oneToOne = true
    private var lastNs = 0L
    private var owed = 0.0
    private var audio: AudioTrack? = null
    private val audioBuf = ShortArray(48000 * 2)
    private var retryResume = false

    // What the menu and messages show
    private var menuOpen by mutableStateOf(false)
    private var message by mutableStateOf<String?>("Starting…")
    private var failed by mutableStateOf(false)
    private var gamepadSeen by mutableStateOf(false)
    private var hasSpot by mutableStateOf(false)

    // The button key and a show next to the game (see ButtonKey.kt and SideShow.kt)
    private var gameShape by mutableFloatStateOf(4f / 3f)
    private var sideKey by mutableStateOf(true)
    private var sideShow: SideShow? = null
    private var canSideShow = false
    private var showOn by mutableStateOf(false)
    private var showBig by mutableStateOf(false)
    private var showShape by mutableFloatStateOf(16f / 9f)
    private var showTitle by mutableStateOf("")

    /** 0 = show and game, 1 = show only, 2 = game only */
    private var soundMode by mutableIntStateOf(0)
    private val showView by lazy { TextureView(this) }
    private val reportProgress =
        object : Runnable {
            override fun run() {
                sideShow?.reportProgress()
                handler.postDelayed(this, 30_000)
            }
        }

    // Buttons: from keys, from sticks/triggers and (player 1) from the touch buttons
    private val keyMask = IntArray(4)
    private val handler = Handler(Looper.getMainLooper())

    /** TV remote Back works as the B button; holding it for a moment opens the menu */
    private var backHeld = false
    private var backOpenedMenu = false
    private val backHoldOpensMenu =
        Runnable {
            if (backHeld) {
                backOpenedMenu = true
                keyMask[0] = keyMask[0] and RetroButton.bit(RetroButton.B).inv()
                push(0)
                openMenu()
            }
        }
    private val axisMask = IntArray(4)
    private var touchMask = 0

    // Phones used as controllers over Wi-Fi (see PhonePad.kt)
    private val phoneMask = IntArray(4)
    private var padServer: PhonePadServer? = null
    private val ports = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTv = isTvDevice(this)
        if (!isTv) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val core = intent.getStringExtra(EXTRA_CORE).orEmpty()
        val rom = intent.getStringExtra(EXTRA_ROM).orEmpty()
        val system = intent.getStringExtra(EXTRA_SYSTEM).orEmpty()
        saveDir = File(intent.getStringExtra(EXTRA_SAVES).orEmpty())
        gameId = intent.getIntExtra(EXTRA_GAME_ID, 0)
        gameName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val resume = intent.getBooleanExtra(EXTRA_RESUME, false)
        console = LocalConsole.ALL.firstOrNull { it.key == intent.getStringExtra(EXTRA_CONSOLE) } ?: LocalConsole.ALL.first()
        hasSpot = GameSaves.file(saveDir, "state").exists()
        displayHz = screenRefreshRate()
        gamepadSeen = hasGamepad()
        sideKey = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("side_key", true)
        val power = DevicePower.get(this)
        canSideShow =
            when (console.power) {
                PowerNeed.LIGHT -> power.ramGb >= 1.5
                PowerNeed.MEDIUM -> power.score >= 2.4 && power.ramGb >= 2.5
                PowerNeed.HEAVY -> false
            }
        sideShow =
            SideShow(
                context = this,
                server = intent.getStringExtra(EXTRA_SERVER).orEmpty(),
                userId = intent.getStringExtra(EXTRA_USER).orEmpty(),
                token = token,
            ).takeIf { it.available }
        if (isTv) {
            padServer =
                PhonePadServer(
                    context = this,
                    consoleKey = console.key,
                    gameName = gameName,
                    onButtons = { player, mask -> onPhoneButtons(player, mask) },
                    onMenu = { runOnUiThread { if (menuOpen) closeMenu() else openMenu() } },
                ).also { it.start() }
        }

        glView =
            GLSurfaceView(this).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                setRenderer(
                    object : GLSurfaceView.Renderer {
                        override fun onSurfaceCreated(
                            gl: GL10?,
                            config: EGLConfig?,
                        ) {
                            if (!loaded && !failed) startGame(core, rom, system, resume)
                            RetroNative.glInit()
                        }

                        override fun onSurfaceChanged(
                            gl: GL10?,
                            width: Int,
                            height: Int,
                        ) {
                            RetroNative.glResize(width, height)
                        }

                        override fun onDrawFrame(gl: GL10?) {
                            drawFrame()
                        }
                    },
                )
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }

        setContent { Screen() }
    }

    @Suppress("DEPRECATION")
    private fun screenRefreshRate(): Double {
        val rate = if (Build.VERSION.SDK_INT >= 30) display?.refreshRate else windowManager.defaultDisplay.refreshRate
        return rate?.toDouble()?.takeIf { it > 1 } ?: 60.0
    }

    // ---------- Running the game (GL thread) ----------

    private fun startGame(
        core: String,
        rom: String,
        system: String,
        resume: Boolean,
    ) {
        console.options.forEach { (k, v) -> RetroNative.setOption(k, v) }
        val coreSaves = File(saveDir, "core").apply { mkdirs() }
        val ok =
            try {
                RetroNative.load(core, rom, system, coreSaves.absolutePath)
            } catch (e: Throwable) {
                Timber.w(e, "Starting the emulator failed")
                false
            }
        if (!ok) {
            runOnUiThread {
                failed = true
                message =
                    "This game couldn't start. The file may be damaged, or this console needs a BIOS file " +
                    "in Games/bios on Tower."
            }
            return
        }
        GameSaves.file(saveDir, "srm").takeIf { it.exists() }?.let { RetroNative.setSaveRam(it.readBytes()) }
        if (resume) {
            val state = GameSaves.file(saveDir, "state")
            if (state.exists() && !RetroNative.loadState(state.readBytes())) retryResume = true
        }
        val timing = RetroNative.timing()
        fps = timing[0]
        val shape = timing.getOrElse(2) { 4.0 / 3.0 }.toFloat().takeIf { it in 0.5f..3f } ?: (4f / 3f)
        runOnUiThread { gameShape = shape }
        oneToOne = abs(displayHz - fps) < 1.0
        startAudio(timing[1].toInt())
        loaded = true
        runOnUiThread { message = null }
    }

    private fun startAudio(rate: Int) {
        try {
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            audio =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    ).setBufferSizeInBytes(maxOf(min * 2, rate * 4 / 8))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                    .also {
                        // Games run in step with the screen, so nudge the sound speed to match it
                        if (oneToOne) it.setPlaybackRate((rate * displayHz / fps).toInt().coerceIn(rate * 9 / 10, rate * 11 / 10))
                        if (!paused) it.play()
                    }
        } catch (e: Exception) {
            Timber.w(e, "No game sound")
        }
    }

    private fun drawFrame() {
        if (!loaded || paused || quitting) {
            lastNs = 0L
            RetroNative.glDraw()
            return
        }
        val frames =
            if (oneToOne) {
                1
            } else {
                val now = System.nanoTime()
                if (lastNs == 0L) {
                    lastNs = now
                    1
                } else {
                    owed += (now - lastNs) / 1e9 * fps
                    lastNs = now
                    val n = owed.toInt().coerceAtMost(3)
                    owed = if (owed > 4) 0.0 else owed - n
                    n
                }
            }
        RetroNative.run(frames)
        if (retryResume) {
            retryResume = false
            GameSaves.file(saveDir, "state").takeIf { it.exists() }?.let { RetroNative.loadState(it.readBytes()) }
        }
        val n = RetroNative.drainAudio(audioBuf)
        if (n > 0) audio?.write(audioBuf, 0, n, AudioTrack.WRITE_NON_BLOCKING)
        RetroNative.glDraw()
    }

    /** Runs [block] on the game's thread and waits for it (up to a few seconds) */
    private fun <T> onGameThread(block: () -> T): T? {
        var result: T? = null
        val done = CountDownLatch(1)
        glView.queueEvent {
            try {
                result = block()
            } catch (e: Throwable) {
                Timber.w(e, "Game thread task failed")
            } finally {
                done.countDown()
            }
        }
        done.await(4, TimeUnit.SECONDS)
        return result
    }

    private class Saved(
        val srm: ByteArray?,
        val state: ByteArray?,
        val picture: IntArray?,
    )

    /** Saves the game's own save, your exact spot and a picture of it on this device. Returns false if it couldn't. */
    private fun saveHere(withSpot: Boolean): Boolean {
        if (!loaded) return false
        val saved =
            onGameThread {
                Saved(
                    RetroNative.getSaveRam(),
                    if (withSpot) RetroNative.saveState() else null,
                    if (withSpot) RetroNative.snapshot() else null,
                )
            } ?: return false
        val changed = mutableListOf<String>()
        try {
            saved.srm?.let { bytes ->
                val f = GameSaves.file(saveDir, "srm")
                if (!f.exists() || !f.readBytes().contentEquals(bytes)) {
                    writeAtomic(f, bytes)
                    changed.add("srm")
                }
            }
            saved.state?.let {
                writeAtomic(GameSaves.file(saveDir, "state"), it)
                changed.add("state")
                runOnUiThread { hasSpot = true }
            }
            saved.picture?.let { px ->
                val w = px[0]
                val h = px[1]
                if (w > 0 && h > 0) {
                    val bmp = Bitmap.createBitmap(px, 2, w, w, h, Bitmap.Config.ARGB_8888)
                    val tmp = File(saveDir, "save.png.tmp")
                    tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    tmp.renameTo(GameSaves.file(saveDir, "png"))
                    changed.add("png")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Saving failed")
            return false
        }
        if (changed.isNotEmpty()) GameSaves.markPending(saveDir, changed)
        return true
    }

    private fun writeAtomic(
        f: File,
        bytes: ByteArray,
    ) {
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    private fun sendToTower() {
        Thread { GameSaves.upload(saveDir, gameId, address, token) }.start()
    }

    private fun quit() {
        if (quitting) return
        quitting = true
        menuOpen = false
        message = "Saving your spot…"
        audio?.pause()
        stopShow()
        Thread {
            saveHere(withSpot = true)
            val sent = GameSaves.upload(saveDir, gameId, address, token)
            if (!sent) Timber.i("Saves will be sent to Tower next time")
            runOnUiThread { finish() }
        }.start()
    }

    private fun setPaused(value: Boolean) {
        paused = value
        if (value) audio?.pause() else audio?.play()
    }

    private fun openMenu() {
        if (failed || quitting) return
        menuOpen = true
        setPaused(true)
    }

    private fun closeMenu() {
        menuOpen = false
        setPaused(false)
    }

    private fun startShow(item: SideShowItem) {
        val show = sideShow ?: return
        showTitle = item.title
        showOn = true
        showBig = false
        show.play(
            item = item,
            view = showView,
            onShape = { showShape = it.coerceIn(1f, 2.6f) },
            onEnded = { runOnUiThread { stopShow() } },
        )
        applySound()
        handler.removeCallbacks(reportProgress)
        handler.postDelayed(reportProgress, 30_000)
    }

    private fun stopShow() {
        sideShow?.stop()
        handler.removeCallbacks(reportProgress)
        showOn = false
        showBig = false
        applySound()
    }

    private fun applySound() {
        val game =
            when {
                !showOn -> 1f
                soundMode == 1 -> 0f
                soundMode == 0 -> 0.5f
                else -> 1f
            }
        audio?.setVolume(game)
        sideShow?.volume = if (soundMode == 2) 0f else 1f
    }

    private fun saveSideKey(on: Boolean) {
        sideKey = on
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("side_key", on).apply()
    }

    /** For remotes without enough buttons: the menu can tap Start or Select for you */
    private fun tapFromMenu(button: Int) {
        closeMenu()
        val press =
            Runnable {
                keyMask[0] = keyMask[0] or RetroButton.bit(button)
                push(0)
            }
        val release =
            Runnable {
                keyMask[0] = keyMask[0] and RetroButton.bit(button).inv()
                push(0)
            }
        handler.postDelayed(press, 150)
        handler.postDelayed(release, 350)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        if (!menuOpen) setPaused(false)
        if (showOn) sideShow?.resume()
    }

    override fun onPause() {
        // Save on this device first (the game thread stops when the view pauses)
        if (loaded && !quitting) saveHere(withSpot = true)
        setPaused(true)
        sideShow?.pause()
        glView.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!quitting) sendToTower()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        sideShow?.stop()
        audio?.release()
        padServer?.stop()
        audio = null
        if (isFinishing) {
            // Emulators keep their own memory around; a fresh process per game keeps things clean
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    // ---------- Buttons ----------

    private fun hasGamepad(): Boolean =
        InputDevice.getDeviceIds().any { id ->
            val d = InputDevice.getDevice(id) ?: return@any false
            !d.isVirtual &&
                (
                    d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                        d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                )
        }

    private fun portFor(deviceId: Int): Int = ports.getOrPut(deviceId) { ports.size.coerceAtMost(3) }

    private fun push(port: Int) {
        val touch = if (port == 0) touchMask else 0
        RetroNative.setButtons(port, keyMask[port] or axisMask[port] or touch or phoneMask[port])
    }

    /** A phone controller's buttons. While the menu is open, they move around the menu instead. */
    private fun onPhoneButtons(
        player: Int,
        mask: Int,
    ) {
        val pressed = mask and phoneMask[player].inv()
        phoneMask[player] = mask
        if (!menuOpen) {
            push(player)
            return
        }
        push(player)
        val key =
            when {
                pressed and RetroButton.bit(RetroButton.UP) != 0 -> KeyEvent.KEYCODE_DPAD_UP
                pressed and RetroButton.bit(RetroButton.DOWN) != 0 -> KeyEvent.KEYCODE_DPAD_DOWN
                pressed and (RetroButton.bit(RetroButton.A) or RetroButton.bit(RetroButton.B)) != 0 -> KeyEvent.KEYCODE_DPAD_CENTER
                else -> return
            }
        runOnUiThread {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        }
    }

    private fun isGamepad(event: KeyEvent): Boolean =
        event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            KeyEvent.isGamepadButton(event.keyCode)

    /** Which libretro button a key is, or null */
    private fun buttonFor(
        keyCode: Int,
        gamepad: Boolean,
    ): Int? =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                RetroButton.UP
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                RetroButton.DOWN
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                RetroButton.LEFT
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                RetroButton.RIGHT
            }

            // Xbox-style layout: bottom A, right B, left X, top Y. Games get the button in the same place.
            KeyEvent.KEYCODE_BUTTON_A -> {
                RetroButton.B
            }

            KeyEvent.KEYCODE_BUTTON_B -> {
                RetroButton.A
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                RetroButton.Y
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                RetroButton.X
            }

            KeyEvent.KEYCODE_BUTTON_L1 -> {
                RetroButton.L
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                RetroButton.R
            }

            KeyEvent.KEYCODE_BUTTON_L2 -> {
                RetroButton.L2
            }

            KeyEvent.KEYCODE_BUTTON_R2 -> {
                RetroButton.R2
            }

            KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                RetroButton.L3
            }

            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                RetroButton.R3
            }

            KeyEvent.KEYCODE_BUTTON_START -> {
                RetroButton.START
            }

            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                RetroButton.SELECT
            }

            // TV remote: OK is A, Play/Pause is Start
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (gamepad) RetroButton.START else RetroButton.A
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                RetroButton.START
            }

            // Extra remote buttons, when the remote has them (Fire TV has menu, rewind and fast-forward)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_7 -> {
                RetroButton.SELECT
            }

            KeyEvent.KEYCODE_9 -> {
                RetroButton.START
            }

            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_1 -> {
                RetroButton.Y
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_2 -> {
                RetroButton.X
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_3 -> {
                RetroButton.L
            }

            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_4 -> {
                RetroButton.R
            }

            else -> {
                null
            }
        }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (menuOpen || failed) {
            if (code == KeyEvent.KEYCODE_BACK && backOpenedMenu) {
                // The Back that was held to open the menu: don't close it again when let go
                if (!down) {
                    backOpenedMenu = false
                    backHeld = false
                }
                return true
            }
            if ((code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BUTTON_MODE) && !down) {
                if (failed) finish() else closeMenu()
                return true
            }
            if (code == KeyEvent.KEYCODE_BUTTON_A && failed) {
                if (!down) finish()
                return true
            }
            // Let the menu use the controller like a remote
            if (code == KeyEvent.KEYCODE_BUTTON_A) {
                return super.dispatchKeyEvent(KeyEvent(event.action, KeyEvent.KEYCODE_DPAD_CENTER))
            }
            return super.dispatchKeyEvent(event)
        }
        if (quitting) return true
        if (code == KeyEvent.KEYCODE_BACK && isTv && !isGamepad(event)) {
            remoteBack(down, event.repeatCount)
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (!down) openMenu()
            return true
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event)
        }
        val gamepad = isGamepad(event)
        if (gamepad && !gamepadSeen) gamepadSeen = true
        val button = buttonFor(code, gamepad) ?: return super.dispatchKeyEvent(event)
        val port = portFor(event.deviceId)
        keyMask[port] =
            if (down) keyMask[port] or RetroButton.bit(button) else keyMask[port] and RetroButton.bit(button).inv()
        push(port)
        // Start + Select together opens the menu, like on the old streamed games
        val both = RetroButton.bit(RetroButton.START) or RetroButton.bit(RetroButton.SELECT)
        if (down && keyMask[port] and both == both) {
            keyMask[port] = 0
            push(port)
            openMenu()
        }
        return true
    }

    /** Remote Back = B straight away (so it's quick in games), held for 0.8 seconds = the menu */
    private fun remoteBack(
        down: Boolean,
        repeat: Int,
    ) {
        if (down) {
            if (repeat > 0 || backHeld) return
            backHeld = true
            backOpenedMenu = false
            keyMask[0] = keyMask[0] or RetroButton.bit(RetroButton.B)
            push(0)
            handler.postDelayed(backHoldOpensMenu, 800)
        } else {
            backHeld = false
            handler.removeCallbacks(backHoldOpensMenu)
            if (!backOpenedMenu) {
                keyMask[0] = keyMask[0] and RetroButton.bit(RetroButton.B).inv()
                push(0)
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!joystick || event.action != MotionEvent.ACTION_MOVE || menuOpen) return super.dispatchGenericMotionEvent(event)
        if (!gamepadSeen) gamepadSeen = true
        val port = portFor(event.deviceId)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        var mask = 0
        if (hatX < -0.5f || x < -0.5f) mask = mask or RetroButton.bit(RetroButton.LEFT)
        if (hatX > 0.5f || x > 0.5f) mask = mask or RetroButton.bit(RetroButton.RIGHT)
        if (hatY < -0.5f || y < -0.5f) mask = mask or RetroButton.bit(RetroButton.UP)
        if (hatY > 0.5f || y > 0.5f) mask = mask or RetroButton.bit(RetroButton.DOWN)
        if (lt > 0.5f) mask = mask or RetroButton.bit(RetroButton.L2)
        if (rt > 0.5f) mask = mask or RetroButton.bit(RetroButton.R2)
        axisMask[port] = mask
        push(port)
        RetroNative.setStick(port, 0, (x * 32767).toInt(), (y * 32767).toInt())
        RetroNative.setStick(
            port,
            1,
            (event.getAxisValue(MotionEvent.AXIS_Z) * 32767).toInt(),
            (
                event.getAxisValue(MotionEvent.AXIS_RZ) *
                    32767
            ).toInt(),
        )
        return true
    }

    // ---------- Screen ----------

    @Composable
    private fun Screen() {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    // A tap brings the touch buttons back after a controller was used
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            if (gamepadSeen && !isTv) gamepadSeen = false
                        }
                    }
                },
        ) {
            SideBySide()
            if (!isTv && !gamepadSeen && !menuOpen && message == null) {
                TouchPad(
                    touch = console.touch,
                    onButtons = {
                        touchMask = it
                        push(0)
                    },
                    onMenu = { openMenu() },
                )
            }
            message?.let { text ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                ) {
                    Text(text, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                    if (failed) {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                        MenuButton("Back to Benflix", Modifier.focusRequester(focus)) { finish() }
                    }
                }
            }
            if (menuOpen) Menu()
        }
    }

    /** Where the game, the show and the button key go on the screen */
    private class Spots(
        val game: Rect,
        val show: Rect?,
        val key: Rect?,
    )

    private class Rect(
        val x: Dp,
        val y: Dp,
        val w: Dp,
        val h: Dp,
    )

    /** Fits something of [shape] (width / height) inside w x h, centred at x, y */
    private fun fit(
        x: Dp,
        y: Dp,
        w: Dp,
        h: Dp,
        shape: Float,
    ): Rect {
        val fw = min(w, h * shape)
        val fh = fw / shape
        return Rect(x + (w - fw) / 2, y + (h - fh) / 2, fw, fh)
    }

    private fun spots(
        w: Dp,
        h: Dp,
    ): Spots {
        val pad = 12.dp
        val keyWanted = sideKey && message == null && (isTv || gamepadSeen)
        return when {
            showOn && showBig -> {
                // Big show on the left; the game (and key) small on the right
                val panel = w * 0.3f
                val main = w - panel
                val gw = panel - pad * 2
                val game = fit(main + pad, pad, gw, min(h * 0.45f, gw / gameShape), gameShape)
                val keyTop = game.y + game.h + pad
                Spots(
                    game = game,
                    show = fit(0.dp, 0.dp, main, h, showShape),
                    key = if (keyWanted) Rect(main + pad, keyTop, gw, h - keyTop - pad) else null,
                )
            }

            showOn -> {
                // The game on the left where its black bar was; the show (and key) on the right
                val panel = maxOf(w * 0.28f, w - h * gameShape)
                val main = w - panel
                val vw = panel - pad * 2
                val show = fit(main + pad, pad, vw, min(h * 0.45f, vw / showShape), showShape)
                val keyTop = show.y + show.h + pad
                Spots(
                    game = Rect(0.dp, 0.dp, main, h),
                    show = show,
                    key = if (keyWanted) Rect(main + pad, keyTop, vw, h - keyTop - pad) else null,
                )
            }

            keyWanted -> {
                val panel = maxOf(w * 0.17f, w - h * gameShape)
                Spots(
                    game = Rect(0.dp, 0.dp, w - panel, h),
                    show = null,
                    key = Rect(w - panel + pad, pad, panel - pad * 2, h - pad * 2),
                )
            }

            else -> {
                Spots(Rect(0.dp, 0.dp, w, h), null, null)
            }
        }
    }

    @Composable
    private fun SideBySide() {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val s = spots(maxWidth, maxHeight)
            AndroidView(
                factory = { glView },
                modifier =
                    Modifier
                        .offset(s.game.x, s.game.y)
                        .size(s.game.w, s.game.h),
            )
            s.show?.let { r ->
                AndroidView(
                    factory = { showView },
                    modifier =
                        Modifier
                            .offset(r.x, r.y)
                            .size(r.w, r.h),
                )
            }
            s.key?.let { r ->
                Box(
                    contentAlignment = if (s.show == null) Alignment.CenterStart else Alignment.TopStart,
                    modifier =
                        Modifier
                            .offset(r.x, r.y)
                            .size(r.w, r.h),
                ) {
                    ButtonKey(console, if (gamepadSeen) KeyFor.CONTROLLER else KeyFor.REMOTE, compact = true)
                }
            }
        }
    }

    @Composable
    private fun Menu() {
        var showHelp by remember { mutableStateOf(false) }
        var picking by remember { mutableStateOf(false) }
        var choices by remember { mutableStateOf<List<SideShowItem>?>(null) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(showHelp, picking, choices) { runCatching { focus.requestFocus() } }
        LaunchedEffect(picking) {
            if (picking) choices = withContext(Dispatchers.IO) { sideShow?.choices().orEmpty() }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .width(if (isTv) 420.dp else 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 24.dp),
            ) {
                Text(gameName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (picking) {
                    val list = choices
                    when {
                        list == null -> {
                            Text("Loading your shows…", color = Color.White, fontSize = 17.sp)
                            MenuButton("Cancel", Modifier.focusRequester(focus)) { picking = false }
                        }

                        list.isEmpty() -> {
                            Text(
                                "Nothing in Continue watching or Next up yet. Start a show in Benflix first.",
                                color = Color.White,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center,
                            )
                            MenuButton("OK", Modifier.focusRequester(focus)) { picking = false }
                        }

                        else -> {
                            Text("Pick something to watch on the side", color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp)
                            list.forEachIndexed { i, item ->
                                val label = if (item.subtitle.isBlank()) item.title else "${item.title} · ${item.subtitle}"
                                MenuButton(label, if (i == 0) Modifier.focusRequester(focus) else Modifier) {
                                    picking = false
                                    startShow(item)
                                    closeMenu()
                                }
                            }
                            MenuButton("Cancel") { picking = false }
                        }
                    }
                } else if (showHelp) {
                    val lines =
                        console.help +
                            listOf(
                                if (isTv) "Hold Back for a second to open this menu" else "The ☰ circle at the top opens this menu",
                                "Start + Select together also opens it",
                            ) +
                            if (isTv) {
                                listOf(
                                    "Phone as a controller: on your phone open Benflix > Games > Use this phone as a controller",
                                )
                            } else {
                                emptyList()
                            }
                    MenuButton("Done", Modifier.focusRequester(focus)) { showHelp = false }
                    if (isTv) ButtonKey(console, KeyFor.REMOTE, Modifier.fillMaxWidth())
                    ButtonKey(console, KeyFor.CONTROLLER, Modifier.fillMaxWidth())
                    lines.forEach { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, textAlign = TextAlign.Center) }
                } else {
                    MenuButton("Resume", Modifier.focusRequester(focus)) { closeMenu() }
                    MenuButton("Save my spot") {
                        Thread {
                            if (saveHere(withSpot = true)) sendToTower()
                            runOnUiThread { closeMenu() }
                        }.start()
                    }
                    if (hasSpot) {
                        MenuButton("Go back to my saved spot") {
                            onGameThread {
                                GameSaves.file(saveDir, "state").takeIf { it.exists() }?.let { RetroNative.loadState(it.readBytes()) }
                            }
                            closeMenu()
                        }
                    }
                    if (isTv) {
                        MenuButton("Press Start") { tapFromMenu(RetroButton.START) }
                        MenuButton("Press Select") { tapFromMenu(RetroButton.SELECT) }
                    }
                    if (sideShow != null && canSideShow) {
                        if (!showOn) {
                            MenuButton("Watch a show on the side") {
                                choices = null
                                picking = true
                            }
                        } else {
                            MenuButton(if (showBig) "Make the game big" else "Make the show big") {
                                showBig = !showBig
                                closeMenu()
                            }
                            val sound =
                                when (soundMode) {
                                    0 -> "Sound: show and game"
                                    1 -> "Sound: show only"
                                    else -> "Sound: game only"
                                }
                            MenuButton(sound) {
                                soundMode = (soundMode + 1) % 3
                                applySound()
                            }
                            MenuButton("Stop $showTitle".take(40)) { stopShow() }
                        }
                    }
                    if (isTv || gamepadSeen) {
                        MenuButton(if (sideKey) "Hide the button key" else "Show the button key on the side") { saveSideKey(!sideKey) }
                    }
                    MenuButton("Restart game") {
                        onGameThread { RetroNative.reset() }
                        closeMenu()
                    }
                    MenuButton("Buttons") { showHelp = true }
                    MenuButton("Quit and save") { quit() }
                }
            }
        }
    }

    @Composable
    private fun MenuButton(
        text: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(28.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(if (focused) Color.White else Color(0xFF2A2A2A), shape)
                    .border(2.dp, if (focused) Color.White else Color(0xFF4A4A4A), shape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .padding(vertical = 14.dp),
        ) {
            Text(text, color = if (focused) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    companion object {
        const val EXTRA_CORE = "core"
        const val EXTRA_ROM = "rom"
        const val EXTRA_SYSTEM = "system"
        const val EXTRA_SAVES = "saves"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_CONSOLE = "console"
        const val EXTRA_RESUME = "resume"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_SERVER = "server"
        const val EXTRA_USER = "user"
        private const val PREFS = "game_player"
    }
}
