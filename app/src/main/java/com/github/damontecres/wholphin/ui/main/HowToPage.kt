package com.github.damontecres.wholphin.ui.main

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixTips
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

private data class HowToTip(
    val title: String,
    val body: String,
)

private val remoteTips =
    listOf(
        HowToTip("OK (the middle button)", "Opens whatever is highlighted. In a movie it shows or hides the controls."),
        HowToTip("Back", "Goes back one page. While watching, it stops the movie and saves your spot."),
        HowToTip("Arrows", "Move around the menus. While watching, Left and Right skip back and forward."),
        HowToTip("Up and Down while watching Channels", "Flips to the next or previous channel, like a normal TV."),
        HowToTip("Play / Pause", "Pauses or plays, if your remote has that button."),
        HowToTip("Missed a line?", "While watching, press Left twice quickly. It jumps back and shows subtitles for a few seconds."),
    )

private val phoneTips =
    listOf(
        HowToTip("Tap", "Opens a movie or show. Tap the video while watching to show the controls."),
        HowToTip("Press and hold a poster", "Shows quick info without opening the page."),
        HowToTip("Bottom bar", "Home, Search, Clips, Channels and My Benflix are always at the bottom."),
    )

private val generalTips =
    listOf(
        HowToTip("Pick up where you left off", "Anything you stopped part way through is in Continue watching on Home."),
        HowToTip("Skip intro", "A Skip intro button pops up at the start of most episodes. Credits can be skipped too."),
        HowToTip("My List", "Open a movie or show and press + My List to save it for later."),
        HowToTip("Can't find something?", "Search for it. If it isn't on Benflix yet, you can ask Ben to add it."),
        HowToTip("Download to your phone", "On a phone, open a movie and press Download (Wi-Fi only). Find it in My Benflix, Downloads."),
        HowToTip("Kids", "Pick the Kids profile on the Who's watching screen. It only shows titles rated PG or lower."),
        HowToTip("Switch profile", "Press your profile picture at the top (TV) or in My Benflix (phone)."),
        HowToTip("Something to watch, fast", "Try Channels, Trailer shuffle or the Mood quiz under More."),
        HowToTip("Bigger text", "Turn on Easy-read mode in Settings, Look, for bigger and brighter text."),
        HowToTip("Play on TV", "On your phone, open a movie or episode and press Play on TV. Benflix needs to be open on the TV."),
        HowToTip("Kids bedtime", "Set a Parent PIN in My Benflix. Kids then goes to bed at 9 PM, and your PIN gives 30 more minutes."),
        HowToTip("Random episodes", "On a show's page, press Random episodes. It keeps playing random ones until you press Back."),
        HowToTip("Getting a new version", "When there's an update, Settings shows Install update at the top."),
    )

/**
 * Benflix: a simple help page for family and friends. What the remote does and a few handy tips.
 */
@Composable
fun HowToPage(modifier: Modifier = Modifier) {
    val isTv = rememberIsTv()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = "HOW TO USE BENFLIX",
                fontFamily = BenflixTitleFont,
                fontSize = 44.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        val sections =
            listOf(
                (if (isTv) "The remote" else "Using your phone") to (if (isTv) remoteTips else phoneTips),
                "Handy tips" to generalTips,
            )
        sections.forEachIndexed { sectionIndex, (title, tips) ->
            item {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(tips) { index, tip ->
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = {
                        Text(
                            text = tip.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = tip.body,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (sectionIndex == 0 && index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            ),
                )
            }
        }
        item {
            val context = LocalContext.current
            ListItem(
                selected = false,
                onClick = {
                    BenflixTips.reset(context)
                    Toast.makeText(context, "Tips will show again", Toast.LENGTH_SHORT).show()
                },
                headlineContent = { Text("Show tips again", fontSize = 18.sp, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Text(
                        "The small first-time tips on Home, Search and in episodes",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            )
        }
    }
}
