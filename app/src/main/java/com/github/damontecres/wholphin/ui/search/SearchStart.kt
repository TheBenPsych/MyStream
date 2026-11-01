package com.github.damontecres.wholphin.ui.search

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.main.BenflixGenre
import com.github.damontecres.wholphin.ui.main.GenreTile
import com.github.damontecres.wholphin.ui.main.MoodRow
import com.github.damontecres.wholphin.ui.main.openGenreHub
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/** The last few things searched for on this device */
object RecentSearches {
    private const val PREFS = "benflix_search"
    private const val KEY = "recent"
    private const val MAX = 6

    fun get(context: Context): List<String> =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun add(
        context: Context,
        query: String,
    ) {
        val q = query.trim()
        if (q.length < 2) return
        val list = (listOf(q) + get(context).filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, list.joinToString("\n")) }
    }
}

/**
 * What Search shows before you type: recent searches, moods and genres to browse
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchStart(
    navigationManager: NavigationManager,
    onClickRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recent = remember { RecentSearches.get(context) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = modifier,
    ) {
        if (recent.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("RECENT")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { text ->
                            Button(
                                onClick = { onClickRecent(text) },
                                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors =
                                    ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = .1f),
                                        contentColor = Color(0xFFE8E2E2),
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black,
                                    ),
                            ) {
                                Text(text = text, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                StartButton("Watching together? Take the mood quiz") { navigationManager.navigateTo(Destination.MoodQuiz) }
                StartButton("Watch in order: Marvel, Star Wars and more") { navigationManager.navigateTo(Destination.WatchOrder) }
            }
        }
        item {
            MoodRow(
                selected = null,
                onClickMood = { navigationManager.navigateTo(Destination.Mood(it.name)) },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Browse by genre",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
                // Phones: two tiles side by side, sharing the width (a fixed tile width left only one per line)
                val isTv = rememberIsTv()
                if (isTv) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) {
                        BenflixGenre.entries.forEach { genre ->
                            GenreTile(genre = genre, onClick = { navigationManager.openGenreHub(genre) })
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) {
                        BenflixGenre.entries.chunked(2).forEach { pair ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                pair.forEach { genre ->
                                    GenreTile(
                                        genre = genre,
                                        onClick = { navigationManager.openGenreHub(genre) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp,
        color = Color(0xFFA69C9D),
        modifier = Modifier.padding(start = 8.dp),
    )

@Composable
private fun StartButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .25f),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
