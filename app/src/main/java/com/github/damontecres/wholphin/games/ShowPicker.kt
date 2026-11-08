package com.github.damontecres.wholphin.games

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * "Mini Benflix": a full-screen picker for the show that plays next to the game.
 * Tabs (Continue watching, Next up, My List, Movies, Shows, Search), posters, and a season/episode list for shows.
 */
@Composable
fun ShowPicker(
    show: SideShow?,
    isTv: Boolean,
    tint: Color,
    firstFocus: FocusRequester,
    registerBack: ((() -> Unit)?) -> Unit,
    onPick: (SideShowItem) -> Unit,
    onCancel: () -> Unit,
) {
    var tab by remember { mutableStateOf(ShowTab.RESUME) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SideShowItem>?>(null) }
    var series by remember { mutableStateOf<SideShowItem?>(null) }

    DisposableEffect(series) {
        registerBack { if (series != null) series = null else onCancel() }
        onDispose { registerBack(null) }
    }
    LaunchedEffect(tab, query) {
        results = null
        if (show == null) {
            results = emptyList()
            return@LaunchedEffect
        }
        if (tab == ShowTab.SEARCH) delay(450)
        results = withContext(Dispatchers.IO) { runCatching { show.list(tab, query) }.getOrDefault(emptyList()) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), Color(0xFF0D0B0C), Color(0xFF0D0B0C))))
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 28.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 10.dp),
    ) {
        val pickedSeries = series
        if (pickedSeries != null && show != null) {
            Episodes(show, pickedSeries, isTv, firstFocus, onBack = { series = null }, onPick = onPick)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerChip("Back", Modifier.focusRequester(firstFocus), icon = true, onClick = onCancel)
            Text(
                "Watch on the side",
                color = Color.White,
                fontSize = if (isTv) 26.sp else 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShowTab.entries) { t ->
                PickerChip(
                    t.label,
                    selected = t == tab,
                    onFocus = { if (isTv) tab = t },
                    onClick = { tab = t },
                )
            }
        }
        if (tab == ShowTab.SEARCH) {
            SearchBox(query, isTv) { query = it }
        }
        val list = results
        when {
            list == null -> {
                Text("Loading…", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }

            list.isEmpty() -> {
                Text(
                    when (tab) {
                        ShowTab.SEARCH -> if (query.isBlank()) "Type or say what you want to watch." else "Nothing found for \"$query\"."
                        ShowTab.MY_LIST -> "Nothing in My List yet. Add things with the heart on Benflix."
                        else -> "Nothing here yet."
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTv) 132.dp else 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { item ->
                        Poster(item, isTv) {
                            if (item.type == "Series") series = item else onPick(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    isTv: Boolean,
    onChange: (String) -> Unit,
) {
    val voice =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let(onChange)
        }
    var micMissing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            interactionSource = interaction,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            modifier =
                Modifier
                    .width(if (isTv) 420.dp else 220.dp)
                    .background(Color(0xFF242424), RoundedCornerShape(24.dp))
                    .border(2.dp, if (focused) Color.White else Color(0xFF3A3A3A), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    Box(Modifier.padding(start = 10.dp)) {
                        if (query.isEmpty()) Text("Movies or shows", color = Color.White.copy(alpha = 0.45f), fontSize = 18.sp)
                        inner()
                    }
                }
            },
        )
        PickerChip(if (micMissing) "No mic here" else "🎤 Say it") {
            try {
                voice.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to watch?"),
                )
            } catch (_: ActivityNotFoundException) {
                micMissing = true
            }
        }
    }
}

/** A show's seasons along the top, its episodes below */
@Composable
private fun Episodes(
    show: SideShow,
    series: SideShowItem,
    isTv: Boolean,
    firstFocus: FocusRequester,
    onBack: () -> Unit,
    onPick: (SideShowItem) -> Unit,
) {
    var seasons by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var season by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<SideShowItem>?>(null) }
    LaunchedEffect(series.id) {
        val s = withContext(Dispatchers.IO) { runCatching { show.seasons(series.id) }.getOrDefault(emptyList()) }
        seasons = s
        season = s.firstOrNull()?.first
    }
    LaunchedEffect(season) {
        val id = season ?: return@LaunchedEffect
        episodes = null
        episodes = withContext(Dispatchers.IO) { runCatching { show.episodes(series.id, id) }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PickerChip("Back", Modifier.focusRequester(firstFocus), icon = true, onClick = onBack)
        Text(
            series.title,
            color = Color.White,
            fontSize = if (isTv) 26.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    seasons?.takeIf { it.size > 1 }?.let { list ->
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { (id, name) ->
                PickerChip(name, selected = id == season, onFocus = { if (isTv) season = id }, onClick = { season = id })
            }
        }
    }
    val list = episodes
    when {
        seasons == null || (season != null && list == null) -> {
            Text("Loading…", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
        }

        list.isNullOrEmpty() -> {
            Text("No episodes found.", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
        }

        else -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { ep -> EpisodeRow(ep, isTv) { onPick(ep) } }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: SideShowItem,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (focused) Color.White else Color(0xFF1C1C1C), shape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = if (isTv) 12.dp else 10.dp),
    ) {
        Text(
            ep.subtitle,
            color = if (focused) Color.Black else Color.White,
            fontSize = if (isTv) 18.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (ep.progress > 0.02f) ProgressLine(ep.progress, Modifier.padding(top = 6.dp).width(160.dp))
    }
}

@Composable
private fun Poster(
    item: SideShowItem,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Color(0xFF242424))
                .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape),
        ) {
            Text(
                item.title,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            AsyncImage(
                model = item.image,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.progress > 0.02f) {
                ProgressLine(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
            }
        }
        Text(
            item.title,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = if (isTv) 14.sp else 12.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isTv) 12.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressLine(
    progress: Float,
    modifier: Modifier,
) {
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(BenflixColors.Text),
        )
    }
}

@Composable
private fun PickerChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: Boolean = false,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(22.dp)
    val bg =
        when {
            focused -> Color.White
            selected -> Color.White.copy(alpha = 0.22f)
            else -> Color(0xFF242424)
        }
    val fg = if (focused) Color.Black else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .onFocusChanged { if (it.isFocused) onFocus() }
                .background(bg, shape)
                .border(1.dp, if (selected && !focused) Color.White.copy(alpha = 0.6f) else Color.Transparent, shape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (icon) Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
