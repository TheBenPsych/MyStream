package com.github.damontecres.wholphin.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin

/*
 * Glowfin new look, Search (Ben picked 2026-11-22): on a TV the letters sit on ONE line under the search box
 * (mic, A to Z, Space, Delete, 123). They never pop out, never push the page and never cover the results.
 * Under them one line holds your recent searches (each with ✕) and the Browse genres and moods pill before you
 * type, and finished names while you type. Made for the TCL TVs: plain fills, no blur, nothing grows.
 */

/** One key on the letters line */
private data class LetterKey(
    val id: String,
    val text: String? = null,
    val icon: BenflixIcon? = null,
    val wide: Boolean = false,
)

private val LETTERS = ('A'..'Z').map { LetterKey(it.toString(), it.toString()) }
private val NUMBERS =
    ('1'..'9').map { LetterKey(it.toString(), it.toString()) } + LetterKey("0", "0") +
        listOf("&", "'", "-", ":", ".", "!", "?").map { LetterKey(it, it) }
private val MIC = LetterKey("mic", icon = BenflixIcon.MIC, wide = true)
private val SPACE = LetterKey("space", icon = BenflixIcon.SPACE, wide = true)
private val DELETE = LetterKey("del", icon = BenflixIcon.BACKSPACE, wide = true)
private val TO_NUMBERS = LetterKey("123", text = "123", wide = true)
private val TO_LETTERS = LetterKey("abc", text = "ABC", wide = true)

/** How many keys the line can hold at most (the requesters are made once) */
internal const val LETTER_KEYS = 32

/** The letters line: tracks which key you were last on, so Up from the results lands back on it */
internal class LetterLineState {
    val keys = List(LETTER_KEYS) { FocusRequester() }

    /** The key you were last on (-1: none yet, so the A) */
    var last = -1

    /** Where the A is (after the mic, when there is one) */
    var aIndex = 1

    /** The key the focus goes back to: the one you were last on (the A the first time) */
    fun target(): Int = if (last < 0) aIndex else last

    /** Put the focus back on the key you were last on (the A the first time) */
    fun focusLast(): Boolean = keys.getOrNull(target())?.let { runCatching { it.requestFocus() }.isSuccess } ?: false
}

/**
 * TV: the one line of letters. [onMic] is null when this TV can't listen (the mic key is left out then).
 * Hold Delete to clear everything. 123 swaps the letters for numbers and a few marks, ABC swaps back.
 */
@Composable
internal fun LetterLine(
    query: String,
    onQueryChange: (String) -> Unit,
    onMic: (() -> Unit)?,
    state: LetterLineState,
    modifier: Modifier = Modifier,
    onKeyFocused: (Int) -> Unit = {},
) {
    // Benflix "Type on your phone for the TV": phones with Glowfin open offer to type
    PhoneKeysForTv(query = query, onQueryChange = onQueryChange)
    var numbers by remember { mutableStateOf(false) }
    state.aIndex = if (onMic != null) 1 else 0
    val keys =
        listOfNotNull(onMic?.let { MIC }) + (if (numbers) NUMBERS else LETTERS) +
            listOf(SPACE, DELETE, if (numbers) TO_LETTERS else TO_NUMBERS)
    Row(
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        modifier = modifier.fillMaxWidth().height(KEY_HEIGHT),
    ) {
        keys.forEachIndexed { index, key ->
            LetterKeyButton(
                key = key,
                onClick = {
                    when (key.id) {
                        "mic" -> onMic?.invoke()
                        "space" -> if (query.isNotEmpty() && !query.endsWith(" ")) onQueryChange("$query ")
                        "del" -> onQueryChange(query.dropLast(1))
                        "123" -> numbers = true
                        "abc" -> numbers = false
                        else -> onQueryChange(query + key.text.orEmpty().lowercase())
                    }
                },
                onLongClick = if (key.id == "del") ({ onQueryChange("") }) else null,
                modifier =
                    Modifier
                        .focusRequester(state.keys[index])
                        .onFocusChanged {
                            if (it.isFocused) {
                                onKeyFocused(index)
                                state.last = index
                            }
                        },
            )
        }
    }
}

