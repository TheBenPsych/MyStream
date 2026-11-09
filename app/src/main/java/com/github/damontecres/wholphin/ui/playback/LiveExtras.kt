package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/** Benflix "This channel is broken": tells Tower, whose nightly channel check swaps it for a working one */
@HiltViewModel
class LiveExtrasViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        private val reported = mutableSetOf<String>()

        fun reportBroken(
            name: String?,
            number: String?,
            url: String?,
        ) {
            val key = url ?: number ?: name ?: return
            if (!reported.add(key)) {
                viewModelScope.launch { showToast(context, "Already sent. Tonight's channel check swaps it.") }
                return
            }
            viewModelScope.launch(WholphinDispatchers.IO) {
                val message =
                    try {
                        val answer =
                            helper.postJson(
                                "/api/livetv/broken",
                                JSONObject()
                                    .put("name", name ?: "")
                                    .put("number", number ?: "")
                                    .put("url", url ?: ""),
                            )
                        answer.optString("message", "Thanks! Tonight's channel check swaps it for a working one.")
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not report broken channel %s", name)
                        reported.remove(key)
                        "Couldn't reach Tower. This only works at home on your Wi-Fi."
                    }
                showToast(context, message)
            }
        }
    }

/**
 * Benflix extras while watching a live channel:
 * - "Pause live TV": after a pause, a "Behind live" tag; Back (or tapping the tag) jumps back to live
 * - "This channel is broken": a button while the player bar shows
 */
@Composable
fun LiveExtras(
    player: Player,
    isLiveChannel: Boolean,
    controlsVisible: Boolean,
    channelName: String?,
    channelNumber: String?,
    streamUrl: String?,
    modifier: Modifier = Modifier,
    viewModel: LiveExtrasViewModel = hiltViewModel(),
) {
    if (!isLiveChannel) return
    var behindMs by remember { mutableLongStateOf(0L) }
    var live by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            live = player.isCurrentMediaItemLive
            val offset = if (live) player.currentLiveOffset else C.TIME_UNSET
            val target =
                player.currentMediaItem
                    ?.liveConfiguration
                    ?.targetOffsetMs
                    ?.takeIf { it != C.TIME_UNSET } ?: DEFAULT_TARGET_MS
            behindMs = if (offset == C.TIME_UNSET) 0L else (offset - target).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    val behind = live && behindMs >= BEHIND_AFTER_MS
    val jumpToLive = {
        player.seekToDefaultPosition()
        player.play()
        behindMs = 0L
    }
    // Back while behind live catches up first; the next Back leaves as usual
    BackHandler(behind && !controlsVisible) { jumpToLive() }
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(24.dp),
    ) {
        if (behind) {
            Pill(
                text = "Behind live · ${behindWords(behindMs)} · Jump to live",
                strong = true,
                onClick = jumpToLive,
            )
        }
        if (controlsVisible) {
            Pill(
                text = "This channel is broken",
                strong = false,
                onClick = { viewModel.reportBroken(channelName, channelNumber, streamUrl) },
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    strong: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (strong) Color(0xE6E8D44D) else Color.Black.copy(alpha = .7f),
                contentColor = if (strong) Color.Black else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun behindWords(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 1) "${ms / 1000} sec" else "$minutes min"
}

/** Counted as "behind" once this far back from the live edge */
private const val BEHIND_AFTER_MS = 20_000L

/** When a stream doesn't say how far from the edge it plays */
private const val DEFAULT_TARGET_MS = 10_000L
