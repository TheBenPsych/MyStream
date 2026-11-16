package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.abs

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface WatchTogetherEntryPoint {
    fun helper(): GameLibraryClient
}

/**
 * Benflix: watch together on TVs and phones. It uses the same lobby as web Benflix's 3D Lobby (Tower keeps it), so
 * people in a browser, on a TV and on a phone all watch the same movie in step: play, pause and jumping around
 * happen for everyone (the host's, or anyone's when the host allows it).
 *
 * Player > Settings > Watch together: start one, or join one that's open (no codes to type).
 */
object WatchTogether {
    data class Lobby(
        val code: String,
        val hostName: String,
        val title: String,
        val people: Int,
        val mine: Boolean,
    )

    data class Tv(
        val item: String,
        val title: String,
        val playing: Boolean,
        val posMs: Long,
    )

    /** The lobby this device is watching in, or null */
    var code by mutableStateOf<String?>(null)
        private set

    /** Who else is watching (names) */
    var people by mutableStateOf<List<String>>(emptyList())
        private set
    var hostName by mutableStateOf("")
        private set
    var mayControl by mutableStateOf(true)
        private set

    /** Open lobbies, for the Join list (null while loading) */
    var lobbies by mutableStateOf<List<Lobby>?>(null)
        private set

    /** Something to tell the person, like "Only Ben can pause right now" */
    var problem by mutableStateOf<String?>(null)

    /** True while the player switches to the lobby's movie (stay in the lobby) */
    @Volatile
    internal var switching = false

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun helper(context: Context): GameLibraryClient =
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, WatchTogetherEntryPoint::class.java)
            .helper()

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    /** Kids profiles can only join while something rated for kids is on */
    private fun kidsOk(item: BaseItem): Boolean =
        item.data.officialRating?.uppercase() in setOf("G", "PG", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-Y7-FV")

    private fun idOf(item: BaseItem) = item.id.toString().replace("-", "")

    suspend fun loadLobbies(context: Context) {
        onMain { lobbies = null }
        val found =
            try {
                val arr = helper(context).getJson("/api/lobby/list").optJSONArray("lobbies")
                (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                    arr?.optJSONObject(i)?.let {
                        Lobby(
                            code = it.optString("code"),
                            hostName = it.optString("host_name"),
                            title = it.optString("title"),
                            people = it.optJSONArray("people")?.length() ?: 0,
                            mine = it.optBoolean("mine"),
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Couldn't list the lobbies")
                onMain { problem = e.message ?: "Couldn't reach Tower" }
                emptyList()
            }
        onMain { lobbies = found }
    }

    /** Opens a lobby showing [item] at [posMs] and starts it playing for everyone */
    suspend fun start(
        context: Context,
        item: BaseItem,
        posMs: Long,
        playing: Boolean,
    ) {
        try {
            val body =
                JSONObject()
                    .put("item", idOf(item))
                    .put("title", item.data.name ?: "")
                    .put("kids_ok", kidsOk(item))
                    .put("pos", posMs / 1000.0)
                    .put("room", "theater")
            val answer = helper(context).postJson("/api/lobby/open", body)
            val newCode = answer.optString("code").takeIf { it.isNotBlank() } ?: return
            onMain {
                code = newCode
                problem = null
            }
            // Come in first, then press play for everyone
            sync(context)
            if (playing) send(context, "play", posMs)
        } catch (e: Exception) {
            Timber.w(e, "Couldn't open a lobby")
            onMain { problem = e.message ?: "Couldn't open a lobby" }
        }
    }

    fun join(lobby: String) {
        code = lobby
        problem = null
    }

    /** Leaves the lobby (Tower also notices after a few seconds when a device stops checking in) */
    fun leave(context: Context) {
        val was = code ?: return
        code = null
        people = emptyList()
        background.launch {
            try {
                helper(context).postJson("/api/lobby/$was/leave")
            } catch (_: Exception) {
            }
        }
    }

    /** Checks in with the lobby and returns what's on its TV */
    suspend fun sync(context: Context): Tv? {
        val c = code ?: return null
        val me =
            JSONObject()
                .put("x", 0)
                .put("z", 6)
                .put("yaw", 0)
                .put("sit", -1)
                .put("act", "app")
        val answer = helper(context).postJson("/api/lobby/$c/sync", JSONObject().put("me", me).put("chat_since", 1_000_000_000))
        val tv = answer.optJSONObject("tv") ?: return null
        val names =
            answer
                .optJSONArray("people")
                ?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                }.orEmpty()
        onMain {
            people = names
            hostName = answer.optString("host_name")
            mayControl = answer.optBoolean("may_control", true)
        }
        return Tv(
            item = tv.optString("item"),
            title = tv.optString("title"),
            playing = tv.optBoolean("playing"),
            posMs = (tv.optDouble("pos", 0.0) * 1000).toLong(),
        )
    }

    /** Play, pause or jump for everyone */
    suspend fun send(
        context: Context,
        action: String,
        posMs: Long,
    ) {
        val c = code ?: return
        try {
            helper(context).postJson("/api/lobby/$c/tv", JSONObject().put("action", action).put("pos", posMs / 1000.0))
        } catch (e: Exception) {
            onMain { problem = e.message }
        }
    }

    internal fun sendLater(
        context: Context,
        action: String,
        posMs: Long,
    ) {
        background.launch { send(context, action, posMs) }
    }
}

/**
 * Benflix: keeps this player in step with the lobby while watching together: follows play, pause, jumps and what's
 * on, and tells the lobby when this person presses play, pause or jumps.
 */
@Composable
fun WatchTogetherEffect(
    player: Player,
    item: BaseItem?,
    navigationManager: NavigationManager,
) {
    val context = LocalContext.current
    val code = WatchTogether.code
    if (code == null || item == null) return
    val applying = remember { booleanArrayOf(false) }
    DisposableEffect(player, code) {
        val listener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (applying[0] || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
                    WatchTogether.sendLater(context, if (playWhenReady) "play" else "pause", player.currentPosition)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (applying[0] || reason != Player.DISCONTINUITY_REASON_SEEK) return
                    WatchTogether.sendLater(context, "seek", newPosition.positionMs)
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Closing the player leaves the lobby; switching to the lobby's next movie doesn't
            if (WatchTogether.switching) WatchTogether.switching = false else WatchTogether.leave(context)
        }
    }
    LaunchedEffect(code, item.id) {
        val mine = item.id.toString().replace("-", "")
        while (isActive) {
            val tv =
                try {
                    WatchTogether.sync(context)
                } catch (e: Exception) {
                    Timber.w(e, "Watch together check-in failed")
                    WatchTogether.problem = e.message
                    null
                }
            if (tv != null && tv.item.isNotBlank()) {
                if (!tv.item.equals(mine, ignoreCase = true)) {
                    // The lobby put on something else: follow it
                    val id = tv.item.toUUIDOrNull()
                    if (id != null) {
                        WatchTogether.switching = true
                        navigationManager.replaceTop(Destination.Playback(id, tv.posMs))
                        return@LaunchedEffect
                    }
                } else {
                    applying[0] = true
                    try {
                        if (tv.playing != player.playWhenReady) player.playWhenReady = tv.playing
                        if (abs(player.currentPosition - tv.posMs) > 2500) player.seekTo(tv.posMs)
                    } finally {
                        applying[0] = false
                    }
                }
            }
            delay(1000)
        }
    }
}
