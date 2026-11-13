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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.door.DoorViewModel
import com.github.damontecres.wholphin.ui.main.BenflixGenre
import com.github.damontecres.wholphin.ui.main.GenreTile
import com.github.damontecres.wholphin.ui.main.MoodRow
import com.github.damontecres.wholphin.ui.main.openGenreHub
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Benflix recent searches, kept per account (Kids has its own list, Guest keeps none).
 * Only the last 6 are kept (approved).
 */
object RecentSearches {
    private const val PREFS = "benflix_search"
    private const val MAX = 6

    private fun key(account: String?): String? =
        when {
            account.isNullOrBlank() || GuestMode.isActive -> null
            KidsMode.isActive -> "recent_${account}_kids"
            else -> "recent_$account"
        }

    fun get(
        context: Context,
        account: String?,
    ): List<String> {
        val key = key(account) ?: return emptyList()
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.take(MAX)
            .orEmpty()
    }

    fun add(
        context: Context,
        account: String?,
        query: String,
    ) {
        val key = key(account) ?: return
        val q = query.trim()
        if (q.length < 2) return
        val list = (listOf(q) + get(context, account).filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(key, list.joinToString("\n")) }
    }

    /** The ✕ beside one recent search */
    fun remove(
        context: Context,
        account: String?,
        query: String,
    ) {
        val key = key(account) ?: return
        val list = get(context, account).filterNot { it.equals(query, ignoreCase = true) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(key, list.joinToString("\n")) }
    }

    /** Clear all */
    fun clear(
        context: Context,
        account: String?,
    ) {
        val key = key(account) ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(key) }
    }
}

/**
 * What Search shows before you type (approved se-start): recent searches, Popular, Browse tiles
 * (Genres, Networks, Channel types, Consoles), then moods and genres further down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchStart(
    navigationManager: NavigationManager,
    onClickRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
    account: String? = null,
    popular: List<com.github.damontecres.wholphin.data.model.BaseItem> = emptyList(),
    onClickItem: (com.github.damontecres.wholphin.data.model.BaseItem) -> Unit = {},
    /** TV keeps recent searches and Popular in the left column instead */
    showRecent: Boolean = true,
    showPopular: Boolean = true,
) {
    val context = LocalContext.current
    var recent by remember(account) { mutableStateOf(RecentSearches.get(context, account)) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = modifier,
    ) {
        if (showRecent && recent.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("RECENT")
                    // Redesign 2: each recent search has its own ✕, and Clear all empties the list
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recent.forEach { text ->
                            RecentChip(
                                text = text,
                                onClick = { onClickRecent(text) },
                                onRemove = {
                                    RecentSearches.remove(context, account, text)
                                    recent = RecentSearches.get(context, account)
                                },
                            )
                        }
                        Button(
                            onClick = {
                                RecentSearches.clear(context, account)
                                recent = emptyList()
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors =
                                ButtonDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color(0xFFA69C9D),
                                    focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                                    focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
                                ),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                        ) {
                            Text(text = "Clear all", fontSize = BenflixType.body)
                        }
                    }
                }
            }
        }
        if (showPopular && popular.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("POPULAR")
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        items(popular.size) { index ->
                            val item = popular[index]
                            com.github.damontecres.wholphin.ui.door.DoorPoster(
                                item = item,
                                width = 104.dp,
                                showName = !rememberIsTv(),
                                onClick = { onClickItem(item) },
                            )
                        }
                    }
                }
            }
        }
        if (!KidsMode.isActive) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("BROWSE")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        val tile = Modifier.width(170.dp)
                        com.github.damontecres.wholphin.ui.door.BrowseTile("Genres", {
                            navigationManager.navigateTo(Destination.DoorBrowse(BenflixDoor.MOVIES.name, DoorViewModel.BROWSE_GENRES))
                        }, tile)
                        com.github.damontecres.wholphin.ui.door.BrowseTile("Networks", {
                            navigationManager.navigateTo(Destination.DoorBrowse(BenflixDoor.SHOWS.name, DoorViewModel.BROWSE_STUDIOS))
                        }, tile)
                        com.github.damontecres.wholphin.ui.door.BrowseTile("Channel types", {
                            navigationManager.navigateTo(Destination.Channels)
                        }, tile)
                        com.github.damontecres.wholphin.ui.door.BrowseTile("Consoles", {
                            navigationManager.navigateTo(Destination.Games)
                        }, tile)
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
                StartButton("Can't decide? Play This or that") { navigationManager.navigateTo(Destination.ThisOrThat()) }
                StartButton("Watching together? Take the mood quiz") { navigationManager.navigateTo(Destination.MoodQuiz()) }
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

/** One recent search with its own ✕ */
@Composable
private fun RecentChip(
    text: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Button(
            onClick = onClick,
            shape = ButtonDefaults.shape(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)),
            contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            colors =
                ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = .1f),
                    contentColor = Color(0xFFE8E2E2),
                    focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                    focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
                ),
            scale = ButtonDefaults.scale(focusedScale = 1f),
        ) {
            Text(text = text, fontSize = BenflixType.body)
        }
        Button(
            onClick = onRemove,
            shape = ButtonDefaults.shape(RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)),
            contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            colors =
                ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = .1f),
                    contentColor = Color(0xFFA69C9D),
                    focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                    focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
                ),
            scale = ButtonDefaults.scale(focusedScale = 1f),
        ) {
            Text(text = "✕", fontSize = BenflixType.body)
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(
        text = text,
        fontSize = BenflixType.small,
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
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .25f),
                contentColor = Color.White,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        Text(text = text, fontSize = BenflixType.body, fontWeight = FontWeight.SemiBold)
    }
}
