package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock

/**
 * Benflix redesign (Requests): the two rows at the top of Movies, Shows and Anime.
 * "Waiting for you" (main profile only): who asked, with Yes and No right on the card.
 * "Your requests": what you asked for, in words, with a thin bar for how far along it is.
 */
@Composable
internal fun RequestCardRow(
    title: String,
    entries: List<RequestEntry>,
    waiting: Boolean,
    onOpen: (RequestEntry) -> Unit,
    onAnswer: (RequestEntry, Boolean) -> Unit,
) {
    if (entries.isEmpty()) return
    val isTv = rememberIsTv()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.scrollAsOneBlock()) {
        Text(
            text = "$title (${entries.size})",
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.Bold,
            color = BenflixColors.Text,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(entries, key = { "rc-$title-${it.request.id ?: it.hashCode()}" }) { entry ->
                RequestCard(
                    entry = entry,
                    waiting = waiting,
                    width = if (isTv) 300 else 260,
                    onOpen = { onOpen(entry) },
                    onAnswer = { onAnswer(entry, it) },
                )
            }
        }
    }
}

/**
 * Benflix (compact Requests, 2026-11-10): "Your requests" and "Waiting for you" are small buttons on the
 * top line now; pressing one opens this short list of the same cards.
 */
@Composable
internal fun RequestListDialog(
    title: String,
    entries: List<RequestEntry>,
    waiting: Boolean,
    onOpen: (RequestEntry) -> Unit,
    onAnswer: (RequestEntry, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = rememberIsTv()
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = "$title (${entries.size})",
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Text,
            )
            if (entries.isEmpty()) {
                Text(
                    text =
                        if (waiting) {
                            "Nobody is waiting on you right now."
                        } else {
                            "Nothing asked for yet. Pick a poster and press Ask for it."
                        },
                    fontSize = BenflixType.body,
                    color = BenflixColors.Soft,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = if (isTv) 380.dp else 520.dp),
                ) {
                    items(entries, key = { "rd-$title-${it.request.id ?: it.hashCode()}" }) { entry ->
                        RequestCard(
                            entry = entry,
                            waiting = waiting,
                            width = if (isTv) 420 else 300,
                            onOpen = {
                                onDismiss()
                                onOpen(entry)
                            },
                            onAnswer = { onAnswer(entry, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    entry: RequestEntry,
    waiting: Boolean,
    width: Int,
    onOpen: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .width(width.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BenflixColors.Panel)
                .padding(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 66.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BenflixColors.Raised),
            ) {
                AsyncImage(
                    model = entry.item?.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.item?.title ?: "Unknown title",
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    color = BenflixColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (waiting) "${entry.who} asked" else statusWords(entry),
                    fontSize = BenflixType.small,
                    color = BenflixColors.Soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (waiting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardButton("Yes", primary = true, modifier = Modifier.weight(1f)) { onAnswer(true) }
                CardButton("No", modifier = Modifier.weight(1f)) { onAnswer(false) }
            }
        } else {
            // Thin bar: asked, then yes, then downloading, then ready
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BenflixColors.Raised),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress(entry))
                        .height(4.dp)
                        .background(if (declined(entry)) Color(0xFFFF8A8A) else BenflixColors.Text),
                )
            }
            CardButton(if (entry.available) "Watch" else "Details", modifier = Modifier.fillMaxWidth(), onClick = onOpen)
        }
    }
}

@Composable
private fun CardButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) BenflixColors.Text else BenflixColors.Raised,
                contentColor = if (primary) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 7.dp, horizontal = 12.dp),
        )
    }
}

private fun declined(entry: RequestEntry) =
    !entry.available && (entry.status == RequestStatus.DECLINED || entry.status == RequestStatus.FAILURE)

private fun statusWords(entry: RequestEntry): String =
    when {
        entry.available -> "Ready to watch"
        declined(entry) -> "Not this time"
        entry.downloading -> entry.downloadPercent?.let { "Downloading, $it%" } ?: "Downloading"
        entry.status == RequestStatus.PENDING -> "Waiting for a yes"
        else -> "Yes! Looking for it"
    }

private fun progress(entry: RequestEntry): Float =
    when {
        entry.available -> 1f
        declined(entry) -> 1f
        entry.downloading -> 0.4f + 0.55f * ((entry.downloadPercent ?: 0) / 100f)
        entry.status == RequestStatus.PENDING -> 0.12f
        else -> 0.35f
    }
