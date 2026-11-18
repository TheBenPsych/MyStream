package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixMotion
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Benflix notices (Redesign 2, section 3, approved nt-levels 2026-11-10). Every notice is one of three levels,
 * and each level always looks the same and sits in the same place:
 *  - Quiet: a row in Home's list plus the white dot on your picture ([HomeNewsDot]).
 *  - Tap on the shoulder: a small [Card] under the top bar on the right for 7 seconds, with a thin timer line.
 *    It never takes the remote's focus; pressing OK while it shows opens it (or just ignore it). One at a time,
 *    oldest first. While something plays, only reminders and messages show.
 *  - Needs you: a side panel (bottom sheet on phones) that waits for Got it. Only messages from Ben, the 3-day
 *    update and Couldn't find it use it (they use [BasicDialog], which is the shared panel).
 * Quick "done" messages are one white [pill] at the bottom, for 3 seconds (instead of Android's toasts).
 */
object BenflixNotices {
    data class Card(
        val id: String,
        val title: String,
        val line: String,
        val image: String? = null,
        /** Reminders you set and messages from Ben: these show even while something plays */
        val whilePlaying: Boolean = false,
        /** What OK (or a tap) does; null = just reading */
        val onOpen: (() -> Unit)? = null,
        /** The 7 seconds ran out without OK */
        val onTimeout: (() -> Unit)? = null,
    )

    private val _cards = MutableStateFlow<List<Card>>(emptyList())

    /** Cards still to show, oldest first */
    val cards: StateFlow<List<Card>> = _cards

    /** The card on screen right now (OK opens it) */
    @Volatile
    internal var showing: Card? = null

    /** Shows a corner card (once per [Card.id] while it's waiting) */
    fun card(card: Card) {
        _cards.update { list -> if (list.any { it.id == card.id }) list else list + card }
    }

    /** Takes a card away without it counting as seen (its page closed) */
    fun drop(id: String) {
        _cards.update { list -> list.filter { it.id != id } }
        if (showing?.id == id) showing = null
    }

    internal fun timedOut(card: Card) {
        drop(card.id)
        card.onTimeout?.invoke()
    }

    /** OK was pressed while a card that can be opened was showing. True when it was used. */
    fun openShowing(): Boolean {
        val card = showing ?: return false
        val open = card.onOpen ?: return false
        drop(card.id)
        open()
        return true
    }

    /** True when OK should go to the corner card instead of the page */
    fun wantsOk(): Boolean = showing?.onOpen != null

    private val pillState = MutableStateFlow<Pair<String, Long>?>(null)
    internal val pillText: StateFlow<Pair<String, Long>?> get() = pillState

    /** A quick "done" message: one white Benflix pill at the bottom for 3 seconds */
    fun pill(text: CharSequence?) {
        val words = text?.toString()?.trim().orEmpty()
        if (words.isEmpty()) return
        pillState.value = words to System.nanoTime()
    }

    /** The same pill with words from the app's strings */
    fun pill(
        @androidx.annotation.StringRes words: Int,
    ) {
        pill(
            com.github.damontecres.wholphin.WholphinApplication.instance
                .getString(words),
        )
    }

    const val CARD_MS = 7_000L
    const val PILL_MS = 3_000L
}

/**
 * The white dot on your picture. It only means "there's news on Home you haven't looked at yet". Server problems
 * live on the Server page and updates are a Home row, so nothing else lights it.
 */
object HomeNewsDot {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on

    fun set(on: Boolean) {
        _on.value = on && !KidsMode.isActive && !GuestMode.isActive
    }
}

/**
 * Draws the corner card and the pill on top of every page. [currentDestination] decides what may show:
 * while something plays only reminders and messages do.
 */
@Composable
fun BoxScope.BenflixNoticeLayer(currentDestination: Destination?) {
    val playing = currentDestination is Destination.Playback || currentDestination is Destination.PlaybackList
    val cards by BenflixNotices.cards.collectAsState()
    val card = cards.firstOrNull { !playing || it.whilePlaying }
    val isTv = rememberIsTv()
    DisposableEffect(card?.id) {
        BenflixNotices.showing = card
        onDispose { if (BenflixNotices.showing?.id == card?.id) BenflixNotices.showing = null }
    }
    AnimatedVisibility(
        visible = card != null,
        enter = fadeIn(BenflixMotion.enter()) + slideInHorizontally(BenflixMotion.enter()) { it / 6 },
        exit = fadeOut(BenflixMotion.exit()),
        modifier =
            Modifier
                .align(if (isTv) Alignment.TopEnd else Alignment.TopCenter)
                .then(if (isTv) Modifier.padding(top = 76.dp, end = 32.dp) else Modifier.statusBarsPadding().padding(12.dp)),
    ) {
        val shown = remember(card?.id) { card } ?: return@AnimatedVisibility
        CornerCard(shown, isTv)
    }
    Pill(Modifier.align(Alignment.BottomCenter))
}

@Composable
private fun CornerCard(
    card: BenflixNotices.Card,
    isTv: Boolean,
) {
    val timer = remember(card.id) { Animatable(0f) }
    LaunchedEffect(card.id) {
        timer.animateTo(1f, tween(BenflixNotices.CARD_MS.toInt(), easing = LinearEasing))
        BenflixNotices.timedOut(card)
    }
    val shape = RoundedCornerShape(if (isTv) 16.dp else 14.dp)
    Column(
        modifier =
            Modifier
                .then(if (isTv) Modifier.widthIn(min = 300.dp, max = 400.dp) else Modifier.fillMaxWidth())
                .clip(shape)
                .background(BenflixColors.Panel)
                .border(1.dp, BenflixColors.Line, shape)
                // Never takes the remote's focus: on a TV, OK opens it; on a phone, a tap does
                .focusProperties { canFocus = false }
                .then(
                    if (card.onOpen != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { BenflixNotices.openShowing() }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            if (card.image != null) {
                AsyncImage(
                    model = card.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(if (isTv) 52.dp else 46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BenflixColors.PanelHigh),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = card.title,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val hint =
                    when {
                        card.onOpen == null -> ""
                        isTv -> " · OK to open"
                        else -> " · Tap to open"
                    }
                Text(
                    text = card.line + hint,
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The thin timer line: it shrinks as the 7 seconds run out (drawn only, so it's cheap on older TVs)
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = 1f - timer.value
                }.background(BenflixColors.Soft),
        )
    }
}

@Composable
private fun Pill(modifier: Modifier = Modifier) {
    val pill by BenflixNotices.pillText.collectAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(pill) {
        if (pill == null) return@LaunchedEffect
        visible = true
        delay(BenflixNotices.PILL_MS)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(BenflixMotion.enter()),
        exit = fadeOut(BenflixMotion.exit()),
        modifier = modifier.navigationBarsPadding().padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
    ) {
        Text(
            text = pill?.first.orEmpty(),
            color = BenflixColors.Tray,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .background(BenflixColors.Text, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }
}
