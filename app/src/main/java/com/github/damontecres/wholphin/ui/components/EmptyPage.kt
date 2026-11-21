package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * One look for every page that has nothing to show (Ben 2026-11-20): the page's icon in a soft tile, a plain
 * heading, one or two lines saying why, and buttons for what to do next. On a TV the icon sits beside the
 * words; on a phone everything is stacked in the middle.
 */
@Composable
fun EmptyPage(
    icon: BenflixIcon,
    title: String,
    line: String,
    modifier: Modifier = Modifier,
    more: String? = null,
    buttons: (@Composable () -> Unit)? = null,
) {
    val isTv = rememberIsTv()
    val tile: @Composable () -> Unit = {
        val shape = RoundedCornerShape(if (isTv) 30.dp else 22.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(if (isTv) 112.dp else 80.dp)
                    .background(BenflixColors.Panel, shape)
                    .border(1.dp, BenflixColors.Line, shape),
        ) {
            BenflixIconImage(icon, BenflixColors.Focus, if (isTv) 52.dp else 38.dp)
        }
    }
    val words: @Composable (TextAlign) -> Unit = { align ->
        Text(
            text = title,
            color = BenflixColors.Text,
            fontFamily = GlowfinDisplayFont,
            fontSize = BenflixType.pageTitle,
            fontWeight = FontWeight.ExtraBold,
            textAlign = align,
        )
        Text(
            text = line,
            color = BenflixColors.Soft,
            fontSize = BenflixType.body,
            lineHeight = BenflixType.body * 1.4f,
            textAlign = align,
        )
        if (!more.isNullOrBlank()) {
            Text(
                text = more,
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small * 1.4f,
                textAlign = align,
            )
        }
    }
    if (isTv) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            modifier = modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp),
        ) {
            tile()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 520.dp)) {
                words(TextAlign.Start)
                if (buttons != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) { buttons() }
                }
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            modifier = modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            tile()
            words(TextAlign.Center)
            if (buttons != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { buttons() }
            }
        }
    }
}
