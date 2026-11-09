package com.github.damontecres.wholphin.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

private val Letters = ('A'..'Z').map { it.toString() } + ('1'..'9').map { it.toString() } + "0"

/**
 * Benflix redesign search keyboard (kb-abc + kb-suggest), TV only: A to Z six across, then the
 * numbers, Space and Delete (hold Delete to clear everything). Finished names from the results show
 * underneath; pick one to search for it. Easy mode and Kids get bigger keys, five across.
 */
@Composable
fun SearchKeyboard(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    big: Boolean,
    modifier: Modifier = Modifier,
) {
    // Benflix "Type on your phone for the TV": phones with Benflix open offer to type
    PhoneKeysForTv(query = query, onQueryChange = onQueryChange)
    val across = if (big) 5 else 6
    val keySize = if (big) 54.dp else 42.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 8.dp, end = 8.dp),
    ) {
        Letters.chunked(across).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { letter ->
                    Key(letter, width = keySize, height = keySize, big = big) { onQueryChange(query + letter.lowercase()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val wide = keySize * (across / 2) + 6.dp * (across / 2 - 1)
            Key("Space", width = wide, height = keySize, big = big) { if (query.isNotEmpty()) onQueryChange("$query ") }
            Key(
                "Delete",
                width = wide,
                height = keySize,
                big = big,
                onLongClick = { onQueryChange("") },
            ) { onQueryChange(query.dropLast(1)) }
        }
        if (suggestions.isNotEmpty()) {
            Text(
                text = "SUGGESTIONS",
                color = BenflixColors.Muted,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            val width = keySize * across + 6.dp * (across - 1)
            suggestions.forEach { name ->
                Key(name, width = width, height = 36.dp, big = false, alignStart = true) { onSuggestion(name) }
            }
        }
    }
}

@Composable
private fun Key(
    label: String,
    width: Dp,
    height: Dp,
    big: Boolean,
    alignStart: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
                pressedContainerColor = BenflixColors.Text,
                pressedContentColor = BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.size(width = width, height = height),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (alignStart) Arrangement.Start else Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(horizontal = 10.dp),
        ) {
            Text(
                text = label,
                fontSize =
                    if (big) {
                        20.sp
                    } else if (label.length > 1) {
                        13.sp
                    } else {
                        16.sp
                    },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
