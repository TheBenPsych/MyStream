package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Benflix Night mode: evens out the volume so explosions are quieter and talking is louder.
 * It squeezes the loud and quiet parts closer together (a "compressor"), then turns the
 * whole thing up a little, with a limiter so nothing gets too loud.
 *
 * Remembered on this device until turned off. Only works when the TV decodes the audio
 * itself (not when surround sound is passed straight through to a soundbar).
 */
object NightMode {
    private const val PREFS = "benflix_audio"
    private const val KEY = "night_mode"

    private val _enabled = MutableStateFlow<Boolean?>(null)

    fun enabled(context: Context): StateFlow<Boolean?> {
        if (_enabled.value == null) {
            _enabled.value =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
        }
        return _enabled
    }

    fun toggle(context: Context) {
        val on = !(enabled(context).value ?: false)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
        _enabled.value = on
    }

    fun isOn(context: Context): Boolean = enabled(context).value ?: false

    /** Builds the audio effect for a player's sound, or null if this TV can't do it */
    fun createEffect(audioSessionId: Int): DynamicsProcessing? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return null
        return try {
            val band =
                DynamicsProcessing.MbcBand(
                    true,
                    20_000f, // whole frequency range
                    5f, // attack ms
                    150f, // release ms
                    4f, // ratio: loud parts are squeezed 4 to 1
                    -28f, // threshold dB
                    6f, // knee width
                    -90f, // noise gate (off)
                    1f, // expander ratio (off)
                    0f, // pre gain
                    8f, // post gain: lift everything, quiet voices included
                )
            val mbc = DynamicsProcessing.Mbc(true, true, 1).apply { setBand(0, band) }
            val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -3f, 0f)
            val config =
                DynamicsProcessing.Config
                    .Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        MAX_CHANNELS,
                        false,
                        0,
                        true,
                        1,
                        false,
                        0,
                        true,
                    ).setMbcAllChannelsTo(mbc)
                    .setLimiterAllChannelsTo(limiter)
                    .build()
            DynamicsProcessing(0, audioSessionId, config)
        } catch (ex: Exception) {
            Timber.w(ex, "Night mode not available")
            null
        }
    }

    private const val MAX_CHANNELS = 8
}

/** Hooks Night mode up to the movie/show player while the player screen is open */
@Composable
fun NightModeEffect(player: Player) {
    val context = LocalContext.current
    val on by remember { NightMode.enabled(context) }.collectAsState()
    val sessionId = (player as? ExoPlayer)?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
    val effect = remember(player, sessionId) { NightMode.createEffect(sessionId) }
    DisposableEffect(effect) {
        onDispose { effect?.release() }
    }
    LaunchedEffect(effect, on) {
        try {
            effect?.enabled = on == true
        } catch (ex: Exception) {
            Timber.w(ex, "Could not switch night mode")
        }
    }
}
