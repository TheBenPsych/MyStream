package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.services.BenflixCantFind
import com.github.damontecres.wholphin.services.CantFindEntry
import com.github.damontecres.wholphin.services.MusicRequest
import com.github.damontecres.wholphin.ui.theme.BenflixType

/*
 * Redesign 2, Requests (approved 2026-11-10): one status helper, five words, everywhere. The same pill shows on
 * posters, in Your requests, on title pages and on Home, so every page says the same thing.
 *
 *  Asked            (grey)  Pending, Requested, Approved, Not out yet ("Asked · out July 17"), Waiting for Ben
 *  Getting it · 62% (blue)  Downloading, Processing, Partly here ("Getting it · 4 of 10 episodes")
 *  Still looking    (amber) Still looking day N, Just came out, Look for it yourself (Ben only, after 14 days)
 *  Couldn't find it (red)   Couldn't find it (with Try again for Ben)
 *  Ready to watch   (green) Available, Ready (books: Ready to read, music: Ready to play)
 */

/** The five request states, in the order Your requests groups them (Ready first) */
enum class AskState(
    val group: String,
    val color: Color,
) {
    READY("Ready", Color(0xFF5BD18B)),
    GETTING("Getting it", Color(0xFF7AA7FF)),
    LOOKING("Still looking", Color(0xFFF2B544)),
    ASKED("Asked", Color(0xFFC9CBD2)),
    CANT("Couldn't find it", Color(0xFFFF8A8A)),
}

/** What kind of thing was asked for, for the Ready words (watch / read / play) */
enum class AskKind { WATCH, READ, PLAY }

/** One status pill: its state and the full words, like "Getting it · 62%" or "Asked · out July 17" */
data class AskPill(
    val state: AskState,
    val words: String,
) {
    val color: Color get() = state.color
}

fun readyPill(kind: AskKind = AskKind.WATCH) =
    AskPill(
        AskState.READY,
        when (kind) {
            AskKind.WATCH -> "Ready to watch"
            AskKind.READ -> "Ready to read"
            AskKind.PLAY -> "Ready to play"
        },
    )

fun askedPill(extra: String? = null) = AskPill(AskState.ASKED, if (extra.isNullOrBlank()) "Asked" else "Asked · $extra")

fun gettingPill(percent: Int? = null) =
    AskPill(AskState.GETTING, if (percent != null && percent in 1..99) "Getting it · $percent%" else "Getting it")

fun lookingPill(day: Long? = null) = AskPill(AskState.LOOKING, if (day != null && day >= 1) "Still looking · day $day" else STILL_LOOKING)

private const val STILL_LOOKING = "Still looking"

val CantPill = AskPill(AskState.CANT, "Couldn't find it")

/**
 * A movie or show request (Seerr) as one of the five pills.
 * [cantFind] = it's on the Couldn't find it list. [daysWaiting] is used when Tower has no search clock.
 */
fun RequestEntry.askPill(
    cantFind: CantFindEntry? = null,
    daysWaiting: Long? = null,
): AskPill {
    val clock = clock
    val looking = clock?.daysLooking ?: daysWaiting
    val waiting = status == RequestStatus.APPROVED && !downloading
    return when {
        available -> {
            readyPill()
        }

        cantFind != null || status == RequestStatus.DECLINED || status == RequestStatus.FAILURE -> {
            CantPill
        }

        downloading -> {
            gettingPill(downloadPercent)
        }

        status == RequestStatus.PENDING -> {
            askedPill("waiting for Ben")
        }

        waiting && clock?.notOut == true -> {
            askedPill(clock?.outOn?.let { BenflixCantFind.niceDate(it.toString()) }?.let { "out $it" })
        }

        waiting && looking != null -> {
            lookingPill(looking + 1)
        }

        waiting -> {
            lookingPill()
        }

        else -> {
            askedPill()
        }
    }
}

/**
 * Turns the old words Tower or Seerr send (any page, any door) into the pill. Anything it doesn't know is "Asked".
 * [kind] picks the Ready words.
 */
fun pillFromWords(
    words: String?,
    kind: AskKind = AskKind.WATCH,
): AskPill {
    val w = words?.trim().orEmpty()
    val low = w.lowercase()
    val pct =
        Regex("(\\d{1,3})\\s?%")
            .find(w)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    val day =
        Regex("day (\\d+)")
            .find(low)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    val cant = low.startsWith("couldn't") || low.contains("went wrong") || low.contains("said no") || low.contains("declined")
    val ready = low.startsWith("ready") || low.contains("on benflix") || low.contains("in your music") || low == "available"
    val getting = listOf("download", "processing", "getting it", "putting it", "partly").any { low.contains(it) }
    val looking = listOf("looking", "searching", "just came out", "finding").any { low.contains(it) }
    return when {
        low.isEmpty() -> askedPill()
        cant -> CantPill
        ready -> readyPill(kind)
        getting -> gettingPill(pct)
        looking -> lookingPill(day)
        low.contains("waiting for") -> askedPill("waiting for Ben")
        low.startsWith("not out") -> askedPill(w.substringAfter("·", "").trim().ifBlank { null })
        else -> askedPill()
    }
}

/** A music request (Tower) as a pill. Newer helpers send the state; older ones only send words. */
fun MusicRequest.askPill(): AskPill =
    when (pill) {
        "ready" -> {
            readyPill(AskKind.PLAY)
        }

        "cant" -> {
            CantPill
        }

        "getting" -> {
            gettingPill(pct)
        }

        "looking" -> {
            lookingPill(days.takeIf { it >= 1 }?.let { it + 1L })
        }

        "asked" -> {
            if (waiting) askedPill("waiting for Ben") else askedPill()
        }

        else -> {
            if (ready) {
                readyPill(AskKind.PLAY)
            } else if (bad) {
                CantPill
            } else {
                pillFromWords(words, AskKind.PLAY)
            }
        }
    }

/** A book request (Tower) as a pill, from its state word */
fun bookPill(
    state: String,
    words: String,
): AskPill =
    when (state) {
        "ready" -> {
            readyPill(AskKind.READ)
        }

        "cant", "problem", "no" -> {
            CantPill
        }

        "downloading" -> {
            gettingPill(
                Regex("(\\d{1,3})%")
                    .find(words)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull(),
            )
        }

        "searching" -> {
            lookingPill(
                Regex("day (\\d+)")
                    .find(words.lowercase())
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull(),
            )
        }

        "waiting", "pending" -> {
            askedPill("waiting for Ben")
        }

        else -> {
            pillFromWords(words, AskKind.READ).let { if (it.state == AskState.READY) readyPill(AskKind.READ) else it }
        }
    }

/** The pill itself: coloured words on a soft tint of the same colour */
@Composable
fun AskPillChip(
    pill: AskPill,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(if (big) 99.dp else 6.dp))
                .background(pill.color.copy(alpha = .18f))
                .padding(horizontal = if (big) 14.dp else 8.dp, vertical = if (big) 8.dp else 2.dp),
    ) {
        Text(
            text = pill.words,
            fontSize = if (big) BenflixType.body else BenflixType.small,
            fontWeight = FontWeight.Bold,
            color = pill.color,
            maxLines = 1,
            softWrap = false,
        )
    }
}
