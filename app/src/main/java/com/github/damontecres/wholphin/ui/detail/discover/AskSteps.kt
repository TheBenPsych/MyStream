package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * "What happens next" on a title that isn't on Glowfin yet (Ben 2026-11-20): three plain steps, so nobody
 * wonders what asking does or where their ask has got to. [at] is the step the ask is on now: 0 before
 * anyone has asked, 1 waiting for a yes, 2 being fetched. Side by side on a TV, stacked on a phone.
 */
@Composable
fun AskStepsBlock(
    at: Int,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    val yesLine =
        when {
            isAdmin -> "Your own asks start right away."
            at == 1 -> "Waiting now. Usually within a day."
            else -> "Usually within a day."
        }
    val fetchLine = if (at == 2) "Getting it now. Most take an hour or two." else "It finds a good copy. Most take an hour or two."
    val steps =
        listOf(
            (if (isAdmin) "It gets a yes" else "Ben says yes") to yesLine,
            "Glowfin fetches it" to fetchLine,
            "Ready to watch" to "It lands in Recently added on Home.",
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .background(BenflixColors.Panel, RoundedCornerShape(14.dp))
                .border(1.dp, BenflixColors.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = "What happens next",
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
        )
        if (isTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                steps.forEachIndexed { index, (name, line) ->
                    AskStep(index + 1, name, line, now = at == index + 1, done = at > index + 1, modifier = Modifier.weight(1f))
                }
            }
        } else {
            steps.forEachIndexed { index, (name, line) ->
                AskStep(index + 1, name, line, now = at == index + 1, done = at > index + 1, modifier = Modifier.fillMaxWidth())
            }
        }
        // How far along the ask is
        Box(Modifier.fillMaxWidth().height(5.dp).background(BenflixColors.Line, CircleShape)) {
            if (at > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(if (at == 1) 0.17f else 0.5f)
                        .fillMaxHeight()
                        .background(Glowfin.GlowAcross, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun AskStep(
    number: Int,
    name: String,
    line: String,
    now: Boolean,
    done: Boolean,
    modifier: Modifier,
) {
    val lit = now || done
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(30.dp)
                    .then(
                        if (lit) {
                            Modifier.background(Glowfin.Glow, CircleShape)
                        } else {
                            Modifier.border(2.dp, BenflixColors.Muted, CircleShape)
                        },
                    ),
        ) {
            Text(
                text = number.toString(),
                color = if (lit) Glowfin.Ink else BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                color = if (now) BenflixColors.Text else BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = line,
                color = if (now) BenflixColors.Soft else BenflixColors.Muted,
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small * 1.35f,
            )
        }
    }
}
