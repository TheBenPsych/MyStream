package com.github.damontecres.wholphin.games

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.hardware.input.InputManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.text.Cue
import androidx.palette.graphics.Palette
import com.github.damontecres.wholphin.ui.touch.isTvDevice
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
    private var showSize by mutableStateOf(ShowSize.SIDE)

    // Living Colour: the side panel takes its colour from the game's box art
    private var panelTint by mutableStateOf(Color(0xFF2A2A2A))

    // The smart key shows whichever was pressed last: the remote or a controller
    private var lastPad by mutableStateOf(KeyFor.REMOTE)

    // Little pop-ups ("Saved", "Controller connected") and the info strip
    private var toast by mutableStateOf<String?>(null)
    private val hideToast = Runnable { toast = null }
    private var lastSaved by mutableLongStateOf(0L)
    private var playedBefore = 0L
    private var playStart = 0L
    private var lastBackTap = 0L
    private var coverUrl = ""
    private val inputListener =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                if (isPadDevice(InputDevice.getDevice(deviceId))) {
                    gamepadSeen = true
                    lastPad = KeyFor.CONTROLLER
                    showToast("Controller connected: Player ${portFor(deviceId) + 1}")
                }
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                if (ports.containsKey(deviceId)) showToast("Controller disconnected")
            }

            override fun onInputDeviceChanged(deviceId: Int) {}
        }
    private var showShape by mutableFloatStateOf(16f / 9f)
    private var showTitle by mutableStateOf("")
    private var showItem: SideShowItem? = null
    private var showCues by mutableStateOf<List<Cue>>(emptyList())

    /** While the show picker is open, Back steps back inside it */
    private var pickerBack: (() -> Unit)? = null
    private val showTick =
        object : Runnable {
            override fun run() {
                sideShow?.tick()
                handler.postDelayed(this, 1000)
            }
        }

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
        if (gamepadSeen) lastPad = KeyFor.CONTROLLER
        coverUrl = intent.getStringExtra(EXTRA_COVER).orEmpty()
        loadPanelColour()
        playedBefore = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(todayKey(), 0L)
        playStart = SystemClock.elapsedRealtime()
        (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(inputListener, handler)
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
                    onJoin = { player, name -> runOnUiThread { showToast("$name joined as Player ${player + 1}") } },
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
    private fun saveHere(
        withSpot: Boolean,
        slot: Int = 0,
    ): Boolean {
        val saved = grabSave(withSpot) ?: return false
        return writeSave(saved, slot)
    }

    /** Copies the game's save out of the emulator (quick; the file writing is done by [writeSave]) */
    private fun grabSave(withSpot: Boolean): Saved? {
        if (!loaded) return null
        return onGameThread {
            Saved(
                RetroNative.getSaveRam(),
                if (withSpot) RetroNative.saveState() else null,
                if (withSpot) RetroNative.snapshot() else null,
            )
        }
    }

    private fun writeSave(
        saved: Saved,
        slot: Int = 0,
    ): Boolean {
        val end = if (slot in 1..3) slot.toString() else ""
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
                writeAtomic(GameSaves.file(saveDir, "state$end"), it)
                changed.add("state$end")
                if (slot == 0) runOnUiThread { hasSpot = true }
            }
            saved.picture?.let { px ->
                val w = px[0]
                val h = px[1]
                if (w > 0 && h > 0) {
                    val bmp = Bitmap.createBitmap(px, 2, w, w, h, Bitmap.Config.ARGB_8888)
                    val tmp = File(saveDir, "save.png.tmp")
                    tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    tmp.renameTo(GameSaves.file(saveDir, "png$end"))
                    changed.add("png$end")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Saving failed")
            return false
        }
        if (changed.isNotEmpty()) GameSaves.markPending(saveDir, changed)
        lastSaved = System.currentTimeMillis()
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

    /** Writes saves and sends them to Tower one after another, away from the screen's thread */
    private val diskWork =
        java.util.concurrent.Executors
            .newSingleThreadExecutor()

    private fun sendToTower() {
        diskWork.execute { GameSaves.upload(saveDir, gameId, address, token) }
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
            keepPlayTime()
            val sent = GameSaves.upload(saveDir, gameId, address, token)
            if (!sent) Timber.i("Saves will be sent to Tower next time")
            // Let Jellyfin hear how far the show got before the game player closes
            sideShow?.awaitReports(4000)
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
        val first = !showOn
        showTitle = item.title
        showItem = item
        showOn = true
        if (first) showSize = ShowSize.SIDE
        show.onCues = { cues -> runOnUiThread { showCues = cues } }
        show.play(
            item = item,
            view = showView,
            onShape = { showShape = it.coerceIn(1f, 2.6f) },
            onEnded = { runOnUiThread { playNextOrStop() } },
        )
        applySound()
        handler.removeCallbacks(reportProgress)
        handler.postDelayed(reportProgress, 10_000)
        handler.removeCallbacks(showTick)
        handler.postDelayed(showTick, 1000)
    }

    /** At the end of an episode the next one starts by itself (intros get skipped) */
    private fun playNextOrStop() {
        val item = showItem
        val show = sideShow
        if (item == null || show == null || item.type != "Episode") {
            stopShow()
            return
        }
        Thread {
            val next = runCatching { show.nextEpisode(item) }.getOrNull()
            runOnUiThread {
                if (next == null || !showOn) {
                    stopShow()
                } else {
                    showToast("Up next: ${next.subtitle}".take(60))
                    startShow(next)
                }
            }
        }.start()
    }

    private fun stopShow() {
        sideShow?.stop()
        handler.removeCallbacks(reportProgress)
        handler.removeCallbacks(showTick)
        showItem = null
        showCues = emptyList()
        showOn = false
        showSize = ShowSize.SIDE
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
        sideShow?.setMuted(soundMode == 2)
    }

    private fun saveSideKey(on: Boolean) {
        sideKey = on
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("side_key", on).apply()
    }

    private fun showToast(text: String) {
        toast = text
        handler.removeCallbacks(hideToast)
        handler.postDelayed(hideToast, 3000)
    }

    /** Small show, half and half, big show, tiny corner: one step on */
    private fun nextShowSize() {
        if (!showOn) return
        showSize = ShowSize.entries[(showSize.ordinal + 1) % ShowSize.entries.size]
        showToast(showSize.label)
    }

    private fun todayKey() = "played_${java.time.LocalDate.now()}_$gameId"

    /** Minutes played today, this game */
    private fun playedTodayMs() = playedBefore + (SystemClock.elapsedRealtime() - playStart)

    private fun keepPlayTime() {
        val total = playedTodayMs()
        playedBefore = total
        playStart = SystemClock.elapsedRealtime()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(todayKey(), total).apply()
    }

    /** Takes the side panel's colour from the game's box art */
    private fun loadPanelColour() {
        if (coverUrl.isBlank()) return
        Thread {
            try {
                val bytes =
                    okhttp3.OkHttpClient
                        .Builder()
                        .callTimeout(10, TimeUnit.SECONDS)
                        .build()
                        .newCall(
                            okhttp3.Request
                                .Builder()
                                .url(coverUrl)
                                .header("X-Benflix-Token", token)
                                .build(),
                        ).execute()
                        .use { if (it.isSuccessful) it.body.bytes() else null } ?: return@Thread
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@Thread
                val palette = Palette.from(bmp).maximumColorCount(16).generate()
                val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch ?: return@Thread
                runOnUiThread { panelTint = Color(swatch.rgb) }
            } catch (e: Exception) {
                Timber.i("No box art colour: ${e.message}")
            }
        }.start()
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
        // Only the quick copy out of the emulator happens here; writing files and the picture is done in the background
        if (loaded && !quitting) grabSave(withSpot = true)?.let { saved -> diskWork.execute { writeSave(saved) } }
        keepPlayTime()
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
        (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(inputListener)
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

    private fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { isPadDevice(InputDevice.getDevice(it)) }

    private val padDevices = mutableMapOf<Int, Boolean>()

    /**
     * A real game controller, not a TV remote. Some remotes say they are "gamepads", so a device only counts
     * if it has a joystick or all four face buttons (A B X Y).
     */
    private fun isPadDevice(d: InputDevice?): Boolean {
        if (d == null || d.isVirtual) return false
        return padDevices.getOrPut(d.id) {
            val stick = d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            val face =
                d
                    .hasKeys(
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_BUTTON_B,
                        KeyEvent.KEYCODE_BUTTON_X,
                        KeyEvent.KEYCODE_BUTTON_Y,
                    ).all { it }
            stick || face
        }
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

    private fun isGamepad(event: KeyEvent): Boolean = KeyEvent.isGamepadButton(event.keyCode) || isPadDevice(event.device)

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
                val back = pickerBack
                if (failed) {
                    finish()
                } else if (back != null) {
                    back()
                } else {
                    closeMenu()
                }
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
        if (down) lastPad = if (gamepad) KeyFor.CONTROLLER else KeyFor.REMOTE
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
            lastPad = KeyFor.REMOTE
            // Two quick presses of Back change the show's size
            val now = SystemClock.uptimeMillis()
            if (showOn && now - lastBackTap < 400) {
                lastBackTap = 0L
                nextShowSize()
            }
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
                lastBackTap = SystemClock.uptimeMillis()
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!joystick || event.action != MotionEvent.ACTION_MOVE || menuOpen) return super.dispatchGenericMotionEvent(event)
        if (!gamepadSeen) gamepadSeen = true
        lastPad = KeyFor.CONTROLLER
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
                        MenuButton("Back to Benflix", modifier = Modifier.focusRequester(focus)) { finish() }
                    }
                }
            }
            if (menuOpen) Menu()
        }
    }

    /** Where the game, the show, the side panel and the button key go on the screen */
    private class Spots(
        val game: Rect,
        val show: Rect?,
        val panel: Rect?,
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

    /** The key under something in the panel, leaving room for the info strip at the bottom */
    private fun keyBelow(
        x: Dp,
        top: Dp,
        w: Dp,
        h: Dp,
        pad: Dp,
    ): Rect? {
        val height = h - top - STRIP - pad
        return if (height < 150.dp) null else Rect(x, top, w, height)
    }

    private fun spots(
        w: Dp,
        h: Dp,
    ): Spots {
        val pad = 12.dp
        val keyWanted = sideKey && message == null && (isTv || gamepadSeen)
        // Without a show: the game sits left and the panel uses its black bar (or the whole screen is the game)
        val keyPanel = maxOf(w * 0.3f, w - h * gameShape)
        return when {
            showOn && showSize == ShowSize.BIG -> {
                val panel = w * 0.3f
                val main = w - panel
                val gw = panel - pad * 2
                val game = fit(main + pad, pad, gw, min(h * 0.45f, gw / gameShape), gameShape)
                Spots(
                    game = game,
                    show = fit(0.dp, 0.dp, main, h, showShape),
                    panel = Rect(main, 0.dp, panel, h),
                    key = if (keyWanted) keyBelow(main + pad, game.y + game.h + pad, gw, h, pad) else null,
                )
            }

            showOn && showSize == ShowSize.HALF -> {
                val half = w / 2
                val vw = half - pad * 2
                val show = fit(half + pad, pad, vw, min(h * 0.55f, vw / showShape), showShape)
                Spots(
                    game = Rect(0.dp, 0.dp, half, h),
                    show = show,
                    panel = Rect(half, 0.dp, half, h),
                    key = if (keyWanted) keyBelow(half + pad, show.y + show.h + pad, vw, h, pad) else null,
                )
            }

            showOn && showSize == ShowSize.CORNER -> {
                // The game as usual, with a tiny show over its bottom corner
                val gameW = if (keyWanted) w - keyPanel else w
                val sw = gameW * 0.28f
                val sh = sw / showShape
                Spots(
                    game = Rect(0.dp, 0.dp, gameW, h),
                    show = Rect(gameW - sw - pad, h - sh - pad, sw, sh),
                    panel = if (keyWanted) Rect(gameW, 0.dp, keyPanel, h) else null,
                    key = if (keyWanted) Rect(gameW + pad, pad, keyPanel - pad * 2, h - pad * 2 - STRIP) else null,
                )
            }

            showOn -> {
                val panel = maxOf(w * 0.28f, w - h * gameShape)
                val main = w - panel
                val vw = panel - pad * 2
                val show = fit(main + pad, pad, vw, min(h * 0.45f, vw / showShape), showShape)
                Spots(
                    game = Rect(0.dp, 0.dp, main, h),
                    show = show,
                    panel = Rect(main, 0.dp, panel, h),
                    key = if (keyWanted) keyBelow(main + pad, show.y + show.h + pad, vw, h, pad) else null,
                )
            }

            keyWanted -> {
                Spots(
                    game = Rect(0.dp, 0.dp, w - keyPanel, h),
                    show = null,
                    panel = Rect(w - keyPanel, 0.dp, keyPanel, h),
                    key = Rect(w - keyPanel + pad, pad, keyPanel - pad * 2, h - pad * 2 - STRIP),
                )
            }

            else -> {
                Spots(Rect(0.dp, 0.dp, w, h), null, null, null)
            }
        }
    }

    @Composable
    private fun SideBySide() {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val s = spots(maxWidth, maxHeight)
            s.panel?.let { r ->
                // Living Colour: a soft glow from the box art's colour, fading to black
                Box(
                    Modifier
                        .offset(r.x, r.y)
                        .size(r.w, r.h)
                        .background(Brush.verticalGradient(listOf(panelTint.copy(alpha = 0.55f), Color(0xFF0D0B0C)))),
                )
            }
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
                if (showCues.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier =
                            Modifier
                                .offset(r.x, r.y)
                                .size(r.w, r.h)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            showCues.mapNotNull { it.text?.toString() }.joinToString("\n"),
                            color = Color.White,
                            fontSize = if (r.w < 300.dp) 11.sp else 15.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            s.key?.let { r ->
                Box(
                    contentAlignment = if (s.show == null) Alignment.CenterStart else Alignment.TopStart,
                    modifier =
                        Modifier
                            .offset(r.x, r.y)
                            .size(r.w, r.h),
                ) {
                    SmartKey(console, if (isTv) lastPad else KeyFor.CONTROLLER, showPhoneTip = isTv)
                }
            }
            s.panel?.let { r ->
                InfoStrip(
                    Modifier
                        .offset(r.x + 12.dp, r.y + r.h - STRIP)
                        .size(r.w - 24.dp, STRIP),
                )
            }
            toast?.let { text ->
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color(0xE6202020), RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    /** One small line: game, console, when it was saved and how long you've played today */
    @Composable
    private fun InfoStrip(modifier: Modifier) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(20_000)
                now = System.currentTimeMillis()
            }
        }
        val saved =
            when {
                lastSaved == 0L -> null
                now - lastSaved < 60_000 -> "Saved just now"
                else -> "Saved ${(now - lastSaved) / 60_000} min ago"
            }
        val played = (playedTodayMs() / 60_000).let { if (it < 1) null else "Played $it min today" }
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Text(
                listOfNotNull(gameName, console.title, saved, played).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    private enum class MenuPage { MAIN, HELP, SAVE, LOAD, PICK }

    /** The game menu: a sheet that slides in from the right, with the paused game still showing on the left */
    @Composable
    private fun Menu() {
        var page by remember { mutableStateOf(MenuPage.MAIN) }
        var slotsChanged by remember { mutableIntStateOf(0) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(page) { runCatching { focus.requestFocus() } }
        val sheetWidth = if (isTv) 440.dp else 360.dp
        if (page == MenuPage.PICK) {
            ShowPicker(
                show = sideShow,
                isTv = isTv,
                tint = panelTint,
                firstFocus = focus,
                registerBack = { pickerBack = it },
                onPick = { item ->
                    startShow(item)
                    page = MenuPage.MAIN
                    closeMenu()
                },
                onCancel = { page = MenuPage.MAIN },
            )
            return
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(sheetWidth)
                        .fillMaxHeight()
                        .background(Brush.verticalGradient(listOf(panelTint.copy(alpha = 0.5f), Color(0xF20D0B0C), Color(0xFA0D0B0C))))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                Text(gameName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    listOfNotNull(console.title, (playedTodayMs() / 60_000).takeIf { it > 0 }?.let { "played $it min today" })
                        .joinToString(" · "),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                )
                when (page) {
                    MenuPage.PICK -> {}

                    MenuPage.HELP -> {
                        MenuButton("Done", Icons.Filled.Check, Modifier.focusRequester(focus)) { page = MenuPage.MAIN }
                        if (isTv) ButtonKey(console, KeyFor.REMOTE, Modifier.fillMaxWidth())
                        ButtonKey(console, KeyFor.CONTROLLER, Modifier.fillMaxWidth())
                        if (isTv) PhoneTip()
                        Text(
                            if (isTv) {
                                "Hold Back for a second to open this menu. Start + Select together also opens it."
                            } else {
                                "The ☰ circle opens this menu."
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 15.sp,
                        )
                    }

                    MenuPage.SAVE, MenuPage.LOAD -> {
                        val saving = page == MenuPage.SAVE
                        Group(if (saving) "Save to a slot" else "Load a save")
                        MenuButton("Back", Icons.AutoMirrored.Filled.ArrowBack, Modifier.focusRequester(focus)) { page = MenuPage.MAIN }
                        key(slotsChanged) {
                            if (!saving && hasSpot) {
                                SlotButton("Where you last stopped", GameSaves.file(saveDir, "state"), GameSaves.file(saveDir, "png")) {
                                    onGameThread {
                                        GameSaves
                                            .file(
                                                saveDir,
                                                "state",
                                            ).takeIf { it.exists() }
                                            ?.let { RetroNative.loadState(it.readBytes()) }
                                    }
                                    page = MenuPage.MAIN
                                    closeMenu()
                                    showToast("Loaded where you last stopped")
                                }
                            }
                            for (n in 1..3) {
                                val state = GameSaves.file(saveDir, "state$n")
                                if (!saving && !state.exists()) continue
                                SlotButton("Slot $n", state, GameSaves.file(saveDir, "png$n")) {
                                    if (saving) {
                                        Thread {
                                            val ok = saveHere(withSpot = true, slot = n)
                                            if (ok) sendToTower()
                                            runOnUiThread {
                                                slotsChanged++
                                                page = MenuPage.MAIN
                                                closeMenu()
                                                showToast(if (ok) "Saved to slot $n" else "Couldn't save just now")
                                            }
                                        }.start()
                                    } else {
                                        onGameThread { RetroNative.loadState(state.readBytes()) }
                                        page = MenuPage.MAIN
                                        closeMenu()
                                        showToast("Loaded slot $n")
                                    }
                                }
                            }
                        }
                    }

                    MenuPage.MAIN -> {
                        Group("Game")
                        MenuButton("Resume", Icons.Filled.PlayArrow, Modifier.focusRequester(focus)) { closeMenu() }
                        MenuButton("Save", Icons.Filled.Add) { page = MenuPage.SAVE }
                        if (hasSpot || (1..3).any { GameSaves.file(saveDir, "state$it").exists() }) {
                            MenuButton("Load", Icons.Filled.DateRange) { page = MenuPage.LOAD }
                        }
                        MenuButton("Restart game", Icons.Filled.Refresh) {
                            onGameThread { RetroNative.reset() }
                            closeMenu()
                        }
                        if (sideShow != null && canSideShow) {
                            Group("Watch")
                            if (!showOn) {
                                MenuButton("Watch a show on the side", Icons.Filled.Face) { page = MenuPage.PICK }
                            } else {
                                MenuButton("Change the show", Icons.Filled.Face) { page = MenuPage.PICK }
                                MenuButton("Size: ${showSize.label.removePrefix("Show: ")}", Icons.Filled.Menu) { nextShowSize() }
                                val sound =
                                    when (soundMode) {
                                        0 -> "Sound: show and game"
                                        1 -> "Sound: show only"
                                        else -> "Sound: game only"
                                    }
                                MenuButton(sound, Icons.Filled.Notifications) {
                                    soundMode = (soundMode + 1) % 3
                                    applySound()
                                }
                                MenuButton("Stop $showTitle".take(40), Icons.Filled.Close) { stopShow() }
                            }
                        }
                        Group("Controls")
                        MenuButton("Buttons", Icons.Filled.Info) { page = MenuPage.HELP }
                        if (isTv || gamepadSeen) {
                            MenuButton(if (sideKey) "Hide the button key" else "Show the button key", Icons.Filled.Edit) {
                                saveSideKey(!sideKey)
                            }
                        }
                        if (isTv) {
                            MenuButton("Press Start", Icons.AutoMirrored.Filled.ArrowForward) { tapFromMenu(RetroButton.START) }
                            MenuButton("Press Select", Icons.AutoMirrored.Filled.ArrowForward) { tapFromMenu(RetroButton.SELECT) }
                        }
                        MenuButton("Quit and save", Icons.AutoMirrored.Filled.ExitToApp) { quit() }
                    }
                }
            }
        }
    }

    @Composable
    private fun Group(title: String) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
    }

    /** A save slot: its picture, its name and when it was saved (or "Empty") */
    @Composable
    private fun SlotButton(
        title: String,
        state: File,
        picture: File,
        onClick: () -> Unit,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(14.dp)
        val image =
            remember(picture.path, picture.lastModified()) {
                if (picture.exists()) {
                    android.graphics.BitmapFactory
                        .decodeFile(picture.path)
                        ?.asImageBitmap()
                } else {
                    null
                }
            }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(if (focused) Color.White else Color(0xFF242424), shape)
                    .border(2.dp, if (focused) Color.White else Color(0xFF3A3A3A), shape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .padding(8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(96.dp, 64.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp)),
            ) {
                if (image != null) {
                    Image(
                        image,
                        null,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("Empty", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            Column {
                Text(title, color = if (focused) Color.Black else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.exists()) savedAgo(state.lastModified()) else "Empty",
                    color = (if (focused) Color.Black else Color.White).copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
        }
    }

    private fun savedAgo(time: Long): String {
        val min = (System.currentTimeMillis() - time) / 60_000
        return when {
            min < 1 -> "Saved just now"
            min < 60 -> "Saved $min min ago"
            min < 24 * 60 -> "Saved ${min / 60} h ago"
            else -> "Saved ${min / (24 * 60)} days ago"
        }
    }

    @Composable
    private fun MenuButton(
        text: String,
        icon: ImageVector? = null,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(28.dp)
        val fg = if (focused) Color.Black else Color.White
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (icon == null) Arrangement.Center else Arrangement.spacedBy(14.dp),
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(if (focused) Color.White else Color(0xFF242424), shape)
                    .border(2.dp, if (focused) Color.White else Color(0xFF3A3A3A), shape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        const val EXTRA_COVER = "cover"
        private const val PREFS = "game_player"
        private val STRIP = 28.dp
    }
}
