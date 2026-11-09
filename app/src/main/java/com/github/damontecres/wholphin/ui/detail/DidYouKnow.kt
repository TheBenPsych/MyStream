package com.github.damontecres.wholphin.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import kotlinx.coroutines.delay

/** How long each fact stays before fading to the next */
const val DID_YOU_KNOW_SECONDS = 10L

/**
 * Benflix "Did you know" on a movie or show page: one fact about that title at a time, fading to the
 * next every [DID_YOU_KNOW_SECONDS] seconds, with small dots for how many there are. Nothing shows
 * when the title has no facts. [card] gives it a panel behind it (phones); on TV it sits under the
 * side column with a thin coloured line on its left, in the door's colour ([accent]).
 */
@Composable
fun DidYouKnow(
    facts: List<String>,
    accent: Color,
    card: Boolean,
    modifier: Modifier = Modifier,
) {
    if (facts.isEmpty()) return
    var index by remember(facts) { mutableIntStateOf(0) }
    LaunchedEffect(facts) {
        if (facts.size < 2) return@LaunchedEffect
        while (true) {
            delay(DID_YOU_KNOW_SECONDS * 1000)
            index = (index + 1) % facts.size
        }
    }
    val inside: @Composable () -> Unit = {
        Column {
            Text(
                text = "DID YOU KNOW",
                color = accent,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
            AnimatedContent(
                targetState = index,
                transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
                label = "did-you-know",
            ) { i ->
                Text(
                    text = facts.getOrElse(i) { facts.first() },
                    color = BenflixColors.Text,
                    fontSize = BenflixType.body,
                    // Room for 3 lines even for a short fact, so nothing around it jumps
                    minLines = 3,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (facts.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                    // At most 8 dots, so a title with lots of facts doesn't get a long line of them
                    val shown = facts.size.coerceAtMost(8)
                    for (d in 0 until shown) {
                        val on = d == index % shown
                        Box(
                            Modifier
                                .height(4.dp)
                                .width(if (on) 12.dp else 4.dp)
                                .background(if (on) BenflixColors.Soft else BenflixColors.Line, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }
    if (card) {
        Box(
            modifier
                .fillMaxWidth()
                .background(BenflixColors.Panel, RoundedCornerShape(10.dp))
                .border(1.dp, BenflixColors.Line, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) { inside() }
    } else {
        Row(modifier) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(64.dp)
                    .background(accent),
            )
            Box(Modifier.padding(start = 10.dp)) { inside() }
        }
    }
}