@Composable
private fun RowScope.LetterKeyButton(
    key: LetterKey,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(KEY_SHAPE),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = .1f),
                contentColor = Glowfin.Text,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
                pressedContainerColor = Color.White,
                pressedContentColor = Glowfin.Ink,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .12f)), shape = KEY_SHAPE),
                focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Focus), shape = KEY_SHAPE),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .weight(if (key.wide) WIDE_WEIGHT else 1f)
                .fillMaxHeight()
                .onFocusChanged { focused = it.isFocused },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val icon = key.icon
            if (icon != null) {
                BenflixIconImage(
                    icon,
                    if (focused) Glowfin.Ink else Glowfin.Text,
                    16.dp,
                    contentDescription =
                        when (key.id) {
                            "mic" -> "Talk"
                            "space" -> "Space"
                            else -> "Delete"
                        },
                )
            } else {
                Text(
                    text = key.text.orEmpty(),
                    fontSize = if (key.wide) BenflixType.tag else BenflixType.body,
                    fontWeight = if (key.wide) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The search box itself, a see-through pill showing what's typed (TV types with the letters under it, so it's
 * only a window onto the words). [onClick] (TV, results showing) brings the letters back.
 */
@Composable
internal fun SearchPill(
    query: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    val inside: @Composable (Color) -> Unit = { ink ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        ) {
            BenflixIconImage(BenflixIcon.SEARCH, ink, 16.dp)
            if (query.isEmpty()) {
                Text(
                    text = "Search movies, shows, music, books and people",
                    color = ink.copy(alpha = .62f),
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Text(
                    text = query,
                    color = ink,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            // The caret: a still aqua line (no blinking, so nothing redraws on the weak TVs)
            if (!small) Box(Modifier.size(width = 1.5.dp, height = 18.dp).background(Glowfin.Aqua))
        }
    }
    if (onClick == null) {
        Box(
            modifier
                .height(PILL_HEIGHT)
                .background(Color.White.copy(alpha = .12f), shape)
                .border(1.dp, Color.White.copy(alpha = .2f), shape),
        ) { inside(Glowfin.Text) }
        return
    }
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = .12f),
                contentColor = Glowfin.Text,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .2f)), shape = shape),
                focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Focus), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(PILL_HEIGHT).onFocusChanged { focused = it.isFocused },
    ) { inside(if (focused) Glowfin.Ink else Glowfin.Text) }
}

/**
 * The line under the letters before you type: "Recent", your recent searches as pills (each with its own ✕),
 * then the Browse genres and moods pill.
 */
@Composable
internal fun RecentLine(
    recent: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBrowse: (() -> Unit)?,
    modifier: Modifier = Modifier,
    edge: Dp = 0.dp,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = edge),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (recent.isNotEmpty()) {
            item(key = "label") {
                Text(
                    text = "Recent",
                    color = Glowfin.Text.copy(alpha = .7f),
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
        }
        items(recent, key = { "r-$it" }) { text ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                LinePill(text = AnnotatedString(text), onClick = { onPick(text) })
                LinePill(text = AnnotatedString("✕"), onClick = { onRemove(text) }, round = true, contentDescription = "Remove $text")
            }
        }
        if (onBrowse != null) {
            item(key = "browse") {
                LinePill(text = AnnotatedString("Browse genres and moods"), onClick = onBrowse, ghost = true)
            }
        }
    }
}

/** The line under the letters while you type: finished names, the typed letters in bold. OK searches it. */
@Composable
internal fun FinishedNamesLine(
    query: String,
    names: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTry: (String) -> Unit = {},
    edge: Dp = 0.dp,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = edge),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(names, key = { it }) { name ->
            LinePill(
                text = withTypedBold(name, query),
                onClick = { onPick(name) },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onTry(name) },
            )
        }
    }
}

/** One see-through pill on the line under the letters (white with an aqua ring when you're on it) */
@Composable
internal fun LinePill(
    text: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    ghost: Boolean = false,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (ghost) Color.Transparent else Glowfin.Ink.copy(alpha = .4f),
                contentColor = if (round) Glowfin.Text.copy(alpha = .65f) else Glowfin.Text,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
                pressedContainerColor = Color.White,
                pressedContentColor = Glowfin.Ink,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = if (ghost) .3f else .16f)), shape = shape),
                focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Focus), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .height(LINE_HEIGHT)
                .then(if (round) Modifier.width(LINE_HEIGHT) else Modifier)
                .then(contentDescription?.let { d -> Modifier.semantics { this.contentDescription = d } } ?: Modifier),
    ) {
        Box(
            Modifier.fillMaxHeight().padding(horizontal = if (round) 0.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Phones: the round mic beside the search box */
@Composable
internal fun MicPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = .12f),
                contentColor = Glowfin.Text,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.size(40.dp).onFocusChanged { focused = it.isFocused },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BenflixIconImage(BenflixIcon.MIC, if (focused) Glowfin.Ink else Glowfin.Text, 18.dp, contentDescription = "Talk")
        }
    }
}

/** TV, bottom right before you type: a phone with Glowfin open can type into the TV's Search */
@Composable
internal fun PhoneTypeHint(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(width = 11.dp, height = 18.dp)
                .border(1.5.dp, Glowfin.Text.copy(alpha = .62f), RoundedCornerShape(3.dp)),
        )
        Text(
            text = "Phone with Glowfin open? Type there",
            color = Glowfin.Text.copy(alpha = .62f),
            fontSize = BenflixType.small,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** How tall the pills on the line under the letters are */
internal val LINE_HEIGHT = 30.dp
internal val PILL_HEIGHT = 36.dp
internal val KEY_HEIGHT = 34.dp
private val KEY_GAP = 3.dp
private const val WIDE_WEIGHT = 1.45f
private val KEY_SHAPE = RoundedCornerShape(7.dp)
