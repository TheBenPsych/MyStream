package com.github.damontecres.wholphin.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

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
                        focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                        focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
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
