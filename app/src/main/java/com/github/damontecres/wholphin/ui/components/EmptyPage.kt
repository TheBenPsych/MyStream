package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
 * One look for every page that has nothing to show (Ben 2026-11-20): a soft picture with the page's icon, a plain
 * heading, one or two lines saying why, and buttons for what to do next (the first one solid white). On a TV the icon sits beside the
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
    // New look (Ben 2026-11-22): a soft little picture instead of a plain grey tile
    val tile: @Composable () -> Unit = {
        SoftArtTile {
            BenflixIconImage(icon, BenflixColors.Text, if (isTv) 44.dp else 34.dp)
        }
    }
    val words: @Composable (TextAlign) -> Unit = { align ->
        Text(
            text = title,
            color = BenflixColors.Text,
            fontFamily = GlowfinDisplayFont,
            fontSize = BenflixType.pageTitle,
            // Design check (Ben 2026-11-24): a two-line heading ("Couldn't load your books") had its lines on top of each other
            lineHeight = BenflixType.pageTitle * 1.15f,
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

/**
 * Glowfin new look "Friendly empty and error pages" (Ben 2026-11-22): a page that couldn't load gets the soft
 * picture, one plain sentence and one clear white button, instead of grey text.
 */
@Composable
fun ProblemPage(
    title: String,
    line: String,
    modifier: Modifier = Modifier,
    buttonLabel: String = "Try again",
    icon: BenflixIcon = BenflixIcon.WARNING,
    onClick: (() -> Unit)? = null,
) {
    EmptyPage(
        icon = icon,
        title = title,
        line = line,
        modifier = modifier,
        buttons =
            onClick?.let {
                {
                    com.github.damontecres.wholphin.ui.music.MusicButton(
                        label = buttonLabel,
                        icon = BenflixIcon.REFRESH,
                        onClick = it,
                        primary = true,
                    )
                }
            },
    )
}
