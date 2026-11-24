package com.github.damontecres.wholphin.ui.playback

import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.ui.formatMaxBitrate
import com.github.damontecres.wholphin.ui.launchIO
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

/*
 * Redesign 2 players: the "Fix a problem" list in the player's settings panel. Each one fixes it on the
 * spot where it can (the same fixes the automatic sound and picture checks use), and Report a problem
 * sends Ben a note with the app's recent log, like Report a problem on the title pages.
 */

/** "No sound": the sound check's fixes, straight away (restart the sound, then the server converts it) */
internal fun PlaybackViewModel.fixSoundByHand() {
    soundWatchdog.fixNow()
}

/**
 * "Picture stutters": the first press starts the picture again at the same spot (lets go of the video
 * chip and picks the best one again). Pressing it again on the same video asks for a lighter picture,
 * a bit below what's playing now, so a slow Wi-Fi or a busy server can keep up.
 */
internal fun PlaybackViewModel.fixPictureByHand() {
    val playing = state.value.currentPlayback ?: return
    if (pictureFixesFor != playing.itemId) {
        pictureFixesFor = playing.itemId
        pictureFixes = 0
    }
    pictureFixes++
    if (pictureFixes == 1 && pictureWatchdog.restartNow(playing.itemId)) return
    val playingNow =
        listOfNotNull(
            state.value.maxBitrate,
            playing.mediaSourceInfo.bitrate
                ?.toLong()
                ?.takeIf { it > 0 },
        ).min()
    val lighter =
        AppPreference.bitrateValues.lastOrNull { it <= playingNow * 6 / 10 }
            ?: AppPreference.bitrateValues.first()
    if (lighter >= playingNow) {
        showNotice(
            PlayerNotice(
                title = "That's the lightest picture already",
                message = "Report a problem so Ben can look at this one.",
                autoHideMs = 6_000L,
            ),
        )
        return
    }
    Timber.i("Picture fix by hand: lighter quality %s (was %s)", lighter, playingNow)
    showNotice(
        PlayerNotice(
            title = "Trying a lighter picture",
            message = "Quality is now ${formatMaxBitrate(lighter)}. Change it back in Settings > Quality.",
            autoHideMs = 6_000L,
        ),
    )
    changeMaxBitrate(lighter)
}

/** "Report a problem" in the player: Ben's Server page lists it, with the app's recent log */
internal fun PlaybackViewModel.reportProblem(problem: String) {
    val problems = benflixProblems ?: return
    val item = state.value.currentPlayback?.item ?: return
    val title =
        if (item.type == BaseItemKind.EPISODE) {
            listOfNotNull(
                item.data.seriesName,
                item.data.parentIndexNumber?.let { s -> item.data.indexNumber?.let { e -> "S$s E$e" } },
            ).joinToString(" ").ifBlank { item.title.orEmpty() }
        } else {
            item.title.orEmpty()
        }
    viewModelScope.launchIO {
        val notice =
            try {
                problems.report(item.id, title, problem)
                PlayerNotice(title = "Thanks! Ben got a fix request", message = "$problem · $title", autoHideMs = 6_000L)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not send the problem report")
                PlayerNotice(title = "Couldn't send that", message = "Try again in a moment.", autoHideMs = 6_000L)
            }
        showNotice(notice)
    }
}

/**
 * Idea Box 29 "This copy is bad": Tower has Radarr / Sonarr throw this file away, never take that download
 * again, and look for a new copy. The video pauses (the file is going away) and Ben's Server page lists it
 * like any other report.
 */
internal fun PlaybackViewModel.swapBadCopy() {
    val helper = gameLibraryClient ?: return
    val item = state.value.currentPlayback?.item ?: return
    if (item.type != BaseItemKind.MOVIE && item.type != BaseItemKind.EPISODE) return
    player.pause()
    viewModelScope.launchIO {
        // Ben's Server page lists it like any other report (quietly, the notice below says what happened)
        try {
            benflixProblems?.report(item.id, item.title.orEmpty(), "This copy is bad")
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Could not send the bad copy report")
        }
        val notice =
            try {
                val answer = helper.postJson("/api/bad-copy", org.json.JSONObject().put("id", item.id.toString()))
                PlayerNotice(
                    title = "Thanks, Tower is on it",
                    message = answer.optString("message").ifBlank { "Tower is finding a new copy. It's usually back within an hour." },
                    autoHideMs = 12_000L,
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't swap the bad copy")
                PlayerNotice(
                    title = "Tower couldn't swap it",
                    message = ex.message?.takeIf { it.isNotBlank() && it.length < 160 } ?: "Ben got your report and will look.",
                    autoHideMs = 10_000L,
                )
            }
        showNotice(notice)
    }
}
