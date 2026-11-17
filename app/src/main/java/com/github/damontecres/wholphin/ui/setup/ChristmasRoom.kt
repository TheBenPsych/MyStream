package com.github.damontecres.wholphin.ui.setup

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.github.damontecres.wholphin.ui.theme.ChristmasSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Benflix Christmas: the cosy room behind Who's watching (1 to 26 December). The tree with the train
 * going round it, the parade past the window, and the presents that pile up through December.
 *
 * The room is drawn by the web version (server/web/js/xmas.js) and recorded as 30-second loops
 * (tools/xmas/record.js), so TVs and phones show exactly the same thing and older TVs stay fast. The video
 * for today is downloaded the first time (about 1 to 2 MB); until then the room is a plain warm dark.
 */
@Composable
fun ChristmasRoom(modifier: Modifier = Modifier) =
    LoopingRoom(XmasVideos, { tall -> XmasVideos.name(ChristmasSeason.variant(LocalDate.now()), tall) }, modifier)

/**
 * Benflix Thanksgiving: the feast table room behind Who's watching (16 to 30 November). Drawn by the web
 * version (server/web/js/feast.js) and recorded as 30-second loops by tools/feast/record.js.
 */
@Composable
fun FeastRoom(modifier: Modifier = Modifier) = LoopingRoom(FeastVideos, { tall -> if (tall) "tall" else "wide" }, modifier)

/**
 * Benflix Halloween: the spooky room behind Who's watching (1 to 31 October). Drawn by the web version
 * (server/web/js/halloween.js) and recorded as 30-second loops by tools/halloween/record.js. [stage] is how far
 * the pumpkins are carved ("whole", "half", "carved") or "night" (31 Oct after 5 PM, with bats).
 */
@Composable
fun HalloweenRoom(
    stage: String,
    modifier: Modifier = Modifier,
) = LoopingRoom(HalloweenVideos, { tall -> "$stage-${if (tall) "tall" else "wide"}" }, modifier)

/**
 * Benflix New Year's: the party room behind Who's watching (27 December to 1 January). Drawn by the web
 * version (server/web/js/newyear.js) and recorded as 60-second loops by tools/newyear/record.js: [variant]
 * "eve" until midnight on New Year's Eve (bottle corked), then "day" (opened, confetti falling all day).
 * The year is on the balloons, so each year has its own videos.
 */
@Composable
fun NewYearRoom(
    variant: String,
    year: Int,
    modifier: Modifier = Modifier,
) {
    LoopingRoom(
        NewYearVideos,
        { tall -> NewYearVideos.name(variant, year, tall) },
        modifier,
        bg = listOf(Color(0xFF090C18), Color(0xFF121830)),
        // On New Year's Eve, the after-midnight video downloads early so the room changes the moment the countdown ends
        alsoFetch = { tall -> if (variant == "eve" && LocalDate.now().dayOfMonth == 31) NewYearVideos.name("day", year, tall) else null },
    )
}

/** Plays a holiday room's recorded loop for today, filling the screen and keeping the floor at the bottom */
@OptIn(UnstableApi::class)
@Composable
private fun LoopingRoom(
    videos: RoomVideos,
    nameFor: (Boolean) -> String,
    modifier: Modifier = Modifier,
    bg: List<Color> = listOf(Color(0xFF1A110D), Color(0xFF0D0A0A)),
    alsoFetch: ((Boolean) -> String?)? = null,
) {
    val context = LocalContext.current
    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                .background(
                    Brush.verticalGradient(bg),
                ),
    ) {
        val tall = maxWidth < maxHeight * 0.8f
        val name = nameFor(tall)
        var file by remember(name) { mutableStateOf(videos.cached(context, name)) }
        LaunchedEffect(name) {
            if (file == null) file = videos.fetch(context, name)
            alsoFetch?.invoke(tall)?.let { videos.fetch(context, it) }
        }
        val video = file ?: return@BoxWithConstraints

        val player =
            remember(video) {
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(video)))
                    prepare()
                    playWhenReady = true
                }
            }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(player, lifecycle) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            player.play()
                        }

                        Lifecycle.Event.ON_STOP -> {
                            player.pause()
                        }

                        else -> {}
                    }
                }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                player.release()
            }
        }
        // Fill the screen and crop, keeping the floor (the tree and the presents) at the bottom
        val (vw, vh) = if (tall) 540f to 1170f else 1280f to 720f
        val scale = maxOf(maxWidth.value / vw, maxHeight.value / vh)
        Box(Modifier.fillMaxSize()) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .requiredSize((vw * scale).dp, (vh * scale).dp),
            )
        }
    }
}

