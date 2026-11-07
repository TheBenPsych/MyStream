package com.github.damontecres.wholphin.ui.playback

import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.SkipFix
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.onMain
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.MediaSegmentType
import timber.log.Timber

/**
 * Benflix: saves a "Fix skip times" choice from the player at the spot the video is at now. Tower uses
 * it for this episode and looks for the same spot in the show's other episodes, so one fix mends the
 * whole season. The new times are fetched straight away.
 */
internal fun PlaybackViewModel.fixSkip(fix: SkipFix) {
    val service = benflixSkips ?: return
    viewModelScope.launchIO {
        val id = itemId
        val atSeconds = onMain { player.currentPosition } / 1000.0
        try {
            val result = service.fix(id, fix, atSeconds)
            // Don't jump away straight after marking where something starts (you may want to mark its end too)
            result
                ?.segments
                ?.filter {
                    (fix == SkipFix.INTRO_START && it.type == MediaSegmentType.INTRO) ||
                        (fix == SkipFix.CREDITS_START && it.type == MediaSegmentType.OUTRO)
                }?.forEach { autoSkippedSegments.add(it.id) }
            refreshSegmentsNow = true
            showNotice(
                PlayerNotice(
                    title = "Saved: ${result?.saved ?: "your fix"}",
                    message = fixSavedMessage(fix),
                    autoHideMs = 7_000L,
                ),
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Couldn't save the skip fix")
            showNotice(
                PlayerNotice(
                    title = "Couldn't save the fix",
                    message = ex.message ?: "Tower's Benflix helper didn't answer. Try again in a minute.",
                    autoHideMs = 8_000L,
                ),
            )
        }
    }
}

private fun fixSavedMessage(fix: SkipFix): String =
    when (fix) {
        SkipFix.INTRO_START -> "Now press \"Intro ends here\" when it finishes. The rest of the show gets fixed too."

        SkipFix.INTRO_END,
        SkipFix.CREDITS_START,
        SkipFix.CREDITS_END,
        -> "Benflix is finding the same spot in the show's other episodes."

        SkipFix.NO_INTRO -> "No Skip intro button on this episode."

        SkipFix.NO_INTRO_SHOW -> "No Skip intro button on this show any more."

        SkipFix.UNDO -> "Your fixes for this episode are gone."
    }
