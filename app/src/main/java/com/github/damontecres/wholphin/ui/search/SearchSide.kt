package com.github.damontecres.wholphin.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * Redesign 2 search, TV: the column under the search box. Before you type it holds your last 6 searches (each
 * with a ✕, plus Clear all) and Popular. While you type it holds whole names that match: landing on one shows
 * its results on the right, OK picks it and moves into the results.
 */
@Composable
fun SearchSideColumn(
    query: String,
    suggestions: List<String>,
    account: String?,
    popular: List<BaseItem>,
    onTry: (String) -> Unit,
    onPick: (String) -> Unit,
    onClickPopular: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var recent by remember(account, query.isBlank()) { mutableStateOf(RecentSearches.get(context, account)) }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 40.dp),
    ) {
        if (query.isBlank()) {
            if (recent.isNotEmpty()) {
                SideLabel("RECENT")
                recent.forEach { text ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SideLine(text = AnnotatedString(text), onClick = { onPick(text) }, modifier = Modifier.weight(1f))
                        SideLine(
                            text = AnnotatedString("✕"),
                            onClick = {
                                RecentSearches.remove(context, account, text)
                                recent = RecentSearches.get(context, account)
                            },
                            muted = true,
                        )
                    }
                }
                SideLine(
                    text = AnnotatedString("Clear all"),
                    onClick = {
                        RecentSearches.clear(context, account)
                        recent = emptyList()
                    },
                    muted = true,
                )
            }
            if (popular.isNotEmpty()) {
                SideLabel("POPULAR", top = if (recent.isNotEmpty()) 14.dp else 0.dp)
                popular.take(6).forEach { item ->
                    SideLine(
                        text = AnnotatedString(item.name.orEmpty()),
                        onClick = { onClickPopular(item) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            suggestions.forEach { name ->
                SideLine(
                    text = withTypedBold(name, query),
                    onClick = { onPick(name) },
                    onFocus = { onTry(name) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** How wide the TV's search column is */
internal val SIDE_WIDTH = 300.dp

/** Phones: the suggestions as a row of chips under the search box */
@Composable
fun SuggestionChips(
    query: String,
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(suggestions, key = { it }) { name ->
            Surface(
                onClick = { onPick(name) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = BenflixColors.Raised,
                        contentColor = BenflixColors.Text,
                        focusedContainerColor = BenflixColors.Text,
                        focusedContentColor = BenflixColors.Tray,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    text = withTypedBold(name, query),
                    fontSize = BenflixType.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** The typed letters in bold, the rest of the name lighter, like "**star** wars" */
internal fun withTypedBold(
    name: String,
    query: String,
): AnnotatedString {
    val q = query.trim()
    val at = if (q.isEmpty()) -1 else name.indexOf(q, ignoreCase = true)
    return buildAnnotatedString {
        if (at < 0) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(name.substring(0, at)) }
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(name.substring(at, at + q.length)) }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(name.substring(at + q.length)) }
        }
    }
}

@Composable
private fun SideLabel(
    text: String,
    top: androidx.compose.ui.unit.Dp = 0.dp,
) = Text(
    text = text,
    fontSize = BenflixType.tag,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = 1.3.sp,
    color = BenflixColors.Muted,
    modifier = Modifier.padding(start = 10.dp, top = top, bottom = 2.dp),
)

@Composable
private fun SideLine(
    text: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (muted) BenflixColors.Muted else BenflixColors.Text,
                focusedContainerColor = BenflixColors.Raised,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus?.invoke() },
    ) {
        Text(
            text = text,
            fontSize = BenflixType.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}