/** A downloaded video smaller than this is a failed download */
private const val MIN_BYTES = 10_000L

/** Downloads and keeps the Christmas room videos (extras/scenes/xmas in the repo) */
object XmasVideos : RoomVideos("xmas", version = 1) {
    /** The recording's name in the repo, like "EVE-wide" */
    fun name(
        variant: String,
        tall: Boolean,
    ) = "$variant-${if (tall) "tall" else "wide"}"
}

/** Downloads and keeps the Halloween room videos (extras/scenes/halloween in the repo: <stage>-<wide|tall>) */
object HalloweenVideos : RoomVideos("halloween", version = 1)

/** Downloads and keeps the New Year's party room videos (extras/scenes/newyear in the repo) */
object NewYearVideos : RoomVideos("newyear", version = 1) {
    /** The recording's name in the repo, like "eve-2027-wide" */
    fun name(
        variant: String,
        year: Int,
        tall: Boolean,
    ) = "$variant-$year-${if (tall) "tall" else "wide"}"
}

/** Downloads and keeps the Thanksgiving feast room videos (extras/scenes/feast in the repo: wide and tall) */
object FeastVideos : RoomVideos("feast", version = 1)

/**
 * Downloads and keeps one holiday room's videos (extras/scenes/[folder] in the repo).
 * [version]: bump when the room is re-recorded, so devices fetch the new videos instead of keeping the old ones.
 */
open class RoomVideos(
    private val folder: String,
    private val version: Int,
) {
    private val base = "https://raw.githubusercontent.com/TheBenPsych/MyStream/main/extras/scenes/$folder"

    private val http by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    private val failed: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet()
    private val lock = Mutex()

    private fun file(
        context: Context,
        name: String,
    ) = File(context.filesDir, "scenes/$folder/$name-v$version.mp4")

    fun cached(
        context: Context,
        name: String,
    ): File? = file(context, name).takeIf { it.length() > MIN_BYTES }

    suspend fun fetch(
        context: Context,
        name: String,
    ): File? =
        withContext(Dispatchers.IO) {
            cached(context, name)?.let { return@withContext it }
            if (name in failed) return@withContext null
            lock.withLock { download(context, name) }
        }

    private fun download(
        context: Context,
        name: String,
    ): File? {
        cached(context, name)?.let { return it }
        val target = file(context, name)
        return try {
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".part")
            http.newCall(Request.Builder().url("$base/$name.mp4?v=$version").build()).execute().use { response ->
                if (!response.isSuccessful) {
                    failed += name
                    return null
                }
                tmp.outputStream().use { response.body.byteStream().copyTo(it) }
            }
            if (tmp.length() <= MIN_BYTES || !tmp.renameTo(target)) {
                tmp.delete()
                failed += name
                return null
            }
            // Only today's room is needed: the next one downloads when its days come round
            target.parentFile
                ?.listFiles()
                ?.filter { it.extension == "mp4" }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(2)
                ?.forEach { it.delete() }
            target
        } catch (e: Exception) {
            Timber.i("%s room video not downloaded: %s", folder, e.javaClass.simpleName)
            failed += name
            null
        }
    }
}
